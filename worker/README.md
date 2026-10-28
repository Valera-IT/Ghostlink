# Worker Ghostlink

Cloudflare Worker на бесплатном тарифе. Выдаёт приложению подписанные ссылки на фото чатов в приватном бакете Backblaze B2 (ключ B2 лежит только в секретах Worker, а ссылка открывает один файл на 10 минут) и шлёт push о новых сообщениях через FCM. Почему B2 и какие у него лимиты — в [docs/ROADMAP.md](../docs/ROADMAP.md), этап 6.

```
POST /media/upload    { chatId, sizes: [байты, …] }  → { files: [{ key, url, headers }] }
POST /media/download  { chatId, keys: [key, …] }     → { urls: { key: url } }
POST /push            { chatId, messageId }          → { sent }
```

Каждый запрос несёт `Authorization: Bearer <Firebase ID-токен>`. Worker проверяет подпись токена ключами Google, проект (prod или dev) и что аккаунт не анонимный, а потом читает `chats/{chatId}` через REST Firestore тем же токеном: правила Firestore отдают чат только участнику, так что Worker не нужен сервис-аккаунт. Загрузить можно до двух файлов (фото и превью) по 1 МБ, размер и тип входят в подпись ссылки. Скачать можно только файлы того чата, членство в котором проверено.

Ключ файла: `{проект}/{chatId}/{uid автора}/{время}-{случайное}.jpg`.

**Push.** Приложение зовёт `/push` сразу после отправки сообщения. Worker читает сообщение токеном автора (так проверяются членство и что автор — он), имя автора и состав чата, а потом сервис-аккаунтом проекта — `chatPrefs` и устройства каждого получателя: эти документы правила отдают только владельцу. На каждое устройство с `pushToken` уходит data-сообщение FCM HTTP v1 с чатом, автором и первыми 100 символами текста; уведомление рисует само приложение. Заглушённый чат, свои устройства автора, удалённые и сообщения старше 5 минут не шлются, повтор запроса — тоже. Токен, который FCM назвал недействительным, Worker стирает из устройства. Расход Firestore: около 5 чтений на сообщение в личном чате.

## Тесты

Нужен только Node 20+, зависимостей нет:

```bash
cd worker && npm test
```

## Развёртывание

Делается один раз владельцем аккаунтов Cloudflare и Backblaze; ключи никуда, кроме секретов Worker, не попадают.

1. **Ключ B2.** Backblaze → Application Keys → Add a New Application Key: доступ только к бакету из `B2_BUCKET` в `wrangler.toml`, Read and Write. Сохрани `keyID` и `applicationKey` — второй показывается один раз.
2. **Вход в Cloudflare:** `cd worker && npx wrangler@4.149.0 login`.
3. **Развёртывание:** `npm run deploy`. Wrangler напечатает адрес вида `https://ghostlink.<поддомен>.workers.dev`.
4. **Секреты** (Wrangler спросит значение):

   ```bash
   npx wrangler@4.149.0 secret put B2_KEY_ID
   npx wrangler@4.149.0 secret put B2_APP_KEY
   ```

5. **Сервис-аккаунт для push.** Для каждого проекта (prod и dev): консоль Firebase → Настройки проекта → Сервисные аккаунты → «Создать закрытый ключ» — скачается JSON. Секрет — JSON-массив обоих ключей (`jq -s . prod.json dev.json`), после загрузки файлы удали:

   ```bash
   jq -s . prod.json dev.json | npx wrangler@4.149.0 secret put SERVICE_ACCOUNTS
   ```

   Без секрета `/push` отвечает 503, а фото работают как раньше. Firebase Cloud Messaging API (V1) включён в новых проектах по умолчанию (консоль Firebase → Cloud Messaging).

6. **Проверка:** запрос без токена должен вернуть 401:

   ```bash
   curl -i -X POST https://ghostlink.<поддомен>.workers.dev/media/upload
   ```

Адрес Worker прописывается в приложении: `MEDIA_WORKER_URL` в `core/data`.
