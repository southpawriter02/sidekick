package com.sidekick.navigation.snippets

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Unit tests for SnippetService serialization and state management.
 *
 * Tests cover:
 * - SnippetData serialization round-trip
 * - State to SnippetPocket conversion
 * - Edge cases for empty/null handling
 *
 * @since 0.4.3
 */
@DisplayName("SnippetService")
class SnippetServiceTest {

    // =========================================================================
    // SnippetData Tests
    // =========================================================================

    @Nested
    @DisplayName("SnippetData Serialization")
    inner class SnippetDataSerializationTests {

        @Test
        @DisplayName("converts to Snippet correctly")
        fun toSnippet_convertsCorrectly() {
            val data = SnippetService.SnippetData(
                id = 42,
                content = "fun hello() = println(\"Hi\")",
                language = "kt",
                sourceFile = "/src/Main.kt",
                lineStart = 10,
                lineEnd = 15,
                savedAt = 1704067200000L,
                label = "Hello Function"
            )

            val snippet = data.toSnippet()!!

            assertEquals(42, snippet.id)
            assertEquals("fun hello() = println(\"Hi\")", snippet.content)
            assertEquals("kt", snippet.language)
            assertEquals("/src/Main.kt", snippet.sourceFile)
            assertEquals(10..15, snippet.lineRange)
            assertEquals("Hello Function", snippet.label)
        }

        @Test
        @DisplayName("empty content returns null snippet")
        fun emptyContent_returnsNull() {
            val data = SnippetService.SnippetData(content = "")
            assertNull(data.toSnippet())
        }

        @Test
        @DisplayName("creates SnippetData from Snippet")
        fun from_createsCorrectly() {
            val snippet = Snippet(
                id = 99,
                content = "val x = 5",
                language = "kt",
                sourceFile = "/path/file.kt",
                lineRange = 5..7,
                savedAt = Instant.ofEpochMilli(1000000L),
                label = "Test"
            )

            val data = SnippetService.SnippetData.from(snippet)

            assertEquals(99, data.id)
            assertEquals("val x = 5", data.content)
            assertEquals("kt", data.language)
            assertEquals("/path/file.kt", data.sourceFile)
            assertEquals(5, data.lineStart)
            assertEquals(7, data.lineEnd)
            assertEquals(1000000L, data.savedAt)
            assertEquals("Test", data.label)
        }

        @Test
        @DisplayName("null language becomes empty string")
        fun nullLanguage_becomesEmptyString() {
            val snippet = Snippet(id = 1, content = "x")
            val data = SnippetService.SnippetData.from(snippet)

            assertEquals("", data.language)
        }

        @Test
        @DisplayName("null lineRange becomes -1 values")
        fun nullLineRange_becomesNegativeValues() {
            val snippet = Snippet(id = 1, content = "x")
            val data = SnippetService.SnippetData.from(snippet)

            assertEquals(-1, data.lineStart)
            assertEquals(-1, data.lineEnd)
        }

        @Test
        @DisplayName("blank language becomes null in Snippet")
        fun blankLanguage_becomesNullInSnippet() {
            val data = SnippetService.SnippetData(
                content = "x",
                language = ""
            )

            val snippet = data.toSnippet()!!
            assertNull(snippet.language)
        }

        @Test
        @DisplayName("negative line values become null range")
        fun negativeLines_becomeNullRange() {
            val data = SnippetService.SnippetData(
                content = "x",
                lineStart = -1,
                lineEnd = -1
            )

            val snippet = data.toSnippet()!!
            assertNull(snippet.lineRange)
        }

        @Test
        @DisplayName("round-trip conversion preserves data")
        fun roundTrip_preservesData() {
            val original = Snippet(
                id = 123,
                content = "multi\nline\ncode",
                language = "java",
                sourceFile = "/src/Test.java",
                lineRange = 20..25,
                label = "Test Snippet"
            )

            val data = SnippetService.SnippetData.from(original)
            val restored = data.toSnippet()!!

            assertEquals(original.id, restored.id)
            assertEquals(original.content, restored.content)
            assertEquals(original.language, restored.language)
            assertEquals(original.sourceFile, restored.sourceFile)
            assertEquals(original.lineRange, restored.lineRange)
            assertEquals(original.label, restored.label)
        }
    }

    // =========================================================================
    // State Tests
    // =========================================================================

    @Nested
    @DisplayName("State Management")
    inner class StateManagementTests {

        @Test
        @DisplayName("default state has empty snippets list")
        fun defaultState_hasEmptyList() {
            val state = SnippetService.State()

            assertTrue(state.snippets.isEmpty())
            assertEquals(SnippetPocket.MAX_SLOTS, state.maxSlots)
        }

        @Test
        @DisplayName("toPocket creates SnippetPocket from state")
        fun toPocket_createsPocket() {
            val state = SnippetService.State(
                snippets = mutableListOf(
                    SnippetService.SnippetData(id = 1, content = "first"),
                    SnippetService.SnippetData(id = 2, content = "second")
                ),
                maxSlots = 10
            )

            val pocket = state.toPocket()

            assertEquals("first", pocket.get(0)?.content)
            assertEquals("second", pocket.get(1)?.content)
        }

        @Test
        @DisplayName("toPocket handles empty data")
        fun toPocket_handlesEmptyData() {
            val state = SnippetService.State(
                snippets = mutableListOf(
                    SnippetService.SnippetData(content = ""), // empty = null
                    SnippetService.SnippetData(id = 2, content = "valid")
                )
            )

            val pocket = state.toPocket()

            assertNull(pocket.get(0))
            assertEquals("valid", pocket.get(1)?.content)
        }

        @Test
        @DisplayName("from creates State from SnippetPocket")
        fun from_createsState() {
            val pocket = SnippetPocket()
                .add(Snippet(id = 1, content = "one"))
                .add(Snippet(id = 2, content = "two"))

            val state = SnippetService.State.from(pocket)

            assertEquals(10, state.snippets.size)
            assertEquals("two", state.snippets[0].content)
            assertEquals("one", state.snippets[1].content)
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles empty state gracefully")
        fun handlesEmptyState() {
            val state = SnippetService.State()
            val pocket = state.toPocket()

            assertTrue(pocket.isEmpty)
            assertEquals(10, pocket.slots.size)
        }

        @Test
        @DisplayName("default constructor creates valid empty data")
        fun defaultConstructor_createsValidData() {
            val data = SnippetService.SnippetData()

            assertEquals(0, data.id)
            assertEquals("", data.content)
            assertNull(data.toSnippet())
        }

        @Test
        @DisplayName("state with many snippets works")
        fun manySnippets_works() {
            val snippets = (1..10).map {
                SnippetService.SnippetData(id = it, content = "snippet $it")
            }.toMutableList()

            val state = SnippetService.State(snippets = snippets)
            val pocket = state.toPocket()

            assertEquals(10, pocket.usedSlots)
            assertEquals("snippet 1", pocket.get(0)?.content)
            assertEquals("snippet 10", pocket.get(9)?.content)
        }

        @Test
        @DisplayName("very long content is preserved")
        fun longContent_isPreserved() {
            val longContent = "x".repeat(50_000)
            val data = SnippetService.SnippetData(id = 1, content = longContent)

            val snippet = data.toSnippet()!!
            assertEquals(longContent, snippet.content)
        }

        @Test
        @DisplayName("special characters are preserved")
        fun specialCharacters_arePreserved() {
            val special = "日本語 🚀 <>&\""
            val data = SnippetService.SnippetData(id = 1, content = special)

            val snippet = data.toSnippet()!!
            assertEquals(special, snippet.content)
        }
    }
}
