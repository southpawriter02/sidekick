package com.sidekick.agent.tasks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Agent Task models.
 */
@DisplayName("Agent Task Models Tests")
class AgentTaskModelsTest {

    // =========================================================================
    // AgentTask Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentTask")
    inner class AgentTaskTests {

        @Test
        @DisplayName("task has unique ID")
        fun taskHasUniqueId() {
            val task1 = AgentTask.simple(TaskType.EXPLAIN_CODE, "Explain", "What does this do?", "/project")
            val task2 = AgentTask.simple(TaskType.EXPLAIN_CODE, "Explain", "What does this do?", "/project")
            assertNotEquals(task1.id, task2.id)
        }

        @Test
        @DisplayName("default status is PENDING")
        fun defaultStatusIsPending() {
            val task = AgentTask.simple(TaskType.REFACTOR, "Refactor", "Clean up", "/project")
            assertEquals(TaskStatus.PENDING, task.status)
        }

        @Test
        @DisplayName("isActive returns true for non-terminal statuses")
        fun isActiveReturnsTrueForNonTerminal() {
            val pending = AgentTask.simple(TaskType.REFACTOR, "Refactor", "Clean up", "/project")
            assertTrue(pending.isActive)

            val executing = pending.withStatus(TaskStatus.EXECUTING)
            assertTrue(executing.isActive)

            val completed = pending.withStatus(TaskStatus.COMPLETED)
            assertFalse(completed.isActive)
        }

        @Test
        @DisplayName("withStep adds step to task")
        fun withStepAddsStep() {
            val task = AgentTask.simple(TaskType.REFACTOR, "Refactor", "Clean up", "/project")
            val step = TaskStep.reasoning(1, "Analyzing code")
            val updated = task.withStep(step)

            assertEquals(1, updated.steps.size)
            assertEquals(0, task.steps.size)
        }

        @Test
        @DisplayName("complete sets result and status")
        fun completeSetsResultAndStatus() {
            val task = AgentTask.simple(TaskType.REFACTOR, "Refactor", "Clean up", "/project")
            val result = TaskResult.success("Done", listOf("/file.kt"))
            val completed = task.complete(result)

            assertEquals(TaskStatus.COMPLETED, completed.status)
            assertTrue(completed.isSuccessful)
            assertEquals(result, completed.result)
        }

        @Test
        @DisplayName("simple factory creates correct task")
        fun simpleFactoryCreatesCorrectTask() {
            val task = AgentTask.simple(
                type = TaskType.FIX_BUG,
                description = "Fix null pointer",
                userInstructions = "The app crashes here",
                projectPath = "/project",
                activeFile = "/project/main.kt",
                selectedCode = "val x = null"
            )

            assertEquals(TaskType.FIX_BUG, task.type)
            assertEquals("/project", task.context.projectPath)
            assertEquals("/project/main.kt", task.context.activeFile)
            assertEquals("val x = null", task.context.selectedCode)
        }
    }

    // =========================================================================
    // TaskType Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskType")
    inner class TaskTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            TaskType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
                assertTrue(type.description.isNotBlank())
                assertTrue(type.defaultPrompt.isNotBlank())
            }
        }

        @Test
        @DisplayName("isDestructive identifies modifying types")
        fun isDestructiveIdentifiesModifyingTypes() {
            assertTrue(TaskType.REFACTOR.isDestructive)
            assertTrue(TaskType.FIX_BUG.isDestructive)
            assertTrue(TaskType.IMPLEMENT_FEATURE.isDestructive)
            assertFalse(TaskType.EXPLAIN_CODE.isDestructive)
            assertFalse(TaskType.REVIEW.isDestructive)
        }

        @Test
        @DisplayName("isReadOnly identifies read-only types")
        fun isReadOnlyIdentifiesReadOnlyTypes() {
            assertTrue(TaskType.EXPLAIN_CODE.isReadOnly)
            assertTrue(TaskType.REVIEW.isReadOnly)
            assertTrue(TaskType.ANSWER_QUESTION.isReadOnly)
            assertFalse(TaskType.REFACTOR.isReadOnly)
        }

        @Test
        @DisplayName("byName finds type case-insensitively")
        fun byNameFindsCaseInsensitively() {
            assertEquals(TaskType.EXPLAIN_CODE, TaskType.byName("explain_code"))
            assertEquals(TaskType.REFACTOR, TaskType.byName("REFACTOR"))
            assertNull(TaskType.byName("unknown"))
        }

        @Test
        @DisplayName("MODIFYING_TYPES contains correct types")
        fun modifyingTypesContainsCorrect() {
            assertTrue(TaskType.REFACTOR in TaskType.MODIFYING_TYPES)
            assertFalse(TaskType.EXPLAIN_CODE in TaskType.MODIFYING_TYPES)
        }
    }

    // =========================================================================
    // TaskContext Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskContext")
    inner class TaskContextTests {

        @Test
        @DisplayName("hasSelection detects selected code")
        fun hasSelectionDetectsSelectedCode() {
            val withSelection = TaskContext(
                projectPath = "/project",
                activeFile = null,
                selectedCode = "fun main() {}",
                cursorPosition = null,
                userInstructions = "Explain"
            )
            assertTrue(withSelection.hasSelection)

            val withoutSelection = withSelection.copy(selectedCode = null)
            assertFalse(withoutSelection.hasSelection)

            val emptySelection = withSelection.copy(selectedCode = "")
            assertFalse(emptySelection.hasSelection)
        }

        @Test
        @DisplayName("allFiles includes active and related")
        fun allFilesIncludesActiveAndRelated() {
            val context = TaskContext(
                projectPath = "/project",
                activeFile = "/project/main.kt",
                selectedCode = null,
                cursorPosition = null,
                relatedFiles = listOf("/project/util.kt", "/project/model.kt"),
                userInstructions = "Analyze"
            )

            val allFiles = context.allFiles
            assertEquals(3, allFiles.size)
            assertTrue("/project/main.kt" in allFiles)
            assertTrue("/project/util.kt" in allFiles)
        }

        @Test
        @DisplayName("simple factory creates minimal context")
        fun simpleFactoryCreatesMinimalContext() {
            val context = TaskContext.simple("/project", "Help me")
            assertEquals("/project", context.projectPath)
            assertEquals("Help me", context.userInstructions)
            assertNull(context.activeFile)
            assertNull(context.selectedCode)
        }
    }

    // =========================================================================
    // TaskConstraints Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskConstraints")
    inner class TaskConstraintsTests {

        @Test
        @DisplayName("default has expected values")
        fun defaultHasExpectedValues() {
            val constraints = TaskConstraints()
            assertEquals(10, constraints.maxSteps)
            assertEquals(8000, constraints.maxTokens)
            assertTrue(constraints.allowFileModification)
            assertTrue(constraints.allowNewFiles)
            assertFalse(constraints.allowDeletion)
            assertFalse(constraints.allowCommands)
            assertTrue(constraints.requireConfirmation)
        }

        @Test
        @DisplayName("READ_ONLY disables all modifications")
        fun readOnlyDisablesAllModifications() {
            val readOnly = TaskConstraints.READ_ONLY
            assertFalse(readOnly.allowFileModification)
            assertFalse(readOnly.allowNewFiles)
            assertFalse(readOnly.allowDeletion)
            assertFalse(readOnly.allowCommands)
            assertTrue(readOnly.isReadOnly)
        }

        @Test
        @DisplayName("PERMISSIVE enables all operations")
        fun permissiveEnablesAllOperations() {
            val permissive = TaskConstraints.PERMISSIVE
            assertTrue(permissive.allowFileModification)
            assertTrue(permissive.allowNewFiles)
            assertTrue(permissive.allowDeletion)
            assertTrue(permissive.allowCommands)
            assertFalse(permissive.requireConfirmation)
        }

        @Test
        @DisplayName("allowsFileOperations checks all file flags")
        fun allowsFileOperationsChecksAllFlags() {
            assertTrue(TaskConstraints.DEFAULT.allowsFileOperations)
            assertFalse(TaskConstraints.READ_ONLY.allowsFileOperations)
        }
    }

    // =========================================================================
    // TaskStatus Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskStatus")
    inner class TaskStatusTests {

        @Test
        @DisplayName("isTerminal for final statuses")
        fun isTerminalForFinalStatuses() {
            assertTrue(TaskStatus.COMPLETED.isTerminal)
            assertTrue(TaskStatus.FAILED.isTerminal)
            assertTrue(TaskStatus.CANCELLED.isTerminal)
            assertFalse(TaskStatus.PENDING.isTerminal)
            assertFalse(TaskStatus.EXECUTING.isTerminal)
        }

        @Test
        @DisplayName("isActive is opposite of isTerminal")
        fun isActiveIsOppositeOfTerminal() {
            TaskStatus.entries.forEach { status ->
                assertEquals(!status.isTerminal, status.isActive)
            }
        }

        @Test
        @DisplayName("ACTIVE_STATUSES and TERMINAL_STATUSES partition all statuses")
        fun statusesPartitionCorrectly() {
            val all = TaskStatus.ACTIVE_STATUSES + TaskStatus.TERMINAL_STATUSES
            assertEquals(TaskStatus.entries.size, all.size)
        }
    }

    // =========================================================================
    // TaskStep Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskStep")
    inner class TaskStepTests {

        @Test
        @DisplayName("toolCall factory creates correct step")
        fun toolCallFactoryCreatesCorrectStep() {
            val step = TaskStep.toolCall(
                id = 1,
                toolName = "read_file",
                toolArgs = mapOf("path" to "/test.txt"),
                reasoning = "Reading the file",
                result = "file contents",
                success = true,
                tokensUsed = 100,
                durationMs = 50
            )

            assertEquals(1, step.id)
            assertEquals(AgentAction.TOOL_CALL, step.action)
            assertEquals("read_file", step.toolName)
            assertTrue(step.isSuccessful)
            assertTrue(step.isToolCall)
            assertEquals(100, step.tokensUsed)
        }

        @Test
        @DisplayName("reasoning factory creates reasoning step")
        fun reasoningFactoryCreatesReasoningStep() {
            val step = TaskStep.reasoning(2, "Thinking about the problem")
            assertEquals(AgentAction.REASONING, step.action)
            assertEquals(StepStatus.COMPLETED, step.status)
            assertFalse(step.isToolCall)
        }

        @Test
        @DisplayName("error factory creates failed step")
        fun errorFactoryCreatesFailedStep() {
            val step = TaskStep.error(3, "Something went wrong")
            assertEquals(AgentAction.ERROR, step.action)
            assertEquals(StepStatus.FAILED, step.status)
            assertTrue(step.isFailed)
        }
    }

    // =========================================================================
    // AgentAction Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentAction")
    inner class AgentActionTests {

        @Test
        @DisplayName("all actions have display names")
        fun allActionsHaveDisplayNames() {
            AgentAction.entries.forEach { action ->
                assertTrue(action.displayName.isNotBlank())
            }
        }
    }

    // =========================================================================
    // TaskResult Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskResult")
    inner class TaskResultTests {

        @Test
        @DisplayName("success factory creates success result")
        fun successFactoryCreatesSuccessResult() {
            val result = TaskResult.success("Done", listOf("/main.kt"))
            assertTrue(result.success)
            assertEquals("Done", result.summary)
            assertEquals(1, result.filesModified.size)
            assertFalse(result.hasErrors)
        }

        @Test
        @DisplayName("failure factory creates failure result")
        fun failureFactoryCreatesFailureResult() {
            val result = TaskResult.failure("Failed", listOf("Error 1", "Error 2"))
            assertFalse(result.success)
            assertTrue(result.hasErrors)
            assertEquals(2, result.errors.size)
        }

        @Test
        @DisplayName("cancelled factory creates cancelled result")
        fun cancelledFactoryCreatesCancelledResult() {
            val result = TaskResult.cancelled()
            assertFalse(result.success)
            assertTrue(result.summary.contains("Cancelled"))
        }

        @Test
        @DisplayName("totalFilesAffected counts all file operations")
        fun totalFilesAffectedCountsAll() {
            val result = TaskResult(
                success = true,
                summary = "Done",
                filesModified = listOf("a.kt", "b.kt"),
                filesCreated = listOf("c.kt"),
                filesDeleted = listOf("d.kt")
            )
            assertEquals(4, result.totalFilesAffected)
        }
    }

    // =========================================================================
    // TaskEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskEvent")
    inner class TaskEventTests {

        @Test
        @DisplayName("TaskStarted has correct structure")
        fun taskStartedHasCorrectStructure() {
            val task = AgentTask.simple(TaskType.REFACTOR, "Refactor", "Clean up", "/project")
            val event = TaskEvent.TaskStarted(task.id, task)

            assertEquals(task.id, event.taskId)
            assertNotNull(event.timestamp)
        }

        @Test
        @DisplayName("StepCompleted contains step")
        fun stepCompletedContainsStep() {
            val step = TaskStep.reasoning(1, "Thinking")
            val event = TaskEvent.StepCompleted("task-123", step)

            assertEquals("task-123", event.taskId)
            assertEquals(step, event.step)
        }

        @Test
        @DisplayName("ConfirmationRequired has action details")
        fun confirmationRequiredHasActionDetails() {
            val event = TaskEvent.ConfirmationRequired(
                taskId = "task-123",
                action = "write_file",
                details = "Writing to /main.kt"
            )

            assertEquals("write_file", event.action)
            assertTrue(event.details.contains("main.kt"))
        }
    }
}
