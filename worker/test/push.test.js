import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { GOOGLE_KEYS_URL } from '../src/auth.js';
import { resetAccessTokens, TOKEN_URL } from '../src/google.js';
import worker from '../src/index.js';
import { PREVIEW_LENGTH, resetPushCaches } from '../src/push.js';
import { idToken, JWKS, PROJECT } from './helpers.js';

const { privateKey } = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true,
  ['sign', 'verify'],
);
const pem = `-----BEGIN PRIVATE KEY-----\n${Buffer.from(await crypto.subtle.exportKey('pkcs8', privateKey)).toString('base64')}\n-----END PRIVATE KEY-----\n`;
const serviceAccount = { project_id: PROJECT, client_email: `push@${PROJECT}.iam.gserviceaccount.com`, private_key: pem, private_key_id: 'k1' };

const env = { FIREBASE_PROJECTS: PROJECT, SERVICE_ACCOUNTS: JSON.stringify([serviceAccount]) };
const ADMIN = 'admin-access-token';
const ROOT = `/v1/projects/${PROJECT}/databases/(default)/documents/`;

/** Поддельный Firestore: путь → поля; правила упрощены до «участник видит чат и его сообщения». */
let docs;
let fcm;
let deleted;
let tokenRequests;

const value = (v) => {
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } };
  return { mapValue: { fields: encode(v) } };
};
const encode = (fields) => Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, value(v)]));
const uidOf = (auth) => JSON.parse(Buffer.from(auth.split('.')[1], 'base64url')).sub;

function allowed(path, auth) {
  if (auth === `Bearer ${ADMIN}`) return true;
  const uid = uidOf(auth);
  if (path.startsWith('users/') && path.split('/').length === 2) return true;
  const chat = docs[`chats/${path.split('/')[1]}`];
  return path.startsWith('chats/') && chat?.members.includes(uid);
}

beforeEach(() => {
  resetAccessTokens();
  resetPushCaches();
  const now = new Date().toISOString();
  docs = {
    'chats/alice_bob': { members: ['alice', 'bob'] },
    'chats/group1': { members: ['alice', 'bob', 'carol'], type: 'group', name: 'Друзья' },
    'chats/alice_bob/messages/m1': { senderId: 'alice', text: 'Привет!', sentAt: now },
    'chats/alice_bob/messages/old': { senderId: 'alice', text: 'давно', sentAt: '2020-01-01T00:00:00Z' },
    'chats/alice_bob/messages/photo': { senderId: 'alice', text: '', sentAt: now, attachment: { type: 'photo' } },
    'chats/alice_bob/messages/long': { senderId: 'alice', text: 'x'.repeat(500), sentAt: now },
    'chats/group1/messages/g1': { senderId: 'alice', text: 'всем', sentAt: now },
    'users/alice': { name: 'Алиса' },
    'users/alice/devices/a1': { pushToken: 'token-alice' },
    'users/bob/devices/b1': { pushToken: 'token-bob-phone' },
    'users/bob/devices/b2': { name: 'MacBook' },
    'users/carol/devices/c1': { pushToken: 'token-carol' },
  };
  fcm = [];
  deleted = [];
  tokenRequests = 0;

  globalThis.fetch = async (url, init = {}) => {
    if (url === GOOGLE_KEYS_URL) return Response.json({ keys: JWKS });
    if (url === TOKEN_URL) {
      tokenRequests++;
      const assertion = new URLSearchParams(init.body).get('assertion');
      const claims = JSON.parse(Buffer.from(assertion.split('.')[1], 'base64url'));
      assert.equal(claims.iss, serviceAccount.client_email);
      return Response.json({ access_token: ADMIN, expires_in: 3600 });
    }
    if (url.startsWith('https://fcm.googleapis.com/')) {
      assert.equal(init.headers.authorization, `Bearer ${ADMIN}`);
      const { message } = JSON.parse(init.body);
      fcm.push(message);
      if (message.token === 'token-stale') {
        return Response.json({ error: { details: [{ errorCode: 'UNREGISTERED' }] } }, { status: 404 });
      }
      return Response.json({ name: 'ok' });
    }
    const path = decodeURIComponent(new URL(url).pathname).slice(ROOT.length);
    const auth = init.headers.authorization;
    if (init.method === 'PATCH') {
      assert.equal(auth, `Bearer ${ADMIN}`);
      deleted.push(path);
      delete docs[path].pushToken;
      return Response.json({});
    }
    if (!allowed(path, auth)) return new Response('{}', { status: 403 });
    if (docs[path]) return Response.json({ fields: encode(docs[path]) });
    const children = Object.keys(docs).filter((p) => p.startsWith(`${path}/`) && p.split('/').length === path.split('/').length + 1);
    if (children.length > 0) {
      return Response.json({
        documents: children.map((p) => ({ name: `projects/${PROJECT}/databases/(default)/documents/${p}`, fields: encode(docs[p]) })),
      });
    }
    if (path.split('/').length % 2 === 1) return Response.json({});
    return new Response('{}', { status: 404 });
  };
});

async function call(body, uid = 'alice') {
  const response = await worker.fetch(
    new Request('https://worker.example/push', { method: 'POST', headers: { authorization: `Bearer ${await idToken(uid)}` }, body: JSON.stringify(body) }),
    env,
  );
  return { status: response.status, body: await response.json() };
}

test('recipient devices with a token get a data message, the author does not', async () => {
  const { status, body } = await call({ chatId: 'alice_bob', messageId: 'm1' });
  assert.equal(status, 200);
  assert.equal(body.sent, 1);
  assert.equal(fcm.length, 1);
  assert.equal(fcm[0].token, 'token-bob-phone');
  assert.deepEqual(fcm[0].data, {
    chatId: 'alice_bob',
    messageId: 'm1',
    senderId: 'alice',
    senderName: 'Алиса',
    chatName: '',
    text: 'Привет!',
    photo: 'false',
  });
  assert.equal(fcm[0].android.priority, 'HIGH');
});

test('group push names the group and reaches every other member', async () => {
  const { body } = await call({ chatId: 'group1', messageId: 'g1' });
  assert.equal(body.sent, 2);
  assert.deepEqual(fcm.map((m) => m.token).sort(), ['token-bob-phone', 'token-carol']);
  assert.equal(fcm[0].data.chatName, 'Друзья');
});

test('photo and long text are previewed', async () => {
  await call({ chatId: 'alice_bob', messageId: 'photo' });
  assert.equal(fcm[0].data.photo, 'true');
  await call({ chatId: 'alice_bob', messageId: 'long' });
  assert.equal(fcm[1].data.text.length, PREVIEW_LENGTH);
});

test('muted chat is not pushed', async () => {
  docs['users/bob/chatPrefs/alice_bob'] = { muted: true };
  const { body } = await call({ chatId: 'alice_bob', messageId: 'm1' });
  assert.equal(body.sent, 0);
  assert.equal(fcm.length, 0);
});

test('only the author can push their message', async () => {
  assert.equal((await call({ chatId: 'alice_bob', messageId: 'm1' }, 'bob')).status, 403);
  assert.equal((await call({ chatId: 'alice_bob', messageId: 'm1' }, 'carol')).status, 404);
  assert.equal((await call({ chatId: 'alice_bob', messageId: 'nope' })).status, 404);
  assert.equal(fcm.length, 0);
});

test('old messages and repeated requests do not push again', async () => {
  assert.equal((await call({ chatId: 'alice_bob', messageId: 'old' })).body.sent, 0);
  assert.equal((await call({ chatId: 'alice_bob', messageId: 'm1' })).body.sent, 1);
  assert.equal((await call({ chatId: 'alice_bob', messageId: 'm1' })).body.sent, 0);
  assert.equal(fcm.length, 1);
});

test('token FCM rejects as unregistered is erased', async () => {
  docs['users/bob/devices/b1'].pushToken = 'token-stale';
  const { body } = await call({ chatId: 'alice_bob', messageId: 'm1' });
  assert.equal(body.sent, 0);
  assert.deepEqual(deleted, ['users/bob/devices/b1']);
  assert.equal(docs['users/bob/devices/b1'].pushToken, undefined);
});

test('access token of the service account is reused', async () => {
  await call({ chatId: 'alice_bob', messageId: 'm1' });
  await call({ chatId: 'group1', messageId: 'g1' });
  assert.equal(tokenRequests, 1);
});

test('bad input and missing service account', async () => {
  assert.equal((await call({ chatId: 'alice_bob' })).status, 400);
  assert.equal((await call({ chatId: 'alice_bob', messageId: '../x' })).status, 400);
  const response = await worker.fetch(
    new Request('https://worker.example/push', {
      method: 'POST',
      headers: { authorization: `Bearer ${await idToken('alice')}` },
      body: JSON.stringify({ chatId: 'alice_bob', messageId: 'm1' }),
    }),
    { FIREBASE_PROJECTS: PROJECT },
  );
  assert.equal(response.status, 503);
});
