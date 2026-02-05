package com.sidekick.quality.deadcode

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiElement
import java.time.Instant

/**
 * # Dead Code Service
 *
 * Project-level service for detecting and managing unused code.
 * Part of Sidekick v0.6.4 Dead Code Cemetery feature.
 *
 * ## Features
 *
 * - Detection of unused symbols
 * - Confidence-based analysis
 * - Safe deletion support
 * - Batch operations
 * - Exclusion management
 *
 * @since 0.6.4
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickDeadCode", storages = [Storage("sidekick-deadcode.xml")])
class DeadCodeService(private val project: Project) : PersistentStateComponent<DeadCodeService.State> {

    private val logger = Logger.getInstance(DeadCodeService::class.java)
    private var state = State()

    // Excluded symbols (by qualified name)
    private val excludedSymbols = mutableSetOf<String>()

    /**
     * Persistent state for dead code detection.
     */
    data class State(
        var enabled: Boolean = true,
        var includePrivate: Boolean = true,
        var includeInternal: Boolean = true,
        var excludePublicApi: Boolean = true,
        var minConfidence: Float = 0.8f,
        var excludePatterns: MutableList<String> = mutableListOf("*Test*", "*Mock*"),
        var knownDeadCode: MutableList<SerializedSymbol> = mutableListOf(),
        var excludedNames: MutableList<String> = mutableListOf()
    ) {
        // No-arg constructor for serialization
        constructor() : this(true, true, true, true, 0.8f, mutableListOf(), mutableListOf(), mutableListOf())

        fun toConfig(): DeadCodeConfig {
            return DeadCodeConfig(
                enabled = enabled,
                includePrivate = includePrivate,
                includeInternal = includeInternal,
                excludePublicApi = excludePublicApi,
                excludePatterns = excludePatterns.toList(),
                minConfidence = minConfidence
            )
        }

        companion object {
            fun from(config: DeadCodeConfig) = State(
                enabled = config.enabled,
                includePrivate = config.includePrivate,
                includeInternal = config.includeInternal,
                excludePublicApi = config.excludePublicApi,
                minConfidence = config.minConfidence,
                excludePatterns = config.excludePatterns.toMutableList()
            )
        }
    }

    /**
     * Serialized form of DeadCodeSymbol for persistence.
     */
    data class SerializedSymbol(
        var name: String = "",
        var qualifiedName: String = "",
        var typeName: String = "METHOD",
        var filePath: String = "",
        var line: Int = 0,
        var className: String? = null,
        var memberName: String? = null,
        var usageCount: Int = 0,
        var confidence: Float = 1.0f,
        var canSafeDelete: Boolean = true,
        var visibilityName: String = "PRIVATE",
        var codeSize: Int = 1
    ) {
        // No-arg constructor for serialization
        constructor() : this("", "", "METHOD", "", 0, null, null, 0, 1.0f, true, "PRIVATE", 1)

        fun toDeadCodeSymbol(): DeadCodeSymbol {
            return DeadCodeSymbol(
                name = name,
                qualifiedName = qualifiedName,
                type = SymbolType.byName(typeName) ?: SymbolType.METHOD,
                location = SymbolLocation(filePath, line, className, memberName),
                usageCount = usageCount,
                lastUsedDate = null,
                confidence = confidence,
                canSafeDelete = canSafeDelete,
                visibility = SymbolVisibility.byName(visibilityName),
                codeSize = codeSize
            )
        }

        companion object {
            fun from(symbol: DeadCodeSymbol) = SerializedSymbol(
                name = symbol.name,
                qualifiedName = symbol.qualifiedName,
                typeName = symbol.type.name,
                filePath = symbol.location.filePath,
                line = symbol.location.line,
                className = symbol.location.className,
                memberName = symbol.location.memberName,
                usageCount = symbol.usageCount,
                confidence = symbol.confidence,
                canSafeDelete = symbol.canSafeDelete,
                visibilityName = symbol.visibility.name,
                codeSize = symbol.codeSize
            )
        }
    }

    companion object {
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): DeadCodeService {
            return project.getService(DeadCodeService::class.java)
        }
    }

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
        excludedSymbols.clear()
        excludedSymbols.addAll(state.excludedNames)
        logger.info("Loaded dead code config with ${state.knownDeadCode.size} known symbols")
    }

    /**
     * Current configuration.
     */
    val config: DeadCodeConfig get() = state.toConfig()

    // -------------------------------------------------------------------------
    // Analysis Methods
    // -------------------------------------------------------------------------

    /**
     * Analyzes the project for dead code.
     *
     * Note: Full implementation would integrate with IntelliJ's unused symbol analysis.
     * This implementation provides the framework and returns cached results.
     *
     * @return Analysis result with detected dead code symbols
     */
    fun analyzeProject(): DeadCodeAnalysisResult {
        if (!state.enabled) {
            return DeadCodeAnalysisResult.empty()
        }

        val startTime = System.currentTimeMillis()
        val currentConfig = config

        // Filter cached symbols by current config
        val symbols = state.knownDeadCode
            .map { it.toDeadCodeSymbol() }
            .filter { currentConfig.shouldAnalyze(it) }
            .filter { it.qualifiedName !in excludedSymbols }

        val analysisTime = System.currentTimeMillis() - startTime

        logger.info("Dead code analysis completed: ${symbols.size} symbols in ${analysisTime}ms")

        return DeadCodeAnalysisResult(
            symbols = symbols,
            totalSymbolsAnalyzed = state.knownDeadCode.size,
            totalLines = 0, // Would be computed from actual file analysis
            deadCodeLines = symbols.sumOf { it.codeSize },
            analysisTimeMs = analysisTime,
            scope = AnalysisScope.PROJECT
        )
    }

    /**
     * Analyzes a single file for dead code.
     */
    fun analyzeFile(psiFile: PsiFile): DeadCodeAnalysisResult {
        if (!state.enabled) {
            return DeadCodeAnalysisResult.empty()
        }

        val filePath = psiFile.virtualFile?.path ?: return DeadCodeAnalysisResult.empty()
        val currentConfig = config

        val symbols = state.knownDeadCode
            .map { it.toDeadCodeSymbol() }
            .filter { it.location.filePath == filePath }
            .filter { currentConfig.shouldAnalyze(it) }

        return DeadCodeAnalysisResult(
            symbols = symbols,
            totalSymbolsAnalyzed = symbols.size,
            totalLines = 0,
            deadCodeLines = symbols.sumOf { it.codeSize },
            scope = AnalysisScope.FILE
        )
    }

    // -------------------------------------------------------------------------
    // Query Methods
    // -------------------------------------------------------------------------

    /**
     * Gets all known dead code symbols.
     */
    fun getDeadCode(): List<DeadCodeSymbol> {
        return state.knownDeadCode.map { it.toDeadCodeSymbol() }
    }

    /**
     * Gets dead code by symbol type.
     */
    fun getDeadCodeByType(type: SymbolType): List<DeadCodeSymbol> {
        return getDeadCode().filter { it.type == type }
    }

    /**
     * Gets dead code by visibility.
     */
    fun getDeadCodeByVisibility(visibility: SymbolVisibility): List<DeadCodeSymbol> {
        return getDeadCode().filter { it.visibility == visibility }
    }

    /**
     * Gets dead code in a specific file.
     */
    fun getDeadCodeByFile(filePath: String): List<DeadCodeSymbol> {
        return getDeadCode().filter { it.location.filePath == filePath }
    }

    /**
     * Gets symbols safe to delete.
     */
    fun getSafeToDelete(): List<DeadCodeSymbol> {
        return getDeadCode().filter { it.canSafeDelete }
    }

    /**
     * Gets high confidence symbols.
     */
    fun getHighConfidence(): List<DeadCodeSymbol> {
        return getDeadCode().filter { it.isHighConfidence }
    }

    /**
     * Searches dead code by name.
     */
    fun search(query: String): List<DeadCodeSymbol> {
        val lowerQuery = query.lowercase()
        return getDeadCode().filter {
            it.name.lowercase().contains(lowerQuery) ||
            it.qualifiedName.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Gets summary statistics.
     */
    fun getSummary(): DeadCodeSummary {
        return DeadCodeSummary.from(getDeadCode())
    }

    // -------------------------------------------------------------------------
    // Mutation Methods
    // -------------------------------------------------------------------------

    /**
     * Adds a detected dead code symbol.
     */
    fun addSymbol(symbol: DeadCodeSymbol) {
        if (symbol.qualifiedName in excludedSymbols) return

        // Remove existing entry if present
        state.knownDeadCode.removeIf { it.qualifiedName == symbol.qualifiedName }
        state.knownDeadCode.add(SerializedSymbol.from(symbol))
        logger.debug("Added dead code symbol: ${symbol.qualifiedName}")
    }

    /**
     * Adds multiple symbols.
     */
    fun addSymbols(symbols: List<DeadCodeSymbol>) {
        symbols.forEach { addSymbol(it) }
    }

    /**
     * Removes a symbol (marks as deleted).
     *
     * @return true if the symbol was removed
     */
    fun deleteSymbol(symbol: DeadCodeSymbol): Boolean {
        if (!symbol.canSafeDelete) {
            logger.warn("Cannot safely delete symbol: ${symbol.qualifiedName}")
            return false
        }

        val removed = state.knownDeadCode.removeIf { it.qualifiedName == symbol.qualifiedName }
        if (removed) {
            logger.info("Deleted dead code symbol: ${symbol.qualifiedName}")
        }
        return removed
    }

    /**
     * Batch deletes multiple symbols.
     *
     * @return Number of symbols deleted
     */
    fun batchDelete(symbols: List<DeadCodeSymbol>): Int {
        var deleted = 0
        symbols.filter { it.canSafeDelete }.forEach { symbol ->
            if (deleteSymbol(symbol)) deleted++
        }
        logger.info("Batch deleted $deleted symbols")
        return deleted
    }

    /**
     * Deletes all symbols that are safe to delete.
     *
     * @return Number of symbols deleted
     */
    fun deleteAllSafe(): Int {
        return batchDelete(getSafeToDelete())
    }

    /**
     * Excludes a symbol from detection (won't be reported again).
     */
    fun excludeSymbol(symbol: DeadCodeSymbol) {
        excludedSymbols.add(symbol.qualifiedName)
        state.excludedNames.add(symbol.qualifiedName)
        state.knownDeadCode.removeIf { it.qualifiedName == symbol.qualifiedName }
        logger.info("Excluded symbol from detection: ${symbol.qualifiedName}")
    }

    /**
     * Removes a symbol from exclusion list.
     */
    fun unexcludeSymbol(qualifiedName: String) {
        excludedSymbols.remove(qualifiedName)
        state.excludedNames.remove(qualifiedName)
    }

    /**
     * Gets all excluded symbol names.
     */
    fun getExcludedSymbols(): List<String> {
        return state.excludedNames.toList()
    }

    /**
     * Clears all known dead code.
     */
    fun clearAll() {
        state.knownDeadCode.clear()
        logger.info("Cleared all dead code symbols")
    }

    // -------------------------------------------------------------------------
    // Configuration Methods
    // -------------------------------------------------------------------------

    /**
     * Updates configuration.
     */
    fun updateConfig(config: DeadCodeConfig) {
        state = State.from(config).copy(
            knownDeadCode = state.knownDeadCode,
            excludedNames = state.excludedNames
        )
        logger.info("Updated dead code config")
    }

    /**
     * Toggles the enabled state.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        return state.enabled
    }

    /**
     * Refreshes dead code analysis (re-scans project).
     */
    fun refresh() {
        // Would trigger full IDE analysis
        logger.info("Dead code refresh requested")
    }
}
