package com.sidekick.agent.index

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Code Index Service logic.
 *
 * Note: These tests focus on the service's pure logic
 * without requiring IntelliJ Platform or LLM provider.
 */
@DisplayName("Code Index Service Tests")
class CodeIndexServiceTest {

    // =========================================================================
    // Constants Tests
    // =========================================================================

    @Nested
    @DisplayName("Constants")
    inner class ConstantsTests {

        @Test
        @DisplayName("default chunk size is reasonable")
        fun defaultChunkSizeIsReasonable() {
            assertTrue(CodeIndexService.DEFAULT_CHUNK_SIZE > 0)
            assertTrue(CodeIndexService.DEFAULT_CHUNK_SIZE <= 200)
        }

        @Test
        @DisplayName("chunk overlap is less than chunk size")
        fun chunkOverlapIsLessThanChunkSize() {
            assertTrue(CodeIndexService.DEFAULT_CHUNK_OVERLAP < CodeIndexService.DEFAULT_CHUNK_SIZE)
        }

        @Test
        @DisplayName("max embedding content is reasonable")
        fun maxEmbeddingContentIsReasonable() {
            assertTrue(CodeIndexService.MAX_EMBEDDING_CONTENT > 1000)
        }
    }

    // =========================================================================
    // Chunk Building Tests
    // =========================================================================

    @Nested
    @DisplayName("Chunk Building")
    inner class ChunkBuildingTests {

        @Test
        @DisplayName("file chunk covers entire content")
        fun fileChunkCoversEntireContent() {
            val content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"
            val chunk = CodeChunk.file("/test.kt", content, "kotlin")

            assertEquals(1, chunk.startLine)
            assertEquals(5, chunk.endLine)
            assertEquals(5, chunk.lineCount)
        }

        @Test
        @DisplayName("method chunk has correct range")
        fun methodChunkHasCorrectRange() {
            val chunk = CodeChunk.method(
                filePath = "/test.kt",
                startLine = 10,
                endLine = 25,
                content = "fun example() {\n  // code\n}",
                methodName = "example"
            )

            assertEquals(10, chunk.startLine)
            assertEquals(25, chunk.endLine)
            assertEquals("example", chunk.symbolName)
            assertTrue(chunk.isSymbol)
        }

        @Test
        @DisplayName("class chunk has correct type")
        fun classChunkHasCorrectType() {
            val chunk = CodeChunk.clazz(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 50,
                content = "class Example { }",
                className = "Example"
            )

            assertEquals(ChunkType.CLASS, chunk.type)
            assertEquals("Example", chunk.symbolName)
        }
    }

    // =========================================================================
    // Symbol Extraction Tests
    // =========================================================================

    @Nested
    @DisplayName("Symbol Structure")
    inner class SymbolStructureTests {

        @Test
        @DisplayName("class symbol has correct kind")
        fun classSymbolHasCorrectKind() {
            val symbol = SymbolInfo.clazz(
                name = "MyClass",
                filePath = "/test.kt",
                startLine = 1,
                endLine = 50,
                signature = "class MyClass : BaseClass"
            )

            assertEquals(SymbolKind.CLASS, symbol.kind)
            assertTrue(symbol.isType)
            assertFalse(symbol.isCallable)
        }

        @Test
        @DisplayName("method symbol has parent")
        fun methodSymbolHasParent() {
            val symbol = SymbolInfo.method(
                name = "doWork",
                parentClass = "Worker",
                filePath = "/test.kt",
                startLine = 10,
                endLine = 20,
                signature = "fun doWork(): Unit"
            )

            assertEquals("Worker", symbol.parent)
            assertEquals("Worker.doWork", symbol.fullName)
        }

        @Test
        @DisplayName("symbol range is correct")
        fun symbolRangeIsCorrect() {
            val symbol = SymbolInfo(
                name = "test",
                kind = SymbolKind.FUNCTION,
                filePath = "/test.kt",
                startLine = 5,
                endLine = 15
            )

            assertEquals(5..15, symbol.range)
            assertTrue(10 in symbol.range)
            assertFalse(20 in symbol.range)
        }
    }

    // =========================================================================
    // Search Query Tests
    // =========================================================================

    @Nested
    @DisplayName("Search Query")
    inner class SearchQueryTests {

        @Test
        @DisplayName("default query has semantic and keyword")
        fun defaultQueryHasSemanticAndKeyword() {
            val query = SearchQuery(query = "find something")

            assertTrue(MatchType.SEMANTIC in query.matchTypes)
            assertTrue(MatchType.KEYWORD in query.matchTypes)
            assertTrue(query.useSemanticSearch)
        }

        @Test
        @DisplayName("keyword-only query")
        fun keywordOnlyQuery() {
            val query = SearchQuery.keyword("search term")

            assertTrue(MatchType.KEYWORD in query.matchTypes)
            assertFalse(MatchType.SEMANTIC in query.matchTypes)
            assertFalse(query.useSemanticSearch)
        }

        @Test
        @DisplayName("semantic-only query")
        fun semanticOnlyQuery() {
            val query = SearchQuery.semantic("search term")

            assertTrue(MatchType.SEMANTIC in query.matchTypes)
            assertFalse(MatchType.KEYWORD in query.matchTypes)
            assertTrue(query.useSemanticSearch)
        }

        @Test
        @DisplayName("symbol query")
        fun symbolQuery() {
            val query = SearchQuery.symbol("MyClass")

            assertTrue(MatchType.SYMBOL in query.matchTypes)
            assertEquals(5, query.limit)
            assertFalse(query.useSemanticSearch)
        }

        @Test
        @DisplayName("query with type filter")
        fun queryWithTypeFilter() {
            val query = SearchQuery(
                query = "find methods",
                types = setOf(ChunkType.METHOD, ChunkType.FUNCTION)
            )

            assertTrue(query.hasTypeFilter)
            assertTrue(ChunkType.METHOD in query.types!!)
        }

        @Test
        @DisplayName("query with file filter")
        fun queryWithFileFilter() {
            val query = SearchQuery(
                query = "find in test files",
                files = listOf("*Test.kt", "*Spec.kt")
            )

            assertTrue(query.hasFileFilter)
            assertEquals(2, query.files!!.size)
        }
    }

    // =========================================================================
    // Search Result Tests
    // =========================================================================

    @Nested
    @DisplayName("Search Results")
    inner class SearchResultTests {

        private fun createChunk(type: ChunkType = ChunkType.METHOD): CodeChunk {
            return CodeChunk(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                content = "fun test() {}",
                type = type,
                symbolName = "test"
            )
        }

        @Test
        @DisplayName("semantic result has correct type")
        fun semanticResultHasCorrectType() {
            val result = CodeSearchResult.semantic(createChunk(), 0.85f)

            assertEquals(MatchType.SEMANTIC, result.matchType)
            assertEquals(0.85f, result.score)
            assertTrue(result.isHighConfidence)
        }

        @Test
        @DisplayName("keyword result score from match count")
        fun keywordResultScoreFromMatchCount() {
            val result = CodeSearchResult.keyword(createChunk(), 5)

            assertEquals(MatchType.KEYWORD, result.matchType)
            assertEquals(0.5f, result.score) // 5 / 10
        }

        @Test
        @DisplayName("symbol result has highest score")
        fun symbolResultHasHighestScore() {
            val result = CodeSearchResult.symbol(createChunk())

            assertEquals(MatchType.SYMBOL, result.matchType)
            assertEquals(1.0f, result.score)
        }

        @Test
        @DisplayName("weighted score applies match type weight")
        fun weightedScoreAppliesWeight() {
            val semantic = CodeSearchResult.semantic(createChunk(), 0.8f)
            val symbol = CodeSearchResult.symbol(createChunk())

            // Symbol should have higher weighted score due to 1.2x weight
            assertTrue(symbol.weightedScore > semantic.weightedScore)
        }

        @Test
        @DisplayName("high confidence threshold is 0.8")
        fun highConfidenceThresholdIs08() {
            val below = CodeSearchResult.semantic(createChunk(), 0.79f)
            assertFalse(below.isHighConfidence)

            val above = CodeSearchResult.semantic(createChunk(), 0.80f)
            assertTrue(above.isHighConfidence)
        }
    }

    // =========================================================================
    // Index Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Index Statistics")
    inner class IndexStatisticsTests {

        @Test
        @DisplayName("empty stats are correct")
        fun emptyStatsAreCorrect() {
            val stats = IndexStats.EMPTY

            assertEquals(0, stats.totalChunks)
            assertEquals(0, stats.totalSymbols)
            assertEquals(0, stats.chunksWithEmbeddings)
            assertEquals(0f, stats.embeddingCoverage)
            assertTrue(stats.isEmpty)
        }

        @Test
        @DisplayName("embedding coverage percentage")
        fun embeddingCoveragePercentage() {
            val stats = IndexStats(
                totalChunks = 200,
                totalSymbols = 100,
                chunksWithEmbeddings = 150,
                fileCount = 20,
                languageBreakdown = mapOf("kotlin" to 150, "java" to 50),
                lastIndexed = null
            )

            assertEquals(0.75f, stats.embeddingCoverage)
            assertFalse(stats.isEmpty)
        }

        @Test
        @DisplayName("language breakdown tracks languages")
        fun languageBreakdownTracksLanguages() {
            val stats = IndexStats(
                totalChunks = 100,
                totalSymbols = 50,
                chunksWithEmbeddings = 80,
                fileCount = 10,
                languageBreakdown = mapOf(
                    "kotlin" to 60,
                    "java" to 30,
                    "python" to 10
                ),
                lastIndexed = null
            )

            assertEquals(60, stats.languageBreakdown["kotlin"])
            assertEquals(30, stats.languageBreakdown["java"])
            assertEquals(3, stats.languageBreakdown.size)
        }
    }

    // =========================================================================
    // Embedding Utilities Tests
    // =========================================================================

    @Nested
    @DisplayName("Embedding Utilities")
    inner class EmbeddingUtilitiesTests {

        @Test
        @DisplayName("cosine similarity range is -1 to 1")
        fun cosineSimilarityRangeIsCorrect() {
            val v1 = listOf(0.5f, 0.5f, 0.5f)
            val v2 = listOf(0.3f, 0.7f, 0.2f)

            val similarity = EmbeddingUtils.cosineSimilarity(v1, v2)
            assertTrue(similarity >= -1f)
            assertTrue(similarity <= 1f)
        }

        @Test
        @DisplayName("euclidean distance is non-negative")
        fun euclideanDistanceIsNonNegative() {
            val v1 = listOf(1f, 2f, 3f)
            val v2 = listOf(4f, 5f, 6f)

            val distance = EmbeddingUtils.euclideanDistance(v1, v2)
            assertTrue(distance >= 0f)
        }

        @Test
        @DisplayName("normalize preserves direction")
        fun normalizePreservesDirection() {
            val v = listOf(3f, 4f)
            val normalized = EmbeddingUtils.normalize(v)

            // Direction should be preserved (ratio of components)
            val originalRatio = v[0] / v[1]
            val normalizedRatio = normalized[0] / normalized[1]
            assertEquals(originalRatio, normalizedRatio, 0.0001f)
        }

        @Test
        @DisplayName("average of single vector is itself")
        fun averageOfSingleVectorIsItself() {
            val v = listOf(1f, 2f, 3f)
            val avg = EmbeddingUtils.average(listOf(v))

            assertEquals(v[0], avg[0], 0.0001f)
            assertEquals(v[1], avg[1], 0.0001f)
            assertEquals(v[2], avg[2], 0.0001f)
        }

        @Test
        @DisplayName("average of empty list is empty")
        fun averageOfEmptyListIsEmpty() {
            val avg = EmbeddingUtils.average(emptyList())
            assertTrue(avg.isEmpty())
        }
    }

    // =========================================================================
    // Index Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Index Events")
    inner class IndexEventTests {

        @Test
        @DisplayName("events have timestamps")
        fun eventsHaveTimestamps() {
            val started = IndexEvent.IndexStarted(10)
            assertNotNull(started.timestamp)

            val fileIndexed = IndexEvent.FileIndexed("/test.kt", 5, 3)
            assertNotNull(fileIndexed.timestamp)

            val completed = IndexEvent.IndexCompleted(IndexStats.EMPTY, 1000)
            assertNotNull(completed.timestamp)

            val error = IndexEvent.IndexError("/test.kt", "Parse error")
            assertNotNull(error.timestamp)
        }

        @Test
        @DisplayName("completed event has duration")
        fun completedEventHasDuration() {
            val event = IndexEvent.IndexCompleted(IndexStats.EMPTY, 5000)
            assertEquals(5000, event.durationMs)
        }

        @Test
        @DisplayName("error event can have null file path")
        fun errorEventCanHaveNullFilePath() {
            val event = IndexEvent.IndexError(null, "General error")
            assertNull(event.filePath)
            assertEquals("General error", event.error)
        }
    }
}
