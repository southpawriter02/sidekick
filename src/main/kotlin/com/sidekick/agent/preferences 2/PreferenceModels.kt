package com.sidekick.agent.preferences

import java.time.Instant
import java.util.UUID

/**
 * # Preference Models
 *
 * Data models for user preferences and style learning.
 * Part of Sidekick v0.9.5 User Preferences feature.
 *
 * ## Overview
 *
 * User preferences enable:
 * - Learning coding style from user interactions
 * - Adapting responses to match user preferences
 * - Tracking feedback patterns
 * - Personalizing agent behavior
 *
 * @since 0.9.5
 */

// =============================================================================
// User Profile
// =============================================================================

/**
 * A user's preference profile.
 *
 * @property id Unique profile identifier
 * @property userId User identifier
 * @property codingStyle Coding style preferences
 * @property communicationStyle Communication preferences
 * @property workflowPreferences Workflow preferences
 * @property feedbackHistory History of feedback
 * @property learnedPatterns Patterns learned from interactions
 * @property createdAt Creation timestamp
 * @property updatedAt Last update timestamp
 */
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val codingStyle: CodingStylePreferences = CodingStylePreferences(),
    val communicationStyle: CommunicationPreferences = CommunicationPreferences(),
    val workflowPreferences: WorkflowPreferences = WorkflowPreferences(),
    val feedbackHistory: List<FeedbackEntry> = emptyList(),
    val learnedPatterns: List<LearnedPattern> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /**
     * Total feedback count.
     */
    val feedbackCount: Int get() = feedbackHistory.size

    /**
     * Positive feedback rate.
     */
    val positiveRate: Float
        get() {
            val positive = feedbackHistory.count { it.sentiment == FeedbackSentiment.POSITIVE }
            return if (feedbackHistory.isNotEmpty()) positive.toFloat() / feedbackHistory.size else 0.5f
        }

    /**
     * Number of learned patterns.
     */
    val patternCount: Int get() = learnedPatterns.size

    /**
     * Adds feedback entry.
     */
    fun addFeedback(feedback: FeedbackEntry): UserProfile =
        copy(
            feedbackHistory = feedbackHistory + feedback,
            updatedAt = Instant.now()
        )

    /**
     * Adds learned pattern.
     */
    fun addPattern(pattern: LearnedPattern): UserProfile =
        copy(
            learnedPatterns = learnedPatterns + pattern,
            updatedAt = Instant.now()
        )

    /**
     * Updates coding style.
     */
    fun withCodingStyle(style: CodingStylePreferences): UserProfile =
        copy(codingStyle = style, updatedAt = Instant.now())

    /**
     * Updates communication style.
     */
    fun withCommunicationStyle(style: CommunicationPreferences): UserProfile =
        copy(communicationStyle = style, updatedAt = Instant.now())

    companion object {
        fun forUser(userId: String) = UserProfile(userId = userId)
    }
}

// =============================================================================
// Coding Style Preferences
// =============================================================================

/**
 * Coding style preferences.
 */
data class CodingStylePreferences(
    val language: String = "kotlin",
    val indentation: IndentationStyle = IndentationStyle.SPACES_4,
    val braceStyle: BraceStyle = BraceStyle.SAME_LINE,
    val namingConvention: NamingConvention = NamingConvention.CAMEL_CASE,
    val maxLineLength: Int = 120,
    val preferExplicitTypes: Boolean = false,
    val preferImmutability: Boolean = true,
    val preferFunctionalStyle: Boolean = true,
    val preferExpressionBodies: Boolean = true,
    val documentationStyle: DocumentationStyle = DocumentationStyle.KDOC,
    val importOrdering: ImportOrdering = ImportOrdering.ALPHABETICAL,
    val blankLinesBetweenMembers: Int = 1,
    val trailingCommas: Boolean = true,
    val customRules: Map<String, String> = emptyMap()
) {
    /**
     * Builds a style guide summary.
     */
    fun toStyleGuide(): String = buildString {
        appendLine("## Coding Style Guide")
        appendLine("- Language: $language")
        appendLine("- Indentation: ${indentation.displayName}")
        appendLine("- Brace Style: ${braceStyle.displayName}")
        appendLine("- Naming: ${namingConvention.displayName}")
        appendLine("- Max Line Length: $maxLineLength")
        appendLine("- Prefer Immutability: $preferImmutability")
        appendLine("- Prefer Functional Style: $preferFunctionalStyle")
        appendLine("- Documentation: ${documentationStyle.displayName}")
    }

    companion object {
        val KOTLIN_STANDARD = CodingStylePreferences()
        val JAVA_STANDARD = CodingStylePreferences(
            language = "java",
            preferExplicitTypes = true,
            preferImmutability = false,
            preferFunctionalStyle = false,
            documentationStyle = DocumentationStyle.JAVADOC
        )
        val CONCISE = CodingStylePreferences(
            preferExpressionBodies = true,
            blankLinesBetweenMembers = 0,
            documentationStyle = DocumentationStyle.MINIMAL
        )
    }
}

/**
 * Indentation style options.
 */
enum class IndentationStyle(val displayName: String, val chars: String) {
    SPACES_2("2 Spaces", "  "),
    SPACES_4("4 Spaces", "    "),
    TABS("Tabs", "\t")
}

/**
 * Brace placement style.
 */
enum class BraceStyle(val displayName: String) {
    SAME_LINE("Same Line (K&R)"),
    NEW_LINE("New Line (Allman)"),
    GNU("GNU Style")
}

/**
 * Naming convention options.
 */
enum class NamingConvention(val displayName: String) {
    CAMEL_CASE("camelCase"),
    PASCAL_CASE("PascalCase"),
    SNAKE_CASE("snake_case"),
    SCREAMING_SNAKE_CASE("SCREAMING_SNAKE_CASE"),
    KEBAB_CASE("kebab-case")
}

/**
 * Documentation style options.
 */
enum class DocumentationStyle(val displayName: String) {
    KDOC("KDoc"),
    JAVADOC("Javadoc"),
    JSDOC("JSDoc"),
    DOCSTRING("Docstring"),
    MINIMAL("Minimal"),
    NONE("None")
}

/**
 * Import ordering options.
 */
enum class ImportOrdering(val displayName: String) {
    ALPHABETICAL("Alphabetical"),
    BY_PACKAGE("By Package"),
    BY_TYPE("By Type"),
    NONE("No Ordering")
}

// =============================================================================
// Communication Preferences
// =============================================================================

/**
 * Communication style preferences.
 */
data class CommunicationPreferences(
    val verbosity: VerbosityLevel = VerbosityLevel.BALANCED,
    val tone: ToneStyle = ToneStyle.PROFESSIONAL,
    val explanationDepth: ExplanationDepth = ExplanationDepth.MODERATE,
    val codeCommentLevel: CodeCommentLevel = CodeCommentLevel.MODERATE,
    val preferCodeExamples: Boolean = true,
    val preferDiagrams: Boolean = false,
    val preferBulletPoints: Boolean = true,
    val showConfidence: Boolean = false,
    val showAlternatives: Boolean = true,
    val maxResponseLength: Int = 2000
) {
    companion object {
        val CONCISE = CommunicationPreferences(
            verbosity = VerbosityLevel.CONCISE,
            explanationDepth = ExplanationDepth.BRIEF,
            codeCommentLevel = CodeCommentLevel.MINIMAL,
            maxResponseLength = 500
        )
        val DETAILED = CommunicationPreferences(
            verbosity = VerbosityLevel.DETAILED,
            explanationDepth = ExplanationDepth.COMPREHENSIVE,
            codeCommentLevel = CodeCommentLevel.EXTENSIVE,
            maxResponseLength = 5000
        )
        val BEGINNER_FRIENDLY = CommunicationPreferences(
            verbosity = VerbosityLevel.DETAILED,
            explanationDepth = ExplanationDepth.COMPREHENSIVE,
            preferCodeExamples = true,
            showAlternatives = true
        )
    }
}

/**
 * Verbosity levels.
 */
enum class VerbosityLevel(val displayName: String, val weight: Float) {
    TERSE("Terse", 0.25f),
    CONCISE("Concise", 0.5f),
    BALANCED("Balanced", 0.75f),
    DETAILED("Detailed", 1.0f),
    COMPREHENSIVE("Comprehensive", 1.25f)
}

/**
 * Communication tone options.
 */
enum class ToneStyle(val displayName: String) {
    CASUAL("Casual"),
    PROFESSIONAL("Professional"),
    FORMAL("Formal"),
    FRIENDLY("Friendly"),
    TECHNICAL("Technical"),
    EDUCATIONAL("Educational")
}

/**
 * Explanation depth levels.
 */
enum class ExplanationDepth(val displayName: String) {
    BRIEF("Brief"),
    MODERATE("Moderate"),
    COMPREHENSIVE("Comprehensive"),
    TUTORIAL("Tutorial-style")
}

/**
 * Code comment levels.
 */
enum class CodeCommentLevel(val displayName: String) {
    NONE("None"),
    MINIMAL("Minimal"),
    MODERATE("Moderate"),
    EXTENSIVE("Extensive")
}

// =============================================================================
// Workflow Preferences
// =============================================================================

/**
 * Workflow and interaction preferences.
 */
data class WorkflowPreferences(
    val confirmBeforeChanges: Boolean = true,
    val autoApplyFixes: Boolean = false,
    val showDiffPreviews: Boolean = true,
    val preferIncrementalChanges: Boolean = true,
    val runTestsAfterChanges: Boolean = true,
    val formatAfterChanges: Boolean = true,
    val organizeImportsAfterChanges: Boolean = true,
    val preferredTestFramework: String = "junit5",
    val preferredBuildTool: String = "gradle",
    val customWorkflows: Map<String, WorkflowStep> = emptyMap()
)

/**
 * A workflow step definition.
 */
data class WorkflowStep(
    val name: String,
    val action: String,
    val parameters: Map<String, String> = emptyMap(),
    val continueOnFailure: Boolean = false
)

// =============================================================================
// Feedback
// =============================================================================

/**
 * A feedback entry from user interaction.
 */
data class FeedbackEntry(
    val id: String = UUID.randomUUID().toString(),
    val taskType: String,
    val sentiment: FeedbackSentiment,
    val category: FeedbackCategory,
    val comment: String? = null,
    val context: Map<String, String> = emptyMap(),
    val timestamp: Instant = Instant.now()
) {
    val isPositive: Boolean get() = sentiment == FeedbackSentiment.POSITIVE
    val isNegative: Boolean get() = sentiment == FeedbackSentiment.NEGATIVE

    companion object {
        fun positive(taskType: String, category: FeedbackCategory, comment: String? = null) =
            FeedbackEntry(taskType = taskType, sentiment = FeedbackSentiment.POSITIVE, category = category, comment = comment)

        fun negative(taskType: String, category: FeedbackCategory, comment: String? = null) =
            FeedbackEntry(taskType = taskType, sentiment = FeedbackSentiment.NEGATIVE, category = category, comment = comment)

        fun neutral(taskType: String, category: FeedbackCategory, comment: String? = null) =
            FeedbackEntry(taskType = taskType, sentiment = FeedbackSentiment.NEUTRAL, category = category, comment = comment)
    }
}

/**
 * Feedback sentiment.
 */
enum class FeedbackSentiment(val displayName: String, val value: Int) {
    POSITIVE("Positive", 1),
    NEUTRAL("Neutral", 0),
    NEGATIVE("Negative", -1)
}

/**
 * Feedback categories.
 */
enum class FeedbackCategory(val displayName: String) {
    CODE_QUALITY("Code Quality"),
    CODE_STYLE("Code Style"),
    EXPLANATION("Explanation"),
    ACCURACY("Accuracy"),
    COMPLETENESS("Completeness"),
    SPEED("Speed"),
    RELEVANCE("Relevance"),
    USABILITY("Usability"),
    OTHER("Other")
}

// =============================================================================
// Learned Patterns
// =============================================================================

/**
 * A pattern learned from user interactions.
 */
data class LearnedPattern(
    val id: String = UUID.randomUUID().toString(),
    val type: PatternType,
    val pattern: String,
    val replacement: String? = null,
    val confidence: Float = 0.5f,
    val occurrences: Int = 1,
    val examples: List<String> = emptyList(),
    val learnedAt: Instant = Instant.now(),
    val lastSeenAt: Instant = Instant.now()
) {
    /**
     * Whether this is a high confidence pattern.
     */
    val isHighConfidence: Boolean get() = confidence >= 0.8f

    /**
     * Updates confidence based on new occurrence.
     */
    fun reinforce(positive: Boolean = true): LearnedPattern {
        val delta = if (positive) 0.1f else -0.15f
        val newConfidence = (confidence + delta).coerceIn(0f, 1f)
        return copy(
            confidence = newConfidence,
            occurrences = occurrences + 1,
            lastSeenAt = Instant.now()
        )
    }

    /**
     * Adds an example.
     */
    fun withExample(example: String): LearnedPattern =
        copy(examples = (examples + example).takeLast(5))

    companion object {
        fun codeStyle(pattern: String, replacement: String, confidence: Float = 0.5f) =
            LearnedPattern(
                type = PatternType.CODE_STYLE,
                pattern = pattern,
                replacement = replacement,
                confidence = confidence
            )

        fun namingConvention(pattern: String, replacement: String) =
            LearnedPattern(
                type = PatternType.NAMING_CONVENTION,
                pattern = pattern,
                replacement = replacement
            )

        fun preference(pattern: String, confidence: Float = 0.7f) =
            LearnedPattern(
                type = PatternType.PREFERENCE,
                pattern = pattern,
                confidence = confidence
            )
    }
}

/**
 * Types of learned patterns.
 */
enum class PatternType(val displayName: String) {
    CODE_STYLE("Code Style"),
    NAMING_CONVENTION("Naming Convention"),
    IMPORT_ORDER("Import Order"),
    COMMENT_STYLE("Comment Style"),
    ERROR_HANDLING("Error Handling"),
    TESTING_PATTERN("Testing Pattern"),
    ARCHITECTURE("Architecture"),
    PREFERENCE("General Preference"),
    CUSTOM("Custom")
}

// =============================================================================
// Preference Events
// =============================================================================

/**
 * Events from preference system.
 */
sealed class PreferenceEvent {
    abstract val profileId: String
    abstract val timestamp: Instant

    data class ProfileCreated(
        override val profileId: String,
        val userId: String,
        override val timestamp: Instant = Instant.now()
    ) : PreferenceEvent()

    data class ProfileUpdated(
        override val profileId: String,
        val section: String,
        override val timestamp: Instant = Instant.now()
    ) : PreferenceEvent()

    data class FeedbackReceived(
        override val profileId: String,
        val sentiment: FeedbackSentiment,
        val category: FeedbackCategory,
        override val timestamp: Instant = Instant.now()
    ) : PreferenceEvent()

    data class PatternLearned(
        override val profileId: String,
        val patternType: PatternType,
        val confidence: Float,
        override val timestamp: Instant = Instant.now()
    ) : PreferenceEvent()

    data class PatternReinforced(
        override val profileId: String,
        val patternId: String,
        val newConfidence: Float,
        override val timestamp: Instant = Instant.now()
    ) : PreferenceEvent()
}

// =============================================================================
// Style Adaptation
// =============================================================================

/**
 * Adapted prompt based on user preferences.
 */
data class AdaptedPrompt(
    val originalPrompt: String,
    val adaptedPrompt: String,
    val appliedPreferences: List<String>,
    val styleGuide: String?
) {
    val hasAdaptations: Boolean get() = appliedPreferences.isNotEmpty()
}

/**
 * Configuration for preference learning.
 */
data class LearningConfig(
    val enableAutoLearning: Boolean = true,
    val minConfidenceThreshold: Float = 0.3f,
    val maxPatterns: Int = 100,
    val patternDecayDays: Int = 30,
    val reinforcementWeight: Float = 0.1f,
    val negativeWeight: Float = 0.15f
)
