// Тема Ghostlink на Compose Multiplatform: палитры, формы сообщений, типографика, логотип.
plugins {
    alias(libs.plugins.ghostlink.kmp.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    // Compose Resources на Android едут в APK как Android-ресурсы библиотеки.
    android {
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.cmp.runtime)
            api(libs.cmp.foundation)
            api(libs.cmp.ui)
            api(libs.cmp.material3)
            implementation(libs.cmp.components.resources)
        }
    }
}
