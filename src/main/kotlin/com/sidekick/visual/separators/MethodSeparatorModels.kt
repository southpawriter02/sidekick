package com.sidekick.visual.separators

import java.awt.Color

/**
 * # Method Separator Models
 *
 * Data structures for visual divider lines between methods.
 * Part of Sidekick v0.5.3 Method Separators feature.
 *
 * ## Overview
 *
 * These models support:
 * - Visual separator lines between methods/functions
 * - Configurable line styles and colors
 * - Smart positioning based on code structure
 * - Support for multiple languages
 *
 * @since 0.5.3
 */

/**
 * Configuration for method separators.
 *
 * Controls how separators are rendered between methods.
 *
 * @property enabled Whether separators are active
 * @property lineStyle Style of the separator line
 * @property lineColor Color of the separator line
 * @property lineThickness Thickness in pixels (1-3)
 * @property showBeforeClasses Also show separators before class declarations
 * @property showBeforeProperties Also show before top-level properties
 * @property excludedLanguages Languages to skip
 * @property minMethodLines Minimum lines in a method to show separator
 */
data class MethodSeparatorConfig(
    val enabled: Boolean = true,
    val lineStyle: SeparatorLineStyle = SeparatorLineStyle.SOLID,
    val lineColor: Color = Color(128, 128, 128, 80),
    val lineThickness: Int = 1,
    val showBeforeClasses: Boolean = true,
    val showBeforeProperties: Boolean = false,
    val excludedLanguages: Set<String> = emptySet(),
    val minMethodLines: Int = 3
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with different line style.
     */
    fun withStyle(style: SeparatorLineStyle) = copy(lineStyle = style)

    /**
     * Returns config with different line color.
     */
    fun withColor(color: Color) = copy(lineColor = color)

    /**
     * Returns config with different thickness.
     */
    fun withThickness(thickness: Int) = copy(lineThickness = thickness.coerceIn(1, 3))

    /**
     * Checks if a language is excluded.
     */
    fun isLanguageExcluded(languageId: String): Boolean {
        return excludedLanguages.contains(languageId.lowercase())
    }

    companion object {
        val DISABLED = MethodSeparatorConfig(enabled = false)
        val SUBTLE = MethodSeparatorConfig(lineColor = Color(128, 128, 128, 40))
        val BOLD = MethodSeparatorConfig(lineThickness = 2, lineColor = Color(100, 100, 100, 120))
    }
}

/**
 * Separator line styles.
 */
enum class SeparatorLineStyle(val displayName: String) {
    /** Continuous solid line */
    SOLID("Solid"),

    /** Dashed line (- - - -) */
    DASHED("Dashed"),

    /** Dotted line (. . . .) */
    DOTTED("Dotted"),

    /** Double solid line */
    DOUBLE("Double");

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()

        fun byName(name: String): SeparatorLineStyle {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: SOLID
        }
    }
}

/**
 * Type of code element that can have a separator.
 */
enum class SeparableElementType(val displayName: String) {
    /** Method or function */
    METHOD("Method"),

    /** Class or interface */
    CLASS("Class"),

    /** Property or field */
    PROPERTY("Property"),

    /** Enum member */
    ENUM_MEMBER("Enum Member"),

    /** Companion object */
    COMPANION("Companion");

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()
    }
}

/**
 * A detected code element that can have a separator.
 *
 * @property lineNumber Line number where element starts (1-indexed)
 * @property elementType Type of element
 * @property elementName Name of the element
 * @property lineCount Number of lines in the element
 * @property offset Character offset in document
 */
data class SeparableElement(
    val lineNumber: Int,
    val elementType: SeparableElementType,
    val elementName: String,
    val lineCount: Int,
    val offset: Int
) {
    /**
     * Line number for the separator (before element).
     */
    val separatorLine: Int get() = (lineNumber - 1).coerceAtLeast(1)

    /**
     * Whether this is a substantial element.
     */
    fun isSubstantial(minLines: Int): Boolean = lineCount >= minLines

    /**
     * Summary for display.
     */
    val summary: String
        get() = "${elementType.displayName}: $elementName ($lineCount lines)"
}

/**
 * Position where a separator should be drawn.
 *
 * @property lineNumber Line number for the separator (0-indexed for rendering)
 * @property yOffset Pixel Y offset in editor
 * @property element The element this separator precedes
 */
data class SeparatorPosition(
    val lineNumber: Int,
    val yOffset: Int = 0,
    val element: SeparableElement
) {
    /**
     * Summary for debug/display.
     */
    val summary: String
        get() = "Line $lineNumber: ${element.elementType.displayName} ${element.elementName}"
}

/**
 * Result of separator detection.
 */
sealed class SeparatorDetectionResult {
    data class Success(val positions: List<SeparatorPosition>) : SeparatorDetectionResult()
    data class Disabled(val reason: String = "Method separators disabled") : SeparatorDetectionResult()
    data class Excluded(val languageId: String) : SeparatorDetectionResult()
    data class Error(val message: String) : SeparatorDetectionResult()

    val isSuccess: Boolean get() = this is Success

    fun positionsOrEmpty(): List<SeparatorPosition> = (this as? Success)?.positions ?: emptyList()
}

/**
 * Separator statistics for a file.
 */
data class SeparatorStats(
    val totalSeparators: Int,
    val methodCount: Int,
    val classCount: Int,
    val propertyCount: Int
) {
    companion object {
        val EMPTY = SeparatorStats(0, 0, 0, 0)

        fun from(elements: List<SeparableElement>): SeparatorStats {
            if (elements.isEmpty()) return EMPTY
            return SeparatorStats(
                totalSeparators = elements.size,
                methodCount = elements.count { it.elementType == SeparableElementType.METHOD },
                classCount = elements.count { it.elementType == SeparableElementType.CLASS },
                propertyCount = elements.count { it.elementType == SeparableElementType.PROPERTY }
            )
        }
    }

    val summary: String
        get() = "$totalSeparators separators ($methodCount methods, $classCount classes)"
}

/**
 * Color themes for separators.
 */
enum class SeparatorColorTheme(val displayName: String, val color: Color) {
    GRAY("Gray", Color(128, 128, 128, 80)),
    DARK("Dark", Color(60, 60, 60, 100)),
    LIGHT("Light", Color(180, 180, 180, 60)),
    BLUE("Blue", Color(100, 150, 200, 80)),
    GREEN("Green", Color(100, 180, 100, 80));

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()

        fun byName(name: String): SeparatorColorTheme {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: GRAY
        }
    }
}
