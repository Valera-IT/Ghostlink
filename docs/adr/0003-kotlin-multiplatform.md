# 0003. Kotlin Multiplatform + Compose Multiplatform

**Статус:** принято, риск проверяется на этапе 2

**Контекст.** Нужны Android, iOS, macOS и Windows, а разработчик один. Сейчас код написан на Kotlin и Compose.

**Решение.** Логика, ViewModel и UI лежат в `commonMain` (Compose Multiplatform). Firebase подключается через GitLive `firebase-kotlin-sdk`, крипто — через `cryptography-kotlin`. Mac и Windows — одна JVM-сборка.

**Альтернативы.** Flutter означает переписывать всё. Нативный SwiftUI вдвое увеличивает объём UI-кода. Для KMP без общего UI тоже пришлось бы писать UI трижды.

**Последствия.** Главный риск — GitLive на JVM работает поверх неофициального `firebase-java-sdk`. Если этап 2 его не подтвердит, на десктопе `core:data` получает реализацию на Firestore REST с polling, а остальное не меняется.
