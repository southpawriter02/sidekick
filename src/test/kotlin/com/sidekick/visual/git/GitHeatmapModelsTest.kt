package com.sidekick.visual.git

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Color
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive unit tests for Git Heatmap Models.
 *
 * Tests cover:
 * - GitHeatmapConfig
 * - HeatmapMetric
 * - HeatmapColorScheme and interpolation
 * - LineGitStats and intensity calculation
 *
 * @since 0.5.5
 */
@DisplayName("Git Heatmap Models")
class GitHeatmapModelsTest {

    // =========================================================================
    // GitHeatmapConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("GitHeatmapConfig")
    inner class GitHeatmapConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = GitHeatmapConfig()

            assertTrue(config.enabled)
            assertTrue(config.showInGutter)
            assertEquals(HeatmapColorScheme.FIRE, config.colorScheme)
            assertEquals(HeatmapMetric.COMMIT_COUNT, config.metricType)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = GitHeatmapConfig(enabled = true)
            val toggled = config.toggle()

            assertTrue(config.enabled)
            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withScheme changes color scheme")
        fun withScheme_changesColorScheme() {
            val config = GitHeatmapConfig()
            val updated = config.withScheme(HeatmapColorScheme.VIRIDIS)

            assertEquals(HeatmapColorScheme.VIRIDIS, updated.colorScheme)
        }

        @Test
        @DisplayName("withMetric changes metric type")
        fun withMetric_changesMetricType() {
            val config = GitHeatmapConfig()
            val updated = config.withMetric(HeatmapMetric.LAST_CHANGED)

            assertEquals(HeatmapMetric.LAST_CHANGED, updated.metricType)
        }
    }

    // =========================================================================
    // HeatmapMetric Tests
    // =========================================================================

    @Nested
    @DisplayName("HeatmapMetric")
    inner class HeatmapMetricTests {

        @ParameterizedTest
        @DisplayName("all metrics have display names")
        @EnumSource(HeatmapMetric::class)
        fun allMetrics_haveDisplayNames(metric: HeatmapMetric) {
            assertNotNull(metric.displayName)
            assertTrue(metric.displayName.isNotBlank())
        }

        @Test
        @DisplayName("byName finds metric case-insensitively")
        fun byName_findsMetricIgnoringCase() {
            assertEquals(HeatmapMetric.LAST_CHANGED, HeatmapMetric.byName("Recency"))
            assertEquals(HeatmapMetric.LAST_CHANGED, HeatmapMetric.byName("RECENCY"))
        }

        @Test
        @DisplayName("byName returns COMMIT_COUNT for unknown")
        fun byName_returnsDefaultForUnknown() {
            assertEquals(HeatmapMetric.COMMIT_COUNT, HeatmapMetric.byName("Unknown"))
        }
    }

    // =========================================================================
    // HeatmapColorScheme Tests
    // =========================================================================

    @Nested
    @DisplayName("HeatmapColorScheme")
    inner class HeatmapColorSchemeTests {

        @Test
        @DisplayName("colorForIntensity returns endpoints correctly")
        fun colorForIntensity_returnsEndpointsCorrectly() {
            val scheme = HeatmapColorScheme.FIRE
            
            // First color (low intensity) - using 0.02 (above 0.01 threshold)
            val c1 = scheme.colorForIntensity(0.02f)
            val first = scheme.colors.first()
            assertEquals(first.red, c1.red) // Approximate check
            
            // Last color (high intensity)
            val c2 = scheme.colorForIntensity(1.0f)
            val last = scheme.colors.last()
            assertEquals(last.red, c2.red)
        }

        @Test
        @DisplayName("colorForIntensity interpolates correctly")
        fun colorForIntensity_interpolatesCorrectly() {
            // Define simple 2-color scheme manualy ideally, but using existing enum
            val scheme = HeatmapColorScheme.FIRE
            // Fire has 4 colors. Intensity 0.5 should be between color 1 and 2 (0-indexed 0,1,2,3)
            // 0.0 -> index 0
            // 0.33 -> index 1
            // 0.66 -> index 2
            // 1.0 -> index 3
            
            val c = scheme.colorForIntensity(0.33f)
            val expected = scheme.colors[1]
            
            // Allow small rounding diffs
            assertTrue(Math.abs(c.red - expected.red) < 5)
        }

        @Test
        @DisplayName("colorForIntensity handles out of bounds")
        fun colorForIntensity_handlesOutOfBounds() {
            val scheme = HeatmapColorScheme.FIRE
            val c1 = scheme.colorForIntensity(-1f)
            val c2 = scheme.colorForIntensity(2f)
            
            assertNotNull(c1)
            assertNotNull(c2)
        }
    }

    // =========================================================================
    // LineGitStats Tests
    // =========================================================================

    @Nested
    @DisplayName("LineGitStats")
    inner class LineGitStatsTests {

        @Test
        @DisplayName("getIntensity for COMMIT_COUNT scales properly")
        fun getIntensity_commitCount_scalesProperly() {
            // count / 10 clamped to 0.1 - 1.0
            
            val stats1 = createStats(commitCount = 1)
            assertEquals(0.1f, stats1.getIntensity(HeatmapMetric.COMMIT_COUNT))
            
            val stats5 = createStats(commitCount = 5)
            assertEquals(0.5f, stats5.getIntensity(HeatmapMetric.COMMIT_COUNT))
            
            val stats20 = createStats(commitCount = 20)
            assertEquals(1.0f, stats20.getIntensity(HeatmapMetric.COMMIT_COUNT))
        }

        @Test
        @DisplayName("getIntensity for LAST_CHANGED calculates recency")
        fun getIntensity_lastChanged_calculatesRecency() {
            val now = Instant.now()
            
            // < 1 day = 1.0
            val statsFresh = createStats(date = now.minus(Duration.ofHours(5)))
            assertEquals(1.0f, statsFresh.getIntensity(HeatmapMetric.LAST_CHANGED))
            
            // < 7 days = 0.8
            val statsRecent = createStats(date = now.minus(Duration.ofDays(3)))
            assertEquals(0.8f, statsRecent.getIntensity(HeatmapMetric.LAST_CHANGED))
            
            // < 30 days = 0.5
            val statsStale = createStats(date = now.minus(Duration.ofDays(15)))
            assertEquals(0.5f, statsStale.getIntensity(HeatmapMetric.LAST_CHANGED))
        }

        private fun createStats(
            line: Int = 1,
            commitCount: Int = 1,
            date: Instant = Instant.now()
        ) = LineGitStats(
            lineNumber = line,
            commitCount = commitCount,
            lastCommitDate = date,
            lastAuthor = "Ryan",
            authorCount = 1,
            commitHash = "abc1234"
        )
    }
}
