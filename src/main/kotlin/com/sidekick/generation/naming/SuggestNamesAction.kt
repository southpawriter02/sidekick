// =============================================================================
// SuggestNamesAction.kt
// =============================================================================
// Editor action for variable naming suggestions.
//
// Available from:
// - Editor context menu (Sidekick > Suggest Names)
// - Refactor menu
// - Keyboard shortcut
//
// DESIGN NOTES:
// - Shows popup with name suggestions
// - Supports rename refactoring integration
// - Copies best suggestion to clipboard
// =============================================================================

package com.sidekick.generation.naming

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
 * Action to suggest variable names for the symbol at cursor.
 *
 * Generates naming suggestions based on context, type, and
 * language conventions.
 *
 * ## Keyboard Shortcut
 * Default: Alt+Shift+N
 */
class SuggestNamesAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(SuggestNamesAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        LOG.info("Suggest Names action triggered")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Suggesting Names...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing context..."
                indicator.fraction = 0.2

                val result = runBlocking {
                    val service = NamingService.getInstance(project)
                    service.suggestForCurrentContext()
                }

                indicator.fraction = 0.9

                if (result.success && result.suggestions.isNotEmpty()) {
                    showSuggestions(project, result)
                } else {
                    showErrorNotification(project, result.error ?: "No suggestions available")
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
    // Private Methods
    // -------------------------------------------------------------------------

    private fun showSuggestions(project: Project, result: NamingResult) {
        val suggestions = result.suggestions
        val best = result.bestSuggestion()
        
        // Copy best suggestion to clipboard
        best?.let { copyToClipboard(it.name) }
        
        // Build suggestions list
        val suggestionsList = suggestions.mapIndexed { index, suggestion ->
            val marker = if (index == 0) "→" else "  "
            "$marker ${suggestion.name} (${suggestion.rationale})"
        }.joinToString("\n")
        
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Name Suggestions",
                    "$suggestionsList\n\nBest suggestion copied to clipboard!",
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
            LOG.info("Name copied to clipboard: $text")
        } catch (e: Exception) {
            LOG.warn("Failed to copy to clipboard: ${e.message}")
        }
    }

    private fun showErrorNotification(project: Project, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Name Suggestion Failed",
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
 * Action to suggest names using patterns only (no LLM).
 */
class QuickSuggestNamesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val service = NamingService.getInstance(project)
        val editorService = com.sidekick.context.EditorContextService.getInstance(project)
        
        val symbol = editorService.getSymbolAtCursor()
        val context = editorService.getCurrentContext()
        
        val request = NamingRequest.forSymbol(symbol, context.language)
        val result = service.suggestFromPatterns(request)
        
        if (result.success && result.suggestions.isNotEmpty()) {
            val names = result.suggestions.joinToString(", ") { it.name }
            
            // Copy first to clipboard
            result.bestSuggestion()?.let { best ->
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val selection = java.awt.datatransfer.StringSelection(best.name)
                clipboard.setContents(selection, selection)
            }
            
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Sidekick.Notifications")
                    .createNotification(
                        "Quick Name Suggestions",
                        "Suggestions: $names\n\nFirst suggestion copied to clipboard!",
                        NotificationType.INFORMATION
                    )
                    .notify(project)
            } catch (_: Exception) {}
        } else {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Sidekick.Notifications")
                    .createNotification(
                        "No Suggestions",
                        "No pattern-based suggestions available for this context.",
                        NotificationType.WARNING
                    )
                    .notify(project)
            } catch (_: Exception) {}
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }
}

/**
 * Action to convert selected name to a different convention.
 */
class ConvertNamingConventionAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        val selection = editor.selectionModel.selectedText
        if (selection.isNullOrBlank()) {
            showNotification(project, "Select a name to convert", NotificationType.WARNING)
            return
        }
        
        val service = NamingService.getInstance(project)
        val currentConvention = service.detectConvention(selection)
        
        // Convert to common alternatives
        val conversions = listOf(
            NamingConvention.CAMEL_CASE,
            NamingConvention.PASCAL_CASE,
            NamingConvention.SNAKE_CASE,
            NamingConvention.SCREAMING_SNAKE_CASE
        ).filter { it != currentConvention }
         .map { "${it.displayName}: ${service.convertName(selection, it)}" }
         .joinToString("\n")
        
        showNotification(
            project,
            "Current: ${currentConvention.displayName}\n\nConversions:\n$conversions",
            NotificationType.INFORMATION
        )
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
                .createNotification("Naming Convention", message, type)
                .notify(project)
        } catch (_: Exception) {}
    }
}
