package com.sidekick.quality.deadcode

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for Dead Code Service.
 *
 * These tests focus on the service's internal logic that can be tested
 * without the IntelliJ Platform (state serialization, queries, etc.).
 *
 * Note: Full PSI-based analysis tests would require IntelliJ Platform
 * test fixtures and are not included in this unit test suite.
 *
 * @since 0.6.4
 */
@DisplayName("Dead Code Service")
class DeadCodeServiceTest {

    // =========================================================================
    // State Serialization Tests
    // =========================================================================

    @Nested
    @DisplayName("State")
    inner class StateTests {

        @Test
        @DisplayName("default state has reasonable values")
        fun defaultState_hasReasonableValues() {
            val state = DeadCodeService.State()

            assertTrue(state.enabled)
            assertTrue(state.includePrivate)
            assertTrue(state.excludePublicApi)
            assertEquals(0.8f, state.minConfidence)
            assertTrue(state.knownDeadCode.isEmpty())
        }

        @Test
        @DisplayName("toConfig produces correct configuration")
        fun toConfig_producesCorrectConfig() {
            val state = DeadCodeService.State(
                enabled = false,
                includePrivate = false,
                includeInternal = true,
                excludePublicApi = false,
                minConfidence = 0.5f,
                excludePatterns = mutableListOf("*Test*", "*Mock*")
            )

            val config = state.toConfig()

            assertFalse(config.enabled)
            assertFalse(config.includePrivate)
            assertTrue(config.includeInternal)
            assertFalse(config.excludePublicApi)
            assertEquals(0.5f, config.minConfidence)
            assertTrue("*Test*" in config.excludePatterns)
        }

        @Test
        @DisplayName("from creates state from config")
        fun from_createsStateFromConfig() {
            val config = DeadCodeConfig(
                enabled = true,
                includePrivate = false,
                excludePublicApi = true,
                minConfidence = 0.9f
            )

            val state = DeadCodeService.State.from(config)

            assertTrue(state.enabled)
            assertFalse(state.includePrivate)
            assertTrue(state.excludePublicApi)
            assertEquals(0.9f, state.minConfidence)
        }

        @Test
        @DisplayName("state roundtrip preserves values")
        fun state_roundtripPreservesValues() {
            val original = DeadCodeConfig(
                enabled = true,
                includePrivate = false,
                excludePublicApi = true,
                minConfidence = 0.75f
            )

            val state = DeadCodeService.State.from(original)
            val restored = state.toConfig()

            assertEquals(original.enabled, restored.enabled)
            assertEquals(original.includePrivate, restored.includePrivate)
            assertEquals(original.excludePublicApi, restored.excludePublicApi)
            assertEquals(original.minConfidence, restored.minConfidence)
        }
    }

    // =========================================================================
    // SerializedSymbol Tests
    // =========================================================================

    @Nested
    @DisplayName("SerializedSymbol")
    inner class SerializedSymbolTests {

        @Test
        @DisplayName("toDeadCodeSymbol converts correctly")
        fun toDeadCodeSymbol_convertsCorrectly() {
            val serialized = DeadCodeService.SerializedSymbol(
                name = "unusedMethod",
                qualifiedName = "com.example.MyClass.unusedMethod",
                typeName = "METHOD",
                filePath = "/path/to/file.kt",
                line = 42,
                className = "MyClass",
                memberName = "unusedMethod",
                usageCount = 0,
                confidence = 0.95f,
                canSafeDelete = true,
                visibilityName = "PRIVATE",
                codeSize = 10
            )

            val symbol = serialized.toDeadCodeSymbol()

            assertEquals("unusedMethod", symbol.name)
            assertEquals("com.example.MyClass.unusedMethod", symbol.qualifiedName)
            assertEquals(SymbolType.METHOD, symbol.type)
            assertEquals("/path/to/file.kt", symbol.location.filePath)
            assertEquals(42, symbol.location.line)
            assertEquals("MyClass", symbol.location.className)
            assertEquals(0.95f, symbol.confidence)
            assertTrue(symbol.canSafeDelete)
            assertEquals(SymbolVisibility.PRIVATE, symbol.visibility)
        }

        @Test
        @DisplayName("from creates serialized symbol correctly")
        fun from_createsSerializedSymbol() {
            val symbol = DeadCodeSymbol(
                name = "testField",
                qualifiedName = "com.example.TestClass.testField",
                type = SymbolType.FIELD,
                location = SymbolLocation("/src/Test.kt", 100, "TestClass", "testField"),
                usageCount = 0,
                lastUsedDate = null,
                confidence = 0.85f,
                canSafeDelete = false,
                visibility = SymbolVisibility.INTERNAL,
                codeSize = 3
            )

            val serialized = DeadCodeService.SerializedSymbol.from(symbol)

            assertEquals("testField", serialized.name)
            assertEquals("FIELD", serialized.typeName)
            assertEquals("INTERNAL", serialized.visibilityName)
            assertEquals(0.85f, serialized.confidence)
            assertFalse(serialized.canSafeDelete)
        }

        @Test
        @DisplayName("roundtrip preserves values")
        fun roundtrip_preservesValues() {
            val original = DeadCodeSymbol(
                name = "myMethod",
                qualifiedName = "com.example.myMethod",
                type = SymbolType.METHOD,
                location = SymbolLocation("/test.kt", 50, "MyClass", "myMethod"),
                usageCount = 0,
                lastUsedDate = null,
                confidence = 0.99f,
                canSafeDelete = true,
                visibility = SymbolVisibility.PRIVATE,
                codeSize = 15
            )

            val serialized = DeadCodeService.SerializedSymbol.from(original)
            val restored = serialized.toDeadCodeSymbol()

            assertEquals(original.name, restored.name)
            assertEquals(original.qualifiedName, restored.qualifiedName)
            assertEquals(original.type, restored.type)
            assertEquals(original.confidence, restored.confidence)
            assertEquals(original.canSafeDelete, restored.canSafeDelete)
            assertEquals(original.visibility, restored.visibility)
        }
    }

    // =========================================================================
    // Pattern Matching Tests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Matching")
    inner class PatternMatchingTests {

        @ParameterizedTest
        @DisplayName("excludePatterns match correctly")
        @CsvSource(
            "'*Test*', MyTestClass, true",
            "'*Test*', TestHelper, true",
            "'*Test*', RealService, false",
            "'*Mock*', MockRepository, true",
            "'*Fake*', FakeDatabase, true"
        )
        fun excludePatterns_matchCorrectly(pattern: String, name: String, expected: Boolean) {
            val config = DeadCodeConfig(excludePatterns = listOf(pattern))
            assertEquals(expected, config.shouldExclude(name))
        }

        @Test
        @DisplayName("patterns are case-insensitive")
        fun patterns_areCaseInsensitive() {
            val config = DeadCodeConfig(excludePatterns = listOf("*Test*"))

            assertTrue(config.shouldExclude("MyTEST"))
            assertTrue(config.shouldExclude("mytest"))
            assertTrue(config.shouldExclude("MYTEST"))
        }
    }

    // =========================================================================
    // Visibility Filtering Tests
    // =========================================================================

    @Nested
    @DisplayName("Visibility Filtering")
    inner class VisibilityFilteringTests {

        @Test
        @DisplayName("private symbols included by default")
        fun privateSymbols_includedByDefault() {
            val config = DeadCodeConfig()
            val symbol = createSymbol(visibility = SymbolVisibility.PRIVATE)

            assertTrue(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("private symbols excluded when configured")
        fun privateSymbols_excludedWhenConfigured() {
            val config = DeadCodeConfig(includePrivate = false)
            val symbol = createSymbol(visibility = SymbolVisibility.PRIVATE)

            assertFalse(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("public API excluded by default")
        fun publicApi_excludedByDefault() {
            val config = DeadCodeConfig()
            val symbol = createSymbol(visibility = SymbolVisibility.PUBLIC)

            assertFalse(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("public API included when configured")
        fun publicApi_includedWhenConfigured() {
            val config = DeadCodeConfig(excludePublicApi = false)
            val symbol = createSymbol(visibility = SymbolVisibility.PUBLIC)

            assertTrue(config.shouldAnalyze(symbol))
        }

        private fun createSymbol(visibility: SymbolVisibility) = DeadCodeSymbol(
            name = "myMethod",
            qualifiedName = "com.example.myMethod",
            type = SymbolType.METHOD,
            location = SymbolLocation.empty(),
            usageCount = 0,
            lastUsedDate = null,
            confidence = 1.0f,
            canSafeDelete = true,
            visibility = visibility
        )
    }

    // =========================================================================
    // Confidence Filtering Tests
    // =========================================================================

    @Nested
    @DisplayName("Confidence Filtering")
    inner class ConfidenceFilteringTests {

        @Test
        @DisplayName("low confidence symbols filtered out")
        fun lowConfidence_filteredOut() {
            val config = DeadCodeConfig(minConfidence = 0.8f)
            val symbol = createSymbol(confidence = 0.5f)

            assertFalse(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("high confidence symbols included")
        fun highConfidence_included() {
            val config = DeadCodeConfig(minConfidence = 0.8f)
            val symbol = createSymbol(confidence = 0.9f)

            assertTrue(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("exact threshold is included")
        fun exactThreshold_isIncluded() {
            val config = DeadCodeConfig(minConfidence = 0.8f)
            val symbol = createSymbol(confidence = 0.8f)

            assertTrue(config.shouldAnalyze(symbol))
        }

        private fun createSymbol(confidence: Float) = DeadCodeSymbol(
            name = "myMethod",
            qualifiedName = "com.example.myMethod",
            type = SymbolType.METHOD,
            location = SymbolLocation.empty(),
            usageCount = 0,
            lastUsedDate = null,
            confidence = confidence,
            canSafeDelete = true,
            visibility = SymbolVisibility.PRIVATE
        )
    }

    // =========================================================================
    // Safe Delete Logic Tests
    // =========================================================================

    @Nested
    @DisplayName("Safe Delete Logic")
    inner class SafeDeleteLogicTests {

        @Test
        @DisplayName("canSafeDelete determines deletability")
        fun canSafeDelete_determinesDeletability() {
            val safeSymbol = createSymbol(canSafeDelete = true)
            val unsafeSymbol = createSymbol(canSafeDelete = false)

            assertTrue(safeSymbol.canSafeDelete)
            assertFalse(unsafeSymbol.canSafeDelete)
        }

        @Test
        @DisplayName("constructor type is not deletable by default")
        fun constructorType_notDeletableByDefault() {
            assertFalse(SymbolType.CONSTRUCTOR.deletable)
        }

        @Test
        @DisplayName("method type is deletable by default")
        fun methodType_deletableByDefault() {
            assertTrue(SymbolType.METHOD.deletable)
        }

        private fun createSymbol(canSafeDelete: Boolean) = DeadCodeSymbol(
            name = "test",
            qualifiedName = "com.example.test",
            type = SymbolType.METHOD,
            location = SymbolLocation.empty(),
            usageCount = 0,
            lastUsedDate = null,
            confidence = 1.0f,
            canSafeDelete = canSafeDelete
        )
    }

    // =========================================================================
    // Summary Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Summary Statistics")
    inner class SummaryStatisticsTests {

        @Test
        @DisplayName("byType groups symbols correctly")
        fun byType_groupsSymbolsCorrectly() {
            val symbols = listOf(
                createSymbol("m1", SymbolType.METHOD),
                createSymbol("m2", SymbolType.METHOD),
                createSymbol("f1", SymbolType.FIELD)
            )

            val summary = DeadCodeSummary.from(symbols)

            assertEquals(2, summary.byType[SymbolType.METHOD])
            assertEquals(1, summary.byType[SymbolType.FIELD])
        }

        @Test
        @DisplayName("byRisk groups symbols correctly")
        fun byRisk_groupsSymbolsCorrectly() {
            val symbols = listOf(
                createSymbol("private", visibility = SymbolVisibility.PRIVATE),
                createSymbol("public", visibility = SymbolVisibility.PUBLIC)
            )

            val summary = DeadCodeSummary.from(symbols)

            assertEquals(1, summary.byRisk[DeletionRisk.LOW])
            assertEquals(1, summary.byRisk[DeletionRisk.HIGH])
        }

        @Test
        @DisplayName("estimatedLines sums code sizes")
        fun estimatedLines_sumsCodeSizes() {
            val symbols = listOf(
                createSymbol("s1", codeSize = 10),
                createSymbol("s2", codeSize = 20)
            )

            val summary = DeadCodeSummary.from(symbols)

            assertEquals(30, summary.estimatedLines)
        }

        private fun createSymbol(
            name: String,
            type: SymbolType = SymbolType.METHOD,
            visibility: SymbolVisibility = SymbolVisibility.PRIVATE,
            codeSize: Int = 5
        ) = DeadCodeSymbol(
            name = name,
            qualifiedName = "com.example.$name",
            type = type,
            location = SymbolLocation.empty(),
            usageCount = 0,
            lastUsedDate = null,
            confidence = 1.0f,
            canSafeDelete = true,
            visibility = visibility,
            codeSize = codeSize
        )
    }
}
