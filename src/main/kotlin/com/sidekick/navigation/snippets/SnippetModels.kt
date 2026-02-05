package com.sidekick.navigation.snippets

import java.time.Instant
import java.util.UUID

/**
 * # Snippet Models
 *
 * Data structures for the multi-slot snippet clipboard.
 * Part of Sidekick v0.4.3 Snippet Pocket feature.
 *
 * ## Overview
 *
 * These models support:
 * - Saving code snippets to numbered slots (0-9)
 * - Tracking source file and line information
 * - Language-aware syntax highlighting hints
 * - Persistent storage across IDE restarts
 *
 * @since 0.4.3
 */

/**
 * A saved code snippet captured from the editor.
 *
 * Snippets store the captured text along with metadata about
 * where it came from, enabling rich preview and context hints.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val snippet = Snippet(
 *     id = 1,
 *     content = "fun hello() = println(\"Hello\")",
 *     language = "kt",
 *     sourceFile = "/src/Main.kt",
 *     lineRange = 10..12
 * )
 * println(snippet.preview) // "fun hello() = println(\"Hello\")"
 * ```
 *
 * @property id Unique identifier for the snippet
 * @property content The captured text content
 * @property language File extension/language hint (e.g., "kt", "java")
 * @property sourceFile Absolute path to the source file, if known
 * @property lineRange Line range in the source file, if known
 * @property savedAt Timestamp when the snippet was captured
 * @property label Optional user-provided label for the snippet
 */
data class Snippet(
    val id: Int,
    val content: String,
    val language: String? = null,
    val sourceFile: String? = null,
    val lineRange: IntRange? = null,
    val savedAt: Instant = Instant.now(),
    val label: String? = null
) {
    /**
     * Short preview of the content (first 100 chars, newlines replaced).
     */
    val preview: String
        get() = content
            .take(100)
            .replace("\n", " ")
            .replace("\r", "")
            .trim()

    /**
     * Full preview with ellipsis if truncated.
     */
    val previewWithEllipsis: String
        get() = if (content.length > 100) "$preview..." else preview

    /**
     * Number of lines in the snippet.
     */
    val lineCount: Int
        get() = content.count { it == '\n' } + 1

    /**
     * Number of characters in the snippet.
     */
    val charCount: Int
        get() = content.length

    /**
     * Display name for the snippet.
     */
    val displayName: String
        get() = label ?: sourceFileName ?: "Snippet #$id"

    /**
     * Just the file name portion of sourceFile.
     */
    val sourceFileName: String?
        get() = sourceFile?.substringAfterLast('/')

    /**
     * Formatted line range for display.
     */
    val lineRangeText: String?
        get() = lineRange?.let { "L${it.first + 1}-${it.last + 1}" }

    /**
     * Short summary for tooltip/status bar.
     */
    val summary: String
        get() = buildString {
            append("$lineCount lines, $charCount chars")
            language?.let { append(" ($it)") }
        }

    /**
     * Formatted timestamp for display.
     */
    val savedAtFormatted: String
        get() = savedAt.toString().substringBefore('T')

    /**
     * Returns true if the snippet has associated file information.
     */
    val hasSource: Boolean
        get() = sourceFile != null

    override fun toString(): String = "Snippet($displayName: $preview)"
}

/**
 * A collection of snippet slots.
 *
 * The pocket maintains up to [maxSlots] snippets in numbered slots.
 * New snippets are added to the front, pushing older ones down.
 * Specific slots can also be set directly.
 *
 * @property slots List of snippet slots (null = empty)
 * @property maxSlots Maximum number of slots (default 10)
 */
data class SnippetPocket(
    val slots: List<Snippet?> = List(MAX_SLOTS) { null },
    val maxSlots: Int = MAX_SLOTS
) {
    companion object {
        /** Default maximum slots */
        const val MAX_SLOTS = 10
    }

    /**
     * Adds a snippet to the front, shifting others down.
     */
    fun add(snippet: Snippet): SnippetPocket {
        val newSlots = slots.toMutableList()
        newSlots.add(0, snippet)
        return copy(slots = newSlots.take(maxSlots))
    }

    /**
     * Gets the snippet at the given slot index.
     */
    fun get(index: Int): Snippet? = slots.getOrNull(index)

    /**
     * Sets a specific slot to a snippet.
     */
    fun setSlot(index: Int, snippet: Snippet): SnippetPocket {
        if (index < 0 || index >= maxSlots) return this
        val newSlots = slots.toMutableList()
        newSlots[index] = snippet
        return copy(slots = newSlots)
    }

    /**
     * Clears a specific slot.
     */
    fun clearSlot(index: Int): SnippetPocket {
        if (index < 0 || index >= maxSlots) return this
        val newSlots = slots.toMutableList()
        newSlots[index] = null
        return copy(slots = newSlots)
    }

    /**
     * Clears all slots.
     */
    fun clearAll(): SnippetPocket = copy(slots = List(maxSlots) { null })

    /**
     * Number of non-empty slots.
     */
    val usedSlots: Int
        get() = slots.count { it != null }

    /**
     * Number of empty slots.
     */
    val emptySlots: Int
        get() = slots.count { it == null }

    /**
     * Returns true if all slots are empty.
     */
    val isEmpty: Boolean
        get() = slots.all { it == null }

    /**
     * Returns true if all slots are full.
     */
    val isFull: Boolean
        get() = slots.all { it != null }

    /**
     * Gets all non-null snippets with their indices.
     */
    val indexedSnippets: List<Pair<Int, Snippet>>
        get() = slots.mapIndexedNotNull { index, snippet ->
            snippet?.let { index to it }
        }

    /**
     * Finds the first empty slot index, or null if full.
     */
    val firstEmptySlot: Int?
        get() = slots.indexOfFirst { it == null }.takeIf { it >= 0 }

    /**
     * Summary of pocket contents.
     */
    val summary: String
        get() = "$usedSlots of $maxSlots slots used"
}

/**
 * Export format for snippets.
 *
 * @property version Export format version
 * @property snippets List of snippets to export
 * @property exportedAt Export timestamp
 */
data class SnippetExport(
    val version: Int = CURRENT_VERSION,
    val snippets: List<Snippet>,
    val exportedAt: Instant = Instant.now()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }

    val count: Int get() = snippets.size
    val isCompatible: Boolean get() = version <= CURRENT_VERSION
}

/**
 * Result of a snippet operation.
 */
sealed class SnippetResult {
    data class Success(val snippet: Snippet) : SnippetResult()
    data class SlotEmpty(val slot: Int) : SnippetResult()
    data class Error(val message: String) : SnippetResult()

    val isSuccess: Boolean get() = this is Success
}
