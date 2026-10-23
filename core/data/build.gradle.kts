import java.time.Duration

// Реализации репозиториев core:domain на Firebase (GitLive): мапперы DTO ↔ domain и перевод ошибок в GhostlinkError.
plugins {
    alias(libs.plugins.ghostlink.kmp.android.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project(":core:firebase"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(project(":core:crypto"))
        }
    }
}

// Интеграционные тесты (пакет …core.data.emulator) ходят в Firebase Emulator, поэтому в обычный jvmTest
// не входят и запускаются отдельно: см. README, раздел «Тесты».
val jvmTest =
    tasks.named<Test>("jvmTest") {
        filter.excludeTestsMatching("*.emulator.*")
    }
tasks.register<Test>("jvmEmulatorTest") {
    description = "Репозитории против Firebase Emulator (auth :9099, firestore :8080)."
    group = "verification"
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    filter.includeTestsMatching("*.emulator.*")
    // Тесты ходят в сеть: результат зависит не только от входов, кэшировать его нельзя.
    outputs.upToDateWhen { false }
    // Слушатель, который так и не дождался документа, не должен держать CI до общего таймаута job.
    timeout.set(Duration.ofMinutes(5))
}

// Firebase iOS SDK подключается в Xcode-проекте приложения, поэтому тестовый бинарник iOS здесь не слинковать.
tasks.matching { it.name.startsWith("linkDebugTestIos") || it.name.matches(Regex("ios\\w*Test")) }.configureEach {
    enabled = false
}
