// =============================================================================
// SidekickSettingsTest.kt
// =============================================================================
// Unit tests for SidekickSettings component.
//
// These tests verify the State data class behavior and constants.
// Service-level tests require IntelliJ Platform context and are skipped.
// =============================================================================

package com.sidekick.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [SidekickSettings] component.
 */
@DisplayName("SidekickSettings")
class SidekickSettingsTest {

    @Nested
    @DisplayName("Constants")
    inner class ConstantsTests {
        
        @Test
        @DisplayName("DEFAULT_OLLAMA_URL is localhost:11434")
        fun `DEFAULT_OLLAMA_URL is localhost 11434`() {
            assertEquals("http://localhost:11434", SidekickSettings.DEFAULT_OLLAMA_URL)
        }
        
        @Test
        @DisplayName("DEFAULT_TEMPERATURE is 0.7")
        fun `DEFAULT_TEMPERATURE is 0_7`() {
            assertEquals(0.7, SidekickSettings.DEFAULT_TEMPERATURE)
        }
        
        @Test
        @DisplayName("DEFAULT_MAX_TOKENS is 2048")
        fun `DEFAULT_MAX_TOKENS is 2048`() {
            assertEquals(2048, SidekickSettings.DEFAULT_MAX_TOKENS)
        }
    }
    
    @Nested
    @DisplayName("State")
    inner class StateTests {
        
        @Test
        @DisplayName("State has correct default values")
        fun `State has correct default values`() {
            val state = SidekickSettings.State()
            
            assertEquals("http://localhost:11434", state.ollamaUrl)
            assertEquals("", state.defaultModel)
            assertTrue(state.streamingEnabled)
            assertEquals(0.7, state.temperature)
            assertEquals(2048, state.maxTokens)
            assertEquals("", state.systemPrompt)
            assertTrue(state.autoConnect)
        }
        
        @Test
        @DisplayName("State can be modified")
        fun `State can be modified`() {
            val state = SidekickSettings.State()
            
            state.ollamaUrl = "http://example.com:11434"
            state.defaultModel = "llama3.2"
            state.streamingEnabled = false
            state.temperature = 0.5
            state.maxTokens = 4096
            state.systemPrompt = "You are a helpful assistant"
            state.autoConnect = false
            
            assertEquals("http://example.com:11434", state.ollamaUrl)
            assertEquals("llama3.2", state.defaultModel)
            assertFalse(state.streamingEnabled)
            assertEquals(0.5, state.temperature)
            assertEquals(4096, state.maxTokens)
            assertEquals("You are a helpful assistant", state.systemPrompt)
            assertFalse(state.autoConnect)
        }
        
        @Test
        @DisplayName("State is a data class with proper equality")
        fun `State is a data class with proper equality`() {
            val state1 = SidekickSettings.State()
            val state2 = SidekickSettings.State()
            
            assertEquals(state1, state2)
            assertEquals(state1.hashCode(), state2.hashCode())
        }
        
        @Test
        @DisplayName("State copy works correctly")
        fun `State copy works correctly`() {
            val original = SidekickSettings.State()
            val modified = original.copy(defaultModel = "codellama")
            
            assertNotEquals(original, modified)
            assertEquals("codellama", modified.defaultModel)
            assertEquals("http://localhost:11434", modified.ollamaUrl)
        }
    }
}
