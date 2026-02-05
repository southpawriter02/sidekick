package com.sidekick.visual.tabs

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color

/**
 * Comprehensive unit tests for Depth-Coded Tab Models.
 *
 * Tests cover:
 * - DepthTabConfig data class
 * - ColorPalette colors and mappings
 * - FileDepthInfo computed properties
 * - DepthResult sealed class
 * - DepthMode enum
 *
 * @since 0.5.1
 */
@DisplayName("Depth Tab Models")
class DepthTabModelsTest {

    // =========================================================================
    // DepthTabConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("DepthTabConfig")
    inner class DepthTabConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = DepthTabConfig()

            assertTrue(config.enabled)
            assertEquals(ColorPalette.DEFAULT, config.colorPalette)
            assertEquals(6, config.maxDepth)
            assertNull(config.baseDirectory)
            assertEquals(0.3f, config.opacity)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = DepthTabConfig(enabled = true)
            val toggled = config.toggle()

            assertTrue(config.enabled)
            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withPalette changes palette")
        fun withPalette_changesPalette() {
            val config = DepthTabConfig()
            val updated = config.withPalette(ColorPalette.RAINBOW)

            assertEquals(ColorPalette.RAINBOW, updated.colorPalette)
        }

        @Test
        @DisplayName("withMaxDepth coerces to valid range")
        fun withMaxDepth_coercesToValidRange() {
            val config = DepthTabConfig()

            assertEquals(1, config.withMaxDepth(0).maxDepth)
            assertEquals(5, config.withMaxDepth(5).maxDepth)
            assertEquals(10, config.withMaxDepth(100).maxDepth)
        }

        @Test
        @DisplayName("withOpacity coerces to valid range")
        fun withOpacity_coercesToValidRange() {
            val config = DepthTabConfig()

            assertEquals(0.1f, config.withOpacity(0f).opacity)
            assertEquals(0.5f, config.withOpacity(0.5f).opacity)
            assertEquals(1.0f, config.withOpacity(2f).opacity)
        }

        @Test
        @DisplayName("withBaseDirectory sets directory")
        fun withBaseDirectory_setsDirectory() {
            val config = DepthTabConfig()
            val updated = config.withBaseDirectory("/custom/base")

            assertEquals("/custom/base", updated.baseDirectory)
        }

        @Test
        @DisplayName("DISABLED preset is disabled")
        fun disabledPreset_isDisabled() {
            assertFalse(DepthTabConfig.DISABLED.enabled)
        }
    }

    // =========================================================================
    // ColorPalette Tests
    // =========================================================================

    @Nested
    @DisplayName("ColorPalette")
    inner class ColorPaletteTests {

        @Test
        @DisplayName("DEFAULT palette has 7 colors")
        fun defaultPalette_has7Colors() {
            assertEquals(7, ColorPalette.DEFAULT.colors.size)
            assertEquals(7, ColorPalette.DEFAULT.depthCount)
        }

        @Test
        @DisplayName("colorForDepth returns correct color")
        fun colorForDepth_returnsCorrectColor() {
            val palette = ColorPalette.DEFAULT
            
            assertEquals(palette.colors[0], palette.colorForDepth(0))
            assertEquals(palette.colors[3], palette.colorForDepth(3))
        }

        @Test
        @DisplayName("colorForDepth clamps out of range")
        fun colorForDepth_clampsOutOfRange() {
            val palette = ColorPalette.DEFAULT
            
            assertEquals(palette.colors.first(), palette.colorForDepth(-1))
            assertEquals(palette.colors.last(), palette.colorForDepth(100))
        }

        @Test
        @DisplayName("colorForDepth with opacity applies alpha")
        fun colorForDepth_withOpacity_appliesAlpha() {
            val palette = ColorPalette.DEFAULT
            val color = palette.colorForDepth(0, 0.5f)

            assertTrue(color.alpha in 126..128) // 0.5 * 255 ≈ 127
        }

        @Test
        @DisplayName("RAINBOW palette has 7 colors")
        fun rainbowPalette_has7Colors() {
            assertEquals(7, ColorPalette.RAINBOW.colors.size)
        }

        @Test
        @DisplayName("MONOCHROME palette has grayscale colors")
        fun monochromePalette_hasGrayscaleColors() {
            ColorPalette.MONOCHROME.colors.forEach { color ->
                assertEquals(color.red, color.green)
                assertEquals(color.green, color.blue)
            }
        }

        @Test
        @DisplayName("PASTEL palette has soft colors")
        fun pastelPalette_hasSoftColors() {
            ColorPalette.PASTEL.colors.forEach { color ->
                assertTrue(color.red >= 140)
                assertTrue(color.green >= 140)
                assertTrue(color.blue >= 140)
            }
        }

        @Test
        @DisplayName("ALL contains all palettes")
        fun all_containsAllPalettes() {
            assertTrue(ColorPalette.ALL.contains(ColorPalette.DEFAULT))
            assertTrue(ColorPalette.ALL.contains(ColorPalette.RAINBOW))
            assertTrue(ColorPalette.ALL.contains(ColorPalette.MONOCHROME))
            assertTrue(ColorPalette.ALL.contains(ColorPalette.PASTEL))
        }

        @Test
        @DisplayName("byName finds palette case-insensitively")
        fun byName_findsPaletteIgnoringCase() {
            assertEquals(ColorPalette.RAINBOW, ColorPalette.byName("Rainbow"))
            assertEquals(ColorPalette.RAINBOW, ColorPalette.byName("RAINBOW"))
            assertEquals(ColorPalette.RAINBOW, ColorPalette.byName("rainbow"))
        }

        @Test
        @DisplayName("byName returns DEFAULT for unknown")
        fun byName_returnsDefaultForUnknown() {
            assertEquals(ColorPalette.DEFAULT, ColorPalette.byName("unknown"))
        }

        @Test
        @DisplayName("toString returns name")
        fun toString_returnsName() {
            assertEquals("Ocean", ColorPalette.DEFAULT.toString())
            assertEquals("Rainbow", ColorPalette.RAINBOW.toString())
        }
    }

    // =========================================================================
    // FileDepthInfo Tests
    // =========================================================================

    @Nested
    @DisplayName("FileDepthInfo")
    inner class FileDepthInfoTests {

        @Test
        @DisplayName("depthLabel describes depth correctly")
        fun depthLabel_describesDepthCorrectly() {
            assertEquals("Root", createInfo(depth = 0).depthLabel)
            assertEquals("Level 1", createInfo(depth = 1).depthLabel)
            assertEquals("Level 2", createInfo(depth = 2).depthLabel)
            assertEquals("Level 5", createInfo(depth = 5).depthLabel)
        }

        @Test
        @DisplayName("fileName extracts from path")
        fun fileName_extractsFromPath() {
            val info = createInfo(filePath = "/project/src/Main.kt")
            assertEquals("Main.kt", info.fileName)
        }

        @Test
        @DisplayName("parentDir returns last segment")
        fun parentDir_returnsLastSegment() {
            val info = createInfo(segments = listOf("src", "main", "kotlin"))
            assertEquals("kotlin", info.parentDir)
        }

        @Test
        @DisplayName("parentDir returns null for empty segments")
        fun parentDir_returnsNullForEmptySegments() {
            val info = createInfo(segments = emptyList())
            assertNull(info.parentDir)
        }

        @Test
        @DisplayName("summary includes depth")
        fun summary_includesDepth() {
            val info = createInfo(depth = 3)
            assertTrue(info.summary.contains("Depth: 3"))
        }

        @Test
        @DisplayName("summary includes namespace when present")
        fun summary_includesNamespaceWhenPresent() {
            val info = createInfo(namespace = "com.example.app")
            assertTrue(info.summary.contains("Namespace: com.example.app"))
        }

        @Test
        @DisplayName("summary includes folder when present")
        fun summary_includesFolderWhenPresent() {
            val info = createInfo(segments = listOf("src", "main"))
            assertTrue(info.summary.contains("Folder: main"))
        }

        private fun createInfo(
            filePath: String = "/test/file.kt",
            depth: Int = 0,
            namespace: String? = null,
            color: Color = Color.BLUE,
            segments: List<String> = emptyList()
        ) = FileDepthInfo(filePath, depth, namespace, color, segments)
    }

    // =========================================================================
    // DepthResult Tests
    // =========================================================================

    @Nested
    @DisplayName("DepthResult")
    inner class DepthResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val info = FileDepthInfo("/test.kt", 0, null, Color.BLUE)
            val result = DepthResult.Success(info)
            
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Disabled has isSuccess false")
        fun disabled_hasIsSuccessFalse() {
            val result = DepthResult.Disabled()
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = DepthResult.Error("failed")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("getOrNull returns info for Success")
        fun getOrNull_returnsInfoForSuccess() {
            val info = FileDepthInfo("/test.kt", 2, null, Color.BLUE)
            val result = DepthResult.Success(info)
            
            assertEquals(info, result.getOrNull())
        }

        @Test
        @DisplayName("getOrNull returns null for Disabled")
        fun getOrNull_returnsNullForDisabled() {
            assertNull(DepthResult.Disabled().getOrNull())
        }

        @Test
        @DisplayName("getOrNull returns null for Error")
        fun getOrNull_returnsNullForError() {
            assertNull(DepthResult.Error("fail").getOrNull())
        }

        @Test
        @DisplayName("pattern matching works correctly")
        fun patternMatching_worksCorrectly() {
            val results = listOf(
                DepthResult.Success(FileDepthInfo("/t.kt", 0, null, Color.BLUE)),
                DepthResult.Disabled(),
                DepthResult.Error("test")
            )

            val types = results.map { result ->
                when (result) {
                    is DepthResult.Success -> "success"
                    is DepthResult.Disabled -> "disabled"
                    is DepthResult.Error -> "error"
                }
            }

            assertEquals("success", types[0])
            assertEquals("disabled", types[1])
            assertEquals("error", types[2])
        }
    }

    // =========================================================================
    // DepthMode Tests
    // =========================================================================

    @Nested
    @DisplayName("DepthMode")
    inner class DepthModeTests {

        @ParameterizedTest
        @DisplayName("all modes have display names")
        @EnumSource(DepthMode::class)
        fun allModes_haveDisplayNames(mode: DepthMode) {
            assertNotNull(mode.displayName)
            assertTrue(mode.displayName.isNotBlank())
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Directory", DepthMode.DIRECTORY.toString())
            assertEquals("Namespace", DepthMode.NAMESPACE.toString())
            assertEquals("Hybrid", DepthMode.HYBRID.toString())
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles very deep paths")
        fun handlesVeryDeepPaths() {
            val palette = ColorPalette.DEFAULT
            val color = palette.colorForDepth(100)
            
            assertEquals(palette.colors.last(), color)
        }

        @Test
        @DisplayName("handles negative depth")
        fun handlesNegativeDepth() {
            val palette = ColorPalette.DEFAULT
            val color = palette.colorForDepth(-5)
            
            assertEquals(palette.colors.first(), color)
        }

        @ParameterizedTest
        @DisplayName("opacity values produce valid alpha")
        @ValueSource(floats = [0f, 0.25f, 0.5f, 0.75f, 1f])
        fun opacityValues_produceValidAlpha(opacity: Float) {
            val palette = ColorPalette.DEFAULT
            val color = palette.colorForDepth(0, opacity)
            
            assertTrue(color.alpha in 0..255)
        }

        @Test
        @DisplayName("empty segments produce null parentDir")
        fun emptySegments_produceNullParentDir() {
            val info = FileDepthInfo("/root.kt", 0, null, Color.BLUE, emptyList())
            assertNull(info.parentDir)
        }

        @Test
        @DisplayName("single segment path has correct fileName")
        fun singleSegmentPath_hasCorrectFileName() {
            val info = FileDepthInfo("file.kt", 0, null, Color.BLUE)
            assertEquals("file.kt", info.fileName)
        }

        @Test
        @DisplayName("path with no extension has correct fileName")
        fun pathWithNoExtension_hasCorrectFileName() {
            val info = FileDepthInfo("/project/Makefile", 1, null, Color.BLUE)
            assertEquals("Makefile", info.fileName)
        }

        @Test
        @DisplayName("color with zero opacity is transparent")
        fun colorWithZeroOpacity_isTransparent() {
            val palette = ColorPalette.DEFAULT
            val color = palette.colorForDepth(0, 0f)
            
            assertEquals(0, color.alpha)
        }

        @Test
        @DisplayName("color with full opacity is opaque")
        fun colorWithFullOpacity_isOpaque() {
            val palette = ColorPalette.DEFAULT
            val color = palette.colorForDepth(0, 1f)
            
            assertEquals(255, color.alpha)
        }
    }
}
