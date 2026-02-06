package com.sidekick.agent.workflow

import java.time.Instant
import java.util.UUID

/**
 * # Workflow Models
 *
 * Data models for multi-step agent workflows and autonomous mode.
 * Part of Sidekick v0.8.7 Agent Orchestration feature.
 *
 * ## Overview
 *
 * The workflow system enables:
 * - Multi-step workflows with branching
 * - Trigger-based automation
 * - Built-in workflows for common tasks
 * - Autonomous execution with user checkpoints
 *
 * @since 0.8.7
 */

// =============================================================================
// Agent Workflow
// =============================================================================

/**
 * Multi-step workflow definition.
 *
 * @property id Unique workflow identifier
 * @property name Human-readable workflow name
 * @property description What the workflow does
 * @property steps Ordered list of workflow steps
 * @property triggers Events that can start this workflow
 * @property config Workflow-level configuration
 * @property version Workflow version for updates
 */
data class AgentWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val triggers: List<WorkflowTrigger> = emptyList(),
    val config: WorkflowConfig = WorkflowConfig(),
    val version: Int = 1
) {
    /**
     * Gets the first step of the workflow.
     */
    val firstStep: WorkflowStep? get() = steps.firstOrNull()

    /**
     * Gets a step by ID.
     */
    fun getStep(stepId: String): WorkflowStep? = steps.find { it.id == stepId }

    /**
     * Gets the next step after a given step.
     */
    fun getNextStep(currentStepId: String, success: Boolean): WorkflowStep? {
        val current = getStep(currentStepId) ?: return null
        val nextId = if (success) current.onSuccess else current.onFailure
        return nextId?.let { getStep(it) }
    }

    /**
     * Validates the workflow structure.
     */
    fun validate(): WorkflowValidation {
        val errors = mutableListOf<String>()

        if (id.isBlank()) errors.add("Workflow ID is required")
        if (name.isBlank()) errors.add("Workflow name is required")
        if (steps.isEmpty()) errors.add("Workflow must have at least one step")

        // Check step references
        val stepIds = steps.map { it.id }.toSet()
        steps.forEach { step ->
            step.onSuccess?.let { ref ->
                if (ref !in stepIds) errors.add("Step '${step.id}' references unknown step '$ref'")
            }
            step.onFailure?.let { ref ->
                if (ref !in stepIds) errors.add("Step '${step.id}' failure references unknown step '$ref'")
            }
        }

        return if (errors.isEmpty()) WorkflowValidation.valid()
        else WorkflowValidation.invalid(errors)
    }

    /**
     * Whether this workflow has any triggers.
     */
    val hasAutoTrigger: Boolean get() = triggers.any { it.type != TriggerType.MANUAL }

    companion object {
        /**
         * Creates a simple linear workflow.
         */
        fun linear(
            id: String,
            name: String,
            description: String,
            actions: List<WorkflowAction>
        ): AgentWorkflow {
            val steps = actions.mapIndexed { index, action ->
                val nextId = if (index < actions.lastIndex) "${index + 2}" else null
                WorkflowStep(
                    id = "${index + 1}",
                    action = action,
                    config = emptyMap(),
                    onSuccess = nextId,
                    onFailure = null
                )
            }
            return AgentWorkflow(id, name, description, steps)
        }
    }
}

// =============================================================================
// Workflow Step
// =============================================================================

/**
 * A single step in a workflow.
 *
 * @property id Unique step identifier within the workflow
 * @property action The action to perform
 * @property config Action-specific configuration
 * @property onSuccess ID of next step on success (null = end)
 * @property onFailure ID of next step on failure (null = abort)
 * @property timeout Optional timeout for this step
 * @property retries Number of retries on failure
 * @property condition Optional condition to skip step
 */
data class WorkflowStep(
    val id: String,
    val action: WorkflowAction,
    val config: Map<String, Any> = emptyMap(),
    val onSuccess: String? = null,
    val onFailure: String? = null,
    val timeout: Long = 60000,
    val retries: Int = 0,
    val condition: StepCondition? = null
) {
    /**
     * Whether this is a terminal step (no next step on success).
     */
    val isTerminal: Boolean get() = onSuccess == null

    /**
     * Whether this step requires user interaction.
     */
    val requiresUserInteraction: Boolean get() = action == WorkflowAction.ASK_USER

    /**
     * Gets a string config value.
     */
    fun getString(key: String, default: String = ""): String {
        return config[key]?.toString() ?: default
    }

    /**
     * Gets a boolean config value.
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return config[key] as? Boolean ?: default
    }

    /**
     * Gets a list config value.
     */
    @Suppress("UNCHECKED_CAST")
    fun getList(key: String): List<String> {
        return (config[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }
}

// =============================================================================
// Step Condition
// =============================================================================

/**
 * Condition for conditional step execution.
 */
data class StepCondition(
    val type: ConditionType,
    val value: String
) {
    /**
     * Evaluates the condition.
     */
    fun evaluate(context: WorkflowContext): Boolean {
        return when (type) {
            ConditionType.VARIABLE_SET -> context.variables.containsKey(value)
            ConditionType.VARIABLE_EQUALS -> {
                val (name, expected) = value.split("=", limit = 2)
                context.variables[name] == expected
            }
            ConditionType.PREVIOUS_SUCCESS -> context.lastStepSuccess
            ConditionType.PREVIOUS_FAILURE -> !context.lastStepSuccess
            ConditionType.ALWAYS -> true
            ConditionType.NEVER -> false
        }
    }
}

/**
 * Types of step conditions.
 */
enum class ConditionType {
    /** Variable is set in context */
    VARIABLE_SET,
    /** Variable equals specific value */
    VARIABLE_EQUALS,
    /** Previous step succeeded */
    PREVIOUS_SUCCESS,
    /** Previous step failed */
    PREVIOUS_FAILURE,
    /** Always execute */
    ALWAYS,
    /** Never execute (skip) */
    NEVER
}

// =============================================================================
// Workflow Action
// =============================================================================

/**
 * Actions that can be performed in a workflow step.
 */
enum class WorkflowAction(
    val displayName: String,
    val requiresUserInteraction: Boolean = false,
    val modifiesCode: Boolean = false
) {
    /** Wait for user input or confirmation */
    ASK_USER("Ask User", requiresUserInteraction = true),

    /** Analyze code for issues or context */
    ANALYZE_CODE("Analyze Code"),

    /** Generate new code */
    GENERATE_CODE("Generate Code"),

    /** Run tests */
    RUN_TESTS("Run Tests"),

    /** Apply generated changes to files */
    APPLY_CHANGES("Apply Changes", modifiesCode = true),

    /** Search the codebase */
    SEARCH_CODEBASE("Search Codebase"),

    /** Create a new file */
    CREATE_FILE("Create File", modifiesCode = true),

    /** Modify an existing file */
    MODIFY_FILE("Modify File", modifiesCode = true),

    /** Commit changes to git */
    COMMIT_CHANGES("Commit Changes", modifiesCode = true),

    /** Run a shell command */
    RUN_COMMAND("Run Command"),

    /** Wait for a condition */
    WAIT("Wait"),

    /** Branch based on condition */
    BRANCH("Branch"),

    /** Set a variable */
    SET_VARIABLE("Set Variable"),

    /** Log a message */
    LOG("Log"),

    /** Send notification */
    NOTIFY("Notify");

    companion object {
        /** Actions that modify code */
        val MODIFYING_ACTIONS = entries.filter { it.modifiesCode }.toSet()

        /** Actions requiring user interaction */
        val INTERACTIVE_ACTIONS = entries.filter { it.requiresUserInteraction }.toSet()

        /** Read-only actions */
        val READONLY_ACTIONS = entries.filter { !it.modifiesCode && !it.requiresUserInteraction }.toSet()
    }
}

// =============================================================================
// Workflow Trigger
// =============================================================================

/**
 * Trigger that can start a workflow.
 *
 * @property type Type of trigger
 * @property pattern Optional pattern for matching
 * @property config Trigger-specific configuration
 */
data class WorkflowTrigger(
    val type: TriggerType,
    val pattern: String? = null,
    val config: Map<String, String> = emptyMap()
) {
    /**
     * Checks if the trigger matches an event.
     */
    fun matches(event: TriggerEvent): Boolean {
        if (type != event.type) return false
        if (pattern == null) return true

        return when (type) {
            TriggerType.FILE_SAVE -> event.data?.matches(Regex(pattern)) == true
            TriggerType.ERROR_DETECTED -> event.data?.contains(pattern, ignoreCase = true) == true
            TriggerType.COMMAND -> event.data == pattern
            TriggerType.SCHEDULE -> true // Handled by scheduler
            TriggerType.MANUAL -> true
            TriggerType.WEBHOOK -> event.data == pattern
            TriggerType.GIT_HOOK -> event.data?.matches(Regex(pattern)) == true
        }
    }
}

/**
 * Types of workflow triggers.
 */
enum class TriggerType(val displayName: String) {
    /** Manual invocation by user */
    MANUAL("Manual"),

    /** Triggered on file save */
    FILE_SAVE("File Save"),

    /** Triggered when error detected */
    ERROR_DETECTED("Error Detected"),

    /** Triggered by command/shortcut */
    COMMAND("Command"),

    /** Triggered on schedule */
    SCHEDULE("Schedule"),

    /** Triggered by webhook */
    WEBHOOK("Webhook"),

    /** Triggered by git hook */
    GIT_HOOK("Git Hook")
}

/**
 * Event that may trigger a workflow.
 */
data class TriggerEvent(
    val type: TriggerType,
    val data: String? = null,
    val timestamp: Instant = Instant.now()
)

// =============================================================================
// Workflow Configuration
// =============================================================================

/**
 * Workflow-level configuration.
 *
 * @property maxSteps Maximum steps to execute
 * @property timeout Total workflow timeout in ms
 * @property requireConfirmation Require user confirmation before modifying actions
 * @property continueOnError Continue workflow on step failure
 * @property parallel Allow parallel step execution
 * @property autonomous Run without user checkpoints
 */
data class WorkflowConfig(
    val maxSteps: Int = 50,
    val timeout: Long = 300000,
    val requireConfirmation: Boolean = true,
    val continueOnError: Boolean = false,
    val parallel: Boolean = false,
    val autonomous: Boolean = false
) {
    companion object {
        val DEFAULT = WorkflowConfig()

        val AUTONOMOUS = WorkflowConfig(
            requireConfirmation = false,
            autonomous = true
        )

        val SAFE = WorkflowConfig(
            requireConfirmation = true,
            continueOnError = false,
            autonomous = false
        )
    }
}

// =============================================================================
// Workflow Context
// =============================================================================

/**
 * Runtime context for workflow execution.
 *
 * @property workflowId ID of the workflow being executed
 * @property runId Unique ID for this run
 * @property projectPath Project root path
 * @property variables Variables set during execution
 * @property results Results from completed steps
 * @property currentStepId Current step being executed
 * @property lastStepSuccess Whether the last step succeeded
 * @property startTime When the workflow started
 */
data class WorkflowContext(
    val workflowId: String,
    val runId: String = UUID.randomUUID().toString(),
    val projectPath: String,
    val variables: MutableMap<String, String> = mutableMapOf(),
    val results: MutableMap<String, StepResult> = mutableMapOf(),
    val currentStepId: String? = null,
    val lastStepSuccess: Boolean = true,
    val startTime: Instant = Instant.now()
) {
    /**
     * Sets a variable.
     */
    fun setVariable(name: String, value: String) {
        variables[name] = value
    }

    /**
     * Gets a variable.
     */
    fun getVariable(name: String): String? = variables[name]

    /**
     * Records a step result.
     */
    fun recordResult(stepId: String, result: StepResult) {
        results[stepId] = result
    }

    /**
     * Gets a step result.
     */
    fun getResult(stepId: String): StepResult? = results[stepId]

    /**
     * Elapsed time in milliseconds.
     */
    fun elapsedMs(now: Instant = Instant.now()): Long {
        return now.toEpochMilli() - startTime.toEpochMilli()
    }

    /**
     * Number of completed steps.
     */
    val completedSteps: Int get() = results.size

    companion object {
        fun create(workflowId: String, projectPath: String): WorkflowContext =
            WorkflowContext(workflowId = workflowId, projectPath = projectPath)
    }
}

// =============================================================================
// Step Result
// =============================================================================

/**
 * Result of a workflow step execution.
 */
data class StepResult(
    val stepId: String,
    val action: WorkflowAction,
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val durationMs: Long = 0,
    val outputs: Map<String, Any> = emptyMap()
) {
    /**
     * Gets a string output.
     */
    fun getString(key: String): String = outputs[key]?.toString() ?: ""

    companion object {
        fun success(stepId: String, action: WorkflowAction, output: String? = null, durationMs: Long = 0): StepResult =
            StepResult(stepId, action, true, output, null, durationMs)

        fun failure(stepId: String, action: WorkflowAction, error: String, durationMs: Long = 0): StepResult =
            StepResult(stepId, action, false, null, error, durationMs)
    }
}

// =============================================================================
// Workflow Validation
// =============================================================================

/**
 * Workflow validation result.
 */
data class WorkflowValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val hasWarnings: Boolean get() = warnings.isNotEmpty()

    companion object {
        fun valid(): WorkflowValidation = WorkflowValidation(true)

        fun invalid(errors: List<String>): WorkflowValidation =
            WorkflowValidation(false, errors)

        fun withWarnings(warnings: List<String>): WorkflowValidation =
            WorkflowValidation(true, emptyList(), warnings)
    }
}

// =============================================================================
// Workflow Status
// =============================================================================

/**
 * Status of a workflow run.
 */
enum class WorkflowStatus {
    /** Not yet started */
    PENDING,
    /** Currently running */
    RUNNING,
    /** Waiting for user input */
    WAITING_USER,
    /** Paused */
    PAUSED,
    /** Completed successfully */
    COMPLETED,
    /** Failed with error */
    FAILED,
    /** Cancelled by user */
    CANCELLED,
    /** Timed out */
    TIMEOUT
}

// =============================================================================
// Workflow Run
// =============================================================================

/**
 * A single execution of a workflow.
 */
data class WorkflowRun(
    val id: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val workflowName: String,
    val status: WorkflowStatus = WorkflowStatus.PENDING,
    val context: WorkflowContext,
    val completedSteps: List<StepResult> = emptyList(),
    val currentStep: String? = null,
    val error: String? = null,
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null
) {
    val isActive: Boolean get() = status in setOf(
        WorkflowStatus.RUNNING,
        WorkflowStatus.WAITING_USER,
        WorkflowStatus.PAUSED
    )

    val isComplete: Boolean get() = status in setOf(
        WorkflowStatus.COMPLETED,
        WorkflowStatus.FAILED,
        WorkflowStatus.CANCELLED,
        WorkflowStatus.TIMEOUT
    )

    val durationMs: Long get() = (endTime ?: Instant.now()).toEpochMilli() - startTime.toEpochMilli()

    fun withStatus(newStatus: WorkflowStatus): WorkflowRun = copy(status = newStatus)

    fun withStep(stepId: String?): WorkflowRun = copy(currentStep = stepId)

    fun withResult(result: StepResult): WorkflowRun = copy(
        completedSteps = completedSteps + result
    )

    fun complete(): WorkflowRun = copy(
        status = WorkflowStatus.COMPLETED,
        endTime = Instant.now()
    )

    fun fail(error: String): WorkflowRun = copy(
        status = WorkflowStatus.FAILED,
        error = error,
        endTime = Instant.now()
    )
}

// =============================================================================
// Workflow Events
// =============================================================================

/**
 * Events from workflow execution.
 */
sealed class WorkflowEvent {
    abstract val runId: String
    abstract val timestamp: Instant

    data class WorkflowStarted(
        override val runId: String,
        val workflowId: String,
        val workflowName: String,
        override val timestamp: Instant = Instant.now()
    ) : WorkflowEvent()

    data class StepStarted(
        override val runId: String,
        val stepId: String,
        val action: WorkflowAction,
        override val timestamp: Instant = Instant.now()
    ) : WorkflowEvent()

    data class StepCompleted(
        override val runId: String,
        val stepId: String,
        val result: StepResult,
        override val timestamp: Instant = Instant.now()
    ) : WorkflowEvent()

    data class UserInputRequired(
        override val runId: String,
        val stepId: String,
        val prompt: String,
        override val timestamp: Instant = Instant.now()
    ) : WorkflowEvent()

    data class WorkflowCompleted(
        override val runId: String,
        val success: Boolean,
        val stepsCompleted: Int,
        val durationMs: Long,
        override val timestamp: Instant = Instant.now()
    ) : WorkflowEvent()

    data class WorkflowFailed(
        override val runId: String,
        val error: String,
        val failedStepId: String?,
        override val timestamp: Instant = Instant.now()
    ) : WorkflowEvent()
}

// =============================================================================
// Built-in Workflows
// =============================================================================

/**
 * Built-in workflows for common tasks.
 */
object BuiltInWorkflows {

    /**
     * Fix Error Workflow
     *
     * Analyzes and fixes a compilation or runtime error.
     */
    val FIX_ERROR = AgentWorkflow(
        id = "fix-error",
        name = "Fix Error",
        description = "Analyze and fix a compilation or runtime error",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("focus" to "error"), "2", null),
            WorkflowStep("2", WorkflowAction.SEARCH_CODEBASE, mapOf("context" to "related"), "3", null),
            WorkflowStep("3", WorkflowAction.GENERATE_CODE, mapOf("type" to "fix"), "4", null),
            WorkflowStep("4", WorkflowAction.ASK_USER, mapOf("confirm" to true), "5", null),
            WorkflowStep("5", WorkflowAction.APPLY_CHANGES, emptyMap(), "6", null),
            WorkflowStep("6", WorkflowAction.RUN_TESTS, emptyMap(), null, "3")
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.ERROR_DETECTED, null))
    )

    /**
     * Implement Feature Workflow
     *
     * Implements a new feature from requirements.
     */
    val IMPLEMENT_FEATURE = AgentWorkflow(
        id = "implement-feature",
        name = "Implement Feature",
        description = "Implement a new feature from requirements",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("scope" to "project"), "2", null),
            WorkflowStep("2", WorkflowAction.ASK_USER, mapOf("clarify" to "requirements"), "3", null),
            WorkflowStep("3", WorkflowAction.GENERATE_CODE, mapOf("type" to "implementation"), "4", null),
            WorkflowStep("4", WorkflowAction.GENERATE_CODE, mapOf("type" to "tests"), "5", null),
            WorkflowStep("5", WorkflowAction.ASK_USER, mapOf("confirm" to true), "6", null),
            WorkflowStep("6", WorkflowAction.APPLY_CHANGES, emptyMap(), "7", null),
            WorkflowStep("7", WorkflowAction.RUN_TESTS, emptyMap(), null, null)
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.MANUAL, null))
    )

    /**
     * Refactor Code Workflow
     *
     * Refactors selected code with tests.
     */
    val REFACTOR_CODE = AgentWorkflow(
        id = "refactor-code",
        name = "Refactor Code",
        description = "Refactor selected code for better quality",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("focus" to "refactoring"), "2", null),
            WorkflowStep("2", WorkflowAction.GENERATE_CODE, mapOf("type" to "refactoring"), "3", null),
            WorkflowStep("3", WorkflowAction.ASK_USER, mapOf("confirm" to true), "4", null),
            WorkflowStep("4", WorkflowAction.APPLY_CHANGES, emptyMap(), "5", null),
            WorkflowStep("5", WorkflowAction.RUN_TESTS, emptyMap(), null, "2")
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.MANUAL, null))
    )

    /**
     * Add Tests Workflow
     *
     * Generates tests for existing code.
     */
    val ADD_TESTS = AgentWorkflow(
        id = "add-tests",
        name = "Add Tests",
        description = "Generate comprehensive tests for existing code",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("focus" to "testing"), "2", null),
            WorkflowStep("2", WorkflowAction.GENERATE_CODE, mapOf("type" to "tests"), "3", null),
            WorkflowStep("3", WorkflowAction.ASK_USER, mapOf("confirm" to true), "4", null),
            WorkflowStep("4", WorkflowAction.CREATE_FILE, mapOf("type" to "test"), "5", null),
            WorkflowStep("5", WorkflowAction.RUN_TESTS, emptyMap(), null, "2")
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.MANUAL, null))
    )

    /**
     * Code Review Workflow
     *
     * Reviews code and suggests improvements.
     */
    val CODE_REVIEW = AgentWorkflow(
        id = "code-review",
        name = "Code Review",
        description = "Review code and suggest improvements",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("focus" to "review"), "2", null),
            WorkflowStep("2", WorkflowAction.SEARCH_CODEBASE, mapOf("context" to "similar"), "3", null),
            WorkflowStep("3", WorkflowAction.LOG, mapOf("output" to "review_report"), null, null)
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.MANUAL, null)),
        config = WorkflowConfig(requireConfirmation = false)
    )

    /**
     * Document Code Workflow
     *
     * Generates documentation for code.
     */
    val DOCUMENT_CODE = AgentWorkflow(
        id = "document-code",
        name = "Document Code",
        description = "Generate documentation for existing code",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("focus" to "documentation"), "2", null),
            WorkflowStep("2", WorkflowAction.GENERATE_CODE, mapOf("type" to "documentation"), "3", null),
            WorkflowStep("3", WorkflowAction.ASK_USER, mapOf("confirm" to true), "4", null),
            WorkflowStep("4", WorkflowAction.APPLY_CHANGES, emptyMap(), null, null)
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.MANUAL, null))
    )

    /**
     * All built-in workflows.
     */
    val ALL = listOf(
        FIX_ERROR,
        IMPLEMENT_FEATURE,
        REFACTOR_CODE,
        ADD_TESTS,
        CODE_REVIEW,
        DOCUMENT_CODE
    )

    /**
     * Gets a built-in workflow by ID.
     */
    fun get(id: String): AgentWorkflow? = ALL.find { it.id == id }

    /**
     * Gets workflows matching a trigger type.
     */
    fun getByTrigger(triggerType: TriggerType): List<AgentWorkflow> =
        ALL.filter { workflow -> workflow.triggers.any { it.type == triggerType } }
}
