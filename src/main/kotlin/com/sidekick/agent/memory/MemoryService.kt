package com.sidekick.agent.memory

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Memory Service
 *
 * Service for managing conversation memory and session persistence.
 * Part of Sidekick v0.8.6 Conversation Memory feature.
 *
 * ## Features
 *
 * - Session management (start, end, track)
 * - Memory storage (remember, recall)
 * - Semantic search via embeddings
 * - Automatic pruning and retention
 * - Statistics and monitoring
 *
 * @since 0.8.6
 */
class MemoryService(
    private val projectPath: String = "",
    private val embedProvider: (suspend (String) -> List<Float>)? = null
) {
    private var config = MemoryConfig.DEFAULT
    private val memories = CopyOnWriteArrayList<MemoryEntry>()
    private val sessions = ConcurrentHashMap<String, SessionContext>()
    private var currentSession: SessionContext? = null

    private val eventListeners = mutableListOf<(MemoryEvent) -> Unit>()

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Gets the current configuration.
     */
    fun getConfig(): MemoryConfig = config

    /**
     * Updates the configuration.
     */
    fun updateConfig(newConfig: MemoryConfig) {
        config = newConfig
    }

    /**
     * Whether memory is enabled.
     */
    val isEnabled: Boolean get() = config.enabled

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Starts a new session.
     */
    fun startSession(): SessionContext {
        val session = SessionContext.create(projectPath)
        sessions[session.sessionId] = session
        currentSession = session
        emitEvent(MemoryEvent.SessionStarted(session))
        return session
    }

    /**
     * Gets the current session, starting one if needed.
     */
    fun getCurrentSession(): SessionContext {
        return currentSession ?: startSession()
    }

    /**
     * Gets a session by ID.
     */
    fun getSession(sessionId: String): SessionContext? = sessions[sessionId]

    /**
     * Ends the current session.
     */
    fun endSession(): SessionContext? {
        val session = currentSession ?: return null
        val ended = session.end()
        sessions[session.sessionId] = ended
        currentSession = null

        val memoryCount = memories.count { it.sessionId == session.sessionId }
        emitEvent(MemoryEvent.SessionEnded(
            sessionId = session.sessionId,
            durationMs = ended.durationMs(),
            memoryCount = memoryCount
        ))

        return ended
    }

    /**
     * Tracks a file access in the current session.
     */
    fun trackFile(filePath: String) {
        currentSession?.addFile(filePath)
    }

    /**
     * Tracks a symbol reference in the current session.
     */
    fun trackSymbol(symbol: String) {
        currentSession?.addSymbol(symbol)
    }

    // =========================================================================
    // Memory Storage
    // =========================================================================

    /**
     * Adds a memory entry.
     */
    suspend fun remember(
        type: MemoryType,
        content: String,
        metadata: Map<String, String> = emptyMap(),
        importance: Float = type.defaultImportance
    ): MemoryEntry {
        if (!config.enabled) {
            return MemoryEntry(
                sessionId = currentSession?.sessionId ?: "",
                type = type,
                content = content
            )
        }

        val embedding = if (config.embedMemories && embedProvider != null) {
            try {
                embedProvider.invoke(content)
            } catch (e: Exception) {
                null
            }
        } else null

        val entry = MemoryEntry(
            sessionId = currentSession?.sessionId ?: "",
            type = type,
            content = content,
            embedding = embedding,
            metadata = metadata,
            importance = importance.coerceIn(0f, 1f)
        )

        memories.add(entry)
        pruneIfNeeded()
        emitEvent(MemoryEvent.MemoryAdded(entry))

        return entry
    }

    /**
     * Remembers a user message.
     */
    suspend fun rememberUserMessage(content: String): MemoryEntry {
        return remember(MemoryType.USER_MESSAGE, content)
    }

    /**
     * Remembers an assistant message.
     */
    suspend fun rememberAssistantMessage(content: String): MemoryEntry {
        return remember(MemoryType.ASSISTANT_MESSAGE, content)
    }

    /**
     * Remembers a tool result.
     */
    suspend fun rememberToolResult(toolName: String, result: String): MemoryEntry {
        return remember(
            MemoryType.TOOL_RESULT,
            result,
            mapOf("tool" to toolName)
        )
    }

    /**
     * Remembers a fact.
     */
    suspend fun rememberFact(fact: String, importance: Float = 0.7f): MemoryEntry {
        currentSession?.addFact(fact)
        return remember(MemoryType.FACT, fact, importance = importance)
    }

    /**
     * Remembers a preference.
     */
    suspend fun rememberPreference(preference: String): MemoryEntry {
        return remember(MemoryType.PREFERENCE, preference, importance = 0.8f)
    }

    // =========================================================================
    // Memory Recall
    // =========================================================================

    /**
     * Recalls memories matching a query.
     */
    suspend fun recall(query: MemoryQuery): List<MemoryEntry> {
        if (!config.enabled) return emptyList()

        var results = memories.toList()

        // Apply filters
        if (query.hasTypeFilter) {
            results = results.filter { it.type in query.types!! }
        }

        if (query.hasSessionFilter) {
            results = results.filter { it.sessionId == query.sessionId }
        }

        if (query.minImportance > 0) {
            results = results.filter { it.importance >= query.minImportance }
        }

        if (query.afterTime != null) {
            results = results.filter { it.timestamp.isAfter(query.afterTime) }
        }

        if (query.beforeTime != null) {
            results = results.filter { it.timestamp.isBefore(query.beforeTime) }
        }

        // Remove expired
        val now = Instant.now()
        results = results.filter { !it.isExpired(now) }

        // Sort by relevance
        results = if (query.useSemantic && query.query.isNotBlank() && embedProvider != null) {
            val queryEmbedding = embedProvider.invoke(query.query)
            results
                .filter { it.hasEmbedding }
                .sortedByDescending { memory ->
                    val similarity = MemoryUtils.cosineSimilarity(queryEmbedding, memory.embedding!!)
                    MemoryUtils.scoreMemory(memory, similarity, now)
                }
        } else {
            results.sortedByDescending { it.timestamp }
        }

        val finalResults = results.take(query.limit)

        emitEvent(MemoryEvent.MemoryRecalled(query.query, finalResults.size))

        return finalResults
    }

    /**
     * Recalls memories by semantic similarity.
     */
    suspend fun recall(query: String, limit: Int = 5): List<MemoryEntry> {
        return recall(MemoryQuery.semantic(query, limit))
    }

    /**
     * Gets recent memories.
     */
    fun getRecentMemories(limit: Int = 10): List<MemoryEntry> {
        return memories.takeLast(limit)
    }

    /**
     * Gets memories for current session.
     */
    fun getSessionMemories(limit: Int = 50): List<MemoryEntry> {
        val sessionId = currentSession?.sessionId ?: return emptyList()
        return memories
            .filter { it.sessionId == sessionId }
            .takeLast(limit)
    }

    /**
     * Gets facts and preferences.
     */
    fun getPersistentMemories(): List<MemoryEntry> {
        return memories.filter { it.type in MemoryType.PERSISTENT_TYPES }
    }

    /**
     * Gets a memory by ID.
     */
    fun getMemory(id: String): MemoryEntry? {
        return memories.find { it.id == id }
    }

    // =========================================================================
    // Memory Management
    // =========================================================================

    /**
     * Prunes memories if over limit.
     */
    private fun pruneIfNeeded() {
        if (memories.size <= config.maxLongTermMemory) return

        val now = Instant.now()
        val cutoff = config.retentionCutoff(now)

        // First, remove expired and old
        val expired = memories.filter { it.isExpired(now) || it.timestamp.isBefore(cutoff) }
        if (expired.isNotEmpty()) {
            memories.removeAll(expired.toSet())
            emitEvent(MemoryEvent.MemoriesPruned(expired.size, "Expired or old"))
        }

        // If still over, prune by importance
        if (memories.size > config.maxLongTermMemory) {
            val sorted = memories.sortedByDescending { it.importance }
            val toRemove = memories.size - config.maxLongTermMemory
            val lowImportance = sorted.takeLast(toRemove)
            memories.removeAll(lowImportance.toSet())
            emitEvent(MemoryEvent.MemoriesPruned(toRemove, "Low importance"))
        }
    }

    /**
     * Clears all memories.
     */
    fun clearMemories() {
        val count = memories.size
        memories.clear()
        emitEvent(MemoryEvent.MemoriesPruned(count, "Manual clear"))
    }

    /**
     * Clears memories for a session.
     */
    fun clearSessionMemories(sessionId: String) {
        val toRemove = memories.filter { it.sessionId == sessionId }
        memories.removeAll(toRemove.toSet())
        emitEvent(MemoryEvent.MemoriesPruned(toRemove.size, "Session clear: $sessionId"))
    }

    /**
     * Updates a memory's importance.
     */
    fun updateImportance(memoryId: String, importance: Float): Boolean {
        val index = memories.indexOfFirst { it.id == memoryId }
        if (index < 0) return false

        val updated = memories[index].withImportance(importance)
        memories[index] = updated
        return true
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets memory statistics.
     */
    fun getStats(): MemoryStats {
        if (memories.isEmpty()) return MemoryStats.EMPTY

        val withEmbeddings = memories.count { it.hasEmbedding }
        val typeBreakdown = memories.groupBy { it.type }.mapValues { it.value.size }
        val avgImportance = memories.map { it.importance }.average().toFloat()

        return MemoryStats(
            totalMemories = memories.size,
            memoriesWithEmbeddings = withEmbeddings,
            sessionCount = sessions.size,
            oldestMemory = memories.minOfOrNull { it.timestamp },
            newestMemory = memories.maxOfOrNull { it.timestamp },
            typeBreakdown = typeBreakdown,
            averageImportance = avgImportance
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (MemoryEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (MemoryEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: MemoryEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Persistence (State)
    // =========================================================================

    /**
     * Memory service state for persistence.
     */
    data class State(
        var config: MemoryConfig = MemoryConfig.DEFAULT,
        var memories: MutableList<MemoryEntry> = mutableListOf(),
        var sessions: MutableMap<String, SessionContext> = mutableMapOf(),
        var currentSessionId: String? = null
    )

    /**
     * Gets the current state for persistence.
     */
    fun getState(): State {
        return State(
            config = config,
            memories = memories.toMutableList(),
            sessions = sessions.toMutableMap(),
            currentSessionId = currentSession?.sessionId
        )
    }

    /**
     * Loads state from persistence.
     */
    fun loadState(state: State) {
        config = state.config
        memories.clear()
        memories.addAll(state.memories)
        sessions.clear()
        sessions.putAll(state.sessions)
        currentSession = state.currentSessionId?.let { sessions[it] }
    }

    companion object {
        /**
         * Creates a service without embedding support.
         */
        fun withoutEmbeddings(projectPath: String = ""): MemoryService {
            return MemoryService(projectPath, null)
        }
    }
}
