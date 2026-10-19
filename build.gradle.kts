plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover) apply false
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "spike/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "spike/**")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    source.setFrom("app/src/main/java", "app/src/test/java")
    config.setFrom("config/detekt/detekt.yml")
    baseline = file("config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    parallel = true
}
