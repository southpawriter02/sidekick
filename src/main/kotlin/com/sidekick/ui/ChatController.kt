// =============================================================================
// ChatController.kt
// =============================================================================
// Controller handling chat business logic and LLM provider communication.
//
// This class:
// - Manages conversation history
// - Communicates with ProviderManager for chat completions
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.CodebaseSearchService
import com.sidekick.context.ContextBuilder
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContextService
import com.sidekick.history.ChatHistoryService
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.MessageRole
import com.sidekick.llm.provider.UnifiedChatRequest
import com.sidekick.llm.provider.UnifiedMessage
import com.sidekick.models.ConnectionStatus
import com.sidekick.settings.SidekickSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Controller for the chat interface.
 *
 * Manages the conversation state, communicates with LLM providers via
 * ProviderManager, and coordinates UI updates in the ChatPanel.
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
     * Gets the ProviderManager instance.
     */
    private fun getProviderManager(): ProviderManager {
        return ProviderManager.getInstance()
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
    private val messageHistory = ConcurrentLinkedQueue<UnifiedMessage>()
    
    /**
     * Currently selected model name.
     */
    internal var selectedModel: String = DEFAULT_MODEL
    
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

        // Initialize providers and load settings
        chatScope.launch {
            try {
                val settings = SidekickSettings.getInstance()

                // Ensure ProviderManager is initialized (registers providers)
                getProviderManager()

                // Load default model from settings
                if (settings.defaultModel.isNotEmpty()) {
                    selectedModel = settings.defaultModel
                }

                updateConnectionStatus()
            } catch (e: Exception) {
                LOG.warn("Failed to initialize providers: ${e.message}")
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
        messageHistory.add(UnifiedMessage.user(userMessage))
        
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
     * @param modelName The model name (e.g., "llama3.2")
     */
    fun setModel(modelName: String) {
        selectedModel = modelName
        LOG.info("Model set to: $modelName")
    }

    // -------------------------------------------------------------------------
    // Private Methods - Chat
    // -------------------------------------------------------------------------
    
    /**
     * Streams the chat response from the active LLM provider.
     */
    private suspend fun streamChatResponse() {
        val providerManager = getProviderManager()
        val settings = SidekickSettings.getInstance()

        // Build context-enriched system prompt (v0.2.2, v1.0.6 codebase search)
        val latestUserMessage = messageHistory.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val systemPrompt = buildContextualSystemPrompt(settings, latestUserMessage)

        // Build message list (system prompt is passed separately on the request)
        val messages = messageHistory.toList()

        // Build the unified chat request
        val request = UnifiedChatRequest(
            model = selectedModel,
            messages = messages,
            temperature = settings.temperature.toFloat(),
            maxTokens = settings.maxTokens,
            systemPrompt = systemPrompt.ifEmpty { null },
            stream = settings.streamingEnabled
        )

        // Accumulate the full response for history
        val responseBuilder = StringBuilder()

        try {
            providerManager.streamChat(request)
                .onStart {
                    LOG.debug("Starting chat stream")
                    chatPanel.startAssistantMessage()
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        LOG.debug("Chat stream completed successfully")
                        val fullResponse = responseBuilder.toString()
                        if (fullResponse.isNotEmpty()) {
                            messageHistory.add(UnifiedMessage.assistant(fullResponse))

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
                .collect { token ->
                    responseBuilder.append(token)
                    chatPanel.appendToken(token)
                }

        } catch (e: CancellationException) {
            throw e  // Re-throw cancellation
        } catch (e: Exception) {
            LOG.error("Failed to send chat request: ${e.message}", e)
            chatPanel.showError(e.message ?: "Failed to communicate with LLM provider")
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
    private fun buildContextualSystemPrompt(settings: SidekickSettings, userMessage: String = ""): String {
        return try {
            val editorService = EditorContextService.getInstance(project)
            val projectService = ProjectContextService.getInstance(project)
            
            val contextBuilder = ContextBuilder.fromProject(editorService, projectService)
                .standardChat()
            
            // Codebase search: find files relevant to the user's query (v1.0.6)
            if (userMessage.isNotBlank()) {
                try {
                    val searchService = CodebaseSearchService.getInstance(project)
                    var searchResults = searchService.search(userMessage)
                    
                    // IF NO RESULTS: Expand query with synonyms (v1.0.7)
                    if (searchResults.isEmpty()) {
                        LOG.info("No direct matches for '$userMessage' — attempting query expansion")
                        val synonyms = expandQueryWithLLM(settings, userMessage)
                        if (synonyms.isNotEmpty()) {
                            LOG.info("Expanded query with synonyms: $synonyms")
                            // Re-run search with original query + synonyms
                            val expandedQuery = "$userMessage ${synonyms.joinToString(" ")}"
                            searchResults = searchService.search(expandedQuery)
                        }
                    }
                    
                    if (searchResults.isNotEmpty()) {
                        LOG.info("Codebase search found ${searchResults.size} relevant files")
                        contextBuilder.includeCodebaseSearch(searchResults)
                    }
                } catch (e: Exception) {
                    LOG.debug("Codebase search failed: ${e.message}")
                }
            }
            
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

    /**
     * Asks the LLM for synonyms/related terms to expand the search query.
     */
    private fun expandQueryWithLLM(settings: SidekickSettings, query: String): List<String> {
        // Quick fail if not configured
        if (settings.defaultModel.isEmpty()) return emptyList()

        return try {
            val providerManager = getProviderManager()
            val prompt = "You are a query semantic expander. The user is searching a codebase. Provide 3-5 single-word synonyms or related technical terms for the query: '$query'. For example, if user says 'magic', you might output 'mana spell ability'. If 'repo', output 'repository storage'. Output ONLY space-separated words. Do not ignore typos."

            val request = UnifiedChatRequest(
                model = settings.defaultModel,
                messages = listOf(UnifiedMessage.user(prompt)),
                systemPrompt = "Output ONLY space-separated words.",
                temperature = 0.3f,
                stream = false
            )

            // We need a blocking call here since we're inside the prompt builder.
            val sb = StringBuilder()
            kotlinx.coroutines.runBlocking {
                try {
                    val response = providerManager.chat(request)
                    sb.append(response.content ?: "")
                } catch (e: Exception) {
                    LOG.warn("Failed to expand query: ${e.message}")
                }
            }

            val expanded = sb.toString().trim()
            if (expanded.isBlank()) return emptyList()

            // Parse words
            expanded.split(Regex("\\s+"))
                .map { it.trim().lowercase() }
                .filter { it.length >= 3 }
                .take(5)

        } catch (e: Exception) {
            LOG.warn("Query expansion failed: ${e.message}")
            emptyList()
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
     * Checks all registered providers — CONNECTED if any provider is healthy.
     */
    private suspend fun updateConnectionStatus() {
        try {
            val providerManager = getProviderManager()
            val healthMap = providerManager.checkAllHealth()

            val anyHealthy = healthMap.values.any { it.healthy }
            val status = when {
                anyHealthy -> ConnectionStatus.CONNECTED
                healthMap.isNotEmpty() -> ConnectionStatus.DISCONNECTED
                else -> ConnectionStatus.NOT_CONFIGURED
            }

            chatPanel.updateConnectionStatus(status)

            // Refresh models when connection transitions to CONNECTED
            if (status == ConnectionStatus.CONNECTED && previousConnectionStatus != ConnectionStatus.CONNECTED) {
                LOG.info("Provider connection established — refreshing model list")
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
