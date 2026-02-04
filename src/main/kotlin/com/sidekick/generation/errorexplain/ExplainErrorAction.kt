// =============================================================================
// ExplainErrorAction.kt
// =============================================================================
// Editor action for explaining error messages.
//
// Available from:
// - Editor context menu
// - Build output console
// - Keyboard shortcut
//
// DESIGN NOTES:
// - Can explain selected text or error from console
// - Shows explanation in popup or tool window
// =============================================================================

package com.sidekick.generation.errorexplain

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Action to explain an error message.
 *
 * Explains the selected error text or prompts for error input.
 *
 * ## Keyboard Shortcut
 * Default: Alt+Shift+E
 */
class ExplainErrorAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(ExplainErrorAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        LOG.info("Explain Error action triggered")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Explaining Error...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing error message..."
                indicator.fraction = 0.2

                val result = runBlocking {
                    val service = ErrorExplainService.getInstance(project)
                    service.explainFromSelection()
                }

                indicator.fraction = 0.9

                if (result.success && result.explanation != null) {
                    showExplanation(project, result)
                } else {
                    showErrorNotification(project, result.error ?: "Could not explain error")
                }

                indicator.fraction = 1.0
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        
        e.presentation.isEnabledAndVisible = project != null && hasSelection
        e.presentation.text = if (hasSelection) {
            "Sidekick: Explain Selected Error"
        } else {
            "Sidekick: Explain Error"
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------

    private fun showExplanation(project: Project, result: ErrorExplainResult) {
        val explanation = result.explanation ?: return
        
        // Copy best fix to clipboard if available
        explanation.bestFix()?.let { fix ->
            if (fix.codeSnippet.isNotBlank()) {
                copyToClipboard(fix.codeSnippet)
            }
        }
        
        // Format for notification
        val content = buildString {
            appendLine("**${explanation.category.displayName}**")
            appendLine()
            appendLine(explanation.summary)
            appendLine()
            if (explanation.fixes.isNotEmpty()) {
                appendLine("**Fixes:**")
                explanation.fixes.take(2).forEach { fix ->
                    appendLine("• ${fix.description}")
                }
                if (explanation.fixes.firstOrNull()?.codeSnippet?.isNotBlank() == true) {
                    appendLine()
                    appendLine("_Code snippet copied to clipboard_")
                }
            }
        }
        
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Error Explained",
                    content,
                    NotificationType.INFORMATION
                )
                .notify(project)
        } catch (e: Exception) {
            LOG.debug("Could not show notification: ${e.message}")
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, selection)
        } catch (e: Exception) {
            LOG.warn("Failed to copy to clipboard: ${e.message}")
        }
    }

    private fun showErrorNotification(project: Project, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Could Not Explain Error",
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
 * Action to quickly categorize an error (no LLM).
 */
class QuickCategorizeErrorAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        val selection = editor.selectionModel.selectedText
        if (selection.isNullOrBlank()) {
            showNotification(project, "Select an error message to categorize", NotificationType.WARNING)
            return
        }
        
        val service = ErrorExplainService.getInstance(project)
        val context = ErrorContext.fromMessage(selection)
        val explanation = service.quickExplain(context)
        
        val content = buildString {
            appendLine("**${explanation.category.displayName}**")
            appendLine(explanation.category.description)
            appendLine()
            if (explanation.category.commonCauses.isNotEmpty()) {
                appendLine("Common causes:")
                explanation.category.commonCauses.forEach { cause ->
                    appendLine("• $cause")
                }
            }
        }
        
        showNotification(project, content, NotificationType.INFORMATION)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        
        e.presentation.isEnabledAndVisible = project != null && hasSelection
    }

    private fun showNotification(project: Project, message: String, type: NotificationType) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification("Error Category", message, type)
                .notify(project)
        } catch (_: Exception) {}
    }
}
