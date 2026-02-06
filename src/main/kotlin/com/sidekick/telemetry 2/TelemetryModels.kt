// =============================================================================
// TelemetryModels.kt
// =============================================================================
// Data models for opt-in telemetry in the Sidekick plugin.
//
// This file contains all data contracts used by the telemetry subsystem:
// - TelemetryConfig: Configuration for what telemetry to collect
// - TelemetryEvent: Individual tracked event
// - CrashReport: Anonymized crash information
// - UsageStats: Aggregated usage statistics
//
// PRIVACY NOTES:
// - All telemetry is OPT-IN only (disabled by default)
// - No personally identifiable information (PII) is collected
// - Users have granular control over what is tracked
// - All data is anonymized before transmission
//
// @since v1.0.3
// =============================================================================

package com.sidekick.telemetry

import java.time.Duration
import java.time.Instant
import java.util.UUID

// =============================================================================
// Telemetry Configuration
// =============================================================================

/**
 * Configuration for telemetry collection.
 *
 * Controls what types of telemetry data are collected. All options are
 * disabled by default (opt-in model) to respect user privacy.
 *
 * ## Privacy Model
 * - **Opt-in only**: [enabled] must be explicitly set to `true`
 * - **Granular controls**: Each category can be individually toggled
 * - **No PII**: Only anonymized, aggregate data is collected
 *
 * ## Usage
 * ```kotlin
 * val config = TelemetryConfig(
 *     enabled = true,
 *     crashReporting = true,  // Only report crashes
 *     featureTracking = false // No feature usage
 * )
 * ```
 *
 * @property enabled Master switch - must be true for any telemetry
 * @property anonymousUsage Track general usage patterns (no identifiers)
 * @property crashReporting Report crashes with anonymized stack traces
 * @property featureTracking Track which features are used
 * @property performanceMetrics Collect timing and performance data
 */
data class TelemetryConfig(
    val enabled: Boolean = false, // Opt-in only - disabled by default
    val anonymousUsage: Boolean = true,
    val crashReporting: Boolean = true,
    val featureTracking: Boolean = true,
    val performanceMetrics: Boolean = true
) {
    /**
     * Checks if a specific telemetry type is enabled.
     *
     * @param type The event type to check
     * @return true if both master switch and specific type are enabled
     */
    fun isTypeEnabled(type: EventType): Boolean {
        if (!enabled) return false
        return when (type) {
            EventType.FEATURE_USED -> featureTracking
            EventType.ERROR -> crashReporting
            EventType.PERFORMANCE -> performanceMetrics
            EventType.SESSION -> anonymousUsage
            EventType.FEEDBACK -> enabled // Always with master switch
        }
    }

    /**
     * Creates a config with all telemetry enabled.
     */
    fun enableAll() = copy(
        enabled = true,
        anonymousUsage = true,
        crashReporting = true,
        featureTracking = true,
        performanceMetrics = true
    )

    /**
     * Creates a config with minimal telemetry (crashes only).
     */
    fun crashesOnly() = copy(
        enabled = true,
        anonymousUsage = false,
        crashReporting = true,
        featureTracking = false,
        performanceMetrics = false
    )

    /**
     * Describes what telemetry is enabled.
     */
    fun describe(): String = buildString {
        if (!enabled) {
            append("Telemetry disabled")
            return@buildString
        }
        append("Enabled: ")
        val types = mutableListOf<String>()
        if (anonymousUsage) types.add("usage")
        if (crashReporting) types.add("crashes")
        if (featureTracking) types.add("features")
        if (performanceMetrics) types.add("performance")
        append(if (types.isEmpty()) "none" else types.joinToString())
    }

    companion object {
        /** Disabled configuration (default). */
        val DISABLED = TelemetryConfig()

        /** Full telemetry enabled. */
        val FULL = TelemetryConfig().enableAll()

        /** Only crash reporting. */
        val CRASHES_ONLY = TelemetryConfig().crashesOnly()
    }
}

// =============================================================================
// Event Types
// =============================================================================

/**
 * Categories of telemetry events.
 */
enum class EventType(val displayName: String, val category: String) {
    /**
     * A feature was used by the user.
     * Example: "chat_sent", "code_generated"
     */
    FEATURE_USED("Feature Used", "usage"),

    /**
     * An error or exception occurred.
     * Stack traces are anonymized.
     */
    ERROR("Error", "errors"),

    /**
     * Performance metric recorded.
     * Example: response times, memory usage
     */
    PERFORMANCE("Performance", "performance"),

    /**
     * Session lifecycle event.
     * Example: session start, session end
     */
    SESSION("Session", "sessions"),

    /**
     * User feedback submitted.
     * Only captured if explicitly provided.
     */
    FEEDBACK("Feedback", "feedback");

    /**
     * Gets all types in a category.
     */
    companion object {
        fun byCategory(category: String): List<EventType> =
            entries.filter { it.category == category }
    }
}

// =============================================================================
// Telemetry Event
// =============================================================================

/**
 * A single telemetry event.
 *
 * Represents one tracked action or occurrence. Events are batched
 * and sent periodically rather than immediately.
 *
 * @property id Unique identifier for this event
 * @property type Category of the event
 * @property name Specific event name (e.g., "chat_sent", "code_generated")
 * @property properties String key-value pairs (all values sanitized)
 * @property metrics Numeric measurements
 * @property timestamp When the event occurred
 * @property sessionId Associates event with a session (anonymized)
 */
data class TelemetryEvent(
    val id: String,
    val type: EventType,
    val name: String,
    val properties: Map<String, String>,
    val metrics: Map<String, Double>,
    val timestamp: Instant,
    val sessionId: String
) {
    /**
     * Gets a property value or default.
     */
    fun getProperty(key: String, default: String = ""): String =
        properties.getOrDefault(key, default)

    /**
     * Gets a metric value or default.
     */
    fun getMetric(key: String, default: Double = 0.0): Double =
        metrics.getOrDefault(key, default)

    /**
     * Checks if this event has a specific property.
     */
    fun hasProperty(key: String): Boolean = properties.containsKey(key)

    /**
     * Formats for logging (sanitized).
     */
    fun format(): String =
        "[${type.displayName}] $name (${properties.size} props, ${metrics.size} metrics)"

    companion object {
        /**
         * Creates a feature usage event.
         */
        fun feature(
            name: String,
            sessionId: String,
            properties: Map<String, String> = emptyMap()
        ) = TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.FEATURE_USED,
            name = name,
            properties = properties,
            metrics = emptyMap(),
            timestamp = Instant.now(),
            sessionId = sessionId
        )

        /**
         * Creates a performance event.
         */
        fun performance(
            operation: String,
            sessionId: String,
            durationMs: Long,
            success: Boolean
        ) = TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.PERFORMANCE,
            name = operation,
            properties = mapOf("success" to success.toString()),
            metrics = mapOf("duration_ms" to durationMs.toDouble()),
            timestamp = Instant.now(),
            sessionId = sessionId
        )

        /**
         * Creates an error event (anonymized).
         */
        fun error(
            errorType: String,
            sessionId: String,
            message: String? = null,
            context: String? = null
        ) = TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.ERROR,
            name = errorType,
            properties = mapOfNotNull(
                "message" to message?.take(100),
                "context" to context?.take(50)
            ),
            metrics = emptyMap(),
            timestamp = Instant.now(),
            sessionId = sessionId
        )

        /**
         * Creates a session event.
         */
        fun session(
            action: String,
            sessionId: String,
            durationMs: Long? = null
        ) = TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.SESSION,
            name = action,
            properties = emptyMap(),
            metrics = if (durationMs != null) mapOf("duration_ms" to durationMs.toDouble()) else emptyMap(),
            timestamp = Instant.now(),
            sessionId = sessionId
        )

        /**
         * Helper to create a map excluding null values.
         */
        private fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V?>): Map<K, V> =
            pairs.filter { it.second != null }.associate { it.first to it.second!! }
    }
}

// =============================================================================
// Crash Reporting
// =============================================================================

/**
 * Context information for crash reports.
 *
 * Contains environment details to help diagnose issues without
 * collecting personal information.
 *
 * @property pluginVersion The Sidekick plugin version
 * @property ideVersion JetBrains IDE version
 * @property osName Operating system name (not hostname)
 * @property javaVersion Java runtime version
 * @property lastAction Last user action before crash (if known)
 */
data class CrashContext(
    val pluginVersion: String,
    val ideVersion: String,
    val osName: String,
    val javaVersion: String,
    val lastAction: String?
) {
    /**
     * Formats as a summary string.
     */
    fun format(): String = buildString {
        appendLine("Plugin: $pluginVersion")
        appendLine("IDE: $ideVersion")
        appendLine("OS: $osName")
        appendLine("Java: $javaVersion")
        lastAction?.let { appendLine("Last action: $it") }
    }

    companion object {
        /**
         * Captures the current environment context.
         */
        fun capture(pluginVersion: String, lastAction: String? = null) = CrashContext(
            pluginVersion = pluginVersion,
            ideVersion = System.getProperty("idea.version", "unknown"),
            osName = System.getProperty("os.name", "unknown"),
            javaVersion = System.getProperty("java.version", "unknown"),
            lastAction = lastAction
        )

        /**
         * Empty context for testing.
         */
        val EMPTY = CrashContext(
            pluginVersion = "0.0.0",
            ideVersion = "unknown",
            osName = "unknown",
            javaVersion = "unknown",
            lastAction = null
        )
    }
}

/**
 * An anonymized crash report.
 *
 * Contains exception information with sensitive data removed.
 * File paths and class names are anonymized.
 *
 * @property id Unique identifier for this crash
 * @property exception Exception class name (simple name only)
 * @property message Sanitized error message (truncated)
 * @property stackTrace Anonymized stack trace
 * @property context Environment when crash occurred
 * @property timestamp When the crash occurred
 */
data class CrashReport(
    val id: String,
    val exception: String,
    val message: String,
    val stackTrace: String,
    val context: CrashContext,
    val timestamp: Instant
) {
    /**
     * Gets the first line of the stack trace.
     */
    val topFrame: String
        get() = stackTrace.lines().firstOrNull() ?: "unknown"

    /**
     * Formats for display.
     */
    fun format(): String = buildString {
        appendLine("=== Crash Report ===")
        appendLine("ID: $id")
        appendLine("Exception: $exception")
        appendLine("Message: $message")
        appendLine()
        appendLine("Context:")
        append(context.format())
        appendLine()
        appendLine("Stack Trace:")
        appendLine(stackTrace.take(1000))
    }

    companion object {
        /**
         * Creates a crash report from a throwable.
         *
         * Anonymizes the stack trace by removing file paths.
         */
        fun fromThrowable(
            throwable: Throwable,
            context: CrashContext
        ): CrashReport {
            val stackTrace = throwable.stackTraceToString()
                .lines()
                .take(20) // Limit depth
                .map { line ->
                    // Remove file paths, keep class/method info
                    line.replace(Regex("""\(.*\.kt:\d+\)"""), "(source)")
                        .replace(Regex("""\(.*\.java:\d+\)"""), "(source)")
                }
                .joinToString("\n")

            return CrashReport(
                id = UUID.randomUUID().toString(),
                exception = throwable.javaClass.simpleName,
                message = throwable.message?.take(200) ?: "",
                stackTrace = stackTrace,
                context = context,
                timestamp = Instant.now()
            )
        }
    }
}

// =============================================================================
// Usage Statistics
// =============================================================================

/**
 * Aggregated usage statistics.
 *
 * Contains anonymized, aggregate data about plugin usage. No individual
 * session or user data is included.
 *
 * @property featuresUsed Count of times each feature was used
 * @property averageSessionLength Mean session duration in milliseconds
 * @property totalSessions Total number of sessions recorded
 * @property providerUsage Count of requests per LLM provider
 * @property errorCount Total errors recorded
 */
data class UsageStats(
    val featuresUsed: Map<String, Int>,
    val averageSessionLength: Long,
    val totalSessions: Int,
    val providerUsage: Map<String, Int>,
    val errorCount: Int
) {
    /**
     * Gets usage count for a feature.
     */
    fun getFeatureCount(feature: String): Int =
        featuresUsed.getOrDefault(feature, 0)

    /**
     * Gets the most used features (top N).
     */
    fun topFeatures(n: Int = 5): List<Pair<String, Int>> =
        featuresUsed.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }

    /**
     * Gets total feature usage count.
     */
    val totalFeatureUsage: Int
        get() = featuresUsed.values.sum()

    /**
     * Gets average session length as Duration.
     */
    val averageSessionDuration: Duration
        get() = Duration.ofMillis(averageSessionLength)

    /**
     * Formats as a summary.
     */
    fun format(): String = buildString {
        appendLine("=== Usage Statistics ===")
        appendLine("Sessions: $totalSessions")
        appendLine("Avg session: ${averageSessionDuration.toMinutes()} min")
        appendLine("Feature usage: ${featuresUsed.size} features, $totalFeatureUsage total uses")
        appendLine("Errors: $errorCount")
        if (featuresUsed.isNotEmpty()) {
            appendLine("Top features:")
            topFeatures(3).forEach { (feature, count) ->
                appendLine("  $feature: $count")
            }
        }
    }

    companion object {
        /**
         * Empty statistics.
         */
        val EMPTY = UsageStats(
            featuresUsed = emptyMap(),
            averageSessionLength = 0,
            totalSessions = 0,
            providerUsage = emptyMap(),
            errorCount = 0
        )

        /**
         * Aggregates statistics from multiple events.
         */
        fun fromEvents(events: List<TelemetryEvent>): UsageStats {
            val features = mutableMapOf<String, Int>()
            var errorCount = 0
            val providers = mutableMapOf<String, Int>()
            val sessionDurations = mutableListOf<Long>()

            events.forEach { event ->
                when (event.type) {
                    EventType.FEATURE_USED -> {
                        features[event.name] = features.getOrDefault(event.name, 0) + 1
                        event.getProperty("provider").takeIf { it.isNotEmpty() }?.let {
                            providers[it] = providers.getOrDefault(it, 0) + 1
                        }
                    }
                    EventType.ERROR -> errorCount++
                    EventType.SESSION -> {
                        if (event.name == "end") {
                            event.getMetric("duration_ms").takeIf { it > 0 }?.toLong()?.let {
                                sessionDurations.add(it)
                            }
                        }
                    }
                    else -> {} // Ignore other types
                }
            }

            return UsageStats(
                featuresUsed = features,
                averageSessionLength = if (sessionDurations.isEmpty()) 0 else sessionDurations.average().toLong(),
                totalSessions = sessionDurations.size,
                providerUsage = providers,
                errorCount = errorCount
            )
        }
    }
}
