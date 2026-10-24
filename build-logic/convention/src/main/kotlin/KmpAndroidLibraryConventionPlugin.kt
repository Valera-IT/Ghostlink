import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP-библиотека, которой на Android нужен свой вариант, а не JVM-овский: например, GitLive на JVM
 * тянет firebase-java-sdk, а на Android должен работать поверх настоящего Firebase Android SDK.
 * Таргеты те же, что у `ghostlink.kmp.library`, плюс android.
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("ghostlink.kmp.library")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    // :core:data → io.github.valerait.ghostlink.core.data
                    namespace = "io.github.valerait.ghostlink" + path.replace(':', '.')
                    compileSdk = libs.version("compileSdk").toInt()
                    minSdk = libs.version("minSdk").toInt()
                    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
}
