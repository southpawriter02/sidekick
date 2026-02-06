package com.sidekick.llm.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * # Ollama LLM Provider
 *
 * LlmProvider implementation for Ollama backend.
 * Part of Sidekick v0.8.2 Provider Abstraction feature.
 *
 * Provides a unified interface to the Ollama local LLM server.
 *
 * @since 0.8.2
 */
class OllamaLlmProvider : LlmProvider {

    override val name: String = "Ollama"
    override val type: ProviderType = ProviderType.OLLAMA

    // Connection state
    private var connected = false
    private var lastHealthCheck: ProviderHealth? = null

    override val isAvailable: Boolean
        get() = lastHealthCheck?.healthy ?: false

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Ollama server configuration.
     */
    data class OllamaConfig(
        val host: String = "localhost",
        val port: Int = 11434,
        val connectionTimeoutMs: Long = 5000,
        val requestTimeoutMs: Long = 120000
    ) {
        val baseUrl: String get() = "http://$host:$port"

        companion object {
            val DEFAULT = OllamaConfig()
        }
    }

    private var config = OllamaConfig.DEFAULT

    fun updateConfig(newConfig: OllamaConfig) {
        config = newConfig
    }

    // =========================================================================
    // Models
    // =========================================================================

    override suspend fun listModels(): List<UnifiedModel> {
        // Implementation would call Ollama API: GET /api/tags
        // For now, return placeholder showing API structure
        return try {
            val url = java.net.URL("${config.baseUrl}/api/tags")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = config.connectionTimeoutMs.toInt()

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parseModelsResponse(response)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseModelsResponse(json: String): List<UnifiedModel> {
        // Parse Ollama model list format
        val models = mutableListOf<UnifiedModel>()

        val nameRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
        nameRegex.findAll(json).forEach { match ->
            val name = match.groupValues[1]
            models.add(UnifiedModel(
                id = name,
                provider = ProviderType.OLLAMA,
                displayName = name.substringBefore(":"),
                contextLength = inferContextLength(name),
                capabilities = inferCapabilities(name),
                isLoaded = false
            ))
        }

        return models
    }

    private fun inferContextLength(modelName: String): Int {
        return when {
            "llama3" in modelName.lowercase() -> 8192
            "mistral" in modelName.lowercase() -> 8192
            "mixtral" in modelName.lowercase() -> 32768
            "codellama" in modelName.lowercase() -> 16384
            else -> 4096
        }
    }

    private fun inferCapabilities(modelName: String): Set<String> {
        val caps = mutableSetOf(UnifiedModel.CAPABILITY_CHAT, UnifiedModel.CAPABILITY_COMPLETION)
        val lower = modelName.lowercase()

        if ("code" in lower || "coder" in lower || "codellama" in lower) {
            caps.add(UnifiedModel.CAPABILITY_CODE)
        }

        if ("instruct" in lower || "chat" in lower) {
            caps.add(UnifiedModel.CAPABILITY_FUNCTION_CALLING)
        }

        return caps
    }

    // =========================================================================
    // Chat
    // =========================================================================

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        return try {
            val url = java.net.URL("${config.baseUrl}/api/chat")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = config.connectionTimeoutMs.toInt()
            connection.readTimeout = config.requestTimeoutMs.toInt()

            val requestBody = buildOllamaChatRequest(request)
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parseOllamaChatResponse(response)
            } else {
                UnifiedChatResponse.error("HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            UnifiedChatResponse.error(e.message ?: "Unknown error")
        }
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<String> {
        // Streaming implementation would use chunked response parsing
        return emptyFlow()
    }

    private fun buildOllamaChatRequest(request: UnifiedChatRequest): String {
        val messages = request.allMessages.joinToString(",") { msg ->
            """{"role": "${msg.role.toApiString()}", "content": "${escapeJson(msg.content)}"}"""
        }

        return buildString {
            append("{")
            append(""""model": "${request.model}",""")
            append(""""messages": [$messages],""")
            append(""""stream": false""")
            request.maxTokens?.let { append(""","options": {"num_predict": $it}""") }
            append("}")
        }
    }

    private fun parseOllamaChatResponse(json: String): UnifiedChatResponse {
        val contentMatch = """"content"\s*:\s*"([^"]*?)"""".toRegex().find(json)
        val content = contentMatch?.groupValues?.get(1)?.let { unescapeJson(it) } ?: ""

        return UnifiedChatResponse(
            content = content,
            toolCalls = null,
            usage = null,
            finishReason = "stop"
        )
    }

    // =========================================================================
    // Embeddings
    // =========================================================================

    override suspend fun embed(text: String): List<Float> {
        return try {
            val url = java.net.URL("${config.baseUrl}/api/embeddings")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = config.connectionTimeoutMs.toInt()

            val requestBody = """{"model": "nomic-embed-text", "prompt": "${escapeJson(text)}"}"""
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parseEmbeddingResponse(response)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseEmbeddingResponse(json: String): List<Float> {
        val embeddingMatch = """\[([0-9.,\s-]+)\]""".toRegex().find(json)
        return embeddingMatch?.groupValues?.get(1)
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?: emptyList()
    }

    // =========================================================================
    // Health
    // =========================================================================

    override suspend fun checkHealth(): ProviderHealth {
        return try {
            val startTime = System.currentTimeMillis()
            val url = java.net.URL("${config.baseUrl}/api/tags")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000

            val healthy = connection.responseCode == 200
            val latency = System.currentTimeMillis() - startTime

            connection.disconnect()

            lastHealthCheck = if (healthy) {
                connected = true
                ProviderHealth.healthy(latency)
            } else {
                connected = false
                ProviderHealth.unhealthy("HTTP ${connection.responseCode}")
            }

            lastHealthCheck!!
        } catch (e: Exception) {
            connected = false
            lastHealthCheck = ProviderHealth.unhealthy(e.message ?: "Connection failed")
            lastHealthCheck!!
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

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
