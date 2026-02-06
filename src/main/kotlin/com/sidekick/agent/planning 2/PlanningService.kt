package com.sidekick.agent.planning

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Planning Service
 *
 * Service for creating and managing task plans.
 * Part of Sidekick v0.9.1 Planning Agent feature.
 *
 * ## Features
 *
 * - Create plans from goals
 * - Validate plan structure
 * - Execute plans step by step
 * - Track plan progress
 * - Refine plans based on feedback
 *
 * @since 0.9.1
 */
class PlanningService(
    private val projectPath: String = "",
    private val llmPlanner: suspend (String, PlanningContext) -> TaskPlan = { goal, _ ->
        // Default simple planner
        TaskPlan.linear(goal, listOf("Analyze", "Implement", "Verify"))
    }
) {
    private val plans = ConcurrentHashMap<String, TaskPlan>()
    private val activePlanId = java.util.concurrent.atomic.AtomicReference<String?>(null)
    private val eventListeners = CopyOnWriteArrayList<(PlanEvent) -> Unit>()
    private val planHistory = CopyOnWriteArrayList<PlanExecution>()

    // =========================================================================
    // Plan Creation
    // =========================================================================

    /**
     * Creates a plan for a goal.
     */
    suspend fun createPlan(goal: String, context: PlanningContext): TaskPlan {
        val plan = llmPlanner(goal, context)
        plans[plan.id] = plan
        emitEvent(PlanEvent.PlanCreated(plan.id, plan.goal, plan.steps.size))
        return plan
    }

    /**
     * Creates a simple linear plan without LLM.
     */
    fun createSimplePlan(goal: String, stepDescriptions: List<String>): TaskPlan {
        val plan = TaskPlan.linear(goal, stepDescriptions)
        plans[plan.id] = plan
        emitEvent(PlanEvent.PlanCreated(plan.id, plan.goal, plan.steps.size))
        return plan
    }

    /**
     * Creates a detailed plan with full specification.
     */
    fun createDetailedPlan(
        goal: String,
        analysis: ProblemAnalysis,
        strategy: PlanStrategy,
        steps: List<PlanStep>,
        dependencies: Map<String, List<String>> = emptyMap(),
        risks: List<PlanRisk> = emptyList()
    ): TaskPlan {
        val plan = TaskPlan(
            goal = goal,
            analysis = analysis,
            strategy = strategy,
            steps = steps,
            dependencies = dependencies,
            estimatedEffort = EffortEstimate.fromSteps(steps),
            risks = risks
        )

        val validation = plan.validate()
        if (!validation.valid) {
            throw IllegalArgumentException("Invalid plan: ${validation.errors.joinToString()}")
        }

        plans[plan.id] = plan
        emitEvent(PlanEvent.PlanCreated(plan.id, plan.goal, plan.steps.size))
        return plan
    }

    // =========================================================================
    // Plan Retrieval
    // =========================================================================

    /**
     * Gets a plan by ID.
     */
    fun getPlan(planId: String): TaskPlan? = plans[planId]

    /**
     * Gets all plans.
     */
    fun getAllPlans(): List<TaskPlan> = plans.values.toList()

    /**
     * Gets the active plan.
     */
    fun getActivePlan(): TaskPlan? = activePlanId.get()?.let { getPlan(it) }

    /**
     * Gets plans by status.
     */
    fun getPlansByStatus(status: PlanStatus): List<TaskPlan> =
        plans.values.filter { it.status == status }

    // =========================================================================
    // Plan Lifecycle
    // =========================================================================

    /**
     * Approves a plan for execution.
     */
    fun approvePlan(planId: String): TaskPlan? {
        val plan = plans[planId] ?: return null
        if (plan.status != PlanStatus.DRAFT) return plan

        val approved = plan.withStatus(PlanStatus.APPROVED)
        plans[planId] = approved
        emitEvent(PlanEvent.PlanApproved(planId))
        return approved
    }

    /**
     * Starts plan execution.
     */
    fun startPlan(planId: String): TaskPlan? {
        val plan = plans[planId] ?: return null
        if (plan.status !in setOf(PlanStatus.DRAFT, PlanStatus.APPROVED)) return plan

        val started = plan.withStatus(PlanStatus.IN_PROGRESS)
        plans[planId] = started
        activePlanId.set(planId)

        planHistory.add(PlanExecution(planId, plan.goal, Instant.now()))
        return started
    }

    /**
     * Cancels a plan.
     */
    fun cancelPlan(planId: String): TaskPlan? {
        val plan = plans[planId] ?: return null
        if (plan.status.displayName == "Completed") return plan

        val cancelled = plan.withStatus(PlanStatus.CANCELLED)
        plans[planId] = cancelled

        if (activePlanId.get() == planId) {
            activePlanId.set(null)
        }

        return cancelled
    }

    // =========================================================================
    // Step Execution
    // =========================================================================

    /**
     * Starts the next ready step.
     */
    fun startNextStep(planId: String): PlanStep? {
        val plan = plans[planId] ?: return null
        val readySteps = plan.getReadySteps()
        val nextStep = readySteps.firstOrNull() ?: return null

        val started = nextStep.start()
        val updatedPlan = plan.withStepStatus(nextStep.id, StepStatus.IN_PROGRESS)
        plans[planId] = updatedPlan

        emitEvent(PlanEvent.StepStarted(planId, nextStep.id, nextStep.title))
        return started
    }

    /**
     * Completes a step.
     */
    fun completeStep(planId: String, stepId: String, output: String? = null): TaskPlan? {
        val plan = plans[planId] ?: return null
        val step = plan.getStep(stepId) ?: return plan

        val updatedPlan = plan.withStepStatus(stepId, StepStatus.COMPLETED)
        
        // Check if plan is complete
        val finalPlan = if (updatedPlan.isComplete) {
            updatedPlan.withStatus(PlanStatus.COMPLETED)
        } else {
            updatedPlan
        }

        plans[planId] = finalPlan
        emitEvent(PlanEvent.StepCompleted(planId, stepId, true, output))

        if (finalPlan.status == PlanStatus.COMPLETED) {
            val execution = planHistory.find { it.planId == planId }
            val durationMs = execution?.let { Instant.now().toEpochMilli() - it.startTime.toEpochMilli() } ?: 0
            emitEvent(PlanEvent.PlanCompleted(planId, true, finalPlan.steps.size, durationMs))
            
            if (activePlanId.get() == planId) {
                activePlanId.set(null)
            }
        }

        return finalPlan
    }

    /**
     * Fails a step.
     */
    fun failStep(planId: String, stepId: String, error: String): TaskPlan? {
        val plan = plans[planId] ?: return null

        val updatedPlan = plan
            .withStepStatus(stepId, StepStatus.FAILED)
            .withStatus(PlanStatus.FAILED)

        plans[planId] = updatedPlan
        emitEvent(PlanEvent.StepCompleted(planId, stepId, false, error))
        emitEvent(PlanEvent.PlanFailed(planId, error, stepId))

        if (activePlanId.get() == planId) {
            activePlanId.set(null)
        }

        return updatedPlan
    }

    /**
     * Skips a step.
     */
    fun skipStep(planId: String, stepId: String): TaskPlan? {
        val plan = plans[planId] ?: return null

        val updatedPlan = plan.withStepStatus(stepId, StepStatus.SKIPPED)
        plans[planId] = updatedPlan
        emitEvent(PlanEvent.StepCompleted(planId, stepId, true, "Skipped"))

        return updatedPlan
    }

    // =========================================================================
    // Plan Refinement
    // =========================================================================

    /**
     * Refines a plan based on feedback.
     */
    suspend fun refinePlan(planId: String, feedback: String): TaskPlan? {
        val plan = plans[planId] ?: return null
        if (plan.status != PlanStatus.DRAFT) return plan

        val context = PlanningContext.create("", projectPath)
        val refinedGoal = "${plan.goal}\n\nFeedback: $feedback"
        val refinedPlan = llmPlanner(refinedGoal, context).copy(
            id = plan.id,
            version = plan.version + 1
        )

        plans[planId] = refinedPlan
        return refinedPlan
    }

    /**
     * Adds a step to a plan.
     */
    fun addStep(planId: String, step: PlanStep, afterStepId: String? = null): TaskPlan? {
        val plan = plans[planId] ?: return null
        if (plan.status != PlanStatus.DRAFT) return plan

        val newSteps = if (afterStepId != null) {
            val index = plan.steps.indexOfFirst { it.id == afterStepId }
            if (index >= 0) {
                plan.steps.toMutableList().apply { add(index + 1, step) }
            } else {
                plan.steps + step
            }
        } else {
            plan.steps + step
        }

        // Renumber steps
        val renumbered = newSteps.mapIndexed { index, s -> s.copy(order = index + 1) }
        val updated = plan.copy(
            steps = renumbered,
            estimatedEffort = EffortEstimate.fromSteps(renumbered),
            version = plan.version + 1
        )

        plans[planId] = updated
        return updated
    }

    /**
     * Removes a step from a plan.
     */
    fun removeStep(planId: String, stepId: String): TaskPlan? {
        val plan = plans[planId] ?: return null
        if (plan.status != PlanStatus.DRAFT) return plan

        val newSteps = plan.steps.filter { it.id != stepId }
        val renumbered = newSteps.mapIndexed { index, s -> s.copy(order = index + 1) }

        // Remove dependencies
        val newDeps = plan.dependencies
            .filterKeys { it != stepId }
            .mapValues { (_, deps) -> deps.filter { it != stepId } }

        val updated = plan.copy(
            steps = renumbered,
            dependencies = newDeps,
            estimatedEffort = EffortEstimate.fromSteps(renumbered),
            version = plan.version + 1
        )

        plans[planId] = updated
        return updated
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Validates a plan.
     */
    fun validatePlan(planId: String): PlanValidation? {
        return plans[planId]?.validate()
    }

    /**
     * Checks if a plan is executable.
     */
    fun isExecutable(planId: String): Boolean {
        val plan = plans[planId] ?: return false
        val validation = plan.validate()
        return validation.valid && plan.status in setOf(PlanStatus.DRAFT, PlanStatus.APPROVED)
    }

    // =========================================================================
    // Analysis Helpers
    // =========================================================================

    /**
     * Analyzes a goal to determine scope and complexity.
     */
    fun analyzeGoal(goal: String): ProblemAnalysis {
        val words = goal.lowercase().split(" ")

        // Simple heuristics for scope
        val scope = when {
            words.any { it in listOf("project", "all", "entire", "every") } -> Scope.PROJECT_WIDE
            words.any { it in listOf("module", "package", "component") } -> Scope.MODULE
            words.any { it in listOf("files", "multiple", "several") } -> Scope.MULTI_FILE
            else -> Scope.SINGLE_FILE
        }

        // Simple heuristics for complexity
        val complexity = when {
            words.any { it in listOf("simple", "quick", "small", "minor") } -> Complexity.SIMPLE
            words.any { it in listOf("complex", "difficult", "major", "redesign") } -> Complexity.COMPLEX
            words.any { it in listOf("refactor", "migrate", "rewrite") } -> Complexity.COMPLEX
            else -> Complexity.MODERATE
        }

        return ProblemAnalysis(scope = scope, complexity = complexity)
    }

    /**
     * Suggests a strategy based on analysis.
     */
    fun suggestStrategy(analysis: ProblemAnalysis): PlanStrategy {
        return when {
            analysis.complexity == Complexity.VERY_COMPLEX -> PlanStrategy.spike()
            analysis.unknowns.isNotEmpty() -> PlanStrategy.spike()
            analysis.scope in setOf(Scope.PROJECT_WIDE, Scope.CROSS_MODULE) -> PlanStrategy(
                approach = Approach.INCREMENTAL,
                reasoning = "Large scope requires careful incremental changes"
            )
            else -> PlanStrategy.default()
        }
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets planning statistics.
     */
    fun getStats(): PlanningStats {
        val allPlans = plans.values.toList()

        return PlanningStats(
            totalPlans = allPlans.size,
            draftPlans = allPlans.count { it.status == PlanStatus.DRAFT },
            inProgressPlans = allPlans.count { it.status == PlanStatus.IN_PROGRESS },
            completedPlans = allPlans.count { it.status == PlanStatus.COMPLETED },
            failedPlans = allPlans.count { it.status == PlanStatus.FAILED },
            averageSteps = if (allPlans.isNotEmpty())
                allPlans.map { it.steps.size }.average().toInt()
            else 0,
            totalStepsCompleted = allPlans.sumOf { plan ->
                plan.steps.count { it.status == StepStatus.COMPLETED }
            }
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (PlanEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (PlanEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: PlanEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Clears all plans.
     */
    fun clearPlans() {
        plans.clear()
        activePlanId.set(null)
    }

    /**
     * Removes completed/cancelled plans older than a duration.
     */
    fun pruneOldPlans(maxAgeMs: Long) {
        val cutoff = Instant.now().toEpochMilli() - maxAgeMs
        plans.entries.removeIf { (_, plan) ->
            plan.status in setOf(PlanStatus.COMPLETED, PlanStatus.CANCELLED, PlanStatus.FAILED) &&
            plan.createdAt.toEpochMilli() < cutoff
        }
    }
}

/**
 * Planning statistics.
 */
data class PlanningStats(
    val totalPlans: Int,
    val draftPlans: Int,
    val inProgressPlans: Int,
    val completedPlans: Int,
    val failedPlans: Int,
    val averageSteps: Int,
    val totalStepsCompleted: Int
) {
    val successRate: Float
        get() = if (completedPlans + failedPlans > 0)
            completedPlans.toFloat() / (completedPlans + failedPlans)
        else 0f
}

/**
 * Record of a plan execution.
 */
data class PlanExecution(
    val planId: String,
    val goal: String,
    val startTime: Instant,
    val endTime: Instant? = null
)
