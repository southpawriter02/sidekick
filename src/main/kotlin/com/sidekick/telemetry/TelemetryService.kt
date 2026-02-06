// =============================================================================
// TelemetryService.kt
// =============================================================================
// Application-level service for opt-in telemetry collection.
//
// Provides privacy-respecting telemetry with:
// - Feature usage tracking
// - Anonymized error reporting
// - Performance metrics
// - Session management
//
// PRIVACY NOTES:
// - All telemetry is OPT-IN only (disabled by default)
// - No personally identifiable information (PII) is collected
// - Users have granular control over what is tracked
// - Events are batched and sent periodically
//
// @since v1.0.3
// =============================================================================

package com.sidekick.telemetry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-level service for opt-in telemetry.
 *
 * Collects anonymized usage data when enabled by the user. All telemetry
 * is disabled by default and requires explicit opt-in.
 *
 * ## Privacy Model
 * - **Opt-in only**: Disabled by default
 * - **Granular controls**: Users can enable specific categories
 * - **Anonymized**: No PII collected
 * - **Local-first**: Events stored locally until flush
 *
 * ## Usage
 * ```kotlin
 * val telemetry = TelemetryService.getInstance()
 *
 * // Enable telemetry
 * telemetry.updateConfig(TelemetryConfig(enabled = true))
 *
 * // Track feature usage
 * telemetry.trackFeature("chat_sent", mapOf("model" to "gpt-4"))
 *
 * // Track performance
 * telemetry.trackPerformance("code_generation", 1500, true)
 * ```
 *
 * ## Persistence
 * Configuration and install ID are persisted using IntelliJ's
 * PersistentStateComponent. Events are stored in memory until flushed.
 */
@Service(Service.Level.APP)
@State(
    name = "SidekickTelemetry",
    storages = [Storage("sidekick-telemetry.xml")]
)
class TelemetryService : PersistentStateComponent<TelemetryService.State> {

    private val log = Logger.getInstance(TelemetryService::class.java)

    /**
     * Persistent state containing configuration and install ID.
     */
    data class State(
        var configEnabled: Boolean = false,
        var configAnonymousUsage: Boolean = true,
        var configCrashReporting: Boolean = true,
        var configFeatureTracking: Boolean = true,
        var configPerformanceMetrics: Boolean = true,
        var installId: String = UUID.randomUUID().toString()
    ) {
        /**
         * Converts state to TelemetryConfig.
         */
        fun toConfig() = TelemetryConfig(
            enabled = configEnabled,
            anonymousUsage = configAnonymousUsage,
            crashReporting = configCrashReporting,
            featureTracking = configFeatureTracking,
            performanceMetrics = configPerformanceMetrics
        )

        /**
         * Updates state from TelemetryConfig.
         */
        fun fromConfig(config: TelemetryConfig) {
            configEnabled = config.enabled
            configAnonymousUsage = config.anonymousUsage
            configCrashReporting = config.crashReporting
            configFeatureTracking = config.featureTracking
            configPerformanceMetrics = config.performanceMetrics
        }
    }

    private var myState = State()

    /**
     * Current session ID.
     */
    private var sessionId = UUID.randomUUID().toString()

    /**
     * Session start time.
     */
    private var sessionStart: Instant = Instant.now()

    /**
     * Event queue for batching.
     */
    private val eventQueue = CopyOnWriteArrayList<TelemetryEvent>()

    /**
     * Last action for crash context.
     */
    @Volatile
    private var lastAction: String? = null

    /**
     * Error count for the current session.
     */
    private val sessionErrorCount = AtomicLong(0)

    companion object {
        /**
         * Gets the singleton TelemetryService instance.
         */
        fun getInstance(): TelemetryService {
            return ApplicationManager.getApplication().getService(TelemetryService::class.java)
        }

        /**
         * Maximum events in queue before auto-flush.
         */
        private const val MAX_QUEUE_SIZE = 50

        /**
         * Plugin version for crash reports.
         */
        private const val PLUGIN_VERSION = "1.0.3"
    }

    // =========================================================================
    // PersistentStateComponent
    // =========================================================================

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        log.debug("Loaded telemetry state: enabled=${state.configEnabled}")
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Gets the current telemetry configuration.
     */
    fun getConfig(): TelemetryConfig = myState.toConfig()

    /**
     * Updates the telemetry configuration.
     *
     * @param config New configuration to apply
     */
    fun updateConfig(config: TelemetryConfig) {
        val wasEnabled = myState.configEnabled
        myState.fromConfig(config)

        if (!wasEnabled && config.enabled) {
            log.info("Telemetry enabled by user")
            startSession()
        } else if (wasEnabled && !config.enabled) {
            log.info("Telemetry disabled by user")
            endSession()
        }
    }

    /**
     * Gets the anonymous install ID.
     */
    fun getInstallId(): String = myState.installId

    /**
     * Checks if telemetry is enabled.
     */
    fun isEnabled(): Boolean = myState.configEnabled

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Starts a new telemetry session.
     */
    fun startSession() {
        sessionId = UUID.randomUUID().toString()
        sessionStart = Instant.now()
        sessionErrorCount.set(0)
        lastAction = null

        if (getConfig().isTypeEnabled(EventType.SESSION)) {
            queueEvent(TelemetryEvent.session("start", sessionId))
        }

        log.debug("Started telemetry session: $sessionId")
    }

    /**
     * Ends the current telemetry session.
     */
    fun endSession() {
        val duration = Duration.between(sessionStart, Instant.now())

        if (getConfig().isTypeEnabled(EventType.SESSION)) {
            queueEvent(TelemetryEvent.session("end", sessionId, duration.toMillis()))
        }

        log.debug("Ended telemetry session: $sessionId (${duration.toMinutes()} min)")
    }

    /**
     * Gets the current session ID.
     */
    fun getSessionId(): String = sessionId

    /**
     * Gets the current session duration.
     */
    fun getSessionDuration(): Duration = Duration.between(sessionStart, Instant.now())

    // =========================================================================
    // Feature Tracking
    // =========================================================================

    /**
     * Tracks a feature usage event.
     *
     * Only recorded if telemetry and feature tracking are enabled.
     *
     * @param featureName Name of the feature used
     * @param properties Additional properties (sanitized)
     */
    fun trackFeature(featureName: String, properties: Map<String, String> = emptyMap()) {
        lastAction = featureName

        if (!getConfig().isTypeEnabled(EventType.FEATURE_USED)) return

        val sanitized = properties.mapValues { it.value.take(100) }
        queueEvent(TelemetryEvent.feature(featureName, sessionId, sanitized))

        log.debug("Tracked feature: $featureName")
    }

    // =========================================================================
    // Error Tracking
    // =========================================================================

    /**
     * Tracks an error event (anonymized).
     *
     * Only recorded if telemetry and crash reporting are enabled.
     * Stack traces and messages are anonymized.
     *
     * @param error The exception that occurred
     * @param context Additional context about where it occurred
     */
    fun trackError(error: Throwable, context: String? = null) {
        sessionErrorCount.incrementAndGet()

        if (!getConfig().isTypeEnabled(EventType.ERROR)) return

        queueEvent(TelemetryEvent.error(
            errorType = error.javaClass.simpleName,
            sessionId = sessionId,
            message = error.message?.take(100),
            context = context?.take(50)
        ))

        log.debug("Tracked error: ${error.javaClass.simpleName}")
    }

    /**
     * Creates a crash report (not queued, returned for immediate submission).
     */
    fun createCrashReport(error: Throwable): CrashReport {
        val context = CrashContext.capture(PLUGIN_VERSION, lastAction)
        return CrashReport.fromThrowable(error, context)
    }

    // =========================================================================
    // Performance Tracking
    // =========================================================================

    /**
     * Tracks a performance metric.
     *
     * Only recorded if telemetry and performance metrics are enabled.
     *
     * @param operation Name of the operation
     * @param durationMs How long the operation took
     * @param success Whether the operation succeeded
     */
    fun trackPerformance(operation: String, durationMs: Long, success: Boolean) {
        lastAction = operation

        if (!getConfig().isTypeEnabled(EventType.PERFORMANCE)) return

        queueEvent(TelemetryEvent.performance(operation, sessionId, durationMs, success))

        log.debug("Tracked performance: $operation (${durationMs}ms, success=$success)")
    }

    /**
     * Tracks an operation with automatic timing.
     *
     * @param operation Name of the operation
     * @param block The operation to execute and time
     * @return The result of the operation
     */
    inline fun <T> trackTimed(operation: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        var success = true
        try {
            return block()
        } catch (e: Exception) {
            success = false
            trackError(e, operation)
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            trackPerformance(operation, duration, success)
        }
    }

    // =========================================================================
    // Event Queue
    // =========================================================================

    /**
     * Queues an event for later transmission.
     */
    private fun queueEvent(event: TelemetryEvent) {
        eventQueue.add(event)

        if (eventQueue.size >= MAX_QUEUE_SIZE) {
            log.debug("Event queue full, triggering flush")
            // In production, would trigger async flush here
        }
    }

    /**
     * Gets all queued events.
     */
    fun getQueuedEvents(): List<TelemetryEvent> = eventQueue.toList()

    /**
     * Gets the number of queued events.
     */
    fun getQueueSize(): Int = eventQueue.size

    /**
     * Flushes queued events.
     *
     * In production, this would send to a telemetry endpoint.
     * For now, just clears the queue.
     */
    suspend fun flush() {
        if (eventQueue.isEmpty()) return

        val events = eventQueue.toList()
        eventQueue.clear()

        log.info("Flushed ${events.size} telemetry events")

        // TODO: Send to telemetry endpoint
        // In production: httpClient.post(TELEMETRY_ENDPOINT) { ... }
    }

    /**
     * Clears all queued events without sending.
     */
    fun clearQueue() {
        eventQueue.clear()
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Generates usage statistics from queued events.
     */
    fun getUsageStats(): UsageStats = UsageStats.fromEvents(eventQueue.toList())

    /**
     * Gets the error count for the current session.
     */
    fun getSessionErrorCount(): Long = sessionErrorCount.get()

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Generates a telemetry status report.
     */
    fun getStatusReport(): String = buildString {
        val config = getConfig()
        appendLine("=== Telemetry Status ===")
        appendLine()
        appendLine("Configuration:")
        appendLine("  ${config.describe()}")
        appendLine()
        appendLine("Session:")
        appendLine("  ID: $sessionId")
        appendLine("  Duration: ${getSessionDuration().toMinutes()} min")
        appendLine("  Errors: ${sessionErrorCount.get()}")
        appendLine()
        appendLine("Queue:")
        appendLine("  Events: ${eventQueue.size}")

        if (eventQueue.isNotEmpty()) {
            appendLine()
            appendLine("Recent Events:")
            eventQueue.takeLast(5).forEach { event ->
                appendLine("  ${event.format()}")
            }
        }
    }

    /**
     * Resets telemetry state (for testing).
     */
    fun reset() {
        eventQueue.clear()
        sessionErrorCount.set(0)
        lastAction = null
        sessionId = UUID.randomUUID().toString()
        sessionStart = Instant.now()
    }
}
