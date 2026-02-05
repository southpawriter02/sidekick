package com.sidekick.quality.performance

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Comprehensive unit tests for Performance Linter Models.
 *
 * Tests cover:
 * - PerformanceIssue properties
 * - IssueLocation formatting
 * - IssueType pattern detection
 * - IssueCategory grouping
 * - IssueSeverity ordering
 * - PerformanceLinterConfig behavior
 * - PerformanceAnalysisResult aggregation
 * - PerformanceSummary computation
 *
 * @since 0.6.3
 */
@DisplayName("Performance Linter Models")
class PerformanceLinterModelsTest {

    // =========================================================================
    // PerformanceIssue Tests
    // =========================================================================

    @Nested
    @DisplayName("PerformanceIssue")
    inner class PerformanceIssueTests {

        @Test
        @DisplayName("isUrgent returns true for critical severity")
        fun isUrgent_returnsTrueForCritical() {
            val issue = PerformanceIssue.simple(IssueType.ASYNC_VOID, IssueSeverity.CRITICAL)
            assertTrue(issue.isUrgent)
        }

        @Test
        @DisplayName("isUrgent returns true for high severity")
        fun isUrgent_returnsTrueForHigh() {
            val issue = PerformanceIssue.simple(IssueType.N_PLUS_ONE, IssueSeverity.HIGH)
            assertTrue(issue.isUrgent)
        }

        @Test
        @DisplayName("isUrgent returns false for medium severity")
        fun isUrgent_returnsFalseForMedium() {
            val issue = PerformanceIssue.simple(IssueType.ALLOCATION_IN_LOOP, IssueSeverity.MEDIUM)
            assertFalse(issue.isUrgent)
        }

        @Test
        @DisplayName("displayString includes severity and type")
        fun displayString_includesSeverityAndType() {
            val issue = PerformanceIssue.simple(
                type = IssueType.STRING_CONCAT_LOOP,
                severity = IssueSeverity.HIGH,
                description = "Test description"
            )

            assertTrue(issue.displayString.contains("High"))
            assertTrue(issue.displayString.contains("String concatenation"))
        }
    }

    // =========================================================================
    // IssueLocation Tests
    // =========================================================================

    @Nested
    @DisplayName("IssueLocation")
    inner class IssueLocationTests {

        @Test
        @DisplayName("fileName extracts file name from path")
        fun fileName_extractsFromPath() {
            val location = IssueLocation(
                filePath = "/Users/test/project/src/main/Service.kt",
                line = 42,
                codeSnippet = "code here"
            )

            assertEquals("Service.kt", location.fileName)
        }

        @Test
        @DisplayName("displayString formats as filename:line")
        fun displayString_formatsCorrectly() {
            val location = IssueLocation("/path/to/Handler.cs", 100, "code")

            assertEquals("Handler.cs:100", location.displayString)
        }
    }

    // =========================================================================
    // IssueType Tests
    // =========================================================================

    @Nested
    @DisplayName("IssueType")
    inner class IssueTypeTests {

        @ParameterizedTest
        @DisplayName("all types have display names")
        @EnumSource(IssueType::class)
        fun allTypes_haveDisplayNames(type: IssueType) {
            assertTrue(type.displayName.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("all types have categories")
        @EnumSource(IssueType::class)
        fun allTypes_haveCategories(type: IssueType) {
            assertNotNull(type.category)
        }

        @Test
        @DisplayName("detect finds string concat in loop")
        fun detect_findsStringConcatInLoop() {
            val code = """for (i in 0..10) { result += "item" }"""
            val detected = IssueType.detect(code)

            assertTrue(IssueType.STRING_CONCAT_LOOP in detected)
        }

        @Test
        @DisplayName("detect finds async void")
        fun detect_findsAsyncVoid() {
            val code = "async void HandleClick() { await DoWork(); }"
            val detected = IssueType.detect(code)

            assertTrue(IssueType.ASYNC_VOID in detected)
        }

        @Test
        @DisplayName("detect finds sync over async")
        fun detect_findsSyncOverAsync() {
            val code = "var result = asyncOperation.Result;"
            val detected = IssueType.detect(code)

            assertTrue(IssueType.SYNC_OVER_ASYNC in detected)
        }

        @Test
        @DisplayName("detect finds .Wait() call")
        fun detect_findsWaitCall() {
            val code = "asyncTask.Wait();"
            val detected = IssueType.detect(code)

            assertTrue(IssueType.SYNC_OVER_ASYNC in detected)
        }

        @Test
        @DisplayName("detect finds LINQ in code")
        fun detect_findsLinq() {
            val code = "items.Where(x => x.Active).Select(x => x.Name)"
            val detected = IssueType.detect(code)

            assertTrue(IssueType.LINQ_IN_HOT_PATH in detected)
        }

        @Test
        @DisplayName("detect returns empty for clean code")
        fun detect_returnsEmptyForCleanCode() {
            val code = "var x = 5; return x * 2;"
            val detected = IssueType.detect(code)

            assertTrue(detected.isEmpty())
        }

        @Test
        @DisplayName("byCategory returns memory issues")
        fun byCategory_returnsMemoryIssues() {
            val memoryIssues = IssueType.byCategory(IssueCategory.MEMORY)

            assertTrue(IssueType.STRING_CONCAT_LOOP in memoryIssues)
            assertTrue(IssueType.ALLOCATION_IN_LOOP in memoryIssues)
            assertTrue(IssueType.BOXING in memoryIssues)
        }

        @Test
        @DisplayName("byCategory returns async issues")
        fun byCategory_returnsAsyncIssues() {
            val asyncIssues = IssueType.byCategory(IssueCategory.ASYNC)

            assertTrue(IssueType.ASYNC_VOID in asyncIssues)
            assertTrue(IssueType.SYNC_OVER_ASYNC in asyncIssues)
        }

        @Test
        @DisplayName("byName finds type case-insensitively")
        fun byName_findsTypeIgnoringCase() {
            assertEquals(IssueType.ASYNC_VOID, IssueType.byName("ASYNC_VOID"))
            assertEquals(IssueType.ASYNC_VOID, IssueType.byName("async_void"))
        }
    }

    // =========================================================================
    // IssueCategory Tests
    // =========================================================================

    @Nested
    @DisplayName("IssueCategory")
    inner class IssueCategoryTests {

        @ParameterizedTest
        @DisplayName("all categories have display names")
        @EnumSource(IssueCategory::class)
        fun allCategories_haveDisplayNames(category: IssueCategory) {
            assertTrue(category.displayName.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("all categories have descriptions")
        @EnumSource(IssueCategory::class)
        fun allCategories_haveDescriptions(category: IssueCategory) {
            assertTrue(category.description.isNotBlank())
        }

        @Test
        @DisplayName("byName finds category case-insensitively")
        fun byName_findsCategoryIgnoringCase() {
            assertEquals(IssueCategory.MEMORY, IssueCategory.byName("memory"))
            assertEquals(IssueCategory.ASYNC, IssueCategory.byName("ASYNC"))
        }
    }

    // =========================================================================
    // IssueSeverity Tests
    // =========================================================================

    @Nested
    @DisplayName("IssueSeverity")
    inner class IssueSeverityTests {

        @ParameterizedTest
        @DisplayName("all severities have valid weights")
        @EnumSource(IssueSeverity::class)
        fun allSeverities_haveValidWeights(severity: IssueSeverity) {
            assertTrue(severity.weight in 1..4)
        }

        @Test
        @DisplayName("severities are ordered by weight")
        fun severities_areOrderedByWeight() {
            assertTrue(IssueSeverity.CRITICAL.weight > IssueSeverity.HIGH.weight)
            assertTrue(IssueSeverity.HIGH.weight > IssueSeverity.MEDIUM.weight)
            assertTrue(IssueSeverity.MEDIUM.weight > IssueSeverity.LOW.weight)
        }

        @Test
        @DisplayName("ALL returns severities in weight order")
        fun all_returnsSeveritiesInWeightOrder() {
            val all = IssueSeverity.ALL
            assertEquals(IssueSeverity.CRITICAL, all[0])
            assertEquals(IssueSeverity.LOW, all.last())
        }

        @Test
        @DisplayName("byName finds severity case-insensitively")
        fun byName_findsSeverityIgnoringCase() {
            assertEquals(IssueSeverity.HIGH, IssueSeverity.byName("high"))
            assertEquals(IssueSeverity.HIGH, IssueSeverity.byName("HIGH"))
        }

        @Test
        @DisplayName("byName returns MEDIUM for unknown")
        fun byName_returnsMediumForUnknown() {
            assertEquals(IssueSeverity.MEDIUM, IssueSeverity.byName("unknown"))
        }
    }

    // =========================================================================
    // PerformanceLinterConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("PerformanceLinterConfig")
    inner class PerformanceLinterConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = PerformanceLinterConfig()

            assertTrue(config.enabled)
            assertEquals(IssueSeverity.MEDIUM, config.minSeverity)
            assertTrue(config.ignoreTestFiles)
            assertEquals(IssueType.entries.size, config.enabledRules.size)
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = PerformanceLinterConfig(enabled = true)
            val toggled = config.toggle()

            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withMinSeverity changes severity")
        fun withMinSeverity_changesSeverity() {
            val config = PerformanceLinterConfig()
            val updated = config.withMinSeverity(IssueSeverity.CRITICAL)

            assertEquals(IssueSeverity.CRITICAL, updated.minSeverity)
        }

        @Test
        @DisplayName("withRule adds rule")
        fun withRule_addsRule() {
            val config = PerformanceLinterConfig(enabledRules = emptySet())
            val updated = config.withRule(IssueType.ASYNC_VOID)

            assertTrue(IssueType.ASYNC_VOID in updated.enabledRules)
        }

        @Test
        @DisplayName("withoutRule removes rule")
        fun withoutRule_removesRule() {
            val config = PerformanceLinterConfig()
            val updated = config.withoutRule(IssueType.BOXING)

            assertFalse(IssueType.BOXING in updated.enabledRules)
        }

        @Test
        @DisplayName("shouldReport returns false when disabled")
        fun shouldReport_returnsFalseWhenDisabled() {
            val config = PerformanceLinterConfig.DISABLED
            val issue = PerformanceIssue.simple(IssueType.ASYNC_VOID, IssueSeverity.CRITICAL)

            assertFalse(config.shouldReport(issue))
        }

        @Test
        @DisplayName("shouldReport filters by severity")
        fun shouldReport_filtersBySeverity() {
            val config = PerformanceLinterConfig(minSeverity = IssueSeverity.HIGH)

            val critical = PerformanceIssue.simple(IssueType.ASYNC_VOID, IssueSeverity.CRITICAL)
            val high = PerformanceIssue.simple(IssueType.N_PLUS_ONE, IssueSeverity.HIGH)
            val medium = PerformanceIssue.simple(IssueType.ALLOCATION_IN_LOOP, IssueSeverity.MEDIUM)

            assertTrue(config.shouldReport(critical))
            assertTrue(config.shouldReport(high))
            assertFalse(config.shouldReport(medium))
        }

        @Test
        @DisplayName("shouldReport filters by enabled rules")
        fun shouldReport_filtersByEnabledRules() {
            val config = PerformanceLinterConfig(
                enabledRules = setOf(IssueType.ASYNC_VOID)
            )

            val async = PerformanceIssue.simple(IssueType.ASYNC_VOID, IssueSeverity.HIGH)
            val concat = PerformanceIssue.simple(IssueType.STRING_CONCAT_LOOP, IssueSeverity.HIGH)

            assertTrue(config.shouldReport(async))
            assertFalse(config.shouldReport(concat))
        }

        @Test
        @DisplayName("STRICT preset reports everything")
        fun preset_strictReportsEverything() {
            val config = PerformanceLinterConfig.STRICT
            val low = PerformanceIssue.simple(IssueType.REGEX_NOT_COMPILED, IssueSeverity.LOW)

            assertTrue(config.shouldReport(low))
        }

        @Test
        @DisplayName("RELAXED preset only reports high and critical")
        fun preset_relaxedOnlyReportsHighAndCritical() {
            val config = PerformanceLinterConfig.RELAXED

            val high = PerformanceIssue.simple(IssueType.N_PLUS_ONE, IssueSeverity.HIGH)
            val medium = PerformanceIssue.simple(IssueType.ALLOCATION_IN_LOOP, IssueSeverity.MEDIUM)

            assertTrue(config.shouldReport(high))
            assertFalse(config.shouldReport(medium))
        }

        @Test
        @DisplayName("ASYNC_FOCUSED preset only has async rules")
        fun preset_asyncFocusedOnlyHasAsyncRules() {
            val config = PerformanceLinterConfig.ASYNC_FOCUSED

            assertTrue(IssueType.ASYNC_VOID in config.enabledRules)
            assertTrue(IssueType.SYNC_OVER_ASYNC in config.enabledRules)
            assertFalse(IssueType.STRING_CONCAT_LOOP in config.enabledRules)
        }
    }

    // =========================================================================
    // PerformanceAnalysisResult Tests
    // =========================================================================

    @Nested
    @DisplayName("PerformanceAnalysisResult")
    inner class PerformanceAnalysisResultTests {

        @Test
        @DisplayName("hasIssues returns true when issues present")
        fun hasIssues_returnsTrueWhenPresent() {
            val result = createResult(listOf(
                PerformanceIssue.simple(IssueType.ASYNC_VOID)
            ))

            assertTrue(result.hasIssues)
        }

        @Test
        @DisplayName("hasIssues returns false when no issues")
        fun hasIssues_returnsFalseWhenEmpty() {
            val result = createResult(emptyList())
            assertFalse(result.hasIssues)
        }

        @Test
        @DisplayName("criticalCount counts only critical issues")
        fun criticalCount_countsOnlyCritical() {
            val result = createResult(listOf(
                PerformanceIssue.simple(IssueType.ASYNC_VOID, IssueSeverity.CRITICAL),
                PerformanceIssue.simple(IssueType.SYNC_OVER_ASYNC, IssueSeverity.CRITICAL),
                PerformanceIssue.simple(IssueType.N_PLUS_ONE, IssueSeverity.HIGH)
            ))

            assertEquals(2, result.criticalCount)
        }

        @Test
        @DisplayName("byType groups correctly")
        fun byType_groupsCorrectly() {
            val result = createResult(listOf(
                PerformanceIssue.simple(IssueType.ASYNC_VOID),
                PerformanceIssue.simple(IssueType.ASYNC_VOID),
                PerformanceIssue.simple(IssueType.N_PLUS_ONE)
            ))

            val grouped = result.byType()
            assertEquals(2, grouped[IssueType.ASYNC_VOID]?.size)
            assertEquals(1, grouped[IssueType.N_PLUS_ONE]?.size)
        }

        @Test
        @DisplayName("byCategory groups correctly")
        fun byCategory_groupsCorrectly() {
            val result = createResult(listOf(
                PerformanceIssue.simple(IssueType.ASYNC_VOID),       // ASYNC
                PerformanceIssue.simple(IssueType.SYNC_OVER_ASYNC),  // ASYNC
                PerformanceIssue.simple(IssueType.N_PLUS_ONE)        // DATABASE
            ))

            val grouped = result.byCategory()
            assertEquals(2, grouped[IssueCategory.ASYNC]?.size)
            assertEquals(1, grouped[IssueCategory.DATABASE]?.size)
        }

        @Test
        @DisplayName("empty creates empty result")
        fun empty_createsEmptyResult() {
            val result = PerformanceAnalysisResult.empty("/test/file.kt")

            assertEquals("/test/file.kt", result.filePath)
            assertFalse(result.hasIssues)
            assertEquals(0, result.totalCount)
        }

        private fun createResult(issues: List<PerformanceIssue>) = PerformanceAnalysisResult(
            filePath = "/test/file.kt",
            issues = issues,
            analysisTimeMs = 100
        )
    }

    // =========================================================================
    // PerformanceSummary Tests
    // =========================================================================

    @Nested
    @DisplayName("PerformanceSummary")
    inner class PerformanceSummaryTests {

        @Test
        @DisplayName("from computes correct counts")
        fun from_computesCorrectCounts() {
            val issues = listOf(
                PerformanceIssue.simple(IssueType.ASYNC_VOID, IssueSeverity.CRITICAL),
                PerformanceIssue.simple(IssueType.N_PLUS_ONE, IssueSeverity.HIGH),
                PerformanceIssue.simple(IssueType.N_PLUS_ONE, IssueSeverity.HIGH)
            )

            val summary = PerformanceSummary.from(issues)

            assertEquals(3, summary.totalIssues)
            assertEquals(IssueType.N_PLUS_ONE, summary.mostCommonType)
        }

        @Test
        @DisplayName("from groups by category correctly")
        fun from_groupsByCategoryCorrectly() {
            val issues = listOf(
                PerformanceIssue.simple(IssueType.ASYNC_VOID),     // ASYNC
                PerformanceIssue.simple(IssueType.ASYNC_VOID),     // ASYNC
                PerformanceIssue.simple(IssueType.N_PLUS_ONE)      // DATABASE
            )

            val summary = PerformanceSummary.from(issues)

            assertEquals(2, summary.byCategory[IssueCategory.ASYNC])
            assertEquals(1, summary.byCategory[IssueCategory.DATABASE])
        }

        @Test
        @DisplayName("hasUrgent returns true when critical exists")
        fun hasUrgent_returnsTrueWhenCritical() {
            val summary = PerformanceSummary(
                totalIssues = 1,
                byCategory = emptyMap(),
                bySeverity = mapOf(IssueSeverity.CRITICAL to 1),
                mostCommonType = null
            )

            assertTrue(summary.hasUrgent)
        }

        @Test
        @DisplayName("hasUrgent returns false when no urgent")
        fun hasUrgent_returnsFalseWhenNoUrgent() {
            val summary = PerformanceSummary(
                totalIssues = 5,
                byCategory = emptyMap(),
                bySeverity = mapOf(IssueSeverity.MEDIUM to 5),
                mostCommonType = null
            )

            assertFalse(summary.hasUrgent)
        }

        @Test
        @DisplayName("EMPTY has zero counts")
        fun empty_hasZeroCounts() {
            val summary = PerformanceSummary.EMPTY

            assertEquals(0, summary.totalIssues)
            assertNull(summary.mostCommonType)
        }
    }
}
