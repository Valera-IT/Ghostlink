// Проверка Firebase ID-токена без Admin SDK: подпись RS256 ключами Google и поля по документации Firebase.
// https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library

export const GOOGLE_KEYS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';

/** Допуск на расхождение часов клиента, Google и Cloudflare. */
const CLOCK_SKEW_SECONDS = 300;

/** Ключи Google живут несколько часов; держим их в памяти изолята, пока не истечёт срок из Cache-Control. */
let keyCache = { keys: null, expiresAt: 0 };

export class AuthError extends Error {}

/**
 * Пользователь из токена `Authorization: Bearer …`: { uid, projectId }.
 * Токен должен быть выписан одним из [projects]; анонимный аккаунт (временный, для привязки устройства) не подходит.
 */
export async function verifyIdToken(token, projects, { now = Date.now() / 1000, fetchKeys = fetchGoogleKeys } = {}) {
  const parts = (token ?? '').split('.');
  if (parts.length !== 3) throw new AuthError('Malformed token');
  const [headerPart, payloadPart, signaturePart] = parts;
  const header = parseJson(headerPart);
  const claims = parseJson(payloadPart);
  if (header.alg !== 'RS256' || typeof header.kid !== 'string') throw new AuthError('Unexpected token header');

  const jwk = (await fetchKeys()).find((key) => key.kid === header.kid);
  if (!jwk) throw new AuthError('Unknown signing key');
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const signed = new TextEncoder().encode(`${headerPart}.${payloadPart}`);
  if (!(await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, base64UrlDecode(signaturePart), signed))) {
    throw new AuthError('Bad signature');
  }

  if (!projects.includes(claims.aud)) throw new AuthError('Token of another project');
  if (claims.iss !== `https://securetoken.google.com/${claims.aud}`) throw new AuthError('Wrong issuer');
  if (typeof claims.sub !== 'string' || claims.sub.length === 0 || claims.sub.length > 128) throw new AuthError('No subject');
  if (!(claims.exp > now)) throw new AuthError('Token expired');
  if (!(claims.iat <= now + CLOCK_SKEW_SECONDS) || !(claims.auth_time <= now + CLOCK_SKEW_SECONDS)) {
    throw new AuthError('Token from the future');
  }
  if (claims.firebase?.sign_in_provider === 'anonymous') throw new AuthError('Anonymous account');
  return { uid: claims.sub, projectId: claims.aud };
}

async function fetchGoogleKeys() {
  if (keyCache.keys && Date.now() < keyCache.expiresAt) return keyCache.keys;
  const response = await fetch(GOOGLE_KEYS_URL);
  if (!response.ok) throw new Error(`Google keys: HTTP ${response.status}`);
  const maxAge = Number(/max-age=(\d+)/.exec(response.headers.get('cache-control') ?? '')?.[1] ?? 3600);
  keyCache = { keys: (await response.json()).keys, expiresAt: Date.now() + maxAge * 1000 };
  return keyCache.keys;
}

function parseJson(part) {
  try {
    return JSON.parse(new TextDecoder().decode(base64UrlDecode(part)));
  } catch {
    throw new AuthError('Malformed token');
  }
}

function base64UrlDecode(value) {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}
