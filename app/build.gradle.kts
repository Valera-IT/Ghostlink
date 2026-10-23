plugins {
    alias(libs.plugins.ghostlink.android.app)
    alias(libs.plugins.ghostlink.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kover)
}

// Конфиг Firebase лежит в app/src/dev|prod/google-services.json и в git не попадает (в CI — из секретов).
// Если его нет, сборка всё равно проходит, а приложение работает в демо-режиме (см. README).
googleServices {
    missingGoogleServicesStrategy = com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy.IGNORE
}

android {
    namespace = "io.github.valerait.ghostlink"

    defaultConfig {
        applicationId = "io.github.valerait.ghostlink"
        versionCode = 1
        versionName = "0.3.0" // x-release-please-version
    }

    flavorDimensions += "env"
    productFlavors {
        // Бэкенд в памяти с ботом: без Firebase и без секретов.
        create("demo") {
            dimension = "env"
            applicationIdSuffix = ".demo"
            resValue("string", "app_name", "Ghostlink Demo")
        }
        // Проект ghostlink-dev: ставится рядом с prod, для разработки и проверки PR.
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Ghostlink Dev")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Ghostlink")
        }
    }

    buildTypes.configureEach {
        // ./gradlew installDebug -Pghostlink.emulatorHost=10.0.2.2 — работать с локальным Firebase Emulator
        // (см. README). В обычной сборке пусто.
        val emulatorHost = if (name == "debug") providers.gradleProperty("ghostlink.emulatorHost").orNull else null
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", emulatorHost?.let { "\"$it\"" } ?: "null")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Покрытие считаем на demo-варианте: на нём идут unit-тесты в CI.
kover {
    merge {
        projects(":core:model", ":core:domain", ":core:testing", ":core:demo")
    }
    currentProject {
        createVariant("ci") {
            add("demoDebug")
        }
    }
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.ComposableSingletons*",
                    "*_Factory*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "hilt_aggregated_deps.*",
                    "dagger.hilt.*",
                )
                annotatedBy("androidx.compose.runtime.Composable", "androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:demo"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.guava)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
