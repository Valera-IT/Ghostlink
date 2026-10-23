// Демо-бэкенд с ботом: приложение работает без Firebase (флейвор demo, скриншоты, UI-тесты).
plugins {
    alias(libs.plugins.ghostlink.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: DemoBackend наследует FakeBackend, и его супертип должен быть виден приложению.
            api(project(":core:testing"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
