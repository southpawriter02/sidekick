package com.sidekick.agent.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Workflow Executor.
 */
@DisplayName("Workflow Executor Tests")
class WorkflowExecutorTest {

    private lateinit var executor: WorkflowExecutor

    @BeforeEach
    fun setUp() {
        executor = WorkflowExecutor("/test/project")
    }

    // =========================================================================
    // Registration Tests
    // =========================================================================

    @Nested
    @DisplayName("Workflow Registration")
    inner class RegistrationTests {

        @Test
        @DisplayName("registers built-in workflows")
        fun registersBuiltInWorkflows() {
            val workflow = executor.getWorkflow("fix-error")
            assertNotNull(workflow)
        }

        @Test
        @DisplayName("registerWorkflow adds workflow")
        fun registerWorkflowAddsWorkflow() {
            val custom = AgentWorkflow(
                id = "custom-workflow",
                name = "Custom",
                description = "Custom workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )

            executor.registerWorkflow(custom)
            val retrieved = executor.getWorkflow("custom-workflow")

            assertNotNull(retrieved)
            assertEquals("Custom", retrieved?.name)
        }

        @Test
        @DisplayName("registerWorkflow rejects invalid workflow")
        fun registerWorkflowRejectsInvalid() {
            val invalid = AgentWorkflow(
                id = "",
                name = "Invalid",
                description = "Invalid workflow",
                steps = emptyList()
            )

            assertThrows(IllegalArgumentException::class.java) {
                executor.registerWorkflow(invalid)
            }
        }

        @Test
        @DisplayName("unregisterWorkflow removes workflow")
        fun unregisterWorkflowRemovesWorkflow() {
            executor.unregisterWorkflow("fix-error")
            assertNull(executor.getWorkflow("fix-error"))
        }

        @Test
        @DisplayName("getAllWorkflows returns all")
        fun getAllWorkflowsReturnsAll() {
            val all = executor.getAllWorkflows()
            assertTrue(all.size >= 6) // Built-in workflows
        }

        @Test
        @DisplayName("getWorkflowsForTrigger filters correctly")
        fun getWorkflowsForTriggerFiltersCorrectly() {
            val errorWorkflows = executor.getWorkflowsForTrigger(TriggerType.ERROR_DETECTED)
            assertTrue(errorWorkflows.any { it.id == "fix-error" })
        }
    }

    // =========================================================================
    // Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Workflow Execution")
    inner class ExecutionTests {

        @Test
        @DisplayName("startWorkflow creates active run")
        fun startWorkflowCreatesActiveRun() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")

            assertEquals(WorkflowStatus.RUNNING, run.status)
            assertEquals("simple", run.workflowId)
            assertEquals("1", run.currentStep)
        }

        @Test
        @DisplayName("startWorkflow throws for unknown workflow")
        fun startWorkflowThrowsForUnknown() {
            assertThrows(IllegalArgumentException::class.java) {
                executor.startWorkflow("nonexistent")
            }
        }

        @Test
        @DisplayName("startWorkflow sets initial variables")
        fun startWorkflowSetsInitialVariables() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple", mapOf("mode" to "debug"))

            assertEquals("debug", run.context.getVariable("mode"))
        }

        @Test
        @DisplayName("executeNextStep executes step")
        fun executeNextStepExecutesStep() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            val result = executor.executeNextStep(run.id)

            assertNotNull(result)
            assertTrue(result!!.success)
        }

        @Test
        @DisplayName("executeNextStep advances to next step")
        fun executeNextStepAdvancesToNextStep() {
            val twoStepWorkflow = AgentWorkflow(
                id = "two-step",
                name = "Two Step",
                description = "Two step workflow",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.LOG, onSuccess = "2"),
                    WorkflowStep("2", WorkflowAction.LOG)
                )
            )
            executor.registerWorkflow(twoStepWorkflow)

            val run = executor.startWorkflow("two-step")
            executor.executeNextStep(run.id)

            val activeRun = executor.getActiveRun(run.id)
            assertEquals("2", activeRun?.currentStep)
        }

        @Test
        @DisplayName("executeNextStep completes workflow on terminal step")
        fun executeNextStepCompletesWorkflowOnTerminalStep() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            executor.executeNextStep(run.id)

            assertNull(executor.getActiveRun(run.id))
            val completed = executor.getCompletedRuns().find { it.id == run.id }
            assertEquals(WorkflowStatus.COMPLETED, completed?.status)
        }

        @Test
        @DisplayName("executeNextStep handles user interaction")
        fun executeNextStepHandlesUserInteraction() {
            val askWorkflow = AgentWorkflow(
                id = "ask-workflow",
                name = "Ask Workflow",
                description = "Workflow with user interaction",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ASK_USER, mapOf("prompt" to "Continue?"))
                )
            )
            executor.registerWorkflow(askWorkflow)

            val run = executor.startWorkflow("ask-workflow")
            val result = executor.executeNextStep(run.id)

            assertNull(result) // No result when waiting for user
            val activeRun = executor.getActiveRun(run.id)
            assertEquals(WorkflowStatus.WAITING_USER, activeRun?.status)
        }
    }

    // =========================================================================
    // User Interaction Tests
    // =========================================================================

    @Nested
    @DisplayName("User Interaction")
    inner class UserInteractionTests {

        @Test
        @DisplayName("continueAfterUserInput proceeds on confirm")
        fun continueAfterUserInputProceedsOnConfirm() {
            val askWorkflow = AgentWorkflow(
                id = "ask-workflow",
                name = "Ask Workflow",
                description = "Workflow with user interaction",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ASK_USER, onSuccess = "2"),
                    WorkflowStep("2", WorkflowAction.LOG)
                )
            )
            executor.registerWorkflow(askWorkflow)

            val run = executor.startWorkflow("ask-workflow")
            executor.executeNextStep(run.id) // Enters WAITING_USER

            val updatedRun = executor.continueAfterUserInput(run.id, proceed = true)

            assertEquals(WorkflowStatus.RUNNING, updatedRun?.status)
            assertEquals("2", updatedRun?.currentStep)
        }

        @Test
        @DisplayName("continueAfterUserInput cancels on reject")
        fun continueAfterUserInputCancelsOnReject() {
            val askWorkflow = AgentWorkflow(
                id = "ask-workflow",
                name = "Ask Workflow",
                description = "Workflow with user interaction",
                steps = listOf(WorkflowStep("1", WorkflowAction.ASK_USER))
            )
            executor.registerWorkflow(askWorkflow)

            val run = executor.startWorkflow("ask-workflow")
            executor.executeNextStep(run.id)

            val updatedRun = executor.continueAfterUserInput(run.id, proceed = false)

            assertEquals(WorkflowStatus.CANCELLED, updatedRun?.status)
        }
    }

    // =========================================================================
    // Control Tests
    // =========================================================================

    @Nested
    @DisplayName("Workflow Control")
    inner class ControlTests {

        @Test
        @DisplayName("pauseWorkflow pauses running workflow")
        fun pauseWorkflowPausesRunning() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.LOG, onSuccess = "2"),
                    WorkflowStep("2", WorkflowAction.LOG)
                )
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            val paused = executor.pauseWorkflow(run.id)

            assertEquals(WorkflowStatus.PAUSED, paused?.status)
        }

        @Test
        @DisplayName("resumeWorkflow resumes paused workflow")
        fun resumeWorkflowResumesPaused() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG, onSuccess = "2"))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            executor.pauseWorkflow(run.id)
            val resumed = executor.resumeWorkflow(run.id)

            assertEquals(WorkflowStatus.RUNNING, resumed?.status)
        }

        @Test
        @DisplayName("cancelWorkflow cancels running workflow")
        fun cancelWorkflowCancelsRunning() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            val cancelled = executor.cancelWorkflow(run.id)

            assertEquals(WorkflowStatus.CANCELLED, cancelled?.status)
            assertNull(executor.getActiveRun(run.id))
        }
    }

    // =========================================================================
    // Trigger Tests
    // =========================================================================

    @Nested
    @DisplayName("Trigger Processing")
    inner class TriggerTests {

        @Test
        @DisplayName("processTrigger starts matching workflows")
        fun processTriggerStartsMatchingWorkflows() {
            val event = TriggerEvent(TriggerType.ERROR_DETECTED, "NullPointerException")
            val runs = executor.processTrigger(event)

            assertTrue(runs.isNotEmpty())
            assertTrue(runs.any { it.workflowId == "fix-error" })
        }

        @Test
        @DisplayName("processTrigger returns empty for no matches")
        fun processTriggerReturnsEmptyForNoMatches() {
            val event = TriggerEvent(TriggerType.WEBHOOK, "unknown")
            val runs = executor.processTrigger(event)

            assertTrue(runs.isEmpty())
        }
    }

    // =========================================================================
    // Run Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Run Management")
    inner class RunManagementTests {

        @Test
        @DisplayName("getActiveRuns returns running workflows")
        fun getActiveRunsReturnsRunning() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG, onSuccess = "2"))
            )
            executor.registerWorkflow(simpleWorkflow)

            executor.startWorkflow("simple")
            executor.startWorkflow("simple")

            val active = executor.getActiveRuns()
            assertEquals(2, active.size)
        }

        @Test
        @DisplayName("getCompletedRuns returns finished workflows")
        fun getCompletedRunsReturnsFinished() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            executor.executeNextStep(run.id)

            val completed = executor.getCompletedRuns()
            assertTrue(completed.any { it.id == run.id })
        }

        @Test
        @DisplayName("getRunsForWorkflow returns workflow runs")
        fun getRunsForWorkflowReturnsWorkflowRuns() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            executor.startWorkflow("simple")
            executor.startWorkflow("simple")

            val runs = executor.getRunsForWorkflow("simple")
            assertEquals(2, runs.size)
        }
    }

    // =========================================================================
    // Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Statistics")
    inner class StatisticsTests {

        @Test
        @DisplayName("getStats returns correct counts")
        fun getStatsReturnsCorrectCounts() {
            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            // Run and complete a workflow
            val run = executor.startWorkflow("simple")
            executor.executeNextStep(run.id)

            val stats = executor.getStats()

            assertTrue(stats.registeredWorkflows >= 6)
            assertEquals(1, stats.completedRuns)
            assertEquals(1, stats.successfulRuns)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits workflow started event")
        fun emitsWorkflowStartedEvent() {
            var received: WorkflowEvent? = null
            executor.addListener { event -> received = event }

            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            executor.startWorkflow("simple")

            assertTrue(received is WorkflowEvent.WorkflowStarted)
        }

        @Test
        @DisplayName("emits step events during execution")
        fun emitsStepEventsDuringExecution() {
            val events = mutableListOf<WorkflowEvent>()
            executor.addListener { events.add(it) }

            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            val run = executor.startWorkflow("simple")
            executor.executeNextStep(run.id)

            assertTrue(events.any { it is WorkflowEvent.StepStarted })
            assertTrue(events.any { it is WorkflowEvent.StepCompleted })
            assertTrue(events.any { it is WorkflowEvent.WorkflowCompleted })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() {
            var count = 0
            val listener: (WorkflowEvent) -> Unit = { count++ }
            executor.addListener(listener)

            val simpleWorkflow = AgentWorkflow(
                id = "simple",
                name = "Simple",
                description = "Simple workflow",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )
            executor.registerWorkflow(simpleWorkflow)

            executor.startWorkflow("simple")
            val firstCount = count

            executor.removeListener(listener)
            executor.startWorkflow("simple")

            assertEquals(firstCount, count)
        }
    }
}
