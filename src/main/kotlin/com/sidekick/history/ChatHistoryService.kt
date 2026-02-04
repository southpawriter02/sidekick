// =============================================================================
// ChatHistoryService.kt
// =============================================================================
// Service for persisting and managing chat history.
//
// This service:
// - Saves/loads chat sessions to disk
// - Manages session lifecycle
// - Provides search and filtering
//
// DESIGN NOTES:
// - Project-level service
// - JSON file-based persistence
// - Sessions stored in plugin data directory
// =============================================================================

package com.sidekick.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Service for persisting and managing chat history.
 *
 * Handles saving and loading chat sessions to the plugin's
 * data directory using JSON serialization.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = ChatHistoryService.getInstance(project)
 * val session = service.createSession("Hello!")
 * session = service.addMessage(session.id, ChatHistoryMessage.assistant("Hi!"))
 * ```
 */
@Service(Service.Level.PROJECT)
class ChatHistoryService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(ChatHistoryService::class.java)
        
        /**
         * Directory name for chat history storage.
         */
        private const val HISTORY_DIR = "sidekick-history"
        
        /**
         * File extension for session files.
         */
        private const val SESSION_EXTENSION = ".json"
        
        /**
         * Maximum sessions to keep (oldest are deleted).
         */
        const val MAX_SESSIONS = 100
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): ChatHistoryService {
            return project.getService(ChatHistoryService::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // JSON Mapper
    // -------------------------------------------------------------------------
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // -------------------------------------------------------------------------
    // In-Memory Cache
    // -------------------------------------------------------------------------
    
    /**
     * Cached sessions (loaded on demand).
     */
    private val sessionCache: MutableMap<String, ChatSession> = mutableMapOf()
    
    /**
     * Current active session ID.
     */
    private var activeSessionId: String? = null

    // -------------------------------------------------------------------------
    // Public Methods - Session Management
    // -------------------------------------------------------------------------
    
    /**
     * Creates a new chat session.
     */
    fun createSession(firstMessage: String? = null): ChatSession {
        val session = if (firstMessage != null) {
            ChatSession.create(firstMessage, project.basePath)
        } else {
            ChatSession(projectPath = project.basePath)
        }
        
        sessionCache[session.id] = session
        activeSessionId = session.id
        saveSession(session)
        
        LOG.info("Created session: ${session.id}")
        return session
    }
    
    /**
     * Gets the active session, creating one if needed.
     */
    fun getOrCreateActiveSession(): ChatSession {
        val currentId = activeSessionId
        if (currentId != null) {
            val session = getSession(currentId)
            if (session != null) return session
        }
        
        return createSession()
    }
    
    /**
     * Sets the active session.
     */
    fun setActiveSession(sessionId: String) {
        activeSessionId = sessionId
    }
    
    /**
     * Gets a session by ID.
     */
    fun getSession(sessionId: String): ChatSession? {
        // Check cache first
        sessionCache[sessionId]?.let { return it }
        
        // Load from disk
        val session = loadSession(sessionId)
        if (session != null) {
            sessionCache[sessionId] = session
        }
        return session
    }
    
    /**
     * Gets all sessions for this project.
     */
    fun getAllSessions(): List<ChatSession> {
        val historyDir = getHistoryDirectory()
        if (!Files.exists(historyDir)) return emptyList()
        
        return historyDir.toFile().listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val session: ChatSession = objectMapper.readValue(file)
                    sessionCache[session.id] = session
                    session
                } catch (e: Exception) {
                    LOG.warn("Failed to load session from ${file.name}: ${e.message}")
                    null
                }
            }
            ?.filter { it.projectPath == project.basePath || it.projectPath == null }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }
    
    /**
     * Gets recent sessions (limited count).
     */
    fun getRecentSessions(limit: Int = 10): List<ChatSession> {
        return getAllSessions().take(limit)
    }

    // -------------------------------------------------------------------------
    // Public Methods - Message Management
    // -------------------------------------------------------------------------
    
    /**
     * Adds a message to a session.
     */
    fun addMessage(sessionId: String, message: ChatHistoryMessage): ChatSession? {
        val session = getSession(sessionId) ?: return null
        val updated = session.addMessage(message)
        
        sessionCache[sessionId] = updated
        saveSession(updated)
        
        return updated
    }
    
    /**
     * Adds a user message to the active session.
     */
    fun addUserMessage(content: String): ChatSession {
        val session = getOrCreateActiveSession()
        val message = ChatHistoryMessage.user(content)
        return addMessage(session.id, message) ?: session
    }
    
    /**
     * Adds an assistant message to the active session.
     */
    fun addAssistantMessage(content: String, model: String? = null): ChatSession {
        val session = getOrCreateActiveSession()
        val message = ChatHistoryMessage.assistant(content, model)
        return addMessage(session.id, message) ?: session
    }

    // -------------------------------------------------------------------------
    // Public Methods - Session Operations
    // -------------------------------------------------------------------------
    
    /**
     * Renames a session.
     */
    fun renameSession(sessionId: String, newTitle: String): ChatSession? {
        val session = getSession(sessionId) ?: return null
        val updated = session.withTitle(newTitle)
        
        sessionCache[sessionId] = updated
        saveSession(updated)
        
        return updated
    }
    
    /**
     * Deletes a session.
     */
    fun deleteSession(sessionId: String): Boolean {
        sessionCache.remove(sessionId)
        
        if (activeSessionId == sessionId) {
            activeSessionId = null
        }
        
        val file = getSessionFile(sessionId)
        return try {
            Files.deleteIfExists(file)
            LOG.info("Deleted session: $sessionId")
            true
        } catch (e: Exception) {
            LOG.warn("Failed to delete session $sessionId: ${e.message}")
            false
        }
    }
    
    /**
     * Clears all sessions.
     */
    fun clearAllSessions() {
        sessionCache.clear()
        activeSessionId = null
        
        val historyDir = getHistoryDirectory()
        if (Files.exists(historyDir)) {
            historyDir.toFile().listFiles()?.forEach { it.delete() }
        }
        
        LOG.info("Cleared all sessions")
    }
    
    /**
     * Prunes old sessions if over limit.
     */
    fun pruneOldSessions() {
        val sessions = getAllSessions()
        if (sessions.size > MAX_SESSIONS) {
            val toDelete = sessions.drop(MAX_SESSIONS)
            toDelete.forEach { deleteSession(it.id) }
            LOG.info("Pruned ${toDelete.size} old sessions")
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods - Search
    // -------------------------------------------------------------------------
    
    /**
     * Searches sessions by content.
     */
    fun searchSessions(query: String): List<ChatSession> {
        if (query.isBlank()) return getAllSessions()
        
        val lowerQuery = query.lowercase()
        return getAllSessions().filter { session ->
            session.title.lowercase().contains(lowerQuery) ||
            session.messages.any { it.content.lowercase().contains(lowerQuery) }
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Persistence
    // -------------------------------------------------------------------------
    
    private fun getHistoryDirectory(): Path {
        val projectDir = Path.of(project.basePath ?: System.getProperty("user.home"))
        return projectDir.resolve(".idea").resolve(HISTORY_DIR)
    }
    
    private fun getSessionFile(sessionId: String): Path {
        return getHistoryDirectory().resolve("$sessionId$SESSION_EXTENSION")
    }
    
    private fun saveSession(session: ChatSession) {
        try {
            val historyDir = getHistoryDirectory()
            Files.createDirectories(historyDir)
            
            val file = getSessionFile(session.id)
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(file.toFile(), session)
            
            LOG.debug("Saved session: ${session.id}")
        } catch (e: Exception) {
            LOG.error("Failed to save session ${session.id}: ${e.message}")
        }
    }
    
    private fun loadSession(sessionId: String): ChatSession? {
        val file = getSessionFile(sessionId)
        if (!Files.exists(file)) return null
        
        return try {
            objectMapper.readValue(file.toFile())
        } catch (e: Exception) {
            LOG.warn("Failed to load session $sessionId: ${e.message}")
            null
        }
    }
}
