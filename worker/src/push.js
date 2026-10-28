// Push о новом сообщении: POST /push { chatId, messageId } → { sent }.
//
// Сообщение Worker читает ID-токеном автора: правила Firestore отдают его только участнику чата, а senderId
// показывает, что автор — тот, кто просит push. Токены устройств получателей и их «без звука» видит только сам
// получатель, поэтому их Worker читает сервис-аккаунтом проекта и шлёт FCM HTTP v1 тем же токеном доступа.

import { accessToken } from './google.js';

/** Сколько символов текста уходит в уведомление. */
export const PREVIEW_LENGTH = 100;

/** Push только о свежем сообщении: старое уже не новость, а повтор запроса не будит получателя снова. */
const MAX_MESSAGE_AGE_MS = 5 * 60 * 1000;

/** Имя автора и состав чата меняются редко: держим их в памяти изолята. */
const CACHE_TTL_MS = 5 * 60 * 1000;
const CACHE_SIZE = 1000;
const names = new Map();
const chats = new Map();
const pushed = new Map();

const MESSAGE_ID = /^[A-Za-z0-9_-]{1,128}$/;

export async function push({ env, user, chatId, body, token, HttpError }) {
  const messageId = body.messageId;
  if (typeof messageId !== 'string' || !MESSAGE_ID.test(messageId)) throw new HttpError(400, 'Bad messageId');
  const pushKey = `${user.projectId}/${chatId}/${messageId}`;
  if (cached(pushed, pushKey)) return { sent: 0 };

  const firestore = new Firestore(env, user.projectId);
  const message = await firestore.get(`chats/${chatId}/messages/${messageId}`, token);
  if (!message) throw new HttpError(404, 'No such message');
  if (message.senderId !== user.uid) throw new HttpError(403, 'Not your message');
  if (message.deletedAt || !(Date.now() - Date.parse(message.sentAt) < MAX_MESSAGE_AGE_MS)) return { sent: 0 };
  remember(pushed, pushKey, true);

  const chat = await cachedFetch(chats, `${user.projectId}/${chatId}`, () => firestore.get(`chats/${chatId}`, token));
  const sender = await cachedFetch(names, `${user.projectId}/${user.uid}`, () => firestore.get(`users/${user.uid}`, token));
  const data = {
    chatId,
    messageId,
    senderId: user.uid,
    senderName: sender?.name ?? '',
    chatName: chat?.type === 'group' ? (chat.name ?? '') : '',
    text: (message.text ?? '').slice(0, PREVIEW_LENGTH),
    photo: String(message.attachment?.type === 'photo'),
  };

  const admin = await accessToken(env, user.projectId);
  const recipients = (chat?.members ?? []).filter((uid) => uid !== user.uid);
  const results = await Promise.all(recipients.map((uid) => notify(env, firestore, admin, uid, chatId, data)));
  return { sent: results.reduce((a, b) => a + b, 0) };
}

/** Все устройства получателя с push, если чат у него не заглушён; сколько сообщений принял FCM. */
async function notify(env, firestore, admin, uid, chatId, data) {
  const prefs = await firestore.get(`users/${uid}/chatPrefs/${chatId}`, admin);
  if (prefs?.muted === true) return 0;
  const devices = await firestore.list(`users/${uid}/devices`, admin, ['pushToken']);
  const sent = await Promise.all(
    devices
      .filter((device) => typeof device.fields.pushToken === 'string')
      .map(async (device) => {
        const status = await sendFcm(env, firestore.projectId, admin, device.fields.pushToken, data);
        // Приложение удалили или токен сменился: стираем его, чтобы больше не слать впустую.
        if (status === 'unregistered') await firestore.deleteField(device.path, 'pushToken', admin);
        return status === 'sent' ? 1 : 0;
      }),
  );
  return sent.reduce((a, b) => a + b, 0);
}

/**
 * Data-сообщение: уведомление показывает само приложение (группирует по чату, молчит на экране чата).
 * Высокий приоритет будит Android в Doze; через сутки сообщение уже неинтересно.
 */
async function sendFcm(env, projectId, admin, token, data) {
  const base = env.FCM_URL ?? 'https://fcm.googleapis.com';
  const response = await fetch(`${base}/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { authorization: `Bearer ${admin}`, 'content-type': 'application/json' },
    body: JSON.stringify({ message: { token, data, android: { priority: 'HIGH', ttl: '86400s' } } }),
  });
  if (response.ok) return 'sent';
  const error = await response.json().catch(() => ({}));
  const code = error.error?.details?.find((d) => d.errorCode)?.errorCode;
  if (response.status === 404 || code === 'UNREGISTERED' || code === 'INVALID_ARGUMENT') return 'unregistered';
  console.error(`FCM: HTTP ${response.status} ${code ?? ''}`);
  return 'failed';
}

/** Документы Firestore через REST, поля — уже обычными значениями JavaScript. */
class Firestore {
  constructor(env, projectId) {
    this.projectId = projectId;
    this.root = `${env.FIRESTORE_URL ?? 'https://firestore.googleapis.com'}/v1/projects/${projectId}/databases/(default)/documents`;
  }

  /** Поля документа; null, если его нет или правила его не отдают. */
  async get(path, token) {
    const response = await fetch(`${this.root}/${path}`, { headers: { authorization: `Bearer ${token}` } });
    if (response.status === 403 || response.status === 404) return null;
    if (!response.ok) throw new Error(`Firestore get: HTTP ${response.status}`);
    return decodeFields((await response.json()).fields);
  }

  /** Документы коллекции: [{ path, fields }], только поля [mask]. */
  async list(path, token, mask) {
    const query = mask.map((field) => `mask.fieldPaths=${field}`).join('&');
    const response = await fetch(`${this.root}/${path}?pageSize=100&${query}`, { headers: { authorization: `Bearer ${token}` } });
    if (!response.ok) throw new Error(`Firestore list: HTTP ${response.status}`);
    const documents = (await response.json()).documents ?? [];
    return documents.map((doc) => ({ path: doc.name.slice(doc.name.indexOf('/documents/') + '/documents/'.length), fields: decodeFields(doc.fields) }));
  }

  /** Поле из маски без значения в теле PATCH удаляется. */
  async deleteField(path, field, token) {
    const response = await fetch(`${this.root}/${path}?updateMask.fieldPaths=${field}&currentDocument.exists=true`, {
      method: 'PATCH',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      body: JSON.stringify({ fields: {} }),
    });
    if (!response.ok && response.status !== 404) console.error(`Firestore delete field: HTTP ${response.status}`);
  }
}

function decodeFields(fields = {}) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, decodeValue(value)]));
}

function decodeValue(value) {
  if ('stringValue' in value) return value.stringValue;
  if ('booleanValue' in value) return value.booleanValue;
  if ('integerValue' in value) return Number(value.integerValue);
  if ('doubleValue' in value) return value.doubleValue;
  if ('timestampValue' in value) return value.timestampValue;
  if ('arrayValue' in value) return (value.arrayValue.values ?? []).map(decodeValue);
  if ('mapValue' in value) return decodeFields(value.mapValue.fields);
  return null;
}

async function cachedFetch(cache, key, load) {
  const hit = cached(cache, key);
  if (hit !== undefined) return hit;
  const value = await load();
  remember(cache, key, value);
  return value;
}

function cached(cache, key) {
  const entry = cache.get(key);
  return entry && entry.expiresAt > Date.now() ? entry.value : undefined;
}

function remember(cache, key, value) {
  if (cache.size >= CACHE_SIZE) cache.delete(cache.keys().next().value);
  cache.set(key, { value, expiresAt: Date.now() + CACHE_TTL_MS });
}

/** Для тестов: забыть кэши изолята. */
export function resetPushCaches() {
  names.clear();
  chats.clear();
  pushed.clear();
}
