package com.sidekick.llm.lmstudio

import java.time.Instant

/**
 * # LM Studio Models
 *
 * Data structures for LM Studio server integration.
 * Part of Sidekick v0.8.1 LM Studio Connection feature.
 *
 * ## Overview
 *
 * Provides models for:
 * - Server connection configuration
 * - Model information and capabilities
 * - OpenAI-compatible chat completion requests
 * - Tool/function calling support
 *
 * @since 0.8.1
 */

// =============================================================================
// Connection Configuration
// =============================================================================

/**
 * LM Studio server connection configuration.
 *
 * @property host Server hostname
 * @property port Server port (default 1234)
 * @property apiPath API path prefix
 * @property connectionTimeoutMs Connection timeout in milliseconds
 * @property requestTimeoutMs Request timeout in milliseconds
 * @property autoConnect Whether to auto-connect on startup
 * @property autoDiscover Whether to auto-discover LM Studio on network
 */
data class LmStudioConfig(
    val host: String = "localhost",
    val port: Int = 1234,
    val apiPath: String = "/v1",
    val connectionTimeoutMs: Long = 5000,
    val requestTimeoutMs: Long = 120000,
    val autoConnect: Boolean = true,
    val autoDiscover: Boolean = true
) {
    /**
     * Constructs the base URL for API requests.
     */
    val baseUrl: String get() = "http://$host:$port$apiPath"

    /**
     * Constructs the full URL for a specific endpoint.
     */
    fun endpoint(path: String): String = "$baseUrl$path"

    /**
     * Default configuration for local LM Studio.
     */
    companion object {
        val DEFAULT = LmStudioConfig()

        val COMMON_PORTS = listOf(1234, 8080, 5000, 11434)

        /**
         * Creates config for a specific host:port.
         */
        fun forAddress(host: String, port: Int) = LmStudioConfig(host = host, port = port)
    }
}

// =============================================================================
// Model Information
// =============================================================================

/**
 * Available model in LM Studio.
 *
 * @property id Unique model identifier
 * @property name Human-readable model name
 * @property path Local file path (if available)
 * @property size Model file size in bytes
 * @property quantization Quantization type (e.g., Q4_K_M)
 * @property contextLength Maximum context window size
 * @property family Model family (Llama, Mistral, etc.)
 * @property capabilities Set of model capabilities
 * @property isLoaded Whether the model is currently loaded
 */
data class LmStudioModel(
    val id: String,
    val name: String,
    val path: String?,
    val size: Long?,
    val quantization: String?,
    val contextLength: Int,
    val family: ModelFamily,
    val capabilities: Set<ModelCapability>,
    val isLoaded: Boolean
) {
    /**
     * File name from path.
     */
    val fileName: String? get() = path?.substringAfterLast("/")

    /**
     * Human-readable size string.
     */
    val sizeDisplay: String?
        get() = size?.let {
            when {
                it >= 1_000_000_000 -> "${it / 1_000_000_000}GB"
                it >= 1_000_000 -> "${it / 1_000_000}MB"
                else -> "${it / 1_000}KB"
            }
        }

    /**
     * Whether this model supports chat completions.
     */
    val supportsChat: Boolean get() = ModelCapability.CHAT in capabilities

    /**
     * Whether this model supports function calling.
     */
    val supportsFunctionCalling: Boolean get() = ModelCapability.FUNCTION_CALLING in capabilities

    /**
     * Whether this model supports code generation.
     */
    val supportsCode: Boolean get() = ModelCapability.CODE in capabilities

    companion object {
        /**
         * Infers model family from model ID/name.
         */
        fun inferFamily(modelId: String): ModelFamily {
            val lower = modelId.lowercase()
            return when {
                "llama" in lower && "code" in lower -> ModelFamily.CODELLAMA
                "llama" in lower -> ModelFamily.LLAMA
                "mistral" in lower || "mixtral" in lower -> ModelFamily.MISTRAL
                "deepseek" in lower -> ModelFamily.DEEPSEEK
                "qwen" in lower -> ModelFamily.QWEN
                "phi" in lower -> ModelFamily.PHI
                "gemma" in lower -> ModelFamily.GEMMA
                "starcoder" in lower -> ModelFamily.STARCODER
                else -> ModelFamily.OTHER
            }
        }

        /**
         * Infers capabilities from model family and ID.
         */
        fun inferCapabilities(modelId: String, family: ModelFamily): Set<ModelCapability> {
            val caps = mutableSetOf(ModelCapability.CHAT, ModelCapability.COMPLETION)

            // Code-specialized models
            val lower = modelId.lowercase()
            if (family == ModelFamily.CODELLAMA || family == ModelFamily.DEEPSEEK ||
                family == ModelFamily.STARCODER || "code" in lower || "coder" in lower) {
                caps.add(ModelCapability.CODE)
            }

            // Models known to support function calling
            if (family in listOf(ModelFamily.MISTRAL, ModelFamily.LLAMA, ModelFamily.QWEN) ||
                "instruct" in lower || "chat" in lower) {
                caps.add(ModelCapability.FUNCTION_CALLING)
            }

            return caps
        }
    }
}

/**
 * Model family classification.
 */
enum class ModelFamily(val displayName: String) {
    LLAMA("LLaMA"),
    CODELLAMA("Code Llama"),
    MISTRAL("Mistral"),
    DEEPSEEK("DeepSeek"),
    QWEN("Qwen"),
    PHI("Phi"),
    GEMMA("Gemma"),
    STARCODER("StarCoder"),
    OTHER("Other");

    override fun toString(): String = displayName

    companion object {
        fun byName(name: String): ModelFamily {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OTHER
        }
    }
}

/**
 * Model capability flags.
 */
enum class ModelCapability(val displayName: String) {
    CHAT("Chat"),
    COMPLETION("Completion"),
    CODE("Code"),
    EMBEDDING("Embedding"),
    FUNCTION_CALLING("Function Calling"),
    VISION("Vision");

    override fun toString(): String = displayName

    companion object {
        val CODING_ESSENTIAL = setOf(CHAT, CODE, FUNCTION_CALLING)
    }
}

// =============================================================================
// Connection Status
// =============================================================================

/**
 * Connection status for LM Studio server.
 *
 * @property connected Whether currently connected
 * @property serverVersion Server version string
 * @property loadedModel Currently loaded model ID
 * @property lastCheck Timestamp of last connection check
 * @property error Error message if connection failed
 */
data class ConnectionStatus(
    val connected: Boolean,
    val serverVersion: String?,
    val loadedModel: String?,
    val lastCheck: Instant,
    val error: String?
) {
    /**
     * Human-readable status string.
     */
    val displayStatus: String
        get() = when {
            connected && loadedModel != null -> "Connected ($loadedModel)"
            connected -> "Connected (no model loaded)"
            error != null -> "Error: $error"
            else -> "Disconnected"
        }

    /**
     * Status icon for UI.
     */
    val statusIcon: String
        get() = when {
            connected && loadedModel != null -> "🟢"
            connected -> "🟡"
            else -> "🔴"
        }

    /**
     * Whether we should retry connection.
     */
    val shouldRetry: Boolean
        get() = !connected && error != null

    companion object {
        /**
         * Creates a disconnected status.
         */
        fun disconnected(error: String? = null) = ConnectionStatus(
            connected = false,
            serverVersion = null,
            loadedModel = null,
            lastCheck = Instant.now(),
            error = error
        )

        /**
         * Creates a connected status.
         */
        fun connected(serverVersion: String? = null, loadedModel: String? = null) = ConnectionStatus(
            connected = true,
            serverVersion = serverVersion,
            loadedModel = loadedModel,
            lastCheck = Instant.now(),
            error = null
        )
    }
}

// =============================================================================
// Chat Completion (OpenAI-Compatible)
// =============================================================================

/**
 * Chat completion request (OpenAI-compatible format).
 *
 * @property model Model ID to use
 * @property messages Conversation messages
 * @property temperature Sampling temperature (0.0-2.0)
 * @property maxTokens Maximum tokens to generate
 * @property stream Whether to stream the response
 * @property stop Stop sequences
 * @property tools Available tools for function calling
 * @property toolChoice How to select tools
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val stream: Boolean = true,
    val stop: List<String>? = null,
    val tools: List<ToolDefinition>? = null,
    val toolChoice: String? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null
) {
    /**
     * Creates a non-streaming version of this request.
     */
    fun nonStreaming() = copy(stream = false)

    /**
     * Adds a message to the conversation.
     */
    fun withMessage(message: ChatMessage) = copy(messages = messages + message)

    /**
     * Whether this request includes tool definitions.
     */
    val hasTools: Boolean get() = !tools.isNullOrEmpty()

    companion object {
        /**
         * Creates a simple chat request.
         */
        fun simple(model: String, userMessage: String, systemPrompt: String? = null): ChatCompletionRequest {
            val messages = buildList {
                if (systemPrompt != null) {
                    add(ChatMessage.system(systemPrompt))
                }
                add(ChatMessage.user(userMessage))
            }
            return ChatCompletionRequest(model = model, messages = messages)
        }
    }
}

/**
 * Chat message in a conversation.
 *
 * @property role Message role (system, user, assistant, tool)
 * @property content Text content of the message
 * @property toolCalls Tool calls made by assistant
 * @property toolCallId ID of the tool call this message responds to
 * @property name Name for tool or function messages
 */
data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
) {
    /**
     * Whether this is a system message.
     */
    val isSystem: Boolean get() = role == ROLE_SYSTEM

    /**
     * Whether this is a user message.
     */
    val isUser: Boolean get() = role == ROLE_USER

    /**
     * Whether this is an assistant message.
     */
    val isAssistant: Boolean get() = role == ROLE_ASSISTANT

    /**
     * Whether this message contains tool calls.
     */
    val hasToolCalls: Boolean get() = !toolCalls.isNullOrEmpty()

    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)
        fun user(content: String) = ChatMessage(ROLE_USER, content)
        fun assistant(content: String) = ChatMessage(ROLE_ASSISTANT, content)
        fun toolResult(toolCallId: String, content: String) = ChatMessage(
            role = ROLE_TOOL,
            content = content,
            toolCallId = toolCallId
        )
    }
}

/**
 * Chat completion response.
 *
 * @property id Response ID
 * @property model Model used
 * @property choices Response choices
 * @property usage Token usage statistics
 */
data class ChatCompletionResponse(
    val id: String,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: TokenUsage?
) {
    /**
     * The first choice's message content.
     */
    val content: String? get() = choices.firstOrNull()?.message?.content

    /**
     * Tool calls from the first choice.
     */
    val toolCalls: List<ToolCall>? get() = choices.firstOrNull()?.message?.toolCalls

    /**
     * Whether the response contains tool calls.
     */
    val hasToolCalls: Boolean get() = !toolCalls.isNullOrEmpty()

    /**
     * The finish reason of the first choice.
     */
    val finishReason: String? get() = choices.firstOrNull()?.finishReason
}

/**
 * A choice in the completion response.
 */
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    val finishReason: String?
)

/**
 * Token usage statistics.
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// =============================================================================
// Tool/Function Calling
// =============================================================================

/**
 * Tool definition for function calling.
 *
 * @property type Tool type (always "function")
 * @property function Function definition
 */
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
) {
    companion object {
        fun fromFunction(function: FunctionDefinition) = ToolDefinition(function = function)
    }
}

/**
 * Function definition for tool calling.
 *
 * @property name Function name
 * @property description What the function does
 * @property parameters JSON Schema for parameters
 */
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
) {
    companion object {
        /**
         * Creates a simple function definition.
         */
        fun simple(
            name: String,
            description: String,
            properties: Map<String, ParameterProperty>,
            required: List<String> = emptyList()
        ) = FunctionDefinition(
            name = name,
            description = description,
            parameters = mapOf(
                "type" to "object",
                "properties" to properties.mapValues { it.value.toMap() },
                "required" to required
            )
        )
    }
}

/**
 * Parameter property for function definitions.
 */
data class ParameterProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("type", type)
        put("description", description)
        enum?.let { put("enum", it) }
    }
}

/**
 * Tool call made by the assistant.
 *
 * @property id Unique call ID
 * @property type Call type (always "function")
 * @property function Function call details
 */
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

/**
 * Function call details.
 *
 * @property name Function name
 * @property arguments JSON string of arguments
 */
data class FunctionCall(
    val name: String,
    val arguments: String
)

// =============================================================================
// Streaming
// =============================================================================

/**
 * Streaming chunk from SSE response.
 *
 * @property id Chunk ID
 * @property delta Content delta
 * @property finishReason Finish reason (if final chunk)
 */
data class StreamingChunk(
    val id: String,
    val delta: ChatDelta?,
    val finishReason: String?
) {
    /**
     * Whether this is the final chunk.
     */
    val isFinal: Boolean get() = finishReason != null
}

/**
 * Delta content in streaming response.
 */
data class ChatDelta(
    val role: String?,
    val content: String?,
    val toolCalls: List<ToolCallDelta>?
)

/**
 * Tool call delta in streaming response.
 */
data class ToolCallDelta(
    val index: Int,
    val id: String?,
    val type: String?,
    val function: FunctionCallDelta?
)

/**
 * Function call delta in streaming response.
 */
data class FunctionCallDelta(
    val name: String?,
    val arguments: String?
)

// =============================================================================
// Discovery
// =============================================================================

/**
 * Result of server discovery.
 */
data class DiscoveryResult(
    val servers: List<DiscoveredServer>,
    val scanDurationMs: Long
) {
    val hasServers: Boolean get() = servers.isNotEmpty()
}

/**
 * A discovered LM Studio server.
 */
data class DiscoveredServer(
    val host: String,
    val port: Int,
    val serverVersion: String?,
    val loadedModel: String?,
    val responseTimeMs: Long
) {
    val address: String get() = "$host:$port"
}
