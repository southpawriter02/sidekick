package com.sidekick.llm.provider

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Provider models.
 */
@DisplayName("Provider Models Tests")
class ProviderModelsTest {

    // =========================================================================
    // ProviderType Tests
    // =========================================================================

    @Nested
    @DisplayName("ProviderType")
    inner class ProviderTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            ProviderType.entries.forEach { type ->
                assertNotNull(type.displayName)
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("local providers are correctly identified")
        fun localProvidersCorrectlyIdentified() {
            assertTrue(ProviderType.OLLAMA.isLocal)
            assertTrue(ProviderType.LM_STUDIO.isLocal)
            assertTrue(ProviderType.CUSTOM.isLocal)
            assertFalse(ProviderType.OPENAI.isLocal)
            assertFalse(ProviderType.ANTHROPIC.isLocal)
        }

        @Test
        @DisplayName("LOCAL_PROVIDERS contains expected types")
        fun localProvidersContainsExpected() {
            assertTrue(ProviderType.OLLAMA in ProviderType.LOCAL_PROVIDERS)
            assertTrue(ProviderType.LM_STUDIO in ProviderType.LOCAL_PROVIDERS)
            assertFalse(ProviderType.OPENAI in ProviderType.LOCAL_PROVIDERS)
        }

        @Test
        @DisplayName("CLOUD_PROVIDERS contains expected types")
        fun cloudProvidersContainsExpected() {
            assertTrue(ProviderType.OPENAI in ProviderType.CLOUD_PROVIDERS)
            assertTrue(ProviderType.ANTHROPIC in ProviderType.CLOUD_PROVIDERS)
            assertFalse(ProviderType.OLLAMA in ProviderType.CLOUD_PROVIDERS)
        }

        @Test
        @DisplayName("byName finds types case-insensitively")
        fun byNameFindsCaseInsensitively() {
            assertEquals(ProviderType.OLLAMA, ProviderType.byName("ollama"))
            assertEquals(ProviderType.OLLAMA, ProviderType.byName("OLLAMA"))
            assertEquals(ProviderType.LM_STUDIO, ProviderType.byName("lm_studio"))
            assertNull(ProviderType.byName("unknown"))
        }

        @Test
        @DisplayName("toString returns displayName")
        fun toStringReturnsDisplayName() {
            assertEquals("Ollama", ProviderType.OLLAMA.toString())
            assertEquals("LM Studio", ProviderType.LM_STUDIO.toString())
        }
    }

    // =========================================================================
    // UnifiedModel Tests
    // =========================================================================

    @Nested
    @DisplayName("UnifiedModel")
    inner class UnifiedModelTests {

        @Test
        @DisplayName("model with full properties")
        fun modelWithFullProperties() {
            val model = UnifiedModel(
                id = "llama-3.1-8b",
                provider = ProviderType.OLLAMA,
                displayName = "Llama 3.1 8B",
                contextLength = 8192,
                capabilities = setOf("chat", "code", "function_calling"),
                isLoaded = true,
                metadata = mapOf("family" to "llama")
            )

            assertEquals("llama-3.1-8b", model.id)
            assertEquals(ProviderType.OLLAMA, model.provider)
            assertEquals("ollama:llama-3.1-8b", model.fullId)
            assertTrue(model.supportsChat)
            assertTrue(model.supportsCode)
            assertTrue(model.supportsFunctionCalling)
            assertFalse(model.supportsEmbedding)
        }

        @Test
        @DisplayName("capability constants are correct")
        fun capabilityConstantsAreCorrect() {
            assertEquals("chat", UnifiedModel.CAPABILITY_CHAT)
            assertEquals("code", UnifiedModel.CAPABILITY_CODE)
            assertEquals("embedding", UnifiedModel.CAPABILITY_EMBEDDING)
            assertEquals("function_calling", UnifiedModel.CAPABILITY_FUNCTION_CALLING)
        }

        @Test
        @DisplayName("CHAT_CAPABILITIES contains expected")
        fun chatCapabilitiesContainsExpected() {
            assertTrue(UnifiedModel.CAPABILITY_CHAT in UnifiedModel.CHAT_CAPABILITIES)
            assertTrue(UnifiedModel.CAPABILITY_COMPLETION in UnifiedModel.CHAT_CAPABILITIES)
        }

        @Test
        @DisplayName("CODE_CAPABILITIES contains expected")
        fun codeCapabilitiesContainsExpected() {
            assertTrue(UnifiedModel.CAPABILITY_CHAT in UnifiedModel.CODE_CAPABILITIES)
            assertTrue(UnifiedModel.CAPABILITY_CODE in UnifiedModel.CODE_CAPABILITIES)
            assertTrue(UnifiedModel.CAPABILITY_FUNCTION_CALLING in UnifiedModel.CODE_CAPABILITIES)
        }
    }

    // =========================================================================
    // UnifiedChatRequest Tests
    // =========================================================================

    @Nested
    @DisplayName("UnifiedChatRequest")
    inner class UnifiedChatRequestTests {

        @Test
        @DisplayName("default values are correct")
        fun defaultValuesAreCorrect() {
            val request = UnifiedChatRequest(
                model = "llama-3.1",
                messages = listOf(UnifiedMessage.user("Hello"))
            )

            assertEquals(0.7f, request.temperature)
            assertNull(request.maxTokens)
            assertNull(request.systemPrompt)
            assertNull(request.tools)
            assertFalse(request.stream)
            assertFalse(request.hasTools)
        }

        @Test
        @DisplayName("streaming creates streaming copy")
        fun streamingCreatesStreamingCopy() {
            val request = UnifiedChatRequest(
                model = "llama-3.1",
                messages = listOf(UnifiedMessage.user("Hello"))
            )

            assertFalse(request.stream)
            assertTrue(request.streaming().stream)
        }

        @Test
        @DisplayName("nonStreaming creates non-streaming copy")
        fun nonStreamingCreatesNonStreamingCopy() {
            val request = UnifiedChatRequest(
                model = "llama-3.1",
                messages = listOf(UnifiedMessage.user("Hello")),
                stream = true
            )

            val nonStreaming = request.nonStreaming()
            assertFalse(nonStreaming.stream)
        }

        @Test
        @DisplayName("withMessage adds message")
        fun withMessageAddsMessage() {
            val request = UnifiedChatRequest(
                model = "llama-3.1",
                messages = listOf(UnifiedMessage.user("Hello"))
            )

            val updated = request.withMessage(UnifiedMessage.assistant("Hi!"))
            assertEquals(2, updated.messages.size)
        }

        @Test
        @DisplayName("allMessages includes system prompt")
        fun allMessagesIncludesSystemPrompt() {
            val request = UnifiedChatRequest(
                model = "llama-3.1",
                messages = listOf(UnifiedMessage.user("Hello")),
                systemPrompt = "You are helpful"
            )

            val all = request.allMessages
            assertEquals(2, all.size)
            assertTrue(all[0].isSystem)
            assertTrue(all[1].isUser)
        }

        @Test
        @DisplayName("simple creates correct request")
        fun simpleCreatesCorrectRequest() {
            val request = UnifiedChatRequest.simple(
                model = "llama-3.1",
                userMessage = "Hello",
                systemPrompt = "Be helpful"
            )

            assertEquals(1, request.messages.size)
            assertEquals("Be helpful", request.systemPrompt)
        }

        @Test
        @DisplayName("hasTools detects tools")
        fun hasToolsDetectsTools() {
            val withoutTools = UnifiedChatRequest(
                model = "llama-3.1",
                messages = listOf(UnifiedMessage.user("Hello"))
            )
            assertFalse(withoutTools.hasTools)

            val withTools = withoutTools.copy(
                tools = listOf(AgentTool.simple("test", "A test tool"))
            )
            assertTrue(withTools.hasTools)
        }
    }

    // =========================================================================
    // UnifiedMessage Tests
    // =========================================================================

    @Nested
    @DisplayName("UnifiedMessage")
    inner class UnifiedMessageTests {

        @Test
        @DisplayName("factory methods create correct roles")
        fun factoryMethodsCreateCorrectRoles() {
            val system = UnifiedMessage.system("System prompt")
            assertTrue(system.isSystem)
            assertEquals(MessageRole.SYSTEM, system.role)

            val user = UnifiedMessage.user("User message")
            assertTrue(user.isUser)
            assertEquals(MessageRole.USER, user.role)

            val assistant = UnifiedMessage.assistant("Response")
            assertTrue(assistant.isAssistant)
            assertEquals(MessageRole.ASSISTANT, assistant.role)

            val tool = UnifiedMessage.tool("Tool output")
            assertTrue(tool.isTool)
            assertEquals(MessageRole.TOOL, tool.role)
        }
    }

    // =========================================================================
    // MessageRole Tests
    // =========================================================================

    @Nested
    @DisplayName("MessageRole")
    inner class MessageRoleTests {

        @Test
        @DisplayName("toApiString returns lowercase")
        fun toApiStringReturnsLowercase() {
            assertEquals("system", MessageRole.SYSTEM.toApiString())
            assertEquals("user", MessageRole.USER.toApiString())
            assertEquals("assistant", MessageRole.ASSISTANT.toApiString())
            assertEquals("tool", MessageRole.TOOL.toApiString())
        }

        @Test
        @DisplayName("fromString parses correctly")
        fun fromStringParsesCorrectly() {
            assertEquals(MessageRole.SYSTEM, MessageRole.fromString("system"))
            assertEquals(MessageRole.SYSTEM, MessageRole.fromString("SYSTEM"))
            assertEquals(MessageRole.USER, MessageRole.fromString("user"))
            assertEquals(MessageRole.USER, MessageRole.fromString("unknown"))
        }
    }

    // =========================================================================
    // UnifiedChatResponse Tests
    // =========================================================================

    @Nested
    @DisplayName("UnifiedChatResponse")
    inner class UnifiedChatResponseTests {

        @Test
        @DisplayName("text creates text response")
        fun textCreatesTextResponse() {
            val response = UnifiedChatResponse.text("Hello!")
            assertEquals("Hello!", response.content)
            assertFalse(response.hasToolCalls)
            assertTrue(response.isComplete)
        }

        @Test
        @DisplayName("error creates error response")
        fun errorCreatesErrorResponse() {
            val response = UnifiedChatResponse.error("Something went wrong")
            assertNull(response.content)
            assertEquals("error", response.finishReason)
            assertFalse(response.isComplete)
        }

        @Test
        @DisplayName("hasToolCalls detects tool calls")
        fun hasToolCallsDetectsToolCalls() {
            val withoutCalls = UnifiedChatResponse.text("Hello")
            assertFalse(withoutCalls.hasToolCalls)

            val withCalls = UnifiedChatResponse(
                content = null,
                toolCalls = listOf(ToolCallRequest("1", "test", "{}")),
                usage = null,
                finishReason = "tool_calls"
            )
            assertTrue(withCalls.hasToolCalls)
        }
    }

    // =========================================================================
    // TokenUsage Tests
    // =========================================================================

    @Nested
    @DisplayName("TokenUsage")
    inner class TokenUsageTests {

        @Test
        @DisplayName("of creates with calculated total")
        fun ofCreatesWithCalculatedTotal() {
            val usage = TokenUsage.of(100, 50)
            assertEquals(100, usage.promptTokens)
            assertEquals(50, usage.completionTokens)
            assertEquals(150, usage.totalTokens)
        }

        @Test
        @DisplayName("ZERO is all zeros")
        fun zeroIsAllZeros() {
            assertEquals(0, TokenUsage.ZERO.promptTokens)
            assertEquals(0, TokenUsage.ZERO.completionTokens)
            assertEquals(0, TokenUsage.ZERO.totalTokens)
        }
    }

    // =========================================================================
    // AgentTool Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentTool")
    inner class AgentToolTests {

        @Test
        @DisplayName("simple creates tool with string params")
        fun simpleCreatesToolWithStringParams() {
            val tool = AgentTool.simple(
                "read_file",
                "Read a file",
                "path" to "File path",
                "encoding" to "File encoding"
            )

            assertEquals("read_file", tool.name)
            assertEquals("Read a file", tool.description)
            assertEquals(2, tool.parameters.properties.size)
            assertTrue("path" in tool.parameters.required)
            assertTrue("encoding" in tool.parameters.required)
        }
    }

    // =========================================================================
    // ToolParameters Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolParameters")
    inner class ToolParametersTests {

        @Test
        @DisplayName("default type is object")
        fun defaultTypeIsObject() {
            val params = ToolParameters(
                properties = mapOf("test" to ParameterSchema("string", "A test"))
            )
            assertEquals("object", params.type)
        }
    }

    // =========================================================================
    // ToolCallRequest Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolCallRequest")
    inner class ToolCallRequestTests {

        @Test
        @DisplayName("stores call information")
        fun storesCallInformation() {
            val call = ToolCallRequest(
                id = "call-123",
                name = "read_file",
                arguments = """{"path": "/test.txt"}"""
            )

            assertEquals("call-123", call.id)
            assertEquals("read_file", call.name)
            assertTrue(call.arguments.contains("path"))
        }
    }

    // =========================================================================
    // ToolResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolResult")
    inner class ToolResultTests {

        @Test
        @DisplayName("success creates success result")
        fun successCreatesSuccessResult() {
            val result = ToolResult.success("call-1", "File contents")
            assertTrue(result.success)
            assertEquals("File contents", result.output)
            assertNull(result.error)
        }

        @Test
        @DisplayName("failure creates failure result")
        fun failureCreatesFailureResult() {
            val result = ToolResult.failure("call-1", "File not found")
            assertFalse(result.success)
            assertEquals("File not found", result.error)
        }
    }

    // =========================================================================
    // ProviderHealth Tests
    // =========================================================================

    @Nested
    @DisplayName("ProviderHealth")
    inner class ProviderHealthTests {

        @Test
        @DisplayName("healthy creates healthy status")
        fun healthyCreatesHealthyStatus() {
            val health = ProviderHealth.healthy(50, "llama-3.1")
            assertTrue(health.healthy)
            assertEquals(50, health.latencyMs)
            assertEquals("llama-3.1", health.loadedModel)
            assertNull(health.error)
        }

        @Test
        @DisplayName("unhealthy creates unhealthy status")
        fun unhealthyCreatesUnhealthyStatus() {
            val health = ProviderHealth.unhealthy("Connection refused")
            assertFalse(health.healthy)
            assertEquals("Connection refused", health.error)
            assertNull(health.latencyMs)
        }

        @Test
        @DisplayName("displayStatus formats correctly")
        fun displayStatusFormatsCorrectly() {
            val healthy = ProviderHealth.healthy(50, "llama-3.1")
            assertEquals("Healthy (llama-3.1)", healthy.displayStatus)

            val healthyNoModel = ProviderHealth.healthy(50)
            assertEquals("Healthy", healthyNoModel.displayStatus)

            val unhealthy = ProviderHealth.unhealthy("Error")
            assertEquals("Error: Error", unhealthy.displayStatus)
        }
    }

    // =========================================================================
    // ProviderSelectionStrategy Tests
    // =========================================================================

    @Nested
    @DisplayName("ProviderSelectionStrategy")
    inner class ProviderSelectionStrategyTests {

        @Test
        @DisplayName("all strategies exist")
        fun allStrategiesExist() {
            assertEquals(4, ProviderSelectionStrategy.entries.size)
            assertNotNull(ProviderSelectionStrategy.FIRST_AVAILABLE)
            assertNotNull(ProviderSelectionStrategy.LOWEST_LATENCY)
            assertNotNull(ProviderSelectionStrategy.ROUND_ROBIN)
            assertNotNull(ProviderSelectionStrategy.PREFERRED)
        }
    }
}
