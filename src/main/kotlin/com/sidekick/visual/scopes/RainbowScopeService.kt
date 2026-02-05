package com.sidekick.visual.scopes

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*

/**
 * # Rainbow Scope Service
 *
 * Project-level service for detecting and managing scope highlighting.
 * Part of Sidekick v0.5.2 Rainbow Scopes feature.
 *
 * ## Features
 *
 * - Detects nested scopes in source files
 * - Assigns colors based on nesting level
 * - Persists configuration per project
 * - Supports multiple languages
 *
 * @since 0.5.2
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickRainbowScopes", storages = [Storage("sidekick-scopes.xml")])
class RainbowScopeService(private val project: Project) : PersistentStateComponent<RainbowScopeService.State> {

    private val logger = Logger.getInstance(RainbowScopeService::class.java)
    private var state = State()

    /**
     * Persistent state for scope configuration.
     */
    data class State(
        var enabled: Boolean = true,
        var opacity: Float = 0.05f,
        var maxNestingLevel: Int = 5,
        var schemeName: String = "Warm",
        var excludedLanguages: MutableSet<String> = mutableSetOf(),
        var excludedScopeTypes: MutableSet<String> = mutableSetOf()
    ) {
        constructor() : this(true, 0.05f, 5, "Warm", mutableSetOf(), mutableSetOf())

        fun toConfig(): RainbowScopeConfig = RainbowScopeConfig(
            enabled = enabled,
            opacity = opacity,
            maxNestingLevel = maxNestingLevel,
            colorScheme = ScopeColorScheme.byName(schemeName),
            excludedLanguages = excludedLanguages.toSet(),
            excludedScopeTypes = excludedScopeTypes.mapNotNull { name ->
                ScopeType.entries.find { it.name == name }
            }.toSet()
        )

        companion object {
            fun from(config: RainbowScopeConfig) = State(
                enabled = config.enabled,
                opacity = config.opacity,
                maxNestingLevel = config.maxNestingLevel,
                schemeName = config.colorScheme.displayName,
                excludedLanguages = config.excludedLanguages.toMutableSet(),
                excludedScopeTypes = config.excludedScopeTypes.map { it.name }.toMutableSet()
            )
        }
    }

    companion object {
        fun getInstance(project: Project): RainbowScopeService {
            return project.getService(RainbowScopeService::class.java)
        }
    }

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded rainbow scope config: scheme=${state.schemeName}, enabled=${state.enabled}")
    }

    /**
     * Current configuration.
     */
    val config: RainbowScopeConfig get() = state.toConfig()

    /**
     * Whether scope highlighting is enabled.
     */
    val isEnabled: Boolean get() = state.enabled

    /**
     * Detects scopes in the given file.
     *
     * @param psiFile File to analyze
     * @return Detection result with scopes or error
     */
    fun detectScopes(psiFile: PsiFile): ScopeDetectionResult {
        if (!state.enabled) {
            return ScopeDetectionResult.Disabled()
        }

        val languageId = psiFile.language.id.lowercase()
        if (state.excludedLanguages.contains(languageId)) {
            return ScopeDetectionResult.Excluded(languageId)
        }

        return try {
            val scopes = mutableListOf<CodeScope>()
            detectScopesRecursive(psiFile, 0, scopes)
            ScopeDetectionResult.Success(scopes.sortedBy { it.startOffset })
        } catch (e: Exception) {
            logger.error("Failed to detect scopes", e)
            ScopeDetectionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Gets the scope at a given offset.
     *
     * @param psiFile File to analyze
     * @param offset Character offset
     * @return Deepest scope containing the offset, or null
     */
    fun getScopeAtOffset(psiFile: PsiFile, offset: Int): CodeScope? {
        val result = detectScopes(psiFile)
        return result.scopesOrEmpty()
            .filter { it.contains(offset) }
            .maxByOrNull { it.nestingLevel }
    }

    /**
     * Gets statistics for scopes in a file.
     */
    fun getStats(psiFile: PsiFile): ScopeStats {
        return ScopeStats.from(detectScopes(psiFile).scopesOrEmpty())
    }

    /**
     * Updates the configuration.
     */
    fun updateConfig(config: RainbowScopeConfig) {
        state = State.from(config)
        logger.info("Updated rainbow scope config: scheme=${config.colorScheme.displayName}")
    }

    /**
     * Toggles scope highlighting on/off.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        logger.info("Rainbow scopes ${if (state.enabled) "enabled" else "disabled"}")
        return state.enabled
    }

    /**
     * Sets the color scheme.
     */
    fun setScheme(scheme: ScopeColorScheme) {
        state.schemeName = scheme.displayName
        logger.info("Set scope scheme to ${scheme.displayName}")
    }

    private fun detectScopesRecursive(
        element: PsiElement,
        level: Int,
        scopes: MutableList<CodeScope>
    ) {
        if (level > state.maxNestingLevel) return

        val scopeType = detectScopeType(element)
        val currentLevel = if (scopeType != null) level + 1 else level

        if (scopeType != null && !state.excludedScopeTypes.contains(scopeType.name)) {
            scopes.add(CodeScope(
                startOffset = element.textRange.startOffset,
                endOffset = element.textRange.endOffset,
                nestingLevel = currentLevel,
                scopeType = scopeType,
                name = extractName(element)
            ))
        }

        element.children.forEach { child ->
            detectScopesRecursive(child, currentLevel, scopes)
        }
    }

    private fun detectScopeType(element: PsiElement): ScopeType? {
        val elementType = element.node?.elementType?.toString()?.uppercase() ?: return null

        return when {
            elementType.contains("CLASS") || elementType.contains("OBJECT_DECLARATION") -> ScopeType.CLASS
            elementType.contains("FUN") || elementType.contains("METHOD") -> ScopeType.METHOD
            elementType.contains("LAMBDA") || elementType.contains("CLOSURE") -> ScopeType.LAMBDA
            elementType.contains("FOR") || elementType.contains("WHILE") || elementType.contains("DO_WHILE") -> ScopeType.LOOP
            elementType.contains("IF") || elementType.contains("WHEN") || elementType.contains("SWITCH") -> ScopeType.CONDITIONAL
            elementType.contains("TRY") -> ScopeType.TRY_CATCH
            elementType == "BLOCK" || elementType.contains("CODE_BLOCK") -> ScopeType.BLOCK
            else -> null
        }
    }

    private fun extractName(element: PsiElement): String? {
        return element.children
            .find { child ->
                val type = child.node?.elementType?.toString()?.uppercase() ?: ""
                type.contains("IDENTIFIER") || type.contains("NAME")
            }
            ?.text
            ?.take(50)
    }
}
