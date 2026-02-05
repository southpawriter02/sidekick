package com.sidekick.navigation.recentfiles

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * # Recent Files Models
 *
 * Data structures for the visual recent files grid.
 * Part of Sidekick v0.4.4 Recent Files Grid feature.
 *
 * ## Overview
 *
 * These models support:
 * - Tracking recently opened files with metadata
 * - Grouping by folder, project, extension, or date
 * - Pinning frequently used files
 * - Configurable grid display options
 *
 * @since 0.4.4
 */

/**
 * A recent file entry with metadata.
 *
 * Tracks when files were opened and how frequently,
 * enabling smart sorting and display in the grid view.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val entry = RecentFileEntry(
 *     path = "/project/src/Main.kt",
 *     name = "Main.kt",
 *     extension = "kt",
 *     projectPath = "/project",
 *     lastOpened = Instant.now()
 * )
 * println(entry.displayName) // "Main.kt"
 * ```
 *
 * @property path Absolute file path
 * @property name File name with extension
 * @property extension File extension (without dot), null for extensionless files
 * @property projectPath Project base path, null if unknown
 * @property lastOpened Timestamp when file was last opened
 * @property openCount Number of times the file has been opened
 * @property pinned Whether the file is pinned to the top
 */
data class RecentFileEntry(
    val path: String,
    val name: String,
    val extension: String? = null,
    val projectPath: String? = null,
    val lastOpened: Instant = Instant.now(),
    val openCount: Int = 1,
    val pinned: Boolean = false
) {
    /**
     * Display name with pin indicator if pinned.
     */
    val displayName: String
        get() = if (pinned) "📌 $name" else name

    /**
     * Short display name (without extension).
     */
    val baseName: String
        get() = name.substringBeforeLast('.')

    /**
     * Parent folder name.
     */
    val folderName: String
        get() = path.substringBeforeLast('/').substringAfterLast('/')

    /**
     * Relative path from project root.
     */
    val relativePath: String?
        get() = projectPath?.let { path.removePrefix(it).removePrefix("/") }

    /**
     * Formatted last opened date.
     */
    val lastOpenedFormatted: String
        get() = lastOpened.toString().substringBefore('T')

    /**
     * Formatted last opened time.
     */
    val lastOpenedTime: String
        get() = lastOpened.toString().substringAfter('T').substringBefore('.')

    /**
     * Date category for grouping (Today, Yesterday, This Week, etc.).
     */
    val dateCategory: DateCategory
        get() {
            val fileDate = lastOpened.atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()

            return when {
                fileDate == today -> DateCategory.TODAY
                fileDate == today.minusDays(1) -> DateCategory.YESTERDAY
                fileDate.isAfter(today.minusDays(7)) -> DateCategory.THIS_WEEK
                fileDate.isAfter(today.minusDays(30)) -> DateCategory.THIS_MONTH
                else -> DateCategory.OLDER
            }
        }

    /**
     * Language hint based on extension.
     */
    val language: String?
        get() = extension?.let { ext ->
            when (ext.lowercase()) {
                "kt", "kts" -> "Kotlin"
                "java" -> "Java"
                "cs" -> "C#"
                "py" -> "Python"
                "js", "mjs" -> "JavaScript"
                "ts", "tsx" -> "TypeScript"
                "xml" -> "XML"
                "json" -> "JSON"
                "md" -> "Markdown"
                "yaml", "yml" -> "YAML"
                "html", "htm" -> "HTML"
                "css" -> "CSS"
                "sql" -> "SQL"
                else -> ext.uppercase()
            }
        }

    /**
     * Icon hint based on file type.
     */
    val iconHint: String
        get() = when (extension?.lowercase()) {
            "kt", "kts" -> "🟣"
            "java" -> "☕"
            "cs" -> "🔷"
            "py" -> "🐍"
            "js", "ts" -> "🟨"
            "xml", "html" -> "📄"
            "json", "yaml", "yml" -> "⚙️"
            "md" -> "📝"
            "gradle" -> "🐘"
            else -> "📁"
        }

    /**
     * Returns true if this is a test file (heuristic).
     */
    val isTestFile: Boolean
        get() = name.contains("Test", ignoreCase = true) ||
                name.contains("Spec", ignoreCase = true) ||
                path.contains("/test/", ignoreCase = true) ||
                path.contains("/tests/", ignoreCase = true)

    /**
     * Summary for tooltip.
     */
    val summary: String
        get() = buildString {
            append("Opened $openCount time")
            if (openCount != 1) append("s")
            append(" • Last: $lastOpenedFormatted")
            if (pinned) append(" • Pinned")
        }

    override fun toString(): String = "RecentFileEntry($displayName)"
}

/**
 * Date categories for grouping recent files.
 */
enum class DateCategory(val displayName: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    OLDER("Older");

    override fun toString(): String = displayName
}

/**
 * Grouping options for recent files.
 */
enum class FileGrouping(val displayName: String) {
    /** No grouping, show as flat list */
    NONE("None"),

    /** Group by parent folder */
    BY_FOLDER("By Folder"),

    /** Group by project */
    BY_PROJECT("By Project"),

    /** Group by file extension */
    BY_EXTENSION("By Extension"),

    /** Group by date category (Today, Yesterday, etc.) */
    BY_DATE("By Date");

    override fun toString(): String = displayName
}

/**
 * Sorting options for recent files.
 */
enum class FileSorting(val displayName: String) {
    /** Most recently opened first */
    BY_RECENT("Recent First"),

    /** Most frequently opened first */
    BY_FREQUENCY("Most Opened"),

    /** Alphabetically by name */
    BY_NAME("By Name"),

    /** By file extension */
    BY_EXTENSION("By Extension");

    override fun toString(): String = displayName
}

/**
 * View options for the recent files grid.
 *
 * @property grouping How to group files
 * @property sorting How to sort files within groups
 * @property showPreview Whether to show file content preview
 * @property gridColumns Number of columns in the grid
 * @property showHiddenFiles Whether to show dot-files
 * @property showTestFiles Whether to include test files
 * @property pinnedFirst Whether pinned files appear first
 */
data class GridViewOptions(
    val grouping: FileGrouping = FileGrouping.BY_FOLDER,
    val sorting: FileSorting = FileSorting.BY_RECENT,
    val showPreview: Boolean = true,
    val gridColumns: Int = 4,
    val showHiddenFiles: Boolean = false,
    val showTestFiles: Boolean = true,
    val pinnedFirst: Boolean = true
) {
    /**
     * Returns options with updated grouping.
     */
    fun withGrouping(grouping: FileGrouping) = copy(grouping = grouping)

    /**
     * Returns options with updated sorting.
     */
    fun withSorting(sorting: FileSorting) = copy(sorting = sorting)

    /**
     * Returns options with preview toggled.
     */
    fun togglePreview() = copy(showPreview = !showPreview)

    /**
     * Returns options with column count.
     */
    fun withColumns(columns: Int) = copy(gridColumns = columns.coerceIn(2, 8))
}

/**
 * A grouped collection of files.
 *
 * @property name Group name for display
 * @property files Files in this group
 */
data class FileGroup(
    val name: String,
    val files: List<RecentFileEntry>
) {
    val count: Int get() = files.size
    val isEmpty: Boolean get() = files.isEmpty()

    override fun toString(): String = "$name ($count files)"
}

/**
 * Result of a file grid operation.
 */
sealed class RecentFilesResult {
    data class Success(val entries: List<RecentFileEntry>) : RecentFilesResult()
    data class Grouped(val groups: List<FileGroup>) : RecentFilesResult()
    data class Error(val message: String) : RecentFilesResult()

    val isSuccess: Boolean get() = this is Success || this is Grouped
}
