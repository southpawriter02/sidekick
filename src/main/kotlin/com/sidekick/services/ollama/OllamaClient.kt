// =============================================================================
// OllamaClient.kt
// =============================================================================
// Low-level HTTP client for communicating with the Ollama REST API.
//
// This client handles the raw HTTP communication with Ollama, including:
// - Connection management and health checks
// - Request serialization and response deserialization
// - Streaming response handling for chat completions
//
// DESIGN NOTES:
// - Uses Ktor's CIO engine for pure-Kotlin async HTTP
// - All methods are suspend functions for coroutine integration
// - Streaming uses Kotlin Flow for backpressure-aware token delivery
// - Client instances should be reused (connection pooling)
//
// USAGE:
// ```kotlin
// val client = OllamaClient("http://localhost:11434")
// val models = client.listModels()
// client.chat(request).collect { response -> println(response.message.content) }
// client.close()
// ```
// =============================================================================

package com.sidekick.services.ollama

import com.intellij.openapi.diagnostic.Logger
import com.sidekick.services.ollama.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Low-level HTTP client for the Ollama REST API.
 *
 * This class provides direct access to Ollama's API endpoints with proper
 * error handling and streaming support. It should be wrapped by a higher-level
 * service ([OllamaService]) for integration with IntelliJ's service system.
 *
 * ## Thread Safety
 *
 * The underlying Ktor client is thread-safe and handles connection pooling
 * internally. Multiple coroutines can safely share a single OllamaClient.
 *
 * ## Resource Management
 *
 * Call [close] when done to release HTTP connections. The client can also
 * be used with `use` for automatic cleanup:
 *
 * ```kotlin
 * OllamaClient(url).use { client ->
 *     val models = client.listModels()
 * }
 * ```
 *
 * @property baseUrl The Ollama server URL (e.g., "http://localhost:11434")
 */
class OllamaClient(
    private val baseUrl: String = DEFAULT_BASE_URL
) : AutoCloseable {

    // -------------------------------------------------------------------------
    // Companion Object - Constants & Factory
    // -------------------------------------------------------------------------
    
    companion object {
        private val LOG = Logger.getInstance(OllamaClient::class.java)
        
        /**
         * Default Ollama server URL (standard local installation).
         */
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        
        /**
         * Timeout for establishing connections (5 seconds).
         */
        private const val CONNECT_TIMEOUT_MS = 5_000L
        
        /**
         * Timeout for receiving data on an established connection.
         * Set high to accommodate slow model inference.
         */
        private const val REQUEST_TIMEOUT_MS = 120_000L
        
        /**
         * Timeout for socket read operations (2 minutes).
         * Streaming responses need generous timeouts.
         */
        private const val SOCKET_TIMEOUT_MS = 120_000L
    }

    // -------------------------------------------------------------------------
    // JSON Configuration
    // -------------------------------------------------------------------------
    
    /**
     * JSON serializer configuration for Ollama API.
     * - ignoreUnknownKeys: Ollama may add new fields; don't break on them
     * - isLenient: Handle minor JSON formatting variations
     * - encodeDefaults: Include default values in requests for clarity
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false  // Don't send null/default optional fields
    }

    // -------------------------------------------------------------------------
    // HTTP Client
    // -------------------------------------------------------------------------
    
    /**
     * The Ktor HTTP client instance.
     * Configured with JSON content negotiation and appropriate timeouts.
     */
    private val client = HttpClient(CIO) {
        // Install JSON serialization for automatic request/response handling
        install(ContentNegotiation) {
            json(this@OllamaClient.json)
        }
        
        // Configure timeouts appropriate for LLM inference
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        
        // Log request/response for debugging (only in development)
        // Note: Actual logging happens in our methods, not via Ktor logging plugin
        
        // Configure default request settings
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    // -------------------------------------------------------------------------
    // Connection Management
    // -------------------------------------------------------------------------
    
    /**
     * Checks if the Ollama server is reachable and responding.
     *
     * Performs a lightweight HEAD request to the API root to verify connectivity
     * without incurring the overhead of a full model listing.
     *
     * @return true if Ollama responds successfully, false otherwise
     */
    suspend fun isConnected(): Boolean {
        return try {
            LOG.debug("Checking Ollama connection at: $baseUrl")
            
            // Use /api/tags as a health check - it's lightweight and always available
            val response = client.get("$baseUrl/api/tags")
            val success = response.status.isSuccess()
            
            if (success) {
                LOG.debug("Ollama connection successful")
            } else {
                LOG.warn("Ollama returned non-success status: ${response.status}")
            }
            
            success
        } catch (e: Exception) {
            LOG.debug("Ollama connection check failed: ${e.message}")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Model Management
    // -------------------------------------------------------------------------
    
    /**
     * Lists all models installed on the Ollama server.
     *
     * Calls the `/api/tags` endpoint to retrieve available models.
     *
     * @return Result containing the list of models, or an error if the request failed
     */
    suspend fun listModels(): Result<List<OllamaModel>> {
        return try {
            LOG.info("Fetching model list from Ollama")
            
            val response: ListModelsResponse = client.get("$baseUrl/api/tags").body()
            
            LOG.info("Found ${response.models.size} models: ${response.models.map { it.name }}")
            
            Result.success(response.models)
        } catch (e: Exception) {
            LOG.error("Failed to list models: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Chat Completion
    // -------------------------------------------------------------------------
    
    /**
     * Sends a chat request and returns a flow of streaming responses.
     *
     * The returned [Flow] emits [ChatResponse] objects as tokens are generated.
     * Each response contains a partial message with new content. The final
     * response has `done = true` and includes generation statistics.
     *
     * ## Example Usage
     *
     * ```kotlin
     * val request = ChatRequest(
     *     model = "llama3.2",
     *     messages = listOf(ChatMessage.user("Hello!"))
     * )
     *
     * client.chat(request).collect { response ->
     *     print(response.message.content)  // Print each token
     *     if (response.done) {
     *         println("\n[Generated ${response.evalCount} tokens]")
     *     }
     * }
     * ```
     *
     * ## Error Handling
     *
     * Errors during streaming will cause the flow to throw an exception.
     * Use `catch` operator to handle errors gracefully:
     *
     * ```kotlin
     * client.chat(request)
     *     .catch { e -> emit(errorResponse(e)) }
     *     .collect { ... }
     * ```
     *
     * @param request The chat request containing model, messages, and options
     * @return A Flow of ChatResponse objects, one per streaming chunk
     */
    fun chat(request: ChatRequest): Flow<ChatResponse> = flow {
        LOG.info("Starting chat request to model: ${request.model}")
        LOG.debug("Chat request: ${request.messages.size} messages, stream=${request.stream}")
        
        try {
            // Make streaming POST request to /api/chat
            val response = client.preparePost("$baseUrl/api/chat") {
                setBody(request)
            }.execute { httpResponse ->
                // Check for HTTP-level errors
                if (!httpResponse.status.isSuccess()) {
                    val errorBody = httpResponse.bodyAsText()
                    LOG.error("Chat request failed with status ${httpResponse.status}: $errorBody")
                    throw OllamaClientException(
                        "Chat request failed: ${httpResponse.status}",
                        httpResponse.status.value
                    )
                }
                
                // Parse streaming response (newline-delimited JSON)
                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                
                // Read lines until the channel is closed
                while (!channel.isClosedForRead) {
                    // Read a single line (each line is a complete JSON object)
                    val line = channel.readUTF8Line()
                    
                    // Skip empty lines
                    if (line.isNullOrBlank()) continue
                    
                    try {
                        // Parse the JSON response
                        val chatResponse = json.decodeFromString<ChatResponse>(line)
                        
                        // Emit the response to the flow collector
                        emit(chatResponse)
                        
                        // Log completion when done
                        if (chatResponse.done) {
                            LOG.info("Chat completed: ${chatResponse.evalCount ?: 0} tokens generated")
                        }
                    } catch (e: Exception) {
                        // Log parse errors but continue - might be a malformed line
                        LOG.warn("Failed to parse chat response line: $line", e)
                    }
                }
            }
        } catch (e: OllamaClientException) {
            // Re-throw our custom exceptions
            throw e
        } catch (e: Exception) {
            LOG.error("Chat request failed: ${e.message}", e)
            throw OllamaClientException("Chat request failed: ${e.message}", cause = e)
        }
    }

    // -------------------------------------------------------------------------
    // Resource Management
    // -------------------------------------------------------------------------
    
    /**
     * Closes the HTTP client and releases all resources.
     *
     * After calling this method, the client cannot be used again.
     * All pending requests will be cancelled.
     */
    override fun close() {
        LOG.debug("Closing Ollama client")
        client.close()
    }
}

// =============================================================================
// Exceptions
// =============================================================================

/**
 * Exception thrown when an Ollama API request fails.
 *
 * @property message Human-readable error description
 * @property statusCode HTTP status code (if applicable)
 * @property cause Underlying exception (if any)
 */
class OllamaClientException(
    override val message: String,
    val statusCode: Int? = null,
    override val cause: Throwable? = null
) : Exception(message, cause)
