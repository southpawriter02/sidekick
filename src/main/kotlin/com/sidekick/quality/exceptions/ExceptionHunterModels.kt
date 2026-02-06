package com.sidekick.quality.exceptions

/**
 * # Exception Hunter Models
 *
 * Data structures for exception analysis and detection.
 * Part of Sidekick v0.6.1 Exception Hunter feature.
 *
 * ## Overview
 *
 * These models support:
 * - Unhandled exception detection and classification
 * - Location tracking with call chain context
 * - Severity-based prioritization
 * - Configurable analysis behavior
 *
 * @since 0.6.1
 */

/**
 * An unhandled exception detected in code.
 *
 * @property exceptionType Fully qualified or simple name of the exception
 * @property location Where the exception is thrown/occurs
 * @property callChain Call stack leading to this exception
 * @property severity Classification of the exception's severity
 * @property suggestion Recommended action to handle the exception
 */
data class UnhandledException(
    val exceptionType: String,
    val location: ExceptionLocation,
    val callChain: List<CallSite>,
    val severity: ExceptionSeverity,
    val suggestion: String
) {
    /**
     * Whether this is a runtime (unchecked) exception.
     * Runtime exceptions include NullPointerException, IllegalArgumentException, etc.
     */
    val isRuntime: Boolean
        get() = exceptionType.contains("Runtime") ||
                exceptionType.contains("Unchecked") ||
                COMMON_RUNTIME_EXCEPTIONS.any { exceptionType.contains(it) }

    companion object {
        private val COMMON_RUNTIME_EXCEPTIONS = setOf(
            "NullPointer", "IllegalArgument", "IllegalState",
            "IndexOutOfBounds", "ClassCast", "NumberFormat"
        )
    }
}

/**
 * Location of an exception in source code.
 *
 * @property filePath Absolute path to the source file
 * @property line 1-based line number
 * @property column 0-based column offset
 * @property methodName Enclosing method name (if available)
 * @property className Enclosing class name (if available)
 */
data class ExceptionLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val methodName: String?,
    val className: String?
) {
    /**
     * Formatted location string for display.
     */
    val displayString: String
        get() = buildString {
            className?.let { append("$it.") }
            methodName?.let { append("$it() ") }
            append("[$line:$column]")
        }
}

/**
 * A call site in the exception chain.
 *
 * Represents a method in the call stack that may propagate an exception.
 *
 * @property methodName Name of the method
 * @property className Containing class name
 * @property throwsDeclaration List of declared thrown exceptions
 */
data class CallSite(
    val methodName: String,
    val className: String,
    val throwsDeclaration: List<String>
) {
    /**
     * Whether this call site declares the exception in its throws clause.
     */
    fun declaresException(exceptionType: String): Boolean {
        return throwsDeclaration.any { declared ->
            exceptionType.contains(declared) || declared.contains(exceptionType) ||
            exceptionType.endsWith(declared) || declared.endsWith(exceptionType)
        }
    }
}

/**
 * Exception severity levels.
 *
 * Used for prioritization and display styling.
 *
 * @property displayName Human-readable name
 * @property priority Numeric priority (1 = highest)
 */
enum class ExceptionSeverity(val displayName: String, val priority: Int) {
    /** Critical exceptions that typically crash the application */
    CRITICAL("Critical", 1),

    /** High-severity I/O, database, or security exceptions */
    HIGH("High", 2),

    /** Medium-severity custom or business logic exceptions */
    MEDIUM("Medium", 3),

    /** Low-severity exceptions unlikely to occur */
    LOW("Low", 4),

    /** Informational - documented or expected exceptions */
    INFO("Info", 5);

    override fun toString(): String = displayName

    companion object {
        /**
         * Determines severity from exception type name.
         *
         * Uses pattern matching to classify exceptions:
         * - CRITICAL: NullPointer, OutOfMemory, StackOverflow
         * - HIGH: IO, SQL, Security exceptions
         * - MEDIUM: Illegal* exceptions
         * - LOW: All others
         */
        fun fromExceptionType(type: String): ExceptionSeverity {
            return when {
                type.contains("NullPointer") -> CRITICAL
                type.contains("OutOfMemory") -> CRITICAL
                type.contains("StackOverflow") -> CRITICAL
                type.contains("IO") -> HIGH
                type.contains("SQL") -> HIGH
                type.contains("Security") -> HIGH
                type.contains("Network") -> HIGH
                type.contains("Illegal") -> MEDIUM
                type.contains("Unsupported") -> MEDIUM
                else -> LOW
            }
        }

        /** All severity levels in priority order */
        val ALL = entries.sortedBy { it.priority }

        /**
         * Find severity by display name (case-insensitive).
         */
        fun byName(name: String): ExceptionSeverity {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: MEDIUM
        }
    }
}

/**
 * Configuration for exception hunting.
 *
 * Controls which exceptions are detected and how they are displayed.
 *
 * @property enabled Whether the feature is active
 * @property minSeverity Minimum severity to report
 * @property showInGutter Show indicators in editor gutter
 * @property highlightInEditor Highlight exception sites in editor
 * @property traverseCallChain Analyze call chain for propagation
 * @property maxCallChainDepth Maximum depth for call chain traversal
 * @property ignoredExceptions Exception types to ignore
 */
data class ExceptionHunterConfig(
    val enabled: Boolean = true,
    val minSeverity: ExceptionSeverity = ExceptionSeverity.MEDIUM,
    val showInGutter: Boolean = true,
    val highlightInEditor: Boolean = true,
    val traverseCallChain: Boolean = true,
    val maxCallChainDepth: Int = 5,
    val ignoredExceptions: Set<String> = emptySet()
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with updated minimum severity.
     */
    fun withMinSeverity(severity: ExceptionSeverity) = copy(minSeverity = severity)

    /**
     * Returns config with exception added to ignore list.
     */
    fun withIgnored(exceptionType: String) = copy(
        ignoredExceptions = ignoredExceptions + exceptionType
    )

    /**
     * Returns config with exception removed from ignore list.
     */
    fun withoutIgnored(exceptionType: String) = copy(
        ignoredExceptions = ignoredExceptions - exceptionType
    )

    /**
     * Whether an exception should be reported based on config.
     */
    fun shouldReport(exception: UnhandledException): Boolean {
        if (!enabled) return false
        if (exception.exceptionType in ignoredExceptions) return false
        return exception.severity.priority <= minSeverity.priority
    }

    companion object {
        /** Disabled configuration */
        val DISABLED = ExceptionHunterConfig(enabled = false)

        /** Strict mode - report everything */
        val STRICT = ExceptionHunterConfig(minSeverity = ExceptionSeverity.INFO)

        /** Relaxed mode - only critical and high */
        val RELAXED = ExceptionHunterConfig(minSeverity = ExceptionSeverity.HIGH)
    }
}

/**
 * Analysis result for a file.
 *
 * Contains all detected unhandled exceptions and analysis metadata.
 *
 * @property filePath Path to the analyzed file
 * @property exceptions List of detected unhandled exceptions
 * @property analyzedMethods Number of methods analyzed
 * @property analysisTimeMs Time taken to analyze in milliseconds
 */
data class ExceptionAnalysisResult(
    val filePath: String,
    val exceptions: List<UnhandledException>,
    val analyzedMethods: Int,
    val analysisTimeMs: Long
) {
    /** Whether any issues were found */
    val hasIssues: Boolean get() = exceptions.isNotEmpty()

    /** Count of critical severity exceptions */
    val criticalCount: Int get() = exceptions.count { it.severity == ExceptionSeverity.CRITICAL }

    /** Count of high severity exceptions */
    val highCount: Int get() = exceptions.count { it.severity == ExceptionSeverity.HIGH }

    /** Total issue count */
    val totalCount: Int get() = exceptions.size

    /**
     * Exceptions grouped by severity.
     */
    fun bySeverity(): Map<ExceptionSeverity, List<UnhandledException>> {
        return exceptions.groupBy { it.severity }
    }

    /**
     * Exceptions at a specific line.
     */
    fun atLine(line: Int): List<UnhandledException> {
        return exceptions.filter { it.location.line == line }
    }

    companion object {
        /**
         * Empty result for disabled analysis.
         */
        fun empty(filePath: String) = ExceptionAnalysisResult(
            filePath = filePath,
            exceptions = emptyList(),
            analyzedMethods = 0,
            analysisTimeMs = 0
        )
    }
}
