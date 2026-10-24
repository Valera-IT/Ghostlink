// То, что каждая платформа делает по-своему: защищённое хранилище, это устройство, жизненный цикл,
// диплинки, камера с распознаванием QR, буфер обмена. Выше этого модуля код платформенных API не видит.
plugins {
    alias(libs.plugins.ghostlink.kmp.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.concurrent.futures.ktx)
            implementation(libs.guava)
            implementation(libs.mlkit.barcode.scanning)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
