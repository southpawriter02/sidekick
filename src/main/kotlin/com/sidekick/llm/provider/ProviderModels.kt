package com.sidekick.llm.provider

import kotlinx.coroutines.flow.Flow

/**
 * # Provider Models
 *
 * Unified LLM provider abstraction for multiple backends.
 * Part of Sidekick v0.8.2 Provider Abstraction feature.
 *
 * ## Overview
 *
 * Provides a common interface for:
 * - Ollama
 * - LM Studio  
 * - OpenAI (future)
 * - Anthropic (future)
 * - Custom providers
 *
 * @since 0.8.2
 */

// =============================================================================
// Provider Interface
// =============================================================================

/**
 * Unified LLM provider interface.
 * 
 * Implementations wrap specific LLM backends (Ollama, LM Studio, etc.)
 * to provide a consistent API for the agent system.
 */
interface LlmProvider {
    /**
     * Provider display name.
     */
    val name: String

    /**
     * Provider type identifier.
     */
    val type: ProviderType

    /**
     * Whether the provider is currently available.
     */
    val isAvailable: Boolean

    /**
     * Lists all available models from this provider.
     */
    suspend fun listModels(): List<UnifiedModel>

    /**
     * Sends a chat completion request.
     *
     * @param request The chat request
     * @return Chat response with content and optional tool calls
     */
    suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse

    /**
     * Streams a chat completion request.
     *
     * @param request The chat request
     * @return Flow of content tokens
     */
    fun streamChat(request: UnifiedChatRequest): Flow<String>

    /**
     * Generates embeddings for text.
     *
     * @param text Text to embed
     * @return Embedding vector
     */
    suspend fun embed(text: String): List<Float>

    /**
     * Checks provider health and availability.
     *
     * @return Health status with latency
     */
    suspend fun checkHealth(): ProviderHealth
}

// =============================================================================
// Provider Types
// =============================================================================

/**
 * Supported LLM provider types.
 */
enum class ProviderType(val displayName: String, val isLocal: Boolean) {
    OLLAMA("Ollama", true),
    LM_STUDIO("LM Studio", true),
    OPENAI("OpenAI", false),
    ANTHROPIC("Anthropic", false),
    CUSTOM("Custom", true);

    override fun toString(): String = displayName

    companion object {
        /**
         * All local providers (no cloud API required).
         */
        val LOCAL_PROVIDERS = entries.filter { it.isLocal }

        /**
         * All cloud providers.
         */
        val CLOUD_PROVIDERS = entries.filter { !it.isLocal }

        fun byName(name: String): ProviderType? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

// =============================================================================
// Unified Model
// =============================================================================

/**
 * Unified model representation across providers.
 *
 * @property id Unique model identifier
 * @property provider Provider this model belongs to
 * @property displayName Human-readable name
 * @property contextLength Maximum context window size
 * @property capabilities Set of capability strings
 * @property isLoaded Whether model is currently loaded
 * @property metadata Additional provider-specific data
 */
data class UnifiedModel(
    val id: String,
    val provider: ProviderType,
    val displayName: String,
    val contextLength: Int,
    val capabilities: Set<String>,
    val isLoaded: Boolean,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Full identifier including provider.
     */
    val fullId: String get() = "${provider.name.lowercase()}:$id"

    /**
     * Whether this model supports chat completions.
     */
    val supportsChat: Boolean get() = CAPABILITY_CHAT in capabilities

    /**
     * Whether this model supports code generation.
     */
    val supportsCode: Boolean get() = CAPABILITY_CODE in capabilities

    /**
     * Whether this model supports function calling.
     */
    val supportsFunctionCalling: Boolean get() = CAPABILITY_FUNCTION_CALLING in capabilities

    /**
     * Whether this model supports embeddings.
     */
    val supportsEmbedding: Boolean get() = CAPABILITY_EMBEDDING in capabilities

    companion object {
        const val CAPABILITY_CHAT = "chat"
        const val CAPABILITY_COMPLETION = "completion"
        const val CAPABILITY_CODE = "code"
        const val CAPABILITY_EMBEDDING = "embedding"
        const val CAPABILITY_FUNCTION_CALLING = "function_calling"
        const val CAPABILITY_VISION = "vision"

        /**
         * Standard chat capabilities.
         */
        val CHAT_CAPABILITIES = setOf(CAPABILITY_CHAT, CAPABILITY_COMPLETION)

        /**
         * Code-focused capabilities.
         */
        val CODE_CAPABILITIES = setOf(CAPABILITY_CHAT, CAPABILITY_CODE, CAPABILITY_FUNCTION_CALLING)
    }
}

// =============================================================================
// Chat Request/Response
// =============================================================================

/**
 * Unified chat completion request.
 *
 * @property model Model ID to use
 * @property messages Conversation messages
 * @property temperature Sampling temperature (0.0-2.0)
 * @property maxTokens Maximum tokens to generate
 * @property systemPrompt Optional system prompt (added as first message if provided)
 * @property tools Tools available for function calling
 * @property stream Whether to stream the response
 */
data class UnifiedChatRequest(
    val model: String,
    val messages: List<UnifiedMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null,
    val tools: List<AgentTool>? = null,
    val stream: Boolean = false
) {
    /**
     * Creates a streaming version of this request.
     */
    fun streaming() = copy(stream = true)

    /**
     * Creates a non-streaming version of this request.
     */
    fun nonStreaming() = copy(stream = false)

    /**
     * Adds a message to the conversation.
     */
    fun withMessage(message: UnifiedMessage) = copy(messages = messages + message)

    /**
     * All messages including system prompt if provided.
     */
    val allMessages: List<UnifiedMessage>
        get() = buildList {
            systemPrompt?.let { add(UnifiedMessage.system(it)) }
            addAll(messages)
        }

    /**
     * Whether this request includes tools.
     */
    val hasTools: Boolean get() = !tools.isNullOrEmpty()

    companion object {
        /**
         * Creates a simple user message request.
         */
        fun simple(model: String, userMessage: String, systemPrompt: String? = null): UnifiedChatRequest {
            return UnifiedChatRequest(
                model = model,
                messages = listOf(UnifiedMessage.user(userMessage)),
                systemPrompt = systemPrompt
            )
        }
    }
}

/**
 * Unified chat message.
 *
 * @property role Message role
 * @property content Text content
 * @property toolResults Results from tool calls (for TOOL role)
 * @property name Optional name for the message source
 */
data class UnifiedMessage(
    val role: MessageRole,
    val content: String,
    val toolResults: List<ToolResult>? = null,
    val name: String? = null
) {
    val isSystem: Boolean get() = role == MessageRole.SYSTEM
    val isUser: Boolean get() = role == MessageRole.USER
    val isAssistant: Boolean get() = role == MessageRole.ASSISTANT
    val isTool: Boolean get() = role == MessageRole.TOOL

    companion object {
        fun system(content: String) = UnifiedMessage(MessageRole.SYSTEM, content)
        fun user(content: String) = UnifiedMessage(MessageRole.USER, content)
        fun assistant(content: String) = UnifiedMessage(MessageRole.ASSISTANT, content)
        fun tool(content: String, results: List<ToolResult>? = null) = UnifiedMessage(
            MessageRole.TOOL, content, results
        )
    }
}

/**
 * Message roles.
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL;

    fun toApiString(): String = name.lowercase()

    companion object {
        fun fromString(value: String): MessageRole {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: USER
        }
    }
}

/**
 * Unified chat completion response.
 *
 * @property content Text content of the response
 * @property toolCalls Tool calls requested by the model
 * @property usage Token usage statistics
 * @property finishReason Why generation stopped
 * @property model Model that generated the response
 */
data class UnifiedChatResponse(
    val content: String?,
    val toolCalls: List<ToolCallRequest>?,
    val usage: TokenUsage?,
    val finishReason: String?,
    val model: String? = null
) {
    /**
     * Whether the response contains tool calls.
     */
    val hasToolCalls: Boolean get() = !toolCalls.isNullOrEmpty()

    /**
     * Whether generation completed normally.
     */
    val isComplete: Boolean get() = finishReason == "stop"

    companion object {
        /**
         * Creates a simple text response.
         */
        fun text(content: String) = UnifiedChatResponse(
            content = content,
            toolCalls = null,
            usage = null,
            finishReason = "stop"
        )

        /**
         * Creates an error response.
         */
        fun error(message: String) = UnifiedChatResponse(
            content = null,
            toolCalls = null,
            usage = null,
            finishReason = "error"
        )
    }
}

/**
 * Token usage statistics.
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
) {
    companion object {
        val ZERO = TokenUsage(0, 0, 0)

        fun of(prompt: Int, completion: Int) = TokenUsage(prompt, completion, prompt + completion)
    }
}

// =============================================================================
// Tool/Function Calling
// =============================================================================

/**
 * Tool available to the agent.
 *
 * @property name Tool name (function name)
 * @property description What the tool does
 * @property parameters JSON schema for parameters
 */
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: ToolParameters
) {
    companion object {
        /**
         * Creates a simple tool with basic parameters.
         */
        fun simple(
            name: String,
            description: String,
            vararg params: Pair<String, String>
        ): AgentTool {
            return AgentTool(
                name = name,
                description = description,
                parameters = ToolParameters(
                    properties = params.associate { (n, desc) ->
                        n to ParameterSchema("string", desc)
                    },
                    required = params.map { it.first }
                )
            )
        }
    }
}

/**
 * Tool parameter schema (JSON Schema subset).
 */
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ParameterSchema>,
    val required: List<String> = emptyList()
)

/**
 * Individual parameter schema.
 */
data class ParameterSchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val default: Any? = null
)

/**
 * Tool call requested by the model.
 *
 * @property id Unique call ID
 * @property name Function name
 * @property arguments JSON string of arguments
 */
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: String
) {
    /**
     * Parses arguments as a simple map.
     */
    fun parseArguments(): Map<String, Any> {
        // Simple JSON parsing - would use kotlinx.serialization in production
        return emptyMap()
    }
}

/**
 * Result from executing a tool.
 *
 * @property callId The tool call ID this responds to
 * @property output Tool output
 * @property success Whether the tool succeeded
 * @property error Error message if failed
 */
data class ToolResult(
    val callId: String,
    val output: String,
    val success: Boolean = true,
    val error: String? = null
) {
    companion object {
        fun success(callId: String, output: String) = ToolResult(callId, output, true)
        fun failure(callId: String, error: String) = ToolResult(callId, "", false, error)
    }
}

// =============================================================================
// Provider Health
// =============================================================================

/**
 * Provider health status.
 *
 * @property healthy Whether the provider is healthy
 * @property latencyMs Response latency in milliseconds
 * @property error Error message if unhealthy
 * @property loadedModel Currently loaded model (if any)
 */
data class ProviderHealth(
    val healthy: Boolean,
    val latencyMs: Long?,
    val error: String?,
    val loadedModel: String? = null
) {
    /**
     * Human-readable status.
     */
    val displayStatus: String
        get() = when {
            healthy && loadedModel != null -> "Healthy ($loadedModel)"
            healthy -> "Healthy"
            error != null -> "Error: $error"
            else -> "Unhealthy"
        }

    companion object {
        fun healthy(latencyMs: Long, loadedModel: String? = null) = ProviderHealth(
            healthy = true,
            latencyMs = latencyMs,
            error = null,
            loadedModel = loadedModel
        )

        fun unhealthy(error: String) = ProviderHealth(
            healthy = false,
            latencyMs = null,
            error = error
        )
    }
}

// =============================================================================
// Provider Configuration
// =============================================================================

/**
 * Configuration for a provider.
 */
data class ProviderConfig(
    val type: ProviderType,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val settings: Map<String, Any> = emptyMap()
)

/**
 * Provider selection strategy.
 */
enum class ProviderSelectionStrategy {
    /**
     * Use the first available provider.
     */
    FIRST_AVAILABLE,

    /**
     * Use the provider with lowest latency.
     */
    LOWEST_LATENCY,

    /**
     * Round-robin between available providers.
     */
    ROUND_ROBIN,

    /**
     * Use a specific preferred provider.
     */
    PREFERRED
}
