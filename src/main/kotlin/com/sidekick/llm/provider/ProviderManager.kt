package com.sidekick.llm.provider

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow

/**
 * # Provider Manager
 *
 * Application-level service managing LLM provider backends.
 * Part of Sidekick v0.8.2 Provider Abstraction feature.
 *
 * ## Features
 *
 * - Registers and manages multiple providers (Ollama, LM Studio)
 * - Provider selection by availability, latency, or preference
 * - Unified API for chat, streaming, and embeddings
 * - Health monitoring across all providers
 *
 * @since 0.8.2
 */
@Service(Service.Level.APP)
@State(name = "SidekickProviders", storages = [Storage("sidekick-providers.xml")])
class ProviderManager : PersistentStateComponent<ProviderManager.State> {

    private val logger = Logger.getInstance(ProviderManager::class.java)

    /**
     * Persistent state for provider configuration.
     */
    data class State(
        var activeProviderName: String = "OLLAMA",
        var selectionStrategy: String = "FIRST_AVAILABLE",
        var providerConfigs: MutableMap<String, Boolean> = mutableMapOf(
            "OLLAMA" to true,
            "LM_STUDIO" to true
        )
    ) {
        constructor() : this("OLLAMA", "FIRST_AVAILABLE", mutableMapOf())
    }

    private var state = State()
    private val providers = mutableMapOf<ProviderType, LlmProvider>()
    private var initialized = false
    private val rateLimiter = RateLimiter(RateLimitConfig.DEFAULT)

    companion object {
        /**
         * Gets the service instance.
         */
        fun getInstance(): ProviderManager {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(ProviderManager::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded provider config: active=${state.activeProviderName}")
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Initializes all available providers.
     */
    fun initialize() {
        if (initialized) return

        logger.info("Initializing LLM providers...")

        // Register built-in providers
        registerProvider(OllamaLlmProvider())
        registerProvider(LmStudioLlmProvider())

        initialized = true
        logger.info("Initialized ${providers.size} providers")
    }

    /**
     * Ensures providers are initialized before use.
     */
    private fun ensureInitialized() {
        if (!initialized) initialize()
    }

    /**
     * Registers a provider.
     */
    fun registerProvider(provider: LlmProvider) {
        providers[provider.type] = provider
        logger.info("Registered provider: ${provider.name}")
    }

    /**
     * Unregisters a provider.
     */
    fun unregisterProvider(type: ProviderType) {
        providers.remove(type)
    }

    // =========================================================================
    // Provider Access
    // =========================================================================

    /**
     * Gets the currently active provider.
     */
    fun getActiveProvider(): LlmProvider? {
        ensureInitialized()
        val type = ProviderType.byName(state.activeProviderName) ?: ProviderType.OLLAMA
        return providers[type]
    }

    /**
     * Sets the active provider.
     */
    fun setActiveProvider(type: ProviderType) {
        ensureInitialized()
        if (type in providers) {
            state.activeProviderName = type.name
            logger.info("Active provider set to: ${type.displayName}")
        }
    }

    /**
     * Gets a provider by type.
     */
    fun getProvider(type: ProviderType): LlmProvider? {
        ensureInitialized()
        return providers[type]
    }

    /**
     * Gets all registered providers.
     */
    fun getAllProviders(): List<LlmProvider> {
        ensureInitialized()
        return providers.values.toList()
    }

    /**
     * Gets all currently available providers.
     */
    fun getAvailableProviders(): List<LlmProvider> {
        ensureInitialized()
        return providers.values.filter { it.isAvailable }
    }

    /**
     * Gets all enabled providers.
     */
    fun getEnabledProviders(): List<LlmProvider> {
        ensureInitialized()
        return providers.entries
            .filter { state.providerConfigs[it.key.name] == true }
            .map { it.value }
    }

    // =========================================================================
    // Provider Selection
    // =========================================================================

    /**
     * Gets the best available provider based on selection strategy.
     */
    suspend fun getBestAvailableProvider(): LlmProvider? {
        ensureInitialized()
        val strategy = ProviderSelectionStrategy.valueOf(state.selectionStrategy)

        return when (strategy) {
            ProviderSelectionStrategy.FIRST_AVAILABLE -> {
                getEnabledProviders().firstOrNull { it.checkHealth().healthy }
            }
            ProviderSelectionStrategy.LOWEST_LATENCY -> {
                getEnabledProviders()
                    .mapNotNull { provider ->
                        val health = provider.checkHealth()
                        if (health.healthy) provider to (health.latencyMs ?: Long.MAX_VALUE) else null
                    }
                    .minByOrNull { it.second }
                    ?.first
            }
            ProviderSelectionStrategy.PREFERRED -> {
                getActiveProvider()?.takeIf { it.checkHealth().healthy }
                    ?: getBestAvailableProvider()
            }
            ProviderSelectionStrategy.ROUND_ROBIN -> {
                // Simple implementation - just get first available
                getAvailableProviders().firstOrNull()
            }
        }
    }

    /**
     * Checks health of all providers.
     */
    suspend fun checkAllHealth(): Map<ProviderType, ProviderHealth> {
        ensureInitialized()
        return providers.mapValues { (_, provider) ->
            try {
                provider.checkHealth()
            } catch (e: Exception) {
                ProviderHealth.unhealthy(e.message ?: "Unknown error")
            }
        }
    }

    // =========================================================================
    // Unified API
    // =========================================================================

    /**
     * Sends a chat request to the active or best available provider.
     * Acquires a rate limit permit before making the request.
     */
    suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        ensureInitialized()
        rateLimiter.acquire()
        val provider = getActiveProvider() ?: getBestAvailableProvider()
            ?: throw ProviderException("No providers available")

        return provider.chat(request)
    }

    /**
     * Streams a chat request.
     * Acquires a rate limit permit before the stream begins.
     */
    suspend fun streamChat(request: UnifiedChatRequest): Flow<String> {
        rateLimiter.acquire()
        val provider = getActiveProvider()
            ?: throw ProviderException("No active provider configured")

        return provider.streamChat(request)
    }

    /**
     * Generates embeddings.
     * Acquires a rate limit permit before making the request.
     */
    suspend fun embed(text: String): List<Float> {
        ensureInitialized()
        rateLimiter.acquire()
        val provider = getActiveProvider() ?: getBestAvailableProvider()
            ?: throw ProviderException("No providers available")

        return provider.embed(text)
    }

    /**
     * Lists all models from all providers.
     */
    suspend fun listAllModels(): List<UnifiedModel> {
        ensureInitialized()
        return providers.values.flatMap { provider ->
            try {
                provider.listModels()
            } catch (e: Exception) {
                logger.warn("Failed to list models from ${provider.name}: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Lists models from available providers only.
     */
    suspend fun listAvailableModels(): List<UnifiedModel> {
        ensureInitialized()
        return getAvailableProviders().flatMap { provider ->
            try {
                provider.listModels()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Enables a provider.
     */
    fun enableProvider(type: ProviderType) {
        state.providerConfigs[type.name] = true
    }

    /**
     * Disables a provider.
     */
    fun disableProvider(type: ProviderType) {
        state.providerConfigs[type.name] = false
    }

    /**
     * Checks if a provider is enabled.
     */
    fun isProviderEnabled(type: ProviderType): Boolean {
        return state.providerConfigs[type.name] != false
    }

    /**
     * Sets the selection strategy.
     */
    fun setSelectionStrategy(strategy: ProviderSelectionStrategy) {
        state.selectionStrategy = strategy.name
    }

    /**
     * Gets the current selection strategy.
     */
    fun getSelectionStrategy(): ProviderSelectionStrategy {
        return ProviderSelectionStrategy.valueOf(state.selectionStrategy)
    }

    // =========================================================================
    // Rate Limiting
    // =========================================================================

    /**
     * Updates the rate limiter configuration. Takes effect immediately.
     */
    fun updateRateLimitConfig(config: RateLimitConfig) {
        rateLimiter.updateConfig(config)
        logger.info("Rate limit config updated: ${config.maxRequestsPerMinute} rpm")
    }

    /**
     * Gets the current rate limiter statistics.
     */
    fun getRateLimitStats(): RateLimitStats = rateLimiter.getStats()

    /**
     * Gets the current rate limit configuration.
     */
    fun getRateLimitConfig(): RateLimitConfig = rateLimiter.getConfig()

    /**
     * Resets rate limiter state (counters, window).
     */
    fun resetRateLimiter() {
        rateLimiter.reset()
    }
}

/**
 * Exception for provider errors.
 */
class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
