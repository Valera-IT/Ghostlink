// Kotlin-часть iOS-приложения: статический фреймворк GhostlinkKit поверх core:* и feature:*.
// Его собирает Xcode-проект из iosApp/ (см. iosApp/project.yml), а Firebase iOS SDK подключается там через SPM.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "GhostlinkKit"
            // Статический: символы Firebase разрешаются при линковке приложения, куда SDK приходит через SPM.
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:crypto"))
            implementation(project(":core:data"))
            implementation(project(":core:demo"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:ui"))
            implementation(project(":core:platform"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:chats"))
            implementation(project(":feature:chat"))
            implementation(project(":feature:friends"))
            implementation(project(":feature:devices"))
            implementation(project(":feature:link"))
            implementation(project(":feature:recovery"))
            implementation(project(":feature:settings"))

            implementation(libs.jb.navigation.compose)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
