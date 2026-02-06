package com.sidekick.agent.preferences

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Preference Service.
 */
@DisplayName("Preference Service Tests")
class PreferenceServiceTest {

    private lateinit var service: PreferenceService

    @BeforeEach
    fun setUp() {
        service = PreferenceService()
    }

    // =========================================================================
    // Profile Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Profile Management")
    inner class ProfileManagementTests {

        @Test
        @DisplayName("getOrCreateProfile creates new profile")
        fun getOrCreateProfileCreatesNewProfile() {
            val profile = service.getOrCreateProfile("user1")

            assertNotNull(profile)
            assertEquals("user1", profile.userId)
        }

        @Test
        @DisplayName("getOrCreateProfile returns existing profile")
        fun getOrCreateProfileReturnsExistingProfile() {
            val first = service.getOrCreateProfile("user1")
            val second = service.getOrCreateProfile("user1")

            assertEquals(first.id, second.id)
        }

        @Test
        @DisplayName("getProfile returns null for unknown user")
        fun getProfileReturnsNullForUnknownUser() {
            val profile = service.getProfile("unknown")

            assertNull(profile)
        }

        @Test
        @DisplayName("updateProfile modifies profile")
        fun updateProfileModifiesProfile() {
            service.getOrCreateProfile("user1")

            val updated = service.updateProfile("user1") {
                it.withCodingStyle(CodingStylePreferences(language = "java"))
            }

            assertEquals("java", updated?.codingStyle?.language)
        }

        @Test
        @DisplayName("deleteProfile removes profile")
        fun deleteProfileRemovesProfile() {
            service.getOrCreateProfile("user1")

            val deleted = service.deleteProfile("user1")

            assertTrue(deleted)
            assertNull(service.getProfile("user1"))
        }
    }

    // =========================================================================
    // Coding Style Tests
    // =========================================================================

    @Nested
    @DisplayName("Coding Style")
    inner class CodingStyleTests {

        @Test
        @DisplayName("updateCodingStyle updates preferences")
        fun updateCodingStyleUpdatesPreferences() {
            service.getOrCreateProfile("user1")
            val newStyle = CodingStylePreferences(language = "typescript", maxLineLength = 80)

            val updated = service.updateCodingStyle("user1", newStyle)

            assertEquals("typescript", updated?.codingStyle?.language)
            assertEquals(80, updated?.codingStyle?.maxLineLength)
        }

        @Test
        @DisplayName("inferCodingStyle detects indentation")
        fun inferCodingStyleDetectsIndentation() {
            val code = """
                fun test() {
                    println("hello")
                }
            """.trimIndent()

            val style = service.inferCodingStyle(code)

            assertEquals(IndentationStyle.SPACES_4, style.indentation)
        }

        @Test
        @DisplayName("inferCodingStyle detects brace style")
        fun inferCodingStyleDetectsBraceStyle() {
            val sameLineCode = "fun test() { println() }"
            val style = service.inferCodingStyle(sameLineCode)

            assertEquals(BraceStyle.SAME_LINE, style.braceStyle)
        }

        @Test
        @DisplayName("inferCodingStyle detects naming convention")
        fun inferCodingStyleDetectsNamingConvention() {
            val camelCode = "fun myFunction() { }\nfun anotherFunction() { }"
            val style = service.inferCodingStyle(camelCode)

            assertEquals(NamingConvention.CAMEL_CASE, style.namingConvention)
        }

        @Test
        @DisplayName("inferCodingStyle detects functional style")
        fun inferCodingStyleDetectsFunctionalStyle() {
            val functionalCode = "list.map { it * 2 }.filter { it > 0 }"
            val style = service.inferCodingStyle(functionalCode)

            assertTrue(style.preferFunctionalStyle)
        }
    }

    // =========================================================================
    // Communication Style Tests
    // =========================================================================

    @Nested
    @DisplayName("Communication Style")
    inner class CommunicationStyleTests {

        @Test
        @DisplayName("updateCommunicationStyle updates preferences")
        fun updateCommunicationStyleUpdatesPreferences() {
            service.getOrCreateProfile("user1")
            val newStyle = CommunicationPreferences(
                verbosity = VerbosityLevel.DETAILED,
                tone = ToneStyle.CASUAL
            )

            val updated = service.updateCommunicationStyle("user1", newStyle)

            assertEquals(VerbosityLevel.DETAILED, updated?.communicationStyle?.verbosity)
            assertEquals(ToneStyle.CASUAL, updated?.communicationStyle?.tone)
        }

        @Test
        @DisplayName("adjustVerbosity increases level")
        fun adjustVerbosityIncreasesLevel() {
            service.getOrCreateProfile("user1")

            val updated = service.adjustVerbosity("user1", increase = true)

            // Default is BALANCED, should become DETAILED
            assertEquals(VerbosityLevel.DETAILED, updated?.communicationStyle?.verbosity)
        }

        @Test
        @DisplayName("adjustVerbosity decreases level")
        fun adjustVerbosityDecreasesLevel() {
            service.getOrCreateProfile("user1")

            val updated = service.adjustVerbosity("user1", increase = false)

            // Default is BALANCED, should become CONCISE
            assertEquals(VerbosityLevel.CONCISE, updated?.communicationStyle?.verbosity)
        }
    }

    // =========================================================================
    // Feedback Tests
    // =========================================================================

    @Nested
    @DisplayName("Feedback")
    inner class FeedbackTests {

        @Test
        @DisplayName("recordFeedback adds to profile")
        fun recordFeedbackAddsToProfile() {
            service.getOrCreateProfile("user1")

            service.recordFeedback(
                "user1",
                "refactor",
                FeedbackSentiment.POSITIVE,
                FeedbackCategory.CODE_QUALITY
            )

            val profile = service.getProfile("user1")
            assertEquals(1, profile?.feedbackCount)
        }

        @Test
        @DisplayName("recordPositiveFeedback creates positive entry")
        fun recordPositiveFeedbackCreatesPositiveEntry() {
            service.getOrCreateProfile("user1")

            val feedback = service.recordPositiveFeedback(
                "user1",
                "task",
                FeedbackCategory.ACCURACY
            )

            assertTrue(feedback.isPositive)
        }

        @Test
        @DisplayName("recordNegativeFeedback creates negative entry")
        fun recordNegativeFeedbackCreatesNegativeEntry() {
            service.getOrCreateProfile("user1")

            val feedback = service.recordNegativeFeedback(
                "user1",
                "task",
                FeedbackCategory.COMPLETENESS
            )

            assertTrue(feedback.isNegative)
        }

        @Test
        @DisplayName("negative feedback about verbosity triggers adjustment")
        fun negativeFeedbackAboutVerbosityTriggersAdjustment() {
            val configWithAutoLearn = LearningConfig(enableAutoLearning = true)
            val serviceWithLearn = PreferenceService(configWithAutoLearn)
            serviceWithLearn.getOrCreateProfile("user1")

            serviceWithLearn.recordNegativeFeedback(
                "user1",
                "explanation",
                FeedbackCategory.EXPLANATION,
                "Too verbose, way too much text"
            )

            val profile = serviceWithLearn.getProfile("user1")
            // Should have decreased verbosity
            assertTrue(profile?.communicationStyle?.verbosity?.ordinal ?: 5 < VerbosityLevel.BALANCED.ordinal)
        }
    }

    // =========================================================================
    // Pattern Learning Tests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Learning")
    inner class PatternLearningTests {

        @Test
        @DisplayName("learnPattern creates new pattern")
        fun learnPatternCreatesNewPattern() {
            service.getOrCreateProfile("user1")

            val pattern = service.learnPattern(
                "user1",
                PatternType.CODE_STYLE,
                "var ",
                "val "
            )

            assertEquals(PatternType.CODE_STYLE, pattern.type)
            assertEquals("var ", pattern.pattern)
            assertEquals("val ", pattern.replacement)
        }

        @Test
        @DisplayName("learnPattern reinforces existing pattern")
        fun learnPatternReinforcesExistingPattern() {
            service.getOrCreateProfile("user1")

            val first = service.learnPattern("user1", PatternType.CODE_STYLE, "var ", "val ")
            val second = service.learnPattern("user1", PatternType.CODE_STYLE, "var ", "val ")

            assertTrue(second.confidence > first.confidence)
            assertEquals(2, second.occurrences)
        }

        @Test
        @DisplayName("learnStyleCorrection creates patterns from diff")
        fun learnStyleCorrectionCreatesPatternsFromDiff() {
            service.getOrCreateProfile("user1")

            val patterns = service.learnStyleCorrection(
                "user1",
                "var x = 1",
                "val x = 1"
            )

            assertTrue(patterns.isNotEmpty())
        }

        @Test
        @DisplayName("getPatterns filters by type")
        fun getPatternsFiltersByType() {
            service.getOrCreateProfile("user1")
            service.learnPattern("user1", PatternType.CODE_STYLE, "a", "b")
            service.learnPattern("user1", PatternType.NAMING_CONVENTION, "c", "d")

            val codeStylePatterns = service.getPatterns("user1", PatternType.CODE_STYLE)
            val allPatterns = service.getPatterns("user1")

            assertEquals(1, codeStylePatterns.size)
            assertEquals(2, allPatterns.size)
        }

        @Test
        @DisplayName("getHighConfidencePatterns filters by confidence")
        fun getHighConfidencePatternsFiltersByConfidence() {
            service.getOrCreateProfile("user1")

            // Create pattern and reinforce to high confidence
            repeat(5) {
                service.learnPattern("user1", PatternType.CODE_STYLE, "high", "conf")
            }
            service.learnPattern("user1", PatternType.CODE_STYLE, "low", "conf")

            val highConf = service.getHighConfidencePatterns("user1")

            assertTrue(highConf.any { it.pattern == "high" })
        }
    }

    // =========================================================================
    // Prompt Adaptation Tests
    // =========================================================================

    @Nested
    @DisplayName("Prompt Adaptation")
    inner class PromptAdaptationTests {

        @Test
        @DisplayName("adaptPrompt adds style guide")
        fun adaptPromptAddsStyleGuide() {
            service.getOrCreateProfile("user1")

            val adapted = service.adaptPrompt("user1", "Write a function")

            assertTrue(adapted.adaptedPrompt.contains("Coding Style Guide"))
            assertTrue(adapted.appliedPreferences.contains("coding_style"))
        }

        @Test
        @DisplayName("adaptPrompt adds verbosity instruction")
        fun adaptPromptAddsVerbosityInstruction() {
            service.getOrCreateProfile("user1")
            service.updateCommunicationStyle("user1", CommunicationPreferences(
                verbosity = VerbosityLevel.TERSE
            ))

            val adapted = service.adaptPrompt("user1", "Explain this")

            assertTrue(adapted.adaptedPrompt.contains("brief", ignoreCase = true))
        }

        @Test
        @DisplayName("adaptPrompt includes learned patterns")
        fun adaptPromptIncludesLearnedPatterns() {
            service.getOrCreateProfile("user1")
            // Create high-confidence pattern
            repeat(5) {
                service.learnPattern("user1", PatternType.CODE_STYLE, "var", "val")
            }

            val adapted = service.adaptPrompt("user1", "Write code")

            assertTrue(adapted.appliedPreferences.contains("learned_patterns"))
        }

        @Test
        @DisplayName("getPromptModifiers returns preference map")
        fun getPromptModifiersReturnsPreferenceMap() {
            service.getOrCreateProfile("user1")

            val modifiers = service.getPromptModifiers("user1")

            assertTrue("language" in modifiers)
            assertTrue("verbosity" in modifiers)
            assertTrue("tone" in modifiers)
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
        fun getStatsReturnsCorrectCounts() {
            service.getOrCreateProfile("user1")
            service.getOrCreateProfile("user2")
            service.recordPositiveFeedback("user1", "task", FeedbackCategory.CODE_QUALITY)
            service.recordNegativeFeedback("user1", "task", FeedbackCategory.ACCURACY)
            service.learnPattern("user1", PatternType.CODE_STYLE, "a", "b")

            val stats = service.getStats()

            assertEquals(2, stats.totalProfiles)
            assertEquals(2, stats.totalFeedback)
            assertEquals(1, stats.positiveFeedback)
            assertEquals(1, stats.negativeFeedback)
            assertEquals(1, stats.totalPatterns)
        }

        @Test
        @DisplayName("feedbackPositiveRate calculates correctly")
        fun feedbackPositiveRateCalculatesCorrectly() {
            service.getOrCreateProfile("user1")
            service.recordPositiveFeedback("user1", "t1", FeedbackCategory.CODE_QUALITY)
            service.recordPositiveFeedback("user1", "t2", FeedbackCategory.CODE_QUALITY)
            service.recordNegativeFeedback("user1", "t3", FeedbackCategory.ACCURACY)

            val stats = service.getStats()

            assertEquals(2f / 3f, stats.feedbackPositiveRate, 0.01f)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits profile created event")
        fun emitsProfileCreatedEvent() {
            var received: PreferenceEvent? = null
            service.addListener { received = it }

            service.getOrCreateProfile("user1")

            assertTrue(received is PreferenceEvent.ProfileCreated)
        }

        @Test
        @DisplayName("emits feedback received event")
        fun emitsFeedbackReceivedEvent() {
            val events = mutableListOf<PreferenceEvent>()
            service.addListener { events.add(it) }

            service.getOrCreateProfile("user1")
            service.recordPositiveFeedback("user1", "task", FeedbackCategory.CODE_QUALITY)

            assertTrue(events.any { it is PreferenceEvent.FeedbackReceived })
        }

        @Test
        @DisplayName("emits pattern learned event")
        fun emitsPatternLearnedEvent() {
            val events = mutableListOf<PreferenceEvent>()
            service.addListener { events.add(it) }

            service.getOrCreateProfile("user1")
            service.learnPattern("user1", PatternType.CODE_STYLE, "a", "b")

            assertTrue(events.any { it is PreferenceEvent.PatternLearned })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() {
            var count = 0
            val listener: (PreferenceEvent) -> Unit = { count++ }
            service.addListener(listener)

            service.getOrCreateProfile("user1")
            val firstCount = count

            service.removeListener(listener)
            service.getOrCreateProfile("user2")

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
        @DisplayName("clearProfiles removes all")
        fun clearProfilesRemovesAll() {
            service.getOrCreateProfile("user1")
            service.getOrCreateProfile("user2")

            service.clearProfiles()

            assertEquals(0, service.getAllProfiles().size)
        }
    }
}
