// =============================================================================
// Sidekick - Gradle Settings
// =============================================================================
// Root settings file for the Sidekick Gradle project.
// This configures the project name and any included subprojects.
// =============================================================================

// The root project name - this will be used in IntelliJ and in the output JAR
rootProject.name = "sidekick"

// -----------------------------------------------------------------------------
// Plugin Management
// -----------------------------------------------------------------------------
// We configure plugin repositories here to ensure Gradle can resolve
// all required plugins, including the IntelliJ Platform Gradle Plugin.
// -----------------------------------------------------------------------------
pluginManagement {
    repositories {
        // JetBrains' plugin repository for IntelliJ Platform Gradle Plugin
        maven("https://plugins.gradle.org/m2/")
        
        // Maven Central for other standard plugins
        mavenCentral()
        
        // Gradle Plugin Portal as fallback
        gradlePluginPortal()
    }
}

// -----------------------------------------------------------------------------
// Toolchain Management - Auto-download JDKs
// -----------------------------------------------------------------------------
// This enables Gradle to automatically download required JDK versions (e.g., JDK 17)
// when they're not installed locally. This is essential when running on JDK 25
// but targeting JDK 17 for IntelliJ Platform plugin development.
// -----------------------------------------------------------------------------
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
