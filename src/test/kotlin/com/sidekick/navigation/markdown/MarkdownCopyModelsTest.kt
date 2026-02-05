package com.sidekick.navigation.markdown

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Comprehensive unit tests for Markdown Copy Models.
 *
 * Tests cover:
 * - MarkdownCopyOptions data class
 * - MarkdownCode formatting
 * - MarkdownCopyResult sealed class
 * - MarkdownPreset enum
 * - Language mapping
 * - Edge cases
 *
 * @since 0.4.5
 */
@DisplayName("Markdown Copy Models")
class MarkdownCopyModelsTest {

    // =========================================================================
    // MarkdownCopyOptions Tests
    // =========================================================================

    @Nested
    @DisplayName("MarkdownCopyOptions")
    inner class MarkdownCopyOptionsTests {

        @Test
        @DisplayName("default options have reasonable values")
        fun defaultOptions_haveReasonableValues() {
            val options = MarkdownCopyOptions()

            assertTrue(options.includeFilePath)
            assertFalse(options.includeLineNumbers)
            assertTrue(options.includeLanguage)
            assertFalse(options.wrapInDetails)
            assertNull(options.maxLines)
            assertEquals(1, options.startLineNumber)
        }

        @Test
        @DisplayName("toggleFilePath flips setting")
        fun toggleFilePath_flipsSetting() {
            val options = MarkdownCopyOptions(includeFilePath = true)
            val toggled = options.toggleFilePath()

            assertTrue(options.includeFilePath)
            assertFalse(toggled.includeFilePath)
        }

        @Test
        @DisplayName("toggleLineNumbers flips setting")
        fun toggleLineNumbers_flipsSetting() {
            val options = MarkdownCopyOptions(includeLineNumbers = false)
            val toggled = options.toggleLineNumbers()

            assertFalse(options.includeLineNumbers)
            assertTrue(toggled.includeLineNumbers)
        }

        @Test
        @DisplayName("toggleLanguage flips setting")
        fun toggleLanguage_flipsSetting() {
            val options = MarkdownCopyOptions(includeLanguage = true)
            val toggled = options.toggleLanguage()

            assertTrue(options.includeLanguage)
            assertFalse(toggled.includeLanguage)
        }

        @Test
        @DisplayName("toggleDetails flips setting")
        fun toggleDetails_flipsSetting() {
            val options = MarkdownCopyOptions(wrapInDetails = false)
            val toggled = options.toggleDetails()

            assertFalse(options.wrapInDetails)
            assertTrue(toggled.wrapInDetails)
        }

        @Test
        @DisplayName("withMaxLines sets max lines")
        fun withMaxLines_setsMaxLines() {
            val options = MarkdownCopyOptions().withMaxLines(50)
            assertEquals(50, options.maxLines)
        }

        @Test
        @DisplayName("withMaxLines coerces to at least 1")
        fun withMaxLines_coercesToAtLeast1() {
            val options = MarkdownCopyOptions().withMaxLines(0)
            assertEquals(1, options.maxLines)
        }

        @Test
        @DisplayName("withMaxLines accepts null")
        fun withMaxLines_acceptsNull() {
            val options = MarkdownCopyOptions(maxLines = 10).withMaxLines(null)
            assertNull(options.maxLines)
        }

        @Test
        @DisplayName("withStartLine sets starting line")
        fun withStartLine_setsStartingLine() {
            val options = MarkdownCopyOptions().withStartLine(10)
            assertEquals(10, options.startLineNumber)
        }

        @Test
        @DisplayName("withStartLine coerces to at least 1")
        fun withStartLine_coercesToAtLeast1() {
            val options = MarkdownCopyOptions().withStartLine(-5)
            assertEquals(1, options.startLineNumber)
        }

        @Test
        @DisplayName("MINIMAL preset has correct settings")
        fun minimalPreset_hasCorrectSettings() {
            val options = MarkdownCopyOptions.MINIMAL

            assertFalse(options.includeFilePath)
            assertFalse(options.includeLineNumbers)
            assertTrue(options.includeLanguage)
            assertFalse(options.wrapInDetails)
        }

        @Test
        @DisplayName("FULL preset has correct settings")
        fun fullPreset_hasCorrectSettings() {
            val options = MarkdownCopyOptions.FULL

            assertTrue(options.includeFilePath)
            assertTrue(options.includeLineNumbers)
            assertTrue(options.includeLanguage)
            assertFalse(options.wrapInDetails)
        }

        @Test
        @DisplayName("COLLAPSIBLE preset has correct settings")
        fun collapsiblePreset_hasCorrectSettings() {
            val options = MarkdownCopyOptions.COLLAPSIBLE

            assertTrue(options.includeFilePath)
            assertFalse(options.includeLineNumbers)
            assertTrue(options.includeLanguage)
            assertTrue(options.wrapInDetails)
        }
    }

    // =========================================================================
    // MarkdownCode.format Tests
    // =========================================================================

    @Nested
    @DisplayName("MarkdownCode.format")
    inner class MarkdownCodeFormatTests {

        @Test
        @DisplayName("formats basic code block")
        fun formatsBasicCodeBlock() {
            val result = MarkdownCode.format(
                code = "fun hello() = println(\"Hi\")",
                language = "kt"
            )

            assertTrue(result.markdown.contains("```kotlin"))
            assertTrue(result.markdown.contains("fun hello()"))
            assertTrue(result.markdown.endsWith("```"))
        }

        @Test
        @DisplayName("includes file path when enabled")
        fun includesFilePath_whenEnabled() {
            val result = MarkdownCode.format(
                code = "val x = 1",
                language = "kt",
                filePath = "/project/src/Main.kt",
                options = MarkdownCopyOptions(includeFilePath = true)
            )

            assertTrue(result.markdown.contains("**`Main.kt`**"))
        }

        @Test
        @DisplayName("excludes file path when disabled")
        fun excludesFilePath_whenDisabled() {
            val result = MarkdownCode.format(
                code = "val x = 1",
                language = "kt",
                filePath = "/project/src/Main.kt",
                options = MarkdownCopyOptions(includeFilePath = false)
            )

            assertFalse(result.markdown.contains("Main.kt"))
        }

        @Test
        @DisplayName("includes line numbers when enabled")
        fun includesLineNumbers_whenEnabled() {
            val result = MarkdownCode.format(
                code = "line1\nline2\nline3",
                options = MarkdownCopyOptions(includeLineNumbers = true)
            )

            assertTrue(result.markdown.contains("  1 | line1"))
            assertTrue(result.markdown.contains("  2 | line2"))
            assertTrue(result.markdown.contains("  3 | line3"))
        }

        @Test
        @DisplayName("respects starting line number")
        fun respectsStartingLineNumber() {
            val result = MarkdownCode.format(
                code = "line1\nline2",
                options = MarkdownCopyOptions(
                    includeLineNumbers = true,
                    startLineNumber = 10
                )
            )

            assertTrue(result.markdown.contains("10 | line1"))
            assertTrue(result.markdown.contains("11 | line2"))
        }

        @Test
        @DisplayName("wraps in details when enabled")
        fun wrapsInDetails_whenEnabled() {
            val result = MarkdownCode.format(
                code = "val x = 1",
                language = "kt",
                options = MarkdownCopyOptions(wrapInDetails = true)
            )

            assertTrue(result.markdown.contains("<details>"))
            assertTrue(result.markdown.contains("<summary>"))
            assertTrue(result.markdown.contains("Code"))
            assertTrue(result.markdown.contains("</details>"))
        }

        @Test
        @DisplayName("truncates when maxLines set")
        fun truncates_whenMaxLinesSet() {
            val result = MarkdownCode.format(
                code = "line1\nline2\nline3\nline4\nline5",
                options = MarkdownCopyOptions(maxLines = 2)
            )

            assertTrue(result.wasTruncated)
            assertTrue(result.markdown.contains("line1"))
            assertTrue(result.markdown.contains("line2"))
            assertTrue(result.markdown.contains("// ... 3 more lines"))
            assertFalse(result.markdown.contains("line4"))
        }

        @Test
        @DisplayName("does not truncate when within limit")
        fun doesNotTruncate_whenWithinLimit() {
            val result = MarkdownCode.format(
                code = "line1\nline2",
                options = MarkdownCopyOptions(maxLines = 10)
            )

            assertFalse(result.wasTruncated)
        }

        @Test
        @DisplayName("handles empty code")
        fun handlesEmptyCode() {
            val result = MarkdownCode.format(code = "", language = "kt")

            assertTrue(result.markdown.contains("```kotlin"))
            assertTrue(result.markdown.contains("```"))
            assertEquals(0, result.lineCount)
            assertFalse(result.wasTruncated)
        }

        @Test
        @DisplayName("excludes language when disabled")
        fun excludesLanguage_whenDisabled() {
            val result = MarkdownCode.format(
                code = "val x = 1",
                language = "kt",
                options = MarkdownCopyOptions(includeLanguage = false)
            )

            assertTrue(result.markdown.startsWith("```\n") || 
                       result.markdown.contains("```\nval"))
            assertFalse(result.markdown.contains("kotlin"))
        }
    }

    // =========================================================================
    // Language Mapping Tests
    // =========================================================================

    @Nested
    @DisplayName("Language Mapping")
    inner class LanguageMappingTests {

        @Test
        @DisplayName("maps Kotlin extensions")
        fun mapsKotlinExtensions() {
            val kt = MarkdownCode.format("x", "kt")
            val kts = MarkdownCode.format("x", "kts")

            assertTrue(kt.markdown.contains("kotlin"))
            assertTrue(kts.markdown.contains("kotlin"))
        }

        @Test
        @DisplayName("maps common languages")
        fun mapsCommonLanguages() {
            assertEquals("java", getLanguageFromMarkdown("java"))
            assertEquals("python", getLanguageFromMarkdown("py"))
            assertEquals("javascript", getLanguageFromMarkdown("js"))
            assertEquals("typescript", getLanguageFromMarkdown("ts"))
            assertEquals("csharp", getLanguageFromMarkdown("cs"))
        }

        @Test
        @DisplayName("maps shell scripts")
        fun mapsShellScripts() {
            assertEquals("bash", getLanguageFromMarkdown("sh"))
            assertEquals("bash", getLanguageFromMarkdown("bash"))
            assertEquals("bash", getLanguageFromMarkdown("zsh"))
        }

        @Test
        @DisplayName("returns lowercase for unknown extensions")
        fun returnsLowercaseForUnknown() {
            assertEquals("xyz", getLanguageFromMarkdown("xyz"))
            assertEquals("custom", getLanguageFromMarkdown("CUSTOM"))
        }

        private fun getLanguageFromMarkdown(ext: String): String {
            val result = MarkdownCode.format("x", ext)
            return result.markdown
                .substringAfter("```")
                .substringBefore("\n")
        }
    }

    // =========================================================================
    // MarkdownCode Properties Tests
    // =========================================================================

    @Nested
    @DisplayName("MarkdownCode Properties")
    inner class MarkdownCodePropertiesTests {

        @Test
        @DisplayName("lineCount reflects original code")
        fun lineCount_reflectsOriginalCode() {
            val result = MarkdownCode.format("a\nb\nc\nd\ne")
            assertEquals(5, result.lineCount)
        }

        @Test
        @DisplayName("markdownLength reflects output")
        fun markdownLength_reflectsOutput() {
            val result = MarkdownCode.format("test")
            assertTrue(result.markdownLength > 4)
        }

        @Test
        @DisplayName("summary includes line count and language")
        fun summary_includesMetadata() {
            val result = MarkdownCode.format("a\nb\nc", "kt")

            assertTrue(result.summary.contains("3 lines"))
            assertTrue(result.summary.contains("kt"))
        }

        @Test
        @DisplayName("summary indicates truncation")
        fun summary_indicatesTruncation() {
            val result = MarkdownCode.format(
                code = "a\nb\nc\nd\ne",
                options = MarkdownCopyOptions(maxLines = 2)
            )

            assertTrue(result.summary.contains("[truncated]"))
        }

        @Test
        @DisplayName("isEmpty detects empty output")
        fun isEmpty_detectsEmptyOutput() {
            val empty = MarkdownCode("", 0, null)
            val nonEmpty = MarkdownCode.format("test")

            assertTrue(empty.isEmpty)
            assertFalse(nonEmpty.isEmpty)
        }
    }

    // =========================================================================
    // MarkdownCopyResult Tests
    // =========================================================================

    @Nested
    @DisplayName("MarkdownCopyResult")
    inner class MarkdownCopyResultTests {

        @Test
        @DisplayName("Success has isSuccess true")
        fun success_hasIsSuccessTrue() {
            val result = MarkdownCopyResult.Success(MarkdownCode.format("x"))
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("NoSelection has isSuccess false")
        fun noSelection_hasIsSuccessFalse() {
            val result = MarkdownCopyResult.NoSelection()
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("Error has isSuccess false")
        fun error_hasIsSuccessFalse() {
            val result = MarkdownCopyResult.Error("fail")
            assertFalse(result.isSuccess)
        }

        @Test
        @DisplayName("pattern matching works correctly")
        fun patternMatching_worksCorrectly() {
            val results = listOf(
                MarkdownCopyResult.Success(MarkdownCode.format("x")),
                MarkdownCopyResult.NoSelection(),
                MarkdownCopyResult.Error("fail")
            )

            val types = results.map { result ->
                when (result) {
                    is MarkdownCopyResult.Success -> "success"
                    is MarkdownCopyResult.NoSelection -> "no selection"
                    is MarkdownCopyResult.Error -> "error"
                }
            }

            assertEquals("success", types[0])
            assertEquals("no selection", types[1])
            assertEquals("error", types[2])
        }
    }

    // =========================================================================
    // MarkdownPreset Tests
    // =========================================================================

    @Nested
    @DisplayName("MarkdownPreset")
    inner class MarkdownPresetTests {

        @ParameterizedTest
        @DisplayName("all presets have display names")
        @EnumSource(MarkdownPreset::class)
        fun allPresets_haveDisplayNames(preset: MarkdownPreset) {
            assertNotNull(preset.displayName)
            assertTrue(preset.displayName.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("all presets have options")
        @EnumSource(MarkdownPreset::class)
        fun allPresets_haveOptions(preset: MarkdownPreset) {
            assertNotNull(preset.options)
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Quick", MarkdownPreset.QUICK.toString())
            assertEquals("Standard", MarkdownPreset.STANDARD.toString())
        }
    }

    // =========================================================================
    // MarkdownFormat Tests
    // =========================================================================

    @Nested
    @DisplayName("MarkdownFormat")
    inner class MarkdownFormatTests {

        @ParameterizedTest
        @DisplayName("all formats have display names")
        @EnumSource(MarkdownFormat::class)
        fun allFormats_haveDisplayNames(format: MarkdownFormat) {
            assertNotNull(format.displayName)
            assertTrue(format.displayName.isNotBlank())
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("handles code with special markdown characters")
        fun handlesSpecialMarkdownCharacters() {
            val code = "val regex = \"```\""
            val result = MarkdownCode.format(code)

            assertTrue(result.markdown.contains(code))
        }

        @Test
        @DisplayName("handles very long lines")
        fun handlesVeryLongLines() {
            val longLine = "x".repeat(10000)
            val result = MarkdownCode.format(longLine)

            assertTrue(result.markdown.contains(longLine))
        }

        @Test
        @DisplayName("handles unicode content")
        fun handlesUnicodeContent() {
            val unicode = "日本語 🚀 αβγδ"
            val result = MarkdownCode.format(unicode)

            assertTrue(result.markdown.contains(unicode))
        }

        @Test
        @DisplayName("handles null language")
        fun handlesNullLanguage() {
            val result = MarkdownCode.format("code", null)

            assertNull(result.language)
            assertTrue(result.markdown.startsWith("```\n") || 
                       result.markdown.contains("```\ncode"))
        }

        @Test
        @DisplayName("handles single line without newline")
        fun handlesSingleLineWithoutNewline() {
            val result = MarkdownCode.format("single line")

            assertEquals(1, result.lineCount)
        }

        @ParameterizedTest
        @DisplayName("line numbers are padded correctly")
        @ValueSource(ints = [1, 10, 100, 1000])
        fun lineNumbers_arePaddedCorrectly(startLine: Int) {
            val result = MarkdownCode.format(
                code = "line",
                options = MarkdownCopyOptions(
                    includeLineNumbers = true,
                    startLineNumber = startLine
                )
            )

            assertTrue(result.markdown.contains("$startLine |"))
        }
    }
}
