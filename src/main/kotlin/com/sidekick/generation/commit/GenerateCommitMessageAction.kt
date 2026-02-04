// =============================================================================
// GenerateCommitMessageAction.kt
// =============================================================================
// Action to generate commit messages from staged changes.
//
// Available from:
// - VCS menu
// - Commit dialog
// - Keyboard shortcut
//
// DESIGN NOTES:
// - Analyzes staged changes
// - Generates conventional commit message
// - Copies to clipboard for easy use
// =============================================================================

package com.sidekick.generation.commit

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Action to generate a commit message from staged changes.
 *
 * Analyzes the current staged changes and generates a conventional
 * commit message using LLM.
 *
 * ## Keyboard Shortcut
 * Default: Alt+Shift+C
 *
 * ## Output
 * The generated message is copied to clipboard.
 */
class GenerateCommitMessageAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GenerateCommitMessageAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        LOG.info("Generate Commit Message action triggered")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating Commit Message...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing staged changes..."
                indicator.fraction = 0.2

                val result = runBlocking {
                    val service = CommitGenService.getInstance(project)
                    service.generateCommitMessage()
                }

                indicator.fraction = 0.9

                if (result.success && result.message != null) {
                    val formatted = result.message.format()
                    copyToClipboard(formatted)
                    showSuccessNotification(project, result)
                } else {
                    showErrorNotification(project, result.error ?: "No staged changes")
                }

                indicator.fraction = 1.0
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, selection)
            LOG.info("Commit message copied to clipboard")
        } catch (e: Exception) {
            LOG.warn("Failed to copy to clipboard: ${e.message}")
        }
    }

    private fun showSuccessNotification(project: Project, result: CommitGenResult) {
        val message = result.message ?: return
        val analysis = result.analysis
        
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Commit Message Generated",
                    "${message.header()}\n\n${analysis.summary()}\n\nCopied to clipboard!",
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
                    "Commit Message Generation Failed",
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
 * Action to generate a quick commit message without LLM.
 */
class QuickCommitMessageAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val service = CommitGenService.getInstance(project)
        val analysis = service.analyzeStaged()
        
        if (!analysis.hasChanges) {
            showNotification(project, "No staged changes", NotificationType.WARNING)
            return
        }
        
        val message = service.generateFallback(analysis)
        val formatted = message.format()
        
        // Copy to clipboard
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(formatted)
            clipboard.setContents(selection, selection)
            
            showNotification(
                project,
                "${message.header()}\n\nCopied to clipboard!",
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            showNotification(project, "Failed to copy: ${e.message}", NotificationType.ERROR)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun showNotification(project: Project, message: String, type: NotificationType) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification("Commit Message", message, type)
                .notify(project)
        } catch (_: Exception) {}
    }
}

/**
 * Action to show commit type picker.
 */
class PickCommitTypeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        // This would show a popup with commit type options
        // For now, just list available types in notification
        val project = e.project ?: return
        
        val types = ConventionalType.all().joinToString("\n") { type ->
            "${type.emoji} ${type.prefix}: ${type.description}"
        }
        
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Conventional Commit Types",
                    types,
                    NotificationType.INFORMATION
                )
                .notify(project)
        } catch (_: Exception) {}
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
