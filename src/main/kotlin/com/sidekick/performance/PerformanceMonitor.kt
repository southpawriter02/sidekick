// =============================================================================
// PerformanceMonitor.kt
// =============================================================================
// Project-level service for monitoring and recording plugin performance.
//
// Tracks startup phase timings, captures JVM metrics, aggregates cache
// statistics, and generates human-readable performance reports.
//
// DESIGN NOTES:
// - Project-level service (@Service(Service.Level.PROJECT))
// - Thread-safe timing recording using synchronized collections
// - Integrates with LazyInitializer for startup measurement
//
// @since v1.0.1
// =============================================================================

package com.sidekick.performance

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Monitors and records plugin performance metrics.
 *
 * This project-level service provides comprehensive performance tracking
 * including startup phase timings, JVM memory metrics, cache statistics,
 * and response time monitoring.
 *
 * ## Usage
 * ```kotlin
 * val monitor = PerformanceMonitor.getInstance(project)
 *
 * // Record startup phases
 * monitor.startPhase(StartupPhase.PLUGIN_LOAD)
 * // ... do work ...
 * monitor.endPhase(StartupPhase.PLUGIN_LOAD)
 *
 * // Capture metrics
 * val metrics = monitor.captureMetrics()
 * println("Startup: ${metrics.startupTime.toMillis()}ms")
 *
 * // Get startup report
 * println(monitor.getStartupReport())
 * ```
 *
 * ## Thread Safety
 * All methods are thread-safe and may be called from any thread.
 *
 * @property project The project this monitor is associated with
 */
@Service(Service.Level.PROJECT)
class PerformanceMonitor(private val project: Project) {

    private val log = Logger.getInstance(PerformanceMonitor::class.java)

    /**
     * Recorded startup phase timings.
     * Using CopyOnWriteArrayList for thread-safe iteration while allowing modifications.
     */
    private val timings = CopyOnWriteArrayList<StartupTiming>()

    /**
     * Registered cache statistics by name.
     */
    private val caches = ConcurrentHashMap<String, CacheStats>()

    /**
     * Response time history for averaging.
     */
    private val responseTimes = CopyOnWriteArrayList<Duration>()

    /**
     * Last captured metrics snapshot.
     */
    @Volatile
    private var lastSnapshot: PerformanceMetrics? = null

    /**
     * Time of first phase start, used for total startup calculation.
     */
    @Volatile
    private var firstPhaseTime: Instant? = null

    companion object {
        /**
         * Maximum number of response times to keep for averaging.
         */
        private const val MAX_RESPONSE_TIMES = 100

        /**
         * Gets the PerformanceMonitor instance for a project.
         *
         * @param project The project
         * @return The PerformanceMonitor service instance
         */
        fun getInstance(project: Project): PerformanceMonitor {
            return project.getService(PerformanceMonitor::class.java)
        }
    }

    // =========================================================================
    // Phase Timing
    // =========================================================================

    /**
     * Records the start of a startup phase.
     *
     * Call this when beginning a measurable phase of startup. The phase
     * timing will be incomplete until [endPhase] is called.
     *
     * @param phase The startup phase starting
     */
    fun startPhase(phase: StartupPhase) {
        val now = Instant.now()
        
        if (firstPhaseTime == null) {
            firstPhaseTime = now
        }
        
        timings.add(StartupTiming(phase, now, null))
        log.debug("Started phase: ${phase.displayName}")
    }

    /**
     * Records the end of a startup phase.
     *
     * Finds the matching uncompleted phase timing and sets its end time.
     * If no matching phase is found, logs a warning.
     *
     * @param phase The startup phase ending
     */
    fun endPhase(phase: StartupPhase) {
        val now = Instant.now()
        
        // Find the uncompleted timing for this phase
        val timingIndex = timings.indexOfFirst { it.phase == phase && it.endTime == null }
        
        if (timingIndex >= 0) {
            val timing = timings[timingIndex]
            timings[timingIndex] = timing.copy(endTime = now)
            log.debug("Ended phase: ${phase.displayName} (${Duration.between(timing.startTime, now).toMillis()}ms)")
        } else {
            log.warn("endPhase called for phase that wasn't started: ${phase.displayName}")
        }
    }

    /**
     * Records a complete phase timing in one call.
     *
     * Convenience method for recording a phase that has already completed.
     *
     * @param phase The startup phase
     * @param duration How long the phase took
     */
    fun recordPhase(phase: StartupPhase, duration: Duration) {
        val now = Instant.now()
        val startTime = now.minus(duration)
        timings.add(StartupTiming(phase, startTime, now))
        log.debug("Recorded phase: ${phase.displayName} (${duration.toMillis()}ms)")
    }

    /**
     * Gets the timing for a specific phase.
     *
     * @param phase The phase to get timing for
     * @return The timing, or null if not recorded
     */
    fun getPhaseTimning(phase: StartupPhase): StartupTiming? {
        return timings.find { it.phase == phase }
    }

    /**
     * Gets all recorded phase timings.
     */
    fun getAllTimings(): List<StartupTiming> = timings.toList()

    // =========================================================================
    // Response Time Tracking
    // =========================================================================

    /**
     * Records an LLM response time.
     *
     * @param duration The time from request to response
     */
    fun recordResponseTime(duration: Duration) {
        responseTimes.add(duration)
        
        // Keep the list bounded
        while (responseTimes.size > MAX_RESPONSE_TIMES) {
            responseTimes.removeAt(0)
        }
    }

    /**
     * Calculates the average response time.
     *
     * @return Average duration, or Duration.ZERO if no responses recorded
     */
    fun getAverageResponseTime(): Duration {
        if (responseTimes.isEmpty()) return Duration.ZERO
        
        val totalMs = responseTimes.sumOf { it.toMillis() }
        return Duration.ofMillis(totalMs / responseTimes.size)
    }

    // =========================================================================
    // Cache Statistics
    // =========================================================================

    /**
     * Registers or updates cache statistics.
     *
     * @param name Unique cache name
     * @param stats Current cache statistics
     */
    fun registerCache(name: String, stats: CacheStats) {
        caches[name] = stats
    }

    /**
     * Gets statistics for a specific cache.
     *
     * @param name The cache name
     * @return The cache stats, or null if not registered
     */
    fun getCacheStats(name: String): CacheStats? = caches[name]

    /**
     * Gets all registered cache statistics.
     */
    fun getAllCacheStats(): Map<String, CacheStats> = caches.toMap()

    /**
     * Calculates the average hit rate across all caches.
     *
     * @return Average hit rate (0.0 to 1.0), or 0 if no caches registered
     */
    fun calculateAverageCacheHitRate(): Float {
        if (caches.isEmpty()) return 0f
        return caches.values.map { it.hitRate }.average().toFloat()
    }

    // =========================================================================
    // Metrics Capture
    // =========================================================================

    /**
     * Captures a snapshot of current performance metrics.
     *
     * Collects startup timing, memory usage, cache statistics, and response
     * times into a comprehensive [PerformanceMetrics] snapshot.
     *
     * @return Current performance metrics
     */
    fun captureMetrics(): PerformanceMetrics {
        val metrics = PerformanceMetrics(
            startupTime = calculateTotalStartupTime(),
            indexingTime = getPhaseTimning(StartupPhase.INDEX_LOAD)?.duration ?: Duration.ZERO,
            averageResponseTime = getAverageResponseTime(),
            memoryUsage = MemoryUsage.capture(),
            cacheHitRate = calculateAverageCacheHitRate(),
            activeConnections = 0 // Would integrate with connection pool
        )
        
        lastSnapshot = metrics
        return metrics
    }

    /**
     * Gets the last captured metrics snapshot, if any.
     */
    fun getLastSnapshot(): PerformanceMetrics? = lastSnapshot

    // =========================================================================
    // Startup Report
    // =========================================================================

    /**
     * Generates a human-readable startup performance report.
     *
     * @return Formatted multi-line report string
     */
    fun getStartupReport(): String = buildString {
        appendLine("=== Sidekick Startup Report ===")
        appendLine()
        
        // Phase timings
        val sortedTimings = timings.sortedBy { it.startTime }
        if (sortedTimings.isNotEmpty()) {
            appendLine("Phase Timings:")
            sortedTimings.forEach { timing ->
                appendLine("  ${timing.format()}")
            }
            appendLine()
        }
        
        // Total startup time
        val total = calculateTotalStartupTime()
        appendLine("Total Startup Time: ${total.toMillis()}ms")
        
        // Memory
        val memory = MemoryUsage.capture()
        appendLine("Memory: ${memory.formatHeapUsage()}")
        
        // Caches
        if (caches.isNotEmpty()) {
            appendLine()
            appendLine("Cache Statistics:")
            caches.values.forEach { cache ->
                appendLine("  ${cache.format()}")
            }
        }
    }

    /**
     * Generates a compact one-line status summary.
     */
    fun getStatusLine(): String {
        val startupMs = calculateTotalStartupTime().toMillis()
        val memory = MemoryUsage.capture()
        val cacheRate = (calculateAverageCacheHitRate() * 100).toInt()
        return "Startup: ${startupMs}ms | Heap: ${memory.formatHeapUsage()} | Cache: ${cacheRate}%"
    }

    // =========================================================================
    // Internal Helpers
    // =========================================================================

    /**
     * Calculates the total time from first phase start to last phase end.
     */
    private fun calculateTotalStartupTime(): Duration {
        if (timings.isEmpty()) return Duration.ZERO
        
        val first = timings.minByOrNull { it.startTime }?.startTime ?: return Duration.ZERO
        val last = timings.mapNotNull { it.endTime }.maxOrNull() ?: return Duration.ZERO
        
        return Duration.between(first, last)
    }

    // =========================================================================
    // Management
    // =========================================================================

    /**
     * Resets all recorded metrics.
     *
     * Clears timings, response times, and cache stats. Primarily for testing.
     */
    fun reset() {
        timings.clear()
        caches.clear()
        responseTimes.clear()
        lastSnapshot = null
        firstPhaseTime = null
        log.debug("PerformanceMonitor reset")
    }
}
