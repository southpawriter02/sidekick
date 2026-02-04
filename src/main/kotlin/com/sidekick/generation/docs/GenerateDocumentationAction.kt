// =============================================================================
// GenerateDocumentationAction.kt
// =============================================================================
// Editor action to trigger documentation generation.
//
// Available from:
// - Editor context menu (Sidekick > Generate Documentation)
// - Generate menu (Alt+Insert)
// - Keyboard shortcut (Alt+Shift+D)
//
// DESIGN NOTES:
// - Background thread for LLM calls
// - Write action for document modification
// - User-friendly error notifications
// =============================================================================

package com.sidekick.generation.docs

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

/**
 * Action to generate documentation for the symbol at cursor.
 *
 * This action analyzes the symbol under the cursor and generates
 * appropriate documentation based on the file's language.
 *
 * ## Keyboard Shortcut
 * Default: Alt+Shift+D
 *
 * ## Context Menu
 * Available under: Sidekick > Generate Documentation
 */
class GenerateDocumentationAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GenerateDocumentationAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        LOG.info("Generate Documentation action triggered")

        // Run in background with progress
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating Documentation...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing symbol..."
                indicator.fraction = 0.2

                val result = runBlocking {
                    val service = DocGenService.getInstance(project)
                    service.generateForCurrentSymbol()
                }

                indicator.fraction = 0.8

                if (result.success) {
                    indicator.text = "Inserting documentation..."
                    insertDocumentation(project, editor, result)
                    showSuccessNotification(project, result)
                } else {
                    showErrorNotification(project, result.error ?: "Generation failed")
                }

                indicator.fraction = 1.0
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)

        e.presentation.isEnabledAndVisible = project != null && editor != null
    }

    // -------------------------------------------------------------------------
    // Private Methods - Document Modification
    // -------------------------------------------------------------------------

    private fun insertDocumentation(
        project: Project,
        editor: Editor,
        result: DocGenResult
    ) {
        WriteCommandAction.runWriteCommandAction(project, "Generate Documentation", null, {
            val document = editor.document
            val offset = findInsertOffset(editor, result.insertPosition)
            
            // Get the indentation of the current line
            val lineNumber = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineText = document.getText(
                com.intellij.openapi.util.TextRange(lineStart, offset)
            )
            val indentation = lineText.takeWhile { it.isWhitespace() }
            
            // Indent each line of documentation
            val indentedDoc = result.documentation
                .lines()
                .joinToString("\n") { line ->
                    if (line.isBlank()) line else "$indentation$line"
                }
            
            // Insert with trailing newline
            document.insertString(offset, "$indentedDoc\n")
            
            LOG.info("Inserted documentation at offset $offset")
        })
    }

    private fun findInsertOffset(
        editor: Editor,
        position: InsertPosition
    ): Int {
        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(caretOffset)
        
        return when (position) {
            InsertPosition.BEFORE_SYMBOL -> {
                // Find the start of the current symbol's line
                document.getLineStartOffset(lineNumber)
            }
            InsertPosition.REPLACE_EXISTING -> {
                // TODO: Find and replace existing documentation
                document.getLineStartOffset(lineNumber)
            }
            InsertPosition.AFTER_SYMBOL -> {
                // Insert after the current line
                document.getLineEndOffset(lineNumber)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Notifications
    // -------------------------------------------------------------------------

    private fun showSuccessNotification(project: Project, result: DocGenResult) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Documentation Generated",
                    "${result.style.displayName} documentation added successfully.",
                    NotificationType.INFORMATION
                )
                .notify(project)
        } catch (e: Exception) {
            LOG.debug("Could not show notification: ${e.message}")
        }
    }

    private fun showErrorNotification(project: Project, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Documentation Generation Failed",
                    message,
                    NotificationType.WARNING
                )
                .notify(project)
        } catch (e: Exception) {
            LOG.warn("Could not show error notification: ${e.message}")
        }
    }
}

/**
 * Action to generate documentation stub without LLM.
 * Useful for offline mode or quick scaffolding.
 */
class GenerateDocumentationStubAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val service = DocGenService.getInstance(project)
        val editorContext = com.sidekick.context.EditorContextService
            .getInstance(project)
            .getCurrentContext()
        val symbol = com.sidekick.context.EditorContextService
            .getInstance(project)
            .getSymbolAtCursor()

        val request = DocGenRequest.forSymbol(symbol, editorContext.language)
        val result = service.generateStub(request)

        if (result.success) {
            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val lineNumber = document.getLineNumber(editor.caretModel.offset)
                val offset = document.getLineStartOffset(lineNumber)
                document.insertString(offset, "${result.documentation}\n")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }
}
