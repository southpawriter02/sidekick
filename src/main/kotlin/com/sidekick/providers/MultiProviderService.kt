// =============================================================================
// MultiProviderService.kt
// =============================================================================
// Application-level service for managing multiple LLM providers.
//
// Provides:
// - Provider configuration persistence
// - Health checking with caching
// - Automatic failover by priority
// - Provider management (add/remove/update)
//
// @since v1.0.5
// =============================================================================

package com.sidekick.providers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level service for managing multiple LLM providers.
 *
 * Supports automatic failover between providers based on health and priority.
 *
 * ## Usage
 * ```kotlin
 * val service = MultiProviderService.getInstance()
 *
 * // Get best available provider
 * val providerId = service.getBestProvider()
 *
 * // Test a provider connection
 * val result = service.testConnection("openai")
 * ```
 *
 * ## Priority System
 * Providers are selected by priority (lowest number = highest priority).
 * When the default provider is unavailable, the system falls back to
 * the next healthy provider by priority.
 */
@Service(Service.Level.APP)
@State(name = "SidekickProviders", storages = [Storage("sidekick-providers.xml")])
class MultiProviderService : PersistentStateComponent<MultiProviderService.State> {

    private val log = Logger.getInstance(MultiProviderService::class.java)

    /**
     * Persisted state for provider configurations.
     */
    data class State(
        var providers: MutableMap<String, ProviderConfigData> = mutableMapOf(),
        var defaultProvider: String = "ollama",
        var fallbackEnabled: Boolean = true
    )

    /**
     * Serializable provider config data for persistence.
     */
    data class ProviderConfigData(
        var type: String = "ollama",
        var enabled: Boolean = true,
        var priority: Int = 1,
        var host: String = "localhost",
        var port: Int = 11434,
        var apiKey: String = "",
        var baseUrl: String = "",
        var organization: String? = null,
        var endpoint: String = "",
        var deploymentId: String = "",
        var apiVersion: String = "",
        var name: String = "",
        var headers: MutableMap<String, String> = mutableMapOf()
    ) {
        /**
         * Converts to ProviderConfig.
         */
        fun toProviderConfig(): ProviderConfig = when (type) {
            "ollama" -> ProviderConfig.Ollama(enabled, priority, host, port)
            "lmstudio" -> ProviderConfig.LmStudio(enabled, priority, host, port)
            "openai" -> ProviderConfig.OpenAI(enabled, priority, apiKey, organization, baseUrl.ifBlank { ProviderConfig.OpenAI.DEFAULT_BASE_URL })
            "anthropic" -> ProviderConfig.Anthropic(enabled, priority, apiKey, baseUrl.ifBlank { ProviderConfig.Anthropic.DEFAULT_BASE_URL })
            "azure" -> ProviderConfig.AzureOpenAI(enabled, priority, endpoint, apiKey, deploymentId, apiVersion.ifBlank { ProviderConfig.AzureOpenAI.DEFAULT_API_VERSION })
            "custom" -> ProviderConfig.Custom(enabled, priority, name, baseUrl, apiKey.ifBlank { null }, headers)
            else -> ProviderConfig.Ollama(enabled, priority, host, port)
        }

        companion object {
            /**
             * Creates from ProviderConfig.
             */
            fun from(config: ProviderConfig): ProviderConfigData = when (config) {
                is ProviderConfig.Ollama -> ProviderConfigData(
                    type = "ollama", enabled = config.enabled, priority = config.priority,
                    host = config.host, port = config.port
                )
                is ProviderConfig.LmStudio -> ProviderConfigData(
                    type = "lmstudio", enabled = config.enabled, priority = config.priority,
                    host = config.host, port = config.port
                )
                is ProviderConfig.OpenAI -> ProviderConfigData(
                    type = "openai", enabled = config.enabled, priority = config.priority,
                    apiKey = config.apiKey, organization = config.organization, baseUrl = config.baseUrl
                )
                is ProviderConfig.Anthropic -> ProviderConfigData(
                    type = "anthropic", enabled = config.enabled, priority = config.priority,
                    apiKey = config.apiKey, baseUrl = config.baseUrl, apiVersion = config.apiVersion
                )
                is ProviderConfig.AzureOpenAI -> ProviderConfigData(
                    type = "azure", enabled = config.enabled, priority = config.priority,
                    endpoint = config.endpoint, apiKey = config.apiKey,
                    deploymentId = config.deploymentId, apiVersion = config.apiVersion
                )
                is ProviderConfig.Custom -> ProviderConfigData(
                    type = "custom", enabled = config.enabled, priority = config.priority,
                    name = config.name, baseUrl = config.baseUrl, apiKey = config.apiKey ?: "",
                    headers = config.headers.toMutableMap()
                )
            }
        }
    }

    private var myState = State()

    /**
     * Health check cache: providerId -> (healthy, timestamp)
     */
    private val healthCache = ConcurrentHashMap<String, Pair<Boolean, Instant>>()

    /**
     * Provider info cache.
     */
    private val infoCache = ConcurrentHashMap<String, ProviderInfo>()

    companion object {
        /**
         * Health cache TTL (5 minutes).
         */
        val HEALTH_CACHE_TTL: Duration = Duration.ofMinutes(5)

        /**
         * Gets the singleton instance.
         */
        fun getInstance(): MultiProviderService {
            return ApplicationManager.getApplication().getService(MultiProviderService::class.java)
        }
    }

    init {
        // Initialize with defaults if empty
        if (myState.providers.isEmpty()) {
            myState.providers["ollama"] = ProviderConfigData.from(ProviderConfig.Ollama())
            myState.providers["lmstudio"] = ProviderConfigData.from(ProviderConfig.LmStudio())
        }
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // Ensure defaults exist
        if (myState.providers.isEmpty()) {
            myState.providers["ollama"] = ProviderConfigData.from(ProviderConfig.Ollama())
            myState.providers["lmstudio"] = ProviderConfigData.from(ProviderConfig.LmStudio())
        }
    }

    // =========================================================================
    // Provider Selection
    // =========================================================================

    /**
     * Gets the best available provider.
     *
     * Returns the default provider if healthy, otherwise falls back to
     * the next healthy provider by priority (if fallback is enabled).
     *
     * @return Provider ID
     */
    suspend fun getBestProvider(): String {
        // Try default first
        if (isHealthy(myState.defaultProvider)) {
            return myState.defaultProvider
        }

        // Fallback to others by priority
        if (myState.fallbackEnabled) {
            val fallback = getProvidersByPriority()
                .filter { it.first != myState.defaultProvider }
                .firstOrNull { isHealthy(it.first) }

            if (fallback != null) {
                log.info("Falling back from ${myState.defaultProvider} to ${fallback.first}")
                return fallback.first
            }
        }

        log.warn("No healthy providers available, returning default: ${myState.defaultProvider}")
        return myState.defaultProvider
    }

    /**
     * Gets providers sorted by priority.
     */
    fun getProvidersByPriority(): List<Pair<String, ProviderConfig>> {
        return myState.providers.entries
            .map { it.key to it.value.toProviderConfig() }
            .filter { it.second.enabled }
            .sortedBy { it.second.priority }
    }

    /**
     * Gets the default provider ID.
     */
    fun getDefaultProvider(): String = myState.defaultProvider

    /**
     * Sets the default provider.
     */
    fun setDefaultProvider(providerId: String) {
        if (myState.providers.containsKey(providerId)) {
            myState.defaultProvider = providerId
            log.info("Default provider set to: $providerId")
        } else {
            log.warn("Cannot set default to unknown provider: $providerId")
        }
    }

    /**
     * Gets whether fallback is enabled.
     */
    fun isFallbackEnabled(): Boolean = myState.fallbackEnabled

    /**
     * Sets whether fallback is enabled.
     */
    fun setFallbackEnabled(enabled: Boolean) {
        myState.fallbackEnabled = enabled
    }

    // =========================================================================
    // Provider Management
    // =========================================================================

    /**
     * Gets a provider configuration.
     */
    fun getProvider(providerId: String): ProviderConfig? {
        return myState.providers[providerId]?.toProviderConfig()
    }

    /**
     * Gets all provider configurations.
     */
    fun getAllProviders(): Map<String, ProviderConfig> {
        return myState.providers.mapValues { it.value.toProviderConfig() }
    }

    /**
     * Gets all provider IDs.
     */
    fun getProviderIds(): Set<String> = myState.providers.keys

    /**
     * Updates a provider configuration.
     */
    fun updateProvider(providerId: String, config: ProviderConfig) {
        myState.providers[providerId] = ProviderConfigData.from(config)
        invalidateHealthCache(providerId)
        log.info("Updated provider: $providerId")
    }

    /**
     * Adds a custom provider.
     */
    fun addCustomProvider(name: String, config: ProviderConfig.Custom): Boolean {
        val id = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        if (myState.providers.containsKey(id)) {
            log.warn("Provider already exists: $id")
            return false
        }
        myState.providers[id] = ProviderConfigData.from(config)
        log.info("Added custom provider: $id")
        return true
    }

    /**
     * Removes a provider.
     */
    fun removeProvider(providerId: String): Boolean {
        if (providerId in listOf("ollama", "lmstudio")) {
            log.warn("Cannot remove built-in provider: $providerId")
            return false
        }
        val removed = myState.providers.remove(providerId) != null
        if (removed) {
            healthCache.remove(providerId)
            infoCache.remove(providerId)
            if (myState.defaultProvider == providerId) {
                myState.defaultProvider = "ollama"
            }
            log.info("Removed provider: $providerId")
        }
        return removed
    }

    /**
     * Enables or disables a provider.
     */
    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        myState.providers[providerId]?.let { data ->
            data.enabled = enabled
            invalidateHealthCache(providerId)
            log.info("Provider $providerId ${if (enabled) "enabled" else "disabled"}")
        }
    }

    // =========================================================================
    // Health Checking
    // =========================================================================

    /**
     * Tests a provider connection.
     *
     * @param providerId Provider to test
     * @return Test result with success/failure and latency
     */
    suspend fun testConnection(providerId: String): ConnectionTestResult {
        val config = myState.providers[providerId]?.toProviderConfig()
            ?: return ConnectionTestResult.failed("Provider not found: $providerId")

        val startTime = System.currentTimeMillis()

        return try {
            val result = when (config) {
                is ProviderConfig.Ollama -> testOllamaConnection(config)
                is ProviderConfig.LmStudio -> testLmStudioConnection(config)
                is ProviderConfig.OpenAI -> testOpenAIConnection(config)
                is ProviderConfig.Anthropic -> testAnthropicConnection(config)
                is ProviderConfig.AzureOpenAI -> testAzureConnection(config)
                is ProviderConfig.Custom -> testCustomConnection(config)
            }

            val latency = System.currentTimeMillis() - startTime
            val finalResult = result.copy(latencyMs = latency)

            // Update cache
            healthCache[providerId] = Pair(finalResult.success, Instant.now())

            finalResult
        } catch (e: Exception) {
            log.warn("Connection test failed for $providerId", e)
            healthCache[providerId] = Pair(false, Instant.now())
            ConnectionTestResult.failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Checks if a provider is healthy (cached).
     */
    suspend fun isHealthy(providerId: String): Boolean {
        val cached = healthCache[providerId]
        if (cached != null) {
            val (healthy, timestamp) = cached
            if (Duration.between(timestamp, Instant.now()) < HEALTH_CACHE_TTL) {
                return healthy
            }
        }

        // Cache expired or missing, test connection
        return testConnection(providerId).success
    }

    /**
     * Invalidates health cache for a provider.
     */
    fun invalidateHealthCache(providerId: String) {
        healthCache.remove(providerId)
        infoCache.remove(providerId)
    }

    /**
     * Invalidates all health caches.
     */
    fun invalidateAllHealthCache() {
        healthCache.clear()
        infoCache.clear()
    }

    /**
     * Gets provider info (with health status).
     */
    suspend fun getProviderInfo(providerId: String): ProviderInfo? {
        val config = getProvider(providerId) ?: return null

        // Check cache
        val cached = infoCache[providerId]
        val healthCached = healthCache[providerId]
        if (cached != null && healthCached != null) {
            val (healthy, timestamp) = healthCached
            if (Duration.between(timestamp, Instant.now()) < HEALTH_CACHE_TTL) {
                return cached.copy(healthy = healthy, lastHealthCheck = timestamp)
            }
        }

        // Refresh
        val testResult = testConnection(providerId)
        val info = ProviderInfo(
            id = providerId,
            config = config,
            healthy = testResult.success,
            lastHealthCheck = Instant.now(),
            models = testResult.models,
            capabilities = getCapabilities(config)
        )

        infoCache[providerId] = info
        return info
    }

    /**
     * Gets all provider info.
     */
    suspend fun getAllProviderInfo(): List<ProviderInfo> {
        return myState.providers.keys.mapNotNull { getProviderInfo(it) }
    }

    // =========================================================================
    // Provider-Specific Connection Tests
    // =========================================================================

    private suspend fun testOllamaConnection(config: ProviderConfig.Ollama): ConnectionTestResult {
        // In production: HTTP GET to config.getApiUrl("tags")
        log.debug("Testing Ollama connection: ${config.resolveBaseUrl()}")
        return ConnectionTestResult.connected("Ollama ready", models = listOf("llama2", "codellama", "mistral"))
    }

    private suspend fun testLmStudioConnection(config: ProviderConfig.LmStudio): ConnectionTestResult {
        // In production: HTTP GET to config.resolveBaseUrl() + "/models"
        log.debug("Testing LM Studio connection: ${config.resolveBaseUrl()}")
        return ConnectionTestResult.connected("LM Studio ready", models = listOf("local-model"))
    }

    private suspend fun testOpenAIConnection(config: ProviderConfig.OpenAI): ConnectionTestResult {
        if (!config.hasApiKey) {
            return ConnectionTestResult.failed("API key not configured")
        }
        // In production: HTTP GET to /v1/models with auth headers
        log.debug("Testing OpenAI connection: ${config.resolveBaseUrl()}")
        return ConnectionTestResult.connected("OpenAI ready", models = listOf("gpt-4", "gpt-4o", "gpt-3.5-turbo"))
    }

    private suspend fun testAnthropicConnection(config: ProviderConfig.Anthropic): ConnectionTestResult {
        if (!config.hasApiKey) {
            return ConnectionTestResult.failed("API key not configured")
        }
        // In production: HTTP POST to /v1/messages (test message)
        log.debug("Testing Anthropic connection: ${config.resolveBaseUrl()}")
        return ConnectionTestResult.connected("Anthropic ready", models = listOf("claude-3-opus", "claude-3-sonnet"))
    }

    private suspend fun testAzureConnection(config: ProviderConfig.AzureOpenAI): ConnectionTestResult {
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            return ConnectionTestResult.failed(errors.joinToString(", "))
        }
        // In production: HTTP GET to deployment endpoint
        log.debug("Testing Azure OpenAI connection: ${config.resolveBaseUrl()}")
        return ConnectionTestResult.connected("Azure OpenAI ready", models = listOf(config.deploymentId))
    }

    private suspend fun testCustomConnection(config: ProviderConfig.Custom): ConnectionTestResult {
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            return ConnectionTestResult.failed(errors.joinToString(", "))
        }
        // In production: HTTP GET to /v1/models
        log.debug("Testing custom provider connection: ${config.resolveBaseUrl()}")
        return ConnectionTestResult.connected("${config.name} ready")
    }

    // =========================================================================
    // Capabilities
    // =========================================================================

    /**
     * Gets capabilities for a provider config.
     */
    fun getCapabilities(config: ProviderConfig): ProviderCapabilities = when (config) {
        is ProviderConfig.Ollama -> ProviderCapabilities.BASIC
        is ProviderConfig.LmStudio -> ProviderCapabilities.BASIC
        is ProviderConfig.OpenAI -> ProviderCapabilities.FULL
        is ProviderConfig.Anthropic -> ProviderCapabilities(
            supportsStreaming = true,
            supportsFunctionCalling = true,
            supportsVision = true,
            supportsEmbeddings = false,
            maxContextLength = 200000,
            costPerMillionTokens = 15f
        )
        is ProviderConfig.AzureOpenAI -> ProviderCapabilities.FULL
        is ProviderConfig.Custom -> ProviderCapabilities.UNKNOWN
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Gets a status report of all providers.
     */
    suspend fun getStatusReport(): String = buildString {
        appendLine("=== Provider Status Report ===")
        appendLine()
        appendLine("Default Provider: ${myState.defaultProvider}")
        appendLine("Fallback Enabled: ${myState.fallbackEnabled}")
        appendLine()

        val providers = getAllProviderInfo()
        appendLine("Providers (${providers.size}):")
        providers.sortedBy { it.config.priority }.forEach { info ->
            val status = if (info.healthy) "✓" else "✗"
            val enabled = if (info.config.enabled) "enabled" else "disabled"
            val isDefault = if (info.id == myState.defaultProvider) " [DEFAULT]" else ""
            appendLine("  $status ${info.displayName} ($enabled, priority ${info.config.priority})$isDefault")
            if (info.models.isNotEmpty()) {
                appendLine("    Models: ${info.models.take(3).joinToString()}")
            }
        }
    }

    /**
     * Resets to default configuration.
     */
    fun reset() {
        myState = State()
        myState.providers["ollama"] = ProviderConfigData.from(ProviderConfig.Ollama())
        myState.providers["lmstudio"] = ProviderConfigData.from(ProviderConfig.LmStudio())
        invalidateAllHealthCache()
        log.info("Provider configuration reset to defaults")
    }
}
