// Доступ к API Google от сервис-аккаунта проекта без Admin SDK: JWT, подписанный ключом из секрета,
// меняется на OAuth-токен. https://developers.google.com/identity/protocols/oauth2/service-account#httprest

export const TOKEN_URL = 'https://oauth2.googleapis.com/token';

/** FCM HTTP v1 и Firestore в обход правил: токены устройств получателя видит только сам получатель. */
const SCOPES = 'https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore';

/** Токен живёт час; берём новый чуть раньше, чтобы он не истёк посреди запроса. */
const REFRESH_MARGIN_SECONDS = 300;

/** projectId → { token, expiresAt } в памяти изолята. */
const tokens = new Map();

export class NotConfiguredError extends Error {}

/**
 * OAuth-токен сервис-аккаунта [projectId]. Секрет SERVICE_ACCOUNTS — JSON-ключ сервис-аккаунта
 * (как его скачивает консоль Google Cloud) или массив таких ключей, по одному на проект.
 */
export async function accessToken(env, projectId, { now = Date.now() / 1000 } = {}) {
  const cached = tokens.get(projectId);
  if (cached && cached.expiresAt > now) return cached.token;

  const account = serviceAccount(env, projectId);
  const assertion = await signJwt(account, now);
  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!response.ok) throw new Error(`Google token: HTTP ${response.status}`);
  const { access_token: token, expires_in: expiresIn } = await response.json();
  tokens.set(projectId, { token, expiresAt: now + expiresIn - REFRESH_MARGIN_SECONDS });
  return token;
}

function serviceAccount(env, projectId) {
  let accounts;
  try {
    accounts = [JSON.parse(env.SERVICE_ACCOUNTS ?? '[]')].flat();
  } catch {
    throw new NotConfiguredError('SERVICE_ACCOUNTS is not JSON');
  }
  const account = accounts.find((a) => a.project_id === projectId);
  if (!account?.client_email || !account?.private_key) throw new NotConfiguredError(`No service account for ${projectId}`);
  return account;
}

async function signJwt(account, now) {
  const iat = Math.floor(now);
  const header = { alg: 'RS256', typ: 'JWT', kid: account.private_key_id };
  const claims = { iss: account.client_email, scope: SCOPES, aud: TOKEN_URL, iat, exp: iat + 3600 };
  const unsigned = `${base64Url(JSON.stringify(header))}.${base64Url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey('pkcs8', pemBody(account.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, [
    'sign',
  ]);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

function pemBody(pem) {
  const base64 = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, '').replace(/\s/g, '');
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function base64Url(value) {
  const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : value;
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/** Для тестов: забыть выданные токены. */
export function resetAccessTokens() {
  tokens.clear();
}
