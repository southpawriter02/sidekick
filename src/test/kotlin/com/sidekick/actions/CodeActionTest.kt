// =============================================================================
// CodeActionTest.kt
// =============================================================================
// Unit tests for CodeAction and related classes.
// =============================================================================

package com.sidekick.actions

import com.sidekick.context.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for CodeAction.
 */
class CodeActionTest {

    @Nested
    @DisplayName("Built-in Actions")
    inner class BuiltInActionsTests {
        
        @Test
        @DisplayName("EXPLAIN action is configured correctly")
        fun `EXPLAIN action is configured correctly`() {
            val action = CodeAction.EXPLAIN
            
            assertEquals("explain", action.id)
            assertEquals("Explain", action.name)
            assertTrue(action.requiresSelection)
            assertFalse(action.requiresSymbol)
        }
        
        @Test
        @DisplayName("REFACTOR action is configured correctly")
        fun `REFACTOR action is configured correctly`() {
            val action = CodeAction.REFACTOR
            
            assertEquals("refactor", action.id)
            assertEquals("Refactor", action.name)
            assertTrue(action.requiresSelection)
        }
        
        @Test
        @DisplayName("GENERATE_TESTS action is configured correctly")
        fun `GENERATE_TESTS action is configured correctly`() {
            val action = CodeAction.GENERATE_TESTS
            
            assertEquals("generate_tests", action.id)
            assertEquals("Generate Tests", action.name)
            assertTrue(action.requiresSelection)
        }
        
        @Test
        @DisplayName("DOCUMENT action is configured correctly")
        fun `DOCUMENT action is configured correctly`() {
            val action = CodeAction.DOCUMENT
            
            assertEquals("document", action.id)
            assertTrue(action.requiresSelection)
        }
        
        @Test
        @DisplayName("FIND_BUGS action is configured correctly")
        fun `FIND_BUGS action is configured correctly`() {
            val action = CodeAction.FIND_BUGS
            
            assertEquals("find_bugs", action.id)
            assertTrue(action.requiresSelection)
        }
        
        @Test
        @DisplayName("OPTIMIZE action is configured correctly")
        fun `OPTIMIZE action is configured correctly`() {
            val action = CodeAction.OPTIMIZE
            
            assertEquals("optimize", action.id)
            assertTrue(action.requiresSelection)
        }
        
        @Test
        @DisplayName("EXPLAIN_SYMBOL action requires symbol")
        fun `EXPLAIN_SYMBOL action requires symbol`() {
            val action = CodeAction.EXPLAIN_SYMBOL
            
            assertEquals("explain_symbol", action.id)
            assertFalse(action.requiresSelection)
            assertTrue(action.requiresSymbol)
        }
        
        @Test
        @DisplayName("CONVERT action is configured correctly")
        fun `CONVERT action is configured correctly`() {
            val action = CodeAction.CONVERT
            
            assertEquals("convert", action.id)
            assertTrue(action.requiresSelection)
        }
        
        @Test
        @DisplayName("BUILT_IN_ACTIONS contains 8 actions")
        fun `BUILT_IN_ACTIONS contains 8 actions`() {
            assertEquals(8, CodeAction.BUILT_IN_ACTIONS.size)
        }
        
        @Test
        @DisplayName("getById finds correct action")
        fun `getById finds correct action`() {
            val action = CodeAction.getById("explain")
            
            assertNotNull(action)
            assertEquals(CodeAction.EXPLAIN, action)
        }
        
        @Test
        @DisplayName("getById returns null for unknown ID")
        fun `getById returns null for unknown ID`() {
            val action = CodeAction.getById("unknown_action")
            
            assertNull(action)
        }
    }

    @Nested
    @DisplayName("canExecute")
    inner class CanExecuteTests {
        
        @Test
        @DisplayName("returns false when selection required but none present")
        fun `returns false when selection required but none present`() {
            val action = CodeAction.EXPLAIN
            val editorContext = EditorContext.EMPTY
            
            assertFalse(action.canExecute(editorContext))
        }
        
        @Test
        @DisplayName("returns true when selection required and present")
        fun `returns true when selection required and present`() {
            val action = CodeAction.EXPLAIN
            val editorContext = EditorContext.EMPTY.copy(selection = "some code")
            
            assertTrue(action.canExecute(editorContext))
        }
        
        @Test
        @DisplayName("returns false when symbol required but none present")
        fun `returns false when symbol required but none present`() {
            val action = CodeAction.EXPLAIN_SYMBOL
            val symbolContext = SymbolContext.EMPTY
            
            assertFalse(action.canExecute(symbolContext = symbolContext))
        }
        
        @Test
        @DisplayName("returns true when symbol required and present")
        fun `returns true when symbol required and present`() {
            val action = CodeAction.EXPLAIN_SYMBOL
            val symbolContext = createValidSymbolContext()
            
            assertTrue(action.canExecute(symbolContext = symbolContext))
        }
    }

    @Nested
    @DisplayName("getDisabledReason")
    inner class GetDisabledReasonTests {
        
        @Test
        @DisplayName("returns selection message when selection required")
        fun `returns selection message when selection required`() {
            val action = CodeAction.EXPLAIN
            val reason = action.getDisabledReason()
            
            assertNotNull(reason)
            assertTrue(reason!!.contains("Select"))
        }
        
        @Test
        @DisplayName("returns symbol message when symbol required")
        fun `returns symbol message when symbol required`() {
            val action = CodeAction.EXPLAIN_SYMBOL
            val reason = action.getDisabledReason()
            
            assertNotNull(reason)
            assertTrue(reason!!.contains("cursor") || reason.contains("symbol"))
        }
        
        @Test
        @DisplayName("returns null when action can execute")
        fun `returns null when action can execute`() {
            val action = CodeAction.EXPLAIN
            val editorContext = EditorContext.EMPTY.copy(selection = "code")
            val reason = action.getDisabledReason(editorContext)
            
            assertNull(reason)
        }
    }

    @Nested
    @DisplayName("buildPrompt")
    inner class BuildPromptTests {
        
        @Test
        @DisplayName("replaces CODE placeholder with selection")
        fun `replaces CODE placeholder with selection`() {
            val action = CodeAction.EXPLAIN
            val editorContext = EditorContext.EMPTY.copy(
                selection = "fun hello() = println(\"Hello\")"
            )
            
            val prompt = action.buildPrompt(editorContext)
            
            assertTrue(prompt.contains("fun hello()"))
        }
        
        @Test
        @DisplayName("replaces LANGUAGE placeholder")
        fun `replaces LANGUAGE placeholder`() {
            // Use DOCUMENT action which has LANGUAGE placeholder
            val action = CodeAction.DOCUMENT
            val editorContext = EditorContext.EMPTY.copy(
                selection = "code",
                language = "Kotlin"
            )
            
            val prompt = action.buildPrompt(editorContext)
            
            assertTrue(prompt.contains("Kotlin") || prompt.contains("kotlin"))
        }
        
        @Test
        @DisplayName("replaces FRAMEWORKS placeholder")
        fun `replaces FRAMEWORKS placeholder`() {
            val action = CodeAction.GENERATE_TESTS
            val editorContext = EditorContext.EMPTY.copy(selection = "code")
            val projectContext = ProjectContext(
                name = "Test",
                basePath = "/path",
                projectType = ProjectType.GRADLE,
                frameworkHints = listOf("JUnit", "Kotest"),
                keyFiles = emptyList()
            )
            
            val prompt = action.buildPrompt(editorContext, projectContext = projectContext)
            
            assertTrue(prompt.contains("JUnit") || prompt.contains("Kotest"))
        }
        
        @Test
        @DisplayName("replaces symbol placeholders")
        fun `replaces symbol placeholders`() {
            val action = CodeAction.EXPLAIN_SYMBOL
            val symbolContext = createValidSymbolContext()
            
            val prompt = action.buildPrompt(symbolContext = symbolContext)
            
            assertTrue(prompt.contains("testMethod"))
            assertTrue(prompt.contains("method"))
        }
    }

    // -------------------------------------------------------------------------
    // Test Helpers
    // -------------------------------------------------------------------------
    
    private fun createValidSymbolContext(): SymbolContext {
        return SymbolContext(
            name = "testMethod",
            kind = SymbolKind.METHOD,
            signature = "fun testMethod(): String",
            containingClass = "TestClass",
            documentation = null,
            definition = "fun testMethod(): String { return \"test\" }"
        )
    }
}
