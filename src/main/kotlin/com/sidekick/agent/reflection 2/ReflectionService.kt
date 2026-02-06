package com.sidekick.agent.reflection

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Reflection Service
 *
 * Service for self-evaluation and iterative refinement.
 * Part of Sidekick v0.9.6 Reflection & Critique feature.
 *
 * ## Features
 *
 * - Multi-dimensional quality evaluation
 * - Critique generation and analysis
 * - Improvement suggestion
 * - Iterative refinement loops
 * - Reflection history tracking
 *
 * @since 0.9.6
 */
class ReflectionService(
    private val config: ReflectionConfig = ReflectionConfig(),
    private val evaluator: suspend (String, OutputType, Set<QualityDimension>) -> QualityEvaluation = { _, _, _ ->
        QualityEvaluation.good()
    },
    private val refiner: suspend (String, List<Critique>, List<Improvement>) -> String = { original, _, _ ->
        original
    }
) {
    private val sessions = ConcurrentHashMap<String, ReflectionSession>()
    private val eventListeners = CopyOnWriteArrayList<(ReflectionEvent) -> Unit>()

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Creates a reflection session.
     */
    fun createSession(taskId: String, config: ReflectionConfig = this.config): ReflectionSession {
        val session = ReflectionSession(taskId = taskId, config = config)
        sessions[session.id] = session
        return session
    }

    /**
     * Gets a session by ID.
     */
    fun getSession(sessionId: String): ReflectionSession? = sessions[sessionId]

    /**
     * Gets all active sessions.
     */
    fun getActiveSessions(): List<ReflectionSession> =
        sessions.values.filter { it.status == ReflectionSession.SessionStatus.ACTIVE }

    /**
     * Ends a session.
     */
    fun endSession(sessionId: String, success: Boolean = true): ReflectionSession? {
        val session = sessions[sessionId] ?: return null
        val status = if (success) ReflectionSession.SessionStatus.COMPLETED
            else ReflectionSession.SessionStatus.FAILED
        val updated = session.withStatus(status)
        sessions[sessionId] = updated

        emitEvent(ReflectionEvent.SessionCompleted(
            sessionId = sessionId,
            finalScore = session.latestReflection?.qualityScore ?: 0f,
            totalIterations = session.iterations,
            improved = session.reflections.size > 1 &&
                    (session.reflections.last().qualityScore > session.reflections.first().qualityScore)
        ))

        return updated
    }

    // =========================================================================
    // Evaluation
    // =========================================================================

    /**
     * Evaluates output quality.
     */
    suspend fun evaluate(
        output: String,
        outputType: OutputType,
        dimensions: Set<QualityDimension> = config.dimensions
    ): QualityEvaluation {
        return evaluator(output, outputType, dimensions)
    }

    /**
     * Quick evaluation using built-in heuristics.
     */
    fun quickEvaluate(output: String, outputType: OutputType): QualityEvaluation {
        val scores = mutableMapOf<QualityDimension, Float>()

        // Correctness - check for obvious issues
        scores[QualityDimension.CORRECTNESS] = evaluateCorrectness(output, outputType)

        // Completeness - check if output seems complete
        scores[QualityDimension.COMPLETENESS] = evaluateCompleteness(output)

        // Clarity - check readability
        scores[QualityDimension.CLARITY] = evaluateClarity(output)

        // Best practices
        scores[QualityDimension.BEST_PRACTICES] = evaluateBestPractices(output, outputType)

        // Style
        scores[QualityDimension.STYLE] = evaluateStyle(output)

        // Relevance (default to good)
        scores[QualityDimension.RELEVANCE] = 0.8f

        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()

        scores.forEach { (dim, score) ->
            if (score >= 0.8f) strengths.add(dim.displayName)
            else if (score < 0.5f) weaknesses.add(dim.displayName)
        }

        return QualityEvaluation(
            scores = scores,
            strengths = strengths,
            weaknesses = weaknesses
        )
    }

    private fun evaluateCorrectness(output: String, outputType: OutputType): Float {
        var score = 0.8f

        if (outputType == OutputType.CODE) {
            // Check for syntax issues
            val braceBalance = output.count { it == '{' } - output.count { it == '}' }
            val parenBalance = output.count { it == '(' } - output.count { it == ')' }

            if (braceBalance != 0) score -= 0.3f
            if (parenBalance != 0) score -= 0.2f

            // Check for TODO placeholders
            if (output.contains("TODO") || output.contains("FIXME")) score -= 0.1f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun evaluateCompleteness(output: String): Float {
        var score = 0.8f

        // Check for incomplete indicators
        if (output.endsWith("...") || output.endsWith("…")) score -= 0.3f
        if (output.contains("// TODO") || output.contains("// ...")) score -= 0.2f
        if (output.length < 50) score -= 0.2f

        // Check for unclosed blocks
        if (output.contains("```") && output.count { it == '`' } % 6 != 0) score -= 0.2f

        return score.coerceIn(0f, 1f)
    }

    private fun evaluateClarity(output: String): Float {
        var score = 0.8f

        val lines = output.lines()

        // Check line lengths
        val longLines = lines.count { it.length > 120 }
        if (longLines > 3) score -= 0.1f

        // Check for comments (positive for clarity)
        if (output.contains("//") || output.contains("/*")) score += 0.1f

        // Check for meaningful names (heuristic)
        if (output.contains("temp") || output.contains("data1")) score -= 0.1f

        return score.coerceIn(0f, 1f)
    }

    private fun evaluateBestPractices(output: String, outputType: OutputType): Float {
        var score = 0.8f

        if (outputType == OutputType.CODE) {
            // Check for error handling
            if (!output.contains("try") && !output.contains("catch") &&
                !output.contains("throw") && !output.contains("Result")) {
                score -= 0.1f
            }

            // Check for hardcoded values
            if (Regex(""""[^"]{20,}"""").containsMatchIn(output)) score -= 0.1f

            // Check for magic numbers
            if (Regex("""\b\d{3,}\b""").containsMatchIn(output)) score -= 0.05f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun evaluateStyle(output: String): Float {
        var score = 0.8f

        // Check indentation consistency
        val lines = output.lines()
        val indents = lines.map { it.takeWhile { c -> c == ' ' || c == '\t' }.length }
        val mixedTabs = lines.any { it.startsWith(" ") } && lines.any { it.startsWith("\t") }
        if (mixedTabs) score -= 0.2f

        // Check for trailing whitespace
        if (lines.any { it.endsWith(" ") }) score -= 0.05f

        return score.coerceIn(0f, 1f)
    }

    // =========================================================================
    // Critique Generation
    // =========================================================================

    /**
     * Generates critiques for output.
     */
    fun generateCritiques(
        output: String,
        outputType: OutputType,
        evaluation: QualityEvaluation
    ): List<Critique> {
        val critiques = mutableListOf<Critique>()

        // Generate critiques based on low scores
        evaluation.scores.forEach { (dimension, score) ->
            if (score < 0.5f) {
                critiques.add(Critique.major(
                    dimension = dimension,
                    issue = "${dimension.displayName} score is low (${"%.0f".format(score * 100)}%)",
                    suggestion = "Review and improve ${dimension.displayName.lowercase()}"
                ))
            } else if (score < 0.7f) {
                critiques.add(Critique.minor(
                    dimension = dimension,
                    issue = "${dimension.displayName} could be improved",
                    suggestion = "Consider improving ${dimension.displayName.lowercase()}"
                ))
            }
        }

        // Add specific critiques based on content analysis
        critiques.addAll(analyzeContent(output, outputType))

        return critiques.take(10)
    }

    private fun analyzeContent(output: String, outputType: OutputType): List<Critique> {
        val critiques = mutableListOf<Critique>()

        if (outputType == OutputType.CODE) {
            // Check for unbalanced braces
            if (output.count { it == '{' } != output.count { it == '}' }) {
                critiques.add(Critique.critical(
                    dimension = QualityDimension.CORRECTNESS,
                    issue = "Unbalanced braces detected",
                    suggestion = "Check opening and closing braces"
                ))
            }

            // Check for TODO comments
            if (output.contains("TODO")) {
                critiques.add(Critique.minor(
                    dimension = QualityDimension.COMPLETENESS,
                    issue = "Contains TODO comments",
                    suggestion = "Complete all TODO items"
                ))
            }

            // Check for empty catch blocks
            if (output.contains("catch") && output.contains("{ }")) {
                critiques.add(Critique.major(
                    dimension = QualityDimension.ROBUSTNESS,
                    issue = "Empty catch block detected",
                    suggestion = "Handle or log the exception"
                ))
            }

            // Check for println debugging
            if (output.contains("println") && output.contains("debug", ignoreCase = true)) {
                critiques.add(Critique.minor(
                    dimension = QualityDimension.BEST_PRACTICES,
                    issue = "Debug println statements found",
                    suggestion = "Use proper logging"
                ))
            }
        }

        // Check for incomplete sentences
        if (output.endsWith("...") || output.endsWith("…")) {
            critiques.add(Critique.major(
                dimension = QualityDimension.COMPLETENESS,
                issue = "Response appears truncated",
                suggestion = "Complete the response"
            ))
        }

        return critiques
    }

    /**
     * Generates critiques from a request.
     */
    fun generateCritiques(request: CritiqueRequest): List<Critique> {
        val evaluation = quickEvaluate(request.output, request.outputType)
        return generateCritiques(request.output, request.outputType, evaluation)
            .take(request.maxCritiques)
    }

    // =========================================================================
    // Improvement Suggestions
    // =========================================================================

    /**
     * Suggests improvements based on critiques.
     */
    fun suggestImprovements(critiques: List<Critique>): List<Improvement> {
        return critiques.mapNotNull { critique ->
            critique.suggestion?.let { suggestion ->
                Improvement(
                    type = mapDimensionToImprovementType(critique.dimension),
                    description = suggestion,
                    priority = mapSeverityToPriority(critique.severity),
                    rationale = critique.issue
                )
            }
        }
    }

    private fun mapDimensionToImprovementType(dimension: QualityDimension): ImprovementType {
        return when (dimension) {
            QualityDimension.CORRECTNESS -> ImprovementType.REFACTORING
            QualityDimension.COMPLETENESS -> ImprovementType.REFACTORING
            QualityDimension.CLARITY -> ImprovementType.SIMPLIFICATION
            QualityDimension.EFFICIENCY -> ImprovementType.OPTIMIZATION
            QualityDimension.BEST_PRACTICES -> ImprovementType.REFACTORING
            QualityDimension.MAINTAINABILITY -> ImprovementType.STRUCTURE
            QualityDimension.ROBUSTNESS -> ImprovementType.ERROR_HANDLING
            QualityDimension.SECURITY -> ImprovementType.SECURITY
            QualityDimension.STYLE -> ImprovementType.STYLE
            QualityDimension.RELEVANCE -> ImprovementType.REFACTORING
            QualityDimension.TESTABILITY -> ImprovementType.TESTING
            QualityDimension.DOCUMENTATION -> ImprovementType.DOCUMENTATION
        }
    }

    private fun mapSeverityToPriority(severity: CritiqueSeverity): ImprovementPriority {
        return when (severity) {
            CritiqueSeverity.CRITICAL -> ImprovementPriority.CRITICAL
            CritiqueSeverity.MAJOR -> ImprovementPriority.HIGH
            CritiqueSeverity.MINOR -> ImprovementPriority.MEDIUM
            CritiqueSeverity.SUGGESTION -> ImprovementPriority.LOW
            CritiqueSeverity.PRAISE -> ImprovementPriority.OPTIONAL
        }
    }

    // =========================================================================
    // Reflection Loop
    // =========================================================================

    /**
     * Performs a single reflection on output.
     */
    suspend fun reflect(
        taskId: String,
        output: String,
        outputType: OutputType,
        sessionId: String? = null
    ): Reflection {
        val session = sessionId?.let { getSession(it) }
            ?: createSession(taskId)

        emitEvent(ReflectionEvent.ReflectionStarted(session.id, taskId, outputType))

        // Evaluate
        val evaluation = evaluate(output, outputType, config.dimensions)
        emitEvent(ReflectionEvent.EvaluationCompleted(session.id, evaluation.overallScore, evaluation.isAcceptable))

        // Generate critiques
        val critiques = generateCritiques(output, outputType, evaluation)
        emitEvent(ReflectionEvent.CritiquesGenerated(
            session.id,
            critiques.size,
            critiques.count { it.isCritical }
        ))

        // Suggest improvements
        val improvements = suggestImprovements(critiques)

        // Create reflection
        val reflection = Reflection(
            taskId = taskId,
            outputType = outputType,
            originalOutput = output,
            evaluation = evaluation,
            critiques = critiques,
            improvements = improvements
        )

        // Update session
        sessions[session.id] = session.addReflection(reflection)

        return reflection
    }

    /**
     * Performs iterative reflection and refinement.
     */
    suspend fun iterativeReflect(
        taskId: String,
        output: String,
        outputType: OutputType,
        maxIterations: Int = config.maxIterations
    ): ReflectionResult {
        val startTime = Instant.now()
        val session = createSession(taskId, config.copy(maxIterations = maxIterations))

        var currentOutput = output
        var currentReflection: Reflection? = null
        val allCritiques = mutableListOf<Critique>()
        val appliedImprovements = mutableListOf<Improvement>()

        for (i in 0 until maxIterations) {
            // Reflect on current output
            currentReflection = reflect(taskId, currentOutput, outputType, session.id)
            allCritiques.addAll(currentReflection.critiques)

            // Check if acceptable
            if (currentReflection.isAcceptable && config.stopOnAcceptable) {
                break
            }

            // Refine if enabled and needed
            if (config.enableAutoRefinement && currentReflection.needsRefinement) {
                val refined = refiner(
                    currentOutput,
                    currentReflection.critiques,
                    currentReflection.improvements
                )

                if (refined != currentOutput) {
                    appliedImprovements.addAll(currentReflection.improvements)
                    currentOutput = refined

                    emitEvent(ReflectionEvent.RefinementApplied(
                        session.id,
                        i + 1,
                        currentReflection.qualityScore
                    ))
                } else {
                    // No changes, stop iterating
                    break
                }
            } else {
                break
            }
        }

        // End session
        val finalSession = endSession(session.id, currentReflection?.isAcceptable == true)

        val endTime = Instant.now()

        return ReflectionResult(
            sessionId = session.id,
            taskId = taskId,
            success = currentReflection?.isAcceptable == true,
            originalOutput = output,
            finalOutput = currentOutput,
            initialScore = finalSession?.reflections?.firstOrNull()?.qualityScore ?: 0f,
            finalScore = currentReflection?.qualityScore ?: 0f,
            totalIterations = finalSession?.iterations ?: 0,
            allCritiques = allCritiques,
            appliedImprovements = appliedImprovements,
            durationMs = endTime.toEpochMilli() - startTime.toEpochMilli()
        )
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets reflection statistics.
     */
    fun getStats(): ReflectionStats {
        val allSessions = sessions.values.toList()
        val allReflections = allSessions.flatMap { it.reflections }

        return ReflectionStats(
            totalSessions = allSessions.size,
            activeSessions = allSessions.count { it.status == ReflectionSession.SessionStatus.ACTIVE },
            completedSessions = allSessions.count { it.status == ReflectionSession.SessionStatus.COMPLETED },
            totalReflections = allReflections.size,
            averageQuality = if (allReflections.isNotEmpty())
                allReflections.map { it.qualityScore }.average().toFloat() else 0f,
            totalCritiques = allReflections.sumOf { it.critiqueCount },
            averageIterations = if (allSessions.isNotEmpty())
                allSessions.map { it.iterations }.average().toFloat() else 0f
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (ReflectionEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (ReflectionEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: ReflectionEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Clears all sessions.
     */
    fun clearSessions() {
        sessions.clear()
    }
}

/**
 * Reflection statistics.
 */
data class ReflectionStats(
    val totalSessions: Int,
    val activeSessions: Int,
    val completedSessions: Int,
    val totalReflections: Int,
    val averageQuality: Float,
    val totalCritiques: Int,
    val averageIterations: Float
)
