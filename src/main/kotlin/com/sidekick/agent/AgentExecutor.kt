package com.sidekick.agent

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.agent.tasks.*
import com.sidekick.agent.tools.*
import com.sidekick.llm.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * # Agent Executor
 *
 * Project-level service for executing agent tasks.
 * Part of Sidekick v0.8.3 Agent Task System feature.
 *
 * ## Overview
 *
 * The executor:
 * - Manages task lifecycle
 * - Coordinates with LLM provider
 * - Executes tool calls
 * - Tracks progress and results
 *
 * @since 0.8.3
 */
@Service(Service.Level.PROJECT)
class AgentExecutor(private val project: Project) {

    private val logger = Logger.getInstance(AgentExecutor::class.java)

    companion object {
        fun getInstance(project: Project): AgentExecutor {
            return project.getService(AgentExecutor::class.java)
        }

        /**
         * System prompt for the agent.
         */
        val AGENT_SYSTEM_PROMPT = """
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
            When you're done, summarize what you accomplished.
        """.trimIndent()
    }

    // Current state
    private var currentTask: AgentTask? = null
    private val taskHistory = mutableListOf<AgentTask>()

    // Available tools
    private val tools: List<AgentTool> = BuiltInTools.ALL

    // Event listeners
    private val eventListeners = mutableListOf<(TaskEvent) -> Unit>()

    // =========================================================================
    // Task Execution
    // =========================================================================

    /**
     * Executes a task.
     *
     * @param task Task to execute
     * @return Task result
     */
    suspend fun executeTask(task: AgentTask): TaskResult {
        val startTime = System.currentTimeMillis()
        logger.info("Starting task: ${task.id} - ${task.description}")

        // Initialize task
        currentTask = task.withStatus(TaskStatus.PLANNING)
        emitEvent(TaskEvent.TaskStarted(task.id, task))

        return try {
            // Get provider
            val provider = ProviderManager.getInstance().getActiveProvider()
                ?: return failTask("No LLM provider available")

            // Build initial messages
            val messages = mutableListOf(
                UnifiedMessage.system(AGENT_SYSTEM_PROMPT),
                UnifiedMessage.user(buildTaskPrompt(task))
            )

            // Update status to executing
            currentTask = currentTask?.withStatus(TaskStatus.EXECUTING)

            // Execute loop
            var stepCount = 0
            val steps = mutableListOf<TaskStep>()
            val filesModified = mutableSetOf<String>()
            val filesCreated = mutableSetOf<String>()

            while (stepCount < task.constraints.maxSteps) {
                // Get next action from LLM
                val response = provider.chat(
                    UnifiedChatRequest(
                        model = "", // Will use default from provider
                        messages = messages,
                        tools = getToolsForTask(task).map { it.toProviderTool() }
                    )
                )

                // Check for completion (no tool calls = done)
                if (response.toolCalls.isNullOrEmpty()) {
                    logger.info("Agent completed task")
                    break
                }

                // Process tool calls
                for (toolCall in response.toolCalls) {
                    emitEvent(TaskEvent.StepStarted(task.id, stepCount, AgentAction.TOOL_CALL))

                    val stepStart = System.currentTimeMillis()
                    val step = executeToolCall(stepCount, toolCall, task.constraints)
                    val stepDuration = System.currentTimeMillis() - stepStart

                    steps.add(step.copy(durationMs = stepDuration))
                    emitEvent(TaskEvent.StepCompleted(task.id, step))

                    // Track file modifications
                    if (step.toolName in listOf("write_file", "edit_file")) {
                        val path = step.toolArgs?.get("path") as? String
                        if (path != null) {
                            if (step.toolName == "write_file") {
                                filesCreated.add(path)
                            } else {
                                filesModified.add(path)
                            }
                        }
                    }

                    // Add tool result to messages
                    messages.add(UnifiedMessage.tool(
                        step.result ?: "",
                        listOf(com.sidekick.llm.provider.ToolResult(
                            toolCall.id,
                            step.result ?: ""
                        ))
                    ))

                    stepCount++
                }

                // Update task with steps
                currentTask = currentTask?.copy(steps = steps)
            }

            // Create result
            val duration = System.currentTimeMillis() - startTime
            val result = TaskResult(
                success = steps.none { it.isFailed },
                summary = "Completed ${steps.size} steps in ${duration}ms",
                filesModified = filesModified.toList(),
                filesCreated = filesCreated.toList(),
                errors = steps.filter { it.isFailed }.mapNotNull { it.result },
                durationMs = duration
            )

            completeTask(result)
            result

        } catch (e: Exception) {
            logger.error("Task failed: ${e.message}", e)
            failTask(e.message ?: "Unknown error")
        }
    }

    /**
     * Executes a single tool call.
     */
    private suspend fun executeToolCall(
        stepId: Int,
        toolCall: ToolCallRequest,
        constraints: TaskConstraints
    ): TaskStep {
        val tool = BuiltInTools.findByName(toolCall.name)
            ?: return TaskStep.error(stepId, "Unknown tool: ${toolCall.name}")

        // Check constraints
        if (tool.isDestructive && !constraints.allowFileModification) {
            return TaskStep.error(stepId, "Tool ${tool.name} not allowed by constraints")
        }

        if (tool.name == "run_command" && !constraints.allowCommands) {
            return TaskStep.error(stepId, "Command execution not allowed")
        }

        // Check for confirmation
        if (constraints.requireConfirmation && tool.isDestructive) {
            currentTask = currentTask?.withStatus(TaskStatus.AWAITING_CONFIRMATION)
            emitEvent(TaskEvent.ConfirmationRequired(
                currentTask?.id ?: "",
                tool.name,
                "Destructive action: ${tool.description}"
            ))
            // In real implementation, would await user confirmation
        }

        // Execute tool
        return try {
            val result = tool.handler(toolCall.arguments)
            TaskStep.toolCall(
                id = stepId,
                toolName = tool.name,
                toolArgs = toolCall.arguments,
                reasoning = "Executing ${tool.name}",
                result = result.output,
                success = result.success
            )
        } catch (e: Exception) {
            TaskStep.error(stepId, "Tool error: ${e.message}")
        }
    }

    /**
     * Gets tools available for a task based on constraints.
     */
    private fun getToolsForTask(task: AgentTask): List<AgentTool> {
        return tools.filter { tool ->
            when {
                tool.isDestructive && !task.constraints.allowFileModification -> false
                tool.name == "run_command" && !task.constraints.allowCommands -> false
                else -> true
            }
        }
    }

    // =========================================================================
    // Task Building
    // =========================================================================

    /**
     * Builds the task prompt for the LLM.
     */
    private fun buildTaskPrompt(task: AgentTask): String = buildString {
        appendLine("# Task: ${task.type.displayName}")
        appendLine()
        appendLine("## Description")
        appendLine(task.description)
        appendLine()

        task.context.activeFile?.let {
            appendLine("## Active File")
            appendLine(it)
            appendLine()
        }

        task.context.selectedCode?.let {
            appendLine("## Selected Code")
            appendLine("```")
            appendLine(it)
            appendLine("```")
            appendLine()
        }

        task.context.errorContext?.let {
            appendLine("## Error Context")
            appendLine("```")
            appendLine(it)
            appendLine("```")
            appendLine()
        }

        if (task.context.relatedFiles.isNotEmpty()) {
            appendLine("## Related Files")
            task.context.relatedFiles.forEach { appendLine("- $it") }
            appendLine()
        }

        appendLine("## User Instructions")
        appendLine(task.context.userInstructions)
        appendLine()

        appendLine("## Constraints")
        appendLine("- Max steps: ${task.constraints.maxSteps}")
        appendLine("- Can modify files: ${task.constraints.allowFileModification}")
        appendLine("- Can create files: ${task.constraints.allowNewFiles}")
        appendLine("- Can run commands: ${task.constraints.allowCommands}")
    }

    // =========================================================================
    // Task Lifecycle
    // =========================================================================

    private fun completeTask(result: TaskResult): TaskResult {
        currentTask = currentTask?.complete(result)
        currentTask?.let {
            taskHistory.add(it)
            emitEvent(TaskEvent.TaskCompleted(it.id, result))
        }
        currentTask = null
        return result
    }

    private fun failTask(error: String): TaskResult {
        val result = TaskResult.failure(error, listOf(error))
        currentTask = currentTask?.complete(result)
        currentTask?.let {
            taskHistory.add(it)
            emitEvent(TaskEvent.TaskFailed(it.id, error))
        }
        currentTask = null
        return result
    }

    /**
     * Cancels the current task.
     */
    fun cancelCurrentTask() {
        currentTask?.let { task ->
            val result = TaskResult.cancelled()
            currentTask = task.copy(
                status = TaskStatus.CANCELLED,
                result = result
            )
            taskHistory.add(currentTask!!)
            emitEvent(TaskEvent.TaskCompleted(task.id, result))
        }
        currentTask = null
    }

    // =========================================================================
    // Task Queries
    // =========================================================================

    /**
     * Gets the current task.
     */
    fun getCurrentTask(): AgentTask? = currentTask

    /**
     * Gets task history.
     */
    fun getTaskHistory(): List<AgentTask> = taskHistory.toList()

    /**
     * Gets a task by ID.
     */
    fun getTask(id: String): AgentTask? {
        return currentTask?.takeIf { it.id == id }
            ?: taskHistory.find { it.id == id }
    }

    /**
     * Clears task history.
     */
    fun clearHistory() {
        taskHistory.clear()
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (TaskEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (TaskEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun emitEvent(event: TaskEvent) {
        eventListeners.forEach { listener ->
            try {
                listener(event)
            } catch (e: Exception) {
                logger.warn("Event listener error: ${e.message}")
            }
        }
    }

    // =========================================================================
    // Streaming Execution
    // =========================================================================

    /**
     * Executes a task with streaming progress.
     */
    fun executeTaskStreaming(task: AgentTask): Flow<TaskEvent> = flow {
        emit(TaskEvent.TaskStarted(task.id, task))

        try {
            val result = executeTask(task)
            emit(TaskEvent.TaskCompleted(task.id, result))
        } catch (e: Exception) {
            emit(TaskEvent.TaskFailed(task.id, e.message ?: "Unknown error"))
        }
    }
}
