package com.sidekick.visual.separators

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import java.awt.Color

/**
 * # Method Separator Service
 *
 * Project-level service for detecting method boundaries.
 * Part of Sidekick v0.5.3 Method Separators feature.
 *
 * ## Features
 *
 * - Detects method/function boundaries in source files
 * - Calculates separator positions
 * - Persists configuration per project
 * - Supports multiple languages
 *
 * @since 0.5.3
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickMethodSeparators", storages = [Storage("sidekick-separators.xml")])
class MethodSeparatorService(private val project: Project) : PersistentStateComponent<MethodSeparatorService.State> {

    private val logger = Logger.getInstance(MethodSeparatorService::class.java)
    private var state = State()

    /**
     * Persistent state for separator configuration.
     */
    data class State(
        var enabled: Boolean = true,
        var lineStyleName: String = "Solid",
        var lineThickness: Int = 1,
        var colorThemeName: String = "Gray",
        var showBeforeClasses: Boolean = true,
        var showBeforeProperties: Boolean = false,
        var excludedLanguages: MutableSet<String> = mutableSetOf(),
        var minMethodLines: Int = 3
    ) {
        constructor() : this(true, "Solid", 1, "Gray", true, false, mutableSetOf(), 3)

        fun toConfig(): MethodSeparatorConfig {
            val theme = SeparatorColorTheme.byName(colorThemeName)
            return MethodSeparatorConfig(
                enabled = enabled,
                lineStyle = SeparatorLineStyle.byName(lineStyleName),
                lineColor = theme.color,
                lineThickness = lineThickness,
                showBeforeClasses = showBeforeClasses,
                showBeforeProperties = showBeforeProperties,
                excludedLanguages = excludedLanguages.toSet(),
                minMethodLines = minMethodLines
            )
        }

        companion object {
            fun from(config: MethodSeparatorConfig, themeName: String = "Gray") = State(
                enabled = config.enabled,
                lineStyleName = config.lineStyle.displayName,
                lineThickness = config.lineThickness,
                colorThemeName = themeName,
                showBeforeClasses = config.showBeforeClasses,
                showBeforeProperties = config.showBeforeProperties,
                excludedLanguages = config.excludedLanguages.toMutableSet(),
                minMethodLines = config.minMethodLines
            )
        }
    }

    companion object {
        fun getInstance(project: Project): MethodSeparatorService {
            return project.getService(MethodSeparatorService::class.java)
        }
    }

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded method separator config: style=${state.lineStyleName}, enabled=${state.enabled}")
    }

    /**
     * Current configuration.
     */
    val config: MethodSeparatorConfig get() = state.toConfig()

    /**
     * Whether separators are enabled.
     */
    val isEnabled: Boolean get() = state.enabled

    /**
     * Detects separator positions in the given file.
     *
     * @param psiFile File to analyze
     * @return Detection result with positions or error
     */
    fun detectSeparators(psiFile: PsiFile): SeparatorDetectionResult {
        if (!state.enabled) {
            return SeparatorDetectionResult.Disabled()
        }

        val languageId = psiFile.language.id.lowercase()
        if (state.excludedLanguages.contains(languageId)) {
            return SeparatorDetectionResult.Excluded(languageId)
        }

        return try {
            val elements = mutableListOf<SeparableElement>()
            detectElementsRecursive(psiFile, elements)

            val config = state.toConfig()
            val positions = elements
                .filter { shouldShowSeparator(it, config) }
                .drop(1) // Don't show separator before first element
                .map { element ->
                    SeparatorPosition(
                        lineNumber = element.separatorLine,
                        element = element
                    )
                }

            SeparatorDetectionResult.Success(positions)
        } catch (e: Exception) {
            logger.error("Failed to detect separators", e)
            SeparatorDetectionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Gets statistics for a file.
     */
    fun getStats(psiFile: PsiFile): SeparatorStats {
        val elements = mutableListOf<SeparableElement>()
        detectElementsRecursive(psiFile, elements)
        return SeparatorStats.from(elements)
    }

    /**
     * Updates the configuration.
     */
    fun updateConfig(config: MethodSeparatorConfig) {
        state = State.from(config, state.colorThemeName)
        logger.info("Updated separator config: style=${config.lineStyle.displayName}")
    }

    /**
     * Toggles separators on/off.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        logger.info("Method separators ${if (state.enabled) "enabled" else "disabled"}")
        return state.enabled
    }

    /**
     * Sets the line style.
     */
    fun setStyle(style: SeparatorLineStyle) {
        state.lineStyleName = style.displayName
        logger.info("Set separator style to ${style.displayName}")
    }

    /**
     * Sets the color theme.
     */
    fun setColorTheme(theme: SeparatorColorTheme) {
        state.colorThemeName = theme.displayName
        logger.info("Set separator color to ${theme.displayName}")
    }

    private fun shouldShowSeparator(element: SeparableElement, config: MethodSeparatorConfig): Boolean {
        return when (element.elementType) {
            SeparableElementType.METHOD -> element.isSubstantial(config.minMethodLines)
            SeparableElementType.CLASS -> config.showBeforeClasses
            SeparableElementType.PROPERTY -> config.showBeforeProperties
            else -> false
        }
    }

    private fun detectElementsRecursive(
        element: PsiElement,
        elements: MutableList<SeparableElement>,
        depth: Int = 0
    ) {
        // Only look at top-level elements (depth 0-1)
        if (depth > 2) return

        val elementType = detectElementType(element)

        if (elementType != null) {
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
            val lineNumber = document?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: 1
            val endLine = document?.getLineNumber(element.textRange.endOffset)?.plus(1) ?: lineNumber
            val lineCount = endLine - lineNumber + 1

            elements.add(SeparableElement(
                lineNumber = lineNumber,
                elementType = elementType,
                elementName = extractName(element) ?: "anonymous",
                lineCount = lineCount,
                offset = element.textRange.startOffset
            ))
        }

        // Continue searching in children for nested elements
        element.children.forEach { child ->
            detectElementsRecursive(child, elements, depth + 1)
        }
    }

    private fun detectElementType(element: PsiElement): SeparableElementType? {
        val elementType = element.node?.elementType?.toString()?.uppercase() ?: return null

        return when {
            elementType.contains("FUN") || elementType.contains("METHOD") -> SeparableElementType.METHOD
            elementType.contains("CLASS") && !elementType.contains("COMPANION") -> SeparableElementType.CLASS
            elementType.contains("COMPANION") -> SeparableElementType.COMPANION
            elementType.contains("PROPERTY") || elementType.contains("FIELD") -> SeparableElementType.PROPERTY
            elementType.contains("ENUM_ENTRY") -> SeparableElementType.ENUM_MEMBER
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
