// KMP-разведка (этап 2b, пункт 8–9): отдельная сборка, чтобы не трогать Android-приложение.
// В main не сливается — итог разведки уходит в ADR 0006.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ghostlink-ios-spike"
include(":core", ":shared")
