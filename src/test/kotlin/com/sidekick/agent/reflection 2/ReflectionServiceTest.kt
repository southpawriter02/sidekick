package com.sidekick.agent.reflection

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Reflection Service.
 */
@DisplayName("Reflection Service Tests")
class ReflectionServiceTest {

    private lateinit var service: ReflectionService

    @BeforeEach
    fun setUp() {
        service = ReflectionService(
            refiner = { original, critiques, _ ->
                // Simple refiner that "fixes" based on critiques
                if (critiques.any { it.isCritical }) {
                    original.replace("broken", "fixed")
                } else {
                    original
                }
            }
        )
    }

    // =========================================================================
    // Session Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Session Management")
    inner class SessionManagementTests {

        @Test
        @DisplayName("createSession creates new session")
        fun createSessionCreatesNewSession() {
            val session = service.createSession("task1")

            assertNotNull(session)
            assertEquals("task1", session.taskId)
            assertEquals(ReflectionSession.SessionStatus.ACTIVE, session.status)
        }

        @Test
        @DisplayName("getSession retrieves by ID")
        fun getSessionRetrievesById() {
            val created = service.createSession("task1")
            val retrieved = service.getSession(created.id)

            assertEquals(created.id, retrieved?.id)
        }

        @Test
        @DisplayName("endSession completes session")
        fun endSessionCompletesSession() {
            val session = service.createSession("task1")
            val ended = service.endSession(session.id, success = true)

            assertEquals(ReflectionSession.SessionStatus.COMPLETED, ended?.status)
        }

        @Test
        @DisplayName("getActiveSessions filters active only")
        fun getActiveSessionsFiltersActiveOnly() {
            val session1 = service.createSession("task1")
            val session2 = service.createSession("task2")
            service.endSession(session1.id)

            val active = service.getActiveSessions()

            assertEquals(1, active.size)
            assertEquals(session2.id, active.first().id)
        }
    }

    // =========================================================================
    // Evaluation Tests
    // =========================================================================

    @Nested
    @DisplayName("Evaluation")
    inner class EvaluationTests {

        @Test
        @DisplayName("quickEvaluate evaluates code")
        fun quickEvaluateEvaluatesCode() {
            val code = """
                fun test() {
                    println("hello")
                }
            """.trimIndent()

            val eval = service.quickEvaluate(code, OutputType.CODE)

            assertTrue(eval.scores.isNotEmpty())
            assertTrue(eval.overallScore > 0)
        }

        @Test
        @DisplayName("quickEvaluate detects unbalanced braces")
        fun quickEvaluateDetectsUnbalancedBraces() {
            val code = "fun test() { { println() }"

            val eval = service.quickEvaluate(code, OutputType.CODE)

            assertTrue(eval.getScore(QualityDimension.CORRECTNESS) < 0.8f)
        }

        @Test
        @DisplayName("quickEvaluate detects incomplete output")
        fun quickEvaluateDetectsIncompleteOutput() {
            val incomplete = "This is an incomplete response..."

            val eval = service.quickEvaluate(incomplete, OutputType.EXPLANATION)

            assertTrue(eval.getScore(QualityDimension.COMPLETENESS) < 0.8f)
        }

        @Test
        @DisplayName("quickEvaluate rewards comments")
        fun quickEvaluateRewardsComments() {
            val withComments = """
                // This function does something
                fun test() {
                    /* explanation */
                    println("hello")
                }
            """.trimIndent()

            val eval = service.quickEvaluate(withComments, OutputType.CODE)

            assertTrue(eval.getScore(QualityDimension.CLARITY) >= 0.8f)
        }
    }

    // =========================================================================
    // Critique Generation Tests
    // =========================================================================

    @Nested
    @DisplayName("Critique Generation")
    inner class CritiqueGenerationTests {

        @Test
        @DisplayName("generateCritiques finds issues")
        fun generateCritiquesFindsIssues() {
            val code = "fun test() { { TODO }"  // Unbalanced + TODO

            val eval = service.quickEvaluate(code, OutputType.CODE)
            val critiques = service.generateCritiques(code, OutputType.CODE, eval)

            assertTrue(critiques.isNotEmpty())
        }

        @Test
        @DisplayName("generateCritiques detects unbalanced braces")
        fun generateCritiquesDetectsUnbalancedBraces() {
            val code = "fun test() { {"

            val eval = service.quickEvaluate(code, OutputType.CODE)
            val critiques = service.generateCritiques(code, OutputType.CODE, eval)

            assertTrue(critiques.any {
                it.isCritical && it.issue.contains("brace", ignoreCase = true)
            })
        }

        @Test
        @DisplayName("generateCritiques detects empty catch blocks")
        fun generateCritiquesDetectsEmptyCatchBlocks() {
            val code = """
                try { risky() } catch (e: Exception) { }
            """.trimIndent()

            val eval = service.quickEvaluate(code, OutputType.CODE)
            val critiques = service.generateCritiques(code, OutputType.CODE, eval)

            assertTrue(critiques.any {
                it.dimension == QualityDimension.ROBUSTNESS
            })
        }

        @Test
        @DisplayName("generateCritiques from request")
        fun generateCritiquesFromRequest() {
            val request = CritiqueRequest(
                output = "fun test() { TODO }",
                outputType = OutputType.CODE,
                maxCritiques = 5
            )

            val critiques = service.generateCritiques(request)

            assertTrue(critiques.size <= 5)
        }
    }

    // =========================================================================
    // Improvement Tests
    // =========================================================================

    @Nested
    @DisplayName("Improvement Suggestions")
    inner class ImprovementTests {

        @Test
        @DisplayName("suggestImprovements maps critiques")
        fun suggestImprovementsMaps() {
            val critiques = listOf(
                Critique.major(QualityDimension.CORRECTNESS, "Error", "Fix it"),
                Critique.minor(QualityDimension.STYLE, "Style issue", "Format")
            )

            val improvements = service.suggestImprovements(critiques)

            assertEquals(2, improvements.size)
            assertTrue(improvements.any { it.priority == ImprovementPriority.HIGH })
        }

        @Test
        @DisplayName("improvement types match dimensions")
        fun improvementTypesMatchDimensions() {
            val critiques = listOf(
                Critique.major(QualityDimension.SECURITY, "Security", "Fix"),
                Critique.major(QualityDimension.DOCUMENTATION, "Docs", "Add")
            )

            val improvements = service.suggestImprovements(critiques)

            assertTrue(improvements.any { it.type == ImprovementType.SECURITY })
            assertTrue(improvements.any { it.type == ImprovementType.DOCUMENTATION })
        }
    }

    // =========================================================================
    // Reflection Tests
    // =========================================================================

    @Nested
    @DisplayName("Reflection")
    inner class ReflectionTests {

        @Test
        @DisplayName("reflect creates reflection")
        fun reflectCreatesReflection() = runBlocking {
            val reflection = service.reflect(
                "task1",
                "fun test() { println() }",
                OutputType.CODE
            )

            assertNotNull(reflection)
            assertEquals(OutputType.CODE, reflection.outputType)
            assertNotNull(reflection.evaluation)
        }

        @Test
        @DisplayName("reflect adds to session")
        fun reflectAddsToSession() = runBlocking {
            val session = service.createSession("task1")

            service.reflect("task1", "code", OutputType.CODE, session.id)

            val updated = service.getSession(session.id)
            assertEquals(1, updated?.reflectionCount)
        }
    }

    // =========================================================================
    // Iterative Reflection Tests
    // =========================================================================

    @Nested
    @DisplayName("Iterative Reflection")
    inner class IterativeReflectionTests {

        @Test
        @DisplayName("iterativeReflect returns result")
        fun iterativeReflectReturnsResult() = runBlocking {
            val result = service.iterativeReflect(
                "task1",
                "fun test() { println() }",
                OutputType.CODE,
                maxIterations = 2
            )

            assertNotNull(result)
            assertTrue(result.totalIterations >= 1)
        }

        @Test
        @DisplayName("iterativeReflect refines on critical issues")
        fun iterativeReflectRefinesOnCriticalIssues() = runBlocking {
            // Create service with refiner that fixes "broken"
            val refiningService = ReflectionService(
                config = ReflectionConfig(enableAutoRefinement = true),
                refiner = { original, critiques, _ ->
                    if (critiques.any { it.isCritical }) {
                        original.replace("{", "").plus(" { }")
                    } else {
                        original
                    }
                }
            )

            val result = refiningService.iterativeReflect(
                "task1",
                "fun test() { {",  // Unbalanced braces
                OutputType.CODE
            )

            assertTrue(result.totalIterations >= 1)
        }

        @Test
        @DisplayName("iterativeReflect respects max iterations")
        fun iterativeReflectRespectsMaxIterations() = runBlocking {
            val result = service.iterativeReflect(
                "task1",
                "code",
                OutputType.CODE,
                maxIterations = 2
            )

            assertTrue(result.totalIterations <= 2)
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
        fun getStatsReturnsCorrectCounts() = runBlocking {
            service.createSession("task1")
            service.reflect("task2", "code", OutputType.CODE)

            val stats = service.getStats()

            assertTrue(stats.totalSessions >= 1)
            assertTrue(stats.totalReflections >= 1)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits reflection started event")
        fun emitsReflectionStartedEvent() = runBlocking {
            var received: ReflectionEvent? = null
            service.addListener { received = it }

            service.reflect("task1", "code", OutputType.CODE)

            assertTrue(received is ReflectionEvent.ReflectionStarted ||
                       received is ReflectionEvent.EvaluationCompleted ||
                       received is ReflectionEvent.CritiquesGenerated)
        }

        @Test
        @DisplayName("emits session completed event")
        fun emitsSessionCompletedEvent() = runBlocking {
            val events = mutableListOf<ReflectionEvent>()
            service.addListener { events.add(it) }

            service.iterativeReflect("task1", "code", OutputType.CODE)

            assertTrue(events.any { it is ReflectionEvent.SessionCompleted })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() = runBlocking {
            var count = 0
            val listener: (ReflectionEvent) -> Unit = { count++ }
            service.addListener(listener)

            service.reflect("task1", "code", OutputType.CODE)
            val firstCount = count

            service.removeListener(listener)
            service.reflect("task2", "code", OutputType.CODE)

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
        @DisplayName("clearSessions removes all")
        fun clearSessionsRemovesAll() {
            service.createSession("task1")
            service.createSession("task2")

            service.clearSessions()

            assertEquals(0, service.getActiveSessions().size)
        }
    }
}
