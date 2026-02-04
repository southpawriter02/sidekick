// =============================================================================
// PromptTemplate.kt
// =============================================================================
// Defines reusable prompt templates with variable substitution.
//
// This includes:
// - Template definitions with placeholders
// - Variable substitution engine
// - Template registry and categories
//
// DESIGN NOTES:
// - Templates use {{VARIABLE}} syntax for placeholders
// - Supports default values: {{VAR:default}}
// - Categories for organization
// =============================================================================

package com.sidekick.prompts

import com.sidekick.context.EditorContext
import com.sidekick.context.ProjectContext
import com.sidekick.context.SymbolContext

/**
 * Represents a reusable prompt template with variable placeholders.
 *
 * Templates support:
 * - Simple placeholders: {{VARIABLE}}
 * - Default values: {{VARIABLE:default value}}
 * - Context auto-injection: {{SELECTION}}, {{FILE}}, {{LANGUAGE}}
 *
 * @property id Unique identifier
 * @property name Display name
 * @property description Short description
 * @property category Template category for organization
 * @property template The template string with placeholders
 * @property variables List of required variable names
 */
data class PromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val template: String,
    val variables: List<TemplateVariable> = emptyList()
) {
    companion object {
        /**
         * Regex that matches placeholders like {{NAME}} or {{NAME:default}}
         */
        private val PLACEHOLDER_REGEX = Regex("""\{\{(\w+)(?::([^}]*))?\}\}""")
        
        // =====================================================================
        // Built-in Templates
        // =====================================================================
        
        val CODE_REVIEW = PromptTemplate(
            id = "code_review",
            name = "Code Review",
            description = "Perform a comprehensive code review",
            category = TemplateCategory.REVIEW,
            template = """
                Please perform a comprehensive code review of the following {{LANGUAGE}} code:
                
                ```{{LANGUAGE}}
                {{SELECTION}}
                ```
                
                Focus on:
                1. Code quality and readability
                2. Potential bugs or edge cases
                3. Performance considerations
                4. Security concerns
                5. Adherence to {{LANGUAGE}} best practices
                
                Provide specific, actionable feedback with code examples where appropriate.
            """.trimIndent()
        )
        
        val EXPLAIN_ERROR = PromptTemplate(
            id = "explain_error",
            name = "Explain Error",
            description = "Explain an error message and suggest fixes",
            category = TemplateCategory.DEBUG,
            template = """
                I'm getting this error in my {{LANGUAGE}} code:
                
                ```
                {{ERROR}}
                ```
                
                Here's the relevant code:
                
                ```{{LANGUAGE}}
                {{SELECTION}}
                ```
                
                Please:
                1. Explain what this error means
                2. Identify the root cause
                3. Suggest how to fix it
                4. Explain how to prevent similar errors
            """.trimIndent(),
            variables = listOf(
                TemplateVariable("ERROR", "The error message", required = true)
            )
        )
        
        val WRITE_FUNCTION = PromptTemplate(
            id = "write_function",
            name = "Write Function",
            description = "Generate a function from description",
            category = TemplateCategory.GENERATE,
            template = """
                Please write a {{LANGUAGE}} function that:
                
                {{DESCRIPTION}}
                
                Requirements:
                - Use idiomatic {{LANGUAGE}} style
                - Include appropriate error handling
                - Add documentation comments
                - Follow the project's conventions
                
                {{#if SIGNATURE}}
                Use this signature: {{SIGNATURE}}
                {{/if}}
            """.trimIndent(),
            variables = listOf(
                TemplateVariable("DESCRIPTION", "What the function should do", required = true),
                TemplateVariable("SIGNATURE", "Optional function signature", required = false)
            )
        )
        
        val CONVERT_CODE = PromptTemplate(
            id = "convert_code",
            name = "Convert Code",
            description = "Convert code between languages or styles",
            category = TemplateCategory.TRANSFORM,
            template = """
                Please convert the following code from {{SOURCE_LANGUAGE}} to {{TARGET_LANGUAGE}}:
                
                ```{{SOURCE_LANGUAGE}}
                {{SELECTION}}
                ```
                
                Requirements:
                - Use idiomatic {{TARGET_LANGUAGE}} patterns
                - Preserve the original functionality
                - Use equivalent libraries where applicable
                - Add comments explaining significant differences
            """.trimIndent(),
            variables = listOf(
                TemplateVariable("SOURCE_LANGUAGE", "Source language", required = false, defaultValue = "{{LANGUAGE}}"),
                TemplateVariable("TARGET_LANGUAGE", "Target language", required = true)
            )
        )
        
        val ADD_COMMENTS = PromptTemplate(
            id = "add_comments",
            name = "Add Comments",
            description = "Add inline comments to code",
            category = TemplateCategory.DOCUMENT,
            template = """
                Please add helpful inline comments to the following {{LANGUAGE}} code:
                
                ```{{LANGUAGE}}
                {{SELECTION}}
                ```
                
                Guidelines:
                - Explain complex logic or algorithms
                - Document non-obvious decisions
                - Don't state the obvious
                - Use {{LANGUAGE}}'s comment conventions
            """.trimIndent()
        )
        
        val SIMPLIFY_CODE = PromptTemplate(
            id = "simplify_code",
            name = "Simplify Code",
            description = "Simplify complex code",
            category = TemplateCategory.TRANSFORM,
            template = """
                Please simplify the following {{LANGUAGE}} code while maintaining its functionality:
                
                ```{{LANGUAGE}}
                {{SELECTION}}
                ```
                
                Goals:
                - Reduce complexity
                - Improve readability
                - Use standard library functions where applicable
                - Remove redundancy
                
                Explain each simplification you make.
            """.trimIndent()
        )
        
        val ASK_QUESTION = PromptTemplate(
            id = "ask_question",
            name = "Ask About Code",
            description = "Ask a question about the code",
            category = TemplateCategory.LEARN,
            template = """
                {{QUESTION}}
                
                Here's the code I'm referring to:
                
                ```{{LANGUAGE}}
                {{SELECTION}}
                ```
                
                Context:
                - File: {{FILE}}
                - Project: {{PROJECT}}
            """.trimIndent(),
            variables = listOf(
                TemplateVariable("QUESTION", "Your question about the code", required = true)
            )
        )
        
        val COMPARE_APPROACHES = PromptTemplate(
            id = "compare_approaches",
            name = "Compare Approaches",
            description = "Compare different implementation approaches",
            category = TemplateCategory.LEARN,
            template = """
                I'm trying to decide between different approaches for {{TASK}} in {{LANGUAGE}}.
                
                Current approach:
                ```{{LANGUAGE}}
                {{SELECTION}}
                ```
                
                Please:
                1. Analyze the current approach
                2. Suggest {{NUM_ALTERNATIVES:2}} alternative approaches
                3. Compare pros/cons of each
                4. Recommend the best approach for this use case
            """.trimIndent(),
            variables = listOf(
                TemplateVariable("TASK", "What you're trying to accomplish", required = true),
                TemplateVariable("NUM_ALTERNATIVES", "Number of alternatives", required = false, defaultValue = "2")
            )
        )
        
        /**
         * All built-in templates.
         */
        val BUILT_IN_TEMPLATES = listOf(
            CODE_REVIEW,
            EXPLAIN_ERROR,
            WRITE_FUNCTION,
            CONVERT_CODE,
            ADD_COMMENTS,
            SIMPLIFY_CODE,
            ASK_QUESTION,
            COMPARE_APPROACHES
        )
        
        /**
         * Gets a template by ID.
         */
        fun getById(id: String): PromptTemplate? {
            return BUILT_IN_TEMPLATES.find { it.id == id }
        }
        
        /**
         * Gets templates by category.
         */
        fun getByCategory(category: TemplateCategory): List<PromptTemplate> {
            return BUILT_IN_TEMPLATES.filter { it.category == category }
        }
    }

    // -------------------------------------------------------------------------
    // Prompt Building
    // -------------------------------------------------------------------------
    
    /**
     * Builds the final prompt by substituting placeholders.
     *
     * @param editorContext Current editor context
     * @param symbolContext Symbol at cursor
     * @param projectContext Project context
     * @param customVariables Custom variable values
     * @return The fully resolved prompt
     */
    fun build(
        editorContext: EditorContext = EditorContext.EMPTY,
        symbolContext: SymbolContext = SymbolContext.EMPTY,
        projectContext: ProjectContext = ProjectContext.EMPTY,
        customVariables: Map<String, String> = emptyMap()
    ): String {
        var result = template
        
        // Build context variables
        val contextVars = buildContextVariables(editorContext, symbolContext, projectContext)
        
        // Merge with custom variables (custom takes precedence)
        val allVars = contextVars + customVariables
        
        // Replace all placeholders
        result = PLACEHOLDER_REGEX.replace(result) { match ->
            val varName = match.groupValues[1]
            val defaultValue = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            
            allVars[varName] ?: defaultValue ?: match.value
        }
        
        // Handle conditional blocks {{#if VAR}}...{{/if}}
        result = processConditionals(result, allVars)
        
        return result.trim()
    }
    
    /**
     * Gets the list of unresolved variables in the template.
     */
    fun getUnresolvedVariables(
        editorContext: EditorContext = EditorContext.EMPTY,
        projectContext: ProjectContext = ProjectContext.EMPTY,
        providedVariables: Set<String> = emptySet()
    ): List<TemplateVariable> {
        val contextVars = setOf(
            "SELECTION", "FILE", "LANGUAGE", "PROJECT", "SYMBOL"
        )
        
        val allProvided = contextVars + providedVariables + 
            (if (editorContext.hasSelection) setOf("SELECTION") else emptySet()) +
            (if (editorContext.hasFile) setOf("FILE") else emptySet())
        
        return variables.filter { variable ->
            variable.required && variable.name !in allProvided
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------
    
    private fun buildContextVariables(
        editorContext: EditorContext,
        symbolContext: SymbolContext,
        projectContext: ProjectContext
    ): Map<String, String> {
        return buildMap {
            // Editor context
            put("SELECTION", editorContext.selection ?: "")
            put("FILE", editorContext.fileName)
            put("LANGUAGE", editorContext.language)
            put("FILEPATH", editorContext.filePath)
            put("CURSOR_LINE", editorContext.cursorLine.toString())
            
            // Symbol context
            put("SYMBOL", symbolContext.name)
            put("SYMBOL_KIND", symbolContext.kind.displayName)
            put("SYMBOL_SIGNATURE", symbolContext.signature ?: "")
            
            // Project context
            put("PROJECT", projectContext.name)
            put("PROJECT_TYPE", projectContext.projectType.displayName)
            put("FRAMEWORKS", projectContext.frameworkHints.joinToString(", "))
        }
    }
    
    private fun processConditionals(template: String, variables: Map<String, String>): String {
        val conditionalRegex = Regex("""\{\{#if (\w+)\}\}(.*?)\{\{/if\}\}""", RegexOption.DOT_MATCHES_ALL)
        
        return conditionalRegex.replace(template) { match ->
            val varName = match.groupValues[1]
            val content = match.groupValues[2]
            
            val varValue = variables[varName]
            if (!varValue.isNullOrBlank()) {
                content.trim()
            } else {
                ""
            }
        }
    }
}

/**
 * Categories for organizing prompt templates.
 */
enum class TemplateCategory(val displayName: String) {
    REVIEW("Code Review"),
    DEBUG("Debugging"),
    GENERATE("Generation"),
    TRANSFORM("Transformation"),
    DOCUMENT("Documentation"),
    LEARN("Learning");
    
    companion object {
        fun fromString(value: String): TemplateCategory? {
            return entries.find { 
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            }
        }
    }
}

/**
 * Describes a variable in a prompt template.
 */
data class TemplateVariable(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: String? = null
)
