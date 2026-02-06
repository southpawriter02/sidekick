package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for Test Generation Actions.
 */
@DisplayName("Test Generation Actions Tests")
class TestGenerationActionsTest {

    // =========================================================================
    // Generate Unit Tests
    // =========================================================================

    @Nested
    @DisplayName("Generate Unit Tests")
    inner class GenerateUnitTestsTests {

        @Test
        @DisplayName("validates target required")
        fun validatesTargetRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = TestGenerationActions.GENERATE_UNIT_TESTS.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("accepts file")
        fun acceptsFile() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/MyClass.kt"
            )
            val validation = TestGenerationActions.GENERATE_UNIT_TESTS.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("accepts symbol")
        fun acceptsSymbol() {
            val input = ActionInput(
                projectPath = "/project",
                targetSymbol = "MyClass"
            )
            val validation = TestGenerationActions.GENERATE_UNIT_TESTS.validateInput(input)

            assertTrue(validation.valid)
        }

        @Test
        @DisplayName("creates task with test framework")
        fun createsTaskWithTestFramework() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/MyClass.kt",
                parameters = mapOf("framework" to "JUnit5")
            )
            val task = TestGenerationActions.GENERATE_UNIT_TESTS.createTask(input)

            assertEquals(TaskType.GENERATE_TESTS, task.type)
            assertTrue(task.context.userInstructions.contains("JUnit5"))
        }

        @Test
        @DisplayName("allows new files")
        fun allowsNewFiles() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/MyClass.kt"
            )
            val task = TestGenerationActions.GENERATE_UNIT_TESTS.createTask(input)

            assertTrue(task.constraints.allowNewFiles)
        }
    }

    // =========================================================================
    // Generate Test Cases
    // =========================================================================

    @Nested
    @DisplayName("Generate Test Cases")
    inner class GenerateTestCasesTests {

        @Test
        @DisplayName("creates task with specified test cases")
        fun createsTaskWithSpecifiedTestCases() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/Validator.kt",
                parameters = mapOf("testCases" to listOf("null input", "empty string", "max length"))
            )
            val task = TestGenerationActions.GENERATE_TEST_CASES.createTask(input)

            assertTrue(task.context.userInstructions.contains("null input"))
            assertTrue(task.context.userInstructions.contains("empty string"))
        }

        @Test
        @DisplayName("creates default test cases when not specified")
        fun createsDefaultTestCasesWhenNotSpecified() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/Validator.kt"
            )
            val task = TestGenerationActions.GENERATE_TEST_CASES.createTask(input)

            assertTrue(task.context.userInstructions.contains("Null"))
        }
    }

    // =========================================================================
    // Add Test Coverage
    // =========================================================================

    @Nested
    @DisplayName("Add Test Coverage")
    inner class AddTestCoverageTests {

        @Test
        @DisplayName("creates task with target coverage")
        fun createsTaskWithTargetCoverage() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/MyClass.kt",
                parameters = mapOf("targetCoverage" to 90)
            )
            val task = TestGenerationActions.ADD_TEST_COVERAGE.createTask(input)

            assertTrue(task.context.userInstructions.contains("90%"))
        }

        @Test
        @DisplayName("uses default 80% coverage")
        fun usesDefaultCoverage() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/MyClass.kt"
            )
            val task = TestGenerationActions.ADD_TEST_COVERAGE.createTask(input)

            assertTrue(task.context.userInstructions.contains("80%"))
        }
    }

    // =========================================================================
    // Generate Mocks
    // =========================================================================

    @Nested
    @DisplayName("Generate Mocks")
    inner class GenerateMocksTests {

        @Test
        @DisplayName("validates target required")
        fun validatesTargetRequired() {
            val input = ActionInput(projectPath = "/project")
            val validation = TestGenerationActions.GENERATE_MOCKS.validateInput(input)

            assertFalse(validation.valid)
        }

        @Test
        @DisplayName("creates task with mock framework")
        fun createsTaskWithMockFramework() {
            val input = ActionInput(
                projectPath = "/project",
                targetSymbol = "UserRepository",
                parameters = mapOf("mockFramework" to "MockK")
            )
            val task = TestGenerationActions.GENERATE_MOCKS.createTask(input)

            assertTrue(task.context.userInstructions.contains("MockK"))
        }
    }

    // =========================================================================
    // Generate Test Data
    // =========================================================================

    @Nested
    @DisplayName("Generate Test Data")
    inner class GenerateTestDataTests {

        @Test
        @DisplayName("creates task with data type")
        fun createsTaskWithDataType() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/User.kt",
                parameters = mapOf("dataType" to "factory")
            )
            val task = TestGenerationActions.GENERATE_TEST_DATA.createTask(input)

            assertTrue(task.description.contains("factory"))
        }
    }

    // =========================================================================
    // Generate Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Generate Integration Tests")
    inner class GenerateIntegrationTestsTests {

        @Test
        @DisplayName("creates task with components")
        fun createsTaskWithComponents() {
            val input = ActionInput(
                projectPath = "/project",
                parameters = mapOf("components" to listOf("UserService", "OrderService"))
            )
            val task = TestGenerationActions.GENERATE_INTEGRATION_TESTS.createTask(input)

            assertTrue(task.context.userInstructions.contains("UserService"))
            assertTrue(task.context.userInstructions.contains("OrderService"))
        }
    }

    // =========================================================================
    // All Actions Tests
    // =========================================================================

    @Nested
    @DisplayName("All Test Generation Actions")
    inner class AllActionsTests {

        @Test
        @DisplayName("ALL contains expected actions")
        fun allContainsExpectedActions() {
            val names = TestGenerationActions.ALL.map { it.name }

            assertTrue("generate_unit_tests" in names)
            assertTrue("generate_test_cases" in names)
            assertTrue("add_test_coverage" in names)
            assertTrue("generate_mocks" in names)
            assertTrue("generate_test_data" in names)
            assertTrue("generate_integration_tests" in names)
        }

        @Test
        @DisplayName("all actions have test generation category")
        fun allActionsHaveTestGenerationCategory() {
            TestGenerationActions.ALL.forEach { action ->
                assertEquals(ActionCategory.TEST_GENERATION, action.category)
            }
        }

        @Test
        @DisplayName("all actions create GENERATE_TESTS tasks")
        fun allActionsCreateGenerateTestsTasks() {
            val input = ActionInput(
                projectPath = "/project",
                activeFile = "/project/Test.kt",
                selectedCode = "class Test {}",
                targetSymbol = "Test"
            )

            TestGenerationActions.ALL.forEach { action ->
                val task = action.createTask(input)
                assertEquals(TaskType.GENERATE_TESTS, task.type)
            }
        }
    }
}
