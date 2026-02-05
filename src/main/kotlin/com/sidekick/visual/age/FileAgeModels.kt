package com.sidekick.visual.age

import java.awt.Color
import java.time.Duration
import java.time.Instant

/**
 * # File Age Models
 *
 * Data structures for file age-based tab coloring.
 * Part of Sidekick v0.5.4 File Age Indicator feature.
 *
 * ## Overview
 *
 * These models support:
 * - Tab coloring based on file modification time
 * - Multiple age categories (fresh, recent, stale, ancient)
 * - Configurable thresholds and colors
 * - Git-aware modification detection
 *
 * @since 0.5.4
 */

/**
 * Configuration for file age indicators.
 *
 * Controls how files are colored based on age.
 *
 * @property enabled Whether age coloring is active
 * @property colorScheme Color scheme for age categories
 * @property freshThreshold Duration for "fresh" files
 * @property recentThreshold Duration for "recent" files
 * @property staleThreshold Duration for "stale" files
 * @property useGitTime Use git commit time instead of file system time
 * @property showAgeInTooltip Show age info in editor tab tooltip
 */
data class FileAgeConfig(
    val enabled: Boolean = true,
    val colorScheme: AgeColorScheme = AgeColorScheme.WARMTH,
    val freshThreshold: Duration = Duration.ofHours(1),
    val recentThreshold: Duration = Duration.ofDays(1),
    val staleThreshold: Duration = Duration.ofDays(7),
    val useGitTime: Boolean = true,
    val showAgeInTooltip: Boolean = true
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with different color scheme.
     */
    fun withScheme(scheme: AgeColorScheme) = copy(colorScheme = scheme)

    /**
     * Returns config with git time toggled.
     */
    fun toggleGitTime() = copy(useGitTime = !useGitTime)

    /**
     * Returns config with different thresholds.
     */
    fun withThresholds(
        fresh: Duration = freshThreshold,
        recent: Duration = recentThreshold,
        stale: Duration = staleThreshold
    ) = copy(
        freshThreshold = fresh,
        recentThreshold = recent,
        staleThreshold = stale
    )

    /**
     * Categorizes a duration into an age category.
     */
    fun categorize(age: Duration): AgeCategory = when {
        age <= freshThreshold -> AgeCategory.FRESH
        age <= recentThreshold -> AgeCategory.RECENT
        age <= staleThreshold -> AgeCategory.STALE
        else -> AgeCategory.ANCIENT
    }

    companion object {
        val DISABLED = FileAgeConfig(enabled = false)
        val AGGRESSIVE = FileAgeConfig(
            freshThreshold = Duration.ofMinutes(30),
            recentThreshold = Duration.ofHours(4),
            staleThreshold = Duration.ofDays(1)
        )
        val RELAXED = FileAgeConfig(
            freshThreshold = Duration.ofDays(1),
            recentThreshold = Duration.ofDays(7),
            staleThreshold = Duration.ofDays(30)
        )
    }
}

/**
 * Age categories for files.
 */
enum class AgeCategory(val displayName: String, val description: String) {
    /** Modified within fresh threshold */
    FRESH("Fresh", "Recently modified"),

    /** Modified within recent threshold */
    RECENT("Recent", "Modified today or yesterday"),

    /** Modified within stale threshold */
    STALE("Stale", "Not touched in a while"),

    /** Older than stale threshold */
    ANCIENT("Ancient", "Very old file");

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()
    }
}

/**
 * Color schemes for age indicators.
 */
enum class AgeColorScheme(val displayName: String) {
    /** Warm to cool (fresh=green, ancient=red) */
    WARMTH("Warmth"),

    /** Vibrant traffic light colors */
    TRAFFIC("Traffic Light"),

    /** Subtle grayscale */
    GRAYSCALE("Grayscale"),

    /** Ocean tones (fresh=light blue, ancient=deep blue) */
    OCEAN("Ocean");

    /**
     * Gets color for an age category.
     */
    fun colorFor(category: AgeCategory): Color = when (this) {
        WARMTH -> when (category) {
            AgeCategory.FRESH -> Color(180, 255, 180, 100)   // Light green
            AgeCategory.RECENT -> Color(255, 255, 180, 80)   // Light yellow
            AgeCategory.STALE -> Color(255, 220, 180, 80)    // Light orange
            AgeCategory.ANCIENT -> Color(255, 180, 180, 80)  // Light red
        }
        TRAFFIC -> when (category) {
            AgeCategory.FRESH -> Color(100, 200, 100, 100)   // Green
            AgeCategory.RECENT -> Color(200, 200, 100, 80)   // Yellow
            AgeCategory.STALE -> Color(200, 150, 100, 80)    // Orange
            AgeCategory.ANCIENT -> Color(200, 100, 100, 80)  // Red
        }
        GRAYSCALE -> when (category) {
            AgeCategory.FRESH -> Color(230, 230, 230, 60)
            AgeCategory.RECENT -> Color(200, 200, 200, 60)
            AgeCategory.STALE -> Color(170, 170, 170, 60)
            AgeCategory.ANCIENT -> Color(140, 140, 140, 60)
        }
        OCEAN -> when (category) {
            AgeCategory.FRESH -> Color(180, 220, 255, 100)   // Light blue
            AgeCategory.RECENT -> Color(150, 200, 255, 80)   // Sky blue
            AgeCategory.STALE -> Color(100, 160, 220, 80)    // Medium blue
            AgeCategory.ANCIENT -> Color(80, 120, 180, 80)   // Deep blue
        }
    }

    override fun toString(): String = displayName

    companion object {
        val ALL = entries.toList()

        fun byName(name: String): AgeColorScheme {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: WARMTH
        }
    }
}

/**
 * File age information.
 *
 * @property filePath Path to the file
 * @property lastModified Timestamp of last modification
 * @property age Duration since modification
 * @property category Age category
 * @property source Source of modification time (file system or git)
 */
data class FileAgeInfo(
    val filePath: String,
    val lastModified: Instant,
    val age: Duration,
    val category: AgeCategory,
    val source: AgeSource
) {
    /**
     * Gets the color for this file's age.
     */
    fun colorWith(scheme: AgeColorScheme): Color = scheme.colorFor(category)

    /**
     * Human-readable age description.
     */
    val ageDescription: String
        get() = formatDuration(age)

    /**
     * Summary for display/tooltip.
     */
    val summary: String
        get() = "${category.displayName}: $ageDescription (${source.displayName})"

    companion object {
        /**
         * Formats a duration in human-readable form.
         */
        fun formatDuration(duration: Duration): String {
            val minutes = duration.toMinutes()
            val hours = duration.toHours()
            val days = duration.toDays()

            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "$minutes minute${if (minutes != 1L) "s" else ""} ago"
                hours < 24 -> "$hours hour${if (hours != 1L) "s" else ""} ago"
                days < 30 -> "$days day${if (days != 1L) "s" else ""} ago"
                days < 365 -> "${days / 30} month${if (days / 30 != 1L) "s" else ""} ago"
                else -> "${days / 365} year${if (days / 365 != 1L) "s" else ""} ago"
            }
        }
    }
}

/**
 * Source of file modification time.
 */
enum class AgeSource(val displayName: String) {
    /** File system modification time */
    FILESYSTEM("File System"),

    /** Git commit time */
    GIT("Git"),

    /** Unknown/fallback */
    UNKNOWN("Unknown");

    override fun toString(): String = displayName
}

/**
 * Result of age detection.
 */
sealed class AgeDetectionResult {
    data class Success(val ageInfo: FileAgeInfo) : AgeDetectionResult()
    data class Disabled(val reason: String = "File age indicator disabled") : AgeDetectionResult()
    data class Error(val message: String) : AgeDetectionResult()

    val isSuccess: Boolean get() = this is Success

    fun ageInfoOrNull(): FileAgeInfo? = (this as? Success)?.ageInfo
}

/**
 * Statistics for file ages in a project.
 */
data class AgeStats(
    val totalFiles: Int,
    val freshCount: Int,
    val recentCount: Int,
    val staleCount: Int,
    val ancientCount: Int,
    val averageAge: Duration
) {
    /**
     * Percentage of files in each category.
     */
    val freshPercent: Int get() = if (totalFiles > 0) (freshCount * 100 / totalFiles) else 0
    val recentPercent: Int get() = if (totalFiles > 0) (recentCount * 100 / totalFiles) else 0
    val stalePercent: Int get() = if (totalFiles > 0) (staleCount * 100 / totalFiles) else 0
    val ancientPercent: Int get() = if (totalFiles > 0) (ancientCount * 100 / totalFiles) else 0

    val summary: String
        get() = "$totalFiles files: $freshCount fresh, $recentCount recent, $staleCount stale, $ancientCount ancient"

    companion object {
        val EMPTY = AgeStats(0, 0, 0, 0, 0, Duration.ZERO)

        fun from(ageInfos: List<FileAgeInfo>): AgeStats {
            if (ageInfos.isEmpty()) return EMPTY

            val byCategory = ageInfos.groupBy { it.category }
            val avgMillis = ageInfos.map { it.age.toMillis() }.average().toLong()

            return AgeStats(
                totalFiles = ageInfos.size,
                freshCount = byCategory[AgeCategory.FRESH]?.size ?: 0,
                recentCount = byCategory[AgeCategory.RECENT]?.size ?: 0,
                staleCount = byCategory[AgeCategory.STALE]?.size ?: 0,
                ancientCount = byCategory[AgeCategory.ANCIENT]?.size ?: 0,
                averageAge = Duration.ofMillis(avgMillis)
            )
        }
    }
}
