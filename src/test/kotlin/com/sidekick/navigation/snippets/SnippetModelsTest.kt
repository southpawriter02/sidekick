package com.sidekick.navigation.snippets

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

/**
 * Comprehensive unit tests for Snippet Models.
 *
 * Tests cover:
 * - Snippet data class and computed properties
 * - SnippetPocket operations (add, get, set, clear)
 * - SnippetExport format and compatibility
 * - SnippetResult sealed class
 *
 * @since 0.4.3
 */
@DisplayName("Snippet Models")
class SnippetModelsTest {

    // =========================================================================
    // Snippet Tests
    // =========================================================================

    @Nested
    @DisplayName("Snippet")
    inner class SnippetTests {

        @Test
        @DisplayName("preview truncates long content")
        fun preview_truncatesLongContent() {
            val longContent = "x".repeat(200)
            val snippet = Snippet(id = 1, content = longContent)

            assertEquals(100, snippet.preview.length)
        }

        @Test
        @DisplayName("preview replaces newlines")
        fun preview_replacesNewlines() {
            val content = "line1\nline2\nline3"
            val snippet = Snippet(id = 1, content = content)

            assertFalse(snippet.preview.contains("\n"))
            assertTrue(snippet.preview.contains("line1 line2 line3"))
        }

        @Test
        @DisplayName("previewWithEllipsis adds ellipsis when truncated")
        fun previewWithEllipsis_addsEllipsis() {
            val longContent = "x".repeat(200)
            val shortContent = "short"

            val longSnippet = Snippet(id = 1, content = longContent)
            val shortSnippet = Snippet(id = 2, content = shortContent)

            assertTrue(longSnippet.previewWithEllipsis.endsWith("..."))
            assertFalse(shortSnippet.previewWithEllipsis.endsWith("..."))
        }

        @Test
        @DisplayName("lineCount counts newlines correctly")
        fun lineCount_countsCorrectly() {
            assertEquals(1, Snippet(1, "single line").lineCount)
            assertEquals(2, Snippet(2, "line1\nline2").lineCount)
            assertEquals(5, Snippet(3, "a\nb\nc\nd\ne").lineCount)
        }

        @Test
        @DisplayName("charCount returns content length")
        fun charCount_returnsLength() {
            val snippet = Snippet(id = 1, content = "hello world")
            assertEquals(11, snippet.charCount)
        }

        @Test
        @DisplayName("displayName uses label when available")
        fun displayName_usesLabel() {
            val withLabel = Snippet(id = 1, content = "x", label = "My Snippet")
            val withFile = Snippet(id = 2, content = "x", sourceFile = "/path/to/File.kt")
            val plain = Snippet(id = 3, content = "x")

            assertEquals("My Snippet", withLabel.displayName)
            assertEquals("File.kt", withFile.displayName)
            assertEquals("Snippet #3", plain.displayName)
        }

        @Test
        @DisplayName("sourceFileName extracts file name")
        fun sourceFileName_extractsFileName() {
            val snippet = Snippet(
                id = 1,
                content = "x",
                sourceFile = "/path/to/project/src/Main.kt"
            )

            assertEquals("Main.kt", snippet.sourceFileName)
        }

        @Test
        @DisplayName("lineRangeText formats correctly")
        fun lineRangeText_formatsCorrectly() {
            val withRange = Snippet(id = 1, content = "x", lineRange = 9..14)
            val withoutRange = Snippet(id = 2, content = "x")

            assertEquals("L10-15", withRange.lineRangeText)
            assertNull(withoutRange.lineRangeText)
        }

        @Test
        @DisplayName("summary provides formatted description")
        fun summary_providesDescription() {
            val snippet = Snippet(id = 1, content = "line1\nline2", language = "kt")

            assertTrue(snippet.summary.contains("2 lines"))
            assertTrue(snippet.summary.contains("(kt)"))
        }

        @Test
        @DisplayName("hasSource detects source file presence")
        fun hasSource_detectsSourceFile() {
            val withSource = Snippet(id = 1, content = "x", sourceFile = "/path")
            val withoutSource = Snippet(id = 2, content = "x")

            assertTrue(withSource.hasSource)
            assertFalse(withoutSource.hasSource)
        }

        @Test
        @DisplayName("savedAtFormatted returns date portion")
        fun savedAtFormatted_returnsDatePortion() {
            val snippet = Snippet(
                id = 1,
                content = "x",
                savedAt = Instant.parse("2026-01-15T10:30:00Z")
            )

            assertEquals("2026-01-15", snippet.savedAtFormatted)
        }
    }

    // =========================================================================
    // SnippetPocket Tests
    // =========================================================================

    @Nested
    @DisplayName("SnippetPocket")
    inner class SnippetPocketTests {

        @Test
        @DisplayName("default pocket has 10 empty slots")
        fun defaultPocket_has10EmptySlots() {
            val pocket = SnippetPocket()

            assertEquals(10, pocket.slots.size)
            assertEquals(10, pocket.maxSlots)
            assertTrue(pocket.isEmpty)
        }

        @Test
        @DisplayName("add pushes snippet to front")
        fun add_pushesToFront() {
            val snippet1 = Snippet(id = 1, content = "first")
            val snippet2 = Snippet(id = 2, content = "second")

            val pocket = SnippetPocket()
                .add(snippet1)
                .add(snippet2)

            assertEquals("second", pocket.get(0)?.content)
            assertEquals("first", pocket.get(1)?.content)
        }

        @Test
        @DisplayName("add respects max slots")
        fun add_respectsMaxSlots() {
            val pocket = SnippetPocket(maxSlots = 3)
            var current = pocket

            for (i in 1..5) {
                current = current.add(Snippet(id = i, content = "snippet $i"))
            }

            assertEquals(3, current.slots.size)
            assertEquals("snippet 5", current.get(0)?.content)
            assertEquals("snippet 4", current.get(1)?.content)
            assertEquals("snippet 3", current.get(2)?.content)
        }

        @Test
        @DisplayName("get returns null for empty slot")
        fun get_returnsNullForEmptySlot() {
            val pocket = SnippetPocket()

            assertNull(pocket.get(0))
            assertNull(pocket.get(5))
            assertNull(pocket.get(9))
        }

        @Test
        @DisplayName("get returns null for out of bounds")
        fun get_returnsNullForOutOfBounds() {
            val pocket = SnippetPocket()

            assertNull(pocket.get(-1))
            assertNull(pocket.get(100))
        }

        @Test
        @DisplayName("setSlot sets specific slot")
        fun setSlot_setsSpecificSlot() {
            val snippet = Snippet(id = 1, content = "at slot 5")
            val pocket = SnippetPocket().setSlot(5, snippet)

            assertNull(pocket.get(0))
            assertNull(pocket.get(4))
            assertEquals("at slot 5", pocket.get(5)?.content)
            assertNull(pocket.get(6))
        }

        @Test
        @DisplayName("setSlot ignores invalid index")
        fun setSlot_ignoresInvalidIndex() {
            val snippet = Snippet(id = 1, content = "x")
            val pocket = SnippetPocket()

            val result1 = pocket.setSlot(-1, snippet)
            val result2 = pocket.setSlot(100, snippet)

            assertTrue(result1.isEmpty)
            assertTrue(result2.isEmpty)
        }

        @Test
        @DisplayName("clearSlot clears specific slot")
        fun clearSlot_clearsSpecificSlot() {
            val pocket = SnippetPocket()
                .add(Snippet(id = 1, content = "first"))
                .add(Snippet(id = 2, content = "second"))
                .clearSlot(0)

            assertNull(pocket.get(0))
            assertEquals("first", pocket.get(1)?.content)
        }

        @Test
        @DisplayName("clearAll empties pocket")
        fun clearAll_emptiesPocket() {
            val pocket = SnippetPocket()
                .add(Snippet(id = 1, content = "one"))
                .add(Snippet(id = 2, content = "two"))
                .clearAll()

            assertTrue(pocket.isEmpty)
            assertEquals(0, pocket.usedSlots)
        }

        @Test
        @DisplayName("usedSlots counts non-null slots")
        fun usedSlots_countsNonNull() {
            val pocket = SnippetPocket()
                .setSlot(0, Snippet(1, "a"))
                .setSlot(5, Snippet(2, "b"))
                .setSlot(9, Snippet(3, "c"))

            assertEquals(3, pocket.usedSlots)
            assertEquals(7, pocket.emptySlots)
        }

        @Test
        @DisplayName("isFull detects when all slots used")
        fun isFull_detectsFullPocket() {
            var pocket = SnippetPocket(slots = List(3) { null }, maxSlots = 3)
            assertFalse(pocket.isFull)

            for (i in 0..2) {
                pocket = pocket.setSlot(i, Snippet(i, "x"))
            }
            assertTrue(pocket.isFull)
        }

        @Test
        @DisplayName("indexedSnippets returns pairs")
        fun indexedSnippets_returnsPairs() {
            val pocket = SnippetPocket()
                .setSlot(1, Snippet(1, "at 1"))
                .setSlot(7, Snippet(7, "at 7"))

            val indexed = pocket.indexedSnippets

            assertEquals(2, indexed.size)
            assertEquals(1, indexed[0].first)
            assertEquals(7, indexed[1].first)
        }

        @Test
        @DisplayName("firstEmptySlot finds first null")
        fun firstEmptySlot_findsFirstNull() {
            val pocket = SnippetPocket()
                .setSlot(0, Snippet(0, "x"))
                .setSlot(1, Snippet(1, "x"))

            assertEquals(2, pocket.firstEmptySlot)
        }

        @Test
        @DisplayName("firstEmptySlot returns null when full")
        fun firstEmptySlot_returnsNullWhenFull() {
            var pocket = SnippetPocket(slots = List(2) { null }, maxSlots = 2)
            pocket = pocket.setSlot(0, Snippet(0, "x"))
            pocket = pocket.setSlot(1, Snippet(1, "x"))

            assertNull(pocket.firstEmptySlot)
        }

        @Test
        @DisplayName("summary provides readable description")
        fun summary_providesDescription() {
            val pocket = SnippetPocket()
                .add(Snippet(1, "a"))
                .add(Snippet(2, "b"))

            assertEquals("2 of 10 slots used", pocket.summary)
        }
    }

    // =========================================================================
    // SnippetExport Tests
    // =========================================================================

    @Nested
    @DisplayName("SnippetExport")
    inner class SnippetExportTests {

        @Test
        @DisplayName("CURRENT_VERSION is defined")
        fun currentVersion_isDefined() {
            assertEquals(1, SnippetExport.CURRENT_VERSION)
        }

        @Test
        @DisplayName("count returns snippet count")
        fun count_returnsSnippetCount() {
            val export = SnippetExport(
                snippets = listOf(
                    Snippet(1, "a"),
                    Snippet(2, "b"),
                    Snippet(3, "c")
                )
            )

            assertEquals(3, export.count)
        }

        @Test
        @DisplayName("isCompatible checks version")
        fun isCompatible_checksVersion() {
            val compatible = SnippetExport(version = 1, snippets = emptyList())
            val futureVersion = SnippetExport(version = 99, snippets = emptyList())

            assertTrue(compatible.isCompatible)
            assertFalse(futureVersion.isCompatible)
        }
    }

    // =========================================================================
    // SnippetResult Tests
    // =========================================================================

    @Nested
    @DisplayName("SnippetResult")
    inner class SnippetResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val result = SnippetResult.Success(Snippet(1, "x"))
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("SlotEmpty has isSuccess false")
        fun slotEmpty_hasIsSuccessFalse() {
            val result = SnippetResult.SlotEmpty(5)
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = SnippetResult.Error("Something went wrong")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("pattern matching works correctly")
        fun patternMatching_worksCorrectly() {
            val results = listOf(
                SnippetResult.Success(Snippet(1, "x")),
                SnippetResult.SlotEmpty(5),
                SnippetResult.Error("fail")
            )

            val messages = results.map { result ->
                when (result) {
                    is SnippetResult.Success -> "success"
                    is SnippetResult.SlotEmpty -> "empty slot ${result.slot}"
                    is SnippetResult.Error -> "error: ${result.message}"
                }
            }

            assertEquals("success", messages[0])
            assertEquals("empty slot 5", messages[1])
            assertEquals("error: fail", messages[2])
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("empty content snippet is valid")
        fun emptyContent_isValid() {
            val snippet = Snippet(id = 1, content = "")

            assertEquals("", snippet.preview)
            assertEquals(1, snippet.lineCount)
            assertEquals(0, snippet.charCount)
        }

        @Test
        @DisplayName("very long content is handled")
        fun veryLongContent_isHandled() {
            val longContent = "x".repeat(100_000)
            val snippet = Snippet(id = 1, content = longContent)

            assertEquals(100, snippet.preview.length)
            assertEquals(100_000, snippet.charCount)
        }

        @Test
        @DisplayName("special characters in content are preserved")
        fun specialCharacters_arePreserved() {
            val content = "Unicode: 日本語 🚀 <script> &amp;"
            val snippet = Snippet(id = 1, content = content)

            assertEquals(content, snippet.content)
        }

        @Test
        @DisplayName("pocket with custom max slots works")
        fun customMaxSlots_works() {
            val pocket = SnippetPocket(slots = List(5) { null }, maxSlots = 5)

            assertEquals(5, pocket.slots.size)
            assertEquals(5, pocket.maxSlots)
        }

        @ParameterizedTest
        @DisplayName("slot indices 0-9 are valid")
        @ValueSource(ints = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9])
        fun slotIndices_areValid(slot: Int) {
            val snippet = Snippet(id = slot, content = "x")
            val pocket = SnippetPocket().setSlot(slot, snippet)

            assertEquals(snippet, pocket.get(slot))
        }
    }
}
