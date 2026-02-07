// =============================================================================
// TestGenService.kt
// =============================================================================
// Service for generating unit tests using LLM.
//
// This service:
// - Generates unit tests for methods, classes, and code selections
// - Supports multiple test frameworks (xUnit, NUnit, JUnit, etc.)
// - Uses Ollama for intelligent test generation
//
// DESIGN NOTES:
// - Project-level service
// - Auto-detects appropriate test framework
// - Generates mocks and edge cases on request
// =============================================================================

package com.sidekick.generation.tests

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContextService
import com.sidekick.context.SymbolContext
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.UnifiedChatRequest
import com.sidekick.llm.provider.UnifiedMessage
import com.sidekick.settings.SidekickSettings

/**
 * Service for generating unit tests using LLM.
 *
 * Handles test generation for methods, classes, and code selections
 * using the appropriate test framework for the project.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = TestGenService.getInstance(project)
 * val result = service.generateForCurrentContext()
 * if (result.success) {
 *     // Create test file with result.testCode
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class TestGenService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(TestGenService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): TestGenService {
            return project.getService(TestGenService::class.java)
        }
        
        /**
         * Prompt template for test generation.
         */
        private val TEST_GEN_PROMPT = """
            Generate %d unit tests for this %s code using %s:
            
            ```%s
            %s
            ```
            
            Requirements:
            - Use %s test framework with proper imports
            - Use %s pattern for test structure
            - Each test should have a descriptive name following the pattern: MethodName_Scenario_ExpectedBehavior
            %s
            %s
            
            Generate complete, compilable test code with all necessary imports.
            Output ONLY the test class code, no explanations.
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Generates tests for the current selection or symbol at cursor.
     */
    suspend fun generateForCurrentContext(): TestGenResult {
        LOG.info("Generating tests for current context")
        
        val editorService = EditorContextService.getInstance(project)
        val projectService = ProjectContextService.getInstance(project)
        
        val editorContext = editorService.getCurrentContext()
        val projectContext = projectService.getProjectContext()
        val symbol = editorService.getSymbolAtCursor()
        
        // Use selection if available, otherwise symbol at cursor
        val codeToTest = if (editorContext.hasSelection) {
            editorContext.selection ?: ""
        } else {
            symbol.definition
        }
        
        if (codeToTest.isBlank()) {
            return TestGenResult.failure("No code selected or symbol at cursor")
        }
        
        val request = if (editorContext.hasSelection) {
            TestGenRequest.forSelection(
                code = codeToTest,
                projectContext = projectContext,
                language = editorContext.language
            )
        } else {
            TestGenRequest(
                symbol = symbol,
                projectContext = projectContext
            )
        }
        
        return generate(request)
    }

    /**
     * Generates tests from a request.
     */
    suspend fun generate(request: TestGenRequest): TestGenResult {
        val framework = request.getEffectiveFramework()
        LOG.info("Generating ${request.testCount} tests using ${framework.displayName}")
        
        if (!request.isValid()) {
            val reason = request.getInvalidReason() ?: "Invalid request"
            LOG.debug("Invalid request: $reason")
            return TestGenResult.failure(reason)
        }
        
        return try {
            val prompt = buildPrompt(request, framework)
            val response = callLLM(prompt)
            val testCode = formatTestCode(response, framework)
            val testFileName = generateTestFileName(request.symbol.name, framework)
            
            LOG.info("Successfully generated tests (${testCode.length} chars)")
            TestGenResult.success(testCode, testFileName, framework, request.testCount)
            
        } catch (e: Exception) {
            LOG.warn("Test generation failed: ${e.message}", e)
            TestGenResult.failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Generates a test stub without LLM.
     * Useful as a fallback or for quick scaffolding.
     */
    fun generateStub(request: TestGenRequest): TestGenResult {
        val framework = request.getEffectiveFramework()
        val symbol = request.symbol
        val testFileName = generateTestFileName(symbol.name, framework)
        
        val stub = when (framework) {
            TestFramework.XUNIT -> generateXUnitStub(symbol, request.style)
            TestFramework.NUNIT -> generateNUnitStub(symbol, request.style)
            TestFramework.MSTEST -> generateMSTestStub(symbol, request.style)
            TestFramework.JUNIT5 -> generateJUnit5Stub(symbol, request.style)
            TestFramework.JUNIT_KOTLIN -> generateJUnitKotlinStub(symbol, request.style)
            TestFramework.KOTEST -> generateKotestStub(symbol, request.style)
            TestFramework.JEST -> generateJestStub(symbol, request.style)
            TestFramework.PYTEST -> generatePytestStub(symbol, request.style)
            TestFramework.AUTO -> generateXUnitStub(symbol, request.style)
        }
        
        return TestGenResult.success(stub, testFileName, framework, 1)
    }

    /**
     * Suggests test cases for a symbol without generating code.
     */
    fun suggestTestCases(symbol: SymbolContext, includeEdgeCases: Boolean): List<TestCase> {
        val cases = mutableListOf<TestCase>()
        
        // Always add a happy path test
        cases.add(TestCase(
            name = "${symbol.name}_ValidInput_ReturnsExpectedResult",
            description = "Test ${symbol.name} with valid input",
            type = TestCaseType.HAPPY_PATH
        ))
        
        if (includeEdgeCases) {
            // Add null case if applicable
            cases.add(TestCase(
                name = "${symbol.name}_NullInput_ThrowsArgumentNullException",
                description = "Test ${symbol.name} handles null input",
                type = TestCaseType.NULL_CASE
            ))
            
            // Add empty/boundary cases
            cases.add(TestCase(
                name = "${symbol.name}_EmptyInput_ReturnsEmptyResult",
                description = "Test ${symbol.name} with empty input",
                type = TestCaseType.EDGE_CASE
            ))
        }
        
        return cases
    }

    // -------------------------------------------------------------------------
    // Private Methods - Prompting
    // -------------------------------------------------------------------------

    private fun buildPrompt(request: TestGenRequest, framework: TestFramework): String {
        val symbol = request.symbol
        
        val mockRequirement = if (request.includeMocks) {
            "- Include mocking for any dependencies using ${getMockingLibrary(framework)}"
        } else ""
        
        val edgeCaseRequirement = if (request.includeEdgeCases) {
            "- Include edge case tests (null, empty, boundary values)"
        } else ""
        
        return TEST_GEN_PROMPT.format(
            request.testCount,
            symbol.kind.name.lowercase(),
            framework.displayName,
            framework.languageId,
            symbol.definition,
            framework.displayName,
            request.style.displayName,
            mockRequirement,
            edgeCaseRequirement
        )
    }

    private suspend fun callLLM(prompt: String): String {
        val providerManager = ProviderManager.getInstance()
        val settings = SidekickSettings.getInstance()

        val request = UnifiedChatRequest(
            model = settings.defaultModel.ifEmpty { "llama3.2" },
            messages = listOf(UnifiedMessage.user(prompt)),
            systemPrompt = "You are a test generator. Generate only test code with imports, no explanations.",
            temperature = 0.3f,
            maxTokens = 1500,
            stream = false
        )

        val response = providerManager.chat(request)
        return response.content ?: ""
    }

    private fun formatTestCode(raw: String, framework: TestFramework): String {
        var result = raw.trim()
        
        // Remove markdown code fences if present
        if (result.startsWith("```")) {
            result = result.substringAfter("\n").substringBeforeLast("```").trim()
        }
        
        return result
    }

    private fun generateTestFileName(symbolName: String, framework: TestFramework): String {
        val baseName = symbolName.replace(Regex("[^a-zA-Z0-9]"), "")
        
        return when (framework) {
            TestFramework.XUNIT, TestFramework.NUNIT, TestFramework.MSTEST -> "${baseName}Tests.cs"
            TestFramework.JUNIT5 -> "${baseName}Test.java"
            TestFramework.JUNIT_KOTLIN, TestFramework.KOTEST -> "${baseName}Test.kt"
            TestFramework.JEST -> "${baseName}.test.ts"
            TestFramework.PYTEST -> "test_${baseName.lowercase()}.py"
            TestFramework.AUTO -> "${baseName}Tests.cs"
        }
    }

    private fun getMockingLibrary(framework: TestFramework): String {
        return when (framework) {
            TestFramework.XUNIT, TestFramework.NUNIT, TestFramework.MSTEST -> "Moq or NSubstitute"
            TestFramework.JUNIT5 -> "Mockito"
            TestFramework.JUNIT_KOTLIN, TestFramework.KOTEST -> "MockK"
            TestFramework.JEST -> "jest.mock()"
            TestFramework.PYTEST -> "pytest-mock or unittest.mock"
            TestFramework.AUTO -> "appropriate mocking library"
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Stub Generation
    // -------------------------------------------------------------------------

    private fun generateXUnitStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            using Xunit;
            
            public class ${symbol.name}Tests
            {
                [Fact]
                public void ${symbol.name}_ValidInput_ReturnsExpectedResult()
                {
                    ${style.sectionComment(0) ?: ""}
                    // TODO: Setup test data
                    
                    ${style.sectionComment(1) ?: ""}
                    // TODO: Execute method under test
                    
                    ${style.sectionComment(2) ?: ""}
                    // TODO: Verify results
                    Assert.True(true);
                }
            }
        """.trimIndent()
    }

    private fun generateNUnitStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            using NUnit.Framework;
            
            [TestFixture]
            public class ${symbol.name}Tests
            {
                [Test]
                public void ${symbol.name}_ValidInput_ReturnsExpectedResult()
                {
                    ${style.sectionComment(0) ?: ""}
                    // TODO: Setup test data
                    
                    ${style.sectionComment(1) ?: ""}
                    // TODO: Execute method under test
                    
                    ${style.sectionComment(2) ?: ""}
                    // TODO: Verify results
                    Assert.That(true, Is.True);
                }
            }
        """.trimIndent()
    }

    private fun generateMSTestStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            using Microsoft.VisualStudio.TestTools.UnitTesting;
            
            [TestClass]
            public class ${symbol.name}Tests
            {
                [TestMethod]
                public void ${symbol.name}_ValidInput_ReturnsExpectedResult()
                {
                    ${style.sectionComment(0) ?: ""}
                    // TODO: Setup test data
                    
                    ${style.sectionComment(1) ?: ""}
                    // TODO: Execute method under test
                    
                    ${style.sectionComment(2) ?: ""}
                    // TODO: Verify results
                    Assert.IsTrue(true);
                }
            }
        """.trimIndent()
    }

    private fun generateJUnit5Stub(symbol: SymbolContext, style: TestStyle): String {
        return """
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;
            
            class ${symbol.name}Test {
            
                @Test
                void ${symbol.name.replaceFirstChar { it.lowercase() }}_validInput_returnsExpectedResult() {
                    ${style.sectionComment(0) ?: ""}
                    // TODO: Setup test data
                    
                    ${style.sectionComment(1) ?: ""}
                    // TODO: Execute method under test
                    
                    ${style.sectionComment(2) ?: ""}
                    // TODO: Verify results
                    assertTrue(true);
                }
            }
        """.trimIndent()
    }

    private fun generateJUnitKotlinStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.Assertions.*
            
            class ${symbol.name}Test {
            
                @Test
                fun `${symbol.name} valid input returns expected result`() {
                    ${style.sectionComment(0) ?: ""}
                    // TODO: Setup test data
                    
                    ${style.sectionComment(1) ?: ""}
                    // TODO: Execute method under test
                    
                    ${style.sectionComment(2) ?: ""}
                    // TODO: Verify results
                    assertTrue(true)
                }
            }
        """.trimIndent()
    }

    private fun generateKotestStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            import io.kotest.core.spec.style.FunSpec
            import io.kotest.matchers.shouldBe
            
            class ${symbol.name}Test : FunSpec({
            
                test("${symbol.name} valid input returns expected result") {
                    // TODO: Setup and execute
                    
                    // TODO: Verify
                    true shouldBe true
                }
            })
        """.trimIndent()
    }

    private fun generateJestStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            describe('${symbol.name}', () => {
                it('should return expected result for valid input', () => {
                    ${style.sectionComment(0)?.replace("//", "//") ?: ""}
                    // TODO: Setup test data
                    
                    ${style.sectionComment(1)?.replace("//", "//") ?: ""}
                    // TODO: Execute function under test
                    
                    ${style.sectionComment(2)?.replace("//", "//") ?: ""}
                    // TODO: Verify results
                    expect(true).toBe(true);
                });
            });
        """.trimIndent()
    }

    private fun generatePytestStub(symbol: SymbolContext, style: TestStyle): String {
        return """
            import pytest
            
            
            def test_${symbol.name.lowercase()}_valid_input_returns_expected_result():
                ${style.sectionComment(0)?.replace("//", "#") ?: ""}
                # TODO: Setup test data
                
                ${style.sectionComment(1)?.replace("//", "#") ?: ""}
                # TODO: Execute function under test
                
                ${style.sectionComment(2)?.replace("//", "#") ?: ""}
                # TODO: Verify results
                assert True
        """.trimIndent()
    }
}
