package com.sidekick.agent.specialists

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Specialist Prompts.
 */
@DisplayName("Specialist Prompts Tests")
class SpecialistPromptsTest {

    @Nested
    @DisplayName("Core Prompts")
    inner class CorePromptsTests {

        @Test
        @DisplayName("all roles have prompts")
        fun allRolesHavePrompts() {
            AgentRole.entries.forEach { role ->
                val prompt = SpecialistPrompts.forRole(role)
                assertTrue(prompt.isNotBlank(), "Prompt for $role should not be blank")
            }
        }

        @Test
        @DisplayName("ARCHITECT prompt focuses on design")
        fun architectPromptFocusesOnDesign() {
            val prompt = SpecialistPrompts.ARCHITECT

            assertTrue(prompt.contains("architect", ignoreCase = true))
            assertTrue(prompt.contains("design", ignoreCase = true))
            assertTrue(prompt.contains("structure", ignoreCase = true))
        }

        @Test
        @DisplayName("IMPLEMENTER prompt focuses on code")
        fun implementerPromptFocusesOnCode() {
            val prompt = SpecialistPrompts.IMPLEMENTER

            assertTrue(prompt.contains("developer", ignoreCase = true))
            assertTrue(prompt.contains("code", ignoreCase = true))
            assertTrue(prompt.contains("implement", ignoreCase = true))
        }

        @Test
        @DisplayName("REVIEWER prompt focuses on quality")
        fun reviewerPromptFocusesOnQuality() {
            val prompt = SpecialistPrompts.REVIEWER

            assertTrue(prompt.contains("review", ignoreCase = true))
            assertTrue(prompt.contains("suggestions", ignoreCase = true) || 
                      prompt.contains("feedback", ignoreCase = true))
        }

        @Test
        @DisplayName("TESTER prompt focuses on tests")
        fun testerPromptFocusesOnTests() {
            val prompt = SpecialistPrompts.TESTER

            assertTrue(prompt.contains("test", ignoreCase = true))
            assertTrue(prompt.contains("coverage", ignoreCase = true))
        }

        @Test
        @DisplayName("SECURITY prompt focuses on vulnerabilities")
        fun securityPromptFocusesOnVulnerabilities() {
            val prompt = SpecialistPrompts.SECURITY

            assertTrue(prompt.contains("security", ignoreCase = true))
            assertTrue(prompt.contains("vulnerab", ignoreCase = true))
        }
    }

    @Nested
    @DisplayName("Prompt Retrieval")
    inner class PromptRetrievalTests {

        @Test
        @DisplayName("forRole returns correct prompt")
        fun forRoleReturnsCorrectPrompt() {
            assertEquals(SpecialistPrompts.ARCHITECT, SpecialistPrompts.forRole(AgentRole.ARCHITECT))
            assertEquals(SpecialistPrompts.IMPLEMENTER, SpecialistPrompts.forRole(AgentRole.IMPLEMENTER))
            assertEquals(SpecialistPrompts.DEBUGGER, SpecialistPrompts.forRole(AgentRole.DEBUGGER))
        }

        @Test
        @DisplayName("all returns map of all prompts")
        fun allReturnsMapOfAllPrompts() {
            val all = SpecialistPrompts.all()

            assertEquals(AgentRole.entries.size, all.size)
            AgentRole.entries.forEach { role ->
                assertTrue(all.containsKey(role))
            }
        }
    }

    @Nested
    @DisplayName("Prompt Customization")
    inner class PromptCustomizationTests {

        @Test
        @DisplayName("withProjectContext adds context")
        fun withProjectContextAddsContext() {
            val prompt = SpecialistPrompts.withProjectContext(
                AgentRole.IMPLEMENTER,
                "MyProject",
                listOf("Kotlin", "Java")
            )

            assertTrue(prompt.contains("MyProject"))
            assertTrue(prompt.contains("Kotlin"))
            assertTrue(prompt.contains("Java"))
        }

        @Test
        @DisplayName("withCollaboration adds collaborators")
        fun withCollaborationAddsCollaborators() {
            val prompt = SpecialistPrompts.withCollaboration(
                AgentRole.ARCHITECT,
                setOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, AgentRole.REVIEWER)
            )

            assertTrue(prompt.contains("Implementer"))
            assertTrue(prompt.contains("Reviewer"))
            assertFalse(prompt.contains("Architect", ignoreCase = false) && 
                       prompt.contains("Available Collaborators"))
        }

        @Test
        @DisplayName("forTask adds task focus")
        fun forTaskAddsTaskFocus() {
            val analysisPrompt = SpecialistPrompts.forTask(AgentRole.REVIEWER, TaskFocus.ANALYSIS)
            assertTrue(analysisPrompt.contains("Analysis"))

            val implementPrompt = SpecialistPrompts.forTask(AgentRole.IMPLEMENTER, TaskFocus.IMPLEMENTATION)
            assertTrue(implementPrompt.contains("Implementation"))

            val debugPrompt = SpecialistPrompts.forTask(AgentRole.DEBUGGER, TaskFocus.DEBUGGING)
            assertTrue(debugPrompt.contains("Debugging"))
        }
    }
}
