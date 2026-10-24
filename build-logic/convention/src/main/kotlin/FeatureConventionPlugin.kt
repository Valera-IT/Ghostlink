import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Модуль фичи: экраны на Compose Multiplatform, ViewModel и маршруты навигации.
 * Видит только core:domain, core:platform и UI-модули: реализации репозиториев подключает приложение через Koin,
 * а в другую фичу экран переходит через переданную лямбду. Границы проверяет ghostlink.module-graph.
 */
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("ghostlink.kmp.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(project(":core:domain"))
                    implementation(project(":core:ui"))
                    implementation(project(":core:platform"))
                    implementation(libs.library("cmp-material-icons-extended"))
                    implementation(libs.library("jb-navigation-compose"))
                    implementation(libs.library("jb-lifecycle-viewmodel-compose"))
                    implementation(libs.library("jb-lifecycle-runtime-compose"))
                    implementation(libs.library("koin-compose-viewmodel"))
                    implementation(libs.library("kotlinx-serialization-json"))
                }
                sourceSets.commonTest.dependencies {
                    implementation(project(":core:testing"))
                    implementation(libs.library("kotlinx-coroutines-test"))
                }
            }
        }
}
