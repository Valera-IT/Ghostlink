# Ghostlink 👻

Мессенджер без номера телефона и почты. Друзей добавляют только при личной встрече: один показывает QR-код, другой сканирует, и у обоих сразу появляется общий чат.

## Возможности

- **Регистрация в один шаг.** Только имя. Под капотом скрытый аккаунт Firebase Email/Password: служебная почта `…@ghostlink.invalid` и случайный пароль, которых пользователь не видит.
- **Один аккаунт на нескольких устройствах.** Новое устройство показывает QR, телефон с аккаунтом сканирует и подтверждает вход, как в WhatsApp Web. Без сервера и Cloud Functions, на бесплатном тарифе Spark.
- **Ключ восстановления** из 40 символов возвращает аккаунт, если потеряны все устройства.
- **Устройства:** список, удаление устройства и «Выйти на всех других устройствах».
- **Друзья по QR.** Свой код (ZXing) и сканер на CameraX + ML Kit. Можно отправить ссылку-приглашение `ghostlink://add/<id>`, она открывается как deep link.
- **Чаты один на один в реальном времени** на Firestore:
  - статусы ⏱ отправляется → ✓ на сервере → ✓✓ прочитано;
  - разделители по дням;
  - непрочитанные в списке чатов.
- **Присутствие:** «в сети» и «был(а) в сети в 14:05» через heartbeat, с истечением статуса, если приложение убили.
- **Демо-режим.** Флейвор `demo` (и любая сборка без `google-services.json`) работает на бэкенде в памяти с ботом-собеседником, поэтому проект можно склонировать и сразу запустить.

## Стек

Kotlin · Jetpack Compose · Material 3 · MVVM (ViewModel + StateFlow) · Koin · Coroutines/Flow · Navigation Compose (типобезопасные маршруты) · Firebase Auth + Firestore (GitLive firebase-kotlin-sdk) · CameraX · ML Kit Barcode · qrose · kotlin.test + kotlinx-coroutines-test

## Архитектура

```
core/model     чистые модели и логика: статусы сообщений, непрочитанное, id чатов, приглашения (KMP)
core/domain    интерфейсы репозиториев и use case (KMP)
core/crypto    шифрование ключа для нового устройства на cryptography-kotlin (KMP)
core/firebase  DTO документов Firestore и поля bytes через нативный SDK (KMP: Android, JVM, iOS)
core/data      репозитории на Firebase (GitLive), мапперы DTO → domain, ошибки → GhostlinkError (KMP)
core/testing   FakeBackend — все репозитории в памяти, фейк для тестов (KMP)
core/demo      DemoBackend — демо-режим со стартовыми чатами и ботом (KMP)
core/platform  защищённое хранилище, это устройство, жизненный цикл, диплинки, сканер QR, буфер обмена (KMP)
core/designsystem  тема: палитры, формы сообщений, типографика, логотип (Compose Multiplatform)
core/ui        аватар, время и «в сети», QR-картинка, плашка демо-режима, строки интерфейса en/ru в Compose Resources
feature/*      экраны по модулям: onboarding, chats, chat, friends, devices, link, recovery, settings.
               ViewModel по UDF (UiState вниз, Action вверх, разовые Effect), свои маршруты и Koin-модуль;
               видят только core:domain, core:platform и UI-модули, друг о друге не знают (Compose Multiplatform)
apps/android/ui  корневой граф навигации: соединяет фичи между собой
apps/android/data  выбранные тема и язык
apps/android/di  Koin: собирает модули core:* и feature:*, выбирает Firebase или демо в зависимости от наличия конфига
apps/ios        то же для iPhone: фреймворк GhostlinkKit (корневой граф, Koin, тема и язык) и Xcode-проект на SwiftUI
```

Целевая модульная архитектура (KMP, Clean Architecture, модели по слоям) описана в [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), план — в [docs/ROADMAP.md](docs/ROADMAP.md).

UI зависит только от интерфейсов репозиториев. Поэтому ViewModel и use case тестируются на `FakeBackend` без моков и эмулятора, а бэкенд можно заменить, не трогая экраны.

**Модель данных Firestore:**

```
users/{uid}                  name, online, lastSeen
users/{uid}/devices/{id}     name, platform, lastActiveAt
chats/{uidA_uidB}            members, lastMessage, updatedAt, lastRead.{uid}
chats/{uidA_uidB}/messages   senderId, text, sentAt
linkRequests/{id}            owner, deviceName, platform, createdAt, expiresAt, payload, approvedBy
```

Id чата — это отсортированная пара uid, поэтому у двух людей ровно один чат, кто бы кого ни отсканировал. Сообщение и `lastMessage` чата пишутся одним batch. Отправка не ждёт сервер: Firestore сразу показывает сообщение локально (`hasPendingWrites` → статус «отправляется»). Правила доступа лежат в [`firestore.rules`](firestore.rules): писать можно только от своего имени, только с серверным временем и только в свои чаты; менять состав чата нельзя.

## Несколько устройств

Аккаунт — это ключ из 25 случайных байт: 8 байт логина и 17 байт секрета. Из него получаются служебная почта и пароль Firebase. Ключ лежит на устройстве, зашифрованный ключом из Android Keystore.

**Вход на новом устройстве:**

```
Новое устройство                          Телефон с аккаунтом
────────────────                          ───────────────────
временный анонимный вход
пара ключей ECDH P-256
linkRequests/{id}: имя и платформа
QR  ghostlink://link/{id}.{publicKey}
                                          сканирует → «Войти на устройстве …?»
                                          ECDH → HKDF-SHA256 → AES-256-GCM
                                          payload = зашифрованный ключ аккаунта
расшифровывает → удаляет заявку
и временный аккаунт → вход по ключу
```

- Публичный ключ берётся только из QR, поэтому подменить его через базу нельзя. id заявки входит в HKDF и в AAD, так что шифротекст нельзя переложить в другую заявку.
- Заявка живёт 2 минуты и подтверждается один раз. Подтвердить её может только настоящий (не анонимный) аккаунт, это проверяют правила.
- Ссылки `ghostlink://link/…` нарочно не открываются из других приложений: код входа можно только отсканировать. Иначе вход можно было бы выманить присланной ссылкой.

**Отзыв доступа.** Без сервера нельзя отключить одно устройство принудительно, поэтому способов два:
- «Удалить устройство» — мягкий: запись пропадает из реестра, и приложение на том устройстве само выходит из аккаунта.
- «Выйти на всех других устройствах» — жёсткий: секрет ключа меняется, Firebase отзывает остальные сессии, а старый ключ восстановления перестаёт работать.

**Старые анонимные аккаунты** при первом запуске новой версии сами получают ключ: к тому же uid привязывается пароль, переписка сохраняется.

Сквозного шифрования сообщений пока нет: Firestore хранит их в открытом виде.

## Запуск

У приложения три флейвора, они ставятся рядом друг с другом:

| Флейвор | Пакет | Бэкенд |
|---|---|---|
| `demo` | `io.github.valerait.ghostlink.demo` | в памяти, с ботом-собеседником |
| `dev` | `io.github.valerait.ghostlink.dev` | Firebase-проект для разработки |
| `prod` | `io.github.valerait.ghostlink` | боевой Firebase-проект |

**Демо-режим:** открыть проект в Android Studio, выбрать вариант `demoDebug` и запустить. Ничего настраивать не нужно. `dev` и `prod` без своего конфига тоже запускаются в демо-режиме.

**С Firebase:**

1. Создать проект в [Firebase Console](https://console.firebase.google.com) и добавить Android-приложение с пакетом `io.github.valerait.ghostlink` (для `dev` — `io.github.valerait.ghostlink.dev`).
2. Положить `google-services.json` в `apps/android/src/prod/` (или `apps/android/src/dev/`). Файлы в `.gitignore`: у каждого свой проект. В CI конфиг `dev` берётся из секрета `GOOGLE_SERVICES_DEV`.
3. В разделе **Authentication → Sign-in method** включить **Email/Password** и **Anonymous**. Других способов входа нет: потерянный аккаунт возвращает только ключ восстановления.
4. Создать базу **Firestore** и вставить содержимое `firestore.rules` во вкладку **Rules**.
5. Только на Blaze: в Firestore → **TTL** добавить политику для коллекции `linkRequests` по полю `expiresAt`, чтобы брошенные заявки удалялись сами. На Spark TTL недоступен, но это не страшно: приложение удаляет свои заявки само, а просроченную заявку правила не дают подтвердить.

Бесплатного тарифа Spark для всего этого достаточно.

**С локальным Firebase Emulator** (ни проект, ни `google-services.json` не нужны):

```bash
npx firebase-tools emulators:start --only auth,firestore --project demo-ghostlink
./gradlew installDemoDebug -Pghostlink.emulatorHost=10.0.2.2
```

`10.0.2.2` — это адрес Mac из Android-эмулятора. Правила из `firestore.rules` эмулятор подхватывает сам, а интерфейс эмулятора открывается на http://localhost:4000.

### iOS

Kotlin-часть живёт в модуле `apps/ios` и собирается в статический фреймворк `GhostlinkKit` поверх тех же `core:*` и `feature:*`, что у Android. Xcode-проект описан в `apps/ios/iosApp/project.yml` и генерируется [XcodeGen](https://github.com/yonaskolb/XcodeGen); сам `.xcodeproj` в git не хранится. Firebase iOS SDK (11.x, под него собран GitLive) Xcode подтягивает через SPM.

```bash
brew install xcodegen
cd apps/ios/iosApp && xcodegen generate && open Ghostlink.xcodeproj
```

Фреймворк собирает Gradle из build phase Xcode, отдельно запускать его не нужно. Без `GoogleService-Info.plist` приложение работает в демо-режиме. Чтобы подключить Firebase, нужно добавить в проект iOS-приложение с bundle id `io.github.valerait.ghostlink` и положить его `GoogleService-Info.plist` в `apps/ios/iosApp/` (файл в `.gitignore`).

## Тесты

```bash
./gradlew testDemoDebugUnitTest jvmTest
```

Покрыты модели (статусы, непрочитанное, онлайн, приглашения), ViewModel чата и онбординга, а также привязка устройств:
- ключ аккаунта и ключ восстановления;
- шифрование: подмена payload, чужой ключ, точка вне кривой, а также test vectors — шифротексты старого Android-кода и iOS расшифровываются общим кодом на JVM и iOS;
- сценарий входа на новом устройстве целиком, истечение кода, вход по ключу восстановления и отказ старого ключа после смены;
- мапперы DTO → domain и перевод исключений Firebase в `GhostlinkError`.

**Репозитории `core:data` против эмулятора** — настоящий Firebase-код (GitLive на JVM) с боевыми `firestore.rules`: вход на новом устройстве через заявку, реестр устройств, чат и группа в реальном времени, отказы правил. В обычный `jvmTest` не входят, в CI идут отдельным job:

```bash
cd firebase && npm ci && cd ..
firebase/node_modules/.bin/firebase emulators:exec --config firebase.json --only auth,firestore --project demo-ghostlink "./gradlew :core:data:jvmEmulatorTest"
```

**Правила Firestore** проверяются на эмуляторе (нужны Node 20+ и Java 21):

```bash
cd firebase && npm ci && npm test
```

На каждую коллекцию есть разрешённые и запрещённые случаи: чужой профиль, чужое устройство, повторное подтверждение заявки, просроченная заявка, сообщение от чужого имени, клиентское время вместо серверного.

**Сквозные сценарии [Maestro](https://maestro.mobile.dev)** в `.maestro/` гоняют demo-сборку в эмуляторе (локаль en-US): регистрация, чат с ботом, друг по ссылке-приглашению, экран входа нового устройства и удаление устройства, выход и вход по ключу восстановления. Подтверждение входа камерой в них не входит: сканер на эмуляторе не работает, а код входа приложение намеренно не принимает из ссылки.

```bash
./gradlew :apps:android:installDemoDebug
maestro test .maestro
```

**Статический анализ:** `./gradlew spotlessCheck detekt` (форматирование — `./gradlew spotlessApply`). **Покрытие:** `./gradlew :apps:android:koverHtmlReportCi`. Всё это и сборка запускаются в CI на каждый PR; зависимости обновляет Renovate, CHANGELOG собирает release-please из заголовков PR (Conventional Commits).

## Дальше

- Push-уведомления (FCM + Cloud Function).
- Фото в сообщениях (Firebase Storage).
- Индикатор «печатает…».
