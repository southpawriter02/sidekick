package com.sidekick.llm.provider

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for RateLimiter and RateLimitConfig.
 */
@DisplayName("Rate Limiter Tests")
class RateLimiterTest {

    private lateinit var limiter: RateLimiter

    @BeforeEach
    fun setup() {
        limiter = RateLimiter(RateLimitConfig.DEFAULT)
    }

    // =========================================================================
    // RateLimitConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("RateLimitConfig")
    inner class RateLimitConfigTests {

        @Test
        @DisplayName("DEFAULT has 60 requests per minute")
        fun defaultConfig() {
            val config = RateLimitConfig.DEFAULT
            assertEquals(60, config.maxRequestsPerMinute)
            assertEquals(60, config.windowSeconds)
            assertEquals(500, config.baseDelayMs)
            assertEquals(30_000, config.maxDelayMs)
            assertTrue(config.enabled)
        }

        @Test
        @DisplayName("DISABLED has enabled=false")
        fun disabledConfig() {
            val config = RateLimitConfig.DISABLED
            assertFalse(config.enabled)
        }

        @Test
        @DisplayName("CONSERVATIVE has 20 requests per minute")
        fun conservativeConfig() {
            val config = RateLimitConfig.CONSERVATIVE
            assertEquals(20, config.maxRequestsPerMinute)
            assertEquals(1000, config.baseDelayMs)
        }

        @Test
        @DisplayName("STRICT has 10 requests per minute")
        fun strictConfig() {
            val config = RateLimitConfig.STRICT
            assertEquals(10, config.maxRequestsPerMinute)
            assertEquals(2000, config.baseDelayMs)
        }

        @Test
        @DisplayName("rejects invalid maxRequestsPerMinute")
        fun rejectsInvalidMaxRequests() {
            assertThrows(IllegalArgumentException::class.java) {
                RateLimitConfig(maxRequestsPerMinute = 0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                RateLimitConfig(maxRequestsPerMinute = -1)
            }
        }

        @Test
        @DisplayName("rejects invalid windowSeconds")
        fun rejectsInvalidWindowSeconds() {
            assertThrows(IllegalArgumentException::class.java) {
                RateLimitConfig(windowSeconds = 0)
            }
        }

        @Test
        @DisplayName("rejects invalid baseDelayMs")
        fun rejectsInvalidBaseDelay() {
            assertThrows(IllegalArgumentException::class.java) {
                RateLimitConfig(baseDelayMs = 0)
            }
        }

        @Test
        @DisplayName("rejects maxDelayMs < baseDelayMs")
        fun rejectsMaxDelayLessThanBase() {
            assertThrows(IllegalArgumentException::class.java) {
                RateLimitConfig(baseDelayMs = 1000, maxDelayMs = 500)
            }
        }
    }

    // =========================================================================
    // tryAcquire Tests
    // =========================================================================

    @Nested
    @DisplayName("tryAcquire")
    inner class TryAcquireTests {

        @Test
        @DisplayName("allows requests within limit")
        fun allowsRequestsWithinLimit() {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 5))
            repeat(5) {
                assertTrue(smallLimiter.tryAcquire(), "Request ${it + 1} should be allowed")
            }
        }

        @Test
        @DisplayName("blocks requests beyond limit")
        fun blocksRequestsBeyondLimit() {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 3))
            repeat(3) { smallLimiter.tryAcquire() }
            assertFalse(smallLimiter.tryAcquire(), "4th request should be blocked")
        }

        @Test
        @DisplayName("disabled limiter always allows")
        fun disabledLimiterAlwaysAllows() {
            val disabled = RateLimiter(RateLimitConfig.DISABLED)
            repeat(1000) {
                assertTrue(disabled.tryAcquire())
            }
        }
    }

    // =========================================================================
    // acquire Tests
    // =========================================================================

    @Nested
    @DisplayName("acquire")
    inner class AcquireTests {

        @Test
        @DisplayName("allows requests within limit without delay")
        fun allowsRequestsWithinLimit() = runBlocking {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 10))
            val start = System.currentTimeMillis()
            repeat(10) { smallLimiter.acquire() }
            val elapsed = System.currentTimeMillis() - start
            // Should be nearly instant (well under 1 second)
            assertTrue(elapsed < 1000, "Should complete quickly, took ${elapsed}ms")
        }

        @Test
        @DisplayName("disabled limiter completes immediately")
        fun disabledLimiterNoDelay() = runBlocking {
            val disabled = RateLimiter(RateLimitConfig.DISABLED)
            val start = System.currentTimeMillis()
            repeat(100) { disabled.acquire() }
            val elapsed = System.currentTimeMillis() - start
            assertTrue(elapsed < 1000, "Disabled limiter should be instant, took ${elapsed}ms")
        }

        @Test
        @DisplayName("throttles requests beyond limit with back-off")
        fun throttlesBeyondLimit() = runBlocking {
            val tinyLimiter = RateLimiter(RateLimitConfig(
                maxRequestsPerMinute = 2,
                baseDelayMs = 100,
                maxDelayMs = 500,
                windowSeconds = 60
            ))
            // First 2 should be instant
            tinyLimiter.acquire()
            tinyLimiter.acquire()

            // 3rd should be throttled and take at least baseDelayMs
            val start = System.currentTimeMillis()
            tinyLimiter.acquire()
            val elapsed = System.currentTimeMillis() - start
            assertTrue(elapsed >= 90, "Throttled request should wait at least ~100ms, took ${elapsed}ms")
        }
    }

    // =========================================================================
    // Back-off Calculation Tests
    // =========================================================================

    @Nested
    @DisplayName("Exponential Back-off")
    inner class BackoffTests {

        @Test
        @DisplayName("first throttle uses base delay")
        fun firstThrottleUsesBaseDelay() {
            val config = RateLimitConfig(baseDelayMs = 500, maxDelayMs = 30_000)
            val testLimiter = RateLimiter(config)
            assertEquals(500, testLimiter.calculateBackoffDelay(1))
        }

        @Test
        @DisplayName("delay doubles with each consecutive throttle")
        fun delayDoubles() {
            val config = RateLimitConfig(baseDelayMs = 500, maxDelayMs = 30_000)
            val testLimiter = RateLimiter(config)
            assertEquals(500, testLimiter.calculateBackoffDelay(1))   // 500 * 2^0
            assertEquals(1000, testLimiter.calculateBackoffDelay(2))  // 500 * 2^1
            assertEquals(2000, testLimiter.calculateBackoffDelay(3))  // 500 * 2^2
            assertEquals(4000, testLimiter.calculateBackoffDelay(4))  // 500 * 2^3
        }

        @Test
        @DisplayName("delay caps at maxDelayMs")
        fun delayCapsAtMax() {
            val config = RateLimitConfig(baseDelayMs = 500, maxDelayMs = 2000)
            val testLimiter = RateLimiter(config)
            assertEquals(2000, testLimiter.calculateBackoffDelay(3))  // 500 * 2^2 = 2000
            assertEquals(2000, testLimiter.calculateBackoffDelay(4))  // Would be 4000, capped at 2000
            assertEquals(2000, testLimiter.calculateBackoffDelay(10)) // Way over, capped
        }

        @Test
        @DisplayName("handles zero consecutive throttles")
        fun handlesZeroThrottles() {
            val config = RateLimitConfig(baseDelayMs = 500, maxDelayMs = 30_000)
            val testLimiter = RateLimiter(config)
            assertEquals(500, testLimiter.calculateBackoffDelay(0))
        }
    }

    // =========================================================================
    // Stats Tests
    // =========================================================================

    @Nested
    @DisplayName("RateLimitStats")
    inner class StatsTests {

        @Test
        @DisplayName("tracks total requests")
        fun tracksTotalRequests() {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 100))
            repeat(5) { smallLimiter.tryAcquire() }
            assertEquals(5, smallLimiter.getStats().totalRequests)
        }

        @Test
        @DisplayName("tracks current window count")
        fun tracksWindowCount() {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 100))
            repeat(3) { smallLimiter.tryAcquire() }
            assertEquals(3, smallLimiter.getStats().currentWindowCount)
        }

        @Test
        @DisplayName("reports remaining in window")
        fun reportsRemaining() {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 10))
            repeat(7) { smallLimiter.tryAcquire() }
            assertEquals(3, smallLimiter.getStats().remainingInWindow)
        }

        @Test
        @DisplayName("remaining is zero when exhausted")
        fun remainingZeroWhenExhausted() {
            val smallLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 3))
            repeat(3) { smallLimiter.tryAcquire() }
            assertEquals(0, smallLimiter.getStats().remainingInWindow)
        }

        @Test
        @DisplayName("disabled limiter tracks requests too")
        fun disabledStillTracks() {
            val disabled = RateLimiter(RateLimitConfig.DISABLED)
            repeat(5) { disabled.tryAcquire() }
            assertEquals(5, disabled.getStats().totalRequests)
        }
    }

    // =========================================================================
    // Config Update Tests
    // =========================================================================

    @Nested
    @DisplayName("Config Updates")
    inner class ConfigUpdateTests {

        @Test
        @DisplayName("updateConfig takes effect immediately")
        fun updateConfigTakesEffect() {
            val testLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 2))
            repeat(2) { testLimiter.tryAcquire() }
            assertFalse(testLimiter.tryAcquire(), "Should be exhausted at limit 2")

            // Increase limit
            testLimiter.updateConfig(RateLimitConfig(maxRequestsPerMinute = 5))
            assertTrue(testLimiter.tryAcquire(), "Should allow after limit increase")
        }

        @Test
        @DisplayName("getConfig returns current config")
        fun getConfigReturnsCurrent() {
            val testLimiter = RateLimiter(RateLimitConfig.STRICT)
            assertEquals(10, testLimiter.getConfig().maxRequestsPerMinute)

            testLimiter.updateConfig(RateLimitConfig.CONSERVATIVE)
            assertEquals(20, testLimiter.getConfig().maxRequestsPerMinute)
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
        fun resetClearsState() {
            val testLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 3))
            repeat(3) { testLimiter.tryAcquire() }
            assertFalse(testLimiter.tryAcquire(), "Should be exhausted")

            testLimiter.reset()

            val stats = testLimiter.getStats()
            assertEquals(0, stats.totalRequests)
            assertEquals(0, stats.currentWindowCount)
            assertEquals(3, stats.remainingInWindow)
            assertTrue(testLimiter.tryAcquire(), "Should allow after reset")
        }
    }

    // =========================================================================
    // Window Expiry Tests
    // =========================================================================

    @Nested
    @DisplayName("Window Expiry")
    inner class WindowExpiryTests {

        @Test
        @DisplayName("requests allowed after window expires")
        fun requestsAllowedAfterWindowExpiry() {
            // Use a very short window
            val shortWindow = RateLimiter(RateLimitConfig(
                maxRequestsPerMinute = 2,
                windowSeconds = 1 // 1 second window
            ))

            repeat(2) { shortWindow.tryAcquire() }
            assertFalse(shortWindow.tryAcquire(), "Should be exhausted")

            // Wait for window to expire
            Thread.sleep(1100)

            assertTrue(shortWindow.tryAcquire(), "Should allow after window expires")
        }
    }

    // =========================================================================
    // Thread Safety Tests
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety")
    inner class ThreadSafetyTests {

        @Test
        @DisplayName("handles concurrent tryAcquire calls")
        fun handlesConcurrentTryAcquire() = runBlocking {
            val concurrentLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 50))

            // Fire 100 concurrent requests
            val results = (1..100).map {
                async {
                    concurrentLimiter.tryAcquire()
                }
            }.awaitAll()

            val accepted = results.count { it }
            val rejected = results.count { !it }

            // Should accept exactly 50 (the limit)
            assertEquals(50, accepted, "Should accept exactly 50 requests")
            assertEquals(50, rejected, "Should reject exactly 50 requests")
        }

        @Test
        @DisplayName("stats are consistent under concurrent access")
        fun statsConsistentUnderConcurrency() = runBlocking {
            val concurrentLimiter = RateLimiter(RateLimitConfig(maxRequestsPerMinute = 100))

            (1..100).map {
                async { concurrentLimiter.tryAcquire() }
            }.awaitAll()

            val stats = concurrentLimiter.getStats()
            assertEquals(100, stats.totalRequests)
        }
    }
}
