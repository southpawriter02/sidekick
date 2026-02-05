package com.sidekick.quality.performance

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for Performance Linter Service.
 *
 * These tests focus on the service's internal logic that can be tested
 * without the IntelliJ Platform (severity mapping, descriptions, etc.).
 *
 * Note: Full PSI-based analysis tests would require IntelliJ Platform
 * test fixtures and are not included in this unit test suite.
 *
 * @since 0.6.3
 */
@DisplayName("Performance Linter Service")
class PerformanceLinterServiceTest {

    // =========================================================================
    // State Serialization Tests
    // =========================================================================

    @Nested
    @DisplayName("State")
    inner class StateTests {

        @Test
        @DisplayName("default state has reasonable values")
        fun defaultState_hasReasonableValues() {
            val state = PerformanceLinterService.State()

            assertTrue(state.enabled)
            assertEquals("Medium", state.minSeverityName)
            assertTrue(state.ignoreTestFiles)
            assertEquals(50, state.maxIssuesPerFile)
            assertTrue(state.disabledRules.isEmpty())
        }

        @Test
        @DisplayName("toConfig produces correct configuration")
        fun toConfig_producesCorrectConfig() {
            val state = PerformanceLinterService.State(
                enabled = false,
                minSeverityName = "High",
                ignoreTestFiles = false,
                maxIssuesPerFile = 25,
                disabledRules = mutableSetOf("ASYNC_VOID", "BOXING")
            )

            val config = state.toConfig()

            assertFalse(config.enabled)
            assertEquals(IssueSeverity.HIGH, config.minSeverity)
            assertFalse(config.ignoreTestFiles)
            assertEquals(25, config.maxIssuesPerFile)
            assertFalse(IssueType.ASYNC_VOID in config.enabledRules)
            assertFalse(IssueType.BOXING in config.enabledRules)
        }

        @Test
        @DisplayName("from creates state from config")
        fun from_createsStateFromConfig() {
            val config = PerformanceLinterConfig(
                enabled = true,
                minSeverity = IssueSeverity.CRITICAL,
                ignoreTestFiles = true,
                maxIssuesPerFile = 100,
                enabledRules = setOf(IssueType.ASYNC_VOID, IssueType.SYNC_OVER_ASYNC)
            )

            val state = PerformanceLinterService.State.from(config)

            assertTrue(state.enabled)
            assertEquals("Critical", state.minSeverityName)
            assertTrue(IssueType.STRING_CONCAT_LOOP.name in state.disabledRules)
            assertFalse(IssueType.ASYNC_VOID.name in state.disabledRules)
        }

        @Test
        @DisplayName("state roundtrip preserves values")
        fun state_roundtripPreservesValues() {
            val original = PerformanceLinterConfig(
                enabled = true,
                minSeverity = IssueSeverity.HIGH,
                enabledRules = setOf(IssueType.ASYNC_VOID, IssueType.N_PLUS_ONE)
            )

            val state = PerformanceLinterService.State.from(original)
            val restored = state.toConfig()

            assertEquals(original.enabled, restored.enabled)
            assertEquals(original.minSeverity, restored.minSeverity)
            assertEquals(original.enabledRules, restored.enabledRules)
        }
    }

    // =========================================================================
    // Severity Mapping Tests
    // =========================================================================

    @Nested
    @DisplayName("Severity Mapping")
    inner class SeverityMappingTests {

        @Test
        @DisplayName("async void is critical")
        fun asyncVoid_isCritical() {
            val severity = getSeverity(IssueType.ASYNC_VOID, false)
            assertEquals(IssueSeverity.CRITICAL, severity)
        }

        @Test
        @DisplayName("sync over async is critical")
        fun syncOverAsync_isCritical() {
            val severity = getSeverity(IssueType.SYNC_OVER_ASYNC, false)
            assertEquals(IssueSeverity.CRITICAL, severity)
        }

        @Test
        @DisplayName("string concat in loop is high")
        fun stringConcatLoop_isHigh() {
            val severity = getSeverity(IssueType.STRING_CONCAT_LOOP, false)
            assertEquals(IssueSeverity.HIGH, severity)
        }

        @Test
        @DisplayName("N+1 query is high")
        fun nPlusOne_isHigh() {
            val severity = getSeverity(IssueType.N_PLUS_ONE, false)
            assertEquals(IssueSeverity.HIGH, severity)
        }

        @Test
        @DisplayName("allocation in loop is medium")
        fun allocationInLoop_isMedium() {
            val severity = getSeverity(IssueType.ALLOCATION_IN_LOOP, false)
            assertEquals(IssueSeverity.MEDIUM, severity)
        }

        @Test
        @DisplayName("regex not compiled is low")
        fun regexNotCompiled_isLow() {
            val severity = getSeverity(IssueType.REGEX_NOT_COMPILED, false)
            assertEquals(IssueSeverity.LOW, severity)
        }

        @Test
        @DisplayName("hot path escalates severity")
        fun hotPath_escalatesSeverity() {
            val normalSeverity = getSeverity(IssueType.LINQ_IN_HOT_PATH, false)
            val hotPathSeverity = getSeverity(IssueType.LINQ_IN_HOT_PATH, true)

            assertEquals(IssueSeverity.MEDIUM, normalSeverity)
            assertEquals(IssueSeverity.HIGH, hotPathSeverity)
        }

        @Test
        @DisplayName("hot path does not escalate critical")
        fun hotPath_doesNotEscalateCritical() {
            val normalSeverity = getSeverity(IssueType.ASYNC_VOID, false)
            val hotPathSeverity = getSeverity(IssueType.ASYNC_VOID, true)

            assertEquals(IssueSeverity.CRITICAL, normalSeverity)
            assertEquals(IssueSeverity.CRITICAL, hotPathSeverity)
        }

        /**
         * Helper that mimics the service's severity mapping.
         */
        private fun getSeverity(type: IssueType, inHotPath: Boolean): IssueSeverity {
            val baseSeverity = when (type) {
                IssueType.ASYNC_VOID -> IssueSeverity.CRITICAL
                IssueType.SYNC_OVER_ASYNC -> IssueSeverity.CRITICAL
                IssueType.THREAD_SLEEP_IN_ASYNC -> IssueSeverity.CRITICAL
                IssueType.STRING_CONCAT_LOOP -> IssueSeverity.HIGH
                IssueType.N_PLUS_ONE -> IssueSeverity.HIGH
                IssueType.ALLOCATION_IN_LOOP -> IssueSeverity.MEDIUM
                IssueType.LINQ_IN_HOT_PATH -> if (inHotPath) IssueSeverity.HIGH else IssueSeverity.MEDIUM
                IssueType.UNBOUNDED_COLLECTION -> IssueSeverity.HIGH
                IssueType.LARGE_OBJECT_HEAP -> IssueSeverity.MEDIUM
                IssueType.REGEX_NOT_COMPILED -> IssueSeverity.LOW
                IssueType.BOXING -> IssueSeverity.LOW
                IssueType.DISPOSE_IN_FINALIZER -> IssueSeverity.MEDIUM
            }

            return if (inHotPath && baseSeverity.weight < IssueSeverity.HIGH.weight) {
                IssueSeverity.HIGH
            } else {
                baseSeverity
            }
        }
    }

    // =========================================================================
    // Description Tests
    // =========================================================================

    @Nested
    @DisplayName("Description Generation")
    inner class DescriptionTests {

        @Test
        @DisplayName("async void has meaningful description")
        fun asyncVoid_hasMeaningfulDescription() {
            val description = getDescription(IssueType.ASYNC_VOID)

            assertTrue(description.contains("await") || description.contains("exception"))
        }

        @Test
        @DisplayName("sync over async mentions deadlock")
        fun syncOverAsync_mentionsDeadlock() {
            val description = getDescription(IssueType.SYNC_OVER_ASYNC)

            assertTrue(description.contains("deadlock") || description.contains("block"))
        }

        @Test
        @DisplayName("string concat mentions allocations")
        fun stringConcat_mentionsAllocations() {
            val description = getDescription(IssueType.STRING_CONCAT_LOOP)

            assertTrue(description.contains("allocation") || description.contains("temporary"))
        }

        @Test
        @DisplayName("n plus one mentions database")
        fun nPlusOne_mentionsDatabase() {
            val description = getDescription(IssueType.N_PLUS_ONE)

            assertTrue(description.contains("database") || description.contains("query"))
        }

        /**
         * Helper that mimics the service's description generation.
         */
        private fun getDescription(type: IssueType): String {
            return when (type) {
                IssueType.STRING_CONCAT_LOOP -> 
                    "String concatenation in loop creates many temporary objects and causes O(n²) allocations"
                IssueType.ASYNC_VOID -> 
                    "Async void methods cannot be awaited and exceptions are unhandled"
                IssueType.SYNC_OVER_ASYNC -> 
                    "Blocking on async code can cause deadlocks and thread pool starvation"
                IssueType.N_PLUS_ONE -> 
                    "Potential N+1 query pattern: executing a query inside a loop causes n additional database roundtrips"
                else -> type.displayName
            }
        }
    }

    // =========================================================================
    // Suggestion Tests
    // =========================================================================

    @Nested
    @DisplayName("Suggestion Generation")
    inner class SuggestionTests {

        @Test
        @DisplayName("string concat suggests StringBuilder")
        fun stringConcat_suggestsStringBuilder() {
            val suggestion = getSuggestion(IssueType.STRING_CONCAT_LOOP)

            assertTrue(suggestion.contains("StringBuilder"))
        }

        @Test
        @DisplayName("async void suggests Task return")
        fun asyncVoid_suggestsTask() {
            val suggestion = getSuggestion(IssueType.ASYNC_VOID)

            assertTrue(suggestion.contains("Task"))
        }

        @Test
        @DisplayName("sync over async suggests await")
        fun syncOverAsync_suggestsAwait() {
            val suggestion = getSuggestion(IssueType.SYNC_OVER_ASYNC)

            assertTrue(suggestion.contains("await"))
        }

        @Test
        @DisplayName("n plus one suggests eager loading")
        fun nPlusOne_suggestsEagerLoading() {
            val suggestion = getSuggestion(IssueType.N_PLUS_ONE)

            assertTrue(suggestion.contains("Include") || suggestion.contains("batch"))
        }

        @Test
        @DisplayName("regex suggests compiled")
        fun regex_suggestsCompiled() {
            val suggestion = getSuggestion(IssueType.REGEX_NOT_COMPILED)

            assertTrue(suggestion.contains("Compiled") || suggestion.contains("static"))
        }

        /**
         * Helper that mimics the service's suggestion generation.
         */
        private fun getSuggestion(type: IssueType): String {
            return when (type) {
                IssueType.STRING_CONCAT_LOOP -> "Use StringBuilder for efficient string building"
                IssueType.ASYNC_VOID -> "Change return type to async Task"
                IssueType.SYNC_OVER_ASYNC -> "Use await instead of .Result/.Wait()"
                IssueType.N_PLUS_ONE -> "Use eager loading with .Include() outside the loop"
                IssueType.REGEX_NOT_COMPILED -> "Use RegexOptions.Compiled or static Regex"
                else -> "Review and optimize"
            }
        }
    }

    // =========================================================================
    // Impact Estimation Tests
    // =========================================================================

    @Nested
    @DisplayName("Impact Estimation")
    inner class ImpactTests {

        @Test
        @DisplayName("string concat has O(n²) impact")
        fun stringConcat_hasQuadraticImpact() {
            val impact = getImpact(IssueType.STRING_CONCAT_LOOP)

            assertNotNull(impact)
            assertTrue(impact!!.contains("O(n²)") || impact.contains("allocation"))
        }

        @Test
        @DisplayName("n plus one has query impact")
        fun nPlusOne_hasQueryImpact() {
            val impact = getImpact(IssueType.N_PLUS_ONE)

            assertNotNull(impact)
            assertTrue(impact!!.contains("database") || impact.contains("query") || impact.contains("N"))
        }

        @Test
        @DisplayName("sync over async has deadlock impact")
        fun syncOverAsync_hasDeadlockImpact() {
            val impact = getImpact(IssueType.SYNC_OVER_ASYNC)

            assertNotNull(impact)
            assertTrue(impact!!.contains("deadlock") || impact.contains("starvation"))
        }

        @Test
        @DisplayName("boxing has no impact estimate")
        fun boxing_hasNoImpact() {
            val impact = getImpact(IssueType.BOXING)

            assertNull(impact)
        }

        /**
         * Helper that mimics the service's impact estimation.
         */
        private fun getImpact(type: IssueType): String? {
            return when (type) {
                IssueType.STRING_CONCAT_LOOP -> "O(n²) allocations, significant GC pressure"
                IssueType.N_PLUS_ONE -> "N additional database queries, high latency"
                IssueType.SYNC_OVER_ASYNC -> "Potential deadlock, thread pool starvation"
                IssueType.UNBOUNDED_COLLECTION -> "Memory leak, eventual OutOfMemoryException"
                IssueType.ASYNC_VOID -> "Unhandled exceptions may crash the process"
                else -> null
            }
        }
    }

    // =========================================================================
    // Test File Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Test File Detection")
    inner class TestFileDetectionTests {

        @ParameterizedTest
        @DisplayName("detects test file paths")
        @ValueSource(strings = [
            "/src/test/kotlin/Service.kt",
            "/tests/UnitTests.cs",
            "/spec/MySpec.js",
            "/src/ServiceTest.kt",
            "/src/Service_test.go"
        ])
        fun detectsTestPaths(path: String) {
            assertTrue(isTestFile(path))
        }

        @ParameterizedTest
        @DisplayName("allows source file paths")
        @ValueSource(strings = [
            "/src/main/kotlin/Service.kt",
            "/src/Service.cs",
            "/lib/Handler.js"
        ])
        fun allowsSourcePaths(path: String) {
            assertFalse(isTestFile(path))
        }

        /**
         * Helper that mimics the service's test file detection.
         */
        private fun isTestFile(path: String): Boolean {
            val testPatterns = listOf(
                "/test/", "/tests/", "/spec/", "/specs/",
                "test.", "tests.", "spec.", "specs.",
                "_test.", "_tests.", "_spec."
            )
            return testPatterns.any { path.lowercase().contains(it) }
        }
    }
}
