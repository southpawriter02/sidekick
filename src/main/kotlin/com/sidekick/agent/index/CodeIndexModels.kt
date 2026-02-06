package com.sidekick.agent.index

import java.time.Instant
import java.util.UUID
import kotlin.math.sqrt

/**
 * # Code Index Models
 *
 * Data models for code understanding and semantic search.
 * Part of Sidekick v0.8.4 Code Understanding feature.
 *
 * ## Overview
 *
 * The code index provides:
 * - Chunked code for embedding
 * - Symbol extraction from AST
 * - Semantic search with cosine similarity
 * - Multi-type matching (semantic, keyword, symbol)
 *
 * @since 0.8.4
 */

// =============================================================================
// Code Chunk
// =============================================================================

/**
 * Indexed code chunk for semantic search.
 *
 * @property id Unique chunk identifier
 * @property filePath Absolute path to source file
 * @property startLine Starting line (1-indexed)
 * @property endLine Ending line (1-indexed)
 * @property content Chunk text content
 * @property type Type of chunk
 * @property symbolName Associated symbol name
 * @property embedding Vector embedding (if computed)
 * @property language Programming language
 * @property lastIndexed When this chunk was indexed
 */
data class CodeChunk(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val type: ChunkType,
    val symbolName: String? = null,
    val embedding: List<Float>? = null,
    val language: String? = null,
    val lastIndexed: Instant = Instant.now()
) {
    /**
     * Number of lines in this chunk.
     */
    val lineCount: Int get() = endLine - startLine + 1

    /**
     * Whether this chunk has an embedding.
     */
    val hasEmbedding: Boolean get() = embedding != null

    /**
     * Whether this is a symbol-level chunk (class, method, etc.)
     */
    val isSymbol: Boolean get() = type in ChunkType.SYMBOL_TYPES

    /**
     * Creates a copy with embedding.
     */
    fun withEmbedding(embedding: List<Float>): CodeChunk = copy(embedding = embedding)

    /**
     * Line range as IntRange.
     */
    val lineRange: IntRange get() = startLine..endLine

    companion object {
        /**
         * Creates a file-level chunk.
         */
        fun file(
            filePath: String,
            content: String,
            language: String? = null
        ): CodeChunk {
            val lines = content.lines().size
            return CodeChunk(
                filePath = filePath,
                startLine = 1,
                endLine = lines,
                content = content,
                type = ChunkType.FILE,
                language = language
            )
        }

        /**
         * Creates a method chunk.
         */
        fun method(
            filePath: String,
            startLine: Int,
            endLine: Int,
            content: String,
            methodName: String
        ): CodeChunk = CodeChunk(
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            content = content,
            type = ChunkType.METHOD,
            symbolName = methodName
        )

        /**
         * Creates a class chunk.
         */
        fun clazz(
            filePath: String,
            startLine: Int,
            endLine: Int,
            content: String,
            className: String
        ): CodeChunk = CodeChunk(
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            content = content,
            type = ChunkType.CLASS,
            symbolName = className
        )
    }
}

/**
 * Types of code chunks.
 */
enum class ChunkType(val displayName: String, val priority: Int) {
    FILE("File", 1),
    CLASS("Class", 10),
    INTERFACE("Interface", 10),
    METHOD("Method", 20),
    FUNCTION("Function", 20),
    PROPERTY("Property", 15),
    BLOCK("Block", 5),
    COMMENT("Comment", 2),
    IMPORT("Import", 1),
    DECLARATION("Declaration", 8);

    override fun toString(): String = displayName

    companion object {
        /**
         * Types that represent symbols.
         */
        val SYMBOL_TYPES = setOf(CLASS, INTERFACE, METHOD, FUNCTION, PROPERTY)

        /**
         * Types that represent code blocks.
         */
        val BLOCK_TYPES = setOf(FILE, CLASS, INTERFACE, METHOD, FUNCTION, BLOCK)
    }
}

// =============================================================================
// Symbol Info
// =============================================================================

/**
 * Symbol information from AST analysis.
 *
 * @property name Symbol name
 * @property kind Symbol category
 * @property filePath File containing the symbol
 * @property startLine Definition start line
 * @property endLine Definition end line
 * @property signature Type signature or declaration
 * @property documentation Doc comment content
 * @property modifiers Access modifiers
 * @property parent Parent symbol name
 * @property references Locations where symbol is used
 */
data class SymbolInfo(
    val name: String,
    val kind: SymbolKind,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val signature: String? = null,
    val documentation: String? = null,
    val modifiers: Set<SymbolModifier> = emptySet(),
    val parent: String? = null,
    val references: List<SymbolReference> = emptyList()
) {
    /**
     * Line range as IntRange.
     */
    val range: IntRange get() = startLine..endLine

    /**
     * Fully qualified name.
     */
    val fullName: String get() = if (parent != null) "$parent.$name" else name

    /**
     * Whether this is a public symbol.
     */
    val isPublic: Boolean get() = SymbolModifier.PUBLIC in modifiers

    /**
     * Whether this is a type (class/interface).
     */
    val isType: Boolean get() = kind in listOf(SymbolKind.CLASS, SymbolKind.INTERFACE, SymbolKind.ENUM)

    /**
     * Whether this is callable (function/method).
     */
    val isCallable: Boolean get() = kind in listOf(SymbolKind.METHOD, SymbolKind.FUNCTION, SymbolKind.CONSTRUCTOR)

    /**
     * Number of references to this symbol.
     */
    val referenceCount: Int get() = references.size

    companion object {
        /**
         * Creates a class symbol.
         */
        fun clazz(
            name: String,
            filePath: String,
            startLine: Int,
            endLine: Int,
            signature: String? = null,
            documentation: String? = null
        ) = SymbolInfo(
            name = name,
            kind = SymbolKind.CLASS,
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            signature = signature,
            documentation = documentation
        )

        /**
         * Creates a method symbol.
         */
        fun method(
            name: String,
            parentClass: String,
            filePath: String,
            startLine: Int,
            endLine: Int,
            signature: String? = null
        ) = SymbolInfo(
            name = name,
            kind = SymbolKind.METHOD,
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            signature = signature,
            parent = parentClass
        )
    }
}

/**
 * Types of symbols.
 */
enum class SymbolKind(val displayName: String) {
    FILE("File"),
    PACKAGE("Package"),
    CLASS("Class"),
    INTERFACE("Interface"),
    ENUM("Enum"),
    STRUCT("Struct"),
    TRAIT("Trait"),
    METHOD("Method"),
    FUNCTION("Function"),
    CONSTRUCTOR("Constructor"),
    PROPERTY("Property"),
    FIELD("Field"),
    VARIABLE("Variable"),
    CONSTANT("Constant"),
    PARAMETER("Parameter"),
    TYPE_PARAMETER("Type Parameter"),
    ANNOTATION("Annotation"),
    MODULE("Module");

    override fun toString(): String = displayName

    companion object {
        val TYPE_KINDS = setOf(CLASS, INTERFACE, ENUM, STRUCT, TRAIT)
        val CALLABLE_KINDS = setOf(METHOD, FUNCTION, CONSTRUCTOR)
        val VARIABLE_KINDS = setOf(PROPERTY, FIELD, VARIABLE, CONSTANT, PARAMETER)
    }
}

/**
 * Symbol modifiers.
 */
enum class SymbolModifier {
    PUBLIC, PRIVATE, PROTECTED, INTERNAL,
    STATIC, FINAL, ABSTRACT, SEALED,
    OVERRIDE, VIRTUAL, SUSPEND, INLINE,
    DEPRECATED, EXPERIMENTAL
}

/**
 * Reference to a symbol usage.
 */
data class SymbolReference(
    val filePath: String,
    val line: Int,
    val column: Int = 0,
    val context: String = "",
    val kind: ReferenceKind = ReferenceKind.READ
) {
    companion object {
        fun read(filePath: String, line: Int, context: String = "") =
            SymbolReference(filePath, line, context = context, kind = ReferenceKind.READ)

        fun write(filePath: String, line: Int, context: String = "") =
            SymbolReference(filePath, line, context = context, kind = ReferenceKind.WRITE)

        fun call(filePath: String, line: Int, context: String = "") =
            SymbolReference(filePath, line, context = context, kind = ReferenceKind.CALL)
    }
}

/**
 * Types of references.
 */
enum class ReferenceKind {
    READ, WRITE, CALL, IMPORT, EXTEND, IMPLEMENT, ANNOTATE
}

// =============================================================================
// Search Results
// =============================================================================

/**
 * Code search result.
 *
 * @property chunk Matched chunk
 * @property score Match score (0-1 for semantic, higher for exact)
 * @property matchType How the match was found
 * @property highlights Text ranges to highlight
 */
data class CodeSearchResult(
    val chunk: CodeChunk,
    val score: Float,
    val matchType: MatchType,
    val highlights: List<IntRange> = emptyList()
) {
    /**
     * Whether this is a high-confidence match.
     */
    val isHighConfidence: Boolean get() = score >= 0.8f

    /**
     * Combined score accounting for match type.
     */
    val weightedScore: Float get() = score * matchType.weight

    companion object {
        /**
         * Creates a semantic search result.
         */
        fun semantic(chunk: CodeChunk, similarity: Float) = CodeSearchResult(
            chunk = chunk,
            score = similarity,
            matchType = MatchType.SEMANTIC
        )

        /**
         * Creates a keyword search result.
         */
        fun keyword(chunk: CodeChunk, matchCount: Int) = CodeSearchResult(
            chunk = chunk,
            score = matchCount.toFloat() / 10f,
            matchType = MatchType.KEYWORD
        )

        /**
         * Creates a symbol match result.
         */
        fun symbol(chunk: CodeChunk) = CodeSearchResult(
            chunk = chunk,
            score = 1.0f,
            matchType = MatchType.SYMBOL
        )
    }
}

/**
 * Types of search matches.
 */
enum class MatchType(val displayName: String, val weight: Float) {
    SEMANTIC("Semantic", 1.0f),
    KEYWORD("Keyword", 0.8f),
    SYMBOL("Symbol", 1.2f),
    FUZZY("Fuzzy", 0.6f),
    REGEX("Regex", 0.9f);

    override fun toString(): String = displayName
}

// =============================================================================
// Search Query
// =============================================================================

/**
 * Search query options.
 *
 * @property query Search text
 * @property limit Max results
 * @property types Chunk types to include
 * @property files File patterns to search
 * @property minScore Minimum match score
 * @property matchTypes Match types to use
 * @property includeEmbeddings Whether to run semantic search
 */
data class SearchQuery(
    val query: String,
    val limit: Int = 10,
    val types: Set<ChunkType>? = null,
    val files: List<String>? = null,
    val minScore: Float = 0.0f,
    val matchTypes: Set<MatchType> = setOf(MatchType.SEMANTIC, MatchType.KEYWORD),
    val includeEmbeddings: Boolean = true
) {
    /**
     * Whether to filter by chunk type.
     */
    val hasTypeFilter: Boolean get() = types != null && types.isNotEmpty()

    /**
     * Whether to filter by file.
     */
    val hasFileFilter: Boolean get() = files != null && files.isNotEmpty()

    /**
     * Whether semantic search is enabled.
     */
    val useSemanticSearch: Boolean get() = includeEmbeddings && MatchType.SEMANTIC in matchTypes

    companion object {
        /**
         * Simple keyword search.
         */
        fun keyword(query: String, limit: Int = 10) = SearchQuery(
            query = query,
            limit = limit,
            matchTypes = setOf(MatchType.KEYWORD),
            includeEmbeddings = false
        )

        /**
         * Semantic search only.
         */
        fun semantic(query: String, limit: Int = 10) = SearchQuery(
            query = query,
            limit = limit,
            matchTypes = setOf(MatchType.SEMANTIC),
            includeEmbeddings = true
        )

        /**
         * Symbol search.
         */
        fun symbol(name: String) = SearchQuery(
            query = name,
            limit = 5,
            matchTypes = setOf(MatchType.SYMBOL),
            includeEmbeddings = false
        )
    }
}

// =============================================================================
// Index Statistics
// =============================================================================

/**
 * Statistics about the code index.
 */
data class IndexStats(
    val totalChunks: Int,
    val totalSymbols: Int,
    val chunksWithEmbeddings: Int,
    val fileCount: Int,
    val languageBreakdown: Map<String, Int>,
    val lastIndexed: Instant?
) {
    /**
     * Percentage of chunks with embeddings.
     */
    val embeddingCoverage: Float
        get() = if (totalChunks > 0) chunksWithEmbeddings.toFloat() / totalChunks else 0f

    /**
     * Whether the index is empty.
     */
    val isEmpty: Boolean get() = totalChunks == 0

    companion object {
        val EMPTY = IndexStats(0, 0, 0, 0, emptyMap(), null)
    }
}

// =============================================================================
// Embedding Utilities
// =============================================================================

/**
 * Utilities for embedding operations.
 */
object EmbeddingUtils {

    /**
     * Computes cosine similarity between two vectors.
     */
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * Computes euclidean distance between two vectors.
     */
    fun euclideanDistance(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return Float.MAX_VALUE

        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Normalizes a vector to unit length.
     */
    fun normalize(vector: List<Float>): List<Float> {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0) vector.map { it / norm } else vector
    }

    /**
     * Averages multiple embeddings.
     */
    fun average(embeddings: List<List<Float>>): List<Float> {
        if (embeddings.isEmpty()) return emptyList()
        val size = embeddings.first().size
        val result = FloatArray(size)

        for (embedding in embeddings) {
            for (i in embedding.indices) {
                result[i] += embedding[i]
            }
        }

        return result.map { it / embeddings.size }
    }
}

// =============================================================================
// Index Events
// =============================================================================

/**
 * Events during indexing.
 */
sealed class IndexEvent {
    abstract val timestamp: Instant

    data class IndexStarted(
        val fileCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : IndexEvent()

    data class FileIndexed(
        val filePath: String,
        val chunkCount: Int,
        val symbolCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : IndexEvent()

    data class IndexCompleted(
        val stats: IndexStats,
        val durationMs: Long,
        override val timestamp: Instant = Instant.now()
    ) : IndexEvent()

    data class IndexError(
        val filePath: String?,
        val error: String,
        override val timestamp: Instant = Instant.now()
    ) : IndexEvent()
}
