# Архитектура Ghostlink

Это целевая архитектура: к ней проект приходит на этапе 3 (см. [ROADMAP.md](ROADMAP.md)). Сейчас всё лежит в одном модуле `app`, но слои уже разделены по пакетам, поэтому миграция — это в основном перенос файлов, а не переписывание.

Решения, которые трудно отменить, записаны в [adr/](adr/).

## Принципы

1. **Clean Architecture, три слоя:** `presentation → domain ← data`. Domain не знает ни про Firebase, ни про Compose, ни про платформу.
2. **Модули по фичам и по слоям,** как в Now in Android. Фича не зависит от другой фичи, а общий код лежит в `core:*`.
3. **Kotlin Multiplatform.** Всё, что можно, лежит в `commonMain`. Платформенный код ограничен `expect/actual` в `core:platform` и тонкими app-модулями.
4. **UDF/MVI на экране:** `UiState` идёт вниз, `Action` вверх, разовые `Effect` (навигация, тосты) уходят отдельным каналом.
5. **Тестируемость без моков.** У каждого репозитория есть фейк в `core:testing`, а ViewModel и use case проверяются на фейках.
6. **Бэкенд заменяемый.** Firebase спрятан за интерфейсами `domain`, поэтому, если GitLive не потянет JVM, на десктопе его можно заменить на Firestore REST без изменений выше слоя `data`.

## Модули

```
build-logic/convention     convention-плагины Gradle: ghostlink.kmp.library, ghostlink.kmp.compose,
                           ghostlink.feature, ghostlink.android.app, ghostlink.android.compose,
                           ghostlink.detekt, ghostlink.spotless, ghostlink.module-graph
core/
  model                    доменные модели: чистый Kotlin, без зависимостей
  domain                   интерфейсы репозиториев + use case
  crypto                   LinkCrypto: шифрование привязки устройства на cryptography-kotlin
  data                     реализации репозиториев, мапперы DTO ↔ domain
  firebase                 обёртки GitLive, DTO Firestore, конфиг окружений
  platform                 платформенный слой: SecureStore, ThisDevice, AppLifecycle, DeepLinks, QrScanner
  designsystem             тема, цвета, типографика, базовые компоненты
  ui                       общие экранные куски: Avatar, форматирование времени, QR-картинка
  testing                  фейки репозиториев (FakeBackend, бывший InMemoryBackend), FakeLinkCipher, TestClock
  demo                     DemoBackend: FakeBackend со стартовыми чатами и ботом, для запуска без Firebase
feature/
  onboarding               имя → аккаунт; «уже есть аккаунт» → link или recovery
  chats                    список чатов
  chat                     переписка
  friends                  мой QR, сканер, приглашение по ссылке (бывшие connect, mycode, scan)
  devices                  список устройств, удаление, «выйти на всех»
  link                     вход на новом устройстве: показать QR / подтвердить
  recovery                 ключ восстановления: показать / ввести
  settings                 профиль, ключ восстановления, о приложении
apps/
  android                  Activity, манифест, google-services.json, Koin start
  desktop                  main(), окно, трей, упаковка .dmg / .msi
  ios                      Kotlin-фреймворк GhostlinkKit (umbrella над core и feature) + Xcode-проект в iosApp/ (XcodeGen)
worker/                    Cloudflare Worker (этап 6): подписанные ссылки B2 на файлы чатов, позже push; JavaScript без зависимостей
firebase/                  firestore.rules, firestore.indexes.json, тесты правил
```

**Правила зависимостей.** Их проверяет сама сборка: плагин `ghostlink.module-graph` в корневом `build.gradle.kts` роняет любой запуск Gradle (и CI) на зависимости не по таблице или от модуля, которого в ней нет. Таблица в коде — `ModuleGraph` в `build-logic`.

```
apps/*      → feature/*, core/*
feature/*   → core:domain, core:model, core:platform, core:designsystem, core:ui   (не feature/*, не core:data)
core:data   → core:domain, core:model, core:firebase, core:crypto, core:platform
core:ui     → core:designsystem, core:model, core:platform
core:platform → core:model
core:domain → core:model
core:firebase → только GitLive (android, jvm, ios)
core:designsystem → ничего из проекта
core:crypto → core:domain
core:testing → core:domain
core:demo   → core:testing
core:model  → ничего
тесты любого модуля → дополнительно core:testing
```

Фичи не видят `core:data`: реализации подключаются только в app-модулях через Koin. Поэтому фичу нельзя случайно связать с Firebase.

**Навигация.** Каждая фича отдаёт `NavGraphBuilder.xxxScreen(...)` и типобезопасный маршрут `@Serializable data object/class XxxRoute`. Граф собирают app-модули. Переход в другую фичу фича делает через переданную лямбду (`onOpenChat: (ChatId) -> Unit`) и про чужие маршруты не знает.

## Модели по слоям

На каждом слое свои модели, а превращения между ними — явные мапперы. Firestore-документы не доходят до UI, а UI-состояние не попадает в domain.

### Domain (`core:model`)

Идентификаторы — `value class`, чтобы `UserId` нельзя было передать вместо `ChatId`. Время — `kotlin.time.Instant`, интервалы — `kotlin.time.Duration` вместо `Long`.

| Модель | Поля | Заметки |
|---|---|---|
| `UserId`, `ChatId`, `DeviceId`, `MessageId`, `LinkRequestId` | `value: String` | `ChatId.of(a, b)` — отсортированная пара |
| `Account` | `id`, `kind: Anonymous \| Keyed` | `Anonymous` — временный аккаунт нового устройства |
| `AccountKey` | `login: ByteArray(8)`, `secret: ByteArray(17)` | → служебная почта и пароль; `rotateSecret()` |
| `RecoveryCode` | `value: String` (40 символов base32) | `parse()` с проверкой формата, `toAccountKey()` |
| `Profile` | `id`, `name: DisplayName` | `DisplayName` проверяет длину 1–40 |
| `Presence` | `Online` \| `LastSeen(at)` \| `Unknown` | считается из `online` + `lastSeen` + `Clock` |
| `Contact` | `profile`, `presence` | то, что видно о собеседнике |
| `Chat` | `id`, `members`, `lastMessage?`, `updatedAt?`, `lastRead: Map<UserId, Instant>` | `partnerId()`, `isUnreadFor()` |
| `Message` | `id`, `chatId`, `senderId`, `text: MessageText`, `sentAt?`, `pending` | `status(partnerLastRead)` → `MessageStatus` |
| `MessageStatus` | `Sending`, `Sent`, `Read` | позже `Failed` |
| `Device` | `id`, `name`, `platform: Platform`, `lastActiveAt?`, `pushToken?` | `pushToken` — токен FCM, null у платформ без push |
| `Platform` | `Android`, `Ios`, `MacOs`, `Windows`, `Unknown` | enum вместо строки |
| `LinkRequest` | `id`, `deviceName`, `platform`, `expiresAt`, `payload?` | `isExpired(now)` |
| `LinkCode` | `requestId`, `publicKey` | QR `ghostlink://link/…` |
| `InviteLink` | `userId` | `ghostlink://add/…` |

### Ошибки (`core:model`)

Ошибки, которые пользователь может исправить, описаны sealed-иерархией. Repository и use case возвращают их в `Result`-подобном типе или бросают, а ViewModel переводит в текст:

```kotlin
sealed class GhostlinkError(message: String?, cause: Throwable?) : Exception(message, cause) {
    class Offline(cause: Throwable?) : GhostlinkError(…)
    class InvalidAccountKey(cause: Throwable?) : GhostlinkError(…)
    class LinkExpired : GhostlinkError(…)
    class LinkAlreadyApproved : GhostlinkError(…)
    class PermissionDenied(cause: Throwable?) : GhostlinkError(…)
    class Unexpected(cause: Throwable) : GhostlinkError(…)
}
```

Это исключения, а не `data object`: так их можно бросать из `suspend`-функций репозиториев и не терять исходную причину.

Исключения Firebase переводит в `GhostlinkError` только `core:data`: выше этого слоя типов Firebase нет. Приложение получает репозитории из `FirebaseBackend` (`core:data`) и типов GitLive не видит.

### Use case (`core:domain`)

Use case заводится, только когда сценарий затрагивает несколько репозиториев или содержит правила. Простое чтение ViewModel берёт из репозитория напрямую.

| Use case | Что делает |
|---|---|
| `CreateAccountUseCase` | имя → аккаунт с ключом (или временный аккаунт становится настоящим) → профиль; устройство записывает `DeviceRegistry` |
| `RenameProfileUseCase` | то же правило имени (1–40 символов без пробелов по краям) → профиль |
| `RequestDeviceLinkUseCase` | анонимный вход → пара ключей → заявка → `Flow<LinkState>` до входа по ключу; заявка удаляется при любом исходе |
| `ApproveDeviceLinkUseCase` | `load()`: заявка ещё действует (иначе `LinkExpired` / `LinkAlreadyApproved`) → шифрование ключа → запись payload |
| `RestoreWithRecoveryCodeUseCase` | разбор кода (`MalformedRecoveryCode`) → вход (`InvalidAccountKey`) |
| `GetRecoveryCodeUseCase` | ключ аккаунта (выдаёт, если на устройстве его нет) → код восстановления |
| `SignOutUseCase` | «не в сети» → запись устройства из реестра → выход |
| `SignOutEverywhereUseCase` | смена секрета → очистка реестра, кроме этого устройства → новый код восстановления |
| `AddFriendUseCase` | не сам себя → `startChat` |
| `ObserveChatListUseCase` | чаты + профили + личные настройки → `List<ChatSummary>`, закреплённые сверху, исчезнувшие превью гаснут в срок |
| `ObserveChatMessagesUseCase` | сообщения без удалённых вместе с чатом и исчезнувших; исчезнувшие стирает из базы |
| `SendMessageUseCase` | проверка текста → ответ только на подтверждённое → TTL из таймера чата; push после записи шлёт `core:data` (`WorkerPush`) |
| `CheckNewMessagesUseCase` | фоновое обновление iOS: чаты с сервера → непрочитанные, не без звука, с чужим сообщением новее прошлой проверки → `NewMessageNotice` |
| `EditMessageUseCase`, `DeleteMessageUseCase` | только свои сообщения; превью в списке чатов следует за последним неудалённым |
| `LeaveGroupUseCase` | админ передаёт права следующему участнику → чат пропадает из своего списка |
| `PhotoSender` | фото и превью → `MediaStore` → сообщение с `attachment`; пока грузится или не загрузилось, фото видно в чате, неудачное можно повторить или убрать |

Шифрование при привязке спрятано за портом `LinkCipher` в `core:domain`: use case не знают, на чём оно сделано.

Файлы чатов так же спрятаны за портом `MediaStore` (загрузить, скачать): реализация в `core:data` берёт у Worker подписанные ссылки B2 и кэширует скачанное на диске, в демо-режиме и тестах — `FakeMediaStore` в памяти.

### Data (`core:firebase`)

DTO повторяют документы Firestore один в один, имеют аннотацию `@Serializable` и в модули выше `core:data` не попадают.

```
UserDto          name, online, lastSeen
DeviceDto        name, platform, lastActiveAt, pushToken?
ChatDto          members, lastMessage{text, senderId, sentAt}, updatedAt, lastRead{uid: ts}
MessageDto       senderId, text, sentAt, attachment?{type, key, thumbKey, size, width, height}
LinkRequestDto   owner, deviceName, platform, createdAt, expiresAt, payload?, approvedBy?
```

Схема хранится в двух местах: в DTO и в `firestore.rules`. Если меняется одно, нужно поменять и второе, иначе падают тесты правил, потому что они пишут документы теми же DTO.

### Presentation (`feature/*`)

На каждом экране одна и та же тройка:

```kotlin
data class ChatUiState(            // всё, что нужно для отрисовки, без Firebase и Flow внутри
    val partner: ContactUi?,
    val messages: List<MessageItem>,   // MessageItem = DayDivider | Bubble(text, time, status, mine)
    val input: String,
    val isLoading: Boolean,
)
sealed interface ChatAction { data class InputChanged(val text: String); data object Send; ... }
sealed interface ChatEffect { data object NavigateBack; data class ShowError(val error: GhostlinkError) }
```

Экран шлёт действия в `viewModel.onAction(...)`, а эффекты забирает через `CollectEffects(viewModel.effects)` из `core:ui`.

Навигацию каждая фича описывает сама: типобезопасные маршруты `@Serializable`, расширение `NavGraphBuilder.chatScreens(...)` и `NavController.navigateToChat(...)`. Переход в другую фичу — лямбда-параметр (`onOpenChat`, `onScan`), которую связывает корневой граф в приложении. Так фичи не видят друг друга.

UI-модели (`ContactUi`, `MessageItem`, `ChatSummaryUi`) отличаются от domain-моделей: в них уже готовые строки (время, «был(а) в сети в 14:05») и группировка по дням.

## Платформенный слой (`core:platform`)

Выше этого модуля код платформенных API не видит. Сервисы — интерфейсы в `commonMain` (их легко подменить в тестах), реализации лежат в `androidMain`/`iosMain`/`jvmMain` и собираются в `expect val platformModule` для Koin. Composable-части (`QrScanner`, буфер обмена, «Поделиться») — `expect fun`.

| | Android | iOS | Desktop (JVM) |
|---|---|---|---|
| `SecureStore` — ключ аккаунта | Keystore (AES-GCM) + SharedPreferences | Keychain (4.2), пока в памяти | `java-keyring` (5.3), пока в памяти |
| `ThisDevice` — id, имя, платформа | `Settings.Global.DEVICE_NAME` / `Build.MODEL` | `UIDevice` | `hostname`, `os.name` |
| `PushTokens` — токен для push | FCM (в `apps:android`, кроме demo) | нет (APNs нужен Apple Developer) | нет |
| `AppLifecycle` — foreground для присутствия | `ProcessLifecycleOwner` | `UIApplication` notifications | фокус окна (этап 5), пока всегда на экране |
| `QrScanner` | CameraX + ML Kit | AVFoundation (4.2) | нет (только показ QR) |
| `DeepLinks` — приглашения `ghostlink://` | intent-filter → `open()` | URL scheme (4.3) | регистрация схемы в ОС (5.4) |
| `logWarning`, буфер обмена, «Поделиться» | Logcat, `ClipboardManager`, `ACTION_SEND` | NSLog, `UIPasteboard` | `java.util.logging`, AWT |

Время — `kotlin.time.Clock` (в тестах `TestClock`). Статус «в сети» (`PresenceTracker`) и реестр устройств (`DeviceRegistry`) — общий код в `core:data` поверх `AppLifecycle`.

## Окружения и конфигурация

| Окружение | Бэкенд | Где |
|---|---|---|
| `demo` | `core:demo` в памяти | запуск без конфига, скриншоты, UI-тесты |
| `local` | Firebase Emulator | разработка, интеграционные тесты, CI |
| `dev` | Firebase-проект `ghostlink-dev` (Spark) | ручная проверка на устройствах |
| `prod` | Firebase-проект `ghostlink-7ffc8` (Spark) | релизы |

Каждый проект Spark бесплатный, поэтому dev и prod разделены по-настоящему. На Android окружение задаётся build-флейвором. Конфиги Firebase в git не лежат: в CI они приходят из секретов.

## Тестирование

| Уровень | Что проверяет | Инструменты | Где |
|---|---|---|---|
| Unit | модели, крипто, use case, ViewModel на фейках | `kotlin.test`, coroutines-test, Turbine | `commonTest`, каждый PR |
| Совместимость крипто | шифротекст с одной платформы расшифровывается на другой | общие test vectors в `commonTest` | Android, JVM, iOS |
| Правила Firestore | каждое `allow` и каждый отказ | `@firebase/rules-unit-testing` + эмулятор | `firebase/test`, каждый PR |
| Интеграция репозиториев | реализации `core:data` против эмулятора | JVM-тесты + эмулятор | каждый PR |
| UI | экраны на фейковом состоянии | Compose UI test, скриншоты Roborazzi | Android/JVM |
| E2E | сценарии: регистрация, привязка, друг, переписка на двух устройствах | Maestro (Android + iOS) | ночная сборка и перед релизом |

Тесты правил лежат в `firebase/test` (`node --test` + `@firebase/rules-unit-testing`) и запускаются в CI отдельным job: `cd firebase && npm test`.

## Процесс разработки

- **Задачи:** у каждого этапа есть GitHub Milestone, у каждого пункта этапа — Issue с критериями «готово, когда». Задачи ведутся на доске GitHub Projects в колонках *Todo / In progress / Review / Done*.
- **Ветки:** trunk-based. Короткие ветки `feat/3.2-core-model`, `fix/…`, `chore/…`; один пункт плана — один PR. Слияние через squash, в `main` нельзя пушить напрямую.
- **Коммиты:** [Conventional Commits](https://www.conventionalcommits.org) (`feat(link): …`, `fix(chat): …`), из них собирается CHANGELOG.
- **PR:** шаблон с полями «что / зачем / как проверено / скриншоты». CI должен быть зелёным. Каждый PR проходит самопроверку по чеклисту и code review.
- **CI (GitHub Actions):** detekt и ktlint (spotless), unit-тесты, тесты правил на эмуляторе, сборка Android, отчёт покрытия Kover. На этапе 9 добавляются сборки `.dmg`, `.msi` и iOS. Зависимости обновляет Renovate.
- **Версии:** SemVer, теги `v0.x.y`, релиз собирается в GitHub Actions по тегу.
- **Документация:** README (что это и как запустить), этот файл (как устроено), ADR (почему так) и ROADMAP (что дальше).
- **Ошибки в проде:** Crashlytics (бесплатно на Spark) на Android и iOS, на десктопе логи в файл. Analytics не подключаем: это часть идеи приложения.
