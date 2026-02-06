package com.sidekick.agent.actions

import com.sidekick.agent.tasks.*

/**
 * # Test Generation Actions
 *
 * Agent actions for generating tests.
 * Part of Sidekick v0.8.5 Agent Actions feature.
 *
 * ## Available Actions
 *
 * - Generate Unit Tests
 * - Generate Test Cases
 * - Add Test Coverage
 * - Generate Mocks
 * - Generate Test Data
 *
 * @since 0.8.5
 */
object TestGenerationActions {

    // =========================================================================
    // Generate Unit Tests
    // =========================================================================

    /**
     * Generates unit tests for a class or function.
     */
    val GENERATE_UNIT_TESTS = object : AgentAction {
        override val name = "generate_unit_tests"
        override val category = ActionCategory.TEST_GENERATION
        override val description = "Generate unit tests for a class or function"

        override fun validateInput(input: ActionInput): ActionValidation {
            if (!input.hasSelection && !input.hasActiveFile && input.targetSymbol.isNullOrBlank()) {
                return ActionValidation.invalid("Target (selection, file, or symbol) is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val target = input.targetSymbol ?: input.activeFile?.substringAfterLast("/") ?: "selected code"
            val framework = input.getString("framework", "JUnit")
            return AgentTask(
                type = TaskType.GENERATE_TESTS,
                description = "Generate unit tests for $target",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Generate comprehensive unit tests for $target using $framework.
                        - Test all public methods
                        - Include edge cases and error conditions
                        - Use descriptive test names
                        - Follow AAA pattern (Arrange, Act, Assert)
                        - Use mocks for dependencies
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Generate Test Cases
    // =========================================================================

    /**
     * Generates specific test cases.
     */
    val GENERATE_TEST_CASES = object : AgentAction {
        override val name = "generate_test_cases"
        override val category = ActionCategory.TEST_GENERATION
        override val description = "Generate specific test cases for edge conditions"

        override fun createTask(input: ActionInput): AgentTask {
            val testCases = input.getList("testCases")
            return AgentTask(
                type = TaskType.GENERATE_TESTS,
                description = "Generate test cases for edge conditions",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Generate test cases focusing on:
                        ${if (testCases.isNotEmpty()) testCases.joinToString("\n") { "- $it" } else """
                        - Null/empty inputs
                        - Boundary conditions
                        - Error conditions
                        - Concurrent access (if applicable)
                        """.trimIndent()}
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Add Test Coverage
    // =========================================================================

    /**
     * Adds tests to improve coverage.
     */
    val ADD_TEST_COVERAGE = object : AgentAction {
        override val name = "add_test_coverage"
        override val category = ActionCategory.TEST_GENERATION
        override val description = "Add tests to improve code coverage"

        override fun createTask(input: ActionInput): AgentTask {
            val targetCoverage = input.getInt("targetCoverage", 80)
            return AgentTask(
                type = TaskType.GENERATE_TESTS,
                description = "Add tests to improve coverage to $targetCoverage%",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Add tests to improve code coverage to $targetCoverage%.
                        - Identify untested code paths
                        - Add tests for uncovered branches
                        - Focus on critical logic first
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Generate Mocks
    // =========================================================================

    /**
     * Generates mock implementations.
     */
    val GENERATE_MOCKS = object : AgentAction {
        override val name = "generate_mocks"
        override val category = ActionCategory.TEST_GENERATION
        override val description = "Generate mock implementations for interfaces"

        override fun validateInput(input: ActionInput): ActionValidation {
            if (input.targetSymbol.isNullOrBlank() && !input.hasSelection) {
                return ActionValidation.invalid("Interface or class to mock is required")
            }
            return ActionValidation.valid()
        }

        override fun createTask(input: ActionInput): AgentTask {
            val target = input.targetSymbol ?: "selected interface"
            val mockFramework = input.getString("mockFramework", "Mockito")
            return AgentTask(
                type = TaskType.GENERATE_TESTS,
                description = "Generate mock implementation for $target",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Generate mock implementation for $target using $mockFramework.
                        - Create configurable mock behavior
                        - Add verification methods
                        - Support argument matchers
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Generate Test Data
    // =========================================================================

    /**
     * Generates test data/fixtures.
     */
    val GENERATE_TEST_DATA = object : AgentAction {
        override val name = "generate_test_data"
        override val category = ActionCategory.TEST_GENERATION
        override val description = "Generate test data and fixtures"

        override fun createTask(input: ActionInput): AgentTask {
            val dataType = input.getString("dataType", "fixture")
            return AgentTask(
                type = TaskType.GENERATE_TESTS,
                description = "Generate test $dataType",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Generate test data/fixtures.
                        - Create realistic test data
                        - Include edge cases (empty, null, boundary values)
                        - Create factory methods for reuse
                        - Use builders where appropriate
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    // =========================================================================
    // Generate Integration Test
    // =========================================================================

    /**
     * Generates integration tests.
     */
    val GENERATE_INTEGRATION_TESTS = object : AgentAction {
        override val name = "generate_integration_tests"
        override val category = ActionCategory.TEST_GENERATION
        override val description = "Generate integration tests for component interactions"

        override fun createTask(input: ActionInput): AgentTask {
            val components = input.getList("components")
            return AgentTask(
                type = TaskType.GENERATE_TESTS,
                description = "Generate integration tests",
                context = TaskContext(
                    projectPath = input.projectPath,
                    activeFile = input.activeFile,
                    selectedCode = input.selectedCode,
                    cursorPosition = input.cursorLine,
                    userInstructions = """
                        Generate integration tests ${if (components.isNotEmpty()) "for: ${components.joinToString()}" else ""}.
                        - Test component interactions
                        - Set up realistic test environment
                        - Use test containers if applicable
                        - Clean up after tests
                        ${input.userInstructions}
                    """.trimIndent()
                ),
                constraints = TaskConstraints.DEFAULT.copy(
                    allowNewFiles = true
                )
            )
        }
    }

    /**
     * All test generation actions.
     */
    val ALL = listOf(
        GENERATE_UNIT_TESTS,
        GENERATE_TEST_CASES,
        ADD_TEST_COVERAGE,
        GENERATE_MOCKS,
        GENERATE_TEST_DATA,
        GENERATE_INTEGRATION_TESTS
    )
}
