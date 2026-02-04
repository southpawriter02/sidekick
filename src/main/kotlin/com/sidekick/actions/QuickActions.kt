// =============================================================================
// QuickActions.kt
// =============================================================================
// Quick action classes for keyboard shortcuts.
//
// These actions:
// - Execute specific code actions directly
// - Are bound to keyboard shortcuts
// - Work with selected text
//
// DESIGN NOTES:
// - Each class wraps a specific CodeAction
// - Inherits common behavior from BaseQuickAction
// =============================================================================

package com.sidekick.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.JPanel

/**
 * Base class for quick actions with common behavior.
 */
abstract class BaseQuickAction(
    private val actionId: String
) : AnAction() {
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        val service = CodeActionService.getInstance(project)
        val prompt = service.executeAction(actionId) ?: return
        
        sendToChat(project, prompt)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        
        val service = CodeActionService.getInstance(project)
        e.presentation.isEnabled = service.canExecuteAction(actionId)
    }
    
    /**
     * Sends the prompt to the Sidekick chat tool window.
     */
    private fun sendToChat(project: Project, prompt: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Sidekick")
        
        if (toolWindow != null) {
            toolWindow.show {
                val content = toolWindow.contentManager.getContent(0)
                val component = content?.component
                
                if (component is JPanel) {
                    findChatPanel(component)?.sendMessage(prompt)
                }
            }
        }
    }
    
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

/**
 * Quick action for explaining selected code.
 * Keyboard shortcut: Alt+Shift+E
 */
class QuickExplainAction : BaseQuickAction("explain")

/**
 * Quick action for refactoring selected code.
 * Keyboard shortcut: Alt+Shift+R
 */
class QuickRefactorAction : BaseQuickAction("refactor")

/**
 * Quick action for generating tests.
 * Keyboard shortcut: Alt+Shift+T
 */
class QuickGenerateTestsAction : BaseQuickAction("generate_tests")
