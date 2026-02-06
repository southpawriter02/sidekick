package com.sidekick.llm.provider

import com.sidekick.llm.lmstudio.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * # LM Studio LLM Provider
 *
 * LlmProvider implementation for LM Studio backend.
 * Part of Sidekick v0.8.2 Provider Abstraction feature.
 *
 * Wraps the LmStudioService to conform to the unified provider interface.
 *
 * @since 0.8.2
 */
class LmStudioLlmProvider : LlmProvider {

    private val service: LmStudioService by lazy { LmStudioService.getInstance() }

    override val name: String = "LM Studio"
    override val type: ProviderType = ProviderType.LM_STUDIO

    override val isAvailable: Boolean
        get() = service.getStatus().connected

    // =========================================================================
    // Models
    // =========================================================================

    override suspend fun listModels(): List<UnifiedModel> {
        return service.listModels().map { model ->
            UnifiedModel(
                id = model.id,
                provider = ProviderType.LM_STUDIO,
                displayName = model.name,
                contextLength = model.contextLength,
                capabilities = model.capabilities.map { it.name.lowercase() }.toSet(),
                isLoaded = model.isLoaded,
                metadata = buildMap {
                    model.path?.let { put("path", it) }
                    model.size?.let { put("size", it) }
                    model.quantization?.let { put("quantization", it) }
                    put("family", model.family.name)
                }
            )
        }
    }

    // =========================================================================
    // Chat
    // =========================================================================

    override suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse {
        val lmRequest = toLmStudioRequest(request)
        val response = service.chat(lmRequest)

        return UnifiedChatResponse(
            content = response.content,
            toolCalls = response.toolCalls?.map { call ->
                ToolCallRequest(
                    id = call.id,
                    name = call.function.name,
                    arguments = call.function.arguments
                )
            },
            usage = response.usage?.let { usage ->
                TokenUsage(usage.promptTokens, usage.completionTokens, usage.totalTokens)
            },
            finishReason = response.finishReason,
            model = response.model
        )
    }

    override fun streamChat(request: UnifiedChatRequest): Flow<String> {
        val lmRequest = toLmStudioRequest(request)
        return service.streamChat(lmRequest)
    }

    // =========================================================================
    // Embeddings
    // =========================================================================

    override suspend fun embed(text: String): List<Float> {
        return service.embed(text)
    }

    // =========================================================================
    // Health
    // =========================================================================

    override suspend fun checkHealth(): ProviderHealth {
        val startTime = System.currentTimeMillis()
        val status = service.checkConnection()
        val latency = System.currentTimeMillis() - startTime

        return if (status.connected) {
            ProviderHealth.healthy(latency, status.loadedModel)
        } else {
            ProviderHealth.unhealthy(status.error ?: "Not connected")
        }
    }

    // =========================================================================
    // Conversion
    // =========================================================================

    private fun toLmStudioRequest(request: UnifiedChatRequest): ChatCompletionRequest {
        val messages = request.allMessages.map { msg ->
            ChatMessage(
                role = msg.role.toApiString(),
                content = msg.content
            )
        }

        return ChatCompletionRequest(
            model = request.model,
            messages = messages,
            temperature = request.temperature,
            maxTokens = request.maxTokens,
            stream = request.stream,
            tools = request.tools?.map { tool ->
                ToolDefinition(
                    function = FunctionDefinition(
                        name = tool.name,
                        description = tool.description,
                        parameters = mapOf(
                            "type" to tool.parameters.type,
                            "properties" to tool.parameters.properties.mapValues { (_, schema) ->
                                mapOf(
                                    "type" to schema.type,
                                    "description" to schema.description
                                )
                            },
                            "required" to tool.parameters.required
                        )
                    )
                )
            }
        )
    }
}
