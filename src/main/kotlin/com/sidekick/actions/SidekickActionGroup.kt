// =============================================================================
// SidekickActionGroup.kt
// =============================================================================
// Action group for Sidekick actions in the context menu.
//
// This group:
// - Appears in the editor context menu
// - Contains code action sub-actions
// - Dynamically shows/hides based on context
//
// DESIGN NOTES:
// - Dynamic children based on available actions
// - Integrates with CodeActionService
// - Uses compact popup for selection actions
// =============================================================================

package com.sidekick.actions

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * Action group containing Sidekick code actions.
 *
 * Appears in the editor context menu under "Sidekick" with
 * dynamic children based on available actions.
 */
class SidekickActionGroup : DefaultActionGroup() {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        
        val service = CodeActionService.getInstance(project)
        val availableActions = service.getAvailableActions()
        
        return availableActions.map { codeAction ->
            ExecuteCodeActionAction(codeAction)
        }.toTypedArray()
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        
        val service = CodeActionService.getInstance(project)
        val hasAvailableActions = service.getAvailableActions().isNotEmpty()
        
        e.presentation.isVisible = hasAvailableActions
    }
}

/**
 * Individual action for executing a code action.
 */
class ExecuteCodeActionAction(
    private val codeAction: CodeAction
) : AnAction(codeAction.name, codeAction.description, null) {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val service = CodeActionService.getInstance(project)
        val prompt = service.executeAction(codeAction)
        
        if (prompt != null) {
            // Send to chat via callback or tool window
            sendToChat(project, prompt)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        
        val service = CodeActionService.getInstance(project)
        e.presentation.isEnabled = service.canExecuteAction(codeAction.id)
        
        val reason = service.getActionDisabledReason(codeAction.id)
        if (reason != null) {
            e.presentation.description = reason
        }
    }
    
    /**
     * Sends the prompt to the Sidekick chat.
     */
    private fun sendToChat(project: Project, prompt: String) {
        // Get the Sidekick tool window and send message
        val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Sidekick")
        
        if (toolWindow != null) {
            // Show the tool window
            toolWindow.show {
                // Get the content and send message
                val content = toolWindow.contentManager.getContent(0)
                val component = content?.component
                
                // Find ChatPanel and send message
                if (component is javax.swing.JPanel) {
                    findChatPanel(component)?.sendMessage(prompt)
                }
            }
        }
    }
    
    /**
     * Recursively finds ChatPanel in the component hierarchy.
     */
    private fun findChatPanel(component: java.awt.Component): com.sidekick.ui.ChatPanel? {
        if (component is com.sidekick.ui.ChatPanel) {
            return component
        }
        
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findChatPanel(child)
                if (found != null) return found
            }
        }
        
        return null
    }
}
