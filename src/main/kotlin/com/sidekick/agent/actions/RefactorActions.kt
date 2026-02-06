package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*

/**
 * # Refactoring Actions
 *
 * Agent actions for code refactoring.
 * Part of Sidekick v0.8.5 Agent Actions feature.
 *
 * ## Available Actions
 *
 * - Extract Method
 * - Extract Variable
 * - Rename Symbol
 * - Move Symbol
 * - Inline
 * - Change Signature
 *
 * @since 0.8.5
 */
object RefactorActions {

    // =========================================================================
    // Extract Method
    // =========================================================================

    /**
     * Extracts selected code into a new method.
     */
    val EXTRACT_METHOD = object : AgentAction {
        override val name = "extract_method"
        override val category = ActionCategory.REFACTORING
        override val description = "Extract selected code into a new method"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            if (!input.hasSelection) {
                return ActionValidation.invalid("No code selected for extraction")
            }
            val methodName = input.getString("methodName")
            if (methodName.isBlank()) {
                return ActionValidation.invalid("Method name is required")
            }
            if (!methodName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
                return ActionValidation.invalid("Invalid method name: $methodName")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val methodName = input.getString("methodName")
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Extract the selected code into a method named '$methodName'",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Extract this code into a new method named '$methodName'.
                        - Identify parameters needed from the outer scope
                        - Determine the appropriate return type
                        - Replace the original code with a method call
                        - Add documentation to the new method
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    requireConfirmation = true
                )
            )
        }
    }

    // =========================================================================
    // Extract Variable
    // =========================================================================

    /**
     * Extracts an expression into a variable.
     */
    val EXTRACT_VARIABLE = object : AgentAction {
        override val name = "extract_variable"
        override val category = ActionCategory.REFACTORING
        override val description = "Extract selected expression into a variable"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            if (!input.hasSelection) {
                return ActionValidation.invalid("No expression selected for extraction")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val varName = input.getString("variableName", "extracted")
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Extract the selected expression into a variable",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Extract this expression into a variable named '$varName'.
                        - Infer the appropriate type
                        - Place the declaration at the appropriate scope
                        - Replace all occurrences of the expression if applicable
                    """.trimIndent()
                )
            )
        }
    }

    // =========================================================================
    // Rename Symbol
    // =========================================================================

    /**
     * Renames a symbol across the codebase.
     */
    val RENAME_SYMBOL = object : AgentAction {
        override val name = "rename_symbol"
        override val category = ActionCategory.REFACTORING
        override val description = "Rename a symbol across the codebase"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            val oldName = input.getString("oldName")
            val newName = input.getString("newName")
            if (oldName.isBlank()) {
                return ActionValidation.invalid("Original name is required")
            }
            if (newName.isBlank()) {
                return ActionValidation.invalid("New name is required")
            }
            if (oldName == newName) {
                return ActionValidation.invalid("New name must be different from old name")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val oldName = input.getString("oldName")
            val newName = input.getString("newName")
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Rename '$oldName' to '$newName' across the codebase",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Rename the symbol '$oldName' to '$newName'.
                        - Find all usages of the symbol
                        - Update file names if renaming a class
                        - Update imports and references
                        - Preserve formatting
                    """.trimIndent()
                )
            )
        }
    }

    // =========================================================================
    // Move Symbol
    // =========================================================================

    /**
     * Moves a symbol to a different file or package.
     */
    val MOVE_SYMBOL = object : AgentAction {
        override val name = "move_symbol"
        override val category = ActionCategory.REFACTORING
        override val description = "Move a symbol to a different file or package"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            val symbolName = input.targetSymbol ?: input.getString("symbolName")
            val destination = input.getString("destination")
            if (symbolName.isBlank()) {
                return ActionValidation.invalid("Symbol name is required")
            }
            if (destination.isBlank()) {
                return ActionValidation.invalid("Destination file/package is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val symbolName = input.targetSymbol ?: input.getString("symbolName")
            val destination = input.getString("destination")
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Move '$symbolName' to '$destination'",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Move the symbol '$symbolName' to '$destination'.
                        - Update package/module declarations
                        - Update all imports referencing this symbol
                        - Create new file if needed
                        - Remove from original location
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Inline
    // =========================================================================

    /**
     * Inlines a variable or method.
     */
    val INLINE = object : AgentAction {
        override val name = "inline"
        override val category = ActionCategory.REFACTORING
        override val description = "Inline a variable or method at its usage sites"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            if (input.targetSymbol.isNullOrBlank() && !input.hasSelection) {
                return ActionValidation.invalid("Symbol name or selection is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val symbolName = input.targetSymbol ?: "selected symbol"
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Inline $symbolName at its usage sites",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Inline $symbolName at all usage sites.
                        - Replace all references with the actual value/code
                        - Remove the original declaration
                        - Preserve behavior exactly
                    """.trimIndent()
                )
            )
        }
    }

    // =========================================================================
    // Change Signature
    // =========================================================================

    /**
     * Changes a method signature.
     */
    val CHANGE_SIGNATURE = object : AgentAction {
        override val name = "change_signature"
        override val category = ActionCategory.REFACTORING
        override val description = "Change a method's signature (parameters, return type)"
        override val requiresConfirmation = true

        override fun createTask(input: ActionInput): AgentTask {
            val methodName = input.targetSymbol ?: input.getString("methodName")
            val newSignature = input.getString("newSignature")
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Change signature of method '$methodName'",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Change the signature of '$methodName'${if (newSignature.isNotBlank()) " to: $newSignature" else ""}.
                        - Update the method declaration
                        - Update all call sites
                        - Add default values or handle missing arguments
                        ${input.userInstructions}
                    """.trimIndent()
                )
            )
        }
    }

    // =========================================================================
    // Simplify
    // =========================================================================

    /**
     * Simplifies complex code.
     */
    val SIMPLIFY = object : AgentAction {
        override val name = "simplify"
        override val category = ActionCategory.REFACTORING
        override val description = "Simplify complex or verbose code"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            if (!input.hasSelection && !input.hasActiveFile) {
                return ActionValidation.invalid("Selection or file is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Simplify the selected code",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Simplify this code while preserving behavior.
                        - Remove unnecessary complexity
                        - Use more idiomatic patterns
                        - Improve readability
                        - Reduce nesting where possible
                    """.trimIndent()
                )
            )
        }
    }

    /**
     * All refactoring actions.
     */
    val ALL = listOf(
        EXTRACT_METHOD,
        EXTRACT_VARIABLE,
        RENAME_SYMBOL,
        MOVE_SYMBOL,
        INLINE,
        CHANGE_SIGNATURE,
        SIMPLIFY
    )
}
