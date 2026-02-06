package com.sidekick.agent.index

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Code Index models.
 */
@DisplayName("Code Index Models Tests")
class CodeIndexModelsTest {

    // =========================================================================
    // CodeChunk Tests
    // =========================================================================

    @Nested
    @DisplayName("CodeChunk")
    inner class CodeChunkTests {

        @Test
        @DisplayName("chunk has unique ID")
        fun chunkHasUniqueId() {
            val chunk1 = CodeChunk(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                content = "code",
                type = ChunkType.FILE
            )
            val chunk2 = CodeChunk(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                content = "code",
                type = ChunkType.FILE
            )
            assertNotEquals(chunk1.id, chunk2.id)
        }

        @Test
        @DisplayName("lineCount calculates correctly")
        fun lineCountCalculatesCorrectly() {
            val chunk = CodeChunk(
                filePath = "/test.kt",
                startLine = 5,
                endLine = 15,
                content = "code",
                type = ChunkType.BLOCK
            )
            assertEquals(11, chunk.lineCount)
        }

        @Test
        @DisplayName("hasEmbedding detects embedding")
        fun hasEmbeddingDetectsEmbedding() {
            val without = CodeChunk(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                content = "code",
                type = ChunkType.FILE
            )
            assertFalse(without.hasEmbedding)

            val with = without.withEmbedding(listOf(0.1f, 0.2f, 0.3f))
            assertTrue(with.hasEmbedding)
        }

        @Test
        @DisplayName("isSymbol identifies symbol chunks")
        fun isSymbolIdentifiesSymbolChunks() {
            val method = CodeChunk(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                content = "fun test() {}",
                type = ChunkType.METHOD,
                symbolName = "test"
            )
            assertTrue(method.isSymbol)

            val block = CodeChunk(
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                content = "code",
                type = ChunkType.BLOCK
            )
            assertFalse(block.isSymbol)
        }

        @Test
        @DisplayName("file factory creates file chunk")
        fun fileFactoryCreatesFileChunk() {
            val chunk = CodeChunk.file(
                filePath = "/test.kt",
                content = "Line 1\nLine 2\nLine 3",
                language = "kotlin"
            )

            assertEquals(ChunkType.FILE, chunk.type)
            assertEquals(1, chunk.startLine)
            assertEquals(3, chunk.endLine)
            assertEquals("kotlin", chunk.language)
        }

        @Test
        @DisplayName("method factory creates method chunk")
        fun methodFactoryCreatesMethodChunk() {
            val chunk = CodeChunk.method(
                filePath = "/test.kt",
                startLine = 10,
                endLine = 20,
                content = "fun test() {}",
                methodName = "test"
            )

            assertEquals(ChunkType.METHOD, chunk.type)
            assertEquals("test", chunk.symbolName)
            assertTrue(chunk.isSymbol)
        }

        @Test
        @DisplayName("lineRange returns correct range")
        fun lineRangeReturnsCorrectRange() {
            val chunk = CodeChunk(
                filePath = "/test.kt",
                startLine = 5,
                endLine = 15,
                content = "code",
                type = ChunkType.BLOCK
            )

            assertEquals(5..15, chunk.lineRange)
            assertTrue(10 in chunk.lineRange)
            assertFalse(20 in chunk.lineRange)
        }
    }

    // =========================================================================
    // ChunkType Tests
    // =========================================================================

    @Nested
    @DisplayName("ChunkType")
    inner class ChunkTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            ChunkType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("SYMBOL_TYPES contains correct types")
        fun symbolTypesContainsCorrect() {
            assertTrue(ChunkType.CLASS in ChunkType.SYMBOL_TYPES)
            assertTrue(ChunkType.METHOD in ChunkType.SYMBOL_TYPES)
            assertTrue(ChunkType.FUNCTION in ChunkType.SYMBOL_TYPES)
            assertFalse(ChunkType.BLOCK in ChunkType.SYMBOL_TYPES)
            assertFalse(ChunkType.FILE in ChunkType.SYMBOL_TYPES)
        }

        @Test
        @DisplayName("BLOCK_TYPES contains correct types")
        fun blockTypesContainsCorrect() {
            assertTrue(ChunkType.FILE in ChunkType.BLOCK_TYPES)
            assertTrue(ChunkType.CLASS in ChunkType.BLOCK_TYPES)
            assertTrue(ChunkType.METHOD in ChunkType.BLOCK_TYPES)
        }
    }

    // =========================================================================
    // SymbolInfo Tests
    // =========================================================================

    @Nested
    @DisplayName("SymbolInfo")
    inner class SymbolInfoTests {

        @Test
        @DisplayName("fullName with parent")
        fun fullNameWithParent() {
            val method = SymbolInfo.method(
                name = "doSomething",
                parentClass = "MyClass",
                filePath = "/test.kt",
                startLine = 10,
                endLine = 20,
                signature = "fun doSomething(): Unit"
            )

            assertEquals("MyClass.doSomething", method.fullName)
        }

        @Test
        @DisplayName("fullName without parent")
        fun fullNameWithoutParent() {
            val clazz = SymbolInfo.clazz(
                name = "MyClass",
                filePath = "/test.kt",
                startLine = 1,
                endLine = 50
            )

            assertEquals("MyClass", clazz.fullName)
        }

        @Test
        @DisplayName("isType identifies type kinds")
        fun isTypeIdentifiesTypeKinds() {
            val clazz = SymbolInfo(
                name = "MyClass",
                kind = SymbolKind.CLASS,
                filePath = "/test.kt",
                startLine = 1,
                endLine = 50
            )
            assertTrue(clazz.isType)

            val method = SymbolInfo(
                name = "doSomething",
                kind = SymbolKind.METHOD,
                filePath = "/test.kt",
                startLine = 10,
                endLine = 20
            )
            assertFalse(method.isType)
        }

        @Test
        @DisplayName("isCallable identifies callable kinds")
        fun isCallableIdentifiesCallableKinds() {
            val method = SymbolInfo(
                name = "doSomething",
                kind = SymbolKind.METHOD,
                filePath = "/test.kt",
                startLine = 10,
                endLine = 20
            )
            assertTrue(method.isCallable)

            val property = SymbolInfo(
                name = "value",
                kind = SymbolKind.PROPERTY,
                filePath = "/test.kt",
                startLine = 5,
                endLine = 5
            )
            assertFalse(property.isCallable)
        }

        @Test
        @DisplayName("referenceCount returns correct count")
        fun referenceCountReturnsCorrectCount() {
            val symbol = SymbolInfo(
                name = "test",
                kind = SymbolKind.METHOD,
                filePath = "/test.kt",
                startLine = 1,
                endLine = 10,
                references = listOf(
                    SymbolReference.call("/main.kt", 5),
                    SymbolReference.call("/util.kt", 10),
                    SymbolReference.call("/helper.kt", 15)
                )
            )

            assertEquals(3, symbol.referenceCount)
        }
    }

    // =========================================================================
    // SymbolKind Tests
    // =========================================================================

    @Nested
    @DisplayName("SymbolKind")
    inner class SymbolKindTests {

        @Test
        @DisplayName("TYPE_KINDS contains correct kinds")
        fun typeKindsContainsCorrect() {
            assertTrue(SymbolKind.CLASS in SymbolKind.TYPE_KINDS)
            assertTrue(SymbolKind.INTERFACE in SymbolKind.TYPE_KINDS)
            assertTrue(SymbolKind.ENUM in SymbolKind.TYPE_KINDS)
            assertFalse(SymbolKind.METHOD in SymbolKind.TYPE_KINDS)
        }

        @Test
        @DisplayName("CALLABLE_KINDS contains correct kinds")
        fun callableKindsContainsCorrect() {
            assertTrue(SymbolKind.METHOD in SymbolKind.CALLABLE_KINDS)
            assertTrue(SymbolKind.FUNCTION in SymbolKind.CALLABLE_KINDS)
            assertTrue(SymbolKind.CONSTRUCTOR in SymbolKind.CALLABLE_KINDS)
            assertFalse(SymbolKind.PROPERTY in SymbolKind.CALLABLE_KINDS)
        }
    }

    // =========================================================================
    // SymbolReference Tests
    // =========================================================================

    @Nested
    @DisplayName("SymbolReference")
    inner class SymbolReferenceTests {

        @Test
        @DisplayName("factory methods create correct kinds")
        fun factoryMethodsCreateCorrectKinds() {
            val read = SymbolReference.read("/test.kt", 10)
            assertEquals(ReferenceKind.READ, read.kind)

            val write = SymbolReference.write("/test.kt", 15)
            assertEquals(ReferenceKind.WRITE, write.kind)

            val call = SymbolReference.call("/test.kt", 20)
            assertEquals(ReferenceKind.CALL, call.kind)
        }
    }

    // =========================================================================
    // CodeSearchResult Tests
    // =========================================================================

    @Nested
    @DisplayName("CodeSearchResult")
    inner class CodeSearchResultTests {

        private fun createChunk() = CodeChunk(
            filePath = "/test.kt",
            startLine = 1,
            endLine = 10,
            content = "code",
            type = ChunkType.FILE
        )

        @Test
        @DisplayName("isHighConfidence for high scores")
        fun isHighConfidenceForHighScores() {
            val high = CodeSearchResult.semantic(createChunk(), 0.9f)
            assertTrue(high.isHighConfidence)

            val low = CodeSearchResult.semantic(createChunk(), 0.5f)
            assertFalse(low.isHighConfidence)
        }

        @Test
        @DisplayName("weightedScore applies match type weight")
        fun weightedScoreAppliesWeight() {
            val semantic = CodeSearchResult.semantic(createChunk(), 0.8f)
            assertEquals(0.8f * MatchType.SEMANTIC.weight, semantic.weightedScore)

            val symbol = CodeSearchResult.symbol(createChunk())
            assertEquals(1.0f * MatchType.SYMBOL.weight, symbol.weightedScore)
        }

        @Test
        @DisplayName("factory methods create correct match types")
        fun factoryMethodsCreateCorrectMatchTypes() {
            val semantic = CodeSearchResult.semantic(createChunk(), 0.8f)
            assertEquals(MatchType.SEMANTIC, semantic.matchType)

            val keyword = CodeSearchResult.keyword(createChunk(), 5)
            assertEquals(MatchType.KEYWORD, keyword.matchType)

            val symbol = CodeSearchResult.symbol(createChunk())
            assertEquals(MatchType.SYMBOL, symbol.matchType)
        }
    }

    // =========================================================================
    // MatchType Tests
    // =========================================================================

    @Nested
    @DisplayName("MatchType")
    inner class MatchTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            MatchType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("weights are positive")
        fun weightsArePositive() {
            MatchType.entries.forEach { type ->
                assertTrue(type.weight > 0)
            }
        }

        @Test
        @DisplayName("SYMBOL weight is highest")
        fun symbolWeightIsHighest() {
            val maxWeight = MatchType.entries.maxOf { it.weight }
            assertEquals(MatchType.SYMBOL.weight, maxWeight)
        }
    }

    // =========================================================================
    // SearchQuery Tests
    // =========================================================================

    @Nested
    @DisplayName("SearchQuery")
    inner class SearchQueryTests {

        @Test
        @DisplayName("default query enables semantic search")
        fun defaultQueryEnablesSemanticSearch() {
            val query = SearchQuery(query = "find something")
            assertTrue(query.useSemanticSearch)
            assertTrue(query.includeEmbeddings)
        }

        @Test
        @DisplayName("keyword factory disables embeddings")
        fun keywordFactoryDisablesEmbeddings() {
            val query = SearchQuery.keyword("find")
            assertFalse(query.includeEmbeddings)
            assertFalse(query.useSemanticSearch)
            assertTrue(MatchType.KEYWORD in query.matchTypes)
        }

        @Test
        @DisplayName("semantic factory enables embeddings only")
        fun semanticFactoryEnablesEmbeddingsOnly() {
            val query = SearchQuery.semantic("find")
            assertTrue(query.includeEmbeddings)
            assertTrue(query.useSemanticSearch)
            assertTrue(MatchType.SEMANTIC in query.matchTypes)
        }

        @Test
        @DisplayName("hasTypeFilter detects type filter")
        fun hasTypeFilterDetectsTypeFilter() {
            val without = SearchQuery(query = "find")
            assertFalse(without.hasTypeFilter)

            val with = SearchQuery(query = "find", types = setOf(ChunkType.METHOD))
            assertTrue(with.hasTypeFilter)
        }

        @Test
        @DisplayName("hasFileFilter detects file filter")
        fun hasFileFilterDetectsFileFilter() {
            val without = SearchQuery(query = "find")
            assertFalse(without.hasFileFilter)

            val with = SearchQuery(query = "find", files = listOf("*.kt"))
            assertTrue(with.hasFileFilter)
        }
    }

    // =========================================================================
    // IndexStats Tests
    // =========================================================================

    @Nested
    @DisplayName("IndexStats")
    inner class IndexStatsTests {

        @Test
        @DisplayName("embeddingCoverage calculates correctly")
        fun embeddingCoverageCalculatesCorrectly() {
            val stats = IndexStats(
                totalChunks = 100,
                totalSymbols = 50,
                chunksWithEmbeddings = 75,
                fileCount = 10,
                languageBreakdown = emptyMap(),
                lastIndexed = Instant.now()
            )

            assertEquals(0.75f, stats.embeddingCoverage)
        }

        @Test
        @DisplayName("embeddingCoverage is zero for empty index")
        fun embeddingCoverageIsZeroForEmptyIndex() {
            val stats = IndexStats.EMPTY
            assertEquals(0f, stats.embeddingCoverage)
        }

        @Test
        @DisplayName("isEmpty detects empty index")
        fun isEmptyDetectsEmptyIndex() {
            assertTrue(IndexStats.EMPTY.isEmpty)

            val nonEmpty = IndexStats.EMPTY.copy(totalChunks = 1)
            assertFalse(nonEmpty.isEmpty)
        }
    }

    // =========================================================================
    // EmbeddingUtils Tests
    // =========================================================================

    @Nested
    @DisplayName("EmbeddingUtils")
    inner class EmbeddingUtilsTests {

        @Test
        @DisplayName("cosineSimilarity of identical vectors is 1")
        fun cosineSimilarityOfIdenticalVectorsIsOne() {
            val v = listOf(1f, 2f, 3f)
            assertEquals(1f, EmbeddingUtils.cosineSimilarity(v, v), 0.0001f)
        }

        @Test
        @DisplayName("cosineSimilarity of orthogonal vectors is 0")
        fun cosineSimilarityOfOrthogonalVectorsIsZero() {
            val v1 = listOf(1f, 0f)
            val v2 = listOf(0f, 1f)
            assertEquals(0f, EmbeddingUtils.cosineSimilarity(v1, v2), 0.0001f)
        }

        @Test
        @DisplayName("cosineSimilarity of opposite vectors is -1")
        fun cosineSimilarityOfOppositeVectorsIsNegativeOne() {
            val v1 = listOf(1f, 0f)
            val v2 = listOf(-1f, 0f)
            assertEquals(-1f, EmbeddingUtils.cosineSimilarity(v1, v2), 0.0001f)
        }

        @Test
        @DisplayName("cosineSimilarity of empty vectors is 0")
        fun cosineSimilarityOfEmptyVectorsIsZero() {
            assertEquals(0f, EmbeddingUtils.cosineSimilarity(emptyList(), emptyList()))
        }

        @Test
        @DisplayName("euclideanDistance of identical vectors is 0")
        fun euclideanDistanceOfIdenticalVectorsIsZero() {
            val v = listOf(1f, 2f, 3f)
            assertEquals(0f, EmbeddingUtils.euclideanDistance(v, v), 0.0001f)
        }

        @Test
        @DisplayName("euclideanDistance calculates correctly")
        fun euclideanDistanceCalculatesCorrectly() {
            val v1 = listOf(0f, 0f)
            val v2 = listOf(3f, 4f)
            assertEquals(5f, EmbeddingUtils.euclideanDistance(v1, v2), 0.0001f)
        }

        @Test
        @DisplayName("normalize creates unit vector")
        fun normalizeCreatesUnitVector() {
            val v = listOf(3f, 4f)
            val normalized = EmbeddingUtils.normalize(v)

            val magnitude = kotlin.math.sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
            assertEquals(1f, magnitude, 0.0001f)
        }

        @Test
        @DisplayName("average computes element-wise average")
        fun averageComputesElementWiseAverage() {
            val embeddings = listOf(
                listOf(1f, 2f, 3f),
                listOf(3f, 4f, 5f),
                listOf(2f, 3f, 4f)
            )
            val avg = EmbeddingUtils.average(embeddings)

            assertEquals(2f, avg[0], 0.0001f)
            assertEquals(3f, avg[1], 0.0001f)
            assertEquals(4f, avg[2], 0.0001f)
        }
    }

    // =========================================================================
    // IndexEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("IndexEvent")
    inner class IndexEventTests {

        @Test
        @DisplayName("IndexStarted has file count")
        fun indexStartedHasFileCount() {
            val event = IndexEvent.IndexStarted(100)
            assertEquals(100, event.fileCount)
            assertNotNull(event.timestamp)
        }

        @Test
        @DisplayName("FileIndexed has counts")
        fun fileIndexedHasCounts() {
            val event = IndexEvent.FileIndexed("/test.kt", 10, 5)
            assertEquals("/test.kt", event.filePath)
            assertEquals(10, event.chunkCount)
            assertEquals(5, event.symbolCount)
        }

        @Test
        @DisplayName("IndexCompleted has duration")
        fun indexCompletedHasDuration() {
            val event = IndexEvent.IndexCompleted(IndexStats.EMPTY, 1000)
            assertEquals(1000, event.durationMs)
        }

        @Test
        @DisplayName("IndexError has error message")
        fun indexErrorHasErrorMessage() {
            val event = IndexEvent.IndexError("/test.kt", "Failed to parse")
            assertEquals("/test.kt", event.filePath)
            assertEquals("Failed to parse", event.error)
        }
    }
}
