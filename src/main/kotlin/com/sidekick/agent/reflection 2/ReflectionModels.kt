package com.sidekick.agent.reflection

import java.time.Instant
import java.util.UUID

/**
 * # Reflection Models
 *
 * Data models for self-evaluation and critique.
 * Part of Sidekick v0.9.6 Reflection & Critique feature.
 *
 * ## Overview
 *
 * Reflection & Critique enables:
 * - Self-evaluation of agent responses
 * - Multi-dimensional quality assessment
 * - Identification of improvement opportunities
 * - Iterative refinement loops
 * - Learning from reflections
 *
 * @since 0.9.6
 */

// =============================================================================
// Reflection
// =============================================================================

/**
 * A reflection on agent output.
 *
 * @property id Unique reflection identifier
 * @property taskId Associated task ID
 * @property outputType Type of output being reflected on
 * @property originalOutput Original output being evaluated
 * @property evaluation Quality evaluation
 * @property critiques List of critiques
 * @property improvements Suggested improvements
 * @property refinedOutput Improved output after reflection
 * @property confidence Overall confidence (0-1)
 * @property createdAt Creation timestamp
 */
data class Reflection(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val outputType: OutputType,
    val originalOutput: String,
    val evaluation: QualityEvaluation,
    val critiques: List<Critique> = emptyList(),
    val improvements: List<Improvement> = emptyList(),
    val refinedOutput: String? = null,
    val confidence: Float = 0.7f,
    val createdAt: Instant = Instant.now()
) {
    /**
     * Overall quality score (0-1).
     */
    val qualityScore: Float get() = evaluation.overallScore

    /**
     * Whether quality is acceptable.
     */
    val isAcceptable: Boolean get() = evaluation.isAcceptable

    /**
     * Whether output was refined.
     */
    val wasRefined: Boolean get() = refinedOutput != null

    /**
     * Number of critiques.
     */
    val critiqueCount: Int get() = critiques.size

    /**
     * Critical critiques.
     */
    val criticalCritiques: List<Critique>
        get() = critiques.filter { it.severity == CritiqueSeverity.CRITICAL }

    /**
     * Whether refinement is needed.
     */
    val needsRefinement: Boolean
        get() = critiques.any { it.severity.requiresAction } || !evaluation.isAcceptable

    /**
     * Adds a critique.
     */
    fun addCritique(critique: Critique): Reflection =
        copy(critiques = critiques + critique)

    /**
     * Adds an improvement.
     */
    fun addImprovement(improvement: Improvement): Reflection =
        copy(improvements = improvements + improvement)

    /**
     * Sets refined output.
     */
    fun withRefinedOutput(output: String): Reflection =
        copy(refinedOutput = output)

    companion object {
        fun forCode(taskId: String, code: String, evaluation: QualityEvaluation) =
            Reflection(
                taskId = taskId,
                outputType = OutputType.CODE,
                originalOutput = code,
                evaluation = evaluation
            )

        fun forExplanation(taskId: String, explanation: String, evaluation: QualityEvaluation) =
            Reflection(
                taskId = taskId,
                outputType = OutputType.EXPLANATION,
                originalOutput = explanation,
                evaluation = evaluation
            )
    }
}

/**
 * Types of output that can be reflected on.
 */
enum class OutputType(val displayName: String) {
    CODE("Code"),
    EXPLANATION("Explanation"),
    PLAN("Plan"),
    REFACTOR("Refactoring"),
    TEST("Test"),
    DOCUMENTATION("Documentation"),
    ANALYSIS("Analysis"),
    SUGGESTION("Suggestion"),
    OTHER("Other")
}

// =============================================================================
// Quality Evaluation
// =============================================================================

/**
 * Multi-dimensional quality evaluation.
 *
 * @property scores Individual dimension scores
 * @property overallScore Weighted overall score
 * @property summary Evaluation summary
 * @property strengths Identified strengths
 * @property weaknesses Identified weaknesses
 */
data class QualityEvaluation(
    val scores: Map<QualityDimension, Float>,
    val overallScore: Float = scores.values.average().toFloat(),
    val summary: String = "",
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList()
) {
    /**
     * Whether evaluation passes threshold.
     */
    val isAcceptable: Boolean get() = overallScore >= 0.6f

    /**
     * Whether evaluation is excellent.
     */
    val isExcellent: Boolean get() = overallScore >= 0.85f

    /**
     * Gets score for a dimension.
     */
    fun getScore(dimension: QualityDimension): Float =
        scores[dimension] ?: 0.5f

    /**
     * Weakest dimension.
     */
    val weakestDimension: QualityDimension?
        get() = scores.minByOrNull { it.value }?.key

    /**
     * Strongest dimension.
     */
    val strongestDimension: QualityDimension?
        get() = scores.maxByOrNull { it.value }?.key

    companion object {
        fun quick(overall: Float, summary: String = "") =
            QualityEvaluation(
                scores = QualityDimension.entries.associateWith { overall },
                overallScore = overall,
                summary = summary
            )

        fun excellent() = quick(0.95f, "Excellent quality")
        fun good() = quick(0.8f, "Good quality")
        fun acceptable() = quick(0.65f, "Acceptable quality")
        fun poor() = quick(0.4f, "Needs improvement")
    }
}

/**
 * Quality dimensions for evaluation.
 */
enum class QualityDimension(
    val displayName: String,
    val description: String,
    val weight: Float = 1.0f
) {
    /** Code/content correctness */
    CORRECTNESS(
        "Correctness",
        "Is the output factually correct and free of errors?",
        1.5f
    ),

    /** Completeness of response */
    COMPLETENESS(
        "Completeness",
        "Does the output fully address the request?",
        1.2f
    ),

    /** Code/content clarity */
    CLARITY(
        "Clarity",
        "Is the output clear and easy to understand?",
        1.0f
    ),

    /** Efficiency and performance */
    EFFICIENCY(
        "Efficiency",
        "Is the code/solution efficient?",
        0.8f
    ),

    /** Best practices adherence */
    BEST_PRACTICES(
        "Best Practices",
        "Does the output follow industry best practices?",
        1.0f
    ),

    /** Code maintainability */
    MAINTAINABILITY(
        "Maintainability",
        "Is the code easy to maintain and extend?",
        0.9f
    ),

    /** Error handling */
    ROBUSTNESS(
        "Robustness",
        "Does the code handle edge cases and errors?",
        1.0f
    ),

    /** Security considerations */
    SECURITY(
        "Security",
        "Are security best practices followed?",
        1.2f
    ),

    /** Style and formatting */
    STYLE(
        "Style",
        "Does the code follow consistent style?",
        0.5f
    ),

    /** Relevance to task */
    RELEVANCE(
        "Relevance",
        "Is the output relevant to the task?",
        1.3f
    ),

    /** Testability of code */
    TESTABILITY(
        "Testability",
        "Is the code easy to test?",
        0.7f
    ),

    /** Documentation quality */
    DOCUMENTATION(
        "Documentation",
        "Is the code/response well documented?",
        0.6f
    )
}

// =============================================================================
// Critique
// =============================================================================

/**
 * A specific critique of the output.
 *
 * @property id Critique identifier
 * @property dimension Quality dimension affected
 * @property severity Critique severity
 * @property issue Description of the issue
 * @property location Where the issue occurs
 * @property suggestion How to fix the issue
 * @property impact Impact on quality
 */
data class Critique(
    val id: String = UUID.randomUUID().toString(),
    val dimension: QualityDimension,
    val severity: CritiqueSeverity,
    val issue: String,
    val location: String? = null,
    val suggestion: String? = null,
    val impact: Float = 0.1f // How much this affects quality score
) {
    /**
     * Whether this critique requires action.
     */
    val requiresAction: Boolean get() = severity.requiresAction

    /**
     * Whether this is a critical issue.
     */
    val isCritical: Boolean get() = severity == CritiqueSeverity.CRITICAL

    companion object {
        fun critical(dimension: QualityDimension, issue: String, suggestion: String? = null) =
            Critique(
                dimension = dimension,
                severity = CritiqueSeverity.CRITICAL,
                issue = issue,
                suggestion = suggestion,
                impact = 0.3f
            )

        fun major(dimension: QualityDimension, issue: String, suggestion: String? = null) =
            Critique(
                dimension = dimension,
                severity = CritiqueSeverity.MAJOR,
                issue = issue,
                suggestion = suggestion,
                impact = 0.2f
            )

        fun minor(dimension: QualityDimension, issue: String, suggestion: String? = null) =
            Critique(
                dimension = dimension,
                severity = CritiqueSeverity.MINOR,
                issue = issue,
                suggestion = suggestion,
                impact = 0.05f
            )

        fun suggestion(dimension: QualityDimension, issue: String, suggestion: String? = null) =
            Critique(
                dimension = dimension,
                severity = CritiqueSeverity.SUGGESTION,
                issue = issue,
                suggestion = suggestion,
                impact = 0.02f
            )
    }
}

/**
 * Severity levels for critiques.
 */
enum class CritiqueSeverity(
    val displayName: String,
    val requiresAction: Boolean,
    val icon: String
) {
    CRITICAL("Critical", true, "🔴"),
    MAJOR("Major", true, "🟠"),
    MINOR("Minor", false, "🟡"),
    SUGGESTION("Suggestion", false, "💡"),
    PRAISE("Praise", false, "✨")
}

// =============================================================================
// Improvement
// =============================================================================

/**
 * A suggested improvement.
 *
 * @property id Improvement identifier
 * @property type Type of improvement
 * @property description What to improve
 * @property before Original content
 * @property after Suggested replacement
 * @property rationale Why this improves quality
 * @property priority Improvement priority
 * @property effort Estimated effort level
 */
data class Improvement(
    val id: String = UUID.randomUUID().toString(),
    val type: ImprovementType,
    val description: String,
    val before: String? = null,
    val after: String? = null,
    val rationale: String? = null,
    val priority: ImprovementPriority = ImprovementPriority.MEDIUM,
    val effort: EffortLevel = EffortLevel.MEDIUM
) {
    /**
     * Whether this is a high priority improvement.
     */
    val isHighPriority: Boolean get() = priority == ImprovementPriority.HIGH

    /**
     * Whether this has a concrete suggestion.
     */
    val hasConcreteSuggestion: Boolean get() = after != null

    companion object {
        fun refactoring(description: String, before: String, after: String, rationale: String? = null) =
            Improvement(
                type = ImprovementType.REFACTORING,
                description = description,
                before = before,
                after = after,
                rationale = rationale
            )

        fun documentation(description: String) =
            Improvement(
                type = ImprovementType.DOCUMENTATION,
                description = description,
                priority = ImprovementPriority.LOW,
                effort = EffortLevel.LOW
            )

        fun errorHandling(description: String, suggestion: String) =
            Improvement(
                type = ImprovementType.ERROR_HANDLING,
                description = description,
                after = suggestion,
                priority = ImprovementPriority.HIGH
            )
    }
}

/**
 * Types of improvements.
 */
enum class ImprovementType(val displayName: String) {
    REFACTORING("Refactoring"),
    SIMPLIFICATION("Simplification"),
    OPTIMIZATION("Optimization"),
    ERROR_HANDLING("Error Handling"),
    VALIDATION("Validation"),
    DOCUMENTATION("Documentation"),
    TESTING("Testing"),
    SECURITY("Security"),
    NAMING("Naming"),
    STRUCTURE("Structure"),
    STYLE("Style"),
    OTHER("Other")
}

/**
 * Improvement priority levels.
 */
enum class ImprovementPriority(val displayName: String, val value: Int) {
    CRITICAL("Critical", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
    OPTIONAL("Optional", 0)
}

/**
 * Effort levels for improvements.
 */
enum class EffortLevel(val displayName: String, val minutes: Int) {
    TRIVIAL("Trivial", 1),
    LOW("Low", 5),
    MEDIUM("Medium", 15),
    HIGH("High", 30),
    SIGNIFICANT("Significant", 60)
}

// =============================================================================
// Reflection Session
// =============================================================================

/**
 * A reflection session tracking multiple reflections.
 *
 * @property id Session identifier
 * @property taskId Associated task ID
 * @property reflections List of reflections
 * @property iterations Refinement iteration count
 * @property status Session status
 * @property config Session configuration
 * @property createdAt Creation timestamp
 */
data class ReflectionSession(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val reflections: List<Reflection> = emptyList(),
    val iterations: Int = 0,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val config: ReflectionConfig = ReflectionConfig(),
    val createdAt: Instant = Instant.now()
) {
    /**
     * Total reflections.
     */
    val reflectionCount: Int get() = reflections.size

    /**
     * Latest reflection.
     */
    val latestReflection: Reflection? get() = reflections.lastOrNull()

    /**
     * Average quality score across reflections.
     */
    val averageQuality: Float
        get() = if (reflections.isNotEmpty())
            reflections.map { it.qualityScore }.average().toFloat()
        else 0f

    /**
     * Whether max iterations reached.
     */
    val maxIterationsReached: Boolean get() = iterations >= config.maxIterations

    /**
     * Whether latest output is acceptable.
     */
    val isAcceptable: Boolean get() = latestReflection?.isAcceptable == true

    /**
     * Adds a reflection.
     */
    fun addReflection(reflection: Reflection): ReflectionSession =
        copy(
            reflections = reflections + reflection,
            iterations = iterations + 1
        )

    /**
     * Updates status.
     */
    fun withStatus(newStatus: SessionStatus): ReflectionSession =
        copy(status = newStatus)

    enum class SessionStatus {
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}

/**
 * Configuration for reflection sessions.
 */
data class ReflectionConfig(
    val maxIterations: Int = 3,
    val minAcceptableScore: Float = 0.6f,
    val targetScore: Float = 0.8f,
    val dimensions: Set<QualityDimension> = QualityDimension.entries.toSet(),
    val enableAutoRefinement: Boolean = true,
    val stopOnAcceptable: Boolean = true
)

// =============================================================================
// Reflection Events
// =============================================================================

/**
 * Events from reflection sessions.
 */
sealed class ReflectionEvent {
    abstract val sessionId: String
    abstract val timestamp: Instant

    data class ReflectionStarted(
        override val sessionId: String,
        val taskId: String,
        val outputType: OutputType,
        override val timestamp: Instant = Instant.now()
    ) : ReflectionEvent()

    data class EvaluationCompleted(
        override val sessionId: String,
        val overallScore: Float,
        val isAcceptable: Boolean,
        override val timestamp: Instant = Instant.now()
    ) : ReflectionEvent()

    data class CritiquesGenerated(
        override val sessionId: String,
        val critiqueCount: Int,
        val criticalCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : ReflectionEvent()

    data class RefinementApplied(
        override val sessionId: String,
        val iteration: Int,
        val newScore: Float,
        override val timestamp: Instant = Instant.now()
    ) : ReflectionEvent()

    data class SessionCompleted(
        override val sessionId: String,
        val finalScore: Float,
        val totalIterations: Int,
        val improved: Boolean,
        override val timestamp: Instant = Instant.now()
    ) : ReflectionEvent()
}

// =============================================================================
// Reflection Result
// =============================================================================

/**
 * Final result of a reflection session.
 */
data class ReflectionResult(
    val sessionId: String,
    val taskId: String,
    val success: Boolean,
    val originalOutput: String,
    val finalOutput: String,
    val initialScore: Float,
    val finalScore: Float,
    val totalIterations: Int,
    val allCritiques: List<Critique>,
    val appliedImprovements: List<Improvement>,
    val durationMs: Long
) {
    /**
     * Score improvement.
     */
    val scoreImprovement: Float get() = finalScore - initialScore

    /**
     * Whether output was improved.
     */
    val wasImproved: Boolean get() = finalOutput != originalOutput

    /**
     * Improvement percentage.
     */
    val improvementPercentage: Float
        get() = if (initialScore > 0) ((finalScore - initialScore) / initialScore) * 100 else 0f
}

// =============================================================================
// Critique Request
// =============================================================================

/**
 * Request for generating critiques.
 */
data class CritiqueRequest(
    val output: String,
    val outputType: OutputType,
    val context: String? = null,
    val dimensions: Set<QualityDimension> = QualityDimension.entries.toSet(),
    val focusAreas: List<String> = emptyList(),
    val maxCritiques: Int = 10
)
