// =============================================================================
// ChatController.kt
// =============================================================================
// Controller handling chat business logic and Ollama communication.
//
// This class:
// - Manages conversation history
// - Communicates with OllamaService for chat completions
// - Handles streaming responses and updates the UI
// - Manages connection status polling
//
// DESIGN NOTES:
// - Uses coroutines for async operations
// - Maintains message history for context continuity
// - Delegates UI updates to ChatPanel via callbacks
// - Handles errors gracefully with user-friendly messages
// =============================================================================

package com.sidekick.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.ContextBuilder
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContextService
import com.sidekick.history.ChatHistoryService
import com.sidekick.services.ollama.OllamaService
import com.sidekick.services.ollama.models.ChatMessage
import com.sidekick.services.ollama.models.ChatOptions
import com.sidekick.services.ollama.models.ChatRequest
import com.sidekick.services.ollama.models.ConnectionStatus
import com.sidekick.settings.SidekickSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Controller for the chat interface.
 *
 * Manages the conversation state, communicates with the Ollama service,
 * and coordinates UI updates in the ChatPanel.
 *
 * @property project The current project context
 * @property chatPanel The panel to update with messages and status
 */
class ChatController(
    private val project: Project,
    private val chatPanel: ChatPanel
) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(ChatController::class.java)
        
        // Default model to use if none configured
        private const val DEFAULT_MODEL = "llama3.2"
        
        // Polling interval for connection status (ms)
        private const val STATUS_POLL_INTERVAL_MS = 10_000L
    }

    // -------------------------------------------------------------------------
    // Service Access
    // -------------------------------------------------------------------------
    
    /**
     * Gets the OllamaService instance.
     */
    private fun getOllamaService(): OllamaService {
        return ApplicationManager.getApplication().getService(OllamaService::class.java)
    }

    // -------------------------------------------------------------------------
    // Coroutine Management
    // -------------------------------------------------------------------------
    
    /**
     * Coroutine scope for chat operations.
     * Uses IO dispatcher for network calls + SupervisorJob for error isolation.
     */
    private val chatScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("SidekickChat")
    )
    
    /**
     * Job for the current chat request (if any).
     * Used to cancel in-progress requests.
     */
    private var currentChatJob: Job? = null
    
    /**
     * Job for status polling.
     */
    private var statusPollingJob: Job? = null

    // -------------------------------------------------------------------------
    // Conversation State
    // -------------------------------------------------------------------------
    
    /**
     * Message history for the current conversation.
     * Thread-safe queue for concurrent access.
     */
    private val messageHistory = ConcurrentLinkedQueue<ChatMessage>()
    
    /**
     * Currently selected model name.
     */
    private var selectedModel: String = DEFAULT_MODEL
    
    /**
     * Whether a chat request is currently in progress.
     */
    @Volatile
    private var isProcessing = false
    
    /**
     * Previous connection status, used to detect transitions.
     */
    @Volatile
    private var previousConnectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED
    
    /**
     * Callback invoked when connection status transitions to CONNECTED.
     * Used by ChatPanel to trigger model list refresh.
     */
    var onConnected: (() -> Unit)? = null

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    
    init {
        LOG.info("Initializing ChatController for project: ${project.name}")
        
        // Start connection status polling
        startStatusPolling()
        
        // Load settings and configure Ollama service
        chatScope.launch {
            try {
                val settings = SidekickSettings.getInstance()
                val service = getOllamaService()
                service.configure(settings.ollamaUrl)
                
                // Load default model from settings
                if (settings.defaultModel.isNotEmpty()) {
                    selectedModel = settings.defaultModel
                }
                
                updateConnectionStatus()
            } catch (e: Exception) {
                LOG.warn("Failed to configure Ollama service: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------
    
    /**
     * Sends a user message and streams the response.
     *
     * @param userMessage The message text from the user
     */
    fun sendMessage(userMessage: String) {
        if (isProcessing) {
            LOG.debug("Already processing a message, ignoring new request")
            return
        }
        
        LOG.info("Sending message: ${userMessage.take(50)}...")
        
        // Add user message to history
        messageHistory.add(ChatMessage.user(userMessage))
        
        // Persist to chat history (v0.2.5)
        try {
            ChatHistoryService.getInstance(project).addUserMessage(userMessage)
        } catch (e: Exception) {
            LOG.debug("Failed to persist user message: ${e.message}")
        }
        
        // Disable input while processing
        isProcessing = true
        chatPanel.setInputEnabled(false)
        
        // Start chat request
        currentChatJob = chatScope.launch {
            streamChatResponse()
        }
    }
    
    /**
     * Cancels the current chat request if one is in progress.
     */
    fun cancelCurrentRequest() {
        currentChatJob?.cancel()
        currentChatJob = null
        isProcessing = false
        chatPanel.setInputEnabled(true)
    }
    
    /**
     * Clears the conversation history.
     */
    fun clearHistory() {
        messageHistory.clear()
        LOG.debug("Conversation history cleared")
    }
    
    /**
     * Sets the model to use for chat.
     *
     * @param modelName The Ollama model name (e.g., "llama3.2")
     */
    fun setModel(modelName: String) {
        selectedModel = modelName
        LOG.info("Model set to: $modelName")
    }

    // -------------------------------------------------------------------------
    // Private Methods - Chat
    // -------------------------------------------------------------------------
    
    /**
     * Streams the chat response from Ollama.
     */
    private suspend fun streamChatResponse() {
        val service = getOllamaService()
        val settings = SidekickSettings.getInstance()
        
        // Build context-enriched system prompt (v0.2.2)
        val systemPrompt = buildContextualSystemPrompt(settings)
        
        // Build message list with context-enriched system prompt
        val messages = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotEmpty()) {
            messages.add(ChatMessage.system(systemPrompt))
        }
        messages.addAll(messageHistory)
        
        // Build chat options from settings
        val options = ChatOptions(
            temperature = settings.temperature,
            numPredict = settings.maxTokens
        )
        
        // Build the chat request
        val request = ChatRequest(
            model = selectedModel,
            messages = messages,
            stream = settings.streamingEnabled,
            options = options
        )
        
        // Accumulate the full response for history
        val responseBuilder = StringBuilder()
        
        try {
            service.chat(request)
                .onStart {
                    LOG.debug("Starting chat stream")
                    chatPanel.startAssistantMessage()
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        LOG.debug("Chat stream completed successfully")
                        // Add assistant message to history
                        val fullResponse = responseBuilder.toString()
                        if (fullResponse.isNotEmpty()) {
                            messageHistory.add(ChatMessage.assistant(fullResponse))
                            
                            // Persist to chat history (v0.2.5)
                            try {
                                ChatHistoryService.getInstance(project)
                                    .addAssistantMessage(fullResponse, selectedModel)
                            } catch (e: Exception) {
                                LOG.debug("Failed to persist assistant message: ${e.message}")
                            }
                        }
                        chatPanel.completeAssistantMessage()
                    } else if (cause is CancellationException) {
                        LOG.debug("Chat stream cancelled")
                    } else {
                        LOG.warn("Chat stream failed: ${cause.message}")
                    }
                    
                    isProcessing = false
                    chatPanel.setInputEnabled(true)
                }
                .catch { e ->
                    LOG.error("Error during chat: ${e.message}", e)
                    chatPanel.showError(e.message ?: "Unknown error occurred")
                }
                .collect { response ->
                    val token = response.message.content
                    responseBuilder.append(token)
                    chatPanel.appendToken(token)
                }
                
        } catch (e: CancellationException) {
            throw e  // Re-throw cancellation
        } catch (e: Exception) {
            LOG.error("Failed to send chat request: ${e.message}", e)
            chatPanel.showError(e.message ?: "Failed to communicate with Ollama")
            isProcessing = false
            chatPanel.setInputEnabled(true)
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Context Building (v0.2.2)
    // -------------------------------------------------------------------------
    
    /**
     * Builds a system prompt enriched with current context.
     */
    private fun buildContextualSystemPrompt(settings: SidekickSettings): String {
        return try {
            val editorService = EditorContextService.getInstance(project)
            val projectService = ProjectContextService.getInstance(project)
            
            val contextBuilder = ContextBuilder.fromProject(editorService, projectService)
                .standardChat()
            
            // Start with user's base system prompt
            val basePrompt = settings.systemPrompt.ifEmpty {
                "You are a helpful AI coding assistant. Help the user with their programming questions."
            }
            
            // Add context if available
            val contextSection = contextBuilder.build()
            
            if (contextSection.isNotEmpty()) {
                buildString {
                    append(basePrompt)
                    append("\n\n## Current Context\n\n")
                    append(contextSection)
                }
            } else {
                basePrompt
            }
        } catch (e: Exception) {
            LOG.debug("Failed to build context: ${e.message}")
            settings.systemPrompt
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Status Polling
    // -------------------------------------------------------------------------
    
    /**
     * Starts periodic polling of the connection status.
     */
    private fun startStatusPolling() {
        statusPollingJob = chatScope.launch {
            while (isActive) {
                updateConnectionStatus()
                delay(STATUS_POLL_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Updates the connection status in the UI.
     */
    private suspend fun updateConnectionStatus() {
        try {
            val service = getOllamaService()
            val status = service.getConnectionStatus()
            chatPanel.updateConnectionStatus(status)
            
            // Refresh models when connection transitions to CONNECTED
            if (status == ConnectionStatus.CONNECTED && previousConnectionStatus != ConnectionStatus.CONNECTED) {
                LOG.info("Connection established — refreshing model list")
                onConnected?.invoke()
            }
            previousConnectionStatus = status
        } catch (e: Exception) {
            LOG.debug("Failed to get connection status: ${e.message}")
            chatPanel.updateConnectionStatus(ConnectionStatus.DISCONNECTED)
            previousConnectionStatus = ConnectionStatus.DISCONNECTED
        }
    }

    // -------------------------------------------------------------------------
    // Disposable Implementation
    // -------------------------------------------------------------------------
    
    override fun dispose() {
        LOG.info("Disposing ChatController")
        
        // Cancel all coroutines
        currentChatJob?.cancel()
        statusPollingJob?.cancel()
        chatScope.cancel()
        
        // Clear state
        messageHistory.clear()
    }
}
