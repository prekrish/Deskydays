pluginManagement {
    repositories {
        // -PcoreOnly skips Google Maven so :core can build where it isn't reachable.
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

val coreOnly = providers.gradleProperty("coreOnly").isPresent

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!coreOnly) google()
        mavenCentral()
    }
}

rootProject.name = "DeskDays"

// Pure Kotlin counting/stats logic. No Android dependencies, so it can move to
// Kotlin Multiplatform later for iOS.
include(":core")

// Pass -PcoreOnly to build/test :core without the Android SDK or Google Maven.
if (!coreOnly) include(":app")
