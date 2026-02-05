package com.sidekick.navigation.workspaces

import java.time.Instant
import java.util.UUID

/**
 * # Workspace Models
 *
 * Data structures for bookmark/breakpoint workspace management.
 * Part of Sidekick v0.4.2 Bookmark Workspaces feature.
 *
 * ## Overview
 *
 * These models support:
 * - Named collections of bookmarks and breakpoints
 * - Saving and restoring IDE navigation state
 * - Import/export of workspace configurations
 * - Workspace versioning for compatibility
 *
 * @since 0.4.2
 */

/**
 * A named collection of bookmarks and breakpoints.
 *
 * Workspaces allow developers to save the current state of their
 * bookmarks and breakpoints, then restore them later. This is useful
 * for switching between different tasks or debugging sessions.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val workspace = BookmarkWorkspace(
 *     name = "Auth Bug Investigation",
 *     description = "Bookmarks for auth flow debugging"
 * )
 *
 * // After capturing bookmarks...
 * val updated = workspace.copy(
 *     bookmarks = listOf(bookmark1, bookmark2),
 *     breakpoints = listOf(breakpoint1)
 * )
 * ```
 *
 * @property id Unique identifier for the workspace
 * @property name Human-readable name for the workspace
 * @property description Optional description of the workspace's purpose
 * @property bookmarks List of saved bookmarks in this workspace
 * @property breakpoints List of saved breakpoints in this workspace
 * @property createdAt Timestamp when the workspace was created
 * @property modifiedAt Timestamp when the workspace was last modified
 */
data class BookmarkWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val bookmarks: List<SavedBookmark> = emptyList(),
    val breakpoints: List<SavedBreakpoint> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val modifiedAt: Instant = Instant.now()
) {
    /**
     * Returns true if the workspace contains no bookmarks or breakpoints.
     */
    val isEmpty: Boolean get() = bookmarks.isEmpty() && breakpoints.isEmpty()

    /**
     * Total number of items (bookmarks + breakpoints) in the workspace.
     */
    val totalItems: Int get() = bookmarks.size + breakpoints.size

    /**
     * Number of bookmarks in the workspace.
     */
    val bookmarkCount: Int get() = bookmarks.size

    /**
     * Number of breakpoints in the workspace.
     */
    val breakpointCount: Int get() = breakpoints.size

    /**
     * Gets a short summary of the workspace contents.
     */
    val summary: String
        get() = when {
            isEmpty -> "Empty"
            else -> "${bookmarkCount} bookmarks, ${breakpointCount} breakpoints"
        }

    /**
     * Creates a copy with updated modification timestamp.
     */
    fun touch(): BookmarkWorkspace = copy(modifiedAt = Instant.now())

    /**
     * Returns formatted creation date for display.
     */
    val createdAtFormatted: String
        get() = createdAt.toString().substringBefore('T')

    /**
     * Returns formatted modification date for display.
     */
    val modifiedAtFormatted: String
        get() = modifiedAt.toString().substringBefore('T')

    override fun toString(): String = "Workspace($name, $summary)"
}

/**
 * A saved bookmark representing a location in source code.
 *
 * Captures all the information needed to restore a bookmark,
 * including the file, line number, mnemonic character, and description.
 *
 * @property filePath Absolute path to the bookmarked file
 * @property line Line number (1-indexed) of the bookmark
 * @property mnemonic Optional single-character mnemonic (0-9, A-Z)
 * @property description Optional description of the bookmark's purpose
 */
data class SavedBookmark(
    val filePath: String,
    val line: Int,
    val mnemonic: Char? = null,
    val description: String = ""
) {
    /**
     * Returns the file name without the path.
     */
    val fileName: String get() = filePath.substringAfterLast('/')

    /**
     * Returns a short display string for the bookmark.
     */
    val displayText: String
        get() = buildString {
            mnemonic?.let { append("[$it] ") }
            append("$fileName:$line")
            if (description.isNotEmpty()) {
                append(" - $description")
            }
        }

    /**
     * Returns true if the bookmark has a mnemonic assigned.
     */
    val hasMnemonic: Boolean get() = mnemonic != null

    /**
     * Validates that the bookmark has a valid line number.
     */
    val isValid: Boolean get() = line > 0 && filePath.isNotBlank()

    override fun toString(): String = displayText
}

/**
 * A saved breakpoint for debugging.
 *
 * Captures breakpoint configuration including condition expressions
 * and log expressions, allowing full restoration of debug settings.
 *
 * @property filePath Absolute path to the file containing the breakpoint
 * @property line Line number (1-indexed) of the breakpoint
 * @property condition Optional condition expression for conditional breakpoints
 * @property logExpression Optional expression to log when breakpoint is hit
 * @property enabled Whether the breakpoint is enabled when restored
 */
data class SavedBreakpoint(
    val filePath: String,
    val line: Int,
    val condition: String? = null,
    val logExpression: String? = null,
    val enabled: Boolean = true
) {
    /**
     * Returns the file name without the path.
     */
    val fileName: String get() = filePath.substringAfterLast('/')

    /**
     * Returns true if this is a conditional breakpoint.
     */
    val isConditional: Boolean get() = !condition.isNullOrBlank()

    /**
     * Returns true if this breakpoint logs when hit.
     */
    val isLogpoint: Boolean get() = !logExpression.isNullOrBlank()

    /**
     * Returns a short display string for the breakpoint.
     */
    val displayText: String
        get() = buildString {
            if (!enabled) append("○ ") else append("● ")
            append("$fileName:$line")
            if (isConditional) append(" [if: $condition]")
            if (isLogpoint) append(" [log: $logExpression]")
        }

    /**
     * Returns the breakpoint type as a string.
     */
    val breakpointType: String
        get() = when {
            isLogpoint -> "Logpoint"
            isConditional -> "Conditional"
            else -> "Line"
        }

    /**
     * Validates that the breakpoint has valid settings.
     */
    val isValid: Boolean get() = line > 0 && filePath.isNotBlank()

    override fun toString(): String = displayText
}

/**
 * Workspace export format for sharing/backup.
 *
 * Contains a version number for forward compatibility
 * and a list of workspaces to export.
 *
 * @property version Format version number (currently 1)
 * @property workspaces List of workspaces included in the export
 * @property exportedAt Timestamp when the export was created
 */
data class WorkspaceExport(
    val version: Int = CURRENT_VERSION,
    val workspaces: List<BookmarkWorkspace>,
    val exportedAt: Instant = Instant.now()
) {
    companion object {
        /** Current export format version */
        const val CURRENT_VERSION = 1
    }

    /**
     * Number of workspaces in the export.
     */
    val workspaceCount: Int get() = workspaces.size

    /**
     * Total number of bookmarks across all workspaces.
     */
    val totalBookmarks: Int get() = workspaces.sumOf { it.bookmarkCount }

    /**
     * Total number of breakpoints across all workspaces.
     */
    val totalBreakpoints: Int get() = workspaces.sumOf { it.breakpointCount }

    /**
     * Returns true if the export contains any workspaces.
     */
    val isNotEmpty: Boolean get() = workspaces.isNotEmpty()

    /**
     * Validates that the export version is compatible.
     */
    val isCompatible: Boolean get() = version <= CURRENT_VERSION

    /**
     * Gets a summary of the export contents.
     */
    val summary: String
        get() = "$workspaceCount workspaces, $totalBookmarks bookmarks, $totalBreakpoints breakpoints"
}

/**
 * Result of a workspace operation.
 */
sealed class WorkspaceResult {
    /**
     * Operation succeeded.
     */
    data class Success(val message: String) : WorkspaceResult()

    /**
     * Workspace not found.
     */
    data class NotFound(val workspaceId: String) : WorkspaceResult()

    /**
     * Operation failed with an error.
     */
    data class Error(val message: String, val cause: Throwable? = null) : WorkspaceResult()

    /**
     * Returns true if the operation succeeded.
     */
    val isSuccess: Boolean get() = this is Success
}
