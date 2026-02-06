package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Refactoring Actions.
 */
@DisplayName("Refactor Actions Tests")
class RefactorActionsTest {

    // =========================================================================
    // Extract Method Tests
    // =========================================================================

    @Nested
    @DisplayName("Extract Method")
    inner class ExtractMethodTests {

        @Test
        @DisplayName("validates selection required")
        fun validatesSelectionRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = RefactorActions.EXTRACT_METHOD.validateInput(input)

            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("selected") })
        }

        @Test
        @DisplayName("validates method name required")
        fun validatesMethodNameRequired() {
            val input = ActionInput(
                projectPath = "/project",
                selectedCode = "val x = 1"
            )
            val validation = RefactorActions.EXTRACT_METHOD.validateInput(input)

            assertFalse(validation.valid)
            assertTrue(validation.errors.any { it.contains("Method name") })
        }

        @Test
        @DisplayName("validates method name format")
        fun validatesMethodNameFormat() {
            val input = ActionInput(
                projectPath = "/project",
                selectedCode = "val x = 1",
                parameters = mapOf("methodName" to "123invalid")
            )
            val validation = RefactorActions.EXTRACT_METHOD.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("valid input passes validation")
        fun validInputPassesValidation() {
            val input = ActionInput(
                projectPath = "/project",
                selectedCode = "val x = 1 + 2",
                parameters = mapOf("methodName" to "calculateSum")
            )
            val validation = RefactorActions.EXTRACT_METHOD.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("creates task with correct type")
        fun createsTaskWithCorrectType() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/main.kt",
                selectedCode = "val x = 1 + 2",
                parameters = mapOf("methodName" to "calculateSum")
            )
            val task = RefactorActions.EXTRACT_METHOD.createTask(input)

            assertEquals(TaskType.REFACTOR, task.type)
            assertTrue(task.description.contains("calculateSum"))
            assertEquals("/project/main.kt", task.context.activeFile)
        }

        @Test
        @DisplayName("requires confirmation")
        fun requiresConfirmation() {
            assertTrue(RefactorActions.EXTRACT_METHOD.requiresConfirmation)
        }
    }

    // =========================================================================
    // Rename Symbol Tests
    // =========================================================================

    @Nested
    @DisplayName("Rename Symbol")
    inner class RenameSymbolTests {

        @Test
        @DisplayName("validates old name required")
        fun validatesOldNameRequired() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("newName" to "NewName")
            )
            val validation = RefactorActions.RENAME_SYMBOL.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("validates new name required")
        fun validatesNewNameRequired() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("oldName" to "OldName")
            )
            val validation = RefactorActions.RENAME_SYMBOL.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("validates names must be different")
        fun validatesNamesMustBeDifferent() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("oldName" to "Same", "newName" to "Same")
            )
            val validation = RefactorActions.RENAME_SYMBOL.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("valid rename passes validation")
        fun validRenamePassesValidation() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("oldName" to "OldName", "newName" to "NewName")
            )
            val validation = RefactorActions.RENAME_SYMBOL.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("creates task with both names")
        fun createsTaskWithBothNames() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("oldName" to "OldClass", "newName" to "NewClass")
            )
            val task = RefactorActions.RENAME_SYMBOL.createTask(input)

            assertTrue(task.description.contains("OldClass"))
            assertTrue(task.description.contains("NewClass"))
        }
    }

    // =========================================================================
    // Move Symbol Tests
    // =========================================================================

    @Nested
    @DisplayName("Move Symbol")
    inner class MoveSymbolTests {

        @Test
        @DisplayName("validates symbol name required")
        fun validatesSymbolNameRequired() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("destination" to "/target/")
            )
            val validation = RefactorActions.MOVE_SYMBOL.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("validates destination required")
        fun validatesDestinationRequired() {
            val input = ActionInput(
                projectPath = "/project",
                targetSymbol = "MyClass"
            )
            val validation = RefactorActions.MOVE_SYMBOL.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("allows new files")
        fun allowsNewFiles() {
            val input = ActionInput(
                projectPath = "/project",
                targetSymbol = "MyClass",
                parameters = mapOf("destination" to "/project/target/")
            )
            val task = RefactorActions.MOVE_SYMBOL.createTask(input)

            assertTrue(task.constraints.allowNewFiles)
        }
    }

    // =========================================================================
    // Inline Tests
    // =========================================================================

    @Nested
    @DisplayName("Inline")
    inner class InlineTests {

        @Test
        @DisplayName("validates target required")
        fun validatesTargetRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = RefactorActions.INLINE.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("accepts symbol")
        fun acceptsSymbol() {
            val input = ActionInput(
                projectPath = "/project",
                targetSymbol = "tempVar"
            )
            val validation = RefactorActions.INLINE.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("accepts selection")
        fun acceptsSelection() {
            val input = ActionInput(
                projectPath = "/project",
                selectedCode = "val tempVar = 42"
            )
            val validation = RefactorActions.INLINE.validateInput(input)

            assertTrue(validation.valid)
        }
    }

    // =========================================================================
    // All Actions Tests
    // =========================================================================

    @Nested
    @DisplayName("All Refactor Actions")
    inner class AllActionsTests {

        @Test
        @DisplayName("ALL contains expected actions")
        fun allContainsExpectedActions() {
            val names = RefactorActions.ALL.map { it.name }

            assertTrue("extract_method" in names)
            assertTrue("extract_variable" in names)
            assertTrue("rename_symbol" in names)
            assertTrue("move_symbol" in names)
            assertTrue("inline" in names)
            assertTrue("change_signature" in names)
            assertTrue("simplify" in names)
        }

        @Test
        @DisplayName("all actions have refactoring category")
        fun allActionsHaveRefactoringCategory() {
            RefactorActions.ALL.forEach { action ->
                assertEquals(ActionCategory.REFACTORING, action.category)
            }
        }

        @Test
        @DisplayName("all actions have descriptions")
        fun allActionsHaveDescriptions() {
            RefactorActions.ALL.forEach { action ->
                assertTrue(action.description.isNotBlank())
            }
        }
    }
}
