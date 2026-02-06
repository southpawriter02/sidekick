package com.sidekick.performance

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import com.intellij.openapi.project.Project
import java.time.Duration
import java.time.Instant

/**
 * Unit tests for PerformanceMonitor.
 *
 * Note: Tests use MockK to create a mock Project since we don't have
 * the full IntelliJ Platform test infrastructure.
 */
@DisplayName("PerformanceMonitor Tests")
class PerformanceMonitorTest {

    private lateinit var mockProject: Project
    private lateinit var monitor: PerformanceMonitor

    @BeforeEach
    fun setup() {
        mockProject = mockk(relaxed = true)
        monitor = PerformanceMonitor(mockProject)
    }

    // =========================================================================
    // Phase Timing Tests
    // =========================================================================

    @Nested
    @DisplayName("Phase Timing")
    inner class PhaseTimingTests {

        @Test
        @DisplayName("startPhase records timing")
        fun startPhaseRecordsTiming() {
            monitor.startPhase(StartupPhase.PLUGIN_LOAD)

            val timings = monitor.getAllTimings()
            assertEquals(1, timings.size)
            assertEquals(StartupPhase.PLUGIN_LOAD, timings[0].phase)
            assertNull(timings[0].endTime)
        }

        @Test
        @DisplayName("endPhase completes timing")
        fun endPhaseCompletesTiming() {
            monitor.startPhase(StartupPhase.PLUGIN_LOAD)
            Thread.sleep(10) // Small delay to ensure measurable duration
            monitor.endPhase(StartupPhase.PLUGIN_LOAD)

            val timings = monitor.getAllTimings()
            assertEquals(1, timings.size)
            assertNotNull(timings[0].endTime)
            assertTrue(timings[0].duration!!.toMillis() >= 10)
        }

        @Test
        @DisplayName("multiple phases can be tracked")
        fun multiplePhasesCanBeTracked() {
            monitor.startPhase(StartupPhase.PLUGIN_LOAD)
            monitor.endPhase(StartupPhase.PLUGIN_LOAD)

            monitor.startPhase(StartupPhase.SERVICE_INIT)
            monitor.endPhase(StartupPhase.SERVICE_INIT)

            monitor.startPhase(StartupPhase.UI_INIT)
            monitor.endPhase(StartupPhase.UI_INIT)

            val timings = monitor.getAllTimings()
            assertEquals(3, timings.size)
            assertTrue(timings.all { it.isComplete })
        }

        @Test
        @DisplayName("recordPhase adds complete timing directly")
        fun recordPhaseAddsCompleteTimingDirectly() {
            monitor.recordPhase(StartupPhase.INDEX_LOAD, Duration.ofMillis(250))

            val timing = monitor.getPhaseTimning(StartupPhase.INDEX_LOAD)
            assertNotNull(timing)
            assertEquals(250, timing!!.duration!!.toMillis())
        }

        @Test
        @DisplayName("getPhaseTimning returns null for unrecorded phase")
        fun getPhaseTimningReturnsNullForUnrecordedPhase() {
            assertNull(monitor.getPhaseTimning(StartupPhase.CONNECTION_CHECK))
        }
    }

    // =========================================================================
    // Response Time Tests
    // =========================================================================

    @Nested
    @DisplayName("Response Time Tracking")
    inner class ResponseTimeTests {

        @Test
        @DisplayName("getAverageResponseTime returns zero when empty")
        fun getAverageResponseTimeReturnsZeroWhenEmpty() {
            assertEquals(Duration.ZERO, monitor.getAverageResponseTime())
        }

        @Test
        @DisplayName("recordResponseTime accumulates values")
        fun recordResponseTimeAccumulatesValues() {
            monitor.recordResponseTime(Duration.ofMillis(100))
            monitor.recordResponseTime(Duration.ofMillis(200))
            monitor.recordResponseTime(Duration.ofMillis(300))

            val average = monitor.getAverageResponseTime()
            assertEquals(200, average.toMillis())
        }

        @Test
        @DisplayName("average handles single value")
        fun averageHandlesSingleValue() {
            monitor.recordResponseTime(Duration.ofMillis(150))

            assertEquals(150, monitor.getAverageResponseTime().toMillis())
        }
    }

    // =========================================================================
    // Cache Stats Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Statistics")
    inner class CacheStatsTests {

        @Test
        @DisplayName("registerCache stores stats")
        fun registerCacheStoresStats() {
            val stats = CacheStats("test-cache", 50, 100, 900, 100, 10)
            monitor.registerCache("test-cache", stats)

            val retrieved = monitor.getCacheStats("test-cache")
            assertEquals(stats, retrieved)
        }

        @Test
        @DisplayName("getCacheStats returns null for unknown cache")
        fun getCacheStatsReturnsNullForUnknownCache() {
            assertNull(monitor.getCacheStats("nonexistent"))
        }

        @Test
        @DisplayName("getAllCacheStats returns all registered caches")
        fun getAllCacheStatsReturnsAllRegisteredCaches() {
            monitor.registerCache("cache1", CacheStats.empty("cache1"))
            monitor.registerCache("cache2", CacheStats.empty("cache2"))
            monitor.registerCache("cache3", CacheStats.empty("cache3"))

            val all = monitor.getAllCacheStats()
            assertEquals(3, all.size)
            assertTrue(all.containsKey("cache1"))
            assertTrue(all.containsKey("cache2"))
            assertTrue(all.containsKey("cache3"))
        }

        @Test
        @DisplayName("calculateAverageCacheHitRate returns 0 when no caches")
        fun calculateAverageCacheHitRateReturnsZeroWhenNoCaches() {
            assertEquals(0f, monitor.calculateAverageCacheHitRate())
        }

        @Test
        @DisplayName("calculateAverageCacheHitRate averages all caches")
        fun calculateAverageCacheHitRateAveragesAllCaches() {
            // Cache with 80% hit rate
            monitor.registerCache("cache1", CacheStats("cache1", 0, 100, 80, 20, 0))
            // Cache with 90% hit rate
            monitor.registerCache("cache2", CacheStats("cache2", 0, 100, 90, 10, 0))

            val average = monitor.calculateAverageCacheHitRate()
            assertEquals(0.85f, average, 0.001f)
        }
    }

    // =========================================================================
    // Metrics Capture Tests
    // =========================================================================

    @Nested
    @DisplayName("Metrics Capture")
    inner class MetricsCaptureTests {

        @Test
        @DisplayName("captureMetrics returns non-null metrics")
        fun captureMetricsReturnsNonNullMetrics() {
            val metrics = monitor.captureMetrics()
            assertNotNull(metrics)
        }

        @Test
        @DisplayName("captureMetrics includes memory usage")
        fun captureMetricsIncludesMemoryUsage() {
            val metrics = monitor.captureMetrics()

            assertTrue(metrics.memoryUsage.heapMax > 0)
        }

        @Test
        @DisplayName("captureMetrics includes startup time")
        fun captureMetricsIncludesStartupTime() {
            monitor.startPhase(StartupPhase.PLUGIN_LOAD)
            Thread.sleep(10)
            monitor.endPhase(StartupPhase.PLUGIN_LOAD)

            val metrics = monitor.captureMetrics()
            assertTrue(metrics.startupTime.toMillis() >= 10)
        }

        @Test
        @DisplayName("captureMetrics updates lastSnapshot")
        fun captureMetricsUpdatesLastSnapshot() {
            assertNull(monitor.getLastSnapshot())

            val metrics = monitor.captureMetrics()

            assertEquals(metrics, monitor.getLastSnapshot())
        }
    }

    // =========================================================================
    // Startup Report Tests
    // =========================================================================

    @Nested
    @DisplayName("Startup Report")
    inner class StartupReportTests {

        @Test
        @DisplayName("getStartupReport includes header")
        fun getStartupReportIncludesHeader() {
            val report = monitor.getStartupReport()
            assertTrue(report.contains("Sidekick Startup Report"))
        }

        @Test
        @DisplayName("getStartupReport includes total time")
        fun getStartupReportIncludesTotalTime() {
            val report = monitor.getStartupReport()
            assertTrue(report.contains("Total Startup Time"))
        }

        @Test
        @DisplayName("getStartupReport includes memory info")
        fun getStartupReportIncludesMemoryInfo() {
            val report = monitor.getStartupReport()
            assertTrue(report.contains("Memory"))
        }

        @Test
        @DisplayName("getStartupReport includes phase timings")
        fun getStartupReportIncludesPhaseTimings() {
            monitor.startPhase(StartupPhase.PLUGIN_LOAD)
            monitor.endPhase(StartupPhase.PLUGIN_LOAD)

            val report = monitor.getStartupReport()
            assertTrue(report.contains("Plugin Load"))
        }

        @Test
        @DisplayName("getStartupReport includes cache stats when registered")
        fun getStartupReportIncludesCacheStatsWhenRegistered() {
            monitor.registerCache("prompt-cache", CacheStats.empty("prompt-cache"))

            val report = monitor.getStartupReport()
            assertTrue(report.contains("Cache Statistics"))
            assertTrue(report.contains("prompt-cache"))
        }

        @Test
        @DisplayName("getStatusLine produces compact output")
        fun getStatusLineProducesCompactOutput() {
            val status = monitor.getStatusLine()

            assertTrue(status.contains("Startup"))
            assertTrue(status.contains("Heap"))
            assertTrue(status.contains("Cache"))
            // Should be a single line
            assertFalse(status.contains("\n"))
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
            monitor.startPhase(StartupPhase.PLUGIN_LOAD)
            monitor.endPhase(StartupPhase.PLUGIN_LOAD)
            monitor.registerCache("cache", CacheStats.empty("cache"))
            monitor.recordResponseTime(Duration.ofMillis(100))
            monitor.captureMetrics()

            monitor.reset()

            assertTrue(monitor.getAllTimings().isEmpty())
            assertTrue(monitor.getAllCacheStats().isEmpty())
            assertEquals(Duration.ZERO, monitor.getAverageResponseTime())
            assertNull(monitor.getLastSnapshot())
        }
    }
}
