// =============================================================================
// ChatExportServiceTest.kt
// =============================================================================
// Unit tests for ChatExportService.
//
// Tests the Markdown formatting logic for single messages (user, assistant,
// error) and full conversations. Uses a fixed timestamp so output is
// deterministic.
//
// @since 1.1.1
// =============================================================================

package com.sidekick.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Tests for [ChatExportService].
 */
@DisplayName("ChatExportService")
class ChatExportServiceTest {

    // Fixed timestamp for deterministic output
    private val fixedTime = LocalDateTime.of(2026, 2, 7, 12, 0)

    // =========================================================================
    // formatSingleMessage
    // =========================================================================

    @Nested
    @DisplayName("formatSingleMessage")
    inner class FormatSingleMessageTests {

        @Test
        @DisplayName("formats user message with You prefix")
        fun `formats user message with You prefix`() {
            val result = ChatExportService.formatSingleMessage("Hello!", isUser = true)

            assertEquals("**You:**\n\nHello!", result)
        }

        @Test
        @DisplayName("formats assistant message with Sidekick prefix")
        fun `formats assistant message with Sidekick prefix`() {
            val result = ChatExportService.formatSingleMessage("Hi there!", isUser = false)

            assertEquals("**Sidekick:**\n\nHi there!", result)
        }

        @Test
        @DisplayName("formats error message as block-quote")
        fun `formats error message as block-quote`() {
            val result = ChatExportService.formatSingleMessage(
                "Connection refused",
                isUser = false,
                isError = true
            )

            assertTrue(result.startsWith("> ⚠️ **Error:**"))
            assertTrue(result.contains("Connection refused"))
        }

        @Test
        @DisplayName("returns empty string for blank input")
        fun `returns empty string for blank input`() {
            val result = ChatExportService.formatSingleMessage("  ", isUser = true)

            assertEquals("", result)
        }

        @Test
        @DisplayName("preserves code blocks in message")
        fun `preserves code blocks in message`() {
            val code = "Here is some code:\n```kotlin\nfun main() {\n    println(\"Hello\")\n}\n```"
            val result = ChatExportService.formatSingleMessage(code, isUser = false)

            assertTrue(result.contains("```kotlin"))
            assertTrue(result.contains("println(\"Hello\")"))
        }

        @Test
        @DisplayName("formats multi-line error as block-quote")
        fun `formats multi-line error as block-quote`() {
            val result = ChatExportService.formatSingleMessage(
                "Line 1\nLine 2\nLine 3",
                isUser = false,
                isError = true
            )

            assertTrue(result.contains("> ⚠️ **Error:** Line 1"))
            assertTrue(result.contains("> Line 2"))
            assertTrue(result.contains("> Line 3"))
        }
    }

    // =========================================================================
    // formatConversation
    // =========================================================================

    @Nested
    @DisplayName("formatConversation")
    inner class FormatConversationTests {

        @Test
        @DisplayName("formats empty conversation with header only")
        fun `formats empty conversation with header only`() {
            val result = ChatExportService.formatConversation(
                messages = emptyList(),
                timestamp = fixedTime
            )

            assertTrue(result.contains("# Sidekick Chat Export"))
            assertTrue(result.contains("2026-02-07 12:00"))
        }

        @Test
        @DisplayName("includes project name in header")
        fun `includes project name in header`() {
            val result = ChatExportService.formatConversation(
                messages = emptyList(),
                projectName = "MyProject",
                timestamp = fixedTime
            )

            assertTrue(result.contains("**Project:** MyProject"))
        }

        @Test
        @DisplayName("includes model name in header")
        fun `includes model name in header`() {
            val result = ChatExportService.formatConversation(
                messages = emptyList(),
                modelName = "llama3.2",
                timestamp = fixedTime
            )

            assertTrue(result.contains("**Model:** llama3.2"))
        }

        @Test
        @DisplayName("formats single message conversation")
        fun `formats single message conversation`() {
            val messages = listOf(
                ExportableMessage("Hello!", isUser = true)
            )

            val result = ChatExportService.formatConversation(
                messages = messages,
                timestamp = fixedTime
            )

            assertTrue(result.contains("# Sidekick Chat Export"))
            assertTrue(result.contains("**You:**"))
            assertTrue(result.contains("Hello!"))
        }

        @Test
        @DisplayName("separates multiple messages with horizontal rules")
        fun `separates multiple messages with horizontal rules`() {
            val messages = listOf(
                ExportableMessage("Hello!", isUser = true),
                ExportableMessage("Hi there!", isUser = false),
                ExportableMessage("How are you?", isUser = true)
            )

            val result = ChatExportService.formatConversation(
                messages = messages,
                timestamp = fixedTime
            )

            // Count separators — should have 2 between 3 messages
            val separatorCount = result.split("---").size - 1
            // Header has 1 "---" separator + 2 between messages = 3 total
            assertEquals(3, separatorCount)
        }

        @Test
        @DisplayName("includes error messages in export")
        fun `includes error messages in export`() {
            val messages = listOf(
                ExportableMessage("Hello!", isUser = true),
                ExportableMessage("Something went wrong", isUser = false, isError = true)
            )

            val result = ChatExportService.formatConversation(
                messages = messages,
                timestamp = fixedTime
            )

            assertTrue(result.contains("> ⚠️ **Error:**"))
            assertTrue(result.contains("Something went wrong"))
        }

        @Test
        @DisplayName("omits blank project and model from header")
        fun `omits blank project and model from header`() {
            val result = ChatExportService.formatConversation(
                messages = emptyList(),
                projectName = null,
                modelName = null,
                timestamp = fixedTime
            )

            assertFalse(result.contains("**Project:**"))
            assertFalse(result.contains("**Model:**"))
        }

        @Test
        @DisplayName("full conversation ends with newline")
        fun `full conversation ends with newline`() {
            val messages = listOf(
                ExportableMessage("Hello!", isUser = true),
                ExportableMessage("Hi there!", isUser = false)
            )

            val result = ChatExportService.formatConversation(
                messages = messages,
                timestamp = fixedTime
            )

            assertTrue(result.endsWith("\n"))
        }
    }

    // =========================================================================
    // ExportableMessage
    // =========================================================================

    @Nested
    @DisplayName("ExportableMessage")
    inner class ExportableMessageTests {

        @Test
        @DisplayName("defaults isError to false")
        fun `defaults isError to false`() {
            val msg = ExportableMessage("test", isUser = true)

            assertFalse(msg.isError)
        }

        @Test
        @DisplayName("data class equality works")
        fun `data class equality works`() {
            val a = ExportableMessage("hi", isUser = true, isError = false)
            val b = ExportableMessage("hi", isUser = true, isError = false)

            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }
    }
}
