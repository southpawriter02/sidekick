package com.sidekick.agent.planning

import java.time.Instant
import java.util.UUID

/**
 * # Planning Models
 *
 * Data models for task decomposition and strategic planning.
 * Part of Sidekick v0.9.1 Planning Agent feature.
 *
 * ## Overview
 *
 * The planning system enables:
 * - Problem analysis and scoping
 * - Strategy selection with alternatives
 * - Step-by-step task decomposition
 * - Dependency graph management
 * - Risk identification and mitigation
 * - Effort estimation
 *
 * @since 0.9.1
 */

// =============================================================================
// Task Plan
// =============================================================================

/**
 * A comprehensive plan for completing a complex task.
 *
 * @property id Unique plan identifier
 * @property goal The high-level goal to achieve
 * @property analysis Problem space analysis
 * @property strategy Chosen approach with reasoning
 * @property steps Ordered list of steps to execute
 * @property dependencies Step dependency graph (stepId -> dependsOn)
 * @property estimatedEffort Time and resource estimates
 * @property risks Identified risks with mitigations
 * @property status Current plan status
 * @property createdAt When the plan was created
 * @property version Plan version for iterations
 */
data class TaskPlan(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val analysis: ProblemAnalysis,
    val strategy: PlanStrategy,
    val steps: List<PlanStep>,
    val dependencies: Map<String, List<String>> = emptyMap(),
    val estimatedEffort: EffortEstimate,
    val risks: List<PlanRisk> = emptyList(),
    val status: PlanStatus = PlanStatus.DRAFT,
    val createdAt: Instant = Instant.now(),
    val version: Int = 1
) {
    /**
     * Gets a step by ID.
     */
    fun getStep(stepId: String): PlanStep? = steps.find { it.id == stepId }

    /**
     * Gets steps that a step depends on.
     */
    fun getDependencies(stepId: String): List<PlanStep> {
        val depIds = dependencies[stepId] ?: emptyList()
        return depIds.mapNotNull { getStep(it) }
    }

    /**
     * Gets steps that depend on a step.
     */
    fun getDependents(stepId: String): List<PlanStep> {
        return steps.filter { step ->
            dependencies[step.id]?.contains(stepId) == true
        }
    }

    /**
     * Gets steps ready to execute (all dependencies complete).
     */
    fun getReadySteps(): List<PlanStep> {
        return steps.filter { step ->
            step.status == StepStatus.PENDING &&
            getDependencies(step.id).all { it.status == StepStatus.COMPLETED }
        }
    }

    /**
     * Gets steps that can run in parallel.
     */
    fun getParallelizableSteps(): List<List<PlanStep>> {
        val levels = mutableListOf<List<PlanStep>>()
        val completed = mutableSetOf<String>()

        while (completed.size < steps.size) {
            val ready = steps.filter { step ->
                step.id !in completed &&
                getDependencies(step.id).all { it.id in completed }
            }
            if (ready.isEmpty()) break
            levels.add(ready)
            completed.addAll(ready.map { it.id })
        }

        return levels
    }

    /**
     * Progress percentage.
     */
    val progress: Float
        get() = if (steps.isEmpty()) 0f
        else steps.count { it.status == StepStatus.COMPLETED }.toFloat() / steps.size

    /**
     * Whether all steps are complete.
     */
    val isComplete: Boolean
        get() = steps.all { it.status == StepStatus.COMPLETED }

    /**
     * Whether any step has failed.
     */
    val hasFailed: Boolean
        get() = steps.any { it.status == StepStatus.FAILED }

    /**
     * Updates a step status.
     */
    fun withStepStatus(stepId: String, status: StepStatus): TaskPlan {
        val updatedSteps = steps.map { step ->
            if (step.id == stepId) step.copy(status = status) else step
        }
        return copy(steps = updatedSteps)
    }

    /**
     * Updates plan status.
     */
    fun withStatus(status: PlanStatus): TaskPlan = copy(status = status)

    /**
     * Validates the plan structure.
     */
    fun validate(): PlanValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (goal.isBlank()) errors.add("Plan goal is required")
        if (steps.isEmpty()) errors.add("Plan must have at least one step")

        // Check step IDs are unique
        val stepIds = steps.map { it.id }
        if (stepIds.size != stepIds.toSet().size) {
            errors.add("Duplicate step IDs found")
        }

        // Check dependencies reference valid steps
        val stepIdSet = stepIds.toSet()
        dependencies.forEach { (stepId, deps) ->
            if (stepId !in stepIdSet) {
                errors.add("Dependency references unknown step: $stepId")
            }
            deps.forEach { dep ->
                if (dep !in stepIdSet) {
                    errors.add("Step '$stepId' depends on unknown step: $dep")
                }
            }
        }

        // Check for circular dependencies
        if (hasCircularDependencies()) {
            errors.add("Circular dependencies detected")
        }

        // Warnings
        if (risks.isEmpty()) {
            warnings.add("No risks identified - consider potential issues")
        }
        if (steps.none { it.type == StepType.TEST }) {
            warnings.add("No testing steps - consider adding verification")
        }

        return PlanValidation(errors.isEmpty(), errors, warnings)
    }

    /**
     * Checks for circular dependencies using DFS.
     */
    private fun hasCircularDependencies(): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun hasCycle(stepId: String): Boolean {
            visited.add(stepId)
            recursionStack.add(stepId)

            for (dep in dependencies[stepId] ?: emptyList()) {
                if (dep == stepId) continue // skip self-references
                if (dep !in visited) {
                    if (hasCycle(dep)) return true
                } else if (dep in recursionStack) {
                    return true
                }
            }

            recursionStack.remove(stepId)
            return false
        }

        return steps.any { step ->
            if (step.id !in visited) hasCycle(step.id) else false
        }
    }

    companion object {
        /**
         * Creates a simple linear plan.
         */
        fun linear(goal: String, stepDescriptions: List<String>): TaskPlan {
            val steps = stepDescriptions.mapIndexed { index, desc ->
                PlanStep(
                    id = "${index + 1}",
                    order = index + 1,
                    title = "Step ${index + 1}",
                    description = desc,
                    type = StepType.IMPLEMENT,
                    estimatedTokens = 1000,
                    canParallelize = false,
                    rollbackStrategy = null,
                    verificationCriteria = "Step completes successfully"
                )
            }

            val deps = steps.drop(1).associate { step ->
                step.id to listOf(step.id)
            }

            return TaskPlan(
                goal = goal,
                analysis = ProblemAnalysis.simple(),
                strategy = PlanStrategy.default(),
                steps = steps,
                dependencies = deps,
                estimatedEffort = EffortEstimate.fromSteps(steps)
            )
        }
    }
}

// =============================================================================
// Problem Analysis
// =============================================================================

/**
 * Analysis of the problem space.
 *
 * @property scope How wide is the change scope
 * @property complexity How difficult is the task
 * @property affectedAreas Files/modules affected
 * @property existingPatterns Patterns to follow
 * @property constraints Limitations to work within
 * @property unknowns Things that need investigation
 */
data class ProblemAnalysis(
    val scope: Scope,
    val complexity: Complexity,
    val affectedAreas: List<String> = emptyList(),
    val existingPatterns: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val unknowns: List<String> = emptyList()
) {
    /**
     * Overall difficulty score (1-10).
     */
    val difficultyScore: Int
        get() = scope.weight + complexity.weight

    /**
     * Whether investigation is needed.
     */
    val needsInvestigation: Boolean
        get() = unknowns.isNotEmpty()

    companion object {
        fun simple(): ProblemAnalysis = ProblemAnalysis(
            scope = Scope.SINGLE_FILE,
            complexity = Complexity.SIMPLE
        )

        fun moderate(): ProblemAnalysis = ProblemAnalysis(
            scope = Scope.MULTI_FILE,
            complexity = Complexity.MODERATE
        )

        fun complex(): ProblemAnalysis = ProblemAnalysis(
            scope = Scope.MODULE,
            complexity = Complexity.COMPLEX
        )
    }
}

/**
 * Scope of changes required.
 */
enum class Scope(val displayName: String, val weight: Int) {
    /** Changes to a single file */
    SINGLE_FILE("Single File", 1),
    /** Changes across multiple files */
    MULTI_FILE("Multiple Files", 2),
    /** Changes within a module/package */
    MODULE("Module", 3),
    /** Changes across modules */
    CROSS_MODULE("Cross-Module", 4),
    /** Changes throughout the project */
    PROJECT_WIDE("Project-Wide", 5)
}

/**
 * Complexity of the task.
 */
enum class Complexity(val displayName: String, val weight: Int) {
    /** Simple, straightforward change */
    TRIVIAL("Trivial", 1),
    /** Easy but requires some thought */
    SIMPLE("Simple", 2),
    /** Average complexity */
    MODERATE("Moderate", 3),
    /** Difficult, needs careful planning */
    COMPLEX("Complex", 4),
    /** Very difficult, high risk */
    VERY_COMPLEX("Very Complex", 5)
}

// =============================================================================
// Plan Strategy
// =============================================================================

/**
 * Strategy for approaching the task.
 *
 * @property approach The main approach to use
 * @property reasoning Why this approach was chosen
 * @property alternatives Other approaches considered
 * @property checkpoints Key verification points
 */
data class PlanStrategy(
    val approach: Approach,
    val reasoning: String,
    val alternatives: List<AlternativeApproach> = emptyList(),
    val checkpoints: List<String> = emptyList()
) {
    companion object {
        fun default(): PlanStrategy = PlanStrategy(
            approach = Approach.INCREMENTAL,
            reasoning = "Incremental changes allow for easy verification and rollback"
        )

        fun testFirst(): PlanStrategy = PlanStrategy(
            approach = Approach.TEST_FIRST,
            reasoning = "Writing tests first ensures requirements are clear and verifiable"
        )

        fun spike(): PlanStrategy = PlanStrategy(
            approach = Approach.SPIKE_FIRST,
            reasoning = "Prototyping first helps understand unknowns before committing"
        )
    }
}

/**
 * Approaches for tackling a task.
 */
enum class Approach(val displayName: String, val description: String) {
    /** Make small changes, verify each step */
    INCREMENTAL("Incremental", "Small changes with verification at each step"),
    /** Make all changes at once */
    BIG_BANG("Big Bang", "Complete implementation before testing"),
    /** Prototype first, then implement properly */
    SPIKE_FIRST("Spike First", "Quick prototype to understand, then clean implementation"),
    /** Write tests first (TDD) */
    TEST_FIRST("Test First", "Write tests before implementation"),
    /** Run independent tracks in parallel */
    PARALLEL("Parallel", "Multiple independent work streams")
}

/**
 * An alternative approach with tradeoffs.
 */
data class AlternativeApproach(
    val approach: Approach,
    val tradeoffs: String,
    val whenToUse: String
)

// =============================================================================
// Plan Step
// =============================================================================

/**
 * A single step in the plan.
 *
 * @property id Unique step identifier
 * @property order Execution order
 * @property title Short step title
 * @property description Detailed description
 * @property type Type of step
 * @property estimatedTokens Token estimate for LLM
 * @property canParallelize Can run parallel with other steps
 * @property rollbackStrategy How to undo this step
 * @property verificationCriteria How to verify success
 * @property status Current step status
 * @property output Step output when complete
 */
data class PlanStep(
    val id: String,
    val order: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedTokens: Int = 1000,
    val canParallelize: Boolean = false,
    val rollbackStrategy: String? = null,
    val verificationCriteria: String,
    val status: StepStatus = StepStatus.PENDING,
    val output: String? = null
) {
    /**
     * Whether the step is actionable.
     */
    val isActionable: Boolean
        get() = status in setOf(StepStatus.PENDING, StepStatus.BLOCKED)

    /**
     * Whether the step is complete (success or skip).
     */
    val isComplete: Boolean
        get() = status in setOf(StepStatus.COMPLETED, StepStatus.SKIPPED)

    /**
     * Whether the step is terminal (failed or complete).
     */
    val isTerminal: Boolean
        get() = status in setOf(StepStatus.COMPLETED, StepStatus.FAILED, StepStatus.SKIPPED)

    /**
     * Creates a completed step with output.
     */
    fun complete(output: String? = null): PlanStep =
        copy(status = StepStatus.COMPLETED, output = output)

    /**
     * Creates a failed step.
     */
    fun fail(error: String? = null): PlanStep =
        copy(status = StepStatus.FAILED, output = error)

    /**
     * Marks step as in progress.
     */
    fun start(): PlanStep = copy(status = StepStatus.IN_PROGRESS)

    /**
     * Marks step as blocked.
     */
    fun block(): PlanStep = copy(status = StepStatus.BLOCKED)
}

/**
 * Types of steps in a plan.
 */
enum class StepType(val displayName: String, val icon: String) {
    /** Research and gather information */
    RESEARCH("Research", "🔍"),
    /** Design and architecture */
    DESIGN("Design", "📐"),
    /** Implement code */
    IMPLEMENT("Implement", "💻"),
    /** Write and run tests */
    TEST("Test", "🧪"),
    /** Refactor existing code */
    REFACTOR("Refactor", "♻️"),
    /** Write documentation */
    DOCUMENT("Document", "📝"),
    /** Verify results */
    VERIFY("Verify", "✅"),
    /** Clean up temporary changes */
    CLEANUP("Cleanup", "🧹")
}

/**
 * Status of a plan step.
 */
enum class StepStatus(val displayName: String, val isTerminal: Boolean) {
    /** Not yet started */
    PENDING("Pending", false),
    /** Currently executing */
    IN_PROGRESS("In Progress", false),
    /** Successfully completed */
    COMPLETED("Completed", true),
    /** Failed with error */
    FAILED("Failed", true),
    /** Skipped (not needed) */
    SKIPPED("Skipped", true),
    /** Blocked by dependency */
    BLOCKED("Blocked", false)
}

/**
 * Status of the overall plan.
 */
enum class PlanStatus(val displayName: String) {
    /** Plan created but not approved */
    DRAFT("Draft"),
    /** Plan approved for execution */
    APPROVED("Approved"),
    /** Plan currently executing */
    IN_PROGRESS("In Progress"),
    /** All steps completed successfully */
    COMPLETED("Completed"),
    /** Plan failed */
    FAILED("Failed"),
    /** Plan cancelled by user */
    CANCELLED("Cancelled")
}

// =============================================================================
// Effort Estimate
// =============================================================================

/**
 * Effort estimate for a plan.
 *
 * @property totalSteps Total number of steps
 * @property estimatedMinutes Estimated time in minutes
 * @property confidence Confidence in estimate (0-1)
 * @property breakdown Steps by type
 * @property tokenEstimate Total token estimate
 */
data class EffortEstimate(
    val totalSteps: Int,
    val estimatedMinutes: Int,
    val confidence: Float,
    val breakdown: Map<StepType, Int> = emptyMap(),
    val tokenEstimate: Int = 0
) {
    /**
     * Estimated hours.
     */
    val estimatedHours: Float get() = estimatedMinutes / 60f

    /**
     * Human-readable time string.
     */
    val timeString: String
        get() = when {
            estimatedMinutes < 60 -> "$estimatedMinutes minutes"
            estimatedMinutes < 120 -> "about 1 hour"
            else -> "about ${(estimatedMinutes / 60)} hours"
        }

    /**
     * Confidence as percentage string.
     */
    val confidencePercent: String get() = "${(confidence * 100).toInt()}%"

    companion object {
        fun fromSteps(steps: List<PlanStep>): EffortEstimate {
            val breakdown = steps.groupBy { it.type }.mapValues { it.value.size }
            val tokenEstimate = steps.sumOf { it.estimatedTokens }

            // Rough estimate: 2 minutes per 1000 tokens
            val minutes = (tokenEstimate / 1000) * 2

            return EffortEstimate(
                totalSteps = steps.size,
                estimatedMinutes = minutes.coerceAtLeast(1),
                confidence = 0.7f,
                breakdown = breakdown,
                tokenEstimate = tokenEstimate
            )
        }

        val UNKNOWN = EffortEstimate(0, 0, 0f)
    }
}

// =============================================================================
// Plan Risk
// =============================================================================

/**
 * A risk identified in the plan.
 *
 * @property id Risk identifier
 * @property description What could go wrong
 * @property probability Likelihood (0-1)
 * @property impact Severity if it occurs
 * @property mitigation How to prevent or handle
 * @property contingency Fallback plan if risk occurs
 */
data class PlanRisk(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val probability: Float,
    val impact: Impact,
    val mitigation: String,
    val contingency: String? = null
) {
    /**
     * Risk score (probability * impact weight).
     */
    val score: Float get() = probability * impact.weight

    /**
     * Risk level based on score.
     */
    val level: RiskLevel
        get() = when {
            score >= 3f -> RiskLevel.CRITICAL
            score >= 2f -> RiskLevel.HIGH
            score >= 1f -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

    companion object {
        fun low(description: String, mitigation: String): PlanRisk = PlanRisk(
            description = description,
            probability = 0.2f,
            impact = Impact.LOW,
            mitigation = mitigation
        )

        fun medium(description: String, mitigation: String): PlanRisk = PlanRisk(
            description = description,
            probability = 0.4f,
            impact = Impact.MEDIUM,
            mitigation = mitigation
        )

        fun high(description: String, mitigation: String): PlanRisk = PlanRisk(
            description = description,
            probability = 0.6f,
            impact = Impact.HIGH,
            mitigation = mitigation
        )
    }
}

/**
 * Impact severity.
 */
enum class Impact(val displayName: String, val weight: Int) {
    /** Minor inconvenience */
    LOW("Low", 1),
    /** Noticeable but manageable */
    MEDIUM("Medium", 2),
    /** Significant problem */
    HIGH("High", 3),
    /** Project-threatening */
    CRITICAL("Critical", 4)
}

/**
 * Overall risk level.
 */
enum class RiskLevel(val displayName: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk"),
    CRITICAL("Critical Risk")
}

// =============================================================================
// Plan Validation
// =============================================================================

/**
 * Result of plan validation.
 */
data class PlanValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val hasWarnings: Boolean get() = warnings.isNotEmpty()

    companion object {
        fun valid(): PlanValidation = PlanValidation(true)
        fun invalid(errors: List<String>): PlanValidation = PlanValidation(false, errors)
    }
}

// =============================================================================
// Plan Events
// =============================================================================

/**
 * Events from plan execution.
 */
sealed class PlanEvent {
    abstract val planId: String
    abstract val timestamp: Instant

    data class PlanCreated(
        override val planId: String,
        val goal: String,
        val stepCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : PlanEvent()

    data class PlanApproved(
        override val planId: String,
        override val timestamp: Instant = Instant.now()
    ) : PlanEvent()

    data class StepStarted(
        override val planId: String,
        val stepId: String,
        val stepTitle: String,
        override val timestamp: Instant = Instant.now()
    ) : PlanEvent()

    data class StepCompleted(
        override val planId: String,
        val stepId: String,
        val success: Boolean,
        val output: String?,
        override val timestamp: Instant = Instant.now()
    ) : PlanEvent()

    data class PlanCompleted(
        override val planId: String,
        val success: Boolean,
        val stepsCompleted: Int,
        val durationMs: Long,
        override val timestamp: Instant = Instant.now()
    ) : PlanEvent()

    data class PlanFailed(
        override val planId: String,
        val error: String,
        val failedStepId: String?,
        override val timestamp: Instant = Instant.now()
    ) : PlanEvent()
}

// =============================================================================
// Planning Context
// =============================================================================

/**
 * Context for planning.
 *
 * @property projectName Project name
 * @property projectPath Project root path
 * @property languages Languages used in project
 * @property framework Primary framework
 * @property activeFiles Currently open files
 * @property selectedCode Selected code context
 * @property errorContext Active error context
 */
data class PlanningContext(
    val projectName: String,
    val projectPath: String,
    val languages: List<String> = emptyList(),
    val framework: String? = null,
    val activeFiles: List<String> = emptyList(),
    val selectedCode: String? = null,
    val errorContext: String? = null
) {
    companion object {
        fun create(projectName: String, projectPath: String): PlanningContext =
            PlanningContext(projectName, projectPath)
    }
}
