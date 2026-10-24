// Друзья: мой QR-код, сканер кодов и экран «добавить в друзья» по приглашению.
plugins {
    alias(libs.plugins.ghostlink.feature)
}

kotlin {
    sourceSets {
        // Камера и распознавание QR пока только на Android; в 3.10 сканер переедет в core:platform.
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.concurrent.futures.ktx)
            implementation(libs.guava)
            implementation(libs.mlkit.barcode.scanning)
        }
    }
}
