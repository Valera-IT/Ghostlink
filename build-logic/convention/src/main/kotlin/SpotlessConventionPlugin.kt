import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Форматирование ktlint через spotless для всего репозитория, кроме отдельной сборки разведки. */
class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            check(this == rootProject) { "ghostlink.spotless подключается только к корневому проекту" }
            pluginManager.apply("com.diffplug.spotless")

            val ktlintVersion = libs.version("ktlint")
            val excludes = listOf("**/build/**", "spike/**")
            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude(excludes)
                    ktlint(ktlintVersion)
                }
                kotlinGradle {
                    target("**/*.gradle.kts")
                    targetExclude(excludes)
                    ktlint(ktlintVersion)
                }
            }
        }
}
