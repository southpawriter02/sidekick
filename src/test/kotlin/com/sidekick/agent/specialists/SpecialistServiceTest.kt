package com.sidekick.agent.specialists

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Specialist Service.
 */
@DisplayName("Specialist Service Tests")
class SpecialistServiceTest {

    private lateinit var service: SpecialistService

    @BeforeEach
    fun setUp() {
        service = SpecialistService("/test/project") { agent, prompt, _ ->
            "Response from ${agent.role.displayName}: $prompt"
        }
    }

    // =========================================================================
    // Specialist Creation Tests
    // =========================================================================

    @Nested
    @DisplayName("Specialist Creation")
    inner class SpecialistCreationTests {

        @Test
        @DisplayName("initializes all specialists")
        fun initializesAllSpecialists() {
            val all = service.getAllSpecialists()

            assertEquals(AgentRole.entries.size, all.size)
            AgentRole.entries.forEach { role ->
                assertTrue(all.any { it.role == role })
            }
        }

        @Test
        @DisplayName("createCustomSpecialist creates with overrides")
        fun createCustomSpecialistCreatesWithOverrides() {
            val custom = service.createCustomSpecialist(
                role = AgentRole.IMPLEMENTER,
                customPrompt = "Custom prompt",
                additionalCapabilities = setOf(Capability.DELETE_FILES),
                temperature = 0.5f
            )

            assertEquals("Custom prompt", custom.systemPrompt)
            assertTrue(custom.canPerform(Capability.DELETE_FILES))
            assertEquals(0.5f, custom.temperature)
        }
    }

    // =========================================================================
    // Specialist Retrieval Tests
    // =========================================================================

    @Nested
    @DisplayName("Specialist Retrieval")
    inner class SpecialistRetrievalTests {

        @Test
        @DisplayName("getSpecialist returns specialist by role")
        fun getSpecialistReturnsSpecialistByRole() {
            val architect = service.getSpecialist(AgentRole.ARCHITECT)

            assertEquals(AgentRole.ARCHITECT, architect.role)
            assertTrue(architect.capabilities.isNotEmpty())
        }

        @Test
        @DisplayName("getSpecialistsWithCapability filters correctly")
        fun getSpecialistsWithCapabilityFiltersCorrectly() {
            val writers = service.getSpecialistsWithCapability(Capability.WRITE_CODE)

            assertTrue(writers.any { it.role == AgentRole.IMPLEMENTER })
            assertTrue(writers.any { it.role == AgentRole.TESTER })
            assertFalse(writers.any { it.role == AgentRole.REVIEWER })
        }

        @Test
        @DisplayName("getPrimarySpecialists returns primary roles")
        fun getPrimarySpecialistsReturnsPrimaryRoles() {
            val primary = service.getPrimarySpecialists()

            assertEquals(AgentRole.PRIMARY_ROLES.size, primary.size)
            assertTrue(primary.any { it.role == AgentRole.ARCHITECT })
            assertTrue(primary.any { it.role == AgentRole.IMPLEMENTER })
        }

        @Test
        @DisplayName("getSupportingSpecialists returns supporting roles")
        fun getSupportingSpecialistsReturnsSupportingRoles() {
            val supporting = service.getSupportingSpecialists()

            assertEquals(AgentRole.SUPPORTING_ROLES.size, supporting.size)
            assertTrue(supporting.any { it.role == AgentRole.DEBUGGER })
            assertTrue(supporting.any { it.role == AgentRole.SECURITY })
        }
    }

    // =========================================================================
    // Invocation Tests
    // =========================================================================

    @Nested
    @DisplayName("Specialist Invocation")
    inner class InvocationTests {

        @Test
        @DisplayName("invoke returns response")
        fun invokeReturnsResponse() = runBlocking {
            val response = service.invoke(AgentRole.ARCHITECT, "Design a module")

            assertEquals(AgentRole.ARCHITECT, response.role)
            assertTrue(response.content.contains("Architect"))
        }

        @Test
        @DisplayName("invoke with context includes context")
        fun invokeWithContextIncludesContext() = runBlocking {
            val response = service.invoke(
                AgentRole.REVIEWER,
                "Review this code",
                "fun test() = 42"
            )

            assertEquals(AgentRole.REVIEWER, response.role)
            assertNotNull(response.timestamp)
        }

        @Test
        @DisplayName("invokeChain returns ordered responses")
        fun invokeChainReturnsOrderedResponses() = runBlocking {
            val responses = service.invokeChain(
                listOf(AgentRole.ARCHITECT, AgentRole.IMPLEMENTER, AgentRole.TESTER),
                "Build a feature"
            )

            assertEquals(3, responses.size)
            assertEquals(AgentRole.ARCHITECT, responses[0].role)
            assertEquals(AgentRole.IMPLEMENTER, responses[1].role)
            assertEquals(AgentRole.TESTER, responses[2].role)
        }

        @Test
        @DisplayName("invokeParallel returns map of responses")
        fun invokeParallelReturnsMapOfResponses() = runBlocking {
            val responses = service.invokeParallel(
                listOf(AgentRole.REVIEWER, AgentRole.SECURITY),
                "Review security"
            )

            assertEquals(2, responses.size)
            assertTrue(responses.containsKey(AgentRole.REVIEWER))
            assertTrue(responses.containsKey(AgentRole.SECURITY))
        }
    }

    // =========================================================================
    // Delegation Tests
    // =========================================================================

    @Nested
    @DisplayName("Delegation")
    inner class DelegationTests {

        @Test
        @DisplayName("delegate creates response from target")
        fun delegateCreatesResponseFromTarget() = runBlocking {
            val response = service.delegate(
                AgentRole.ARCHITECT,
                AgentRole.IMPLEMENTER,
                "Implement the design"
            )

            assertEquals(AgentRole.IMPLEMENTER, response.role)
        }

        @Test
        @DisplayName("suggestSpecialist recommends correct role")
        fun suggestSpecialistRecommendsCorrectRole() {
            assertEquals(AgentRole.ARCHITECT, service.suggestSpecialist("design the architecture"))
            assertEquals(AgentRole.IMPLEMENTER, service.suggestSpecialist("implement the feature"))
            assertEquals(AgentRole.TESTER, service.suggestSpecialist("run unit tests"))
            assertEquals(AgentRole.DEBUGGER, service.suggestSpecialist("fix this bug"))
            assertEquals(AgentRole.SECURITY, service.suggestSpecialist("assess vulnerability risk"))
            assertEquals(AgentRole.OPTIMIZER, service.suggestSpecialist("optimize performance"))
        }
    }

    // =========================================================================
    // Review Loop Tests
    // =========================================================================

    @Nested
    @DisplayName("Review Loop")
    inner class ReviewLoopTests {

        @Test
        @DisplayName("implementReviewLoop runs iterations")
        fun implementReviewLoopRunsIterations() = runBlocking {
            val result = service.implementReviewLoop(
                "Write a function",
                maxIterations = 2
            )

            assertTrue(result.finalContent.isNotBlank())
            assertTrue(result.iterations >= 1)
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
        fun getStatsReturnsCorrectCounts() = runBlocking {
            service.invoke(AgentRole.ARCHITECT, "Design")
            service.invoke(AgentRole.ARCHITECT, "More design")
            service.invoke(AgentRole.IMPLEMENTER, "Implement")

            val stats = service.getStats()

            assertEquals(3, stats.totalInvocations)
            assertEquals(2, stats.invocationsByRole[AgentRole.ARCHITECT])
            assertEquals(1, stats.invocationsByRole[AgentRole.IMPLEMENTER])
        }

        @Test
        @DisplayName("getHistoryForRole filters correctly")
        fun getHistoryForRoleFiltersCorrectly() = runBlocking {
            service.invoke(AgentRole.ARCHITECT, "Design 1")
            service.invoke(AgentRole.IMPLEMENTER, "Implement")
            service.invoke(AgentRole.ARCHITECT, "Design 2")

            val history = service.getHistoryForRole(AgentRole.ARCHITECT)

            assertEquals(2, history.size)
            assertTrue(history.all { it.role == AgentRole.ARCHITECT })
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventsTests {

        @Test
        @DisplayName("emits agent invoked event")
        fun emitsAgentInvokedEvent() = runBlocking {
            val events = mutableListOf<SpecialistEvent>()
            service.addListener { event -> events.add(event) }

            service.invoke(AgentRole.TESTER, "Write tests")

            assertTrue(events.any { it is SpecialistEvent.AgentInvoked })
        }

        @Test
        @DisplayName("emits agent responded event")
        fun emitsAgentRespondedEvent() = runBlocking {
            val events = mutableListOf<SpecialistEvent>()
            service.addListener { events.add(it) }

            service.invoke(AgentRole.REVIEWER, "Review code")

            assertTrue(events.any { it is SpecialistEvent.AgentResponded })
        }

        @Test
        @DisplayName("removeListener stops events")
        fun removeListenerStopsEvents() = runBlocking {
            var count = 0
            val listener: (SpecialistEvent) -> Unit = { count++ }
            service.addListener(listener)

            service.invoke(AgentRole.ARCHITECT, "Design")
            val firstCount = count

            service.removeListener(listener)
            service.invoke(AgentRole.ARCHITECT, "Design again")

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
        @DisplayName("clearHistory removes all history")
        fun clearHistoryRemovesAllHistory() = runBlocking {
            service.invoke(AgentRole.ARCHITECT, "Design")
            service.invoke(AgentRole.IMPLEMENTER, "Implement")

            service.clearHistory()

            val stats = service.getStats()
            assertEquals(0, stats.totalInvocations)
        }
    }
}
