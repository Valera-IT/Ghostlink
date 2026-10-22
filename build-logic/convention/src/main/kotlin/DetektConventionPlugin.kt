import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * detekt на корневом проекте по исходникам всех модулей. В модулях с Kotlin-плагином
 * detekt 1.x ломается на несовпадении версий Kotlin, поэтому в модули его не ставим.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            check(this == rootProject) { "ghostlink.detekt подключается только к корневому проекту" }
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<DetektExtension> {
                source.setFrom(
                    subprojects.flatMap { listOf(it.file("src/main/java"), it.file("src/test/java")) } +
                        file("build-logic/convention/src/main/kotlin"),
                )
                config.setFrom("config/detekt/detekt.yml")
                baseline = file("config/detekt/baseline.xml")
                buildUponDefaultConfig = true
                parallel = true
            }
        }
}
