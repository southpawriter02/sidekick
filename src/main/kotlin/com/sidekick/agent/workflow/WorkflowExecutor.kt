package com.sidekick.agent.workflow

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Workflow Executor
 *
 * Service for executing multi-step agent workflows.
 * Part of Sidekick v0.8.7 Agent Orchestration feature.
 *
 * ## Features
 *
 * - Execute workflows step by step
 * - Handle step branching and retries
 * - Process triggers and automation
 * - Track active and completed runs
 *
 * @since 0.8.7
 */
class WorkflowExecutor(
    private val projectPath: String = "",
    private val actionExecutor: (WorkflowAction, WorkflowStep, WorkflowContext) -> StepResult = { _, step, _ ->
        StepResult.success(step.id, step.action)
    }
) {
    private val workflows = ConcurrentHashMap<String, AgentWorkflow>()
    private val activeRuns = ConcurrentHashMap<String, WorkflowRun>()
    private val completedRuns = CopyOnWriteArrayList<WorkflowRun>()

    private val eventListeners = mutableListOf<(WorkflowEvent) -> Unit>()

    init {
        // Register built-in workflows
        BuiltInWorkflows.ALL.forEach { registerWorkflow(it) }
    }

    // =========================================================================
    // Workflow Registration
    // =========================================================================

    /**
     * Registers a workflow.
     */
    fun registerWorkflow(workflow: AgentWorkflow) {
        val validation = workflow.validate()
        if (!validation.valid) {
            throw IllegalArgumentException("Invalid workflow: ${validation.errors.joinToString()}")
        }
        workflows[workflow.id] = workflow
    }

    /**
     * Unregisters a workflow.
     */
    fun unregisterWorkflow(workflowId: String) {
        workflows.remove(workflowId)
    }

    /**
     * Gets a registered workflow.
     */
    fun getWorkflow(workflowId: String): AgentWorkflow? = workflows[workflowId]

    /**
     * Gets all registered workflows.
     */
    fun getAllWorkflows(): List<AgentWorkflow> = workflows.values.toList()

    /**
     * Gets workflows matching a trigger type.
     */
    fun getWorkflowsForTrigger(triggerType: TriggerType): List<AgentWorkflow> {
        return workflows.values.filter { workflow ->
            workflow.triggers.any { it.type == triggerType }
        }
    }

    // =========================================================================
    // Workflow Execution
    // =========================================================================

    /**
     * Starts a workflow execution.
     */
    fun startWorkflow(workflowId: String, variables: Map<String, String> = emptyMap()): WorkflowRun {
        val workflow = getWorkflow(workflowId)
            ?: throw IllegalArgumentException("Unknown workflow: $workflowId")

        val context = WorkflowContext.create(workflowId, projectPath).apply {
            variables.forEach { (k, v) -> setVariable(k, v) }
        }

        val run = WorkflowRun(
            workflowId = workflow.id,
            workflowName = workflow.name,
            status = WorkflowStatus.RUNNING,
            context = context,
            currentStep = workflow.firstStep?.id
        )

        activeRuns[run.id] = run
        emitEvent(WorkflowEvent.WorkflowStarted(run.id, workflow.id, workflow.name))

        return run
    }

    /**
     * Executes the next step in a workflow run.
     */
    fun executeNextStep(runId: String): StepResult? {
        val run = activeRuns[runId] ?: return null
        val workflow = getWorkflow(run.workflowId) ?: return null
        val stepId = run.currentStep ?: return null
        val step = workflow.getStep(stepId) ?: return null

        // Check condition
        if (step.condition != null && !step.condition.evaluate(run.context)) {
            // Skip step, move to next
            val nextStep = workflow.getNextStep(stepId, true)
            val updatedRun = if (nextStep != null) run.withStep(nextStep.id) else run
            activeRuns[runId] = updatedRun
            return StepResult.success(stepId, step.action, "Skipped due to condition")
        }

        // Check for user interaction
        if (step.requiresUserInteraction) {
            val updatedRun = run.withStatus(WorkflowStatus.WAITING_USER)
            activeRuns[runId] = updatedRun
            emitEvent(WorkflowEvent.UserInputRequired(
                runId,
                stepId,
                step.getString("prompt", "Please confirm to continue")
            ))
            return null
        }

        // Execute step
        emitEvent(WorkflowEvent.StepStarted(runId, stepId, step.action))

        val startTime = System.currentTimeMillis()
        val result = try {
            executeStep(step, run.context)
        } catch (e: Exception) {
            StepResult.failure(stepId, step.action, e.message ?: "Unknown error")
        }
        val durationMs = System.currentTimeMillis() - startTime

        val finalResult = result.copy(durationMs = durationMs)

        // Record result
        run.context.recordResult(stepId, finalResult)

        // Emit event
        emitEvent(WorkflowEvent.StepCompleted(runId, stepId, finalResult))

        // Determine next step
        val nextStep = workflow.getNextStep(stepId, finalResult.success)

        if (nextStep == null) {
            // Workflow complete
            val completedRun = run.withResult(finalResult).complete()
            activeRuns.remove(runId)
            completedRuns.add(completedRun)
            emitEvent(WorkflowEvent.WorkflowCompleted(
                runId,
                finalResult.success,
                completedRun.completedSteps.size,
                completedRun.durationMs
            ))
        } else {
            // Update run with next step
            val updatedRun = run
                .withResult(finalResult)
                .withStep(nextStep.id)
                .copy(context = run.context.copy(lastStepSuccess = finalResult.success))
            activeRuns[runId] = updatedRun
        }

        return finalResult
    }

    /**
     * Executes a single step.
     */
    private fun executeStep(step: WorkflowStep, context: WorkflowContext): StepResult {
        return actionExecutor(step.action, step, context)
    }

    /**
     * Executes all remaining steps until completion or user interaction.
     */
    fun executeUntilComplete(runId: String): WorkflowRun? {
        var run = activeRuns[runId] ?: return null
        val workflow = getWorkflow(run.workflowId) ?: return null

        var stepsExecuted = 0
        val maxSteps = workflow.config.maxSteps

        while (run.isActive && !run.status.equals(WorkflowStatus.WAITING_USER) && stepsExecuted < maxSteps) {
            val result = executeNextStep(runId)
            if (result == null) break // User interaction required or complete

            stepsExecuted++
            run = activeRuns[runId] ?: completedRuns.find { it.id == runId } ?: break
        }

        return activeRuns[runId] ?: completedRuns.find { it.id == runId }
    }

    /**
     * Continues a workflow after user input.
     */
    fun continueAfterUserInput(runId: String, proceed: Boolean): WorkflowRun? {
        val run = activeRuns[runId] ?: return null
        if (run.status != WorkflowStatus.WAITING_USER) return run

        val workflow = getWorkflow(run.workflowId) ?: return null
        val stepId = run.currentStep ?: return run
        val step = workflow.getStep(stepId) ?: return run

        // Create result based on user decision
        val result = if (proceed) {
            StepResult.success(stepId, step.action, "User confirmed")
        } else {
            StepResult.failure(stepId, step.action, "User cancelled")
        }

        run.context.recordResult(stepId, result)

        // Determine next step
        val nextStep = workflow.getNextStep(stepId, proceed)

        if (nextStep == null && !proceed) {
            // User cancelled, abort workflow
            val cancelledRun = run.copy(
                status = WorkflowStatus.CANCELLED,
                endTime = Instant.now()
            )
            activeRuns.remove(runId)
            completedRuns.add(cancelledRun)
            return cancelledRun
        }

        // Update run
        val updatedRun = run
            .withResult(result)
            .let { if (nextStep != null) it.withStep(nextStep.id) else it }
            .withStatus(if (nextStep != null) WorkflowStatus.RUNNING else WorkflowStatus.COMPLETED)
            .let { if (nextStep == null) it.complete() else it }

        if (nextStep == null) {
            activeRuns.remove(runId)
            completedRuns.add(updatedRun)
        } else {
            activeRuns[runId] = updatedRun
        }

        return updatedRun
    }

    /**
     * Pauses a running workflow.
     */
    fun pauseWorkflow(runId: String): WorkflowRun? {
        val run = activeRuns[runId] ?: return null
        if (run.status != WorkflowStatus.RUNNING) return run

        val pausedRun = run.withStatus(WorkflowStatus.PAUSED)
        activeRuns[runId] = pausedRun
        return pausedRun
    }

    /**
     * Resumes a paused workflow.
     */
    fun resumeWorkflow(runId: String): WorkflowRun? {
        val run = activeRuns[runId] ?: return null
        if (run.status != WorkflowStatus.PAUSED) return run

        val resumedRun = run.withStatus(WorkflowStatus.RUNNING)
        activeRuns[runId] = resumedRun
        return resumedRun
    }

    /**
     * Cancels a workflow.
     */
    fun cancelWorkflow(runId: String): WorkflowRun? {
        val run = activeRuns[runId] ?: return null

        val cancelledRun = run.copy(
            status = WorkflowStatus.CANCELLED,
            endTime = Instant.now()
        )

        activeRuns.remove(runId)
        completedRuns.add(cancelledRun)

        return cancelledRun
    }

    // =========================================================================
    // Trigger Processing
    // =========================================================================

    /**
     * Processes a trigger event.
     */
    fun processTrigger(event: TriggerEvent): List<WorkflowRun> {
        val matchingWorkflows = workflows.values.filter { workflow ->
            workflow.triggers.any { trigger -> trigger.matches(event) }
        }

        return matchingWorkflows.map { workflow ->
            startWorkflow(workflow.id)
        }
    }

    // =========================================================================
    // Run Management
    // =========================================================================

    /**
     * Gets an active run by ID.
     */
    fun getActiveRun(runId: String): WorkflowRun? = activeRuns[runId]

    /**
     * Gets all active runs.
     */
    fun getActiveRuns(): List<WorkflowRun> = activeRuns.values.toList()

    /**
     * Gets completed runs.
     */
    fun getCompletedRuns(limit: Int = 50): List<WorkflowRun> = completedRuns.takeLast(limit)

    /**
     * Gets runs for a workflow.
     */
    fun getRunsForWorkflow(workflowId: String): List<WorkflowRun> {
        val active = activeRuns.values.filter { it.workflowId == workflowId }
        val completed = completedRuns.filter { it.workflowId == workflowId }
        return active + completed
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets executor statistics.
     */
    fun getStats(): ExecutorStats {
        val allRuns = activeRuns.values + completedRuns

        return ExecutorStats(
            registeredWorkflows = workflows.size,
            activeRuns = activeRuns.size,
            completedRuns = completedRuns.size,
            successfulRuns = completedRuns.count { it.status == WorkflowStatus.COMPLETED },
            failedRuns = completedRuns.count { it.status == WorkflowStatus.FAILED },
            cancelledRuns = completedRuns.count { it.status == WorkflowStatus.CANCELLED },
            averageDurationMs = if (completedRuns.isNotEmpty())
                completedRuns.map { it.durationMs }.average().toLong()
            else 0
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (WorkflowEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (WorkflowEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: WorkflowEvent) {
        eventListeners.forEach { it(event) }
    }
}

/**
 * Executor statistics.
 */
data class ExecutorStats(
    val registeredWorkflows: Int,
    val activeRuns: Int,
    val completedRuns: Int,
    val successfulRuns: Int,
    val failedRuns: Int,
    val cancelledRuns: Int,
    val averageDurationMs: Long
) {
    val successRate: Float
        get() = if (completedRuns > 0) successfulRuns.toFloat() / completedRuns else 0f
}
