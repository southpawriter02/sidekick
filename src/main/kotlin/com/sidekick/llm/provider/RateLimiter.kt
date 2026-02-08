// =============================================================================
// RateLimiter.kt
// =============================================================================
// Configurable, thread-safe rate limiter for LLM requests with exponential
// back-off. Uses a sliding-window approach to track request timestamps.
//
// @since v1.1.0
// =============================================================================

package com.sidekick.llm.provider

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// =============================================================================
// Rate Limit Configuration
// =============================================================================

/**
 * Configuration for LLM request rate limiting.
 *
 * @property maxRequestsPerMinute Maximum requests allowed within the time window
 * @property windowSeconds Duration of the sliding window in seconds
 * @property baseDelayMs Initial back-off delay when throttled (milliseconds)
 * @property maxDelayMs Maximum back-off delay cap (milliseconds)
 * @property enabled Whether rate limiting is active
 */
data class RateLimitConfig(
    val maxRequestsPerMinute: Int = 60,
    val windowSeconds: Int = 60,
    val baseDelayMs: Long = 500,
    val maxDelayMs: Long = 30_000,
    val enabled: Boolean = true
) {
    init {
        require(maxRequestsPerMinute > 0) { "maxRequestsPerMinute must be positive" }
        require(windowSeconds > 0) { "windowSeconds must be positive" }
        require(baseDelayMs > 0) { "baseDelayMs must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
    }

    companion object {
        /** Rate limiting disabled — all requests pass through immediately. */
        val DISABLED = RateLimitConfig(
            maxRequestsPerMinute = Int.MAX_VALUE,
            enabled = false
        )

        /** Default: 60 requests per minute. */
        val DEFAULT = RateLimitConfig()

        /** Conservative: 20 requests per minute with longer back-off. */
        val CONSERVATIVE = RateLimitConfig(
            maxRequestsPerMinute = 20,
            baseDelayMs = 1000,
            maxDelayMs = 60_000
        )

        /** Strict: 10 requests per minute. */
        val STRICT = RateLimitConfig(
            maxRequestsPerMinute = 10,
            baseDelayMs = 2000,
            maxDelayMs = 60_000
        )
    }
}

// =============================================================================
// Rate Limit Statistics
// =============================================================================

/**
 * Runtime statistics from the rate limiter.
 *
 * @property totalRequests Total requests processed
 * @property throttledRequests Number of requests that were throttled
 * @property currentWindowCount Current number of requests in the active window
 * @property remainingInWindow Requests remaining before throttling
 * @property totalWaitTimeMs Total time spent waiting due to throttling
 * @property averageWaitTimeMs Average wait time per throttled request
 * @property consecutiveThrottles Current consecutive throttle streak
 */
data class RateLimitStats(
    val totalRequests: Long,
    val throttledRequests: Long,
    val currentWindowCount: Int,
    val remainingInWindow: Int,
    val totalWaitTimeMs: Long,
    val averageWaitTimeMs: Long,
    val consecutiveThrottles: Int
)

// =============================================================================
// Rate Limiter
// =============================================================================

/**
 * Thread-safe sliding-window rate limiter with exponential back-off.
 *
 * Tracks request timestamps in a sliding window and throttles new requests
 * when the limit is reached. Throttled requests wait with exponential
 * back-off, doubling the delay on each consecutive throttle up to `maxDelayMs`.
 *
 * ## Usage
 * ```kotlin
 * val limiter = RateLimiter(RateLimitConfig.DEFAULT)
 *
 * // Blocking acquire — waits if at limit
 * limiter.acquire()
 * provider.chat(request)
 *
 * // Non-blocking check
 * if (limiter.tryAcquire()) {
 *     provider.chat(request)
 * }
 * ```
 *
 * ## Thread Safety
 * All public methods are safe to call from multiple coroutines concurrently.
 * The implementation uses [ConcurrentLinkedDeque] for the timestamp window
 * and atomic operations for counters.
 */
class RateLimiter(
    @Volatile private var config: RateLimitConfig = RateLimitConfig.DEFAULT
) {
    // Sliding window of request timestamps (epoch millis)
    private val requestTimestamps = ConcurrentLinkedDeque<Long>()

    // Counters
    private val totalRequests = AtomicLong(0)
    private val throttledRequests = AtomicLong(0)
    private val totalWaitTimeMs = AtomicLong(0)
    private val consecutiveThrottles = AtomicInteger(0)

    /**
     * Acquires a permit, suspending with exponential back-off if the rate
     * limit has been reached.
     *
     * If rate limiting is disabled, this returns immediately.
     *
     * @throws kotlinx.coroutines.CancellationException if the coroutine is cancelled while waiting
     */
    suspend fun acquire() {
        if (!config.enabled) {
            recordRequest()
            return
        }

        while (true) {
            pruneExpiredTimestamps()

            if (requestTimestamps.size < config.maxRequestsPerMinute) {
                // Permit available
                consecutiveThrottles.set(0)
                recordRequest()
                return
            }

            // Throttled — apply exponential back-off
            val throttleCount = consecutiveThrottles.incrementAndGet()
            throttledRequests.incrementAndGet()

            val backoffDelay = calculateBackoffDelay(throttleCount)
            totalWaitTimeMs.addAndGet(backoffDelay)
            delay(backoffDelay)
        }
    }

    /**
     * Attempts to acquire a permit without waiting.
     *
     * @return `true` if a permit was acquired, `false` if the rate limit is exhausted
     */
    fun tryAcquire(): Boolean {
        if (!config.enabled) {
            recordRequest()
            return true
        }

        pruneExpiredTimestamps()

        return if (requestTimestamps.size < config.maxRequestsPerMinute) {
            consecutiveThrottles.set(0)
            recordRequest()
            true
        } else {
            false
        }
    }

    /**
     * Returns the current rate limiter statistics.
     */
    fun getStats(): RateLimitStats {
        pruneExpiredTimestamps()
        val total = totalRequests.get()
        val throttled = throttledRequests.get()
        val windowCount = requestTimestamps.size
        val totalWait = totalWaitTimeMs.get()

        return RateLimitStats(
            totalRequests = total,
            throttledRequests = throttled,
            currentWindowCount = windowCount,
            remainingInWindow = (config.maxRequestsPerMinute - windowCount).coerceAtLeast(0),
            totalWaitTimeMs = totalWait,
            averageWaitTimeMs = if (throttled > 0) totalWait / throttled else 0,
            consecutiveThrottles = consecutiveThrottles.get()
        )
    }

    /**
     * Updates the rate limiter configuration. Takes effect immediately.
     *
     * @param newConfig The new configuration to apply
     */
    fun updateConfig(newConfig: RateLimitConfig) {
        config = newConfig
    }

    /**
     * Returns the current configuration.
     */
    fun getConfig(): RateLimitConfig = config

    /**
     * Resets all state — clears timestamps, counters, and throttle streak.
     */
    fun reset() {
        requestTimestamps.clear()
        totalRequests.set(0)
        throttledRequests.set(0)
        totalWaitTimeMs.set(0)
        consecutiveThrottles.set(0)
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Records a successful request by adding a timestamp and incrementing
     * the total counter.
     */
    private fun recordRequest() {
        requestTimestamps.addLast(System.currentTimeMillis())
        totalRequests.incrementAndGet()
    }

    /**
     * Removes timestamps that have fallen outside the sliding window.
     */
    private fun pruneExpiredTimestamps() {
        val cutoff = System.currentTimeMillis() - (config.windowSeconds * 1000L)
        while (true) {
            val oldest = requestTimestamps.peekFirst() ?: break
            if (oldest < cutoff) {
                requestTimestamps.pollFirst()
            } else {
                break
            }
        }
    }

    /**
     * Calculates exponential back-off delay.
     *
     * delay = baseDelayMs * 2^(consecutiveThrottles - 1), capped at maxDelayMs
     */
    internal fun calculateBackoffDelay(consecutiveThrottleCount: Int): Long {
        val exponent = (consecutiveThrottleCount - 1).coerceAtLeast(0)
        val delay = config.baseDelayMs * (1L shl exponent.coerceAtMost(30))
        return delay.coerceAtMost(config.maxDelayMs)
    }
}
