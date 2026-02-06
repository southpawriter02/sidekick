package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for File Operation and Code Analysis Actions.
 */
@DisplayName("File Operation Actions Tests")
class FileOperationActionsTest {

    // =========================================================================
    // Create File Tests
    // =========================================================================

    @Nested
    @DisplayName("Create File")
    inner class CreateFileTests {

        @Test
        @DisplayName("validates file name required")
        fun validatesFileNameRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = FileOperationActions.CREATE_FILE.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("valid file name passes validation")
        fun validFileNamePassesValidation() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("fileName" to "NewClass.kt")
            )
            val validation = FileOperationActions.CREATE_FILE.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("creates task with file name")
        fun createsTaskWithFileName() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("fileName" to "UserService.kt", "fileType" to "service")
            )
            val task = FileOperationActions.CREATE_FILE.createTask(input)

            assertTrue(task.description.contains("UserService.kt"))
            assertTrue(task.constraints.allowNewFiles)
        }

        @Test
        @DisplayName("requires confirmation")
        fun requiresConfirmation() {
            assertTrue(FileOperationActions.CREATE_FILE.requiresConfirmation)
        }
    }

    // =========================================================================
    // Split File Tests
    // =========================================================================

    @Nested
    @DisplayName("Split File")
    inner class SplitFileTests {

        @Test
        @DisplayName("validates file required")
        fun validatesFileRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = FileOperationActions.SPLIT_FILE.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("creates task with strategy")
        fun createsTaskWithStrategy() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/BigFile.kt",
                parameters = mapOf("strategy" to "by-class")
            )
            val task = FileOperationActions.SPLIT_FILE.createTask(input)

            assertTrue(task.context.userInstructions.contains("by-class"))
            assertTrue(task.constraints.allowNewFiles)
        }
    }

    // =========================================================================
    // Merge Files Tests
    // =========================================================================

    @Nested
    @DisplayName("Merge Files")
    inner class MergeFilesTests {

        @Test
        @DisplayName("validates at least two files")
        fun validatesAtLeastTwoFiles() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("files" to listOf("only_one.kt"))
            )
            val validation = FileOperationActions.MERGE_FILES.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("valid files pass validation")
        fun validFilesPassValidation() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("files" to listOf("a.kt", "b.kt"))
            )
            val validation = FileOperationActions.MERGE_FILES.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("allows deletion for source files")
        fun allowsDeletionForSourceFiles() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("files" to listOf("a.kt", "b.kt"), "targetFile" to "merged.kt")
            )
            val task = FileOperationActions.MERGE_FILES.createTask(input)

            assertTrue(task.constraints.allowDeletion)
        }
    }

    // =========================================================================
    // Delete Unused Tests
    // =========================================================================

    @Nested
    @DisplayName("Delete Unused")
    inner class DeleteUnusedTests {

        @Test
        @DisplayName("requires confirmation")
        fun requiresConfirmation() {
            assertTrue(FileOperationActions.DELETE_UNUSED.requiresConfirmation)
        }

        @Test
        @DisplayName("allows deletion")
        fun allowsDeletion() {
            val input = ActionInput(projectPath = "/project")
            val task = FileOperationActions.DELETE_UNUSED.createTask(input)

            assertTrue(task.constraints.allowDeletion)
            assertTrue(task.constraints.requireConfirmation)
        }
    }

    // =========================================================================
    // Organize Imports Tests
    // =========================================================================

    @Nested
    @DisplayName("Organize Imports")
    inner class OrganizeImportsTests {

        @Test
        @DisplayName("validates file required")
        fun validatesFileRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = FileOperationActions.ORGANIZE_IMPORTS.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("creates refactor task")
        fun createsRefactorTask() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/main.kt"
            )
            val task = FileOperationActions.ORGANIZE_IMPORTS.createTask(input)

            assertEquals(TaskType.REFACTOR, task.type)
        }
    }

    // =========================================================================
    // All File Operation Actions Tests
    // =========================================================================

    @Nested
    @DisplayName("All File Operation Actions")
    inner class AllFileOperationActionsTests {

        @Test
        @DisplayName("ALL contains expected actions")
        fun allContainsExpectedActions() {
            val names = FileOperationActions.ALL.map { it.name }

            assertTrue("create_file" in names)
            assertTrue("create_from_template" in names)
            assertTrue("split_file" in names)
            assertTrue("merge_files" in names)
            assertTrue("delete_unused" in names)
            assertTrue("organize_imports" in names)
        }

        @Test
        @DisplayName("all actions have file operations category")
        fun allActionsHaveFileOperationsCategory() {
            FileOperationActions.ALL.forEach { action ->
                assertEquals(ActionCategory.FILE_OPERATIONS, action.category)
            }
        }
    }

    // =========================================================================
    // Code Analysis Actions Tests
    // =========================================================================

    @Nested
    @DisplayName("Code Analysis Actions")
    inner class CodeAnalysisActionsTests {

        @Test
        @DisplayName("explain creates read-only task")
        fun explainCreatesReadOnlyTask() {
            val input = ActionInput(
                projectPath = "/project",
                selectedCode = "fun test() {}"
            )
            val task = CodeAnalysisActions.EXPLAIN.createTask(input)

            assertEquals(TaskType.EXPLAIN_CODE, task.type)
            assertTrue(task.constraints.isReadOnly)
        }

        @Test
        @DisplayName("review creates read-only task")
        fun reviewCreatesReadOnlyTask() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/main.kt"
            )
            val task = CodeAnalysisActions.REVIEW.createTask(input)

            assertEquals(TaskType.REVIEW, task.type)
            assertTrue(task.constraints.isReadOnly)
        }

        @Test
        @DisplayName("optimize creates task with focus")
        fun optimizeCreatesTaskWithFocus() {
            val input = ActionInput(
                projectPath = "/project",
                selectedCode = "for (i in list) {}",
                parameters = mapOf("focus" to "memory")
            )
            val task = CodeAnalysisActions.OPTIMIZE.createTask(input)

            assertEquals(TaskType.OPTIMIZE, task.type)
            assertTrue(task.context.userInstructions.contains("memory"))
        }

        @Test
        @DisplayName("document creates task with style")
        fun documentCreatesTaskWithStyle() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/main.kt",
                parameters = mapOf("style" to "JavaDoc")
            )
            val task = CodeAnalysisActions.DOCUMENT.createTask(input)

            assertEquals(TaskType.DOCUMENT, task.type)
            assertTrue(task.context.userInstructions.contains("JavaDoc"))
        }

        @Test
        @DisplayName("ALL contains expected actions")
        fun allContainsExpectedActions() {
            val names = CodeAnalysisActions.ALL.map { it.name }

            assertTrue("explain" in names)
            assertTrue("review" in names)
            assertTrue("optimize" in names)
            assertTrue("document" in names)
        }
    }
}
