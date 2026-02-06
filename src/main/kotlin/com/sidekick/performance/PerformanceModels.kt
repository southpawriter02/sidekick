// =============================================================================
// PerformanceModels.kt
// =============================================================================
// Data models for performance monitoring and optimization in the Sidekick plugin.
//
// This file contains all data contracts used by the performance subsystem:
// - PerformanceMetrics: Aggregate performance snapshot
// - MemoryUsage: JVM memory statistics
// - StartupPhase/StartupTiming: Startup phase tracking
// - CacheStats: Cache hit/miss statistics
// - PerformanceConfig: Performance configuration options
//
// DESIGN NOTES:
// - All models are immutable data classes for thread-safety
// - Computed properties provide derived metrics (hitRate, heapPercentage)
// - Default values enable incremental configuration
//
// @since v1.0.1
// =============================================================================

package com.sidekick.performance

import java.time.Duration
import java.time.Instant

// =============================================================================
// Performance Metrics
// =============================================================================

/**
 * Aggregate performance metrics snapshot.
 *
 * Provides a comprehensive view of the plugin's performance characteristics
 * at a point in time, including startup timing, memory usage, and cache
 * efficiency.
 *
 * ## Usage
 * ```kotlin
 * val metrics = performanceMonitor.captureMetrics()
 * println("Startup: ${metrics.startupTime.toMillis()}ms")
 * println("Cache hit rate: ${metrics.cacheHitRate * 100}%")
 * ```
 *
 * @property startupTime Total time from plugin load to ready state
 * @property indexingTime Time spent indexing project files
 * @property averageResponseTime Average LLM response latency
 * @property memoryUsage Current JVM memory state
 * @property cacheHitRate Aggregate cache hit ratio (0.0 to 1.0)
 * @property activeConnections Number of active LLM provider connections
 */
data class PerformanceMetrics(
    val startupTime: Duration,
    val indexingTime: Duration,
    val averageResponseTime: Duration,
    val memoryUsage: MemoryUsage,
    val cacheHitRate: Float,
    val activeConnections: Int
) {
    /**
     * Checks if startup time is within acceptable bounds.
     *
     * A startup time under 3 seconds is considered acceptable for IDE plugins,
     * as longer delays impact perceived IDE responsiveness.
     */
    val isStartupAcceptable: Boolean
        get() = startupTime.toMillis() < 3000

    /**
     * Checks if memory usage is healthy.
     *
     * Memory is considered healthy when heap usage is below 80% of maximum,
     * leaving headroom for operations without triggering aggressive GC.
     */
    val isMemoryHealthy: Boolean
        get() = memoryUsage.heapPercentage < 0.8f
}

// =============================================================================
// Memory Usage
// =============================================================================

/**
 * JVM memory usage statistics.
 *
 * Captures heap and non-heap memory usage along with garbage collection
 * metrics. Used for monitoring memory pressure and detecting leaks.
 *
 * ## Memory Regions
 * - **Heap**: Object allocations, managed by GC
 * - **Non-Heap**: Metaspace, code cache, thread stacks
 *
 * @property heapUsed Current bytes used in heap space
 * @property heapMax Maximum bytes available for heap
 * @property nonHeapUsed Current bytes used in non-heap space
 * @property gcCount Number of GC cycles since JVM start
 * @property gcTime Cumulative time spent in GC
 */
data class MemoryUsage(
    val heapUsed: Long,
    val heapMax: Long,
    val nonHeapUsed: Long,
    val gcCount: Long,
    val gcTime: Duration
) {
    /**
     * Heap usage as a percentage (0.0 to 1.0).
     *
     * Calculated as `heapUsed / heapMax`. Returns 0 if heapMax is 0.
     */
    val heapPercentage: Float
        get() = if (heapMax > 0) heapUsed.toFloat() / heapMax else 0f

    /**
     * Total memory used (heap + non-heap) in bytes.
     */
    val totalUsed: Long
        get() = heapUsed + nonHeapUsed

    /**
     * Human-readable heap usage string.
     *
     * @return Formatted string like "512MB / 2048MB (25%)"
     */
    fun formatHeapUsage(): String {
        val usedMb = heapUsed / (1024 * 1024)
        val maxMb = heapMax / (1024 * 1024)
        val pct = (heapPercentage * 100).toInt()
        return "${usedMb}MB / ${maxMb}MB ($pct%)"
    }

    companion object {
        /**
         * Creates a MemoryUsage snapshot from the current JVM state.
         */
        fun capture(): MemoryUsage {
            val runtime = Runtime.getRuntime()
            return MemoryUsage(
                heapUsed = runtime.totalMemory() - runtime.freeMemory(),
                heapMax = runtime.maxMemory(),
                nonHeapUsed = 0, // Would need ManagementFactory for full data
                gcCount = 0,
                gcTime = Duration.ZERO
            )
        }

        /**
         * Empty memory usage for testing or initialization.
         */
        val ZERO = MemoryUsage(0, 0, 0, 0, Duration.ZERO)
    }
}

// =============================================================================
// Startup Phases
// =============================================================================

/**
 * Enumeration of plugin startup phases.
 *
 * Defines the distinct phases during plugin initialization, enabling
 * granular timing measurement and bottleneck identification.
 *
 * ## Phase Order
 * Phases typically execute in this order:
 * 1. PLUGIN_LOAD - Initial class loading and static initialization
 * 2. SERVICE_INIT - Application and project service creation
 * 3. CONNECTION_CHECK - LLM provider connectivity verification
 * 4. INDEX_LOAD - Loading or building code indices
 * 5. UI_INIT - Tool window and UI component setup
 *
 * @property displayName Human-readable name for reports
 */
enum class StartupPhase(val displayName: String) {
    /** Initial plugin loading and class initialization. */
    PLUGIN_LOAD("Plugin Load"),

    /** Service container initialization and dependency injection. */
    SERVICE_INIT("Service Initialization"),

    /** LLM provider connection and health checks. */
    CONNECTION_CHECK("Connection Check"),

    /** Code index loading from cache or initial indexing. */
    INDEX_LOAD("Index Load"),

    /** Tool window and UI component initialization. */
    UI_INIT("UI Initialization");

    companion object {
        /**
         * Returns all phases in recommended execution order.
         */
        fun inOrder(): List<StartupPhase> = listOf(
            PLUGIN_LOAD,
            SERVICE_INIT,
            CONNECTION_CHECK,
            INDEX_LOAD,
            UI_INIT
        )
    }
}

/**
 * Records timing for a single startup phase.
 *
 * Captures the start and end times for a phase, enabling duration
 * calculation and timeline visualization.
 *
 * @property phase The startup phase being timed
 * @property startTime When the phase began
 * @property endTime When the phase completed (null if still running)
 */
data class StartupTiming(
    val phase: StartupPhase,
    val startTime: Instant,
    val endTime: Instant? = null
) {
    /**
     * Duration of the phase, or null if phase hasn't completed.
     */
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    /**
     * Duration in milliseconds for display, or -1 if incomplete.
     */
    val durationMs: Long
        get() = duration?.toMillis() ?: -1

    /**
     * Whether this phase has completed.
     */
    val isComplete: Boolean
        get() = endTime != null

    /**
     * Creates a copy with the end time set to now.
     */
    fun complete(): StartupTiming = copy(endTime = Instant.now())

    /**
     * Formats the timing for display in reports.
     *
     * @return String like "Plugin Load: 150ms" or "Plugin Load: (running)"
     */
    fun format(): String {
        val durationText = duration?.let { "${it.toMillis()}ms" } ?: "(running)"
        return "${phase.displayName}: $durationText"
    }
}

// =============================================================================
// Cache Statistics
// =============================================================================

/**
 * Statistics for a named cache.
 *
 * Tracks cache performance metrics including hits, misses, and evictions.
 * Multiple caches can be registered with the PerformanceMonitor for
 * aggregate reporting.
 *
 * ## Example
 * ```kotlin
 * val stats = CacheStats(
 *     name = "prompt-cache",
 *     size = 150,
 *     maxSize = 200,
 *     hits = 1000,
 *     misses = 100,
 *     evictions = 50
 * )
 * println("Hit rate: ${stats.hitRate * 100}%") // 90.9%
 * ```
 *
 * @property name Unique identifier for this cache
 * @property size Current number of entries in the cache
 * @property maxSize Maximum capacity of the cache
 * @property hits Number of successful cache lookups
 * @property misses Number of cache lookup failures
 * @property evictions Number of entries evicted due to capacity
 */
data class CacheStats(
    val name: String,
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val evictions: Long
) {
    /**
     * Cache hit rate as a ratio (0.0 to 1.0).
     *
     * Calculated as `hits / (hits + misses)`. Returns 0 if no lookups
     * have been performed.
     */
    val hitRate: Float
        get() = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f

    /**
     * Cache utilization as a percentage of max capacity.
     */
    val utilization: Float
        get() = if (maxSize > 0) size.toFloat() / maxSize else 0f

    /**
     * Total number of cache lookups (hits + misses).
     */
    val totalLookups: Long
        get() = hits + misses

    /**
     * Whether the cache is at or near capacity (>90% full).
     */
    val isNearCapacity: Boolean
        get() = utilization > 0.9f

    /**
     * Formats cache stats for display.
     *
     * @return String like "prompt-cache: 150/200 entries, 90.9% hit rate"
     */
    fun format(): String {
        val hitPct = (hitRate * 100).toInt()
        return "$name: $size/$maxSize entries, $hitPct% hit rate"
    }

    companion object {
        /**
         * Creates empty stats for a named cache.
         */
        fun empty(name: String, maxSize: Int = 1000) = CacheStats(
            name = name,
            size = 0,
            maxSize = maxSize,
            hits = 0,
            misses = 0,
            evictions = 0
        )
    }
}

// =============================================================================
// Performance Configuration
// =============================================================================

/**
 * Configuration options for performance optimization.
 *
 * Controls lazy initialization, caching behavior, connection pooling,
 * and timeout settings. All options have sensible defaults.
 *
 * ## Default Configuration
 * ```kotlin
 * val config = PerformanceConfig() // Uses all defaults
 * ```
 *
 * ## Custom Configuration
 * ```kotlin
 * val config = PerformanceConfig(
 *     lazyInitialization = true,
 *     maxCacheSize = 500,
 *     requestTimeout = Duration.ofSeconds(60)
 * )
 * ```
 *
 * @property lazyInitialization Whether to defer service init until first use
 * @property backgroundIndexing Whether to index code in background threads
 * @property cacheEnabled Whether caching is enabled globally
 * @property maxCacheSize Maximum entries per cache
 * @property connectionPoolSize Number of pooled LLM connections
 * @property requestTimeout Timeout for LLM API requests
 */
data class PerformanceConfig(
    val lazyInitialization: Boolean = true,
    val backgroundIndexing: Boolean = true,
    val cacheEnabled: Boolean = true,
    val maxCacheSize: Int = 1000,
    val connectionPoolSize: Int = 5,
    val requestTimeout: Duration = Duration.ofSeconds(30)
) {
    /**
     * Creates a high-performance configuration optimized for speed.
     *
     * Maximizes caching and connection pooling at the cost of memory.
     */
    fun optimizeForSpeed() = copy(
        lazyInitialization = false,
        maxCacheSize = 2000,
        connectionPoolSize = 10
    )

    /**
     * Creates a low-memory configuration.
     *
     * Minimizes memory usage at the cost of some performance.
     */
    fun optimizeForMemory() = copy(
        maxCacheSize = 250,
        connectionPoolSize = 2
    )

    /**
     * Validates the configuration and returns any issues.
     *
     * @return List of validation error messages, empty if valid
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()
        if (maxCacheSize < 0) issues.add("maxCacheSize must be non-negative")
        if (connectionPoolSize < 1) issues.add("connectionPoolSize must be at least 1")
        if (requestTimeout.isNegative) issues.add("requestTimeout must be positive")
        return issues
    }

    companion object {
        /** Default configuration with balanced settings. */
        val DEFAULT = PerformanceConfig()

        /** Minimal configuration for testing. */
        val MINIMAL = PerformanceConfig(
            lazyInitialization = false,
            backgroundIndexing = false,
            cacheEnabled = false,
            maxCacheSize = 10,
            connectionPoolSize = 1,
            requestTimeout = Duration.ofSeconds(5)
        )
    }
}
