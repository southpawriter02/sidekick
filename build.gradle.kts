// =============================================================================
// Sidekick - Local LLM Plugin for JetBrains Rider
// =============================================================================
// This is the root Gradle build configuration for the Sidekick plugin.
// Sidekick integrates local LLM inference (via Ollama) directly into JetBrains
// Rider, providing privacy-first AI code assistance without external API calls.
// =============================================================================

plugins {
    // Java support - required for IntelliJ Platform plugins
    id("java")
    
    // Kotlin JVM - our primary implementation language
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    
    // Kotlin Serialization - for @Serializable annotations on data classes
    // Used for Ollama API request/response JSON serialization
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    
    // IntelliJ Platform Gradle Plugin 2.x - handles SDK setup, running, and packaging
    // Version 2.x has proper Java 25 support and modern Gradle compatibility
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

// -----------------------------------------------------------------------------
// Project Metadata
// -----------------------------------------------------------------------------
group = "com.sidekick"
version = "0.1.0"

// -----------------------------------------------------------------------------
// Repository Configuration
// -----------------------------------------------------------------------------
repositories {
    mavenCentral()
    
    // IntelliJ Platform dependencies repository (required for 2.x plugin)
    intellijPlatform {
        defaultRepositories()
    }
}

// -----------------------------------------------------------------------------
// Dependencies
// -----------------------------------------------------------------------------
// Note: IntelliJ Platform dependencies are now managed in the intellijPlatform { } block.
// Here we add libraries for HTTP communication (Ktor) and testing.
// -----------------------------------------------------------------------------

// Version constants for dependency management
val ktorVersion = "2.3.8"
val coroutinesVersion = "1.7.3"

dependencies {
    // -------------------------------------------------------------------------
    // IntelliJ Platform Dependencies (2.x plugin style)
    // -------------------------------------------------------------------------
    intellijPlatform {
        // Target Rider - we're building specifically for C#/.NET developers
        rider("2024.1")
        
        // Additional plugins we depend on
        bundledPlugin("Git4Idea")
    }
    
    // -------------------------------------------------------------------------
    // Runtime Dependencies - Ollama Client (v0.1.1)
    // -------------------------------------------------------------------------
    
    // Ktor HTTP Client - Async, coroutine-based HTTP client for Ollama API
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    
    // CIO Engine - Pure Kotlin async engine (no native dependencies)
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    
    // Content Negotiation - Automatic JSON serialization/deserialization
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    
    // Kotlinx Serialization JSON - JSON codec for Ktor
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    
    // Kotlinx Serialization Core - Required for @Serializable annotations
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Kotlin Coroutines - Async programming primitives
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    
    // -------------------------------------------------------------------------
    // Test Dependencies
    // -------------------------------------------------------------------------
    
    // JUnit 5 - Modern testing framework with better assertions and lifecycle
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    
    // MockK - Kotlin-first mocking library, works great with coroutines
    testImplementation("io.mockk:mockk:1.13.9")
    
    // Coroutines Test - For testing suspend functions and flows
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    
    // Ktor Mock Engine - For testing HTTP client without real network calls
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    
    // JUnit Platform Launcher - Required by Gradle 9.x to start the test executor
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// -----------------------------------------------------------------------------
// Kotlin Compiler Configuration
// -----------------------------------------------------------------------------
kotlin {
    // JDK 17 is the minimum for modern IntelliJ Platform development
    jvmToolchain(17)
}

// -----------------------------------------------------------------------------
// IntelliJ Platform Plugin Configuration (2.x style)
// -----------------------------------------------------------------------------
intellijPlatform {
    pluginConfiguration {
        name = "Sidekick"
        
        ideaVersion {
            // Minimum supported IDE build number (2024.1.x)
            sinceBuild = "241"
            
            // Maximum supported IDE build number (up to 2025.3.x)
            // Using wildcard to support all minor versions in the 253 series
            untilBuild = "253.*"
        }
    }
}

// -----------------------------------------------------------------------------
// Build Tasks Configuration
// -----------------------------------------------------------------------------
tasks {
    // Ensure we're using UTF-8 for all Java compilation
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    
    // Configure Kotlin compilation options
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            // Enable strict null-safety warnings
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
    
    // Configure test task to use JUnit Platform (JUnit 5)
    test {
        useJUnitPlatform()
        
        // Log test execution for visibility
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

// -----------------------------------------------------------------------------
// Logging Configuration
// -----------------------------------------------------------------------------
// Note: We use IntelliJ's built-in Logger (com.intellij.openapi.diagnostic.Logger)
// for all plugin logging. This integrates with the IDE's log system and respects
// user log level settings. See SidekickPlugin.kt for usage examples.
// -----------------------------------------------------------------------------
