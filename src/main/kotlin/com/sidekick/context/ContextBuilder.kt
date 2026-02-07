// =============================================================================
// ContextBuilder.kt
// =============================================================================
// Builds context-enriched prompts for the LLM.
//
// This class:
// - Assembles editor, symbol, and project context into prompts
// - Provides configurable context sections
// - Formats context for optimal LLM understanding
//
// DESIGN NOTES:
// - Builder pattern for flexible context composition
// - Respects token limits by truncating content
// - Produces structured markdown for clarity
// =============================================================================

package com.sidekick.context

import com.intellij.openapi.diagnostic.Logger
import com.sidekick.settings.SidekickSettings

/**
 * Builds context-enriched prompts for LLM communication.
 *
 * Assembles various context sources (editor, symbol, project) into
 * a coherent system prompt or message prefix.
 *
 * ## Usage
 *
 * ```kotlin
 * val context = ContextBuilder(editorContext, symbolContext, projectContext)
 *     .includeFileInfo()
 *     .includeSelection()
 *     .includeProjectSummary()
 *     .build()
 * ```
 */
class ContextBuilder(
    private val editorContext: EditorContext = EditorContext.EMPTY,
    private val symbolContext: SymbolContext = SymbolContext.EMPTY,
    private val projectContext: ProjectContext = ProjectContext.EMPTY
) {
    companion object {
        private val LOG = Logger.getInstance(ContextBuilder::class.java)
        
        /**
         * Maximum characters for file content inclusion.
         */
        const val MAX_FILE_CONTENT_CHARS = 8000
        
        /**
         * Maximum characters for selection inclusion.
         */
        const val MAX_SELECTION_CHARS = 4000
        
        /**
         * Maximum characters for symbol definition.
         */
        const val MAX_SYMBOL_CHARS = 2000
        
        /**
         * Creates a builder with current context from services.
         */
        fun fromProject(
            editorService: EditorContextService,
            projectService: ProjectContextService
        ): ContextBuilder {
            return ContextBuilder(
                editorContext = editorService.getCurrentContext(),
                symbolContext = editorService.getSymbolAtCursor(),
                projectContext = projectService.getProjectContext()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Configuration Flags
    // -------------------------------------------------------------------------
    
    private var includeFileInfo = false
    private var includeFileContent = false
    private var includeSelection = false
    private var includeSymbol = false
    private var includeProjectSummary = false
    private var includeSurroundingCode = false
    private var surroundingLines = 10
    private var searchResults: List<CodebaseSearchService.SearchResult> = emptyList()

    // -------------------------------------------------------------------------
    // Builder Methods
    // -------------------------------------------------------------------------
    
    /**
     * Includes basic file information (name, language, cursor position).
     */
    fun includeFileInfo(): ContextBuilder {
        includeFileInfo = true
        return this
    }
    
    /**
     * Includes the full file content (truncated if large).
     */
    fun includeFileContent(): ContextBuilder {
        includeFileContent = true
        return this
    }
    
    /**
     * Includes the current selection if any.
     */
    fun includeSelection(): ContextBuilder {
        includeSelection = true
        return this
    }
    
    /**
     * Includes symbol information at cursor.
     */
    fun includeSymbol(): ContextBuilder {
        includeSymbol = true
        return this
    }
    
    /**
     * Includes project summary.
     */
    fun includeProjectSummary(): ContextBuilder {
        includeProjectSummary = true
        return this
    }
    
    /**
     * Includes code surrounding the cursor.
     */
    fun includeSurroundingCode(lines: Int = 10): ContextBuilder {
        includeSurroundingCode = true
        surroundingLines = lines
        return this
    }
    
    /**
     * Includes all available context.
     */
    fun includeAll(): ContextBuilder {
        includeFileInfo = true
        includeSelection = true
        includeSymbol = true
        includeProjectSummary = true
        includeSurroundingCode = true
        return this
    }
    
    /**
     * Includes codebase search results.
     */
    fun includeCodebaseSearch(results: List<CodebaseSearchService.SearchResult>): ContextBuilder {
        searchResults = results
        return this
    }
    
    /**
     * Standard context for chat messages.
     */
    fun standardChat(): ContextBuilder {
        includeFileInfo = true
        includeSelection = true
        includeProjectSummary = true
        return this
    }

    // -------------------------------------------------------------------------
    // Build Methods
    // -------------------------------------------------------------------------
    
    /**
     * Builds the context string.
     *
     * @return Formatted context string for inclusion in prompts
     */
    fun build(): String {
        val sections = mutableListOf<String>()
        
        // Project summary first (highest-level context)
        if (includeProjectSummary && projectContext.isValid) {
            sections.add(buildProjectSection())
        }
        
        // File information
        if (includeFileInfo && editorContext.hasFile) {
            sections.add(buildFileInfoSection())
        }
        
        // Current selection (priority for user intent)
        if (includeSelection && editorContext.hasSelection) {
            sections.add(buildSelectionSection())
        }
        
        // Symbol at cursor
        if (includeSymbol && symbolContext.isValid) {
            sections.add(buildSymbolSection())
        }
        
        // Surrounding code
        if (includeSurroundingCode && editorContext.hasFile) {
            sections.add(buildSurroundingSection())
        }
        
        // Full file content (if explicitly requested and no selection)
        if (includeFileContent && editorContext.hasFile && !editorContext.hasSelection) {
            sections.add(buildFileContentSection())
        }
        
        // Codebase search results
        if (searchResults.isNotEmpty()) {
            sections.add(buildCodebaseSearchSection())
        }
        
        if (sections.isEmpty()) {
            return ""
        }
        
        return sections.joinToString("\n\n")
    }
    
    /**
     * Builds a system prompt with context.
     */
    fun buildSystemPrompt(): String {
        val basePrompt = SidekickSettings.getInstance().systemPrompt
        val contextSection = build()
        
        return buildString {
            if (basePrompt.isNotEmpty()) {
                append(basePrompt)
                append("\n\n")
            }
            
            if (contextSection.isNotEmpty()) {
                append("## Current Context\n\n")
                append(contextSection)
            }
        }.trim()
    }
    
    /**
     * Checks if any meaningful context is available.
     */
    fun hasContext(): Boolean {
        return editorContext.hasFile || 
               editorContext.hasSelection || 
               symbolContext.isValid || 
               projectContext.isValid
    }

    // -------------------------------------------------------------------------
    // Section Builders
    // -------------------------------------------------------------------------
    
    private fun buildProjectSection(): String {
        return buildString {
            append("### Project\n")
            append("- **Name:** ${projectContext.name}\n")
            append("- **Type:** ${projectContext.projectType.displayName}")
            if (projectContext.frameworkHints.isNotEmpty()) {
                append(" (${projectContext.frameworkHints.joinToString(", ")})")
            }
        }
    }
    
    private fun buildFileInfoSection(): String {
        return buildString {
            append("### Current File\n")
            append("- **File:** `${editorContext.fileName}`\n")
            append("- **Language:** ${editorContext.language}\n")
            append("- **Cursor:** Line ${editorContext.cursorLine}, Column ${editorContext.cursorColumn}")
            if (editorContext.lineCount > 0) {
                append(" (of ${editorContext.lineCount} lines)")
            }
        }
    }
    
    private fun buildSelectionSection(): String {
        val selection = editorContext.selection ?: return ""
        val truncated = selection.take(MAX_SELECTION_CHARS)
        val wasTruncated = selection.length > MAX_SELECTION_CHARS
        
        return buildString {
            append("### Selected Code")
            if (editorContext.selectionRange != null) {
                append(" (Lines ${editorContext.selectionRange})")
            }
            append("\n")
            append("```${editorContext.language.lowercase()}\n")
            append(truncated)
            if (wasTruncated) {
                append("\n// ... (truncated, ${selection.length} chars total)")
            }
            append("\n```")
        }
    }
    
    private fun buildSymbolSection(): String {
        return buildString {
            append("### Symbol at Cursor\n")
            append("- **Name:** `${symbolContext.qualifiedName}`\n")
            append("- **Kind:** ${symbolContext.kind.displayName}")
            if (symbolContext.signature != null) {
                append("\n- **Signature:** `${symbolContext.signature}`")
            }
            if (symbolContext.hasDocumentation) {
                append("\n- **Documentation:** ${symbolContext.documentation?.take(200)}")
            }
        }
    }
    
    private fun buildSurroundingSection(): String {
        val surrounding = editorContext.getSurroundingText(
            linesBefore = surroundingLines / 2,
            linesAfter = surroundingLines / 2
        ) ?: return ""
        
        val startLine = maxOf(1, editorContext.cursorLine - surroundingLines / 2)
        
        return buildString {
            append("### Code Around Cursor (Line ${editorContext.cursorLine})\n")
            append("```${editorContext.language.lowercase()}\n")
            // Add line numbers for reference
            surrounding.lines().forEachIndexed { index, line ->
                val lineNum = startLine + index
                val marker = if (lineNum == editorContext.cursorLine) "→" else " "
                append("$marker${lineNum.toString().padStart(4)}: $line\n")
            }
            append("```")
        }
    }
    
    private fun buildFileContentSection(): String {
        val content = editorContext.fileContent
        val truncated = content.take(MAX_FILE_CONTENT_CHARS)
        val wasTruncated = content.length > MAX_FILE_CONTENT_CHARS
        
        return buildString {
            append("### Full File Content\n")
            append("```${editorContext.language.lowercase()}\n")
            append(truncated)
            if (wasTruncated) {
                append("\n// ... (truncated, ${content.length} chars total)")
            }
            append("\n```")
        }
    }
    
    private fun buildCodebaseSearchSection(): String {
        return buildString {
            append("### Relevant Project Files\n\n")
            append("The following source files from the project may be relevant:\n")
            
            for (result in searchResults) {
                append("\n#### `${result.fileName}` (`${result.filePath}`)\n")
                append("```${result.language}\n")
                append(result.snippet)
                append("\n```\n")
            }
        }
    }
}
