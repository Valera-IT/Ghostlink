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
                           ghostlink.detekt, ghostlink.spotless
core/
  model                    доменные модели: чистый Kotlin, без зависимостей
  domain                   интерфейсы репозиториев + use case
  crypto                   AccountKey, RecoveryCode, LinkCrypto (cryptography-kotlin)
  data                     реализации репозиториев, мапперы DTO ↔ domain
  firebase                 обёртки GitLive, DTO Firestore, конфиг окружений
  platform                 expect/actual: SecureStore, DeviceInfo, AppLifecycle, DeepLinks, Clock
  designsystem             тема, цвета, типографика, базовые компоненты
  ui                       общие экранные куски: Avatar, форматирование времени, QR-картинка
  testing                  фейки репозиториев (бывший InMemoryBackend), тестовые правила, фикстуры
  demo                     демо-бэкенд с ботом для запуска без Firebase
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
  ios                      Xcode-проект + shared-фреймворк (umbrella над core и feature)
push-worker/               Cloudflare Worker (этап 6), TypeScript
firebase/                  firestore.rules, firestore.indexes.json, тесты правил
```

**Правила зависимостей** (проверяются в CI, задача `checkModuleGraph`):

```
apps/*      → feature/*, core/*
feature/*   → core:domain, core:model, core:designsystem, core:ui   (не feature/*, не core:data)
core:data   → core:domain, core:model, core:firebase, core:crypto, core:platform
core:domain → core:model
core:model  → ничего
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
| `Device` | `id`, `name`, `platform: Platform`, `lastActiveAt?`, `pushToken?` | `pushToken` с этапа 6 |
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

Исключения Firebase переводит в `GhostlinkError` только `core:data`: выше этого слоя типов Firebase нет.

### Use case (`core:domain`)

Use case заводится, только когда сценарий затрагивает несколько репозиториев или содержит правила. Простое чтение ViewModel берёт из репозитория напрямую.

| Use case | Что делает |
|---|---|
| `CreateAccountUseCase` | ключ → аккаунт → профиль → регистрация устройства |
| `RequestDeviceLinkUseCase` | анонимный вход → ECDH-пара → заявка → `Flow<LinkState>` до входа по ключу |
| `ApproveDeviceLinkUseCase` | разбор QR → проверка заявки → шифрование ключа → запись payload |
| `RestoreWithRecoveryCodeUseCase` | разбор кода → вход → регистрация устройства |
| `SignOutEverywhereUseCase` | смена секрета → очистка реестра → новый код восстановления |
| `AddFriendUseCase` | разбор QR или ссылки → профиль → `startChat` |
| `SendMessageUseCase` | проверка текста → отправка (позже → вызов push-worker) |
| `ObserveChatListUseCase` | чаты + профили собеседников + присутствие → `List<ChatSummary>` |

### Data (`core:firebase`)

DTO повторяют документы Firestore один в один, имеют аннотацию `@Serializable` и в модули выше `core:data` не попадают.

```
UserDto          name, online, lastSeen
DeviceDto        name, platform, lastActiveAt, pushToken?
ChatDto          members, lastMessage{text, senderId, sentAt}, updatedAt, lastRead{uid: ts}
MessageDto       senderId, text, sentAt
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

UI-модели (`ContactUi`, `MessageItem`, `ChatSummaryUi`) отличаются от domain-моделей: в них уже готовые строки (время, «был(а) в сети в 14:05») и группировка по дням.

## Платформенный слой (`core:platform`)

| `expect` | Android | iOS | Desktop (JVM) |
|---|---|---|---|
| `SecureStore` — ключ аккаунта | Keystore + EncryptedFile | Keychain | macOS Keychain / Windows Credential Manager (`java-keyring`) |
| `DeviceInfo` — имя, платформа | `Build.MODEL` | `UIDevice` | `hostname`, `os.name` |
| `AppLifecycle` — foreground для присутствия | `ProcessLifecycleOwner` | `UIApplication` notifications | фокус окна |
| `QrScanner` | CameraX + ML Kit | AVFoundation | нет (только показ QR) |
| `DeepLinks` | intent-filter | URL scheme | регистрация схемы в ОС |
| `Clock` | `kotlin.time.Clock.System` (в тестах фейк) | ← | ← |

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
- **CI (GitHub Actions):** detekt и ktlint (spotless), unit-тесты, тесты правил на эмуляторе, сборка Android, отчёт покрытия Kover. На этапе 7 добавляются сборки `.dmg`, `.msi` и iOS. Зависимости обновляет Renovate.
- **Версии:** SemVer, теги `v0.x.y`, релиз собирается в GitHub Actions по тегу.
- **Документация:** README (что это и как запустить), этот файл (как устроено), ADR (почему так) и ROADMAP (что дальше).
- **Ошибки в проде:** Crashlytics (бесплатно на Spark) на Android и iOS, на десктопе логи в файл. Analytics не подключаем: это часть идеи приложения.
