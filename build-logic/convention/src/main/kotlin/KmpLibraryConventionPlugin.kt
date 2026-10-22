import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP-библиотека без Android API: JVM (десктоп, а через неё и Android) и iOS.
 * Android-приложение подключает JVM-вариант, отдельная android-цель не нужна.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KotlinMultiplatformExtension> {
                // Java 17, как у Android-приложения: класс-файлы новее оно не прочитает.
                jvm {
                    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
                }
                iosArm64()
                iosSimulatorArm64()

                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                }
            }

            // Вариант «ci» с тем же именем, что у Android-приложения: его отчёт сливает покрытие всех модулей.
            extensions.configure<KoverProjectExtension> {
                currentProject {
                    createVariant("ci") {
                        add("jvm")
                    }
                }
            }
        }
}
