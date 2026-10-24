import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Границы модулей из ARCHITECTURE.md: кто от кого может зависеть. Проверку делает сама сборка,
 * а не ревью: запрещённая зависимость роняет любой запуск Gradle, в том числе в CI.
 */
class ModuleGraphConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            check(this == rootProject) { "ghostlink.module-graph подключается только к корневому проекту" }

            subprojects {
                val from = path
                configurations.configureEach {
                    val test = name.contains("test", ignoreCase = true)
                    dependencies.withType(ProjectDependency::class.java).configureEach {
                        ModuleGraph.violation(from, path, test)?.let { error(it) }
                    }
                }
            }
        }
}

internal object ModuleGraph {
    private val ui = setOf(":core:designsystem", ":core:ui")

    /** Прямые зависимости production-кода; транзитивные через `api` проверяются у самого модуля. */
    private val allowed: Map<String, Set<String>> =
        mapOf(
            ":core:model" to emptySet(),
            ":core:domain" to setOf(":core:model"),
            ":core:crypto" to setOf(":core:domain", ":core:model"),
            ":core:firebase" to emptySet(),
            ":core:platform" to setOf(":core:model"),
            ":core:designsystem" to emptySet(),
            ":core:ui" to setOf(":core:designsystem", ":core:model", ":core:platform"),
            ":core:data" to setOf(":core:domain", ":core:model", ":core:firebase", ":core:crypto", ":core:platform"),
            ":core:testing" to setOf(":core:domain", ":core:model"),
            ":core:demo" to setOf(":core:testing", ":core:domain", ":core:model"),
        )

    /** Фичи видят только domain, platform и UI-модули: не другие фичи и не core:data. */
    private val feature = setOf(":core:domain", ":core:model", ":core:platform") + ui

    /** Тестам любого модуля можно брать фейки из core:testing. */
    private val testOnly = setOf(":core:testing")

    fun violation(
        from: String,
        to: String,
        test: Boolean,
    ): String? {
        val rule = if (from.startsWith(":feature:")) feature else allowed[from]
        val ok =
            when {
                from == to -> true
                from.startsWith(":apps:") -> to.startsWith(":core:") || to.startsWith(":feature:")
                rule == null -> return "$from нет в графе модулей: добавьте правило в ModuleGraph и в ARCHITECTURE.md"
                else -> to in rule || (test && to in testOnly)
            }
        return if (ok) null else "$from не может зависеть от $to: границы модулей описаны в ARCHITECTURE.md"
    }
}
