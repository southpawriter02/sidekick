package com.sidekick.agent.preferences

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Preference Models.
 */
@DisplayName("Preference Models Tests")
class PreferenceModelsTest {

    // =========================================================================
    // UserProfile Tests
    // =========================================================================

    @Nested
    @DisplayName("UserProfile")
    inner class UserProfileTests {

        @Test
        @DisplayName("forUser factory creates profile")
        fun forUserFactoryCreatesProfile() {
            val profile = UserProfile.forUser("user123")

            assertEquals("user123", profile.userId)
            assertNotNull(profile.id)
            assertEquals(0, profile.feedbackCount)
        }

        @Test
        @DisplayName("addFeedback appends entry")
        fun addFeedbackAppendsEntry() {
            var profile = UserProfile.forUser("user1")

            profile = profile.addFeedback(FeedbackEntry.positive("refactor", FeedbackCategory.CODE_QUALITY))
            profile = profile.addFeedback(FeedbackEntry.negative("explain", FeedbackCategory.EXPLANATION))

            assertEquals(2, profile.feedbackCount)
        }

        @Test
        @DisplayName("positiveRate calculates correctly")
        fun positiveRateCalculatesCorrectly() {
            var profile = UserProfile.forUser("user1")
                .addFeedback(FeedbackEntry.positive("task1", FeedbackCategory.CODE_QUALITY))
                .addFeedback(FeedbackEntry.positive("task2", FeedbackCategory.CODE_QUALITY))
                .addFeedback(FeedbackEntry.negative("task3", FeedbackCategory.ACCURACY))

            assertEquals(2f / 3f, profile.positiveRate, 0.01f)
        }

        @Test
        @DisplayName("addPattern appends pattern")
        fun addPatternAppendsPattern() {
            var profile = UserProfile.forUser("user1")

            profile = profile.addPattern(LearnedPattern.codeStyle("val ", "var ", 0.7f))

            assertEquals(1, profile.patternCount)
        }

        @Test
        @DisplayName("withCodingStyle updates style")
        fun withCodingStyleUpdatesStyle() {
            val profile = UserProfile.forUser("user1")
            val newStyle = CodingStylePreferences(language = "java", maxLineLength = 80)

            val updated = profile.withCodingStyle(newStyle)

            assertEquals("java", updated.codingStyle.language)
            assertEquals(80, updated.codingStyle.maxLineLength)
        }
    }

    // =========================================================================
    // CodingStylePreferences Tests
    // =========================================================================

    @Nested
    @DisplayName("CodingStylePreferences")
    inner class CodingStylePreferencesTests {

        @Test
        @DisplayName("default values are sensible")
        fun defaultValuesAreSensible() {
            val style = CodingStylePreferences()

            assertEquals("kotlin", style.language)
            assertEquals(120, style.maxLineLength)
            assertTrue(style.preferImmutability)
        }

        @Test
        @DisplayName("KOTLIN_STANDARD preset")
        fun kotlinStandardPreset() {
            val style = CodingStylePreferences.KOTLIN_STANDARD

            assertEquals(IndentationStyle.SPACES_4, style.indentation)
            assertEquals(BraceStyle.SAME_LINE, style.braceStyle)
        }

        @Test
        @DisplayName("JAVA_STANDARD preset")
        fun javaStandardPreset() {
            val style = CodingStylePreferences.JAVA_STANDARD

            assertEquals("java", style.language)
            assertTrue(style.preferExplicitTypes)
            assertFalse(style.preferFunctionalStyle)
        }

        @Test
        @DisplayName("toStyleGuide generates guide")
        fun toStyleGuideGeneratesGuide() {
            val style = CodingStylePreferences()
            val guide = style.toStyleGuide()

            assertTrue(guide.contains("kotlin"))
            assertTrue(guide.contains("Indentation"))
            assertTrue(guide.contains("Brace Style"))
        }
    }

    // =========================================================================
    // CommunicationPreferences Tests
    // =========================================================================

    @Nested
    @DisplayName("CommunicationPreferences")
    inner class CommunicationPreferencesTests {

        @Test
        @DisplayName("default values are balanced")
        fun defaultValuesAreBalanced() {
            val prefs = CommunicationPreferences()

            assertEquals(VerbosityLevel.BALANCED, prefs.verbosity)
            assertEquals(ToneStyle.PROFESSIONAL, prefs.tone)
        }

        @Test
        @DisplayName("CONCISE preset has short limits")
        fun concisePresetHasShortLimits() {
            val prefs = CommunicationPreferences.CONCISE

            assertEquals(VerbosityLevel.CONCISE, prefs.verbosity)
            assertEquals(500, prefs.maxResponseLength)
        }

        @Test
        @DisplayName("DETAILED preset has long limits")
        fun detailedPresetHasLongLimits() {
            val prefs = CommunicationPreferences.DETAILED

            assertEquals(VerbosityLevel.DETAILED, prefs.verbosity)
            assertEquals(5000, prefs.maxResponseLength)
        }
    }

    // =========================================================================
    // FeedbackEntry Tests
    // =========================================================================

    @Nested
    @DisplayName("FeedbackEntry")
    inner class FeedbackEntryTests {

        @Test
        @DisplayName("positive factory creates positive feedback")
        fun positiveFactoryCreatesPositiveFeedback() {
            val feedback = FeedbackEntry.positive("refactor", FeedbackCategory.CODE_QUALITY, "Great!")

            assertEquals(FeedbackSentiment.POSITIVE, feedback.sentiment)
            assertTrue(feedback.isPositive)
            assertFalse(feedback.isNegative)
        }

        @Test
        @DisplayName("negative factory creates negative feedback")
        fun negativeFactoryCreatesNegativeFeedback() {
            val feedback = FeedbackEntry.negative("explain", FeedbackCategory.EXPLANATION, "Too verbose")

            assertEquals(FeedbackSentiment.NEGATIVE, feedback.sentiment)
            assertTrue(feedback.isNegative)
        }

        @Test
        @DisplayName("neutral factory creates neutral feedback")
        fun neutralFactoryCreatesNeutralFeedback() {
            val feedback = FeedbackEntry.neutral("task", FeedbackCategory.OTHER)

            assertEquals(FeedbackSentiment.NEUTRAL, feedback.sentiment)
            assertFalse(feedback.isPositive)
            assertFalse(feedback.isNegative)
        }
    }

    // =========================================================================
    // LearnedPattern Tests
    // =========================================================================

    @Nested
    @DisplayName("LearnedPattern")
    inner class LearnedPatternTests {

        @Test
        @DisplayName("codeStyle factory creates pattern")
        fun codeStyleFactoryCreatesPattern() {
            val pattern = LearnedPattern.codeStyle("var ", "val ", 0.8f)

            assertEquals(PatternType.CODE_STYLE, pattern.type)
            assertEquals("var ", pattern.pattern)
            assertEquals("val ", pattern.replacement)
        }

        @Test
        @DisplayName("isHighConfidence checks threshold")
        fun isHighConfidenceChecksThreshold() {
            val high = LearnedPattern.codeStyle("a", "b", 0.9f)
            assertTrue(high.isHighConfidence)

            val low = LearnedPattern.codeStyle("a", "b", 0.5f)
            assertFalse(low.isHighConfidence)
        }

        @Test
        @DisplayName("reinforce increases confidence")
        fun reinforceIncreasesConfidence() {
            val pattern = LearnedPattern.codeStyle("a", "b", 0.5f)

            val reinforced = pattern.reinforce(positive = true)

            assertEquals(0.6f, reinforced.confidence, 0.01f)
            assertEquals(2, reinforced.occurrences)
        }

        @Test
        @DisplayName("reinforce decreases on negative")
        fun reinforceDecreasesOnNegative() {
            val pattern = LearnedPattern.codeStyle("a", "b", 0.5f)

            val reinforced = pattern.reinforce(positive = false)

            assertEquals(0.35f, reinforced.confidence, 0.01f)
        }

        @Test
        @DisplayName("withExample adds example")
        fun withExampleAddsExample() {
            val pattern = LearnedPattern.codeStyle("a", "b")

            val withEx = pattern.withExample("example1").withExample("example2")

            assertEquals(2, withEx.examples.size)
        }

        @Test
        @DisplayName("examples limited to 5")
        fun examplesLimitedToFive() {
            var pattern = LearnedPattern.codeStyle("a", "b")

            repeat(10) { pattern = pattern.withExample("ex$it") }

            assertEquals(5, pattern.examples.size)
        }
    }

    // =========================================================================
    // PreferenceEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("PreferenceEvent")
    inner class PreferenceEventTests {

        @Test
        @DisplayName("events have required fields")
        fun eventsHaveRequiredFields() {
            val created = PreferenceEvent.ProfileCreated("p1", "u1")
            assertEquals("p1", created.profileId)
            assertNotNull(created.timestamp)

            val feedback = PreferenceEvent.FeedbackReceived(
                "p1",
                FeedbackSentiment.POSITIVE,
                FeedbackCategory.CODE_QUALITY
            )
            assertEquals(FeedbackSentiment.POSITIVE, feedback.sentiment)
        }
    }

    // =========================================================================
    // AdaptedPrompt Tests
    // =========================================================================

    @Nested
    @DisplayName("AdaptedPrompt")
    inner class AdaptedPromptTests {

        @Test
        @DisplayName("hasAdaptations checks preferences")
        fun hasAdaptationsChecksPreferences() {
            val adapted = AdaptedPrompt(
                "original",
                "adapted",
                listOf("verbosity", "tone"),
                null
            )
            assertTrue(adapted.hasAdaptations)

            val unadapted = AdaptedPrompt("original", "original", emptyList(), null)
            assertFalse(unadapted.hasAdaptations)
        }
    }

    // =========================================================================
    // Enum Tests
    // =========================================================================

    @Nested
    @DisplayName("Enums")
    inner class EnumTests {

        @Test
        @DisplayName("all enums have display names")
        fun allEnumsHaveDisplayNames() {
            IndentationStyle.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
            BraceStyle.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
            NamingConvention.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
            VerbosityLevel.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
            ToneStyle.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
            FeedbackCategory.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
            PatternType.entries.forEach { assertTrue(it.displayName.isNotBlank()) }
        }

        @Test
        @DisplayName("verbosity weights are ordered")
        fun verbosityWeightsAreOrdered() {
            val weights = VerbosityLevel.entries.map { it.weight }
            assertEquals(weights.sorted(), weights)
        }
    }
}
