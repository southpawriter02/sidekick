package com.sidekick.navigation.recentfiles

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Unit tests for RecentFilesService serialization and state management.
 *
 * Tests cover:
 * - FileEntryData serialization round-trip
 * - State to entry list conversion
 * - Edge cases for empty/null handling
 *
 * @since 0.4.4
 */
@DisplayName("RecentFilesService")
class RecentFilesServiceTest {

    // =========================================================================
    // FileEntryData Tests
    // =========================================================================

    @Nested
    @DisplayName("FileEntryData Serialization")
    inner class FileEntryDataSerializationTests {

        @Test
        @DisplayName("converts to RecentFileEntry correctly")
        fun toEntry_convertsCorrectly() {
            val data = RecentFilesService.FileEntryData(
                path = "/project/src/Main.kt",
                name = "Main.kt",
                extension = "kt",
                projectPath = "/project",
                lastOpened = 1704067200000L,
                openCount = 5,
                pinned = true
            )

            val entry = data.toEntry()!!

            assertEquals("/project/src/Main.kt", entry.path)
            assertEquals("Main.kt", entry.name)
            assertEquals("kt", entry.extension)
            assertEquals("/project", entry.projectPath)
            assertEquals(5, entry.openCount)
            assertTrue(entry.pinned)
        }

        @Test
        @DisplayName("empty path returns null entry")
        fun emptyPath_returnsNull() {
            val data = RecentFilesService.FileEntryData(path = "")
            assertNull(data.toEntry())
        }

        @Test
        @DisplayName("creates FileEntryData from RecentFileEntry")
        fun from_createsCorrectly() {
            val entry = RecentFileEntry(
                path = "/src/file.kt",
                name = "file.kt",
                extension = "kt",
                projectPath = "/project",
                lastOpened = Instant.ofEpochMilli(1000000L),
                openCount = 10,
                pinned = true
            )

            val data = RecentFilesService.FileEntryData.from(entry)

            assertEquals("/src/file.kt", data.path)
            assertEquals("file.kt", data.name)
            assertEquals("kt", data.extension)
            assertEquals("/project", data.projectPath)
            assertEquals(1000000L, data.lastOpened)
            assertEquals(10, data.openCount)
            assertTrue(data.pinned)
        }

        @Test
        @DisplayName("null extension becomes empty string")
        fun nullExtension_becomesEmptyString() {
            val entry = RecentFileEntry(
                path = "/Makefile",
                name = "Makefile"
            )
            val data = RecentFilesService.FileEntryData.from(entry)

            assertEquals("", data.extension)
        }

        @Test
        @DisplayName("null projectPath becomes empty string")
        fun nullProjectPath_becomesEmptyString() {
            val entry = RecentFileEntry(
                path = "/file.kt",
                name = "file.kt"
            )
            val data = RecentFilesService.FileEntryData.from(entry)

            assertEquals("", data.projectPath)
        }

        @Test
        @DisplayName("blank extension becomes null in Entry")
        fun blankExtension_becomesNullInEntry() {
            val data = RecentFilesService.FileEntryData(
                path = "/file",
                name = "file",
                extension = ""
            )

            val entry = data.toEntry()!!
            assertNull(entry.extension)
        }

        @Test
        @DisplayName("round-trip conversion preserves data")
        fun roundTrip_preservesData() {
            val original = RecentFileEntry(
                path = "/project/src/Service.kt",
                name = "Service.kt",
                extension = "kt",
                projectPath = "/project",
                openCount = 7,
                pinned = true
            )

            val data = RecentFilesService.FileEntryData.from(original)
            val restored = data.toEntry()!!

            assertEquals(original.path, restored.path)
            assertEquals(original.name, restored.name)
            assertEquals(original.extension, restored.extension)
            assertEquals(original.projectPath, restored.projectPath)
            assertEquals(original.openCount, restored.openCount)
            assertEquals(original.pinned, restored.pinned)
        }
    }

    // =========================================================================
    // State Tests
    // =========================================================================

    @Nested
    @DisplayName("State Management")
    inner class StateManagementTests {

        @Test
        @DisplayName("default state has empty entries list")
        fun defaultState_hasEmptyList() {
            val state = RecentFilesService.State()

            assertTrue(state.entries.isEmpty())
            assertEquals(RecentFilesService.DEFAULT_MAX_ENTRIES, state.maxEntries)
        }

        @Test
        @DisplayName("state can hold multiple entries")
        fun stateCanHoldMultipleEntries() {
            val state = RecentFilesService.State(
                entries = mutableListOf(
                    RecentFilesService.FileEntryData(
                        path = "/a.kt",
                        name = "a.kt",
                        extension = "kt"
                    ),
                    RecentFilesService.FileEntryData(
                        path = "/b.kt",
                        name = "b.kt",
                        extension = "kt"
                    )
                )
            )

            assertEquals(2, state.entries.size)
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
            val state = RecentFilesService.State()
            assertTrue(state.entries.isEmpty())
        }

        @Test
        @DisplayName("default constructor creates valid empty data")
        fun defaultConstructor_createsValidData() {
            val data = RecentFilesService.FileEntryData()

            assertEquals("", data.path)
            assertEquals("", data.name)
            assertNull(data.toEntry())
        }

        @Test
        @DisplayName("state with many entries works")
        fun manyEntries_works() {
            val entries = (1..50).map {
                RecentFilesService.FileEntryData(
                    path = "/file$it.kt",
                    name = "file$it.kt",
                    extension = "kt"
                )
            }.toMutableList()

            val state = RecentFilesService.State(entries = entries)

            assertEquals(50, state.entries.size)
        }

        @Test
        @DisplayName("very long path is preserved")
        fun longPath_isPreserved() {
            val longPath = "/very/long/path/" + "subdir/".repeat(20) + "file.kt"
            val data = RecentFilesService.FileEntryData(
                path = longPath,
                name = "file.kt",
                extension = "kt"
            )

            val entry = data.toEntry()!!
            assertEquals(longPath, entry.path)
        }

        @Test
        @DisplayName("special characters are preserved")
        fun specialCharacters_arePreserved() {
            val special = "my-file_v2.0.kt"
            val data = RecentFilesService.FileEntryData(
                path = "/project/$special",
                name = special,
                extension = "kt"
            )

            val entry = data.toEntry()!!
            assertEquals(special, entry.name)
        }

        @Test
        @DisplayName("zero lastOpened uses current time")
        fun zeroLastOpened_usesCurrentTime() {
            val data = RecentFilesService.FileEntryData(
                path = "/file.kt",
                name = "file.kt",
                lastOpened = 0
            )

            val entry = data.toEntry()!!
            // Should be close to now (within last minute)
            val diff = Instant.now().toEpochMilli() - entry.lastOpened.toEpochMilli()
            assertTrue(diff < 60000)
        }
    }
}
