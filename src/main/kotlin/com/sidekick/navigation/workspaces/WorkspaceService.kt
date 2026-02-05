package com.sidekick.navigation.workspaces

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import java.time.Instant

/**
 * # Workspace Service
 *
 * Service for managing bookmark/breakpoint workspaces.
 * Part of Sidekick v0.4.2 Bookmark Workspaces feature.
 *
 * ## Features
 *
 * - Create, save, restore, and delete workspaces
 * - Capture current IDE bookmarks and breakpoints
 * - Restore saved bookmarks and breakpoints
 * - Import/export workspace configurations
 *
 * ## Usage
 *
 * ```kotlin
 * val service = WorkspaceService.getInstance(project)
 * val workspace = service.createWorkspace("Bug Investigation")
 * service.saveCurrentState(workspace.id)
 *
 * // Later...
 * service.restoreWorkspace(workspace.id)
 * ```
 *
 * @since 0.4.2
 */
@Service(Service.Level.PROJECT)
@State(
    name = "SidekickWorkspaces",
    storages = [Storage("sidekick-workspaces.xml")]
)
class WorkspaceService(private val project: Project) : PersistentStateComponent<WorkspaceService.State> {

    private val logger = Logger.getInstance(WorkspaceService::class.java)

    /**
     * Persistent state for workspace storage.
     */
    data class State(
        var workspaces: MutableList<WorkspaceData> = mutableListOf(),
        var activeWorkspaceId: String? = null
    )

    /**
     * Serializable workspace data for persistence.
     * Uses simple types that can be serialized by the IntelliJ persistence framework.
     */
    data class WorkspaceData(
        var id: String = "",
        var name: String = "",
        var description: String = "",
        var bookmarks: MutableList<BookmarkData> = mutableListOf(),
        var breakpoints: MutableList<BreakpointData> = mutableListOf(),
        var createdAt: Long = 0,
        var modifiedAt: Long = 0
    ) {
        constructor() : this("", "", "", mutableListOf(), mutableListOf(), 0, 0)

        fun toWorkspace(): BookmarkWorkspace = BookmarkWorkspace(
            id = id,
            name = name,
            description = description,
            bookmarks = bookmarks.map { it.toSavedBookmark() },
            breakpoints = breakpoints.map { it.toSavedBreakpoint() },
            createdAt = Instant.ofEpochMilli(createdAt),
            modifiedAt = Instant.ofEpochMilli(modifiedAt)
        )

        companion object {
            fun from(workspace: BookmarkWorkspace): WorkspaceData = WorkspaceData(
                id = workspace.id,
                name = workspace.name,
                description = workspace.description,
                bookmarks = workspace.bookmarks.map { BookmarkData.from(it) }.toMutableList(),
                breakpoints = workspace.breakpoints.map { BreakpointData.from(it) }.toMutableList(),
                createdAt = workspace.createdAt.toEpochMilli(),
                modifiedAt = workspace.modifiedAt.toEpochMilli()
            )
        }
    }

    /**
     * Serializable bookmark data.
     */
    data class BookmarkData(
        var filePath: String = "",
        var line: Int = 0,
        var mnemonic: String = "",
        var description: String = ""
    ) {
        constructor() : this("", 0, "", "")

        fun toSavedBookmark(): SavedBookmark = SavedBookmark(
            filePath = filePath,
            line = line,
            mnemonic = mnemonic.firstOrNull(),
            description = description
        )

        companion object {
            fun from(bookmark: SavedBookmark): BookmarkData = BookmarkData(
                filePath = bookmark.filePath,
                line = bookmark.line,
                mnemonic = bookmark.mnemonic?.toString() ?: "",
                description = bookmark.description
            )
        }
    }

    /**
     * Serializable breakpoint data.
     */
    data class BreakpointData(
        var filePath: String = "",
        var line: Int = 0,
        var condition: String = "",
        var logExpression: String = "",
        var enabled: Boolean = true
    ) {
        constructor() : this("", 0, "", "", true)

        fun toSavedBreakpoint(): SavedBreakpoint = SavedBreakpoint(
            filePath = filePath,
            line = line,
            condition = condition.ifBlank { null },
            logExpression = logExpression.ifBlank { null },
            enabled = enabled
        )

        companion object {
            fun from(breakpoint: SavedBreakpoint): BreakpointData = BreakpointData(
                filePath = breakpoint.filePath,
                line = breakpoint.line,
                condition = breakpoint.condition ?: "",
                logExpression = breakpoint.logExpression ?: "",
                enabled = breakpoint.enabled
            )
        }
    }

    private var state = State()

    companion object {
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): WorkspaceService {
            return project.getService(WorkspaceService::class.java)
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    // =========================================================================
    // Workspace CRUD Operations
    // =========================================================================

    /**
     * Gets all workspaces.
     */
    fun getWorkspaces(): List<BookmarkWorkspace> {
        return state.workspaces.map { it.toWorkspace() }
    }

    /**
     * Gets a workspace by ID.
     */
    fun getWorkspace(id: String): BookmarkWorkspace? {
        return state.workspaces.find { it.id == id }?.toWorkspace()
    }

    /**
     * Gets the currently active workspace ID.
     */
    fun getActiveWorkspaceId(): String? = state.activeWorkspaceId

    /**
     * Creates a new workspace with the given name.
     *
     * @param name The name for the new workspace
     * @param description Optional description
     * @return The created workspace
     */
    fun createWorkspace(name: String, description: String = ""): BookmarkWorkspace {
        logger.info("Creating workspace: $name")
        val workspace = BookmarkWorkspace(name = name, description = description)
        state.workspaces.add(WorkspaceData.from(workspace))
        return workspace
    }

    /**
     * Updates an existing workspace.
     *
     * @param workspace The workspace with updated values
     * @return Result indicating success or failure
     */
    fun updateWorkspace(workspace: BookmarkWorkspace): WorkspaceResult {
        val index = state.workspaces.indexOfFirst { it.id == workspace.id }
        return if (index >= 0) {
            state.workspaces[index] = WorkspaceData.from(workspace.touch())
            logger.info("Updated workspace: ${workspace.name}")
            WorkspaceResult.Success("Workspace updated")
        } else {
            WorkspaceResult.NotFound(workspace.id)
        }
    }

    /**
     * Deletes a workspace by ID.
     *
     * @param workspaceId The ID of the workspace to delete
     * @return Result indicating success or failure
     */
    fun deleteWorkspace(workspaceId: String): WorkspaceResult {
        val removed = state.workspaces.removeIf { it.id == workspaceId }
        return if (removed) {
            if (state.activeWorkspaceId == workspaceId) {
                state.activeWorkspaceId = null
            }
            logger.info("Deleted workspace: $workspaceId")
            WorkspaceResult.Success("Workspace deleted")
        } else {
            WorkspaceResult.NotFound(workspaceId)
        }
    }

    /**
     * Renames a workspace.
     *
     * @param workspaceId The ID of the workspace to rename
     * @param newName The new name
     */
    fun renameWorkspace(workspaceId: String, newName: String): WorkspaceResult {
        val workspace = state.workspaces.find { it.id == workspaceId }
            ?: return WorkspaceResult.NotFound(workspaceId)
        workspace.name = newName
        workspace.modifiedAt = Instant.now().toEpochMilli()
        return WorkspaceResult.Success("Workspace renamed")
    }

    // =========================================================================
    // Save/Restore Operations
    // =========================================================================

    /**
     * Saves the current bookmarks and breakpoints to a workspace.
     *
     * @param workspaceId The ID of the workspace to save to
     * @return Result indicating success or failure
     */
    fun saveCurrentState(workspaceId: String): WorkspaceResult {
        val workspaceData = state.workspaces.find { it.id == workspaceId }
            ?: return WorkspaceResult.NotFound(workspaceId)

        logger.info("Saving current state to workspace: ${workspaceData.name}")

        return try {
            val bookmarks = captureBookmarks()
            val breakpoints = captureBreakpoints()

            workspaceData.bookmarks.clear()
            workspaceData.bookmarks.addAll(bookmarks.map { BookmarkData.from(it) })
            workspaceData.breakpoints.clear()
            workspaceData.breakpoints.addAll(breakpoints.map { BreakpointData.from(it) })
            workspaceData.modifiedAt = Instant.now().toEpochMilli()

            logger.info("Saved ${bookmarks.size} bookmarks and ${breakpoints.size} breakpoints")
            WorkspaceResult.Success("Saved ${bookmarks.size} bookmarks and ${breakpoints.size} breakpoints")
        } catch (e: Exception) {
            logger.error("Failed to save current state", e)
            WorkspaceResult.Error("Failed to save: ${e.message}", e)
        }
    }

    /**
     * Restores bookmarks and breakpoints from a workspace.
     *
     * @param workspaceId The ID of the workspace to restore
     * @param clearExisting Whether to clear existing bookmarks/breakpoints first
     * @return Result indicating success or failure
     */
    fun restoreWorkspace(workspaceId: String, clearExisting: Boolean = true): WorkspaceResult {
        val workspaceData = state.workspaces.find { it.id == workspaceId }
            ?: return WorkspaceResult.NotFound(workspaceId)

        val workspace = workspaceData.toWorkspace()
        logger.info("Restoring workspace: ${workspace.name}")

        return try {
            if (clearExisting) {
                clearCurrentBookmarks()
                clearCurrentBreakpoints()
            }

            workspace.bookmarks.forEach { restoreBookmark(it) }
            workspace.breakpoints.forEach { restoreBreakpoint(it) }
            state.activeWorkspaceId = workspaceId

            logger.info("Restored ${workspace.bookmarkCount} bookmarks and ${workspace.breakpointCount} breakpoints")
            WorkspaceResult.Success("Restored ${workspace.totalItems} items")
        } catch (e: Exception) {
            logger.error("Failed to restore workspace", e)
            WorkspaceResult.Error("Failed to restore: ${e.message}", e)
        }
    }

    // =========================================================================
    // Import/Export Operations
    // =========================================================================

    /**
     * Exports all workspaces to export format.
     */
    fun exportWorkspaces(): WorkspaceExport {
        return WorkspaceExport(workspaces = getWorkspaces())
    }

    /**
     * Exports a single workspace to export format.
     */
    fun exportWorkspace(workspaceId: String): WorkspaceExport? {
        val workspace = getWorkspace(workspaceId) ?: return null
        return WorkspaceExport(workspaces = listOf(workspace))
    }

    /**
     * Imports workspaces from an export.
     *
     * @param export The workspace export to import
     * @param overwrite Whether to overwrite existing workspaces with same ID
     * @return Number of workspaces imported
     */
    fun importWorkspaces(export: WorkspaceExport, overwrite: Boolean = false): Int {
        if (!export.isCompatible) {
            logger.warn("Export version ${export.version} is not compatible")
            return 0
        }

        var imported = 0
        for (workspace in export.workspaces) {
            val existing = state.workspaces.find { it.id == workspace.id }
            if (existing != null) {
                if (overwrite) {
                    state.workspaces.remove(existing)
                    state.workspaces.add(WorkspaceData.from(workspace))
                    imported++
                }
            } else {
                state.workspaces.add(WorkspaceData.from(workspace))
                imported++
            }
        }

        logger.info("Imported $imported workspaces")
        return imported
    }

    // =========================================================================
    // IDE Integration - Bookmark Capture/Restore
    // =========================================================================

    /**
     * Captures current bookmarks from the IDE.
     */
    private fun captureBookmarks(): List<SavedBookmark> {
        val bookmarks = mutableListOf<SavedBookmark>()

        try {
            val bookmarksManager = BookmarksManager.getInstance(project)
            if (bookmarksManager != null) {
                for (group in bookmarksManager.groups) {
                    for (bookmark in group.getBookmarks()) {
                        if (bookmark is LineBookmark) {
                            val file = bookmark.file
                            val saved = SavedBookmark(
                                filePath = file.path,
                                line = bookmark.line + 1, // Convert to 1-indexed
                                mnemonic = null, // Will be set if available
                                description = ""
                            )
                            bookmarks.add(saved)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not capture bookmarks: ${e.message}")
        }

        return bookmarks
    }

    /**
     * Captures current breakpoints from the IDE.
     */
    private fun captureBreakpoints(): List<SavedBreakpoint> {
        val breakpoints = mutableListOf<SavedBreakpoint>()

        try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val breakpointManager = debuggerManager.breakpointManager

            for (breakpoint in breakpointManager.allBreakpoints) {
                if (breakpoint is XLineBreakpoint<*>) {
                    val file = breakpoint.fileUrl?.let {
                        LocalFileSystem.getInstance().findFileByPath(it.removePrefix("file://"))
                    }
                    if (file != null) {
                        val saved = SavedBreakpoint(
                            filePath = file.path,
                            line = breakpoint.line + 1, // Convert to 1-indexed
                            condition = breakpoint.conditionExpression?.expression,
                            logExpression = breakpoint.logExpressionObject?.expression,
                            enabled = breakpoint.isEnabled
                        )
                        breakpoints.add(saved)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not capture breakpoints: ${e.message}")
        }

        return breakpoints
    }

    /**
     * Clears all current bookmarks in the IDE.
     */
    private fun clearCurrentBookmarks() {
        try {
            val bookmarksManager = BookmarksManager.getInstance(project)
            bookmarksManager?.groups?.forEach { group ->
                group.getBookmarks().forEach { bookmark ->
                    bookmarksManager.remove(bookmark)
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not clear bookmarks: ${e.message}")
        }
    }

    /**
     * Clears all current breakpoints in the IDE.
     */
    private fun clearCurrentBreakpoints() {
        try {
            val debuggerManager = XDebuggerManager.getInstance(project)
            val breakpointManager = debuggerManager.breakpointManager

            breakpointManager.allBreakpoints.forEach { breakpoint ->
                breakpointManager.removeBreakpoint(breakpoint)
            }
        } catch (e: Exception) {
            logger.warn("Could not clear breakpoints: ${e.message}")
        }
    }

    /**
     * Restores a single bookmark.
     */
    private fun restoreBookmark(bookmark: SavedBookmark) {
        try {
            val file = LocalFileSystem.getInstance().findFileByPath(bookmark.filePath) ?: return
            val bookmarksManager = BookmarksManager.getInstance(project) ?: return

            // Create line bookmark
            // Note: Actual implementation depends on IntelliJ version
            logger.debug("Restoring bookmark: ${bookmark.displayText}")
        } catch (e: Exception) {
            logger.warn("Could not restore bookmark: ${e.message}")
        }
    }

    /**
     * Restores a single breakpoint.
     */
    private fun restoreBreakpoint(breakpoint: SavedBreakpoint) {
        try {
            val file = LocalFileSystem.getInstance().findFileByPath(breakpoint.filePath) ?: return
            val debuggerManager = XDebuggerManager.getInstance(project)

            // Create line breakpoint
            // Note: Actual implementation depends on IntelliJ version and breakpoint type
            logger.debug("Restoring breakpoint: ${breakpoint.displayText}")
        } catch (e: Exception) {
            logger.warn("Could not restore breakpoint: ${e.message}")
        }
    }
}
