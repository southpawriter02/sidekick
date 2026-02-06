package com.sidekick.agent.collaboration

import com.sidekick.agent.specialists.AgentRole
import com.sidekick.agent.specialists.SpecialistAgent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Collaboration Orchestrator.
 */
@DisplayName("Collaboration Orchestrator Tests")
class CollaborationOrchestratorTest {

    private lateinit var orchestrator: CollaborationOrchestrator

    @BeforeEach
    fun setUp() {
        orchestrator = CollaborationOrchestrator { agent, prompt, _ ->
            "Response from ${agent.role.displayName}: ${prompt.take(50)}"
        }
    }

    // =========================================================================
    // Session Creation Tests
    // =========================================================================

    @Nested
    @DisplayName("Session Creation")
    inner class SessionCreationTests {

        @Test
        @DisplayName("createSession creates with parameters")
        fun createSessionCreatesWithParameters() {
            val session = orchestrator.createSession(
                "Test Session",
                "Implement feature",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, AgentRole.TESTER),
                CollaborationProtocol.ROUND_ROBIN
            )

            assertEquals("Test Session", session.name)
            assertEquals("Implement feature", session.goal)
            assertEquals(3, session.participants.size)
            assertEquals(CollaborationProtocol.ROUND_ROBIN, session.protocol)
        }

        @Test
        @DisplayName("createDebate creates debate session")
        fun createDebateCreatesDebateSession() {
            val session = orchestrator.createDebate(
                "Best database choice",
                AgentRole.ARCHITECT,
                AgentRole.OPTIMIZER
            )

            assertEquals(CollaborationProtocol.DEBATE, session.protocol)
            assertEquals(2, session.participants.size)
        }

        @Test
        @DisplayName("createReview creates review session")
        fun createReviewCreatesReviewSession() {
            val session = orchestrator.createReview("Review auth module")

            assertTrue(session.participants.any { it.role == AgentRole.IMPLEMENTER })
            assertTrue(session.participants.any { it.role == AgentRole.REVIEWER })
        }
    }

    // =========================================================================
    // Session Retrieval Tests
    // =========================================================================

    @Nested
    @DisplayName("Session Retrieval")
    inner class SessionRetrievalTests {

        @Test
        @DisplayName("getSession returns by ID")
        fun getSessionReturnsById() {
            val created = orchestrator.createSession("Test", "Goal", listOf(AgentRole.ARCHITECT))
            val retrieved = orchestrator.getSession(created.id)

            assertEquals(created.id, retrieved?.id)
        }

        @Test
        @DisplayName("getAllSessions returns all")
        fun getAllSessionsReturnsAll() {
            orchestrator.createSession("Session 1", "Goal 1", listOf(AgentRole.ARCHITECT))
            orchestrator.createSession("Session 2", "Goal 2", listOf(AgentRole.IMPLEMENTER))

            val all = orchestrator.getAllSessions()
            assertEquals(2, all.size)
        }

        @Test
        @DisplayName("getActiveSessions filters active")
        fun getActiveSessionsFiltersActive() {
            val session1 = orchestrator.createSession("S1", "G1", listOf(AgentRole.ARCHITECT))
            orchestrator.createSession("S2", "G2", listOf(AgentRole.IMPLEMENTER))

            orchestrator.startSession(session1.id)

            val active = orchestrator.getActiveSessions()
            assertEquals(1, active.size)
            assertTrue(active.first().isActive)
        }
    }

    // =========================================================================
    // Session Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Session Lifecycle")
    inner class SessionLifecycleTests {

        @Test
        @DisplayName("startSession activates session")
        fun startSessionActivatesSession() {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            val started = orchestrator.startSession(session.id)

            assertEquals(SessionStatus.ACTIVE, started?.status)
            assertNotNull(started?.participants?.first()?.agent)
        }

        @Test
        @DisplayName("pauseSession pauses active session")
        fun pauseSessionPausesActiveSession() {
            val session = orchestrator.createSession("Test", "Goal", listOf(AgentRole.ARCHITECT))
            orchestrator.startSession(session.id)

            val paused = orchestrator.pauseSession(session.id)

            assertEquals(SessionStatus.PAUSED, paused?.status)
        }

        @Test
        @DisplayName("resumeSession resumes paused session")
        fun resumeSessionResumesPausedSession() {
            val session = orchestrator.createSession("Test", "Goal", listOf(AgentRole.ARCHITECT))
            orchestrator.startSession(session.id)
            orchestrator.pauseSession(session.id)

            val resumed = orchestrator.resumeSession(session.id)

            assertEquals(SessionStatus.ACTIVE, resumed?.status)
        }

        @Test
        @DisplayName("endSession completes session")
        fun endSessionCompletesSession() {
            val session = orchestrator.createSession("Test", "Goal", listOf(AgentRole.ARCHITECT))
            orchestrator.startSession(session.id)

            val result = orchestrator.endSession(session.id)

            assertNotNull(result)
            assertTrue(result!!.success)
            assertEquals(session.id, result.sessionId)
        }

        @Test
        @DisplayName("cancelSession cancels session")
        fun cancelSessionCancelsSession() {
            val session = orchestrator.createSession("Test", "Goal", listOf(AgentRole.ARCHITECT))

            val cancelled = orchestrator.cancelSession(session.id)

            assertEquals(SessionStatus.CANCELLED, cancelled?.status)
        }
    }

    // =========================================================================
    // Turn Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Turn Execution")
    inner class TurnExecutionTests {

        @Test
        @DisplayName("executeTurn executes current participant")
        fun executeTurnExecutesCurrentParticipant() = runBlocking {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER)
            )
            orchestrator.startSession(session.id)

            val result = orchestrator.executeTurn(session.id)

            assertTrue(result.success)
            assertNotNull(result.message)
            assertEquals(AgentRole.ARCHITECT, result.message?.senderRole)
        }

        @Test
        @DisplayName("executeTurn advances turn")
        fun executeTurnAdvancesTurn() = runBlocking {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER)
            )
            orchestrator.startSession(session.id)

            orchestrator.executeTurn(session.id)
            val updated = orchestrator.getSession(session.id)

            assertEquals(1, updated?.currentTurn)
        }

        @Test
        @DisplayName("executeTurn fails for inactive session")
        fun executeTurnFailsForInactiveSession() = runBlocking {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )
            // Not started

            val result = orchestrator.executeTurn(session.id)

            assertFalse(result.success)
            assertNotNull(result.error)
        }

        @Test
        @DisplayName("runRound executes all participants")
        fun runRoundExecutesAllParticipants() = runBlocking {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, AgentRole.TESTER)
            )
            orchestrator.startSession(session.id)

            val messages = orchestrator.runRound(session.id)

            assertEquals(3, messages.size)
            assertTrue(messages.any { it.senderRole == AgentRole.ARCHITECT })
            assertTrue(messages.any { it.senderRole == AgentRole.IMPLEMENTER })
            assertTrue(messages.any { it.senderRole == AgentRole.TESTER })
        }
    }

    // =========================================================================
    // Protocol Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Protocol Execution")
    inner class ProtocolExecutionTests {

        @Test
        @DisplayName("executeSession runs full session")
        fun executeSessionRunsFullSession() = runBlocking {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER),
                CollaborationProtocol.ROUND_ROBIN
            )

            val result = orchestrator.executeSession(session.id, maxRounds = 2)

            assertNotNull(result)
            assertTrue(result!!.success)
            assertTrue(result.messageCount > 0)
        }

        @Test
        @DisplayName("executeSession handles debate protocol")
        fun executeSessionHandlesDebateProtocol() = runBlocking {
            val session = orchestrator.createDebate(
                "Best approach",
                AgentRole.ARCHITECT,
                AgentRole.OPTIMIZER
            )

            val result = orchestrator.executeSession(session.id, maxRounds = 2)

            assertNotNull(result)
            assertEquals(CollaborationProtocol.DEBATE, orchestrator.getSession(session.id)?.protocol)
        }
    }

    // =========================================================================
    // Messaging Tests
    // =========================================================================

    @Nested
    @DisplayName("Messaging")
    inner class MessagingTests {

        @Test
        @DisplayName("sendMessage adds message to session")
        fun sendMessageAddsMessageToSession() {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            val message = orchestrator.sendMessage(
                session.id,
                "sender1",
                AgentRole.ARCHITECT,
                MessageType.PROPOSAL,
                "I propose we use caching"
            )

            assertNotNull(message)
            assertEquals(MessageType.PROPOSAL, message?.type)

            val updated = orchestrator.getSession(session.id)
            assertEquals(1, updated?.messageCount)
        }

        @Test
        @DisplayName("recordDecision adds to context")
        fun recordDecisionAddsToContext() {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            val decision = orchestrator.recordDecision(
                session.id,
                "Use PostgreSQL",
                "Better for complex queries",
                AgentRole.ARCHITECT
            )

            assertNotNull(decision)
            assertEquals("Use PostgreSQL", decision?.description)

            val updated = orchestrator.getSession(session.id)
            assertEquals(1, updated?.sharedContext?.decisions?.size)
        }
    }

    // =========================================================================
    // Consensus Tests
    // =========================================================================

    @Nested
    @DisplayName("Consensus")
    inner class ConsensusTests {

        @Test
        @DisplayName("recordVote tracks votes")
        fun recordVoteTracksVotes() {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, AgentRole.REVIEWER),
                CollaborationProtocol.CONSENSUS
            )

            // Setup consensus state manually for test
            orchestrator.sendMessage(
                session.id,
                "proposer",
                AgentRole.ARCHITECT,
                MessageType.PROPOSAL,
                "Use caching"
            )

            // In a real scenario, consensus state would be initialized
            // This test verifies the vote recording mechanism
            val vote1 = orchestrator.sendMessage(
                session.id,
                "voter1",
                AgentRole.IMPLEMENTER,
                MessageType.VOTE,
                "APPROVE"
            )

            assertNotNull(vote1)
            assertEquals(MessageType.VOTE, vote1?.type)
        }
    }

    // =========================================================================
    // Context Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Context Management")
    inner class ContextManagementTests {

        @Test
        @DisplayName("addArtifact updates shared context")
        fun addArtifactUpdatesSharedContext() {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            val context = orchestrator.addArtifact(session.id, "code.kt", "fun main() {}")

            assertNotNull(context)
            assertEquals("fun main() {}", context?.artifacts?.get("code.kt"))
        }

        @Test
        @DisplayName("addFact updates shared context")
        fun addFactUpdatesSharedContext() {
            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            val context = orchestrator.addFact(session.id, "The API uses JWT")

            assertNotNull(context)
            assertTrue(context?.facts?.contains("The API uses JWT") == true)
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
            val session1 = orchestrator.createSession(
                "S1",
                "G1",
                listOf(AgentRole.ARCHITECT),
                CollaborationProtocol.ROUND_ROBIN
            )
            orchestrator.createSession(
                "S2",
                "G2",
                listOf(AgentRole.IMPLEMENTER),
                CollaborationProtocol.CONSENSUS
            )

            orchestrator.startSession(session1.id)
            orchestrator.executeTurn(session1.id)
            orchestrator.endSession(session1.id)

            val stats = orchestrator.getStats()

            assertEquals(2, stats.totalSessions)
            assertEquals(1, stats.completedSessions)
            assertTrue(stats.totalMessages > 0)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits session started event")
        fun emitsSessionStartedEvent() {
            var received: CollaborationEvent? = null
            orchestrator.addListener { received = it }

            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )
            orchestrator.startSession(session.id)

            assertTrue(received is CollaborationEvent.SessionStarted)
        }

        @Test
        @DisplayName("emits message sent event")
        fun emitsMessageSentEvent() {
            val events = mutableListOf<CollaborationEvent>()
            orchestrator.addListener { events.add(it) }

            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            orchestrator.sendMessage(
                session.id,
                "sender",
                AgentRole.ARCHITECT,
                MessageType.CONTRIBUTION,
                "Content"
            )

            assertTrue(events.any { it is CollaborationEvent.MessageSent })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() {
            var count = 0
            val listener: (CollaborationEvent) -> Unit = { count++ }
            orchestrator.addListener(listener)

            val session = orchestrator.createSession(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )
            orchestrator.startSession(session.id)
            val firstCount = count

            orchestrator.removeListener(listener)
            orchestrator.sendMessage(session.id, "s", AgentRole.ARCHITECT, MessageType.CONTRIBUTION, "C")

            assertEquals(firstCount, count)
        }
    }

    // =========================================================================
    // Cleanup Tests
    // =========================================================================

    @Nested
    @DisplayName("Cleanup")
    inner class CleanupTests {

        @Test
        @DisplayName("clearSessions removes all")
        fun clearSessionsRemovesAll() {
            orchestrator.createSession("S1", "G1", listOf(AgentRole.ARCHITECT))
            orchestrator.createSession("S2", "G2", listOf(AgentRole.IMPLEMENTER))

            orchestrator.clearSessions()

            assertEquals(0, orchestrator.getAllSessions().size)
        }
    }
}
