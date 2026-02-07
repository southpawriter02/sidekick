// =============================================================================
// OllamaModels.kt
// =============================================================================
// Data models for the Ollama REST API.
//
// These Kotlin data classes map directly to the JSON structures used by
// Ollama's API (https://github.com/ollama/ollama/blob/main/docs/api.md).
// We use kotlinx.serialization for JSON encoding/decoding.
//
// DESIGN NOTES:
// - All models are immutable (data classes with val properties)
// - Optional fields use nullable types with defaults
// - @SerialName maps Kotlin naming to JSON snake_case
// - We only model the fields we actually use to keep things simple
//
// API ENDPOINTS COVERED:
// - GET  /api/tags      -> ListModelsResponse
// - POST /api/chat      -> ChatRequest / ChatResponse (streaming)
// - POST /api/generate  -> GenerateRequest / GenerateResponse (future)
// =============================================================================

package com.sidekick.services.ollama.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Model Management
// =============================================================================

/**
 * Represents an Ollama model installed on the local system.
 *
 * Returned by the `/api/tags` endpoint as part of [ListModelsResponse].
 *
 * @property name The model identifier (e.g., "llama3.2:latest", "codellama:7b")
 * @property modifiedAt ISO 8601 timestamp of last modification
 * @property size Model size in bytes
 * @property digest SHA256 digest of the model
 * @property details Optional detailed model information
 */
@Serializable
data class OllamaModel(
    val name: String,
    
    @SerialName("modified_at")
    val modifiedAt: String,
    
    val size: Long,
    
    val digest: String,
    
    val details: ModelDetails? = null
)

/**
 * Detailed information about a model's architecture and quantization.
 *
 * @property format Model format (e.g., "gguf")
 * @property family Model family (e.g., "llama")
 * @property families List of model families this model belongs to
 * @property parameterSize Human-readable parameter count (e.g., "7B", "13B")
 * @property quantizationLevel Quantization scheme (e.g., "Q4_0", "Q8_0")
 */
@Serializable
data class ModelDetails(
    val format: String? = null,
    
    val family: String? = null,
    
    val families: List<String>? = null,
    
    @SerialName("parameter_size")
    val parameterSize: String? = null,
    
    @SerialName("quantization_level")
    val quantizationLevel: String? = null
)

/**
 * Response from the `/api/tags` endpoint listing all installed models.
 *
 * @property models List of installed Ollama models
 */
@Serializable
data class ListModelsResponse(
    val models: List<OllamaModel>
)

// =============================================================================
// Chat API
// =============================================================================

/**
 * A single message in a chat conversation.
 *
 * Used in both [ChatRequest] (to send history) and [ChatResponse] (to receive).
 *
 * @property role The message author: "system", "user", or "assistant"
 * @property content The message text content
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        // Role constants for type-safety
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        
        /**
         * Creates a system message (sets initial context/behavior).
         */
        fun system(content: String) = ChatMessage(ROLE_SYSTEM, content)
        
        /**
         * Creates a user message.
         */
        fun user(content: String) = ChatMessage(ROLE_USER, content)
        
        /**
         * Creates an assistant message (for including history).
         */
        fun assistant(content: String) = ChatMessage(ROLE_ASSISTANT, content)
    }
}

/**
 * Optional parameters for controlling model generation behavior.
 *
 * All fields are optional - Ollama uses sensible defaults if not specified.
 *
 * @property temperature Controls randomness (0.0 = deterministic, 1.0+ = creative)
 * @property topP Nucleus sampling threshold (0.0-1.0)
 * @property topK Limits vocabulary to top K tokens
 * @property numPredict Maximum tokens to generate (-1 for unlimited)
 * @property stop List of stop sequences that halt generation
 */
@Serializable
data class ChatOptions(
    val temperature: Double? = null,
    
    @SerialName("top_p")
    val topP: Double? = null,
    
    @SerialName("top_k")
    val topK: Int? = null,
    
    @SerialName("num_predict")
    val numPredict: Int? = null,
    
    val stop: List<String>? = null
)

/**
 * Request body for the `/api/chat` endpoint.
 *
 * Sends a conversation to Ollama for completion. When [stream] is true (default),
 * the response is a series of newline-delimited JSON objects with partial content.
 *
 * @property model Name of the model to use (e.g., "llama3.2")
 * @property messages Conversation history as a list of messages
 * @property stream Whether to stream the response (default: true)
 * @property options Optional generation parameters
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val options: ChatOptions? = null
)

/**
 * Response from the `/api/chat` endpoint.
 *
 * When streaming, multiple responses are sent - each contains a partial message.
 * The final response has [done] = true and includes timing statistics.
 *
 * @property model The model that generated this response
 * @property createdAt ISO 8601 timestamp of response creation
 * @property message The (partial or complete) assistant message
 * @property done Whether this is the final response in the stream
 * @property totalDuration Total inference time in nanoseconds (final response only)
 * @property loadDuration Model load time in nanoseconds (final response only)
 * @property promptEvalCount Number of tokens in the prompt (final response only)
 * @property evalCount Number of tokens generated (final response only)
 * @property evalDuration Generation time in nanoseconds (final response only)
 */
@Serializable
data class ChatResponse(
    val model: String,
    
    @SerialName("created_at")
    val createdAt: String,
    
    val message: ChatMessage,
    
    val done: Boolean,
    
    // Statistics (only present in final response when done=true)
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    
    @SerialName("eval_count")
    val evalCount: Int? = null,
    
    @SerialName("eval_duration")
    val evalDuration: Long? = null
)

// =============================================================================
// Connection Status
// =============================================================================
// ConnectionStatus enum has been moved to com.sidekick.models.ConnectionStatus
// for provider-agnostic usage across the codebase.
