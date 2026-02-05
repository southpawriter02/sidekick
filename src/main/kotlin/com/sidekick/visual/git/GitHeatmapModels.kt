package com.sidekick.visual.git

import java.awt.Color
import java.time.Instant

/**
 * # Git Heatmap Models
 *
 * Data structures for Git-based change visualization.
 * Part of Sidekick v0.5.5 Git Diff Heatmap feature.
 *
 * ## Overview
 *
 * These models support:
 * - Line-level metric tracking (commit count, recency)
 * - File-level statistics
 * - Heatmap color generation based on intensity
 * - Configurable metrics and color schemes
 *
 * @since 0.5.5
 */

/**
 * Configuration for Git heatmap.
 *
 * @property enabled Whether heatmap is active
 * @property showInGutter Show heatmap in editor gutter
 * @property colorScheme Color scheme for the heatmap
 * @property metricType Metric to visualize (frequency vs recency)
 */
data class GitHeatmapConfig(
    val enabled: Boolean = true,
    val showInGutter: Boolean = true,
    val colorScheme: HeatmapColorScheme = HeatmapColorScheme.FIRE,
    val metricType: HeatmapMetric = HeatmapMetric.COMMIT_COUNT
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with different color scheme.
     */
    fun withScheme(scheme: HeatmapColorScheme) = copy(colorScheme = scheme)

    /**
     * Returns config with different metric.
     */
    fun withMetric(metric: HeatmapMetric) = copy(metricType = metric)

    companion object {
        val DISABLED = GitHeatmapConfig(enabled = false)
        val RECENCY_MODE = GitHeatmapConfig(metricType = HeatmapMetric.LAST_CHANGED)
    }
}

/**
 * What metric to visualize.
 */
enum class HeatmapMetric(val displayName: String, val description: String) {
    /** How often this line is changed (simulated or actual) */
    COMMIT_COUNT("Commit Count", "Frequency of changes"),

    /** When was this line last changed */
    LAST_CHANGED("Recency", "Time since last change"),

    /** How many authors touched this */
    AUTHOR_COUNT("Author Count", "Number of contributors"),

    /** Changes per time period */
    CHURN_RATE("Churn Rate", "Changes over time");

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()

        fun byName(name: String): HeatmapMetric {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: COMMIT_COUNT
        }
    }
}

/**
 * Color schemes for the heatmap.
 */
enum class HeatmapColorScheme(val displayName: String, val colors: List<Color>) {
    FIRE("Fire", listOf(
        Color(255, 255, 200, 100),  // Cold - Light Yellow
        Color(255, 200, 100, 100),  // Warm - Orange
        Color(255, 100, 50, 100),   // Hot - Red-Orange
        Color(200, 50, 50, 100)     // Very Hot - Dark Red
    )),
    PLASMA("Plasma", listOf(
        Color(13, 8, 135, 100),
        Color(126, 3, 168, 100),
        Color(203, 71, 120, 100),
        Color(248, 149, 64, 100)
    )),
    VIRIDIS("Viridis", listOf(
        Color(68, 1, 84, 100),
        Color(59, 82, 139, 100),
        Color(33, 145, 140, 100),
        Color(94, 201, 98, 100)
    ));

    /**
     * Gets color for a normalized intensity (0.0 to 1.0).
     */
    fun colorForIntensity(intensity: Float): Color {
        val safeIntensity = intensity.coerceIn(0f, 1f)
        
        // If 0 intensity, return transparent or very subtle
        if (safeIntensity <= 0.01f) return colors.first()
        
        val scaled = safeIntensity * (colors.size - 1)
        val idx = scaled.toInt()
        val nextIdx = (idx + 1).coerceAtMost(colors.lastIndex)
        val t = scaled - idx
        
        val c1 = colors[idx]
        val c2 = colors[nextIdx]
        
        return interpolate(c1, c2, t)
    }

    private fun interpolate(c1: Color, c2: Color, t: Float): Color {
        val r = (c1.red + (c2.red - c1.red) * t).toInt()
        val g = (c1.green + (c2.green - c1.green) * t).toInt()
        val b = (c1.blue + (c2.blue - c1.blue) * t).toInt()
        val a = (c1.alpha + (c2.alpha - c1.alpha) * t).toInt()
        return Color(r, g, b, a)
    }

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()

        fun byName(name: String): HeatmapColorScheme {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: FIRE
        }
    }
}

/**
 * Line-level Git statistics.
 *
 * @property lineNumber 1-based line number (from blame)
 * @property commitCount Number of commits affecting this line (approximate)
 * @property lastCommitDate Timestamp of last change
 * @property lastAuthor Last author name
 * @property authorCount Number of distinct authors (approximate)
 * @property commitHash Commit hash of last change
 */
data class LineGitStats(
    val lineNumber: Int,
    val commitCount: Int,
    val lastCommitDate: Instant?,
    val lastAuthor: String?,
    val authorCount: Int,
    val commitHash: String? = null
) {
    /**
     * Normalized intensity (0.0 to 1.0) based on metric.
     * Default uses commit count capped at 20.
     */
    fun getIntensity(metric: HeatmapMetric): Float {
        return when (metric) {
            HeatmapMetric.COMMIT_COUNT -> (commitCount.toFloat() / 10f).coerceIn(0.1f, 1f)
            HeatmapMetric.AUTHOR_COUNT -> (authorCount.toFloat() / 5f).coerceIn(0.1f, 1f)
            HeatmapMetric.LAST_CHANGED -> calculateRecencyIntensity()
            else -> 0.1f
        }
    }

    private fun calculateRecencyIntensity(): Float {
        val date = lastCommitDate ?: return 0f
        val daysOld = java.time.Duration.between(date, Instant.now()).toDays()
        // Newer = Hotter (Higher intensity)
        return when {
            daysOld < 1 -> 1.0f
            daysOld < 7 -> 0.8f
            daysOld < 30 -> 0.5f
            daysOld < 90 -> 0.3f
            else -> 0.1f
        }
    }
}

/**
 * File-level Git statistics.
 */
data class FileGitStats(
    val filePath: String,
    val totalCommits: Int,
    val lineStats: Map<Int, LineGitStats>,
    val hotspotLines: List<Int>
)
