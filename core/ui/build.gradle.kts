// Общие экранные куски (аватар, время и «в сети», плашка демо-режима) и строки интерфейса в Compose Resources.
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
            api(project(":core:designsystem"))
            api(project(":core:model"))
            implementation(project(":core:platform"))
            // api: строки (Res.string.*) нужны экранам приложения, а StringResource — их состоянию.
            api(libs.cmp.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.qrose)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // Skiko под текущую ОС: без него getString() на JVM не узнаёт системную тему.
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.valerait.ghostlink.core.ui.resources"
}
