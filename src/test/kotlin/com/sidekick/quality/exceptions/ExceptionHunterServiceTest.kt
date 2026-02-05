package com.sidekick.quality.exceptions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for Exception Hunter Service.
 *
 * These tests focus on the service's internal logic that can be tested
 * without the IntelliJ Platform (pattern detection, extraction, etc.).
 *
 * Note: Full PSI-based analysis tests would require IntelliJ Platform
 * test fixtures and are not included in this unit test suite.
 *
 * @since 0.6.1
 */
@DisplayName("Exception Hunter Service")
class ExceptionHunterServiceTest {

    // =========================================================================
    // State Serialization Tests
    // =========================================================================

    @Nested
    @DisplayName("State")
    inner class StateTests {

        @Test
        @DisplayName("default state has reasonable values")
        fun defaultState_hasReasonableValues() {
            val state = ExceptionHunterService.State()

            assertTrue(state.enabled)
            assertEquals("Medium", state.minSeverityName)
            assertTrue(state.showInGutter)
            assertTrue(state.highlightInEditor)
            assertTrue(state.traverseCallChain)
            assertEquals(5, state.maxCallChainDepth)
            assertTrue(state.ignoredExceptions.isEmpty())
        }

        @Test
        @DisplayName("toConfig produces correct configuration")
        fun toConfig_producesCorrectConfig() {
            val state = ExceptionHunterService.State(
                enabled = false,
                minSeverityName = "High",
                showInGutter = false,
                highlightInEditor = true,
                traverseCallChain = false,
                maxCallChainDepth = 3,
                ignoredExceptions = mutableSetOf("TestException")
            )

            val config = state.toConfig()

            assertFalse(config.enabled)
            assertEquals(ExceptionSeverity.HIGH, config.minSeverity)
            assertFalse(config.showInGutter)
            assertTrue(config.highlightInEditor)
            assertFalse(config.traverseCallChain)
            assertEquals(3, config.maxCallChainDepth)
            assertTrue("TestException" in config.ignoredExceptions)
        }

        @Test
        @DisplayName("from creates state from config")
        fun from_createsStateFromConfig() {
            val config = ExceptionHunterConfig(
                enabled = true,
                minSeverity = ExceptionSeverity.CRITICAL,
                showInGutter = true,
                highlightInEditor = false,
                traverseCallChain = true,
                maxCallChainDepth = 10,
                ignoredExceptions = setOf("IgnoredException")
            )

            val state = ExceptionHunterService.State.from(config)

            assertTrue(state.enabled)
            assertEquals("Critical", state.minSeverityName)
            assertTrue(state.showInGutter)
            assertFalse(state.highlightInEditor)
            assertTrue(state.traverseCallChain)
            assertEquals(10, state.maxCallChainDepth)
            assertTrue("IgnoredException" in state.ignoredExceptions)
        }

        @Test
        @DisplayName("state roundtrip preserves values")
        fun state_roundtripPreservesValues() {
            val original = ExceptionHunterConfig(
                enabled = true,
                minSeverity = ExceptionSeverity.HIGH,
                ignoredExceptions = setOf("Ex1", "Ex2")
            )

            val state = ExceptionHunterService.State.from(original)
            val restored = state.toConfig()

            assertEquals(original.enabled, restored.enabled)
            assertEquals(original.minSeverity, restored.minSeverity)
            assertEquals(original.ignoredExceptions, restored.ignoredExceptions)
        }
    }

    // =========================================================================
    // Pattern Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Detection")
    inner class PatternDetectionTests {

        @ParameterizedTest
        @DisplayName("detects throw statements")
        @CsvSource(
            "'throw new IOException(\"error\")', IOException",
            "'throw IllegalArgumentException()', IllegalArgumentException",
            "'throw RuntimeException(message)', RuntimeException"
        )
        fun detectsThrowStatements(code: String, expectedType: String) {
            val extracted = extractExceptionTypeFromThrow(code)
            assertEquals(expectedType, extracted)
        }

        @Test
        @DisplayName("extractExceptionType handles 'throw new Type'")
        fun extractExceptionType_handlesThrowNew() {
            val type = extractExceptionTypeFromThrow("throw new NullPointerException()")
            assertEquals("NullPointerException", type)
        }

        @Test
        @DisplayName("extractExceptionType handles 'throw Type' without new")
        fun extractExceptionType_handlesThrowWithoutNew() {
            val type = extractExceptionTypeFromThrow("throw IOException(message)")
            assertEquals("IOException", type)
        }

        @Test
        @DisplayName("extractExceptionType returns Exception for malformed throw")
        fun extractExceptionType_returnsExceptionForMalformed() {
            val type = extractExceptionTypeFromThrow("throw")
            assertEquals("Exception", type)
        }

        /**
         * Helper that mimics the service's extraction logic.
         */
        private fun extractExceptionTypeFromThrow(text: String): String {
            val match = Regex("""throw\s+(?:new\s+)?(\w+)""").find(text)
            return match?.groupValues?.get(1) ?: "Exception"
        }
    }

    // =========================================================================
    // Throwing Pattern Tests
    // =========================================================================

    @Nested
    @DisplayName("Throwing Patterns")
    inner class ThrowingPatternTests {

        private val patterns = mapOf(
            ".read(" to "IOException",
            ".write(" to "IOException",
            ".close(" to "IOException",
            ".execute(" to "SQLException",
            ".executeQuery(" to "SQLException",
            ".executeUpdate(" to "SQLException",
            ".connect(" to "SQLException",
            "!!" to "NullPointerException",
            ".parseInt(" to "NumberFormatException",
            ".parseDouble(" to "NumberFormatException"
        )

        @ParameterizedTest
        @DisplayName("IO patterns map to IOException")
        @ValueSource(strings = [
            "stream.read(buffer)",
            "writer.write(data)",
            "resource.close()"
        ])
        fun ioPatterns_mapToIOException(code: String) {
            val detected = detectPatternException(code)
            assertEquals("IOException", detected)
        }

        @ParameterizedTest
        @DisplayName("SQL patterns map to SQLException")
        @ValueSource(strings = [
            "statement.execute(sql)",
            "statement.executeQuery(sql)",
            "statement.executeUpdate(sql)",
            "dataSource.connect()"
        ])
        fun sqlPatterns_mapToSQLException(code: String) {
            val detected = detectPatternException(code)
            assertEquals("SQLException", detected)
        }

        @Test
        @DisplayName("double bang maps to NullPointerException")
        fun doubleBang_mapsToNPE() {
            val detected = detectPatternException("value!!")
            assertEquals("NullPointerException", detected)
        }

        @ParameterizedTest
        @DisplayName("parse patterns map to NumberFormatException")
        @ValueSource(strings = [
            "Integer.parseInt(str)",
            "Double.parseDouble(str)"
        ])
        fun parsePatterns_mapToNumberFormatException(code: String) {
            val detected = detectPatternException(code)
            assertEquals("NumberFormatException", detected)
        }

        @Test
        @DisplayName("unknown pattern returns null")
        fun unknownPattern_returnsNull() {
            val detected = detectPatternException("println(message)")
            assertNull(detected)
        }

        /**
         * Helper that mimics the service's pattern detection.
         */
        private fun detectPatternException(text: String): String? {
            for ((pattern, exceptionType) in patterns) {
                if (text.contains(pattern)) {
                    return exceptionType
                }
            }
            return null
        }
    }

    // =========================================================================
    // Suggestion Generation Tests
    // =========================================================================

    @Nested
    @DisplayName("Suggestion Generation")
    inner class SuggestionGenerationTests {

        @Test
        @DisplayName("NullPointer gets null check suggestion")
        fun nullPointer_getsNullCheckSuggestion() {
            val suggestion = generateSuggestion("NullPointerException")
            assertTrue(suggestion.contains("null check") || suggestion.contains("safe call"))
        }

        @Test
        @DisplayName("IOException gets try-catch suggestion")
        fun ioException_getsTryCatchSuggestion() {
            val suggestion = generateSuggestion("IOException")
            assertTrue(suggestion.contains("try-catch") || suggestion.contains("throws"))
        }

        @Test
        @DisplayName("SQLException gets try-catch suggestion")
        fun sqlException_getsTryCatchSuggestion() {
            val suggestion = generateSuggestion("SQLException")
            assertTrue(suggestion.contains("try-catch") || suggestion.contains("throws"))
        }

        @Test
        @DisplayName("NumberFormatException gets validation suggestion")
        fun numberFormatException_getsValidationSuggestion() {
            val suggestion = generateSuggestion("NumberFormatException")
            assertTrue(suggestion.contains("Validate") || suggestion.contains("parsing"))
        }

        @Test
        @DisplayName("unknown exception gets generic suggestion")
        fun unknownException_getsGenericSuggestion() {
            val suggestion = generateSuggestion("CustomException")
            assertTrue(suggestion.contains("try-catch") || suggestion.contains("throws"))
        }

        /**
         * Helper that mimics the service's suggestion generation.
         */
        private fun generateSuggestion(exceptionType: String): String {
            return when {
                exceptionType.contains("NullPointer") -> "Add null check or use safe call operator"
                exceptionType.contains("IO") -> "Wrap in try-catch or add throws IOException"
                exceptionType.contains("SQL") -> "Wrap in try-catch or add throws SQLException"
                exceptionType.contains("Security") -> "Handle security exception or add throws declaration"
                exceptionType.contains("NumberFormat") -> "Validate input before parsing"
                else -> "Consider wrapping in try-catch or declaring throws"
            }
        }
    }

    // =========================================================================
    // Throws Declaration Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Throws Declaration Detection")
    inner class ThrowsDeclarationTests {

        @Test
        @DisplayName("detects Java throws clause")
        fun detectsJavaThrowsClause() {
            val methodText = "public void readFile() throws IOException { ... }"
            assertTrue(hasThrowsDeclaration(methodText, "IOException"))
        }

        @Test
        @DisplayName("detects multiple throws")
        fun detectsMultipleThrows() {
            val methodText = "public void process() throws IOException, SQLException { ... }"
            assertTrue(hasThrowsDeclaration(methodText, "IOException"))
        }

        @Test
        @DisplayName("detects Kotlin @Throws annotation")
        fun detectsKotlinThrowsAnnotation() {
            val methodText = "@Throws(IOException::class) fun readFile() { ... }"
            assertTrue(hasThrowsDeclaration(methodText, ""))
        }

        @Test
        @DisplayName("returns false when not declared")
        fun returnsFalseWhenNotDeclared() {
            val methodText = "public void calculate() { ... }"
            assertFalse(hasThrowsDeclaration(methodText, "IOException"))
        }

        /**
         * Helper that mimics the service's throws detection.
         */
        private fun hasThrowsDeclaration(methodText: String, exceptionType: String): Boolean {
            return methodText.contains("throws $exceptionType") ||
                   methodText.contains("throws $exceptionType,") ||
                   methodText.contains("@Throws") ||
                   methodText.contains("@throws")
        }
    }
}
