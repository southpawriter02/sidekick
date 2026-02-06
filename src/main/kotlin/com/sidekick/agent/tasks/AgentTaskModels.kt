package com.sidekick.agent.tasks

import java.time.Instant
import java.util.UUID

/**
 * # Agent Task Models
 *
 * Data models for the agent task system.
 * Part of Sidekick v0.8.3 Agent Task System feature.
 *
 * ## Overview
 *
 * The task system allows the agent to:
 * - Plan multi-step tasks
 * - Execute code modifications
 * - Track progress and results
 * - Handle user confirmations
 *
 * @since 0.8.3
 */

// =============================================================================
// Agent Task
// =============================================================================

/**
 * A task the agent can execute.
 *
 * @property id Unique task identifier
 * @property type Type of task (explain, refactor, etc.)
 * @property description User-facing task description
 * @property context Task context (files, code, etc.)
 * @property constraints Execution constraints
 * @property status Current task status
 * @property steps Executed steps
 * @property result Final result (when complete)
 * @property createdAt When the task was created
 */
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val description: String,
    val context: TaskContext,
    val constraints: TaskConstraints = TaskConstraints(),
    val status: TaskStatus = TaskStatus.PENDING,
    val steps: List<TaskStep> = emptyList(),
    val result: TaskResult? = null,
    val createdAt: Instant = Instant.now()
) {
    /**
     * Total token usage across all steps.
     */
    val totalTokens: Int
        get() = steps.sumOf { it.tokensUsed }

    /**
     * Whether the task is still running.
     */
    val isActive: Boolean
        get() = !status.isTerminal

    /**
     * Whether the task completed successfully.
     */
    val isSuccessful: Boolean
        get() = status == TaskStatus.COMPLETED && result?.success == true

    /**
     * Adds a step to the task.
     */
    fun withStep(step: TaskStep): AgentTask = copy(steps = steps + step)

    /**
     * Updates task status.
     */
    fun withStatus(newStatus: TaskStatus): AgentTask = copy(status = newStatus)

    /**
     * Sets the result and marks complete/failed.
     */
    fun complete(taskResult: TaskResult): AgentTask = copy(
        status = if (taskResult.success) TaskStatus.COMPLETED else TaskStatus.FAILED,
        result = taskResult
    )

    companion object {
        /**
         * Creates a simple task.
         */
        fun simple(
            type: TaskType,
            description: String,
            userInstructions: String,
            projectPath: String,
            activeFile: String? = null,
            selectedCode: String? = null
        ): AgentTask = AgentTask(
            type = type,
            description = description,
            context = TaskContext(
                projectPath = projectPath,
                activeFile = activeFile,
                selectedCode = selectedCode,
                cursorPosition = null,
                userInstructions = userInstructions
            )
        )
    }
}

// =============================================================================
// Task Type
// =============================================================================

/**
 * Types of tasks the agent can execute.
 */
enum class TaskType(
    val displayName: String,
    val description: String,
    val defaultPrompt: String
) {
    EXPLAIN_CODE(
        "Explain Code",
        "Explain what code does",
        "Please explain this code in detail, including its purpose and how it works."
    ),
    REFACTOR(
        "Refactor",
        "Improve code structure",
        "Please refactor this code to improve its structure, readability, and maintainability."
    ),
    GENERATE_TESTS(
        "Generate Tests",
        "Create unit tests",
        "Please generate comprehensive unit tests for this code."
    ),
    FIX_BUG(
        "Fix Bug",
        "Diagnose and fix a bug",
        "Please analyze this code, identify the bug, and provide a fix."
    ),
    IMPLEMENT_FEATURE(
        "Implement Feature",
        "Add new functionality",
        "Please implement this feature according to the provided requirements."
    ),
    DOCUMENT(
        "Document",
        "Add documentation",
        "Please add comprehensive documentation to this code."
    ),
    OPTIMIZE(
        "Optimize",
        "Improve performance",
        "Please optimize this code for better performance."
    ),
    REVIEW(
        "Code Review",
        "Review code for issues",
        "Please review this code and identify potential issues or improvements."
    ),
    ANSWER_QUESTION(
        "Answer Question",
        "Answer a coding question",
        "Please answer the following question about my codebase."
    ),
    CUSTOM(
        "Custom Task",
        "Custom agent task",
        "Please complete the following task."
    );

    override fun toString(): String = displayName

    /**
     * Whether this task type typically modifies files.
     */
    val isDestructive: Boolean
        get() = this in listOf(REFACTOR, FIX_BUG, IMPLEMENT_FEATURE, DOCUMENT, OPTIMIZE)

    /**
     * Whether this task type is read-only.
     */
    val isReadOnly: Boolean
        get() = this in listOf(EXPLAIN_CODE, REVIEW, ANSWER_QUESTION)

    companion object {
        /**
         * Task types that modify code.
         */
        val MODIFYING_TYPES = entries.filter { it.isDestructive }

        /**
         * Task types that only read code.
         */
        val READONLY_TYPES = entries.filter { it.isReadOnly }

        fun byName(name: String): TaskType? = entries.find { 
            it.name.equals(name, ignoreCase = true) 
        }
    }
}

// =============================================================================
// Task Context
// =============================================================================

/**
 * Context for task execution.
 *
 * @property projectPath Root project path
 * @property activeFile Currently active file
 * @property selectedCode Selected code text
 * @property cursorPosition Cursor position in file
 * @property relatedFiles Related files for context
 * @property errorContext Error message/stacktrace
 * @property userInstructions User's instructions
 */
data class TaskContext(
    val projectPath: String,
    val activeFile: String?,
    val selectedCode: String?,
    val cursorPosition: Int?,
    val relatedFiles: List<String> = emptyList(),
    val errorContext: String? = null,
    val userInstructions: String
) {
    /**
     * Whether there is selected code.
     */
    val hasSelection: Boolean get() = !selectedCode.isNullOrBlank()

    /**
     * Whether there is an active file.
     */
    val hasActiveFile: Boolean get() = !activeFile.isNullOrBlank()

    /**
     * Whether there is error context.
     */
    val hasError: Boolean get() = !errorContext.isNullOrBlank()

    /**
     * All files involved (active + related).
     */
    val allFiles: List<String>
        get() = buildList {
            activeFile?.let { add(it) }
            addAll(relatedFiles)
        }

    companion object {
        /**
         * Creates context with just user instructions.
         */
        fun simple(projectPath: String, instructions: String) = TaskContext(
            projectPath = projectPath,
            activeFile = null,
            selectedCode = null,
            cursorPosition = null,
            userInstructions = instructions
        )
    }
}

// =============================================================================
// Task Constraints
// =============================================================================

/**
 * Constraints for task execution.
 *
 * @property maxSteps Maximum number of steps
 * @property maxTokens Maximum token budget
 * @property allowFileModification Allow modifying files
 * @property allowNewFiles Allow creating new files
 * @property allowDeletion Allow deleting files
 * @property allowCommands Allow running shell commands
 * @property requireConfirmation Require user confirmation
 * @property timeoutSeconds Task timeout
 */
data class TaskConstraints(
    val maxSteps: Int = 10,
    val maxTokens: Int = 8000,
    val allowFileModification: Boolean = true,
    val allowNewFiles: Boolean = true,
    val allowDeletion: Boolean = false,
    val allowCommands: Boolean = false,
    val requireConfirmation: Boolean = true,
    val timeoutSeconds: Int = 300
) {
    /**
     * Whether any file operations are allowed.
     */
    val allowsFileOperations: Boolean
        get() = allowFileModification || allowNewFiles || allowDeletion

    /**
     * Strict mode (read-only).
     */
    val isReadOnly: Boolean
        get() = !allowFileModification && !allowNewFiles && !allowDeletion && !allowCommands

    companion object {
        /**
         * Default constraints for most tasks.
         */
        val DEFAULT = TaskConstraints()

        /**
         * Strict read-only constraints.
         */
        val READ_ONLY = TaskConstraints(
            allowFileModification = false,
            allowNewFiles = false,
            allowDeletion = false,
            allowCommands = false,
            requireConfirmation = false
        )

        /**
         * Permissive constraints for trusted tasks.
         */
        val PERMISSIVE = TaskConstraints(
            maxSteps = 50,
            maxTokens = 32000,
            allowDeletion = true,
            allowCommands = true,
            requireConfirmation = false
        )
    }
}

// =============================================================================
// Task Status
// =============================================================================

/**
 * Task execution status.
 */
enum class TaskStatus(val displayName: String, val isTerminal: Boolean) {
    PENDING("Pending", false),
    PLANNING("Planning", false),
    EXECUTING("Executing", false),
    AWAITING_CONFIRMATION("Waiting for Confirmation", false),
    COMPLETED("Completed", true),
    FAILED("Failed", true),
    CANCELLED("Cancelled", true);

    override fun toString(): String = displayName

    /**
     * Whether the task is still running.
     */
    val isActive: Boolean get() = !isTerminal

    companion object {
        val ACTIVE_STATUSES = entries.filter { it.isActive }
        val TERMINAL_STATUSES = entries.filter { it.isTerminal }
    }
}

// =============================================================================
// Task Step
// =============================================================================

/**
 * A single step in task execution.
 *
 * @property id Step number
 * @property action Type of action taken
 * @property reasoning Agent's reasoning
 * @property status Step execution status
 * @property result Output or error message
 * @property toolName Tool used (if any)
 * @property toolArgs Tool arguments (if any)
 * @property tokensUsed Tokens used for this step
 * @property durationMs Execution time in ms
 */
data class TaskStep(
    val id: Int,
    val action: AgentAction,
    val reasoning: String,
    val status: StepStatus,
    val result: String?,
    val toolName: String? = null,
    val toolArgs: Map<String, Any>? = null,
    val tokensUsed: Int = 0,
    val durationMs: Long = 0
) {
    /**
     * Whether this step succeeded.
     */
    val isSuccessful: Boolean get() = status == StepStatus.COMPLETED

    /**
     * Whether this step failed.
     */
    val isFailed: Boolean get() = status == StepStatus.FAILED

    /**
     * Whether this was a tool call.
     */
    val isToolCall: Boolean get() = action == AgentAction.TOOL_CALL && toolName != null

    companion object {
        /**
         * Creates a tool call step.
         */
        fun toolCall(
            id: Int,
            toolName: String,
            toolArgs: Map<String, Any>,
            reasoning: String,
            result: String?,
            success: Boolean,
            tokensUsed: Int = 0,
            durationMs: Long = 0
        ) = TaskStep(
            id = id,
            action = AgentAction.TOOL_CALL,
            reasoning = reasoning,
            status = if (success) StepStatus.COMPLETED else StepStatus.FAILED,
            result = result,
            toolName = toolName,
            toolArgs = toolArgs,
            tokensUsed = tokensUsed,
            durationMs = durationMs
        )

        /**
         * Creates a reasoning step.
         */
        fun reasoning(id: Int, content: String) = TaskStep(
            id = id,
            action = AgentAction.REASONING,
            reasoning = content,
            status = StepStatus.COMPLETED,
            result = null
        )

        /**
         * Creates an error step.
         */
        fun error(id: Int, message: String) = TaskStep(
            id = id,
            action = AgentAction.ERROR,
            reasoning = message,
            status = StepStatus.FAILED,
            result = message
        )
    }
}

/**
 * Types of agent actions.
 */
enum class AgentAction(val displayName: String) {
    TOOL_CALL("Tool Call"),
    REASONING("Reasoning"),
    ERROR("Error"),
    COMPLETE("Complete");

    override fun toString(): String = displayName
}

/**
 * Step execution status.
 */
enum class StepStatus(val displayName: String) {
    PENDING("Pending"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    SKIPPED("Skipped");

    override fun toString(): String = displayName
}

// =============================================================================
// Task Result
// =============================================================================

/**
 * Result of task execution.
 *
 * @property success Whether the task succeeded
 * @property summary Human-readable summary
 * @property filesModified Files that were modified
 * @property filesCreated Files that were created
 * @property filesDeleted Files that were deleted
 * @property errors Error messages
 * @property tokensUsed Total tokens used
 * @property durationMs Execution duration
 */
data class TaskResult(
    val success: Boolean,
    val summary: String,
    val filesModified: List<String> = emptyList(),
    val filesCreated: List<String> = emptyList(),
    val filesDeleted: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val tokensUsed: Int = 0,
    val durationMs: Long = 0
) {
    /**
     * Total files affected.
     */
    val totalFilesAffected: Int
        get() = filesModified.size + filesCreated.size + filesDeleted.size

    /**
     * Whether any files were created.
     */
    val hasCreatedFiles: Boolean get() = filesCreated.isNotEmpty()

    /**
     * Whether any files were modified.
     */
    val hasModifiedFiles: Boolean get() = filesModified.isNotEmpty()

    /**
     * Whether there were errors.
     */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    companion object {
        /**
         * Creates a success result.
         */
        fun success(summary: String, filesModified: List<String> = emptyList()) = TaskResult(
            success = true,
            summary = summary,
            filesModified = filesModified
        )

        /**
         * Creates a failure result.
         */
        fun failure(summary: String, errors: List<String> = emptyList()) = TaskResult(
            success = false,
            summary = summary,
            errors = errors
        )

        /**
         * Creates a cancelled result.
         */
        fun cancelled(reason: String = "Cancelled by user") = TaskResult(
            success = false,
            summary = reason
        )
    }
}

// =============================================================================
// Task Events
// =============================================================================

/**
 * Events emitted during task execution.
 */
sealed class TaskEvent {
    abstract val taskId: String
    abstract val timestamp: Instant

    data class TaskStarted(
        override val taskId: String,
        val task: AgentTask,
        override val timestamp: Instant = Instant.now()
    ) : TaskEvent()

    data class StepStarted(
        override val taskId: String,
        val stepId: Int,
        val action: AgentAction,
        override val timestamp: Instant = Instant.now()
    ) : TaskEvent()

    data class StepCompleted(
        override val taskId: String,
        val step: TaskStep,
        override val timestamp: Instant = Instant.now()
    ) : TaskEvent()

    data class ConfirmationRequired(
        override val taskId: String,
        val action: String,
        val details: String,
        override val timestamp: Instant = Instant.now()
    ) : TaskEvent()

    data class TaskCompleted(
        override val taskId: String,
        val result: TaskResult,
        override val timestamp: Instant = Instant.now()
    ) : TaskEvent()

    data class TaskFailed(
        override val taskId: String,
        val error: String,
        override val timestamp: Instant = Instant.now()
    ) : TaskEvent()
}
