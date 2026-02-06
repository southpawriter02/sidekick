package com.sidekick.agent.planning

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Planning Service.
 */
@DisplayName("Planning Service Tests")
class PlanningServiceTest {

    private lateinit var service: PlanningService

    @BeforeEach
    fun setUp() {
        service = PlanningService("/test/project")
    }

    // =========================================================================
    // Plan Creation Tests
    // =========================================================================

    @Nested
    @DisplayName("Plan Creation")
    inner class PlanCreationTests {

        @Test
        @DisplayName("createSimplePlan creates linear plan")
        fun createSimplePlanCreatesLinearPlan() {
            val plan = service.createSimplePlan(
                "Test goal",
                listOf("Analyze", "Implement", "Verify")
            )

            assertEquals("Test goal", plan.goal)
            assertEquals(3, plan.steps.size)
            assertEquals(PlanStatus.DRAFT, plan.status)
        }

        @Test
        @DisplayName("createDetailedPlan validates plan")
        fun createDetailedPlanValidatesPlan() {
            val steps = listOf(
                PlanStep("1", 1, "Step 1", "First", StepType.IMPLEMENT, verificationCriteria = "Pass")
            )

            val plan = service.createDetailedPlan(
                goal = "Test goal",
                analysis = ProblemAnalysis.simple(),
                strategy = PlanStrategy.default(),
                steps = steps
            )

            assertNotNull(plan)
            assertTrue(plan.validate().valid)
        }

        @Test
        @DisplayName("createDetailedPlan rejects invalid plan")
        fun createDetailedPlanRejectsInvalidPlan() {
            val steps = listOf(
                PlanStep("1", 1, "Step 1", "First", StepType.IMPLEMENT, verificationCriteria = "Pass")
            )
            val deps = mapOf("1" to listOf("nonexistent"))

            assertThrows(IllegalArgumentException::class.java) {
                service.createDetailedPlan(
                    goal = "Test",
                    analysis = ProblemAnalysis.simple(),
                    strategy = PlanStrategy.default(),
                    steps = steps,
                    dependencies = deps
                )
            }
        }

        @Test
        @DisplayName("createPlan uses LLM planner")
        fun createPlanUsesLlmPlanner() = runBlocking {
            val context = PlanningContext.create("Test", "/project")
            val plan = service.createPlan("Build a feature", context)

            assertNotNull(plan)
            assertEquals("Build a feature", plan.goal)
        }
    }

    // =========================================================================
    // Plan Retrieval Tests
    // =========================================================================

    @Nested
    @DisplayName("Plan Retrieval")
    inner class PlanRetrievalTests {

        @Test
        @DisplayName("getPlan returns plan by ID")
        fun getPlanReturnsPlanById() {
            val created = service.createSimplePlan("Test", listOf("Step"))
            val retrieved = service.getPlan(created.id)

            assertEquals(created.id, retrieved?.id)
        }

        @Test
        @DisplayName("getAllPlans returns all plans")
        fun getAllPlansReturnsAllPlans() {
            service.createSimplePlan("Plan 1", listOf("Step"))
            service.createSimplePlan("Plan 2", listOf("Step"))

            val all = service.getAllPlans()
            assertEquals(2, all.size)
        }

        @Test
        @DisplayName("getPlansByStatus filters correctly")
        fun getPlansByStatusFiltersCorrectly() {
            val plan1 = service.createSimplePlan("Plan 1", listOf("Step"))
            service.createSimplePlan("Plan 2", listOf("Step"))
            service.approvePlan(plan1.id)

            val approved = service.getPlansByStatus(PlanStatus.APPROVED)
            assertEquals(1, approved.size)
            assertEquals(plan1.id, approved.first().id)
        }
    }

    // =========================================================================
    // Plan Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Plan Lifecycle")
    inner class PlanLifecycleTests {

        @Test
        @DisplayName("approvePlan changes status to APPROVED")
        fun approvePlanChangesStatusToApproved() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            val approved = service.approvePlan(plan.id)

            assertEquals(PlanStatus.APPROVED, approved?.status)
        }

        @Test
        @DisplayName("startPlan changes status to IN_PROGRESS")
        fun startPlanChangesStatusToInProgress() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            service.approvePlan(plan.id)
            val started = service.startPlan(plan.id)

            assertEquals(PlanStatus.IN_PROGRESS, started?.status)
        }

        @Test
        @DisplayName("startPlan sets active plan")
        fun startPlanSetsActivePlan() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            service.startPlan(plan.id)

            val active = service.getActivePlan()
            assertEquals(plan.id, active?.id)
        }

        @Test
        @DisplayName("cancelPlan changes status to CANCELLED")
        fun cancelPlanChangesStatusToCancelled() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            val cancelled = service.cancelPlan(plan.id)

            assertEquals(PlanStatus.CANCELLED, cancelled?.status)
        }
    }

    // =========================================================================
    // Step Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Step Execution")
    inner class StepExecutionTests {

        @Test
        @DisplayName("startNextStep returns ready step")
        fun startNextStepReturnsReadyStep() {
            val plan = service.createSimplePlan("Test", listOf("Step 1", "Step 2"))
            service.startPlan(plan.id)

            val step = service.startNextStep(plan.id)
            assertEquals(StepStatus.IN_PROGRESS, step?.status)
        }

        @Test
        @DisplayName("completeStep updates step status")
        fun completeStepUpdatesStepStatus() {
            val plan = service.createSimplePlan("Test", listOf("Step 1"))
            service.startPlan(plan.id)
            service.startNextStep(plan.id)

            val updated = service.completeStep(plan.id, "1", "Done")
            val step = updated?.getStep("1")

            assertEquals(StepStatus.COMPLETED, step?.status)
        }

        @Test
        @DisplayName("completeStep marks plan complete when all steps done")
        fun completeStepMarksPlanCompleteWhenAllStepsDone() {
            val plan = service.createSimplePlan("Test", listOf("Only step"))
            service.startPlan(plan.id)
            service.startNextStep(plan.id)

            val updated = service.completeStep(plan.id, "1")

            assertEquals(PlanStatus.COMPLETED, updated?.status)
        }

        @Test
        @DisplayName("failStep marks plan as failed")
        fun failStepMarksPlanAsFailed() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            service.startPlan(plan.id)
            service.startNextStep(plan.id)

            val updated = service.failStep(plan.id, "1", "Error occurred")

            assertEquals(PlanStatus.FAILED, updated?.status)
        }

        @Test
        @DisplayName("skipStep skips the step")
        fun skipStepSkipsTheStep() {
            val plan = service.createSimplePlan("Test", listOf("Step 1", "Step 2"))
            service.startPlan(plan.id)

            val updated = service.skipStep(plan.id, "1")
            val step = updated?.getStep("1")

            assertEquals(StepStatus.SKIPPED, step?.status)
        }
    }

    // =========================================================================
    // Plan Modification Tests
    // =========================================================================

    @Nested
    @DisplayName("Plan Modification")
    inner class PlanModificationTests {

        @Test
        @DisplayName("addStep adds step to plan")
        fun addStepAddsStepToPlan() {
            val plan = service.createSimplePlan("Test", listOf("Step 1"))
            val newStep = PlanStep(
                id = "new",
                order = 2,
                title = "New Step",
                description = "Added step",
                type = StepType.TEST,
                verificationCriteria = "Pass"
            )

            val updated = service.addStep(plan.id, newStep)

            assertEquals(2, updated?.steps?.size)
            assertTrue(updated?.steps?.any { it.title == "New Step" } == true)
        }

        @Test
        @DisplayName("removeStep removes step from plan")
        fun removeStepRemovesStepFromPlan() {
            val plan = service.createSimplePlan("Test", listOf("Step 1", "Step 2"))
            val updated = service.removeStep(plan.id, "1")

            assertEquals(1, updated?.steps?.size)
            assertEquals("Step 2", updated?.steps?.first()?.description)
        }

        @Test
        @DisplayName("removeStep updates dependencies")
        fun removeStepUpdatesDependencies() {
            val plan = service.createSimplePlan("Test", listOf("Step 1", "Step 2", "Step 3"))
            val updated = service.removeStep(plan.id, "2")

            // Step 3 should no longer depend on step 2
            assertFalse(updated?.dependencies?.values?.flatten()?.contains("2") == true)
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("validatePlan returns validation result")
        fun validatePlanReturnsValidationResult() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            val validation = service.validatePlan(plan.id)

            assertNotNull(validation)
            assertTrue(validation!!.valid)
        }

        @Test
        @DisplayName("isExecutable checks plan validity and status")
        fun isExecutableChecksPlanValidityAndStatus() {
            val plan = service.createSimplePlan("Test", listOf("Step"))
            assertTrue(service.isExecutable(plan.id))

            service.startPlan(plan.id)
            service.startNextStep(plan.id)
            service.completeStep(plan.id, "1")

            assertFalse(service.isExecutable(plan.id)) // Already completed
        }
    }

    // =========================================================================
    // Analysis Tests
    // =========================================================================

    @Nested
    @DisplayName("Analysis")
    inner class AnalysisTests {

        @Test
        @DisplayName("analyzeGoal detects project-wide scope")
        fun analyzeGoalDetectsProjectWideScope() {
            val analysis = service.analyzeGoal("Refactor the entire project for consistency")
            assertEquals(Scope.PROJECT_WIDE, analysis.scope)
        }

        @Test
        @DisplayName("analyzeGoal detects complex tasks")
        fun analyzeGoalDetectsComplexTasks() {
            val analysis = service.analyzeGoal("Major redesign of the auth module")
            assertEquals(Complexity.COMPLEX, analysis.complexity)
        }

        @Test
        @DisplayName("suggestStrategy recommends incremental for large scope")
        fun suggestStrategyRecommendsIncrementalForLargeScope() {
            val analysis = ProblemAnalysis(Scope.PROJECT_WIDE, Complexity.MODERATE)
            val strategy = service.suggestStrategy(analysis)

            assertEquals(Approach.INCREMENTAL, strategy.approach)
        }

        @Test
        @DisplayName("suggestStrategy recommends spike for unknowns")
        fun suggestStrategyRecommendsSpikeForUnknowns() {
            val analysis = ProblemAnalysis(
                scope = Scope.MODULE,
                complexity = Complexity.COMPLEX,
                unknowns = listOf("How does the API work?")
            )
            val strategy = service.suggestStrategy(analysis)

            assertEquals(Approach.SPIKE_FIRST, strategy.approach)
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
            val plan1 = service.createSimplePlan("Plan 1", listOf("Step"))
            service.createSimplePlan("Plan 2", listOf("Step", "Step"))

            service.startPlan(plan1.id)
            service.startNextStep(plan1.id)
            service.completeStep(plan1.id, "1")

            val stats = service.getStats()

            assertEquals(2, stats.totalPlans)
            assertEquals(1, stats.completedPlans)
            assertEquals(1, stats.draftPlans)
        }

        @Test
        @DisplayName("successRate calculates correctly")
        fun successRateCalculatesCorrectly() {
            val plan1 = service.createSimplePlan("Plan 1", listOf("Step"))
            val plan2 = service.createSimplePlan("Plan 2", listOf("Step"))

            service.startPlan(plan1.id)
            service.startNextStep(plan1.id)
            service.completeStep(plan1.id, "1") // Completed

            service.startPlan(plan2.id)
            service.startNextStep(plan2.id)
            service.failStep(plan2.id, "1", "Error") // Failed

            val stats = service.getStats()
            assertEquals(0.5f, stats.successRate)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits plan created event")
        fun emitsPlanCreatedEvent() {
            var received: PlanEvent? = null
            service.addListener { event -> received = event }

            service.createSimplePlan("Test", listOf("Step"))

            assertTrue(received is PlanEvent.PlanCreated)
        }

        @Test
        @DisplayName("emits step events during execution")
        fun emitsStepEventsDuringExecution() {
            val events = mutableListOf<PlanEvent>()
            service.addListener { events.add(it) }

            val plan = service.createSimplePlan("Test", listOf("Step"))
            service.startPlan(plan.id)
            service.startNextStep(plan.id)
            service.completeStep(plan.id, "1")

            assertTrue(events.any { it is PlanEvent.StepStarted })
            assertTrue(events.any { it is PlanEvent.StepCompleted })
            assertTrue(events.any { it is PlanEvent.PlanCompleted })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() {
            var count = 0
            val listener: (PlanEvent) -> Unit = { count++ }
            service.addListener(listener)

            service.createSimplePlan("Test 1", listOf("Step"))
            val firstCount = count

            service.removeListener(listener)
            service.createSimplePlan("Test 2", listOf("Step"))

            assertEquals(firstCount, count)
        }
    }

    // =========================================================================
    // Cleanup Tests
    // =========================================================================

    @Nested
    @DisplayName("Cleanup")
    inner class CleanupTests {

        @Test
        @DisplayName("clearPlans removes all plans")
        fun clearPlansRemovesAllPlans() {
            service.createSimplePlan("Plan 1", listOf("Step"))
            service.createSimplePlan("Plan 2", listOf("Step"))

            service.clearPlans()

            assertEquals(0, service.getAllPlans().size)
        }
    }
}
