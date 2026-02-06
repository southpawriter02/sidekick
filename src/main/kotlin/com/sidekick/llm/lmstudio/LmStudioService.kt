package com.sidekick.llm.lmstudio

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * # LM Studio Service
 *
 * Application-level service for connecting to LM Studio servers.
 * Part of Sidekick v0.8.1 LM Studio Connection feature.
 *
 * ## Features
 *
 * - Server connection management
 * - Model listing and selection
 * - OpenAI-compatible chat completions (streaming and non-streaming)
 * - Embeddings support
 * - Auto-discovery of LM Studio servers
 *
 * ## API Compatibility
 *
 * LM Studio exposes an OpenAI-compatible API at /v1/ endpoints:
 * - GET /v1/models - List available models
 * - POST /v1/chat/completions - Chat completion
 * - POST /v1/embeddings - Generate embeddings
 *
 * @since 0.8.1
 */
@Service(Service.Level.APP)
@State(name = "SidekickLmStudio", storages = [Storage("sidekick-lmstudio.xml")])
class LmStudioService : PersistentStateComponent<LmStudioService.State> {

    private val logger = Logger.getInstance(LmStudioService::class.java)

    /**
     * Persistent state for LM Studio configuration.
     */
    data class State(
        var host: String = "localhost",
        var port: Int = 1234,
        var apiPath: String = "/v1",
        var connectionTimeoutMs: Long = 5000,
        var requestTimeoutMs: Long = 120000,
        var autoConnect: Boolean = true,
        var autoDiscover: Boolean = true,
        var preferredModel: String? = null
    ) {
        // No-arg constructor for serialization
        constructor() : this("localhost", 1234, "/v1", 5000, 120000, true, true, null)

        fun toConfig() = LmStudioConfig(
            host = host,
            port = port,
            apiPath = apiPath,
            connectionTimeoutMs = connectionTimeoutMs,
            requestTimeoutMs = requestTimeoutMs,
            autoConnect = autoConnect,
            autoDiscover = autoDiscover
        )

        companion object {
            fun from(config: LmStudioConfig, preferredModel: String? = null) = State(
                host = config.host,
                port = config.port,
                apiPath = config.apiPath,
                connectionTimeoutMs = config.connectionTimeoutMs,
                requestTimeoutMs = config.requestTimeoutMs,
                autoConnect = config.autoConnect,
                autoDiscover = config.autoDiscover,
                preferredModel = preferredModel
            )
        }
    }

    private var state = State()
    private var connectionStatus = ConnectionStatus.disconnected()
    private var cachedModels: List<LmStudioModel>? = null

    companion object {
        /**
         * Gets the service instance.
         */
        fun getInstance(): LmStudioService {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(LmStudioService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded LM Studio config: ${state.host}:${state.port}")
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Current configuration.
     */
    val config: LmStudioConfig get() = state.toConfig()

    /**
     * Current preferred model.
     */
    val preferredModel: String? get() = state.preferredModel

    /**
     * Updates configuration.
     */
    fun updateConfig(config: LmStudioConfig, preferredModel: String? = null) {
        state = State.from(config, preferredModel ?: state.preferredModel)
        cachedModels = null
        logger.info("Updated LM Studio config: ${config.host}:${config.port}")
    }

    /**
     * Sets the preferred model.
     */
    fun setPreferredModel(modelId: String?) {
        state.preferredModel = modelId
    }

    // =========================================================================
    // Connection Management
    // =========================================================================

    /**
     * Gets current connection status.
     */
    fun getStatus(): ConnectionStatus = connectionStatus

    /**
     * Checks connection to LM Studio server.
     *
     * @return Connection status
     */
    suspend fun checkConnection(): ConnectionStatus = withContext(Dispatchers.IO) {
        try {
            val url = URL("${config.baseUrl}/models")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = config.connectionTimeoutMs.toInt()
            connection.readTimeout = config.requestTimeoutMs.toInt()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val loadedModel = parseLoadedModel(responseBody)

                connectionStatus = ConnectionStatus.connected(
                    serverVersion = null,
                    loadedModel = loadedModel
                )
            } else {
                connectionStatus = ConnectionStatus.disconnected("HTTP $responseCode")
            }

            connection.disconnect()
            connectionStatus
        } catch (e: Exception) {
            logger.warn("LM Studio connection check failed: ${e.message}")
            connectionStatus = ConnectionStatus.disconnected(e.message)
            connectionStatus
        }
    }

    /**
     * Attempts to connect to the server.
     */
    suspend fun connect(): Boolean {
        val status = checkConnection()
        return status.connected
    }

    /**
     * Disconnects from the server (clears status).
     */
    fun disconnect() {
        connectionStatus = ConnectionStatus.disconnected()
        cachedModels = null
    }

    // =========================================================================
    // Model Management
    // =========================================================================

    /**
     * Lists available models from the server.
     *
     * @param forceRefresh Whether to bypass cache
     * @return List of available models
     */
    suspend fun listModels(forceRefresh: Boolean = false): List<LmStudioModel> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedModels != null) {
            return@withContext cachedModels!!
        }

        try {
            val url = URL("${config.baseUrl}/models")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = config.connectionTimeoutMs.toInt()

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                cachedModels = parseModelsResponse(responseBody)
                connection.disconnect()
                cachedModels!!
            } else {
                connection.disconnect()
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Failed to list models: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets a specific model by ID.
     */
    suspend fun getModel(modelId: String): LmStudioModel? {
        return listModels().find { it.id == modelId }
    }

    /**
     * Gets the currently loaded model (if any).
     */
    suspend fun getLoadedModel(): LmStudioModel? {
        val status = checkConnection()
        return status.loadedModel?.let { getModel(it) }
    }

    // =========================================================================
    // Chat Completion
    // =========================================================================

    /**
     * Sends a chat completion request (non-streaming).
     *
     * @param request Chat completion request
     * @return Chat completion response
     */
    suspend fun chat(request: ChatCompletionRequest): ChatCompletionResponse = withContext(Dispatchers.IO) {
        val nonStreamRequest = request.nonStreaming()
        val requestBody = buildChatRequestJson(nonStreamRequest)

        val url = URL("${config.baseUrl}/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = config.connectionTimeoutMs.toInt()
        connection.readTimeout = config.requestTimeoutMs.toInt()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        if (connection.responseCode == 200) {
            val responseBody = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseChatResponse(responseBody)
        } else {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            connection.disconnect()
            throw LmStudioException("Chat completion failed: $error")
        }
    }

    /**
     * Sends a chat completion request with streaming.
     *
     * @param request Chat completion request
     * @return Flow of content chunks
     */
    fun streamChat(request: ChatCompletionRequest): Flow<String> = flow {
        val streamRequest = request.copy(stream = true)
        val requestBody = buildChatRequestJson(streamRequest)

        val url = URL("${config.baseUrl}/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.connectTimeout = config.connectionTimeoutMs.toInt()
        connection.readTimeout = config.requestTimeoutMs.toInt()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("data: ")) {
                    val data = line!!.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    val content = parseStreamingContent(data)
                    if (content != null) {
                        emit(content)
                    }
                }
            }

            reader.close()
        } else {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            connection.disconnect()
            throw LmStudioException("Streaming chat failed: $error")
        }

        connection.disconnect()
    }

    /**
     * Simple chat helper method.
     */
    suspend fun simpleChat(userMessage: String, systemPrompt: String? = null): String {
        val model = state.preferredModel ?: listModels().firstOrNull()?.id
            ?: throw LmStudioException("No model available")

        val request = ChatCompletionRequest.simple(model, userMessage, systemPrompt)
        val response = chat(request)
        return response.content ?: ""
    }

    // =========================================================================
    // Embeddings
    // =========================================================================

    /**
     * Generates embeddings for text.
     *
     * @param text Text to embed
     * @param model Model to use (optional)
     * @return Embedding vector
     */
    suspend fun embed(text: String, model: String? = null): List<Float> = withContext(Dispatchers.IO) {
        val modelId = model ?: state.preferredModel ?: listModels().firstOrNull()?.id
            ?: throw LmStudioException("No model available")

        val requestBody = "{\"model\": \"$modelId\", \"input\": \"${escapeJson(text)}\"}"

        val url = URL("${config.baseUrl}/embeddings")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = config.connectionTimeoutMs.toInt()
        connection.readTimeout = config.requestTimeoutMs.toInt()

        connection.outputStream.bufferedWriter().use { it.write(requestBody) }

        if (connection.responseCode == 200) {
            val responseBody = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseEmbeddingResponse(responseBody)
        } else {
            connection.disconnect()
            throw LmStudioException("Embedding failed: HTTP ${connection.responseCode}")
        }
    }

    // =========================================================================
    // Discovery
    // =========================================================================

    /**
     * Discovers LM Studio servers on the local network.
     *
     * @return Discovery result with found servers
     */
    suspend fun discover(): DiscoveryResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val servers = mutableListOf<DiscoveredServer>()

        // Check common ports on localhost
        for (port in LmStudioConfig.COMMON_PORTS) {
            try {
                val checkStart = System.currentTimeMillis()
                val url = URL("http://localhost:$port/v1/models")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1000
                connection.readTimeout = 2000

                if (connection.responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    val loadedModel = parseLoadedModel(responseBody)
                    servers.add(DiscoveredServer(
                        host = "localhost",
                        port = port,
                        serverVersion = null,
                        loadedModel = loadedModel,
                        responseTimeMs = System.currentTimeMillis() - checkStart
                    ))
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Port not available, continue
            }
        }

        DiscoveryResult(
            servers = servers,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    // =========================================================================
    // JSON Parsing (Simple implementation without external libraries)
    // =========================================================================

    private fun buildChatRequestJson(request: ChatCompletionRequest): String {
        val messagesJson = request.messages.joinToString(",") { msg ->
            "{\"role\": \"${msg.role}\", \"content\": \"${escapeJson(msg.content ?: "")}\"}"
        }

        return buildString {
            append("{")
            append("\"model\": \"${request.model}\",")
            append("\"messages\": [$messagesJson],")
            append("\"temperature\": ${request.temperature},")
            append("\"stream\": ${request.stream}")
            request.maxTokens?.let { append(",\"max_tokens\": $it") }
            append("}")
        }
    }

    private fun parseModelsResponse(json: String): List<LmStudioModel> {
        // Simple parsing - in production use kotlinx.serialization
        val models = mutableListOf<LmStudioModel>()

        val dataRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        dataRegex.findAll(json).forEach { match ->
            val id = match.groupValues[1]
            val family = LmStudioModel.inferFamily(id)
            models.add(LmStudioModel(
                id = id,
                name = id.substringAfterLast("/"),
                path = null,
                size = null,
                quantization = extractQuantization(id),
                contextLength = 4096,
                family = family,
                capabilities = LmStudioModel.inferCapabilities(id, family),
                isLoaded = false
            ))
        }

        return models
    }

    private fun parseChatResponse(json: String): ChatCompletionResponse {
        // Simple parsing
        val idMatch = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)
        val contentMatch = "\"content\"\\s*:\\s*\"([^\"]*?)\"".toRegex().find(json)
        val modelMatch = "\"model\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)

        return ChatCompletionResponse(
            id = idMatch?.groupValues?.get(1) ?: "",
            model = modelMatch?.groupValues?.get(1) ?: "",
            choices = listOf(ChatChoice(
                index = 0,
                message = ChatMessage.assistant(unescapeJson(contentMatch?.groupValues?.get(1) ?: "")),
                finishReason = "stop"
            )),
            usage = null
        )
    }

    private fun parseStreamingContent(json: String): String? {
        val contentMatch = "\"content\"\\s*:\\s*\"([^\"]*?)\"".toRegex().find(json)
        return contentMatch?.groupValues?.get(1)?.let { unescapeJson(it) }
    }

    private fun parseLoadedModel(json: String): String? {
        val idMatch = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)
        return idMatch?.groupValues?.get(1)
    }

    private fun parseEmbeddingResponse(json: String): List<Float> {
        // Simple parsing of embedding array
        val embeddingMatch = "\\[([0-9.,\\s-]+)\\]".toRegex().find(json)
        return embeddingMatch?.groupValues?.get(1)
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?: emptyList()
    }

    private fun extractQuantization(modelId: String): String? {
        val patterns = listOf("Q4_K_M", "Q5_K_M", "Q6_K", "Q8_0", "Q4_0", "Q5_0", "F16", "F32")
        return patterns.find { modelId.uppercase().contains(it) }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJson(text: String): String {
        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

/**
 * Exception for LM Studio errors.
 */
class LmStudioException(message: String, cause: Throwable? = null) : Exception(message, cause)
