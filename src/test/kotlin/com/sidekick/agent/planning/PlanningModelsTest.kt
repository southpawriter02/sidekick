package com.sidekick.agent.planning

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Planning Models.
 */
@DisplayName("Planning Models Tests")
class PlanningModelsTest {

    // =========================================================================
    // TaskPlan Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskPlan")
    inner class TaskPlanTests {

        @Test
        @DisplayName("getStep returns step by ID")
        fun getStepReturnsStepById() {
            val plan = createTestPlan()
            val step = plan.getStep("2")
            assertEquals("Step 2", step?.title)
        }

        @Test
        @DisplayName("getDependencies returns dependent steps")
        fun getDependenciesReturnsDependentSteps() {
            val plan = createTestPlan()
            val deps = plan.getDependencies("2")
            assertEquals(1, deps.size)
            assertEquals("1", deps.first().id)
        }

        @Test
        @DisplayName("getDependents returns steps that depend on step")
        fun getDependentsReturnsStepsThatDependOnStep() {
            val plan = createTestPlan()
            val dependents = plan.getDependents("1")
            assertEquals(1, dependents.size)
            assertEquals("2", dependents.first().id)
        }

        @Test
        @DisplayName("getReadySteps returns steps with completed dependencies")
        fun getReadyStepsReturnsStepsWithCompletedDependencies() {
            var plan = createTestPlan()
            
            // Initially only step 1 is ready (no dependencies)
            assertEquals(1, plan.getReadySteps().size)
            assertEquals("1", plan.getReadySteps().first().id)

            // Complete step 1
            plan = plan.withStepStatus("1", StepStatus.COMPLETED)
            
            // Now step 2 should be ready
            val ready = plan.getReadySteps()
            assertEquals(1, ready.size)
            assertEquals("2", ready.first().id)
        }

        @Test
        @DisplayName("getParallelizableSteps groups by level")
        fun getParallelizableStepsGroupsByLevel() {
            val steps = listOf(
                PlanStep("1", 1, "A", "First", StepType.IMPLEMENT, verificationCriteria = "Pass"),
                PlanStep("2", 2, "B", "Second", StepType.IMPLEMENT, verificationCriteria = "Pass"),
                PlanStep("3", 3, "C", "Third", StepType.IMPLEMENT, verificationCriteria = "Pass")
            )
            val deps = mapOf("2" to listOf("1"), "3" to listOf("1"))

            val plan = TaskPlan(
                goal = "Test",
                analysis = ProblemAnalysis.simple(),
                strategy = PlanStrategy.default(),
                steps = steps,
                dependencies = deps,
                estimatedEffort = EffortEstimate.fromSteps(steps)
            )

            val levels = plan.getParallelizableSteps()
            assertEquals(2, levels.size)
            assertEquals(1, levels[0].size) // Step 1 alone
            assertEquals(2, levels[1].size) // Steps 2 and 3 can run in parallel
        }

        @Test
        @DisplayName("progress calculates correctly")
        fun progressCalculatesCorrectly() {
            var plan = createTestPlan()
            assertEquals(0f, plan.progress)

            plan = plan.withStepStatus("1", StepStatus.COMPLETED)
            assertEquals(0.5f, plan.progress)

            plan = plan.withStepStatus("2", StepStatus.COMPLETED)
            assertEquals(1f, plan.progress)
        }

        @Test
        @DisplayName("validate detects circular dependencies")
        fun validateDetectsCircularDependencies() {
            val steps = listOf(
                PlanStep("1", 1, "A", "First", StepType.IMPLEMENT, verificationCriteria = "Pass"),
                PlanStep("2", 2, "B", "Second", StepType.IMPLEMENT, verificationCriteria = "Pass")
            )
            val deps = mapOf("1" to listOf("2"), "2" to listOf("1"))

            val plan = TaskPlan(
                goal = "Test",
                analysis = ProblemAnalysis.simple(),
                strategy = PlanStrategy.default(),
                steps = steps,
                dependencies = deps,
                estimatedEffort = EffortEstimate.fromSteps(steps)
            )

            val validation = plan.validate()
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("Circular") })
        }

        @Test
        @DisplayName("validate detects unknown dependency references")
        fun validateDetectsUnknownDependencyReferences() {
            val steps = listOf(
                PlanStep("1", 1, "A", "First", StepType.IMPLEMENT, verificationCriteria = "Pass")
            )
            val deps = mapOf("1" to listOf("unknown"))

            val plan = TaskPlan(
                goal = "Test",
                analysis = ProblemAnalysis.simple(),
                strategy = PlanStrategy.default(),
                steps = steps,
                dependencies = deps,
                estimatedEffort = EffortEstimate.fromSteps(steps)
            )

            val validation = plan.validate()
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("unknown") })
        }

        @Test
        @DisplayName("linear factory creates valid plan")
        fun linearFactoryCreatesValidPlan() {
            val plan = TaskPlan.linear("Test goal", listOf("Step 1", "Step 2", "Step 3"))

            assertEquals(3, plan.steps.size)
            assertTrue(plan.validate().valid)
            assertEquals("1", plan.dependencies["2"]?.first())
            assertEquals("2", plan.dependencies["3"]?.first())
        }

        private fun createTestPlan(): TaskPlan {
            val steps = listOf(
                PlanStep("1", 1, "Step 1", "First step", StepType.IMPLEMENT, verificationCriteria = "Pass"),
                PlanStep("2", 2, "Step 2", "Second step", StepType.IMPLEMENT, verificationCriteria = "Pass")
            )
            val deps = mapOf("2" to listOf("1"))

            return TaskPlan(
                goal = "Test goal",
                analysis = ProblemAnalysis.simple(),
                strategy = PlanStrategy.default(),
                steps = steps,
                dependencies = deps,
                estimatedEffort = EffortEstimate.fromSteps(steps)
            )
        }
    }

    // =========================================================================
    // ProblemAnalysis Tests
    // =========================================================================

    @Nested
    @DisplayName("ProblemAnalysis")
    inner class ProblemAnalysisTests {

        @Test
        @DisplayName("difficultyScore combines scope and complexity")
        fun difficultyScoreCombinesScopeAndComplexity() {
            val simple = ProblemAnalysis(Scope.SINGLE_FILE, Complexity.SIMPLE)
            assertEquals(3, simple.difficultyScore)

            val complex = ProblemAnalysis(Scope.PROJECT_WIDE, Complexity.VERY_COMPLEX)
            assertEquals(10, complex.difficultyScore)
        }

        @Test
        @DisplayName("needsInvestigation checks unknowns")
        fun needsInvestigationChecksUnknowns() {
            val noUnknowns = ProblemAnalysis.simple()
            assertFalse(noUnknowns.needsInvestigation)

            val withUnknowns = ProblemAnalysis(
                scope = Scope.SINGLE_FILE,
                complexity = Complexity.MODERATE,
                unknowns = listOf("How does the API work?")
            )
            assertTrue(withUnknowns.needsInvestigation)
        }
    }

    // =========================================================================
    // PlanStrategy Tests
    // =========================================================================

    @Nested
    @DisplayName("PlanStrategy")
    inner class PlanStrategyTests {

        @Test
        @DisplayName("default uses incremental approach")
        fun defaultUsesIncremental() {
            val strategy = PlanStrategy.default()
            assertEquals(Approach.INCREMENTAL, strategy.approach)
        }

        @Test
        @DisplayName("testFirst uses TEST_FIRST approach")
        fun testFirstUsesTddApproach() {
            val strategy = PlanStrategy.testFirst()
            assertEquals(Approach.TEST_FIRST, strategy.approach)
        }

        @Test
        @DisplayName("all approaches have display names")
        fun allApproachesHaveDisplayNames() {
            Approach.entries.forEach { approach ->
                assertTrue(approach.displayName.isNotBlank())
                assertTrue(approach.description.isNotBlank())
            }
        }
    }

    // =========================================================================
    // PlanStep Tests
    // =========================================================================

    @Nested
    @DisplayName("PlanStep")
    inner class PlanStepTests {

        @Test
        @DisplayName("isActionable checks pending or blocked")
        fun isActionableChecksPendingOrBlocked() {
            val pending = createStep(StepStatus.PENDING)
            assertTrue(pending.isActionable)

            val blocked = createStep(StepStatus.BLOCKED)
            assertTrue(blocked.isActionable)

            val completed = createStep(StepStatus.COMPLETED)
            assertFalse(completed.isActionable)
        }

        @Test
        @DisplayName("isComplete checks completed or skipped")
        fun isCompleteChecksCompletedOrSkipped() {
            val completed = createStep(StepStatus.COMPLETED)
            assertTrue(completed.isComplete)

            val skipped = createStep(StepStatus.SKIPPED)
            assertTrue(skipped.isComplete)

            val pending = createStep(StepStatus.PENDING)
            assertFalse(pending.isComplete)
        }

        @Test
        @DisplayName("complete returns completed step")
        fun completeReturnsCompletedStep() {
            val step = createStep(StepStatus.PENDING)
            val completed = step.complete("Output")

            assertEquals(StepStatus.COMPLETED, completed.status)
            assertEquals("Output", completed.output)
        }

        @Test
        @DisplayName("fail returns failed step")
        fun failReturnsFailedStep() {
            val step = createStep(StepStatus.IN_PROGRESS)
            val failed = step.fail("Error message")

            assertEquals(StepStatus.FAILED, failed.status)
            assertEquals("Error message", failed.output)
        }

        private fun createStep(status: StepStatus): PlanStep = PlanStep(
            id = "1",
            order = 1,
            title = "Test Step",
            description = "A test step",
            type = StepType.IMPLEMENT,
            verificationCriteria = "Test passes",
            status = status
        )
    }

    // =========================================================================
    // StepType Tests
    // =========================================================================

    @Nested
    @DisplayName("StepType")
    inner class StepTypeTests {

        @Test
        @DisplayName("all types have display names and icons")
        fun allTypesHaveDisplayNamesAndIcons() {
            StepType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
                assertTrue(type.icon.isNotBlank())
            }
        }
    }

    // =========================================================================
    // EffortEstimate Tests
    // =========================================================================

    @Nested
    @DisplayName("EffortEstimate")
    inner class EffortEstimateTests {

        @Test
        @DisplayName("fromSteps calculates estimate")
        fun fromStepsCalculatesEstimate() {
            val steps = listOf(
                PlanStep("1", 1, "A", "First", StepType.IMPLEMENT, 
                    estimatedTokens = 2000, verificationCriteria = "Pass"),
                PlanStep("2", 2, "B", "Second", StepType.TEST, 
                    estimatedTokens = 1000, verificationCriteria = "Pass")
            )

            val estimate = EffortEstimate.fromSteps(steps)

            assertEquals(2, estimate.totalSteps)
            assertEquals(3000, estimate.tokenEstimate)
            assertEquals(1, estimate.breakdown[StepType.IMPLEMENT])
            assertEquals(1, estimate.breakdown[StepType.TEST])
        }

        @Test
        @DisplayName("timeString formats correctly")
        fun timeStringFormatsCorrectly() {
            val short = EffortEstimate(1, 30, 0.8f)
            assertEquals("30 minutes", short.timeString)

            val hour = EffortEstimate(5, 60, 0.8f)
            assertEquals("about 1 hour", hour.timeString)

            val long = EffortEstimate(10, 180, 0.7f)
            assertEquals("about 3 hours", long.timeString)
        }
    }

    // =========================================================================
    // PlanRisk Tests
    // =========================================================================

    @Nested
    @DisplayName("PlanRisk")
    inner class PlanRiskTests {

        @Test
        @DisplayName("score calculates probability times impact")
        fun scoreCalculatesProbabilityTimesImpact() {
            val risk = PlanRisk(
                description = "Test risk",
                probability = 0.5f,
                impact = Impact.HIGH,
                mitigation = "Mitigate"
            )

            assertEquals(1.5f, risk.score)
        }

        @Test
        @DisplayName("level categorizes by score")
        fun levelCategorizesByScore() {
            val low = PlanRisk.low("Low risk", "Mitigate")
            assertEquals(RiskLevel.LOW, low.level)

            val high = PlanRisk.high("High risk", "Mitigate")
            assertTrue(high.level in setOf(RiskLevel.MEDIUM, RiskLevel.HIGH))
        }
    }

    // =========================================================================
    // PlanEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("PlanEvent")
    inner class PlanEventTests {

        @Test
        @DisplayName("events have timestamps")
        fun eventsHaveTimestamps() {
            val created = PlanEvent.PlanCreated("p1", "Goal", 5)
            assertNotNull(created.timestamp)

            val completed = PlanEvent.PlanCompleted("p1", true, 5, 1000)
            assertNotNull(completed.timestamp)
        }
    }

    // =========================================================================
    // PlanningContext Tests
    // =========================================================================

    @Nested
    @DisplayName("PlanningContext")
    inner class PlanningContextTests {

        @Test
        @DisplayName("create sets basic fields")
        fun createSetsBasicFields() {
            val context = PlanningContext.create("MyProject", "/path/to/project")

            assertEquals("MyProject", context.projectName)
            assertEquals("/path/to/project", context.projectPath)
        }
    }
}
