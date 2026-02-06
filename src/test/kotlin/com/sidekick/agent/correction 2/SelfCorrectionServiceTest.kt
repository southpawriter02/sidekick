package com.sidekick.agent.correction

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Self-Correction Service.
 */
@DisplayName("Self-Correction Service Tests")
class SelfCorrectionServiceTest {

    private lateinit var service: SelfCorrectionService

    @BeforeEach
    fun setUp() {
        service = SelfCorrectionService(
            corrector = { error, content, strategy ->
                // Simple mock corrector that "fixes" the content
                content.replace("broken", "fixed")
            },
            validator = { content, category ->
                ValidationCheck.pass("validation", "Passed", category)
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
            assertEquals(CorrectionSession.SessionStatus.ACTIVE, session.status)
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

            assertEquals(CorrectionSession.SessionStatus.COMPLETED, ended?.status)
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
    // Error Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Detection")
    inner class ErrorDetectionTests {

        @Test
        @DisplayName("detects unbalanced braces")
        fun detectsUnbalancedBraces() {
            val content = """
                fun test() {
                    if (true) {
                        println("hello")
                    // Missing closing brace
                }
            """.trimIndent()

            val errors = service.detectErrors(content)

            assertTrue(errors.any { it.type == ErrorType.SYNTAX_ERROR })
            assertTrue(errors.any { it.description.contains("brace", ignoreCase = true) })
        }

        @Test
        @DisplayName("detects unbalanced parentheses")
        fun detectsUnbalancedParentheses() {
            val content = "foo(bar(baz)"

            val errors = service.detectErrors(content)

            assertTrue(errors.any {
                it.type == ErrorType.SYNTAX_ERROR &&
                it.description.contains("parenthes", ignoreCase = true)
            })
        }

        @Test
        @DisplayName("detects security issues")
        fun detectsSecurityIssues() {
            val content = """
                val password = "secret123"
                val query = "SELECT * FROM users WHERE id = " + userId
            """.trimIndent()

            val errors = service.detectErrors(content)

            assertTrue(errors.any { it.type == ErrorType.SECURITY_ISSUE })
        }

        @Test
        @DisplayName("detects incomplete responses")
        fun detectsIncompleteResponses() {
            val content = "This is an incomplete response that was cut off..."

            val errors = service.detectErrors(content)

            assertTrue(errors.any { it.type == ErrorType.INCOMPLETE_RESPONSE })
        }

        @Test
        @DisplayName("detects hallucination indicators")
        fun detectsHallucinationIndicators() {
            val content = "Use the nonexistent library for this"

            val errors = service.detectErrors(content)

            assertTrue(errors.any { it.type == ErrorType.HALLUCINATION })
        }

        @Test
        @DisplayName("adds errors to session when provided")
        fun addsErrorsToSessionWhenProvided() {
            val session = service.createSession("task1")
            val content = "fun test() { {"  // Unbalanced braces

            service.detectErrors(content, sessionId = session.id)

            val updated = service.getSession(session.id)
            assertTrue(updated?.errorCount ?: 0 > 0)
        }
    }

    // =========================================================================
    // Correction Tests
    // =========================================================================

    @Nested
    @DisplayName("Correction")
    inner class CorrectionTests {

        @Test
        @DisplayName("correctError applies fix")
        fun correctErrorAppliesFix() = runBlocking {
            val session = service.createSession("task1")
            val error = DetectedError.syntax("Broken code")

            // Add error to session manually
            val updatedSession = session.addError(error)
            // Replace session
            service.clearSessions()
            val newSession = service.createSession("task1")
            service.detectErrors("broken code", sessionId = newSession.id)

            val sessionWithError = service.getSession(newSession.id)!!
            val errorToFix = sessionWithError.errors.first()

            val attempt = service.correctError(
                newSession.id,
                errorToFix.id,
                "broken code"
            )

            assertNotNull(attempt)
            assertTrue(attempt!!.isSuccessful)
            assertEquals("fixed code", attempt.correctedContent)
        }

        @Test
        @DisplayName("correctAllErrors fixes multiple errors")
        fun correctAllErrorsFixesMultipleErrors() = runBlocking {
            val session = service.createSession("task1")
            val content = "broken { broken"  // Multiple issues

            service.detectErrors(content, sessionId = session.id)

            val result = service.correctAllErrors(session.id, content)

            assertTrue(result.errorsCorrected > 0)
            assertEquals("fixed { fixed", result.finalContent)
        }

        @Test
        @DisplayName("iterativeCorrection runs multiple passes")
        fun iterativeCorrectionRunsMultiplePasses() = runBlocking {
            val content = "broken code here"

            val result = service.iterativeCorrection("task1", content, maxIterations = 3)

            assertNotNull(result)
            assertEquals("fixed code here", result.finalContent)
        }

        @Test
        @DisplayName("respects max attempts per error")
        fun respectsMaxAttemptsPerError() = runBlocking {
            val config = CorrectionConfig(maxAttemptsPerError = 1)
            val session = service.createSession("task1", config)

            service.detectErrors("broken", sessionId = session.id)
            val error = service.getSession(session.id)!!.errors.first()

            // First attempt should work
            val attempt1 = service.correctError(session.id, error.id, "broken")
            assertNotNull(attempt1)

            // Second attempt should be blocked
            val attempt2 = service.correctError(session.id, error.id, "broken")
            assertNull(attempt2)
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("validateContent checks multiple aspects")
        fun validateContentChecksMultipleAspects() = runBlocking {
            val validContent = "fun test() { println(\"hello\") }"

            val result = service.validateContent(validContent)

            assertTrue(result.checks.isNotEmpty())
            assertTrue(result.checks.any { it.category == ValidationCategory.SYNTAX })
        }

        @Test
        @DisplayName("validateCorrection uses appropriate category")
        fun validateCorrectionUsesAppropriateCategory() = runBlocking {
            val result = service.validateCorrection(
                "fixed code",
                ErrorType.SECURITY_ISSUE
            )

            assertTrue(result.checks.any { it.category == ValidationCategory.SECURITY })
        }
    }

    // =========================================================================
    // Strategy Selection Tests
    // =========================================================================

    @Nested
    @DisplayName("Strategy Selection")
    inner class StrategySelectionTests {

        @Test
        @DisplayName("suggestStrategy uses default for first attempt")
        fun suggestStrategyUsesDefaultForFirstAttempt() {
            val error = DetectedError.syntax("Error")

            val strategy = service.suggestStrategy(error, previousAttempts = 0)

            assertEquals(CorrectionStrategy.TARGETED_FIX, strategy)
        }

        @Test
        @DisplayName("suggestStrategy escalates with failures")
        fun suggestStrategyEscalatesWithFailures() {
            val error = DetectedError.syntax("Error")

            val strategy1 = service.suggestStrategy(error, previousAttempts = 1)
            assertEquals(CorrectionStrategy.REGENERATE_SECTION, strategy1)

            val strategy2 = service.suggestStrategy(error, previousAttempts = 2)
            assertEquals(CorrectionStrategy.FULL_REGENERATION, strategy2)
        }

        @Test
        @DisplayName("getStrategiesForError returns appropriate options")
        fun getStrategiesForErrorReturnsAppropriateOptions() {
            val syntaxStrategies = service.getStrategiesForError(ErrorType.SYNTAX_ERROR)
            assertTrue(CorrectionStrategy.TARGETED_FIX in syntaxStrategies)

            val incompleteStrategies = service.getStrategiesForError(ErrorType.INCOMPLETE_RESPONSE)
            assertTrue(CorrectionStrategy.CONTINUE_GENERATION in incompleteStrategies)

            val hallucinationStrategies = service.getStrategiesForError(ErrorType.HALLUCINATION)
            assertTrue(CorrectionStrategy.FULL_REGENERATION in hallucinationStrategies)
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
            val session = service.createSession("task1")
            service.detectErrors("broken { broken", sessionId = session.id)
            service.correctAllErrors(session.id, "broken { broken")

            val stats = service.getStats()

            assertTrue(stats.totalSessions > 0)
            assertTrue(stats.totalErrors > 0)
            assertTrue(stats.totalAttempts > 0)
        }

        @Test
        @DisplayName("successRate calculates correctly")
        fun successRateCalculatesCorrectly() = runBlocking {
            val session = service.createSession("task1")
            service.detectErrors("broken code", sessionId = session.id)
            service.correctAllErrors(session.id, "broken code")

            val stats = service.getStats()

            assertTrue(stats.successRate >= 0f)
            assertTrue(stats.successRate <= 1f)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits error detected event")
        fun emitsErrorDetectedEvent() {
            var received: CorrectionEvent? = null
            service.addListener { received = it }

            val session = service.createSession("task1")
            service.detectErrors("broken code", sessionId = session.id)

            assertTrue(received is CorrectionEvent.ErrorDetected)
        }

        @Test
        @DisplayName("emits correction events")
        fun emitsCorrectionEvents() = runBlocking {
            val events = mutableListOf<CorrectionEvent>()
            service.addListener { events.add(it) }

            val session = service.createSession("task1")
            service.detectErrors("broken code", sessionId = session.id)
            service.correctAllErrors(session.id, "broken code")

            assertTrue(events.any { it is CorrectionEvent.CorrectionStarted })
            assertTrue(events.any { it is CorrectionEvent.CorrectionSucceeded })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() {
            var count = 0
            val listener: (CorrectionEvent) -> Unit = { count++ }
            service.addListener(listener)

            val session1 = service.createSession("task1")
            service.detectErrors("broken", sessionId = session1.id)
            val firstCount = count

            service.removeListener(listener)
            val session2 = service.createSession("task2")
            service.detectErrors("broken", sessionId = session2.id)

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
