package com.sidekick.agent.specialists

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Specialist Models.
 */
@DisplayName("Specialist Models Tests")
class SpecialistModelsTest {

    // =========================================================================
    // SpecialistAgent Tests
    // =========================================================================

    @Nested
    @DisplayName("SpecialistAgent")
    inner class SpecialistAgentTests {

        @Test
        @DisplayName("displayName includes icon and name")
        fun displayNameIncludesIconAndName() {
            val agent = createAgent(AgentRole.ARCHITECT)
            assertTrue(agent.displayName.contains("🏗️"))
            assertTrue(agent.displayName.contains("Architect"))
        }

        @Test
        @DisplayName("canPerform checks capabilities")
        fun canPerformChecksCapabilities() {
            val agent = createAgent(AgentRole.IMPLEMENTER)

            assertTrue(agent.canPerform(Capability.WRITE_CODE))
            assertFalse(agent.canPerform(Capability.DELETE_FILES))
        }

        @Test
        @DisplayName("canModifyFiles checks write capabilities")
        fun canModifyFilesChecksWriteCapabilities() {
            val implementer = createAgent(AgentRole.IMPLEMENTER)
            assertTrue(implementer.canModifyFiles)

            val reviewer = createAgent(AgentRole.REVIEWER)
            assertFalse(reviewer.canModifyFiles)
        }

        @Test
        @DisplayName("isReadOnly checks modification capabilities")
        fun isReadOnlyChecksModificationCapabilities() {
            val reviewer = createAgent(AgentRole.REVIEWER)
            assertTrue(reviewer.isReadOnly)

            val implementer = createAgent(AgentRole.IMPLEMENTER)
            assertFalse(implementer.isReadOnly)
        }

        @Test
        @DisplayName("createRequest builds request")
        fun createRequestBuildsRequest() {
            val agent = createAgent(AgentRole.TESTER)
            val request = agent.createRequest("Write tests", "context")

            assertEquals(agent.id, request.agentId)
            assertEquals(AgentRole.TESTER, request.role)
            assertEquals("Write tests", request.prompt)
            assertEquals("context", request.context)
        }

        private fun createAgent(role: AgentRole): SpecialistAgent {
            return SpecialistAgent(
                role = role,
                systemPrompt = SpecialistPrompts.forRole(role),
                capabilities = AgentRole.defaultCapabilities(role)
            )
        }
    }

    // =========================================================================
    // AgentRole Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentRole")
    inner class AgentRoleTests {

        @Test
        @DisplayName("all roles have display names and icons")
        fun allRolesHaveDisplayNamesAndIcons() {
            AgentRole.entries.forEach { role ->
                assertTrue(role.displayName.isNotBlank())
                assertTrue(role.icon.isNotBlank())
                assertTrue(role.description.isNotBlank())
            }
        }

        @Test
        @DisplayName("PRIMARY_ROLES contains core roles")
        fun primaryRolesContainsCoreRoles() {
            assertTrue(AgentRole.ARCHITECT in AgentRole.PRIMARY_ROLES)
            assertTrue(AgentRole.IMPLEMENTER in AgentRole.PRIMARY_ROLES)
            assertTrue(AgentRole.REVIEWER in AgentRole.PRIMARY_ROLES)
            assertTrue(AgentRole.TESTER in AgentRole.PRIMARY_ROLES)
        }

        @Test
        @DisplayName("SUPPORTING_ROLES contains specialized roles")
        fun supportingRolesContainsSpecializedRoles() {
            assertTrue(AgentRole.DEBUGGER in AgentRole.SUPPORTING_ROLES)
            assertTrue(AgentRole.OPTIMIZER in AgentRole.SUPPORTING_ROLES)
            assertTrue(AgentRole.SECURITY in AgentRole.SUPPORTING_ROLES)
        }

        @Test
        @DisplayName("defaultCapabilities provides role-specific capabilities")
        fun defaultCapabilitiesProvides() {
            val archCaps = AgentRole.defaultCapabilities(AgentRole.ARCHITECT)
            assertTrue(Capability.READ_CODE in archCaps)
            assertTrue(Capability.DELEGATE_TASKS in archCaps)
            assertFalse(Capability.WRITE_CODE in archCaps)

            val implCaps = AgentRole.defaultCapabilities(AgentRole.IMPLEMENTER)
            assertTrue(Capability.WRITE_CODE in implCaps)
            assertTrue(Capability.CREATE_FILES in implCaps)
        }
    }

    // =========================================================================
    // Capability Tests
    // =========================================================================

    @Nested
    @DisplayName("Capability")
    inner class CapabilityTests {

        @Test
        @DisplayName("all capabilities have display names")
        fun allCapabilitiesHaveDisplayNames() {
            Capability.entries.forEach { cap ->
                assertTrue(cap.displayName.isNotBlank())
                assertTrue(cap.description.isNotBlank())
            }
        }

        @Test
        @DisplayName("MODIFYING contains write capabilities")
        fun modifyingContainsWriteCapabilities() {
            assertTrue(Capability.WRITE_CODE in Capability.MODIFYING)
            assertTrue(Capability.CREATE_FILES in Capability.MODIFYING)
            assertTrue(Capability.DELETE_FILES in Capability.MODIFYING)
            assertFalse(Capability.READ_CODE in Capability.MODIFYING)
        }

        @Test
        @DisplayName("READ_ONLY contains analysis capabilities")
        fun readOnlyContainsAnalysisCapabilities() {
            assertTrue(Capability.READ_CODE in Capability.READ_ONLY)
            assertTrue(Capability.ANALYZE_AST in Capability.READ_ONLY)
            assertFalse(Capability.WRITE_CODE in Capability.READ_ONLY)
        }
    }

    // =========================================================================
    // SpecialistRequest Tests
    // =========================================================================

    @Nested
    @DisplayName("SpecialistRequest")
    inner class SpecialistRequestTests {

        @Test
        @DisplayName("withFiles adds files")
        fun withFilesAddsFiles() {
            val request = SpecialistRequest(
                agentId = "agent1",
                role = AgentRole.REVIEWER,
                prompt = "Review code"
            )

            val updated = request.withFiles(listOf("file1.kt", "file2.kt"))

            assertEquals(2, updated.referencedFiles.size)
            assertTrue("file1.kt" in updated.referencedFiles)
        }

        @Test
        @DisplayName("withContext appends context")
        fun withContextAppendsContext() {
            val request = SpecialistRequest(
                agentId = "agent1",
                role = AgentRole.IMPLEMENTER,
                prompt = "Implement feature",
                context = "Initial context"
            )

            val updated = request.withContext("Additional context")

            assertTrue(updated.context?.contains("Initial context") == true)
            assertTrue(updated.context?.contains("Additional context") == true)
        }
    }

    // =========================================================================
    // AgentResponse Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentResponse")
    inner class AgentResponseTests {

        @Test
        @DisplayName("suggestsDelegation checks delegateTo")
        fun suggestsDelegationChecksDelegateTo() {
            val noDelegation = AgentResponse.simple("r1", "a1", AgentRole.ARCHITECT, "Response")
            assertFalse(noDelegation.suggestsDelegation)

            val withDelegation = AgentResponse.delegate(
                "r1", "a1", AgentRole.ARCHITECT, "Need implementation", AgentRole.IMPLEMENTER
            )
            assertTrue(withDelegation.suggestsDelegation)
        }

        @Test
        @DisplayName("isHighConfidence checks threshold")
        fun isHighConfidenceChecksThreshold() {
            val high = AgentResponse.simple("r1", "a1", AgentRole.REVIEWER, "Approved")
            assertTrue(high.isHighConfidence) // Default is 0.8

            val low = AgentResponse(
                requestId = "r1",
                agentId = "a1",
                role = AgentRole.DEBUGGER,
                content = "Maybe...",
                confidence = 0.5f
            )
            assertFalse(low.isHighConfidence)
        }

        @Test
        @DisplayName("getActionsByPriority sorts descending")
        fun getActionsByPrioritySortsDescending() {
            val response = AgentResponse(
                requestId = "r1",
                agentId = "a1",
                role = AgentRole.REVIEWER,
                content = "Review",
                suggestedActions = listOf(
                    SuggestedAction("low", "Low priority", 2),
                    SuggestedAction("high", "High priority", 9),
                    SuggestedAction("mid", "Mid priority", 5)
                )
            )

            val sorted = response.getActionsByPriority()

            assertEquals("high", sorted[0].action)
            assertEquals("mid", sorted[1].action)
            assertEquals("low", sorted[2].action)
        }

        @Test
        @DisplayName("getCodeArtifacts filters correctly")
        fun getCodeArtifactsFiltersCorrectly() {
            val response = AgentResponse(
                requestId = "r1",
                agentId = "a1",
                role = AgentRole.IMPLEMENTER,
                content = "Implementation",
                artifacts = listOf(
                    ResponseArtifact.code("Code", "content", "/file.kt", "kotlin"),
                    ResponseArtifact.documentation("Docs", "docs content"),
                    ResponseArtifact.code("More code", "more", "/other.kt", "kotlin")
                )
            )

            val codeArtifacts = response.getCodeArtifacts()

            assertEquals(2, codeArtifacts.size)
            assertTrue(codeArtifacts.all { it.isCode })
        }
    }

    // =========================================================================
    // SuggestedAction Tests
    // =========================================================================

    @Nested
    @DisplayName("SuggestedAction")
    inner class SuggestedActionTests {

        @Test
        @DisplayName("factory methods create correct actions")
        fun factoryMethodsCreateCorrectActions() {
            val refactor = SuggestedAction.refactor("Extract method")
            assertEquals(ActionCategory.REFACTORING, refactor.category)
            assertEquals(7, refactor.priority)

            val test = SuggestedAction.test("Add unit tests")
            assertEquals(ActionCategory.TESTING, test.category)

            val fix = SuggestedAction.fix("Fix null check")
            assertEquals(ActionCategory.FIX, fix.category)
            assertEquals(8, fix.priority) // Fixes are high priority
        }
    }

    // =========================================================================
    // ResponseArtifact Tests
    // =========================================================================

    @Nested
    @DisplayName("ResponseArtifact")
    inner class ResponseArtifactTests {

        @Test
        @DisplayName("isCode checks type")
        fun isCodeChecksType() {
            val code = ResponseArtifact.code("Test", "code", "/test.kt", "kotlin")
            assertTrue(code.isCode)

            val docs = ResponseArtifact.documentation("Docs", "content")
            assertFalse(docs.isCode)
        }

        @Test
        @DisplayName("lineCount counts lines")
        fun lineCountCountsLines() {
            val artifact = ResponseArtifact.code(
                "Multi-line",
                "line1\nline2\nline3",
                "/file.kt",
                "kotlin"
            )
            assertEquals(3, artifact.lineCount)
        }
    }

    // =========================================================================
    // ReviewFeedback Tests
    // =========================================================================

    @Nested
    @DisplayName("ReviewFeedback")
    inner class ReviewFeedbackTests {

        @Test
        @DisplayName("counts severity correctly")
        fun countsSeverityCorrectly() {
            val feedback = ReviewFeedback(
                items = listOf(
                    ReviewItem(ReviewSeverity.CRITICAL, ReviewCategory.BUG, "Bug"),
                    ReviewItem(ReviewSeverity.CRITICAL, ReviewCategory.SECURITY, "Security"),
                    ReviewItem(ReviewSeverity.IMPORTANT, ReviewCategory.STYLE, "Style"),
                    ReviewItem(ReviewSeverity.SUGGESTION, ReviewCategory.PERFORMANCE, "Perf")
                ),
                overallAssessment = "Needs work",
                approved = false,
                confidence = 0.8f
            )

            assertEquals(2, feedback.criticalCount)
            assertEquals(1, feedback.importantCount)
            assertEquals(1, feedback.suggestionCount)
        }

        @Test
        @DisplayName("hasCriticalIssues checks count")
        fun hasCriticalIssuesChecksCount() {
            val withCritical = ReviewFeedback(
                items = listOf(ReviewItem(ReviewSeverity.CRITICAL, ReviewCategory.BUG, "Bug")),
                overallAssessment = "Fix required",
                approved = false,
                confidence = 0.9f
            )
            assertTrue(withCritical.hasCriticalIssues)

            val noCritical = ReviewFeedback(
                items = listOf(ReviewItem(ReviewSeverity.SUGGESTION, ReviewCategory.STYLE, "Style")),
                overallAssessment = "Looks good",
                approved = true,
                confidence = 0.9f
            )
            assertFalse(noCritical.hasCriticalIssues)
        }
    }

    // =========================================================================
    // SpecialistEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("SpecialistEvent")
    inner class SpecialistEventTests {

        @Test
        @DisplayName("events have required fields")
        fun eventsHaveRequiredFields() {
            val invoked = SpecialistEvent.AgentInvoked("a1", AgentRole.ARCHITECT, "r1", "prompt")
            assertEquals("a1", invoked.agentId)
            assertEquals(AgentRole.ARCHITECT, invoked.role)
            assertNotNull(invoked.timestamp)

            val delegated = SpecialistEvent.AgentDelegated(
                "a1", AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, "reason"
            )
            assertEquals(AgentRole.IMPLEMENTER, delegated.delegateTo)
        }
    }
}
