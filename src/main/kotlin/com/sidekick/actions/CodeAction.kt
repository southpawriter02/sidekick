// =============================================================================
// CodeAction.kt
// =============================================================================
// Defines code actions that can be executed on selected code.
//
// This includes:
// - Action types (Explain, Refactor, Test, Document, etc.)
// - Action definitions with prompts
// - Action registry
//
// DESIGN NOTES:
// - Actions are declarative definitions
// - Each action has a prompt template
// - Actions integrate with ContextBuilder
// =============================================================================

package com.sidekick.actions

import com.sidekick.context.ContextBuilder
import com.sidekick.context.EditorContext
import com.sidekick.context.ProjectContext
import com.sidekick.context.SymbolContext

/**
 * Represents a code action that can be executed on selected code.
 *
 * @property id Unique identifier for the action
 * @property name Display name for the action
 * @property description Short description of what the action does
 * @property icon Icon name for UI display (optional)
 * @property promptTemplate The prompt template with placeholders
 * @property requiresSelection Whether this action requires selected text
 * @property requiresSymbol Whether this action requires a symbol at cursor
 */
data class CodeAction(
    val id: String,
    val name: String,
    val description: String,
    val icon: String? = null,
    val promptTemplate: String,
    val requiresSelection: Boolean = false,
    val requiresSymbol: Boolean = false
) {
    companion object {
        // =====================================================================
        // Built-in Actions
        // =====================================================================
        
        /**
         * Explain the selected code or symbol.
         */
        val EXPLAIN = CodeAction(
            id = "explain",
            name = "Explain",
            description = "Explain what the selected code does",
            icon = "explain",
            promptTemplate = """
                Please explain the following code in detail:
                
                {{CODE}}
                
                Provide:
                1. A brief summary of what this code does
                2. Step-by-step breakdown of the logic
                3. Any important concepts or patterns used
                4. Potential edge cases or gotchas
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * Refactor the selected code.
         */
        val REFACTOR = CodeAction(
            id = "refactor",
            name = "Refactor",
            description = "Suggest improvements for the selected code",
            icon = "refactor",
            promptTemplate = """
                Please analyze the following code and suggest refactoring improvements:
                
                {{CODE}}
                
                Consider:
                1. Code readability and clarity
                2. Performance optimizations
                3. Design patterns that could be applied
                4. SOLID principles adherence
                5. Error handling improvements
                
                Provide the refactored code with explanations for each change.
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * Generate tests for the selected code.
         */
        val GENERATE_TESTS = CodeAction(
            id = "generate_tests",
            name = "Generate Tests",
            description = "Generate unit tests for the selected code",
            icon = "test",
            promptTemplate = """
                Please generate comprehensive unit tests for the following code:
                
                {{CODE}}
                
                Requirements:
                1. Follow the testing conventions of {{LANGUAGE}}
                2. Include tests for happy path scenarios
                3. Include edge cases and error conditions
                4. Use descriptive test names
                5. Add comments explaining what each test verifies
                
                Project uses: {{FRAMEWORKS}}
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * Add documentation to the selected code.
         */
        val DOCUMENT = CodeAction(
            id = "document",
            name = "Add Documentation",
            description = "Generate documentation comments for the code",
            icon = "document",
            promptTemplate = """
                Please add comprehensive documentation comments to the following code:
                
                {{CODE}}
                
                Requirements:
                1. Use the appropriate documentation format for {{LANGUAGE}}
                2. Document all public methods/functions
                3. Include parameter descriptions
                4. Include return value descriptions
                5. Add usage examples where helpful
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * Find and explain bugs in the code.
         */
        val FIND_BUGS = CodeAction(
            id = "find_bugs",
            name = "Find Bugs",
            description = "Analyze code for potential bugs",
            icon = "bug",
            promptTemplate = """
                Please analyze the following code for potential bugs and issues:
                
                {{CODE}}
                
                Look for:
                1. Logic errors
                2. Off-by-one errors
                3. Null/undefined reference issues
                4. Resource leaks
                5. Race conditions (if applicable)
                6. Security vulnerabilities
                
                For each issue found, explain the problem and suggest a fix.
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * Optimize the selected code for performance.
         */
        val OPTIMIZE = CodeAction(
            id = "optimize",
            name = "Optimize",
            description = "Suggest performance optimizations",
            icon = "performance",
            promptTemplate = """
                Please analyze the following code for performance improvements:
                
                {{CODE}}
                
                Consider:
                1. Time complexity optimizations
                2. Space/memory optimizations
                3. Unnecessary computations
                4. Caching opportunities
                5. Database/IO optimizations (if applicable)
                
                Provide optimized code with explanations for the improvements.
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * Explain the symbol at cursor.
         */
        val EXPLAIN_SYMBOL = CodeAction(
            id = "explain_symbol",
            name = "Explain Symbol",
            description = "Explain the symbol at cursor",
            icon = "info",
            promptTemplate = """
                Please explain this {{SYMBOL_KIND}}:
                
                Name: {{SYMBOL_NAME}}
                {{SYMBOL_SIGNATURE}}
                
                Definition:
                ```{{LANGUAGE}}
                {{SYMBOL_DEFINITION}}
                ```
                
                Provide:
                1. What this {{SYMBOL_KIND}} does
                2. How and when to use it
                3. Any important considerations
            """.trimIndent(),
            requiresSymbol = true
        )
        
        /**
         * Convert code to a different style/pattern.
         */
        val CONVERT = CodeAction(
            id = "convert",
            name = "Convert",
            description = "Convert to modern syntax or patterns",
            icon = "convert",
            promptTemplate = """
                Please convert the following code to use more modern {{LANGUAGE}} syntax and patterns:
                
                {{CODE}}
                
                Convert to:
                1. Modern language features
                2. Idiomatic expressions
                3. Cleaner syntax where available
                
                Explain each conversion you make.
            """.trimIndent(),
            requiresSelection = true
        )
        
        /**
         * All built-in actions.
         */
        val BUILT_IN_ACTIONS = listOf(
            EXPLAIN,
            REFACTOR,
            GENERATE_TESTS,
            DOCUMENT,
            FIND_BUGS,
            OPTIMIZE,
            EXPLAIN_SYMBOL,
            CONVERT
        )
        
        /**
         * Gets an action by ID.
         */
        fun getById(id: String): CodeAction? {
            return BUILT_IN_ACTIONS.find { it.id == id }
        }
    }

    // -------------------------------------------------------------------------
    // Prompt Building
    // -------------------------------------------------------------------------
    
    /**
     * Builds the full prompt for this action with context.
     *
     * @param editorContext Current editor context
     * @param symbolContext Symbol at cursor (if any)
     * @param projectContext Project context
     * @return The fully resolved prompt
     */
    fun buildPrompt(
        editorContext: EditorContext = EditorContext.EMPTY,
        symbolContext: SymbolContext = SymbolContext.EMPTY,
        projectContext: ProjectContext = ProjectContext.EMPTY
    ): String {
        var prompt = promptTemplate
        
        // Replace code placeholder
        val code = editorContext.selection ?: symbolContext.definition
        prompt = prompt.replace("{{CODE}}", code)
        
        // Replace language placeholder
        prompt = prompt.replace("{{LANGUAGE}}", editorContext.language)
        
        // Replace framework placeholder
        val frameworks = projectContext.frameworkHints.joinToString(", ")
            .ifEmpty { "standard library" }
        prompt = prompt.replace("{{FRAMEWORKS}}", frameworks)
        
        // Replace symbol placeholders
        prompt = prompt.replace("{{SYMBOL_NAME}}", symbolContext.qualifiedName)
        prompt = prompt.replace("{{SYMBOL_KIND}}", symbolContext.kind.displayName.lowercase())
        prompt = prompt.replace(
            "{{SYMBOL_SIGNATURE}}", 
            symbolContext.signature?.let { "Signature: `$it`" } ?: ""
        )
        prompt = prompt.replace("{{SYMBOL_DEFINITION}}", symbolContext.definition)
        
        return prompt.trim()
    }

    /**
     * Checks if this action can be executed given the current context.
     */
    fun canExecute(
        editorContext: EditorContext = EditorContext.EMPTY,
        symbolContext: SymbolContext = SymbolContext.EMPTY
    ): Boolean {
        if (requiresSelection && !editorContext.hasSelection) {
            return false
        }
        if (requiresSymbol && !symbolContext.isValid) {
            return false
        }
        return true
    }

    /**
     * Gets the reason why this action cannot be executed.
     */
    fun getDisabledReason(
        editorContext: EditorContext = EditorContext.EMPTY,
        symbolContext: SymbolContext = SymbolContext.EMPTY
    ): String? {
        if (requiresSelection && !editorContext.hasSelection) {
            return "Select code to use this action"
        }
        if (requiresSymbol && !symbolContext.isValid) {
            return "Place cursor on a symbol to use this action"
        }
        return null
    }
}
