// =============================================================================
// EditorContext.kt
// =============================================================================
// Data class representing the current state of the user's editor.
//
// This captures:
// - Current file and language information
// - Selected text and selection range
// - Cursor position
// - File content and visible range
//
// DESIGN NOTES:
// - Immutable data class for thread-safe context passing
// - EMPTY companion provides a safe default value
// - Used by EditorContextService to capture snapshots
// =============================================================================

package com.sidekick.context

import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents the current context of the user's editor.
 *
 * Captures a snapshot of the editor state including file information,
 * selection, cursor position, and content.
 *
 * @property file The current file being edited (null if no file open)
 * @property language The programming language ID (e.g., "C#", "Kotlin")
 * @property filePath Absolute path to the file
 * @property fileName Base name of the file
 * @property selection Currently selected text (null if no selection)
 * @property selectionRange Line range of selection (1-indexed, inclusive)
 * @property cursorLine Current cursor line number (1-indexed)
 * @property cursorColumn Current cursor column (1-indexed)
 * @property fileContent Full content of the file
 * @property visibleRange Lines currently visible in editor viewport (1-indexed)
 */
data class EditorContext(
    val file: VirtualFile?,
    val language: String,
    val filePath: String,
    val fileName: String,
    val selection: String?,
    val selectionRange: IntRange?,
    val cursorLine: Int,
    val cursorColumn: Int,
    val fileContent: String,
    val visibleRange: IntRange?
) {
    companion object {
        /**
         * Empty context representing no editor state.
         */
        val EMPTY = EditorContext(
            file = null,
            language = "text",
            filePath = "",
            fileName = "",
            selection = null,
            selectionRange = null,
            cursorLine = 0,
            cursorColumn = 0,
            fileContent = "",
            visibleRange = null
        )
    }
    
    // -------------------------------------------------------------------------
    // Computed Properties
    // -------------------------------------------------------------------------
    
    /**
     * Whether there is any selected text.
     */
    val hasSelection: Boolean 
        get() = !selection.isNullOrEmpty()
    
    /**
     * Whether a file is currently open in the editor.
     */
    val hasFile: Boolean 
        get() = file != null
    
    /**
     * The file extension (lowercase) or empty string.
     */
    val fileExtension: String
        get() = file?.extension?.lowercase() ?: ""
    
    /**
     * Number of lines in the file.
     */
    val lineCount: Int
        get() = if (fileContent.isEmpty()) 0 else fileContent.lines().size
    
    /**
     * Gets the text around the cursor (context window).
     *
     * @param linesBefore Number of lines before cursor to include
     * @param linesAfter Number of lines after cursor to include
     * @return The surrounding text, or null if no file
     */
    fun getSurroundingText(linesBefore: Int = 5, linesAfter: Int = 5): String? {
        if (!hasFile || fileContent.isEmpty()) return null
        
        val lines = fileContent.lines()
        val startLine = maxOf(0, cursorLine - 1 - linesBefore)
        val endLine = minOf(lines.size, cursorLine + linesAfter)
        
        return lines.subList(startLine, endLine).joinToString("\n")
    }
    
    /**
     * Gets a compact summary suitable for logging or display.
     */
    fun toSummary(): String = buildString {
        append("EditorContext(")
        append("file=$fileName")
        append(", lang=$language")
        append(", line=$cursorLine")
        if (hasSelection) {
            append(", selection=${selection?.length ?: 0} chars")
        }
        append(")")
    }
}
