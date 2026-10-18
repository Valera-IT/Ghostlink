# План разработки Ghostlink

Цель — один аккаунт на любом наборе устройств: **Android, iPhone, macOS, Windows**.
Ограничения: только бесплатный тариф Firebase **Spark** (без Cloud Functions), без телефона и почты пользователя.

Как устроен проект — в [ARCHITECTURE.md](ARCHITECTURE.md), почему так — в [adr/](adr/).

**Как читать план.** Этап — это GitHub Milestone, пункт `N.M` — Issue и один PR (ветка `feat/N.M-…`). У каждого этапа есть критерий «готово, когда»: этап закрыт, только когда он выполнен и CI зелёный.

## Этап 1. Привязка устройств на Android — ✅ готово

Скрытый аккаунт, привязка по QR, экран «Устройства», ключ восстановления, миграция анонимных аккаунтов (`4ccd305`). Проверено на Firebase Emulator и на настоящем проекте `ghostlink-7ffc8`: два устройства привязываются, удаление устройства выкидывает второе, переписка идёт в реальном времени.

Хвосты переходят в этап 2: подтверждение входа камерой реального телефона, бейдж непрочитанного, вход через Google.

## Этап 2. Инженерный фундамент и разведка

Сначала делаем из проекта «настоящий» репозиторий, пока он маленький. Потом проверяем главный риск миграции.

**2a. Фундамент (на текущем Android-модуле)**
1. GitHub-репозиторий, защита `main`, шаблоны Issue и PR, Milestones 2–7, доска Projects.
2. CI: сборка, unit-тесты, Android lint на каждый PR.
3. Статический анализ: spotless + ktlint, detekt с baseline; обязательны в CI.
4. Тесты правил Firestore (`firebase/test`, `@firebase/rules-unit-testing`) на эмуляторе в CI. Они заменяют ручной `approve.js`.
5. Окружения: второй Spark-проект `ghostlink-dev`, флейворы `demo` / `dev` / `prod`, конфиги из секретов CI.
6. Renovate, Kover-отчёт покрытия, Conventional Commits + CHANGELOG.
7. Хвосты этапа 1: подтверждение входа камерой реального телефона, проверка бейджа непрочитанного, решение по Google-входу (включить с SHA-1 или убрать).

**2b. Разведка KMP (1–2 дня, отдельная ветка, в main не сливается)**
8. Минимальный KMP-прототип: вход по паролю и слушатель коллекции Firestore на Android, iOS-симуляторе и JVM против эмулятора.
9. Тот же прототип: `cryptography-kotlin` ECDH/HKDF/AES-GCM, шифротекст от текущего Android-кода расшифровывается на JVM и iOS.
10. Итог — ADR 0006: GitLive на JVM подходит, или десктоп уходит на REST + polling.

**Готово, когда:** любой PR в `main` проходит lint, тесты и тесты правил; есть dev-проект; ADR 0006 принят.

## Этап 3. Модули и Kotlin Multiplatform (Android остаётся рабочим)

Порядок выбран так, чтобы после каждого PR приложение собиралось и работало. Сначала разделяем на модули внутри Android, потом переводим модули на KMP.

1. `build-logic` с convention-плагинами, обновление Kotlin, AGP и Compose до актуальных версий.
2. `core:model`: перенос моделей; `value class` для id, `Instant` вместо `Long`, `Platform` enum, `GhostlinkError`.
3. `core:domain`: интерфейсы репозиториев и use case из ARCHITECTURE.md. ViewModel переходят на use case.
4. `core:testing` (фейки из `InMemoryBackend`) и `core:demo` (бот). Тесты ViewModel работают на фейках.
5. `core:crypto` на `cryptography-kotlin` + общие test vectors; старый `java.security`-код удаляется.
6. `core:firebase` + `core:data`: DTO, мапперы, реализации на GitLive, перевод ошибок в `GhostlinkError`, интеграционные тесты против эмулятора.
7. Hilt → Koin, `KoinModulesTest`.
8. `core:designsystem`, `core:ui`; ресурсы → Compose Resources.
9. `feature:*`: экраны по модулям, UiState/Action/Effect, навигация через `NavGraphBuilder`-расширения.
10. `core:platform`: `expect/actual` для `SecureStore`, `DeviceInfo`, `AppLifecycle`, `QrScanner`, `DeepLinks`.
11. Проверка графа модулей в CI; `app` → `apps:android`.

**Готово, когда:** Android работает как до миграции (прогон Maestro-сценариев: регистрация, привязка, друг, чат); все тесты в `commonTest`; `feature:*` не зависят от `core:data`.

## Этап 4. Десктоп — macOS и Windows

1. `apps:desktop`: окно, адаптивная раскладка (список слева, чат справа при ширине > 840 dp).
2. Вход только через привязку: десктоп показывает QR, телефон сканирует.
3. `SecureStore` на `java-keyring` (macOS Keychain, Windows Credential Manager).
4. Друзья: показать свой QR или вставить ссылку `ghostlink://add/…`; регистрация схемы `ghostlink://` в ОС.
5. Трей и системные уведомления, пока приложение запущено.
6. Упаковка `.dmg` (arm64 + x64) и `.msi` через `nativeDistributions`, сборка в CI.

**Готово, когда:** Mac и Windows (или VM) привязываются к телефону и переписываются в реальном времени.

## Этап 5. iOS

1. `apps:ios`: Xcode-проект, `ComposeUIViewController`, Firebase через SPM, shared-фреймворк.
2. `QrScanner` на AVFoundation, разрешение камеры; `SecureStore` на Keychain.
3. URL-схема `ghostlink://`, безопасные отступы, клавиатура, жест «назад».
4. Maestro-сценарии на iOS-симуляторе в CI.

**Готово, когда:** iPhone в одном аккаунте с Android и десктопом; камера добавляет друга и подтверждает вход.

## Этап 6. Push-уведомления (Cloudflare Worker, бесплатно)

1. `DeviceDto.pushToken` + правила + тесты правил.
2. `push-worker`: проверяет Firebase ID-токен, читает токены получателя, шлёт FCM HTTP v1. Ключ сервис-аккаунта лежит только в секретах Worker. Лимит частоты против спама.
3. `SendMessageUseCase` вызывает Worker; ошибка Worker не мешает отправке сообщения.
4. Android — FCM; iOS — FCM поверх APNs (нужен ключ APNs); десктоп — без push.

**Готово, когда:** сообщение будит закрытое приложение на Android (и на iOS, если есть Apple Developer).

## Этап 7. Релиз

1. CI по тегу `v*`: AAB/APK, `.dmg`, `.msi`, iOS-сборка → GitHub Releases.
2. Crashlytics на Android и iOS, логи в файл на десктопе.
3. Дистрибуция: GitHub Releases, по желанию Google Play и TestFlight.
4. README: скриншоты всех платформ, схема архитектуры, протокол привязки, видео.

## Что понадобится вне кода

| Что | Зачем | Цена |
|---|---|---|
| Firebase-проект (Spark) | бэкенд | бесплатно |
| Apple Developer Program | iPhone вне симулятора дольше 7 дней, TestFlight, APNs для push, нотаризация `.dmg` | $99/год |
| Cloudflare аккаунт | push-worker | бесплатно |
| Windows-машина или VM | проверка `.msi` | — |
| Сертификат подписи Windows | убрать предупреждение SmartScreen | необязательно |
| Google Play Console | публикация в Play | $25 один раз, необязательно |

Без Apple Developer можно сделать всё, кроме push на iPhone, TestFlight и нотаризации — для портфолио достаточно симулятора и видео.

## Сознательные ограничения
- Нет сквозного шифрования (E2E) — возможный этап после 7.
- Отзыв отдельного устройства мягкий; жёсткий — только «выйти на всех устройствах» (смена пароля).
- На десктопе нет push, только уведомления запущенного приложения.
