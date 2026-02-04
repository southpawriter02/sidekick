// =============================================================================
// CodeActionService.kt
// =============================================================================
// Service for executing code actions.
//
// This service:
// - Registers and manages code actions
// - Executes actions via the chat system
// - Provides action availability checks
//
// DESIGN NOTES:
// - Project-level service
// - Delegates chat execution to ChatController
// - Integrates with context services
// =============================================================================

package com.sidekick.actions

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContext
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContext
import com.sidekick.context.ProjectContextService
import com.sidekick.context.SymbolContext

/**
 * Service for executing code actions on selected code.
 *
 * Provides methods to check action availability and execute actions
 * via the chat interface.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = CodeActionService.getInstance(project)
 * val actions = service.getAvailableActions()
 * actions.first().let { action ->
 *     service.executeAction(action.id) { prompt ->
 *         // Handle the generated prompt
 *     }
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class CodeActionService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(CodeActionService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): CodeActionService {
            return project.getService(CodeActionService::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Action Registry
    // -------------------------------------------------------------------------
    
    /**
     * All registered code actions.
     */
    private val actions: MutableList<CodeAction> = CodeAction.BUILT_IN_ACTIONS.toMutableList()
    
    /**
     * Registered action execution callback.
     */
    private var onExecuteAction: ((String) -> Unit)? = null

    // -------------------------------------------------------------------------
    // Public Methods - Registration
    // -------------------------------------------------------------------------
    
    /**
     * Registers a custom code action.
     */
    fun registerAction(action: CodeAction) {
        if (actions.none { it.id == action.id }) {
            actions.add(action)
            LOG.info("Registered code action: ${action.id}")
        }
    }
    
    /**
     * Unregisters a code action by ID.
     */
    fun unregisterAction(actionId: String) {
        actions.removeIf { it.id == actionId }
    }
    
    /**
     * Sets the callback for action execution.
     */
    fun setExecutionCallback(callback: (String) -> Unit) {
        onExecuteAction = callback
    }

    // -------------------------------------------------------------------------
    // Public Methods - Action Access
    // -------------------------------------------------------------------------
    
    /**
     * Gets all registered actions.
     */
    fun getAllActions(): List<CodeAction> {
        return actions.toList()
    }
    
    /**
     * Gets an action by ID.
     */
    fun getAction(id: String): CodeAction? {
        return actions.find { it.id == id }
    }
    
    /**
     * Gets all actions that can be executed with current context.
     */
    fun getAvailableActions(): List<CodeAction> {
        val editorContext = getEditorContext()
        val symbolContext = getSymbolContext()
        
        return actions.filter { action ->
            action.canExecute(editorContext, symbolContext)
        }
    }
    
    /**
     * Gets actions that require selection (for context menu).
     */
    fun getSelectionActions(): List<CodeAction> {
        return actions.filter { it.requiresSelection }
    }
    
    /**
     * Gets actions for symbols (for cursor context).
     */
    fun getSymbolActions(): List<CodeAction> {
        return actions.filter { it.requiresSymbol }
    }

    // -------------------------------------------------------------------------
    // Public Methods - Execution
    // -------------------------------------------------------------------------
    
    /**
     * Executes a code action by ID.
     *
     * @param actionId The action ID to execute
     * @return The generated prompt, or null if action not found/cannot execute
     */
    fun executeAction(actionId: String): String? {
        val action = getAction(actionId)
        if (action == null) {
            LOG.warn("Action not found: $actionId")
            return null
        }
        
        return executeAction(action)
    }
    
    /**
     * Executes a code action.
     *
     * @param action The action to execute
     * @return The generated prompt, or null if cannot execute
     */
    fun executeAction(action: CodeAction): String? {
        val editorContext = getEditorContext()
        val symbolContext = getSymbolContext()
        val projectContext = getProjectContext()
        
        if (!action.canExecute(editorContext, symbolContext)) {
            val reason = action.getDisabledReason(editorContext, symbolContext)
            LOG.info("Cannot execute action ${action.id}: $reason")
            return null
        }
        
        val prompt = action.buildPrompt(editorContext, symbolContext, projectContext)
        LOG.info("Executing action ${action.id}, prompt length: ${prompt.length}")
        
        // Notify callback if registered
        onExecuteAction?.invoke(prompt)
        
        return prompt
    }
    
    /**
     * Checks if an action can be executed.
     */
    fun canExecuteAction(actionId: String): Boolean {
        val action = getAction(actionId) ?: return false
        val editorContext = getEditorContext()
        val symbolContext = getSymbolContext()
        
        return action.canExecute(editorContext, symbolContext)
    }
    
    /**
     * Gets the disabled reason for an action.
     */
    fun getActionDisabledReason(actionId: String): String? {
        val action = getAction(actionId) ?: return "Action not found"
        val editorContext = getEditorContext()
        val symbolContext = getSymbolContext()
        
        return action.getDisabledReason(editorContext, symbolContext)
    }

    // -------------------------------------------------------------------------
    // Private Methods - Context Access
    // -------------------------------------------------------------------------
    
    private fun getEditorContext(): EditorContext {
        return try {
            EditorContextService.getInstance(project).getCurrentContext()
        } catch (e: Exception) {
            LOG.debug("Failed to get editor context: ${e.message}")
            EditorContext.EMPTY
        }
    }
    
    private fun getSymbolContext(): SymbolContext {
        return try {
            EditorContextService.getInstance(project).getSymbolAtCursor()
        } catch (e: Exception) {
            LOG.debug("Failed to get symbol context: ${e.message}")
            SymbolContext.EMPTY
        }
    }
    
    private fun getProjectContext(): ProjectContext {
        return try {
            ProjectContextService.getInstance(project).getProjectContext()
        } catch (e: Exception) {
            LOG.debug("Failed to get project context: ${e.message}")
            ProjectContext.EMPTY
        }
    }
}
