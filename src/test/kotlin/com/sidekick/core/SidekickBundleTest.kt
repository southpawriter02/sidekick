// =============================================================================
// SidekickBundleTest.kt
// =============================================================================
// Unit tests for the SidekickBundle localization system.
//
// NOTE: These tests require the IntelliJ Platform's DynamicBundle infrastructure.
// For v0.1.0, we test that the bundle class exists and is properly structured.
// Full message resolution tests will be added in v0.1.1+ when we set up the
// proper test sandbox with resource loading.
// =============================================================================

package com.sidekick.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Tests for [SidekickBundle] localization.
 *
 * These are structural tests that don't require full platform initialization.
 * Message resolution tests require the resource bundle to be loaded, which
 * needs the IntelliJ Platform test framework.
 */
@DisplayName("SidekickBundle")
class SidekickBundleTest {

    // -------------------------------------------------------------------------
    // Structural Tests
    // -------------------------------------------------------------------------
    // These tests validate the bundle is properly defined without loading
    // actual resources (which requires platform initialization).
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SidekickBundle object exists")
    fun `SidekickBundle object exists`() {
        // Assert - the bundle object should be accessible
        assertNotNull(SidekickBundle, "SidekickBundle should be a valid object")
    }

    @Test
    @DisplayName("SidekickBundle has message function")
    fun `SidekickBundle has message function`() {
        // This test just verifies the function signature exists
        // Actual message resolution requires platform initialization
        
        // Use reflection to verify the method exists
        val messageMethod = SidekickBundle::class.java.methods.find { it.name == "message" }
        assertNotNull(messageMethod, "SidekickBundle should have a 'message' function")
    }

    // -------------------------------------------------------------------------
    // Resource File Verification
    // -------------------------------------------------------------------------
    // Verify the properties file exists in the classpath
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SidekickBundle.properties file exists in resources")
    fun `SidekickBundle properties file exists in resources`() {
        // Arrange - the properties file path
        val resourcePath = "/messages/SidekickBundle.properties"
        
        // Act - try to load the resource
        val resource = this::class.java.getResource(resourcePath)
        
        // Assert
        assertNotNull(resource, "SidekickBundle.properties should exist at $resourcePath")
    }

    @Test
    @DisplayName("Properties file contains expected keys")
    fun `properties file contains expected keys`() {
        // Arrange
        val resourcePath = "/messages/SidekickBundle.properties"
        val inputStream = this::class.java.getResourceAsStream(resourcePath)
        
        assertNotNull(inputStream, "Should be able to read properties file")
        
        // Act - read the properties
        val content = inputStream!!.bufferedReader().readText()
        
        // Assert - check for key structural elements
        assertTrue(content.contains("plugin.name="), "Should contain plugin.name key")
        assertTrue(content.contains("toolwindow.title="), "Should contain toolwindow.title key")
        assertTrue(content.contains("chat."), "Should contain chat.* keys")
        assertTrue(content.contains("error."), "Should contain error.* keys")
    }
}
