package com.sidekick.llm.lmstudio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for LM Studio Service logic.
 * 
 * Note: These tests focus on the service's pure logic functions
 * without requiring IntelliJ Platform or network connectivity.
 */
@DisplayName("LM Studio Service Tests")
class LmStudioServiceTest {

    // =========================================================================
    // State Tests
    // =========================================================================

    @Nested
    @DisplayName("State")
    inner class StateTests {

        @Test
        @DisplayName("default state has expected values")
        fun defaultStateHasExpectedValues() {
            val state = LmStudioService.State()
            assertEquals("localhost", state.host)
            assertEquals(1234, state.port)
            assertEquals("/v1", state.apiPath)
            assertEquals(5000, state.connectionTimeoutMs)
            assertEquals(120000, state.requestTimeoutMs)
            assertTrue(state.autoConnect)
            assertTrue(state.autoDiscover)
            assertNull(state.preferredModel)
        }

        @Test
        @DisplayName("state converts to config correctly")
        fun stateConvertsToConfigCorrectly() {
            val state = LmStudioService.State(
                host = "192.168.1.100",
                port = 8080,
                apiPath = "/v2",
                connectionTimeoutMs = 10000,
                requestTimeoutMs = 60000,
                autoConnect = false,
                autoDiscover = false,
                preferredModel = "llama-3.1"
            )

            val config = state.toConfig()
            assertEquals("192.168.1.100", config.host)
            assertEquals(8080, config.port)
            assertEquals("/v2", config.apiPath)
            assertEquals(10000, config.connectionTimeoutMs)
            assertEquals(60000, config.requestTimeoutMs)
            assertFalse(config.autoConnect)
            assertFalse(config.autoDiscover)
        }

        @Test
        @DisplayName("state creates from config correctly")
        fun stateCreatesFromConfigCorrectly() {
            val config = LmStudioConfig(
                host = "my-server",
                port = 5000,
                autoConnect = true,
                autoDiscover = false
            )

            val state = LmStudioService.State.from(config, "my-model")
            assertEquals("my-server", state.host)
            assertEquals(5000, state.port)
            assertTrue(state.autoConnect)
            assertFalse(state.autoDiscover)
            assertEquals("my-model", state.preferredModel)
        }

        @Test
        @DisplayName("baseUrl constructed correctly from state config")
        fun baseUrlConstructedCorrectlyFromStateConfig() {
            val state = LmStudioService.State(host = "lm.local", port = 9999)
            val config = state.toConfig()
            assertEquals("http://lm.local:9999/v1", config.baseUrl)
        }
    }

    // =========================================================================
    // JSON Building Tests (Testing the logic, not network)
    // =========================================================================

    @Nested
    @DisplayName("Chat Request Building")
    inner class ChatRequestBuildingTests {

        @Test
        @DisplayName("simple request creates valid structure")
        fun simpleRequestCreatesValidStructure() {
            val request = ChatCompletionRequest.simple(
                model = "llama-3.1",
                userMessage = "Hello, world!",
                systemPrompt = "You are helpful"
            )

            assertEquals("llama-3.1", request.model)
            assertEquals(2, request.messages.size)
            assertEquals("system", request.messages[0].role)
            assertEquals("user", request.messages[1].role)
            assertEquals("You are helpful", request.messages[0].content)
            assertEquals("Hello, world!", request.messages[1].content)
        }

        @Test
        @DisplayName("request with tools sets hasTools")
        fun requestWithToolsSetsHasTools() {
            val tool = ToolDefinition.fromFunction(
                FunctionDefinition(
                    name = "read_file",
                    description = "Read a file",
                    parameters = mapOf("type" to "object")
                )
            )

            val request = ChatCompletionRequest(
                model = "llama-3.1",
                messages = listOf(ChatMessage.user("Read test.txt")),
                tools = listOf(tool)
            )

            assertTrue(request.hasTools)
        }

        @Test
        @DisplayName("request without tools has no tools")
        fun requestWithoutToolsHasNoTools() {
            val request = ChatCompletionRequest(
                model = "llama-3.1",
                messages = listOf(ChatMessage.user("Hello"))
            )

            assertFalse(request.hasTools)
        }
    }

    // =========================================================================
    // Model Inference Tests
    // =========================================================================

    @Nested
    @DisplayName("Model Family Inference")
    inner class ModelFamilyInferenceTests {

        @Test
        @DisplayName("infers Llama family correctly")
        fun infersLlamaFamilyCorrectly() {
            assertEquals(ModelFamily.LLAMA, LmStudioModel.inferFamily("llama-3.1-8b-instruct"))
            assertEquals(ModelFamily.LLAMA, LmStudioModel.inferFamily("meta-llama-3-70b"))
            assertEquals(ModelFamily.LLAMA, LmStudioModel.inferFamily("Llama-2-13B-GGUF"))
        }

        @Test
        @DisplayName("infers Code Llama family correctly")
        fun infersCodeLlamaFamilyCorrectly() {
            assertEquals(ModelFamily.CODELLAMA, LmStudioModel.inferFamily("codellama-13b"))
            assertEquals(ModelFamily.CODELLAMA, LmStudioModel.inferFamily("CodeLlama-34b-Instruct"))
            assertEquals(ModelFamily.CODELLAMA, LmStudioModel.inferFamily("llama-code-7b"))
        }

        @Test
        @DisplayName("infers Mistral family correctly")
        fun infersMistralFamilyCorrectly() {
            assertEquals(ModelFamily.MISTRAL, LmStudioModel.inferFamily("mistral-7b"))
            assertEquals(ModelFamily.MISTRAL, LmStudioModel.inferFamily("mistral-nemo"))
            assertEquals(ModelFamily.MISTRAL, LmStudioModel.inferFamily("mixtral-8x7b"))
        }

        @Test
        @DisplayName("infers DeepSeek family correctly")
        fun infersDeepSeekFamilyCorrectly() {
            assertEquals(ModelFamily.DEEPSEEK, LmStudioModel.inferFamily("deepseek-coder-6.7b"))
            assertEquals(ModelFamily.DEEPSEEK, LmStudioModel.inferFamily("deepseek-v2-lite"))
        }

        @Test
        @DisplayName("infers Qwen family correctly")
        fun infersQwenFamilyCorrectly() {
            assertEquals(ModelFamily.QWEN, LmStudioModel.inferFamily("qwen2-7b"))
            assertEquals(ModelFamily.QWEN, LmStudioModel.inferFamily("Qwen1.5-0.5B-Chat"))
        }

        @Test
        @DisplayName("infers Phi family correctly")
        fun infersPhiFamilyCorrectly() {
            assertEquals(ModelFamily.PHI, LmStudioModel.inferFamily("phi-3-mini"))
            assertEquals(ModelFamily.PHI, LmStudioModel.inferFamily("Phi-2"))
        }

        @Test
        @DisplayName("infers Gemma family correctly")
        fun infersGemmaFamilyCorrectly() {
            assertEquals(ModelFamily.GEMMA, LmStudioModel.inferFamily("gemma-2-9b"))
            assertEquals(ModelFamily.GEMMA, LmStudioModel.inferFamily("gemma-7b-it"))
        }

        @Test
        @DisplayName("infers StarCoder family correctly")
        fun infersStarCoderFamilyCorrectly() {
            assertEquals(ModelFamily.STARCODER, LmStudioModel.inferFamily("starcoder2-7b"))
            assertEquals(ModelFamily.STARCODER, LmStudioModel.inferFamily("starcoder-15b"))
        }

        @Test
        @DisplayName("unknown models return OTHER")
        fun unknownModelsReturnOther() {
            assertEquals(ModelFamily.OTHER, LmStudioModel.inferFamily("some-random-model"))
            assertEquals(ModelFamily.OTHER, LmStudioModel.inferFamily("custom-fine-tune"))
        }
    }

    // =========================================================================
    // Capability Inference Tests
    // =========================================================================

    @Nested
    @DisplayName("Model Capability Inference")
    inner class ModelCapabilityInferenceTests {

        @Test
        @DisplayName("all models have chat and completion")
        fun allModelsHaveChatAndCompletion() {
            val capabilities = LmStudioModel.inferCapabilities("any-model", ModelFamily.OTHER)
            assertTrue(ModelCapability.CHAT in capabilities)
            assertTrue(ModelCapability.COMPLETION in capabilities)
        }

        @Test
        @DisplayName("code models have CODE capability")
        fun codeModelsHaveCodeCapability() {
            val codeLlama = LmStudioModel.inferCapabilities("codellama-13b", ModelFamily.CODELLAMA)
            assertTrue(ModelCapability.CODE in codeLlama)

            val deepseek = LmStudioModel.inferCapabilities("deepseek-coder", ModelFamily.DEEPSEEK)
            assertTrue(ModelCapability.CODE in deepseek)

            val starcoder = LmStudioModel.inferCapabilities("starcoder2", ModelFamily.STARCODER)
            assertTrue(ModelCapability.CODE in starcoder)

            val withCoder = LmStudioModel.inferCapabilities("some-coder-model", ModelFamily.OTHER)
            assertTrue(ModelCapability.CODE in withCoder)
        }

        @Test
        @DisplayName("instruct models have function calling")
        fun instructModelsHaveFunctionCalling() {
            val mistral = LmStudioModel.inferCapabilities("mistral-7b-instruct", ModelFamily.MISTRAL)
            assertTrue(ModelCapability.FUNCTION_CALLING in mistral)

            val llama = LmStudioModel.inferCapabilities("llama-3-chat", ModelFamily.LLAMA)
            assertTrue(ModelCapability.FUNCTION_CALLING in llama)

            val qwen = LmStudioModel.inferCapabilities("qwen2-instruct", ModelFamily.QWEN)
            assertTrue(ModelCapability.FUNCTION_CALLING in qwen)
        }
    }

    // =========================================================================
    // Connection Status Tests
    // =========================================================================

    @Nested
    @DisplayName("Connection Status")
    inner class ConnectionStatusTests {

        @Test
        @DisplayName("connected status displays correctly")
        fun connectedStatusDisplaysCorrectly() {
            val withModel = ConnectionStatus.connected("1.0", "llama-3.1")
            assertEquals("Connected (llama-3.1)", withModel.displayStatus)
            assertEquals("🟢", withModel.statusIcon)
            assertFalse(withModel.shouldRetry)

            val withoutModel = ConnectionStatus.connected()
            assertEquals("Connected (no model loaded)", withoutModel.displayStatus)
            assertEquals("🟡", withoutModel.statusIcon)
        }

        @Test
        @DisplayName("disconnected status displays correctly")
        fun disconnectedStatusDisplaysCorrectly() {
            val withError = ConnectionStatus.disconnected("Connection refused")
            assertEquals("Error: Connection refused", withError.displayStatus)
            assertEquals("🔴", withError.statusIcon)
            assertTrue(withError.shouldRetry)

            val withoutError = ConnectionStatus.disconnected()
            assertEquals("Disconnected", withoutError.displayStatus)
            assertFalse(withoutError.shouldRetry)
        }
    }

    // =========================================================================
    // Message Building Tests
    // =========================================================================

    @Nested
    @DisplayName("Message Building")
    inner class MessageBuildingTests {

        @Test
        @DisplayName("system message created correctly")
        fun systemMessageCreatedCorrectly() {
            val msg = ChatMessage.system("You are a helpful assistant")
            assertEquals("system", msg.role)
            assertEquals("You are a helpful assistant", msg.content)
            assertTrue(msg.isSystem)
            assertFalse(msg.isUser)
        }

        @Test
        @DisplayName("user message created correctly")
        fun userMessageCreatedCorrectly() {
            val msg = ChatMessage.user("Hello!")
            assertEquals("user", msg.role)
            assertEquals("Hello!", msg.content)
            assertTrue(msg.isUser)
            assertFalse(msg.isAssistant)
        }

        @Test
        @DisplayName("assistant message created correctly")
        fun assistantMessageCreatedCorrectly() {
            val msg = ChatMessage.assistant("Hi there!")
            assertEquals("assistant", msg.role)
            assertEquals("Hi there!", msg.content)
            assertTrue(msg.isAssistant)
        }

        @Test
        @DisplayName("tool result message created correctly")
        fun toolResultMessageCreatedCorrectly() {
            val msg = ChatMessage.toolResult("call-123", "File contents here")
            assertEquals("tool", msg.role)
            assertEquals("call-123", msg.toolCallId)
            assertEquals("File contents here", msg.content)
        }
    }

    // =========================================================================
    // Quantization Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Quantization Detection")
    inner class QuantizationDetectionTests {

        @Test
        @DisplayName("model properties track quantization")
        fun modelPropertiesTrackQuantization() {
            val model = LmStudioModel(
                id = "llama-3.1-8b-Q4_K_M",
                name = "Llama 3.1 8B Q4_K_M",
                path = null,
                size = null,
                quantization = "Q4_K_M",
                contextLength = 8192,
                family = ModelFamily.LLAMA,
                capabilities = setOf(ModelCapability.CHAT),
                isLoaded = false
            )

            assertEquals("Q4_K_M", model.quantization)
        }
    }

    // =========================================================================
    // Tool Definition Tests
    // =========================================================================

    @Nested
    @DisplayName("Tool Definition")
    inner class ToolDefinitionTests {

        @Test
        @DisplayName("simple function definition created correctly")
        fun simpleFunctionDefinitionCreatedCorrectly() {
            val function = FunctionDefinition.simple(
                name = "read_file",
                description = "Read the contents of a file",
                properties = mapOf(
                    "path" to ParameterProperty("string", "The file path"),
                    "encoding" to ParameterProperty("string", "File encoding", listOf("utf-8", "ascii"))
                ),
                required = listOf("path")
            )

            assertEquals("read_file", function.name)
            assertEquals("Read the contents of a file", function.description)
            assertEquals("object", function.parameters["type"])
            assertEquals(listOf("path"), function.parameters["required"])

            @Suppress("UNCHECKED_CAST")
            val properties = function.parameters["properties"] as Map<String, Map<String, Any>>
            assertEquals("string", properties["path"]?.get("type"))
            assertEquals(listOf("utf-8", "ascii"), properties["encoding"]?.get("enum"))
        }

        @Test
        @DisplayName("tool definition wraps function correctly")
        fun toolDefinitionWrapsFunctionCorrectly() {
            val function = FunctionDefinition(
                name = "test",
                description = "A test function",
                parameters = mapOf("type" to "object")
            )

            val tool = ToolDefinition.fromFunction(function)
            assertEquals("function", tool.type)
            assertEquals("test", tool.function.name)
        }
    }

    // =========================================================================
    // Response Parsing Tests
    // =========================================================================

    @Nested
    @DisplayName("Response Parsing")
    inner class ResponseParsingTests {

        @Test
        @DisplayName("response extracts content correctly")
        fun responseExtractsContentCorrectly() {
            val response = ChatCompletionResponse(
                id = "chatcmpl-123",
                model = "llama-3.1",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage.assistant("Hello, I'm here to help!"),
                        finishReason = "stop"
                    )
                ),
                usage = TokenUsage(10, 8, 18)
            )

            assertEquals("Hello, I'm here to help!", response.content)
            assertEquals("stop", response.finishReason)
            assertFalse(response.hasToolCalls)
        }

        @Test
        @DisplayName("response with tool calls detected")
        fun responseWithToolCallsDetected() {
            val response = ChatCompletionResponse(
                id = "chatcmpl-456",
                model = "llama-3.1",
                choices = listOf(
                    ChatChoice(
                        index = 0,
                        message = ChatMessage(
                            role = "assistant",
                            content = null,
                            toolCalls = listOf(
                                ToolCall("call-1", "function", FunctionCall("read_file", """{"path":"/test.txt"}"""))
                            )
                        ),
                        finishReason = "tool_calls"
                    )
                ),
                usage = null
            )

            assertTrue(response.hasToolCalls)
            assertEquals(1, response.toolCalls?.size)
            assertEquals("read_file", response.toolCalls?.first()?.function?.name)
        }

        @Test
        @DisplayName("empty choices handled gracefully")
        fun emptyChoicesHandledGracefully() {
            val response = ChatCompletionResponse(
                id = "chatcmpl-789",
                model = "llama-3.1",
                choices = emptyList(),
                usage = null
            )

            assertNull(response.content)
            assertNull(response.finishReason)
            assertNull(response.toolCalls)
            assertFalse(response.hasToolCalls)
        }
    }

    // =========================================================================
    // Streaming Chunk Tests
    // =========================================================================

    @Nested
    @DisplayName("Streaming Chunks")
    inner class StreamingChunkTests {

        @Test
        @DisplayName("ongoing chunk is not final")
        fun ongoingChunkIsNotFinal() {
            val chunk = StreamingChunk(
                id = "chunk-1",
                delta = ChatDelta("assistant", "Hello", null),
                finishReason = null
            )

            assertFalse(chunk.isFinal)
        }

        @Test
        @DisplayName("final chunk detected by finish reason")
        fun finalChunkDetectedByFinishReason() {
            val chunk = StreamingChunk(
                id = "chunk-final",
                delta = null,
                finishReason = "stop"
            )

            assertTrue(chunk.isFinal)
        }

        @Test
        @DisplayName("delta with content extracted")
        fun deltaWithContentExtracted() {
            val delta = ChatDelta("assistant", "Hello, ", null)
            assertEquals("assistant", delta.role)
            assertEquals("Hello, ", delta.content)
        }
    }

    // =========================================================================
    // Discovery Result Tests
    // =========================================================================

    @Nested
    @DisplayName("Discovery Results")
    inner class DiscoveryResultTests {

        @Test
        @DisplayName("hasServers when servers found")
        fun hasServersWhenServersFound() {
            val result = DiscoveryResult(
                servers = listOf(
                    DiscoveredServer("localhost", 1234, "1.0", "llama-3.1", 25)
                ),
                scanDurationMs = 100
            )

            assertTrue(result.hasServers)
        }

        @Test
        @DisplayName("no servers when empty")
        fun noServersWhenEmpty() {
            val result = DiscoveryResult(servers = emptyList(), scanDurationMs = 500)
            assertFalse(result.hasServers)
        }

        @Test
        @DisplayName("server address formatted correctly")
        fun serverAddressFormattedCorrectly() {
            val server = DiscoveredServer("192.168.1.100", 8080, null, null, 50)
            assertEquals("192.168.1.100:8080", server.address)
        }
    }
}
