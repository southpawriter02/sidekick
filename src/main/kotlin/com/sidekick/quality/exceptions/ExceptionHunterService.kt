package com.sidekick.quality.exceptions

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*

/**
 * # Exception Hunter Service
 *
 * Project-level service for analyzing code and detecting unhandled exceptions.
 * Part of Sidekick v0.6.1 Exception Hunter feature.
 *
 * ## Features
 *
 * - PSI tree traversal for exception detection
 * - Pattern-based throwing call identification
 * - Try-catch ancestor detection
 * - In-memory caching of analysis results
 * - Configurable severity filtering
 *
 * @since 0.6.1
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickExceptionHunter", storages = [Storage("sidekick-exceptions.xml")])
class ExceptionHunterService(private val project: Project) : PersistentStateComponent<ExceptionHunterService.State> {

    private val logger = Logger.getInstance(ExceptionHunterService::class.java)
    private var state = State()

    // In-memory cache of analysis results by file path
    private val cache = mutableMapOf<String, ExceptionAnalysisResult>()

    /**
     * Persistent state for the exception hunter.
     */
    data class State(
        var enabled: Boolean = true,
        var minSeverityName: String = "Medium",
        var showInGutter: Boolean = true,
        var highlightInEditor: Boolean = true,
        var traverseCallChain: Boolean = true,
        var maxCallChainDepth: Int = 5,
        var ignoredExceptions: MutableSet<String> = mutableSetOf()
    ) {
        // No-arg constructor for serialization
        constructor() : this(true, "Medium", true, true, true, 5, mutableSetOf())

        fun toConfig(): ExceptionHunterConfig {
            return ExceptionHunterConfig(
                enabled = enabled,
                minSeverity = ExceptionSeverity.byName(minSeverityName),
                showInGutter = showInGutter,
                highlightInEditor = highlightInEditor,
                traverseCallChain = traverseCallChain,
                maxCallChainDepth = maxCallChainDepth,
                ignoredExceptions = ignoredExceptions.toSet()
            )
        }

        companion object {
            fun from(config: ExceptionHunterConfig) = State(
                enabled = config.enabled,
                minSeverityName = config.minSeverity.displayName,
                showInGutter = config.showInGutter,
                highlightInEditor = config.highlightInEditor,
                traverseCallChain = config.traverseCallChain,
                maxCallChainDepth = config.maxCallChainDepth,
                ignoredExceptions = config.ignoredExceptions.toMutableSet()
            )
        }
    }

    companion object {
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): ExceptionHunterService {
            return project.getService(ExceptionHunterService::class.java)
        }

        // Common exceptions that are often thrown
        private val COMMON_EXCEPTIONS = setOf(
            "NullPointerException", "IllegalArgumentException", "IllegalStateException",
            "IOException", "SQLException", "SecurityException", "RuntimeException",
            "FileNotFoundException", "ClassNotFoundException", "NumberFormatException"
        )

        // Patterns that indicate throwing calls
        private val THROWING_PATTERNS = mapOf(
            ".read(" to "IOException",
            ".write(" to "IOException",
            ".close(" to "IOException",
            ".execute(" to "SQLException",
            ".executeQuery(" to "SQLException",
            ".executeUpdate(" to "SQLException",
            ".connect(" to "SQLException",
            ".newInstance(" to "ReflectiveOperationException",
            ".forName(" to "ClassNotFoundException",
            "!!" to "NullPointerException",
            ".parseInt(" to "NumberFormatException",
            ".parseDouble(" to "NumberFormatException"
        )
    }

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded exception hunter config: enabled=${state.enabled}, minSeverity=${state.minSeverityName}")
    }

    /**
     * Current configuration.
     */
    val config: ExceptionHunterConfig get() = state.toConfig()

    /**
     * Analyzes a file for unhandled exceptions.
     *
     * @param psiFile The file to analyze
     * @return Analysis result containing all detected exceptions
     */
    fun analyzeFile(psiFile: PsiFile): ExceptionAnalysisResult {
        val filePath = psiFile.virtualFile?.path ?: ""

        if (!state.enabled) {
            return ExceptionAnalysisResult.empty(filePath)
        }

        val startTime = System.currentTimeMillis()
        val exceptions = mutableListOf<UnhandledException>()
        var methodCount = 0

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (isMethodElement(element)) {
                    methodCount++
                    analyzeMethod(element, exceptions)
                }
                super.visitElement(element)
            }
        })

        val currentConfig = state.toConfig()
        val filtered = exceptions.filter { currentConfig.shouldReport(it) }

        val result = ExceptionAnalysisResult(
            filePath = filePath,
            exceptions = filtered,
            analyzedMethods = methodCount,
            analysisTimeMs = System.currentTimeMillis() - startTime
        )

        cache[filePath] = result
        logger.debug("Analyzed $filePath: ${result.totalCount} exceptions in ${result.analysisTimeMs}ms")

        return result
    }

    /**
     * Gets cached analysis for a file.
     *
     * @param filePath Path to the file
     * @return Cached result or null if not cached
     */
    fun getCachedAnalysis(filePath: String): ExceptionAnalysisResult? = cache[filePath]

    /**
     * Gets exceptions at a specific line.
     *
     * @param filePath Path to the file
     * @param line 1-based line number
     * @return List of exceptions at that line
     */
    fun getExceptionsAtLine(filePath: String, line: Int): List<UnhandledException> {
        return cache[filePath]?.atLine(line) ?: emptyList()
    }

    /**
     * Invalidates the cache for a file.
     *
     * @param filePath Path to invalidate
     */
    fun invalidate(filePath: String) {
        cache.remove(filePath)
    }

    /**
     * Clears the entire cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Updates the configuration.
     *
     * @param config New configuration
     */
    fun updateConfig(config: ExceptionHunterConfig) {
        state = State.from(config)
        logger.info("Updated exception hunter config")
    }

    /**
     * Toggles the enabled state.
     *
     * @return The new enabled state
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        return state.enabled
    }

    /**
     * Adds an exception type to the ignore list.
     *
     * @param exceptionType Exception type to ignore
     */
    fun ignoreException(exceptionType: String) {
        state.ignoredExceptions.add(exceptionType)
    }

    // -------------------------------------------------------------------------
    // Private Analysis Methods
    // -------------------------------------------------------------------------

    /**
     * Checks if an element is a method definition.
     */
    private fun isMethodElement(element: PsiElement): Boolean {
        val type = element.node?.elementType?.toString() ?: return false
        return type.contains("METHOD") || type.contains("FUN") || type.contains("FUNCTION")
    }

    /**
     * Analyzes a method for unhandled exceptions.
     */
    private fun analyzeMethod(method: PsiElement, results: MutableList<UnhandledException>) {
        method.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val throwingCall = detectThrowingCall(element)
                if (throwingCall != null && !isHandled(element, throwingCall)) {
                    results.add(createUnhandledException(element, throwingCall))
                }
                super.visitElement(element)
            }
        })
    }

    /**
     * Detects if an element represents a throwing call.
     *
     * Uses pattern matching and keyword detection to identify
     * potential exception sources.
     *
     * @return Exception type if throwing, null otherwise
     */
    internal fun detectThrowingCall(element: PsiElement): String? {
        val text = element.text ?: return null

        // Explicit throw statement
        if (text.contains("throw ")) {
            return extractExceptionType(text)
        }

        // Check known throwing patterns
        for ((pattern, exceptionType) in THROWING_PATTERNS) {
            if (text.contains(pattern)) {
                return exceptionType
            }
        }

        return null
    }

    /**
     * Extracts exception type from a throw statement.
     */
    internal fun extractExceptionType(text: String): String {
        // Match: throw new ExceptionType or throw ExceptionType
        val match = Regex("""throw\s+(?:new\s+)?(\w+)""").find(text)
        return match?.groupValues?.get(1) ?: "Exception"
    }

    /**
     * Checks if an exception is handled (wrapped in try-catch or declared).
     */
    private fun isHandled(element: PsiElement, exceptionType: String): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            val type = current.node?.elementType?.toString() ?: ""

            // Inside a try block
            if (type.contains("TRY")) return true

            // Method declares throws
            if (type.contains("METHOD") && hasThrowsDeclaration(current, exceptionType)) {
                return true
            }

            current = current.parent
        }
        return false
    }

    /**
     * Checks if a method has a throws declaration for the exception.
     */
    private fun hasThrowsDeclaration(method: PsiElement, exceptionType: String): Boolean {
        val text = method.text ?: return false
        return text.contains("throws $exceptionType") ||
               text.contains("throws ${exceptionType},") ||
               text.contains("@Throws") ||
               text.contains("@throws")
    }

    /**
     * Creates an UnhandledException from an element.
     */
    private fun createUnhandledException(element: PsiElement, exceptionType: String): UnhandledException {
        val file = element.containingFile
        val doc = file?.viewProvider?.document
        val offset = element.textRange?.startOffset ?: 0
        val line = doc?.getLineNumber(offset)?.plus(1) ?: 0
        val column = offset - (doc?.getLineStartOffset(line - 1) ?: 0)

        // Try to find enclosing method/class names
        var methodName: String? = null
        var className: String? = null
        var current: PsiElement? = element.parent
        while (current != null) {
            val type = current.node?.elementType?.toString() ?: ""
            if (type.contains("METHOD") || type.contains("FUN")) {
                methodName = extractName(current)
            }
            if (type.contains("CLASS")) {
                className = extractName(current)
                break
            }
            current = current.parent
        }

        return UnhandledException(
            exceptionType = exceptionType,
            location = ExceptionLocation(
                filePath = file?.virtualFile?.path ?: "",
                line = line,
                column = column,
                methodName = methodName,
                className = className
            ),
            callChain = emptyList(), // Simplified - no call chain traversal
            severity = ExceptionSeverity.fromExceptionType(exceptionType),
            suggestion = generateSuggestion(exceptionType)
        )
    }

    /**
     * Extracts the name from a named element.
     */
    private fun extractName(element: PsiElement): String? {
        // Try to find identifier child
        for (child in element.children) {
            val type = child.node?.elementType?.toString() ?: ""
            if (type.contains("IDENTIFIER") || type.contains("NAME")) {
                return child.text
            }
        }
        return null
    }

    /**
     * Generates a suggestion for handling an exception.
     */
    private fun generateSuggestion(exceptionType: String): String {
        return when {
            exceptionType.contains("NullPointer") -> "Add null check or use safe call operator"
            exceptionType.contains("IO") -> "Wrap in try-catch or add throws IOException"
            exceptionType.contains("SQL") -> "Wrap in try-catch or add throws SQLException"
            exceptionType.contains("Security") -> "Handle security exception or add throws declaration"
            exceptionType.contains("NumberFormat") -> "Validate input before parsing"
            else -> "Consider wrapping in try-catch or declaring throws"
        }
    }
}
