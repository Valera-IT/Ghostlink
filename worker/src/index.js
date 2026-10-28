// Worker Ghostlink: выдаёт подписанные ссылки на фото чатов в приватном бакете Backblaze B2 и шлёт push.
// Ключ B2 и сервис-аккаунт лежат только в секретах Worker; приложение получает ссылки на один файл, живущие минуты.
//
//   POST /media/upload    { chatId, sizes: [байты, …] }  → { files: [{ key, url, headers }] }
//   POST /media/download  { chatId, keys: [key, …] }     → { urls: { key: url } }
//   POST /push            { chatId, messageId }          → { sent } — см. push.js
//
// Заголовок Authorization: Bearer <Firebase ID-токен>. Пускаем только участников чата: Worker читает
// chats/{chatId} через REST Firestore тем же токеном, и правила Firestore отдают документ только участнику.

import { AuthError, verifyIdToken } from './auth.js';
import { NotConfiguredError } from './google.js';
import { push } from './push.js';
import { presign } from './sigv4.js';

/** Совпадают с ограничениями в приложении (MediaLimits) и в firestore.rules. */
export const MAX_FILE_BYTES = 1024 * 1024;
export const MAX_FILES_PER_UPLOAD = 2;
export const MAX_KEYS_PER_DOWNLOAD = 100;

/** Сколько живёт ссылка: загрузка и скачивание начинаются сразу после ответа. */
const UPLOAD_URL_TTL_SECONDS = 600;
const DOWNLOAD_URL_TTL_SECONDS = 600;

/** Участник чата редко выходит из него: проверку членства держим в памяти изолята несколько минут. */
const MEMBERSHIP_TTL_MS = 5 * 60 * 1000;
const MEMBERSHIP_CACHE_SIZE = 1000;
const membership = new Map();

const CHAT_ID = /^[A-Za-z0-9_-]{1,128}$/;
const FILE_NAME = /^[0-9a-z]+-[0-9a-f]{16}\.jpg$/;

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      if (e instanceof NotConfiguredError) return json({ error: 'Push is not configured' }, 503);
      console.error(e);
      return json({ error: 'Internal error' }, 500);
    }
  },
};

async function route(request, env) {
  const { pathname } = new URL(request.url);
  if (request.method !== 'POST') throw new HttpError(405, 'Method not allowed');
  if (pathname === '/media/upload') return upload(request, env);
  if (pathname === '/media/download') return download(request, env);
  if (pathname === '/push') return pushMessage(request, env);
  throw new HttpError(404, 'Not found');
}

async function upload(request, env) {
  const { user, chatId, body } = await authorize(request, env);
  await limit(env.UPLOAD_LIMITER, user.uid);
  const sizes = body.sizes;
  if (!Array.isArray(sizes) || sizes.length === 0 || sizes.length > MAX_FILES_PER_UPLOAD) throw new HttpError(400, 'Bad sizes');
  if (!sizes.every((size) => Number.isInteger(size) && size > 0 && size <= MAX_FILE_BYTES)) {
    throw new HttpError(413, 'File too large');
  }
  // Имя файла начинается со времени: по нему клиентский кэш выбрасывает старые фото первыми.
  const stamp = Date.now().toString(36);
  const files = await Promise.all(
    sizes.map(async (size) => {
      const key = `${user.projectId}/${chatId}/${user.uid}/${stamp}-${randomHex(8)}.jpg`;
      const headers = { 'content-type': 'image/jpeg', 'content-length': String(size) };
      const url = await sign(env, 'PUT', key, UPLOAD_URL_TTL_SECONDS, headers);
      return { key, url, headers };
    }),
  );
  return json({ files });
}

async function download(request, env) {
  const { user, chatId, body } = await authorize(request, env);
  await limit(env.DOWNLOAD_LIMITER, user.uid);
  const keys = body.keys;
  if (!Array.isArray(keys) || keys.length === 0 || keys.length > MAX_KEYS_PER_DOWNLOAD) throw new HttpError(400, 'Bad keys');
  // Скачать можно только файлы этого чата: членство проверено именно для него.
  if (!keys.every((key) => isChatFile(key, user.projectId, chatId))) throw new HttpError(403, 'Not a file of this chat');
  const urls = {};
  for (const key of keys) urls[key] = await sign(env, 'GET', key, DOWNLOAD_URL_TTL_SECONDS);
  return json({ urls });
}

async function pushMessage(request, env) {
  // Членство проверяет само чтение сообщения токеном автора, отдельный запрос чата не нужен.
  const { user, chatId, body, token } = await authenticate(request, env);
  await limit(env.PUSH_LIMITER, user.uid);
  return json(await push({ env, user, chatId, body, token, HttpError }));
}

/** Токен, тело запроса и членство в чате из него. */
async function authorize(request, env) {
  const { user, chatId, body, token } = await authenticate(request, env);
  if (!(await isMember(env, user, chatId, token))) throw new HttpError(403, 'Not a member of this chat');
  return { user, chatId, body };
}

/** Пользователь из токена и chatId из тела запроса. */
async function authenticate(request, env) {
  const token = /^Bearer (.+)$/.exec(request.headers.get('authorization') ?? '')?.[1];
  let user;
  try {
    user = await verifyIdToken(token, env.FIREBASE_PROJECTS.split(','));
  } catch (e) {
    if (e instanceof AuthError) throw new HttpError(401, e.message);
    throw e;
  }
  const body = await request.json().catch(() => null);
  const chatId = body?.chatId;
  if (typeof chatId !== 'string' || !CHAT_ID.test(chatId)) throw new HttpError(400, 'Bad chatId');
  return { user, chatId, body, token };
}

async function isMember(env, user, chatId, token) {
  const cacheKey = `${user.projectId}/${chatId}/${user.uid}`;
  const cached = membership.get(cacheKey);
  if (cached && cached > Date.now()) return true;

  const base = env.FIRESTORE_URL ?? 'https://firestore.googleapis.com';
  const url = `${base}/v1/projects/${user.projectId}/databases/(default)/documents/chats/${chatId}?mask.fieldPaths=members`;
  const response = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
  if (response.status === 403 || response.status === 404) return false;
  if (!response.ok) throw new Error(`Firestore: HTTP ${response.status}`);
  const members = (await response.json()).fields?.members?.arrayValue?.values ?? [];
  if (!members.some((value) => value.stringValue === user.uid)) return false;

  if (membership.size >= MEMBERSHIP_CACHE_SIZE) membership.delete(membership.keys().next().value);
  membership.set(cacheKey, Date.now() + MEMBERSHIP_TTL_MS);
  return true;
}

function isChatFile(key, projectId, chatId) {
  if (typeof key !== 'string') return false;
  const parts = key.split('/');
  return parts.length === 4 && parts[0] === projectId && parts[1] === chatId && CHAT_ID.test(parts[2]) && FILE_NAME.test(parts[3]);
}

/** Лимит частоты на пользователя (Rate Limiting API Cloudflare); без привязки в wrangler.toml — без лимита. */
async function limit(limiter, uid) {
  if (limiter && !(await limiter.limit({ key: uid })).success) throw new HttpError(429, 'Too many requests');
}

function sign(env, method, key, expiresIn, headers) {
  return presign({
    method,
    host: `${env.B2_BUCKET}.${env.B2_ENDPOINT}`,
    region: env.B2_REGION,
    accessKeyId: env.B2_KEY_ID,
    secretKey: env.B2_APP_KEY,
    key,
    expiresIn,
    headers,
  });
}

function randomHex(bytes) {
  return Array.from(crypto.getRandomValues(new Uint8Array(bytes)), (b) => b.toString(16).padStart(2, '0')).join('');
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
