package com.sidekick.navigation.workspaces

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

/**
 * Comprehensive unit tests for Workspace Models.
 *
 * Tests cover:
 * - BookmarkWorkspace data class and computed properties
 * - SavedBookmark data class and display formatting
 * - SavedBreakpoint data class and type detection
 * - WorkspaceExport validation and compatibility
 * - WorkspaceResult sealed class hierarchy
 *
 * @since 0.4.2
 */
@DisplayName("Workspace Models")
class WorkspaceModelsTest {

    // =========================================================================
    // BookmarkWorkspace Tests
    // =========================================================================

    @Nested
    @DisplayName("BookmarkWorkspace")
    inner class BookmarkWorkspaceTests {

        @Test
        @DisplayName("isEmpty returns true for workspace with no items")
        fun isEmpty_returnsTrue_whenNoItems() {
            val workspace = BookmarkWorkspace(name = "Empty")

            assertTrue(workspace.isEmpty)
            assertEquals(0, workspace.totalItems)
        }

        @Test
        @DisplayName("isEmpty returns false when bookmarks exist")
        fun isEmpty_returnsFalse_whenBookmarksExist() {
            val workspace = BookmarkWorkspace(
                name = "With Bookmarks",
                bookmarks = listOf(
                    SavedBookmark(filePath = "/path/to/file.kt", line = 10)
                )
            )

            assertFalse(workspace.isEmpty)
            assertEquals(1, workspace.totalItems)
        }

        @Test
        @DisplayName("isEmpty returns false when breakpoints exist")
        fun isEmpty_returnsFalse_whenBreakpointsExist() {
            val workspace = BookmarkWorkspace(
                name = "With Breakpoints",
                breakpoints = listOf(
                    SavedBreakpoint(filePath = "/path/to/file.kt", line = 20)
                )
            )

            assertFalse(workspace.isEmpty)
            assertEquals(1, workspace.totalItems)
        }

        @Test
        @DisplayName("totalItems sums bookmarks and breakpoints")
        fun totalItems_sumsCorrectly() {
            val workspace = BookmarkWorkspace(
                name = "Mixed",
                bookmarks = listOf(
                    SavedBookmark(filePath = "/a.kt", line = 1),
                    SavedBookmark(filePath = "/b.kt", line = 2)
                ),
                breakpoints = listOf(
                    SavedBreakpoint(filePath = "/c.kt", line = 3)
                )
            )

            assertEquals(3, workspace.totalItems)
            assertEquals(2, workspace.bookmarkCount)
            assertEquals(1, workspace.breakpointCount)
        }

        @Test
        @DisplayName("summary provides readable description")
        fun summary_providesDescription() {
            val empty = BookmarkWorkspace(name = "Empty")
            assertEquals("Empty", empty.summary)

            val withItems = BookmarkWorkspace(
                name = "With Items",
                bookmarks = listOf(SavedBookmark("/a.kt", 1)),
                breakpoints = listOf(SavedBreakpoint("/b.kt", 2), SavedBreakpoint("/c.kt", 3))
            )
            assertEquals("1 bookmarks, 2 breakpoints", withItems.summary)
        }

        @Test
        @DisplayName("touch updates modifiedAt timestamp")
        fun touch_updatesModifiedAt() {
            val original = BookmarkWorkspace(
                name = "Original",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z")
            )

            val touched = original.touch()

            assertNotEquals(original.modifiedAt, touched.modifiedAt)
            assertTrue(touched.modifiedAt.isAfter(original.modifiedAt))
        }

        @Test
        @DisplayName("default ID is generated UUID")
        fun defaultId_isGeneratedUuid() {
            val w1 = BookmarkWorkspace(name = "W1")
            val w2 = BookmarkWorkspace(name = "W2")

            assertNotEquals(w1.id, w2.id)
            assertTrue(w1.id.length >= 32)
        }

        @Test
        @DisplayName("createdAtFormatted returns date portion")
        fun createdAtFormatted_returnsDatePortion() {
            val workspace = BookmarkWorkspace(
                name = "Test",
                createdAt = Instant.parse("2026-01-15T10:30:00Z")
            )

            assertEquals("2026-01-15", workspace.createdAtFormatted)
        }

        @Test
        @DisplayName("toString provides useful debug output")
        fun toString_providesDebugOutput() {
            val workspace = BookmarkWorkspace(
                name = "Debug Test",
                bookmarks = listOf(SavedBookmark("/a.kt", 1))
            )

            val str = workspace.toString()
            assertTrue(str.contains("Debug Test"))
            assertTrue(str.contains("bookmark"))
        }
    }

    // =========================================================================
    // SavedBookmark Tests
    // =========================================================================

    @Nested
    @DisplayName("SavedBookmark")
    inner class SavedBookmarkTests {

        @Test
        @DisplayName("fileName extracts just the file name")
        fun fileName_extractsFileName() {
            val bookmark = SavedBookmark(filePath = "/project/src/com/example/Service.kt", line = 42)

            assertEquals("Service.kt", bookmark.fileName)
        }

        @Test
        @DisplayName("displayText formats correctly without mnemonic")
        fun displayText_formatsWithoutMnemonic() {
            val bookmark = SavedBookmark(
                filePath = "/project/src/Service.kt",
                line = 42
            )

            assertEquals("Service.kt:42", bookmark.displayText)
        }

        @Test
        @DisplayName("displayText includes mnemonic when present")
        fun displayText_includesMnemonic() {
            val bookmark = SavedBookmark(
                filePath = "/project/src/Service.kt",
                line = 42,
                mnemonic = 'A'
            )

            assertTrue(bookmark.displayText.contains("[A]"))
            assertTrue(bookmark.displayText.contains("Service.kt:42"))
        }

        @Test
        @DisplayName("displayText includes description when present")
        fun displayText_includesDescription() {
            val bookmark = SavedBookmark(
                filePath = "/project/src/Service.kt",
                line = 42,
                description = "Entry point"
            )

            assertTrue(bookmark.displayText.contains("Entry point"))
        }

        @Test
        @DisplayName("hasMnemonic correctly detects mnemonic presence")
        fun hasMnemonic_detectsCorrectly() {
            val withMnemonic = SavedBookmark("/a.kt", 1, mnemonic = '5')
            val withoutMnemonic = SavedBookmark("/b.kt", 2)

            assertTrue(withMnemonic.hasMnemonic)
            assertFalse(withoutMnemonic.hasMnemonic)
        }

        @Test
        @DisplayName("isValid checks line and path")
        fun isValid_checksLineAndPath() {
            val valid = SavedBookmark("/valid.kt", 1)
            val invalidLine = SavedBookmark("/file.kt", 0)
            val invalidPath = SavedBookmark("", 1)

            assertTrue(valid.isValid)
            assertFalse(invalidLine.isValid)
            assertFalse(invalidPath.isValid)
        }

        @ParameterizedTest
        @DisplayName("mnemonic supports digits and letters")
        @ValueSource(chars = ['0', '5', '9', 'A', 'Z', 'a', 'z'])
        fun mnemonic_supportsDigitsAndLetters(mnemonic: Char) {
            val bookmark = SavedBookmark("/file.kt", 1, mnemonic = mnemonic)

            assertEquals(mnemonic, bookmark.mnemonic)
            assertTrue(bookmark.hasMnemonic)
        }
    }

    // =========================================================================
    // SavedBreakpoint Tests
    // =========================================================================

    @Nested
    @DisplayName("SavedBreakpoint")
    inner class SavedBreakpointTests {

        @Test
        @DisplayName("fileName extracts just the file name")
        fun fileName_extractsFileName() {
            val breakpoint = SavedBreakpoint(
                filePath = "/project/src/com/example/Handler.kt",
                line = 100
            )

            assertEquals("Handler.kt", breakpoint.fileName)
        }

        @Test
        @DisplayName("isConditional detects condition presence")
        fun isConditional_detectsCondition() {
            val conditional = SavedBreakpoint("/a.kt", 1, condition = "x > 10")
            val unconditional = SavedBreakpoint("/b.kt", 2)

            assertTrue(conditional.isConditional)
            assertFalse(unconditional.isConditional)
        }

        @Test
        @DisplayName("isLogpoint detects log expression presence")
        fun isLogpoint_detectsLogExpression() {
            val logpoint = SavedBreakpoint("/a.kt", 1, logExpression = "\"Value: \" + x")
            val regular = SavedBreakpoint("/b.kt", 2)

            assertTrue(logpoint.isLogpoint)
            assertFalse(regular.isLogpoint)
        }

        @Test
        @DisplayName("breakpointType returns correct type string")
        fun breakpointType_returnsCorrectType() {
            val line = SavedBreakpoint("/a.kt", 1)
            val conditional = SavedBreakpoint("/b.kt", 2, condition = "x > 0")
            val logpoint = SavedBreakpoint("/c.kt", 3, logExpression = "log")

            assertEquals("Line", line.breakpointType)
            assertEquals("Conditional", conditional.breakpointType)
            assertEquals("Logpoint", logpoint.breakpointType)
        }

        @Test
        @DisplayName("displayText shows enabled/disabled state")
        fun displayText_showsEnabledState() {
            val enabled = SavedBreakpoint("/a.kt", 1, enabled = true)
            val disabled = SavedBreakpoint("/b.kt", 2, enabled = false)

            assertTrue(enabled.displayText.startsWith("●"))
            assertTrue(disabled.displayText.startsWith("○"))
        }

        @Test
        @DisplayName("displayText includes condition when present")
        fun displayText_includesCondition() {
            val breakpoint = SavedBreakpoint(
                filePath = "/file.kt",
                line = 10,
                condition = "count == 5"
            )

            assertTrue(breakpoint.displayText.contains("[if: count == 5]"))
        }

        @Test
        @DisplayName("displayText includes log expression when present")
        fun displayText_includesLogExpression() {
            val breakpoint = SavedBreakpoint(
                filePath = "/file.kt",
                line = 10,
                logExpression = "Result: \$result"
            )

            assertTrue(breakpoint.displayText.contains("[log:"))
        }

        @Test
        @DisplayName("isValid checks line and path")
        fun isValid_checksLineAndPath() {
            val valid = SavedBreakpoint("/valid.kt", 1)
            val invalidLine = SavedBreakpoint("/file.kt", -1)
            val invalidPath = SavedBreakpoint("", 1)

            assertTrue(valid.isValid)
            assertFalse(invalidLine.isValid)
            assertFalse(invalidPath.isValid)
        }
    }

    // =========================================================================
    // WorkspaceExport Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkspaceExport")
    inner class WorkspaceExportTests {

        @Test
        @DisplayName("CURRENT_VERSION is defined")
        fun currentVersion_isDefined() {
            assertEquals(1, WorkspaceExport.CURRENT_VERSION)
        }

        @Test
        @DisplayName("workspaceCount returns correct count")
        fun workspaceCount_returnsCorrectCount() {
            val export = WorkspaceExport(
                workspaces = listOf(
                    BookmarkWorkspace(name = "W1"),
                    BookmarkWorkspace(name = "W2"),
                    BookmarkWorkspace(name = "W3")
                )
            )

            assertEquals(3, export.workspaceCount)
        }

        @Test
        @DisplayName("totalBookmarks sums across all workspaces")
        fun totalBookmarks_sumsAcrossWorkspaces() {
            val export = WorkspaceExport(
                workspaces = listOf(
                    BookmarkWorkspace(name = "W1", bookmarks = listOf(
                        SavedBookmark("/a.kt", 1),
                        SavedBookmark("/b.kt", 2)
                    )),
                    BookmarkWorkspace(name = "W2", bookmarks = listOf(
                        SavedBookmark("/c.kt", 3)
                    ))
                )
            )

            assertEquals(3, export.totalBookmarks)
        }

        @Test
        @DisplayName("totalBreakpoints sums across all workspaces")
        fun totalBreakpoints_sumsAcrossWorkspaces() {
            val export = WorkspaceExport(
                workspaces = listOf(
                    BookmarkWorkspace(name = "W1", breakpoints = listOf(
                        SavedBreakpoint("/a.kt", 1)
                    )),
                    BookmarkWorkspace(name = "W2", breakpoints = listOf(
                        SavedBreakpoint("/b.kt", 2),
                        SavedBreakpoint("/c.kt", 3)
                    ))
                )
            )

            assertEquals(3, export.totalBreakpoints)
        }

        @Test
        @DisplayName("isNotEmpty detects workspaces presence")
        fun isNotEmpty_detectsWorkspaces() {
            val empty = WorkspaceExport(workspaces = emptyList())
            val notEmpty = WorkspaceExport(workspaces = listOf(BookmarkWorkspace(name = "W1")))

            assertFalse(empty.isNotEmpty)
            assertTrue(notEmpty.isNotEmpty)
        }

        @Test
        @DisplayName("isCompatible checks version")
        fun isCompatible_checksVersion() {
            val compatible = WorkspaceExport(version = 1, workspaces = emptyList())
            val futureVersion = WorkspaceExport(version = 99, workspaces = emptyList())

            assertTrue(compatible.isCompatible)
            assertFalse(futureVersion.isCompatible)
        }

        @Test
        @DisplayName("summary provides readable description")
        fun summary_providesDescription() {
            val export = WorkspaceExport(
                workspaces = listOf(
                    BookmarkWorkspace(
                        name = "W1",
                        bookmarks = listOf(SavedBookmark("/a.kt", 1)),
                        breakpoints = listOf(SavedBreakpoint("/b.kt", 2))
                    )
                )
            )

            assertEquals("1 workspaces, 1 bookmarks, 1 breakpoints", export.summary)
        }
    }

    // =========================================================================
    // WorkspaceResult Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkspaceResult")
    inner class WorkspaceResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val result = WorkspaceResult.Success("Done")

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("NotFound has isSuccess false")
        fun notFound_hasIsSuccessFalse() {
            val result = WorkspaceResult.NotFound("missing-id")

            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = WorkspaceResult.Error("Something went wrong")

            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error can include cause")
        fun error_canIncludeCause() {
            val cause = RuntimeException("Root cause")
            val result = WorkspaceResult.Error("Failed", cause)

            assertEquals("Failed", result.message)
            assertEquals(cause, result.cause)
        }

        @Test
        @DisplayName("pattern matching works correctly")
        fun patternMatching_worksCorrectly() {
            val results = listOf(
                WorkspaceResult.Success("ok"),
                WorkspaceResult.NotFound("id"),
                WorkspaceResult.Error("fail")
            )

            val messages = results.map { result ->
                when (result) {
                    is WorkspaceResult.Success -> "success: ${result.message}"
                    is WorkspaceResult.NotFound -> "not found: ${result.workspaceId}"
                    is WorkspaceResult.Error -> "error: ${result.message}"
                }
            }

            assertEquals(3, messages.size)
            assertTrue(messages[0].startsWith("success"))
            assertTrue(messages[1].startsWith("not found"))
            assertTrue(messages[2].startsWith("error"))
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("empty workspace name is allowed")
        fun emptyName_isAllowed() {
            val workspace = BookmarkWorkspace(name = "")
            assertEquals("", workspace.name)
        }

        @Test
        @DisplayName("very long file paths are handled")
        fun longFilePaths_areHandled() {
            val longPath = "/very/long/path/" + "subdir/".repeat(50) + "file.kt"
            val bookmark = SavedBookmark(filePath = longPath, line = 1)

            assertEquals("file.kt", bookmark.fileName)
            assertTrue(bookmark.isValid)
        }

        @Test
        @DisplayName("special characters in description are preserved")
        fun specialCharacters_arePreserved() {
            val bookmark = SavedBookmark(
                filePath = "/file.kt",
                line = 1,
                description = "Unicode: 日本語 🚀 <script>"
            )

            assertEquals("Unicode: 日本語 🚀 <script>", bookmark.description)
        }

        @Test
        @DisplayName("workspace with many items performs well")
        fun manyItems_performsWell() {
            val bookmarks = (1..1000).map { SavedBookmark("/file$it.kt", it) }
            val breakpoints = (1..500).map { SavedBreakpoint("/bp$it.kt", it) }

            val workspace = BookmarkWorkspace(
                name = "Large",
                bookmarks = bookmarks,
                breakpoints = breakpoints
            )

            assertEquals(1500, workspace.totalItems)
            assertEquals("1000 bookmarks, 500 breakpoints", workspace.summary)
        }
    }
}
