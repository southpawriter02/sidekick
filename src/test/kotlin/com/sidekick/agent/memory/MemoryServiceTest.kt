package com.sidekick.agent.memory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Unit tests for Memory Service.
 */
@DisplayName("Memory Service Tests")
class MemoryServiceTest {

    private lateinit var service: MemoryService

    @BeforeEach
    fun setUp() {
        service = MemoryService.withoutEmbeddings("/test/project")
    }

    // =========================================================================
    // Session Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Session Management")
    inner class SessionManagementTests {

        @Test
        @DisplayName("startSession creates new session")
        fun startSessionCreatesNewSession() {
            val session = service.startSession()

            assertNotNull(session.sessionId)
            assertTrue(session.isActive)
            assertEquals("/test/project", session.projectPath)
        }

        @Test
        @DisplayName("getCurrentSession starts one if needed")
        fun getCurrentSessionStartsOneIfNeeded() {
            val session = service.getCurrentSession()
            assertNotNull(session)
            assertTrue(session.isActive)
        }

        @Test
        @DisplayName("getSession returns session by ID")
        fun getSessionReturnsSessionById() {
            val started = service.startSession()
            val retrieved = service.getSession(started.sessionId)

            assertNotNull(retrieved)
            assertEquals(started.sessionId, retrieved?.sessionId)
        }

        @Test
        @DisplayName("endSession ends current session")
        fun endSessionEndsCurrentSession() {
            service.startSession()
            val ended = service.endSession()

            assertNotNull(ended)
            assertFalse(ended!!.isActive)
        }

        @Test
        @DisplayName("trackFile adds to session")
        fun trackFileAddsToSession() {
            val session = service.startSession()
            service.trackFile("/main.kt")
            service.trackFile("/util.kt")

            assertTrue("/main.kt" in session.activeFiles)
            assertTrue("/util.kt" in session.activeFiles)
        }

        @Test
        @DisplayName("trackSymbol adds to session")
        fun trackSymbolAddsToSession() {
            val session = service.startSession()
            service.trackSymbol("MyClass")
            service.trackSymbol("doSomething")

            assertTrue("MyClass" in session.recentSymbols)
            assertTrue("doSomething" in session.recentSymbols)
        }
    }

    // =========================================================================
    // Memory Storage Tests
    // =========================================================================

    @Nested
    @DisplayName("Memory Storage")
    inner class MemoryStorageTests {

        @Test
        @DisplayName("remember stores memory")
        fun rememberStoresMemory() = runBlocking {
            service.startSession()
            val memory = service.remember(MemoryType.FACT, "Test fact")

            assertEquals(MemoryType.FACT, memory.type)
            assertEquals("Test fact", memory.content)
        }

        @Test
        @DisplayName("rememberUserMessage stores user message")
        fun rememberUserMessageStoresUserMessage() = runBlocking {
            service.startSession()
            val memory = service.rememberUserMessage("Hello")

            assertEquals(MemoryType.USER_MESSAGE, memory.type)
            assertEquals("Hello", memory.content)
        }

        @Test
        @DisplayName("rememberAssistantMessage stores assistant message")
        fun rememberAssistantMessageStoresAssistantMessage() = runBlocking {
            service.startSession()
            val memory = service.rememberAssistantMessage("Hi there")

            assertEquals(MemoryType.ASSISTANT_MESSAGE, memory.type)
        }

        @Test
        @DisplayName("rememberToolResult stores with metadata")
        fun rememberToolResultStoresWithMetadata() = runBlocking {
            service.startSession()
            val memory = service.rememberToolResult("read_file", "File content")

            assertEquals(MemoryType.TOOL_RESULT, memory.type)
            assertEquals("read_file", memory.getMeta("tool"))
        }

        @Test
        @DisplayName("rememberFact adds to session facts")
        fun rememberFactAddsToSessionFacts() = runBlocking {
            val session = service.startSession()
            service.rememberFact("The project uses Kotlin")

            assertTrue("The project uses Kotlin" in session.facts)
        }

        @Test
        @DisplayName("rememberPreference stores with high importance")
        fun rememberPreferenceStoresWithHighImportance() = runBlocking {
            service.startSession()
            val memory = service.rememberPreference("Use dark mode")

            assertEquals(MemoryType.PREFERENCE, memory.type)
            assertEquals(0.8f, memory.importance)
        }
    }

    // =========================================================================
    // Memory Recall Tests
    // =========================================================================

    @Nested
    @DisplayName("Memory Recall")
    inner class MemoryRecallTests {

        @Test
        @DisplayName("getRecentMemories returns last N")
        fun getRecentMemoriesReturnsLastN() = runBlocking {
            service.startSession()
            repeat(20) { i ->
                service.remember(MemoryType.FACT, "Fact $i")
            }

            val recent = service.getRecentMemories(5)
            assertEquals(5, recent.size)
            assertTrue(recent.last().content.contains("19"))
        }

        @Test
        @DisplayName("getSessionMemories returns current session only")
        fun getSessionMemoriesReturnsCurrentSessionOnly() = runBlocking {
            // First session
            service.startSession()
            service.remember(MemoryType.FACT, "Session 1 fact")
            service.endSession()

            // Second session
            service.startSession()
            service.remember(MemoryType.FACT, "Session 2 fact")

            val memories = service.getSessionMemories()
            assertEquals(1, memories.size)
            assertEquals("Session 2 fact", memories.first().content)
        }

        @Test
        @DisplayName("getPersistentMemories returns facts and preferences")
        fun getPersistentMemoriesReturnsFactsAndPreferences() = runBlocking {
            service.startSession()
            service.rememberFact("A fact")
            service.rememberPreference("A preference")
            service.rememberUserMessage("A message")

            val persistent = service.getPersistentMemories()
            assertEquals(2, persistent.size)
        }

        @Test
        @DisplayName("getMemory returns by ID")
        fun getMemoryReturnsById() = runBlocking {
            service.startSession()
            val memory = service.remember(MemoryType.FACT, "Test")

            val retrieved = service.getMemory(memory.id)
            assertNotNull(retrieved)
            assertEquals(memory.id, retrieved?.id)
        }

        @Test
        @DisplayName("recall with query filters results")
        fun recallWithQueryFiltersResults() = runBlocking {
            service.startSession()
            service.rememberFact("Kotlin is a programming language")
            service.rememberFact("Java is also a programming language")
            service.rememberUserMessage("Hello world")

            val query = MemoryQuery(
                query = "",
                types = setOf(MemoryType.FACT),
                useSemantic = false
            )
            val results = service.recall(query)

            assertEquals(2, results.size)
            assertTrue(results.all { it.type == MemoryType.FACT })
        }

        @Test
        @DisplayName("recall filters by importance")
        fun recallFiltersByImportance() = runBlocking {
            service.startSession()
            service.remember(MemoryType.FACT, "Low importance", importance = 0.2f)
            service.remember(MemoryType.FACT, "High importance", importance = 0.8f)

            val query = MemoryQuery(query = "", minImportance = 0.5f, useSemantic = false)
            val results = service.recall(query)

            assertEquals(1, results.size)
            assertEquals("High importance", results.first().content)
        }
    }

    // =========================================================================
    // Memory Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Memory Management")
    inner class MemoryManagementTests {

        @Test
        @DisplayName("clearMemories removes all memories")
        fun clearMemoriesRemovesAll() = runBlocking {
            service.startSession()
            repeat(10) { i ->
                service.remember(MemoryType.FACT, "Fact $i")
            }

            service.clearMemories()
            assertEquals(0, service.getRecentMemories(100).size)
        }

        @Test
        @DisplayName("clearSessionMemories removes session memories only")
        fun clearSessionMemoriesRemovesSessionOnly() = runBlocking {
            // First session
            val session1 = service.startSession()
            service.remember(MemoryType.FACT, "Session 1")
            service.endSession()

            // Second session
            service.startSession()
            service.remember(MemoryType.FACT, "Session 2")

            service.clearSessionMemories(session1.sessionId)

            val all = service.getRecentMemories(100)
            assertEquals(1, all.size)
            assertEquals("Session 2", all.first().content)
        }

        @Test
        @DisplayName("updateImportance changes importance")
        fun updateImportanceChangesImportance() = runBlocking {
            service.startSession()
            val memory = service.remember(MemoryType.FACT, "Test", importance = 0.5f)

            val updated = service.updateImportance(memory.id, 0.9f)
            assertTrue(updated)

            val retrieved = service.getMemory(memory.id)
            assertEquals(0.9f, retrieved?.importance)
        }
    }

    // =========================================================================
    // Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Configuration")
    inner class ConfigurationTests {

        @Test
        @DisplayName("getConfig returns current config")
        fun getConfigReturnsCurrentConfig() {
            val config = service.getConfig()
            assertNotNull(config)
            assertTrue(config.enabled)
        }

        @Test
        @DisplayName("updateConfig changes config")
        fun updateConfigChangesConfig() {
            val newConfig = MemoryConfig(
                enabled = false,
                maxShortTermMemory = 5
            )
            service.updateConfig(newConfig)

            assertEquals(5, service.getConfig().maxShortTermMemory)
        }

        @Test
        @DisplayName("disabled service still returns entries")
        fun disabledServiceStillReturnsEntries() = runBlocking {
            service.updateConfig(MemoryConfig(enabled = false))
            service.startSession()

            val memory = service.remember(MemoryType.FACT, "Test")
            assertNotNull(memory)
        }
    }

    // =========================================================================
    // Statistics Tests
    // =========================================================================

    @Nested
    @DisplayName("Statistics")
    inner class StatisticsTests {

        @Test
        @DisplayName("getStats returns correct counts")
        fun getStatsReturnsCorrectCounts() = runBlocking {
            service.startSession()
            service.rememberFact("Fact 1")
            service.rememberFact("Fact 2")
            service.rememberUserMessage("Message")

            val stats = service.getStats()
            assertEquals(3, stats.totalMemories)
            assertEquals(2, stats.typeBreakdown[MemoryType.FACT])
            assertEquals(1, stats.typeBreakdown[MemoryType.USER_MESSAGE])
        }

        @Test
        @DisplayName("empty stats for empty service")
        fun emptyStatsForEmptyService() {
            val stats = service.getStats()
            assertTrue(stats.isEmpty)
        }
    }

    // =========================================================================
    // Events Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits session started event")
        fun emitsSessionStartedEvent() {
            var received: MemoryEvent? = null
            service.addListener { event -> received = event }

            service.startSession()

            assertTrue(received is MemoryEvent.SessionStarted)
        }

        @Test
        @DisplayName("emits memory added event")
        fun emitsMemoryAddedEvent() = runBlocking {
            service.startSession()
            
            var received: MemoryEvent? = null
            service.addListener { event -> received = event }

            service.remember(MemoryType.FACT, "Test")

            assertTrue(received is MemoryEvent.MemoryAdded)
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() = runBlocking {
            service.startSession()
            
            var count = 0
            val listener: (MemoryEvent) -> Unit = { count++ }
            service.addListener(listener)

            service.remember(MemoryType.FACT, "Test 1")
            assertEquals(1, count)

            service.removeListener(listener)
            service.remember(MemoryType.FACT, "Test 2")
            assertEquals(1, count) // Still 1, no new event
        }
    }

    // =========================================================================
    // State Persistence Tests
    // =========================================================================

    @Nested
    @DisplayName("State Persistence")
    inner class StatePersistenceTests {

        @Test
        @DisplayName("getState captures current state")
        fun getStateCapturesCurrentState() = runBlocking {
            service.startSession()
            service.rememberFact("Test fact")

            val state = service.getState()
            assertEquals(1, state.memories.size)
            assertNotNull(state.currentSessionId)
        }

        @Test
        @DisplayName("loadState restores state")
        fun loadStateRestoresState() = runBlocking {
            // Create some state
            service.startSession()
            service.rememberFact("Test fact")
            val originalState = service.getState()

            // Create new service and load state
            val newService = MemoryService.withoutEmbeddings()
            newService.loadState(originalState)

            assertEquals(1, newService.getRecentMemories(10).size)
        }
    }
}
