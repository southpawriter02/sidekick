# Sidekick v0.8.x – LM Studio Integration & Local Coding Agent

> **Phase Goal:** Deep integration with LM Studio for local LLM-powered coding agent capabilities  
> **Building On:** v0.7.x Gamification, existing Ollama infrastructure

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.8.1 | LM Studio Connection | Discovery, connection, model management |
| v0.8.2 | Provider Abstraction | Unified API for Ollama + LM Studio |
| v0.8.3 | Agent Task System | Task planning, execution, and tool use |
| v0.8.4 | Code Understanding | AST analysis, semantic search, embeddings |
| v0.8.5 | Agent Actions | File operations, refactoring, test generation |
| v0.8.6 | Conversation Memory | Long-term context and session persistence |
| v0.8.7 | Agent Orchestration | Multi-step workflows and autonomous mode |

---

## v0.8.1 — LM Studio Connection

### v0.8.1a — LmStudioModels

```kotlin
package com.sidekick.llm.lmstudio

import java.time.Instant

/**
 * LM Studio server connection configuration.
 */
data class LmStudioConfig(
    val host: String = "localhost",
    val port: Int = 1234,
    val apiPath: String = "/v1",
    val connectionTimeoutMs: Long = 5000,
    val requestTimeoutMs: Long = 120000,
    val autoConnect: Boolean = true,
    val autoDiscover: Boolean = true
) {
    val baseUrl: String get() = "http://$host:$port$apiPath"
}

/**
 * Available model in LM Studio.
 */
data class LmStudioModel(
    val id: String,
    val name: String,
    val path: String?,
    val size: Long?,
    val quantization: String?,
    val contextLength: Int,
    val family: ModelFamily,
    val capabilities: Set<ModelCapability>,
    val isLoaded: Boolean
)

enum class ModelFamily { LLAMA, MISTRAL, CODELLAMA, DEEPSEEK, QWEN, PHI, GEMMA, OTHER }

enum class ModelCapability { CHAT, COMPLETION, CODE, EMBEDDING, FUNCTION_CALLING, VISION }

/**
 * Connection status.
 */
data class ConnectionStatus(
    val connected: Boolean,
    val serverVersion: String?,
    val loadedModel: String?,
    val lastCheck: Instant,
    val error: String?
)

/**
 * Chat completion request (OpenAI-compatible).
 */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val stream: Boolean = true,
    val stop: List<String>? = null,
    val tools: List<ToolDefinition>? = null,
    val toolChoice: String? = null
)

data class ChatMessage(
    val role: String, // system, user, assistant, tool
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
)

data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

data class FunctionCall(val name: String, val arguments: String)
```

---

### v0.8.1b — LmStudioService

```kotlin
package com.sidekick.llm.lmstudio

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

@Service(Service.Level.APP)
@State(name = "SidekickLmStudio", storages = [Storage("sidekick-lmstudio.xml")])
class LmStudioService : PersistentStateComponent<LmStudioService.State> {

    data class State(
        var config: LmStudioConfig = LmStudioConfig(),
        var preferredModel: String? = null
    )

    private var state = State()
    private val client = HttpClient()
    private var connectionStatus = ConnectionStatus(false, null, null, java.time.Instant.now(), null)

    companion object {
        fun getInstance(): LmStudioService {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(LmStudioService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Checks connection to LM Studio server.
     */
    suspend fun checkConnection(): ConnectionStatus {
        return try {
            val response = client.get("${state.config.baseUrl}/models")
            connectionStatus = ConnectionStatus(
                connected = response.status == HttpStatusCode.OK,
                serverVersion = null,
                loadedModel = null,
                lastCheck = java.time.Instant.now(),
                error = null
            )
            connectionStatus
        } catch (e: Exception) {
            connectionStatus = ConnectionStatus(false, null, null, java.time.Instant.now(), e.message)
            connectionStatus
        }
    }

    /**
     * Lists available models.
     */
    suspend fun listModels(): List<LmStudioModel> {
        val response = client.get("${state.config.baseUrl}/models")
        // Parse OpenAI-compatible model list response
        return emptyList() // Implementation would parse JSON
    }

    /**
     * Streams a chat completion.
     */
    fun streamChat(request: ChatCompletionRequest): Flow<String> = flow {
        val response = client.post("${state.config.baseUrl}/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ChatCompletionRequest.serializer(), request))
        }
        // Parse SSE stream and emit tokens
    }

    /**
     * Non-streaming chat completion.
     */
    suspend fun chat(request: ChatCompletionRequest): String {
        val nonStreamRequest = request.copy(stream = false)
        val response = client.post("${state.config.baseUrl}/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ChatCompletionRequest.serializer(), nonStreamRequest))
        }
        return response.bodyAsText()
    }

    /**
     * Generates embeddings.
     */
    suspend fun embed(text: String, model: String? = null): List<Float> {
        // POST to /embeddings endpoint
        return emptyList()
    }

    fun getStatus(): ConnectionStatus = connectionStatus
}
```

---

### v0.8.1c — LmStudioSettingsPanel

```kotlin
package com.sidekick.llm.lmstudio

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class LmStudioSettingsConfigurable : Configurable {
    private var hostField = JBTextField()
    private var portField = JBTextField()
    private var autoConnectCheckbox = JBCheckBox("Auto-connect on startup")
    
    override fun getDisplayName() = "LM Studio"

    override fun createComponent(): JComponent = panel {
        group("Connection") {
            row("Host:") { cell(hostField).columns(COLUMNS_MEDIUM) }
            row("Port:") { cell(portField).columns(COLUMNS_SHORT) }
            row { cell(autoConnectCheckbox) }
            row {
                button("Test Connection") { testConnection() }
                button("Discover") { discoverServer() }
            }
        }
        
        group("Model Selection") {
            row("Preferred Model:") { comboBox(listOf<String>()) }
            row { button("Refresh Models") { refreshModels() } }
        }
        
        group("Performance") {
            row("Context Length:") { intTextField(1024..32768).columns(COLUMNS_SHORT) }
            row("Request Timeout (sec):") { intTextField(10..300).columns(COLUMNS_SHORT) }
        }
    }

    private fun testConnection() { /* Test and show result */ }
    private fun discoverServer() { /* Scan for LM Studio on network */ }
    private fun refreshModels() { /* Reload model list */ }

    override fun isModified() = false
    override fun apply() {}
}
```

---

## v0.8.2 — Provider Abstraction

### v0.8.2a — ProviderModels

```kotlin
package com.sidekick.llm.provider

/**
 * Unified LLM provider interface.
 */
interface LlmProvider {
    val name: String
    val isAvailable: Boolean
    
    suspend fun listModels(): List<UnifiedModel>
    suspend fun chat(request: UnifiedChatRequest): UnifiedChatResponse
    fun streamChat(request: UnifiedChatRequest): kotlinx.coroutines.flow.Flow<String>
    suspend fun embed(text: String): List<Float>
    suspend fun checkHealth(): ProviderHealth
}

/**
 * Unified model representation.
 */
data class UnifiedModel(
    val id: String,
    val provider: ProviderType,
    val displayName: String,
    val contextLength: Int,
    val capabilities: Set<String>,
    val isLoaded: Boolean
)

enum class ProviderType { OLLAMA, LM_STUDIO, OPENAI, ANTHROPIC, CUSTOM }

data class UnifiedChatRequest(
    val model: String,
    val messages: List<UnifiedMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null,
    val tools: List<AgentTool>? = null
)

data class UnifiedMessage(
    val role: MessageRole,
    val content: String,
    val toolResults: List<ToolResult>? = null
)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

data class UnifiedChatResponse(
    val content: String,
    val toolCalls: List<ToolCallRequest>?,
    val usage: TokenUsage?,
    val finishReason: String?
)

data class TokenUsage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int)

data class ProviderHealth(val healthy: Boolean, val latencyMs: Long?, val error: String?)
```

---

### v0.8.2b — ProviderManager

```kotlin
package com.sidekick.llm.provider

import com.intellij.openapi.components.Service
import com.sidekick.llm.lmstudio.LmStudioService
import com.sidekick.services.ollama.OllamaService

@Service(Service.Level.APP)
class ProviderManager {
    
    private val providers = mutableMapOf<ProviderType, LlmProvider>()
    private var activeProvider: ProviderType = ProviderType.OLLAMA

    companion object {
        fun getInstance(): ProviderManager {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(ProviderManager::class.java)
        }
    }

    fun initialize() {
        providers[ProviderType.OLLAMA] = OllamaLlmProvider()
        providers[ProviderType.LM_STUDIO] = LmStudioLlmProvider()
    }

    fun getActiveProvider(): LlmProvider = providers[activeProvider]!!
    
    fun setActiveProvider(type: ProviderType) {
        activeProvider = type
    }

    fun getAllProviders(): List<LlmProvider> = providers.values.toList()

    fun getAvailableProviders(): List<LlmProvider> = providers.values.filter { it.isAvailable }

    suspend fun getBestAvailableProvider(): LlmProvider? {
        return providers.values.firstOrNull { 
            it.checkHealth().healthy 
        }
    }
}
```

---

## v0.8.3 — Agent Task System

### v0.8.3a — AgentTaskModels

```kotlin
package com.sidekick.agent.tasks

import java.time.Instant
import java.util.UUID

/**
 * A task the agent can execute.
 */
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val description: String,
    val context: TaskContext,
    val constraints: TaskConstraints = TaskConstraints(),
    val status: TaskStatus = TaskStatus.PENDING,
    val steps: List<TaskStep> = emptyList(),
    val result: TaskResult? = null,
    val createdAt: Instant = Instant.now()
)

enum class TaskType {
    EXPLAIN_CODE, REFACTOR, GENERATE_TESTS, FIX_BUG, IMPLEMENT_FEATURE,
    DOCUMENT, OPTIMIZE, REVIEW, ANSWER_QUESTION, CUSTOM
}

data class TaskContext(
    val projectPath: String,
    val activeFile: String?,
    val selectedCode: String?,
    val cursorPosition: Int?,
    val relatedFiles: List<String> = emptyList(),
    val errorContext: String? = null,
    val userInstructions: String
)

data class TaskConstraints(
    val maxSteps: Int = 10,
    val maxTokens: Int = 8000,
    val allowFileModification: Boolean = true,
    val allowNewFiles: Boolean = true,
    val requireConfirmation: Boolean = true,
    val timeoutSeconds: Int = 300
)

enum class TaskStatus { PENDING, PLANNING, EXECUTING, AWAITING_CONFIRMATION, COMPLETED, FAILED, CANCELLED }

data class TaskStep(
    val id: Int,
    val action: AgentAction,
    val reasoning: String,
    val status: StepStatus,
    val result: String?
)

enum class StepStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

data class TaskResult(
    val success: Boolean,
    val summary: String,
    val filesModified: List<String>,
    val filesCreated: List<String>,
    val errors: List<String>
)
```

---

### v0.8.3b — AgentTools

```kotlin
package com.sidekick.agent.tools

/**
 * Tool the agent can invoke.
 */
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
    val handler: suspend (Map<String, Any>) -> ToolResult
)

data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ParameterSchema>,
    val required: List<String>
)

data class ParameterSchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>
)

/**
 * Built-in agent tools.
 */
object BuiltInTools {
    
    val READ_FILE = AgentTool(
        name = "read_file",
        description = "Read the contents of a file",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema("string", "Absolute path to the file"),
                "startLine" to ParameterSchema("integer", "Starting line (optional)"),
                "endLine" to ParameterSchema("integer", "Ending line (optional)")
            ),
            required = listOf("path")
        ),
        handler = { args -> readFileHandler(args) }
    )

    val WRITE_FILE = AgentTool(
        name = "write_file",
        description = "Write content to a file (creates or overwrites)",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema("string", "Absolute path"),
                "content" to ParameterSchema("string", "File content")
            ),
            required = listOf("path", "content")
        ),
        handler = { args -> writeFileHandler(args) }
    )

    val EDIT_FILE = AgentTool(
        name = "edit_file",
        description = "Make targeted edits to a file",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema("string", "Absolute path"),
                "edits" to ParameterSchema("array", "List of edits with old/new text")
            ),
            required = listOf("path", "edits")
        ),
        handler = { args -> editFileHandler(args) }
    )

    val SEARCH_CODE = AgentTool(
        name = "search_code",
        description = "Search for code patterns in the project",
        parameters = ToolParameters(
            properties = mapOf(
                "query" to ParameterSchema("string", "Search query or regex"),
                "filePattern" to ParameterSchema("string", "File glob pattern (optional)")
            ),
            required = listOf("query")
        ),
        handler = { args -> searchCodeHandler(args) }
    )

    val LIST_FILES = AgentTool(
        name = "list_files",
        description = "List files in a directory",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema("string", "Directory path"),
                "recursive" to ParameterSchema("boolean", "Include subdirectories")
            ),
            required = listOf("path")
        ),
        handler = { args -> listFilesHandler(args) }
    )

    val RUN_COMMAND = AgentTool(
        name = "run_command",
        description = "Run a shell command",
        parameters = ToolParameters(
            properties = mapOf(
                "command" to ParameterSchema("string", "Command to run"),
                "cwd" to ParameterSchema("string", "Working directory")
            ),
            required = listOf("command")
        ),
        handler = { args -> runCommandHandler(args) }
    )

    val GET_SYMBOL_INFO = AgentTool(
        name = "get_symbol_info",
        description = "Get information about a code symbol",
        parameters = ToolParameters(
            properties = mapOf(
                "symbol" to ParameterSchema("string", "Symbol name"),
                "file" to ParameterSchema("string", "File path (optional)")
            ),
            required = listOf("symbol")
        ),
        handler = { args -> getSymbolInfoHandler(args) }
    )

    val ALL = listOf(READ_FILE, WRITE_FILE, EDIT_FILE, SEARCH_CODE, LIST_FILES, RUN_COMMAND, GET_SYMBOL_INFO)

    private suspend fun readFileHandler(args: Map<String, Any>): ToolResult { TODO() }
    private suspend fun writeFileHandler(args: Map<String, Any>): ToolResult { TODO() }
    private suspend fun editFileHandler(args: Map<String, Any>): ToolResult { TODO() }
    private suspend fun searchCodeHandler(args: Map<String, Any>): ToolResult { TODO() }
    private suspend fun listFilesHandler(args: Map<String, Any>): ToolResult { TODO() }
    private suspend fun runCommandHandler(args: Map<String, Any>): ToolResult { TODO() }
    private suspend fun getSymbolInfoHandler(args: Map<String, Any>): ToolResult { TODO() }
}
```

---

### v0.8.3c — AgentExecutor

```kotlin
package com.sidekick.agent

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.agent.tasks.*
import com.sidekick.agent.tools.*
import com.sidekick.llm.provider.ProviderManager

@Service(Service.Level.PROJECT)
class AgentExecutor(private val project: Project) {

    companion object {
        fun getInstance(project: Project): AgentExecutor {
            return project.getService(AgentExecutor::class.java)
        }
        
        private val AGENT_SYSTEM_PROMPT = """
            You are a coding assistant agent with access to tools for reading, writing, and 
            analyzing code. You can:
            - Read and write files
            - Search codebases
            - Run commands
            - Analyze symbols and structure
            
            When given a task:
            1. Understand the goal
            2. Gather context by reading relevant files
            3. Plan your approach
            4. Execute changes step by step
            5. Verify your work
            
            Always explain your reasoning before each action.
            Use tools when you need information - don't guess.
        """.trimIndent()
    }

    private val tools = BuiltInTools.ALL
    private var currentTask: AgentTask? = null

    /**
     * Executes a task.
     */
    suspend fun executeTask(task: AgentTask): TaskResult {
        currentTask = task.copy(status = TaskStatus.PLANNING)
        
        val provider = ProviderManager.getInstance().getActiveProvider()
        val messages = mutableListOf(
            com.sidekick.llm.provider.UnifiedMessage(
                com.sidekick.llm.provider.MessageRole.SYSTEM,
                AGENT_SYSTEM_PROMPT
            ),
            com.sidekick.llm.provider.UnifiedMessage(
                com.sidekick.llm.provider.MessageRole.USER,
                buildTaskPrompt(task)
            )
        )

        var stepCount = 0
        val steps = mutableListOf<TaskStep>()

        while (stepCount < task.constraints.maxSteps) {
            val response = provider.chat(
                com.sidekick.llm.provider.UnifiedChatRequest(
                    model = "", // From config
                    messages = messages,
                    tools = tools.map { it.toAgentTool() }
                )
            )

            if (response.toolCalls.isNullOrEmpty()) {
                // Agent is done
                break
            }

            for (toolCall in response.toolCalls) {
                val tool = tools.find { it.name == toolCall.name }
                if (tool == null) {
                    steps.add(TaskStep(stepCount++, AgentAction.ERROR, "Unknown tool", StepStatus.FAILED, null))
                    continue
                }

                if (task.constraints.requireConfirmation && isDestructiveAction(tool)) {
                    currentTask = currentTask?.copy(status = TaskStatus.AWAITING_CONFIRMATION)
                    // Wait for user confirmation
                }

                val result = tool.handler(toolCall.arguments)
                steps.add(TaskStep(
                    stepCount++,
                    AgentAction.TOOL_CALL,
                    "Called ${tool.name}",
                    if (result.success) StepStatus.COMPLETED else StepStatus.FAILED,
                    result.output
                ))

                messages.add(com.sidekick.llm.provider.UnifiedMessage(
                    com.sidekick.llm.provider.MessageRole.TOOL,
                    result.output,
                    toolResults = listOf(com.sidekick.llm.provider.ToolResult(toolCall.id, result.output))
                ))
            }
        }

        return TaskResult(
            success = steps.none { it.status == StepStatus.FAILED },
            summary = "Completed ${steps.size} steps",
            filesModified = emptyList(),
            filesCreated = emptyList(),
            errors = steps.filter { it.status == StepStatus.FAILED }.mapNotNull { it.result }
        )
    }

    private fun buildTaskPrompt(task: AgentTask): String {
        return buildString {
            appendLine("Task: ${task.description}")
            appendLine()
            task.context.activeFile?.let { appendLine("Active file: $it") }
            task.context.selectedCode?.let { 
                appendLine("Selected code:")
                appendLine("```")
                appendLine(it)
                appendLine("```")
            }
            appendLine()
            appendLine("User instructions: ${task.context.userInstructions}")
        }
    }

    private fun isDestructiveAction(tool: AgentTool): Boolean {
        return tool.name in listOf("write_file", "edit_file", "run_command")
    }

    private fun AgentTool.toAgentTool() = this
}

enum class AgentAction { TOOL_CALL, REASONING, ERROR, COMPLETE }
```

---

## v0.8.4 — Code Understanding

### v0.8.4a — CodeIndexModels

```kotlin
package com.sidekick.agent.index

/**
 * Indexed code chunk for semantic search.
 */
data class CodeChunk(
    val id: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val type: ChunkType,
    val symbolName: String?,
    val embedding: List<Float>?
)

enum class ChunkType { FILE, CLASS, METHOD, FUNCTION, PROPERTY, BLOCK, COMMENT }

/**
 * Symbol information from AST.
 */
data class SymbolInfo(
    val name: String,
    val kind: SymbolKind,
    val filePath: String,
    val range: IntRange,
    val signature: String?,
    val documentation: String?,
    val references: List<SymbolReference>
)

enum class SymbolKind { CLASS, INTERFACE, METHOD, FUNCTION, PROPERTY, FIELD, VARIABLE, PARAMETER }

data class SymbolReference(val filePath: String, val line: Int, val context: String)

/**
 * Search result.
 */
data class CodeSearchResult(
    val chunk: CodeChunk,
    val score: Float,
    val matchType: MatchType
)

enum class MatchType { SEMANTIC, KEYWORD, SYMBOL }
```

---

### v0.8.4b — CodeIndexService

```kotlin
package com.sidekick.agent.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.sidekick.llm.provider.ProviderManager

@Service(Service.Level.PROJECT)
class CodeIndexService(private val project: Project) {

    private val chunks = mutableListOf<CodeChunk>()
    private val symbols = mutableMapOf<String, SymbolInfo>()

    companion object {
        fun getInstance(project: Project): CodeIndexService {
            return project.getService(CodeIndexService::class.java)
        }
    }

    /**
     * Indexes the entire project.
     */
    suspend fun indexProject() {
        chunks.clear()
        symbols.clear()
        // Walk all source files and index
    }

    /**
     * Indexes a single file.
     */
    suspend fun indexFile(file: PsiFile) {
        val fileChunks = extractChunks(file)
        val provider = ProviderManager.getInstance().getActiveProvider()
        
        for (chunk in fileChunks) {
            val embedding = provider.embed(chunk.content)
            chunks.add(chunk.copy(embedding = embedding))
        }
        
        extractSymbols(file).forEach { symbols[it.name] = it }
    }

    /**
     * Semantic search across codebase.
     */
    suspend fun semanticSearch(query: String, limit: Int = 10): List<CodeSearchResult> {
        val provider = ProviderManager.getInstance().getActiveProvider()
        val queryEmbedding = provider.embed(query)
        
        return chunks
            .filter { it.embedding != null }
            .map { chunk ->
                CodeSearchResult(
                    chunk = chunk,
                    score = cosineSimilarity(queryEmbedding, chunk.embedding!!),
                    matchType = MatchType.SEMANTIC
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Finds symbol by name.
     */
    fun findSymbol(name: String): SymbolInfo? = symbols[name]

    /**
     * Gets symbols in a file.
     */
    fun getFileSymbols(filePath: String): List<SymbolInfo> {
        return symbols.values.filter { it.filePath == filePath }
    }

    private fun extractChunks(file: PsiFile): List<CodeChunk> = emptyList()
    private fun extractSymbols(file: PsiFile): List<SymbolInfo> = emptyList()
    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float = 0f
}
```

---

## v0.8.5 — Agent Actions

### v0.8.5a — RefactorAction

```kotlin
package com.sidekick.agent.actions

import com.intellij.openapi.project.Project
import com.sidekick.agent.AgentExecutor
import com.sidekick.agent.tasks.*

/**
 * Refactoring action using the agent.
 */
class AgentRefactorAction {

    suspend fun extractMethod(project: Project, code: String, methodName: String): TaskResult {
        val task = AgentTask(
            type = TaskType.REFACTOR,
            description = "Extract the selected code into a method named '$methodName'",
            context = TaskContext(
                projectPath = project.basePath ?: "",
                activeFile = null,
                selectedCode = code,
                cursorPosition = null,
                userInstructions = "Extract this code into a new method. Ensure proper parameters and return type."
            )
        )
        return AgentExecutor.getInstance(project).executeTask(task)
    }

    suspend fun renameSymbol(project: Project, oldName: String, newName: String): TaskResult {
        val task = AgentTask(
            type = TaskType.REFACTOR,
            description = "Rename '$oldName' to '$newName' across the codebase",
            context = TaskContext(
                projectPath = project.basePath ?: "",
                activeFile = null,
                selectedCode = null,
                cursorPosition = null,
                userInstructions = "Find all usages and rename consistently."
            )
        )
        return AgentExecutor.getInstance(project).executeTask(task)
    }
}
```

---

## v0.8.6 — Conversation Memory

### v0.8.6a — MemoryModels

```kotlin
package com.sidekick.agent.memory

import java.time.Instant

/**
 * Conversation memory entry.
 */
data class MemoryEntry(
    val id: String,
    val sessionId: String,
    val type: MemoryType,
    val content: String,
    val embedding: List<Float>?,
    val metadata: Map<String, String>,
    val timestamp: Instant,
    val importance: Float = 0.5f
)

enum class MemoryType { USER_MESSAGE, ASSISTANT_MESSAGE, TOOL_RESULT, FACT, PREFERENCE, CODE_CONTEXT }

/**
 * Session context.
 */
data class SessionContext(
    val sessionId: String,
    val projectPath: String,
    val startTime: Instant,
    val activeFiles: MutableSet<String> = mutableSetOf(),
    val recentSymbols: MutableList<String> = mutableListOf(),
    val facts: MutableList<String> = mutableListOf()
)

/**
 * Memory configuration.
 */
data class MemoryConfig(
    val enabled: Boolean = true,
    val maxShortTermMemory: Int = 20,
    val maxLongTermMemory: Int = 1000,
    val retentionDays: Int = 30,
    val embedMemories: Boolean = true
)
```

---

### v0.8.6b — MemoryService

```kotlin
package com.sidekick.agent.memory

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.sidekick.llm.provider.ProviderManager
import java.time.Instant
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(name = "SidekickMemory", storages = [Storage("sidekick-memory.xml")])
class MemoryService(private val project: Project) : PersistentStateComponent<MemoryService.State> {

    data class State(
        var config: MemoryConfig = MemoryConfig(),
        var memories: MutableList<MemoryEntry> = mutableListOf(),
        var currentSession: SessionContext? = null
    )

    private var state = State()

    companion object {
        fun getInstance(project: Project): MemoryService {
            return project.getService(MemoryService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Starts a new session.
     */
    fun startSession(): SessionContext {
        val session = SessionContext(
            sessionId = UUID.randomUUID().toString(),
            projectPath = project.basePath ?: "",
            startTime = Instant.now()
        )
        state.currentSession = session
        return session
    }

    /**
     * Adds a memory entry.
     */
    suspend fun remember(type: MemoryType, content: String, metadata: Map<String, String> = emptyMap()) {
        val embedding = if (state.config.embedMemories) {
            ProviderManager.getInstance().getActiveProvider().embed(content)
        } else null

        val entry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            sessionId = state.currentSession?.sessionId ?: "",
            type = type,
            content = content,
            embedding = embedding,
            metadata = metadata,
            timestamp = Instant.now()
        )

        state.memories.add(entry)
        pruneMemories()
    }

    /**
     * Recalls relevant memories.
     */
    suspend fun recall(query: String, limit: Int = 5): List<MemoryEntry> {
        val queryEmbedding = ProviderManager.getInstance().getActiveProvider().embed(query)
        
        return state.memories
            .filter { it.embedding != null }
            .sortedByDescending { cosineSimilarity(queryEmbedding, it.embedding!!) }
            .take(limit)
    }

    /**
     * Gets recent memories.
     */
    fun getRecentMemories(limit: Int = 10): List<MemoryEntry> {
        return state.memories.takeLast(limit)
    }

    private fun pruneMemories() {
        if (state.memories.size > state.config.maxLongTermMemory) {
            state.memories = state.memories
                .sortedByDescending { it.importance }
                .take(state.config.maxLongTermMemory)
                .toMutableList()
        }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float = 0f
}
```

---

## v0.8.7 — Agent Orchestration

### v0.8.7a — WorkflowModels

```kotlin
package com.sidekick.agent.workflow

/**
 * Multi-step workflow definition.
 */
data class AgentWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val triggers: List<WorkflowTrigger>
)

data class WorkflowStep(
    val id: String,
    val action: WorkflowAction,
    val config: Map<String, Any>,
    val onSuccess: String?, // Next step ID
    val onFailure: String?
)

enum class WorkflowAction {
    ASK_USER, ANALYZE_CODE, GENERATE_CODE, RUN_TESTS, APPLY_CHANGES, 
    SEARCH_CODEBASE, CREATE_FILE, MODIFY_FILE, COMMIT_CHANGES
}

data class WorkflowTrigger(
    val type: TriggerType,
    val pattern: String?
)

enum class TriggerType { MANUAL, FILE_SAVE, ERROR_DETECTED, COMMAND, SCHEDULE }

/**
 * Built-in workflows.
 */
object BuiltInWorkflows {
    
    val FIX_ERROR = AgentWorkflow(
        id = "fix-error",
        name = "Fix Error",
        description = "Analyze and fix a compilation or runtime error",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("focus" to "error"), "2", null),
            WorkflowStep("2", WorkflowAction.SEARCH_CODEBASE, mapOf("context" to "related"), "3", null),
            WorkflowStep("3", WorkflowAction.GENERATE_CODE, mapOf("type" to "fix"), "4", null),
            WorkflowStep("4", WorkflowAction.ASK_USER, mapOf("confirm" to true), "5", null),
            WorkflowStep("5", WorkflowAction.APPLY_CHANGES, emptyMap(), "6", null),
            WorkflowStep("6", WorkflowAction.RUN_TESTS, emptyMap(), null, "3")
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.ERROR_DETECTED, null))
    )

    val IMPLEMENT_FEATURE = AgentWorkflow(
        id = "implement-feature",
        name = "Implement Feature",
        description = "Implement a new feature from requirements",
        steps = listOf(
            WorkflowStep("1", WorkflowAction.ANALYZE_CODE, mapOf("scope" to "project"), "2", null),
            WorkflowStep("2", WorkflowAction.ASK_USER, mapOf("clarify" to "requirements"), "3", null),
            WorkflowStep("3", WorkflowAction.GENERATE_CODE, mapOf("type" to "implementation"), "4", null),
            WorkflowStep("4", WorkflowAction.GENERATE_CODE, mapOf("type" to "tests"), "5", null),
            WorkflowStep("5", WorkflowAction.ASK_USER, mapOf("confirm" to true), "6", null),
            WorkflowStep("6", WorkflowAction.APPLY_CHANGES, emptyMap(), "7", null),
            WorkflowStep("7", WorkflowAction.RUN_TESTS, emptyMap(), null, null)
        ),
        triggers = listOf(WorkflowTrigger(TriggerType.MANUAL, null))
    )
}
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- LM Studio & Agent Services (v0.8.x) -->
    <applicationService serviceImplementation="com.sidekick.llm.lmstudio.LmStudioService"/>
    <applicationService serviceImplementation="com.sidekick.llm.provider.ProviderManager"/>
    <projectService serviceImplementation="com.sidekick.agent.AgentExecutor"/>
    <projectService serviceImplementation="com.sidekick.agent.index.CodeIndexService"/>
    <projectService serviceImplementation="com.sidekick.agent.memory.MemoryService"/>
    
    <!-- Settings -->
    <applicationConfigurable 
        instance="com.sidekick.llm.lmstudio.LmStudioSettingsConfigurable"
        displayName="LM Studio"
        parentId="com.sidekick.settings"/>
</extensions>

<actions>
    <action id="Sidekick.AgentChat" class="com.sidekick.agent.AgentChatAction" text="Agent Chat">
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift A"/>
    </action>
    <action id="Sidekick.AgentFix" class="com.sidekick.agent.AgentFixAction" text="Agent Fix Error"/>
    <action id="Sidekick.AgentRefactor" class="com.sidekick.agent.AgentRefactorAction" text="Agent Refactor"/>
    <action id="Sidekick.IndexProject" class="com.sidekick.agent.index.IndexProjectAction" text="Index Project"/>
</actions>
```

---

## Long-Term Roadmap

### v0.9.x — Advanced Agent Capabilities

| Feature | Description |
|---------|-------------|
| **Multi-Agent Collaboration** | Specialized sub-agents (Code Writer, Reviewer, Tester) |
| **Planning Agent** | Breaks complex tasks into subtasks |
| **Self-Correction** | Learns from failed attempts |
| **User Preferences Learning** | Adapts to coding style |

### v1.0.x — Production Polish

| Feature | Description |
|---------|-------------|
| **Performance Optimization** | Lazy loading, background indexing |
| **Security Hardening** | Sandboxed command execution |
| **Telemetry (Opt-in)** | Usage analytics for improvement |
| **Marketplace Release** | JetBrains plugin marketplace |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.8.0 | 2026-02-04 | Ryan | Initial v0.8.x design specification |
