package com.sidekick.agent.specialists

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Specialist Service
 *
 * Service for managing and invoking specialist agents.
 * Part of Sidekick v0.9.2 Specialist Agents feature.
 *
 * ## Features
 *
 * - Create and manage specialists for each role
 * - Invoke specialists with prompts and context
 * - Track invocation history and statistics
 * - Support delegation between specialists
 * - Emit events for monitoring
 *
 * @since 0.9.2
 */
class SpecialistService(
    private val projectPath: String = "",
    private val llmInvoker: suspend (SpecialistAgent, String, String?) -> String = { _, prompt, _ -> prompt }
) {
    private val specialists = ConcurrentHashMap<AgentRole, SpecialistAgent>()
    private val requestHistory = CopyOnWriteArrayList<SpecialistRequest>()
    private val responseHistory = CopyOnWriteArrayList<AgentResponse>()
    private val eventListeners = CopyOnWriteArrayList<(SpecialistEvent) -> Unit>()

    init {
        // Initialize all specialists
        AgentRole.entries.forEach { role ->
            specialists[role] = createSpecialist(role)
        }
    }

    // =========================================================================
    // Specialist Creation
    // =========================================================================

    /**
     * Creates a specialist for a role.
     */
    private fun createSpecialist(role: AgentRole): SpecialistAgent {
        return SpecialistAgent(
            role = role,
            systemPrompt = SpecialistPrompts.forRole(role),
            capabilities = AgentRole.defaultCapabilities(role)
        )
    }

    /**
     * Creates a custom specialist.
     */
    fun createCustomSpecialist(
        role: AgentRole,
        customPrompt: String? = null,
        additionalCapabilities: Set<Capability> = emptySet(),
        temperature: Float = 0.7f
    ): SpecialistAgent {
        val baseCapabilities = AgentRole.defaultCapabilities(role)
        return SpecialistAgent(
            role = role,
            systemPrompt = customPrompt ?: SpecialistPrompts.forRole(role),
            capabilities = baseCapabilities + additionalCapabilities,
            temperature = temperature
        )
    }

    // =========================================================================
    // Specialist Retrieval
    // =========================================================================

    /**
     * Gets a specialist by role.
     */
    fun getSpecialist(role: AgentRole): SpecialistAgent {
        return specialists[role] ?: createSpecialist(role).also { specialists[role] = it }
    }

    /**
     * Gets all specialists.
     */
    fun getAllSpecialists(): List<SpecialistAgent> = specialists.values.toList()

    /**
     * Gets specialists that can perform a capability.
     */
    fun getSpecialistsWithCapability(capability: Capability): List<SpecialistAgent> {
        return specialists.values.filter { it.canPerform(capability) }
    }

    /**
     * Gets specialists by role type.
     */
    fun getPrimarySpecialists(): List<SpecialistAgent> {
        return AgentRole.PRIMARY_ROLES.mapNotNull { specialists[it] }
    }

    fun getSupportingSpecialists(): List<SpecialistAgent> {
        return AgentRole.SUPPORTING_ROLES.mapNotNull { specialists[it] }
    }

    // =========================================================================
    // Specialist Invocation
    // =========================================================================

    /**
     * Invokes a specialist with a prompt.
     */
    suspend fun invoke(role: AgentRole, prompt: String, context: String? = null): AgentResponse {
        val specialist = getSpecialist(role)
        val request = SpecialistRequest(
            agentId = specialist.id,
            role = role,
            prompt = prompt,
            context = context
        )

        return invokeWithRequest(specialist, request)
    }

    /**
     * Invokes a specialist with a full request.
     */
    suspend fun invokeWithRequest(specialist: SpecialistAgent, request: SpecialistRequest): AgentResponse {
        requestHistory.add(request)
        emitEvent(SpecialistEvent.AgentInvoked(specialist.id, specialist.role, request.id, request.prompt))

        val startTime = System.currentTimeMillis()

        return try {
            // Build full prompt with context
            val fullPrompt = buildPrompt(specialist, request)

            // Invoke LLM
            val content = llmInvoker(specialist, fullPrompt, request.context)

            val durationMs = System.currentTimeMillis() - startTime
            val response = parseResponse(request.id, specialist, content, durationMs)

            responseHistory.add(response)
            emitEvent(SpecialistEvent.AgentResponded(
                specialist.id,
                specialist.role,
                request.id,
                response.confidence,
                durationMs
            ))

            // Handle delegation if suggested
            if (response.suggestsDelegation && response.delegateTo != null) {
                emitEvent(SpecialistEvent.AgentDelegated(
                    specialist.id,
                    specialist.role,
                    response.delegateTo,
                    "Delegating to ${response.delegateTo.displayName}"
                ))
            }

            response
        } catch (e: Exception) {
            emitEvent(SpecialistEvent.AgentFailed(specialist.id, specialist.role, e.message ?: "Unknown error"))
            
            AgentResponse(
                requestId = request.id,
                agentId = specialist.id,
                role = specialist.role,
                content = "Error: ${e.message}",
                confidence = 0f,
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Invokes multiple specialists in sequence.
     */
    suspend fun invokeChain(
        roles: List<AgentRole>,
        initialPrompt: String,
        contextBuilder: (AgentResponse) -> String = { it.content }
    ): List<AgentResponse> {
        val responses = mutableListOf<AgentResponse>()
        var currentContext: String? = null

        for (role in roles) {
            val prompt = if (responses.isEmpty()) initialPrompt else initialPrompt
            val response = invoke(role, prompt, currentContext)
            responses.add(response)
            currentContext = contextBuilder(response)
        }

        return responses
    }

    /**
     * Invokes specialists in parallel.
     */
    suspend fun invokeParallel(
        roles: List<AgentRole>,
        prompt: String,
        context: String? = null
    ): Map<AgentRole, AgentResponse> {
        // Note: In real implementation, use coroutines for parallel execution
        return roles.associateWith { role ->
            invoke(role, prompt, context)
        }
    }

    // =========================================================================
    // Delegation
    // =========================================================================

    /**
     * Delegates a task from one specialist to another.
     */
    suspend fun delegate(
        fromRole: AgentRole,
        toRole: AgentRole,
        prompt: String,
        originalContext: String? = null
    ): AgentResponse {
        val fromSpecialist = getSpecialist(fromRole)

        // Create delegation context
        val delegationContext = buildString {
            appendLine("## Delegation from ${fromRole.displayName}")
            originalContext?.let {
                appendLine()
                appendLine("### Original Context")
                appendLine(it)
            }
        }

        emitEvent(SpecialistEvent.AgentDelegated(
            fromSpecialist.id,
            fromRole,
            toRole,
            "Delegating task to ${toRole.displayName}"
        ))

        return invoke(toRole, prompt, delegationContext)
    }

    /**
     * Suggests the best specialist for a task.
     */
    fun suggestSpecialist(taskDescription: String): AgentRole {
        val description = taskDescription.lowercase()

        return when {
            description.containsAny("design", "architecture", "structure", "module") -> AgentRole.ARCHITECT
            description.containsAny("implement", "code", "write", "create", "add") -> AgentRole.IMPLEMENTER
            description.containsAny("review", "check", "approve", "feedback") -> AgentRole.REVIEWER
            description.containsAny("test", "coverage", "unit", "integration") -> AgentRole.TESTER
            description.containsAny("document", "readme", "comment", "explain") -> AgentRole.DOCUMENTER
            description.containsAny("debug", "fix", "error", "bug", "issue") -> AgentRole.DEBUGGER
            description.containsAny("optimize", "performance", "speed", "memory") -> AgentRole.OPTIMIZER
            description.containsAny("security", "vulnerability", "auth", "injection") -> AgentRole.SECURITY
            else -> AgentRole.IMPLEMENTER
        }
    }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { this.contains(it) }

    // =========================================================================
    // Review Loop
    // =========================================================================

    /**
     * Runs an implement-review loop until approval.
     */
    suspend fun implementReviewLoop(
        prompt: String,
        maxIterations: Int = 3,
        context: String? = null
    ): ReviewLoopResult {
        var currentContent = ""
        var iteration = 0

        // Initial implementation
        val implResponse = invoke(AgentRole.IMPLEMENTER, prompt, context)
        currentContent = implResponse.content

        while (iteration < maxIterations) {
            // Review the implementation
            val reviewResponse = invoke(
                AgentRole.REVIEWER,
                "Review this implementation:\n\n$currentContent",
                context
            )

            // Check if approved
            val feedback = parseReviewFeedback(reviewResponse)
            if (feedback.approved || !feedback.hasCriticalIssues) {
                return ReviewLoopResult(
                    finalContent = currentContent,
                    iterations = iteration + 1,
                    approved = feedback.approved,
                    feedback = feedback
                )
            }

            // Request revisions
            val revisionPrompt = buildString {
                appendLine("Address the following review feedback:")
                appendLine()
                feedback.items.filter { it.isBlocking || it.severity == ReviewSeverity.CRITICAL }
                    .forEach { item ->
                        appendLine("- [${item.severity}] ${item.description}")
                        item.suggestion?.let { appendLine("  Suggestion: $it") }
                    }
                appendLine()
                appendLine("Original code:")
                appendLine(currentContent)
            }

            val revisionResponse = invoke(AgentRole.IMPLEMENTER, revisionPrompt, context)
            currentContent = revisionResponse.content
            iteration++
        }

        return ReviewLoopResult(
            finalContent = currentContent,
            iterations = iteration,
            approved = false,
            feedback = null
        )
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun buildPrompt(specialist: SpecialistAgent, request: SpecialistRequest): String {
        return buildString {
            appendLine(request.prompt)

            if (request.referencedFiles.isNotEmpty()) {
                appendLine()
                appendLine("## Referenced Files")
                request.referencedFiles.forEach { file ->
                    appendLine("- $file")
                }
            }
        }
    }

    private fun parseResponse(
        requestId: String,
        specialist: SpecialistAgent,
        content: String,
        durationMs: Long
    ): AgentResponse {
        // Parse response for structured elements
        val suggestedActions = extractSuggestedActions(content)
        val artifacts = extractArtifacts(content)
        val delegateTo = extractDelegation(content)
        val confidence = estimateConfidence(content)

        return AgentResponse(
            requestId = requestId,
            agentId = specialist.id,
            role = specialist.role,
            content = content,
            confidence = confidence,
            suggestedActions = suggestedActions,
            delegateTo = delegateTo,
            artifacts = artifacts,
            durationMs = durationMs
        )
    }

    private fun extractSuggestedActions(content: String): List<SuggestedAction> {
        // Simple extraction - in real implementation, use structured output parsing
        val actions = mutableListOf<SuggestedAction>()

        if (content.contains("TODO:", ignoreCase = true) || content.contains("Action:", ignoreCase = true)) {
            actions.add(SuggestedAction("follow_up", "Review suggested actions in response", 5))
        }

        return actions
    }

    private fun extractArtifacts(content: String): List<ResponseArtifact> {
        // Extract code blocks as artifacts
        val artifacts = mutableListOf<ResponseArtifact>()
        val codeBlockPattern = Regex("```(\\w+)?\\n([\\s\\S]*?)```")

        codeBlockPattern.findAll(content).forEach { match ->
            val language = match.groupValues[1].ifEmpty { "text" }
            val code = match.groupValues[2]
            artifacts.add(ResponseArtifact(
                name = "Code Block",
                type = ArtifactType.CODE,
                content = code.trim(),
                language = language
            ))
        }

        return artifacts
    }

    private fun extractDelegation(content: String): AgentRole? {
        val delegationPatterns = mapOf(
            AgentRole.IMPLEMENTER to listOf("delegate to implementer", "implementer should"),
            AgentRole.REVIEWER to listOf("needs review", "delegate to reviewer"),
            AgentRole.TESTER to listOf("needs tests", "delegate to tester"),
            AgentRole.SECURITY to listOf("security review", "delegate to security")
        )

        val lowerContent = content.lowercase()
        return delegationPatterns.entries.firstOrNull { (_, patterns) ->
            patterns.any { lowerContent.contains(it) }
        }?.key
    }

    private fun estimateConfidence(content: String): Float {
        // Estimate confidence based on content characteristics
        val uncertainPhrases = listOf("might", "could", "perhaps", "not sure", "unclear")
        val confidentPhrases = listOf("definitely", "certainly", "clearly", "obviously")

        val lowerContent = content.lowercase()
        val uncertainCount = uncertainPhrases.count { lowerContent.contains(it) }
        val confidentCount = confidentPhrases.count { lowerContent.contains(it) }

        return when {
            confidentCount > uncertainCount -> 0.9f
            uncertainCount > confidentCount * 2 -> 0.5f
            else -> 0.7f
        }
    }

    private fun parseReviewFeedback(response: AgentResponse): ReviewFeedback {
        val content = response.content.lowercase()
        val approved = content.contains("approved") && !content.contains("not approved")

        // Simple parsing - in real implementation, use structured output
        val items = mutableListOf<ReviewItem>()

        if (content.contains("critical") || content.contains("must fix")) {
            items.add(ReviewItem(
                severity = ReviewSeverity.CRITICAL,
                category = ReviewCategory.BUG,
                description = "Critical issue found",
                isBlocking = true
            ))
        }

        return ReviewFeedback(
            items = items,
            overallAssessment = response.content.take(200),
            approved = approved,
            confidence = response.confidence
        )
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets invocation statistics.
     */
    fun getStats(): SpecialistStats {
        val byRole = responseHistory.groupBy { it.role }

        return SpecialistStats(
            totalInvocations = responseHistory.size,
            invocationsByRole = byRole.mapValues { it.value.size },
            averageConfidence = if (responseHistory.isNotEmpty())
                responseHistory.map { it.confidence }.average().toFloat()
            else 0f,
            averageDurationMs = if (responseHistory.isNotEmpty())
                responseHistory.map { it.durationMs }.average().toLong()
            else 0,
            delegationCount = responseHistory.count { it.suggestsDelegation }
        )
    }

    /**
     * Gets history for a role.
     */
    fun getHistoryForRole(role: AgentRole): List<AgentResponse> {
        return responseHistory.filter { it.role == role }
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (SpecialistEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (SpecialistEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: SpecialistEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Clears history.
     */
    fun clearHistory() {
        requestHistory.clear()
        responseHistory.clear()
    }
}

/**
 * Statistics about specialist invocations.
 */
data class SpecialistStats(
    val totalInvocations: Int,
    val invocationsByRole: Map<AgentRole, Int>,
    val averageConfidence: Float,
    val averageDurationMs: Long,
    val delegationCount: Int
) {
    val delegationRate: Float
        get() = if (totalInvocations > 0) delegationCount.toFloat() / totalInvocations else 0f
}

/**
 * Result of an implement-review loop.
 */
data class ReviewLoopResult(
    val finalContent: String,
    val iterations: Int,
    val approved: Boolean,
    val feedback: ReviewFeedback?
)
