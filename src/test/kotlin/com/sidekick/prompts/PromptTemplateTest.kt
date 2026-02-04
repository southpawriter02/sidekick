// =============================================================================
// PromptTemplateTest.kt
// =============================================================================
// Unit tests for PromptTemplate and related classes.
// =============================================================================

package com.sidekick.prompts

import com.sidekick.context.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for PromptTemplate.
 */
class PromptTemplateTest {

    @Nested
    @DisplayName("Built-in Templates")
    inner class BuiltInTemplatesTests {
        
        @Test
        @DisplayName("BUILT_IN_TEMPLATES contains 8 templates")
        fun `BUILT_IN_TEMPLATES contains 8 templates`() {
            assertEquals(8, PromptTemplate.BUILT_IN_TEMPLATES.size)
        }
        
        @Test
        @DisplayName("CODE_REVIEW template is configured correctly")
        fun `CODE_REVIEW template is configured correctly`() {
            val template = PromptTemplate.CODE_REVIEW
            
            assertEquals("code_review", template.id)
            assertEquals("Code Review", template.name)
            assertEquals(TemplateCategory.REVIEW, template.category)
        }
        
        @Test
        @DisplayName("EXPLAIN_ERROR has required ERROR variable")
        fun `EXPLAIN_ERROR has required ERROR variable`() {
            val template = PromptTemplate.EXPLAIN_ERROR
            
            assertTrue(template.variables.any { it.name == "ERROR" && it.required })
        }
        
        @Test
        @DisplayName("getById finds correct template")
        fun `getById finds correct template`() {
            val template = PromptTemplate.getById("code_review")
            
            assertNotNull(template)
            assertEquals(PromptTemplate.CODE_REVIEW, template)
        }
        
        @Test
        @DisplayName("getById returns null for unknown ID")
        fun `getById returns null for unknown ID`() {
            val template = PromptTemplate.getById("unknown_template")
            
            assertNull(template)
        }
        
        @Test
        @DisplayName("getByCategory returns correct templates")
        fun `getByCategory returns correct templates`() {
            val reviewTemplates = PromptTemplate.getByCategory(TemplateCategory.REVIEW)
            
            assertTrue(reviewTemplates.isNotEmpty())
            assertTrue(reviewTemplates.all { it.category == TemplateCategory.REVIEW })
        }
    }

    @Nested
    @DisplayName("Variable Substitution")
    inner class VariableSubstitutionTests {
        
        @Test
        @DisplayName("replaces SELECTION placeholder")
        fun `replaces SELECTION placeholder`() {
            val template = PromptTemplate.CODE_REVIEW
            val editorContext = EditorContext.EMPTY.copy(
                selection = "fun test() = 42",
                language = "Kotlin"
            )
            
            val result = template.build(editorContext)
            
            assertTrue(result.contains("fun test() = 42"))
        }
        
        @Test
        @DisplayName("replaces LANGUAGE placeholder")
        fun `replaces LANGUAGE placeholder`() {
            val template = PromptTemplate.CODE_REVIEW
            val editorContext = EditorContext.EMPTY.copy(
                selection = "code",
                language = "TypeScript"
            )
            
            val result = template.build(editorContext)
            
            assertTrue(result.contains("TypeScript"))
        }
        
        @Test
        @DisplayName("replaces custom variables")
        fun `replaces custom variables`() {
            val template = PromptTemplate.EXPLAIN_ERROR
            val editorContext = EditorContext.EMPTY.copy(selection = "code")
            
            val result = template.build(
                editorContext,
                customVariables = mapOf("ERROR" to "NullPointerException")
            )
            
            assertTrue(result.contains("NullPointerException"))
        }
        
        @Test
        @DisplayName("uses default values when variable not provided")
        fun `uses default values when variable not provided`() {
            val template = PromptTemplate.COMPARE_APPROACHES
            val editorContext = EditorContext.EMPTY.copy(selection = "code")
            
            val result = template.build(
                editorContext,
                customVariables = mapOf("TASK" to "sorting")
            )
            
            // Default is "2" alternatives
            assertTrue(result.contains("2"))
        }
        
        @Test
        @DisplayName("replaces PROJECT placeholder")
        fun `replaces PROJECT placeholder`() {
            val template = PromptTemplate.ASK_QUESTION
            val editorContext = EditorContext.EMPTY.copy(selection = "code")
            val projectContext = ProjectContext(
                name = "MyProject",
                basePath = "/path",
                projectType = ProjectType.GRADLE,
                frameworkHints = emptyList(),
                keyFiles = emptyList()
            )
            
            val result = template.build(
                editorContext,
                projectContext = projectContext,
                customVariables = mapOf("QUESTION" to "What does this do?")
            )
            
            assertTrue(result.contains("MyProject"))
        }
    }

    @Nested
    @DisplayName("Conditional Blocks")
    inner class ConditionalBlocksTests {
        
        @Test
        @DisplayName("includes conditional when variable is present")
        fun `includes conditional when variable is present`() {
            val template = PromptTemplate.WRITE_FUNCTION
            val editorContext = EditorContext.EMPTY.copy(language = "Kotlin")
            
            val result = template.build(
                editorContext,
                customVariables = mapOf(
                    "DESCRIPTION" to "Calculate factorial",
                    "SIGNATURE" to "fun factorial(n: Int): Int"
                )
            )
            
            assertTrue(result.contains("fun factorial(n: Int)"))
        }
        
        @Test
        @DisplayName("excludes conditional when variable is empty")
        fun `excludes conditional when variable is empty`() {
            val template = PromptTemplate.WRITE_FUNCTION
            val editorContext = EditorContext.EMPTY.copy(language = "Kotlin")
            
            val result = template.build(
                editorContext,
                customVariables = mapOf("DESCRIPTION" to "Calculate factorial")
            )
            
            assertFalse(result.contains("Use this signature"))
        }
    }

    @Nested
    @DisplayName("getUnresolvedVariables")
    inner class GetUnresolvedVariablesTests {
        
        @Test
        @DisplayName("returns required variables not in context")
        fun `returns required variables not in context`() {
            val template = PromptTemplate.EXPLAIN_ERROR
            
            val unresolved = template.getUnresolvedVariables()
            
            assertTrue(unresolved.any { it.name == "ERROR" })
        }
        
        @Test
        @DisplayName("does not return context variables as unresolved")
        fun `does not return context variables as unresolved`() {
            val template = PromptTemplate.CODE_REVIEW
            val editorContext = EditorContext.EMPTY.copy(selection = "code")
            
            val unresolved = template.getUnresolvedVariables(editorContext)
            
            assertFalse(unresolved.any { it.name == "SELECTION" })
        }
    }

    @Nested
    @DisplayName("TemplateCategory")
    inner class TemplateCategoryTests {
        
        @Test
        @DisplayName("has all expected categories")
        fun `has all expected categories`() {
            val categories = TemplateCategory.entries
            
            assertTrue(categories.any { it == TemplateCategory.REVIEW })
            assertTrue(categories.any { it == TemplateCategory.DEBUG })
            assertTrue(categories.any { it == TemplateCategory.GENERATE })
            assertTrue(categories.any { it == TemplateCategory.TRANSFORM })
            assertTrue(categories.any { it == TemplateCategory.DOCUMENT })
            assertTrue(categories.any { it == TemplateCategory.LEARN })
        }
        
        @Test
        @DisplayName("displayName is human readable")
        fun `displayName is human readable`() {
            assertEquals("Code Review", TemplateCategory.REVIEW.displayName)
            assertEquals("Debugging", TemplateCategory.DEBUG.displayName)
        }
        
        @Test
        @DisplayName("fromString finds category by name")
        fun `fromString finds category by name`() {
            assertEquals(TemplateCategory.REVIEW, TemplateCategory.fromString("review"))
            assertEquals(TemplateCategory.REVIEW, TemplateCategory.fromString("REVIEW"))
            assertEquals(TemplateCategory.REVIEW, TemplateCategory.fromString("Code Review"))
        }
        
        @Test
        @DisplayName("fromString returns null for unknown")
        fun `fromString returns null for unknown`() {
            assertNull(TemplateCategory.fromString("unknown"))
        }
    }

    @Nested
    @DisplayName("TemplateVariable")
    inner class TemplateVariableTests {
        
        @Test
        @DisplayName("has correct default values")
        fun `has correct default values`() {
            val variable = TemplateVariable("TEST", "Description")
            
            assertTrue(variable.required)
            assertNull(variable.defaultValue)
        }
        
        @Test
        @DisplayName("stores optional parameters")
        fun `stores optional parameters`() {
            val variable = TemplateVariable(
                name = "TEST",
                description = "Description",
                required = false,
                defaultValue = "default"
            )
            
            assertFalse(variable.required)
            assertEquals("default", variable.defaultValue)
        }
    }
}
