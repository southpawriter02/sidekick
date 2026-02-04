// =============================================================================
// MessageBubbleTest.kt
// =============================================================================
// Unit tests for the MessageBubble UI component.
//
// These tests verify structural properties and behavior that can be tested
// without the IntelliJ Platform context.
// =============================================================================

package com.sidekick.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.BorderLayout

/**
 * Tests for [MessageBubble] component.
 */
@DisplayName("MessageBubble")
class MessageBubbleTest {

    @Nested
    @DisplayName("Construction")
    inner class ConstructionTests {
        
        @Test
        @DisplayName("creates user message bubble")
        fun `creates user message bubble`() {
            val bubble = MessageBubble("Hello", isUser = true)
            
            assertNotNull(bubble)
            assertEquals("Hello", bubble.getText())
        }
        
        @Test
        @DisplayName("creates assistant message bubble")
        fun `creates assistant message bubble`() {
            val bubble = MessageBubble("Hi there!", isUser = false)
            
            assertNotNull(bubble)
            assertEquals("Hi there!", bubble.getText())
        }
        
        @Test
        @DisplayName("creates error message bubble")
        fun `creates error message bubble`() {
            val bubble = MessageBubble("Error occurred", isUser = false, isError = true)
            
            assertNotNull(bubble)
            assertEquals("Error occurred", bubble.getText())
        }
        
        @Test
        @DisplayName("uses BorderLayout")
        fun `uses BorderLayout`() {
            val bubble = MessageBubble("Test", isUser = true)
            
            assertTrue(bubble.layout is BorderLayout)
        }
    }
    
    @Nested
    @DisplayName("Text Manipulation")
    inner class TextManipulationTests {
        
        @Test
        @DisplayName("appendText adds to existing content")
        fun `appendText adds to existing content`() {
            val bubble = MessageBubble("Hello", isUser = false)
            
            bubble.appendText(" World")
            
            assertEquals("Hello World", bubble.getText())
        }
        
        @Test
        @DisplayName("appendText works with empty initial text")
        fun `appendText works with empty initial text`() {
            val bubble = MessageBubble("", isUser = false)
            
            bubble.appendText("Streaming")
            bubble.appendText(" response")
            
            assertEquals("Streaming response", bubble.getText())
        }
        
        @Test
        @DisplayName("markComplete does not throw")
        fun `markComplete does not throw`() {
            val bubble = MessageBubble("Test", isUser = false)
            
            assertDoesNotThrow {
                bubble.markComplete()
            }
        }
    }
}
