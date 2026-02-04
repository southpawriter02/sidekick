// =============================================================================
// TestGenModels.kt
// =============================================================================
// Data models for unit test generation.
//
// This includes:
// - TestGenRequest - request for generating unit tests
// - TestFramework - supported test frameworks
// - TestStyle - test structure patterns (AAA, GWT)
// - TestGenResult - generation result with test code
//
// DESIGN NOTES:
// - Framework auto-detection from project context
// - Multiple style options for test structure
// - Supports mocking and edge case generation
// =============================================================================

package com.sidekick.generation.tests

import com.sidekick.context.ProjectContext
import com.sidekick.context.ProjectType
import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind

/**
 * Request for generating unit tests.
 *
 * @property symbol The symbol to generate tests for
 * @property projectContext Project context for framework detection
 * @property framework Test framework to use
 * @property style Test structure style
 * @property includeMocks Whether to include mocking for dependencies
 * @property includeEdgeCases Whether to include edge case tests
 * @property testCount Number of tests to generate
 */
data class TestGenRequest(
    val symbol: SymbolContext,
    val projectContext: ProjectContext,
    val framework: TestFramework = TestFramework.AUTO,
    val style: TestStyle = TestStyle.AAA,
    val includeMocks: Boolean = true,
    val includeEdgeCases: Boolean = true,
    val testCount: Int = 3
) {
    /**
     * Whether this request is valid for generation.
     */
    fun isValid(): Boolean {
        return symbol.name.isNotBlank() &&
               symbol.kind in TESTABLE_KINDS &&
               testCount in 1..10
    }
    
    /**
     * Gets a descriptive reason if the request is invalid.
     */
    fun getInvalidReason(): String? {
        return when {
            symbol.name.isBlank() -> "No symbol name"
            symbol.kind !in TESTABLE_KINDS -> "Symbol type '${symbol.kind}' is not testable"
            testCount !in 1..10 -> "Test count must be between 1 and 10"
            else -> null
        }
    }
    
    /**
     * Gets the effective framework, auto-detecting if needed.
     */
    fun getEffectiveFramework(): TestFramework {
        return if (framework == TestFramework.AUTO) {
            TestFramework.detect(projectContext)
        } else {
            framework
        }
    }
    
    companion object {
        /**
         * Symbol kinds that can have tests generated.
         */
        val TESTABLE_KINDS = setOf(
            SymbolKind.CLASS,
            SymbolKind.METHOD,
            SymbolKind.FUNCTION,
            SymbolKind.PROPERTY
        )
        
        /**
         * Creates a request from code selection.
         */
        fun forSelection(
            code: String,
            projectContext: ProjectContext,
            language: String
        ): TestGenRequest {
            val symbol = SymbolContext(
                name = "Selection",
                kind = SymbolKind.FUNCTION,
                signature = null,
                containingClass = null,
                documentation = null,
                definition = code
            )
            
            return TestGenRequest(
                symbol = symbol,
                projectContext = projectContext
            )
        }
    }
}

/**
 * Supported test frameworks.
 */
enum class TestFramework(
    val displayName: String,
    val languageId: String,
    val imports: List<String>,
    val testAnnotation: String
) {
    AUTO(
        displayName = "Auto-detect",
        languageId = "text",
        imports = emptyList(),
        testAnnotation = ""
    ),
    
    XUNIT(
        displayName = "xUnit",
        languageId = "csharp",
        imports = listOf("Xunit"),
        testAnnotation = "[Fact]"
    ),
    
    NUNIT(
        displayName = "NUnit",
        languageId = "csharp",
        imports = listOf("NUnit.Framework"),
        testAnnotation = "[Test]"
    ),
    
    MSTEST(
        displayName = "MSTest",
        languageId = "csharp",
        imports = listOf("Microsoft.VisualStudio.TestTools.UnitTesting"),
        testAnnotation = "[TestMethod]"
    ),
    
    JUNIT5(
        displayName = "JUnit 5",
        languageId = "java",
        imports = listOf("org.junit.jupiter.api.*"),
        testAnnotation = "@Test"
    ),
    
    KOTEST(
        displayName = "Kotest",
        languageId = "kotlin",
        imports = listOf(
            "io.kotest.core.spec.style.FunSpec",
            "io.kotest.matchers.shouldBe"
        ),
        testAnnotation = ""
    ),
    
    JUNIT_KOTLIN(
        displayName = "JUnit 5 (Kotlin)",
        languageId = "kotlin",
        imports = listOf(
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.Assertions.*"
        ),
        testAnnotation = "@Test"
    ),
    
    JEST(
        displayName = "Jest",
        languageId = "typescript",
        imports = emptyList(),
        testAnnotation = ""
    ),
    
    PYTEST(
        displayName = "pytest",
        languageId = "python",
        imports = listOf("pytest"),
        testAnnotation = ""
    );
    
    companion object {
        /**
         * Detects the appropriate test framework from project context.
         */
        fun detect(projectContext: ProjectContext): TestFramework {
            val hints = projectContext.frameworkHints.map { it.lowercase() }
            
            // Check for explicit framework hints
            return when {
                "xunit" in hints -> XUNIT
                "nunit" in hints -> NUNIT
                "mstest" in hints -> MSTEST
                "junit" in hints -> JUNIT5
                "kotest" in hints -> KOTEST
                "jest" in hints -> JEST
                "pytest" in hints -> PYTEST
                else -> detectFromProjectType(projectContext.projectType)
            }
        }
        
        private fun detectFromProjectType(projectType: ProjectType): TestFramework {
            return when (projectType) {
                ProjectType.DOTNET -> XUNIT
                ProjectType.GRADLE -> JUNIT5
                ProjectType.NPM -> JEST
                ProjectType.PYTHON -> PYTEST
                ProjectType.UNITY -> NUNIT
                ProjectType.RUST -> XUNIT  // No specific support yet
                ProjectType.GO -> XUNIT    // No specific support yet
                ProjectType.UNKNOWN -> XUNIT
            }
        }
        
        /**
         * Gets frameworks for a specific language.
         */
        fun forLanguage(language: String): List<TestFramework> {
            val lang = language.lowercase()
            return entries.filter { 
                it != AUTO && it.languageId == lang 
            }
        }
    }
}

/**
 * Test structure styles.
 */
enum class TestStyle(
    val displayName: String,
    val sections: List<String>,
    val description: String
) {
    AAA(
        displayName = "Arrange-Act-Assert",
        sections = listOf("Arrange", "Act", "Assert"),
        description = "Classic pattern: setup, execute, verify"
    ),
    
    GWT(
        displayName = "Given-When-Then",
        sections = listOf("Given", "When", "Then"),
        description = "BDD pattern: precondition, action, expected outcome"
    ),
    
    SIMPLE(
        displayName = "Simple",
        sections = emptyList(),
        description = "Direct assertions without explicit sections"
    );
    
    /**
     * Generates comment prefix for a section.
     */
    fun sectionComment(index: Int): String? {
        return sections.getOrNull(index)?.let { "// $it" }
    }
}

/**
 * Generated test result.
 *
 * @property testCode The generated test code
 * @property testFileName Suggested file name for the tests
 * @property framework Framework used for generation
 * @property testCount Number of tests generated
 * @property success Whether generation succeeded
 * @property error Error message if generation failed
 */
data class TestGenResult(
    val testCode: String,
    val testFileName: String,
    val framework: TestFramework,
    val testCount: Int,
    val success: Boolean,
    val error: String? = null
) {
    companion object {
        /**
         * Creates a successful result.
         */
        fun success(
            testCode: String,
            testFileName: String,
            framework: TestFramework,
            testCount: Int
        ): TestGenResult {
            return TestGenResult(
                testCode = testCode,
                testFileName = testFileName,
                framework = framework,
                testCount = testCount,
                success = true
            )
        }
        
        /**
         * Creates a failure result.
         */
        fun failure(error: String): TestGenResult {
            return TestGenResult(
                testCode = "",
                testFileName = "",
                framework = TestFramework.AUTO,
                testCount = 0,
                success = false,
                error = error
            )
        }
    }
}

/**
 * Test case suggestion for structured generation.
 */
data class TestCase(
    val name: String,
    val description: String,
    val type: TestCaseType,
    val inputs: Map<String, String> = emptyMap(),
    val expectedOutput: String? = null
)

/**
 * Types of test cases.
 */
enum class TestCaseType {
    /** Normal/happy path test case */
    HAPPY_PATH,
    
    /** Edge case (boundaries, extremes) */
    EDGE_CASE,
    
    /** Error/exception handling test */
    ERROR_CASE,
    
    /** Null/empty input handling */
    NULL_CASE,
    
    /** Performance or stress test */
    PERFORMANCE
}
