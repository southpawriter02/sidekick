package com.sidekick.navigation.workspaces

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

/**
 * Unit tests for WorkspaceService.
 *
 * These tests validate the service's workspace management
 * capabilities using the serializable data classes.
 * Full IDE integration is tested separately.
 *
 * @since 0.4.2
 */
@DisplayName("WorkspaceService")
class WorkspaceServiceTest {

    // =========================================================================
    // WorkspaceData Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkspaceData Serialization")
    inner class WorkspaceDataSerializationTests {

        @Test
        @DisplayName("converts to BookmarkWorkspace correctly")
        fun toWorkspace_convertsCorrectly() {
            val data = WorkspaceService.WorkspaceData(
                id = "test-id",
                name = "Test Workspace",
                description = "Test description",
                bookmarks = mutableListOf(
                    WorkspaceService.BookmarkData("/file.kt", 10, "A", "Desc")
                ),
                breakpoints = mutableListOf(
                    WorkspaceService.BreakpointData("/bp.kt", 20, "x > 0", "", true)
                ),
                createdAt = 1000L,
                modifiedAt = 2000L
            )

            val workspace = data.toWorkspace()

            assertEquals("test-id", workspace.id)
            assertEquals("Test Workspace", workspace.name)
            assertEquals("Test description", workspace.description)
            assertEquals(1, workspace.bookmarkCount)
            assertEquals(1, workspace.breakpointCount)
            assertEquals(Instant.ofEpochMilli(1000L), workspace.createdAt)
            assertEquals(Instant.ofEpochMilli(2000L), workspace.modifiedAt)
        }

        @Test
        @DisplayName("creates WorkspaceData from BookmarkWorkspace")
        fun from_createsCorrectly() {
            val workspace = BookmarkWorkspace(
                id = "ws-id",
                name = "My Workspace",
                description = "Description",
                bookmarks = listOf(SavedBookmark("/a.kt", 5, 'B', "Note")),
                breakpoints = listOf(SavedBreakpoint("/b.kt", 15, "y < 10", null, false)),
                createdAt = Instant.ofEpochMilli(3000L),
                modifiedAt = Instant.ofEpochMilli(4000L)
            )

            val data = WorkspaceService.WorkspaceData.from(workspace)

            assertEquals("ws-id", data.id)
            assertEquals("My Workspace", data.name)
            assertEquals("Description", data.description)
            assertEquals(1, data.bookmarks.size)
            assertEquals(1, data.breakpoints.size)
            assertEquals(3000L, data.createdAt)
            assertEquals(4000L, data.modifiedAt)
        }

        @Test
        @DisplayName("round-trip conversion preserves data")
        fun roundTrip_preservesData() {
            val original = BookmarkWorkspace(
                name = "Round Trip",
                bookmarks = listOf(
                    SavedBookmark("/file1.kt", 1, '1', "First"),
                    SavedBookmark("/file2.kt", 2, null, "Second")
                ),
                breakpoints = listOf(
                    SavedBreakpoint("/bp1.kt", 10, "a == b", "log(a)", true),
                    SavedBreakpoint("/bp2.kt", 20, null, null, false)
                )
            )

            val data = WorkspaceService.WorkspaceData.from(original)
            val restored = data.toWorkspace()

            assertEquals(original.name, restored.name)
            assertEquals(original.bookmarkCount, restored.bookmarkCount)
            assertEquals(original.breakpointCount, restored.breakpointCount)

            // Check first bookmark
            assertEquals(original.bookmarks[0].filePath, restored.bookmarks[0].filePath)
            assertEquals(original.bookmarks[0].mnemonic, restored.bookmarks[0].mnemonic)

            // Check first breakpoint
            assertEquals(original.breakpoints[0].condition, restored.breakpoints[0].condition)
            assertEquals(original.breakpoints[0].enabled, restored.breakpoints[0].enabled)
        }
    }

    // =========================================================================
    // BookmarkData Tests
    // =========================================================================

    @Nested
    @DisplayName("BookmarkData Serialization")
    inner class BookmarkDataSerializationTests {

        @Test
        @DisplayName("converts to SavedBookmark correctly")
        fun toSavedBookmark_convertsCorrectly() {
            val data = WorkspaceService.BookmarkData(
                filePath = "/path/to/file.kt",
                line = 42,
                mnemonic = "X",
                description = "Important spot"
            )

            val bookmark = data.toSavedBookmark()

            assertEquals("/path/to/file.kt", bookmark.filePath)
            assertEquals(42, bookmark.line)
            assertEquals('X', bookmark.mnemonic)
            assertEquals("Important spot", bookmark.description)
        }

        @Test
        @DisplayName("handles empty mnemonic")
        fun handlesEmptyMnemonic() {
            val data = WorkspaceService.BookmarkData(
                filePath = "/file.kt",
                line = 1,
                mnemonic = "",
                description = ""
            )

            val bookmark = data.toSavedBookmark()

            assertNull(bookmark.mnemonic)
        }

        @Test
        @DisplayName("creates BookmarkData from SavedBookmark")
        fun from_createsCorrectly() {
            val bookmark = SavedBookmark(
                filePath = "/src/Main.kt",
                line = 100,
                mnemonic = '5',
                description = "Entry point"
            )

            val data = WorkspaceService.BookmarkData.from(bookmark)

            assertEquals("/src/Main.kt", data.filePath)
            assertEquals(100, data.line)
            assertEquals("5", data.mnemonic)
            assertEquals("Entry point", data.description)
        }

        @Test
        @DisplayName("null mnemonic becomes empty string")
        fun nullMnemonic_becomesEmptyString() {
            val bookmark = SavedBookmark("/file.kt", 1, null, "")
            val data = WorkspaceService.BookmarkData.from(bookmark)

            assertEquals("", data.mnemonic)
        }
    }

    // =========================================================================
    // BreakpointData Tests
    // =========================================================================

    @Nested
    @DisplayName("BreakpointData Serialization")
    inner class BreakpointDataSerializationTests {

        @Test
        @DisplayName("converts to SavedBreakpoint correctly")
        fun toSavedBreakpoint_convertsCorrectly() {
            val data = WorkspaceService.BreakpointData(
                filePath = "/debug/Handler.kt",
                line = 55,
                condition = "count > 100",
                logExpression = "Count is: \$count",
                enabled = true
            )

            val breakpoint = data.toSavedBreakpoint()

            assertEquals("/debug/Handler.kt", breakpoint.filePath)
            assertEquals(55, breakpoint.line)
            assertEquals("count > 100", breakpoint.condition)
            assertEquals("Count is: \$count", breakpoint.logExpression)
            assertTrue(breakpoint.enabled)
        }

        @Test
        @DisplayName("empty condition becomes null")
        fun emptyCondition_becomesNull() {
            val data = WorkspaceService.BreakpointData(
                filePath = "/file.kt",
                line = 1,
                condition = "",
                logExpression = "",
                enabled = true
            )

            val breakpoint = data.toSavedBreakpoint()

            assertNull(breakpoint.condition)
            assertNull(breakpoint.logExpression)
        }

        @Test
        @DisplayName("creates BreakpointData from SavedBreakpoint")
        fun from_createsCorrectly() {
            val breakpoint = SavedBreakpoint(
                filePath = "/src/Service.kt",
                line = 200,
                condition = "x == null",
                logExpression = "x value: {x}",
                enabled = false
            )

            val data = WorkspaceService.BreakpointData.from(breakpoint)

            assertEquals("/src/Service.kt", data.filePath)
            assertEquals(200, data.line)
            assertEquals("x == null", data.condition)
            assertEquals("x value: {x}", data.logExpression)
            assertFalse(data.enabled)
        }

        @Test
        @DisplayName("null condition becomes empty string")
        fun nullCondition_becomesEmptyString() {
            val breakpoint = SavedBreakpoint("/file.kt", 1)
            val data = WorkspaceService.BreakpointData.from(breakpoint)

            assertEquals("", data.condition)
            assertEquals("", data.logExpression)
        }
    }

    // =========================================================================
    // State Tests
    // =========================================================================

    @Nested
    @DisplayName("State Management")
    inner class StateManagementTests {

        @Test
        @DisplayName("default state is empty")
        fun defaultState_isEmpty() {
            val state = WorkspaceService.State()

            assertTrue(state.workspaces.isEmpty())
            assertNull(state.activeWorkspaceId)
        }

        @Test
        @DisplayName("state supports multiple workspaces")
        fun state_supportsMultipleWorkspaces() {
            val state = WorkspaceService.State(
                workspaces = mutableListOf(
                    WorkspaceService.WorkspaceData(id = "1", name = "W1"),
                    WorkspaceService.WorkspaceData(id = "2", name = "W2"),
                    WorkspaceService.WorkspaceData(id = "3", name = "W3")
                ),
                activeWorkspaceId = "2"
            )

            assertEquals(3, state.workspaces.size)
            assertEquals("2", state.activeWorkspaceId)
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles empty workspace gracefully")
        fun handlesEmptyWorkspace() {
            val data = WorkspaceService.WorkspaceData(
                id = "empty",
                name = "Empty Workspace"
            )

            val workspace = data.toWorkspace()

            assertTrue(workspace.isEmpty)
            assertEquals(0, workspace.totalItems)
        }

        @Test
        @DisplayName("handles workspace with many items")
        fun handlesManyItems() {
            val bookmarks = (1..100).map {
                WorkspaceService.BookmarkData("/file$it.kt", it)
            }.toMutableList()

            val breakpoints = (1..50).map {
                WorkspaceService.BreakpointData("/bp$it.kt", it)
            }.toMutableList()

            val data = WorkspaceService.WorkspaceData(
                id = "large",
                name = "Large Workspace",
                bookmarks = bookmarks,
                breakpoints = breakpoints
            )

            val workspace = data.toWorkspace()

            assertEquals(100, workspace.bookmarkCount)
            assertEquals(50, workspace.breakpointCount)
            assertEquals(150, workspace.totalItems)
        }

        @Test
        @DisplayName("default constructor creates valid empty data")
        fun defaultConstructor_createsValidData() {
            val workspaceData = WorkspaceService.WorkspaceData()
            val bookmarkData = WorkspaceService.BookmarkData()
            val breakpointData = WorkspaceService.BreakpointData()

            assertEquals("", workspaceData.id)
            assertEquals("", bookmarkData.filePath)
            assertTrue(breakpointData.enabled)
        }
    }
}
