pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ghostlink"
include(":app")
include(":core:model")
include(":core:domain")
include(":core:crypto")
include(":core:firebase")
include(":core:data")
include(":core:testing")
include(":core:demo")
include(":core:designsystem")
include(":core:ui")
