// =============================================================================
// ChatHistory.kt
// =============================================================================
// Data models for chat history persistence.
//
// This includes:
// - ChatSession - a complete conversation
// - ChatHistoryMessage - a single message in the history
// - Serialization support for JSON persistence
//
// DESIGN NOTES:
// - Immutable data classes for thread safety
// - JSON serialization for file-based storage
// - Timestamps for ordering and display
// =============================================================================

package com.sidekick.history

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/**
 * Represents a complete chat session/conversation.
 *
 * @property id Unique session identifier
 * @property title Display title (auto-generated or user-defined)
 * @property messages List of messages in chronological order
 * @property createdAt When the session was created
 * @property updatedAt When the session was last updated
 * @property projectPath Associated project path (for filtering)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatSession(
    @JsonProperty("id")
    val id: String = UUID.randomUUID().toString(),
    
    @JsonProperty("title")
    val title: String = "New Chat",
    
    @JsonProperty("messages")
    val messages: List<ChatHistoryMessage> = emptyList(),
    
    @JsonProperty("createdAt")
    val createdAt: Long = Instant.now().toEpochMilli(),
    
    @JsonProperty("updatedAt")
    val updatedAt: Long = Instant.now().toEpochMilli(),
    
    @JsonProperty("projectPath")
    val projectPath: String? = null
) {
    companion object {
        /**
         * Maximum title length for display.
         */
        const val MAX_TITLE_LENGTH = 50
        
        /**
         * Creates a new session with an auto-generated title from the first message.
         */
        fun create(
            firstMessage: String,
            projectPath: String? = null
        ): ChatSession {
            val title = generateTitle(firstMessage)
            return ChatSession(
                title = title,
                projectPath = projectPath
            )
        }
        
        /**
         * Generates a title from the first user message.
         */
        private fun generateTitle(message: String): String {
            val cleaned = message
                .replace(Regex("\\s+"), " ")
                .trim()
            
            return if (cleaned.length <= MAX_TITLE_LENGTH) {
                cleaned
            } else {
                cleaned.take(MAX_TITLE_LENGTH - 3) + "..."
            }
        }
    }

    // -------------------------------------------------------------------------
    // Computed Properties
    // -------------------------------------------------------------------------
    
    /**
     * Number of messages in the session.
     */
    val messageCount: Int get() = messages.size
    
    /**
     * Whether the session has any messages.
     */
    val isEmpty: Boolean get() = messages.isEmpty()
    
    /**
     * The last message in the session, if any.
     */
    val lastMessage: ChatHistoryMessage? get() = messages.lastOrNull()
    
    /**
     * Preview text for the session (last message or title).
     */
    val preview: String get() = lastMessage?.content?.take(100) ?: title

    // -------------------------------------------------------------------------
    // Methods
    // -------------------------------------------------------------------------
    
    /**
     * Adds a message to the session.
     */
    fun addMessage(message: ChatHistoryMessage): ChatSession {
        return copy(
            messages = messages + message,
            updatedAt = Instant.now().toEpochMilli()
        )
    }
    
    /**
     * Adds a user message.
     */
    fun addUserMessage(content: String): ChatSession {
        return addMessage(ChatHistoryMessage.user(content))
    }
    
    /**
     * Adds an assistant message.
     */
    fun addAssistantMessage(content: String): ChatSession {
        return addMessage(ChatHistoryMessage.assistant(content))
    }
    
    /**
     * Updates the title.
     */
    fun withTitle(newTitle: String): ChatSession {
        return copy(
            title = newTitle,
            updatedAt = Instant.now().toEpochMilli()
        )
    }
}

/**
 * Represents a single message in chat history.
 *
 * @property id Unique message identifier
 * @property role Message role (user, assistant, system)
 * @property content Message content
 * @property timestamp When the message was sent
 * @property metadata Optional metadata (model used, tokens, etc.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatHistoryMessage(
    @JsonProperty("id")
    val id: String = UUID.randomUUID().toString(),
    
    @JsonProperty("role")
    val role: MessageRole,
    
    @JsonProperty("content")
    val content: String,
    
    @JsonProperty("timestamp")
    val timestamp: Long = Instant.now().toEpochMilli(),
    
    @JsonProperty("metadata")
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Creates a user message.
         */
        fun user(content: String): ChatHistoryMessage {
            return ChatHistoryMessage(
                role = MessageRole.USER,
                content = content
            )
        }
        
        /**
         * Creates an assistant message.
         */
        fun assistant(content: String, model: String? = null): ChatHistoryMessage {
            val metadata = buildMap {
                if (model != null) put("model", model)
            }
            return ChatHistoryMessage(
                role = MessageRole.ASSISTANT,
                content = content,
                metadata = metadata
            )
        }
        
        /**
         * Creates a system message.
         */
        fun system(content: String): ChatHistoryMessage {
            return ChatHistoryMessage(
                role = MessageRole.SYSTEM,
                content = content
            )
        }
    }

    // -------------------------------------------------------------------------
    // Computed Properties
    // -------------------------------------------------------------------------
    
    /**
     * Whether this is a user message.
     */
    val isUser: Boolean get() = role == MessageRole.USER
    
    /**
     * Whether this is an assistant message.
     */
    val isAssistant: Boolean get() = role == MessageRole.ASSISTANT
    
    /**
     * The model used for this message (if assistant).
     */
    val model: String? get() = metadata["model"]
    
    /**
     * Formatted timestamp for display.
     */
    val formattedTime: String get() {
        val instant = Instant.ofEpochMilli(timestamp)
        return instant.toString().take(19).replace("T", " ")
    }
}

/**
 * Message roles in chat history.
 */
enum class MessageRole {
    @JsonProperty("user")
    USER,
    
    @JsonProperty("assistant")
    ASSISTANT,
    
    @JsonProperty("system")
    SYSTEM
}
