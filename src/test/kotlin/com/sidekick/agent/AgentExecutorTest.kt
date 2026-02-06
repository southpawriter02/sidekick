package com.sidekick.agent

import com.sidekick.agent.tasks.*
import com.sidekick.agent.tools.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Agent Executor logic.
 *
 * Note: These tests focus on the executor's pure logic
 * without requiring IntelliJ Platform or LLM provider.
 */
@DisplayName("Agent Executor Tests")
class AgentExecutorTest {

    // =========================================================================
    // System Prompt Tests
    // =========================================================================

    @Nested
    @DisplayName("System Prompt")
    inner class SystemPromptTests {

        @Test
        @DisplayName("system prompt is not empty")
        fun systemPromptIsNotEmpty() {
            assertTrue(AgentExecutor.AGENT_SYSTEM_PROMPT.isNotBlank())
        }

        @Test
        @DisplayName("system prompt contains key instructions")
        fun systemPromptContainsKeyInstructions() {
            val prompt = AgentExecutor.AGENT_SYSTEM_PROMPT
            assertTrue(prompt.contains("tool", ignoreCase = true))
            assertTrue(prompt.contains("reasoning", ignoreCase = true))
        }
    }

    // =========================================================================
    // Task Building Tests
    // =========================================================================

    @Nested
    @DisplayName("Task Building")
    inner class TaskBuildingTests {

        @Test
        @DisplayName("task is created with correct structure")
        fun taskIsCreatedWithCorrectStructure() {
            val task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor the code",
                userInstructions = "Clean up the function",
                projectPath = "/project",
                activeFile = "/project/main.kt",
                selectedCode = "fun foo() {}"
            )

            assertEquals(TaskType.REFACTOR, task.type)
            assertEquals("Refactor the code", task.description)
            assertEquals("/project", task.context.projectPath)
            assertEquals("/project/main.kt", task.context.activeFile)
            assertEquals("fun foo() {}", task.context.selectedCode)
        }

        @Test
        @DisplayName("task with default constraints")
        fun taskWithDefaultConstraints() {
            val task = AgentTask.simple(
                type = TaskType.EXPLAIN_CODE,
                description = "Explain",
                userInstructions = "What does this do?",
                projectPath = "/project"
            )

            assertEquals(10, task.constraints.maxSteps)
            assertTrue(task.constraints.allowFileModification)
            assertTrue(task.constraints.requireConfirmation)
        }
    }

    // =========================================================================
    // Task Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Task Lifecycle")
    inner class TaskLifecycleTests {

        @Test
        @DisplayName("task transitions through statuses")
        fun taskTransitionsThroughStatuses() {
            var task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            )

            assertEquals(TaskStatus.PENDING, task.status)
            assertTrue(task.isActive)

            task = task.withStatus(TaskStatus.PLANNING)
            assertEquals(TaskStatus.PLANNING, task.status)
            assertTrue(task.isActive)

            task = task.withStatus(TaskStatus.EXECUTING)
            assertEquals(TaskStatus.EXECUTING, task.status)
            assertTrue(task.isActive)

            val result = TaskResult.success("Done")
            task = task.complete(result)
            assertEquals(TaskStatus.COMPLETED, task.status)
            assertFalse(task.isActive)
        }

        @Test
        @DisplayName("task can be cancelled")
        fun taskCanBeCancelled() {
            var task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            ).withStatus(TaskStatus.EXECUTING)

            val result = TaskResult.cancelled()
            task = task.copy(status = TaskStatus.CANCELLED, result = result)

            assertEquals(TaskStatus.CANCELLED, task.status)
            assertFalse(task.result?.success ?: true)
        }

        @Test
        @DisplayName("task can fail")
        fun taskCanFail() {
            var task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            ).withStatus(TaskStatus.EXECUTING)

            val result = TaskResult.failure("Something went wrong", listOf("Error 1"))
            task = task.complete(result)

            assertEquals(TaskStatus.FAILED, task.status)
            assertFalse(task.isSuccessful)
            assertTrue(task.result?.hasErrors ?: false)
        }
    }

    // =========================================================================
    // Step Tests
    // =========================================================================

    @Nested
    @DisplayName("Steps")
    inner class StepsTests {

        @Test
        @DisplayName("task accumulates steps")
        fun taskAccumulatesSteps() {
            var task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            )

            val step1 = TaskStep.toolCall(
                id = 0,
                toolName = "read_file",
                toolArgs = mapOf("path" to "/main.kt"),
                reasoning = "Reading main file",
                result = "file contents",
                success = true
            )

            val step2 = TaskStep.toolCall(
                id = 1,
                toolName = "edit_file",
                toolArgs = mapOf("path" to "/main.kt", "oldText" to "foo", "newText" to "bar"),
                reasoning = "Renaming function",
                result = "edited",
                success = true
            )

            task = task.withStep(step1).withStep(step2)

            assertEquals(2, task.steps.size)
            assertEquals("read_file", task.steps[0].toolName)
            assertEquals("edit_file", task.steps[1].toolName)
        }

        @Test
        @DisplayName("totalTokens sums across steps")
        fun totalTokensSumsAcrossSteps() {
            val steps = listOf(
                TaskStep.toolCall(0, "read_file", emptyMap(), "Read", "ok", true, tokensUsed = 100),
                TaskStep.toolCall(1, "edit_file", emptyMap(), "Edit", "ok", true, tokensUsed = 200),
                TaskStep.toolCall(2, "write_file", emptyMap(), "Write", "ok", true, tokensUsed = 150)
            )

            val task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            ).copy(steps = steps)

            assertEquals(450, task.totalTokens)
        }
    }

    // =========================================================================
    // Tool Filtering Tests
    // =========================================================================

    @Nested
    @DisplayName("Tool Filtering")
    inner class ToolFilteringTests {

        @Test
        @DisplayName("safe tools don't include destructive ones")
        fun safeToolsDontIncludeDestructive() {
            val safeTools = BuiltInTools.SAFE_TOOLS

            assertTrue(safeTools.any { it.name == "read_file" })
            assertTrue(safeTools.any { it.name == "list_files" })
            assertFalse(safeTools.any { it.name == "write_file" })
            assertFalse(safeTools.any { it.name == "run_command" })
        }

        @Test
        @DisplayName("destructive tools are correctly identified")
        fun destructiveToolsAreCorrectlyIdentified() {
            val destructive = BuiltInTools.DESTRUCTIVE_TOOLS

            assertTrue(destructive.any { it.name == "write_file" })
            assertTrue(destructive.any { it.name == "edit_file" })
            assertTrue(destructive.any { it.name == "run_command" })
            assertFalse(destructive.any { it.name == "read_file" })
        }
    }

    // =========================================================================
    // Result Tests
    // =========================================================================

    @Nested
    @DisplayName("Results")
    inner class ResultTests {

        @Test
        @DisplayName("success result has correct structure")
        fun successResultHasCorrectStructure() {
            val result = TaskResult(
                success = true,
                summary = "Completed refactoring",
                filesModified = listOf("/main.kt", "/util.kt"),
                filesCreated = listOf("/test.kt"),
                durationMs = 5000,
                tokensUsed = 1500
            )

            assertTrue(result.success)
            assertEquals(2, result.filesModified.size)
            assertEquals(1, result.filesCreated.size)
            assertEquals(3, result.totalFilesAffected)
            assertTrue(result.hasModifiedFiles)
            assertTrue(result.hasCreatedFiles)
            assertFalse(result.hasErrors)
        }

        @Test
        @DisplayName("failure result has correct structure")
        fun failureResultHasCorrectStructure() {
            val result = TaskResult.failure(
                summary = "Failed to refactor",
                errors = listOf("File not found", "Permission denied")
            )

            assertFalse(result.success)
            assertTrue(result.hasErrors)
            assertEquals(2, result.errors.size)
        }
    }

    // =========================================================================
    // Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Events")
    inner class EventTests {

        @Test
        @DisplayName("task events have taskId")
        fun taskEventsHaveTaskId() {
            val task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            )

            val startedEvent = TaskEvent.TaskStarted(task.id, task)
            assertEquals(task.id, startedEvent.taskId)

            val stepEvent = TaskEvent.StepStarted(task.id, 0, AgentAction.TOOL_CALL)
            assertEquals(task.id, stepEvent.taskId)

            val completedEvent = TaskEvent.TaskCompleted(task.id, TaskResult.success("Done"))
            assertEquals(task.id, completedEvent.taskId)
        }

        @Test
        @DisplayName("events have timestamps")
        fun eventsHaveTimestamps() {
            val task = AgentTask.simple(
                type = TaskType.REFACTOR,
                description = "Refactor",
                userInstructions = "Clean up",
                projectPath = "/project"
            )

            val event = TaskEvent.TaskStarted(task.id, task)
            assertNotNull(event.timestamp)
        }
    }
}
