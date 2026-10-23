// Firebase через GitLive: DTO документов Firestore и то, что GitLive не умеет сам (поля bytes).
// Выше core:data эти типы не попадают.
plugins {
    alias(libs.plugins.ghostlink.kmp.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
        }
    }
}

// Firebase iOS SDK подключается в Xcode-проекте приложения, поэтому тестовый бинарник iOS здесь не слинковать.
tasks.matching { it.name.startsWith("linkDebugTestIos") || it.name.matches(Regex("ios\\w*Test")) }.configureEach {
    enabled = false
}
