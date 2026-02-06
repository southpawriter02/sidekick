package com.sidekick.agent.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.sidekick.llm.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * # Code Index Service
 *
 * Project-level service for indexing and searching code.
 * Part of Sidekick v0.8.4 Code Understanding feature.
 *
 * ## Overview
 *
 * The service provides:
 * - Project-wide code indexing
 * - Semantic vector search
 * - Symbol extraction and lookup
 * - Keyword/regex search
 *
 * @since 0.8.4
 */
@Service(Service.Level.PROJECT)
class CodeIndexService(private val project: Project) {

    private val logger = Logger.getInstance(CodeIndexService::class.java)

    // Index storage
    private val chunks = ConcurrentHashMap<String, CodeChunk>()
    private val symbols = ConcurrentHashMap<String, SymbolInfo>()
    private val fileChunks = ConcurrentHashMap<String, MutableList<String>>() // filePath -> chunkIds

    // State
    private var lastIndexed: Instant? = null
    private var indexing = false

    // Event listeners
    private val eventListeners = mutableListOf<(IndexEvent) -> Unit>()

    companion object {
        fun getInstance(project: Project): CodeIndexService {
            return project.getService(CodeIndexService::class.java)
        }

        /**
         * Default chunk size in lines.
         */
        const val DEFAULT_CHUNK_SIZE = 50

        /**
         * Overlap between chunks in lines.
         */
        const val DEFAULT_CHUNK_OVERLAP = 10

        /**
         * Maximum content length for embedding.
         */
        const val MAX_EMBEDDING_CONTENT = 8000
    }

    // =========================================================================
    // Indexing
    // =========================================================================

    /**
     * Indexes the entire project.
     *
     * @param generateEmbeddings Whether to generate embeddings
     */
    suspend fun indexProject(generateEmbeddings: Boolean = true) {
        if (indexing) {
            logger.warn("Indexing already in progress")
            return
        }

        indexing = true
        val startTime = System.currentTimeMillis()

        try {
            chunks.clear()
            symbols.clear()
            fileChunks.clear()

            val sourceRoots = getSourceRoots()
            val files = collectSourceFiles(sourceRoots)

            emitEvent(IndexEvent.IndexStarted(files.size))
            logger.info("Indexing ${files.size} files")

            for (file in files) {
                try {
                    indexFile(file, generateEmbeddings)
                } catch (e: Exception) {
                    logger.warn("Error indexing ${file.path}: ${e.message}")
                    emitEvent(IndexEvent.IndexError(file.path, e.message ?: "Unknown error"))
                }
            }

            lastIndexed = Instant.now()
            val stats = getStats()
            val duration = System.currentTimeMillis() - startTime

            emitEvent(IndexEvent.IndexCompleted(stats, duration))
            logger.info("Indexing completed: ${stats.totalChunks} chunks, ${stats.totalSymbols} symbols in ${duration}ms")

        } finally {
            indexing = false
        }
    }

    /**
     amer* Indexes a single file.
     */
    suspend fun indexFile(file: VirtualFile, generateEmbeddings: Boolean = true) {
        val filePath = file.path
        val content = withContext(Dispatchers.IO) {
            String(file.contentsToByteArray())
        }

        // Remove old chunks for this file
        fileChunks[filePath]?.forEach { chunks.remove(it) }
        fileChunks[filePath] = mutableListOf()

        // Extract chunks
        val fileChunkList = extractChunks(filePath, content, detectLanguage(file))

        // Generate embeddings if enabled
        val provider = if (generateEmbeddings) {
            ProviderManager.getInstance().getActiveProvider()
        } else null

        for (chunk in fileChunkList) {
            val finalChunk = if (provider != null && chunk.content.length <= MAX_EMBEDDING_CONTENT) {
                try {
                    val embedding = provider.embed(chunk.content)
                    chunk.withEmbedding(embedding)
                } catch (e: Exception) {
                    logger.debug("Failed to embed chunk: ${e.message}")
                    chunk
                }
            } else {
                chunk
            }

            chunks[finalChunk.id] = finalChunk
            fileChunks.getOrPut(filePath) { mutableListOf() }.add(finalChunk.id)
        }

        // Extract symbols
        val fileSymbols = extractSymbols(filePath, content)
        fileSymbols.forEach { symbols[it.fullName] = it }

        emitEvent(IndexEvent.FileIndexed(filePath, fileChunkList.size, fileSymbols.size))
    }

    /**
     * Removes a file from the index.
     */
    fun removeFile(filePath: String) {
        fileChunks[filePath]?.forEach { chunks.remove(it) }
        fileChunks.remove(filePath)
        symbols.values.removeIf { it.filePath == filePath }
    }

    // =========================================================================
    // Search
    // =========================================================================

    /**
     * Performs semantic search across the codebase.
     *
     * @param query Search query
     * @param limit Maximum results
     * @return Ranked search results
     */
    suspend fun semanticSearch(query: String, limit: Int = 10): List<CodeSearchResult> {
        val provider = ProviderManager.getInstance().getActiveProvider()
            ?: return emptyList()

        val queryEmbedding = try {
            provider.embed(query)
        } catch (e: Exception) {
            logger.warn("Failed to embed query: ${e.message}")
            return emptyList()
        }

        return chunks.values
            .filter { it.hasEmbedding }
            .map { chunk ->
                CodeSearchResult.semantic(
                    chunk = chunk,
                    similarity = EmbeddingUtils.cosineSimilarity(queryEmbedding, chunk.embedding!!)
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Performs keyword search.
     *
     * @param query Search text
     * @param limit Maximum results
     * @param caseSensitive Case-sensitive match
     * @return Matched results
     */
    fun keywordSearch(query: String, limit: Int = 10, caseSensitive: Boolean = false): List<CodeSearchResult> {
        val searchQuery = if (caseSensitive) query else query.lowercase()

        return chunks.values
            .mapNotNull { chunk ->
                val content = if (caseSensitive) chunk.content else chunk.content.lowercase()
                val matchCount = content.split(searchQuery).size - 1
                if (matchCount > 0) {
                    CodeSearchResult.keyword(chunk, matchCount)
                } else null
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Combined search using multiple strategies.
     */
    suspend fun search(query: SearchQuery): List<CodeSearchResult> {
        val results = mutableListOf<CodeSearchResult>()

        // Semantic search
        if (query.useSemanticSearch) {
            results.addAll(semanticSearch(query.query, query.limit))
        }

        // Keyword search
        if (MatchType.KEYWORD in query.matchTypes) {
            results.addAll(keywordSearch(query.query, query.limit))
        }

        // Symbol search
        if (MatchType.SYMBOL in query.matchTypes) {
            findSymbol(query.query)?.let { symbol ->
                chunks.values
                    .find { it.filePath == symbol.filePath && symbol.startLine in it.lineRange }
                    ?.let { results.add(CodeSearchResult.symbol(it)) }
            }
        }

        // Apply filters
        var filtered = results
            .filter { it.score >= query.minScore }

        if (query.hasTypeFilter) {
            filtered = filtered.filter { it.chunk.type in query.types!! }
        }

        if (query.hasFileFilter) {
            filtered = filtered.filter { result ->
                query.files!!.any { pattern ->
                    result.chunk.filePath.contains(pattern) ||
                    result.chunk.filePath.matches(Regex(pattern.replace("*", ".*")))
                }
            }
        }

        // Dedupe and sort
        return filtered
            .distinctBy { it.chunk.id }
            .sortedByDescending { it.weightedScore }
            .take(query.limit)
    }

    // =========================================================================
    // Symbol Lookup
    // =========================================================================

    /**
     * Finds a symbol by name.
     */
    fun findSymbol(name: String): SymbolInfo? {
        return symbols[name] ?: symbols.values.find { it.name == name }
    }

    /**
     * Gets all symbols in a file.
     */
    fun getFileSymbols(filePath: String): List<SymbolInfo> {
        return symbols.values.filter { it.filePath == filePath }
    }

    /**
     * Gets symbols by kind.
     */
    fun getSymbolsByKind(kind: SymbolKind): List<SymbolInfo> {
        return symbols.values.filter { it.kind == kind }
    }

    /**
     * Searches symbols by name pattern.
     */
    fun searchSymbols(pattern: String, limit: Int = 20): List<SymbolInfo> {
        val regex = Regex(pattern.replace("*", ".*"), RegexOption.IGNORE_CASE)
        return symbols.values
            .filter { regex.matches(it.name) || regex.matches(it.fullName) }
            .take(limit)
    }

    // =========================================================================
    // Chunk Access
    // =========================================================================

    /**
     * Gets a chunk by ID.
     */
    fun getChunk(id: String): CodeChunk? = chunks[id]

    /**
     * Gets all chunks for a file.
     */
    fun getFileChunks(filePath: String): List<CodeChunk> {
        return fileChunks[filePath]?.mapNotNull { chunks[it] } ?: emptyList()
    }

    /**
     * Gets chunks at a specific line.
     */
    fun getChunksAtLine(filePath: String, line: Int): List<CodeChunk> {
        return getFileChunks(filePath).filter { line in it.lineRange }
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets index statistics.
     */
    fun getStats(): IndexStats {
        val languageBreakdown = chunks.values
            .mapNotNull { it.language }
            .groupingBy { it }
            .eachCount()

        return IndexStats(
            totalChunks = chunks.size,
            totalSymbols = symbols.size,
            chunksWithEmbeddings = chunks.values.count { it.hasEmbedding },
            fileCount = fileChunks.size,
            languageBreakdown = languageBreakdown,
            lastIndexed = lastIndexed
        )
    }

    /**
     * Whether the index is empty.
     */
    fun isEmpty(): Boolean = chunks.isEmpty()

    /**
     * Whether indexing is in progress.
     */
    fun isIndexing(): Boolean = indexing

    // =========================================================================
    // Events
    // =========================================================================

    fun addListener(listener: (IndexEvent) -> Unit) {
        eventListeners.add(listener)
    }

    fun removeListener(listener: (IndexEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: IndexEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Chunk Extraction
    // =========================================================================

    private fun extractChunks(
        filePath: String,
        content: String,
        language: String?
    ): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        val lines = content.lines()

        // File-level chunk
        chunks.add(CodeChunk.file(filePath, content, language))

        // Split into overlapping chunks
        var i = 0
        while (i < lines.size) {
            val endLine = minOf(i + DEFAULT_CHUNK_SIZE, lines.size)
            val chunkContent = lines.subList(i, endLine).joinToString("\n")

            if (chunkContent.isNotBlank()) {
                chunks.add(CodeChunk(
                    filePath = filePath,
                    startLine = i + 1,
                    endLine = endLine,
                    content = chunkContent,
                    type = ChunkType.BLOCK,
                    language = language
                ))
            }

            i += DEFAULT_CHUNK_SIZE - DEFAULT_CHUNK_OVERLAP
        }

        // Extract method/class chunks (simplified - would use PSI in production)
        extractStructuralChunks(filePath, content, language, lines, chunks)

        return chunks
    }

    private fun extractStructuralChunks(
        filePath: String,
        content: String,
        language: String?,
        lines: List<String>,
        chunks: MutableList<CodeChunk>
    ) {
        // Simple pattern-based extraction (in production would use PSI)
        val classPattern = Regex("""^\s*(class|interface|object|enum class)\s+(\w+)""")
        val funPattern = Regex("""^\s*(fun|suspend fun|private fun|override fun)\s+(\w+)""")

        var currentClass: Pair<Int, String>? = null
        var braceCount = 0

        for ((index, line) in lines.withIndex()) {
            val lineNum = index + 1

            // Track class start
            classPattern.find(line)?.let { match ->
                currentClass = lineNum to match.groupValues[2]
            }

            // Track function start
            funPattern.find(line)?.let { match ->
                val funcName = match.groupValues[2]
                val startLine = lineNum

                // Find function end (simplified)
                var endLine = startLine
                var depth = 0
                var started = false
                for (j in index until minOf(index + 100, lines.size)) {
                    val l = lines[j]
                    if (l.contains("{")) {
                        started = true
                        depth += l.count { it == '{' }
                    }
                    if (l.contains("}")) {
                        depth -= l.count { it == '}' }
                    }
                    if (started && depth <= 0) {
                        endLine = j + 1
                        break
                    }
                }

                if (endLine > startLine) {
                    chunks.add(CodeChunk.method(
                        filePath = filePath,
                        startLine = startLine,
                        endLine = endLine,
                        content = lines.subList(index, endLine).joinToString("\n"),
                        methodName = funcName
                    ))
                }
            }
        }
    }

    // =========================================================================
    // Symbol Extraction
    // =========================================================================

    private fun extractSymbols(filePath: String, content: String): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()
        val lines = content.lines()

        val classPattern = Regex("""^\s*(class|interface|object|enum class)\s+(\w+)""")
        val funPattern = Regex("""^\s*(fun|suspend fun)\s+(\w+)\s*\(([^)]*)\)(\s*:\s*(\w+))?""")
        val valPattern = Regex("""^\s*(val|var)\s+(\w+)\s*:?\s*(\w+)?""")

        var currentClass: String? = null

        for ((index, line) in lines.withIndex()) {
            val lineNum = index + 1

            classPattern.find(line)?.let { match ->
                val kind = match.groupValues[1]
                val name = match.groupValues[2]
                currentClass = name

                symbols.add(SymbolInfo(
                    name = name,
                    kind = when (kind) {
                        "interface" -> SymbolKind.INTERFACE
                        "object" -> SymbolKind.CLASS
                        "enum class" -> SymbolKind.ENUM
                        else -> SymbolKind.CLASS
                    },
                    filePath = filePath,
                    startLine = lineNum,
                    endLine = lineNum + 10, // Simplified
                    signature = line.trim()
                ))
            }

            funPattern.find(line)?.let { match ->
                val name = match.groupValues[2]
                val params = match.groupValues[3]
                val returnType = match.groupValues.getOrNull(5)

                symbols.add(SymbolInfo.method(
                    name = name,
                    parentClass = currentClass ?: "",
                    filePath = filePath,
                    startLine = lineNum,
                    endLine = lineNum + 5, // Simplified
                    signature = "fun $name($params)${returnType?.let { ": $it" } ?: ""}"
                ))
            }

            valPattern.find(line)?.let { match ->
                val name = match.groupValues[2]
                symbols.add(SymbolInfo(
                    name = name,
                    kind = SymbolKind.PROPERTY,
                    filePath = filePath,
                    startLine = lineNum,
                    endLine = lineNum,
                    signature = line.trim(),
                    parent = currentClass
                ))
            }
        }

        return symbols
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private fun getSourceRoots(): List<VirtualFile> {
        // In production would use ProjectRootManager
        return emptyList()
    }

    private fun collectSourceFiles(roots: List<VirtualFile>): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val extensions = setOf("kt", "java", "py", "js", "ts", "go", "rs", "cpp", "c", "cs")

        fun visit(file: VirtualFile) {
            if (file.isDirectory) {
                file.children.forEach { visit(it) }
            } else if (file.extension in extensions) {
                files.add(file)
            }
        }

        roots.forEach { visit(it) }
        return files
    }

    private fun detectLanguage(file: VirtualFile): String? {
        return when (file.extension) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "go" -> "go"
            "rs" -> "rust"
            "cpp", "cc", "cxx" -> "cpp"
            "c", "h" -> "c"
            "cs" -> "csharp"
            else -> null
        }
    }
}
