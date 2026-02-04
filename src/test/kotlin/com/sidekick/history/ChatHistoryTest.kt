// =============================================================================
// ChatHistoryTest.kt
// =============================================================================
// Unit tests for ChatHistory data models.
// =============================================================================

package com.sidekick.history

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for ChatHistory data models.
 */
class ChatHistoryTest {

    @Nested
    @DisplayName("ChatSession")
    inner class ChatSessionTests {
        
        @Test
        @DisplayName("creates session with default values")
        fun `creates session with default values`() {
            val session = ChatSession()
            
            assertNotNull(session.id)
            assertEquals("New Chat", session.title)
            assertTrue(session.messages.isEmpty())
            assertTrue(session.createdAt > 0)
        }
        
        @Test
        @DisplayName("create generates title from first message")
        fun `create generates title from first message`() {
            val session = ChatSession.create("How do I implement a singleton?")
            
            assertEquals("How do I implement a singleton?", session.title)
        }
        
        @Test
        @DisplayName("create truncates long titles")
        fun `create truncates long titles`() {
            val longMessage = "This is a very long message that should be truncated " +
                "because it exceeds the maximum title length limit that we have set"
            val session = ChatSession.create(longMessage)
            
            assertTrue(session.title.length <= ChatSession.MAX_TITLE_LENGTH)
            assertTrue(session.title.endsWith("..."))
        }
        
        @Test
        @DisplayName("isEmpty returns true for no messages")
        fun `isEmpty returns true for no messages`() {
            val session = ChatSession()
            
            assertTrue(session.isEmpty)
        }
        
        @Test
        @DisplayName("messageCount returns correct count")
        fun `messageCount returns correct count`() {
            val session = ChatSession()
                .addUserMessage("Hello")
                .addAssistantMessage("Hi there!")
            
            assertEquals(2, session.messageCount)
        }
        
        @Test
        @DisplayName("addUserMessage adds message with USER role")
        fun `addUserMessage adds message with USER role`() {
            val session = ChatSession().addUserMessage("Test message")
            
            assertEquals(1, session.messages.size)
            assertEquals(MessageRole.USER, session.messages[0].role)
            assertEquals("Test message", session.messages[0].content)
        }
        
        @Test
        @DisplayName("addAssistantMessage adds message with ASSISTANT role")
        fun `addAssistantMessage adds message with ASSISTANT role`() {
            val session = ChatSession().addAssistantMessage("Response")
            
            assertEquals(MessageRole.ASSISTANT, session.messages[0].role)
        }
        
        @Test
        @DisplayName("lastMessage returns the last message")
        fun `lastMessage returns the last message`() {
            val session = ChatSession()
                .addUserMessage("First")
                .addAssistantMessage("Second")
            
            assertEquals("Second", session.lastMessage?.content)
        }
        
        @Test
        @DisplayName("withTitle updates title and timestamp")
        fun `withTitle updates title and timestamp`() {
            val session = ChatSession()
            val original = session.updatedAt
            
            Thread.sleep(10)  // Ensure different timestamp
            val updated = session.withTitle("New Title")
            
            assertEquals("New Title", updated.title)
            assertTrue(updated.updatedAt >= original)
        }
        
        @Test
        @DisplayName("preview shows last message content")
        fun `preview shows last message content`() {
            val session = ChatSession()
                .addUserMessage("Hello")
                .addAssistantMessage("This is a preview")
            
            assertTrue(session.preview.contains("This is a preview"))
        }
    }

    @Nested
    @DisplayName("ChatHistoryMessage")
    inner class ChatHistoryMessageTests {
        
        @Test
        @DisplayName("user creates message with USER role")
        fun `user creates message with USER role`() {
            val message = ChatHistoryMessage.user("Hello")
            
            assertEquals(MessageRole.USER, message.role)
            assertEquals("Hello", message.content)
            assertTrue(message.isUser)
            assertFalse(message.isAssistant)
        }
        
        @Test
        @DisplayName("assistant creates message with ASSISTANT role")
        fun `assistant creates message with ASSISTANT role`() {
            val message = ChatHistoryMessage.assistant("Response")
            
            assertEquals(MessageRole.ASSISTANT, message.role)
            assertTrue(message.isAssistant)
        }
        
        @Test
        @DisplayName("assistant stores model in metadata")
        fun `assistant stores model in metadata`() {
            val message = ChatHistoryMessage.assistant("Response", "llama3.2")
            
            assertEquals("llama3.2", message.model)
        }
        
        @Test
        @DisplayName("system creates message with SYSTEM role")
        fun `system creates message with SYSTEM role`() {
            val message = ChatHistoryMessage.system("System prompt")
            
            assertEquals(MessageRole.SYSTEM, message.role)
        }
        
        @Test
        @DisplayName("has unique id")
        fun `has unique id`() {
            val message1 = ChatHistoryMessage.user("A")
            val message2 = ChatHistoryMessage.user("B")
            
            assertNotEquals(message1.id, message2.id)
        }
        
        @Test
        @DisplayName("formattedTime produces readable format")
        fun `formattedTime produces readable format`() {
            val message = ChatHistoryMessage.user("Test")
            
            // Format should be like "2024-01-15 12:30:45"
            assertTrue(message.formattedTime.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        }
    }

    @Nested
    @DisplayName("MessageRole")
    inner class MessageRoleTests {
        
        @Test
        @DisplayName("has all expected values")
        fun `has all expected values`() {
            val roles = MessageRole.entries
            
            assertTrue(roles.any { it == MessageRole.USER })
            assertTrue(roles.any { it == MessageRole.ASSISTANT })
            assertTrue(roles.any { it == MessageRole.SYSTEM })
            assertEquals(3, roles.size)
        }
    }
}
