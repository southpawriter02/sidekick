// =============================================================================
// SidekickPluginTest.kt
// =============================================================================
// Unit tests for the main SidekickPlugin application service.
//
// IMPORTANT: Tests that require IntelliJ Application/Service infrastructure
// cannot run as simple JUnit tests. They require the IntelliJ test framework
// which sets up mock Application, Project, and Service containers.
//
// For v0.1.0, we focus on tests that don't require the full platform:
// - Version constant validation
// - Static behavior tests
//
// Integration tests requiring the full platform will be added when we set up
// the plugin test runner in a later version.
// =============================================================================

package com.sidekick

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Tests for [SidekickPlugin] that don't require the full IntelliJ Platform.
 *
 * These are lightweight tests that validate static properties and constants.
 * Full integration tests requiring ApplicationManager will be added in v0.1.1+
 * when we configure the proper test sandbox.
 */
@DisplayName("SidekickPlugin")
class SidekickPluginTest {

    // -------------------------------------------------------------------------
    // Version Tests
    // -------------------------------------------------------------------------
    // These tests validate constants and don't require Application context
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("VERSION constant is not blank")
    fun `VERSION constant is not blank`() {
        // Assert - VERSION should always be a non-empty string
        assertTrue(SidekickPlugin.VERSION.isNotBlank(), "VERSION should not be blank")
    }

    @Test
    @DisplayName("VERSION constant follows semver format")
    fun `VERSION constant follows semver format`() {
        // Arrange - semver pattern: MAJOR.MINOR.PATCH (optionally with pre-release)
        val semverPattern = Regex("""^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$""")

        // Assert
        assertTrue(
            semverPattern.matches(SidekickPlugin.VERSION),
            "VERSION '${SidekickPlugin.VERSION}' should match semver format (e.g., 0.1.0)"
        )
    }

    @Test
    @DisplayName("VERSION constant matches expected initial version")
    fun `VERSION constant matches expected initial version`() {
        // Assert - v0.1.0 is our initial release
        assertEquals("0.1.0", SidekickPlugin.VERSION, "VERSION should be 0.1.0 for initial release")
    }

    // -------------------------------------------------------------------------
    // Note on Platform Tests
    // -------------------------------------------------------------------------
    // Tests that call SidekickPlugin.getInstance() require the IntelliJ Platform
    // to be initialized. These tests should be run via:
    //   ./gradlew runPluginVerifier
    // or within the IDE's test runner which properly initializes the platform.
    //
    // For CI, we'll add integration tests in v0.1.1+ that use the proper
    // test sandbox configuration.
    // -------------------------------------------------------------------------
}
