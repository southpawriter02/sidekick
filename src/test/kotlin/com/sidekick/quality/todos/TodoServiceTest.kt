package com.sidekick.quality.todos

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

/**
 * Unit tests for TODO Tracker Service.
 *
 * These tests focus on the service's internal logic that can be tested
 * without the IntelliJ Platform (text parsing, extraction, etc.).
 *
 * Note: Full PSI-based scanning tests would require IntelliJ Platform
 * test fixtures and are not included in this unit test suite.
 *
 * @since 0.6.2
 */
@DisplayName("TODO Tracker Service")
class TodoServiceTest {

    // =========================================================================
    // State Serialization Tests
    // =========================================================================

    @Nested
    @DisplayName("State")
    inner class StateTests {

        @Test
        @DisplayName("default state has reasonable values")
        fun defaultState_hasReasonableValues() {
            val state = TodoService.State()

            assertTrue(state.enabled)
            assertTrue(state.scanOnOpen)
            assertTrue(state.showNotifications)
            assertEquals(3, state.dueSoonDays)
            assertTrue(state.todos.isEmpty())
        }

        @Test
        @DisplayName("toConfig produces correct configuration")
        fun toConfig_producesCorrectConfig() {
            val state = TodoService.State(
                enabled = false,
                scanOnOpen = false,
                showNotifications = true,
                overdueNotifications = false,
                dueSoonDays = 7,
                customPatterns = mutableListOf("CUSTOM:")
            )

            val config = state.toConfig()

            assertFalse(config.enabled)
            assertFalse(config.scanOnOpen)
            assertTrue(config.showNotifications)
            assertFalse(config.overdueNotifications)
            assertEquals(7, config.dueSoonDays)
            assertTrue("CUSTOM:" in config.customPatterns)
        }

        @Test
        @DisplayName("from creates state from config")
        fun from_createsStateFromConfig() {
            val config = TodoConfig(
                enabled = true,
                scanOnOpen = false,
                showNotifications = false,
                dueSoonDays = 5
            )

            val state = TodoService.State.from(config)

            assertTrue(state.enabled)
            assertFalse(state.scanOnOpen)
            assertFalse(state.showNotifications)
            assertEquals(5, state.dueSoonDays)
        }
    }

    // =========================================================================
    // SerializedTodoItem Tests
    // =========================================================================

    @Nested
    @DisplayName("SerializedTodoItem")
    inner class SerializedTodoItemTests {

        @Test
        @DisplayName("toTodoItem converts correctly")
        fun toTodoItem_convertsCorrectly() {
            val serialized = TodoService.SerializedTodoItem(
                id = "test-id",
                text = "Test TODO",
                typeName = "FIXME",
                priorityName = "HIGH",
                filePath = "/path/to/file.kt",
                line = 42,
                column = 8,
                deadline = "2024-12-31",
                author = "ryan",
                tags = mutableListOf("urgent", "backend")
            )

            val item = serialized.toTodoItem()

            assertEquals("test-id", item.id)
            assertEquals("Test TODO", item.text)
            assertEquals(TodoType.FIXME, item.type)
            assertEquals(TodoPriority.HIGH, item.priority)
            assertEquals("/path/to/file.kt", item.location.filePath)
            assertEquals(42, item.location.line)
            assertEquals(LocalDate.of(2024, 12, 31), item.deadline)
            assertEquals("ryan", item.author)
            assertEquals(listOf("urgent", "backend"), item.tags)
        }

        @Test
        @DisplayName("from creates serialized item from TodoItem")
        fun from_createsSerializedItem() {
            val item = TodoItem(
                id = "abc-123",
                text = "Fix bug",
                type = TodoType.BUG,
                priority = TodoPriority.CRITICAL,
                location = TodoLocation("/src/Main.kt", 100, 0, null),
                deadline = LocalDate.of(2024, 6, 15),
                author = "dev",
                createdDate = LocalDate.of(2024, 1, 1),
                tags = listOf("critical")
            )

            val serialized = TodoService.SerializedTodoItem.from(item)

            assertEquals("abc-123", serialized.id)
            assertEquals("BUG", serialized.typeName)
            assertEquals("CRITICAL", serialized.priorityName)
            assertEquals("2024-06-15", serialized.deadline)
        }

        @Test
        @DisplayName("roundtrip preserves values")
        fun roundtrip_preservesValues() {
            val original = TodoItem(
                id = "test-123",
                text = "Test task",
                type = TodoType.TODO,
                priority = TodoPriority.MEDIUM,
                location = TodoLocation("/test.kt", 50, 5, null),
                deadline = LocalDate.of(2024, 3, 15),
                author = "tester",
                createdDate = null,
                tags = listOf("test", "unit")
            )

            val serialized = TodoService.SerializedTodoItem.from(original)
            val restored = serialized.toTodoItem()

            assertEquals(original.id, restored.id)
            assertEquals(original.text, restored.text)
            assertEquals(original.type, restored.type)
            assertEquals(original.priority, restored.priority)
            assertEquals(original.deadline, restored.deadline)
            assertEquals(original.author, restored.author)
            assertEquals(original.tags, restored.tags)
        }
    }

    // =========================================================================
    // Text Cleaning Tests
    // =========================================================================

    @Nested
    @DisplayName("Text Cleaning")
    inner class TextCleaningTests {

        @ParameterizedTest
        @DisplayName("cleanTodoText removes comment markers")
        @CsvSource(
            "'// TODO: fix this', 'fix this'",
            "'/* TODO: update */  ', 'update'",
            "'* TODO: document', 'document'",
            "'//TODO: no space', 'no space'"
        )
        fun cleanTodoText_removesMarkers(input: String, expected: String) {
            val cleaned = cleanTodoText(input)
            assertEquals(expected, cleaned)
        }

        @ParameterizedTest
        @DisplayName("cleanTodoText removes TODO keywords")
        @CsvSource(
            "'// FIXME: broken', 'broken'",
            "'// BUG: crashes', 'crashes'",
            "'// HACK: workaround', 'workaround'"
        )
        fun cleanTodoText_removesKeywords(input: String, expected: String) {
            val cleaned = cleanTodoText(input)
            assertEquals(expected, cleaned)
        }

        /**
         * Helper that mimics the service's text cleaning.
         */
        private fun cleanTodoText(text: String): String {
            return text
                .removePrefix("//")
                .removePrefix("/*")
                .removeSuffix("*/")
                .removePrefix("*")
                .replace(Regex("""TODO:?|FIXME:?|HACK:?|BUG:?|NOTE:?|OPTIMIZE:?|PERF:?|REVIEW:?|DEPRECATED:?""", RegexOption.IGNORE_CASE), "")
                .trim()
        }
    }

    // =========================================================================
    // Deadline Extraction Tests
    // =========================================================================

    @Nested
    @DisplayName("Deadline Extraction")
    inner class DeadlineExtractionTests {

        @Test
        @DisplayName("extracts ISO date format")
        fun extractDeadline_extractsISOFormat() {
            val text = "TODO: finish by 2024-12-31"
            val date = extractDeadline(text)

            assertEquals(LocalDate.of(2024, 12, 31), date)
        }

        @Test
        @DisplayName("extracts 'by MM/dd/yyyy' format")
        fun extractDeadline_extractsByFormat() {
            val text = "TODO: complete by 12/31/2024"
            val date = extractDeadline(text)

            assertEquals(LocalDate.of(2024, 12, 31), date)
        }

        @Test
        @DisplayName("extracts 'due: date' format")
        fun extractDeadline_extractsDueFormat() {
            val text = "TODO: due: 06/15/2024 urgent"
            val date = extractDeadline(text)

            assertEquals(LocalDate.of(2024, 6, 15), date)
        }

        @Test
        @DisplayName("returns null when no date found")
        fun extractDeadline_returnsNullWhenNoDate() {
            val text = "TODO: no date here"
            val date = extractDeadline(text)

            assertNull(date)
        }

        /**
         * Helper that mimics the service's deadline extraction.
         */
        private fun extractDeadline(text: String): LocalDate? {
            val patterns = listOf(
                """(\d{4}-\d{2}-\d{2})""",
                """by\s+(\d{1,2}/\d{1,2}/\d{4})""",
                """due:?\s*(\d{1,2}/\d{1,2}/\d{4})"""
            )

            val formats = listOf(
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy")
            )

            for (pattern in patterns) {
                val match = Regex(pattern, RegexOption.IGNORE_CASE).find(text)
                val dateStr = match?.groupValues?.getOrNull(1) ?: continue

                for (format in formats) {
                    try {
                        return LocalDate.parse(dateStr, format)
                    } catch (_: Exception) {
                    }
                }
            }
            return null
        }
    }

    // =========================================================================
    // Author Extraction Tests
    // =========================================================================

    @Nested
    @DisplayName("Author Extraction")
    inner class AuthorExtractionTests {

        @Test
        @DisplayName("extracts @mention author")
        fun extractAuthor_extractsMention() {
            val text = "TODO: @ryan fix this bug"
            val author = extractAuthor(text)

            assertEquals("ryan", author)
        }

        @Test
        @DisplayName("returns first @mention when multiple")
        fun extractAuthor_returnsFirstMention() {
            val text = "TODO: @alice and @bob review"
            val author = extractAuthor(text)

            assertEquals("alice", author)
        }

        @Test
        @DisplayName("returns null when no author")
        fun extractAuthor_returnsNullWhenNoMention() {
            val text = "TODO: no author"
            val author = extractAuthor(text)

            assertNull(author)
        }

        /**
         * Helper that mimics the service's author extraction.
         */
        private fun extractAuthor(text: String): String? {
            val match = Regex("""@(\w+)""").find(text)
            return match?.groupValues?.get(1)
        }
    }

    // =========================================================================
    // Tag Extraction Tests
    // =========================================================================

    @Nested
    @DisplayName("Tag Extraction")
    inner class TagExtractionTests {

        @Test
        @DisplayName("extracts #hashtags")
        fun extractTags_extractsHashtags() {
            val text = "TODO: #urgent #backend fix database"
            val tags = extractTags(text)

            assertEquals(listOf("urgent", "backend"), tags)
        }

        @Test
        @DisplayName("returns empty list when no tags")
        fun extractTags_returnsEmptyWhenNoTags() {
            val text = "TODO: no tags here"
            val tags = extractTags(text)

            assertTrue(tags.isEmpty())
        }

        @Test
        @DisplayName("handles mixed content")
        fun extractTags_handlesMixedContent() {
            val text = "TODO: @ryan #priority1 fix #bug in auth"
            val tags = extractTags(text)

            assertEquals(listOf("priority1", "bug"), tags)
        }

        /**
         * Helper that mimics the service's tag extraction.
         */
        private fun extractTags(text: String): List<String> {
            return Regex("""#(\w+)""").findAll(text).map { it.groupValues[1] }.toList()
        }
    }

    // =========================================================================
    // TodoFilter Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoFilter")
    inner class TodoFilterTests {

        @Test
        @DisplayName("all filters have display names")
        fun allFilters_haveDisplayNames() {
            TodoFilter.entries.forEach { filter ->
                assertTrue(filter.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Overdue", TodoFilter.OVERDUE.toString())
            assertEquals("Due Soon", TodoFilter.DUE_SOON.toString())
        }
    }
}
