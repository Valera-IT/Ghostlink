import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { GOOGLE_KEYS_URL } from '../src/auth.js';
import worker, { MAX_FILE_BYTES } from '../src/index.js';
import { idToken, JWKS, PROJECT } from './helpers.js';

const env = {
  FIREBASE_PROJECTS: `${PROJECT},ghostlink-other`,
  B2_BUCKET: 'bucket',
  B2_REGION: 'eu-central-003',
  B2_ENDPOINT: 's3.eu-central-003.backblazeb2.com',
  B2_KEY_ID: 'key-id',
  B2_APP_KEY: 'app-key',
};

/** Чаты поддельного Firestore: id → участники. Как и правила, чужой чат отдаёт 403. */
let chats;
let firestoreCalls;

beforeEach(() => {
  chats = { alice_bob: ['alice', 'bob'], group1: ['alice', 'bob', 'carol'] };
  firestoreCalls = 0;
  globalThis.fetch = async (url, init) => {
    if (url === GOOGLE_KEYS_URL) {
      return new Response(JSON.stringify({ keys: JWKS }), { headers: { 'cache-control': 'public, max-age=3600' } });
    }
    const match = /\/v1\/projects\/([^/]+)\/databases\/\(default\)\/documents\/chats\/([^?]+)\?/.exec(url);
    assert.ok(match, `unexpected fetch ${url}`);
    firestoreCalls++;
    const uid = JSON.parse(Buffer.from(init.headers.authorization.split('.')[1], 'base64url')).sub;
    const members = chats[match[2]];
    if (!members) return new Response('{}', { status: 403 });
    if (!members.includes(uid)) return new Response('{}', { status: 403 });
    const values = members.map((stringValue) => ({ stringValue }));
    return Response.json({ fields: { members: { arrayValue: { values } } } });
  };
});

async function call(path, body, uid = 'alice', method = 'POST') {
  const headers = uid ? { authorization: `Bearer ${await idToken(uid)}` } : {};
  const request = new Request(`https://worker.example${path}`, {
    method,
    headers,
    body: method === 'POST' ? JSON.stringify(body) : undefined,
  });
  const response = await worker.fetch(request, env);
  return { status: response.status, body: await response.json() };
}

test('member gets upload URLs for a photo and its preview', async () => {
  const { status, body } = await call('/media/upload', { chatId: 'alice_bob', sizes: [200_000, 20_000] });
  assert.equal(status, 200);
  assert.equal(body.files.length, 2);
  for (const [i, file] of body.files.entries()) {
    assert.match(file.key, new RegExp(`^${PROJECT}/alice_bob/alice/[0-9a-z]+-[0-9a-f]{16}\\.jpg$`));
    assert.ok(file.url.startsWith(`https://bucket.s3.eu-central-003.backblazeb2.com/${file.key}?`));
    assert.deepEqual(file.headers, { 'content-type': 'image/jpeg', 'content-length': String([200_000, 20_000][i]) });
  }
  assert.notEqual(body.files[0].key, body.files[1].key);
});

test('any group member can download, outsiders cannot', async () => {
  const { body } = await call('/media/upload', { chatId: 'group1', sizes: [1000] });
  const key = body.files[0].key;
  const forCarol = await call('/media/download', { chatId: 'group1', keys: [key] }, 'carol');
  assert.equal(forCarol.status, 200);
  assert.ok(forCarol.body.urls[key].includes('X-Amz-Signature='));
  assert.equal((await call('/media/download', { chatId: 'group1', keys: [key] }, 'mallory')).status, 403);
  assert.equal((await call('/media/upload', { chatId: 'group1', sizes: [1000] }, 'mallory')).status, 403);
});

test('files of another chat cannot be downloaded through a chat you are in', async () => {
  const { body } = await call('/media/upload', { chatId: 'group1', sizes: [1000] });
  const response = await call('/media/download', { chatId: 'alice_bob', keys: [body.files[0].key] });
  assert.equal(response.status, 403);
  const traversal = await call('/media/download', { chatId: 'alice_bob', keys: [`${PROJECT}/alice_bob/../group1/x.jpg`] });
  assert.equal(traversal.status, 403);
});

test('membership is checked once and then cached', async () => {
  chats.cached = ['alice', 'bob'];
  await call('/media/upload', { chatId: 'cached', sizes: [1] });
  await call('/media/upload', { chatId: 'cached', sizes: [1] });
  assert.equal(firestoreCalls, 1);
});

test('rejects bad requests', async () => {
  const cases = [
    [401, '/media/upload', { chatId: 'alice_bob', sizes: [1] }, null],
    [400, '/media/upload', { chatId: '../users', sizes: [1] }],
    [400, '/media/upload', { chatId: 'alice_bob', sizes: [] }],
    [400, '/media/upload', { chatId: 'alice_bob', sizes: [1, 1, 1] }],
    [413, '/media/upload', { chatId: 'alice_bob', sizes: [MAX_FILE_BYTES + 1] }],
    [413, '/media/upload', { chatId: 'alice_bob', sizes: [1.5] }],
    [400, '/media/download', { chatId: 'alice_bob', keys: [] }],
    [404, '/media/nothing', { chatId: 'alice_bob' }],
  ];
  for (const [status, path, body, uid = 'alice'] of cases) {
    assert.equal((await call(path, body, uid)).status, status, `${path} ${JSON.stringify(body)}`);
  }
  assert.equal((await call('/media/upload', null, 'alice', 'GET')).status, 405);
});

test('rate limit answers 429', async () => {
  const limited = { ...env, UPLOAD_LIMITER: { limit: async () => ({ success: false }) } };
  const request = new Request('https://worker.example/media/upload', {
    method: 'POST',
    headers: { authorization: `Bearer ${await idToken('alice')}` },
    body: JSON.stringify({ chatId: 'alice_bob', sizes: [1] }),
  });
  assert.equal((await worker.fetch(request, limited)).status, 429);
});
