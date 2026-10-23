// Шифрование привязки устройства на cryptography-kotlin: один код и один формат на Android, iOS и десктопе.
plugins {
    alias(libs.plugins.ghostlink.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
    }
}
