package com.sidekick.visual.separators

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color

/**
 * Comprehensive unit tests for Method Separator Models.
 *
 * Tests cover:
 * - MethodSeparatorConfig data class
 * - SeparatorLineStyle enum
 * - SeparableElementType enum
 * - SeparableElement computed properties
 * - SeparatorPosition data class
 * - SeparatorDetectionResult sealed class
 * - SeparatorStats calculations
 * - SeparatorColorTheme enum
 *
 * @since 0.5.3
 */
@DisplayName("Method Separator Models")
class MethodSeparatorModelsTest {

    // =========================================================================
    // MethodSeparatorConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("MethodSeparatorConfig")
    inner class MethodSeparatorConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = MethodSeparatorConfig()

            assertTrue(config.enabled)
            assertEquals(SeparatorLineStyle.SOLID, config.lineStyle)
            assertEquals(1, config.lineThickness)
            assertTrue(config.showBeforeClasses)
            assertFalse(config.showBeforeProperties)
            assertTrue(config.excludedLanguages.isEmpty())
            assertEquals(3, config.minMethodLines)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = MethodSeparatorConfig(enabled = true)
            val toggled = config.toggle()

            assertTrue(config.enabled)
            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withStyle changes line style")
        fun withStyle_changesLineStyle() {
            val config = MethodSeparatorConfig()
            val updated = config.withStyle(SeparatorLineStyle.DASHED)

            assertEquals(SeparatorLineStyle.DASHED, updated.lineStyle)
        }

        @Test
        @DisplayName("withColor changes line color")
        fun withColor_changesLineColor() {
            val config = MethodSeparatorConfig()
            val newColor = Color(255, 0, 0)
            val updated = config.withColor(newColor)

            assertEquals(newColor, updated.lineColor)
        }

        @Test
        @DisplayName("withThickness coerces to valid range")
        fun withThickness_coercesToValidRange() {
            val config = MethodSeparatorConfig()

            assertEquals(1, config.withThickness(0).lineThickness)
            assertEquals(2, config.withThickness(2).lineThickness)
            assertEquals(3, config.withThickness(10).lineThickness)
        }

        @Test
        @DisplayName("isLanguageExcluded checks case-insensitively")
        fun isLanguageExcluded_checksCaseInsensitively() {
            val config = MethodSeparatorConfig(excludedLanguages = setOf("kotlin"))

            assertTrue(config.isLanguageExcluded("kotlin"))
            assertTrue(config.isLanguageExcluded("KOTLIN"))
            assertFalse(config.isLanguageExcluded("java"))
        }

        @Test
        @DisplayName("DISABLED preset is disabled")
        fun disabledPreset_isDisabled() {
            assertFalse(MethodSeparatorConfig.DISABLED.enabled)
        }

        @Test
        @DisplayName("SUBTLE preset has low opacity color")
        fun subtlePreset_hasLowOpacityColor() {
            assertTrue(MethodSeparatorConfig.SUBTLE.lineColor.alpha < 60)
        }

        @Test
        @DisplayName("BOLD preset has higher thickness")
        fun boldPreset_hasHigherThickness() {
            assertEquals(2, MethodSeparatorConfig.BOLD.lineThickness)
        }
    }

    // =========================================================================
    // SeparatorLineStyle Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparatorLineStyle")
    inner class SeparatorLineStyleTests {

        @ParameterizedTest
        @DisplayName("all styles have display names")
        @EnumSource(SeparatorLineStyle::class)
        fun allStyles_haveDisplayNames(style: SeparatorLineStyle) {
            assertNotNull(style.displayName)
            assertTrue(style.displayName.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Solid", SeparatorLineStyle.SOLID.toString())
            assertEquals("Dashed", SeparatorLineStyle.DASHED.toString())
            assertEquals("Double", SeparatorLineStyle.DOUBLE.toString())
        }

        @Test
        @DisplayName("byName finds style case-insensitively")
        fun byName_findsStyleIgnoringCase() {
            assertEquals(SeparatorLineStyle.DASHED, SeparatorLineStyle.byName("Dashed"))
            assertEquals(SeparatorLineStyle.DASHED, SeparatorLineStyle.byName("DASHED"))
            assertEquals(SeparatorLineStyle.DASHED, SeparatorLineStyle.byName("dashed"))
        }

        @Test
        @DisplayName("byName returns SOLID for unknown")
        fun byName_returnsSolidForUnknown() {
            assertEquals(SeparatorLineStyle.SOLID, SeparatorLineStyle.byName("unknown"))
        }

        @Test
        @DisplayName("ALL contains all styles")
        fun all_containsAllStyles() {
            assertEquals(4, SeparatorLineStyle.ALL.size)
            assertTrue(SeparatorLineStyle.ALL.contains(SeparatorLineStyle.SOLID))
            assertTrue(SeparatorLineStyle.ALL.contains(SeparatorLineStyle.DASHED))
            assertTrue(SeparatorLineStyle.ALL.contains(SeparatorLineStyle.DOTTED))
            assertTrue(SeparatorLineStyle.ALL.contains(SeparatorLineStyle.DOUBLE))
        }
    }

    // =========================================================================
    // SeparableElementType Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparableElementType")
    inner class SeparableElementTypeTests {

        @ParameterizedTest
        @DisplayName("all types have display names")
        @EnumSource(SeparableElementType::class)
        fun allTypes_haveDisplayNames(type: SeparableElementType) {
            assertNotNull(type.displayName)
            assertTrue(type.displayName.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Method", SeparableElementType.METHOD.toString())
            assertEquals("Class", SeparableElementType.CLASS.toString())
            assertEquals("Enum Member", SeparableElementType.ENUM_MEMBER.toString())
        }

        @Test
        @DisplayName("ALL contains all types")
        fun all_containsAllTypes() {
            assertEquals(5, SeparableElementType.ALL.size)
        }
    }

    // =========================================================================
    // SeparableElement Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparableElement")
    inner class SeparableElementTests {

        @Test
        @DisplayName("separatorLine is one less than lineNumber")
        fun separatorLine_isOneLessThanLineNumber() {
            val element = createElement(lineNumber = 10)
            assertEquals(9, element.separatorLine)
        }

        @Test
        @DisplayName("separatorLine is at least 1")
        fun separatorLine_isAtLeast1() {
            val element = createElement(lineNumber = 1)
            assertEquals(1, element.separatorLine)
        }

        @Test
        @DisplayName("isSubstantial checks line count")
        fun isSubstantial_checksLineCount() {
            val small = createElement(lineCount = 2)
            val large = createElement(lineCount = 10)

            assertFalse(small.isSubstantial(3))
            assertTrue(large.isSubstantial(3))
        }

        @Test
        @DisplayName("summary includes type and name")
        fun summary_includesTypeAndName() {
            val element = createElement(elementName = "myMethod", lineCount = 15)

            assertTrue(element.summary.contains("Method"))
            assertTrue(element.summary.contains("myMethod"))
            assertTrue(element.summary.contains("15 lines"))
        }

        private fun createElement(
            lineNumber: Int = 10,
            elementType: SeparableElementType = SeparableElementType.METHOD,
            elementName: String = "testMethod",
            lineCount: Int = 5,
            offset: Int = 100
        ) = SeparableElement(lineNumber, elementType, elementName, lineCount, offset)
    }

    // =========================================================================
    // SeparatorPosition Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparatorPosition")
    inner class SeparatorPositionTests {

        @Test
        @DisplayName("summary describes position and element")
        fun summary_describesPositionAndElement() {
            val element = SeparableElement(10, SeparableElementType.METHOD, "foo", 5, 100)
            val position = SeparatorPosition(9, 0, element)

            assertTrue(position.summary.contains("Line 9"))
            assertTrue(position.summary.contains("Method"))
            assertTrue(position.summary.contains("foo"))
        }

        @Test
        @DisplayName("default yOffset is 0")
        fun defaultYOffset_isZero() {
            val element = SeparableElement(10, SeparableElementType.CLASS, "Bar", 20, 200)
            val position = SeparatorPosition(9, element = element)

            assertEquals(0, position.yOffset)
        }
    }

    // =========================================================================
    // SeparatorDetectionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparatorDetectionResult")
    inner class SeparatorDetectionResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val result = SeparatorDetectionResult.Success(emptyList())
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Disabled has isSuccess false")
        fun disabled_hasIsSuccessFalse() {
            val result = SeparatorDetectionResult.Disabled()
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Excluded has isSuccess false")
        fun excluded_hasIsSuccessFalse() {
            val result = SeparatorDetectionResult.Excluded("kotlin")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = SeparatorDetectionResult.Error("failed")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("positionsOrEmpty returns positions for Success")
        fun positionsOrEmpty_returnsPositionsForSuccess() {
            val element = SeparableElement(10, SeparableElementType.METHOD, "foo", 5, 100)
            val positions = listOf(SeparatorPosition(9, 0, element))
            val result = SeparatorDetectionResult.Success(positions)

            assertEquals(positions, result.positionsOrEmpty())
        }

        @Test
        @DisplayName("positionsOrEmpty returns empty for non-Success")
        fun positionsOrEmpty_returnsEmptyForNonSuccess() {
            assertTrue(SeparatorDetectionResult.Disabled().positionsOrEmpty().isEmpty())
            assertTrue(SeparatorDetectionResult.Error("fail").positionsOrEmpty().isEmpty())
        }
    }

    // =========================================================================
    // SeparatorStats Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparatorStats")
    inner class SeparatorStatsTests {

        @Test
        @DisplayName("EMPTY has zero values")
        fun empty_hasZeroValues() {
            assertEquals(0, SeparatorStats.EMPTY.totalSeparators)
            assertEquals(0, SeparatorStats.EMPTY.methodCount)
            assertEquals(0, SeparatorStats.EMPTY.classCount)
            assertEquals(0, SeparatorStats.EMPTY.propertyCount)
        }

        @Test
        @DisplayName("from calculates stats correctly")
        fun from_calculatesStatsCorrectly() {
            val elements = listOf(
                SeparableElement(1, SeparableElementType.CLASS, "Foo", 50, 0),
                SeparableElement(10, SeparableElementType.METHOD, "bar", 10, 100),
                SeparableElement(25, SeparableElementType.METHOD, "baz", 8, 250),
                SeparableElement(40, SeparableElementType.PROPERTY, "prop", 1, 400)
            )

            val stats = SeparatorStats.from(elements)

            assertEquals(4, stats.totalSeparators)
            assertEquals(2, stats.methodCount)
            assertEquals(1, stats.classCount)
            assertEquals(1, stats.propertyCount)
        }

        @Test
        @DisplayName("from returns EMPTY for empty list")
        fun from_returnsEmptyForEmptyList() {
            val stats = SeparatorStats.from(emptyList())
            assertEquals(SeparatorStats.EMPTY, stats)
        }

        @Test
        @DisplayName("summary describes stats")
        fun summary_describesStats() {
            val stats = SeparatorStats(5, 3, 2, 0)

            assertTrue(stats.summary.contains("5 separators"))
            assertTrue(stats.summary.contains("3 methods"))
            assertTrue(stats.summary.contains("2 classes"))
        }
    }

    // =========================================================================
    // SeparatorColorTheme Tests
    // =========================================================================

    @Nested
    @DisplayName("SeparatorColorTheme")
    inner class SeparatorColorThemeTests {

        @ParameterizedTest
        @DisplayName("all themes have colors with alpha")
        @EnumSource(SeparatorColorTheme::class)
        fun allThemes_haveColorsWithAlpha(theme: SeparatorColorTheme) {
            assertNotNull(theme.color)
            assertTrue(theme.color.alpha > 0)
            assertTrue(theme.color.alpha < 255) // Should be semi-transparent
        }

        @Test
        @DisplayName("byName finds theme case-insensitively")
        fun byName_findsThemeIgnoringCase() {
            assertEquals(SeparatorColorTheme.BLUE, SeparatorColorTheme.byName("Blue"))
            assertEquals(SeparatorColorTheme.BLUE, SeparatorColorTheme.byName("BLUE"))
            assertEquals(SeparatorColorTheme.BLUE, SeparatorColorTheme.byName("blue"))
        }

        @Test
        @DisplayName("byName returns GRAY for unknown")
        fun byName_returnsGrayForUnknown() {
            assertEquals(SeparatorColorTheme.GRAY, SeparatorColorTheme.byName("unknown"))
        }

        @Test
        @DisplayName("ALL contains all themes")
        fun all_containsAllThemes() {
            assertEquals(5, SeparatorColorTheme.ALL.size)
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles element at line 1")
        fun handlesElementAtLine1() {
            val element = SeparableElement(1, SeparableElementType.CLASS, "Main", 100, 0)
            assertEquals(1, element.separatorLine)
        }

        @Test
        @DisplayName("handles zero line count")
        fun handlesZeroLineCount() {
            val element = SeparableElement(10, SeparableElementType.METHOD, "empty", 0, 100)
            assertFalse(element.isSubstantial(1))
        }

        @ParameterizedTest
        @DisplayName("thickness values are coerced")
        @ValueSource(ints = [-1, 0, 1, 2, 3, 4, 100])
        fun thicknessValues_areCoerced(thickness: Int) {
            val config = MethodSeparatorConfig().withThickness(thickness)
            assertTrue(config.lineThickness in 1..3)
        }

        @Test
        @DisplayName("empty excluded languages allows all")
        fun emptyExcludedLanguages_allowsAll() {
            val config = MethodSeparatorConfig(excludedLanguages = emptySet())
            assertFalse(config.isLanguageExcluded("kotlin"))
            assertFalse(config.isLanguageExcluded("java"))
        }
    }
}
