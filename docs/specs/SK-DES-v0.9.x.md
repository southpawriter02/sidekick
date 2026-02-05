# Sidekick v0.9.x – Advanced Agent Capabilities

> **Phase Goal:** Multi-agent systems, planning intelligence, self-correction, and personalization  
> **Building On:** v0.8.x LM Studio Integration & Local Coding Agent

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.9.1 | Planning Agent | Task decomposition and strategy |
| v0.9.2 | Specialist Agents | Role-based sub-agents |
| v0.9.3 | Agent Collaboration | Multi-agent coordination |
| v0.9.4 | Self-Correction | Error detection and recovery |
| v0.9.5 | User Preferences | Style learning and adaptation |
| v0.9.6 | Reflection & Critique | Self-evaluation loops |

---

## v0.9.1 — Planning Agent

### v0.9.1a — PlanningModels

```kotlin
package com.sidekick.agent.planning

import java.time.Instant

/**
 * A plan for completing a complex task.
 */
data class TaskPlan(
    val id: String,
    val goal: String,
    val analysis: ProblemAnalysis,
    val strategy: PlanStrategy,
    val steps: List<PlanStep>,
    val dependencies: Map<String, List<String>>, // stepId -> dependsOn
    val estimatedEffort: EffortEstimate,
    val risks: List<PlanRisk>,
    val status: PlanStatus = PlanStatus.DRAFT,
    val createdAt: Instant = Instant.now()
)

/**
 * Analysis of the problem space.
 */
data class ProblemAnalysis(
    val scope: Scope,
    val complexity: Complexity,
    val affectedAreas: List<String>,
    val existingPatterns: List<String>,
    val constraints: List<String>,
    val unknowns: List<String>
)

enum class Scope { SINGLE_FILE, MULTI_FILE, MODULE, CROSS_MODULE, PROJECT_WIDE }
enum class Complexity { TRIVIAL, SIMPLE, MODERATE, COMPLEX, VERY_COMPLEX }

/**
 * Strategy for approaching the task.
 */
data class PlanStrategy(
    val approach: Approach,
    val reasoning: String,
    val alternatives: List<AlternativeApproach>
)

enum class Approach {
    INCREMENTAL,      // Small changes, verify each
    BIG_BANG,         // All at once
    SPIKE_FIRST,      // Prototype then implement
    TEST_FIRST,       // TDD style
    PARALLEL          // Independent tracks
}

data class AlternativeApproach(
    val approach: Approach,
    val tradeoffs: String,
    val whenToUse: String
)

/**
 * A step in the plan.
 */
data class PlanStep(
    val id: String,
    val order: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedTokens: Int,
    val canParallelize: Boolean,
    val rollbackStrategy: String?,
    val verificationCriteria: String,
    val status: StepStatus = StepStatus.PENDING
)

enum class StepType {
    RESEARCH, DESIGN, IMPLEMENT, TEST, REFACTOR, DOCUMENT, VERIFY, CLEANUP
}

enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, BLOCKED }
enum class PlanStatus { DRAFT, APPROVED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

/**
 * Effort estimate.
 */
data class EffortEstimate(
    val totalSteps: Int,
    val estimatedMinutes: Int,
    val confidence: Float,
    val breakdown: Map<StepType, Int>
)

/**
 * Identified risk.
 */
data class PlanRisk(
    val description: String,
    val probability: Float,
    val impact: Impact,
    val mitigation: String
)

enum class Impact { LOW, MEDIUM, HIGH, CRITICAL }
```

---

### v0.9.1b — PlanningService

```kotlin
package com.sidekick.agent.planning

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.agent.index.CodeIndexService

@Service(Service.Level.PROJECT)
class PlanningService(private val project: Project) {

    companion object {
        fun getInstance(project: Project) = project.getService(PlanningService::class.java)

        private val PLANNING_PROMPT = """
            You are a senior software architect planning a coding task. Analyze the request and create
            a detailed execution plan. Consider:
            
            1. **Scope Analysis**: What files/modules are affected?
            2. **Complexity Assessment**: How difficult is this task?
            3. **Strategy Selection**: What approach minimizes risk?
            4. **Step Breakdown**: What are the concrete steps?
            5. **Dependencies**: What order must steps execute?
            6. **Risks**: What could go wrong? How to mitigate?
            
            Output a structured JSON plan following the TaskPlan schema.
        """.trimIndent()
    }

    /**
     * Creates a plan for a task.
     */
    suspend fun createPlan(goal: String, context: PlanningContext): TaskPlan {
        val provider = ProviderManager.getInstance().getActiveProvider()
        val codeIndex = CodeIndexService.getInstance(project)
        
        // Gather relevant context
        val relevantCode = codeIndex.semanticSearch(goal, limit = 5)
        
        val prompt = buildString {
            appendLine(PLANNING_PROMPT)
            appendLine()
            appendLine("## Task Goal")
            appendLine(goal)
            appendLine()
            appendLine("## Project Context")
            appendLine("- Project: ${context.projectName}")
            appendLine("- Languages: ${context.languages.joinToString()}")
            appendLine("- Framework: ${context.framework ?: "Unknown"}")
            appendLine()
            if (relevantCode.isNotEmpty()) {
                appendLine("## Relevant Code")
                relevantCode.forEach { result ->
                    appendLine("### ${result.chunk.filePath}")
                    appendLine("```")
                    appendLine(result.chunk.content.take(500))
                    appendLine("```")
                }
            }
        }

        val response = provider.chat(
            com.sidekick.llm.provider.UnifiedChatRequest(
                model = "",
                messages = listOf(
                    com.sidekick.llm.provider.UnifiedMessage(
                        com.sidekick.llm.provider.MessageRole.USER,
                        prompt
                    )
                )
            )
        )

        return parsePlanFromResponse(response.content)
    }

    /**
     * Refines a plan based on feedback.
     */
    suspend fun refinePlan(plan: TaskPlan, feedback: String): TaskPlan {
        // Re-plan with feedback incorporated
        return plan
    }

    /**
     * Validates a plan is executable.
     */
    fun validatePlan(plan: TaskPlan): PlanValidation {
        val issues = mutableListOf<String>()
        
        // Check for circular dependencies
        if (hasCircularDependencies(plan.dependencies)) {
            issues.add("Plan contains circular dependencies")
        }
        
        // Check all dependencies exist
        val stepIds = plan.steps.map { it.id }.toSet()
        plan.dependencies.values.flatten().forEach { dep ->
            if (dep !in stepIds) {
                issues.add("Unknown dependency: $dep")
            }
        }
        
        return PlanValidation(issues.isEmpty(), issues)
    }

    private fun parsePlanFromResponse(response: String): TaskPlan {
        // Parse JSON response into TaskPlan
        TODO()
    }

    private fun hasCircularDependencies(deps: Map<String, List<String>>): Boolean {
        // Topological sort check
        return false
    }
}

data class PlanningContext(
    val projectName: String,
    val languages: List<String>,
    val framework: String?,
    val activeFiles: List<String>
)

data class PlanValidation(val valid: Boolean, val issues: List<String>)
```

---

## v0.9.2 — Specialist Agents

### v0.9.2a — SpecialistModels

```kotlin
package com.sidekick.agent.specialists

/**
 * A specialist agent with focused expertise.
 */
data class SpecialistAgent(
    val id: String,
    val role: AgentRole,
    val systemPrompt: String,
    val capabilities: Set<Capability>,
    val preferredModel: String?,
    val temperature: Float = 0.7f
)

enum class AgentRole(val displayName: String, val icon: String) {
    ARCHITECT("Architect", "🏗️"),
    IMPLEMENTER("Implementer", "⚙️"),
    REVIEWER("Reviewer", "👁️"),
    TESTER("Tester", "🧪"),
    DOCUMENTER("Documenter", "📝"),
    DEBUGGER("Debugger", "🐛"),
    OPTIMIZER("Optimizer", "⚡"),
    SECURITY("Security Analyst", "🔒")
}

enum class Capability {
    READ_CODE, WRITE_CODE, RUN_TESTS, ANALYZE_AST, SEARCH_CODEBASE,
    EXECUTE_COMMANDS, MODIFY_CONFIG, CREATE_FILES, DELETE_FILES,
    ACCESS_MEMORY, DELEGATE_TASKS
}

/**
 * Agent response with metadata.
 */
data class AgentResponse(
    val agentId: String,
    val role: AgentRole,
    val content: String,
    val confidence: Float,
    val reasoning: String?,
    val suggestedActions: List<SuggestedAction>,
    val delegateTo: AgentRole?
)

data class SuggestedAction(
    val action: String,
    val description: String,
    val priority: Int
)
```

---

### v0.9.2b — SpecialistPrompts

```kotlin
package com.sidekick.agent.specialists

/**
 * System prompts for each specialist.
 */
object SpecialistPrompts {

    val ARCHITECT = """
        You are a senior software architect. Your responsibilities:
        - Analyze system structure and design patterns
        - Identify architectural concerns and technical debt
        - Propose high-level solutions and module boundaries
        - Ensure consistency with existing architecture
        - Consider scalability, maintainability, and performance
        
        You focus on the "what" and "why", not the implementation details.
        Delegate implementation to the Implementer agent.
    """.trimIndent()

    val IMPLEMENTER = """
        You are an expert software developer. Your responsibilities:
        - Write clean, idiomatic code following project conventions
        - Implement features based on architectural decisions
        - Make precise, targeted code changes
        - Follow SOLID principles and best practices
        - Ensure code is testable and well-structured
        
        Focus on writing correct, maintainable code. Request clarification
        from the Architect if design decisions are unclear.
    """.trimIndent()

    val REVIEWER = """
        You are a meticulous code reviewer. Your responsibilities:
        - Review code for correctness, style, and best practices
        - Identify potential bugs, edge cases, and security issues
        - Suggest improvements and optimizations
        - Verify code matches requirements and design
        - Check for test coverage gaps
        
        Be constructive and specific. Explain why issues matter.
        Categorize feedback as: Critical, Important, Suggestion, Nitpick.
    """.trimIndent()

    val TESTER = """
        You are a quality assurance engineer. Your responsibilities:
        - Write comprehensive unit and integration tests
        - Identify edge cases and boundary conditions
        - Ensure adequate test coverage
        - Create test fixtures and mocks
        - Verify tests are deterministic and fast
        
        Follow the project's testing patterns. Write tests that document
        expected behavior and catch regressions.
    """.trimIndent()

    val DEBUGGER = """
        You are an expert debugger. Your responsibilities:
        - Analyze error messages and stack traces
        - Identify root causes of bugs
        - Form hypotheses and test them systematically
        - Explain the debugging process clearly
        - Suggest fixes with minimal side effects
        
        Think like a detective. Gather evidence, form hypotheses,
        and verify them before proposing fixes.
    """.trimIndent()

    val SECURITY = """
        You are a security analyst. Your responsibilities:
        - Identify security vulnerabilities (OWASP Top 10)
        - Review authentication and authorization logic
        - Check for injection, XSS, CSRF vulnerabilities
        - Ensure proper input validation and sanitization
        - Verify secure handling of secrets and credentials
        
        Think like an attacker. Consider how code could be exploited.
        Prioritize findings by severity and exploitability.
    """.trimIndent()

    fun forRole(role: AgentRole): String = when (role) {
        AgentRole.ARCHITECT -> ARCHITECT
        AgentRole.IMPLEMENTER -> IMPLEMENTER
        AgentRole.REVIEWER -> REVIEWER
        AgentRole.TESTER -> TESTER
        AgentRole.DEBUGGER -> DEBUGGER
        AgentRole.SECURITY -> SECURITY
        AgentRole.DOCUMENTER -> "You are a technical writer..."
        AgentRole.OPTIMIZER -> "You are a performance engineer..."
    }
}
```

---

### v0.9.2c — SpecialistService

```kotlin
package com.sidekick.agent.specialists

import com.intellij.openapi.components.Service
import com.sidekick.llm.provider.ProviderManager

@Service(Service.Level.APP)
class SpecialistService {

    private val specialists = AgentRole.entries.map { role ->
        SpecialistAgent(
            id = role.name.lowercase(),
            role = role,
            systemPrompt = SpecialistPrompts.forRole(role),
            capabilities = getCapabilitiesForRole(role),
            preferredModel = null
        )
    }

    companion object {
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication().getService(SpecialistService::class.java)
    }

    /**
     * Gets a specialist by role.
     */
    fun getSpecialist(role: AgentRole): SpecialistAgent {
        return specialists.first { it.role == role }
    }

    /**
     * Invokes a specialist.
     */
    suspend fun invoke(role: AgentRole, prompt: String, context: String? = null): AgentResponse {
        val specialist = getSpecialist(role)
        val provider = ProviderManager.getInstance().getActiveProvider()

        val messages = mutableListOf(
            com.sidekick.llm.provider.UnifiedMessage(
                com.sidekick.llm.provider.MessageRole.SYSTEM,
                specialist.systemPrompt
            )
        )
        
        context?.let {
            messages.add(com.sidekick.llm.provider.UnifiedMessage(
                com.sidekick.llm.provider.MessageRole.USER,
                "Context:\n$it"
            ))
        }
        
        messages.add(com.sidekick.llm.provider.UnifiedMessage(
            com.sidekick.llm.provider.MessageRole.USER,
            prompt
        ))

        val response = provider.chat(
            com.sidekick.llm.provider.UnifiedChatRequest(
                model = specialist.preferredModel ?: "",
                messages = messages,
                temperature = specialist.temperature
            )
        )

        return AgentResponse(
            agentId = specialist.id,
            role = role,
            content = response.content,
            confidence = 0.8f,
            reasoning = null,
            suggestedActions = emptyList(),
            delegateTo = null
        )
    }

    private fun getCapabilitiesForRole(role: AgentRole): Set<Capability> = when (role) {
        AgentRole.ARCHITECT -> setOf(Capability.READ_CODE, Capability.ANALYZE_AST, Capability.SEARCH_CODEBASE)
        AgentRole.IMPLEMENTER -> setOf(Capability.READ_CODE, Capability.WRITE_CODE, Capability.CREATE_FILES)
        AgentRole.REVIEWER -> setOf(Capability.READ_CODE, Capability.ANALYZE_AST)
        AgentRole.TESTER -> setOf(Capability.READ_CODE, Capability.WRITE_CODE, Capability.RUN_TESTS)
        AgentRole.DEBUGGER -> setOf(Capability.READ_CODE, Capability.EXECUTE_COMMANDS, Capability.SEARCH_CODEBASE)
        AgentRole.SECURITY -> setOf(Capability.READ_CODE, Capability.ANALYZE_AST, Capability.SEARCH_CODEBASE)
        else -> setOf(Capability.READ_CODE)
    }
}
```

---

## v0.9.3 — Agent Collaboration

### v0.9.3a — CollaborationModels

```kotlin
package com.sidekick.agent.collaboration

import com.sidekick.agent.specialists.AgentRole

/**
 * A collaborative session between agents.
 */
data class CollaborationSession(
    val id: String,
    val goal: String,
    val participants: List<AgentRole>,
    val coordinator: AgentRole,
    val messages: MutableList<CollaborationMessage> = mutableListOf(),
    val artifacts: MutableList<SessionArtifact> = mutableListOf(),
    val status: SessionStatus = SessionStatus.ACTIVE
)

data class CollaborationMessage(
    val id: String,
    val from: AgentRole,
    val to: AgentRole?,  // null = broadcast
    val type: MessageType,
    val content: String,
    val referencedArtifacts: List<String> = emptyList()
)

enum class MessageType {
    REQUEST, RESPONSE, HANDOFF, QUESTION, FEEDBACK, APPROVAL, REJECTION
}

data class SessionArtifact(
    val id: String,
    val type: ArtifactType,
    val name: String,
    val content: String,
    val createdBy: AgentRole,
    val version: Int
)

enum class ArtifactType { PLAN, CODE, TEST, DOCUMENTATION, REVIEW, DECISION }
enum class SessionStatus { ACTIVE, PAUSED, COMPLETED, FAILED }

/**
 * Coordination protocol.
 */
enum class CoordinationProtocol {
    SEQUENTIAL,     // One agent at a time
    PARALLEL,       // Independent work streams
    REVIEW_LOOP,    // Implement -> Review -> Revise
    CONSENSUS       // Agreement required
}
```

---

### v0.9.3b — CollaborationOrchestrator

```kotlin
package com.sidekick.agent.collaboration

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.agent.specialists.*
import com.sidekick.agent.planning.TaskPlan

@Service(Service.Level.PROJECT)
class CollaborationOrchestrator(private val project: Project) {

    companion object {
        fun getInstance(project: Project) = project.getService(CollaborationOrchestrator::class.java)
    }

    /**
     * Executes a plan using multiple agents.
     */
    suspend fun executePlanCollaboratively(plan: TaskPlan): CollaborationResult {
        val session = CollaborationSession(
            id = java.util.UUID.randomUUID().toString(),
            goal = plan.goal,
            participants = determineParticipants(plan),
            coordinator = AgentRole.ARCHITECT
        )

        val specialistService = SpecialistService.getInstance()

        // Phase 1: Architecture Review
        val archResponse = specialistService.invoke(
            AgentRole.ARCHITECT,
            "Review this plan and provide architectural guidance:\n${plan.goal}"
        )
        session.messages.add(createMessage(AgentRole.ARCHITECT, null, MessageType.RESPONSE, archResponse.content))

        // Phase 2: Implementation
        for (step in plan.steps.filter { it.type == com.sidekick.agent.planning.StepType.IMPLEMENT }) {
            val implResponse = specialistService.invoke(
                AgentRole.IMPLEMENTER,
                "Implement: ${step.description}",
                context = archResponse.content
            )
            session.messages.add(createMessage(AgentRole.IMPLEMENTER, null, MessageType.RESPONSE, implResponse.content))

            // Phase 3: Review
            val reviewResponse = specialistService.invoke(
                AgentRole.REVIEWER,
                "Review this implementation:\n${implResponse.content}"
            )
            session.messages.add(createMessage(AgentRole.REVIEWER, AgentRole.IMPLEMENTER, MessageType.FEEDBACK, reviewResponse.content))
        }

        return CollaborationResult(
            session = session,
            success = true,
            summary = "Completed ${plan.steps.size} steps with ${session.participants.size} agents"
        )
    }

    /**
     * Runs a review loop until approval.
     */
    suspend fun reviewLoop(code: String, maxIterations: Int = 3): ReviewLoopResult {
        val specialistService = SpecialistService.getInstance()
        var currentCode = code
        var iteration = 0

        while (iteration < maxIterations) {
            val review = specialistService.invoke(AgentRole.REVIEWER, "Review:\n$currentCode")
            
            if (review.content.contains("APPROVED") || review.confidence > 0.9f) {
                return ReviewLoopResult(currentCode, iteration + 1, true)
            }

            val revision = specialistService.invoke(
                AgentRole.IMPLEMENTER,
                "Address feedback:\n${review.content}\n\nOriginal code:\n$currentCode"
            )
            currentCode = revision.content
            iteration++
        }

        return ReviewLoopResult(currentCode, iteration, false)
    }

    private fun determineParticipants(plan: TaskPlan): List<AgentRole> {
        val roles = mutableSetOf<AgentRole>()
        roles.add(AgentRole.ARCHITECT)
        
        plan.steps.forEach { step ->
            when (step.type) {
                com.sidekick.agent.planning.StepType.IMPLEMENT -> roles.add(AgentRole.IMPLEMENTER)
                com.sidekick.agent.planning.StepType.TEST -> roles.add(AgentRole.TESTER)
                com.sidekick.agent.planning.StepType.DOCUMENT -> roles.add(AgentRole.DOCUMENTER)
                else -> {}
            }
        }
        
        roles.add(AgentRole.REVIEWER)
        return roles.toList()
    }

    private fun createMessage(from: AgentRole, to: AgentRole?, type: MessageType, content: String) =
        CollaborationMessage(java.util.UUID.randomUUID().toString(), from, to, type, content)
}

data class CollaborationResult(val session: CollaborationSession, val success: Boolean, val summary: String)
data class ReviewLoopResult(val finalCode: String, val iterations: Int, val approved: Boolean)
```

---

## v0.9.4 — Self-Correction

### v0.9.4a — CorrectionModels

```kotlin
package com.sidekick.agent.correction

/**
 * An error detected during execution.
 */
data class ExecutionError(
    val id: String,
    val type: ErrorType,
    val message: String,
    val context: ErrorContext,
    val stackTrace: String?,
    val severity: ErrorSeverity
)

enum class ErrorType {
    COMPILATION, RUNTIME, TEST_FAILURE, ASSERTION, TIMEOUT,
    TOOL_FAILURE, INVALID_OUTPUT, LOOP_DETECTED, RESOURCE_EXHAUSTED
}

enum class ErrorSeverity { WARNING, ERROR, CRITICAL }

data class ErrorContext(
    val stepId: String?,
    val action: String,
    val input: String,
    val expectedOutput: String?
)

/**
 * A correction attempt.
 */
data class CorrectionAttempt(
    val id: String,
    val error: ExecutionError,
    val strategy: CorrectionStrategy,
    val hypothesis: String,
    val actions: List<CorrectionAction>,
    val result: CorrectionResult?
)

enum class CorrectionStrategy {
    RETRY,              // Try again
    ROLLBACK,           // Undo and retry differently  
    DECOMPOSE,          // Break into smaller steps
    SEEK_HELP,          // Ask for clarification
    ALTERNATIVE_APPROACH, // Try different method
    SKIP                // Skip and continue
}

data class CorrectionAction(
    val type: ActionType,
    val description: String,
    val executed: Boolean
)

enum class ActionType { UNDO, MODIFY, REPLACE, ADD, REMOVE, VERIFY }

data class CorrectionResult(
    val success: Boolean,
    val newOutput: String?,
    val lessonsLearned: List<String>
)
```

---

### v0.9.4b — SelfCorrectionService

```kotlin
package com.sidekick.agent.correction

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.llm.provider.ProviderManager

@Service(Service.Level.PROJECT)
class SelfCorrectionService(private val project: Project) {

    companion object {
        fun getInstance(project: Project) = project.getService(SelfCorrectionService::class.java)

        private val CORRECTION_PROMPT = """
            An error occurred during task execution. Analyze the error and propose a correction:
            
            1. Identify the root cause
            2. Determine if this is recoverable
            3. Propose a correction strategy
            4. List specific actions to take
            5. Explain how to verify the fix
        """.trimIndent()
    }

    private val attemptHistory = mutableListOf<CorrectionAttempt>()
    private var consecutiveFailures = 0
    private val maxConsecutiveFailures = 3

    /**
     * Attempts to correct an error.
     */
    suspend fun attemptCorrection(error: ExecutionError): CorrectionAttempt {
        consecutiveFailures++
        
        if (consecutiveFailures > maxConsecutiveFailures) {
            return createSeekHelpAttempt(error)
        }

        // Check for loop detection
        if (isRepeatedError(error)) {
            return createAlternativeApproachAttempt(error)
        }

        val provider = ProviderManager.getInstance().getActiveProvider()
        val response = provider.chat(
            com.sidekick.llm.provider.UnifiedChatRequest(
                model = "",
                messages = listOf(
                    com.sidekick.llm.provider.UnifiedMessage(
                        com.sidekick.llm.provider.MessageRole.SYSTEM,
                        CORRECTION_PROMPT
                    ),
                    com.sidekick.llm.provider.UnifiedMessage(
                        com.sidekick.llm.provider.MessageRole.USER,
                        formatErrorForAnalysis(error)
                    )
                )
            )
        )

        val attempt = parseCorrectionAttempt(error, response.content)
        attemptHistory.add(attempt)
        return attempt
    }

    /**
     * Records a successful correction.
     */
    fun recordSuccess() {
        consecutiveFailures = 0
    }

    /**
     * Gets lessons learned from corrections.
     */
    fun getLessonsLearned(): List<String> {
        return attemptHistory
            .filter { it.result?.success == true }
            .flatMap { it.result?.lessonsLearned ?: emptyList() }
            .distinct()
    }

    private fun isRepeatedError(error: ExecutionError): Boolean {
        return attemptHistory.takeLast(3).count { 
            it.error.type == error.type && it.error.message == error.message 
        } >= 2
    }

    private fun createSeekHelpAttempt(error: ExecutionError) = CorrectionAttempt(
        id = java.util.UUID.randomUUID().toString(),
        error = error,
        strategy = CorrectionStrategy.SEEK_HELP,
        hypothesis = "Multiple correction attempts failed. User intervention required.",
        actions = emptyList(),
        result = null
    )

    private fun createAlternativeApproachAttempt(error: ExecutionError) = CorrectionAttempt(
        id = java.util.UUID.randomUUID().toString(),
        error = error,
        strategy = CorrectionStrategy.ALTERNATIVE_APPROACH,
        hypothesis = "Repeated error detected. Trying alternative approach.",
        actions = emptyList(),
        result = null
    )

    private fun formatErrorForAnalysis(error: ExecutionError): String = buildString {
        appendLine("Error Type: ${error.type}")
        appendLine("Message: ${error.message}")
        appendLine("Action: ${error.context.action}")
        error.stackTrace?.let { appendLine("Stack Trace:\n$it") }
    }

    private fun parseCorrectionAttempt(error: ExecutionError, response: String): CorrectionAttempt {
        // Parse LLM response into structured attempt
        return CorrectionAttempt(
            id = java.util.UUID.randomUUID().toString(),
            error = error,
            strategy = CorrectionStrategy.RETRY,
            hypothesis = response.take(200),
            actions = emptyList(),
            result = null
        )
    }
}
```

---

## v0.9.5 — User Preferences

### v0.9.5a — PreferenceModels

```kotlin
package com.sidekick.agent.preferences

/**
 * User coding preferences learned over time.
 */
data class UserPreferences(
    val codingStyle: CodingStyle,
    val namingConventions: NamingConventions,
    val documentationStyle: DocumentationStyle,
    val reviewPreferences: ReviewPreferences,
    val communicationStyle: CommunicationStyle,
    val learnedPatterns: List<LearnedPattern>
)

data class CodingStyle(
    val indentation: Indentation = Indentation.SPACES_4,
    val braceStyle: BraceStyle = BraceStyle.SAME_LINE,
    val maxLineLength: Int = 120,
    val preferExplicitTypes: Boolean = false,
    val preferEarlyReturn: Boolean = true,
    val preferenceStrength: Map<String, Float> = emptyMap()
)

enum class Indentation { SPACES_2, SPACES_4, TABS }
enum class BraceStyle { SAME_LINE, NEW_LINE }

data class NamingConventions(
    val classCase: NamingCase = NamingCase.PASCAL_CASE,
    val methodCase: NamingCase = NamingCase.CAMEL_CASE,
    val variableCase: NamingCase = NamingCase.CAMEL_CASE,
    val constantCase: NamingCase = NamingCase.SCREAMING_SNAKE,
    val prefixes: Map<String, String> = emptyMap() // e.g., "interface" -> "I"
)

enum class NamingCase { CAMEL_CASE, PASCAL_CASE, SNAKE_CASE, SCREAMING_SNAKE, KEBAB_CASE }

data class DocumentationStyle(
    val docCommentStyle: DocStyle = DocStyle.KDOC,
    val minDocLength: Int = 20,
    val includeExamples: Boolean = true,
    val documentPrivate: Boolean = false
)

enum class DocStyle { KDOC, JAVADOC, JSDOC, XML_DOC }

data class ReviewPreferences(
    val verbosity: Verbosity = Verbosity.MEDIUM,
    val focusAreas: Set<ReviewFocus> = setOf(ReviewFocus.CORRECTNESS),
    val nitpickLevel: NitpickLevel = NitpickLevel.MODERATE
)

enum class Verbosity { BRIEF, MEDIUM, DETAILED }
enum class ReviewFocus { CORRECTNESS, PERFORMANCE, SECURITY, STYLE, READABILITY }
enum class NitpickLevel { NONE, MODERATE, THOROUGH }

data class CommunicationStyle(
    val explanationDepth: Verbosity = Verbosity.MEDIUM,
    val useEmoji: Boolean = false,
    val formalityLevel: Float = 0.5f // 0 = casual, 1 = formal
)

/**
 * A pattern learned from user behavior.
 */
data class LearnedPattern(
    val id: String,
    val category: PatternCategory,
    val pattern: String,
    val examples: List<String>,
    val confidence: Float,
    val timesObserved: Int
)

enum class PatternCategory { 
    ERROR_HANDLING, LOGGING, TESTING, ARCHITECTURE, DEPENDENCY_INJECTION
}
```

---

### v0.9.5b — PreferenceLearningService

```kotlin
package com.sidekick.agent.preferences

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "SidekickPreferences", storages = [Storage("sidekick-preferences.xml")])
class PreferenceLearningService(private val project: Project) : PersistentStateComponent<PreferenceLearningService.State> {

    data class State(
        var preferences: UserPreferences = UserPreferences(
            CodingStyle(), NamingConventions(), DocumentationStyle(),
            ReviewPreferences(), CommunicationStyle(), emptyList()
        ),
        var observations: MutableList<Observation> = mutableListOf()
    )

    private var state = State()

    companion object {
        fun getInstance(project: Project) = project.getService(PreferenceLearningService::class.java)
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Records an observation of user behavior.
     */
    fun observe(observation: Observation) {
        state.observations.add(observation)
        if (state.observations.size % 10 == 0) {
            updatePreferences()
        }
    }

    /**
     * Records user feedback on generated code.
     */
    fun recordFeedback(generatedCode: String, userModifiedCode: String, accepted: Boolean) {
        if (!accepted) {
            // Analyze diff to learn preferences
            val diff = analyzeDiff(generatedCode, userModifiedCode)
            diff.forEach { change ->
                observe(Observation(ObservationType.CODE_MODIFICATION, change))
            }
        }
    }

    /**
     * Gets current preferences.
     */
    fun getPreferences(): UserPreferences = state.preferences

    /**
     * Applies preferences to a prompt.
     */
    fun enhancePrompt(basePrompt: String): String {
        val prefs = state.preferences
        return buildString {
            appendLine(basePrompt)
            appendLine()
            appendLine("User preferences:")
            appendLine("- Indentation: ${prefs.codingStyle.indentation}")
            appendLine("- Max line length: ${prefs.codingStyle.maxLineLength}")
            appendLine("- Brace style: ${prefs.codingStyle.braceStyle}")
            if (prefs.codingStyle.preferEarlyReturn) {
                appendLine("- Prefer early returns over nested conditions")
            }
            
            prefs.learnedPatterns.filter { it.confidence > 0.7f }.forEach { pattern ->
                appendLine("- ${pattern.category}: ${pattern.pattern}")
            }
        }
    }

    private fun updatePreferences() {
        // Analyze observations and update preferences
        val codeObs = state.observations.filter { it.type == ObservationType.CODE_MODIFICATION }
        
        // Detect indentation preference
        val indentCounts = mutableMapOf<Indentation, Int>()
        // ... analysis logic
    }

    private fun analyzeDiff(original: String, modified: String): List<String> {
        // Return list of changes
        return emptyList()
    }
}

data class Observation(
    val type: ObservationType,
    val data: String,
    val timestamp: java.time.Instant = java.time.Instant.now()
)

enum class ObservationType { CODE_MODIFICATION, REJECTION, ACCEPTANCE, STYLE_CORRECTION }
```

---

## v0.9.6 — Reflection & Critique

### v0.9.6a — ReflectionModels

```kotlin
package com.sidekick.agent.reflection

/**
 * Self-evaluation of agent output.
 */
data class SelfReflection(
    val id: String,
    val outputId: String,
    val evaluation: Evaluation,
    val critiques: List<Critique>,
    val improvements: List<Improvement>,
    val confidence: Float
)

data class Evaluation(
    val correctness: Float,
    val completeness: Float,
    val efficiency: Float,
    val readability: Float,
    val overall: Float
)

data class Critique(
    val aspect: CritiqueAspect,
    val issue: String,
    val severity: CritiqueSeverity,
    val location: String?
)

enum class CritiqueAspect { LOGIC, STYLE, PERFORMANCE, SECURITY, EDGE_CASES, DOCUMENTATION }
enum class CritiqueSeverity { MINOR, MODERATE, MAJOR, CRITICAL }

data class Improvement(
    val description: String,
    val before: String,
    val after: String,
    val impact: Float
)
```

---

### v0.9.6b — ReflectionService

```kotlin
package com.sidekick.agent.reflection

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.llm.provider.ProviderManager

@Service(Service.Level.PROJECT)
class ReflectionService(private val project: Project) {

    companion object {
        fun getInstance(project: Project) = project.getService(ReflectionService::class.java)

        private val REFLECTION_PROMPT = """
            Critically evaluate this code/response. Be your own harshest critic:
            
            1. Is the logic correct? Check edge cases.
            2. Is it complete? What's missing?
            3. Is it efficient? Any performance issues?
            4. Is it readable? Following best practices?
            5. Are there security concerns?
            
            Score each aspect 0-1 and provide specific critiques.
            Then suggest concrete improvements.
        """.trimIndent()
    }

    /**
     * Reflects on generated output.
     */
    suspend fun reflect(output: String, context: String? = null): SelfReflection {
        val provider = ProviderManager.getInstance().getActiveProvider()
        
        val response = provider.chat(
            com.sidekick.llm.provider.UnifiedChatRequest(
                model = "",
                messages = listOf(
                    com.sidekick.llm.provider.UnifiedMessage(
                        com.sidekick.llm.provider.MessageRole.SYSTEM,
                        REFLECTION_PROMPT
                    ),
                    com.sidekick.llm.provider.UnifiedMessage(
                        com.sidekick.llm.provider.MessageRole.USER,
                        "Output to evaluate:\n$output${context?.let { "\n\nContext: $it" } ?: ""}"
                    )
                ),
                temperature = 0.3f // Lower temp for more critical evaluation
            )
        )

        return parseReflection(response.content)
    }

    /**
     * Improves output based on reflection.
     */
    suspend fun improveWithReflection(output: String, maxIterations: Int = 2): ImprovedOutput {
        var current = output
        val reflections = mutableListOf<SelfReflection>()

        repeat(maxIterations) { iteration ->
            val reflection = reflect(current)
            reflections.add(reflection)

            if (reflection.evaluation.overall > 0.9f || reflection.critiques.isEmpty()) {
                return ImprovedOutput(current, reflections, iteration + 1)
            }

            // Apply improvements
            current = applyImprovements(current, reflection.improvements)
        }

        return ImprovedOutput(current, reflections, maxIterations)
    }

    private fun parseReflection(response: String): SelfReflection {
        // Parse LLM response into SelfReflection
        return SelfReflection(
            id = java.util.UUID.randomUUID().toString(),
            outputId = "",
            evaluation = Evaluation(0.8f, 0.8f, 0.8f, 0.8f, 0.8f),
            critiques = emptyList(),
            improvements = emptyList(),
            confidence = 0.8f
        )
    }

    private suspend fun applyImprovements(output: String, improvements: List<Improvement>): String {
        if (improvements.isEmpty()) return output
        
        val provider = ProviderManager.getInstance().getActiveProvider()
        val response = provider.chat(
            com.sidekick.llm.provider.UnifiedChatRequest(
                model = "",
                messages = listOf(
                    com.sidekick.llm.provider.UnifiedMessage(
                        com.sidekick.llm.provider.MessageRole.USER,
                        "Apply these improvements to the code:\n${improvements.joinToString("\n") { it.description }}\n\nCode:\n$output"
                    )
                )
            )
        )
        return response.content
    }
}

data class ImprovedOutput(val output: String, val reflections: List<SelfReflection>, val iterations: Int)
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Advanced Agent Services (v0.9.x) -->
    <projectService serviceImplementation="com.sidekick.agent.planning.PlanningService"/>
    <applicationService serviceImplementation="com.sidekick.agent.specialists.SpecialistService"/>
    <projectService serviceImplementation="com.sidekick.agent.collaboration.CollaborationOrchestrator"/>
    <projectService serviceImplementation="com.sidekick.agent.correction.SelfCorrectionService"/>
    <projectService serviceImplementation="com.sidekick.agent.preferences.PreferenceLearningService"/>
    <projectService serviceImplementation="com.sidekick.agent.reflection.ReflectionService"/>
</extensions>
```

---

## Verification Plan

### Automated Tests

```bash
./gradlew test --tests "com.sidekick.agent.planning.*"
./gradlew test --tests "com.sidekick.agent.specialists.*"
./gradlew test --tests "com.sidekick.agent.collaboration.*"
```

### Manual Verification

| Version | Step | Expected Result |
|---------|------|-----------------|
| v0.9.1 | Request complex feature | Plan generated with steps |
| v0.9.2 | Invoke architect agent | Receives architectural guidance |
| v0.9.3 | Execute plan | Multiple agents collaborate |
| v0.9.4 | Introduce error | Agent self-corrects |
| v0.9.5 | Reject code style | Preferences updated |
| v0.9.6 | Request code | Agent self-critiques |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.9.0 | 2026-02-04 | Ryan | Initial v0.9.x design specification |
