package com.sidekick.agent.reflection

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Reflection Models.
 */
@DisplayName("Reflection Models Tests")
class ReflectionModelsTest {

    // =========================================================================
    // Reflection Tests
    // =========================================================================

    @Nested
    @DisplayName("Reflection")
    inner class ReflectionTests {

        @Test
        @DisplayName("forCode factory creates code reflection")
        fun forCodeFactoryCreatesCodeReflection() {
            val reflection = Reflection.forCode(
                "task1",
                "fun test() {}",
                QualityEvaluation.good()
            )

            assertEquals(OutputType.CODE, reflection.outputType)
            assertEquals("fun test() {}", reflection.originalOutput)
        }

        @Test
        @DisplayName("forExplanation factory creates explanation reflection")
        fun forExplanationFactoryCreatesExplanationReflection() {
            val reflection = Reflection.forExplanation(
                "task1",
                "This is an explanation",
                QualityEvaluation.good()
            )

            assertEquals(OutputType.EXPLANATION, reflection.outputType)
        }

        @Test
        @DisplayName("qualityScore returns overall score")
        fun qualityScoreReturnsOverallScore() {
            val reflection = Reflection.forCode(
                "task1",
                "code",
                QualityEvaluation.quick(0.85f)
            )

            assertEquals(0.85f, reflection.qualityScore)
        }

        @Test
        @DisplayName("isAcceptable checks threshold")
        fun isAcceptableChecksThreshold() {
            val good = Reflection.forCode("task1", "code", QualityEvaluation.good())
            assertTrue(good.isAcceptable)

            val poor = Reflection.forCode("task1", "code", QualityEvaluation.poor())
            assertFalse(poor.isAcceptable)
        }

        @Test
        @DisplayName("addCritique appends critique")
        fun addCritiqueAppendsCritique() {
            var reflection = Reflection.forCode("task1", "code", QualityEvaluation.good())

            reflection = reflection.addCritique(
                Critique.minor(QualityDimension.STYLE, "Minor style issue")
            )

            assertEquals(1, reflection.critiqueCount)
        }

        @Test
        @DisplayName("criticalCritiques filters correctly")
        fun criticalCritiquesFiltersCorrectly() {
            val reflection = Reflection.forCode("task1", "code", QualityEvaluation.good())
                .addCritique(Critique.critical(QualityDimension.CORRECTNESS, "Critical"))
                .addCritique(Critique.minor(QualityDimension.STYLE, "Minor"))
                .addCritique(Critique.critical(QualityDimension.SECURITY, "Security"))

            assertEquals(2, reflection.criticalCritiques.size)
        }

        @Test
        @DisplayName("needsRefinement checks critiques")
        fun needsRefinementChecksCritiques() {
            val withMajor = Reflection.forCode("task1", "code", QualityEvaluation.good())
                .addCritique(Critique.major(QualityDimension.CORRECTNESS, "Major issue"))
            assertTrue(withMajor.needsRefinement)

            val withMinorOnly = Reflection.forCode("task1", "code", QualityEvaluation.good())
                .addCritique(Critique.minor(QualityDimension.STYLE, "Minor"))
            assertFalse(withMinorOnly.needsRefinement)
        }

        @Test
        @DisplayName("withRefinedOutput sets refined output")
        fun withRefinedOutputSetsRefinedOutput() {
            val reflection = Reflection.forCode("task1", "code", QualityEvaluation.good())
                .withRefinedOutput("refined code")

            assertTrue(reflection.wasRefined)
            assertEquals("refined code", reflection.refinedOutput)
        }
    }

    // =========================================================================
    // QualityEvaluation Tests
    // =========================================================================

    @Nested
    @DisplayName("QualityEvaluation")
    inner class QualityEvaluationTests {

        @Test
        @DisplayName("quick factory creates uniform scores")
        fun quickFactoryCreatesUniformScores() {
            val eval = QualityEvaluation.quick(0.75f, "Summary")

            assertEquals(0.75f, eval.overallScore)
            assertEquals("Summary", eval.summary)
        }

        @Test
        @DisplayName("excellent preset has high score")
        fun excellentPresetHasHighScore() {
            val eval = QualityEvaluation.excellent()

            assertTrue(eval.isExcellent)
            assertTrue(eval.isAcceptable)
        }

        @Test
        @DisplayName("poor preset has low score")
        fun poorPresetHasLowScore() {
            val eval = QualityEvaluation.poor()

            assertFalse(eval.isExcellent)
            assertFalse(eval.isAcceptable)
        }

        @Test
        @DisplayName("getScore returns dimension score")
        fun getScoreReturnsDimensionScore() {
            val scores = mapOf(
                QualityDimension.CORRECTNESS to 0.9f,
                QualityDimension.CLARITY to 0.7f
            )
            val eval = QualityEvaluation(scores)

            assertEquals(0.9f, eval.getScore(QualityDimension.CORRECTNESS))
            assertEquals(0.7f, eval.getScore(QualityDimension.CLARITY))
            assertEquals(0.5f, eval.getScore(QualityDimension.SECURITY)) // Default
        }

        @Test
        @DisplayName("weakestDimension finds minimum")
        fun weakestDimensionFindsMinimum() {
            val scores = mapOf(
                QualityDimension.CORRECTNESS to 0.9f,
                QualityDimension.SECURITY to 0.3f,
                QualityDimension.CLARITY to 0.7f
            )
            val eval = QualityEvaluation(scores)

            assertEquals(QualityDimension.SECURITY, eval.weakestDimension)
        }

        @Test
        @DisplayName("strongestDimension finds maximum")
        fun strongestDimensionFindsMaximum() {
            val scores = mapOf(
                QualityDimension.CORRECTNESS to 0.9f,
                QualityDimension.CLARITY to 0.7f
            )
            val eval = QualityEvaluation(scores)

            assertEquals(QualityDimension.CORRECTNESS, eval.strongestDimension)
        }
    }

    // =========================================================================
    // Critique Tests
    // =========================================================================

    @Nested
    @DisplayName("Critique")
    inner class CritiqueTests {

        @Test
        @DisplayName("critical factory creates critical critique")
        fun criticalFactoryCreatesCriticalCritique() {
            val critique = Critique.critical(
                QualityDimension.CORRECTNESS,
                "Critical issue",
                "Fix suggestion"
            )

            assertEquals(CritiqueSeverity.CRITICAL, critique.severity)
            assertTrue(critique.isCritical)
            assertTrue(critique.requiresAction)
        }

        @Test
        @DisplayName("major factory creates major critique")
        fun majorFactoryCreatesMajorCritique() {
            val critique = Critique.major(
                QualityDimension.ROBUSTNESS,
                "Major issue"
            )

            assertEquals(CritiqueSeverity.MAJOR, critique.severity)
            assertTrue(critique.requiresAction)
        }

        @Test
        @DisplayName("minor factory creates minor critique")
        fun minorFactoryCreatesMinorCritique() {
            val critique = Critique.minor(
                QualityDimension.STYLE,
                "Minor issue"
            )

            assertEquals(CritiqueSeverity.MINOR, critique.severity)
            assertFalse(critique.requiresAction)
        }

        @Test
        @DisplayName("suggestion factory creates suggestion")
        fun suggestionFactoryCreatesSuggestion() {
            val critique = Critique.suggestion(
                QualityDimension.DOCUMENTATION,
                "Consider adding"
            )

            assertEquals(CritiqueSeverity.SUGGESTION, critique.severity)
            assertFalse(critique.requiresAction)
        }
    }

    // =========================================================================
    // Improvement Tests
    // =========================================================================

    @Nested
    @DisplayName("Improvement")
    inner class ImprovementTests {

        @Test
        @DisplayName("refactoring factory creates refactoring")
        fun refactoringFactoryCreatesRefactoring() {
            val improvement = Improvement.refactoring(
                "Extract method",
                "long code block",
                "extracted()"
            )

            assertEquals(ImprovementType.REFACTORING, improvement.type)
            assertTrue(improvement.hasConcreteSuggestion)
        }

        @Test
        @DisplayName("documentation factory creates documentation improvement")
        fun documentationFactoryCreatesDocumentationImprovement() {
            val improvement = Improvement.documentation("Add KDoc")

            assertEquals(ImprovementType.DOCUMENTATION, improvement.type)
            assertEquals(ImprovementPriority.LOW, improvement.priority)
        }

        @Test
        @DisplayName("errorHandling factory creates high priority")
        fun errorHandlingFactoryCreatesHighPriority() {
            val improvement = Improvement.errorHandling(
                "Add null check",
                "if (x != null)"
            )

            assertEquals(ImprovementType.ERROR_HANDLING, improvement.type)
            assertTrue(improvement.isHighPriority)
        }
    }

    // =========================================================================
    // ReflectionSession Tests
    // =========================================================================

    @Nested
    @DisplayName("ReflectionSession")
    inner class ReflectionSessionTests {

        @Test
        @DisplayName("addReflection appends and increments")
        fun addReflectionAppendsAndIncrements() {
            var session = ReflectionSession(taskId = "task1")

            session = session.addReflection(
                Reflection.forCode("task1", "code", QualityEvaluation.good())
            )

            assertEquals(1, session.reflectionCount)
            assertEquals(1, session.iterations)
        }

        @Test
        @DisplayName("latestReflection returns most recent")
        fun latestReflectionReturnsMostRecent() {
            val session = ReflectionSession(taskId = "task1")
                .addReflection(Reflection.forCode("task1", "v1", QualityEvaluation.poor()))
                .addReflection(Reflection.forCode("task1", "v2", QualityEvaluation.good()))

            assertEquals("v2", session.latestReflection?.originalOutput)
        }

        @Test
        @DisplayName("averageQuality calculates correctly")
        fun averageQualityCalculatesCorrectly() {
            val session = ReflectionSession(taskId = "task1")
                .addReflection(Reflection.forCode("task1", "v1", QualityEvaluation.quick(0.6f)))
                .addReflection(Reflection.forCode("task1", "v2", QualityEvaluation.quick(0.8f)))

            assertEquals(0.7f, session.averageQuality, 0.01f)
        }

        @Test
        @DisplayName("maxIterationsReached checks config")
        fun maxIterationsReachedChecksConfig() {
            var session = ReflectionSession(
                taskId = "task1",
                config = ReflectionConfig(maxIterations = 2)
            )

            assertFalse(session.maxIterationsReached)

            session = session.addReflection(Reflection.forCode("task1", "v1", QualityEvaluation.good()))
            session = session.addReflection(Reflection.forCode("task1", "v2", QualityEvaluation.good()))

            assertTrue(session.maxIterationsReached)
        }
    }

    // =========================================================================
    // ReflectionEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("ReflectionEvent")
    inner class ReflectionEventTests {

        @Test
        @DisplayName("events have required fields")
        fun eventsHaveRequiredFields() {
            val started = ReflectionEvent.ReflectionStarted("s1", "t1", OutputType.CODE)
            assertEquals("s1", started.sessionId)
            assertNotNull(started.timestamp)

            val completed = ReflectionEvent.SessionCompleted("s1", 0.85f, 3, true)
            assertEquals(0.85f, completed.finalScore)
            assertTrue(completed.improved)
        }
    }

    // =========================================================================
    // ReflectionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ReflectionResult")
    inner class ReflectionResultTests {

        @Test
        @DisplayName("scoreImprovement calculates difference")
        fun scoreImprovementCalculatesDifference() {
            val result = ReflectionResult(
                sessionId = "s1",
                taskId = "t1",
                success = true,
                originalOutput = "original",
                finalOutput = "refined",
                initialScore = 0.5f,
                finalScore = 0.8f,
                totalIterations = 2,
                allCritiques = emptyList(),
                appliedImprovements = emptyList(),
                durationMs = 100
            )

            assertEquals(0.3f, result.scoreImprovement, 0.01f)
        }

        @Test
        @DisplayName("wasImproved checks content change")
        fun wasImprovedChecksContentChange() {
            val improved = ReflectionResult(
                sessionId = "s1",
                taskId = "t1",
                success = true,
                originalOutput = "original",
                finalOutput = "refined",
                initialScore = 0.5f,
                finalScore = 0.8f,
                totalIterations = 2,
                allCritiques = emptyList(),
                appliedImprovements = emptyList(),
                durationMs = 100
            )
            assertTrue(improved.wasImproved)

            val unchanged = improved.copy(finalOutput = "original")
            assertFalse(unchanged.wasImproved)
        }

        @Test
        @DisplayName("improvementPercentage calculates correctly")
        fun improvementPercentageCalculatesCorrectly() {
            val result = ReflectionResult(
                sessionId = "s1",
                taskId = "t1",
                success = true,
                originalOutput = "original",
                finalOutput = "refined",
                initialScore = 0.5f,
                finalScore = 0.75f,
                totalIterations = 2,
                allCritiques = emptyList(),
                appliedImprovements = emptyList(),
                durationMs = 100
            )

            assertEquals(50f, result.improvementPercentage, 0.01f)
        }
    }

    // =========================================================================
    // Enum Tests
    // =========================================================================

    @Nested
    @DisplayName("Enums")
    inner class EnumTests {

        @Test
        @DisplayName("all dimensions have display names")
        fun allDimensionsHaveDisplayNames() {
            QualityDimension.entries.forEach { dim ->
                assertTrue(dim.displayName.isNotBlank())
                assertTrue(dim.description.isNotBlank())
                assertTrue(dim.weight > 0)
            }
        }

        @Test
        @DisplayName("all severities have icons")
        fun allSeveritiesHaveIcons() {
            CritiqueSeverity.entries.forEach { sev ->
                assertTrue(sev.icon.isNotBlank())
            }
        }

        @Test
        @DisplayName("improvement priorities are ordered")
        fun improvementPrioritiesAreOrdered() {
            assertTrue(ImprovementPriority.CRITICAL.value > ImprovementPriority.HIGH.value)
            assertTrue(ImprovementPriority.HIGH.value > ImprovementPriority.MEDIUM.value)
        }
    }
}
