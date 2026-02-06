package com.sidekick.agent.collaboration

import com.sidekick.agent.specialists.AgentRole
import com.sidekick.agent.specialists.SpecialistAgent
import java.time.Instant
import java.util.UUID

/**
 * # Collaboration Models
 *
 * Data models for multi-agent collaboration and coordination.
 * Part of Sidekick v0.9.3 Agent Collaboration feature.
 *
 * ## Overview
 *
 * Agent collaboration enables:
 * - Collaboration sessions with multiple agents
 * - Message passing between agents
 * - Coordination protocols (Round Robin, Debate, Consensus)
 * - Shared context and artifacts
 * - Turn management and flow control
 *
 * @since 0.9.3
 */

// =============================================================================
// Collaboration Session
// =============================================================================

/**
 * A collaboration session with multiple agents.
 *
 * @property id Unique session identifier
 * @property name Human-readable session name
 * @property goal The objective of this collaboration
 * @property participants Agents participating in this session
 * @property protocol Coordination protocol
 * @property status Current session status
 * @property sharedContext Context accessible to all participants
 * @property messages Messages exchanged in this session
 * @property currentTurn Index of current participant (for turn-based protocols)
 * @property createdAt Session creation time
 */
data class CollaborationSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val goal: String,
    val participants: List<Participant>,
    val protocol: CollaborationProtocol,
    val status: SessionStatus = SessionStatus.CREATED,
    val sharedContext: SharedContext = SharedContext(),
    val messages: List<CollaborationMessage> = emptyList(),
    val currentTurn: Int = 0,
    val maxTurns: Int = 20,
    val createdAt: Instant = Instant.now()
) {
    /**
     * Gets participant by role.
     */
    fun getParticipant(role: AgentRole): Participant? =
        participants.find { it.role == role }

    /**
     * Gets the current turn participant.
     */
    fun getCurrentParticipant(): Participant? =
        if (participants.isNotEmpty() && currentTurn < participants.size)
            participants[currentTurn % participants.size]
        else null

    /**
     * Gets the next participant.
     */
    fun getNextParticipant(): Participant? =
        if (participants.isNotEmpty())
            participants[(currentTurn + 1) % participants.size]
        else null

    /**
     * Whether session is active.
     */
    val isActive: Boolean get() = status == SessionStatus.ACTIVE

    /**
     * Whether session has reached max turns.
     */
    val hasReachedMaxTurns: Boolean get() = currentTurn >= maxTurns

    /**
     * Total message count.
     */
    val messageCount: Int get() = messages.size

    /**
     * Messages by a specific participant.
     */
    fun getMessagesByParticipant(participantId: String): List<CollaborationMessage> =
        messages.filter { it.senderId == participantId }

    /**
     * Advances to next turn.
     */
    fun advanceTurn(): CollaborationSession = copy(currentTurn = currentTurn + 1)

    /**
     * Adds a message.
     */
    fun addMessage(message: CollaborationMessage): CollaborationSession =
        copy(messages = messages + message)

    /**
     * Updates status.
     */
    fun withStatus(newStatus: SessionStatus): CollaborationSession = copy(status = newStatus)

    /**
     * Updates shared context.
     */
    fun withContext(context: SharedContext): CollaborationSession = copy(sharedContext = context)

    companion object {
        /**
         * Creates a session with standard roles.
         */
        fun create(
            name: String,
            goal: String,
            roles: List<AgentRole>,
            protocol: CollaborationProtocol = CollaborationProtocol.ROUND_ROBIN
        ): CollaborationSession {
            val participants = roles.mapIndexed { index, role ->
                Participant(
                    id = UUID.randomUUID().toString(),
                    role = role,
                    order = index
                )
            }
            return CollaborationSession(
                name = name,
                goal = goal,
                participants = participants,
                protocol = protocol
            )
        }

        /**
         * Creates a debate session between two roles.
         */
        fun debate(goal: String, role1: AgentRole, role2: AgentRole): CollaborationSession =
            create("Debate: $goal", goal, listOf(role1, role2), CollaborationProtocol.DEBATE)

        /**
         * Creates a review session.
         */
        fun review(goal: String): CollaborationSession = create(
            "Review: $goal",
            goal,
            listOf(AgentRole.IMPLEMENTER, AgentRole.REVIEWER),
            CollaborationProtocol.ROUND_ROBIN
        )
    }
}

// =============================================================================
// Session Status
// =============================================================================

/**
 * Status of a collaboration session.
 */
enum class SessionStatus(val displayName: String) {
    /** Session created but not started */
    CREATED("Created"),
    
    /** Session is active */
    ACTIVE("Active"),
    
    /** Session is paused */
    PAUSED("Paused"),
    
    /** Waiting for user input */
    WAITING_FOR_USER("Waiting for User"),
    
    /** Consensus reached */
    CONSENSUS_REACHED("Consensus Reached"),
    
    /** Session completed successfully */
    COMPLETED("Completed"),
    
    /** Session cancelled */
    CANCELLED("Cancelled"),
    
    /** Session failed */
    FAILED("Failed")
}

// =============================================================================
// Participant
// =============================================================================

/**
 * A participant in a collaboration session.
 *
 * @property id Unique participant identifier
 * @property role Agent role
 * @property agent Optional specialist agent (may be assigned later)
 * @property order Turn order
 * @property status Participant status
 * @property messageCount Messages sent by this participant
 */
data class Participant(
    val id: String = UUID.randomUUID().toString(),
    val role: AgentRole,
    val agent: SpecialistAgent? = null,
    val order: Int = 0,
    val status: ParticipantStatus = ParticipantStatus.READY,
    val messageCount: Int = 0
) {
    /**
     * Display name with role icon.
     */
    val displayName: String get() = "${role.icon} ${role.displayName}"

    /**
     * Whether participant is ready.
     */
    val isReady: Boolean get() = status == ParticipantStatus.READY

    /**
     * Updates with assigned agent.
     */
    fun withAgent(specialist: SpecialistAgent): Participant =
        copy(agent = specialist)

    /**
     * Increments message count.
     */
    fun incrementMessages(): Participant = copy(messageCount = messageCount + 1)
}

/**
 * Status of a participant.
 */
enum class ParticipantStatus {
    READY,
    THINKING,
    RESPONDED,
    BLOCKED,
    EXITED
}

// =============================================================================
// Collaboration Protocol
// =============================================================================

/**
 * Protocols for coordinating agent collaboration.
 */
enum class CollaborationProtocol(
    val displayName: String,
    val description: String
) {
    /**
     * Agents take turns in order.
     */
    ROUND_ROBIN(
        "Round Robin",
        "Each agent takes a turn in sequence"
    ),

    /**
     * Broadcasting to all agents simultaneously.
     */
    BROADCAST(
        "Broadcast",
        "All agents receive and respond to messages in parallel"
    ),

    /**
     * Free-form discussion.
     */
    FREE_FORM(
        "Free Form",
        "Agents can respond at any time based on context"
    ),

    /**
     * Structured debate between opposing viewpoints.
     */
    DEBATE(
        "Debate",
        "Two agents present and defend opposing approaches"
    ),

    /**
     * Work toward consensus.
     */
    CONSENSUS(
        "Consensus",
        "Agents collaborate until reaching agreement"
    ),

    /**
     * Lead agent coordinates others.
     */
    LEADER_FOLLOWER(
        "Leader-Follower",
        "One agent leads and delegates to others"
    ),

    /**
     * Agents vote on proposals.
     */
    VOTING(
        "Voting",
        "Agents vote to decide between options"
    )
}

// =============================================================================
// Collaboration Message
// =============================================================================

/**
 * A message in a collaboration session.
 *
 * @property id Message identifier
 * @property sessionId Session this message belongs to
 * @property senderId ID of the sending participant
 * @property senderRole Role of the sender
 * @property type Message type
 * @property content Message content
 * @property replyTo ID of message being replied to
 * @property mentions Participants mentioned in this message
 * @property attachments Attached artifacts
 * @property metadata Additional metadata
 * @property timestamp Message timestamp
 */
data class CollaborationMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val senderId: String,
    val senderRole: AgentRole,
    val type: MessageType,
    val content: String,
    val replyTo: String? = null,
    val mentions: List<AgentRole> = emptyList(),
    val attachments: List<MessageAttachment> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Instant = Instant.now()
) {
    /**
     * Whether this is a reply.
     */
    val isReply: Boolean get() = replyTo != null

    /**
     * Whether message mentions a specific role.
     */
    fun mentions(role: AgentRole): Boolean = role in mentions

    /**
     * Whether message has attachments.
     */
    val hasAttachments: Boolean get() = attachments.isNotEmpty()

    /**
     * Gets code attachments.
     */
    fun getCodeAttachments(): List<MessageAttachment> =
        attachments.filter { it.type == AttachmentType.CODE }

    companion object {
        /**
         * Creates a contribution message.
         */
        fun contribution(
            sessionId: String,
            senderId: String,
            senderRole: AgentRole,
            content: String
        ): CollaborationMessage = CollaborationMessage(
            sessionId = sessionId,
            senderId = senderId,
            senderRole = senderRole,
            type = MessageType.CONTRIBUTION,
            content = content
        )

        /**
         * Creates a question message.
         */
        fun question(
            sessionId: String,
            senderId: String,
            senderRole: AgentRole,
            content: String,
            targetRole: AgentRole
        ): CollaborationMessage = CollaborationMessage(
            sessionId = sessionId,
            senderId = senderId,
            senderRole = senderRole,
            type = MessageType.QUESTION,
            content = content,
            mentions = listOf(targetRole)
        )

        /**
         * Creates a proposal message.
         */
        fun proposal(
            sessionId: String,
            senderId: String,
            senderRole: AgentRole,
            content: String
        ): CollaborationMessage = CollaborationMessage(
            sessionId = sessionId,
            senderId = senderId,
            senderRole = senderRole,
            type = MessageType.PROPOSAL,
            content = content
        )

        /**
         * Creates a vote message.
         */
        fun vote(
            sessionId: String,
            senderId: String,
            senderRole: AgentRole,
            proposalId: String,
            approve: Boolean
        ): CollaborationMessage = CollaborationMessage(
            sessionId = sessionId,
            senderId = senderId,
            senderRole = senderRole,
            type = MessageType.VOTE,
            content = if (approve) "APPROVE" else "REJECT",
            replyTo = proposalId
        )
    }
}

// =============================================================================
// Message Type
// =============================================================================

/**
 * Types of collaboration messages.
 */
enum class MessageType(val displayName: String) {
    /** General contribution to discussion */
    CONTRIBUTION("Contribution"),
    
    /** Question to another participant */
    QUESTION("Question"),
    
    /** Answer to a question */
    ANSWER("Answer"),
    
    /** Proposal for consideration */
    PROPOSAL("Proposal"),
    
    /** Vote for/against a proposal */
    VOTE("Vote"),
    
    /** Critique of another's contribution */
    CRITIQUE("Critique"),
    
    /** Agreement with a contribution */
    AGREEMENT("Agreement"),
    
    /** Request for delegation */
    DELEGATION_REQUEST("Delegation Request"),
    
    /** Summary of discussion */
    SUMMARY("Summary"),
    
    /** Final decision */
    DECISION("Decision"),
    
    /** System message */
    SYSTEM("System")
}

// =============================================================================
// Message Attachment
// =============================================================================

/**
 * Attachment to a message.
 */
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AttachmentType,
    val content: String,
    val filePath: String? = null
) {
    companion object {
        fun code(name: String, content: String, filePath: String? = null) =
            MessageAttachment(name = name, type = AttachmentType.CODE, content = content, filePath = filePath)

        fun document(name: String, content: String) =
            MessageAttachment(name = name, type = AttachmentType.DOCUMENT, content = content)

        fun diagram(name: String, content: String) =
            MessageAttachment(name = name, type = AttachmentType.DIAGRAM, content = content)
    }
}

/**
 * Types of attachments.
 */
enum class AttachmentType {
    CODE,
    DOCUMENT,
    DIAGRAM,
    TEST,
    REVIEW,
    DATA
}

// =============================================================================
// Shared Context
// =============================================================================

/**
 * Context shared among all participants.
 *
 * @property artifacts Shared artifacts (code, documents)
 * @property facts Established facts
 * @property decisions Decisions made
 * @property openQuestions Unresolved questions
 * @property metadata Additional context
 */
data class SharedContext(
    val artifacts: Map<String, String> = emptyMap(),
    val facts: List<String> = emptyList(),
    val decisions: List<Decision> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Adds an artifact.
     */
    fun withArtifact(name: String, content: String): SharedContext =
        copy(artifacts = artifacts + (name to content))

    /**
     * Adds a fact.
     */
    fun withFact(fact: String): SharedContext =
        copy(facts = facts + fact)

    /**
     * Adds a decision.
     */
    fun withDecision(decision: Decision): SharedContext =
        copy(decisions = decisions + decision)

    /**
     * Adds an open question.
     */
    fun withQuestion(question: String): SharedContext =
        copy(openQuestions = openQuestions + question)

    /**
     * Removes a resolved question.
     */
    fun resolveQuestion(question: String): SharedContext =
        copy(openQuestions = openQuestions.filter { it != question })
}

/**
 * A decision made during collaboration.
 */
data class Decision(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val rationale: String,
    val madeBy: AgentRole,
    val supporters: List<AgentRole> = emptyList(),
    val timestamp: Instant = Instant.now()
)

// =============================================================================
// Consensus
// =============================================================================

/**
 * Tracks consensus state.
 */
data class ConsensusState(
    val proposalId: String,
    val proposal: String,
    val votes: Map<String, Vote> = emptyMap(),
    val status: ConsensusStatus = ConsensusStatus.PENDING
) {
    /**
     * Total votes.
     */
    val totalVotes: Int get() = votes.size

    /**
     * Approval count.
     */
    val approvalCount: Int get() = votes.values.count { it.approve }

    /**
     * Rejection count.
     */
    val rejectionCount: Int get() = votes.values.count { !it.approve }

    /**
     * Approval percentage.
     */
    val approvalPercentage: Float
        get() = if (totalVotes > 0) approvalCount.toFloat() / totalVotes else 0f

    /**
     * Whether consensus is reached (>= threshold).
     */
    fun hasConsensus(threshold: Float = 0.7f): Boolean = approvalPercentage >= threshold

    /**
     * Records a vote.
     */
    fun recordVote(participantId: String, approve: Boolean, reason: String? = null): ConsensusState {
        val vote = Vote(participantId, approve, reason)
        return copy(votes = votes + (participantId to vote))
    }

    /**
     * Updates status based on votes.
     */
    fun updateStatus(participantCount: Int, threshold: Float = 0.7f): ConsensusState {
        val allVoted = totalVotes >= participantCount
        val newStatus = when {
            allVoted && hasConsensus(threshold) -> ConsensusStatus.ACCEPTED
            allVoted && !hasConsensus(threshold) -> ConsensusStatus.REJECTED
            else -> ConsensusStatus.PENDING
        }
        return copy(status = newStatus)
    }
}

/**
 * A vote on a proposal.
 */
data class Vote(
    val participantId: String,
    val approve: Boolean,
    val reason: String? = null,
    val timestamp: Instant = Instant.now()
)

/**
 * Status of consensus.
 */
enum class ConsensusStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}

// =============================================================================
// Collaboration Events
// =============================================================================

/**
 * Events from collaboration sessions.
 */
sealed class CollaborationEvent {
    abstract val sessionId: String
    abstract val timestamp: Instant

    data class SessionStarted(
        override val sessionId: String,
        val participantCount: Int,
        val protocol: CollaborationProtocol,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()

    data class MessageSent(
        override val sessionId: String,
        val messageId: String,
        val senderRole: AgentRole,
        val messageType: MessageType,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()

    data class TurnAdvanced(
        override val sessionId: String,
        val turnNumber: Int,
        val nextParticipant: AgentRole,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()

    data class ConsensusReached(
        override val sessionId: String,
        val proposal: String,
        val approvalRate: Float,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()

    data class DecisionMade(
        override val sessionId: String,
        val decision: String,
        val madeBy: AgentRole,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()

    data class SessionCompleted(
        override val sessionId: String,
        val totalTurns: Int,
        val messageCount: Int,
        val decisionsCount: Int,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()

    data class SessionFailed(
        override val sessionId: String,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : CollaborationEvent()
}

// =============================================================================
// Collaboration Result
// =============================================================================

/**
 * Result of a collaboration session.
 */
data class CollaborationResult(
    val sessionId: String,
    val goal: String,
    val success: Boolean,
    val outcome: String,
    val decisions: List<Decision>,
    val artifacts: Map<String, String>,
    val totalTurns: Int,
    val messageCount: Int,
    val participantContributions: Map<AgentRole, Int>,
    val durationMs: Long
) {
    /**
     * Whether any decisions were made.
     */
    val hasDecisions: Boolean get() = decisions.isNotEmpty()

    /**
     * Whether any artifacts were produced.
     */
    val hasArtifacts: Boolean get() = artifacts.isNotEmpty()

    /**
     * Most active participant.
     */
    val mostActiveParticipant: AgentRole?
        get() = participantContributions.maxByOrNull { it.value }?.key
}
