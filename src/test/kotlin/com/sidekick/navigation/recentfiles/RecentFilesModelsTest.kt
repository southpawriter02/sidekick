package com.sidekick.navigation.recentfiles

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Comprehensive unit tests for Recent Files Models.
 *
 * Tests cover:
 * - RecentFileEntry data class and computed properties
 * - DateCategory enum and categorization
 * - FileGrouping and FileSorting enums
 * - GridViewOptions data class
 * - FileGroup data class
 * - RecentFilesResult sealed class
 *
 * @since 0.4.4
 */
@DisplayName("Recent Files Models")
class RecentFilesModelsTest {

    // =========================================================================
    // RecentFileEntry Tests
    // =========================================================================

    @Nested
    @DisplayName("RecentFileEntry")
    inner class RecentFileEntryTests {

        @Test
        @DisplayName("displayName adds pin emoji when pinned")
        fun displayName_addsPinEmoji() {
            val pinned = RecentFileEntry(
                path = "/path/file.kt",
                name = "file.kt",
                pinned = true
            )
            val unpinned = RecentFileEntry(
                path = "/path/file.kt",
                name = "file.kt",
                pinned = false
            )

            assertTrue(pinned.displayName.startsWith("📌"))
            assertFalse(unpinned.displayName.startsWith("📌"))
        }

        @Test
        @DisplayName("baseName removes extension")
        fun baseName_removesExtension() {
            val entry = RecentFileEntry(
                path = "/path/MyClass.kt",
                name = "MyClass.kt"
            )

            assertEquals("MyClass", entry.baseName)
        }

        @Test
        @DisplayName("folderName extracts parent folder")
        fun folderName_extractsParentFolder() {
            val entry = RecentFileEntry(
                path = "/project/src/main/kotlin/file.kt",
                name = "file.kt"
            )

            assertEquals("kotlin", entry.folderName)
        }

        @Test
        @DisplayName("relativePath removes project path prefix")
        fun relativePath_removesProjectPrefix() {
            val entry = RecentFileEntry(
                path = "/project/src/main/file.kt",
                name = "file.kt",
                projectPath = "/project"
            )

            assertEquals("src/main/file.kt", entry.relativePath)
        }

        @Test
        @DisplayName("relativePath returns null when no project")
        fun relativePath_returnsNullWithoutProject() {
            val entry = RecentFileEntry(
                path = "/path/file.kt",
                name = "file.kt"
            )

            assertNull(entry.relativePath)
        }

        @Test
        @DisplayName("language maps common extensions")
        fun language_mapsCommonExtensions() {
            assertEquals("Kotlin", createEntry("kt").language)
            assertEquals("Java", createEntry("java").language)
            assertEquals("C#", createEntry("cs").language)
            assertEquals("Python", createEntry("py").language)
            assertEquals("JavaScript", createEntry("js").language)
            assertEquals("TypeScript", createEntry("ts").language)
        }

        @Test
        @DisplayName("language returns uppercase for unknown extensions")
        fun language_returnsUppercaseForUnknown() {
            val entry = createEntry("xyz")
            assertEquals("XYZ", entry.language)
        }

        @Test
        @DisplayName("iconHint provides appropriate emoji")
        fun iconHint_providesAppropriateEmoji() {
            assertEquals("🟣", createEntry("kt").iconHint)
            assertEquals("☕", createEntry("java").iconHint)
            assertEquals("🐍", createEntry("py").iconHint)
        }

        @Test
        @DisplayName("isTestFile detects test naming patterns")
        fun isTestFile_detectsTestPatterns() {
            assertTrue(
                RecentFileEntry("/src/test/MyTest.kt", "MyTest.kt").isTestFile
            )
            assertTrue(
                RecentFileEntry("/src/MyClassTest.kt", "MyClassTest.kt").isTestFile
            )
            assertTrue(
                RecentFileEntry("/tests/file.kt", "file.kt").isTestFile
            )
            assertFalse(
                RecentFileEntry("/src/main/Service.kt", "Service.kt").isTestFile
            )
        }

        @Test
        @DisplayName("summary includes open count and date")
        fun summary_includesOpenCountAndDate() {
            val entry = RecentFileEntry(
                path = "/file.kt",
                name = "file.kt",
                openCount = 5,
                pinned = true
            )

            assertTrue(entry.summary.contains("5 times"))
            assertTrue(entry.summary.contains("Pinned"))
        }

        @Test
        @DisplayName("dateCategory categorizes correctly")
        fun dateCategory_categorizesCorrectly() {
            val today = RecentFileEntry(
                path = "/file.kt",
                name = "file.kt",
                lastOpened = Instant.now()
            )
            val yesterday = RecentFileEntry(
                path = "/file.kt",
                name = "file.kt",
                lastOpened = Instant.now().minus(1, ChronoUnit.DAYS)
            )
            val old = RecentFileEntry(
                path = "/file.kt",
                name = "file.kt",
                lastOpened = Instant.now().minus(60, ChronoUnit.DAYS)
            )

            assertEquals(DateCategory.TODAY, today.dateCategory)
            assertEquals(DateCategory.YESTERDAY, yesterday.dateCategory)
            assertEquals(DateCategory.OLDER, old.dateCategory)
        }

        private fun createEntry(ext: String) = RecentFileEntry(
            path = "/path/file.$ext",
            name = "file.$ext",
            extension = ext
        )
    }

    // =========================================================================
    // DateCategory Tests
    // =========================================================================

    @Nested
    @DisplayName("DateCategory")
    inner class DateCategoryTests {

        @ParameterizedTest
        @DisplayName("all categories have display names")
        @EnumSource(DateCategory::class)
        fun allCategories_haveDisplayNames(category: DateCategory) {
            assertNotNull(category.displayName)
            assertTrue(category.displayName.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Today", DateCategory.TODAY.toString())
            assertEquals("Yesterday", DateCategory.YESTERDAY.toString())
        }
    }

    // =========================================================================
    // FileGrouping Tests
    // =========================================================================

    @Nested
    @DisplayName("FileGrouping")
    inner class FileGroupingTests {

        @ParameterizedTest
        @DisplayName("all groupings have display names")
        @EnumSource(FileGrouping::class)
        fun allGroupings_haveDisplayNames(grouping: FileGrouping) {
            assertNotNull(grouping.displayName)
            assertTrue(grouping.displayName.isNotBlank())
        }

        @Test
        @DisplayName("has expected grouping options")
        fun hasExpectedOptions() {
            assertEquals(5, FileGrouping.entries.size)
            assertNotNull(FileGrouping.BY_FOLDER)
            assertNotNull(FileGrouping.BY_DATE)
        }
    }

    // =========================================================================
    // FileSorting Tests
    // =========================================================================

    @Nested
    @DisplayName("FileSorting")
    inner class FileSortingTests {

        @ParameterizedTest
        @DisplayName("all sortings have display names")
        @EnumSource(FileSorting::class)
        fun allSortings_haveDisplayNames(sorting: FileSorting) {
            assertNotNull(sorting.displayName)
            assertTrue(sorting.displayName.isNotBlank())
        }

        @Test
        @DisplayName("has expected sorting options")
        fun hasExpectedOptions() {
            assertEquals(4, FileSorting.entries.size)
            assertNotNull(FileSorting.BY_RECENT)
            assertNotNull(FileSorting.BY_FREQUENCY)
        }
    }

    // =========================================================================
    // GridViewOptions Tests
    // =========================================================================

    @Nested
    @DisplayName("GridViewOptions")
    inner class GridViewOptionsTests {

        @Test
        @DisplayName("default options are reasonable")
        fun defaultOptions_areReasonable() {
            val options = GridViewOptions()

            assertEquals(FileGrouping.BY_FOLDER, options.grouping)
            assertEquals(FileSorting.BY_RECENT, options.sorting)
            assertTrue(options.showPreview)
            assertEquals(4, options.gridColumns)
            assertFalse(options.showHiddenFiles)
            assertTrue(options.showTestFiles)
            assertTrue(options.pinnedFirst)
        }

        @Test
        @DisplayName("withGrouping returns new options")
        fun withGrouping_returnsNewOptions() {
            val original = GridViewOptions()
            val modified = original.withGrouping(FileGrouping.BY_DATE)

            assertEquals(FileGrouping.BY_FOLDER, original.grouping)
            assertEquals(FileGrouping.BY_DATE, modified.grouping)
        }

        @Test
        @DisplayName("withSorting returns new options")
        fun withSorting_returnsNewOptions() {
            val original = GridViewOptions()
            val modified = original.withSorting(FileSorting.BY_FREQUENCY)

            assertEquals(FileSorting.BY_RECENT, original.sorting)
            assertEquals(FileSorting.BY_FREQUENCY, modified.sorting)
        }

        @Test
        @DisplayName("togglePreview flips preview setting")
        fun togglePreview_flipsPreviewSetting() {
            val original = GridViewOptions(showPreview = true)
            val toggled = original.togglePreview()

            assertTrue(original.showPreview)
            assertFalse(toggled.showPreview)
        }

        @Test
        @DisplayName("withColumns coerces to valid range")
        fun withColumns_coercesToValidRange() {
            val options = GridViewOptions()

            assertEquals(2, options.withColumns(1).gridColumns)
            assertEquals(5, options.withColumns(5).gridColumns)
            assertEquals(8, options.withColumns(10).gridColumns)
        }
    }

    // =========================================================================
    // FileGroup Tests
    // =========================================================================

    @Nested
    @DisplayName("FileGroup")
    inner class FileGroupTests {

        @Test
        @DisplayName("count returns file count")
        fun count_returnsFileCount() {
            val group = FileGroup(
                name = "Test",
                files = listOf(
                    RecentFileEntry("/a.kt", "a.kt"),
                    RecentFileEntry("/b.kt", "b.kt")
                )
            )

            assertEquals(2, group.count)
        }

        @Test
        @DisplayName("isEmpty detects empty group")
        fun isEmpty_detectsEmptyGroup() {
            val empty = FileGroup("Empty", emptyList())
            val notEmpty = FileGroup("Not Empty", listOf(RecentFileEntry("/a.kt", "a.kt")))

            assertTrue(empty.isEmpty)
            assertFalse(notEmpty.isEmpty)
        }

        @Test
        @DisplayName("toString provides readable description")
        fun toString_providesDescription() {
            val group = FileGroup(
                name = "Kotlin",
                files = listOf(
                    RecentFileEntry("/a.kt", "a.kt"),
                    RecentFileEntry("/b.kt", "b.kt")
                )
            )

            assertEquals("Kotlin (2 files)", group.toString())
        }
    }

    // =========================================================================
    // RecentFilesResult Tests
    // =========================================================================

    @Nested
    @DisplayName("RecentFilesResult")
    inner class RecentFilesResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val result = RecentFilesResult.Success(emptyList())
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Grouped has isSuccess true")
        fun grouped_hasIsSuccessTrue() {
            val result = RecentFilesResult.Grouped(emptyList())
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = RecentFilesResult.Error("Something went wrong")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("pattern matching works correctly")
        fun patternMatching_worksCorrectly() {
            val results = listOf(
                RecentFilesResult.Success(listOf(RecentFileEntry("/a.kt", "a.kt"))),
                RecentFilesResult.Grouped(listOf(FileGroup("Test", emptyList()))),
                RecentFilesResult.Error("fail")
            )

            val messages = results.map { result ->
                when (result) {
                    is RecentFilesResult.Success -> "success: ${result.entries.size}"
                    is RecentFilesResult.Grouped -> "grouped: ${result.groups.size}"
                    is RecentFilesResult.Error -> "error: ${result.message}"
                }
            }

            assertEquals("success: 1", messages[0])
            assertEquals("grouped: 1", messages[1])
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
        @DisplayName("handles files without extension")
        fun handlesFilesWithoutExtension() {
            val entry = RecentFileEntry(
                path = "/project/Makefile",
                name = "Makefile"
            )

            assertNull(entry.extension)
            assertNull(entry.language)
            assertEquals("📁", entry.iconHint)
        }

        @Test
        @DisplayName("handles very long paths")
        fun handlesVeryLongPaths() {
            val longPath = "/very/long/path/" + "subdir/".repeat(20) + "file.kt"
            val entry = RecentFileEntry(
                path = longPath,
                name = "file.kt"
            )

            assertEquals("file.kt", entry.name)
            assertEquals("subdir", entry.folderName)
        }

        @Test
        @DisplayName("handles special characters in names")
        fun handlesSpecialCharacters() {
            val entry = RecentFileEntry(
                path = "/project/my-file_v2.0.kt",
                name = "my-file_v2.0.kt"
            )

            assertEquals("my-file_v2.0", entry.baseName)
        }

        @ParameterizedTest
        @DisplayName("column count is coerced correctly")
        @ValueSource(ints = [0, 1, 2, 5, 8, 10, 100])
        fun columnCount_isCoercedCorrectly(columns: Int) {
            val options = GridViewOptions().withColumns(columns)
            assertTrue(options.gridColumns in 2..8)
        }
    }
}
