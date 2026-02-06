package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Agent Actions.
 */
@DisplayName("Agent Actions Tests")
class AgentActionsTest {

    // =========================================================================
    // ActionCategory Tests
    // =========================================================================

    @Nested
    @DisplayName("ActionCategory")
    inner class ActionCategoryTests {

        @Test
        @DisplayName("all categories have display names")
        fun allCategoriesHaveDisplayNames() {
            ActionCategory.entries.forEach { category ->
                assertTrue(category.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("contains expected categories")
        fun containsExpectedCategories() {
            val names = ActionCategory.entries.map { it.name }
            assertTrue("REFACTORING" in names)
            assertTrue("TEST_GENERATION" in names)
            assertTrue("FILE_OPERATIONS" in names)
            assertTrue("CODE_ANALYSIS" in names)
        }
    }

    // =========================================================================
    // ActionInput Tests
    // =========================================================================

    @Nested
    @DisplayName("ActionInput")
    inner class ActionInputTests {

        @Test
        @DisplayName("hasSelection detects selected code")
        fun hasSelectionDetectsSelectedCode() {
            val with = ActionInput(
                projectPath = "/project",
                selectedCode = "fun test() {}"
            )
            assertTrue(with.hasSelection)

            val without = ActionInput(projectPath = "/project")
            assertFalse(without.hasSelection)
        }

        @Test
        @DisplayName("hasActiveFile detects active file")
        fun hasActiveFileDetectsActiveFile() {
            val with = ActionInput(
                projectPath = "/project",
                activeFile = "/project/main.kt"
            )
            assertTrue(with.hasActiveFile)

            val without = ActionInput(projectPath = "/project")
            assertFalse(without.hasActiveFile)
        }

        @Test
        @DisplayName("getString extracts string parameter")
        fun getStringExtractsStringParameter() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("methodName" to "doSomething")
            )

            assertEquals("doSomething", input.getString("methodName"))
            assertEquals("default", input.getString("missing", "default"))
        }

        @Test
        @DisplayName("getInt extracts int parameter")
        fun getIntExtractsIntParameter() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("count" to 42)
            )

            assertEquals(42, input.getInt("count"))
            assertEquals(0, input.getInt("missing"))
        }

        @Test
        @DisplayName("getBoolean extracts boolean parameter")
        fun getBooleanExtractsBooleanParameter() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("enabled" to true)
            )

            assertTrue(input.getBoolean("enabled"))
            assertFalse(input.getBoolean("missing"))
        }

        @Test
        @DisplayName("getList extracts list parameter")
        fun getListExtractsListParameter() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("files" to listOf("a.kt", "b.kt"))
            )

            val files = input.getList("files")
            assertEquals(2, files.size)
            assertTrue("a.kt" in files)
        }

        @Test
        @DisplayName("forFile factory creates file input")
        fun forFileFactoryCreatesFileInput() {
            val input = ActionInput.forFile("/project", "/project/main.kt", "instruction")

            assertEquals("/project", input.projectPath)
            assertEquals("/project/main.kt", input.activeFile)
            assertEquals("instruction", input.userInstructions)
        }

        @Test
        @DisplayName("forSelection factory creates selection input")
        fun forSelectionFactoryCreatesSelectionInput() {
            val input = ActionInput.forSelection(
                "/project",
                "/project/main.kt",
                "fun test() {}",
                "explain"
            )

            assertTrue(input.hasActiveFile)
            assertTrue(input.hasSelection)
        }

        @Test
        @DisplayName("forSymbol factory creates symbol input")
        fun forSymbolFactoryCreatesSymbolInput() {
            val input = ActionInput.forSymbol("/project", "MyClass")

            assertEquals("MyClass", input.targetSymbol)
        }
    }

    // =========================================================================
    // ActionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ActionResult")
    inner class ActionResultTests {

        @Test
        @DisplayName("success creates success result")
        fun successCreatesSuccessResult() {
            val result = ActionResult.success("test_action")

            assertTrue(result.success)
            assertEquals("test_action", result.actionName)
        }

        @Test
        @DisplayName("failure creates failure result")
        fun failureCreatesFailureResult() {
            val result = ActionResult.failure("test_action", "Error message")

            assertFalse(result.success)
            assertEquals("Error message", result.getString("error"))
        }

        @Test
        @DisplayName("durationMs calculates correctly")
        fun durationMsCalculatesCorrectly() {
            val start = Instant.now()
            val end = start.plusMillis(1000)

            val result = ActionResult(
                actionName = "test",
                success = true,
                taskResult = null,
                startTime = start,
                endTime = end
            )

            assertEquals(1000, result.durationMs)
        }

        @Test
        @DisplayName("summary from task result")
        fun summaryFromTaskResult() {
            val taskResult = TaskResult.success("Task completed")
            val result = ActionResult.success("test", taskResult)

            assertEquals("Task completed", result.summary)
        }
    }

    // =========================================================================
    // ActionValidation Tests
    // =========================================================================

    @Nested
    @DisplayName("ActionValidation")
    inner class ActionValidationTests {

        @Test
        @DisplayName("valid creates valid result")
        fun validCreatesValidResult() {
            val validation = ActionValidation.valid()

            assertTrue(validation.valid)
            assertFalse(validation.hasErrors)
        }

        @Test
        @DisplayName("invalid creates invalid result")
        fun invalidCreatesInvalidResult() {
            val validation = ActionValidation.invalid("Error 1", "Error 2")

            assertFalse(validation.valid)
            assertTrue(validation.hasErrors)
            assertEquals(2, validation.errors.size)
        }

        @Test
        @DisplayName("withWarnings creates valid with warnings")
        fun withWarningsCreatesValidWithWarnings() {
            val validation = ActionValidation.withWarnings("Warning 1")

            assertTrue(validation.valid)
            assertTrue(validation.hasWarnings)
            assertEquals(1, validation.warnings.size)
        }
    }

    // =========================================================================
    // ActionRegistry Tests
    // =========================================================================

    @Nested
    @DisplayName("ActionRegistry")
    inner class ActionRegistryTests {

        @Test
        @DisplayName("contains built-in actions")
        fun containsBuiltInActions() {
            val names = ActionRegistry.getNames()

            assertTrue("extract_method" in names)
            assertTrue("rename_symbol" in names)
            assertTrue("generate_unit_tests" in names)
            assertTrue("create_file" in names)
        }

        @Test
        @DisplayName("get returns action by name")
        fun getReturnsActionByName() {
            val action = ActionRegistry.get("extract_method")

            assertNotNull(action)
            assertEquals("extract_method", action?.name)
            assertEquals(ActionCategory.REFACTORING, action?.category)
        }

        @Test
        @DisplayName("get returns null for unknown action")
        fun getReturnsNullForUnknown() {
            val action = ActionRegistry.get("unknown_action")
            assertNull(action)
        }

        @Test
        @DisplayName("getByCategory filters by category")
        fun getByCategoryFiltersCorrectly() {
            val refactorActions = ActionRegistry.getByCategory(ActionCategory.REFACTORING)

            assertTrue(refactorActions.isNotEmpty())
            refactorActions.forEach { action ->
                assertEquals(ActionCategory.REFACTORING, action.category)
            }
        }

        @Test
        @DisplayName("getAll returns all actions")
        fun getAllReturnsAllActions() {
            val all = ActionRegistry.getAll()

            assertTrue(all.size >= 20) // At least 20 built-in actions
        }
    }

    // =========================================================================
    // ActionEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("ActionEvent")
    inner class ActionEventTests {

        @Test
        @DisplayName("ActionStarted has input")
        fun actionStartedHasInput() {
            val input = ActionInput(projectPath = "/project")
            val event = ActionEvent.ActionStarted("test", input)

            assertEquals("test", event.actionName)
            assertEquals(input, event.input)
            assertNotNull(event.timestamp)
        }

        @Test
        @DisplayName("ActionProgress has progress")
        fun actionProgressHasProgress() {
            val event = ActionEvent.ActionProgress("test", 0.5f, "Halfway done")

            assertEquals(0.5f, event.progress)
            assertEquals("Halfway done", event.message)
        }

        @Test
        @DisplayName("ActionCompleted has result")
        fun actionCompletedHasResult() {
            val result = ActionResult.success("test")
            val event = ActionEvent.ActionCompleted("test", result)

            assertEquals(result, event.result)
        }

        @Test
        @DisplayName("ActionFailed has error")
        fun actionFailedHasError() {
            val event = ActionEvent.ActionFailed("test", "Something went wrong")

            assertEquals("Something went wrong", event.error)
        }
    }
}
