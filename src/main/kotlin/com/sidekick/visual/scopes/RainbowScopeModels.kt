package com.sidekick.visual.scopes

import java.awt.Color

/**
 * # Rainbow Scope Models
 *
 * Data structures for scope-based background highlighting.
 * Part of Sidekick v0.5.2 Rainbow Scopes feature.
 *
 * ## Overview
 *
 * These models support:
 * - Background tinting for nested code blocks
 * - Multiple color schemes (Warm, Cool, Monochrome, Neon)
 * - Configurable opacity and max nesting level
 * - Scope type detection (class, method, loop, etc.)
 *
 * @since 0.5.2
 */

/**
 * Configuration for rainbow scope highlighting.
 *
 * Controls how scopes are detected and colored.
 *
 * @property enabled Whether scope highlighting is active
 * @property opacity Background color opacity (0.0 to 1.0)
 * @property maxNestingLevel Maximum nesting depth to highlight
 * @property colorScheme Color scheme to use
 * @property excludedLanguages Languages to skip
 * @property excludedScopeTypes Scope types to skip
 */
data class RainbowScopeConfig(
    val enabled: Boolean = true,
    val opacity: Float = 0.05f,
    val maxNestingLevel: Int = 5,
    val colorScheme: ScopeColorScheme = ScopeColorScheme.WARM,
    val excludedLanguages: Set<String> = emptySet(),
    val excludedScopeTypes: Set<ScopeType> = emptySet()
) {
    /**
     * Returns config with highlighting toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with different opacity.
     */
    fun withOpacity(opacity: Float) = copy(opacity = opacity.coerceIn(0.01f, 0.3f))

    /**
     * Returns config with different max nesting.
     */
    fun withMaxNesting(level: Int) = copy(maxNestingLevel = level.coerceIn(1, 10))

    /**
     * Returns config with different color scheme.
     */
    fun withScheme(scheme: ScopeColorScheme) = copy(colorScheme = scheme)

    /**
     * Checks if a language is excluded.
     */
    fun isLanguageExcluded(languageId: String): Boolean {
        return excludedLanguages.contains(languageId.lowercase())
    }

    /**
     * Checks if a scope type is excluded.
     */
    fun isScopeExcluded(type: ScopeType): Boolean {
        return excludedScopeTypes.contains(type)
    }

    companion object {
        val DISABLED = RainbowScopeConfig(enabled = false)
        val SUBTLE = RainbowScopeConfig(opacity = 0.03f)
        val VIVID = RainbowScopeConfig(opacity = 0.12f)
    }
}

/**
 * Color schemes for scope highlighting.
 *
 * Each scheme provides colors for different nesting levels.
 */
enum class ScopeColorScheme(val displayName: String, val colors: List<Color>) {
    /** Warm colors: reds, oranges, yellows */
    WARM("Warm", listOf(
        Color(255, 200, 200),  // Level 1 - Light Red
        Color(255, 220, 180),  // Level 2 - Light Orange
        Color(255, 255, 180),  // Level 3 - Light Yellow
        Color(220, 255, 180),  // Level 4 - Light Lime
        Color(180, 255, 200)   // Level 5 - Light Green
    )),

    /** Cool colors: blues, purples, cyans */
    COOL("Cool", listOf(
        Color(180, 220, 255),  // Level 1 - Light Blue
        Color(200, 180, 255),  // Level 2 - Light Purple
        Color(180, 255, 255),  // Level 3 - Light Cyan
        Color(220, 200, 255),  // Level 4 - Light Lavender
        Color(180, 200, 220)   // Level 5 - Light Slate
    )),

    /** Grayscale for minimal distraction */
    MONOCHROME("Monochrome", listOf(
        Color(240, 240, 240),
        Color(230, 230, 230),
        Color(220, 220, 220),
        Color(210, 210, 210),
        Color(200, 200, 200)
    )),

    /** Bright neon colors */
    NEON("Neon", listOf(
        Color(255, 100, 100),  // Neon Red
        Color(255, 180, 50),   // Neon Orange
        Color(200, 255, 50),   // Neon Yellow-Green
        Color(50, 255, 200),   // Neon Cyan
        Color(150, 100, 255)   // Neon Purple
    ));

    /**
     * Gets color for a nesting level with applied opacity.
     */
    fun colorForLevel(level: Int, opacity: Float): Color {
        val base = colors[(level - 1).coerceIn(0, colors.lastIndex)]
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        return Color(base.red, base.green, base.blue, alpha)
    }

    /**
     * Gets base color (no opacity applied).
     */
    fun baseColorForLevel(level: Int): Color {
        return colors[(level - 1).coerceIn(0, colors.lastIndex)]
    }

    /**
     * Number of distinct nesting levels.
     */
    val levelCount: Int get() = colors.size

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()

        fun byName(name: String): ScopeColorScheme {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: WARM
        }
    }
}

/**
 * Types of code scopes that can be highlighted.
 */
enum class ScopeType(val displayName: String) {
    /** Class or type definition */
    CLASS("Class"),

    /** Method or function definition */
    METHOD("Method"),

    /** Generic code block */
    BLOCK("Block"),

    /** Lambda or closure */
    LAMBDA("Lambda"),

    /** For, while, do loops */
    LOOP("Loop"),

    /** If, when, switch statements */
    CONDITIONAL("Conditional"),

    /** Try-catch-finally blocks */
    TRY_CATCH("Try/Catch");

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()
    }
}

/**
 * A detected scope in code.
 *
 * Represents a contiguous region with a specific nesting level.
 *
 * @property startOffset Start position in document
 * @property endOffset End position in document
 * @property nestingLevel How deeply nested this scope is (1 = first level)
 * @property scopeType Type of scope
 * @property name Optional name (e.g., method name)
 */
data class CodeScope(
    val startOffset: Int,
    val endOffset: Int,
    val nestingLevel: Int,
    val scopeType: ScopeType,
    val name: String? = null
) {
    /**
     * Character count of this scope.
     */
    val length: Int get() = endOffset - startOffset

    /**
     * Whether an offset is within this scope.
     */
    fun contains(offset: Int): Boolean = offset in startOffset..endOffset

    /**
     * Whether this scope overlaps with another.
     */
    fun overlaps(other: CodeScope): Boolean {
        return startOffset < other.endOffset && endOffset > other.startOffset
    }

    /**
     * Whether this scope fully contains another.
     */
    fun fullyContains(other: CodeScope): Boolean {
        return startOffset <= other.startOffset && endOffset >= other.endOffset
    }

    /**
     * Summary for display.
     */
    val summary: String
        get() = buildString {
            append(scopeType.displayName)
            name?.let { append(": $it") }
            append(" (Level $nestingLevel)")
        }
}

/**
 * Result of scope detection.
 */
sealed class ScopeDetectionResult {
    data class Success(val scopes: List<CodeScope>) : ScopeDetectionResult()
    data class Disabled(val reason: String = "Scope highlighting disabled") : ScopeDetectionResult()
    data class Excluded(val languageId: String) : ScopeDetectionResult()
    data class Error(val message: String) : ScopeDetectionResult()

    val isSuccess: Boolean get() = this is Success

    fun scopesOrEmpty(): List<CodeScope> = (this as? Success)?.scopes ?: emptyList()
}

/**
 * Scope statistics for a file.
 */
data class ScopeStats(
    val totalScopes: Int,
    val maxNesting: Int,
    val scopesByType: Map<ScopeType, Int>
) {
    companion object {
        val EMPTY = ScopeStats(0, 0, emptyMap())

        fun from(scopes: List<CodeScope>): ScopeStats {
            if (scopes.isEmpty()) return EMPTY
            return ScopeStats(
                totalScopes = scopes.size,
                maxNesting = scopes.maxOfOrNull { it.nestingLevel } ?: 0,
                scopesByType = scopes.groupBy { it.scopeType }.mapValues { it.value.size }
            )
        }
    }

    val summary: String
        get() = "$totalScopes scopes, max depth $maxNesting"
}
