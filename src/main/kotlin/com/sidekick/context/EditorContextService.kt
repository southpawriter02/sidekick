// =============================================================================
// EditorContextService.kt
// =============================================================================
// Project-level service for extracting context from the current editor.
//
// This service:
// - Captures current file, selection, and cursor information
// - Extracts symbol information at cursor position
// - Provides context for AI prompts
//
// DESIGN NOTES:
// - Project-level service (one per project)
// - Uses IntelliJ PSI for symbol resolution
// - Thread-safe for background access
// - Returns immutable context snapshots
// =============================================================================

package com.sidekick.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import java.awt.Point

/**
 * Service for extracting context from the current editor state.
 *
 * Provides snapshots of editor context including file information,
 * selection, cursor position, and symbol information at cursor.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = EditorContextService.getInstance(project)
 * val context = service.getCurrentContext()
 * if (context.hasSelection) {
 *     // Use selection for "explain selection" feature
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class EditorContextService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(EditorContextService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): EditorContextService {
            return project.getService(EditorContextService::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods - Editor Context
    // -------------------------------------------------------------------------
    
    /**
     * Gets the current editor context.
     *
     * @return EditorContext snapshot, or EMPTY if no editor is open
     */
    fun getCurrentContext(): EditorContext {
        return ReadAction.compute<EditorContext, RuntimeException> {
            try {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    ?: return@compute EditorContext.EMPTY
                
                getContextFromEditor(editor)
            } catch (e: Exception) {
                LOG.warn("Failed to get editor context: ${e.message}")
                EditorContext.EMPTY
            }
        }
    }
    
    /**
     * Gets context from a specific editor instance.
     *
     * @param editor The editor to extract context from
     * @return EditorContext snapshot
     */
    fun getContextFromEditor(editor: Editor): EditorContext {
        return ReadAction.compute<EditorContext, RuntimeException> {
            try {
                extractEditorContext(editor)
            } catch (e: Exception) {
                LOG.warn("Failed to extract editor context: ${e.message}")
                EditorContext.EMPTY
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods - Symbol Context
    // -------------------------------------------------------------------------
    
    /**
     * Gets the symbol at the current cursor position.
     *
     * @return SymbolContext for the symbol at cursor, or EMPTY if none
     */
    fun getSymbolAtCursor(): SymbolContext {
        return ReadAction.compute<SymbolContext, RuntimeException> {
            try {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    ?: return@compute SymbolContext.EMPTY
                
                getSymbolFromEditor(editor)
            } catch (e: Exception) {
                LOG.warn("Failed to get symbol at cursor: ${e.message}")
                SymbolContext.EMPTY
            }
        }
    }
    
    /**
     * Gets symbol context from a specific editor.
     *
     * @param editor The editor to extract from
     * @return SymbolContext for the symbol at cursor
     */
    fun getSymbolFromEditor(editor: Editor): SymbolContext {
        return ReadAction.compute<SymbolContext, RuntimeException> {
            try {
                extractSymbolContext(editor)
            } catch (e: Exception) {
                LOG.debug("Failed to extract symbol: ${e.message}")
                SymbolContext.EMPTY
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Context Extraction
    // -------------------------------------------------------------------------
    
    /**
     * Extracts editor context from an editor instance.
     */
    private fun extractEditorContext(editor: Editor): EditorContext {
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        
        // Selection info
        val selectionModel = editor.selectionModel
        val selection = if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else null
        
        val selectionRange = if (selectionModel.hasSelection()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            startLine..endLine
        } else null
        
        // Cursor position
        val caretModel = editor.caretModel
        val offset = caretModel.offset
        val cursorLine = document.getLineNumber(offset) + 1
        val lineStartOffset = document.getLineStartOffset(document.getLineNumber(offset))
        val cursorColumn = offset - lineStartOffset + 1
        
        // Visible range
        val visibleRange = try {
            val visibleArea = editor.scrollingModel.visibleArea
            val startLine = editor.xyToLogicalPosition(Point(0, visibleArea.y)).line + 1
            val endLine = editor.xyToLogicalPosition(
                Point(0, visibleArea.y + visibleArea.height)
            ).line + 1
            startLine..endLine
        } catch (e: Exception) {
            null
        }
        
        return EditorContext(
            file = file,
            language = psiFile?.language?.displayName ?: "text",
            filePath = file?.path ?: "",
            fileName = file?.name ?: "",
            selection = selection,
            selectionRange = selectionRange,
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            fileContent = document.text,
            visibleRange = visibleRange
        )
    }
    
    /**
     * Extracts symbol context from an editor instance.
     */
    private fun extractSymbolContext(editor: Editor): SymbolContext {
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?: return SymbolContext.EMPTY
        
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
            ?: return SymbolContext.EMPTY
        
        // Walk up to find a meaningful parent element
        return findSymbolContext(element)
    }
    
    /**
     * Walks up the PSI tree to find a symbol-bearing element.
     */
    private fun findSymbolContext(element: PsiElement): SymbolContext {
        var current: PsiElement? = element
        
        while (current != null && current !is com.intellij.psi.PsiFile) {
            val context = tryExtractSymbol(current)
            if (context != null) {
                return context
            }
            current = current.parent
        }
        
        return SymbolContext.EMPTY
    }
    
    /**
     * Tries to extract symbol information from a PSI element.
     */
    private fun tryExtractSymbol(element: PsiElement): SymbolContext? {
        // Get element class name for type detection
        val className = element.javaClass.simpleName.lowercase()
        
        // Determine symbol kind based on class name
        val kind = when {
            "class" in className && "declaration" in className -> SymbolKind.CLASS
            "interface" in className -> SymbolKind.INTERFACE
            "method" in className || "function" in className -> SymbolKind.METHOD
            "constructor" in className -> SymbolKind.CONSTRUCTOR
            "property" in className -> SymbolKind.PROPERTY
            "field" in className -> SymbolKind.FIELD
            "variable" in className || "localvariable" in className -> SymbolKind.VARIABLE
            "parameter" in className -> SymbolKind.PARAMETER
            "enum" in className && "declaration" in className -> SymbolKind.ENUM
            "namespace" in className -> SymbolKind.NAMESPACE
            else -> return null  // Not a recognized symbol type
        }
        
        // Get name (prefer PsiNamedElement)
        val name = if (element is PsiNamedElement) {
            element.name ?: ""
        } else {
            // Try to get name from first child
            element.children.firstOrNull()?.text?.take(50) ?: ""
        }
        
        if (name.isBlank()) return null
        
        // Get definition text (truncated)
        val definition = element.text.take(SymbolContext.MAX_DEFINITION_LENGTH)
        
        // Try to find containing class
        val containingClass = findContainingClassName(element)
        
        // Try to get documentation comment
        val documentation = findDocumentation(element)
        
        // Build signature for methods
        val signature = if (kind == SymbolKind.METHOD || kind == SymbolKind.CONSTRUCTOR) {
            buildSignature(element, name)
        } else null
        
        return SymbolContext(
            name = name,
            kind = kind,
            signature = signature,
            containingClass = containingClass,
            documentation = documentation,
            definition = definition
        )
    }
    
    /**
     * Finds the containing class name for an element.
     */
    private fun findContainingClassName(element: PsiElement): String? {
        var current = element.parent
        while (current != null && current !is com.intellij.psi.PsiFile) {
            val className = current.javaClass.simpleName.lowercase()
            if ("class" in className && "declaration" in className) {
                if (current is PsiNamedElement) {
                    return current.name
                }
            }
            current = current.parent
        }
        return null
    }
    
    /**
     * Tries to find documentation comment for an element.
     */
    private fun findDocumentation(element: PsiElement): String? {
        // Look for preceding doc comment
        var prev = element.prevSibling
        while (prev != null) {
            val text = prev.text.trim()
            if (text.startsWith("/**") || text.startsWith("///") || text.startsWith("'''")) {
                return text.take(500)
            }
            if (text.isNotEmpty() && !text.all { it.isWhitespace() }) {
                break  // Hit non-whitespace, non-comment
            }
            prev = prev.prevSibling
        }
        return null
    }
    
    /**
     * Builds a simplified signature for methods.
     */
    private fun buildSignature(element: PsiElement, name: String): String {
        // For now, return a simplified first-line signature
        val text = element.text
        val firstLine = text.lines().firstOrNull() ?: ""
        
        // Truncate and clean up
        return firstLine
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }
}
