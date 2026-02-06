package com.sidekick.agent.correction

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Correction Models.
 */
@DisplayName("Correction Models Tests")
class CorrectionModelsTest {

    // =========================================================================
    // DetectedError Tests
    // =========================================================================

    @Nested
    @DisplayName("DetectedError")
    inner class DetectedErrorTests {

        @Test
        @DisplayName("syntax factory creates syntax error")
        fun syntaxFactoryCreatesSyntaxError() {
            val error = DetectedError.syntax(
                "Missing semicolon",
                ErrorLocation.line(10),
                "Add semicolon at end of line"
            )

            assertEquals(ErrorType.SYNTAX_ERROR, error.type)
            assertEquals(ErrorSeverity.HIGH, error.severity)
            assertEquals(10, error.location?.startLine)
            assertTrue(error.hasSuggestedFix)
        }

        @Test
        @DisplayName("logic factory creates logic error")
        fun logicFactoryCreatesLogicError() {
            val error = DetectedError.logic(
                "Off-by-one error in loop",
                "for (i in 0..list.size)"
            )

            assertEquals(ErrorType.LOGIC_ERROR, error.type)
            assertEquals(ErrorSeverity.HIGH, error.severity)
        }

        @Test
        @DisplayName("hallucination factory creates hallucination error")
        fun hallucinationFactoryCreatesHallucinationError() {
            val error = DetectedError.hallucination(
                "API doesn't exist",
                0.7f
            )

            assertEquals(ErrorType.HALLUCINATION, error.type)
            assertEquals(ErrorSeverity.MEDIUM, error.severity)
            assertEquals(0.7f, error.confidence)
        }

        @Test
        @DisplayName("isCritical checks severity")
        fun isCriticalChecksSeverity() {
            val critical = DetectedError(
                type = ErrorType.SECURITY_ISSUE,
                severity = ErrorSeverity.CRITICAL,
                description = "SQL injection"
            )
            assertTrue(critical.isCritical)

            val high = DetectedError(
                type = ErrorType.SYNTAX_ERROR,
                severity = ErrorSeverity.HIGH,
                description = "Syntax error"
            )
            assertFalse(high.isCritical)
        }

        @Test
        @DisplayName("isHighConfidence checks threshold")
        fun isHighConfidenceChecksThreshold() {
            val highConf = DetectedError(
                type = ErrorType.SYNTAX_ERROR,
                severity = ErrorSeverity.HIGH,
                description = "Error",
                confidence = 0.9f
            )
            assertTrue(highConf.isHighConfidence)

            val lowConf = highConf.copy(confidence = 0.5f)
            assertFalse(lowConf.isHighConfidence)
        }
    }

    // =========================================================================
    // ErrorType Tests
    // =========================================================================

    @Nested
    @DisplayName("ErrorType")
    inner class ErrorTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            ErrorType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
                assertTrue(type.description.isNotBlank())
            }
        }

        @Test
        @DisplayName("all types have default strategies")
        fun allTypesHaveDefaultStrategies() {
            ErrorType.entries.forEach { type ->
                assertNotNull(type.defaultStrategy)
            }
        }
    }

    // =========================================================================
    // ErrorSeverity Tests
    // =========================================================================

    @Nested
    @DisplayName("ErrorSeverity")
    inner class ErrorSeverityTests {

        @Test
        @DisplayName("critical and high require correction")
        fun criticalAndHighRequireCorrection() {
            assertTrue(ErrorSeverity.CRITICAL.requiresCorrection)
            assertTrue(ErrorSeverity.HIGH.requiresCorrection)
            assertFalse(ErrorSeverity.MEDIUM.requiresCorrection)
            assertFalse(ErrorSeverity.LOW.requiresCorrection)
        }

        @Test
        @DisplayName("priorities are ordered correctly")
        fun prioritiesAreOrderedCorrectly() {
            assertTrue(ErrorSeverity.CRITICAL.priority > ErrorSeverity.HIGH.priority)
            assertTrue(ErrorSeverity.HIGH.priority > ErrorSeverity.MEDIUM.priority)
            assertTrue(ErrorSeverity.MEDIUM.priority > ErrorSeverity.LOW.priority)
        }
    }

    // =========================================================================
    // ErrorLocation Tests
    // =========================================================================

    @Nested
    @DisplayName("ErrorLocation")
    inner class ErrorLocationTests {

        @Test
        @DisplayName("line factory creates single line location")
        fun lineFactoryCreatesSingleLineLocation() {
            val location = ErrorLocation.line(42, "File.kt")

            assertEquals("File.kt", location.file)
            assertEquals(42, location.startLine)
            assertEquals(42, location.endLine)
        }

        @Test
        @DisplayName("range factory creates multi-line location")
        fun rangeFactoryCreatesMultiLineLocation() {
            val location = ErrorLocation.range(10, 20, "File.kt")

            assertEquals(10, location.startLine)
            assertEquals(20, location.endLine)
        }

        @Test
        @DisplayName("displayString formats correctly")
        fun displayStringFormatsCorrectly() {
            val single = ErrorLocation.line(42, "File.kt")
            assertEquals("File.kt:42", single.displayString)

            val range = ErrorLocation.range(10, 20, "File.kt")
            assertEquals("File.kt:10-20", range.displayString)

            val noFile = ErrorLocation.line(15)
            assertEquals("15", noFile.displayString)
        }
    }

    // =========================================================================
    // CorrectionStrategy Tests
    // =========================================================================

    @Nested
    @DisplayName("CorrectionStrategy")
    inner class CorrectionStrategyTests {

        @Test
        @DisplayName("all strategies have display names")
        fun allStrategiesHaveDisplayNames() {
            CorrectionStrategy.entries.forEach { strategy ->
                assertTrue(strategy.displayName.isNotBlank())
                assertTrue(strategy.description.isNotBlank())
            }
        }

        @Test
        @DisplayName("cost levels are in valid range")
        fun costLevelsAreInValidRange() {
            CorrectionStrategy.entries.forEach { strategy ->
                assertTrue(strategy.costLevel in 0..5)
            }
        }

        @Test
        @DisplayName("full regeneration has highest cost")
        fun fullRegenerationHasHighestCost() {
            assertEquals(5, CorrectionStrategy.FULL_REGENERATION.costLevel)
            assertTrue(CorrectionStrategy.TARGETED_FIX.costLevel < CorrectionStrategy.FULL_REGENERATION.costLevel)
        }
    }

    // =========================================================================
    // CorrectionAttempt Tests
    // =========================================================================

    @Nested
    @DisplayName("CorrectionAttempt")
    inner class CorrectionAttemptTests {

        @Test
        @DisplayName("start marks as in progress")
        fun startMarksAsInProgress() {
            val attempt = CorrectionAttempt(
                errorId = "e1",
                strategy = CorrectionStrategy.TARGETED_FIX,
                originalContent = "broken code"
            )

            val started = attempt.start()

            assertEquals(CorrectionStatus.IN_PROGRESS, started.status)
        }

        @Test
        @DisplayName("succeed marks with corrected content")
        fun succeedMarksWithCorrectedContent() {
            val attempt = CorrectionAttempt(
                errorId = "e1",
                strategy = CorrectionStrategy.TARGETED_FIX,
                originalContent = "broken code"
            ).start()

            val succeeded = attempt.succeed("fixed code", ValidationResult.passed())

            assertEquals(CorrectionStatus.SUCCEEDED, succeeded.status)
            assertEquals("fixed code", succeeded.correctedContent)
            assertTrue(succeeded.isSuccessful)
            assertNotNull(succeeded.completedAt)
        }

        @Test
        @DisplayName("fail marks with reason")
        fun failMarksWithReason() {
            val attempt = CorrectionAttempt(
                errorId = "e1",
                strategy = CorrectionStrategy.TARGETED_FIX,
                originalContent = "broken code"
            ).start()

            val failed = attempt.fail("Could not fix")

            assertEquals(CorrectionStatus.FAILED, failed.status)
            assertFalse(failed.isSuccessful)
            assertNotNull(failed.completedAt)
        }
    }

    // =========================================================================
    // ValidationResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ValidationResult")
    inner class ValidationResultTests {

        @Test
        @DisplayName("passed factory creates passing result")
        fun passedFactoryCreatesPassingResult() {
            val result = ValidationResult.passed("All good")

            assertTrue(result.passed)
            assertEquals("All good", result.message)
        }

        @Test
        @DisplayName("failed factory creates failing result")
        fun failedFactoryCreatesFailingResult() {
            val result = ValidationResult.failed("Something wrong")

            assertFalse(result.passed)
            assertEquals("Something wrong", result.message)
        }

        @Test
        @DisplayName("fromChecks aggregates correctly")
        fun fromChecksAggregatesCorrectly() {
            val checks = listOf(
                ValidationCheck.pass("check1"),
                ValidationCheck.fail("check2", "Failed"),
                ValidationCheck.pass("check3")
            )

            val result = ValidationResult.fromChecks(checks)

            assertFalse(result.passed)
            assertEquals(2, result.passedCount)
            assertEquals(1, result.failedCount)
            assertEquals(2f / 3f, result.passRate, 0.01f)
        }
    }

    // =========================================================================
    // CorrectionSession Tests
    // =========================================================================

    @Nested
    @DisplayName("CorrectionSession")
    inner class CorrectionSessionTests {

        @Test
        @DisplayName("addError appends error")
        fun addErrorAppendsError() {
            var session = CorrectionSession(taskId = "task1")

            session = session.addError(DetectedError.syntax("Error 1"))
            session = session.addError(DetectedError.logic("Error 2"))

            assertEquals(2, session.errorCount)
        }

        @Test
        @DisplayName("criticalErrorCount counts correctly")
        fun criticalErrorCountCountsCorrectly() {
            var session = CorrectionSession(taskId = "task1")
                .addError(DetectedError(
                    type = ErrorType.SECURITY_ISSUE,
                    severity = ErrorSeverity.CRITICAL,
                    description = "SQL injection"
                ))
                .addError(DetectedError.syntax("Syntax"))
                .addError(DetectedError(
                    type = ErrorType.RUNTIME_ERROR,
                    severity = ErrorSeverity.CRITICAL,
                    description = "NPE"
                ))

            assertEquals(2, session.criticalErrorCount)
        }

        @Test
        @DisplayName("uncorrectedErrors filters correctly")
        fun uncorrectedErrorsFiltersCorrectly() {
            val error1 = DetectedError.syntax("Error 1")
            val error2 = DetectedError.logic("Error 2")

            var session = CorrectionSession(taskId = "task1")
                .addError(error1)
                .addError(error2)

            // Add successful attempt for error1
            session = session.addAttempt(CorrectionAttempt(
                errorId = error1.id,
                strategy = CorrectionStrategy.TARGETED_FIX,
                originalContent = "code",
                status = CorrectionStatus.SUCCEEDED
            ))

            assertEquals(1, session.uncorrectedErrors.size)
            assertEquals(error2.id, session.uncorrectedErrors.first().id)
        }

        @Test
        @DisplayName("allCorrected checks completion")
        fun allCorrectedChecksCompletion() {
            val error = DetectedError.syntax("Error")

            var session = CorrectionSession(taskId = "task1")
                .addError(error)

            assertFalse(session.allCorrected)

            session = session.addAttempt(CorrectionAttempt(
                errorId = error.id,
                strategy = CorrectionStrategy.TARGETED_FIX,
                originalContent = "code",
                status = CorrectionStatus.SUCCEEDED
            ))

            assertTrue(session.allCorrected)
        }
    }

    // =========================================================================
    // CorrectionEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("CorrectionEvent")
    inner class CorrectionEventTests {

        @Test
        @DisplayName("events have required fields")
        fun eventsHaveRequiredFields() {
            val detected = CorrectionEvent.ErrorDetected(
                "s1",
                "e1",
                ErrorType.SYNTAX_ERROR,
                ErrorSeverity.HIGH
            )
            assertEquals("s1", detected.sessionId)
            assertNotNull(detected.timestamp)

            val completed = CorrectionEvent.SessionCompleted("s1", 5, 4, 6)
            assertEquals(5, completed.totalErrors)
            assertEquals(4, completed.correctedErrors)
        }
    }

    // =========================================================================
    // CorrectionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("CorrectionResult")
    inner class CorrectionResultTests {

        @Test
        @DisplayName("correctionRate calculates correctly")
        fun correctionRateCalculatesCorrectly() {
            val result = CorrectionResult(
                sessionId = "s1",
                taskId = "t1",
                success = true,
                originalContent = "original",
                finalContent = "fixed",
                errorsDetected = 10,
                errorsCorrected = 8,
                totalAttempts = 12,
                validationResult = null,
                remainingErrors = emptyList(),
                durationMs = 1000
            )

            assertEquals(0.8f, result.correctionRate)
        }

        @Test
        @DisplayName("contentChanged checks difference")
        fun contentChangedChecksDifference() {
            val changed = CorrectionResult(
                sessionId = "s1",
                taskId = "t1",
                success = true,
                originalContent = "original",
                finalContent = "fixed",
                errorsDetected = 1,
                errorsCorrected = 1,
                totalAttempts = 1,
                validationResult = null,
                remainingErrors = emptyList(),
                durationMs = 100
            )
            assertTrue(changed.contentChanged)

            val unchanged = changed.copy(finalContent = "original")
            assertFalse(unchanged.contentChanged)
        }
    }
}
