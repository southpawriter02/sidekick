package com.sidekick.agent.correction

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Self-Correction Service
 *
 * Service for detecting and correcting errors in agent output.
 * Part of Sidekick v0.9.4 Self-Correction feature.
 *
 * ## Features
 *
 * - Detect errors in generated code and responses
 * - Apply appropriate correction strategies
 * - Validate corrections
 * - Track correction sessions and statistics
 * - Learn from corrections over time
 *
 * @since 0.9.4
 */
class SelfCorrectionService(
    private val detectorConfig: ErrorDetectorConfig = ErrorDetectorConfig(),
    private val corrector: suspend (DetectedError, String, CorrectionStrategy) -> String = { _, content, _ -> content },
    private val validator: suspend (String, ValidationCategory) -> ValidationCheck = { _, category ->
        ValidationCheck.pass("validation", "Passed", category)
    }
) {
    private val sessions = ConcurrentHashMap<String, CorrectionSession>()
    private val eventListeners = CopyOnWriteArrayList<(CorrectionEvent) -> Unit>()

    // Error patterns for detection
    private val syntaxPatterns = listOf(
        Regex("""error:\s*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""unresolved reference:\s*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""expecting\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val hallucinationIndicators = listOf(
        "nonexistent", "deprecated since", "not a real", "fabricated",
        "doesn't exist", "made up", "imaginary api"
    )

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Creates a new correction session.
     */
    fun createSession(
        taskId: String,
        config: CorrectionConfig = CorrectionConfig()
    ): CorrectionSession {
        val session = CorrectionSession(taskId = taskId, config = config)
        sessions[session.id] = session
        return session
    }

    /**
     * Gets a session by ID.
     */
    fun getSession(sessionId: String): CorrectionSession? = sessions[sessionId]

    /**
     * Gets all active sessions.
     */
    fun getActiveSessions(): List<CorrectionSession> =
        sessions.values.filter { it.status == CorrectionSession.SessionStatus.ACTIVE }

    /**
     * Ends a session.
     */
    fun endSession(sessionId: String, success: Boolean = true): CorrectionSession? {
        val session = sessions[sessionId] ?: return null
        val status = if (success) CorrectionSession.SessionStatus.COMPLETED
                     else CorrectionSession.SessionStatus.FAILED

        val updated = session.withStatus(status)
        sessions[sessionId] = updated

        emitEvent(CorrectionEvent.SessionCompleted(
            sessionId,
            session.errorCount,
            session.successfulAttempts,
            session.totalAttempts
        ))

        return updated
    }

    // =========================================================================
    // Error Detection
    // =========================================================================

    /**
     * Detects errors in content.
     */
    fun detectErrors(
        content: String,
        context: String? = null,
        sessionId: String? = null
    ): List<DetectedError> {
        val errors = mutableListOf<DetectedError>()

        // Syntax errors
        if (detectorConfig.enableSyntaxCheck) {
            errors.addAll(detectSyntaxErrors(content))
        }

        // Type errors
        if (detectorConfig.enableTypeCheck) {
            errors.addAll(detectTypeErrors(content))
        }

        // Hallucinations
        if (detectorConfig.enableHallucinationDetection) {
            errors.addAll(detectHallucinations(content))
        }

        // Incomplete responses
        errors.addAll(detectIncompleteResponse(content))

        // Security issues
        if (detectorConfig.enableSecurityCheck) {
            errors.addAll(detectSecurityIssues(content))
        }

        // Filter by confidence threshold
        val filtered = errors.filter { it.confidence >= detectorConfig.minConfidence }

        // Add to session if provided
        sessionId?.let { id ->
            val session = sessions[id]
            if (session != null) {
                var updated: CorrectionSession = session
                filtered.forEach { error ->
                    updated = updated.addError(error)
                    emitEvent(CorrectionEvent.ErrorDetected(id, error.id, error.type, error.severity))
                }
                sessions[id] = updated
            }
        }

        return filtered
    }

    private fun detectSyntaxErrors(content: String): List<DetectedError> {
        val errors = mutableListOf<DetectedError>()

        // Check for unbalanced braces
        val openBraces = content.count { it == '{' }
        val closeBraces = content.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add(DetectedError.syntax(
                "Unbalanced braces: $openBraces open, $closeBraces close",
                fix = "Add ${kotlin.math.abs(openBraces - closeBraces)} ${if (openBraces > closeBraces) "closing" else "opening"} brace(s)"
            ))
        }

        // Check for unbalanced parentheses
        val openParens = content.count { it == '(' }
        val closeParens = content.count { it == ')' }
        if (openParens != closeParens) {
            errors.add(DetectedError.syntax(
                "Unbalanced parentheses: $openParens open, $closeParens close"
            ))
        }

        // Check for common syntax patterns in error messages
        syntaxPatterns.forEach { pattern ->
            pattern.find(content)?.let { match ->
                errors.add(DetectedError.syntax(match.groupValues.getOrElse(1) { match.value }))
            }
        }

        return errors
    }

    private fun detectTypeErrors(content: String): List<DetectedError> {
        val errors = mutableListOf<DetectedError>()

        // Type mismatch patterns
        val typeMismatchPattern = Regex("""type mismatch.*expected:?\s*(\w+).*found:?\s*(\w+)""", RegexOption.IGNORE_CASE)
        typeMismatchPattern.find(content)?.let { match ->
            errors.add(DetectedError(
                type = ErrorType.TYPE_ERROR,
                severity = ErrorSeverity.HIGH,
                description = "Type mismatch: expected ${match.groupValues[1]}, found ${match.groupValues[2]}",
                confidence = 0.9f
            ))
        }

        return errors
    }

    private fun detectHallucinations(content: String): List<DetectedError> {
        val errors = mutableListOf<DetectedError>()
        val lowerContent = content.lowercase()

        hallucinationIndicators.forEach { indicator ->
            if (lowerContent.contains(indicator)) {
                errors.add(DetectedError.hallucination(
                    "Possible hallucination detected: content mentions '$indicator'",
                    confidence = 0.6f
                ))
            }
        }

        // Check for obviously fake API patterns
        val fakeApiPattern = Regex("""(\w+)\.doMagic\(|autoSolve\(|fixEverything\(""")
        fakeApiPattern.find(content)?.let {
            errors.add(DetectedError.hallucination(
                "Suspicious API call detected: ${it.value}",
                confidence = 0.8f
            ))
        }

        return errors
    }

    private fun detectIncompleteResponse(content: String): List<DetectedError> {
        val errors = mutableListOf<DetectedError>()

        // Check for very short content (likely incomplete)
        if (content.length < 50) {
            errors.add(DetectedError.incomplete("Response is too short (${content.length} chars)"))
        }

        // Check for truncated content
        if (content.endsWith("...") || content.endsWith("…")) {
            errors.add(DetectedError.incomplete("Response appears to be truncated"))
        }

        // Check for incomplete code blocks
        val codeBlockStarts = content.count { content.contains("```") } / 2
        val codeBlockEnds = Regex("```\n").findAll(content).count()
        if (content.contains("```") && !content.trimEnd().endsWith("```")) {
            val afterLastBlock = content.substringAfterLast("```")
            if (afterLastBlock.isNotBlank() && !afterLastBlock.trim().startsWith("\n")) {
                errors.add(DetectedError.incomplete("Unclosed code block detected"))
            }
        }

        // Check for TODO markers indicating incomplete implementation
        val todoPattern = Regex("""//\s*TODO|/\*\s*TODO|#\s*TODO""", RegexOption.IGNORE_CASE)
        val todoCount = todoPattern.findAll(content).count()
        if (todoCount > 3) {
            errors.add(DetectedError.incomplete(
                "Multiple TODO markers ($todoCount) suggest incomplete implementation"
            ))
        }

        return errors
    }

    private fun detectSecurityIssues(content: String): List<DetectedError> {
        val errors = mutableListOf<DetectedError>()

        // SQL injection patterns
        if (content.contains("\"SELECT") && content.contains("+ ")) {
            errors.add(DetectedError(
                type = ErrorType.SECURITY_ISSUE,
                severity = ErrorSeverity.CRITICAL,
                description = "Possible SQL injection vulnerability: string concatenation in SQL query",
                suggestedFix = "Use parameterized queries or prepared statements",
                confidence = 0.85f
            ))
        }

        // Hardcoded secrets
        val secretPatterns = listOf(
            Regex("""password\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE),
            Regex("""api[_-]?key\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE),
            Regex("""secret\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE)
        )
        secretPatterns.forEach { pattern ->
            pattern.find(content)?.let {
                errors.add(DetectedError(
                    type = ErrorType.SECURITY_ISSUE,
                    severity = ErrorSeverity.HIGH,
                    description = "Hardcoded credential detected",
                    suggestedFix = "Use environment variables or secure configuration",
                    confidence = 0.9f
                ))
            }
        }

        return errors
    }

    // =========================================================================
    // Correction
    // =========================================================================

    /**
     * Corrects a single error.
     */
    suspend fun correctError(
        sessionId: String,
        errorId: String,
        content: String,
        strategy: CorrectionStrategy? = null
    ): CorrectionAttempt? {
        val session = sessions[sessionId] ?: return null
        val error = session.errors.find { it.id == errorId } ?: return null

        // Check attempt limits
        val existingAttempts = session.attempts.count { it.errorId == errorId }
        if (existingAttempts >= session.config.maxAttemptsPerError) {
            return null
        }

        val selectedStrategy = strategy ?: error.type.defaultStrategy
        val attemptNumber = existingAttempts + 1

        // Create attempt
        var attempt = CorrectionAttempt(
            errorId = errorId,
            strategy = selectedStrategy,
            originalContent = content,
            attemptNumber = attemptNumber
        )

        // Update session
        var updatedSession = session.addAttempt(attempt)
        sessions[sessionId] = updatedSession

        emitEvent(CorrectionEvent.CorrectionStarted(sessionId, attempt.id, errorId, selectedStrategy))

        // Start correction
        attempt = attempt.start()

        return try {
            // Apply correction
            val corrected = corrector(error, content, selectedStrategy)

            // Validate if configured
            val validation = if (session.config.validateAfterCorrection) {
                validateCorrection(corrected, error.type)
            } else null

            if (validation?.passed != false) {
                attempt = attempt.succeed(corrected, validation)
                emitEvent(CorrectionEvent.CorrectionSucceeded(sessionId, attempt.id, errorId))
            } else {
                attempt = attempt.fail(validation.message ?: "Validation failed")
                emitEvent(CorrectionEvent.CorrectionFailed(sessionId, attempt.id, errorId, validation.message ?: "Validation failed"))
            }

            // Update session with final attempt
            updatedSession = sessions[sessionId]!!.copy(
                attempts = sessions[sessionId]!!.attempts.map { if (it.id == attempt.id) attempt else it }
            )
            sessions[sessionId] = updatedSession

            attempt
        } catch (e: Exception) {
            attempt = attempt.fail(e.message ?: "Unknown error")
            emitEvent(CorrectionEvent.CorrectionFailed(sessionId, attempt.id, errorId, e.message ?: "Unknown error"))

            updatedSession = sessions[sessionId]!!.copy(
                attempts = sessions[sessionId]!!.attempts.map { if (it.id == attempt.id) attempt else it }
            )
            sessions[sessionId] = updatedSession

            attempt
        }
    }

    /**
     * Corrects all errors in a session.
     */
    suspend fun correctAllErrors(
        sessionId: String,
        content: String
    ): CorrectionResult {
        val startTime = Instant.now()
        val session = sessions[sessionId] ?: return CorrectionResult(
            sessionId = sessionId,
            taskId = "",
            success = false,
            originalContent = content,
            finalContent = content,
            errorsDetected = 0,
            errorsCorrected = 0,
            totalAttempts = 0,
            validationResult = null,
            remainingErrors = emptyList(),
            durationMs = 0
        )

        var currentContent = content

        // Sort errors by severity (critical first)
        val sortedErrors = session.errors.sortedByDescending { it.severity.priority }

        for (error in sortedErrors) {
            if (session.maxAttemptsReached) break

            val attempt = correctError(sessionId, error.id, currentContent)
            if (attempt?.isSuccessful == true && attempt.correctedContent != null) {
                currentContent = attempt.correctedContent
            }
        }

        val endTime = Instant.now()
        val finalSession = sessions[sessionId]!!

        // Final validation
        val finalValidation = validateContent(currentContent)
        emitEvent(CorrectionEvent.ValidationCompleted(sessionId, finalValidation.passed, finalValidation.passRate))

        endSession(sessionId, finalSession.allCorrected && finalValidation.passed)

        return CorrectionResult(
            sessionId = sessionId,
            taskId = session.taskId,
            success = finalSession.allCorrected && finalValidation.passed,
            originalContent = content,
            finalContent = currentContent,
            errorsDetected = finalSession.errorCount,
            errorsCorrected = finalSession.successfulAttempts,
            totalAttempts = finalSession.totalAttempts,
            validationResult = finalValidation,
            remainingErrors = finalSession.uncorrectedErrors,
            durationMs = endTime.toEpochMilli() - startTime.toEpochMilli()
        )
    }

    /**
     * Performs iterative correction until all errors are fixed or max attempts reached.
     */
    suspend fun iterativeCorrection(
        taskId: String,
        content: String,
        maxIterations: Int = 3
    ): CorrectionResult {
        val session = createSession(taskId)
        var currentContent = content

        for (iteration in 1..maxIterations) {
            // Detect errors
            val errors = detectErrors(currentContent, sessionId = session.id)
            if (errors.isEmpty()) break

            // Correct all errors
            val result = correctAllErrors(session.id, currentContent)
            currentContent = result.finalContent

            // Check if all corrected
            if (!result.hasRemainingErrors) break
        }

        val finalSession = sessions[session.id]!!
        endSession(session.id, finalSession.allCorrected)

        return CorrectionResult(
            sessionId = session.id,
            taskId = taskId,
            success = finalSession.allCorrected,
            originalContent = content,
            finalContent = currentContent,
            errorsDetected = finalSession.errorCount,
            errorsCorrected = finalSession.successfulAttempts,
            totalAttempts = finalSession.totalAttempts,
            validationResult = validateContent(currentContent),
            remainingErrors = finalSession.uncorrectedErrors,
            durationMs = Instant.now().toEpochMilli() - session.createdAt.toEpochMilli()
        )
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Validates corrected content.
     */
    suspend fun validateCorrection(content: String, errorType: ErrorType): ValidationResult {
        val category = when (errorType) {
            ErrorType.SYNTAX_ERROR -> ValidationCategory.SYNTAX
            ErrorType.TYPE_ERROR -> ValidationCategory.TYPES
            ErrorType.SECURITY_ISSUE -> ValidationCategory.SECURITY
            ErrorType.PERFORMANCE_ISSUE -> ValidationCategory.PERFORMANCE
            ErrorType.TEST_FAILURE -> ValidationCategory.TESTS
            ErrorType.BUILD_FAILURE -> ValidationCategory.BUILD
            ErrorType.RUNTIME_ERROR -> ValidationCategory.RUNTIME
            ErrorType.STYLE_VIOLATION -> ValidationCategory.STYLE
            else -> ValidationCategory.GENERAL
        }

        val check = validator(content, category)
        return ValidationResult.fromChecks(listOf(check))
    }

    /**
     * Validates content with multiple checks.
     */
    suspend fun validateContent(content: String): ValidationResult {
        val checks = mutableListOf<ValidationCheck>()

        // Syntax check
        val syntaxErrors = detectSyntaxErrors(content)
        checks.add(
            if (syntaxErrors.isEmpty()) ValidationCheck.pass("syntax", "No syntax errors", ValidationCategory.SYNTAX)
            else ValidationCheck.fail("syntax", "Found ${syntaxErrors.size} syntax error(s)", ValidationCategory.SYNTAX)
        )

        // Security check
        if (detectorConfig.enableSecurityCheck) {
            val securityErrors = detectSecurityIssues(content)
            checks.add(
                if (securityErrors.isEmpty()) ValidationCheck.pass("security", "No security issues", ValidationCategory.SECURITY)
                else ValidationCheck.fail("security", "Found ${securityErrors.size} security issue(s)", ValidationCategory.SECURITY)
            )
        }

        // Completeness check
        val incompleteErrors = detectIncompleteResponse(content)
        checks.add(
            if (incompleteErrors.isEmpty()) ValidationCheck.pass("completeness", "Response is complete", ValidationCategory.GENERAL)
            else ValidationCheck.fail("completeness", "Response appears incomplete", ValidationCategory.GENERAL)
        )

        return ValidationResult.fromChecks(checks)
    }

    // =========================================================================
    // Strategy Selection
    // =========================================================================

    /**
     * Suggests the best correction strategy for an error.
     */
    fun suggestStrategy(error: DetectedError, previousAttempts: Int = 0): CorrectionStrategy {
        // If previous attempts failed, escalate strategy
        return when {
            previousAttempts >= 2 -> CorrectionStrategy.FULL_REGENERATION
            previousAttempts == 1 -> CorrectionStrategy.REGENERATE_SECTION
            else -> CorrectionStrategy.TARGETED_FIX
        }
    }

    /**
     * Gets available strategies for an error type.
     */
    fun getStrategiesForError(errorType: ErrorType): List<CorrectionStrategy> {
        return when (errorType) {
            ErrorType.SYNTAX_ERROR -> listOf(
                CorrectionStrategy.TARGETED_FIX,
                CorrectionStrategy.REGENERATE_SECTION,
                CorrectionStrategy.FULL_REGENERATION
            )
            ErrorType.INCOMPLETE_RESPONSE -> listOf(
                CorrectionStrategy.CONTINUE_GENERATION,
                CorrectionStrategy.REGENERATE_SECTION
            )
            ErrorType.HALLUCINATION -> listOf(
                CorrectionStrategy.FULL_REGENERATION,
                CorrectionStrategy.REGENERATE_WITH_CONTEXT
            )
            ErrorType.MISSING_IMPORT -> listOf(
                CorrectionStrategy.ADD_MISSING,
                CorrectionStrategy.TARGETED_FIX
            )
            else -> listOf(
                CorrectionStrategy.TARGETED_FIX,
                CorrectionStrategy.REGENERATE_SECTION,
                CorrectionStrategy.FULL_REGENERATION
            )
        }
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets correction statistics.
     */
    fun getStats(): CorrectionStats {
        val allSessions = sessions.values.toList()
        val allAttempts = allSessions.flatMap { it.attempts }
        val allErrors = allSessions.flatMap { it.errors }

        return CorrectionStats(
            totalSessions = allSessions.size,
            activeSessions = allSessions.count { it.status == CorrectionSession.SessionStatus.ACTIVE },
            totalErrors = allErrors.size,
            totalAttempts = allAttempts.size,
            successfulCorrections = allAttempts.count { it.isSuccessful },
            errorsByType = allErrors.groupBy { it.type }.mapValues { it.value.size },
            errorsBySeverity = allErrors.groupBy { it.severity }.mapValues { it.value.size },
            strategiesUsed = allAttempts.groupBy { it.strategy }.mapValues { it.value.size },
            averageAttemptsPerError = if (allErrors.isNotEmpty())
                allAttempts.size.toFloat() / allErrors.size else 0f
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (CorrectionEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (CorrectionEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: CorrectionEvent) {
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
 * Correction statistics.
 */
data class CorrectionStats(
    val totalSessions: Int,
    val activeSessions: Int,
    val totalErrors: Int,
    val totalAttempts: Int,
    val successfulCorrections: Int,
    val errorsByType: Map<ErrorType, Int>,
    val errorsBySeverity: Map<ErrorSeverity, Int>,
    val strategiesUsed: Map<CorrectionStrategy, Int>,
    val averageAttemptsPerError: Float
) {
    /**
     * Overall success rate.
     */
    val successRate: Float
        get() = if (totalAttempts > 0) successfulCorrections.toFloat() / totalAttempts else 0f
}
