package com.sidekick.agent.memory

import java.time.Instant
import java.util.UUID

/**
 * # Memory Models
 *
 * Data models for conversation memory and session persistence.
 * Part of Sidekick v0.8.6 Conversation Memory feature.
 *
 * ## Overview
 *
 * The memory system enables:
 * - Short-term memory: Recent conversation context
 * - Long-term memory: Persistent facts and preferences
 * - Session tracking: Project-level context
 * - Semantic recall: Vector similarity search
 *
 * @since 0.8.6
 */

// =============================================================================
// Memory Entry
// =============================================================================

/**
 * A single memory entry.
 *
 * @property id Unique identifier
 * @property sessionId Session this memory belongs to
 * @property type Type of memory
 * @property content The memory content
 * @property embedding Optional vector embedding for semantic search
 * @property metadata Additional key-value metadata
 * @property timestamp When this memory was created
 * @property importance Importance score (0.0 to 1.0)
 * @property expiresAt Optional expiration time
 */
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: MemoryType,
    val content: String,
    val embedding: List<Float>? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Instant = Instant.now(),
    val importance: Float = 0.5f,
    val expiresAt: Instant? = null
) {
    /**
     * Whether this memory has an embedding.
     */
    val hasEmbedding: Boolean get() = embedding != null

    /**
     * Whether this memory has expired.
     */
    fun isExpired(now: Instant = Instant.now()): Boolean {
        return expiresAt?.isBefore(now) ?: false
    }

    /**
     * Age of this memory in milliseconds.
     */
    fun ageMs(now: Instant = Instant.now()): Long {
        return now.toEpochMilli() - timestamp.toEpochMilli()
    }

    /**
     * Creates a copy with embedding.
     */
    fun withEmbedding(embedding: List<Float>): MemoryEntry {
        return copy(embedding = embedding)
    }

    /**
     * Creates a copy with updated importance.
     */
    fun withImportance(importance: Float): MemoryEntry {
        return copy(importance = importance.coerceIn(0f, 1f))
    }

    /**
     * Gets metadata value.
     */
    fun getMeta(key: String): String? = metadata[key]

    companion object {
        /**
         * Creates a user message memory.
         */
        fun userMessage(sessionId: String, content: String): MemoryEntry = MemoryEntry(
            sessionId = sessionId,
            type = MemoryType.USER_MESSAGE,
            content = content,
            importance = 0.6f
        )

        /**
         * Creates an assistant message memory.
         */
        fun assistantMessage(sessionId: String, content: String): MemoryEntry = MemoryEntry(
            sessionId = sessionId,
            type = MemoryType.ASSISTANT_MESSAGE,
            content = content,
            importance = 0.5f
        )

        /**
         * Creates a tool result memory.
         */
        fun toolResult(sessionId: String, toolName: String, result: String): MemoryEntry = MemoryEntry(
            sessionId = sessionId,
            type = MemoryType.TOOL_RESULT,
            content = result,
            metadata = mapOf("tool" to toolName),
            importance = 0.4f
        )

        /**
         * Creates a fact memory.
         */
        fun fact(sessionId: String, fact: String, importance: Float = 0.7f): MemoryEntry = MemoryEntry(
            sessionId = sessionId,
            type = MemoryType.FACT,
            content = fact,
            importance = importance
        )

        /**
         * Creates a preference memory.
         */
        fun preference(sessionId: String, preference: String): MemoryEntry = MemoryEntry(
            sessionId = sessionId,
            type = MemoryType.PREFERENCE,
            content = preference,
            importance = 0.8f
        )

        /**
         * Creates a code context memory.
         */
        fun codeContext(sessionId: String, filePath: String, description: String): MemoryEntry = MemoryEntry(
            sessionId = sessionId,
            type = MemoryType.CODE_CONTEXT,
            content = description,
            metadata = mapOf("file" to filePath),
            importance = 0.5f
        )
    }
}

// =============================================================================
// Memory Type
// =============================================================================

/**
 * Types of memory entries.
 */
enum class MemoryType(val displayName: String, val defaultImportance: Float) {
    /** User's input message */
    USER_MESSAGE("User Message", 0.6f),

    /** Assistant's response */
    ASSISTANT_MESSAGE("Assistant Message", 0.5f),

    /** Result from a tool execution */
    TOOL_RESULT("Tool Result", 0.4f),

    /** Extracted fact or knowledge */
    FACT("Fact", 0.7f),

    /** User preference */
    PREFERENCE("Preference", 0.8f),

    /** Code context reference */
    CODE_CONTEXT("Code Context", 0.5f),

    /** Error or exception context */
    ERROR_CONTEXT("Error Context", 0.6f),

    /** Decision or conclusion */
    DECISION("Decision", 0.7f),

    /** Summary of a conversation */
    SUMMARY("Summary", 0.6f),

    /** System note */
    SYSTEM_NOTE("System Note", 0.3f);

    companion object {
        /** Types that should be persisted long-term */
        val PERSISTENT_TYPES = setOf(FACT, PREFERENCE, DECISION)

        /** Types that are conversational */
        val CONVERSATIONAL_TYPES = setOf(USER_MESSAGE, ASSISTANT_MESSAGE, TOOL_RESULT)

        /** Types related to code */
        val CODE_TYPES = setOf(CODE_CONTEXT, ERROR_CONTEXT)
    }
}

// =============================================================================
// Session Context
// =============================================================================

/**
 * Session context tracking.
 *
 * @property sessionId Unique session identifier
 * @property projectPath Project root path
 * @property startTime When the session started
 * @property endTime When the session ended (null if active)
 * @property activeFiles Files accessed during session
 * @property recentSymbols Recently referenced symbols
 * @property facts Facts learned during session
 * @property metadata Additional session metadata
 */
data class SessionContext(
    val sessionId: String = UUID.randomUUID().toString(),
    val projectPath: String,
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    val activeFiles: MutableSet<String> = mutableSetOf(),
    val recentSymbols: MutableList<String> = mutableListOf(),
    val facts: MutableList<String> = mutableListOf(),
    val metadata: MutableMap<String, String> = mutableMapOf()
) {
    /**
     * Whether the session is active.
     */
    val isActive: Boolean get() = endTime == null

    /**
     * Session duration in milliseconds.
     */
    fun durationMs(now: Instant = Instant.now()): Long {
        val end = endTime ?: now
        return end.toEpochMilli() - startTime.toEpochMilli()
    }

    /**
     * Adds an active file.
     */
    fun addFile(filePath: String) {
        activeFiles.add(filePath)
    }

    /**
     * Adds a recent symbol, keeping last N.
     */
    fun addSymbol(symbol: String, maxSymbols: Int = 50) {
        recentSymbols.remove(symbol)
        recentSymbols.add(symbol)
        while (recentSymbols.size > maxSymbols) {
            recentSymbols.removeAt(0)
        }
    }

    /**
     * Adds a fact.
     */
    fun addFact(fact: String, maxFacts: Int = 100) {
        if (fact !in facts) {
            facts.add(fact)
            while (facts.size > maxFacts) {
                facts.removeAt(0)
            }
        }
    }

    /**
     * Ends the session.
     */
    fun end(): SessionContext = copy(endTime = Instant.now())

    companion object {
        /**
         * Creates a new session for a project.
         */
        fun create(projectPath: String): SessionContext =
            SessionContext(projectPath = projectPath)
    }
}

// =============================================================================
// Memory Configuration
// =============================================================================

/**
 * Memory system configuration.
 *
 * @property enabled Whether memory is enabled
 * @property maxShortTermMemory Maximum short-term memories
 * @property maxLongTermMemory Maximum long-term memories
 * @property retentionDays Days to retain old memories
 * @property embedMemories Whether to generate embeddings
 * @property autoSummarize Whether to auto-summarize conversations
 * @property importanceThreshold Minimum importance to persist
 */
data class MemoryConfig(
    val enabled: Boolean = true,
    val maxShortTermMemory: Int = 20,
    val maxLongTermMemory: Int = 1000,
    val retentionDays: Int = 30,
    val embedMemories: Boolean = true,
    val autoSummarize: Boolean = true,
    val importanceThreshold: Float = 0.3f
) {
    /**
     * Whether to persist a memory based on importance.
     */
    fun shouldPersist(importance: Float): Boolean {
        return importance >= importanceThreshold
    }

    /**
     * Calculates retention cutoff.
     */
    fun retentionCutoff(now: Instant = Instant.now()): Instant {
        return now.minusSeconds(retentionDays.toLong() * 24 * 60 * 60)
    }

    companion object {
        /** Default configuration */
        val DEFAULT = MemoryConfig()

        /** Minimal memory (for testing) */
        val MINIMAL = MemoryConfig(
            maxShortTermMemory = 5,
            maxLongTermMemory = 50,
            embedMemories = false,
            autoSummarize = false
        )

        /** Maximum memory retention */
        val MAXIMUM = MemoryConfig(
            maxShortTermMemory = 50,
            maxLongTermMemory = 5000,
            retentionDays = 365,
            importanceThreshold = 0.1f
        )
    }
}

// =============================================================================
// Memory Query
// =============================================================================

/**
 * Query for memory recall.
 *
 * @property query Query text
 * @property limit Maximum results
 * @property types Types to include
 * @property sessionId Filter by session
 * @property minImportance Minimum importance
 * @property afterTime Only memories after this time
 * @property beforeTime Only memories before this time
 * @property useSemantic Use semantic search
 */
data class MemoryQuery(
    val query: String,
    val limit: Int = 10,
    val types: Set<MemoryType>? = null,
    val sessionId: String? = null,
    val minImportance: Float = 0f,
    val afterTime: Instant? = null,
    val beforeTime: Instant? = null,
    val useSemantic: Boolean = true
) {
    val hasTypeFilter: Boolean get() = types != null && types.isNotEmpty()
    val hasSessionFilter: Boolean get() = sessionId != null
    val hasTimeFilter: Boolean get() = afterTime != null || beforeTime != null

    companion object {
        /**
         * Recent memories query.
         */
        fun recent(limit: Int = 10): MemoryQuery =
            MemoryQuery(query = "", limit = limit, useSemantic = false)

        /**
         * Semantic search query.
         */
        fun semantic(query: String, limit: Int = 5): MemoryQuery =
            MemoryQuery(query = query, limit = limit, useSemantic = true)

        /**
         * Session memories query.
         */
        fun forSession(sessionId: String, limit: Int = 50): MemoryQuery =
            MemoryQuery(query = "", sessionId = sessionId, limit = limit, useSemantic = false)

        /**
         * Facts and preferences query.
         */
        fun persistent(): MemoryQuery =
            MemoryQuery(
                query = "",
                types = MemoryType.PERSISTENT_TYPES,
                useSemantic = false,
                limit = 100
            )
    }
}

// =============================================================================
// Memory Statistics
// =============================================================================

/**
 * Memory system statistics.
 */
data class MemoryStats(
    val totalMemories: Int,
    val memoriesWithEmbeddings: Int,
    val sessionCount: Int,
    val oldestMemory: Instant?,
    val newestMemory: Instant?,
    val typeBreakdown: Map<MemoryType, Int>,
    val averageImportance: Float
) {
    val embeddingCoverage: Float
        get() = if (totalMemories > 0) memoriesWithEmbeddings.toFloat() / totalMemories else 0f

    val isEmpty: Boolean get() = totalMemories == 0

    companion object {
        val EMPTY = MemoryStats(
            totalMemories = 0,
            memoriesWithEmbeddings = 0,
            sessionCount = 0,
            oldestMemory = null,
            newestMemory = null,
            typeBreakdown = emptyMap(),
            averageImportance = 0f
        )
    }
}

// =============================================================================
// Memory Events
// =============================================================================

/**
 * Events from the memory system.
 */
sealed class MemoryEvent {
    abstract val timestamp: Instant

    data class MemoryAdded(
        val memory: MemoryEntry,
        override val timestamp: Instant = Instant.now()
    ) : MemoryEvent()

    data class MemoryRecalled(
        val query: String,
        val resultCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : MemoryEvent()

    data class SessionStarted(
        val session: SessionContext,
        override val timestamp: Instant = Instant.now()
    ) : MemoryEvent()

    data class SessionEnded(
        val sessionId: String,
        val durationMs: Long,
        val memoryCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : MemoryEvent()

    data class MemoriesPruned(
        val count: Int,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : MemoryEvent()

    data class SummaryGenerated(
        val sessionId: String,
        val summary: String,
        override val timestamp: Instant = Instant.now()
    ) : MemoryEvent()
}

// =============================================================================
// Memory Utilities
// =============================================================================

/**
 * Utilities for memory operations.
 */
object MemoryUtils {

    /**
     * Calculates cosine similarity between embeddings.
     */
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val magnitude = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (magnitude > 0) dotProduct / magnitude else 0f
    }

    /**
     * Calculates decay factor based on age.
     */
    fun decayFactor(ageMs: Long, halfLifeMs: Long = 24 * 60 * 60 * 1000): Float {
        return kotlin.math.exp(-0.693 * ageMs / halfLifeMs).toFloat()
    }

    /**
     * Scores a memory for relevance.
     */
    fun scoreMemory(
        memory: MemoryEntry,
        querySimilarity: Float,
        now: Instant = Instant.now()
    ): Float {
        val ageFactor = decayFactor(memory.ageMs(now))
        return (querySimilarity * 0.6f) + (memory.importance * 0.3f) + (ageFactor * 0.1f)
    }

    /**
     * Truncates content to max length.
     */
    fun truncate(content: String, maxLength: Int = 500): String {
        return if (content.length <= maxLength) content
        else content.take(maxLength - 3) + "..."
    }

    /**
     * Extracts keywords from text.
     */
    fun extractKeywords(text: String, maxKeywords: Int = 10): List<String> {
        return text.lowercase()
            .split(Regex("[\\s,.:;!?()\\[\\]{}]+"))
            .filter { it.length > 3 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(maxKeywords)
            .map { it.key }
    }
}
