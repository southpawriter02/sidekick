package com.sidekick.agent.collaboration

import com.sidekick.agent.specialists.AgentRole
import com.sidekick.agent.specialists.SpecialistAgent
import com.sidekick.agent.specialists.SpecialistService
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Collaboration Orchestrator
 *
 * Orchestrates multi-agent collaboration sessions.
 * Part of Sidekick v0.9.3 Agent Collaboration feature.
 *
 * ## Features
 *
 * - Create and manage collaboration sessions
 * - Execute various coordination protocols
 * - Manage turn-taking and message flow
 * - Track consensus and decisions
 * - Produce collaboration results
 *
 * @since 0.9.3
 */
class CollaborationOrchestrator(
    private val specialistService: SpecialistService? = null,
    private val agentInvoker: suspend (SpecialistAgent, String, CollaborationSession) -> String = { agent, prompt, _ ->
        "Response from ${agent.role.displayName}: $prompt"
    }
) {
    private val sessions = ConcurrentHashMap<String, CollaborationSession>()
    private val consensusStates = ConcurrentHashMap<String, ConsensusState>()
    private val eventListeners = CopyOnWriteArrayList<(CollaborationEvent) -> Unit>()

    // =========================================================================
    // Session Management
    // =========================================================================

    /**
     * Creates a new collaboration session.
     */
    fun createSession(
        name: String,
        goal: String,
        roles: List<AgentRole>,
        protocol: CollaborationProtocol = CollaborationProtocol.ROUND_ROBIN
    ): CollaborationSession {
        val session = CollaborationSession.create(name, goal, roles, protocol)
        sessions[session.id] = session
        return session
    }

    /**
     * Creates a debate session.
     */
    fun createDebate(goal: String, role1: AgentRole, role2: AgentRole): CollaborationSession {
        val session = CollaborationSession.debate(goal, role1, role2)
        sessions[session.id] = session
        return session
    }

    /**
     * Creates a review session.
     */
    fun createReview(goal: String): CollaborationSession {
        val session = CollaborationSession.review(goal)
        sessions[session.id] = session
        return session
    }

    /**
     * Gets a session by ID.
     */
    fun getSession(sessionId: String): CollaborationSession? = sessions[sessionId]

    /**
     * Gets all sessions.
     */
    fun getAllSessions(): List<CollaborationSession> = sessions.values.toList()

    /**
     * Gets active sessions.
     */
    fun getActiveSessions(): List<CollaborationSession> =
        sessions.values.filter { it.isActive }

    // =========================================================================
    // Session Lifecycle
    // =========================================================================

    /**
     * Starts a collaboration session.
     */
    fun startSession(sessionId: String): CollaborationSession? {
        val session = sessions[sessionId] ?: return null
        if (session.status != SessionStatus.CREATED) return session

        // Assign agents to participants
        val updatedParticipants = session.participants.map { participant ->
            val agent = specialistService?.getSpecialist(participant.role)
                ?: createDefaultAgent(participant.role)
            participant.withAgent(agent)
        }

        val started = session.copy(
            participants = updatedParticipants,
            status = SessionStatus.ACTIVE
        )

        sessions[sessionId] = started
        emitEvent(CollaborationEvent.SessionStarted(
            sessionId,
            started.participants.size,
            started.protocol
        ))

        return started
    }

    /**
     * Pauses a session.
     */
    fun pauseSession(sessionId: String): CollaborationSession? {
        val session = sessions[sessionId] ?: return null
        val paused = session.withStatus(SessionStatus.PAUSED)
        sessions[sessionId] = paused
        return paused
    }

    /**
     * Resumes a session.
     */
    fun resumeSession(sessionId: String): CollaborationSession? {
        val session = sessions[sessionId] ?: return null
        if (session.status != SessionStatus.PAUSED) return session

        val resumed = session.withStatus(SessionStatus.ACTIVE)
        sessions[sessionId] = resumed
        return resumed
    }

    /**
     * Ends a session.
     */
    fun endSession(sessionId: String, success: Boolean = true): CollaborationResult? {
        val session = sessions[sessionId] ?: return null
        val startTime = session.createdAt.toEpochMilli()
        val endTime = Instant.now().toEpochMilli()

        val finalSession = session.withStatus(
            if (success) SessionStatus.COMPLETED else SessionStatus.FAILED
        )
        sessions[sessionId] = finalSession

        emitEvent(CollaborationEvent.SessionCompleted(
            sessionId,
            finalSession.currentTurn,
            finalSession.messageCount,
            finalSession.sharedContext.decisions.size
        ))

        return createResult(finalSession, endTime - startTime)
    }

    /**
     * Cancels a session.
     */
    fun cancelSession(sessionId: String): CollaborationSession? {
        val session = sessions[sessionId] ?: return null
        val cancelled = session.withStatus(SessionStatus.CANCELLED)
        sessions[sessionId] = cancelled
        return cancelled
    }

    // =========================================================================
    // Turn Management
    // =========================================================================

    /**
     * Executes a single turn in the session.
     */
    suspend fun executeTurn(sessionId: String, userPrompt: String? = null): TurnResult {
        val session = sessions[sessionId] ?: return TurnResult.error("Session not found")
        if (!session.isActive) return TurnResult.error("Session is not active")
        if (session.hasReachedMaxTurns) return TurnResult.error("Max turns reached")

        val participant = session.getCurrentParticipant()
            ?: return TurnResult.error("No current participant")
        val agent = participant.agent
            ?: return TurnResult.error("Participant has no agent assigned")

        // Build prompt for this turn
        val turnPrompt = buildTurnPrompt(session, participant, userPrompt)

        // Invoke the agent
        val response = agentInvoker(agent, turnPrompt, session)

        // Create message from response
        val message = CollaborationMessage.contribution(
            sessionId = sessionId,
            senderId = participant.id,
            senderRole = participant.role,
            content = response
        )

        // Update session
        val updatedSession = session
            .addMessage(message)
            .advanceTurn()

        sessions[sessionId] = updatedSession

        emitEvent(CollaborationEvent.MessageSent(
            sessionId,
            message.id,
            participant.role,
            message.type
        ))

        emitEvent(CollaborationEvent.TurnAdvanced(
            sessionId,
            updatedSession.currentTurn,
            updatedSession.getCurrentParticipant()?.role ?: participant.role
        ))

        return TurnResult.success(message, updatedSession)
    }

    /**
     * Runs multiple turns until a condition is met.
     */
    suspend fun runUntil(
        sessionId: String,
        maxTurns: Int = 10,
        stopCondition: (CollaborationSession, CollaborationMessage) -> Boolean = { _, _ -> false }
    ): List<CollaborationMessage> {
        val messages = mutableListOf<CollaborationMessage>()
        var turns = 0

        while (turns < maxTurns) {
            val result = executeTurn(sessionId)
            if (!result.success) break

            val message = result.message
            if (message != null) {
                messages.add(message)
                val session = sessions[sessionId] ?: break
                if (stopCondition(session, message)) break
            }

            turns++
        }

        return messages
    }

    /**
     * Runs a complete round (all participants get one turn).
     */
    suspend fun runRound(sessionId: String): List<CollaborationMessage> {
        val session = sessions[sessionId] ?: return emptyList()
        val participantCount = session.participants.size

        return runUntil(sessionId, maxTurns = participantCount) { _, _ -> false }
    }

    // =========================================================================
    // Protocol Execution
    // =========================================================================

    /**
     * Executes the session according to its protocol.
     */
    suspend fun executeSession(sessionId: String, maxRounds: Int = 3): CollaborationResult? {
        val session = startSession(sessionId) ?: return null

        when (session.protocol) {
            CollaborationProtocol.ROUND_ROBIN -> executeRoundRobin(sessionId, maxRounds)
            CollaborationProtocol.DEBATE -> executeDebate(sessionId, maxRounds)
            CollaborationProtocol.CONSENSUS -> executeConsensus(sessionId, maxRounds)
            CollaborationProtocol.BROADCAST -> executeBroadcast(sessionId)
            CollaborationProtocol.LEADER_FOLLOWER -> executeLeaderFollower(sessionId, maxRounds)
            CollaborationProtocol.VOTING -> executeVoting(sessionId)
            CollaborationProtocol.FREE_FORM -> executeFreeForm(sessionId, maxRounds * session.participants.size)
        }

        return endSession(sessionId)
    }

    private suspend fun executeRoundRobin(sessionId: String, rounds: Int) {
        val session = sessions[sessionId] ?: return
        val totalTurns = rounds * session.participants.size

        runUntil(sessionId, maxTurns = totalTurns) { _, _ -> false }
    }

    private suspend fun executeDebate(sessionId: String, rounds: Int) {
        val session = sessions[sessionId] ?: return
        val totalTurns = rounds * 2 // Two participants debating

        runUntil(sessionId, maxTurns = totalTurns) { s, msg ->
            // Stop if agreement is reached
            msg.content.contains("agree", ignoreCase = true) &&
            s.messages.size >= 4 // Minimum 2 rounds
        }
    }

    private suspend fun executeConsensus(sessionId: String, maxRounds: Int) {
        val session = sessions[sessionId] ?: return

        // First round: Collect proposals
        runRound(sessionId)

        // Get or create consensus state
        val proposal = sessions[sessionId]?.messages?.lastOrNull()?.content ?: return
        val state = ConsensusState(
            proposalId = sessions[sessionId]?.messages?.lastOrNull()?.id ?: "",
            proposal = proposal
        )
        consensusStates[sessionId] = state

        // Subsequent rounds: Vote and refine
        for (round in 1 until maxRounds) {
            runRound(sessionId)

            val currentSession = sessions[sessionId] ?: break
            val currentState = consensusStates[sessionId] ?: break

            // Check for consensus
            if (currentState.hasConsensus()) {
                val updated = currentSession.withStatus(SessionStatus.CONSENSUS_REACHED)
                sessions[sessionId] = updated
                emitEvent(CollaborationEvent.ConsensusReached(
                    sessionId,
                    proposal,
                    currentState.approvalPercentage
                ))
                break
            }
        }
    }

    private suspend fun executeBroadcast(sessionId: String) {
        // In broadcast mode, execute all participants in one "turn"
        val session = sessions[sessionId] ?: return

        for (participant in session.participants) {
            val agent = participant.agent ?: continue
            val prompt = buildTurnPrompt(session, participant, null)
            val response = agentInvoker(agent, prompt, session)

            val message = CollaborationMessage.contribution(
                sessionId = sessionId,
                senderId = participant.id,
                senderRole = participant.role,
                content = response
            )

            val updated = sessions[sessionId]?.addMessage(message) ?: continue
            sessions[sessionId] = updated
        }
    }

    private suspend fun executeLeaderFollower(sessionId: String, maxRounds: Int) {
        val session = sessions[sessionId] ?: return
        val leader = session.participants.firstOrNull() ?: return

        // Leader speaks first
        executeTurn(sessionId)

        // Followers respond
        for (round in 1..maxRounds) {
            for (i in 1 until session.participants.size) {
                executeTurn(sessionId)
            }
            // Leader summarizes/redirects
            executeTurn(sessionId)
        }
    }

    private suspend fun executeVoting(sessionId: String) {
        // Collect proposals
        runRound(sessionId)

        // Voting round
        val session = sessions[sessionId] ?: return
        val proposals = session.messages.filter { it.type == MessageType.PROPOSAL }

        if (proposals.isEmpty()) return

        // Each participant votes on first proposal
        val proposal = proposals.first()

        for (participant in session.participants.drop(1)) {
            val voteMessage = CollaborationMessage.vote(
                sessionId = sessionId,
                senderId = participant.id,
                senderRole = participant.role,
                proposalId = proposal.id,
                approve = true // In real implementation, agent would decide
            )

            val updated = sessions[sessionId]?.addMessage(voteMessage) ?: continue
            sessions[sessionId] = updated
        }
    }

    private suspend fun executeFreeForm(sessionId: String, maxMessages: Int) {
        runUntil(sessionId, maxTurns = maxMessages) { _, _ -> false }
    }

    // =========================================================================
    // Messaging
    // =========================================================================

    /**
     * Sends a message to the session.
     */
    fun sendMessage(
        sessionId: String,
        senderId: String,
        senderRole: AgentRole,
        type: MessageType,
        content: String
    ): CollaborationMessage? {
        val session = sessions[sessionId] ?: return null

        val message = CollaborationMessage(
            sessionId = sessionId,
            senderId = senderId,
            senderRole = senderRole,
            type = type,
            content = content
        )

        val updated = session.addMessage(message)
        sessions[sessionId] = updated

        emitEvent(CollaborationEvent.MessageSent(sessionId, message.id, senderRole, type))

        return message
    }

    /**
     * Records a decision.
     */
    fun recordDecision(
        sessionId: String,
        description: String,
        rationale: String,
        madeBy: AgentRole
    ): Decision? {
        val session = sessions[sessionId] ?: return null

        val decision = Decision(
            description = description,
            rationale = rationale,
            madeBy = madeBy
        )

        val updatedContext = session.sharedContext.withDecision(decision)
        val updated = session.withContext(updatedContext)
        sessions[sessionId] = updated

        emitEvent(CollaborationEvent.DecisionMade(sessionId, description, madeBy))

        return decision
    }

    /**
     * Records a vote on a proposal.
     */
    fun recordVote(
        sessionId: String,
        participantId: String,
        proposalId: String,
        approve: Boolean,
        reason: String? = null
    ): ConsensusState? {
        val session = sessions[sessionId] ?: return null
        var state = consensusStates[sessionId] ?: return null

        state = state.recordVote(participantId, approve, reason)
        state = state.updateStatus(session.participants.size)

        consensusStates[sessionId] = state

        if (state.status == ConsensusStatus.ACCEPTED) {
            emitEvent(CollaborationEvent.ConsensusReached(
                sessionId,
                state.proposal,
                state.approvalPercentage
            ))
        }

        return state
    }

    // =========================================================================
    // Context Management
    // =========================================================================

    /**
     * Adds an artifact to shared context.
     */
    fun addArtifact(sessionId: String, name: String, content: String): SharedContext? {
        val session = sessions[sessionId] ?: return null
        val updated = session.sharedContext.withArtifact(name, content)
        sessions[sessionId] = session.withContext(updated)
        return updated
    }

    /**
     * Adds a fact to shared context.
     */
    fun addFact(sessionId: String, fact: String): SharedContext? {
        val session = sessions[sessionId] ?: return null
        val updated = session.sharedContext.withFact(fact)
        sessions[sessionId] = session.withContext(updated)
        return updated
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun buildTurnPrompt(
        session: CollaborationSession,
        participant: Participant,
        userPrompt: String?
    ): String = buildString {
        appendLine("## Collaboration Session: ${session.name}")
        appendLine("**Goal:** ${session.goal}")
        appendLine("**Your Role:** ${participant.role.displayName}")
        appendLine("**Protocol:** ${session.protocol.displayName}")
        appendLine()

        // Recent messages
        if (session.messages.isNotEmpty()) {
            appendLine("### Recent Discussion")
            session.messages.takeLast(5).forEach { msg ->
                appendLine("**${msg.senderRole.displayName}:** ${msg.content.take(500)}")
            }
            appendLine()
        }

        // Shared context
        if (session.sharedContext.facts.isNotEmpty()) {
            appendLine("### Established Facts")
            session.sharedContext.facts.forEach { appendLine("- $it") }
            appendLine()
        }

        // User prompt if provided
        userPrompt?.let {
            appendLine("### User Input")
            appendLine(it)
            appendLine()
        }

        appendLine("Provide your contribution to this collaboration.")
    }

    private fun createDefaultAgent(role: AgentRole): SpecialistAgent =
        SpecialistAgent(
            role = role,
            systemPrompt = "You are a ${role.displayName}.",
            capabilities = AgentRole.defaultCapabilities(role)
        )

    private fun createResult(session: CollaborationSession, durationMs: Long): CollaborationResult {
        val contributions = session.participants.associate { participant ->
            participant.role to session.messages.count { it.senderId == participant.id }
        }

        return CollaborationResult(
            sessionId = session.id,
            goal = session.goal,
            success = session.status == SessionStatus.COMPLETED ||
                     session.status == SessionStatus.CONSENSUS_REACHED,
            outcome = summarizeOutcome(session),
            decisions = session.sharedContext.decisions,
            artifacts = session.sharedContext.artifacts,
            totalTurns = session.currentTurn,
            messageCount = session.messageCount,
            participantContributions = contributions,
            durationMs = durationMs
        )
    }

    private fun summarizeOutcome(session: CollaborationSession): String {
        val lastMessages = session.messages.takeLast(2)
        return when {
            session.status == SessionStatus.CONSENSUS_REACHED -> "Consensus reached"
            session.sharedContext.decisions.isNotEmpty() ->
                "Decided: ${session.sharedContext.decisions.last().description}"
            lastMessages.isNotEmpty() ->
                lastMessages.last().content.take(200)
            else -> "Session completed"
        }
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets collaboration statistics.
     */
    fun getStats(): CollaborationStats {
        val allSessions = sessions.values.toList()

        return CollaborationStats(
            totalSessions = allSessions.size,
            activeSessions = allSessions.count { it.isActive },
            completedSessions = allSessions.count { it.status == SessionStatus.COMPLETED },
            totalMessages = allSessions.sumOf { it.messageCount },
            totalDecisions = allSessions.sumOf { it.sharedContext.decisions.size },
            byProtocol = allSessions.groupBy { it.protocol }.mapValues { it.value.size }
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (CollaborationEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (CollaborationEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: CollaborationEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Clears all sessions.
     */
    fun clearSessions() {
        sessions.clear()
        consensusStates.clear()
    }
}

/**
 * Result of a single turn.
 */
data class TurnResult(
    val success: Boolean,
    val message: CollaborationMessage?,
    val session: CollaborationSession?,
    val error: String? = null
) {
    companion object {
        fun success(message: CollaborationMessage, session: CollaborationSession) =
            TurnResult(true, message, session)

        fun error(reason: String) =
            TurnResult(false, null, null, reason)
    }
}

/**
 * Collaboration statistics.
 */
data class CollaborationStats(
    val totalSessions: Int,
    val activeSessions: Int,
    val completedSessions: Int,
    val totalMessages: Int,
    val totalDecisions: Int,
    val byProtocol: Map<CollaborationProtocol, Int>
)
