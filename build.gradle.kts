// AGP 9 has Kotlin built in. Putting a newer Kotlin Gradle plugin on the classpath
// makes the build use that Kotlin version (it must match the Compose compiler plugin).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.roborazzi) apply false
}
