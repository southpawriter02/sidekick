package com.sidekick.visual.age

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive unit tests for File Age Models.
 *
 * Tests cover:
 * - FileAgeConfig data class
 * - AgeCategory enum
 * - AgeColorScheme colors and mappings
 * - FileAgeInfo computed properties
 * - AgeSource enum
 * - AgeDetectionResult sealed class
 * - AgeStats calculations
 *
 * @since 0.5.4
 */
@DisplayName("File Age Models")
class FileAgeModelsTest {

    // =========================================================================
    // FileAgeConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("FileAgeConfig")
    inner class FileAgeConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = FileAgeConfig()

            assertTrue(config.enabled)
            assertEquals(AgeColorScheme.WARMTH, config.colorScheme)
            assertEquals(Duration.ofHours(1), config.freshThreshold)
            assertEquals(Duration.ofDays(1), config.recentThreshold)
            assertEquals(Duration.ofDays(7), config.staleThreshold)
            assertTrue(config.useGitTime)
            assertTrue(config.showAgeInTooltip)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = FileAgeConfig(enabled = true)
            val toggled = config.toggle()

            assertTrue(config.enabled)
            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withScheme changes color scheme")
        fun withScheme_changesColorScheme() {
            val config = FileAgeConfig()
            val updated = config.withScheme(AgeColorScheme.OCEAN)

            assertEquals(AgeColorScheme.OCEAN, updated.colorScheme)
        }

        @Test
        @DisplayName("toggleGitTime flips useGitTime")
        fun toggleGitTime_flipsUseGitTime() {
            val config = FileAgeConfig(useGitTime = true)
            val toggled = config.toggleGitTime()

            assertTrue(config.useGitTime)
            assertFalse(toggled.useGitTime)
        }

        @Test
        @DisplayName("withThresholds updates thresholds")
        fun withThresholds_updatesThresholds() {
            val config = FileAgeConfig()
            val updated = config.withThresholds(
                fresh = Duration.ofMinutes(30),
                recent = Duration.ofHours(12),
                stale = Duration.ofDays(3)
            )

            assertEquals(Duration.ofMinutes(30), updated.freshThreshold)
            assertEquals(Duration.ofHours(12), updated.recentThreshold)
            assertEquals(Duration.ofDays(3), updated.staleThreshold)
        }

        @Test
        @DisplayName("categorize returns FRESH for short duration")
        fun categorize_returnsFreshForShortDuration() {
            val config = FileAgeConfig()
            val age = Duration.ofMinutes(30)

            assertEquals(AgeCategory.FRESH, config.categorize(age))
        }

        @Test
        @DisplayName("categorize returns RECENT for medium duration")
        fun categorize_returnsRecentForMediumDuration() {
            val config = FileAgeConfig()
            val age = Duration.ofHours(12)

            assertEquals(AgeCategory.RECENT, config.categorize(age))
        }

        @Test
        @DisplayName("categorize returns STALE for longer duration")
        fun categorize_returnsStaleForLongerDuration() {
            val config = FileAgeConfig()
            val age = Duration.ofDays(3)

            assertEquals(AgeCategory.STALE, config.categorize(age))
        }

        @Test
        @DisplayName("categorize returns ANCIENT for very long duration")
        fun categorize_returnsAncientForVeryLongDuration() {
            val config = FileAgeConfig()
            val age = Duration.ofDays(30)

            assertEquals(AgeCategory.ANCIENT, config.categorize(age))
        }

        @Test
        @DisplayName("DISABLED preset is disabled")
        fun disabledPreset_isDisabled() {
            assertFalse(FileAgeConfig.DISABLED.enabled)
        }

        @Test
        @DisplayName("AGGRESSIVE preset has shorter thresholds")
        fun aggressivePreset_hasShorterThresholds() {
            val config = FileAgeConfig.AGGRESSIVE
            assertTrue(config.freshThreshold < Duration.ofHours(1))
            assertTrue(config.staleThreshold < Duration.ofDays(7))
        }

        @Test
        @DisplayName("RELAXED preset has longer thresholds")
        fun relaxedPreset_hasLongerThresholds() {
            val config = FileAgeConfig.RELAXED
            assertTrue(config.freshThreshold > Duration.ofHours(1))
            assertTrue(config.staleThreshold > Duration.ofDays(7))
        }
    }

    // =========================================================================
    // AgeCategory Tests
    // =========================================================================

    @Nested
    @DisplayName("AgeCategory")
    inner class AgeCategoryTests {

        @ParameterizedTest
        @DisplayName("all categories have display names")
        @EnumSource(AgeCategory::class)
        fun allCategories_haveDisplayNames(category: AgeCategory) {
            assertNotNull(category.displayName)
            assertTrue(category.displayName.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("all categories have descriptions")
        @EnumSource(AgeCategory::class)
        fun allCategories_haveDescriptions(category: AgeCategory) {
            assertNotNull(category.description)
            assertTrue(category.description.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Fresh", AgeCategory.FRESH.toString())
            assertEquals("Ancient", AgeCategory.ANCIENT.toString())
        }

        @Test
        @DisplayName("ALL contains all categories")
        fun all_containsAllCategories() {
            assertEquals(4, AgeCategory.ALL.size)
        }
    }

    // =========================================================================
    // AgeColorScheme Tests
    // =========================================================================

    @Nested
    @DisplayName("AgeColorScheme")
    inner class AgeColorSchemeTests {

        @ParameterizedTest
        @DisplayName("all schemes return colors for all categories")
        @EnumSource(AgeColorScheme::class)
        fun allSchemes_returnColorsForAllCategories(scheme: AgeColorScheme) {
            AgeCategory.ALL.forEach { category ->
                val color = scheme.colorFor(category)
                assertNotNull(color)
                assertTrue(color.alpha > 0)
            }
        }

        @Test
        @DisplayName("byName finds scheme case-insensitively")
        fun byName_findsSchemeIgnoringCase() {
            assertEquals(AgeColorScheme.OCEAN, AgeColorScheme.byName("Ocean"))
            assertEquals(AgeColorScheme.OCEAN, AgeColorScheme.byName("OCEAN"))
            assertEquals(AgeColorScheme.OCEAN, AgeColorScheme.byName("ocean"))
        }

        @Test
        @DisplayName("byName returns WARMTH for unknown")
        fun byName_returnsWarmthForUnknown() {
            assertEquals(AgeColorScheme.WARMTH, AgeColorScheme.byName("unknown"))
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Warmth", AgeColorScheme.WARMTH.toString())
            assertEquals("Traffic Light", AgeColorScheme.TRAFFIC.toString())
        }

        @Test
        @DisplayName("ALL contains all schemes")
        fun all_containsAllSchemes() {
            assertEquals(4, AgeColorScheme.ALL.size)
        }

        @Test
        @DisplayName("WARMTH fresh color has green tint")
        fun warmth_freshColor_hasGreenTint() {
            val color = AgeColorScheme.WARMTH.colorFor(AgeCategory.FRESH)
            assertTrue(color.green > color.red)
            assertTrue(color.green > color.blue)
        }

        @Test
        @DisplayName("WARMTH ancient color has red tint")
        fun warmth_ancientColor_hasRedTint() {
            val color = AgeColorScheme.WARMTH.colorFor(AgeCategory.ANCIENT)
            assertTrue(color.red > color.blue)
        }
    }

    // =========================================================================
    // FileAgeInfo Tests
    // =========================================================================

    @Nested
    @DisplayName("FileAgeInfo")
    inner class FileAgeInfoTests {

        @Test
        @DisplayName("colorWith returns scheme color for category")
        fun colorWith_returnsSchemeColorForCategory() {
            val info = createAgeInfo(category = AgeCategory.FRESH)
            val color = info.colorWith(AgeColorScheme.WARMTH)

            assertEquals(AgeColorScheme.WARMTH.colorFor(AgeCategory.FRESH), color)
        }

        @Test
        @DisplayName("ageDescription formats minutes correctly")
        fun ageDescription_formatsMinutesCorrectly() {
            val info = createAgeInfo(age = Duration.ofMinutes(30))

            assertTrue(info.ageDescription.contains("30"))
            assertTrue(info.ageDescription.contains("minute"))
        }

        @Test
        @DisplayName("ageDescription formats hours correctly")
        fun ageDescription_formatsHoursCorrectly() {
            val info = createAgeInfo(age = Duration.ofHours(5))

            assertTrue(info.ageDescription.contains("5"))
            assertTrue(info.ageDescription.contains("hour"))
        }

        @Test
        @DisplayName("ageDescription formats days correctly")
        fun ageDescription_formatsDaysCorrectly() {
            val info = createAgeInfo(age = Duration.ofDays(3))

            assertTrue(info.ageDescription.contains("3"))
            assertTrue(info.ageDescription.contains("day"))
        }

        @Test
        @DisplayName("ageDescription returns 'just now' for very short duration")
        fun ageDescription_returnsJustNowForVeryShort() {
            val info = createAgeInfo(age = Duration.ofSeconds(30))

            assertEquals("just now", info.ageDescription)
        }

        @Test
        @DisplayName("summary includes category and age")
        fun summary_includesCategoryAndAge() {
            val info = createAgeInfo(category = AgeCategory.STALE, age = Duration.ofDays(3))

            assertTrue(info.summary.contains("Stale"))
            assertTrue(info.summary.contains("3"))
        }

        @Test
        @DisplayName("summary includes source")
        fun summary_includesSource() {
            val info = createAgeInfo(source = AgeSource.GIT)

            assertTrue(info.summary.contains("Git"))
        }

        private fun createAgeInfo(
            filePath: String = "/path/to/file.kt",
            lastModified: Instant = Instant.now().minus(Duration.ofHours(1)),
            age: Duration = Duration.ofHours(1),
            category: AgeCategory = AgeCategory.RECENT,
            source: AgeSource = AgeSource.FILESYSTEM
        ) = FileAgeInfo(filePath, lastModified, age, category, source)
    }

    // =========================================================================
    // FileAgeInfo.formatDuration Tests
    // =========================================================================

    @Nested
    @DisplayName("FileAgeInfo.formatDuration")
    inner class FormatDurationTests {

        @Test
        @DisplayName("formats seconds as 'just now'")
        fun formatsSeconds_asJustNow() {
            assertEquals("just now", FileAgeInfo.formatDuration(Duration.ofSeconds(30)))
        }

        @Test
        @DisplayName("formats single minute correctly")
        fun formatsSingleMinute_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofMinutes(1))
            assertEquals("1 minute ago", result)
        }

        @Test
        @DisplayName("formats multiple minutes correctly")
        fun formatsMultipleMinutes_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofMinutes(45))
            assertEquals("45 minutes ago", result)
        }

        @Test
        @DisplayName("formats single hour correctly")
        fun formatsSingleHour_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofHours(1))
            assertEquals("1 hour ago", result)
        }

        @Test
        @DisplayName("formats multiple hours correctly")
        fun formatsMultipleHours_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofHours(12))
            assertEquals("12 hours ago", result)
        }

        @Test
        @DisplayName("formats single day correctly")
        fun formatsSingleDay_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofDays(1))
            assertEquals("1 day ago", result)
        }

        @Test
        @DisplayName("formats multiple days correctly")
        fun formatsMultipleDays_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofDays(15))
            assertEquals("15 days ago", result)
        }

        @Test
        @DisplayName("formats months correctly")
        fun formatsMonths_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofDays(60))
            assertEquals("2 months ago", result)
        }

        @Test
        @DisplayName("formats years correctly")
        fun formatsYears_correctly() {
            val result = FileAgeInfo.formatDuration(Duration.ofDays(400))
            assertEquals("1 year ago", result)
        }
    }

    // =========================================================================
    // AgeSource Tests
    // =========================================================================

    @Nested
    @DisplayName("AgeSource")
    inner class AgeSourceTests {

        @ParameterizedTest
        @DisplayName("all sources have display names")
        @EnumSource(AgeSource::class)
        fun allSources_haveDisplayNames(source: AgeSource) {
            assertNotNull(source.displayName)
            assertTrue(source.displayName.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Git", AgeSource.GIT.toString())
            assertEquals("File System", AgeSource.FILESYSTEM.toString())
        }
    }

    // =========================================================================
    // AgeDetectionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("AgeDetectionResult")
    inner class AgeDetectionResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val info = createTestAgeInfo()
            val result = AgeDetectionResult.Success(info)
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Disabled has isSuccess false")
        fun disabled_hasIsSuccessFalse() {
            val result = AgeDetectionResult.Disabled()
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = AgeDetectionResult.Error("failed")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("ageInfoOrNull returns info for Success")
        fun ageInfoOrNull_returnsInfoForSuccess() {
            val info = createTestAgeInfo()
            val result = AgeDetectionResult.Success(info)

            assertEquals(info, result.ageInfoOrNull())
        }

        @Test
        @DisplayName("ageInfoOrNull returns null for non-Success")
        fun ageInfoOrNull_returnsNullForNonSuccess() {
            assertNull(AgeDetectionResult.Disabled().ageInfoOrNull())
            assertNull(AgeDetectionResult.Error("fail").ageInfoOrNull())
        }

        private fun createTestAgeInfo() = FileAgeInfo(
            "/path/to/file.kt",
            Instant.now(),
            Duration.ofHours(1),
            AgeCategory.RECENT,
            AgeSource.FILESYSTEM
        )
    }

    // =========================================================================
    // AgeStats Tests
    // =========================================================================

    @Nested
    @DisplayName("AgeStats")
    inner class AgeStatsTests {

        @Test
        @DisplayName("EMPTY has zero values")
        fun empty_hasZeroValues() {
            assertEquals(0, AgeStats.EMPTY.totalFiles)
            assertEquals(0, AgeStats.EMPTY.freshCount)
            assertEquals(0, AgeStats.EMPTY.recentCount)
            assertEquals(0, AgeStats.EMPTY.staleCount)
            assertEquals(0, AgeStats.EMPTY.ancientCount)
            assertEquals(Duration.ZERO, AgeStats.EMPTY.averageAge)
        }

        @Test
        @DisplayName("from calculates stats correctly")
        fun from_calculatesStatsCorrectly() {
            val now = Instant.now()
            val infos = listOf(
                FileAgeInfo("/a.kt", now, Duration.ofMinutes(30), AgeCategory.FRESH, AgeSource.GIT),
                FileAgeInfo("/b.kt", now, Duration.ofHours(5), AgeCategory.RECENT, AgeSource.GIT),
                FileAgeInfo("/c.kt", now, Duration.ofHours(6), AgeCategory.RECENT, AgeSource.GIT),
                FileAgeInfo("/d.kt", now, Duration.ofDays(3), AgeCategory.STALE, AgeSource.FILESYSTEM)
            )

            val stats = AgeStats.from(infos)

            assertEquals(4, stats.totalFiles)
            assertEquals(1, stats.freshCount)
            assertEquals(2, stats.recentCount)
            assertEquals(1, stats.staleCount)
            assertEquals(0, stats.ancientCount)
        }

        @Test
        @DisplayName("from returns EMPTY for empty list")
        fun from_returnsEmptyForEmptyList() {
            val stats = AgeStats.from(emptyList())
            assertEquals(AgeStats.EMPTY, stats)
        }

        @Test
        @DisplayName("percentages calculate correctly")
        fun percentages_calculateCorrectly() {
            val stats = AgeStats(10, 2, 3, 4, 1, Duration.ZERO)

            assertEquals(20, stats.freshPercent)
            assertEquals(30, stats.recentPercent)
            assertEquals(40, stats.stalePercent)
            assertEquals(10, stats.ancientPercent)
        }

        @Test
        @DisplayName("percentages return 0 when totalFiles is 0")
        fun percentages_returnZeroWhenEmpty() {
            assertEquals(0, AgeStats.EMPTY.freshPercent)
        }

        @Test
        @DisplayName("summary describes stats")
        fun summary_describesStats() {
            val stats = AgeStats(10, 2, 3, 4, 1, Duration.ofHours(5))

            assertTrue(stats.summary.contains("10 files"))
            assertTrue(stats.summary.contains("2 fresh"))
            assertTrue(stats.summary.contains("3 recent"))
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles zero duration")
        fun handlesZeroDuration() {
            val config = FileAgeConfig()
            assertEquals(AgeCategory.FRESH, config.categorize(Duration.ZERO))
        }

        @Test
        @DisplayName("handles negative duration gracefully")
        fun handlesNegativeDuration() {
            val config = FileAgeConfig()
            // Negative duration should be treated as fresh
            assertEquals(AgeCategory.FRESH, config.categorize(Duration.ofHours(-1)))
        }

        @Test
        @DisplayName("handles very long duration")
        fun handlesVeryLongDuration() {
            val config = FileAgeConfig()
            val age = Duration.ofDays(365 * 10) // 10 years

            assertEquals(AgeCategory.ANCIENT, config.categorize(age))
        }

        @Test
        @DisplayName("formatDuration handles multiple years")
        fun formatDuration_handlesMultipleYears() {
            val result = FileAgeInfo.formatDuration(Duration.ofDays(800))
            assertTrue(result.contains("2 years"))
        }

        @Test
        @DisplayName("grayscale scheme has uniform colors")
        fun grayscaleScheme_hasUniformColors() {
            AgeCategory.ALL.forEach { category ->
                val color = AgeColorScheme.GRAYSCALE.colorFor(category)
                // Grayscale colors have equal RGB values
                assertEquals(color.red, color.green)
                assertEquals(color.green, color.blue)
            }
        }
    }
}
