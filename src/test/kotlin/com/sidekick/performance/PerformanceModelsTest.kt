package com.sidekick.performance

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Duration
import java.time.Instant

/**
 * Comprehensive unit tests for Performance Models.
 */
@DisplayName("Performance Models Tests")
class PerformanceModelsTest {

    // =========================================================================
    // PerformanceMetrics Tests
    // =========================================================================

    @Nested
    @DisplayName("PerformanceMetrics")
    inner class PerformanceMetricsTests {

        @Test
        @DisplayName("creation with all required fields")
        fun creationWithAllRequiredFields() {
            val metrics = PerformanceMetrics(
                startupTime = Duration.ofMillis(500),
                indexingTime = Duration.ofMillis(200),
                averageResponseTime = Duration.ofMillis(150),
                memoryUsage = MemoryUsage.ZERO,
                cacheHitRate = 0.85f,
                activeConnections = 3
            )

            assertEquals(500, metrics.startupTime.toMillis())
            assertEquals(200, metrics.indexingTime.toMillis())
            assertEquals(0.85f, metrics.cacheHitRate)
            assertEquals(3, metrics.activeConnections)
        }

        @Test
        @DisplayName("isStartupAcceptable returns true for fast startup")
        fun isStartupAcceptableReturnsTrueForFastStartup() {
            val fastMetrics = createMetrics(startupMs = 1000)
            assertTrue(fastMetrics.isStartupAcceptable)
        }

        @Test
        @DisplayName("isStartupAcceptable returns false for slow startup")
        fun isStartupAcceptableReturnsFalseForSlowStartup() {
            val slowMetrics = createMetrics(startupMs = 5000)
            assertFalse(slowMetrics.isStartupAcceptable)
        }

        @Test
        @DisplayName("isStartupAcceptable boundary at 3000ms")
        fun isStartupAcceptableBoundaryAt3000ms() {
            val atBoundary = createMetrics(startupMs = 3000)
            assertFalse(atBoundary.isStartupAcceptable)

            val justUnder = createMetrics(startupMs = 2999)
            assertTrue(justUnder.isStartupAcceptable)
        }

        @Test
        @DisplayName("isMemoryHealthy returns true for low usage")
        fun isMemoryHealthyReturnsTrueForLowUsage() {
            val lowUsage = createMetrics(heapPercentage = 0.5f)
            assertTrue(lowUsage.isMemoryHealthy)
        }

        @Test
        @DisplayName("isMemoryHealthy returns false for high usage")
        fun isMemoryHealthyReturnsFalseForHighUsage() {
            val highUsage = createMetrics(heapPercentage = 0.9f)
            assertFalse(highUsage.isMemoryHealthy)
        }

        private fun createMetrics(
            startupMs: Long = 500,
            heapPercentage: Float = 0.5f
        ): PerformanceMetrics {
            val heapMax = 1000L
            val heapUsed = (heapMax * heapPercentage).toLong()
            return PerformanceMetrics(
                startupTime = Duration.ofMillis(startupMs),
                indexingTime = Duration.ZERO,
                averageResponseTime = Duration.ZERO,
                memoryUsage = MemoryUsage(heapUsed, heapMax, 0, 0, Duration.ZERO),
                cacheHitRate = 0f,
                activeConnections = 0
            )
        }
    }

    // =========================================================================
    // MemoryUsage Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryUsage")
    inner class MemoryUsageTests {

        @Test
        @DisplayName("heapPercentage calculates correctly")
        fun heapPercentageCalculatesCorrectly() {
            val usage = MemoryUsage(
                heapUsed = 512,
                heapMax = 1024,
                nonHeapUsed = 100,
                gcCount = 5,
                gcTime = Duration.ofMillis(50)
            )

            assertEquals(0.5f, usage.heapPercentage, 0.001f)
        }

        @Test
        @DisplayName("heapPercentage returns 0 when heapMax is 0")
        fun heapPercentageReturnsZeroWhenHeapMaxIsZero() {
            val usage = MemoryUsage(
                heapUsed = 100,
                heapMax = 0,
                nonHeapUsed = 0,
                gcCount = 0,
                gcTime = Duration.ZERO
            )

            assertEquals(0f, usage.heapPercentage)
        }

        @Test
        @DisplayName("totalUsed sums heap and non-heap")
        fun totalUsedSumsHeapAndNonHeap() {
            val usage = MemoryUsage(
                heapUsed = 500,
                heapMax = 1000,
                nonHeapUsed = 200,
                gcCount = 0,
                gcTime = Duration.ZERO
            )

            assertEquals(700, usage.totalUsed)
        }

        @Test
        @DisplayName("formatHeapUsage produces readable string")
        fun formatHeapUsageProducesReadableString() {
            val usage = MemoryUsage(
                heapUsed = 512 * 1024 * 1024, // 512MB
                heapMax = 2048 * 1024 * 1024, // 2048MB
                nonHeapUsed = 0,
                gcCount = 0,
                gcTime = Duration.ZERO
            )

            val formatted = usage.formatHeapUsage()
            assertTrue(formatted.contains("512MB"))
            assertTrue(formatted.contains("2048MB"))
            assertTrue(formatted.contains("25%"))
        }

        @Test
        @DisplayName("ZERO constant has all zero values")
        fun zeroConstantHasAllZeroValues() {
            val zero = MemoryUsage.ZERO

            assertEquals(0, zero.heapUsed)
            assertEquals(0, zero.heapMax)
            assertEquals(0, zero.nonHeapUsed)
            assertEquals(0, zero.gcCount)
            assertEquals(Duration.ZERO, zero.gcTime)
        }

        @Test
        @DisplayName("capture creates non-null instance")
        fun captureCreatesNonNullInstance() {
            val captured = MemoryUsage.capture()

            assertNotNull(captured)
            assertTrue(captured.heapMax > 0)
        }
    }

    // =========================================================================
    // StartupPhase Tests
    // =========================================================================

    @Nested
    @DisplayName("StartupPhase")
    inner class StartupPhaseTests {

        @Test
        @DisplayName("all phases have display names")
        fun allPhasesHaveDisplayNames() {
            StartupPhase.entries.forEach { phase ->
                assertTrue(phase.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("phases have expected display names")
        fun phasesHaveExpectedDisplayNames() {
            assertEquals("Plugin Load", StartupPhase.PLUGIN_LOAD.displayName)
            assertEquals("Service Initialization", StartupPhase.SERVICE_INIT.displayName)
            assertEquals("Connection Check", StartupPhase.CONNECTION_CHECK.displayName)
            assertEquals("Index Load", StartupPhase.INDEX_LOAD.displayName)
            assertEquals("UI Initialization", StartupPhase.UI_INIT.displayName)
        }

        @Test
        @DisplayName("inOrder returns all phases in sequence")
        fun inOrderReturnsAllPhasesInSequence() {
            val ordered = StartupPhase.inOrder()

            assertEquals(5, ordered.size)
            assertEquals(StartupPhase.PLUGIN_LOAD, ordered[0])
            assertEquals(StartupPhase.UI_INIT, ordered.last())
        }
    }

    // =========================================================================
    // StartupTiming Tests
    // =========================================================================

    @Nested
    @DisplayName("StartupTiming")
    inner class StartupTimingTests {

        @Test
        @DisplayName("duration returns null when endTime is null")
        fun durationReturnsNullWhenEndTimeIsNull() {
            val timing = StartupTiming(
                phase = StartupPhase.PLUGIN_LOAD,
                startTime = Instant.now(),
                endTime = null
            )

            assertNull(timing.duration)
            assertEquals(-1, timing.durationMs)
        }

        @Test
        @DisplayName("duration calculates correctly when complete")
        fun durationCalculatesCorrectlyWhenComplete() {
            val start = Instant.now()
            val end = start.plusMillis(500)
            val timing = StartupTiming(
                phase = StartupPhase.PLUGIN_LOAD,
                startTime = start,
                endTime = end
            )

            assertEquals(500, timing.duration?.toMillis())
            assertEquals(500, timing.durationMs)
        }

        @Test
        @DisplayName("isComplete reflects endTime presence")
        fun isCompleteReflectsEndTimePresence() {
            val incomplete = StartupTiming(
                phase = StartupPhase.PLUGIN_LOAD,
                startTime = Instant.now(),
                endTime = null
            )
            assertFalse(incomplete.isComplete)

            val complete = incomplete.complete()
            assertTrue(complete.isComplete)
        }

        @Test
        @DisplayName("complete sets endTime to now")
        fun completeSetsEndTimeToNow() {
            val timing = StartupTiming(
                phase = StartupPhase.PLUGIN_LOAD,
                startTime = Instant.now().minusMillis(100)
            )

            val completed = timing.complete()

            assertNotNull(completed.endTime)
            assertTrue(completed.duration!!.toMillis() >= 100)
        }

        @Test
        @DisplayName("format produces readable string")
        fun formatProducesReadableString() {
            val start = Instant.now()
            val timing = StartupTiming(
                phase = StartupPhase.PLUGIN_LOAD,
                startTime = start,
                endTime = start.plusMillis(150)
            )

            val formatted = timing.format()
            assertTrue(formatted.contains("Plugin Load"))
            assertTrue(formatted.contains("150ms"))
        }

        @Test
        @DisplayName("format shows running for incomplete")
        fun formatShowsRunningForIncomplete() {
            val timing = StartupTiming(
                phase = StartupPhase.SERVICE_INIT,
                startTime = Instant.now()
            )

            val formatted = timing.format()
            assertTrue(formatted.contains("(running)"))
        }
    }

    // =========================================================================
    // CacheStats Tests
    // =========================================================================

    @Nested
    @DisplayName("CacheStats")
    inner class CacheStatsTests {

        @Test
        @DisplayName("hitRate calculates correctly")
        fun hitRateCalculatesCorrectly() {
            val stats = CacheStats(
                name = "test-cache",
                size = 50,
                maxSize = 100,
                hits = 900,
                misses = 100,
                evictions = 10
            )

            assertEquals(0.9f, stats.hitRate, 0.001f)
        }

        @Test
        @DisplayName("hitRate returns 0 when no lookups")
        fun hitRateReturnsZeroWhenNoLookups() {
            val stats = CacheStats(
                name = "test-cache",
                size = 0,
                maxSize = 100,
                hits = 0,
                misses = 0,
                evictions = 0
            )

            assertEquals(0f, stats.hitRate)
        }

        @Test
        @DisplayName("utilization calculates correctly")
        fun utilizationCalculatesCorrectly() {
            val stats = CacheStats(
                name = "test-cache",
                size = 75,
                maxSize = 100,
                hits = 0,
                misses = 0,
                evictions = 0
            )

            assertEquals(0.75f, stats.utilization, 0.001f)
        }

        @Test
        @DisplayName("utilization returns 0 when maxSize is 0")
        fun utilizationReturnsZeroWhenMaxSizeIsZero() {
            val stats = CacheStats(
                name = "test-cache",
                size = 10,
                maxSize = 0,
                hits = 0,
                misses = 0,
                evictions = 0
            )

            assertEquals(0f, stats.utilization)
        }

        @Test
        @DisplayName("totalLookups sums hits and misses")
        fun totalLookupsSumsHitsAndMisses() {
            val stats = CacheStats(
                name = "test-cache",
                size = 50,
                maxSize = 100,
                hits = 800,
                misses = 200,
                evictions = 0
            )

            assertEquals(1000, stats.totalLookups)
        }

        @Test
        @DisplayName("isNearCapacity detects high utilization")
        fun isNearCapacityDetectsHighUtilization() {
            val nearFull = CacheStats(
                name = "test",
                size = 95,
                maxSize = 100,
                hits = 0,
                misses = 0,
                evictions = 0
            )
            assertTrue(nearFull.isNearCapacity)

            val notFull = nearFull.copy(size = 50)
            assertFalse(notFull.isNearCapacity)
        }

        @Test
        @DisplayName("format produces readable string")
        fun formatProducesReadableString() {
            val stats = CacheStats(
                name = "prompt-cache",
                size = 150,
                maxSize = 200,
                hits = 1000,
                misses = 100,
                evictions = 50
            )

            val formatted = stats.format()
            assertTrue(formatted.contains("prompt-cache"))
            assertTrue(formatted.contains("150/200"))
            assertTrue(formatted.contains("90%"))
        }

        @Test
        @DisplayName("empty factory creates zeroed stats")
        fun emptyFactoryCreatesZeroedStats() {
            val empty = CacheStats.empty("new-cache", 500)

            assertEquals("new-cache", empty.name)
            assertEquals(0, empty.size)
            assertEquals(500, empty.maxSize)
            assertEquals(0, empty.hits)
            assertEquals(0, empty.misses)
            assertEquals(0, empty.evictions)
        }
    }

    // =========================================================================
    // PerformanceConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("PerformanceConfig")
    inner class PerformanceConfigTests {

        @Test
        @DisplayName("default values are sensible")
        fun defaultValuesAreSensible() {
            val config = PerformanceConfig()

            assertTrue(config.lazyInitialization)
            assertTrue(config.backgroundIndexing)
            assertTrue(config.cacheEnabled)
            assertEquals(1000, config.maxCacheSize)
            assertEquals(5, config.connectionPoolSize)
            assertEquals(Duration.ofSeconds(30), config.requestTimeout)
        }

        @Test
        @DisplayName("DEFAULT constant matches default constructor")
        fun defaultConstantMatchesDefaultConstructor() {
            assertEquals(PerformanceConfig(), PerformanceConfig.DEFAULT)
        }

        @Test
        @DisplayName("MINIMAL constant has reduced values")
        fun minimalConstantHasReducedValues() {
            val minimal = PerformanceConfig.MINIMAL

            assertFalse(minimal.lazyInitialization)
            assertFalse(minimal.backgroundIndexing)
            assertFalse(minimal.cacheEnabled)
            assertEquals(10, minimal.maxCacheSize)
            assertEquals(1, minimal.connectionPoolSize)
        }

        @Test
        @DisplayName("optimizeForSpeed increases resources")
        fun optimizeForSpeedIncreasesResources() {
            val config = PerformanceConfig().optimizeForSpeed()

            assertFalse(config.lazyInitialization)
            assertEquals(2000, config.maxCacheSize)
            assertEquals(10, config.connectionPoolSize)
        }

        @Test
        @DisplayName("optimizeForMemory reduces resources")
        fun optimizeForMemoryReducesResources() {
            val config = PerformanceConfig().optimizeForMemory()

            assertEquals(250, config.maxCacheSize)
            assertEquals(2, config.connectionPoolSize)
        }

        @Test
        @DisplayName("validate returns empty for valid config")
        fun validateReturnsEmptyForValidConfig() {
            val issues = PerformanceConfig.DEFAULT.validate()
            assertTrue(issues.isEmpty())
        }

        @Test
        @DisplayName("validate detects negative maxCacheSize")
        fun validateDetectsNegativeMaxCacheSize() {
            val config = PerformanceConfig(maxCacheSize = -1)
            val issues = config.validate()

            assertTrue(issues.any { it.contains("maxCacheSize") })
        }

        @Test
        @DisplayName("validate detects zero connectionPoolSize")
        fun validateDetectsZeroConnectionPoolSize() {
            val config = PerformanceConfig(connectionPoolSize = 0)
            val issues = config.validate()

            assertTrue(issues.any { it.contains("connectionPoolSize") })
        }

        @Test
        @DisplayName("validate detects negative timeout")
        fun validateDetectsNegativeTimeout() {
            val config = PerformanceConfig(requestTimeout = Duration.ofSeconds(-1))
            val issues = config.validate()

            assertTrue(issues.any { it.contains("requestTimeout") })
        }
    }
}
