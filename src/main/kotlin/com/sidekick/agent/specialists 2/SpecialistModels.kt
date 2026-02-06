package com.sidekick.agent.specialists

import java.time.Instant
import java.util.UUID

/**
 * # Specialist Models
 *
 * Data models for role-based specialist agents with focused expertise.
 * Part of Sidekick v0.9.2 Specialist Agents feature.
 *
 * ## Overview
 *
 * Specialist agents provide:
 * - Role-based expertise (Architect, Implementer, Reviewer, etc.)
 * - Capability constraints per role
 * - Specialized system prompts
 * - Confidence scoring
 * - Delegation support
 *
 * @since 0.9.2
 */

// =============================================================================
// Specialist Agent
// =============================================================================

/**
 * A specialist agent with focused expertise.
 *
 * @property id Unique agent identifier
 * @property role The agent's role
 * @property systemPrompt System prompt defining behavior
 * @property capabilities Set of allowed capabilities
 * @property preferredModel Preferred LLM model (null = use default)
 * @property temperature LLM temperature setting
 * @property maxTokens Maximum tokens for response
 */
data class SpecialistAgent(
    val id: String = UUID.randomUUID().toString(),
    val role: AgentRole,
    val systemPrompt: String,
    val capabilities: Set<Capability>,
    val preferredModel: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096
) {
    /**
     * Display name with icon.
     */
    val displayName: String get() = "${role.icon} ${role.displayName}"

    /**
     * Whether agent can perform a capability.
     */
    fun canPerform(capability: Capability): Boolean = capability in capabilities

    /**
     * Whether agent can modify files.
     */
    val canModifyFiles: Boolean
        get() = Capability.WRITE_CODE in capabilities || Capability.CREATE_FILES in capabilities

    /**
     * Whether agent is read-only.
     */
    val isReadOnly: Boolean
        get() = !canModifyFiles && Capability.DELETE_FILES !in capabilities

    /**
     * Creates an invocation request.
     */
    fun createRequest(prompt: String, context: String? = null): SpecialistRequest =
        SpecialistRequest(
            agentId = id,
            role = role,
            prompt = prompt,
            context = context
        )
}

// =============================================================================
// Agent Role
// =============================================================================

/**
 * Roles for specialist agents.
 */
enum class AgentRole(
    val displayName: String,
    val icon: String,
    val description: String
) {
    /** Designs architecture and high-level solutions */
    ARCHITECT(
        "Architect",
        "🏗️",
        "Analyzes system structure, designs solutions, ensures architectural consistency"
    ),

    /** Writes and implements code */
    IMPLEMENTER(
        "Implementer",
        "⚙️",
        "Writes clean, idiomatic code following project conventions"
    ),

    /** Reviews code for quality */
    REVIEWER(
        "Reviewer",
        "👁️",
        "Reviews code for correctness, style, best practices, and security"
    ),

    /** Writes and runs tests */
    TESTER(
        "Tester",
        "🧪",
        "Creates comprehensive tests, identifies edge cases, ensures coverage"
    ),

    /** Writes documentation */
    DOCUMENTER(
        "Documenter",
        "📝",
        "Creates clear documentation, API docs, and usage examples"
    ),

    /** Debugs issues */
    DEBUGGER(
        "Debugger",
        "🐛",
        "Analyzes errors, identifies root causes, proposes minimal fixes"
    ),

    /** Optimizes performance */
    OPTIMIZER(
        "Optimizer",
        "⚡",
        "Identifies bottlenecks, optimizes algorithms, improves efficiency"
    ),

    /** Analyzes security */
    SECURITY(
        "Security Analyst",
        "🔒",
        "Identifies vulnerabilities, reviews auth logic, ensures secure coding"
    );

    companion object {
        /**
         * Primary roles for most tasks.
         */
        val PRIMARY_ROLES = setOf(ARCHITECT, IMPLEMENTER, REVIEWER, TESTER)

        /**
         * Supporting roles for specialized tasks.
         */
        val SUPPORTING_ROLES = setOf(DOCUMENTER, DEBUGGER, OPTIMIZER, SECURITY)

        /**
         * Gets default capabilities for a role.
         */
        fun defaultCapabilities(role: AgentRole): Set<Capability> = when (role) {
            ARCHITECT -> setOf(
                Capability.READ_CODE,
                Capability.ANALYZE_AST,
                Capability.SEARCH_CODEBASE,
                Capability.DELEGATE_TASKS
            )
            IMPLEMENTER -> setOf(
                Capability.READ_CODE,
                Capability.WRITE_CODE,
                Capability.CREATE_FILES,
                Capability.MODIFY_CONFIG,
                Capability.SEARCH_CODEBASE
            )
            REVIEWER -> setOf(
                Capability.READ_CODE,
                Capability.ANALYZE_AST,
                Capability.SEARCH_CODEBASE
            )
            TESTER -> setOf(
                Capability.READ_CODE,
                Capability.WRITE_CODE,
                Capability.RUN_TESTS,
                Capability.CREATE_FILES
            )
            DOCUMENTER -> setOf(
                Capability.READ_CODE,
                Capability.WRITE_CODE,
                Capability.CREATE_FILES
            )
            DEBUGGER -> setOf(
                Capability.READ_CODE,
                Capability.ANALYZE_AST,
                Capability.EXECUTE_COMMANDS,
                Capability.SEARCH_CODEBASE,
                Capability.ACCESS_MEMORY
            )
            OPTIMIZER -> setOf(
                Capability.READ_CODE,
                Capability.ANALYZE_AST,
                Capability.WRITE_CODE,
                Capability.RUN_TESTS
            )
            SECURITY -> setOf(
                Capability.READ_CODE,
                Capability.ANALYZE_AST,
                Capability.SEARCH_CODEBASE
            )
        }
    }
}

// =============================================================================
// Capability
// =============================================================================

/**
 * Capabilities that agents can have.
 */
enum class Capability(val displayName: String, val description: String) {
    /** Read source code files */
    READ_CODE("Read Code", "Read source files from the project"),

    /** Write or modify code */
    WRITE_CODE("Write Code", "Modify existing source files"),

    /** Run test suites */
    RUN_TESTS("Run Tests", "Execute test commands"),

    /** Analyze abstract syntax trees */
    ANALYZE_AST("Analyze AST", "Parse and analyze code structure"),

    /** Search through codebase */
    SEARCH_CODEBASE("Search Codebase", "Search files and symbols"),

    /** Execute shell commands */
    EXECUTE_COMMANDS("Execute Commands", "Run shell commands"),

    /** Modify configuration files */
    MODIFY_CONFIG("Modify Config", "Change configuration files"),

    /** Create new files */
    CREATE_FILES("Create Files", "Create new source files"),

    /** Delete files */
    DELETE_FILES("Delete Files", "Remove source files"),

    /** Access conversation memory */
    ACCESS_MEMORY("Access Memory", "Read/write conversation memory"),

    /** Delegate to other agents */
    DELEGATE_TASKS("Delegate Tasks", "Assign work to other specialists");

    companion object {
        /** Capabilities that modify the codebase */
        val MODIFYING = setOf(WRITE_CODE, CREATE_FILES, DELETE_FILES, MODIFY_CONFIG)

        /** Read-only capabilities */
        val READ_ONLY = setOf(READ_CODE, ANALYZE_AST, SEARCH_CODEBASE)

        /** Capabilities requiring confirmation */
        val REQUIRES_CONFIRMATION = setOf(DELETE_FILES, EXECUTE_COMMANDS)
    }
}

// =============================================================================
// Specialist Request
// =============================================================================

/**
 * A request to a specialist agent.
 *
 * @property id Request identifier
 * @property agentId Target agent ID
 * @property role Target agent role
 * @property prompt The prompt/question
 * @property context Additional context
 * @property referencedFiles Files to include
 * @property constraints Execution constraints
 * @property createdAt Request creation time
 */
data class SpecialistRequest(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val role: AgentRole,
    val prompt: String,
    val context: String? = null,
    val referencedFiles: List<String> = emptyList(),
    val constraints: RequestConstraints = RequestConstraints.DEFAULT,
    val createdAt: Instant = Instant.now()
) {
    /**
     * Creates request with file references.
     */
    fun withFiles(files: List<String>): SpecialistRequest =
        copy(referencedFiles = referencedFiles + files)

    /**
     * Creates request with additional context.
     */
    fun withContext(additionalContext: String): SpecialistRequest =
        copy(context = listOfNotNull(context, additionalContext).joinToString("\n\n"))
}

/**
 * Constraints for specialist requests.
 */
data class RequestConstraints(
    val maxTokens: Int = 4096,
    val timeout: Long = 60000,
    val requireConfirmation: Boolean = false,
    val allowModification: Boolean = true
) {
    companion object {
        val DEFAULT = RequestConstraints()
        val READ_ONLY = RequestConstraints(allowModification = false)
        val STRICT = RequestConstraints(requireConfirmation = true, maxTokens = 2048)
    }
}

// =============================================================================
// Agent Response
// =============================================================================

/**
 * Response from a specialist agent.
 *
 * @property id Response identifier
 * @property requestId Original request ID
 * @property agentId Agent that responded
 * @property role Agent's role
 * @property content Response content
 * @property confidence Confidence in response (0-1)
 * @property reasoning Explanation of approach
 * @property suggestedActions Recommended follow-ups
 * @property delegateTo Suggested delegation target
 * @property artifacts Generated artifacts
 * @property tokensUsed Tokens consumed
 * @property durationMs Response time
 */
data class AgentResponse(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val agentId: String,
    val role: AgentRole,
    val content: String,
    val confidence: Float = 0.8f,
    val reasoning: String? = null,
    val suggestedActions: List<SuggestedAction> = emptyList(),
    val delegateTo: AgentRole? = null,
    val artifacts: List<ResponseArtifact> = emptyList(),
    val tokensUsed: Int = 0,
    val durationMs: Long = 0,
    val timestamp: Instant = Instant.now()
) {
    /**
     * Whether response suggests delegation.
     */
    val suggestsDelegation: Boolean get() = delegateTo != null

    /**
     * Whether response is high confidence.
     */
    val isHighConfidence: Boolean get() = confidence >= 0.8f

    /**
     * Whether response includes actions.
     */
    val hasActions: Boolean get() = suggestedActions.isNotEmpty()

    /**
     * Gets actions by priority.
     */
    fun getActionsByPriority(): List<SuggestedAction> =
        suggestedActions.sortedByDescending { it.priority }

    /**
     * Gets code artifacts.
     */
    fun getCodeArtifacts(): List<ResponseArtifact> =
        artifacts.filter { it.type == ArtifactType.CODE }

    companion object {
        /**
         * Creates a simple response.
         */
        fun simple(
            requestId: String,
            agentId: String,
            role: AgentRole,
            content: String
        ): AgentResponse = AgentResponse(
            requestId = requestId,
            agentId = agentId,
            role = role,
            content = content
        )

        /**
         * Creates a delegation response.
         */
        fun delegate(
            requestId: String,
            agentId: String,
            role: AgentRole,
            reason: String,
            delegateTo: AgentRole
        ): AgentResponse = AgentResponse(
            requestId = requestId,
            agentId = agentId,
            role = role,
            content = reason,
            delegateTo = delegateTo,
            confidence = 0.5f
        )
    }
}

// =============================================================================
// Suggested Action
// =============================================================================

/**
 * A suggested follow-up action.
 *
 * @property action Action identifier
 * @property description Human-readable description
 * @property priority Priority (higher = more important)
 * @property category Action category
 * @property parameters Action parameters
 */
data class SuggestedAction(
    val action: String,
    val description: String,
    val priority: Int = 5,
    val category: ActionCategory = ActionCategory.GENERAL,
    val parameters: Map<String, String> = emptyMap()
) {
    companion object {
        fun refactor(description: String, parameters: Map<String, String> = emptyMap()) =
            SuggestedAction("refactor", description, 7, ActionCategory.REFACTORING, parameters)

        fun test(description: String, parameters: Map<String, String> = emptyMap()) =
            SuggestedAction("add_test", description, 6, ActionCategory.TESTING, parameters)

        fun fix(description: String, parameters: Map<String, String> = emptyMap()) =
            SuggestedAction("fix", description, 8, ActionCategory.FIX, parameters)

        fun document(description: String, parameters: Map<String, String> = emptyMap()) =
            SuggestedAction("document", description, 4, ActionCategory.DOCUMENTATION, parameters)
    }
}

/**
 * Categories for actions.
 */
enum class ActionCategory {
    GENERAL,
    REFACTORING,
    TESTING,
    FIX,
    DOCUMENTATION,
    SECURITY,
    PERFORMANCE
}

// =============================================================================
// Response Artifact
// =============================================================================

/**
 * An artifact generated by a specialist.
 *
 * @property id Artifact identifier
 * @property name Artifact name
 * @property type Artifact type
 * @property content Artifact content
 * @property filePath Target file path (if applicable)
 * @property language Language (for code)
 */
data class ResponseArtifact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ArtifactType,
    val content: String,
    val filePath: String? = null,
    val language: String? = null
) {
    /**
     * Whether this is a code artifact.
     */
    val isCode: Boolean get() = type == ArtifactType.CODE

    /**
     * Estimated lines of code.
     */
    val lineCount: Int get() = content.lines().size

    companion object {
        fun code(name: String, content: String, filePath: String, language: String) =
            ResponseArtifact(
                name = name,
                type = ArtifactType.CODE,
                content = content,
                filePath = filePath,
                language = language
            )

        fun documentation(name: String, content: String) =
            ResponseArtifact(
                name = name,
                type = ArtifactType.DOCUMENTATION,
                content = content
            )

        fun review(content: String) =
            ResponseArtifact(
                name = "Code Review",
                type = ArtifactType.REVIEW,
                content = content
            )

        fun decision(name: String, content: String) =
            ResponseArtifact(
                name = name,
                type = ArtifactType.DECISION,
                content = content
            )
    }
}

/**
 * Types of artifacts.
 */
enum class ArtifactType {
    CODE,
    TEST,
    DOCUMENTATION,
    REVIEW,
    PLAN,
    DECISION,
    ANALYSIS
}

// =============================================================================
// Review Feedback  
// =============================================================================

/**
 * Structured feedback from a code review.
 */
data class ReviewFeedback(
    val id: String = UUID.randomUUID().toString(),
    val items: List<ReviewItem>,
    val overallAssessment: String,
    val approved: Boolean,
    val confidence: Float
) {
    val criticalCount: Int get() = items.count { it.severity == ReviewSeverity.CRITICAL }
    val importantCount: Int get() = items.count { it.severity == ReviewSeverity.IMPORTANT }
    val suggestionCount: Int get() = items.count { it.severity == ReviewSeverity.SUGGESTION }

    val hasCriticalIssues: Boolean get() = criticalCount > 0
    val hasBlockingIssues: Boolean get() = items.any { it.isBlocking }
}

/**
 * Individual review item.
 */
data class ReviewItem(
    val severity: ReviewSeverity,
    val category: ReviewCategory,
    val description: String,
    val location: String? = null,
    val suggestion: String? = null,
    val isBlocking: Boolean = false
)

/**
 * Severity of review items.
 */
enum class ReviewSeverity(val displayName: String, val weight: Int) {
    CRITICAL("Critical", 4),
    IMPORTANT("Important", 3),
    SUGGESTION("Suggestion", 2),
    NITPICK("Nitpick", 1)
}

/**
 * Categories for review items.
 */
enum class ReviewCategory {
    BUG,
    SECURITY,
    PERFORMANCE,
    STYLE,
    MAINTAINABILITY,
    TESTING,
    DOCUMENTATION,
    BEST_PRACTICE
}

// =============================================================================
// Agent Events
// =============================================================================

/**
 * Events from specialist agents.
 */
sealed class SpecialistEvent {
    abstract val agentId: String
    abstract val role: AgentRole
    abstract val timestamp: Instant

    data class AgentInvoked(
        override val agentId: String,
        override val role: AgentRole,
        val requestId: String,
        val prompt: String,
        override val timestamp: Instant = Instant.now()
    ) : SpecialistEvent()

    data class AgentResponded(
        override val agentId: String,
        override val role: AgentRole,
        val requestId: String,
        val confidence: Float,
        val durationMs: Long,
        override val timestamp: Instant = Instant.now()
    ) : SpecialistEvent()

    data class AgentDelegated(
        override val agentId: String,
        override val role: AgentRole,
        val delegateTo: AgentRole,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : SpecialistEvent()

    data class AgentFailed(
        override val agentId: String,
        override val role: AgentRole,
        val error: String,
        override val timestamp: Instant = Instant.now()
    ) : SpecialistEvent()
}
