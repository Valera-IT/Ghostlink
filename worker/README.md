# Worker Ghostlink

Cloudflare Worker на бесплатном тарифе. Выдаёт приложению подписанные ссылки на фото чатов в приватном бакете Backblaze B2: ключ B2 лежит только в секретах Worker, а ссылка открывает один файл на 10 минут. Почему B2 и какие у него лимиты — в [docs/ROADMAP.md](../docs/ROADMAP.md), этап 6.

```
POST /media/upload    { chatId, sizes: [байты, …] }  → { files: [{ key, url, headers }] }
POST /media/download  { chatId, keys: [key, …] }     → { urls: { key: url } }
```

Каждый запрос несёт `Authorization: Bearer <Firebase ID-токен>`. Worker проверяет подпись токена ключами Google, проект (prod или dev) и что аккаунт не анонимный, а потом читает `chats/{chatId}` через REST Firestore тем же токеном: правила Firestore отдают чат только участнику, так что Worker не нужен сервис-аккаунт. Загрузить можно до двух файлов (фото и превью) по 1 МБ, размер и тип входят в подпись ссылки. Скачать можно только файлы того чата, членство в котором проверено.

Ключ файла: `{проект}/{chatId}/{uid автора}/{время}-{случайное}.jpg`.

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

5. **Проверка:** запрос без токена должен вернуть 401:

   ```bash
   curl -i -X POST https://ghostlink.<поддомен>.workers.dev/media/upload
   ```

Адрес Worker прописывается в приложении: `MEDIA_WORKER_URL` в `core/data`.
