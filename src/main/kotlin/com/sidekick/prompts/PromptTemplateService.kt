// =============================================================================
// PromptTemplateService.kt
// =============================================================================
// Service for managing and executing prompt templates.
//
// This service:
// - Manages built-in and custom templates
// - Handles template execution with variable prompting
// - Persists user-defined templates
//
// DESIGN NOTES:
// - Project-level service
// - Templates can be customized per project
// - Integrates with context services
// =============================================================================

package com.sidekick.prompts

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContext
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContext
import com.sidekick.context.ProjectContextService
import com.sidekick.context.SymbolContext

/**
 * Service for managing and executing prompt templates.
 *
 * Provides access to built-in templates and supports custom templates
 * with variable substitution.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = PromptTemplateService.getInstance(project)
 * val template = service.getTemplate("code_review")
 * val prompt = service.executeTemplate(template!!) { varName ->
 *     // Prompt user for variable value
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class PromptTemplateService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(PromptTemplateService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): PromptTemplateService {
            return project.getService(PromptTemplateService::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Template Registry
    // -------------------------------------------------------------------------
    
    /**
     * Custom templates added at runtime.
     */
    private val customTemplates: MutableList<PromptTemplate> = mutableListOf()
    
    /**
     * Recently used template IDs for quick access.
     */
    private val recentTemplateIds: MutableList<String> = mutableListOf()
    
    /**
     * Maximum number of recent templates to track.
     */
    private val maxRecentTemplates = 5

    // -------------------------------------------------------------------------
    // Public Methods - Template Access
    // -------------------------------------------------------------------------
    
    /**
     * Gets all available templates (built-in + custom).
     */
    fun getAllTemplates(): List<PromptTemplate> {
        return PromptTemplate.BUILT_IN_TEMPLATES + customTemplates
    }
    
    /**
     * Gets a template by ID.
     */
    fun getTemplate(id: String): PromptTemplate? {
        return PromptTemplate.getById(id) ?: customTemplates.find { it.id == id }
    }
    
    /**
     * Gets templates by category.
     */
    fun getTemplatesByCategory(category: TemplateCategory): List<PromptTemplate> {
        return getAllTemplates().filter { it.category == category }
    }
    
    /**
     * Gets recently used templates.
     */
    fun getRecentTemplates(): List<PromptTemplate> {
        return recentTemplateIds.mapNotNull { getTemplate(it) }
    }
    
    /**
     * Gets templates matching a search query.
     */
    fun searchTemplates(query: String): List<PromptTemplate> {
        if (query.isBlank()) return getAllTemplates()
        
        val lowerQuery = query.lowercase()
        return getAllTemplates().filter { template ->
            template.name.lowercase().contains(lowerQuery) ||
            template.description.lowercase().contains(lowerQuery) ||
            template.category.displayName.lowercase().contains(lowerQuery)
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods - Template Management
    // -------------------------------------------------------------------------
    
    /**
     * Registers a custom template.
     */
    fun registerTemplate(template: PromptTemplate) {
        if (customTemplates.none { it.id == template.id }) {
            customTemplates.add(template)
            LOG.info("Registered custom template: ${template.id}")
        }
    }
    
    /**
     * Unregisters a custom template.
     */
    fun unregisterTemplate(templateId: String) {
        customTemplates.removeIf { it.id == templateId }
    }
    
    /**
     * Marks a template as recently used.
     */
    fun markAsRecentlyUsed(templateId: String) {
        recentTemplateIds.remove(templateId)
        recentTemplateIds.add(0, templateId)
        
        if (recentTemplateIds.size > maxRecentTemplates) {
            recentTemplateIds.removeAt(recentTemplateIds.lastIndex)
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods - Template Execution
    // -------------------------------------------------------------------------
    
    /**
     * Executes a template with current context and custom variables.
     *
     * @param template The template to execute
     * @param customVariables Variable values provided by user
     * @return The generated prompt
     */
    fun executeTemplate(
        template: PromptTemplate,
        customVariables: Map<String, String> = emptyMap()
    ): String {
        markAsRecentlyUsed(template.id)
        
        val editorContext = getEditorContext()
        val symbolContext = getSymbolContext()
        val projectContext = getProjectContext()
        
        LOG.info("Executing template: ${template.id}")
        
        return template.build(
            editorContext = editorContext,
            symbolContext = symbolContext,
            projectContext = projectContext,
            customVariables = customVariables
        )
    }
    
    /**
     * Executes a template by ID.
     */
    fun executeTemplate(
        templateId: String,
        customVariables: Map<String, String> = emptyMap()
    ): String? {
        val template = getTemplate(templateId)
        if (template == null) {
            LOG.warn("Template not found: $templateId")
            return null
        }
        
        return executeTemplate(template, customVariables)
    }
    
    /**
     * Gets variables that need user input for a template.
     */
    fun getRequiredVariables(template: PromptTemplate): List<TemplateVariable> {
        val editorContext = getEditorContext()
        val projectContext = getProjectContext()
        
        return template.getUnresolvedVariables(editorContext, projectContext)
    }
    
    /**
     * Checks if a template can be executed with current context.
     */
    fun canExecuteTemplate(template: PromptTemplate): Boolean {
        val required = getRequiredVariables(template)
        return required.isEmpty()
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
