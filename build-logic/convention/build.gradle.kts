import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "io.github.valerait.ghostlink.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// compileOnly: сами плагины кладёт на classpath корневой build.gradle.kts (`apply false`).
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ghostlink.android.app"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "ghostlink.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("kmpLibrary") {
            id = "ghostlink.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("detekt") {
            id = "ghostlink.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("spotless") {
            id = "ghostlink.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
    }
}
