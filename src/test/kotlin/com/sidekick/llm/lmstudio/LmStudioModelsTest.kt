package com.sidekick.llm.lmstudio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for LM Studio models.
 */
@DisplayName("LM Studio Models Tests")
class LmStudioModelsTest {

    // =========================================================================
    // LmStudioConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("LmStudioConfig")
    inner class LmStudioConfigTests {

        @Test
        @DisplayName("default config has expected values")
        fun defaultConfigHasExpectedValues() {
            val config = LmStudioConfig()
            assertEquals("localhost", config.host)
            assertEquals(1234, config.port)
            assertEquals("/v1", config.apiPath)
            assertEquals(5000, config.connectionTimeoutMs)
            assertEquals(120000, config.requestTimeoutMs)
            assertTrue(config.autoConnect)
            assertTrue(config.autoDiscover)
        }

        @Test
        @DisplayName("baseUrl constructs correctly")
        fun baseUrlConstructsCorrectly() {
            val config = LmStudioConfig(host = "192.168.1.100", port = 8080)
            assertEquals("http://192.168.1.100:8080/v1", config.baseUrl)
        }

        @Test
        @DisplayName("endpoint constructs full URL")
        fun endpointConstructsFullUrl() {
            val config = LmStudioConfig()
            assertEquals("http://localhost:1234/v1/models", config.endpoint("/models"))
            assertEquals("http://localhost:1234/v1/chat/completions", config.endpoint("/chat/completions"))
        }

        @Test
        @DisplayName("DEFAULT companion object provides default config")
        fun defaultCompanionObjectProvidesDefaultConfig() {
            assertEquals(LmStudioConfig(), LmStudioConfig.DEFAULT)
        }

        @Test
        @DisplayName("COMMON_PORTS contains expected ports")
        fun commonPortsContainsExpectedPorts() {
            assertTrue(1234 in LmStudioConfig.COMMON_PORTS)
            assertTrue(8080 in LmStudioConfig.COMMON_PORTS)
        }

        @Test
        @DisplayName("forAddress creates config with specified host and port")
        fun forAddressCreatesConfig() {
            val config = LmStudioConfig.forAddress("my-server", 5000)
            assertEquals("my-server", config.host)
            assertEquals(5000, config.port)
        }
    }

    // =========================================================================
    // LmStudioModel Tests
    // =========================================================================

    @Nested
    @DisplayName("LmStudioModel")
    inner class LmStudioModelTests {

        @Test
        @DisplayName("model with full properties")
        fun modelWithFullProperties() {
            val model = LmStudioModel(
                id = "llama-3.1-8b-instruct",
                name = "Llama 3.1 8B Instruct",
                path = "/models/llama-3.1-8b-instruct.gguf",
                size = 4_500_000_000,
                quantization = "Q4_K_M",
                contextLength = 8192,
                family = ModelFamily.LLAMA,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE),
                isLoaded = true
            )

            assertEquals("llama-3.1-8b-instruct", model.id)
            assertEquals("Llama 3.1 8B Instruct", model.name)
            assertEquals("llama-3.1-8b-instruct.gguf", model.fileName)
            assertEquals("4GB", model.sizeDisplay)
            assertTrue(model.supportsChat)
            assertTrue(model.supportsCode)
            assertFalse(model.supportsFunctionCalling)
        }

        @Test
        @DisplayName("sizeDisplay formats correctly")
        fun sizeDisplayFormatsCorrectly() {
            val smallModel = LmStudioModel(
                id = "test", name = "Test", path = null, size = 500_000,
                quantization = null, contextLength = 2048, family = ModelFamily.OTHER,
                capabilities = emptySet(), isLoaded = false
            )
            assertEquals("500KB", smallModel.sizeDisplay)

            val mediumModel = smallModel.copy(size = 250_000_000)
            assertEquals("250MB", mediumModel.sizeDisplay)

            val largeModel = smallModel.copy(size = 8_000_000_000)
            assertEquals("8GB", largeModel.sizeDisplay)

            val nullSize = smallModel.copy(size = null)
            assertNull(nullSize.sizeDisplay)
        }

        @Test
        @DisplayName("inferFamily detects model families correctly")
        fun inferFamilyDetectsCorrectly() {
            assertEquals(ModelFamily.LLAMA, LmStudioModel.inferFamily("llama-3.1-8b"))
            assertEquals(ModelFamily.CODELLAMA, LmStudioModel.inferFamily("codellama-13b"))
            assertEquals(ModelFamily.MISTRAL, LmStudioModel.inferFamily("mistral-7b-instruct"))
            assertEquals(ModelFamily.MISTRAL, LmStudioModel.inferFamily("mixtral-8x7b"))
            assertEquals(ModelFamily.DEEPSEEK, LmStudioModel.inferFamily("deepseek-coder-6.7b"))
            assertEquals(ModelFamily.QWEN, LmStudioModel.inferFamily("qwen2-7b-instruct"))
            assertEquals(ModelFamily.PHI, LmStudioModel.inferFamily("phi-3-mini"))
            assertEquals(ModelFamily.GEMMA, LmStudioModel.inferFamily("gemma-2-9b"))
            assertEquals(ModelFamily.STARCODER, LmStudioModel.inferFamily("starcoder2-7b"))
            assertEquals(ModelFamily.OTHER, LmStudioModel.inferFamily("unknown-model"))
        }

        @Test
        @DisplayName("inferCapabilities detects capabilities correctly")
        fun inferCapabilitiesDetectsCorrectly() {
            val codeCapabilities = LmStudioModel.inferCapabilities("codellama-13b", ModelFamily.CODELLAMA)
            assertTrue(ModelCapability.CODE in codeCapabilities)

            val mistralCapabilities = LmStudioModel.inferCapabilities("mistral-7b-instruct", ModelFamily.MISTRAL)
            assertTrue(ModelCapability.FUNCTION_CALLING in mistralCapabilities)

            val coderCapabilities = LmStudioModel.inferCapabilities("deepseek-coder", ModelFamily.DEEPSEEK)
            assertTrue(ModelCapability.CODE in coderCapabilities)
        }
    }

    // =========================================================================
    // ModelFamily Tests
    // =========================================================================

    @Nested
    @DisplayName("ModelFamily")
    inner class ModelFamilyTests {

        @Test
        @DisplayName("all families have display names")
        fun allFamiliesHaveDisplayNames() {
            ModelFamily.entries.forEach { family ->
                assertNotNull(family.displayName)
                assertTrue(family.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("toString returns displayName")
        fun toStringReturnsDisplayName() {
            assertEquals("LLaMA", ModelFamily.LLAMA.toString())
            assertEquals("Code Llama", ModelFamily.CODELLAMA.toString())
            assertEquals("Mistral", ModelFamily.MISTRAL.toString())
        }

        @Test
        @DisplayName("byName finds family case-insensitively")
        fun byNameFindsFamilyCaseInsensitively() {
            assertEquals(ModelFamily.LLAMA, ModelFamily.byName("llama"))
            assertEquals(ModelFamily.LLAMA, ModelFamily.byName("LLAMA"))
            assertEquals(ModelFamily.MISTRAL, ModelFamily.byName("Mistral"))
            assertEquals(ModelFamily.OTHER, ModelFamily.byName("unknown"))
        }
    }

    // =========================================================================
    // ModelCapability Tests
    // =========================================================================

    @Nested
    @DisplayName("ModelCapability")
    inner class ModelCapabilityTests {

        @Test
        @DisplayName("all capabilities have display names")
        fun allCapabilitiesHaveDisplayNames() {
            ModelCapability.entries.forEach { capability ->
                assertNotNull(capability.displayName)
                assertTrue(capability.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("CODING_ESSENTIAL contains expected capabilities")
        fun codingEssentialContainsExpected() {
            assertTrue(ModelCapability.CHAT in ModelCapability.CODING_ESSENTIAL)
            assertTrue(ModelCapability.CODE in ModelCapability.CODING_ESSENTIAL)
            assertTrue(ModelCapability.FUNCTION_CALLING in ModelCapability.CODING_ESSENTIAL)
        }
    }

    // =========================================================================
    // ConnectionStatus Tests
    // =========================================================================

    @Nested
    @DisplayName("ConnectionStatus")
    inner class ConnectionStatusTests {

        @Test
        @DisplayName("connected status with model")
        fun connectedStatusWithModel() {
            val status = ConnectionStatus(
                connected = true,
                serverVersion = "1.0.0",
                loadedModel = "llama-3.1",
                lastCheck = Instant.now(),
                error = null
            )

            assertTrue(status.connected)
            assertEquals("Connected (llama-3.1)", status.displayStatus)
            assertEquals("🟢", status.statusIcon)
            assertFalse(status.shouldRetry)
        }

        @Test
        @DisplayName("connected status without model")
        fun connectedStatusWithoutModel() {
            val status = ConnectionStatus.connected()
            assertTrue(status.connected)
            assertEquals("Connected (no model loaded)", status.displayStatus)
            assertEquals("🟡", status.statusIcon)
        }

        @Test
        @DisplayName("disconnected status with error")
        fun disconnectedStatusWithError() {
            val status = ConnectionStatus.disconnected("Connection refused")
            assertFalse(status.connected)
            assertEquals("Error: Connection refused", status.displayStatus)
            assertEquals("🔴", status.statusIcon)
            assertTrue(status.shouldRetry)
        }

        @Test
        @DisplayName("disconnected status without error")
        fun disconnectedStatusWithoutError() {
            val status = ConnectionStatus.disconnected()
            assertEquals("Disconnected", status.displayStatus)
            assertFalse(status.shouldRetry)
        }
    }

    // =========================================================================
    // ChatCompletionRequest Tests
    // =========================================================================

    @Nested
    @DisplayName("ChatCompletionRequest")
    inner class ChatCompletionRequestTests {

        @Test
        @DisplayName("default values are correct")
        fun defaultValuesAreCorrect() {
            val request = ChatCompletionRequest(
                model = "llama-3.1",
                messages = listOf(ChatMessage.user("Hello"))
            )

            assertEquals(0.7f, request.temperature)
            assertNull(request.maxTokens)
            assertTrue(request.stream)
            assertNull(request.stop)
            assertNull(request.tools)
            assertFalse(request.hasTools)
        }

        @Test
        @DisplayName("nonStreaming creates non-streaming copy")
        fun nonStreamingCreatesNonStreamingCopy() {
            val request = ChatCompletionRequest(
                model = "llama-3.1",
                messages = listOf(ChatMessage.user("Hello")),
                stream = true
            )

            val nonStreaming = request.nonStreaming()
            assertFalse(nonStreaming.stream)
            assertEquals(request.model, nonStreaming.model)
            assertEquals(request.messages, nonStreaming.messages)
        }

        @Test
        @DisplayName("withMessage adds message")
        fun withMessageAddsMessage() {
            val request = ChatCompletionRequest(
                model = "llama-3.1",
                messages = listOf(ChatMessage.user("Hello"))
            )

            val updated = request.withMessage(ChatMessage.assistant("Hi!"))
            assertEquals(2, updated.messages.size)
            assertEquals("assistant", updated.messages[1].role)
        }

        @Test
        @DisplayName("simple creates correct request")
        fun simpleCreatesCorrectRequest() {
            val request = ChatCompletionRequest.simple(
                model = "llama-3.1",
                userMessage = "Hello",
                systemPrompt = "You are helpful"
            )

            assertEquals(2, request.messages.size)
            assertTrue(request.messages[0].isSystem)
            assertTrue(request.messages[1].isUser)
        }

        @Test
        @DisplayName("simple without system prompt")
        fun simpleWithoutSystemPrompt() {
            val request = ChatCompletionRequest.simple(
                model = "llama-3.1",
                userMessage = "Hello"
            )

            assertEquals(1, request.messages.size)
            assertTrue(request.messages[0].isUser)
        }
    }

    // =========================================================================
    // ChatMessage Tests
    // =========================================================================

    @Nested
    @DisplayName("ChatMessage")
    inner class ChatMessageTests {

        @Test
        @DisplayName("factory methods create correct roles")
        fun factoryMethodsCreateCorrectRoles() {
            val system = ChatMessage.system("System prompt")
            assertTrue(system.isSystem)
            assertEquals("system", system.role)

            val user = ChatMessage.user("User message")
            assertTrue(user.isUser)
            assertEquals("user", user.role)

            val assistant = ChatMessage.assistant("Assistant response")
            assertTrue(assistant.isAssistant)
            assertEquals("assistant", assistant.role)

            val tool = ChatMessage.toolResult("call-123", "Tool result")
            assertEquals("tool", tool.role)
            assertEquals("call-123", tool.toolCallId)
        }

        @Test
        @DisplayName("hasToolCalls detects tool calls")
        fun hasToolCallsDetectsToolCalls() {
            val withoutTools = ChatMessage.assistant("Just text")
            assertFalse(withoutTools.hasToolCalls)

            val withTools = ChatMessage(
                role = "assistant",
                content = null,
                toolCalls = listOf(ToolCall("id", "function", FunctionCall("name", "{}")))
            )
            assertTrue(withTools.hasToolCalls)
        }

        @Test
        @DisplayName("role constants are correct")
        fun roleConstantsAreCorrect() {
            assertEquals("system", ChatMessage.ROLE_SYSTEM)
            assertEquals("user", ChatMessage.ROLE_USER)
            assertEquals("assistant", ChatMessage.ROLE_ASSISTANT)
            assertEquals("tool", ChatMessage.ROLE_TOOL)
        }
    }

    // =========================================================================
    // ChatCompletionResponse Tests
    // =========================================================================

    @Nested
    @DisplayName("ChatCompletionResponse")
    inner class ChatCompletionResponseTests {

        @Test
        @DisplayName("content extracts first choice content")
        fun contentExtractsFirstChoiceContent() {
            val response = ChatCompletionResponse(
                id = "resp-123",
                model = "llama-3.1",
                choices = listOf(ChatChoice(0, ChatMessage.assistant("Hello!"), "stop")),
                usage = null
            )

            assertEquals("Hello!", response.content)
            assertEquals("stop", response.finishReason)
            assertFalse(response.hasToolCalls)
        }

        @Test
        @DisplayName("empty choices returns null content")
        fun emptyChoicesReturnsNullContent() {
            val response = ChatCompletionResponse(
                id = "resp-123",
                model = "llama-3.1",
                choices = emptyList(),
                usage = null
            )

            assertNull(response.content)
            assertNull(response.finishReason)
        }
    }

    // =========================================================================
    // ToolDefinition Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolDefinition")
    inner class ToolDefinitionTests {

        @Test
        @DisplayName("fromFunction creates tool definition")
        fun fromFunctionCreatesToolDefinition() {
            val function = FunctionDefinition(
                name = "read_file",
                description = "Read a file",
                parameters = mapOf("type" to "object")
            )

            val tool = ToolDefinition.fromFunction(function)
            assertEquals("function", tool.type)
            assertEquals("read_file", tool.function.name)
        }
    }

    // =========================================================================
    // FunctionDefinition Tests
    // =========================================================================

    @Nested
    @DisplayName("FunctionDefinition")
    inner class FunctionDefinitionTests {

        @Test
        @DisplayName("simple creates correct structure")
        fun simpleCreatesCorrectStructure() {
            val function = FunctionDefinition.simple(
                name = "get_weather",
                description = "Get weather for a city",
                properties = mapOf(
                    "city" to ParameterProperty("string", "City name"),
                    "units" to ParameterProperty("string", "Temperature units", listOf("celsius", "fahrenheit"))
                ),
                required = listOf("city")
            )

            assertEquals("get_weather", function.name)
            assertEquals("Get weather for a city", function.description)
            assertEquals("object", function.parameters["type"])
            assertEquals(listOf("city"), function.parameters["required"])
        }
    }

    // =========================================================================
    // ParameterProperty Tests
    // =========================================================================

    @Nested
    @DisplayName("ParameterProperty")
    inner class ParameterPropertyTests {

        @Test
        @DisplayName("toMap includes all fields when present")
        fun toMapIncludesAllFieldsWhenPresent() {
            val prop = ParameterProperty("string", "A description", listOf("a", "b"))
            val map = prop.toMap()

            assertEquals("string", map["type"])
            assertEquals("A description", map["description"])
            assertEquals(listOf("a", "b"), map["enum"])
        }

        @Test
        @DisplayName("toMap excludes enum when null")
        fun toMapExcludesEnumWhenNull() {
            val prop = ParameterProperty("integer", "A number")
            val map = prop.toMap()

            assertEquals("integer", map["type"])
            assertEquals("A number", map["description"])
            assertFalse("enum" in map)
        }
    }

    // =========================================================================
    // ToolCall Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolCall")
    inner class ToolCallTests {

        @Test
        @DisplayName("default type is function")
        fun defaultTypeIsFunction() {
            val call = ToolCall(
                id = "call-123",
                function = FunctionCall("read_file", """{"path": "/test.txt"}""")
            )

            assertEquals("function", call.type)
            assertEquals("call-123", call.id)
            assertEquals("read_file", call.function.name)
        }
    }

    // =========================================================================
    // StreamingChunk Tests
    // =========================================================================

    @Nested
    @DisplayName("StreamingChunk")
    inner class StreamingChunkTests {

        @Test
        @DisplayName("isFinal when finishReason is set")
        fun isFinalWhenFinishReasonIsSet() {
            val ongoing = StreamingChunk("chunk-1", ChatDelta("assistant", "Hello", null), null)
            assertFalse(ongoing.isFinal)

            val finalChunk = StreamingChunk("chunk-2", null, "stop")
            assertTrue(finalChunk.isFinal)
        }
    }

    // =========================================================================
    // TokenUsage Tests
    // =========================================================================

    @Nested
    @DisplayName("TokenUsage")
    inner class TokenUsageTests {

        @Test
        @DisplayName("stores token counts correctly")
        fun storesTokenCountsCorrectly() {
            val usage = TokenUsage(100, 50, 150)
            assertEquals(100, usage.promptTokens)
            assertEquals(50, usage.completionTokens)
            assertEquals(150, usage.totalTokens)
        }
    }

    // =========================================================================
    // DiscoveryResult Tests
    // =========================================================================

    @Nested
    @DisplayName("DiscoveryResult")
    inner class DiscoveryResultTests {

        @Test
        @DisplayName("hasServers returns true when servers found")
        fun hasServersReturnsTrueWhenServersFound() {
            val withServers = DiscoveryResult(
                servers = listOf(DiscoveredServer("localhost", 1234, null, null, 50)),
                scanDurationMs = 100
            )
            assertTrue(withServers.hasServers)

            val empty = DiscoveryResult(servers = emptyList(), scanDurationMs = 100)
            assertFalse(empty.hasServers)
        }
    }

    // =========================================================================
    // DiscoveredServer Tests
    // =========================================================================

    @Nested
    @DisplayName("DiscoveredServer")
    inner class DiscoveredServerTests {

        @Test
        @DisplayName("address formats correctly")
        fun addressFormatsCorrectly() {
            val server = DiscoveredServer("192.168.1.100", 8080, "1.0.0", "llama-3.1", 25)
            assertEquals("192.168.1.100:8080", server.address)
        }
    }
}
