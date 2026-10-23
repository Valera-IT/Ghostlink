// Фейки репозиториев и тестовые часы: на них проверяются use case и ViewModel (ARCHITECTURE.md, принцип 5).
plugins {
    alias(libs.plugins.ghostlink.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
