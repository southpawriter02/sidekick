// =============================================================================
// SidekickTestBase.kt
// =============================================================================
// Base class for Sidekick unit tests.
//
// NOTE: For v0.1.0, we use simple JUnit 5 tests without IntelliJ Platform
// dependencies. This allows tests to run quickly via `./gradlew test`.
//
// In future versions (v0.1.1+), when we need tests that require the full
// IntelliJ Platform (e.g., testing services with Application context), we'll
// add a separate test source set that uses BasePlatformTestCase.
//
// TEST CATEGORIES:
// - Unit tests (this file): Fast, no platform dependencies
// - Integration tests (future): Require IntelliJ Platform test sandbox
// =============================================================================

package com.sidekick.testutil

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/**
 * Base class for Sidekick unit tests.
 *
 * Provides common setup/teardown hooks and test utilities.
 * These tests run without the full IntelliJ Platform, making them
 * fast and suitable for CI.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyFeatureTest : SidekickTestBase() {
 *
 *     @Test
 *     fun `test my feature does something`() {
 *         // Arrange
 *         val myService = MyService()
 *
 *         // Act
 *         val result = myService.doSomething()
 *
 *         // Assert
 *         assertEquals("expected", result)
 *     }
 * }
 * ```
 *
 * ## Naming Convention
 *
 * Use backtick-quoted test names for readability:
 * ```kotlin
 * @Test
 * fun `test feature X returns Y when Z`() { ... }
 * ```
 */
abstract class SidekickTestBase {

    // -------------------------------------------------------------------------
    // Setup & Teardown
    // -------------------------------------------------------------------------

    /**
     * Called before each test method.
     * Override in subclasses to add test-specific setup.
     */
    @BeforeEach
    open fun setUp() {
        // Subclasses can override to add setup logic
    }

    /**
     * Called after each test method.
     * Override in subclasses to add test-specific teardown.
     */
    @AfterEach
    open fun tearDown() {
        // Subclasses can override to add teardown logic
    }

    // -------------------------------------------------------------------------
    // Test Data Path
    // -------------------------------------------------------------------------

    /**
     * Returns the path to test data files.
     *
     * Test data files (JSON fixtures, sample code, etc.) should be placed
     * in this directory.
     */
    protected val testDataPath: String = "src/test/testData"

    // -------------------------------------------------------------------------
    // Test Utilities
    // -------------------------------------------------------------------------
    // Add common test helper methods here as the test suite grows.
    // Examples:
    // - JSON fixture loaders
    // - Mock response builders for Ollama
    // - Assertion helpers
    // -------------------------------------------------------------------------
}
