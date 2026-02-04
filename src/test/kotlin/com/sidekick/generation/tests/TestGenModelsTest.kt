// =============================================================================
// TestGenModelsTest.kt
// =============================================================================
// Unit tests for test generation data models.
// =============================================================================

package com.sidekick.generation.tests

import com.sidekick.context.ProjectContext
import com.sidekick.context.ProjectType
import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for TestGenModels.
 */
class TestGenModelsTest {

    private fun createMethodSymbol(): SymbolContext {
        return SymbolContext(
            name = "calculate",
            kind = SymbolKind.METHOD,
            signature = "fun calculate(x: Int, y: Int): Int",
            containingClass = "Calculator",
            documentation = null,
            definition = "fun calculate(x: Int, y: Int): Int { return x + y }"
        )
    }
    
    private fun createDotNetProject(): ProjectContext {
        return ProjectContext(
            name = "TestProject",
            basePath = "/test/project",
            projectType = ProjectType.DOTNET,
            frameworkHints = listOf("xunit"),
            keyFiles = listOf("TestProject.csproj")
        )
    }

    @Nested
    @DisplayName("TestGenRequest")
    inner class TestGenRequestTests {
        
        @Test
        @DisplayName("isValid returns true for method symbol")
        fun `isValid returns true for method symbol`() {
            val request = TestGenRequest(
                symbol = createMethodSymbol(),
                projectContext = createDotNetProject()
            )
            assertTrue(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for empty symbol")
        fun `isValid returns false for empty symbol`() {
            val request = TestGenRequest(
                symbol = SymbolContext.EMPTY,
                projectContext = createDotNetProject()
            )
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for unknown symbol kind")
        fun `isValid returns false for unknown symbol kind`() {
            val symbol = SymbolContext(
                name = "something",
                kind = SymbolKind.UNKNOWN,
                signature = null,
                containingClass = null,
                documentation = null,
                definition = "something"
            )
            
            val request = TestGenRequest(
                symbol = symbol,
                projectContext = createDotNetProject()
            )
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for invalid test count")
        fun `isValid returns false for invalid test count`() {
            val request = TestGenRequest(
                symbol = createMethodSymbol(),
                projectContext = createDotNetProject(),
                testCount = 20  // Max is 10
            )
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("getInvalidReason returns null for valid request")
        fun `getInvalidReason returns null for valid request`() {
            val request = TestGenRequest(
                symbol = createMethodSymbol(),
                projectContext = createDotNetProject()
            )
            assertNull(request.getInvalidReason())
        }
        
        @Test
        @DisplayName("getEffectiveFramework auto-detects from project")
        fun `getEffectiveFramework auto-detects from project`() {
            val request = TestGenRequest(
                symbol = createMethodSymbol(),
                projectContext = createDotNetProject(),
                framework = TestFramework.AUTO
            )
            
            assertEquals(TestFramework.XUNIT, request.getEffectiveFramework())
        }
        
        @Test
        @DisplayName("getEffectiveFramework uses explicit framework")
        fun `getEffectiveFramework uses explicit framework`() {
            val request = TestGenRequest(
                symbol = createMethodSymbol(),
                projectContext = createDotNetProject(),
                framework = TestFramework.NUNIT
            )
            
            assertEquals(TestFramework.NUNIT, request.getEffectiveFramework())
        }
        
        @Test
        @DisplayName("forSelection creates request from code")
        fun `forSelection creates request from code`() {
            val request = TestGenRequest.forSelection(
                code = "fun add(a: Int, b: Int) = a + b",
                projectContext = createDotNetProject(),
                language = "kotlin"
            )
            
            assertEquals("Selection", request.symbol.name)
            assertTrue(request.isValid())
        }
        
        @Test
        @DisplayName("TESTABLE_KINDS contains expected kinds")
        fun `TESTABLE_KINDS contains expected kinds`() {
            assertTrue(SymbolKind.METHOD in TestGenRequest.TESTABLE_KINDS)
            assertTrue(SymbolKind.CLASS in TestGenRequest.TESTABLE_KINDS)
            assertTrue(SymbolKind.FUNCTION in TestGenRequest.TESTABLE_KINDS)
            assertFalse(SymbolKind.NAMESPACE in TestGenRequest.TESTABLE_KINDS)
        }
    }

    @Nested
    @DisplayName("TestFramework")
    inner class TestFrameworkTests {
        
        @Test
        @DisplayName("detect returns XUNIT for xunit hints")
        fun `detect returns XUNIT for xunit hints`() {
            val project = createDotNetProject()
            assertEquals(TestFramework.XUNIT, TestFramework.detect(project))
        }
        
        @Test
        @DisplayName("detect returns NUNIT for nunit hints")
        fun `detect returns NUNIT for nunit hints`() {
            val project = ProjectContext(
                name = "TestProject",
                basePath = "/test",
                projectType = ProjectType.DOTNET,
                frameworkHints = listOf("nunit"),
                keyFiles = emptyList()
            )
            assertEquals(TestFramework.NUNIT, TestFramework.detect(project))
        }
        
        @Test
        @DisplayName("detect falls back to project type")
        fun `detect falls back to project type`() {
            val project = ProjectContext(
                name = "TestProject",
                basePath = "/test",
                projectType = ProjectType.GRADLE,
                frameworkHints = emptyList(),
                keyFiles = emptyList()
            )
            assertEquals(TestFramework.JUNIT5, TestFramework.detect(project))
        }
        
        @Test
        @DisplayName("detect returns JEST for NPM project")
        fun `detect returns JEST for NPM project`() {
            val project = ProjectContext(
                name = "TestProject",
                basePath = "/test",
                projectType = ProjectType.NPM,
                frameworkHints = emptyList(),
                keyFiles = emptyList()
            )
            assertEquals(TestFramework.JEST, TestFramework.detect(project))
        }
        
        @Test
        @DisplayName("detect returns PYTEST for Python project")
        fun `detect returns PYTEST for Python project`() {
            val project = ProjectContext(
                name = "TestProject",
                basePath = "/test",
                projectType = ProjectType.PYTHON,
                frameworkHints = emptyList(),
                keyFiles = emptyList()
            )
            assertEquals(TestFramework.PYTEST, TestFramework.detect(project))
        }
        
        @Test
        @DisplayName("forLanguage returns frameworks for csharp")
        fun `forLanguage returns frameworks for csharp`() {
            val frameworks = TestFramework.forLanguage("csharp")
            
            assertTrue(TestFramework.XUNIT in frameworks)
            assertTrue(TestFramework.NUNIT in frameworks)
            assertTrue(TestFramework.MSTEST in frameworks)
            assertFalse(TestFramework.JUNIT5 in frameworks)
        }
        
        @Test
        @DisplayName("forLanguage returns frameworks for kotlin")
        fun `forLanguage returns frameworks for kotlin`() {
            val frameworks = TestFramework.forLanguage("kotlin")
            
            assertTrue(TestFramework.KOTEST in frameworks)
            assertTrue(TestFramework.JUNIT_KOTLIN in frameworks)
            assertFalse(TestFramework.XUNIT in frameworks)
        }
        
        @Test
        @DisplayName("has correct test annotations")
        fun `has correct test annotations`() {
            assertEquals("[Fact]", TestFramework.XUNIT.testAnnotation)
            assertEquals("[Test]", TestFramework.NUNIT.testAnnotation)
            assertEquals("@Test", TestFramework.JUNIT5.testAnnotation)
        }
    }

    @Nested
    @DisplayName("TestStyle")
    inner class TestStyleTests {
        
        @Test
        @DisplayName("AAA has correct sections")
        fun `AAA has correct sections`() {
            val style = TestStyle.AAA
            assertEquals(listOf("Arrange", "Act", "Assert"), style.sections)
        }
        
        @Test
        @DisplayName("GWT has correct sections")
        fun `GWT has correct sections`() {
            val style = TestStyle.GWT
            assertEquals(listOf("Given", "When", "Then"), style.sections)
        }
        
        @Test
        @DisplayName("SIMPLE has empty sections")
        fun `SIMPLE has empty sections`() {
            val style = TestStyle.SIMPLE
            assertTrue(style.sections.isEmpty())
        }
        
        @Test
        @DisplayName("sectionComment returns formatted comment")
        fun `sectionComment returns formatted comment`() {
            assertEquals("// Arrange", TestStyle.AAA.sectionComment(0))
            assertEquals("// Assert", TestStyle.AAA.sectionComment(2))
        }
        
        @Test
        @DisplayName("sectionComment returns null for invalid index")
        fun `sectionComment returns null for invalid index`() {
            assertNull(TestStyle.AAA.sectionComment(5))
        }
    }

    @Nested
    @DisplayName("TestGenResult")
    inner class TestGenResultTests {
        
        @Test
        @DisplayName("success creates successful result")
        fun `success creates successful result`() {
            val result = TestGenResult.success(
                testCode = "test code",
                testFileName = "CalculatorTests.cs",
                framework = TestFramework.XUNIT,
                testCount = 3
            )
            
            assertTrue(result.success)
            assertEquals("test code", result.testCode)
            assertEquals("CalculatorTests.cs", result.testFileName)
            assertEquals(TestFramework.XUNIT, result.framework)
            assertEquals(3, result.testCount)
            assertNull(result.error)
        }
        
        @Test
        @DisplayName("failure creates failed result")
        fun `failure creates failed result`() {
            val result = TestGenResult.failure("Error message")
            
            assertFalse(result.success)
            assertEquals("", result.testCode)
            assertEquals("Error message", result.error)
        }
    }

    @Nested
    @DisplayName("TestCase")
    inner class TestCaseTests {
        
        @Test
        @DisplayName("creates test case with all properties")
        fun `creates test case with all properties`() {
            val testCase = TestCase(
                name = "Calculate_ValidInput_ReturnsSum",
                description = "Test addition",
                type = TestCaseType.HAPPY_PATH,
                inputs = mapOf("a" to "1", "b" to "2"),
                expectedOutput = "3"
            )
            
            assertEquals("Calculate_ValidInput_ReturnsSum", testCase.name)
            assertEquals(TestCaseType.HAPPY_PATH, testCase.type)
            assertEquals("3", testCase.expectedOutput)
        }
    }

    @Nested
    @DisplayName("TestCaseType")
    inner class TestCaseTypeTests {
        
        @Test
        @DisplayName("has all expected values")
        fun `has all expected values`() {
            val types = TestCaseType.entries
            
            assertTrue(types.any { it == TestCaseType.HAPPY_PATH })
            assertTrue(types.any { it == TestCaseType.EDGE_CASE })
            assertTrue(types.any { it == TestCaseType.ERROR_CASE })
            assertTrue(types.any { it == TestCaseType.NULL_CASE })
            assertTrue(types.any { it == TestCaseType.PERFORMANCE })
            assertEquals(5, types.size)
        }
    }
}
