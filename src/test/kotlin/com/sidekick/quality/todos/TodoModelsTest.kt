package com.sidekick.quality.todos

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

/**
 * Comprehensive unit tests for TODO Tracker Models.
 *
 * Tests cover:
 * - TodoItem properties and computed values
 * - TodoLocation formatting
 * - TodoType detection
 * - TodoPriority detection
 * - TodoStatus resolution
 * - TodoConfig behavior
 * - TodoSummary aggregation
 *
 * @since 0.6.2
 */
@DisplayName("TODO Tracker Models")
class TodoModelsTest {

    // =========================================================================
    // TodoItem Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoItem")
    inner class TodoItemTests {

        @Test
        @DisplayName("isOverdue returns true for past deadline")
        fun isOverdue_returnsTrueForPastDeadline() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().minusDays(1)
            )

            assertTrue(todo.isOverdue)
        }

        @Test
        @DisplayName("isOverdue returns false for future deadline")
        fun isOverdue_returnsFalseForFutureDeadline() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().plusDays(1)
            )

            assertFalse(todo.isOverdue)
        }

        @Test
        @DisplayName("isOverdue returns false when no deadline")
        fun isOverdue_returnsFalseWhenNoDeadline() {
            val todo = TodoItem.simple(text = "Test", deadline = null)

            assertFalse(todo.isOverdue)
        }

        @Test
        @DisplayName("daysUntilDue returns positive for future deadline")
        fun daysUntilDue_returnsPositiveForFuture() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().plusDays(5)
            )

            assertEquals(5L, todo.daysUntilDue)
        }

        @Test
        @DisplayName("daysUntilDue returns negative for past deadline")
        fun daysUntilDue_returnsNegativeForPast() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().minusDays(3)
            )

            assertEquals(-3L, todo.daysUntilDue)
        }

        @Test
        @DisplayName("daysUntilDue returns null when no deadline")
        fun daysUntilDue_returnsNullWhenNoDeadline() {
            val todo = TodoItem.simple(text = "Test", deadline = null)

            assertNull(todo.daysUntilDue)
        }

        @Test
        @DisplayName("status returns OVERDUE for past deadline")
        fun status_returnsOverdueForPastDeadline() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().minusDays(1)
            )

            assertEquals(TodoStatus.OVERDUE, todo.status)
        }

        @Test
        @DisplayName("status returns DUE_SOON for deadline within 3 days")
        fun status_returnsDueSoonForNearDeadline() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().plusDays(2)
            )

            assertEquals(TodoStatus.DUE_SOON, todo.status)
        }

        @Test
        @DisplayName("status returns OPEN for far deadline")
        fun status_returnsOpenForFarDeadline() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().plusDays(10)
            )

            assertEquals(TodoStatus.OPEN, todo.status)
        }

        @Test
        @DisplayName("status returns OPEN when no deadline")
        fun status_returnsOpenWhenNoDeadline() {
            val todo = TodoItem.simple(text = "Test", deadline = null)

            assertEquals(TodoStatus.OPEN, todo.status)
        }

        @Test
        @DisplayName("deadlineDisplay shows overdue message")
        fun deadlineDisplay_showsOverdueMessage() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().minusDays(5)
            )

            assertTrue(todo.deadlineDisplay.contains("Overdue"))
            assertTrue(todo.deadlineDisplay.contains("5"))
        }

        @Test
        @DisplayName("deadlineDisplay shows due today")
        fun deadlineDisplay_showsDueToday() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now()
            )

            assertEquals("Due today", todo.deadlineDisplay)
        }

        @Test
        @DisplayName("deadlineDisplay shows due tomorrow")
        fun deadlineDisplay_showsDueTomorrow() {
            val todo = TodoItem.simple(
                text = "Test",
                deadline = LocalDate.now().plusDays(1)
            )

            assertEquals("Due tomorrow", todo.deadlineDisplay)
        }
    }

    // =========================================================================
    // TodoLocation Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoLocation")
    inner class TodoLocationTests {

        @Test
        @DisplayName("fileName extracts file name from path")
        fun fileName_extractsFromPath() {
            val location = TodoLocation(
                filePath = "/Users/test/project/src/main/File.kt",
                line = 10,
                column = 0,
                contextSnippet = null
            )

            assertEquals("File.kt", location.fileName)
        }

        @Test
        @DisplayName("displayString formats as filename:line")
        fun displayString_formatsCorrectly() {
            val location = TodoLocation(
                filePath = "/path/to/Service.kt",
                line = 42,
                column = 8,
                contextSnippet = null
            )

            assertEquals("Service.kt:42", location.displayString)
        }
    }

    // =========================================================================
    // TodoType Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoType")
    inner class TodoTypeTests {

        @ParameterizedTest
        @DisplayName("all types have patterns")
        @EnumSource(TodoType::class)
        fun allTypes_havePatterns(type: TodoType) {
            assertTrue(type.patterns.isNotEmpty())
        }

        @ParameterizedTest
        @DisplayName("all types have icons")
        @EnumSource(TodoType::class)
        fun allTypes_haveIcons(type: TodoType) {
            assertTrue(type.icon.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("detect identifies correct type from patterns")
        @CsvSource(
            "TODO: fix this later, TODO",
            "FIXME: broken code, FIXME",
            "HACK: temporary workaround, HACK",
            "BUG: crashes on null, BUG",
            "NOTE: important detail, NOTE",
            "OPTIMIZE: slow query, OPTIMIZE",
            "PERF: needs caching, OPTIMIZE",
            "REVIEW: needs approval, REVIEW",
            "DEPRECATED: use newMethod, DEPRECATED"
        )
        fun detect_identifiesCorrectType(text: String, expected: String) {
            assertEquals(TodoType.valueOf(expected), TodoType.detect(text))
        }

        @Test
        @DisplayName("detect returns TODO for unknown text")
        fun detect_returnsTodoForUnknown() {
            assertEquals(TodoType.TODO, TodoType.detect("random text"))
        }

        @Test
        @DisplayName("containsPattern returns true for matching text")
        fun containsPattern_returnsTrueForMatch() {
            assertTrue(TodoType.containsPattern("// TODO: something"))
            assertTrue(TodoType.containsPattern("/* FIXME */"))
        }

        @Test
        @DisplayName("containsPattern returns false for non-matching text")
        fun containsPattern_returnsFalseForNoMatch() {
            assertFalse(TodoType.containsPattern("// just a comment"))
        }

        @Test
        @DisplayName("byName finds type case-insensitively")
        fun byName_findsTypeIgnoringCase() {
            assertEquals(TodoType.FIXME, TodoType.byName("fixme"))
            assertEquals(TodoType.FIXME, TodoType.byName("FIXME"))
        }
    }

    // =========================================================================
    // TodoPriority Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoPriority")
    inner class TodoPriorityTests {

        @ParameterizedTest
        @DisplayName("all priorities have valid weights")
        @EnumSource(TodoPriority::class)
        fun allPriorities_haveValidWeights(priority: TodoPriority) {
            assertTrue(priority.weight in 1..4)
        }

        @ParameterizedTest
        @DisplayName("detect identifies priority markers")
        @CsvSource(
            "CRITICAL: must fix now, CRITICAL",
            "!!! urgent issue, CRITICAL",
            "URGENT: fix asap, HIGH",
            "!! need attention, HIGH",
            "LOW: can defer, LOW",
            "MINOR: small issue, LOW",
            "normal todo, MEDIUM"
        )
        fun detect_identifiesPriorityMarkers(text: String, expected: String) {
            assertEquals(TodoPriority.valueOf(expected), TodoPriority.detect(text))
        }

        @Test
        @DisplayName("detect returns MEDIUM for unmarked text")
        fun detect_returnsMediumForUnmarked() {
            assertEquals(TodoPriority.MEDIUM, TodoPriority.detect("TODO: regular task"))
        }

        @Test
        @DisplayName("ALL returns priorities in weight order")
        fun all_returnsPrioritiesInWeightOrder() {
            val all = TodoPriority.ALL
            assertEquals(TodoPriority.CRITICAL, all[0])
            assertEquals(TodoPriority.LOW, all.last())
        }
    }

    // =========================================================================
    // TodoStatus Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoStatus")
    inner class TodoStatusTests {

        @ParameterizedTest
        @DisplayName("all statuses have display names")
        @EnumSource(TodoStatus::class)
        fun allStatuses_haveDisplayNames(status: TodoStatus) {
            assertTrue(status.displayName.isNotBlank())
        }

        @Test
        @DisplayName("isUrgent is true for OVERDUE and DUE_SOON")
        fun isUrgent_isTrueForUrgentStatuses() {
            assertTrue(TodoStatus.OVERDUE.isUrgent)
            assertTrue(TodoStatus.DUE_SOON.isUrgent)
            assertFalse(TodoStatus.OPEN.isUrgent)
            assertFalse(TodoStatus.COMPLETED.isUrgent)
        }

        @Test
        @DisplayName("byName finds status case-insensitively")
        fun byName_findsStatusIgnoringCase() {
            assertEquals(TodoStatus.OVERDUE, TodoStatus.byName("overdue"))
            assertEquals(TodoStatus.DUE_SOON, TodoStatus.byName("DUE_SOON"))
        }
    }

    // =========================================================================
    // TodoConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoConfig")
    inner class TodoConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = TodoConfig()

            assertTrue(config.enabled)
            assertTrue(config.scanOnOpen)
            assertTrue(config.showNotifications)
            assertEquals(3, config.dueSoonDays)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = TodoConfig(enabled = true)
            val toggled = config.toggle()

            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withPattern adds custom pattern")
        fun withPattern_addsPattern() {
            val config = TodoConfig()
            val updated = config.withPattern("CUSTOM:")

            assertTrue("CUSTOM:" in updated.customPatterns)
        }

        @Test
        @DisplayName("DISABLED preset is disabled")
        fun preset_disabledIsDisabled() {
            assertFalse(TodoConfig.DISABLED.enabled)
        }

        @Test
        @DisplayName("SILENT preset has no notifications")
        fun preset_silentHasNoNotifications() {
            val config = TodoConfig.SILENT

            assertFalse(config.showNotifications)
            assertFalse(config.overdueNotifications)
        }
    }

    // =========================================================================
    // TodoSummary Tests
    // =========================================================================

    @Nested
    @DisplayName("TodoSummary")
    inner class TodoSummaryTests {

        @Test
        @DisplayName("from computes correct counts")
        fun from_computesCorrectCounts() {
            val todos = listOf(
                TodoItem.simple("Test 1", TodoType.TODO, TodoPriority.HIGH, LocalDate.now().minusDays(1)),
                TodoItem.simple("Test 2", TodoType.FIXME, TodoPriority.CRITICAL, LocalDate.now().plusDays(1)),
                TodoItem.simple("Test 3", TodoType.TODO, TodoPriority.LOW, null)
            )

            val summary = TodoSummary.from(todos)

            assertEquals(3, summary.total)
            assertEquals(1, summary.overdue)
            assertEquals(1, summary.dueSoon)
        }

        @Test
        @DisplayName("from groups by type correctly")
        fun from_groupsByTypeCorrectly() {
            val todos = listOf(
                TodoItem.simple("Test 1", TodoType.TODO),
                TodoItem.simple("Test 2", TodoType.TODO),
                TodoItem.simple("Test 3", TodoType.FIXME)
            )

            val summary = TodoSummary.from(todos)

            assertEquals(2, summary.byType[TodoType.TODO])
            assertEquals(1, summary.byType[TodoType.FIXME])
        }

        @Test
        @DisplayName("from groups by priority correctly")
        fun from_groupsByPriorityCorrectly() {
            val todos = listOf(
                TodoItem.simple("Test 1", priority = TodoPriority.HIGH),
                TodoItem.simple("Test 2", priority = TodoPriority.HIGH),
                TodoItem.simple("Test 3", priority = TodoPriority.LOW)
            )

            val summary = TodoSummary.from(todos)

            assertEquals(2, summary.byPriority[TodoPriority.HIGH])
            assertEquals(1, summary.byPriority[TodoPriority.LOW])
        }

        @Test
        @DisplayName("hasUrgent returns true when overdue exists")
        fun hasUrgent_returnsTrueWhenOverdue() {
            val summary = TodoSummary(
                total = 1,
                overdue = 1,
                dueSoon = 0,
                byType = emptyMap(),
                byPriority = emptyMap()
            )

            assertTrue(summary.hasUrgent)
        }

        @Test
        @DisplayName("hasUrgent returns false when no urgent items")
        fun hasUrgent_returnsFalseWhenNoUrgent() {
            val summary = TodoSummary(
                total = 5,
                overdue = 0,
                dueSoon = 0,
                byType = emptyMap(),
                byPriority = emptyMap()
            )

            assertFalse(summary.hasUrgent)
        }

        @Test
        @DisplayName("EMPTY has zero counts")
        fun empty_hasZeroCounts() {
            val summary = TodoSummary.EMPTY

            assertEquals(0, summary.total)
            assertEquals(0, summary.overdue)
            assertEquals(0, summary.dueSoon)
        }
    }
}
