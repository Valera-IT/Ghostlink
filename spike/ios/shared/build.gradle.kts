plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Десктоп (macOS/Windows) — та же JVM-сборка; Firebase через GitLive поверх firebase-java-sdk.
    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            // Статический фреймворк: символы Firebase разрешаются при линковке приложения,
            // куда Firebase iOS SDK подключён через SPM (GitLive не тянет его транзитивно).
            isStatic = true
        }
    }

    compilerOptions {
        optIn.addAll("kotlinx.cinterop.ExperimentalForeignApi", "kotlinx.cinterop.BetaInteropApi", "kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coroutines.core)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.qrose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.valerait.ghostlink.MainKt"
    }
}
