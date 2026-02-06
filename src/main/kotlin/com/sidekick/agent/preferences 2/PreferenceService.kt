package com.sidekick.agent.preferences

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Preference Service
 *
 * Service for managing user preferences and learning from interactions.
 * Part of Sidekick v0.9.5 User Preferences feature.
 *
 * ## Features
 *
 * - Profile management (create, update, retrieve)
 * - Style learning from user corrections
 * - Feedback processing and pattern extraction
 * - Prompt adaptation based on preferences
 * - Pattern reinforcement and decay
 *
 * @since 0.9.5
 */
class PreferenceService(
    private val config: LearningConfig = LearningConfig()
) {
    private val profiles = ConcurrentHashMap<String, UserProfile>()
    private val eventListeners = CopyOnWriteArrayList<(PreferenceEvent) -> Unit>()

    // =========================================================================
    // Profile Management
    // =========================================================================

    /**
     * Creates or retrieves a profile for a user.
     */
    fun getOrCreateProfile(userId: String): UserProfile {
        return profiles.getOrPut(userId) {
            val profile = UserProfile.forUser(userId)
            emitEvent(PreferenceEvent.ProfileCreated(profile.id, userId))
            profile
        }
    }

    /**
     * Gets a profile by user ID.
     */
    fun getProfile(userId: String): UserProfile? = profiles[userId]

    /**
     * Gets all profiles.
     */
    fun getAllProfiles(): List<UserProfile> = profiles.values.toList()

    /**
     * Updates a profile.
     */
    fun updateProfile(userId: String, updater: (UserProfile) -> UserProfile): UserProfile? {
        val current = profiles[userId] ?: return null
        val updated = updater(current)
        profiles[userId] = updated
        return updated
    }

    /**
     * Deletes a profile.
     */
    fun deleteProfile(userId: String): Boolean = profiles.remove(userId) != null

    // =========================================================================
    // Coding Style
    // =========================================================================

    /**
     * Updates coding style preferences.
     */
    fun updateCodingStyle(userId: String, style: CodingStylePreferences): UserProfile? {
        val updated = updateProfile(userId) { it.withCodingStyle(style) }
        updated?.let { emitEvent(PreferenceEvent.ProfileUpdated(it.id, "codingStyle")) }
        return updated
    }

    /**
     * Infers coding style from code sample.
     */
    fun inferCodingStyle(code: String): CodingStylePreferences {
        val lines = code.lines()

        // Detect indentation
        val indentation = detectIndentation(code)

        // Detect brace style
        val braceStyle = detectBraceStyle(code)

        // Detect naming convention
        val namingConvention = detectNamingConvention(code)

        // Detect preferences
        val preferImmutability = code.contains("val ") && !code.contains("var ")
        val preferFunctional = code.contains(".map") || code.contains(".filter") || code.contains(".let")
        val preferExpressionBodies = code.contains(" = ") && code.contains("fun ")
        val trailingCommas = code.contains(",\n")

        // Detect max line length
        val maxLine = lines.maxOfOrNull { it.length } ?: 120

        return CodingStylePreferences(
            indentation = indentation,
            braceStyle = braceStyle,
            namingConvention = namingConvention,
            maxLineLength = maxLine.coerceAtMost(200),
            preferImmutability = preferImmutability,
            preferFunctionalStyle = preferFunctional,
            preferExpressionBodies = preferExpressionBodies,
            trailingCommas = trailingCommas
        )
    }

    private fun detectIndentation(code: String): IndentationStyle {
        val lines = code.lines()
        val indentedLines = lines.filter { it.startsWith(" ") || it.startsWith("\t") }

        if (indentedLines.isEmpty()) return IndentationStyle.SPACES_4

        val firstIndent = indentedLines.firstOrNull()?.takeWhile { it == ' ' || it == '\t' } ?: ""

        return when {
            firstIndent.startsWith("\t") -> IndentationStyle.TABS
            firstIndent.length == 2 -> IndentationStyle.SPACES_2
            else -> IndentationStyle.SPACES_4
        }
    }

    private fun detectBraceStyle(code: String): BraceStyle {
        // Check for opening brace on same line as declaration
        val sameLinePattern = Regex("""(fun|class|if|for|while).*\{""")
        val newLinePattern = Regex("""(fun|class|if|for|while).*\n\s*\{""")

        val sameLineCount = sameLinePattern.findAll(code).count()
        val newLineCount = newLinePattern.findAll(code).count()

        return if (sameLineCount >= newLineCount) BraceStyle.SAME_LINE else BraceStyle.NEW_LINE
    }

    private fun detectNamingConvention(code: String): NamingConvention {
        val functionPattern = Regex("""fun\s+(\w+)""")
        val functions = functionPattern.findAll(code).map { it.groupValues[1] }.toList()

        if (functions.isEmpty()) return NamingConvention.CAMEL_CASE

        val hasUnderscores = functions.any { it.contains("_") }
        val startsLowercase = functions.all { it.first().isLowerCase() }

        return when {
            hasUnderscores && functions.all { it == it.lowercase() } -> NamingConvention.SNAKE_CASE
            hasUnderscores -> NamingConvention.SCREAMING_SNAKE_CASE
            startsLowercase -> NamingConvention.CAMEL_CASE
            else -> NamingConvention.PASCAL_CASE
        }
    }

    // =========================================================================
    // Communication Style
    // =========================================================================

    /**
     * Updates communication preferences.
     */
    fun updateCommunicationStyle(userId: String, style: CommunicationPreferences): UserProfile? {
        val updated = updateProfile(userId) { it.withCommunicationStyle(style) }
        updated?.let { emitEvent(PreferenceEvent.ProfileUpdated(it.id, "communicationStyle")) }
        return updated
    }

    /**
     * Adjusts verbosity based on feedback.
     */
    fun adjustVerbosity(userId: String, increase: Boolean): UserProfile? {
        return updateProfile(userId) { profile ->
            val current = profile.communicationStyle.verbosity
            val newLevel = when {
                increase && current.ordinal < VerbosityLevel.entries.size - 1 ->
                    VerbosityLevel.entries[current.ordinal + 1]
                !increase && current.ordinal > 0 ->
                    VerbosityLevel.entries[current.ordinal - 1]
                else -> current
            }
            profile.withCommunicationStyle(
                profile.communicationStyle.copy(verbosity = newLevel)
            )
        }
    }

    // =========================================================================
    // Feedback Processing
    // =========================================================================

    /**
     * Records user feedback.
     */
    fun recordFeedback(
        userId: String,
        taskType: String,
        sentiment: FeedbackSentiment,
        category: FeedbackCategory,
        comment: String? = null,
        context: Map<String, String> = emptyMap()
    ): FeedbackEntry {
        val feedback = FeedbackEntry(
            taskType = taskType,
            sentiment = sentiment,
            category = category,
            comment = comment,
            context = context
        )

        updateProfile(userId) { it.addFeedback(feedback) }?.let { profile ->
            emitEvent(PreferenceEvent.FeedbackReceived(profile.id, sentiment, category))

            // Auto-adjust based on feedback
            if (config.enableAutoLearning) {
                processAutoAdjustments(userId, feedback)
            }
        }

        return feedback
    }

    /**
     * Records positive feedback.
     */
    fun recordPositiveFeedback(
        userId: String,
        taskType: String,
        category: FeedbackCategory,
        comment: String? = null
    ): FeedbackEntry = recordFeedback(userId, taskType, FeedbackSentiment.POSITIVE, category, comment)

    /**
     * Records negative feedback.
     */
    fun recordNegativeFeedback(
        userId: String,
        taskType: String,
        category: FeedbackCategory,
        comment: String? = null
    ): FeedbackEntry = recordFeedback(userId, taskType, FeedbackSentiment.NEGATIVE, category, comment)

    private fun processAutoAdjustments(userId: String, feedback: FeedbackEntry) {
        when (feedback.category) {
            FeedbackCategory.EXPLANATION -> {
                if (feedback.isNegative) {
                    // Check if user wants more or less explanation
                    val tooMuch = feedback.comment?.contains("too much", ignoreCase = true) == true ||
                                  feedback.comment?.contains("verbose", ignoreCase = true) == true
                    adjustVerbosity(userId, increase = !tooMuch)
                }
            }
            FeedbackCategory.CODE_STYLE -> {
                // Could trigger style re-learning
            }
            else -> { /* No auto-adjustment for other categories */ }
        }
    }

    // =========================================================================
    // Pattern Learning
    // =========================================================================

    /**
     * Learns a pattern from user correction.
     */
    fun learnPattern(
        userId: String,
        type: PatternType,
        pattern: String,
        replacement: String? = null,
        example: String? = null
    ): LearnedPattern {
        val profile = getOrCreateProfile(userId)

        // Check if pattern already exists
        val existing = profile.learnedPatterns.find {
            it.type == type && it.pattern == pattern
        }

        val learned = if (existing != null) {
            // Reinforce existing pattern
            val reinforced = existing.reinforce(positive = true)
            example?.let { reinforced.withExample(it) } ?: reinforced
        } else {
            // Create new pattern
            LearnedPattern(
                type = type,
                pattern = pattern,
                replacement = replacement,
                examples = listOfNotNull(example)
            )
        }

        updateProfile(userId) { p ->
            val patterns = if (existing != null) {
                p.learnedPatterns.map { if (it.id == existing.id) learned else it }
            } else {
                (p.learnedPatterns + learned).takeLast(config.maxPatterns)
            }
            p.copy(learnedPatterns = patterns, updatedAt = Instant.now())
        }

        if (existing == null) {
            emitEvent(PreferenceEvent.PatternLearned(profile.id, type, learned.confidence))
        } else {
            emitEvent(PreferenceEvent.PatternReinforced(profile.id, learned.id, learned.confidence))
        }

        return learned
    }

    /**
     * Learns coding style pattern from user correction.
     */
    fun learnStyleCorrection(
        userId: String,
        original: String,
        corrected: String
    ): List<LearnedPattern> {
        val patterns = mutableListOf<LearnedPattern>()

        // Detect what changed
        if (original != corrected) {
            // Simple pattern: direct replacement
            patterns.add(learnPattern(
                userId,
                PatternType.CODE_STYLE,
                pattern = original.trim(),
                replacement = corrected.trim(),
                example = "$original -> $corrected"
            ))
        }

        return patterns
    }

    /**
     * Gets patterns by type.
     */
    fun getPatterns(userId: String, type: PatternType? = null): List<LearnedPattern> {
        val profile = getProfile(userId) ?: return emptyList()
        return if (type != null) {
            profile.learnedPatterns.filter { it.type == type }
        } else {
            profile.learnedPatterns
        }
    }

    /**
     * Gets high-confidence patterns.
     */
    fun getHighConfidencePatterns(userId: String): List<LearnedPattern> {
        val profile = getProfile(userId) ?: return emptyList()
        return profile.learnedPatterns.filter { it.isHighConfidence }
    }

    /**
     * Applies pattern decay based on age.
     */
    fun applyPatternDecay(userId: String) {
        updateProfile(userId) { profile ->
            val now = Instant.now()
            val decayed = profile.learnedPatterns.mapNotNull { pattern ->
                val daysSinceSeen = ChronoUnit.DAYS.between(pattern.lastSeenAt, now)
                if (daysSinceSeen > config.patternDecayDays) {
                    val decayFactor = 1.0f - (daysSinceSeen - config.patternDecayDays) * 0.01f
                    val newConfidence = (pattern.confidence * decayFactor).coerceAtLeast(0f)
                    if (newConfidence < config.minConfidenceThreshold) null
                    else pattern.copy(confidence = newConfidence)
                } else {
                    pattern
                }
            }
            profile.copy(learnedPatterns = decayed, updatedAt = now)
        }
    }

    // =========================================================================
    // Prompt Adaptation
    // =========================================================================

    /**
     * Adapts a prompt based on user preferences.
     */
    fun adaptPrompt(userId: String, prompt: String): AdaptedPrompt {
        val profile = getProfile(userId)
            ?: return AdaptedPrompt(prompt, prompt, emptyList(), null)

        val appliedPreferences = mutableListOf<String>()
        var adapted = prompt

        // Add style guide
        val styleGuide = profile.codingStyle.toStyleGuide()
        adapted = "$adapted\n\n$styleGuide"
        appliedPreferences.add("coding_style")

        // Add verbosity instruction
        val verbosity = profile.communicationStyle.verbosity
        val verbosityInstruction = when (verbosity) {
            VerbosityLevel.TERSE -> "Be extremely brief and concise."
            VerbosityLevel.CONCISE -> "Keep responses short and to the point."
            VerbosityLevel.BALANCED -> "Provide balanced explanations."
            VerbosityLevel.DETAILED -> "Provide detailed explanations."
            VerbosityLevel.COMPREHENSIVE -> "Provide comprehensive, thorough explanations."
        }
        adapted = "$adapted\n$verbosityInstruction"
        appliedPreferences.add("verbosity")

        // Add tone instruction
        val tone = profile.communicationStyle.tone
        adapted = "$adapted\nUse a ${tone.displayName.lowercase()} tone."
        appliedPreferences.add("tone")

        // Add high-confidence patterns as rules
        val patterns = getHighConfidencePatterns(userId)
        if (patterns.isNotEmpty()) {
            val patternRules = patterns.take(5).mapNotNull { p ->
                p.replacement?.let { "- Prefer '${p.replacement}' over '${p.pattern}'" }
            }
            if (patternRules.isNotEmpty()) {
                adapted = "$adapted\n\nStyle rules:\n${patternRules.joinToString("\n")}"
                appliedPreferences.add("learned_patterns")
            }
        }

        return AdaptedPrompt(prompt, adapted, appliedPreferences, styleGuide)
    }

    /**
     * Gets prompt modifiers based on preferences.
     */
    fun getPromptModifiers(userId: String): Map<String, String> {
        val profile = getProfile(userId) ?: return emptyMap()
        val modifiers = mutableMapOf<String, String>()

        modifiers["language"] = profile.codingStyle.language
        modifiers["indentation"] = profile.codingStyle.indentation.chars
        modifiers["max_line_length"] = profile.codingStyle.maxLineLength.toString()
        modifiers["verbosity"] = profile.communicationStyle.verbosity.name
        modifiers["tone"] = profile.communicationStyle.tone.name
        modifiers["explanation_depth"] = profile.communicationStyle.explanationDepth.name

        return modifiers
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Gets preference statistics.
     */
    fun getStats(): PreferenceStats {
        val allProfiles = profiles.values.toList()
        val allFeedback = allProfiles.flatMap { it.feedbackHistory }
        val allPatterns = allProfiles.flatMap { it.learnedPatterns }

        return PreferenceStats(
            totalProfiles = allProfiles.size,
            totalFeedback = allFeedback.size,
            positiveFeedback = allFeedback.count { it.isPositive },
            negativeFeedback = allFeedback.count { it.isNegative },
            totalPatterns = allPatterns.size,
            highConfidencePatterns = allPatterns.count { it.isHighConfidence },
            patternsByType = allPatterns.groupBy { it.type }.mapValues { it.value.size },
            feedbackByCategory = allFeedback.groupBy { it.category }.mapValues { it.value.size }
        )
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (PreferenceEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (PreferenceEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: PreferenceEvent) {
        eventListeners.forEach { it(event) }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Clears all profiles.
     */
    fun clearProfiles() {
        profiles.clear()
    }
}

/**
 * Preference statistics.
 */
data class PreferenceStats(
    val totalProfiles: Int,
    val totalFeedback: Int,
    val positiveFeedback: Int,
    val negativeFeedback: Int,
    val totalPatterns: Int,
    val highConfidencePatterns: Int,
    val patternsByType: Map<PatternType, Int>,
    val feedbackByCategory: Map<FeedbackCategory, Int>
) {
    val feedbackPositiveRate: Float
        get() = if (totalFeedback > 0) positiveFeedback.toFloat() / totalFeedback else 0f
}
