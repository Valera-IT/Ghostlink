// Поддельные Google и Firestore для тестов: свой RSA-ключ вместо ключей Google и подменённый fetch.

export const PROJECT = 'ghostlink-test';

const { publicKey, privateKey } = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true,
  ['sign', 'verify'],
);
export const KID = 'test-key';
export const JWKS = [{ ...(await crypto.subtle.exportKey('jwk', publicKey)), kid: KID, alg: 'RS256', use: 'sig' }];

const base64Url = (bytes) => Buffer.from(bytes).toString('base64url');

/** ID-токен, как его выписывает Firebase Auth; [overrides] портят нужные поля. */
export async function idToken(uid, overrides = {}, header = {}) {
  const now = Math.floor(Date.now() / 1000);
  const claims = {
    iss: `https://securetoken.google.com/${PROJECT}`,
    aud: PROJECT,
    sub: uid,
    iat: now - 10,
    auth_time: now - 100,
    exp: now + 3600,
    firebase: { sign_in_provider: 'password' },
    ...overrides,
  };
  const head = base64Url(JSON.stringify({ alg: 'RS256', kid: KID, typ: 'JWT', ...header }));
  const body = base64Url(JSON.stringify(claims));
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', privateKey, new TextEncoder().encode(`${head}.${body}`));
  return `${head}.${body}.${base64Url(new Uint8Array(signature))}`;
}
