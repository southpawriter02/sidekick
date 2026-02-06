package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*
import java.time.Instant
import java.util.UUID

/**
 * # Agent Actions
 *
 * High-level actions that the agent can perform.
 * Part of Sidekick v0.8.5 Agent Actions feature.
 *
 * ## Overview
 *
 * Actions encapsulate common development tasks:
 * - Refactoring (extract method, rename, move)
 * - Test generation (unit tests, coverage)
 * - File operations (create, modify, delete)
 * - Code analysis (explain, review, optimize)
 *
 * @since 0.8.5
 */

// =============================================================================
// Action Base
// =============================================================================

/**
 * Base interface for agent actions.
 *
 * @property name Action identifier
 * @property category Action category
 * @property description Human-readable description
 */
interface AgentAction {
    val name: String
    val category: ActionCategory
    val description: String
    val requiresConfirmation: Boolean get() = false

    /**
     * Creates a task for this action.
     */
    fun createTask(input: ActionInput): AgentTask

    /**
     * Validates the input for this action.
     */
    fun validateInput(input: ActionInput): ActionValidation {
        return ActionValidation.valid()
    }
}

/**
 * Categories of agent actions.
 */
enum class ActionCategory(val displayName: String) {
    REFACTORING("Refactoring"),
    TEST_GENERATION("Test Generation"),
    FILE_OPERATIONS("File Operations"),
    CODE_ANALYSIS("Code Analysis"),
    DOCUMENTATION("Documentation"),
    DEBUGGING("Debugging"),
    OPTIMIZATION("Optimization"),
    OTHER("Other");

    override fun toString(): String = displayName
}

// =============================================================================
// Action Input/Output
// =============================================================================

/**
 * Input for an action.
 *
 * @property projectPath Project root path
 * @property activeFile Currently active file
 * @property selectedCode Selected code text
 * @property cursorLine Line number of cursor
 * @property targetSymbol Target symbol name
 * @property parameters Additional parameters
 * @property userInstructions Free-form instructions
 */
data class ActionInput(
    val projectPath: String,
    val activeFile: String? = null,
    val selectedCode: String? = null,
    val cursorLine: Int? = null,
    val targetSymbol: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val userInstructions: String = ""
) {
    /**
     * Gets a string parameter.
     */
    fun getString(key: String, default: String = ""): String =
        parameters[key]?.toString() ?: default

    /**
     * Gets an int parameter.
     */
    fun getInt(key: String, default: Int = 0): Int =
        (parameters[key] as? Number)?.toInt() ?: default

    /**
     * Gets a boolean parameter.
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        parameters[key] as? Boolean ?: default

    /**
     * Gets a list parameter.
     */
    @Suppress("UNCHECKED_CAST")
    fun getList(key: String): List<String> =
        (parameters[key] as? List<*>)?.map { it.toString() } ?: emptyList()

    /**
     * Whether there is selected code.
     */
    val hasSelection: Boolean get() = !selectedCode.isNullOrBlank()

    /**
     * Whether there is an active file.
     */
    val hasActiveFile: Boolean get() = !activeFile.isNullOrBlank()

    companion object {
        /**
         * Creates input for a file operation.
         */
        fun forFile(projectPath: String, filePath: String, instructions: String = ""): ActionInput =
            ActionInput(
                projectPath = projectPath,
                activeFile = filePath,
                userInstructions = instructions
            )

        /**
         * Creates input for code selection.
         */
        fun forSelection(
            projectPath: String,
            filePath: String,
            selectedCode: String,
            instructions: String = ""
        ): ActionInput = ActionInput(
            projectPath = projectPath,
            activeFile = filePath,
            selectedCode = selectedCode,
            userInstructions = instructions
        )

        /**
         * Creates input for a symbol.
         */
        fun forSymbol(projectPath: String, symbolName: String, instructions: String = ""): ActionInput =
            ActionInput(
                projectPath = projectPath,
                targetSymbol = symbolName,
                userInstructions = instructions
            )
    }
}

/**
 * Result of an action.
 *
 * @property actionName Name of the action
 * @property success Whether the action succeeded
 * @property taskResult Underlying task result
 * @property outputs Action-specific outputs
 * @property startTime When the action started
 * @property endTime When the action ended
 */
data class ActionResult(
    val actionName: String,
    val success: Boolean,
    val taskResult: TaskResult?,
    val outputs: Map<String, Any> = emptyMap(),
    val startTime: Instant = Instant.now(),
    val endTime: Instant = Instant.now()
) {
    /**
     * Duration in milliseconds.
     */
    val durationMs: Long get() = endTime.toEpochMilli() - startTime.toEpochMilli()

    /**
     * Gets a string output.
     */
    fun getString(key: String): String? = outputs[key]?.toString()

    /**
     * Gets a list output.
     */
    @Suppress("UNCHECKED_CAST")
    fun getList(key: String): List<String> =
        (outputs[key] as? List<*>)?.map { it.toString() } ?: emptyList()

    /**
     * Summary of the action.
     */
    val summary: String
        get() = taskResult?.summary ?: if (success) "Action completed" else "Action failed"

    companion object {
        /**
         * Creates a success result.
         */
        fun success(
            actionName: String,
            taskResult: TaskResult? = null,
            outputs: Map<String, Any> = emptyMap()
        ) = ActionResult(
            actionName = actionName,
            success = true,
            taskResult = taskResult,
            outputs = outputs
        )

        /**
         * Creates a failure result.
         */
        fun failure(
            actionName: String,
            error: String,
            taskResult: TaskResult? = null
        ) = ActionResult(
            actionName = actionName,
            success = false,
            taskResult = taskResult,
            outputs = mapOf("error" to error)
        )
    }
}

/**
 * Validation result for action input.
 */
data class ActionValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    /**
     * Whether there are errors.
     */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /**
     * Whether there are warnings.
     */
    val hasWarnings: Boolean get() = warnings.isNotEmpty()

    companion object {
        fun valid() = ActionValidation(valid = true)

        fun invalid(vararg errors: String) = ActionValidation(
            valid = false,
            errors = errors.toList()
        )

        fun withWarnings(vararg warnings: String) = ActionValidation(
            valid = true,
            warnings = warnings.toList()
        )
    }
}

// =============================================================================
// Action Registry
// =============================================================================

/**
 * Registry of available actions.
 */
object ActionRegistry {
    private val actions = mutableMapOf<String, AgentAction>()

    init {
        // Register built-in actions
        registerAll(RefactorActions.ALL)
        registerAll(TestGenerationActions.ALL)
        registerAll(FileOperationActions.ALL)
        registerAll(CodeAnalysisActions.ALL)
    }

    /**
     * Registers an action.
     */
    fun register(action: AgentAction) {
        actions[action.name] = action
    }

    /**
     * Registers multiple actions.
     */
    fun registerAll(actionList: List<AgentAction>) {
        actionList.forEach { register(it) }
    }

    /**
     * Gets an action by name.
     */
    fun get(name: String): AgentAction? = actions[name]

    /**
     * Gets all actions.
     */
    fun getAll(): List<AgentAction> = actions.values.toList()

    /**
     * Gets actions by category.
     */
    fun getByCategory(category: ActionCategory): List<AgentAction> =
        actions.values.filter { it.category == category }

    /**
     * Gets action names.
     */
    fun getNames(): Set<String> = actions.keys.toSet()
}

// =============================================================================
// Action Events
// =============================================================================

/**
 * Events during action execution.
 */
sealed class ActionEvent {
    abstract val actionName: String
    abstract val timestamp: Instant

    data class ActionStarted(
        override val actionName: String,
        val input: ActionInput,
        override val timestamp: Instant = Instant.now()
    ) : ActionEvent()

    data class ActionProgress(
        override val actionName: String,
        val progress: Float,
        val message: String,
        override val timestamp: Instant = Instant.now()
    ) : ActionEvent()

    data class ActionCompleted(
        override val actionName: String,
        val result: ActionResult,
        override val timestamp: Instant = Instant.now()
    ) : ActionEvent()

    data class ActionFailed(
        override val actionName: String,
        val error: String,
        override val timestamp: Instant = Instant.now()
    ) : ActionEvent()
}
