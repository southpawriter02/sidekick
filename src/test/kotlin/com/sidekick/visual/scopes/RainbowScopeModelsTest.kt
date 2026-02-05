package com.sidekick.visual.scopes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color

/**
 * Comprehensive unit tests for Rainbow Scope Models.
 *
 * Tests cover:
 * - RainbowScopeConfig data class
 * - ScopeColorScheme colors and mappings
 * - ScopeType enum
 * - CodeScope computed properties
 * - ScopeDetectionResult sealed class
 * - ScopeStats calculations
 *
 * @since 0.5.2
 */
@DisplayName("Rainbow Scope Models")
class RainbowScopeModelsTest {

    // =========================================================================
    // RainbowScopeConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("RainbowScopeConfig")
    inner class RainbowScopeConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = RainbowScopeConfig()

            assertTrue(config.enabled)
            assertEquals(0.05f, config.opacity)
            assertEquals(5, config.maxNestingLevel)
            assertEquals(ScopeColorScheme.WARM, config.colorScheme)
            assertTrue(config.excludedLanguages.isEmpty())
            assertTrue(config.excludedScopeTypes.isEmpty())
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = RainbowScopeConfig(enabled = true)
            val toggled = config.toggle()

            assertTrue(config.enabled)
            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withOpacity coerces to valid range")
        fun withOpacity_coercesToValidRange() {
            val config = RainbowScopeConfig()

            assertEquals(0.01f, config.withOpacity(0f).opacity)
            assertEquals(0.15f, config.withOpacity(0.15f).opacity)
            assertEquals(0.3f, config.withOpacity(1f).opacity)
        }

        @Test
        @DisplayName("withMaxNesting coerces to valid range")
        fun withMaxNesting_coercesToValidRange() {
            val config = RainbowScopeConfig()

            assertEquals(1, config.withMaxNesting(0).maxNestingLevel)
            assertEquals(5, config.withMaxNesting(5).maxNestingLevel)
            assertEquals(10, config.withMaxNesting(100).maxNestingLevel)
        }

        @Test
        @DisplayName("withScheme changes scheme")
        fun withScheme_changesScheme() {
            val config = RainbowScopeConfig()
            val updated = config.withScheme(ScopeColorScheme.COOL)

            assertEquals(ScopeColorScheme.COOL, updated.colorScheme)
        }

        @Test
        @DisplayName("isLanguageExcluded checks case-insensitively")
        fun isLanguageExcluded_checksCaseInsensitively() {
            val config = RainbowScopeConfig(excludedLanguages = setOf("kotlin"))

            assertTrue(config.isLanguageExcluded("kotlin"))
            assertTrue(config.isLanguageExcluded("KOTLIN"))
            assertFalse(config.isLanguageExcluded("java"))
        }

        @Test
        @DisplayName("isScopeExcluded checks excluded types")
        fun isScopeExcluded_checksExcludedTypes() {
            val config = RainbowScopeConfig(excludedScopeTypes = setOf(ScopeType.BLOCK))

            assertTrue(config.isScopeExcluded(ScopeType.BLOCK))
            assertFalse(config.isScopeExcluded(ScopeType.METHOD))
        }

        @Test
        @DisplayName("DISABLED preset is disabled")
        fun disabledPreset_isDisabled() {
            assertFalse(RainbowScopeConfig.DISABLED.enabled)
        }

        @Test
        @DisplayName("SUBTLE preset has low opacity")
        fun subtlePreset_hasLowOpacity() {
            assertEquals(0.03f, RainbowScopeConfig.SUBTLE.opacity)
        }

        @Test
        @DisplayName("VIVID preset has high opacity")
        fun vividPreset_hasHighOpacity() {
            assertEquals(0.12f, RainbowScopeConfig.VIVID.opacity)
        }
    }

    // =========================================================================
    // ScopeColorScheme Tests
    // =========================================================================

    @Nested
    @DisplayName("ScopeColorScheme")
    inner class ScopeColorSchemeTests {

        @ParameterizedTest
        @DisplayName("all schemes have 5 colors")
        @EnumSource(ScopeColorScheme::class)
        fun allSchemes_have5Colors(scheme: ScopeColorScheme) {
            assertEquals(5, scheme.colors.size)
            assertEquals(5, scheme.levelCount)
        }

        @Test
        @DisplayName("colorForLevel returns correct color with opacity")
        fun colorForLevel_returnsCorrectColorWithOpacity() {
            val scheme = ScopeColorScheme.WARM
            val color = scheme.colorForLevel(1, 0.5f)

            assertEquals(scheme.colors[0].red, color.red)
            assertEquals(scheme.colors[0].green, color.green)
            assertEquals(scheme.colors[0].blue, color.blue)
            assertTrue(color.alpha in 126..128) // ~127 for 0.5 * 255
        }

        @Test
        @DisplayName("baseColorForLevel returns color without alpha")
        fun baseColorForLevel_returnsColorWithoutAlpha() {
            val scheme = ScopeColorScheme.COOL
            val color = scheme.baseColorForLevel(2)

            assertEquals(scheme.colors[1], color)
        }

        @Test
        @DisplayName("colorForLevel clamps out of range")
        fun colorForLevel_clampsOutOfRange() {
            val scheme = ScopeColorScheme.WARM

            assertEquals(scheme.colors.first().red, scheme.colorForLevel(0, 1f).red)
            assertEquals(scheme.colors.last().red, scheme.colorForLevel(100, 1f).red)
        }

        @Test
        @DisplayName("byName finds scheme case-insensitively")
        fun byName_findsSchemeIgnoringCase() {
            assertEquals(ScopeColorScheme.COOL, ScopeColorScheme.byName("Cool"))
            assertEquals(ScopeColorScheme.COOL, ScopeColorScheme.byName("COOL"))
            assertEquals(ScopeColorScheme.COOL, ScopeColorScheme.byName("cool"))
        }

        @Test
        @DisplayName("byName returns WARM for unknown")
        fun byName_returnsWarmForUnknown() {
            assertEquals(ScopeColorScheme.WARM, ScopeColorScheme.byName("unknown"))
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Warm", ScopeColorScheme.WARM.toString())
            assertEquals("Neon", ScopeColorScheme.NEON.toString())
        }

        @Test
        @DisplayName("ALL contains all schemes")
        fun all_containsAllSchemes() {
            assertEquals(4, ScopeColorScheme.ALL.size)
            assertTrue(ScopeColorScheme.ALL.contains(ScopeColorScheme.WARM))
            assertTrue(ScopeColorScheme.ALL.contains(ScopeColorScheme.COOL))
            assertTrue(ScopeColorScheme.ALL.contains(ScopeColorScheme.MONOCHROME))
            assertTrue(ScopeColorScheme.ALL.contains(ScopeColorScheme.NEON))
        }
    }

    // =========================================================================
    // ScopeType Tests
    // =========================================================================

    @Nested
    @DisplayName("ScopeType")
    inner class ScopeTypeTests {

        @ParameterizedTest
        @DisplayName("all types have display names")
        @EnumSource(ScopeType::class)
        fun allTypes_haveDisplayNames(type: ScopeType) {
            assertNotNull(type.displayName)
            assertTrue(type.displayName.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Class", ScopeType.CLASS.toString())
            assertEquals("Method", ScopeType.METHOD.toString())
            assertEquals("Try/Catch", ScopeType.TRY_CATCH.toString())
        }

        @Test
        @DisplayName("ALL contains all types")
        fun all_containsAllTypes() {
            assertEquals(7, ScopeType.ALL.size)
        }
    }

    // =========================================================================
    // CodeScope Tests
    // =========================================================================

    @Nested
    @DisplayName("CodeScope")
    inner class CodeScopeTests {

        @Test
        @DisplayName("length calculates correctly")
        fun length_calculatesCorrectly() {
            val scope = createScope(startOffset = 10, endOffset = 50)
            assertEquals(40, scope.length)
        }

        @Test
        @DisplayName("contains checks offset correctly")
        fun contains_checksOffsetCorrectly() {
            val scope = createScope(startOffset = 10, endOffset = 50)

            assertFalse(scope.contains(5))
            assertTrue(scope.contains(10))
            assertTrue(scope.contains(30))
            assertTrue(scope.contains(50))
            assertFalse(scope.contains(51))
        }

        @Test
        @DisplayName("overlaps detects overlap correctly")
        fun overlaps_detectsOverlapCorrectly() {
            val scope = createScope(startOffset = 10, endOffset = 50)

            assertTrue(scope.overlaps(createScope(0, 20)))
            assertTrue(scope.overlaps(createScope(40, 60)))
            assertTrue(scope.overlaps(createScope(20, 30)))
            assertFalse(scope.overlaps(createScope(0, 10)))
            assertFalse(scope.overlaps(createScope(50, 60)))
        }

        @Test
        @DisplayName("fullyContains checks containment correctly")
        fun fullyContains_checksContainmentCorrectly() {
            val outer = createScope(startOffset = 10, endOffset = 100)
            val inner = createScope(startOffset = 20, endOffset = 80)

            assertTrue(outer.fullyContains(inner))
            assertFalse(inner.fullyContains(outer))
        }

        @Test
        @DisplayName("summary includes type and level")
        fun summary_includesTypeAndLevel() {
            val scope = createScope(nestingLevel = 3, name = "myMethod")

            assertTrue(scope.summary.contains("Method"))
            assertTrue(scope.summary.contains("Level 3"))
            assertTrue(scope.summary.contains("myMethod"))
        }

        @Test
        @DisplayName("summary without name excludes name")
        fun summary_withoutName_excludesName() {
            val scope = createScope(name = null)

            assertTrue(scope.summary.contains("Method"))
            assertFalse(scope.summary.contains(":"))
        }

        private fun createScope(
            startOffset: Int = 0,
            endOffset: Int = 100,
            nestingLevel: Int = 1,
            scopeType: ScopeType = ScopeType.METHOD,
            name: String? = null
        ) = CodeScope(startOffset, endOffset, nestingLevel, scopeType, name)
    }

    // =========================================================================
    // ScopeDetectionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ScopeDetectionResult")
    inner class ScopeDetectionResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val result = ScopeDetectionResult.Success(emptyList())
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Disabled has isSuccess false")
        fun disabled_hasIsSuccessFalse() {
            val result = ScopeDetectionResult.Disabled()
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Excluded has isSuccess false")
        fun excluded_hasIsSuccessFalse() {
            val result = ScopeDetectionResult.Excluded("kotlin")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = ScopeDetectionResult.Error("failed")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("getScopes returns scopes for Success")
        fun getScopes_returnsScopesForSuccess() {
            val scopes = listOf(CodeScope(0, 10, 1, ScopeType.METHOD))
            val result = ScopeDetectionResult.Success(scopes)

            assertEquals(scopes, result.scopesOrEmpty())
        }

        @Test
        @DisplayName("getScopes returns empty for non-Success")
        fun getScopes_returnsEmptyForNonSuccess() {
            assertTrue(ScopeDetectionResult.Disabled().scopesOrEmpty().isEmpty())
            assertTrue(ScopeDetectionResult.Error("fail").scopesOrEmpty().isEmpty())
        }
    }

    // =========================================================================
    // ScopeStats Tests
    // =========================================================================

    @Nested
    @DisplayName("ScopeStats")
    inner class ScopeStatsTests {

        @Test
        @DisplayName("EMPTY has zero values")
        fun empty_hasZeroValues() {
            assertEquals(0, ScopeStats.EMPTY.totalScopes)
            assertEquals(0, ScopeStats.EMPTY.maxNesting)
            assertTrue(ScopeStats.EMPTY.scopesByType.isEmpty())
        }

        @Test
        @DisplayName("from calculates stats correctly")
        fun from_calculatesStatsCorrectly() {
            val scopes = listOf(
                CodeScope(0, 100, 1, ScopeType.CLASS),
                CodeScope(10, 50, 2, ScopeType.METHOD),
                CodeScope(60, 90, 2, ScopeType.METHOD),
                CodeScope(20, 40, 3, ScopeType.LOOP)
            )

            val stats = ScopeStats.from(scopes)

            assertEquals(4, stats.totalScopes)
            assertEquals(3, stats.maxNesting)
            assertEquals(1, stats.scopesByType[ScopeType.CLASS])
            assertEquals(2, stats.scopesByType[ScopeType.METHOD])
            assertEquals(1, stats.scopesByType[ScopeType.LOOP])
        }

        @Test
        @DisplayName("from returns EMPTY for empty list")
        fun from_returnsEmptyForEmptyList() {
            val stats = ScopeStats.from(emptyList())
            assertEquals(ScopeStats.EMPTY, stats)
        }

        @Test
        @DisplayName("summary describes stats")
        fun summary_describesStats() {
            val stats = ScopeStats(10, 3, mapOf(ScopeType.METHOD to 5))

            assertTrue(stats.summary.contains("10 scopes"))
            assertTrue(stats.summary.contains("depth 3"))
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles zero-length scope")
        fun handlesZeroLengthScope() {
            val scope = CodeScope(50, 50, 1, ScopeType.BLOCK)
            assertEquals(0, scope.length)
            assertTrue(scope.contains(50))
        }

        @ParameterizedTest
        @DisplayName("opacity values produce valid alpha")
        @ValueSource(floats = [0f, 0.1f, 0.2f, 0.3f])
        fun opacityValues_produceValidAlpha(opacity: Float) {
            val scheme = ScopeColorScheme.WARM
            val color = scheme.colorForLevel(1, opacity)

            assertTrue(color.alpha in 0..255)
        }

        @Test
        @DisplayName("handles deeply nested scopes")
        fun handlesDeepNesting() {
            val config = RainbowScopeConfig(maxNestingLevel = 3)
            assertEquals(3, config.maxNestingLevel)
        }

        @Test
        @DisplayName("color with zero opacity is transparent")
        fun colorWithZeroOpacity_isTransparent() {
            val scheme = ScopeColorScheme.WARM
            val color = scheme.colorForLevel(1, 0f)

            assertEquals(0, color.alpha)
        }

        @Test
        @DisplayName("color with neon scheme is vivid")
        fun colorWithNeonScheme_isVivid() {
            val scheme = ScopeColorScheme.NEON
            val color = scheme.baseColorForLevel(1)

            // Neon colors should have at least one high channel
            assertTrue(color.red > 200 || color.green > 200 || color.blue > 200)
        }
    }
}
