// KGP and KSP are loaded here, in the root classloader, so AGP has to be loaded here too:
// otherwise KSP can't see AGP's classes and sync fails with NoClassDefFoundError: BaseVariant.
// AGP goes on the classpath via buildscript so `-PcoreOnly` can skip it and never needs Google Maven.
buildscript {
    if (!gradle.startParameter.projectProperties.containsKey("coreOnly")) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Keep in step with `agp` in gradle/libs.versions.toml.
            classpath("com.android.tools.build:gradle:8.10.1")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
