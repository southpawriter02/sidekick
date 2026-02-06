package com.sidekick.agent.correction

import java.time.Instant
import java.util.UUID

/**
 * # Correction Models
 *
 * Data models for self-correction and error recovery.
 * Part of Sidekick v0.9.4 Self-Correction feature.
 *
 * ## Overview
 *
 * Self-correction enables:
 * - Detection of errors in agent responses
 * - Classification of error types and severity
 * - Selection of appropriate correction strategies
 * - Tracking correction attempts and outcomes
 * - Learning from corrections for future improvement
 *
 * @since 0.9.4
 */

// =============================================================================
// Detected Error
// =============================================================================

/**
 * An error detected in agent output.
 *
 * @property id Unique error identifier
 * @property type Type/category of error
 * @property severity Error severity
 * @property description Human-readable description
 * @property location Where the error occurred
 * @property context Surrounding context
 * @property suggestedFix Suggested correction
 * @property confidence Confidence in detection (0-1)
 * @property detectedAt Detection timestamp
 */
data class DetectedError(
    val id: String = UUID.randomUUID().toString(),
    val type: ErrorType,
    val severity: ErrorSeverity,
    val description: String,
    val location: ErrorLocation? = null,
    val context: String? = null,
    val suggestedFix: String? = null,
    val confidence: Float = 0.8f,
    val detectedAt: Instant = Instant.now()
) {
    /**
     * Whether this is a critical error.
     */
    val isCritical: Boolean get() = severity == ErrorSeverity.CRITICAL

    /**
     * Whether this is high confidence detection.
     */
    val isHighConfidence: Boolean get() = confidence >= 0.8f

    /**
     * Whether a fix is suggested.
     */
    val hasSuggestedFix: Boolean get() = !suggestedFix.isNullOrBlank()

    companion object {
        /**
         * Creates a syntax error.
         */
        fun syntax(description: String, location: ErrorLocation? = null, fix: String? = null) =
            DetectedError(
                type = ErrorType.SYNTAX_ERROR,
                severity = ErrorSeverity.HIGH,
                description = description,
                location = location,
                suggestedFix = fix
            )

        /**
         * Creates a logic error.
         */
        fun logic(description: String, context: String? = null) =
            DetectedError(
                type = ErrorType.LOGIC_ERROR,
                severity = ErrorSeverity.HIGH,
                description = description,
                context = context
            )

        /**
         * Creates a hallucination error.
         */
        fun hallucination(description: String, confidence: Float = 0.7f) =
            DetectedError(
                type = ErrorType.HALLUCINATION,
                severity = ErrorSeverity.MEDIUM,
                description = description,
                confidence = confidence
            )

        /**
         * Creates an incomplete response error.
         */
        fun incomplete(description: String) =
            DetectedError(
                type = ErrorType.INCOMPLETE_RESPONSE,
                severity = ErrorSeverity.MEDIUM,
                description = description
            )
    }
}

// =============================================================================
// Error Type
// =============================================================================

/**
 * Types of errors that can be detected.
 */
enum class ErrorType(
    val displayName: String,
    val description: String,
    val defaultStrategy: CorrectionStrategy
) {
    /** Code syntax error */
    SYNTAX_ERROR(
        "Syntax Error",
        "Invalid code syntax that won't compile",
        CorrectionStrategy.REGENERATE_SECTION
    ),

    /** Logic or semantic error */
    LOGIC_ERROR(
        "Logic Error",
        "Code compiles but has incorrect logic",
        CorrectionStrategy.TARGETED_FIX
    ),

    /** Type mismatch or incompatibility */
    TYPE_ERROR(
        "Type Error",
        "Type mismatch or incompatible types",
        CorrectionStrategy.TARGETED_FIX
    ),

    /** Missing import or dependency */
    MISSING_IMPORT(
        "Missing Import",
        "Required import statement missing",
        CorrectionStrategy.ADD_MISSING
    ),

    /** Undefined reference */
    UNDEFINED_REFERENCE(
        "Undefined Reference",
        "Reference to undefined variable, function, or class",
        CorrectionStrategy.TARGETED_FIX
    ),

    /** API misuse */
    API_MISUSE(
        "API Misuse",
        "Incorrect use of API or library",
        CorrectionStrategy.REGENERATE_SECTION
    ),

    /** Fabricated information */
    HALLUCINATION(
        "Hallucination",
        "Made up API, function, or information",
        CorrectionStrategy.FULL_REGENERATION
    ),

    /** Security vulnerability */
    SECURITY_ISSUE(
        "Security Issue",
        "Potential security vulnerability",
        CorrectionStrategy.TARGETED_FIX
    ),

    /** Performance issue */
    PERFORMANCE_ISSUE(
        "Performance Issue",
        "Inefficient or slow code pattern",
        CorrectionStrategy.TARGETED_FIX
    ),

    /** Style or convention violation */
    STYLE_VIOLATION(
        "Style Violation",
        "Code style or convention violation",
        CorrectionStrategy.TARGETED_FIX
    ),

    /** Incomplete or truncated response */
    INCOMPLETE_RESPONSE(
        "Incomplete Response",
        "Response was cut off or incomplete",
        CorrectionStrategy.CONTINUE_GENERATION
    ),

    /** Inconsistent with context */
    CONTEXT_MISMATCH(
        "Context Mismatch",
        "Response doesn't match the given context",
        CorrectionStrategy.REGENERATE_WITH_CONTEXT
    ),

    /** Test failure */
    TEST_FAILURE(
        "Test Failure",
        "Generated code fails tests",
        CorrectionStrategy.ITERATIVE_REFINEMENT
    ),

    /** Build failure */
    BUILD_FAILURE(
        "Build Failure",
        "Code fails to compile or build",
        CorrectionStrategy.ITERATIVE_REFINEMENT
    ),

    /** Runtime error */
    RUNTIME_ERROR(
        "Runtime Error",
        "Code throws exception at runtime",
        CorrectionStrategy.TARGETED_FIX
    )
}

// =============================================================================
// Error Severity
// =============================================================================

/**
 * Severity levels for detected errors.
 */
enum class ErrorSeverity(
    val displayName: String,
    val priority: Int,
    val requiresCorrection: Boolean
) {
    /** Must be fixed immediately */
    CRITICAL("Critical", 4, true),

    /** Should be fixed before use */
    HIGH("High", 3, true),

    /** Should be fixed but code may work */
    MEDIUM("Medium", 2, false),

    /** Nice to fix but not urgent */
    LOW("Low", 1, false),

    /** Informational only */
    INFO("Info", 0, false)
}

// =============================================================================
// Error Location
// =============================================================================

/**
 * Location of an error in code.
 */
data class ErrorLocation(
    val file: String? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val startColumn: Int? = null,
    val endColumn: Int? = null
) {
    /**
     * Human-readable location string.
     */
    val displayString: String
        get() = buildString {
            file?.let { append(it) }
            startLine?.let {
                if (isNotEmpty()) append(":")
                append(it)
                endLine?.takeIf { end -> end != startLine }?.let { end -> append("-$end") }
            }
        }

    companion object {
        fun line(lineNumber: Int, file: String? = null) =
            ErrorLocation(file = file, startLine = lineNumber, endLine = lineNumber)

        fun range(startLine: Int, endLine: Int, file: String? = null) =
            ErrorLocation(file = file, startLine = startLine, endLine = endLine)
    }
}

// =============================================================================
// Correction Strategy
// =============================================================================

/**
 * Strategies for correcting errors.
 */
enum class CorrectionStrategy(
    val displayName: String,
    val description: String,
    val costLevel: Int // 1-5, higher = more expensive
) {
    /** Fix the specific error only */
    TARGETED_FIX(
        "Targeted Fix",
        "Fix only the specific error location",
        1
    ),

    /** Add missing elements */
    ADD_MISSING(
        "Add Missing",
        "Add missing imports, types, or declarations",
        1
    ),

    /** Regenerate the affected section */
    REGENERATE_SECTION(
        "Regenerate Section",
        "Regenerate the section containing the error",
        3
    ),

    /** Continue from where generation stopped */
    CONTINUE_GENERATION(
        "Continue Generation",
        "Continue generating from where it stopped",
        2
    ),

    /** Regenerate with better context */
    REGENERATE_WITH_CONTEXT(
        "Regenerate with Context",
        "Regenerate with improved context information",
        3
    ),

    /** Complete regeneration */
    FULL_REGENERATION(
        "Full Regeneration",
        "Completely regenerate the response",
        5
    ),

    /** Iterative test-fix cycle */
    ITERATIVE_REFINEMENT(
        "Iterative Refinement",
        "Repeatedly test and fix until passing",
        4
    ),

    /** Ask user for clarification */
    REQUEST_CLARIFICATION(
        "Request Clarification",
        "Ask user for more information",
        2
    ),

    /** Roll back to previous version */
    ROLLBACK(
        "Rollback",
        "Revert to a previous working version",
        1
    ),

    /** Skip the correction */
    SKIP(
        "Skip",
        "Skip this correction",
        0
    )
}

// =============================================================================
// Correction Attempt
// =============================================================================

/**
 * A correction attempt for an error.
 *
 * @property id Attempt identifier
 * @property errorId ID of error being corrected
 * @property strategy Strategy used
 * @property originalContent Original content with error
 * @property correctedContent Corrected content
 * @property status Correction status
 * @property validationResult Result of validation
 * @property attemptNumber Attempt number (1-based)
 * @property startedAt Start time
 * @property completedAt Completion time
 */
data class CorrectionAttempt(
    val id: String = UUID.randomUUID().toString(),
    val errorId: String,
    val strategy: CorrectionStrategy,
    val originalContent: String,
    val correctedContent: String? = null,
    val status: CorrectionStatus = CorrectionStatus.PENDING,
    val validationResult: ValidationResult? = null,
    val attemptNumber: Int = 1,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null
) {
    /**
     * Duration of the attempt in milliseconds.
     */
    val durationMs: Long?
        get() = completedAt?.let { it.toEpochMilli() - startedAt.toEpochMilli() }

    /**
     * Whether the correction was successful.
     */
    val isSuccessful: Boolean get() = status == CorrectionStatus.SUCCEEDED

    /**
     * Whether the correction is still in progress.
     */
    val isInProgress: Boolean get() = status == CorrectionStatus.IN_PROGRESS

    /**
     * Marks as in progress.
     */
    fun start(): CorrectionAttempt = copy(status = CorrectionStatus.IN_PROGRESS)

    /**
     * Marks as succeeded with result.
     */
    fun succeed(corrected: String, validation: ValidationResult? = null): CorrectionAttempt =
        copy(
            correctedContent = corrected,
            status = CorrectionStatus.SUCCEEDED,
            validationResult = validation,
            completedAt = Instant.now()
        )

    /**
     * Marks as failed.
     */
    fun fail(reason: String? = null): CorrectionAttempt =
        copy(
            status = CorrectionStatus.FAILED,
            validationResult = ValidationResult.failed(reason ?: "Correction failed"),
            completedAt = Instant.now()
        )
}

/**
 * Status of a correction attempt.
 */
enum class CorrectionStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    ROLLED_BACK
}

// =============================================================================
// Validation Result
// =============================================================================

/**
 * Result of validating a correction.
 *
 * @property passed Whether validation passed
 * @property checks Individual check results
 * @property message Summary message
 * @property details Additional details
 */
data class ValidationResult(
    val passed: Boolean,
    val checks: List<ValidationCheck> = emptyList(),
    val message: String? = null,
    val details: Map<String, String> = emptyMap()
) {
    /**
     * Number of passed checks.
     */
    val passedCount: Int get() = checks.count { it.passed }

    /**
     * Number of failed checks.
     */
    val failedCount: Int get() = checks.count { !it.passed }

    /**
     * Pass rate (0-1).
     */
    val passRate: Float
        get() = if (checks.isEmpty()) if (passed) 1f else 0f
                else passedCount.toFloat() / checks.size

    companion object {
        fun passed(message: String? = null) =
            ValidationResult(passed = true, message = message)

        fun failed(message: String) =
            ValidationResult(passed = false, message = message)

        fun fromChecks(checks: List<ValidationCheck>): ValidationResult =
            ValidationResult(
                passed = checks.all { it.passed },
                checks = checks,
                message = if (checks.all { it.passed }) "All checks passed"
                         else "${checks.count { !it.passed }} check(s) failed"
            )
    }
}

/**
 * A single validation check.
 */
data class ValidationCheck(
    val name: String,
    val passed: Boolean,
    val message: String? = null,
    val category: ValidationCategory = ValidationCategory.GENERAL
) {
    companion object {
        fun pass(name: String, message: String? = null, category: ValidationCategory = ValidationCategory.GENERAL) =
            ValidationCheck(name, true, message, category)

        fun fail(name: String, message: String, category: ValidationCategory = ValidationCategory.GENERAL) =
            ValidationCheck(name, false, message, category)
    }
}

/**
 * Categories of validation checks.
 */
enum class ValidationCategory {
    SYNTAX,
    SEMANTICS,
    TYPES,
    TESTS,
    BUILD,
    RUNTIME,
    STYLE,
    SECURITY,
    PERFORMANCE,
    GENERAL
}

// =============================================================================
// Correction Session
// =============================================================================

/**
 * A correction session tracking multiple correction attempts.
 *
 * @property id Session identifier
 * @property taskId Associated task ID
 * @property errors Detected errors
 * @property attempts Correction attempts
 * @property status Session status
 * @property config Session configuration
 * @property createdAt Creation time
 */
data class CorrectionSession(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val errors: List<DetectedError> = emptyList(),
    val attempts: List<CorrectionAttempt> = emptyList(),
    val status: SessionStatus = SessionStatus.ACTIVE,
    val config: CorrectionConfig = CorrectionConfig(),
    val createdAt: Instant = Instant.now()
) {
    /**
     * Total error count.
     */
    val errorCount: Int get() = errors.size

    /**
     * Critical error count.
     */
    val criticalErrorCount: Int get() = errors.count { it.isCritical }

    /**
     * Total attempts made.
     */
    val totalAttempts: Int get() = attempts.size

    /**
     * Successful attempts.
     */
    val successfulAttempts: Int get() = attempts.count { it.isSuccessful }

    /**
     * Uncorrected errors.
     */
    val uncorrectedErrors: List<DetectedError>
        get() {
            val correctedIds = attempts.filter { it.isSuccessful }.map { it.errorId }.toSet()
            return errors.filter { it.id !in correctedIds }
        }

    /**
     * Whether all errors are corrected.
     */
    val allCorrected: Boolean get() = uncorrectedErrors.isEmpty()

    /**
     * Whether max attempts reached.
     */
    val maxAttemptsReached: Boolean get() = totalAttempts >= config.maxAttempts

    /**
     * Adds an error.
     */
    fun addError(error: DetectedError): CorrectionSession =
        copy(errors = errors + error)

    /**
     * Adds an attempt.
     */
    fun addAttempt(attempt: CorrectionAttempt): CorrectionSession =
        copy(attempts = attempts + attempt)

    /**
     * Updates status.
     */
    fun withStatus(newStatus: SessionStatus): CorrectionSession =
        copy(status = newStatus)

    enum class SessionStatus {
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}

/**
 * Configuration for correction sessions.
 */
data class CorrectionConfig(
    val maxAttempts: Int = 5,
    val maxAttemptsPerError: Int = 3,
    val autoCorrectThreshold: Float = 0.9f,
    val enableIterativeRefinement: Boolean = true,
    val validateAfterCorrection: Boolean = true,
    val runTestsOnCorrection: Boolean = true,
    val rollbackOnFailure: Boolean = true
)

// =============================================================================
// Correction Events
// =============================================================================

/**
 * Events from correction sessions.
 */
sealed class CorrectionEvent {
    abstract val sessionId: String
    abstract val timestamp: Instant

    data class ErrorDetected(
        override val sessionId: String,
        val errorId: String,
        val errorType: ErrorType,
        val severity: ErrorSeverity,
        override val timestamp: Instant = Instant.now()
    ) : CorrectionEvent()

    data class CorrectionStarted(
        override val sessionId: String,
        val attemptId: String,
        val errorId: String,
        val strategy: CorrectionStrategy,
        override val timestamp: Instant = Instant.now()
    ) : CorrectionEvent()

    data class CorrectionSucceeded(
        override val sessionId: String,
        val attemptId: String,
        val errorId: String,
        override val timestamp: Instant = Instant.now()
    ) : CorrectionEvent()

    data class CorrectionFailed(
        override val sessionId: String,
        val attemptId: String,
        val errorId: String,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : CorrectionEvent()

    data class ValidationCompleted(
        override val sessionId: String,
        val passed: Boolean,
        val passRate: Float,
        override val timestamp: Instant = Instant.now()
    ) : CorrectionEvent()

    data class SessionCompleted(
        override val sessionId: String,
        val totalErrors: Int,
        val correctedErrors: Int,
        val totalAttempts: Int,
        override val timestamp: Instant = Instant.now()
    ) : CorrectionEvent()
}

// =============================================================================
// Correction Result
// =============================================================================

/**
 * Final result of a correction session.
 */
data class CorrectionResult(
    val sessionId: String,
    val taskId: String,
    val success: Boolean,
    val originalContent: String,
    val finalContent: String,
    val errorsDetected: Int,
    val errorsCorrected: Int,
    val totalAttempts: Int,
    val validationResult: ValidationResult?,
    val remainingErrors: List<DetectedError>,
    val durationMs: Long
) {
    /**
     * Correction rate (0-1).
     */
    val correctionRate: Float
        get() = if (errorsDetected > 0) errorsCorrected.toFloat() / errorsDetected else 1f

    /**
     * Whether content changed.
     */
    val contentChanged: Boolean get() = originalContent != finalContent

    /**
     * Whether there are remaining errors.
     */
    val hasRemainingErrors: Boolean get() = remainingErrors.isNotEmpty()
}

// =============================================================================
// Error Detector Config
// =============================================================================

/**
 * Configuration for error detection.
 */
data class ErrorDetectorConfig(
    val enableSyntaxCheck: Boolean = true,
    val enableTypeCheck: Boolean = true,
    val enableLogicCheck: Boolean = true,
    val enableSecurityCheck: Boolean = true,
    val enableStyleCheck: Boolean = false,
    val enableHallucinationDetection: Boolean = true,
    val minConfidence: Float = 0.5f,
    val language: String = "kotlin"
)
