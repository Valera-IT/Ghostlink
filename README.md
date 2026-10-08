# Ghostlink 👻

Мессенджер без номера телефона и почты. Друзей добавляют только при личной встрече: один показывает QR-код, другой сканирует, и у обоих сразу появляется общий чат.

## Возможности

- **Регистрация в один шаг.** Только имя, под капотом Firebase Anonymous Auth.
- **Друзья по QR.** Свой код (ZXing) и сканер на CameraX + ML Kit. Можно отправить ссылку-приглашение `ghostlink://add/<id>`, она открывается как deep link.
- **Чаты один на один в реальном времени** на Firestore:
  - статусы ⏱ отправляется → ✓ на сервере → ✓✓ прочитано;
  - разделители по дням;
  - непрочитанные в списке чатов.
- **Присутствие:** «в сети» и «был(а) в сети в 14:05» через heartbeat, с истечением статуса, если приложение убили.
- **Восстановление аккаунта.** Анонимный аккаунт можно привязать к Google (Credential Manager): uid и переписка сохраняются, а на новом телефоне достаточно войти через Google.
- **Демо-режим.** Без `google-services.json` приложение работает на бэкенде в памяти с ботом-собеседником, поэтому проект можно склонировать и сразу запустить.

## Стек

Kotlin · Jetpack Compose · Material 3 · MVVM (ViewModel + StateFlow) · Hilt · Coroutines/Flow · Navigation Compose (типобезопасные маршруты) · Firebase Auth + Firestore · CameraX · ML Kit Barcode · ZXing · Credential Manager · JUnit + kotlinx-coroutines-test

## Архитектура

```
ui/            Compose-экраны + ViewModel на каждый экран (UDF: state вниз, события вверх)
data/model     чистые модели и логика: статусы сообщений, непрочитанное, id чатов, приглашения
data/repository  интерфейсы AuthRepository / UserRepository / ChatRepository
data/firebase  реализация на Firebase
data/demo      InMemoryBackend — демо-режим и фейк для тестов
di/            Hilt: выбирает Firebase или демо в зависимости от наличия конфига
```

UI зависит только от интерфейсов репозиториев. Поэтому ViewModel тестируются на `InMemoryBackend` без моков и эмулятора, а бэкенд можно заменить, не трогая экраны.

**Модель данных Firestore:**

```
users/{uid}                  name, online, lastSeen
chats/{uidA_uidB}            members, lastMessage, updatedAt, lastRead.{uid}
chats/{uidA_uidB}/messages   senderId, text, sentAt
```

Id чата — это отсортированная пара uid, поэтому у двух людей ровно один чат, кто бы кого ни отсканировал. Сообщение и `lastMessage` чата пишутся одним batch. Отправка не ждёт сервер: Firestore сразу показывает сообщение локально (`hasPendingWrites` → статус «отправляется»). Правила доступа лежат в [`firestore.rules`](firestore.rules): писать можно только от своего имени, только с серверным временем и только в свои чаты; менять состав чата нельзя.

## Запуск

**Демо-режим:** открыть проект в Android Studio и запустить. Ничего настраивать не нужно.

**С Firebase:**

1. Создать проект в [Firebase Console](https://console.firebase.google.com) и добавить Android-приложение с пакетом `io.github.valerait.ghostlink`.
2. Положить `google-services.json` в `app/`. Файл в `.gitignore`: у каждого свой проект.
3. В разделе **Authentication → Sign-in method** включить **Anonymous** и **Google**. Для Google нужно добавить SHA-1 debug-ключа: его выводит `./gradlew signingReport`.
4. Создать базу **Firestore** и вставить содержимое `firestore.rules` во вкладку **Rules**.

Бесплатного тарифа Spark для всего этого достаточно.

## Тесты

```bash
./gradlew testDebugUnitTest
```

Покрыты модели (статусы, непрочитанное, онлайн, приглашения) и ViewModel чата и онбординга: отправка, прочтение только при видимом экране, разделители дней, валидация имени.

## Дальше

- Push-уведомления (FCM + Cloud Function).
- Перенос аккаунта на новый телефон сканированием QR со старого.
- Фото в сообщениях (Firebase Storage).
- Индикатор «печатает…».
