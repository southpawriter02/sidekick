// =============================================================================
// GenerateDtoAction.kt
// =============================================================================
// Editor action for generating DTOs from JSON.
//
// Available from:
// - Generate menu (Alt+Insert)
// - Sidekick menu
// - Keyboard shortcut
//
// DESIGN NOTES:
// - Reads JSON from clipboard
// - Auto-detects target language
// - Inserts code at cursor
// =============================================================================

package com.sidekick.generation.dto

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Action to generate DTO from JSON in clipboard.
 *
 * ## Keyboard Shortcut
 * Default: Alt+Shift+J
 */
class GenerateDtoAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GenerateDtoAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)

        LOG.info("Generate DTO action triggered")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating DTO...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Parsing JSON..."
                indicator.fraction = 0.2

                val result = runBlocking {
                    val service = DtoGenService.getInstance(project)
                    service.generateFromClipboard()
                }

                indicator.text = "Generating code..."
                indicator.fraction = 0.8

                if (result.success) {
                    if (editor != null) {
                        insertCode(project, editor, result)
                    } else {
                        copyToClipboard(result.fullCode())
                        showSuccessNotification(project, result)
                    }
                } else {
                    showErrorNotification(project, result.error ?: "Generation failed")
                }

                indicator.fraction = 1.0
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        // Check if clipboard has JSON
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val content = clipboard.getData(DataFlavor.stringFlavor) as? String
            if (content != null && content.trim().startsWith("{") || content?.trim()?.startsWith("[") == true) {
                e.presentation.text = "Sidekick: Generate DTO from JSON"
            } else {
                e.presentation.text = "Sidekick: Generate DTO (copy JSON first)"
            }
        } catch (_: Exception) {
            e.presentation.text = "Sidekick: Generate DTO"
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------

    private fun insertCode(
        project: Project,
        editor: com.intellij.openapi.editor.Editor,
        result: DtoGenResult
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val offset = editor.caretModel.offset
            document.insertString(offset, result.fullCode())
        }
        
        showSuccessNotification(project, result, inserted = true)
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

    private fun showSuccessNotification(project: Project, result: DtoGenResult, inserted: Boolean = false) {
        val action = if (inserted) "inserted at cursor" else "copied to clipboard"
        val nested = if (result.nestedClasses.isNotEmpty()) {
            " + ${result.nestedClasses.size} nested class(es)"
        } else ""
        
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "DTO Generated",
                    "**${result.className}**$nested $action",
                    NotificationType.INFORMATION
                )
                .notify(project)
        } catch (_: Exception) {}
    }

    private fun showErrorNotification(project: Project, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "DTO Generation Failed",
                    message,
                    NotificationType.WARNING
                )
                .notify(project)
        } catch (_: Exception) {}
    }
}

/**
 * Action to generate DTO without LLM (quick mode).
 */
class QuickGenerateDtoAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val clipboard = try {
            Toolkit.getDefaultToolkit().systemClipboard
                .getData(DataFlavor.stringFlavor) as? String ?: ""
        } catch (_: Exception) { "" }
        
        if (clipboard.isBlank()) {
            showNotification(project, "Clipboard is empty", NotificationType.WARNING)
            return
        }
        
        val service = DtoGenService.getInstance(project)
        
        if (!service.isValidJson(clipboard)) {
            showNotification(project, "Clipboard does not contain valid JSON", NotificationType.WARNING)
            return
        }
        
        val className = NamingUtils.inferClassName(clipboard)
        val request = DtoGenRequest.simple(clipboard, className)
        val result = service.generateQuick(request)
        
        if (result.success) {
            // Copy to clipboard
            try {
                val selection = StringSelection(result.fullCode())
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            } catch (_: Exception) {}
            
            showNotification(
                project,
                "Generated ${result.className} (${result.code.lines().size} lines) - copied to clipboard",
                NotificationType.INFORMATION
            )
        } else {
            showNotification(project, result.error ?: "Generation failed", NotificationType.WARNING)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun showNotification(project: Project, message: String, type: NotificationType) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification("Quick DTO", message, type)
                .notify(project)
        } catch (_: Exception) {}
    }
}
