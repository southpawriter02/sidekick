package com.sidekick.quality.deadcode

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Comprehensive unit tests for Dead Code Cemetery Models.
 *
 * Tests cover:
 * - DeadCodeSymbol properties
 * - SymbolLocation formatting
 * - SymbolType classification
 * - SymbolVisibility and DeletionRisk
 * - DeadCodeConfig behavior
 * - DeadCodeAnalysisResult aggregation
 * - DeadCodeSummary computation
 *
 * @since 0.6.4
 */
@DisplayName("Dead Code Cemetery Models")
class DeadCodeModelsTest {

    // =========================================================================
    // DeadCodeSymbol Tests
    // =========================================================================

    @Nested
    @DisplayName("DeadCodeSymbol")
    inner class DeadCodeSymbolTests {

        @Test
        @DisplayName("isUnused returns true when usageCount is 0")
        fun isUnused_returnsTrueWhenZeroUsages() {
            val symbol = DeadCodeSymbol.simple("test", usageCount = 0)
            assertTrue(symbol.isUnused)
        }

        @Test
        @DisplayName("isUnused returns false when usageCount > 0")
        fun isUnused_returnsFalseWhenHasUsages() {
            val symbol = createSymbol(usageCount = 1)
            assertFalse(symbol.isUnused)
        }

        @Test
        @DisplayName("isHighConfidence returns true for 0.8+")
        fun isHighConfidence_returnsTrueFor80Percent() {
            val symbol = DeadCodeSymbol.simple("test", confidence = 0.8f)
            assertTrue(symbol.isHighConfidence)
        }

        @Test
        @DisplayName("isHighConfidence returns false for < 0.8")
        fun isHighConfidence_returnsFalseForLow() {
            val symbol = DeadCodeSymbol.simple("test", confidence = 0.7f)
            assertFalse(symbol.isHighConfidence)
        }

        @Test
        @DisplayName("confidencePercent formats correctly")
        fun confidencePercent_formatsCorrectly() {
            val symbol = DeadCodeSymbol.simple("test", confidence = 0.95f)
            assertEquals("95%", symbol.confidencePercent)
        }

        @Test
        @DisplayName("displayString includes type and name")
        fun displayString_includesTypeAndName() {
            val symbol = DeadCodeSymbol.simple("myMethod", type = SymbolType.METHOD)
            assertTrue(symbol.displayString.contains("myMethod"))
            assertTrue(symbol.displayString.contains("Method"))
        }

        private fun createSymbol(usageCount: Int = 0) = DeadCodeSymbol(
            name = "test",
            qualifiedName = "com.example.test",
            type = SymbolType.METHOD,
            location = SymbolLocation.empty(),
            usageCount = usageCount,
            lastUsedDate = null,
            confidence = 1.0f,
            canSafeDelete = true
        )
    }

    // =========================================================================
    // SymbolLocation Tests
    // =========================================================================

    @Nested
    @DisplayName("SymbolLocation")
    inner class SymbolLocationTests {

        @Test
        @DisplayName("fileName extracts file name from path")
        fun fileName_extractsFromPath() {
            val location = SymbolLocation(
                filePath = "/Users/test/project/src/main/Service.kt",
                line = 42,
                className = "Service",
                memberName = "method"
            )

            assertEquals("Service.kt", location.fileName)
        }

        @Test
        @DisplayName("displayString formats as filename:line")
        fun displayString_formatsCorrectly() {
            val location = SymbolLocation("/path/to/Handler.kt", 100, null, null)

            assertEquals("Handler.kt:100", location.displayString)
        }

        @Test
        @DisplayName("qualifiedString includes class and member")
        fun qualifiedString_includesClassAndMember() {
            val location = SymbolLocation(
                "/path/to/Service.kt", 50, "MyService", "handleRequest"
            )

            val qualified = location.qualifiedString
            assertTrue(qualified.contains("Service.kt"))
            assertTrue(qualified.contains("MyService"))
            assertTrue(qualified.contains("handleRequest"))
        }

        @Test
        @DisplayName("empty creates default location")
        fun empty_createsDefaultLocation() {
            val location = SymbolLocation.empty()

            assertEquals("", location.filePath)
            assertEquals(0, location.line)
        }
    }

    // =========================================================================
    // SymbolType Tests
    // =========================================================================

    @Nested
    @DisplayName("SymbolType")
    inner class SymbolTypeTests {

        @ParameterizedTest
        @DisplayName("all types have display names")
        @EnumSource(SymbolType::class)
        fun allTypes_haveDisplayNames(type: SymbolType) {
            assertTrue(type.displayName.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("all types have icons")
        @EnumSource(SymbolType::class)
        fun allTypes_haveIcons(type: SymbolType) {
            assertTrue(type.icon.isNotBlank())
        }

        @Test
        @DisplayName("DECLARATIONS contains class-level types")
        fun declarations_containsClassLevelTypes() {
            assertTrue(SymbolType.CLASS in SymbolType.DECLARATIONS)
            assertTrue(SymbolType.INTERFACE in SymbolType.DECLARATIONS)
            assertTrue(SymbolType.ENUM in SymbolType.DECLARATIONS)
        }

        @Test
        @DisplayName("MEMBERS contains member-level types")
        fun members_containsMemberLevelTypes() {
            assertTrue(SymbolType.METHOD in SymbolType.MEMBERS)
            assertTrue(SymbolType.FIELD in SymbolType.MEMBERS)
            assertTrue(SymbolType.PROPERTY in SymbolType.MEMBERS)
        }

        @Test
        @DisplayName("SAFE_DELETABLE excludes constructor")
        fun safeDeletable_excludesConstructor() {
            assertFalse(SymbolType.CONSTRUCTOR in SymbolType.SAFE_DELETABLE)
            assertTrue(SymbolType.METHOD in SymbolType.SAFE_DELETABLE)
        }

        @Test
        @DisplayName("byName finds type case-insensitively")
        fun byName_findsTypeIgnoringCase() {
            assertEquals(SymbolType.METHOD, SymbolType.byName("method"))
            assertEquals(SymbolType.CLASS, SymbolType.byName("CLASS"))
        }
    }

    // =========================================================================
    // SymbolVisibility Tests
    // =========================================================================

    @Nested
    @DisplayName("SymbolVisibility")
    inner class SymbolVisibilityTests {

        @Test
        @DisplayName("PRIVATE has LOW deletion risk")
        fun private_hasLowRisk() {
            assertEquals(DeletionRisk.LOW, SymbolVisibility.PRIVATE.deletionRisk)
        }

        @Test
        @DisplayName("PUBLIC has HIGH deletion risk")
        fun public_hasHighRisk() {
            assertEquals(DeletionRisk.HIGH, SymbolVisibility.PUBLIC.deletionRisk)
        }

        @Test
        @DisplayName("byName finds visibility case-insensitively")
        fun byName_findsVisibilityIgnoringCase() {
            assertEquals(SymbolVisibility.PRIVATE, SymbolVisibility.byName("private"))
            assertEquals(SymbolVisibility.PUBLIC, SymbolVisibility.byName("PUBLIC"))
        }

        @Test
        @DisplayName("byName returns PRIVATE for unknown")
        fun byName_returnsPrivateForUnknown() {
            assertEquals(SymbolVisibility.PRIVATE, SymbolVisibility.byName("unknown"))
        }
    }

    // =========================================================================
    // DeletionRisk Tests
    // =========================================================================

    @Nested
    @DisplayName("DeletionRisk")
    inner class DeletionRiskTests {

        @Test
        @DisplayName("fromSymbol returns HIGH for public symbols")
        fun fromSymbol_returnsHighForPublic() {
            val symbol = DeadCodeSymbol(
                name = "test",
                qualifiedName = "com.example.test",
                type = SymbolType.METHOD,
                location = SymbolLocation.empty(),
                usageCount = 0,
                lastUsedDate = null,
                confidence = 1.0f,
                canSafeDelete = true,
                visibility = SymbolVisibility.PUBLIC
            )

            assertEquals(DeletionRisk.HIGH, DeletionRisk.fromSymbol(symbol))
        }

        @Test
        @DisplayName("fromSymbol returns HIGH for low confidence")
        fun fromSymbol_returnsHighForLowConfidence() {
            val symbol = DeadCodeSymbol.simple("test", confidence = 0.4f)

            assertEquals(DeletionRisk.HIGH, DeletionRisk.fromSymbol(symbol))
        }

        @Test
        @DisplayName("fromSymbol returns LOW for private high-confidence")
        fun fromSymbol_returnsLowForPrivateHighConfidence() {
            val symbol = DeadCodeSymbol.simple("test", confidence = 0.95f)

            assertEquals(DeletionRisk.LOW, DeletionRisk.fromSymbol(symbol))
        }

        @Test
        @DisplayName("weights are ordered correctly")
        fun weights_areOrderedCorrectly() {
            assertTrue(DeletionRisk.HIGH.weight > DeletionRisk.MEDIUM.weight)
            assertTrue(DeletionRisk.MEDIUM.weight > DeletionRisk.LOW.weight)
        }
    }

    // =========================================================================
    // DeadCodeConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("DeadCodeConfig")
    inner class DeadCodeConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = DeadCodeConfig()

            assertTrue(config.enabled)
            assertTrue(config.includePrivate)
            assertTrue(config.excludePublicApi)
            assertEquals(0.8f, config.minConfidence)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = DeadCodeConfig(enabled = true)
            val toggled = config.toggle()

            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withPattern adds pattern")
        fun withPattern_addsPattern() {
            val config = DeadCodeConfig()
            val updated = config.withPattern("*Generated*")

            assertTrue("*Generated*" in updated.excludePatterns)
        }

        @ParameterizedTest
        @DisplayName("shouldExclude matches patterns")
        @CsvSource(
            "MyTest, true",
            "MockService, true",
            "FakeRepository, true",
            "RealService, false"
        )
        fun shouldExclude_matchesPatterns(name: String, expected: Boolean) {
            val config = DeadCodeConfig()
            assertEquals(expected, config.shouldExclude(name))
        }

        @Test
        @DisplayName("shouldAnalyze respects enabled flag")
        fun shouldAnalyze_respectsEnabledFlag() {
            val config = DeadCodeConfig.DISABLED
            val symbol = DeadCodeSymbol.simple("test")

            assertFalse(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("shouldAnalyze excludes by pattern")
        fun shouldAnalyze_excludesByPattern() {
            val config = DeadCodeConfig()
            val symbol = DeadCodeSymbol.simple("MyTestClass")

            assertFalse(config.shouldAnalyze(symbol))
        }

        @Test
        @DisplayName("STRICT preset includes more symbols")
        fun strict_includesMoreSymbols() {
            val strict = DeadCodeConfig.STRICT

            assertFalse(strict.excludePublicApi)
            assertEquals(0.5f, strict.minConfidence)
        }

        @Test
        @DisplayName("CONSERVATIVE preset is more restrictive")
        fun conservative_isMoreRestrictive() {
            val conservative = DeadCodeConfig.CONSERVATIVE

            assertFalse(conservative.includeInternal)
            assertEquals(0.95f, conservative.minConfidence)
        }
    }

    // =========================================================================
    // DeadCodeAnalysisResult Tests
    // =========================================================================

    @Nested
    @DisplayName("DeadCodeAnalysisResult")
    inner class DeadCodeAnalysisResultTests {

        @Test
        @DisplayName("deadCodePercentage calculates correctly")
        fun deadCodePercentage_calculatesCorrectly() {
            val result = DeadCodeAnalysisResult(
                symbols = listOf(DeadCodeSymbol.simple("test")),
                totalSymbolsAnalyzed = 10,
                totalLines = 1000,
                deadCodeLines = 100
            )

            assertEquals(10f, result.deadCodePercentage, 0.1f)
        }

        @Test
        @DisplayName("byType groups correctly")
        fun byType_groupsCorrectly() {
            val symbols = listOf(
                DeadCodeSymbol.simple("method1", SymbolType.METHOD),
                DeadCodeSymbol.simple("method2", SymbolType.METHOD),
                DeadCodeSymbol.simple("field1", SymbolType.FIELD)
            )
            val result = DeadCodeAnalysisResult(symbols, 3, 100, 10)

            assertEquals(2, result.byType[SymbolType.METHOD]?.size)
            assertEquals(1, result.byType[SymbolType.FIELD]?.size)
        }

        @Test
        @DisplayName("safeToDelete filters correctly")
        fun safeToDelete_filtersCorrectly() {
            val symbols = listOf(
                DeadCodeSymbol.simple("safe", canSafeDelete = true),
                DeadCodeSymbol.simple("unsafe", canSafeDelete = false)
            )
            val result = DeadCodeAnalysisResult(symbols, 2, 100, 10)

            assertEquals(1, result.safeDeleteCount)
            assertEquals("safe", result.safeToDelete.first().name)
        }

        @Test
        @DisplayName("empty creates empty result")
        fun empty_createsEmptyResult() {
            val result = DeadCodeAnalysisResult.empty()

            assertTrue(result.symbols.isEmpty())
            assertEquals(0, result.totalSymbolsAnalyzed)
        }
    }

    // =========================================================================
    // DeadCodeSummary Tests
    // =========================================================================

    @Nested
    @DisplayName("DeadCodeSummary")
    inner class DeadCodeSummaryTests {

        @Test
        @DisplayName("from computes correct counts")
        fun from_computesCorrectCounts() {
            val symbols = listOf(
                DeadCodeSymbol.simple("test1", SymbolType.METHOD, canSafeDelete = true),
                DeadCodeSymbol.simple("test2", SymbolType.FIELD, canSafeDelete = true),
                DeadCodeSymbol.simple("test3", SymbolType.METHOD, canSafeDelete = false)
            )

            val summary = DeadCodeSummary.from(symbols)

            assertEquals(3, summary.totalSymbols)
            assertEquals(2, summary.safeDeleteCount)
        }

        @Test
        @DisplayName("mostCommonType returns most frequent")
        fun mostCommonType_returnsMostFrequent() {
            val symbols = listOf(
                DeadCodeSymbol.simple("m1", SymbolType.METHOD),
                DeadCodeSymbol.simple("m2", SymbolType.METHOD),
                DeadCodeSymbol.simple("f1", SymbolType.FIELD)
            )

            val summary = DeadCodeSummary.from(symbols)

            assertEquals(SymbolType.METHOD, summary.mostCommonType)
        }

        @Test
        @DisplayName("hasDeadCode returns true when symbols exist")
        fun hasDeadCode_returnsTrueWhenSymbolsExist() {
            val summary = DeadCodeSummary.from(listOf(DeadCodeSymbol.simple("test")))
            assertTrue(summary.hasDeadCode)
        }

        @Test
        @DisplayName("EMPTY has zero counts")
        fun empty_hasZeroCounts() {
            val summary = DeadCodeSummary.EMPTY

            assertEquals(0, summary.totalSymbols)
            assertFalse(summary.hasDeadCode)
        }
    }

    // =========================================================================
    // DeadCodeFilter Tests
    // =========================================================================

    @Nested
    @DisplayName("DeadCodeFilter")
    inner class DeadCodeFilterTests {

        @Test
        @DisplayName("all filters have display names")
        fun allFilters_haveDisplayNames() {
            DeadCodeFilter.entries.forEach { filter ->
                assertTrue(filter.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("toString returns display name")
        fun toString_returnsDisplayName() {
            assertEquals("Safe to Delete", DeadCodeFilter.SAFE_DELETE.toString())
            assertEquals("High Confidence", DeadCodeFilter.HIGH_CONFIDENCE.toString())
        }
    }

    // =========================================================================
    // Helper extension for simpler test creation
    // =========================================================================

    private fun DeadCodeSymbol.Companion.simple(
        name: String,
        type: SymbolType = SymbolType.METHOD,
        confidence: Float = 1.0f,
        canSafeDelete: Boolean = true,
        usageCount: Int = 0
    ) = DeadCodeSymbol(
        name = name,
        qualifiedName = "com.example.$name",
        type = type,
        location = SymbolLocation.empty(),
        usageCount = usageCount,
        lastUsedDate = null,
        confidence = confidence,
        canSafeDelete = canSafeDelete
    )
}
