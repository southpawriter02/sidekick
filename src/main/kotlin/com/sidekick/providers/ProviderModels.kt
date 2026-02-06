// =============================================================================
// ProviderModels.kt
// =============================================================================
// Data models for multi-provider LLM support.
//
// This file contains all data contracts for the provider subsystem:
// - ProviderConfig: Sealed class with configs for each provider type
// - ProviderCapabilities: Feature support matrix
// - ProviderInfo: Runtime provider metadata
// - ConnectionTestResult: Health check result
//
// PROVIDERS SUPPORTED:
// - Local: Ollama, LM Studio
// - Cloud: OpenAI, Anthropic, Azure OpenAI
// - Custom: Any OpenAI-compatible endpoint
//
// @since v1.0.5
// =============================================================================

package com.sidekick.providers

import java.time.Instant

// =============================================================================
// Provider Configuration (Sealed Class)
// =============================================================================

/**
 * Configuration for an LLM provider.
 *
 * This sealed class represents all supported provider types with their
 * specific configuration options. Each provider has:
 * - [enabled]: Whether the provider is active
 * - [priority]: Selection priority (lower = higher priority)
 *
 * ## Provider Types
 * - [Ollama]: Local Ollama instance
 * - [LmStudio]: Local LM Studio instance
 * - [OpenAI]: OpenAI API
 * - [Anthropic]: Anthropic Claude API
 * - [AzureOpenAI]: Azure OpenAI Service
 * - [Custom]: Any OpenAI-compatible endpoint
 *
 * ## Priority System
 * When the default provider is unavailable, the system falls back to
 * enabled providers in priority order (lowest number first).
 */
sealed class ProviderConfig {
    /**
     * Whether this provider is enabled.
     */
    abstract val enabled: Boolean

    /**
     * Selection priority (lower = higher priority).
     */
    abstract val priority: Int

    /**
     * Gets the provider type name.
     */
    abstract val typeName: String

    /**
     * Creates a copy with enabled state toggled.
     */
    abstract fun withEnabled(enabled: Boolean): ProviderConfig

    /**
     * Validates the configuration.
     * @return List of validation errors (empty if valid)
     */
    abstract fun validate(): List<String>

    /**
     * Gets the base URL for API requests.
     */
    abstract fun resolveBaseUrl(): String

    // =========================================================================
    // Ollama - Local LLM Server
    // =========================================================================

    /**
     * Configuration for Ollama (local LLM server).
     *
     * @property enabled Whether Ollama is enabled
     * @property priority Selection priority (default: 1 - highest)
     * @property host Hostname (default: localhost)
     * @property port Port number (default: 11434)
     */
    data class Ollama(
        override val enabled: Boolean = true,
        override val priority: Int = 1,
        val host: String = "localhost",
        val port: Int = 11434
    ) : ProviderConfig() {
        override val typeName = "Ollama"

        override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

        override fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (host.isBlank()) errors.add("Host cannot be blank")
            if (port !in 1..65535) errors.add("Port must be between 1 and 65535")
            return errors
        }

        override fun resolveBaseUrl() = "http://$host:$port"

        /**
         * Gets the API URL for a specific endpoint.
         */
        fun getApiUrl(endpoint: String) = "${resolveBaseUrl()}/api/$endpoint"
    }

    // =========================================================================
    // LM Studio - Local LLM Server
    // =========================================================================

    /**
     * Configuration for LM Studio (local LLM server).
     *
     * @property enabled Whether LM Studio is enabled
     * @property priority Selection priority (default: 2)
     * @property host Hostname (default: localhost)
     * @property port Port number (default: 1234)
     */
    data class LmStudio(
        override val enabled: Boolean = true,
        override val priority: Int = 2,
        val host: String = "localhost",
        val port: Int = 1234
    ) : ProviderConfig() {
        override val typeName = "LM Studio"

        override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

        override fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (host.isBlank()) errors.add("Host cannot be blank")
            if (port !in 1..65535) errors.add("Port must be between 1 and 65535")
            return errors
        }

        override fun resolveBaseUrl() = "http://$host:$port/v1"
    }

    // =========================================================================
    // OpenAI - Cloud API
    // =========================================================================

    /**
     * Configuration for OpenAI API.
     *
     * @property enabled Whether OpenAI is enabled (default: false)
     * @property priority Selection priority (default: 3)
     * @property apiKey OpenAI API key
     * @property organization Optional organization ID
     * @property baseUrl API base URL (for proxies)
     */
    data class OpenAI(
        override val enabled: Boolean = false,
        override val priority: Int = 3,
        val apiKey: String = "",
        val organization: String? = null,
        val baseUrl: String = DEFAULT_BASE_URL
    ) : ProviderConfig() {
        override val typeName = "OpenAI"

        override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

        override fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (enabled && apiKey.isBlank()) errors.add("API key is required")
            if (baseUrl.isBlank()) errors.add("Base URL cannot be blank")
            return errors
        }

        override fun resolveBaseUrl() = baseUrl

        /**
         * Checks if API key is configured.
         */
        val hasApiKey: Boolean get() = apiKey.isNotBlank()

        /**
         * Gets headers for API requests.
         */
        fun getHeaders(): Map<String, String> = buildMap {
            put("Authorization", "Bearer $apiKey")
            organization?.let { put("OpenAI-Organization", it) }
        }

        companion object {
            const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        }
    }

    // =========================================================================
    // Anthropic - Cloud API
    // =========================================================================

    /**
     * Configuration for Anthropic Claude API.
     *
     * @property enabled Whether Anthropic is enabled (default: false)
     * @property priority Selection priority (default: 4)
     * @property apiKey Anthropic API key
     * @property baseUrl API base URL
     * @property apiVersion API version header
     */
    data class Anthropic(
        override val enabled: Boolean = false,
        override val priority: Int = 4,
        val apiKey: String = "",
        val baseUrl: String = DEFAULT_BASE_URL,
        val apiVersion: String = DEFAULT_API_VERSION
    ) : ProviderConfig() {
        override val typeName = "Anthropic"

        override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

        override fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (enabled && apiKey.isBlank()) errors.add("API key is required")
            if (baseUrl.isBlank()) errors.add("Base URL cannot be blank")
            return errors
        }

        override fun resolveBaseUrl() = baseUrl

        /**
         * Checks if API key is configured.
         */
        val hasApiKey: Boolean get() = apiKey.isNotBlank()

        /**
         * Gets headers for API requests.
         */
        fun getHeaders(): Map<String, String> = mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to apiVersion,
            "Content-Type" to "application/json"
        )

        companion object {
            const val DEFAULT_BASE_URL = "https://api.anthropic.com"
            const val DEFAULT_API_VERSION = "2024-01-01"
        }
    }

    // =========================================================================
    // Azure OpenAI - Cloud API
    // =========================================================================

    /**
     * Configuration for Azure OpenAI Service.
     *
     * @property enabled Whether Azure is enabled (default: false)
     * @property priority Selection priority (default: 5)
     * @property endpoint Azure resource endpoint
     * @property apiKey Azure API key
     * @property deploymentId Model deployment ID
     * @property apiVersion API version
     */
    data class AzureOpenAI(
        override val enabled: Boolean = false,
        override val priority: Int = 5,
        val endpoint: String = "",
        val apiKey: String = "",
        val deploymentId: String = "",
        val apiVersion: String = DEFAULT_API_VERSION
    ) : ProviderConfig() {
        override val typeName = "Azure OpenAI"

        override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

        override fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (enabled) {
                if (endpoint.isBlank()) errors.add("Endpoint is required")
                if (apiKey.isBlank()) errors.add("API key is required")
                if (deploymentId.isBlank()) errors.add("Deployment ID is required")
            }
            return errors
        }

        override fun resolveBaseUrl() = "$endpoint/openai/deployments/$deploymentId"

        /**
         * Gets the full URL for chat completions.
         */
        fun getChatCompletionsUrl() = "${resolveBaseUrl()}/chat/completions?api-version=$apiVersion"

        /**
         * Gets headers for API requests.
         */
        fun getHeaders(): Map<String, String> = mapOf(
            "api-key" to apiKey,
            "Content-Type" to "application/json"
        )

        companion object {
            const val DEFAULT_API_VERSION = "2024-02-01"
        }
    }

    // =========================================================================
    // Custom - OpenAI-Compatible Endpoint
    // =========================================================================

    /**
     * Configuration for custom OpenAI-compatible provider.
     *
     * @property enabled Whether this provider is enabled
     * @property priority Selection priority (default: 10)
     * @property name Display name for this provider
     * @property baseUrl API base URL
     * @property apiKey Optional API key
     * @property headers Additional HTTP headers
     */
    data class Custom(
        override val enabled: Boolean = false,
        override val priority: Int = 10,
        val name: String,
        val baseUrl: String,
        val apiKey: String? = null,
        val headers: Map<String, String> = emptyMap()
    ) : ProviderConfig() {
        override val typeName = "Custom ($name)"

        override fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

        override fun validate(): List<String> {
            val errors = mutableListOf<String>()
            if (name.isBlank()) errors.add("Name is required")
            if (baseUrl.isBlank()) errors.add("Base URL is required")
            return errors
        }

        override fun resolveBaseUrl() = baseUrl

        /**
         * Gets all headers including API key.
         */
        fun getAllHeaders(): Map<String, String> = buildMap {
            putAll(headers)
            apiKey?.let { put("Authorization", "Bearer $it") }
        }
    }

    companion object {
        /**
         * Default provider configurations.
         */
        val DEFAULTS: Map<String, ProviderConfig> = mapOf(
            "ollama" to Ollama(),
            "lmstudio" to LmStudio()
        )

        /**
         * Gets all provider type names.
         */
        val ALL_TYPES = listOf("Ollama", "LM Studio", "OpenAI", "Anthropic", "Azure OpenAI", "Custom")
    }
}

// =============================================================================
// Provider Capabilities
// =============================================================================

/**
 * Capabilities supported by a provider/model.
 *
 * @property supportsStreaming Whether streaming responses are supported
 * @property supportsFunctionCalling Whether function/tool calling is supported
 * @property supportsVision Whether image inputs are supported
 * @property supportsEmbeddings Whether embeddings generation is supported
 * @property maxContextLength Maximum context window in tokens
 * @property costPerMillionTokens Cost per million tokens (null for local/free)
 */
data class ProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsFunctionCalling: Boolean,
    val supportsVision: Boolean,
    val supportsEmbeddings: Boolean,
    val maxContextLength: Int,
    val costPerMillionTokens: Float?
) {
    /**
     * Checks if this provider supports all required capabilities.
     */
    fun meetsRequirements(
        streaming: Boolean = false,
        functionCalling: Boolean = false,
        vision: Boolean = false,
        minContext: Int = 0
    ): Boolean {
        return (!streaming || supportsStreaming) &&
               (!functionCalling || supportsFunctionCalling) &&
               (!vision || supportsVision) &&
               (maxContextLength >= minContext)
    }

    /**
     * Checks if this is a free/local provider.
     */
    val isFree: Boolean get() = costPerMillionTokens == null

    /**
     * Formats capabilities for display.
     */
    fun format(): String = buildString {
        val features = mutableListOf<String>()
        if (supportsStreaming) features.add("streaming")
        if (supportsFunctionCalling) features.add("functions")
        if (supportsVision) features.add("vision")
        if (supportsEmbeddings) features.add("embeddings")
        append(features.joinToString(", "))
        append(" | ${maxContextLength / 1000}K context")
        costPerMillionTokens?.let { append(" | \$$it/1M tokens") }
    }

    companion object {
        /**
         * Full capabilities (GPT-4 class).
         */
        val FULL = ProviderCapabilities(
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsVision = true,
            supportsEmbeddings = true,
            maxContextLength = 128000,
            costPerMillionTokens = 10f
        )

        /**
         * Basic capabilities (local models).
         */
        val BASIC = ProviderCapabilities(
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsVision = false,
            supportsEmbeddings = true,
            maxContextLength = 8192,
            costPerMillionTokens = null
        )

        /**
         * Unknown/minimal capabilities.
         */
        val UNKNOWN = ProviderCapabilities(
            supportsStreaming = true,
            supportsFunctionCalling = false,
            supportsVision = false,
            supportsEmbeddings = false,
            maxContextLength = 4096,
            costPerMillionTokens = null
        )
    }
}

// =============================================================================
// Provider Info
// =============================================================================

/**
 * Runtime information about a provider.
 *
 * @property id Provider identifier
 * @property config Provider configuration
 * @property healthy Whether the provider is currently healthy
 * @property lastHealthCheck When health was last checked
 * @property models Available models (if known)
 * @property capabilities Provider capabilities
 */
data class ProviderInfo(
    val id: String,
    val config: ProviderConfig,
    val healthy: Boolean,
    val lastHealthCheck: Instant?,
    val models: List<String>,
    val capabilities: ProviderCapabilities
) {
    /**
     * Gets the display name.
     */
    val displayName: String get() = config.typeName

    /**
     * Checks if provider is enabled and healthy.
     */
    val isAvailable: Boolean get() = config.enabled && healthy

    /**
     * Time since last health check.
     */
    fun getHealthCheckAge(): Long? =
        lastHealthCheck?.let { java.time.Duration.between(it, Instant.now()).toMillis() }

    /**
     * Formats for display.
     */
    fun format(): String = buildString {
        append(displayName)
        append(if (config.enabled) " [enabled]" else " [disabled]")
        append(if (healthy) " ✓" else " ✗")
        append(" (priority: ${config.priority})")
    }

    companion object {
        /**
         * Creates info from config with unknown health.
         */
        fun fromConfig(id: String, config: ProviderConfig) = ProviderInfo(
            id = id,
            config = config,
            healthy = false,
            lastHealthCheck = null,
            models = emptyList(),
            capabilities = ProviderCapabilities.UNKNOWN
        )
    }
}

// =============================================================================
// Connection Test Result
// =============================================================================

/**
 * Result of a provider connection test.
 *
 * @property success Whether the connection succeeded
 * @property message Human-readable result message
 * @property latencyMs Response latency in milliseconds
 * @property models Available models (if retrieved)
 * @property error Error details if failed
 */
data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long? = null,
    val models: List<String> = emptyList(),
    val error: String? = null
) {
    /**
     * Formats for display.
     */
    fun format(): String = buildString {
        append(if (success) "✓ " else "✗ ")
        append(message)
        latencyMs?.let { append(" (${it}ms)") }
        if (models.isNotEmpty()) {
            append(" - ${models.size} models available")
        }
    }

    companion object {
        /**
         * Creates a successful result.
         */
        fun connected(message: String = "Connected", latencyMs: Long? = null, models: List<String> = emptyList()) =
            ConnectionTestResult(true, message, latencyMs, models)

        /**
         * Creates a failed result.
         */
        fun failed(error: String) =
            ConnectionTestResult(false, "Connection failed", error = error)

        /**
         * Creates a timeout result.
         */
        fun timeout() =
            ConnectionTestResult(false, "Connection timed out", error = "Timeout")
    }
}
