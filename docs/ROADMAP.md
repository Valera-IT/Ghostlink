# План разработки Ghostlink

Цель — один аккаунт на любом наборе устройств: **Android, iPhone, macOS, Windows**.
Ограничения: только бесплатный тариф Firebase **Spark** (без Cloud Functions), без телефона и почты пользователя.

## Целевая архитектура

```
ghostlink/
├── shared/            Kotlin Multiplatform: модели, репозитории, ViewModel, крипто, Firebase
│   ├── commonMain     вся логика + Compose Multiplatform UI
│   ├── androidMain    Keystore, CameraX + ML Kit, FCM
│   ├── iosMain        Keychain, AVFoundation-сканер, APNs
│   └── desktopMain    Keychain macOS / Credential Manager Windows, трей, уведомления
├── androidApp/        Activity, манифест, google-services.json
├── iosApp/            Xcode-проект (SwiftUI-обёртка над Compose), GoogleService-Info.plist
├── desktopApp/        main(), упаковка .dmg / .msi
└── push-worker/       Cloudflare Worker → FCM HTTP v1 (этап 6)
```

| Слой | Решение |
|---|---|
| UI | Compose Multiplatform (один UI на все 4 платформы) |
| DI | Koin (вместо Hilt — Hilt только для Android) |
| Навигация | Navigation Compose Multiplatform |
| Firebase | GitLive `firebase-kotlin-sdk` (Auth + Firestore) |
| Крипто | `cryptography-kotlin` (ECDH P-256, HKDF, AES-GCM) |
| QR | генерация — общий код; сканирование — только телефоны |
| Хранение ключа аккаунта | Keystore / Keychain / Credential Manager через `expect/actual` |

Mac и Windows — **одна сборка** Compose Desktop (JVM), отличаются только упаковкой и хранилищем секрета.

## Этапы

### Этап 1. Привязка устройств на Android — *в рабочем дереве, не закоммичено*
Скрытый email/password-аккаунт, передача ключа по QR (ECDH → HKDF → AES-GCM), экраны «Устройства» и «Войти на другом устройстве», ключ восстановления, правила Firestore.

**Готово, когда:** два эмулятора против Firebase Emulator — второй входит в аккаунт по QR, видит те же чаты; удаление устройства и «выйти везде» работают; тесты зелёные; коммит.

### Этап 2. Разведка рисков (1–2 дня, до миграции)
Самый большой риск — GitLive на **JVM-десктопе**: там он работает поверх неофициального `firebase-java-sdk`. Минимальный KMP-прототип: вход по паролю + слушатель коллекции Firestore на Android, iOS-симуляторе и JVM против эмулятора.

**Готово, когда:** realtime-слушатель работает на всех трёх таргетах. Если JVM не тянет — запасной путь для десктопа: Firestore REST + короткий polling только в `desktopMain` (интерфейсы репозиториев это позволяют).

### Этап 3. Миграция на Kotlin Multiplatform (Android остаётся рабочим)
1. Структура модулей `shared` / `androidApp`, Gradle-плагины KMP + Compose Multiplatform.
2. Модели, `InviteLink`, `LinkCode`, `AccountKey` → `commonMain` (чистый Kotlin, переносятся почти без изменений).
3. Hilt → Koin; `ViewModel` и `StateFlow` → multiplatform lifecycle-viewmodel.
4. Firebase Android SDK → GitLive; `callbackFlow` заменяются на готовые `Flow` из GitLive.
5. Крипто `java.security` → `cryptography-kotlin`; тест совместимости: шифротекст с Android расшифровывается на JVM и наоборот.
6. Экраны → `commonMain`; ресурсы `strings.xml` → Compose Resources.
7. `expect/actual`: хранилище ключа, сканер QR, присутствие (lifecycle), открытие deep link.
8. Демо-режим (`InMemoryBackend`) — в `commonMain`, работает на всех платформах.

**Готово, когда:** Android-приложение работает как до миграции, все тесты переехали в `commonTest` и проходят.

### Этап 4. Десктоп — macOS и Windows
- `desktopApp`: окно, адаптивная раскладка (список чатов слева, чат справа при ширине > 840 dp).
- Вход только через привязку: десктоп показывает QR, телефон сканирует. Своего сканера нет.
- Добавление друга с десктопа: показать свой QR или вставить ссылку `ghostlink://add/…`.
- Секрет аккаунта: macOS Keychain, Windows Credential Manager (`java-keyring`).
- Трей, системные уведомления о новых сообщениях, пока приложение запущено.
- Регистрация схемы `ghostlink://` в ОС.
- Упаковка: `.dmg` (macOS, arm64 + x64) и `.msi` (Windows) через `compose.desktop.nativeDistributions`.

**Готово, когда:** Mac и Windows-машина (или VM) привязываются к телефону и переписываются в реальном времени.

### Этап 5. iOS
- `iosApp` на Xcode, `ComposeUIViewController`, Firebase через CocoaPods/SPM.
- Сканер QR на AVFoundation (`actual`), разрешение камеры, Keychain.
- Universal link / URL-схема `ghostlink://`.
- Безопасные отступы, клавиатура, жесты «назад» — проверка на симуляторе и реальном iPhone.

**Готово, когда:** iPhone в одном аккаунте с Android и десктопом; добавление друга и привязка устройства по камере работают.

### Этап 6. Push-уведомления (Cloudflare Worker, бесплатно)
- Устройства регистрируют FCM-токены в `users/{uid}/devices/{id}`.
- Клиент после отправки сообщения дёргает Worker; Worker проверяет Firebase ID-токен, читает токены получателя и шлёт FCM HTTP v1. Ключ сервис-аккаунта хранится только в секретах Worker.
- Android — FCM напрямую; iOS — FCM поверх APNs (нужен ключ APNs); десктоп — без push, уведомления из трея.
- Лимит частоты на Worker, чтобы его нельзя было использовать для спама.

### Этап 7. Релиз и CI
- GitHub Actions: тесты `shared` + Android-сборка (ubuntu), `.dmg` (macos), `.msi` (windows), iOS-сборка (macos).
- Firebase-проект: включить Anonymous + Email/Password, задеплоить `firestore.rules`.
- Дистрибуция: APK/AAB → GitHub Releases или Google Play; iOS → TestFlight; десктоп → GitHub Releases.
- README: скриншоты всех платформ, схема архитектуры, описание протокола привязки.

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
