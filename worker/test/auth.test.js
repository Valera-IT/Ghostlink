import assert from 'node:assert/strict';
import { test } from 'node:test';
import { AuthError, verifyIdToken } from '../src/auth.js';
import { idToken, JWKS, PROJECT } from './helpers.js';

const verify = (token) => verifyIdToken(token, [PROJECT], { fetchKeys: async () => JWKS });

test('valid token gives uid and project', async () => {
  assert.deepEqual(await verify(await idToken('alice')), { uid: 'alice', projectId: PROJECT });
});

test('rejects tokens that are broken, foreign, expired or anonymous', async (t) => {
  const now = Math.floor(Date.now() / 1000);
  const cases = {
    garbage: 'not-a-token',
    'other project': await idToken('alice', { aud: 'someone-else', iss: 'https://securetoken.google.com/someone-else' }),
    'wrong issuer': await idToken('alice', { iss: 'https://evil.example.com' }),
    expired: await idToken('alice', { exp: now - 1 }),
    'issued in the future': await idToken('alice', { iat: now + 3600 }),
    'no subject': await idToken(''),
    anonymous: await idToken('alice', { firebase: { sign_in_provider: 'anonymous' } }),
    'unknown key': await idToken('alice', {}, { kid: 'other' }),
    'other algorithm': await idToken('alice', {}, { alg: 'HS256' }),
  };
  for (const [name, token] of Object.entries(cases)) {
    await t.test(name, () => assert.rejects(verify(token), AuthError));
  }
});

test('rejects a token with a forged payload', async () => {
  const [head, , signature] = (await idToken('alice')).split('.');
  const forged = Buffer.from(JSON.stringify({ aud: PROJECT, sub: 'mallory' })).toString('base64url');
  await assert.rejects(verify(`${head}.${forged}.${signature}`), AuthError);
});
