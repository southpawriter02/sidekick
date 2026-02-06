package com.sidekick.telemetry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for TelemetryService.
 *
 * Note: Tests for getInstance() are skipped as they require
 * IntelliJ Platform test infrastructure. Core logic is tested here.
 */
@DisplayName("TelemetryService Tests")
class TelemetryServiceTest {

    private lateinit var service: TelemetryService

    @BeforeEach
    fun setup() {
        service = TelemetryService()
        service.reset()
    }

    // =========================================================================
    // Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Configuration")
    inner class ConfigurationTests {

        @Test
        @DisplayName("disabled by default")
        fun disabledByDefault() {
            assertFalse(service.isEnabled())
        }

        @Test
        @DisplayName("updateConfig changes settings")
        fun updateConfigChangesSettings() {
            val config = TelemetryConfig(enabled = true)
            service.updateConfig(config)

            assertTrue(service.isEnabled())
            assertEquals(config, service.getConfig())
        }

        @Test
        @DisplayName("getInstallId returns stable ID")
        fun getInstallIdReturnsStableId() {
            val id1 = service.getInstallId()
            val id2 = service.getInstallId()

            assertEquals(id1, id2)
            assertTrue(id1.isNotBlank())
        }
    }

    // =========================================================================
    // Session Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Session Management")
    inner class SessionManagementTests {

        @Test
        @DisplayName("startSession creates new session ID")
        fun startSessionCreatesNewSessionId() {
            val oldSession = service.getSessionId()
            service.startSession()
            val newSession = service.getSessionId()

            assertNotEquals(oldSession, newSession)
        }

        @Test
        @DisplayName("getSessionDuration returns positive value")
        fun getSessionDurationReturnsPositiveValue() {
            Thread.sleep(10)
            val duration = service.getSessionDuration()

            assertTrue(duration.toMillis() >= 10)
        }

        @Test
        @DisplayName("endSession queues event when enabled")
        fun endSessionQueuesEventWhenEnabled() {
            service.updateConfig(TelemetryConfig(enabled = true, anonymousUsage = true))
            service.clearQueue()
            service.startSession()

            service.endSession()

            val events = service.getQueuedEvents()
            assertTrue(events.any { it.name == "start" })
        }
    }

    // =========================================================================
    // Feature Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("Feature Tracking")
    inner class FeatureTrackingTests {

        @Test
        @DisplayName("trackFeature does nothing when disabled")
        fun trackFeatureDoesNothingWhenDisabled() {
            service.clearQueue()

            service.trackFeature("test_feature")

            assertEquals(0, service.getQueueSize())
        }

        @Test
        @DisplayName("trackFeature queues event when enabled")
        fun trackFeatureQueuesEventWhenEnabled() {
            service.updateConfig(TelemetryConfig(enabled = true, featureTracking = true))
            service.clearQueue()

            service.trackFeature("chat_sent", mapOf("model" to "gpt-4"))

            val events = service.getQueuedEvents()
            assertEquals(1, events.size)
            assertEquals("chat_sent", events[0].name)
            assertEquals(EventType.FEATURE_USED, events[0].type)
        }

        @Test
        @DisplayName("trackFeature respects featureTracking toggle")
        fun trackFeatureRespectsFeatureTrackingToggle() {
            service.updateConfig(TelemetryConfig(enabled = true, featureTracking = false))
            service.clearQueue()

            service.trackFeature("test")

            assertEquals(0, service.getQueueSize())
        }
    }

    // =========================================================================
    // Error Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Tracking")
    inner class ErrorTrackingTests {

        @Test
        @DisplayName("trackError does nothing when disabled")
        fun trackErrorDoesNothingWhenDisabled() {
            service.clearQueue()

            service.trackError(RuntimeException("test"))

            assertEquals(0, service.getQueueSize())
        }

        @Test
        @DisplayName("trackError queues event when enabled")
        fun trackErrorQueuesEventWhenEnabled() {
            service.updateConfig(TelemetryConfig(enabled = true, crashReporting = true))
            service.clearQueue()

            service.trackError(RuntimeException("test error"), "test_context")

            val events = service.getQueuedEvents()
            assertEquals(1, events.size)
            assertEquals("RuntimeException", events[0].name)
            assertEquals(EventType.ERROR, events[0].type)
        }

        @Test
        @DisplayName("trackError increments session error count")
        fun trackErrorIncrementsSessionErrorCount() {
            service.updateConfig(TelemetryConfig(enabled = true, crashReporting = true))
            assertEquals(0, service.getSessionErrorCount())

            service.trackError(RuntimeException("1"))
            service.trackError(RuntimeException("2"))

            assertEquals(2, service.getSessionErrorCount())
        }

        @Test
        @DisplayName("createCrashReport generates anonymized report")
        fun createCrashReportGeneratesAnonymizedReport() {
            val exception = RuntimeException("test")
            val report = service.createCrashReport(exception)

            assertEquals("RuntimeException", report.exception)
            assertTrue(report.stackTrace.contains("(source)"))
        }
    }

    // =========================================================================
    // Performance Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("Performance Tracking")
    inner class PerformanceTrackingTests {

        @Test
        @DisplayName("trackPerformance does nothing when disabled")
        fun trackPerformanceDoesNothingWhenDisabled() {
            service.clearQueue()

            service.trackPerformance("operation", 100, true)

            assertEquals(0, service.getQueueSize())
        }

        @Test
        @DisplayName("trackPerformance queues event when enabled")
        fun trackPerformanceQueuesEventWhenEnabled() {
            service.updateConfig(TelemetryConfig(enabled = true, performanceMetrics = true))
            service.clearQueue()

            service.trackPerformance("code_generation", 1500, true)

            val events = service.getQueuedEvents()
            assertEquals(1, events.size)
            assertEquals("code_generation", events[0].name)
            assertEquals(EventType.PERFORMANCE, events[0].type)
            assertEquals(1500.0, events[0].getMetric("duration_ms"))
        }

        @Test
        @DisplayName("trackTimed measures duration")
        fun trackTimedMeasuresDuration() {
            service.updateConfig(TelemetryConfig(enabled = true, performanceMetrics = true))
            service.clearQueue()

            val result = service.trackTimed("test_op") {
                Thread.sleep(50)
                "result"
            }

            assertEquals("result", result)
            val events = service.getQueuedEvents()
            assertTrue(events.any { it.getMetric("duration_ms") >= 50 })
        }

        @Test
        @DisplayName("trackTimed records error on exception")
        fun trackTimedRecordsErrorOnException() {
            service.updateConfig(TelemetryConfig(enabled = true, performanceMetrics = true, crashReporting = true))
            service.clearQueue()

            try {
                service.trackTimed("failing_op") {
                    throw RuntimeException("fail")
                }
            } catch (e: RuntimeException) {
                // Expected
            }

            val events = service.getQueuedEvents()
            assertTrue(events.any { it.type == EventType.ERROR })
            assertTrue(events.any { it.type == EventType.PERFORMANCE && it.getProperty("success") == "false" })
        }
    }

    // =========================================================================
    // Event Queue Tests
    // =========================================================================

    @Nested
    @DisplayName("Event Queue")
    inner class EventQueueTests {

        @Test
        @DisplayName("getQueuedEvents returns all events")
        fun getQueuedEventsReturnsAllEvents() {
            service.updateConfig(TelemetryConfig.FULL)
            service.clearQueue()

            service.trackFeature("a")
            service.trackFeature("b")
            service.trackFeature("c")

            assertEquals(3, service.getQueueSize())
        }

        @Test
        @DisplayName("clearQueue removes all events")
        fun clearQueueRemovesAllEvents() {
            service.updateConfig(TelemetryConfig.FULL)
            service.trackFeature("test")

            service.clearQueue()

            assertEquals(0, service.getQueueSize())
        }
    }

    // =========================================================================
    // Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Statistics")
    inner class StatisticsTests {

        @Test
        @DisplayName("getUsageStats aggregates events")
        fun getUsageStatsAggregatesEvents() {
            service.updateConfig(TelemetryConfig.FULL)
            service.clearQueue()

            service.trackFeature("chat")
            service.trackFeature("chat")
            service.trackFeature("generate")
            service.trackError(RuntimeException("test"))

            val stats = service.getUsageStats()
            assertEquals(2, stats.getFeatureCount("chat"))
            assertEquals(1, stats.getFeatureCount("generate"))
            assertEquals(1, stats.errorCount)
        }
    }

    // =========================================================================
    // Reporting Tests
    // =========================================================================

    @Nested
    @DisplayName("Reporting")
    inner class ReportingTests {

        @Test
        @DisplayName("getStatusReport includes configuration")
        fun getStatusReportIncludesConfiguration() {
            val report = service.getStatusReport()

            assertTrue(report.contains("Telemetry Status"))
            assertTrue(report.contains("Configuration"))
        }

        @Test
        @DisplayName("getStatusReport includes session info")
        fun getStatusReportIncludesSessionInfo() {
            val report = service.getStatusReport()

            assertTrue(report.contains("Session"))
            assertTrue(report.contains("ID"))
        }

        @Test
        @DisplayName("getStatusReport includes queue size")
        fun getStatusReportIncludesQueueSize() {
            service.updateConfig(TelemetryConfig.FULL)
            service.clearQueue()
            service.trackFeature("test")

            val report = service.getStatusReport()

            assertTrue(report.contains("Events: 1"))
        }
    }

    // =========================================================================
    // Reset Tests
    // =========================================================================

    @Nested
    @DisplayName("Reset")
    inner class ResetTests {

        @Test
        @DisplayName("reset clears all state")
        fun resetClearsAllState() {
            service.updateConfig(TelemetryConfig.FULL)
            service.trackFeature("test")
            service.trackError(RuntimeException("test"))

            service.reset()

            assertEquals(0, service.getQueueSize())
            assertEquals(0, service.getSessionErrorCount())
        }

        @Test
        @DisplayName("reset creates new session ID")
        fun resetCreatesNewSessionId() {
            val oldSession = service.getSessionId()

            service.reset()

            assertNotEquals(oldSession, service.getSessionId())
        }
    }
}
