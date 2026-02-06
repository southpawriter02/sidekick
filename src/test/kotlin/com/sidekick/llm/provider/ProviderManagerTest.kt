package com.sidekick.llm.provider

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Provider Manager logic.
 *
 * Note: These tests focus on the manager's pure logic
 * without requiring IntelliJ Platform.
 */
@DisplayName("Provider Manager Tests")
class ProviderManagerTest {

    // =========================================================================
    // State Tests
    // =========================================================================

    @Nested
    @DisplayName("State")
    inner class StateTests {

        @Test
        @DisplayName("default state has expected values")
        fun defaultStateHasExpectedValues() {
            val state = ProviderManager.State()
            assertEquals("OLLAMA", state.activeProviderName)
            assertEquals("FIRST_AVAILABLE", state.selectionStrategy)
        }

        @Test
        @DisplayName("provider configs track enabled state")
        fun providerConfigsTrackEnabledState() {
            val state = ProviderManager.State(
                providerConfigs = mutableMapOf(
                    "OLLAMA" to true,
                    "LM_STUDIO" to false
                )
            )

            assertTrue(state.providerConfigs["OLLAMA"]!!)
            assertFalse(state.providerConfigs["LM_STUDIO"]!!)
        }
    }

    // =========================================================================
    // Provider Type Resolution Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Type Resolution")
    inner class ProviderTypeResolutionTests {

        @Test
        @DisplayName("byName resolves Ollama")
        fun byNameResolvesOllama() {
            val type = ProviderType.byName("OLLAMA")
            assertEquals(ProviderType.OLLAMA, type)
        }

        @Test
        @DisplayName("byName resolves LM Studio")
        fun byNameResolvesLmStudio() {
            val type = ProviderType.byName("LM_STUDIO")
            assertEquals(ProviderType.LM_STUDIO, type)
        }

        @Test
        @DisplayName("byName is case insensitive")
        fun byNameIsCaseInsensitive() {
            assertEquals(ProviderType.OLLAMA, ProviderType.byName("ollama"))
            assertEquals(ProviderType.LM_STUDIO, ProviderType.byName("lm_studio"))
        }

        @Test
        @DisplayName("byName returns null for unknown")
        fun byNameReturnsNullForUnknown() {
            assertNull(ProviderType.byName("unknown"))
            assertNull(ProviderType.byName(""))
        }
    }

    // =========================================================================
    // Selection Strategy Tests
    // =========================================================================

    @Nested
    @DisplayName("Selection Strategy")
    inner class SelectionStrategyTests {

        @Test
        @DisplayName("FIRST_AVAILABLE is default")
        fun firstAvailableIsDefault() {
            val state = ProviderManager.State()
            assertEquals("FIRST_AVAILABLE", state.selectionStrategy)
        }

        @Test
        @DisplayName("all strategies can be stored")
        fun allStrategiesCanBeStored() {
            ProviderSelectionStrategy.entries.forEach { strategy ->
                val state = ProviderManager.State(selectionStrategy = strategy.name)
                assertEquals(strategy.name, state.selectionStrategy)
            }
        }

        @Test
        @DisplayName("strategy can be parsed from state")
        fun strategyCanBeParsedFromState() {
            val state = ProviderManager.State(selectionStrategy = "LOWEST_LATENCY")
            val strategy = ProviderSelectionStrategy.valueOf(state.selectionStrategy)
            assertEquals(ProviderSelectionStrategy.LOWEST_LATENCY, strategy)
        }
    }

    // =========================================================================
    // LmStudioLlmProvider Tests
    // =========================================================================

    @Nested
    @DisplayName("LmStudioLlmProvider")
    inner class LmStudioLlmProviderTests {

        @Test
        @DisplayName("has correct name and type")
        fun hasCorrectNameAndType() {
            val provider = LmStudioLlmProvider()
            assertEquals("LM Studio", provider.name)
            assertEquals(ProviderType.LM_STUDIO, provider.type)
        }
    }

    // =========================================================================
    // OllamaLlmProvider Tests
    // =========================================================================

    @Nested
    @DisplayName("OllamaLlmProvider")
    inner class OllamaLlmProviderTests {

        @Test
        @DisplayName("has correct name and type")
        fun hasCorrectNameAndType() {
            val provider = OllamaLlmProvider()
            assertEquals("Ollama", provider.name)
            assertEquals(ProviderType.OLLAMA, provider.type)
        }

        @Test
        @DisplayName("default config has correct values")
        fun defaultConfigHasCorrectValues() {
            val config = OllamaLlmProvider.OllamaConfig.DEFAULT
            assertEquals("localhost", config.host)
            assertEquals(11434, config.port)
            assertEquals("http://localhost:11434", config.baseUrl)
        }

        @Test
        @DisplayName("custom config constructs baseUrl correctly")
        fun customConfigConstructsBaseUrlCorrectly() {
            val config = OllamaLlmProvider.OllamaConfig(
                host = "192.168.1.100",
                port = 8080
            )
            assertEquals("http://192.168.1.100:8080", config.baseUrl)
        }
    }

    // =========================================================================
    // Provider Interface Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Interface Compliance")
    inner class ProviderInterfaceComplianceTests {

        @Test
        @DisplayName("LmStudioLlmProvider implements LlmProvider")
        fun lmStudioImplementsLlmProvider() {
            val provider: LlmProvider = LmStudioLlmProvider()
            assertNotNull(provider.name)
            assertNotNull(provider.type)
        }

        @Test
        @DisplayName("OllamaLlmProvider implements LlmProvider")
        fun ollamaImplementsLlmProvider() {
            val provider: LlmProvider = OllamaLlmProvider()
            assertNotNull(provider.name)
            assertNotNull(provider.type)
        }
    }

    // =========================================================================
    // Health Check Result Tests
    // =========================================================================

    @Nested
    @DisplayName("Health Check Results")
    inner class HealthCheckResultTests {

        @Test
        @DisplayName("healthy result has expected properties")
        fun healthyResultHasExpectedProperties() {
            val health = ProviderHealth.healthy(100, "llama-3.1")
            assertTrue(health.healthy)
            assertEquals(100, health.latencyMs)
            assertEquals("llama-3.1", health.loadedModel)
            assertNull(health.error)
        }

        @Test
        @DisplayName("unhealthy result has expected properties")
        fun unhealthyResultHasExpectedProperties() {
            val health = ProviderHealth.unhealthy("Connection refused")
            assertFalse(health.healthy)
            assertNull(health.latencyMs)
            assertEquals("Connection refused", health.error)
        }

        @Test
        @DisplayName("displayStatus formats healthy with model")
        fun displayStatusFormatsHealthyWithModel() {
            val health = ProviderHealth.healthy(50, "phi-3")
            assertEquals("Healthy (phi-3)", health.displayStatus)
        }

        @Test
        @DisplayName("displayStatus formats healthy without model")
        fun displayStatusFormatsHealthyWithoutModel() {
            val health = ProviderHealth.healthy(50)
            assertEquals("Healthy", health.displayStatus)
        }

        @Test
        @DisplayName("displayStatus formats error")
        fun displayStatusFormatsError() {
            val health = ProviderHealth.unhealthy("Timeout")
            assertEquals("Error: Timeout", health.displayStatus)
        }
    }

    // =========================================================================
    // Model Capability Inference Tests
    // =========================================================================

    @Nested
    @DisplayName("Model Capability Inference")
    inner class ModelCapabilityInferenceTests {

        @Test
        @DisplayName("chat capabilities are detected")
        fun chatCapabilitiesAreDetected() {
            val model = UnifiedModel(
                id = "llama-3.1",
                provider = ProviderType.OLLAMA,
                displayName = "Llama 3.1",
                contextLength = 8192,
                capabilities = setOf("chat", "completion"),
                isLoaded = true
            )

            assertTrue(model.supportsChat)
        }

        @Test
        @DisplayName("code capabilities are detected")
        fun codeCapabilitiesAreDetected() {
            val model = UnifiedModel(
                id = "codellama",
                provider = ProviderType.OLLAMA,
                displayName = "Code Llama",
                contextLength = 16384,
                capabilities = setOf("chat", "code", "function_calling"),
                isLoaded = false
            )

            assertTrue(model.supportsCode)
            assertTrue(model.supportsFunctionCalling)
        }

        @Test
        @DisplayName("embedding capability is detected")
        fun embeddingCapabilityIsDetected() {
            val model = UnifiedModel(
                id = "nomic-embed-text",
                provider = ProviderType.OLLAMA,
                displayName = "Nomic Embed",
                contextLength = 8192,
                capabilities = setOf("embedding"),
                isLoaded = true
            )

            assertTrue(model.supportsEmbedding)
            assertFalse(model.supportsChat)
        }
    }

    // =========================================================================
    // Request Building Tests
    // =========================================================================

    @Nested
    @DisplayName("Request Building")
    inner class RequestBuildingTests {

        @Test
        @DisplayName("simple request is created correctly")
        fun simpleRequestIsCreatedCorrectly() {
            val request = UnifiedChatRequest.simple(
                model = "llama-3.1",
                userMessage = "Hello",
                systemPrompt = "Be helpful"
            )

            assertEquals("llama-3.1", request.model)
            assertEquals(1, request.messages.size)
            assertEquals("Be helpful", request.systemPrompt)
        }

        @Test
        @DisplayName("allMessages includes system and user")
        fun allMessagesIncludesSystemAndUser() {
            val request = UnifiedChatRequest.simple(
                model = "llama-3.1",
                userMessage = "Hello",
                systemPrompt = "System"
            )

            val all = request.allMessages
            assertEquals(2, all.size)
            assertEquals(MessageRole.SYSTEM, all[0].role)
            assertEquals(MessageRole.USER, all[1].role)
        }

        @Test
        @DisplayName("streaming toggle works")
        fun streamingToggleWorks() {
            val request = UnifiedChatRequest(
                model = "test",
                messages = listOf(UnifiedMessage.user("hi"))
            )

            assertFalse(request.stream)
            assertTrue(request.streaming().stream)
            assertFalse(request.streaming().nonStreaming().stream)
        }
    }

    // =========================================================================
    // Response Parsing Tests
    // =========================================================================

    @Nested
    @DisplayName("Response Building")
    inner class ResponseBuildingTests {

        @Test
        @DisplayName("text response is complete")
        fun textResponseIsComplete() {
            val response = UnifiedChatResponse.text("Hello!")
            assertTrue(response.isComplete)
            assertEquals("Hello!", response.content)
            assertFalse(response.hasToolCalls)
        }

        @Test
        @DisplayName("error response is not complete")
        fun errorResponseIsNotComplete() {
            val response = UnifiedChatResponse.error("Failed")
            assertFalse(response.isComplete)
            assertNull(response.content)
        }

        @Test
        @DisplayName("tool calls response has proper structure")
        fun toolCallsResponseHasProperStructure() {
            val response = UnifiedChatResponse(
                content = null,
                toolCalls = listOf(
                    ToolCallRequest("1", "read_file", """{"path":"/test.txt"}""")
                ),
                usage = TokenUsage.of(10, 5),
                finishReason = "tool_calls"
            )

            assertTrue(response.hasToolCalls)
            assertEquals(1, response.toolCalls?.size)
            assertEquals("read_file", response.toolCalls?.first()?.name)
        }
    }

    // =========================================================================
    // Tool Definition Tests
    // =========================================================================

    @Nested
    @DisplayName("Tool Definition")
    inner class ToolDefinitionTests {

        @Test
        @DisplayName("simple tool has correct structure")
        fun simpleToolHasCorrectStructure() {
            val tool = AgentTool.simple(
                "list_files",
                "List files in a directory",
                "path" to "Directory path",
                "recursive" to "Whether to recurse"
            )

            assertEquals("list_files", tool.name)
            assertEquals("object", tool.parameters.type)
            assertEquals(2, tool.parameters.properties.size)
            assertEquals(2, tool.parameters.required.size)
        }

        @Test
        @DisplayName("tool result success factory works")
        fun toolResultSuccessFactoryWorks() {
            val result = ToolResult.success("call-1", "Output text")
            assertTrue(result.success)
            assertEquals("call-1", result.callId)
            assertEquals("Output text", result.output)
        }

        @Test
        @DisplayName("tool result failure factory works")
        fun toolResultFailureFactoryWorks() {
            val result = ToolResult.failure("call-2", "Not found")
            assertFalse(result.success)
            assertEquals("call-2", result.callId)
            assertEquals("Not found", result.error)
        }
    }
}
