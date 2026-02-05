package com.sidekick.quality.performance

/**
 * # Performance Linter Models
 *
 * Data structures for detecting and reporting performance anti-patterns.
 * Part of Sidekick v0.6.3 Performance Linter feature.
 *
 * ## Overview
 *
 * These models support:
 * - Pattern-based detection of performance issues
 * - Severity classification
 * - Suggestions and impact estimates
 * - Configurable rule sets
 *
 * @since 0.6.3
 */

/**
 * A detected performance issue.
 *
 * @property type The type/category of issue
 * @property severity How serious the issue is
 * @property location Where the issue was found
 * @property description Human-readable explanation
 * @property suggestion How to fix the issue
 * @property estimatedImpact Optional performance impact estimate
 */
data class PerformanceIssue(
    val type: IssueType,
    val severity: IssueSeverity,
    val location: IssueLocation,
    val description: String,
    val suggestion: String,
    val estimatedImpact: String?
) {
    /**
     * Whether this is a critical or high severity issue.
     */
    val isUrgent: Boolean get() = severity.weight >= IssueSeverity.HIGH.weight

    /**
     * Display string for the issue.
     */
    val displayString: String
        get() = "[${severity.displayName}] ${type.displayName}: $description"

    companion object {
        /**
         * Creates a simple issue for testing.
         */
        fun simple(
            type: IssueType,
            severity: IssueSeverity = IssueSeverity.MEDIUM,
            description: String = type.displayName
        ) = PerformanceIssue(
            type = type,
            severity = severity,
            location = IssueLocation("", 0, ""),
            description = description,
            suggestion = "Review and optimize",
            estimatedImpact = null
        )
    }
}

/**
 * Location of an issue in source code.
 *
 * @property filePath Absolute path to the file
 * @property line 1-based line number
 * @property codeSnippet Relevant code snippet
 */
data class IssueLocation(
    val filePath: String,
    val line: Int,
    val codeSnippet: String
) {
    /**
     * File name without path.
     */
    val fileName: String get() = filePath.substringAfterLast("/")

    /**
     * Display string for location.
     */
    val displayString: String get() = "$fileName:$line"
}

/**
 * Types of performance issues that can be detected.
 *
 * @property displayName Human-readable name
 * @property pattern Regex pattern to detect this issue (null if detection is more complex)
 * @property category Category grouping for this issue
 */
enum class IssueType(
    val displayName: String,
    val pattern: String?,
    val category: IssueCategory
) {
    /** String concatenation in loop creates many temporary objects */
    STRING_CONCAT_LOOP(
        "String concatenation in loop",
        """for.*\+=\s*["']|while.*\+=\s*["']""",
        IssueCategory.MEMORY
    ),

    /** LINQ operations allocate enumerators and delegates in hot paths */
    LINQ_IN_HOT_PATH(
        "LINQ in hot path",
        """\.(Where|Select|Any|All|First|Last|Count)\(""",
        IssueCategory.MEMORY
    ),

    /** Object allocation inside loops can cause GC pressure */
    ALLOCATION_IN_LOOP(
        "Allocation in loop",
        """for\s*\(.*\)\s*\{[^}]*new\s+\w+|while\s*\(.*\)\s*\{[^}]*new\s+\w+""",
        IssueCategory.MEMORY
    ),

    /** Async void methods cannot be awaited and exceptions are unhandled */
    ASYNC_VOID(
        "Async void method",
        """async\s+void\s+\w+""",
        IssueCategory.ASYNC
    ),

    /** Large byte arrays go on Large Object Heap */
    LARGE_OBJECT_HEAP(
        "Large object heap allocation",
        """new\s+byte\s*\[\s*\d{5,}|new\s+byte\s*\[\s*\w+\s*\*""",
        IssueCategory.MEMORY
    ),

    /** Value types being boxed to object */
    BOXING(
        "Boxing operation",
        null, // Requires semantic analysis
        IssueCategory.MEMORY
    ),

    /** Regex without RegexOptions.Compiled */
    REGEX_NOT_COMPILED(
        "Uncompiled Regex",
        """new\s+Regex\s*\([^)]*\)(?!\s*,\s*RegexOptions)""",
        IssueCategory.CPU
    ),

    /** Collections that grow without bounds */
    UNBOUNDED_COLLECTION(
        "Unbounded collection growth",
        """while\s*\(true\)[^}]*\.Add\(|for\s*\(;;\)[^}]*\.Add\(""",
        IssueCategory.MEMORY
    ),

    /** N+1 query pattern in loops */
    N_PLUS_ONE(
        "Potential N+1 query",
        """foreach.*\{[^}]*\.Find|foreach.*\{[^}]*\.Get|for\s*\(.*\)\s*\{[^}]*repository\.""",
        IssueCategory.DATABASE
    ),

    /** Blocking on async code can cause deadlocks */
    SYNC_OVER_ASYNC(
        "Sync over async",
        """\.Result\s*[;,)]|\.Wait\s*\(\s*\)|\.GetAwaiter\s*\(\s*\)\s*\.\s*GetResult\s*\(""",
        IssueCategory.ASYNC
    ),

    /** Thread.Sleep in async context */
    THREAD_SLEEP_IN_ASYNC(
        "Thread.Sleep in async",
        """async\s+\w+[^{]*\{[^}]*Thread\.Sleep""",
        IssueCategory.ASYNC
    ),

    /** Disposing in finalizer instead of Dispose pattern */
    DISPOSE_IN_FINALIZER(
        "Dispose in finalizer",
        """~\w+\s*\(\s*\)\s*\{[^}]*\.Dispose\s*\(""",
        IssueCategory.MEMORY
    );

    override fun toString(): String = displayName

    companion object {
        /**
         * Detects issue types from code text using regex patterns.
         */
        fun detect(code: String): List<IssueType> {
            return entries.filter { type ->
                type.pattern?.let { pattern ->
                    try {
                        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(code)
                    } catch (_: Exception) {
                        false
                    }
                } ?: false
            }
        }

        /**
         * All types in a specific category.
         */
        fun byCategory(category: IssueCategory): List<IssueType> {
            return entries.filter { it.category == category }
        }

        /**
         * Finds type by name (case-insensitive).
         */
        fun byName(name: String): IssueType? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Categories of performance issues.
 */
enum class IssueCategory(val displayName: String, val description: String) {
    /** Memory allocation and GC pressure issues */
    MEMORY("Memory", "Memory allocation and GC pressure"),

    /** CPU-intensive operations */
    CPU("CPU", "CPU-bound performance issues"),

    /** Async/await anti-patterns */
    ASYNC("Async", "Async/await anti-patterns"),

    /** Database query performance */
    DATABASE("Database", "Database query performance"),

    /** I/O bound operations */
    IO("I/O", "I/O bound operations");

    companion object {
        fun byName(name: String): IssueCategory? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Issue severity levels.
 *
 * @property weight Numeric weight for sorting/filtering (higher = more severe)
 * @property displayName Human-readable name
 */
enum class IssueSeverity(val weight: Int, val displayName: String) {
    /** Definite performance problem that must be fixed */
    CRITICAL(4, "Critical"),

    /** Likely performance problem that should be fixed */
    HIGH(3, "High"),

    /** Potential performance problem to review */
    MEDIUM(2, "Medium"),

    /** Minor optimization opportunity */
    LOW(1, "Low");

    override fun toString(): String = displayName

    companion object {
        /**
         * All severities in weight order (highest first).
         */
        val ALL = entries.sortedByDescending { it.weight }

        /**
         * Finds severity by name (case-insensitive).
         */
        fun byName(name: String): IssueSeverity {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
        }
    }
}

/**
 * Configuration for performance linting.
 *
 * @property enabled Whether the feature is active
 * @property minSeverity Minimum severity to report
 * @property enabledRules Set of enabled issue types
 * @property ignoreTestFiles Whether to skip test files
 * @property hotPathAnnotations Annotations marking performance-critical code
 * @property maxIssuesPerFile Maximum issues to report per file
 */
data class PerformanceLinterConfig(
    val enabled: Boolean = true,
    val minSeverity: IssueSeverity = IssueSeverity.MEDIUM,
    val enabledRules: Set<IssueType> = IssueType.entries.toSet(),
    val ignoreTestFiles: Boolean = true,
    val hotPathAnnotations: Set<String> = setOf("HotPath", "PerformanceCritical"),
    val maxIssuesPerFile: Int = 50
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with minimum severity changed.
     */
    fun withMinSeverity(severity: IssueSeverity) = copy(minSeverity = severity)

    /**
     * Returns config with a rule enabled.
     */
    fun withRule(rule: IssueType) = copy(enabledRules = enabledRules + rule)

    /**
     * Returns config with a rule disabled.
     */
    fun withoutRule(rule: IssueType) = copy(enabledRules = enabledRules - rule)

    /**
     * Checks if an issue should be reported based on config.
     */
    fun shouldReport(issue: PerformanceIssue): Boolean {
        if (!enabled) return false
        if (issue.type !in enabledRules) return false
        if (issue.severity.weight < minSeverity.weight) return false
        return true
    }

    companion object {
        /** Disabled configuration */
        val DISABLED = PerformanceLinterConfig(enabled = false)

        /** Strict mode - reports everything */
        val STRICT = PerformanceLinterConfig(minSeverity = IssueSeverity.LOW)

        /** Relaxed mode - only critical and high */
        val RELAXED = PerformanceLinterConfig(minSeverity = IssueSeverity.HIGH)

        /** Async-focused configuration */
        val ASYNC_FOCUSED = PerformanceLinterConfig(
            enabledRules = setOf(
                IssueType.ASYNC_VOID,
                IssueType.SYNC_OVER_ASYNC,
                IssueType.THREAD_SLEEP_IN_ASYNC
            )
        )

        /** Memory-focused configuration */
        val MEMORY_FOCUSED = PerformanceLinterConfig(
            enabledRules = IssueType.byCategory(IssueCategory.MEMORY).toSet()
        )
    }
}

/**
 * Analysis result for a file or project.
 *
 * @property filePath Path to the analyzed file (or "project" for project-wide)
 * @property issues List of detected issues
 * @property analysisTimeMs Time taken for analysis
 */
data class PerformanceAnalysisResult(
    val filePath: String,
    val issues: List<PerformanceIssue>,
    val analysisTimeMs: Long
) {
    /**
     * Whether any issues were found.
     */
    val hasIssues: Boolean get() = issues.isNotEmpty()

    /**
     * Total number of issues.
     */
    val totalCount: Int get() = issues.size

    /**
     * Number of critical issues.
     */
    val criticalCount: Int get() = issues.count { it.severity == IssueSeverity.CRITICAL }

    /**
     * Number of high severity issues.
     */
    val highCount: Int get() = issues.count { it.severity == IssueSeverity.HIGH }

    /**
     * Issues grouped by type.
     */
    fun byType(): Map<IssueType, List<PerformanceIssue>> = issues.groupBy { it.type }

    /**
     * Issues grouped by severity.
     */
    fun bySeverity(): Map<IssueSeverity, List<PerformanceIssue>> = issues.groupBy { it.severity }

    /**
     * Issues grouped by category.
     */
    fun byCategory(): Map<IssueCategory, List<PerformanceIssue>> = issues.groupBy { it.type.category }

    /**
     * Issues at a specific line.
     */
    fun atLine(line: Int): List<PerformanceIssue> = issues.filter { it.location.line == line }

    companion object {
        /**
         * Creates an empty result.
         */
        fun empty(filePath: String) = PerformanceAnalysisResult(filePath, emptyList(), 0)
    }
}

/**
 * Summary statistics for performance analysis.
 */
data class PerformanceSummary(
    val totalIssues: Int,
    val byCategory: Map<IssueCategory, Int>,
    val bySeverity: Map<IssueSeverity, Int>,
    val mostCommonType: IssueType?
) {
    /**
     * Whether there are urgent issues.
     */
    val hasUrgent: Boolean get() = (bySeverity[IssueSeverity.CRITICAL] ?: 0) > 0 ||
            (bySeverity[IssueSeverity.HIGH] ?: 0) > 0

    companion object {
        fun from(issues: List<PerformanceIssue>): PerformanceSummary {
            return PerformanceSummary(
                totalIssues = issues.size,
                byCategory = issues.groupingBy { it.type.category }.eachCount(),
                bySeverity = issues.groupingBy { it.severity }.eachCount(),
                mostCommonType = issues.groupingBy { it.type }.eachCount()
                    .maxByOrNull { it.value }?.key
            )
        }

        val EMPTY = PerformanceSummary(0, emptyMap(), emptyMap(), null)
    }
}
