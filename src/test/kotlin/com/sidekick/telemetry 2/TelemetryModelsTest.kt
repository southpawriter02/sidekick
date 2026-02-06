package com.sidekick.telemetry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive unit tests for Telemetry Models.
 */
@DisplayName("Telemetry Models Tests")
class TelemetryModelsTest {

    // =========================================================================
    // TelemetryConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("TelemetryConfig")
    inner class TelemetryConfigTests {

        @Test
        @DisplayName("disabled by default")
        fun disabledByDefault() {
            val config = TelemetryConfig()
            assertFalse(config.enabled)
        }

        @Test
        @DisplayName("isTypeEnabled requires master switch")
        fun isTypeEnabledRequiresMasterSwitch() {
            val disabled = TelemetryConfig(enabled = false, featureTracking = true)
            val enabled = TelemetryConfig(enabled = true, featureTracking = true)

            assertFalse(disabled.isTypeEnabled(EventType.FEATURE_USED))
            assertTrue(enabled.isTypeEnabled(EventType.FEATURE_USED))
        }

        @Test
        @DisplayName("isTypeEnabled respects individual toggles")
        fun isTypeEnabledRespectsIndividualToggles() {
            val config = TelemetryConfig(
                enabled = true,
                featureTracking = true,
                crashReporting = false,
                performanceMetrics = true,
                anonymousUsage = false
            )

            assertTrue(config.isTypeEnabled(EventType.FEATURE_USED))
            assertFalse(config.isTypeEnabled(EventType.ERROR))
            assertTrue(config.isTypeEnabled(EventType.PERFORMANCE))
            assertFalse(config.isTypeEnabled(EventType.SESSION))
        }

        @Test
        @DisplayName("enableAll enables all options")
        fun enableAllEnablesAllOptions() {
            val config = TelemetryConfig().enableAll()

            assertTrue(config.enabled)
            assertTrue(config.anonymousUsage)
            assertTrue(config.crashReporting)
            assertTrue(config.featureTracking)
            assertTrue(config.performanceMetrics)
        }

        @Test
        @DisplayName("crashesOnly enables only crash reporting")
        fun crashesOnlyEnablesOnlyCrashReporting() {
            val config = TelemetryConfig().crashesOnly()

            assertTrue(config.enabled)
            assertTrue(config.crashReporting)
            assertFalse(config.featureTracking)
            assertFalse(config.performanceMetrics)
            assertFalse(config.anonymousUsage)
        }

        @Test
        @DisplayName("describe produces readable output")
        fun describeProducesReadableOutput() {
            val disabled = TelemetryConfig()
            assertTrue(disabled.describe().contains("disabled"))

            val enabled = TelemetryConfig(enabled = true, crashReporting = true)
            assertTrue(enabled.describe().contains("crashes"))
        }

        @Test
        @DisplayName("DISABLED constant is disabled")
        fun disabledConstantIsDisabled() {
            assertFalse(TelemetryConfig.DISABLED.enabled)
        }

        @Test
        @DisplayName("FULL constant is fully enabled")
        fun fullConstantIsFullyEnabled() {
            val full = TelemetryConfig.FULL
            assertTrue(full.enabled)
            assertTrue(full.isTypeEnabled(EventType.FEATURE_USED))
            assertTrue(full.isTypeEnabled(EventType.ERROR))
        }
    }

    // =========================================================================
    // EventType Tests
    // =========================================================================

    @Nested
    @DisplayName("EventType")
    inner class EventTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            EventType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("all types have categories")
        fun allTypesHaveCategories() {
            EventType.entries.forEach { type ->
                assertTrue(type.category.isNotBlank())
            }
        }

        @Test
        @DisplayName("byCategory filters correctly")
        fun byCategoryFiltersCorrectly() {
            val errors = EventType.byCategory("errors")
            assertEquals(1, errors.size)
            assertEquals(EventType.ERROR, errors[0])
        }
    }

    // =========================================================================
    // TelemetryEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("TelemetryEvent")
    inner class TelemetryEventTests {

        @Test
        @DisplayName("feature factory creates correct event")
        fun featureFactoryCreatesCorrectEvent() {
            val event = TelemetryEvent.feature(
                name = "chat_sent",
                sessionId = "session-123",
                properties = mapOf("model" to "gpt-4")
            )

            assertEquals(EventType.FEATURE_USED, event.type)
            assertEquals("chat_sent", event.name)
            assertEquals("gpt-4", event.getProperty("model"))
        }

        @Test
        @DisplayName("performance factory creates correct event")
        fun performanceFactoryCreatesCorrectEvent() {
            val event = TelemetryEvent.performance(
                operation = "code_generation",
                sessionId = "session-123",
                durationMs = 1500,
                success = true
            )

            assertEquals(EventType.PERFORMANCE, event.type)
            assertEquals("code_generation", event.name)
            assertEquals(1500.0, event.getMetric("duration_ms"))
            assertEquals("true", event.getProperty("success"))
        }

        @Test
        @DisplayName("error factory creates correct event")
        fun errorFactoryCreatesCorrectEvent() {
            val event = TelemetryEvent.error(
                errorType = "NullPointerException",
                sessionId = "session-123",
                message = "Something was null",
                context = "processRequest"
            )

            assertEquals(EventType.ERROR, event.type)
            assertEquals("NullPointerException", event.name)
            assertTrue(event.hasProperty("message"))
        }

        @Test
        @DisplayName("session factory creates correct event")
        fun sessionFactoryCreatesCorrectEvent() {
            val event = TelemetryEvent.session(
                action = "end",
                sessionId = "session-123",
                durationMs = 60000
            )

            assertEquals(EventType.SESSION, event.type)
            assertEquals("end", event.name)
            assertEquals(60000.0, event.getMetric("duration_ms"))
        }

        @Test
        @DisplayName("getProperty returns default for missing key")
        fun getPropertyReturnsDefaultForMissingKey() {
            val event = TelemetryEvent.feature("test", "session", emptyMap())

            assertEquals("default", event.getProperty("missing", "default"))
        }

        @Test
        @DisplayName("format produces readable output")
        fun formatProducesReadableOutput() {
            val event = TelemetryEvent.feature("chat_sent", "session")
            val formatted = event.format()

            assertTrue(formatted.contains("Feature Used"))
            assertTrue(formatted.contains("chat_sent"))
        }

        @Test
        @DisplayName("each event has unique ID")
        fun eachEventHasUniqueId() {
            val event1 = TelemetryEvent.feature("test", "session")
            val event2 = TelemetryEvent.feature("test", "session")

            assertNotEquals(event1.id, event2.id)
        }
    }

    // =========================================================================
    // CrashContext Tests
    // =========================================================================

    @Nested
    @DisplayName("CrashContext")
    inner class CrashContextTests {

        @Test
        @DisplayName("capture creates context with system properties")
        fun captureCreatesContextWithSystemProperties() {
            val context = CrashContext.capture("1.0.0", "test_action")

            assertEquals("1.0.0", context.pluginVersion)
            assertEquals("test_action", context.lastAction)
            assertTrue(context.osName.isNotBlank())
            assertTrue(context.javaVersion.isNotBlank())
        }

        @Test
        @DisplayName("format produces readable output")
        fun formatProducesReadableOutput() {
            val context = CrashContext(
                pluginVersion = "1.0.0",
                ideVersion = "2024.1",
                osName = "macOS",
                javaVersion = "17",
                lastAction = "chat_sent"
            )

            val formatted = context.format()
            assertTrue(formatted.contains("Plugin: 1.0.0"))
            assertTrue(formatted.contains("Last action: chat_sent"))
        }

        @Test
        @DisplayName("EMPTY has placeholder values")
        fun emptyHasPlaceholderValues() {
            val empty = CrashContext.EMPTY
            assertEquals("0.0.0", empty.pluginVersion)
            assertNull(empty.lastAction)
        }
    }

    // =========================================================================
    // CrashReport Tests
    // =========================================================================

    @Nested
    @DisplayName("CrashReport")
    inner class CrashReportTests {

        @Test
        @DisplayName("fromThrowable creates anonymized report")
        fun fromThrowableCreatesAnonymizedReport() {
            val exception = RuntimeException("Test error")
            val context = CrashContext.EMPTY
            val report = CrashReport.fromThrowable(exception, context)

            assertEquals("RuntimeException", report.exception)
            assertEquals("Test error", report.message)
            assertTrue(report.stackTrace.contains("(source)"))
        }

        @Test
        @DisplayName("topFrame returns first stack line")
        fun topFrameReturnsFirstStackLine() {
            val report = CrashReport(
                id = "1",
                exception = "Test",
                message = "error",
                stackTrace = "at Something(source)\nat Other(source)",
                context = CrashContext.EMPTY,
                timestamp = Instant.now()
            )

            assertTrue(report.topFrame.contains("Something"))
        }

        @Test
        @DisplayName("format produces readable report")
        fun formatProducesReadableReport() {
            val exception = RuntimeException("Test")
            val report = CrashReport.fromThrowable(exception, CrashContext.EMPTY)
            val formatted = report.format()

            assertTrue(formatted.contains("Crash Report"))
            assertTrue(formatted.contains("RuntimeException"))
        }
    }

    // =========================================================================
    // UsageStats Tests
    // =========================================================================

    @Nested
    @DisplayName("UsageStats")
    inner class UsageStatsTests {

        @Test
        @DisplayName("getFeatureCount returns 0 for unknown feature")
        fun getFeatureCountReturnsZeroForUnknownFeature() {
            val stats = UsageStats.EMPTY
            assertEquals(0, stats.getFeatureCount("unknown"))
        }

        @Test
        @DisplayName("topFeatures returns sorted list")
        fun topFeaturesReturnsSortedList() {
            val stats = UsageStats(
                featuresUsed = mapOf("a" to 10, "b" to 5, "c" to 15),
                averageSessionLength = 0,
                totalSessions = 0,
                providerUsage = emptyMap(),
                errorCount = 0
            )

            val top = stats.topFeatures(2)
            assertEquals(2, top.size)
            assertEquals("c", top[0].first)
            assertEquals(15, top[0].second)
        }

        @Test
        @DisplayName("totalFeatureUsage sums all counts")
        fun totalFeatureUsageSumsAllCounts() {
            val stats = UsageStats(
                featuresUsed = mapOf("a" to 10, "b" to 5, "c" to 15),
                averageSessionLength = 0,
                totalSessions = 0,
                providerUsage = emptyMap(),
                errorCount = 0
            )

            assertEquals(30, stats.totalFeatureUsage)
        }

        @Test
        @DisplayName("averageSessionDuration converts from millis")
        fun averageSessionDurationConvertsFromMillis() {
            val stats = UsageStats(
                featuresUsed = emptyMap(),
                averageSessionLength = 60000, // 1 minute
                totalSessions = 1,
                providerUsage = emptyMap(),
                errorCount = 0
            )

            assertEquals(Duration.ofMinutes(1), stats.averageSessionDuration)
        }

        @Test
        @DisplayName("fromEvents aggregates correctly")
        fun fromEventsAggregatesCorrectly() {
            val events = listOf(
                TelemetryEvent.feature("chat", "s1"),
                TelemetryEvent.feature("chat", "s1"),
                TelemetryEvent.feature("generate", "s1"),
                TelemetryEvent.error("Error", "s1")
            )

            val stats = UsageStats.fromEvents(events)

            assertEquals(2, stats.getFeatureCount("chat"))
            assertEquals(1, stats.getFeatureCount("generate"))
            assertEquals(1, stats.errorCount)
        }

        @Test
        @DisplayName("EMPTY has zero values")
        fun emptyHasZeroValues() {
            val empty = UsageStats.EMPTY

            assertTrue(empty.featuresUsed.isEmpty())
            assertEquals(0, empty.totalSessions)
            assertEquals(0, empty.errorCount)
        }
    }
}
