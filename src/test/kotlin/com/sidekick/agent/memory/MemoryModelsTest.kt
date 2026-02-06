package com.sidekick.agent.memory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Memory Models.
 */
@DisplayName("Memory Models Tests")
class MemoryModelsTest {

    // =========================================================================
    // MemoryEntry Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryEntry")
    inner class MemoryEntryTests {

        @Test
        @DisplayName("has unique ID")
        fun hasUniqueId() {
            val entry1 = MemoryEntry(sessionId = "s1", type = MemoryType.FACT, content = "test")
            val entry2 = MemoryEntry(sessionId = "s1", type = MemoryType.FACT, content = "test")
            assertNotEquals(entry1.id, entry2.id)
        }

        @Test
        @DisplayName("hasEmbedding detects embedding")
        fun hasEmbeddingDetectsEmbedding() {
            val without = MemoryEntry(sessionId = "s1", type = MemoryType.FACT, content = "test")
            assertFalse(without.hasEmbedding)

            val with = without.withEmbedding(listOf(0.1f, 0.2f, 0.3f))
            assertTrue(with.hasEmbedding)
        }

        @Test
        @DisplayName("isExpired checks expiration")
        fun isExpiredChecksExpiration() {
            val now = Instant.now()
            val expired = MemoryEntry(
                sessionId = "s1",
                type = MemoryType.FACT,
                content = "test",
                expiresAt = now.minusSeconds(60)
            )
            assertTrue(expired.isExpired(now))

            val notExpired = MemoryEntry(
                sessionId = "s1",
                type = MemoryType.FACT,
                content = "test",
                expiresAt = now.plusSeconds(60)
            )
            assertFalse(notExpired.isExpired(now))
        }

        @Test
        @DisplayName("ageMs calculates correctly")
        fun ageMsCalculatesCorrectly() {
            val past = Instant.now().minusSeconds(60)
            val entry = MemoryEntry(
                sessionId = "s1",
                type = MemoryType.FACT,
                content = "test",
                timestamp = past
            )
            val age = entry.ageMs()
            assertTrue(age >= 60000)
        }

        @Test
        @DisplayName("withImportance clamps value")
        fun withImportanceClampsValue() {
            val entry = MemoryEntry(sessionId = "s1", type = MemoryType.FACT, content = "test")
            
            val tooHigh = entry.withImportance(1.5f)
            assertEquals(1.0f, tooHigh.importance)

            val tooLow = entry.withImportance(-0.5f)
            assertEquals(0.0f, tooLow.importance)
        }

        @Test
        @DisplayName("factory methods create correct types")
        fun factoryMethodsCreateCorrectTypes() {
            val user = MemoryEntry.userMessage("s1", "Hello")
            assertEquals(MemoryType.USER_MESSAGE, user.type)

            val assistant = MemoryEntry.assistantMessage("s1", "Hi there")
            assertEquals(MemoryType.ASSISTANT_MESSAGE, assistant.type)

            val tool = MemoryEntry.toolResult("s1", "read_file", "content")
            assertEquals(MemoryType.TOOL_RESULT, tool.type)
            assertEquals("read_file", tool.getMeta("tool"))

            val fact = MemoryEntry.fact("s1", "The sky is blue")
            assertEquals(MemoryType.FACT, fact.type)

            val pref = MemoryEntry.preference("s1", "Use dark mode")
            assertEquals(MemoryType.PREFERENCE, pref.type)

            val code = MemoryEntry.codeContext("s1", "/main.kt", "main function")
            assertEquals(MemoryType.CODE_CONTEXT, code.type)
            assertEquals("/main.kt", code.getMeta("file"))
        }

        @Test
        @DisplayName("getMeta returns metadata value")
        fun getMetaReturnsMetadataValue() {
            val entry = MemoryEntry(
                sessionId = "s1",
                type = MemoryType.FACT,
                content = "test",
                metadata = mapOf("key" to "value")
            )
            assertEquals("value", entry.getMeta("key"))
            assertNull(entry.getMeta("missing"))
        }
    }

    // =========================================================================
    // MemoryType Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryType")
    inner class MemoryTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            MemoryType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("all types have default importance")
        fun allTypesHaveDefaultImportance() {
            MemoryType.entries.forEach { type ->
                assertTrue(type.defaultImportance in 0f..1f)
            }
        }

        @Test
        @DisplayName("PERSISTENT_TYPES contains correct types")
        fun persistentTypesContainsCorrect() {
            assertTrue(MemoryType.FACT in MemoryType.PERSISTENT_TYPES)
            assertTrue(MemoryType.PREFERENCE in MemoryType.PERSISTENT_TYPES)
            assertTrue(MemoryType.DECISION in MemoryType.PERSISTENT_TYPES)
            assertFalse(MemoryType.USER_MESSAGE in MemoryType.PERSISTENT_TYPES)
        }

        @Test
        @DisplayName("CONVERSATIONAL_TYPES contains correct types")
        fun conversationalTypesContainsCorrect() {
            assertTrue(MemoryType.USER_MESSAGE in MemoryType.CONVERSATIONAL_TYPES)
            assertTrue(MemoryType.ASSISTANT_MESSAGE in MemoryType.CONVERSATIONAL_TYPES)
            assertTrue(MemoryType.TOOL_RESULT in MemoryType.CONVERSATIONAL_TYPES)
        }
    }

    // =========================================================================
    // SessionContext Tests
    // =========================================================================

    @Nested
    @DisplayName("SessionContext")
    inner class SessionContextTests {

        @Test
        @DisplayName("create generates unique session")
        fun createGeneratesUniqueSession() {
            val s1 = SessionContext.create("/project1")
            val s2 = SessionContext.create("/project1")
            assertNotEquals(s1.sessionId, s2.sessionId)
        }

        @Test
        @DisplayName("isActive checks end time")
        fun isActiveChecksEndTime() {
            val active = SessionContext.create("/project")
            assertTrue(active.isActive)

            val ended = active.end()
            assertFalse(ended.isActive)
        }

        @Test
        @DisplayName("addFile tracks files")
        fun addFileTracksFiles() {
            val session = SessionContext.create("/project")
            session.addFile("/main.kt")
            session.addFile("/util.kt")
            session.addFile("/main.kt") // Duplicate

            assertEquals(2, session.activeFiles.size)
            assertTrue("/main.kt" in session.activeFiles)
        }

        @Test
        @DisplayName("addSymbol tracks and limits symbols")
        fun addSymbolTracksAndLimitsSymbols() {
            val session = SessionContext.create("/project")
            
            repeat(10) { i ->
                session.addSymbol("Symbol$i", maxSymbols = 5)
            }

            assertEquals(5, session.recentSymbols.size)
            assertTrue("Symbol9" in session.recentSymbols)
            assertFalse("Symbol0" in session.recentSymbols)
        }

        @Test
        @DisplayName("addFact avoids duplicates")
        fun addFactAvoidsDuplicates() {
            val session = SessionContext.create("/project")
            session.addFact("Fact 1")
            session.addFact("Fact 2")
            session.addFact("Fact 1") // Duplicate

            assertEquals(2, session.facts.size)
        }

        @Test
        @DisplayName("durationMs calculates correctly")
        fun durationMsCalculatesCorrectly() {
            val start = Instant.now().minusSeconds(60)
            val session = SessionContext(
                projectPath = "/project",
                startTime = start
            )
            val duration = session.durationMs()
            assertTrue(duration >= 60000)
        }
    }

    // =========================================================================
    // MemoryConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryConfig")
    inner class MemoryConfigTests {

        @Test
        @DisplayName("shouldPersist checks threshold")
        fun shouldPersistChecksThreshold() {
            val config = MemoryConfig(importanceThreshold = 0.5f)

            assertTrue(config.shouldPersist(0.6f))
            assertTrue(config.shouldPersist(0.5f))
            assertFalse(config.shouldPersist(0.4f))
        }

        @Test
        @DisplayName("retentionCutoff calculates correctly")
        fun retentionCutoffCalculatesCorrectly() {
            val config = MemoryConfig(retentionDays = 7)
            val now = Instant.now()
            val cutoff = config.retentionCutoff(now)

            val sevenDaysAgo = now.minusSeconds(7 * 24 * 60 * 60)
            assertEquals(sevenDaysAgo.epochSecond, cutoff.epochSecond)
        }

        @Test
        @DisplayName("presets have expected values")
        fun presetsHaveExpectedValues() {
            assertTrue(MemoryConfig.DEFAULT.enabled)
            assertTrue(MemoryConfig.DEFAULT.embedMemories)

            assertFalse(MemoryConfig.MINIMAL.embedMemories)
            assertEquals(5, MemoryConfig.MINIMAL.maxShortTermMemory)

            assertEquals(5000, MemoryConfig.MAXIMUM.maxLongTermMemory)
            assertEquals(365, MemoryConfig.MAXIMUM.retentionDays)
        }
    }

    // =========================================================================
    // MemoryQuery Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryQuery")
    inner class MemoryQueryTests {

        @Test
        @DisplayName("hasTypeFilter detects filter")
        fun hasTypeFilterDetectsFilter() {
            val without = MemoryQuery(query = "test")
            assertFalse(without.hasTypeFilter)

            val with = MemoryQuery(query = "test", types = setOf(MemoryType.FACT))
            assertTrue(with.hasTypeFilter)
        }

        @Test
        @DisplayName("recent factory creates non-semantic query")
        fun recentFactoryCreatesNonSemanticQuery() {
            val query = MemoryQuery.recent(20)
            assertFalse(query.useSemantic)
            assertEquals(20, query.limit)
        }

        @Test
        @DisplayName("semantic factory creates semantic query")
        fun semanticFactoryCreatesSemanticQuery() {
            val query = MemoryQuery.semantic("find something", 5)
            assertTrue(query.useSemantic)
            assertEquals("find something", query.query)
        }

        @Test
        @DisplayName("forSession filters by session")
        fun forSessionFiltersBySession() {
            val query = MemoryQuery.forSession("session-123")
            assertTrue(query.hasSessionFilter)
            assertEquals("session-123", query.sessionId)
        }

        @Test
        @DisplayName("persistent queries facts and preferences")
        fun persistentQueriesFacts() {
            val query = MemoryQuery.persistent()
            assertTrue(query.hasTypeFilter)
            assertTrue(MemoryType.FACT in query.types!!)
        }
    }

    // =========================================================================
    // MemoryStats Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryStats")
    inner class MemoryStatsTests {

        @Test
        @DisplayName("embeddingCoverage calculates correctly")
        fun embeddingCoverageCalculatesCorrectly() {
            val stats = MemoryStats(
                totalMemories = 100,
                memoriesWithEmbeddings = 75,
                sessionCount = 5,
                oldestMemory = null,
                newestMemory = null,
                typeBreakdown = emptyMap(),
                averageImportance = 0.5f
            )
            assertEquals(0.75f, stats.embeddingCoverage)
        }

        @Test
        @DisplayName("isEmpty detects empty")
        fun isEmptyDetectsEmpty() {
            assertTrue(MemoryStats.EMPTY.isEmpty)

            val nonEmpty = MemoryStats.EMPTY.copy(totalMemories = 1)
            assertFalse(nonEmpty.isEmpty)
        }
    }

    // =========================================================================
    // MemoryEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryEvent")
    inner class MemoryEventTests {

        @Test
        @DisplayName("events have timestamps")
        fun eventsHaveTimestamps() {
            val added = MemoryEvent.MemoryAdded(
                MemoryEntry(sessionId = "s1", type = MemoryType.FACT, content = "test")
            )
            assertNotNull(added.timestamp)

            val recalled = MemoryEvent.MemoryRecalled("query", 5)
            assertNotNull(recalled.timestamp)
        }

        @Test
        @DisplayName("SessionEnded has duration")
        fun sessionEndedHasDuration() {
            val event = MemoryEvent.SessionEnded("s1", 60000, 10)
            assertEquals(60000, event.durationMs)
            assertEquals(10, event.memoryCount)
        }
    }

    // =========================================================================
    // MemoryUtils Tests
    // =========================================================================

    @Nested
    @DisplayName("MemoryUtils")
    inner class MemoryUtilsTests {

        @Test
        @DisplayName("cosineSimilarity of identical vectors is 1")
        fun cosineSimilarityOfIdenticalIsOne() {
            val v = listOf(1f, 2f, 3f)
            assertEquals(1f, MemoryUtils.cosineSimilarity(v, v), 0.0001f)
        }

        @Test
        @DisplayName("cosineSimilarity of orthogonal vectors is 0")
        fun cosineSimilarityOfOrthogonalIsZero() {
            val v1 = listOf(1f, 0f)
            val v2 = listOf(0f, 1f)
            assertEquals(0f, MemoryUtils.cosineSimilarity(v1, v2), 0.0001f)
        }

        @Test
        @DisplayName("decayFactor decreases over time")
        fun decayFactorDecreasesOverTime() {
            val halfLife = 1000L
            val new = MemoryUtils.decayFactor(0, halfLife)
            val mid = MemoryUtils.decayFactor(halfLife, halfLife)
            val old = MemoryUtils.decayFactor(halfLife * 2, halfLife)

            assertEquals(1f, new, 0.0001f)
            assertEquals(0.5f, mid, 0.01f)
            assertTrue(old < mid)
        }

        @Test
        @DisplayName("truncate limits length")
        fun truncateLimitsLength() {
            val short = "Hello"
            assertEquals(short, MemoryUtils.truncate(short, 100))

            val long = "A".repeat(100)
            val truncated = MemoryUtils.truncate(long, 20)
            assertEquals(20, truncated.length)
            assertTrue(truncated.endsWith("..."))
        }

        @Test
        @DisplayName("extractKeywords finds common words")
        fun extractKeywordsFindsCommonWords() {
            val text = "The quick brown fox jumps over the lazy brown dog"
            val keywords = MemoryUtils.extractKeywords(text, 5)

            assertTrue("brown" in keywords) // Appears twice
            assertTrue(keywords.size <= 5)
        }
    }
}
