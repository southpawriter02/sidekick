package com.sidekick.navigation.snippets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant

/**
 * # Snippet Service
 *
 * Application-level service for managing the snippet pocket.
 * Part of Sidekick v0.4.3 Snippet Pocket feature.
 *
 * ## Features
 *
 * - Capture code snippets to numbered slots (0-9)
 * - Paste snippets from slots to clipboard or editor
 * - Persistent storage across IDE restarts
 * - Import/export snippet collections
 *
 * ## Usage
 *
 * ```kotlin
 * val service = SnippetService.getInstance()
 *
 * // Capture a snippet
 * service.captureSelection("code here", "kt", "/path/file.kt", 10..15)
 *
 * // Paste from slot 0
 * service.pasteSnippet(0)
 *
 * // Get all snippets
 * service.getAllSnippets()
 * ```
 *
 * @since 0.4.3
 */
@Service(Service.Level.APP)
@State(
    name = "SidekickSnippets",
    storages = [Storage("sidekick-snippets.xml")]
)
class SnippetService : PersistentStateComponent<SnippetService.State> {

    private val logger = Logger.getInstance(SnippetService::class.java)

    /**
     * Persistent state for snippet storage.
     */
    data class State(
        var snippets: MutableList<SnippetData> = mutableListOf(),
        var maxSlots: Int = SnippetPocket.MAX_SLOTS
    ) {
        constructor() : this(mutableListOf(), SnippetPocket.MAX_SLOTS)

        fun toPocket(): SnippetPocket {
            val slots = MutableList<Snippet?>(maxSlots) { null }
            snippets.forEachIndexed { index, data ->
                if (index < maxSlots) {
                    slots[index] = data.toSnippet()
                }
            }
            return SnippetPocket(slots = slots, maxSlots = maxSlots)
        }

        companion object {
            fun from(pocket: SnippetPocket): State {
                return State(
                    snippets = pocket.slots.map { snippet ->
                        snippet?.let { SnippetData.from(it) } ?: SnippetData()
                    }.toMutableList(),
                    maxSlots = pocket.maxSlots
                )
            }
        }
    }

    /**
     * Serializable snippet data for persistence.
     */
    data class SnippetData(
        var id: Int = 0,
        var content: String = "",
        var language: String = "",
        var sourceFile: String = "",
        var lineStart: Int = -1,
        var lineEnd: Int = -1,
        var savedAt: Long = 0,
        var label: String = ""
    ) {
        constructor() : this(0, "", "", "", -1, -1, 0, "")

        fun toSnippet(): Snippet? {
            if (content.isEmpty()) return null
            return Snippet(
                id = id,
                content = content,
                language = language.ifBlank { null },
                sourceFile = sourceFile.ifBlank { null },
                lineRange = if (lineStart >= 0 && lineEnd >= 0) lineStart..lineEnd else null,
                savedAt = if (savedAt > 0) Instant.ofEpochMilli(savedAt) else Instant.now(),
                label = label.ifBlank { null }
            )
        }

        companion object {
            fun from(snippet: Snippet): SnippetData = SnippetData(
                id = snippet.id,
                content = snippet.content,
                language = snippet.language ?: "",
                sourceFile = snippet.sourceFile ?: "",
                lineStart = snippet.lineRange?.first ?: -1,
                lineEnd = snippet.lineRange?.last ?: -1,
                savedAt = snippet.savedAt.toEpochMilli(),
                label = snippet.label ?: ""
            )
        }
    }

    private var state = State()
    private var pocket = SnippetPocket()

    companion object {
        /**
         * Gets the singleton service instance.
         */
        fun getInstance(): SnippetService {
            return ApplicationManager.getApplication().getService(SnippetService::class.java)
        }
    }

    override fun getState(): State {
        return State.from(pocket)
    }

    override fun loadState(state: State) {
        this.state = state
        this.pocket = state.toPocket()
    }

    // =========================================================================
    // Capture Operations
    // =========================================================================

    /**
     * Captures a selection to the next available slot (pushes to front).
     *
     * @param content The text content to capture
     * @param language Optional language/extension hint
     * @param file Optional source file path
     * @param lines Optional line range in source file
     * @return The captured snippet
     */
    fun captureSelection(
        content: String,
        language: String? = null,
        file: String? = null,
        lines: IntRange? = null
    ): Snippet {
        val snippet = Snippet(
            id = generateId(),
            content = content,
            language = language,
            sourceFile = file,
            lineRange = lines
        )
        pocket = pocket.add(snippet)
        logger.info("Captured snippet to slot 0: ${snippet.preview}")
        return snippet
    }

    /**
     * Captures a selection to a specific slot.
     *
     * @param slot The slot index (0-9)
     * @param content The text content to capture
     * @param language Optional language/extension hint
     * @param file Optional source file path
     * @param lines Optional line range in source file
     * @return The captured snippet, or null if slot is invalid
     */
    fun captureToSlot(
        slot: Int,
        content: String,
        language: String? = null,
        file: String? = null,
        lines: IntRange? = null
    ): Snippet? {
        if (slot < 0 || slot >= pocket.maxSlots) return null

        val snippet = Snippet(
            id = generateId(),
            content = content,
            language = language,
            sourceFile = file,
            lineRange = lines
        )
        pocket = pocket.setSlot(slot, snippet)
        logger.info("Captured snippet to slot $slot: ${snippet.preview}")
        return snippet
    }

    // =========================================================================
    // Retrieval Operations
    // =========================================================================

    /**
     * Gets the snippet at the given slot.
     */
    fun getSnippet(slot: Int): Snippet? = pocket.get(slot)

    /**
     * Gets all snippets (including nulls for empty slots).
     */
    fun getAllSnippets(): List<Snippet?> = pocket.slots

    /**
     * Gets all non-empty snippets with their slot indices.
     */
    fun getIndexedSnippets(): List<Pair<Int, Snippet>> = pocket.indexedSnippets

    /**
     * Gets the pocket summary.
     */
    fun getPocketSummary(): String = pocket.summary

    // =========================================================================
    // Paste Operations
    // =========================================================================

    /**
     * Pastes a snippet to the system clipboard.
     *
     * @param slot The slot index
     * @return The snippet content, or null if slot is empty
     */
    fun pasteSnippet(slot: Int): String? {
        val snippet = getSnippet(slot) ?: return null
        setClipboard(snippet.content)
        logger.info("Pasted snippet from slot $slot to clipboard")
        return snippet.content
    }

    /**
     * Gets snippet content for insertion (without clipboard).
     *
     * @param slot The slot index
     * @return Result with snippet or error
     */
    fun getSnippetContent(slot: Int): SnippetResult {
        val snippet = getSnippet(slot)
            ?: return SnippetResult.SlotEmpty(slot)
        return SnippetResult.Success(snippet)
    }

    // =========================================================================
    // Management Operations
    // =========================================================================

    /**
     * Clears a specific slot.
     */
    fun clearSlot(slot: Int): Boolean {
        if (slot < 0 || slot >= pocket.maxSlots) return false
        pocket = pocket.clearSlot(slot)
        logger.info("Cleared slot $slot")
        return true
    }

    /**
     * Clears all slots.
     */
    fun clearAll() {
        pocket = pocket.clearAll()
        logger.info("Cleared all snippets")
    }

    /**
     * Labels a snippet in a slot.
     */
    fun labelSnippet(slot: Int, label: String): Boolean {
        val snippet = getSnippet(slot) ?: return false
        pocket = pocket.setSlot(slot, snippet.copy(label = label))
        return true
    }

    // =========================================================================
    // Import/Export
    // =========================================================================

    /**
     * Exports all non-empty snippets.
     */
    fun exportSnippets(): SnippetExport {
        val snippets = pocket.slots.filterNotNull()
        return SnippetExport(snippets = snippets)
    }

    /**
     * Imports snippets from an export.
     *
     * @param export The export to import
     * @param merge If true, merge with existing; if false, replace all
     * @return Number of snippets imported
     */
    fun importSnippets(export: SnippetExport, merge: Boolean = false): Int {
        if (!export.isCompatible) {
            logger.warn("Export version ${export.version} is not compatible")
            return 0
        }

        if (!merge) {
            pocket = pocket.clearAll()
        }

        var imported = 0
        for (snippet in export.snippets) {
            val slot = pocket.firstEmptySlot
            if (slot != null) {
                pocket = pocket.setSlot(slot, snippet)
                imported++
            } else {
                break // Pocket is full
            }
        }

        logger.info("Imported $imported snippets")
        return imported
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Generates a unique snippet ID.
     */
    private fun generateId(): Int = System.currentTimeMillis().toInt()

    /**
     * Sets the system clipboard content.
     */
    private fun setClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (e: Exception) {
            logger.warn("Failed to set clipboard: ${e.message}")
        }
    }
}
