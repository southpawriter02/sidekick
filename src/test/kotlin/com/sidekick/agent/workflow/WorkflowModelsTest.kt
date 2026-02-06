package com.sidekick.agent.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Workflow Models.
 */
@DisplayName("Workflow Models Tests")
class WorkflowModelsTest {

    // =========================================================================
    // AgentWorkflow Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentWorkflow")
    inner class AgentWorkflowTests {

        @Test
        @DisplayName("firstStep returns first step")
        fun firstStepReturnsFirstStep() {
            val workflow = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test workflow",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ANALYZE_CODE),
                    WorkflowStep("2", WorkflowAction.GENERATE_CODE)
                )
            )

            assertEquals("1", workflow.firstStep?.id)
        }

        @Test
        @DisplayName("getStep returns step by ID")
        fun getStepReturnsStepById() {
            val workflow = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test workflow",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ANALYZE_CODE),
                    WorkflowStep("2", WorkflowAction.GENERATE_CODE)
                )
            )

            val step = workflow.getStep("2")
            assertEquals(WorkflowAction.GENERATE_CODE, step?.action)
        }

        @Test
        @DisplayName("getNextStep follows success path")
        fun getNextStepFollowsSuccessPath() {
            val workflow = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test workflow",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ANALYZE_CODE, onSuccess = "2", onFailure = "3"),
                    WorkflowStep("2", WorkflowAction.GENERATE_CODE),
                    WorkflowStep("3", WorkflowAction.LOG)
                )
            )

            val next = workflow.getNextStep("1", success = true)
            assertEquals("2", next?.id)
        }

        @Test
        @DisplayName("getNextStep follows failure path")
        fun getNextStepFollowsFailurePath() {
            val workflow = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test workflow",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ANALYZE_CODE, onSuccess = "2", onFailure = "3"),
                    WorkflowStep("2", WorkflowAction.GENERATE_CODE),
                    WorkflowStep("3", WorkflowAction.LOG)
                )
            )

            val next = workflow.getNextStep("1", success = false)
            assertEquals("3", next?.id)
        }

        @Test
        @DisplayName("validate detects missing ID")
        fun validateDetectsMissingId() {
            val workflow = AgentWorkflow(
                id = "",
                name = "Test",
                description = "Test",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG))
            )

            val validation = workflow.validate()
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("ID") })
        }

        @Test
        @DisplayName("validate detects empty steps")
        fun validateDetectsEmptySteps() {
            val workflow = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test",
                steps = emptyList()
            )

            val validation = workflow.validate()
            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("validate detects invalid step references")
        fun validateDetectsInvalidStepReferences() {
            val workflow = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test",
                steps = listOf(
                    WorkflowStep("1", WorkflowAction.ANALYZE_CODE, onSuccess = "invalid")
                )
            )

            val validation = workflow.validate()
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("unknown step") })
        }

        @Test
        @DisplayName("linear factory creates simple workflow")
        fun linearFactoryCreatesSimpleWorkflow() {
            val workflow = AgentWorkflow.linear(
                "test",
                "Test",
                "Test workflow",
                listOf(WorkflowAction.ANALYZE_CODE, WorkflowAction.GENERATE_CODE, WorkflowAction.APPLY_CHANGES)
            )

            assertEquals(3, workflow.steps.size)
            assertEquals("2", workflow.steps[0].onSuccess)
            assertEquals("3", workflow.steps[1].onSuccess)
            assertNull(workflow.steps[2].onSuccess)
        }

        @Test
        @DisplayName("hasAutoTrigger detects non-manual triggers")
        fun hasAutoTriggerDetectsNonManualTriggers() {
            val manual = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG)),
                triggers = listOf(WorkflowTrigger(TriggerType.MANUAL))
            )
            assertFalse(manual.hasAutoTrigger)

            val auto = AgentWorkflow(
                id = "test",
                name = "Test",
                description = "Test",
                steps = listOf(WorkflowStep("1", WorkflowAction.LOG)),
                triggers = listOf(WorkflowTrigger(TriggerType.ERROR_DETECTED))
            )
            assertTrue(auto.hasAutoTrigger)
        }
    }

    // =========================================================================
    // WorkflowStep Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowStep")
    inner class WorkflowStepTests {

        @Test
        @DisplayName("isTerminal detects terminal step")
        fun isTerminalDetectsTerminalStep() {
            val terminal = WorkflowStep("1", WorkflowAction.LOG, onSuccess = null)
            assertTrue(terminal.isTerminal)

            val notTerminal = WorkflowStep("1", WorkflowAction.LOG, onSuccess = "2")
            assertFalse(notTerminal.isTerminal)
        }

        @Test
        @DisplayName("requiresUserInteraction detects ASK_USER")
        fun requiresUserInteractionDetectsAskUser() {
            val askUser = WorkflowStep("1", WorkflowAction.ASK_USER)
            assertTrue(askUser.requiresUserInteraction)

            val other = WorkflowStep("1", WorkflowAction.ANALYZE_CODE)
            assertFalse(other.requiresUserInteraction)
        }

        @Test
        @DisplayName("getString extracts config value")
        fun getStringExtractsConfigValue() {
            val step = WorkflowStep(
                id = "1",
                action = WorkflowAction.ANALYZE_CODE,
                config = mapOf("focus" to "error")
            )

            assertEquals("error", step.getString("focus"))
            assertEquals("default", step.getString("missing", "default"))
        }

        @Test
        @DisplayName("getBoolean extracts config value")
        fun getBooleanExtractsConfigValue() {
            val step = WorkflowStep(
                id = "1",
                action = WorkflowAction.ASK_USER,
                config = mapOf("confirm" to true)
            )

            assertTrue(step.getBoolean("confirm"))
            assertFalse(step.getBoolean("missing"))
        }
    }

    // =========================================================================
    // StepCondition Tests
    // =========================================================================

    @Nested
    @DisplayName("StepCondition")
    inner class StepConditionTests {

        @Test
        @DisplayName("VARIABLE_SET evaluates correctly")
        fun variableSetEvaluatesCorrectly() {
            val condition = StepCondition(ConditionType.VARIABLE_SET, "myVar")
            val context = WorkflowContext.create("test", "/project")

            assertFalse(condition.evaluate(context))

            context.setVariable("myVar", "value")
            assertTrue(condition.evaluate(context))
        }

        @Test
        @DisplayName("VARIABLE_EQUALS evaluates correctly")
        fun variableEqualsEvaluatesCorrectly() {
            val condition = StepCondition(ConditionType.VARIABLE_EQUALS, "mode=debug")
            val context = WorkflowContext.create("test", "/project")

            context.setVariable("mode", "release")
            assertFalse(condition.evaluate(context))

            context.setVariable("mode", "debug")
            assertTrue(condition.evaluate(context))
        }

        @Test
        @DisplayName("PREVIOUS_SUCCESS evaluates correctly")
        fun previousSuccessEvaluatesCorrectly() {
            val condition = StepCondition(ConditionType.PREVIOUS_SUCCESS, "")
            
            val successContext = WorkflowContext.create("test", "/project").copy(lastStepSuccess = true)
            assertTrue(condition.evaluate(successContext))

            val failContext = WorkflowContext.create("test", "/project").copy(lastStepSuccess = false)
            assertFalse(condition.evaluate(failContext))
        }
    }

    // =========================================================================
    // WorkflowAction Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowAction")
    inner class WorkflowActionTests {

        @Test
        @DisplayName("all actions have display names")
        fun allActionsHaveDisplayNames() {
            WorkflowAction.entries.forEach { action ->
                assertTrue(action.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("MODIFYING_ACTIONS contains correct actions")
        fun modifyingActionsContainsCorrect() {
            assertTrue(WorkflowAction.APPLY_CHANGES in WorkflowAction.MODIFYING_ACTIONS)
            assertTrue(WorkflowAction.CREATE_FILE in WorkflowAction.MODIFYING_ACTIONS)
            assertTrue(WorkflowAction.MODIFY_FILE in WorkflowAction.MODIFYING_ACTIONS)
            assertFalse(WorkflowAction.ANALYZE_CODE in WorkflowAction.MODIFYING_ACTIONS)
        }

        @Test
        @DisplayName("INTERACTIVE_ACTIONS contains ASK_USER")
        fun interactiveActionsContainsAskUser() {
            assertTrue(WorkflowAction.ASK_USER in WorkflowAction.INTERACTIVE_ACTIONS)
            assertFalse(WorkflowAction.GENERATE_CODE in WorkflowAction.INTERACTIVE_ACTIONS)
        }
    }

    // =========================================================================
    // WorkflowTrigger Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowTrigger")
    inner class WorkflowTriggerTests {

        @Test
        @DisplayName("matches checks type")
        fun matchesChecksType() {
            val trigger = WorkflowTrigger(TriggerType.FILE_SAVE)
            
            val saveEvent = TriggerEvent(TriggerType.FILE_SAVE)
            assertTrue(trigger.matches(saveEvent))

            val errorEvent = TriggerEvent(TriggerType.ERROR_DETECTED)
            assertFalse(trigger.matches(errorEvent))
        }

        @Test
        @DisplayName("matches with pattern")
        fun matchesWithPattern() {
            val trigger = WorkflowTrigger(TriggerType.FILE_SAVE, ".*\\.kt$")

            val kotlinFile = TriggerEvent(TriggerType.FILE_SAVE, "Main.kt")
            assertTrue(trigger.matches(kotlinFile))

            val javaFile = TriggerEvent(TriggerType.FILE_SAVE, "Main.java")
            assertFalse(trigger.matches(javaFile))
        }

        @Test
        @DisplayName("matches command exactly")
        fun matchesCommandExactly() {
            val trigger = WorkflowTrigger(TriggerType.COMMAND, "fix-error")

            val matchingEvent = TriggerEvent(TriggerType.COMMAND, "fix-error")
            assertTrue(trigger.matches(matchingEvent))

            val differentEvent = TriggerEvent(TriggerType.COMMAND, "refactor")
            assertFalse(trigger.matches(differentEvent))
        }
    }

    // =========================================================================
    // WorkflowContext Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowContext")
    inner class WorkflowContextTests {

        @Test
        @DisplayName("setVariable and getVariable work")
        fun setAndGetVariableWork() {
            val context = WorkflowContext.create("test", "/project")

            context.setVariable("name", "value")
            assertEquals("value", context.getVariable("name"))
            assertNull(context.getVariable("missing"))
        }

        @Test
        @DisplayName("recordResult and getResult work")
        fun recordAndGetResultWork() {
            val context = WorkflowContext.create("test", "/project")
            val result = StepResult.success("1", WorkflowAction.LOG)

            context.recordResult("1", result)
            assertEquals(result, context.getResult("1"))
        }

        @Test
        @DisplayName("elapsedMs calculates correctly")
        fun elapsedMsCalculatesCorrectly() {
            val past = Instant.now().minusSeconds(60)
            val context = WorkflowContext(
                workflowId = "test",
                projectPath = "/project",
                startTime = past
            )

            assertTrue(context.elapsedMs() >= 60000)
        }

        @Test
        @DisplayName("completedSteps counts results")
        fun completedStepsCountsResults() {
            val context = WorkflowContext.create("test", "/project")
            assertEquals(0, context.completedSteps)

            context.recordResult("1", StepResult.success("1", WorkflowAction.LOG))
            context.recordResult("2", StepResult.success("2", WorkflowAction.LOG))
            assertEquals(2, context.completedSteps)
        }
    }

    // =========================================================================
    // StepResult Tests
    // =========================================================================

    @Nested
    @DisplayName("StepResult")
    inner class StepResultTests {

        @Test
        @DisplayName("success creates success result")
        fun successCreatesSuccessResult() {
            val result = StepResult.success("1", WorkflowAction.ANALYZE_CODE, "Done")

            assertTrue(result.success)
            assertEquals("1", result.stepId)
            assertEquals("Done", result.output)
        }

        @Test
        @DisplayName("failure creates failure result")
        fun failureCreatesFailureResult() {
            val result = StepResult.failure("1", WorkflowAction.RUN_TESTS, "Tests failed")

            assertFalse(result.success)
            assertEquals("Tests failed", result.error)
        }
    }

    // =========================================================================
    // WorkflowRun Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowRun")
    inner class WorkflowRunTests {

        @Test
        @DisplayName("isActive checks status")
        fun isActiveChecksStatus() {
            val context = WorkflowContext.create("test", "/project")

            val running = WorkflowRun(
                workflowId = "test",
                workflowName = "Test",
                status = WorkflowStatus.RUNNING,
                context = context
            )
            assertTrue(running.isActive)

            val completed = running.complete()
            assertFalse(completed.isActive)
        }

        @Test
        @DisplayName("withStatus changes status")
        fun withStatusChangesStatus() {
            val context = WorkflowContext.create("test", "/project")
            val run = WorkflowRun(
                workflowId = "test",
                workflowName = "Test",
                status = WorkflowStatus.RUNNING,
                context = context
            )

            val paused = run.withStatus(WorkflowStatus.PAUSED)
            assertEquals(WorkflowStatus.PAUSED, paused.status)
        }

        @Test
        @DisplayName("fail sets error")
        fun failSetsError() {
            val context = WorkflowContext.create("test", "/project")
            val run = WorkflowRun(
                workflowId = "test",
                workflowName = "Test",
                status = WorkflowStatus.RUNNING,
                context = context
            )

            val failed = run.fail("Something went wrong")
            assertEquals(WorkflowStatus.FAILED, failed.status)
            assertEquals("Something went wrong", failed.error)
        }
    }

    // =========================================================================
    // BuiltInWorkflows Tests
    // =========================================================================

    @Nested
    @DisplayName("BuiltInWorkflows")
    inner class BuiltInWorkflowsTests {

        @Test
        @DisplayName("FIX_ERROR is valid")
        fun fixErrorIsValid() {
            val validation = BuiltInWorkflows.FIX_ERROR.validate()
            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("IMPLEMENT_FEATURE is valid")
        fun implementFeatureIsValid() {
            val validation = BuiltInWorkflows.IMPLEMENT_FEATURE.validate()
            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("ALL contains expected workflows")
        fun allContainsExpectedWorkflows() {
            val ids = BuiltInWorkflows.ALL.map { it.id }

            assertTrue("fix-error" in ids)
            assertTrue("implement-feature" in ids)
            assertTrue("refactor-code" in ids)
            assertTrue("add-tests" in ids)
            assertTrue("code-review" in ids)
            assertTrue("document-code" in ids)
        }

        @Test
        @DisplayName("get returns workflow by ID")
        fun getReturnsWorkflowById() {
            val workflow = BuiltInWorkflows.get("fix-error")
            assertNotNull(workflow)
            assertEquals("Fix Error", workflow?.name)
        }

        @Test
        @DisplayName("getByTrigger filters correctly")
        fun getByTriggerFiltersCorrectly() {
            val errorTriggered = BuiltInWorkflows.getByTrigger(TriggerType.ERROR_DETECTED)
            assertTrue(errorTriggered.any { it.id == "fix-error" })
        }
    }
}
