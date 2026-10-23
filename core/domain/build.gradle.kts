// Интерфейсы репозиториев и use case: правила приложения без Firebase, Compose и платформы.
plugins {
    alias(libs.plugins.ghostlink.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
