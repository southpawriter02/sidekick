// =============================================================================
// ContentBlockParserTest.kt
// =============================================================================
// Unit tests for ContentBlockParser.
//
// Tests the splitting of raw message text into Text and CodeFence blocks,
// including edge cases like unclosed fences, empty input, and nested fences.
//
// @since 1.1.2
// =============================================================================

package com.sidekick.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [ContentBlockParser].
 */
@DisplayName("ContentBlockParser")
class ContentBlockParserTest {

    // =========================================================================
    // parse — basic cases
    // =========================================================================

    @Nested
    @DisplayName("parse")
    inner class ParseTests {

        @Test
        @DisplayName("returns empty list for blank input")
        fun `returns empty list for blank input`() {
            val result = ContentBlockParser.parse("")
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("returns empty list for whitespace-only input")
        fun `returns empty list for whitespace-only input`() {
            val result = ContentBlockParser.parse("   \n  \n  ")
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("plain text becomes single Text block")
        fun `plain text becomes single Text block`() {
            val result = ContentBlockParser.parse("Hello, world!")

            assertEquals(1, result.size)
            val block = result[0] as ContentBlock.Text
            assertEquals("Hello, world!", block.content)
        }

        @Test
        @DisplayName("multi-line plain text stays as one Text block")
        fun `multi-line plain text stays as one Text block`() {
            val text = "Line one\nLine two\nLine three"
            val result = ContentBlockParser.parse(text)

            assertEquals(1, result.size)
            assertTrue(result[0] is ContentBlock.Text)
        }
    }

    // =========================================================================
    // parse — code fences
    // =========================================================================

    @Nested
    @DisplayName("code fences")
    inner class CodeFenceTests {

        @Test
        @DisplayName("detects simple code fence")
        fun `detects simple code fence`() {
            val text = "```kotlin\nfun main() {}\n```"
            val result = ContentBlockParser.parse(text)

            assertEquals(1, result.size)
            val block = result[0] as ContentBlock.CodeFence
            assertEquals("kotlin", block.language)
            assertEquals("fun main() {}", block.code)
            assertEquals(1, block.lineCount)
        }

        @Test
        @DisplayName("detects code fence without language")
        fun `detects code fence without language`() {
            val text = "```\nsome code\n```"
            val result = ContentBlockParser.parse(text)

            assertEquals(1, result.size)
            val block = result[0] as ContentBlock.CodeFence
            assertEquals("", block.language)
            assertEquals("some code", block.code)
        }

        @Test
        @DisplayName("counts lines correctly in multi-line code block")
        fun `counts lines correctly in multi-line code block`() {
            val code = (1..15).joinToString("\n") { "line $it" }
            val text = "```\n$code\n```"
            val result = ContentBlockParser.parse(text)

            val block = result[0] as ContentBlock.CodeFence
            assertEquals(15, block.lineCount)
        }

        @Test
        @DisplayName("empty code fence has zero lines")
        fun `empty code fence has zero lines`() {
            val text = "```\n```"
            val result = ContentBlockParser.parse(text)

            val block = result[0] as ContentBlock.CodeFence
            assertEquals(0, block.lineCount)
        }
    }

    // =========================================================================
    // parse — interleaved blocks
    // =========================================================================

    @Nested
    @DisplayName("interleaved blocks")
    inner class InterleavedTests {

        @Test
        @DisplayName("text before and after code fence")
        fun `text before and after code fence`() {
            val text = "Before\n```python\nprint('hi')\n```\nAfter"
            val result = ContentBlockParser.parse(text)

            assertEquals(3, result.size)
            assertTrue(result[0] is ContentBlock.Text)
            assertTrue(result[1] is ContentBlock.CodeFence)
            assertTrue(result[2] is ContentBlock.Text)

            assertEquals("Before", (result[0] as ContentBlock.Text).content)
            assertEquals("python", (result[1] as ContentBlock.CodeFence).language)
            assertEquals("After", (result[2] as ContentBlock.Text).content)
        }

        @Test
        @DisplayName("multiple code fences")
        fun `multiple code fences`() {
            val text = """
Here is some code:
```kotlin
fun a() {}
```
And another:
```java
void b() {}
```
Done.
            """.trimIndent()

            val result = ContentBlockParser.parse(text)

            assertEquals(5, result.size)
            assertTrue(result[0] is ContentBlock.Text)
            assertTrue(result[1] is ContentBlock.CodeFence)
            assertTrue(result[2] is ContentBlock.Text)
            assertTrue(result[3] is ContentBlock.CodeFence)
            assertTrue(result[4] is ContentBlock.Text)

            assertEquals("kotlin", (result[1] as ContentBlock.CodeFence).language)
            assertEquals("java", (result[3] as ContentBlock.CodeFence).language)
        }
    }

    // =========================================================================
    // parse — edge cases
    // =========================================================================

    @Nested
    @DisplayName("edge cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("unclosed fence treated as text")
        fun `unclosed fence treated as text`() {
            val text = "Hello\n```kotlin\nval x = 1\nval y = 2"
            val result = ContentBlockParser.parse(text)

            // "Hello" becomes one Text block, the unclosed fence becomes another
            assertEquals(2, result.size)
            assertTrue(result[0] is ContentBlock.Text)
            assertTrue(result[1] is ContentBlock.Text)

            assertEquals("Hello", (result[0] as ContentBlock.Text).content)
            val fenceText = (result[1] as ContentBlock.Text).content
            assertTrue(fenceText.contains("```kotlin"))
            assertTrue(fenceText.contains("val x = 1"))
        }

        @Test
        @DisplayName("four-backtick fence is recognized")
        fun `four-backtick fence is recognized`() {
            val text = "````\ncode here\n````"
            val result = ContentBlockParser.parse(text)

            assertEquals(1, result.size)
            assertTrue(result[0] is ContentBlock.CodeFence)
        }

        @Test
        @DisplayName("code fence with only whitespace content")
        fun `code fence with only whitespace content`() {
            val text = "```\n   \n```"
            val result = ContentBlockParser.parse(text)

            assertEquals(1, result.size)
            val block = result[0] as ContentBlock.CodeFence
            // Whitespace-only content is preserved
            assertTrue(block.code.isNotEmpty())
        }
    }

    // =========================================================================
    // shouldCollapse
    // =========================================================================

    @Nested
    @DisplayName("shouldCollapse")
    inner class ShouldCollapseTests {

        @Test
        @DisplayName("returns false for small code block")
        fun `returns false for small code block`() {
            val block = ContentBlock.CodeFence("kotlin", "line1\nline2", 2)
            assertFalse(ContentBlockParser.shouldCollapse(block))
        }

        @Test
        @DisplayName("returns false at threshold minus one")
        fun `returns false at threshold minus one`() {
            val lines = (1 until ContentBlockParser.COLLAPSE_THRESHOLD).joinToString("\n")
            val block = ContentBlock.CodeFence("", lines, ContentBlockParser.COLLAPSE_THRESHOLD - 1)
            assertFalse(ContentBlockParser.shouldCollapse(block))
        }

        @Test
        @DisplayName("returns true at threshold")
        fun `returns true at threshold`() {
            val lines = (1..ContentBlockParser.COLLAPSE_THRESHOLD).joinToString("\n")
            val block = ContentBlock.CodeFence("", lines, ContentBlockParser.COLLAPSE_THRESHOLD)
            assertTrue(ContentBlockParser.shouldCollapse(block))
        }

        @Test
        @DisplayName("returns true above threshold")
        fun `returns true above threshold`() {
            val block = ContentBlock.CodeFence("python", "x", 100)
            assertTrue(ContentBlockParser.shouldCollapse(block))
        }
    }
}
