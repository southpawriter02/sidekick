package com.sidekick.visual.tabs

import java.awt.Color

/**
 * # Depth-Coded Tab Models
 *
 * Data structures for tab coloring based on file depth.
 * Part of Sidekick v0.5.1 Depth-Coded Tabs feature.
 *
 * ## Overview
 *
 * These models support:
 * - Color coding tabs by directory/namespace depth
 * - Multiple color palettes (Ocean, Rainbow, Monochrome)
 * - Configurable max depth and base directory
 * - Namespace extraction for various languages
 *
 * @since 0.5.1
 */

/**
 * Configuration for depth-coded tabs.
 *
 * Controls how tabs are colored based on file depth.
 *
 * @property enabled Whether depth coloring is active
 * @property colorPalette The color palette to use
 * @property maxDepth Maximum depth to color (depths beyond use last color)
 * @property baseDirectory Custom base for depth calculation
 * @property opacity Opacity of the tab color (0.0 to 1.0)
 */
data class DepthTabConfig(
    val enabled: Boolean = true,
    val colorPalette: ColorPalette = ColorPalette.DEFAULT,
    val maxDepth: Int = 6,
    val baseDirectory: String? = null,
    val opacity: Float = 0.3f
) {
    /**
     * Returns config with coloring toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with different palette.
     */
    fun withPalette(palette: ColorPalette) = copy(colorPalette = palette)

    /**
     * Returns config with different max depth.
     */
    fun withMaxDepth(depth: Int) = copy(maxDepth = depth.coerceIn(1, 10))

    /**
     * Returns config with different opacity.
     */
    fun withOpacity(opacity: Float) = copy(opacity = opacity.coerceIn(0.1f, 1.0f))

    /**
     * Returns config with base directory set.
     */
    fun withBaseDirectory(base: String?) = copy(baseDirectory = base)

    companion object {
        val DISABLED = DepthTabConfig(enabled = false)
    }
}

/**
 * Color palette for depth coding.
 *
 * Provides a list of colors for different depth levels.
 *
 * @property colors List of colors, one per depth level
 * @property name Human-readable palette name
 */
data class ColorPalette(
    val colors: List<Color>,
    val name: String
) {
    /**
     * Gets the color for a specific depth.
     * Depths beyond the palette size use the last color.
     */
    fun colorForDepth(depth: Int): Color {
        return colors[depth.coerceIn(0, colors.lastIndex)]
    }

    /**
     * Gets color with applied opacity.
     */
    fun colorForDepth(depth: Int, opacity: Float): Color {
        val base = colorForDepth(depth)
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        return Color(base.red, base.green, base.blue, alpha)
    }

    /**
     * Number of distinct depth levels.
     */
    val depthCount: Int get() = colors.size

    override fun toString(): String = name

    companion object {
        /**
         * Ocean palette - blues and teals.
         */
        val DEFAULT = ColorPalette(
            name = "Ocean",
            colors = listOf(
                Color(66, 133, 244),   // Depth 0 - Blue
                Color(52, 168, 83),    // Depth 1 - Green
                Color(251, 188, 4),    // Depth 2 - Yellow
                Color(234, 67, 53),    // Depth 3 - Red
                Color(156, 39, 176),   // Depth 4 - Purple
                Color(0, 150, 136),    // Depth 5 - Teal
                Color(255, 87, 34)     // Depth 6+ - Orange
            )
        )

        /**
         * Classic rainbow palette.
         */
        val RAINBOW = ColorPalette(
            name = "Rainbow",
            colors = listOf(
                Color(255, 0, 0),      // Red
                Color(255, 127, 0),    // Orange
                Color(255, 255, 0),    // Yellow
                Color(0, 255, 0),      // Green
                Color(0, 0, 255),      // Blue
                Color(75, 0, 130),     // Indigo
                Color(148, 0, 211)     // Violet
            )
        )

        /**
         * Grayscale palette for minimal visual impact.
         */
        val MONOCHROME = ColorPalette(
            name = "Monochrome",
            colors = (0..6).map { Color(80 + it * 25, 80 + it * 25, 80 + it * 25) }
        )

        /**
         * Pastel palette - softer colors.
         */
        val PASTEL = ColorPalette(
            name = "Pastel",
            colors = listOf(
                Color(173, 216, 230),  // Light Blue
                Color(144, 238, 144),  // Light Green
                Color(255, 255, 224),  // Light Yellow
                Color(255, 182, 193),  // Light Pink
                Color(221, 160, 221),  // Plum
                Color(176, 224, 230),  // Powder Blue
                Color(255, 218, 185)   // Peach
            )
        )

        /**
         * All available palettes.
         */
        val ALL = listOf(DEFAULT, RAINBOW, MONOCHROME, PASTEL)

        /**
         * Gets palette by name.
         */
        fun byName(name: String): ColorPalette {
            return ALL.find { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * File depth analysis result.
 *
 * Contains depth information and assigned color for a file.
 *
 * @property filePath Absolute path to the file
 * @property depth Directory depth from base
 * @property namespace Extracted namespace/package (if applicable)
 * @property color Assigned color for this depth
 * @property segments Path segments from base
 */
data class FileDepthInfo(
    val filePath: String,
    val depth: Int,
    val namespace: String?,
    val color: Color,
    val segments: List<String> = emptyList()
) {
    /**
     * Human-readable depth description.
     */
    val depthLabel: String
        get() = when (depth) {
            0 -> "Root"
            1 -> "Level 1"
            2 -> "Level 2"
            else -> "Level $depth"
        }

    /**
     * File name without path.
     */
    val fileName: String
        get() = filePath.substringAfterLast('/')

    /**
     * Parent directory name.
     */
    val parentDir: String?
        get() = segments.lastOrNull()

    /**
     * Summary for tooltip display.
     */
    val summary: String
        get() = buildString {
            append("Depth: $depth")
            namespace?.let { append("\nNamespace: $it") }
            parentDir?.let { append("\nFolder: $it") }
        }
}

/**
 * Result of a depth calculation operation.
 */
sealed class DepthResult {
    data class Success(val info: FileDepthInfo) : DepthResult()
    data class Disabled(val message: String = "Depth coding disabled") : DepthResult()
    data class Error(val message: String) : DepthResult()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): FileDepthInfo? = (this as? Success)?.info
}

/**
 * Depth calculation mode.
 */
enum class DepthMode(val displayName: String) {
    /** Calculate depth from directory structure */
    DIRECTORY("Directory"),

    /** Calculate depth from namespace/package */
    NAMESPACE("Namespace"),

    /** Combine directory and namespace */
    HYBRID("Hybrid");

    override fun toString(): String = displayName
}
