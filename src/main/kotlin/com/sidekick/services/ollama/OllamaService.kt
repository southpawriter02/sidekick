// =============================================================================
// OllamaService.kt
// =============================================================================
// IntelliJ application service for Ollama integration.
//
// This service wraps [OllamaClient] and provides:
// - Lifecycle management (automatic client creation/disposal)
// - Configuration integration (reads URL from settings)
// - Thread-safe access from multiple coroutines
// - Connection status monitoring
//
// DESIGN NOTES:
// - Application-level service (one instance for the entire IDE)
// - Lazily creates OllamaClient when first configured
// - Recreates client when configuration changes
// - All public methods are thread-safe
//
// USAGE:
// ```kotlin
// val service = OllamaService.getInstance()
// service.configure("http://localhost:11434")
// val models = service.listModels()
// service.chat(request).collect { ... }
// ```
// =============================================================================

package com.sidekick.services.ollama

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.sidekick.models.ConnectionStatus
import com.sidekick.services.ollama.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-level service for Ollama LLM integration.
 *
 * Provides a high-level interface for interacting with a local Ollama instance.
 * Handles client lifecycle, configuration, and provides connection status
 * monitoring for UI feedback.
 *
 * ## Initialization
 *
 * The service starts unconfigured. Call [configure] with the Ollama URL
 * before making any API requests:
 *
 * ```kotlin
 * val service = OllamaService.getInstance()
 * service.configure("http://localhost:11434")
 * ```
 *
 * ## Thread Safety
 *
 * All methods are thread-safe and can be called from any coroutine context.
 * The service uses a mutex to protect client reconfiguration.
 *
 * @see OllamaClient
 */
@Service
class OllamaService : Disposable {

    // -------------------------------------------------------------------------
    // Companion Object - Static Access
    // -------------------------------------------------------------------------
    
    companion object {
        private val LOG = Logger.getInstance(OllamaService::class.java)
        
        /**
         * Gets the singleton instance of the Ollama service.
         *
         * @return The OllamaService application service
         */
        fun getInstance(): OllamaService {
            return ApplicationManager.getApplication().getService(OllamaService::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Instance State
    // -------------------------------------------------------------------------
    
    /**
     * The underlying Ollama HTTP client.
     * Null until [configure] is called.
     */
    private var client: OllamaClient? = null
    
    /**
     * The currently configured Ollama URL.
     * Null if not yet configured.
     */
    private var configuredUrl: String? = null
    
    /**
     * Mutex for thread-safe client reconfiguration.
     */
    private val mutex = Mutex()
    
    /**
     * Current connection status.
     * Updated by [getConnectionStatus] and connection checks.
     */
    @Volatile
    private var currentStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------
    
    /**
     * Configures the service with an Ollama server URL.
     *
     * If the URL is different from the current configuration, the existing
     * client is closed and a new one is created. If the URL is the same,
     * this method does nothing.
     *
     * ## Example
     *
     * ```kotlin
     * service.configure("http://localhost:11434")
     * // or for a remote server:
     * service.configure("http://my-gpu-server:11434")
     * ```
     *
     * @param baseUrl The Ollama server URL (e.g., "http://localhost:11434")
     */
    suspend fun configure(baseUrl: String) = mutex.withLock {
        // Skip if already configured with same URL
        if (baseUrl == configuredUrl && client != null) {
            LOG.debug("Ollama already configured for: $baseUrl")
            return@withLock
        }
        
        LOG.info("Configuring Ollama service for: $baseUrl")
        
        // Close existing client if any
        client?.let { oldClient ->
            LOG.debug("Closing previous Ollama client")
            try {
                oldClient.close()
            } catch (e: Exception) {
                LOG.warn("Error closing previous client: ${e.message}")
            }
        }
        
        // Create new client
        client = OllamaClient(baseUrl)
        configuredUrl = baseUrl
        currentStatus = ConnectionStatus.CONNECTING
        
        LOG.info("Ollama service configured successfully")
    }

    // -------------------------------------------------------------------------
    // Connection Status
    // -------------------------------------------------------------------------
    
    /**
     * Gets the current connection status.
     *
     * This method checks actual connectivity if the service is configured,
     * otherwise returns [ConnectionStatus.NOT_CONFIGURED].
     *
     * @return The current connection status
     */
    suspend fun getConnectionStatus(): ConnectionStatus {
        val localClient = client
        
        if (localClient == null) {
            currentStatus = ConnectionStatus.NOT_CONFIGURED
            return currentStatus
        }
        
        // Actually check the connection
        currentStatus = if (localClient.isConnected()) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.DISCONNECTED
        }
        
        return currentStatus
    }
    
    /**
     * Gets the cached connection status without making a network request.
     *
     * Use this for UI updates where you want instant feedback.
     * Call [getConnectionStatus] periodically to update the cached value.
     *
     * @return The cached connection status
     */
    fun getCachedConnectionStatus(): ConnectionStatus = currentStatus

    // -------------------------------------------------------------------------
    // Model Management
    // -------------------------------------------------------------------------
    
    /**
     * Lists all models installed on the configured Ollama server.
     *
     * @return Result containing the list of models, or an error
     * @throws IllegalStateException if the service is not configured
     */
    suspend fun listModels(): Result<List<OllamaModel>> {
        val localClient = client ?: return Result.failure(
            IllegalStateException("Ollama service not configured. Call configure() first.")
        )
        
        return localClient.listModels()
    }

    // -------------------------------------------------------------------------
    // Chat Completion
    // -------------------------------------------------------------------------
    
    /**
     * Sends a chat request and returns a flow of streaming responses.
     *
     * The returned [Flow] emits [ChatResponse] objects as tokens are generated.
     * Collect the flow to receive responses in real-time.
     *
     * ## Example
     *
     * ```kotlin
     * val request = ChatRequest(
     *     model = "llama3.2",
     *     messages = listOf(ChatMessage.user("Hello, world!"))
     * )
     *
     * service.chat(request).collect { response ->
     *     print(response.message.content)
     * }
     * ```
     *
     * @param request The chat request
     * @return A Flow of ChatResponse objects
     * @throws IllegalStateException if the service is not configured
     */
    fun chat(request: ChatRequest): Flow<ChatResponse> {
        val localClient = client ?: throw IllegalStateException(
            "Ollama service not configured. Call configure() first."
        )
        
        LOG.debug("Starting chat with model: ${request.model}")
        
        return localClient.chat(request)
    }

    // -------------------------------------------------------------------------
    // Disposable Implementation
    // -------------------------------------------------------------------------
    
    /**
     * Disposes of the service and releases all resources.
     *
     * Called automatically by IntelliJ when the application shuts down.
     */
    override fun dispose() {
        LOG.info("Disposing Ollama service")
        
        client?.let { c ->
            try {
                c.close()
            } catch (e: Exception) {
                LOG.warn("Error closing Ollama client during disposal: ${e.message}")
            }
        }
        
        client = null
        configuredUrl = null
        currentStatus = ConnectionStatus.NOT_CONFIGURED
    }
}
