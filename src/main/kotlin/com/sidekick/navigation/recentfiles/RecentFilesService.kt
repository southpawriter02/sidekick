package com.sidekick.navigation.recentfiles

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.time.Instant

/**
 * # Recent Files Service
 *
 * Application-level service for tracking recently opened files.
 * Part of Sidekick v0.4.4 Recent Files Grid feature.
 *
 * ## Features
 *
 * - Tracks files opened across all projects
 * - Records open count and last opened time
 * - Supports pinning files to the top
 * - Provides grouping and filtering options
 * - Persistent storage across IDE restarts
 *
 * ## Usage
 *
 * ```kotlin
 * val service = RecentFilesService.getInstance()
 *
 * // Record a file open
 * service.recordFileOpen(virtualFile, project)
 *
 * // Get recent files with options
 * service.getRecentFiles(GridViewOptions())
 *
 * // Pin a file
 * service.togglePin("/path/to/file.kt")
 * ```
 *
 * @since 0.4.4
 */
@Service(Service.Level.APP)
@State(
    name = "SidekickRecentFiles",
    storages = [Storage("sidekick-recent.xml")]
)
class RecentFilesService : PersistentStateComponent<RecentFilesService.State> {

    private val logger = Logger.getInstance(RecentFilesService::class.java)

    /**
     * Persistent state for recent files.
     */
    data class State(
        var entries: MutableList<FileEntryData> = mutableListOf(),
        var maxEntries: Int = DEFAULT_MAX_ENTRIES
    ) {
        constructor() : this(mutableListOf(), DEFAULT_MAX_ENTRIES)
    }

    /**
     * Serializable file entry for persistence.
     */
    data class FileEntryData(
        var path: String = "",
        var name: String = "",
        var extension: String = "",
        var projectPath: String = "",
        var lastOpened: Long = 0,
        var openCount: Int = 1,
        var pinned: Boolean = false
    ) {
        constructor() : this("", "", "", "", 0, 1, false)

        fun toEntry(): RecentFileEntry? {
            if (path.isEmpty()) return null
            return RecentFileEntry(
                path = path,
                name = name,
                extension = extension.ifBlank { null },
                projectPath = projectPath.ifBlank { null },
                lastOpened = if (lastOpened > 0) Instant.ofEpochMilli(lastOpened) else Instant.now(),
                openCount = openCount,
                pinned = pinned
            )
        }

        companion object {
            fun from(entry: RecentFileEntry) = FileEntryData(
                path = entry.path,
                name = entry.name,
                extension = entry.extension ?: "",
                projectPath = entry.projectPath ?: "",
                lastOpened = entry.lastOpened.toEpochMilli(),
                openCount = entry.openCount,
                pinned = entry.pinned
            )
        }
    }

    private var state = State()

    companion object {
        const val DEFAULT_MAX_ENTRIES = 50

        /**
         * Gets the singleton service instance.
         */
        fun getInstance(): RecentFilesService {
            return ApplicationManager.getApplication().getService(RecentFilesService::class.java)
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    // =========================================================================
    // Recording Operations
    // =========================================================================

    /**
     * Records that a file was opened.
     *
     * Updates the entry if it already exists (incrementing count),
     * or creates a new entry. Moves to front of list.
     *
     * @param file The opened file
     * @param project The project context
     */
    fun recordFileOpen(file: VirtualFile, project: Project) {
        val existing = state.entries.find { it.path == file.path }

        val entryData = if (existing != null) {
            existing.copy(
                lastOpened = Instant.now().toEpochMilli(),
                openCount = existing.openCount + 1
            )
        } else {
            FileEntryData(
                path = file.path,
                name = file.name,
                extension = file.extension ?: "",
                projectPath = project.basePath ?: "",
                lastOpened = Instant.now().toEpochMilli(),
                openCount = 1,
                pinned = false
            )
        }

        // Remove old entry and add to front
        state.entries.removeIf { it.path == file.path }
        state.entries.add(0, entryData)

        // Trim to max entries
        if (state.entries.size > state.maxEntries) {
            state.entries = state.entries.take(state.maxEntries).toMutableList()
        }

        logger.debug("Recorded file open: ${file.name}")
    }

    // =========================================================================
    // Retrieval Operations
    // =========================================================================

    /**
     * Gets recent files with the specified options.
     *
     * @param options View options for filtering and sorting
     * @return List of recent file entries
     */
    fun getRecentFiles(options: GridViewOptions = GridViewOptions()): List<RecentFileEntry> {
        val entries = state.entries.mapNotNull { it.toEntry() }

        // Filter
        val filtered = entries.filter { entry ->
            (options.showHiddenFiles || !entry.name.startsWith(".")) &&
            (options.showTestFiles || !entry.isTestFile)
        }

        // Sort
        val sorted = when (options.sorting) {
            FileSorting.BY_RECENT -> filtered.sortedByDescending { it.lastOpened }
            FileSorting.BY_FREQUENCY -> filtered.sortedByDescending { it.openCount }
            FileSorting.BY_NAME -> filtered.sortedBy { it.name.lowercase() }
            FileSorting.BY_EXTENSION -> filtered.sortedBy { it.extension ?: "" }
        }

        // Pinned first
        return if (options.pinnedFirst) {
            sorted.sortedByDescending { it.pinned }
        } else {
            sorted
        }
    }

    /**
     * Gets recent files grouped by the specified grouping option.
     *
     * @param options View options for grouping
     * @return List of file groups
     */
    fun getGroupedFiles(options: GridViewOptions = GridViewOptions()): List<FileGroup> {
        val files = getRecentFiles(options)

        return when (options.grouping) {
            FileGrouping.NONE -> listOf(FileGroup("All Files", files))
            FileGrouping.BY_FOLDER -> files.groupBy { it.folderName }
                .map { (folder, files) -> FileGroup(folder, files) }
                .sortedByDescending { it.count }
            FileGrouping.BY_PROJECT -> files.groupBy { it.projectPath ?: "Unknown" }
                .map { (project, files) -> FileGroup(project.substringAfterLast('/'), files) }
            FileGrouping.BY_EXTENSION -> files.groupBy { it.extension ?: "No Extension" }
                .map { (ext, files) -> FileGroup(ext.uppercase(), files) }
                .sortedByDescending { it.count }
            FileGrouping.BY_DATE -> files.groupBy { it.dateCategory }
                .map { (category, files) -> FileGroup(category.displayName, files) }
                .sortedBy { DateCategory.entries.indexOf(it.name.let { n -> 
                    DateCategory.entries.find { c -> c.displayName == n } ?: DateCategory.OLDER 
                })}
        }
    }

    /**
     * Gets all entries as a flat list.
     */
    fun getAllEntries(): List<RecentFileEntry> {
        return state.entries.mapNotNull { it.toEntry() }
    }

    /**
     * Gets the entry count.
     */
    fun getEntryCount(): Int = state.entries.size

    /**
     * Gets pinned files only.
     */
    fun getPinnedFiles(): List<RecentFileEntry> {
        return state.entries.mapNotNull { it.toEntry() }.filter { it.pinned }
    }

    // =========================================================================
    // Management Operations
    // =========================================================================

    /**
     * Toggles the pinned status of a file.
     */
    fun togglePin(path: String): Boolean {
        val entry = state.entries.find { it.path == path } ?: return false
        val index = state.entries.indexOf(entry)
        state.entries[index] = entry.copy(pinned = !entry.pinned)
        logger.info("Toggled pin for: $path")
        return true
    }

    /**
     * Pins a file.
     */
    fun pin(path: String): Boolean {
        val entry = state.entries.find { it.path == path } ?: return false
        val index = state.entries.indexOf(entry)
        state.entries[index] = entry.copy(pinned = true)
        return true
    }

    /**
     * Unpins a file.
     */
    fun unpin(path: String): Boolean {
        val entry = state.entries.find { it.path == path } ?: return false
        val index = state.entries.indexOf(entry)
        state.entries[index] = entry.copy(pinned = false)
        return true
    }

    /**
     * Removes a file from the recent list.
     */
    fun removeEntry(path: String): Boolean {
        val removed = state.entries.removeIf { it.path == path }
        if (removed) {
            logger.info("Removed entry: $path")
        }
        return removed
    }

    /**
     * Clears all entries.
     */
    fun clearAll() {
        state.entries.clear()
        logger.info("Cleared all recent files")
    }

    /**
     * Clears unpinned entries only.
     */
    fun clearUnpinned() {
        state.entries.removeIf { !it.pinned }
        logger.info("Cleared unpinned recent files")
    }

    /**
     * Sets the maximum number of entries.
     */
    fun setMaxEntries(max: Int) {
        state.maxEntries = max.coerceIn(10, 200)
        if (state.entries.size > state.maxEntries) {
            state.entries = state.entries.take(state.maxEntries).toMutableList()
        }
    }

    // =========================================================================
    // Query Operations
    // =========================================================================

    /**
     * Checks if a file is in the recent list.
     */
    fun hasEntry(path: String): Boolean {
        return state.entries.any { it.path == path }
    }

    /**
     * Gets a specific entry by path.
     */
    fun getEntry(path: String): RecentFileEntry? {
        return state.entries.find { it.path == path }?.toEntry()
    }

    /**
     * Searches entries by name.
     */
    fun search(query: String): List<RecentFileEntry> {
        if (query.isBlank()) return getAllEntries()

        val lowerQuery = query.lowercase()
        return state.entries.mapNotNull { it.toEntry() }
            .filter { entry ->
                entry.name.lowercase().contains(lowerQuery) ||
                entry.path.lowercase().contains(lowerQuery)
            }
    }
}
