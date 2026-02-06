package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*

/**
 * # File Operation Actions
 *
 * Agent actions for file operations.
 * Part of Sidekick v0.8.5 Agent Actions feature.
 *
 * ## Available Actions
 *
 * - Create File
 * - Create from Template
 * - Split File
 * - Merge Files
 * - Delete Unused
 *
 * @since 0.8.5
 */
object FileOperationActions {

    // =========================================================================
    // Create File
    // =========================================================================

    /**
     * Creates a new file with generated content.
     */
    val CREATE_FILE = object : AgentAction {
        override val name = "create_file"
        override val category = ActionCategory.FILE_OPERATIONS
        override val description = "Create a new file with generated content"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            val fileName = input.getString("fileName")
            if (fileName.isBlank()) {
                return ActionValidation.invalid("File name is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val fileName = input.getString("fileName")
            val fileType = input.getString("fileType", "source")
            return AgentTask(
                type = TaskType.IMPLEMENT_FEATURE,
                description = "Create new file: $fileName",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Create a new $fileType file named '$fileName'.
                        - Follow project conventions
                        - Add appropriate imports
                        - Include file header/license if applicable
                        - Create in appropriate directory
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Create from Template
    // =========================================================================

    /**
     * Creates a file from a template.
     */
    val CREATE_FROM_TEMPLATE = object : AgentAction {
        override val name = "create_from_template"
        override val category = ActionCategory.FILE_OPERATIONS
        override val description = "Create a new file from a template"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            val template = input.getString("template")
            val name = input.getString("name")
            if (template.isBlank()) {
                return ActionValidation.invalid("Template name is required")
            }
            if (name.isBlank()) {
                return ActionValidation.invalid("Name is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val template = input.getString("template")
            val name = input.getString("name")
            return AgentTask(
                type = TaskType.IMPLEMENT_FEATURE,
                description = "Create $name from $template template",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Create a new file from the '$template' template.
                        - Use the name '$name'
                        - Replace template placeholders
                        - Follow project conventions
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Split File
    // =========================================================================

    /**
     * Splits a large file into smaller files.
     */
    val SPLIT_FILE = object : AgentAction {
        override val name = "split_file"
        override val category = ActionCategory.FILE_OPERATIONS
        override val description = "Split a large file into smaller files"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            if (!input.hasActiveFile) {
                return ActionValidation.invalid("File to split is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val strategy = input.getString("strategy", "by-class")
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Split file: ${input.activeFile}",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Split this file into smaller, focused files using strategy: $strategy.
                        - One class/interface per file (if applicable)
                        - Create appropriate directory structure
                        - Update all imports
                        - Maintain logical grouping
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Merge Files
    // =========================================================================

    /**
     * Merges multiple files into one.
     */
    val MERGE_FILES = object : AgentAction {
        override val name = "merge_files"
        override val category = ActionCategory.FILE_OPERATIONS
        override val description = "Merge multiple files into one"
        override val requiresConfirmation = true

        override fun validateInput(input: ActionInput): ActionValidation {
            val files = input.getList("files")
            if (files.size < 2) {
                return ActionValidation.invalid("At least two files are required for merging")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val files = input.getList("files")
            val targetFile = input.getString("targetFile", files.first())
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Merge ${files.size} files into $targetFile",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = targetFile,
                    selectedCode = null,
                    cursorPosition = null,
                    relatedFiles = files,
                    userInstructions = """
                        Merge these files: ${files.joinToString()}
                        Into: $targetFile
                        - Combine imports, removing duplicates
                        - Organize content logically
                        - Delete source files after merge
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowDeletion = true
                )
            )
        }
    }

    // =========================================================================
    // Delete Unused
    // =========================================================================

    /**
     * Deletes unused files.
     */
    val DELETE_UNUSED = object : AgentAction {
        override val name = "delete_unused"
        override val category = ActionCategory.FILE_OPERATIONS
        override val description = "Delete unused or dead code files"
        override val requiresConfirmation = true

        override fun createTask(input: ActionInput): AgentTask {
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Delete unused files",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Identify and delete unused files.
                        - Find files with no references
                        - Check for dead code
                        - Verify before deletion
                        - Create backup suggestion
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowDeletion = true,
                    requireConfirmation = true
                )
            )
        }
    }

    // =========================================================================
    // Organize Imports
    // =========================================================================

    /**
     * Organizes imports in a file.
     */
    val ORGANIZE_IMPORTS = object : AgentAction {
        override val name = "organize_imports"
        override val category = ActionCategory.FILE_OPERATIONS
        override val description = "Organize and optimize imports"

        override fun validateInput(input: ActionInput): ActionValidation {
            if (!input.hasActiveFile) {
                return ActionValidation.invalid("File is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            return AgentTask(
                type = TaskType.REFACTOR,
                description = "Organize imports in ${input.activeFile}",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = null,
                    cursorPosition = null,
                    userInstructions = """
                        Organize imports in this file.
                        - Remove unused imports
                        - Sort imports alphabetically
                        - Group by package
                        - Follow project conventions
                    """.trimIndent()
                )
            )
        }
    }

    /**
     * All file operation actions.
     */
    val ALL = listOf(
        CREATE_FILE,
        CREATE_FROM_TEMPLATE,
        SPLIT_FILE,
        MERGE_FILES,
        DELETE_UNUSED,
        ORGANIZE_IMPORTS
    )
}

// =============================================================================
// Code Analysis Actions
// =============================================================================

/**
 * Actions for code analysis.
 */
object CodeAnalysisActions {

    /**
     * Explains code functionality.
     */
    val EXPLAIN = object : AgentAction {
        override val name = "explain"
        override val category = ActionCategory.CODE_ANALYSIS
        override val description = "Explain what the code does"

        override fun createTask(input: ActionInput): AgentTask {
            return AgentTask(
                type = TaskType.EXPLAIN_CODE,
                description = "Explain the selected code",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Explain what this code does.
                        - Describe the overall purpose
                        - Explain key algorithms/patterns
                        - Note any potential issues
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.READ_ONLY
            )
        }
    }

    /**
     * Reviews code for issues.
     */
    val REVIEW = object : AgentAction {
        override val name = "review"
        override val category = ActionCategory.CODE_ANALYSIS
        override val description = "Review code for issues and improvements"

        override fun createTask(input: ActionInput): AgentTask {
            return AgentTask(
                type = TaskType.REVIEW,
                description = "Review the code",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Review this code for:
                        - Bugs and potential issues
                        - Performance problems
                        - Security vulnerabilities
                        - Code style and best practices
                        - Possible improvements
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.READ_ONLY
            )
        }
    }

    /**
     * Optimizes code for performance.
     */
    val OPTIMIZE = object : AgentAction {
        override val name = "optimize"
        override val category = ActionCategory.OPTIMIZATION
        override val description = "Optimize code for better performance"
        override val requiresConfirmation = true

        override fun createTask(input: ActionInput): AgentTask {
            val focus = input.getString("focus", "general")
            return AgentTask(
                type = TaskType.OPTIMIZE,
                description = "Optimize code for $focus performance",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Optimize this code for better $focus performance.
                        - Identify bottlenecks
                        - Reduce complexity
                        - Improve memory usage
                        - Add caching if beneficial
                        ${input.userInstructions}
                    """.trimIndent()
                )
            )
        }
    }

    /**
     * Documents code.
     */
    val DOCUMENT = object : AgentAction {
        override val name = "document"
        override val category = ActionCategory.DOCUMENTATION
        override val description = "Add documentation to code"
        override val requiresConfirmation = true

        override fun createTask(input: ActionInput): AgentTask {
            val style = input.getString("style", "KDoc")
            return AgentTask(
                type = TaskType.DOCUMENT,
                description = "Add $style documentation",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Add $style documentation to this code.
                        - Document public APIs
                        - Add parameter descriptions
                        - Include examples where helpful
                        - Note any preconditions/postconditions
                        ${input.userInstructions}
                    """.trimIndent()
                )
            )
        }
    }

    /**
     * All code analysis actions.
     */
    val ALL = listOf(
        EXPLAIN,
        REVIEW,
        OPTIMIZE,
        DOCUMENT
    )
}
