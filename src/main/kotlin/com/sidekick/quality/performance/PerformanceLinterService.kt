package com.sidekick.quality.performance

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiElement

/**
 * # Performance Linter Service
 *
 * Project-level service for detecting performance anti-patterns.
 * Part of Sidekick v0.6.3 Performance Linter feature.
 *
 * ## Features
 *
 * - PSI-based code analysis
 * - Pattern-based detection of common anti-patterns
 * - Configurable rule sets
 * - Severity classification with suggestions
 * - Hot path annotation awareness
 *
 * @since 0.6.3
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickPerformanceLinter", storages = [Storage("sidekick-perf.xml")])
class PerformanceLinterService(private val project: Project) : PersistentStateComponent<PerformanceLinterService.State> {

    private val logger = Logger.getInstance(PerformanceLinterService::class.java)
    private var state = State()

    // Cache for analysis results
    private val cache = mutableMapOf<String, PerformanceAnalysisResult>()

    /**
     * Persistent state for the performance linter.
     */
    data class State(
        var enabled: Boolean = true,
        var minSeverityName: String = "Medium",
        var ignoreTestFiles: Boolean = true,
        var maxIssuesPerFile: Int = 50,
        var disabledRules: MutableSet<String> = mutableSetOf()
    ) {
        // No-arg constructor for serialization
        constructor() : this(true, "Medium", true, 50, mutableSetOf())

        fun toConfig(): PerformanceLinterConfig {
            val enabledRules = IssueType.entries.filter { it.name !in disabledRules }.toSet()
            return PerformanceLinterConfig(
                enabled = enabled,
                minSeverity = IssueSeverity.byName(minSeverityName),
                enabledRules = enabledRules,
                ignoreTestFiles = ignoreTestFiles,
                maxIssuesPerFile = maxIssuesPerFile
            )
        }

        companion object {
            fun from(config: PerformanceLinterConfig) = State(
                enabled = config.enabled,
                minSeverityName = config.minSeverity.displayName,
                ignoreTestFiles = config.ignoreTestFiles,
                maxIssuesPerFile = config.maxIssuesPerFile,
                disabledRules = IssueType.entries
                    .filter { it !in config.enabledRules }
                    .map { it.name }
                    .toMutableSet()
            )
        }
    }

    companion object {
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): PerformanceLinterService {
            return project.getService(PerformanceLinterService::class.java)
        }

        /**
         * Test file path patterns.
         */
        private val TEST_PATH_PATTERNS = listOf(
            "/test/", "/tests/", "/spec/", "/specs/",
            "test.", "tests.", "spec.", "specs.",
            "_test.", "_tests.", "_spec."
        )
    }

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
        cache.clear()
        logger.info("Loaded performance linter config")
    }

    /**
     * Current configuration.
     */
    val config: PerformanceLinterConfig get() = state.toConfig()

    // -------------------------------------------------------------------------
    // Analysis Methods
    // -------------------------------------------------------------------------

    /**
     * Analyzes a file for performance issues.
     *
     * @param psiFile The file to analyze
     * @return Analysis result with detected issues
     */
    fun analyzeFile(psiFile: PsiFile): PerformanceAnalysisResult {
        val filePath = psiFile.virtualFile?.path ?: return PerformanceAnalysisResult.empty("")
        val currentConfig = config

        if (!currentConfig.enabled) {
            return PerformanceAnalysisResult.empty(filePath)
        }

        if (currentConfig.ignoreTestFiles && isTestFile(psiFile)) {
            logger.debug("Skipping test file: $filePath")
            return PerformanceAnalysisResult.empty(filePath)
        }

        // Check cache
        cache[filePath]?.let { cached ->
            logger.debug("Returning cached result for $filePath")
            return cached
        }

        val startTime = System.currentTimeMillis()
        val issues = mutableListOf<PerformanceIssue>()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (issues.size < currentConfig.maxIssuesPerFile) {
                    analyzeElement(element, issues, currentConfig)
                }
                super.visitElement(element)
            }
        })

        val analysisTime = System.currentTimeMillis() - startTime
        val result = PerformanceAnalysisResult(filePath, issues, analysisTime)

        // Update cache
        cache[filePath] = result
        logger.debug("Analyzed $filePath: found ${issues.size} issues in ${analysisTime}ms")

        return result
    }

    /**
     * Clears cached results for a file.
     */
    fun invalidateCache(filePath: String? = null) {
        if (filePath != null) {
            cache.remove(filePath)
        } else {
            cache.clear()
        }
    }

    // -------------------------------------------------------------------------
    // Element Analysis
    // -------------------------------------------------------------------------

    /**
     * Analyzes a single PSI element for performance issues.
     */
    private fun analyzeElement(
        element: PsiElement,
        issues: MutableList<PerformanceIssue>,
        currentConfig: PerformanceLinterConfig
    ) {
        val text = element.text ?: return
        val file = element.containingFile ?: return
        val doc = file.viewProvider.document
        val offset = element.textRange?.startOffset ?: return
        val line = doc?.getLineNumber(offset)?.plus(1) ?: 0

        // Check if inside hot path
        val inHotPath = isInHotPath(element, currentConfig.hotPathAnnotations)

        // Detect issues using patterns
        val detectedTypes = IssueType.detect(text)

        detectedTypes.forEach { type ->
            if (type !in currentConfig.enabledRules) return@forEach

            val severity = getSeverity(type, inHotPath)
            if (severity.weight < currentConfig.minSeverity.weight) return@forEach

            issues.add(PerformanceIssue(
                type = type,
                severity = severity,
                location = IssueLocation(
                    filePath = file.virtualFile?.path ?: "",
                    line = line,
                    codeSnippet = text.take(100)
                ),
                description = getDescription(type),
                suggestion = getSuggestion(type),
                estimatedImpact = getImpact(type)
            ))
        }
    }

    // -------------------------------------------------------------------------
    // Severity and Description Logic
    // -------------------------------------------------------------------------

    /**
     * Determines severity for an issue type.
     *
     * @param type The issue type
     * @param inHotPath Whether the issue is in performance-critical code
     */
    internal fun getSeverity(type: IssueType, inHotPath: Boolean = false): IssueSeverity {
        val baseSeverity = when (type) {
            IssueType.ASYNC_VOID -> IssueSeverity.CRITICAL
            IssueType.SYNC_OVER_ASYNC -> IssueSeverity.CRITICAL
            IssueType.THREAD_SLEEP_IN_ASYNC -> IssueSeverity.CRITICAL
            IssueType.STRING_CONCAT_LOOP -> IssueSeverity.HIGH
            IssueType.N_PLUS_ONE -> IssueSeverity.HIGH
            IssueType.ALLOCATION_IN_LOOP -> IssueSeverity.MEDIUM
            IssueType.LINQ_IN_HOT_PATH -> if (inHotPath) IssueSeverity.HIGH else IssueSeverity.MEDIUM
            IssueType.UNBOUNDED_COLLECTION -> IssueSeverity.HIGH
            IssueType.LARGE_OBJECT_HEAP -> IssueSeverity.MEDIUM
            IssueType.REGEX_NOT_COMPILED -> IssueSeverity.LOW
            IssueType.BOXING -> IssueSeverity.LOW
            IssueType.DISPOSE_IN_FINALIZER -> IssueSeverity.MEDIUM
        }

        // Escalate severity in hot paths
        return if (inHotPath && baseSeverity.weight < IssueSeverity.HIGH.weight) {
            IssueSeverity.HIGH
        } else {
            baseSeverity
        }
    }

    /**
     * Gets description for an issue type.
     */
    internal fun getDescription(type: IssueType): String {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> 
                "String concatenation in loop creates many temporary objects and causes O(n²) allocations"
            IssueType.ASYNC_VOID -> 
                "Async void methods cannot be awaited and exceptions are unhandled, potentially crashing the application"
            IssueType.SYNC_OVER_ASYNC -> 
                "Blocking on async code can cause deadlocks and thread pool starvation"
            IssueType.LINQ_IN_HOT_PATH -> 
                "LINQ operations allocate enumerators and delegates, causing GC pressure in hot paths"
            IssueType.N_PLUS_ONE -> 
                "Potential N+1 query pattern: executing a query inside a loop causes n additional database roundtrips"
            IssueType.ALLOCATION_IN_LOOP -> 
                "Object allocation inside loops can cause GC pressure and reduce performance"
            IssueType.LARGE_OBJECT_HEAP -> 
                "Large byte arrays (>85KB) go on the Large Object Heap which is collected less frequently"
            IssueType.BOXING -> 
                "Value type is being boxed to object, causing allocation"
            IssueType.REGEX_NOT_COMPILED -> 
                "Regex is not compiled, causing repeated JIT compilation on each use"
            IssueType.UNBOUNDED_COLLECTION -> 
                "Collection is growing without bounds in an infinite loop, potential memory leak"
            IssueType.THREAD_SLEEP_IN_ASYNC -> 
                "Thread.Sleep blocks the thread in async context; use Task.Delay instead"
            IssueType.DISPOSE_IN_FINALIZER -> 
                "Disposing managed resources in finalizer is incorrect; use Dispose pattern properly"
        }
    }

    /**
     * Gets fix suggestion for an issue type.
     */
    internal fun getSuggestion(type: IssueType): String {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> 
                "Use StringBuilder for efficient string building in loops"
            IssueType.ASYNC_VOID -> 
                "Change return type to async Task; only use async void for event handlers"
            IssueType.SYNC_OVER_ASYNC -> 
                "Use await instead of .Result/.Wait()/.GetAwaiter().GetResult()"
            IssueType.LINQ_IN_HOT_PATH -> 
                "Consider using a for/foreach loop or cache LINQ results outside the hot path"
            IssueType.N_PLUS_ONE -> 
                "Use eager loading with .Include() before the loop, or batch queries"
            IssueType.ALLOCATION_IN_LOOP -> 
                "Move allocation outside the loop or use object pooling"
            IssueType.LARGE_OBJECT_HEAP -> 
                "Consider using ArrayPool<byte>.Shared.Rent() for large buffers"
            IssueType.BOXING -> 
                "Use generic methods or strongly-typed collections to avoid boxing"
            IssueType.REGEX_NOT_COMPILED -> 
                "Use RegexOptions.Compiled or create a static readonly Regex instance"
            IssueType.UNBOUNDED_COLLECTION -> 
                "Add a maximum size check or use a bounded collection"
            IssueType.THREAD_SLEEP_IN_ASYNC -> 
                "Replace Thread.Sleep() with await Task.Delay()"
            IssueType.DISPOSE_IN_FINALIZER -> 
                "Implement IDisposable properly with the Dispose pattern"
        }
    }

    /**
     * Gets estimated impact for an issue type.
     */
    internal fun getImpact(type: IssueType): String? {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> "O(n²) allocations, significant GC pressure"
            IssueType.N_PLUS_ONE -> "N additional database queries, high latency"
            IssueType.SYNC_OVER_ASYNC -> "Potential deadlock, thread pool starvation"
            IssueType.UNBOUNDED_COLLECTION -> "Memory leak, eventual OutOfMemoryException"
            IssueType.ASYNC_VOID -> "Unhandled exceptions may crash the process"
            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Checks if element is inside a hot path (annotated code).
     */
    private fun isInHotPath(element: PsiElement, annotations: Set<String>): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            val text = current.text ?: ""
            if (annotations.any { text.contains("@$it") || text.contains("[$it]") }) {
                return true
            }
            current = current.parent
        }
        return false
    }

    /**
     * Checks if file is a test file.
     */
    internal fun isTestFile(file: PsiFile): Boolean {
        val path = file.virtualFile?.path?.lowercase() ?: return false
        return TEST_PATH_PATTERNS.any { path.contains(it) }
    }

    // -------------------------------------------------------------------------
    // Configuration Methods
    // -------------------------------------------------------------------------

    /**
     * Updates configuration.
     */
    fun updateConfig(config: PerformanceLinterConfig) {
        state = State.from(config)
        cache.clear()
        logger.info("Updated performance linter config")
    }

    /**
     * Toggles the enabled state.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        cache.clear()
        return state.enabled
    }

    /**
     * Gets summary of cached results.
     */
    fun getSummary(): PerformanceSummary {
        val allIssues = cache.values.flatMap { it.issues }
        return PerformanceSummary.from(allIssues)
    }
}
