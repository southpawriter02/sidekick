package com.sidekick.agent.collaboration

import com.sidekick.agent.specialists.AgentRole
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Collaboration Models.
 */
@DisplayName("Collaboration Models Tests")
class CollaborationModelsTest {

    // =========================================================================
    // CollaborationSession Tests
    // =========================================================================

    @Nested
    @DisplayName("CollaborationSession")
    inner class CollaborationSessionTests {

        @Test
        @DisplayName("create factory initializes session")
        fun createFactoryInitializesSession() {
            val session = CollaborationSession.create(
                "Test Session",
                "Build a feature",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER),
                CollaborationProtocol.ROUND_ROBIN
            )

            assertEquals("Test Session", session.name)
            assertEquals("Build a feature", session.goal)
            assertEquals(2, session.participants.size)
            assertEquals(CollaborationProtocol.ROUND_ROBIN, session.protocol)
            assertEquals(SessionStatus.CREATED, session.status)
        }

        @Test
        @DisplayName("debate factory creates two-participant session")
        fun debateFactoryCreatesTwoParticipantSession() {
            val session = CollaborationSession.debate(
                "Best approach for caching",
                AgentRole.ARCHITECT,
                AgentRole.OPTIMIZER
            )

            assertEquals(CollaborationProtocol.DEBATE, session.protocol)
            assertEquals(2, session.participants.size)
        }

        @Test
        @DisplayName("getParticipant returns by role")
        fun getParticipantReturnsByRole() {
            val session = CollaborationSession.create(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER)
            )

            val architect = session.getParticipant(AgentRole.ARCHITECT)
            assertNotNull(architect)
            assertEquals(AgentRole.ARCHITECT, architect?.role)

            val tester = session.getParticipant(AgentRole.TESTER)
            assertNull(tester)
        }

        @Test
        @DisplayName("getCurrentParticipant returns correct participant")
        fun getCurrentParticipantReturnsCorrectParticipant() {
            val session = CollaborationSession.create(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, AgentRole.TESTER)
            )

            assertEquals(AgentRole.ARCHITECT, session.getCurrentParticipant()?.role)

            val advanced = session.advanceTurn()
            assertEquals(AgentRole.IMPLEMENTER, advanced.getCurrentParticipant()?.role)
        }

        @Test
        @DisplayName("advanceTurn increments turn counter")
        fun advanceTurnIncrementsTurnCounter() {
            var session = CollaborationSession.create("Test", "Goal", listOf(AgentRole.ARCHITECT))

            assertEquals(0, session.currentTurn)
            session = session.advanceTurn()
            assertEquals(1, session.currentTurn)
            session = session.advanceTurn()
            assertEquals(2, session.currentTurn)
        }

        @Test
        @DisplayName("addMessage appends message")
        fun addMessageAppendsMessage() {
            var session = CollaborationSession.create(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            )

            assertEquals(0, session.messageCount)

            val message = CollaborationMessage.contribution(
                sessionId = session.id,
                senderId = "sender1",
                senderRole = AgentRole.ARCHITECT,
                content = "Test message"
            )

            session = session.addMessage(message)
            assertEquals(1, session.messageCount)
        }

        @Test
        @DisplayName("hasReachedMaxTurns checks limit")
        fun hasReachedMaxTurnsChecksLimit() {
            var session = CollaborationSession.create(
                "Test",
                "Goal",
                listOf(AgentRole.ARCHITECT)
            ).copy(maxTurns = 2)

            assertFalse(session.hasReachedMaxTurns)
            session = session.advanceTurn()
            assertFalse(session.hasReachedMaxTurns)
            session = session.advanceTurn()
            assertTrue(session.hasReachedMaxTurns)
        }
    }

    // =========================================================================
    // Participant Tests
    // =========================================================================

    @Nested
    @DisplayName("Participant")
    inner class ParticipantTests {

        @Test
        @DisplayName("displayName includes icon")
        fun displayNameIncludesIcon() {
            val participant = Participant(role = AgentRole.ARCHITECT)

            assertTrue(participant.displayName.contains("🏗️"))
            assertTrue(participant.displayName.contains("Architect"))
        }

        @Test
        @DisplayName("isReady checks status")
        fun isReadyChecksStatus() {
            val ready = Participant(role = AgentRole.IMPLEMENTER)
            assertTrue(ready.isReady)

            val blocked = Participant(role = AgentRole.IMPLEMENTER, status = ParticipantStatus.BLOCKED)
            assertFalse(blocked.isReady)
        }

        @Test
        @DisplayName("incrementMessages updates count")
        fun incrementMessagesUpdatesCount() {
            var participant = Participant(role = AgentRole.REVIEWER)

            assertEquals(0, participant.messageCount)
            participant = participant.incrementMessages()
            assertEquals(1, participant.messageCount)
        }
    }

    // =========================================================================
    // CollaborationProtocol Tests
    // =========================================================================

    @Nested
    @DisplayName("CollaborationProtocol")
    inner class CollaborationProtocolTests {

        @Test
        @DisplayName("all protocols have display names")
        fun allProtocolsHaveDisplayNames() {
            CollaborationProtocol.entries.forEach { protocol ->
                assertTrue(protocol.displayName.isNotBlank())
                assertTrue(protocol.description.isNotBlank())
            }
        }
    }

    // =========================================================================
    // CollaborationMessage Tests
    // =========================================================================

    @Nested
    @DisplayName("CollaborationMessage")
    inner class CollaborationMessageTests {

        @Test
        @DisplayName("contribution factory creates message")
        fun contributionFactoryCreatesMessage() {
            val message = CollaborationMessage.contribution(
                sessionId = "session1",
                senderId = "sender1",
                senderRole = AgentRole.ARCHITECT,
                content = "My contribution"
            )

            assertEquals(MessageType.CONTRIBUTION, message.type)
            assertEquals("My contribution", message.content)
        }

        @Test
        @DisplayName("question factory includes mention")
        fun questionFactoryIncludesMention() {
            val message = CollaborationMessage.question(
                sessionId = "session1",
                senderId = "sender1",
                senderRole = AgentRole.ARCHITECT,
                content = "What do you think?",
                targetRole = AgentRole.IMPLEMENTER
            )

            assertEquals(MessageType.QUESTION, message.type)
            assertTrue(message.mentions(AgentRole.IMPLEMENTER))
        }

        @Test
        @DisplayName("vote factory includes reference")
        fun voteFactoryIncludesReference() {
            val message = CollaborationMessage.vote(
                sessionId = "session1",
                senderId = "sender1",
                senderRole = AgentRole.REVIEWER,
                proposalId = "proposal1",
                approve = true
            )

            assertEquals(MessageType.VOTE, message.type)
            assertEquals("proposal1", message.replyTo)
            assertEquals("APPROVE", message.content)
        }

        @Test
        @DisplayName("getCodeAttachments filters correctly")
        fun getCodeAttachmentsFiltersCorrectly() {
            val message = CollaborationMessage(
                sessionId = "s1",
                senderId = "sender1",
                senderRole = AgentRole.IMPLEMENTER,
                type = MessageType.CONTRIBUTION,
                content = "Here's my code",
                attachments = listOf(
                    MessageAttachment.code("Code", "fun test()", "/test.kt"),
                    MessageAttachment.document("Docs", "Documentation"),
                    MessageAttachment.code("More Code", "fun more()", "/more.kt")
                )
            )

            val codeAttachments = message.getCodeAttachments()
            assertEquals(2, codeAttachments.size)
        }
    }

    // =========================================================================
    // SharedContext Tests
    // =========================================================================

    @Nested
    @DisplayName("SharedContext")
    inner class SharedContextTests {

        @Test
        @DisplayName("withArtifact adds artifact")
        fun withArtifactAddsArtifact() {
            var context = SharedContext()

            context = context.withArtifact("code.kt", "fun main() {}")

            assertEquals(1, context.artifacts.size)
            assertEquals("fun main() {}", context.artifacts["code.kt"])
        }

        @Test
        @DisplayName("withFact adds fact")
        fun withFactAddsFact() {
            var context = SharedContext()

            context = context.withFact("The API uses REST")
            context = context.withFact("Auth is required")

            assertEquals(2, context.facts.size)
        }

        @Test
        @DisplayName("withDecision adds decision")
        fun withDecisionAddsDecision() {
            var context = SharedContext()

            val decision = Decision(
                description = "Use PostgreSQL",
                rationale = "Better for our use case",
                madeBy = AgentRole.ARCHITECT
            )
            context = context.withDecision(decision)

            assertEquals(1, context.decisions.size)
            assertEquals("Use PostgreSQL", context.decisions.first().description)
        }

        @Test
        @DisplayName("resolveQuestion removes question")
        fun resolveQuestionRemovesQuestion() {
            var context = SharedContext()
                .withQuestion("What database?")
                .withQuestion("What framework?")

            assertEquals(2, context.openQuestions.size)

            context = context.resolveQuestion("What database?")
            assertEquals(1, context.openQuestions.size)
            assertEquals("What framework?", context.openQuestions.first())
        }
    }

    // =========================================================================
    // ConsensusState Tests
    // =========================================================================

    @Nested
    @DisplayName("ConsensusState")
    inner class ConsensusStateTests {

        @Test
        @DisplayName("recordVote adds vote")
        fun recordVoteAddsVote() {
            var state = ConsensusState(
                proposalId = "p1",
                proposal = "Use caching"
            )

            state = state.recordVote("participant1", true, "Sounds good")
            state = state.recordVote("participant2", false, "Too complex")

            assertEquals(2, state.totalVotes)
            assertEquals(1, state.approvalCount)
            assertEquals(1, state.rejectionCount)
        }

        @Test
        @DisplayName("approvalPercentage calculates correctly")
        fun approvalPercentageCalculatesCorrectly() {
            var state = ConsensusState(proposalId = "p1", proposal = "Proposal")

            state = state.recordVote("p1", true)
            state = state.recordVote("p2", true)
            state = state.recordVote("p3", false)

            assertEquals(2f / 3f, state.approvalPercentage, 0.01f)
        }

        @Test
        @DisplayName("hasConsensus checks threshold")
        fun hasConsensusChecksThreshold() {
            var state = ConsensusState(proposalId = "p1", proposal = "Proposal")

            state = state.recordVote("p1", true)
            state = state.recordVote("p2", true)
            state = state.recordVote("p3", true)
            state = state.recordVote("p4", false)

            assertTrue(state.hasConsensus(0.7f)) // 75% approval
            assertFalse(state.hasConsensus(0.8f)) // 75% < 80%
        }

        @Test
        @DisplayName("updateStatus changes based on votes")
        fun updateStatusChangesBasedOnVotes() {
            var state = ConsensusState(proposalId = "p1", proposal = "Proposal")

            state = state.recordVote("p1", true)
            state = state.updateStatus(3)
            assertEquals(ConsensusStatus.PENDING, state.status)

            state = state.recordVote("p2", true)
            state = state.recordVote("p3", true)
            state = state.updateStatus(3)
            assertEquals(ConsensusStatus.ACCEPTED, state.status)
        }
    }

    // =========================================================================
    // CollaborationEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("CollaborationEvent")
    inner class CollaborationEventTests {

        @Test
        @DisplayName("events have required fields")
        fun eventsHaveRequiredFields() {
            val started = CollaborationEvent.SessionStarted(
                "s1",
                3,
                CollaborationProtocol.CONSENSUS
            )
            assertEquals("s1", started.sessionId)
            assertEquals(3, started.participantCount)
            assertNotNull(started.timestamp)

            val completed = CollaborationEvent.SessionCompleted("s1", 10, 15, 2)
            assertEquals(10, completed.totalTurns)
            assertEquals(15, completed.messageCount)
        }
    }

    // =========================================================================
    // CollaborationResult Tests
    // =========================================================================

    @Nested
    @DisplayName("CollaborationResult")
    inner class CollaborationResultTests {

        @Test
        @DisplayName("hasDecisions checks list")
        fun hasDecisionsChecksList() {
            val withDecisions = CollaborationResult(
                sessionId = "s1",
                goal = "Goal",
                success = true,
                outcome = "Done",
                decisions = listOf(Decision("D1", "R1", AgentRole.ARCHITECT)),
                artifacts = emptyMap(),
                totalTurns = 5,
                messageCount = 10,
                participantContributions = emptyMap(),
                durationMs = 1000
            )
            assertTrue(withDecisions.hasDecisions)

            val noDecisions = withDecisions.copy(decisions = emptyList())
            assertFalse(noDecisions.hasDecisions)
        }

        @Test
        @DisplayName("mostActiveParticipant finds max contributor")
        fun mostActiveParticipantFindsMaxContributor() {
            val result = CollaborationResult(
                sessionId = "s1",
                goal = "Goal",
                success = true,
                outcome = "Done",
                decisions = emptyList(),
                artifacts = emptyMap(),
                totalTurns = 10,
                messageCount = 20,
                participantContributions = mapOf(
                    AgentRole.ARCHITECT to 3,
                    AgentRole.IMPLEMENTER to 8,
                    AgentRole.REVIEWER to 5
                ),
                durationMs = 5000
            )

            assertEquals(AgentRole.IMPLEMENTER, result.mostActiveParticipant)
        }
    }
}
