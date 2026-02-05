package com.sidekick.quality.exceptions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Comprehensive unit tests for Exception Hunter Models.
 *
 * Tests cover:
 * - UnhandledException properties
 * - ExceptionLocation formatting
 * - CallSite exception matching
 * - ExceptionSeverity classification
 * - ExceptionHunterConfig behavior
 * - ExceptionAnalysisResult aggregation
 *
 * @since 0.6.1
 */
@DisplayName("Exception Hunter Models")
class ExceptionHunterModelsTest {

    // =========================================================================
    // UnhandledException Tests
    // =========================================================================

    @Nested
    @DisplayName("UnhandledException")
    inner class UnhandledExceptionTests {

        @Test
        @DisplayName("isRuntime returns true for runtime exceptions")
        fun isRuntime_returnsTrueForRuntimeExceptions() {
            val exception = createException("NullPointerException")
            assertTrue(exception.isRuntime)
        }

        @Test
        @DisplayName("isRuntime returns true for exceptions containing Runtime")
        fun isRuntime_returnsTrueForRuntimeKeyword() {
            val exception = createException("RuntimeException")
            assertTrue(exception.isRuntime)
        }

        @Test
        @DisplayName("isRuntime returns true for IllegalArgumentException")
        fun isRuntime_returnsTrueForIllegalArgument() {
            val exception = createException("IllegalArgumentException")
            assertTrue(exception.isRuntime)
        }

        @Test
        @DisplayName("isRuntime returns false for checked exceptions")
        fun isRuntime_returnsFalseForCheckedException() {
            val exception = createException("IOException")
            assertFalse(exception.isRuntime)
        }

        @Test
        @DisplayName("isRuntime returns false for SQLException")
        fun isRuntime_returnsFalseForSQLException() {
            val exception = createException("SQLException")
            assertFalse(exception.isRuntime)
        }

        private fun createException(type: String) = UnhandledException(
            exceptionType = type,
            location = ExceptionLocation("/test/file.kt", 10, 0, null, null),
            callChain = emptyList(),
            severity = ExceptionSeverity.MEDIUM,
            suggestion = "Test suggestion"
        )
    }

    // =========================================================================
    // ExceptionLocation Tests
    // =========================================================================

    @Nested
    @DisplayName("ExceptionLocation")
    inner class ExceptionLocationTests {

        @Test
        @DisplayName("displayString shows full context when available")
        fun displayString_showsFullContext() {
            val location = ExceptionLocation(
                filePath = "/test/file.kt",
                line = 42,
                column = 8,
                methodName = "processData",
                className = "DataProcessor"
            )
            
            assertEquals("DataProcessor.processData() [42:8]", location.displayString)
        }

        @Test
        @DisplayName("displayString shows only line when no context")
        fun displayString_showsOnlyLineWhenNoContext() {
            val location = ExceptionLocation(
                filePath = "/test/file.kt",
                line = 42,
                column = 8,
                methodName = null,
                className = null
            )
            
            assertEquals("[42:8]", location.displayString)
        }

        @Test
        @DisplayName("displayString shows class only when no method")
        fun displayString_showsClassOnlyWhenNoMethod() {
            val location = ExceptionLocation(
                filePath = "/test/file.kt",
                line = 42,
                column = 8,
                methodName = null,
                className = "DataProcessor"
            )
            
            assertEquals("DataProcessor.[42:8]", location.displayString)
        }
    }

    // =========================================================================
    // CallSite Tests
    // =========================================================================

    @Nested
    @DisplayName("CallSite")
    inner class CallSiteTests {

        @Test
        @DisplayName("declaresException returns true for exact match")
        fun declaresException_returnsTrueForExactMatch() {
            val callSite = CallSite(
                methodName = "readFile",
                className = "FileReader",
                throwsDeclaration = listOf("IOException", "FileNotFoundException")
            )

            assertTrue(callSite.declaresException("IOException"))
        }

        @Test
        @DisplayName("declaresException returns true for partial match")
        fun declaresException_returnsTrueForPartialMatch() {
            val callSite = CallSite(
                methodName = "readFile",
                className = "FileReader",
                throwsDeclaration = listOf("IOException")
            )

            assertTrue(callSite.declaresException("FileNotFoundException"))
        }

        @Test
        @DisplayName("declaresException returns false when not declared")
        fun declaresException_returnsFalseWhenNotDeclared() {
            val callSite = CallSite(
                methodName = "readFile",
                className = "FileReader",
                throwsDeclaration = listOf("IOException")
            )

            assertFalse(callSite.declaresException("SQLException"))
        }

        @Test
        @DisplayName("declaresException handles empty throws list")
        fun declaresException_handleEmptyThrowsList() {
            val callSite = CallSite(
                methodName = "calculate",
                className = "Calculator",
                throwsDeclaration = emptyList()
            )

            assertFalse(callSite.declaresException("IOException"))
        }
    }

    // =========================================================================
    // ExceptionSeverity Tests
    // =========================================================================

    @Nested
    @DisplayName("ExceptionSeverity")
    inner class ExceptionSeverityTests {

        @ParameterizedTest
        @DisplayName("all severities have display names")
        @EnumSource(ExceptionSeverity::class)
        fun allSeverities_haveDisplayNames(severity: ExceptionSeverity) {
            assertNotNull(severity.displayName)
            assertTrue(severity.displayName.isNotBlank())
        }

        @ParameterizedTest
        @DisplayName("all severities have valid priorities")
        @EnumSource(ExceptionSeverity::class)
        fun allSeverities_haveValidPriorities(severity: ExceptionSeverity) {
            assertTrue(severity.priority in 1..5)
        }

        @ParameterizedTest
        @DisplayName("fromExceptionType classifies critical exceptions")
        @CsvSource(
            "NullPointerException, CRITICAL",
            "OutOfMemoryError, CRITICAL",
            "StackOverflowError, CRITICAL"
        )
        fun fromExceptionType_classifiesCritical(type: String, expected: String) {
            assertEquals(ExceptionSeverity.valueOf(expected), ExceptionSeverity.fromExceptionType(type))
        }

        @ParameterizedTest
        @DisplayName("fromExceptionType classifies high severity exceptions")
        @CsvSource(
            "IOException, HIGH",
            "SQLException, HIGH",
            "SecurityException, HIGH",
            "NetworkException, HIGH"
        )
        fun fromExceptionType_classifiesHigh(type: String, expected: String) {
            assertEquals(ExceptionSeverity.valueOf(expected), ExceptionSeverity.fromExceptionType(type))
        }

        @ParameterizedTest
        @DisplayName("fromExceptionType classifies medium severity exceptions")
        @CsvSource(
            "IllegalArgumentException, MEDIUM",
            "IllegalStateException, MEDIUM",
            "UnsupportedOperationException, MEDIUM"
        )
        fun fromExceptionType_classifiesMedium(type: String, expected: String) {
            assertEquals(ExceptionSeverity.valueOf(expected), ExceptionSeverity.fromExceptionType(type))
        }

        @Test
        @DisplayName("fromExceptionType returns LOW for unknown types")
        fun fromExceptionType_returnsLowForUnknown() {
            assertEquals(ExceptionSeverity.LOW, ExceptionSeverity.fromExceptionType("CustomException"))
        }

        @Test
        @DisplayName("byName finds severity case-insensitively")
        fun byName_findsSeverityIgnoringCase() {
            assertEquals(ExceptionSeverity.HIGH, ExceptionSeverity.byName("High"))
            assertEquals(ExceptionSeverity.HIGH, ExceptionSeverity.byName("HIGH"))
            assertEquals(ExceptionSeverity.HIGH, ExceptionSeverity.byName("high"))
        }

        @Test
        @DisplayName("byName returns MEDIUM for unknown name")
        fun byName_returnsMediumForUnknown() {
            assertEquals(ExceptionSeverity.MEDIUM, ExceptionSeverity.byName("Unknown"))
        }

        @Test
        @DisplayName("ALL returns severities in priority order")
        fun all_returnsSeveritiesInPriorityOrder() {
            val all = ExceptionSeverity.ALL
            assertEquals(5, all.size)
            assertEquals(ExceptionSeverity.CRITICAL, all[0])
            assertEquals(ExceptionSeverity.INFO, all[4])
        }
    }

    // =========================================================================
    // ExceptionHunterConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("ExceptionHunterConfig")
    inner class ExceptionHunterConfigTests {

        @Test
        @DisplayName("default config has reasonable values")
        fun defaultConfig_hasReasonableValues() {
            val config = ExceptionHunterConfig()

            assertTrue(config.enabled)
            assertEquals(ExceptionSeverity.MEDIUM, config.minSeverity)
            assertTrue(config.showInGutter)
            assertTrue(config.highlightInEditor)
            assertTrue(config.traverseCallChain)
            assertEquals(5, config.maxCallChainDepth)
            assertTrue(config.ignoredExceptions.isEmpty())
        }

        @Test
        @DisplayName("toggle flips enabled state")
        fun toggle_flipsEnabledState() {
            val config = ExceptionHunterConfig(enabled = true)
            val toggled = config.toggle()

            assertTrue(config.enabled)
            assertFalse(toggled.enabled)
        }

        @Test
        @DisplayName("withMinSeverity changes severity")
        fun withMinSeverity_changesSeverity() {
            val config = ExceptionHunterConfig()
            val updated = config.withMinSeverity(ExceptionSeverity.HIGH)

            assertEquals(ExceptionSeverity.HIGH, updated.minSeverity)
        }

        @Test
        @DisplayName("withIgnored adds exception to ignore list")
        fun withIgnored_addsException() {
            val config = ExceptionHunterConfig()
            val updated = config.withIgnored("CustomException")

            assertTrue("CustomException" in updated.ignoredExceptions)
            assertFalse("CustomException" in config.ignoredExceptions)
        }

        @Test
        @DisplayName("withoutIgnored removes exception from ignore list")
        fun withoutIgnored_removesException() {
            val config = ExceptionHunterConfig(ignoredExceptions = setOf("CustomException"))
            val updated = config.withoutIgnored("CustomException")

            assertFalse("CustomException" in updated.ignoredExceptions)
            assertTrue("CustomException" in config.ignoredExceptions)
        }

        @Test
        @DisplayName("shouldReport returns false when disabled")
        fun shouldReport_returnsFalseWhenDisabled() {
            val config = ExceptionHunterConfig.DISABLED
            val exception = createTestException(ExceptionSeverity.CRITICAL)

            assertFalse(config.shouldReport(exception))
        }

        @Test
        @DisplayName("shouldReport returns false for ignored exception")
        fun shouldReport_returnsFalseForIgnored() {
            val config = ExceptionHunterConfig(ignoredExceptions = setOf("TestException"))
            val exception = createTestException(ExceptionSeverity.CRITICAL, "TestException")

            assertFalse(config.shouldReport(exception))
        }

        @Test
        @DisplayName("shouldReport filters by severity")
        fun shouldReport_filtersBySeverity() {
            val config = ExceptionHunterConfig(minSeverity = ExceptionSeverity.HIGH)
            
            val critical = createTestException(ExceptionSeverity.CRITICAL)
            val high = createTestException(ExceptionSeverity.HIGH)
            val medium = createTestException(ExceptionSeverity.MEDIUM)

            assertTrue(config.shouldReport(critical))
            assertTrue(config.shouldReport(high))
            assertFalse(config.shouldReport(medium))
        }

        @Test
        @DisplayName("preset STRICT reports everything")
        fun preset_strictReportsEverything() {
            val config = ExceptionHunterConfig.STRICT
            val info = createTestException(ExceptionSeverity.INFO)

            assertTrue(config.shouldReport(info))
        }

        @Test
        @DisplayName("preset RELAXED only reports critical and high")
        fun preset_relaxedOnlyReportsCriticalAndHigh() {
            val config = ExceptionHunterConfig.RELAXED
            
            val high = createTestException(ExceptionSeverity.HIGH)
            val medium = createTestException(ExceptionSeverity.MEDIUM)

            assertTrue(config.shouldReport(high))
            assertFalse(config.shouldReport(medium))
        }

        private fun createTestException(
            severity: ExceptionSeverity,
            type: String = "TestException"
        ) = UnhandledException(
            exceptionType = type,
            location = ExceptionLocation("/test/file.kt", 10, 0, null, null),
            callChain = emptyList(),
            severity = severity,
            suggestion = "Test"
        )
    }

    // =========================================================================
    // ExceptionAnalysisResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ExceptionAnalysisResult")
    inner class ExceptionAnalysisResultTests {

        @Test
        @DisplayName("hasIssues returns true when exceptions present")
        fun hasIssues_returnsTrueWhenExceptionsPresent() {
            val result = createResult(listOf(
                createException(ExceptionSeverity.MEDIUM)
            ))

            assertTrue(result.hasIssues)
        }

        @Test
        @DisplayName("hasIssues returns false when no exceptions")
        fun hasIssues_returnsFalseWhenEmpty() {
            val result = createResult(emptyList())

            assertFalse(result.hasIssues)
        }

        @Test
        @DisplayName("criticalCount counts only critical exceptions")
        fun criticalCount_countsOnlyCritical() {
            val result = createResult(listOf(
                createException(ExceptionSeverity.CRITICAL),
                createException(ExceptionSeverity.CRITICAL),
                createException(ExceptionSeverity.HIGH),
                createException(ExceptionSeverity.MEDIUM)
            ))

            assertEquals(2, result.criticalCount)
        }

        @Test
        @DisplayName("highCount counts only high exceptions")
        fun highCount_countsOnlyHigh() {
            val result = createResult(listOf(
                createException(ExceptionSeverity.CRITICAL),
                createException(ExceptionSeverity.HIGH),
                createException(ExceptionSeverity.HIGH),
                createException(ExceptionSeverity.MEDIUM)
            ))

            assertEquals(2, result.highCount)
        }

        @Test
        @DisplayName("totalCount returns total exception count")
        fun totalCount_returnsTotalCount() {
            val result = createResult(listOf(
                createException(ExceptionSeverity.CRITICAL),
                createException(ExceptionSeverity.HIGH),
                createException(ExceptionSeverity.MEDIUM)
            ))

            assertEquals(3, result.totalCount)
        }

        @Test
        @DisplayName("bySeverity groups exceptions correctly")
        fun bySeverity_groupsCorrectly() {
            val result = createResult(listOf(
                createException(ExceptionSeverity.CRITICAL),
                createException(ExceptionSeverity.HIGH),
                createException(ExceptionSeverity.HIGH)
            ))

            val grouped = result.bySeverity()
            
            assertEquals(1, grouped[ExceptionSeverity.CRITICAL]?.size)
            assertEquals(2, grouped[ExceptionSeverity.HIGH]?.size)
            assertNull(grouped[ExceptionSeverity.MEDIUM])
        }

        @Test
        @DisplayName("atLine returns exceptions at specific line")
        fun atLine_returnsExceptionsAtLine() {
            val result = createResult(listOf(
                createException(ExceptionSeverity.MEDIUM, line = 10),
                createException(ExceptionSeverity.HIGH, line = 10),
                createException(ExceptionSeverity.LOW, line = 20)
            ))

            val atLine10 = result.atLine(10)
            
            assertEquals(2, atLine10.size)
        }

        @Test
        @DisplayName("empty creates empty result")
        fun empty_createsEmptyResult() {
            val result = ExceptionAnalysisResult.empty("/test/file.kt")

            assertEquals("/test/file.kt", result.filePath)
            assertFalse(result.hasIssues)
            assertEquals(0, result.analyzedMethods)
        }

        private fun createResult(exceptions: List<UnhandledException>) = ExceptionAnalysisResult(
            filePath = "/test/file.kt",
            exceptions = exceptions,
            analyzedMethods = 5,
            analysisTimeMs = 100
        )

        private fun createException(severity: ExceptionSeverity, line: Int = 10) = UnhandledException(
            exceptionType = "TestException",
            location = ExceptionLocation("/test/file.kt", line, 0, null, null),
            callChain = emptyList(),
            severity = severity,
            suggestion = "Test"
        )
    }
}
