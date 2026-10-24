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
include(":apps:android")
include(":core:model")
include(":core:domain")
include(":core:crypto")
include(":core:firebase")
include(":core:data")
include(":core:testing")
include(":core:demo")
include(":core:designsystem")
include(":core:ui")
include(":core:platform")
include(":feature:onboarding")
include(":feature:chats")
include(":feature:chat")
include(":feature:friends")
include(":feature:devices")
include(":feature:link")
include(":feature:recovery")
include(":feature:settings")
