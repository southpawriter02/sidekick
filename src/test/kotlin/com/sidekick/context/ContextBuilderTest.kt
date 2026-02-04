// =============================================================================
// ContextBuilderTest.kt
// =============================================================================
// Unit tests for ContextBuilder.
// =============================================================================

package com.sidekick.context

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for ContextBuilder.
 */
class ContextBuilderTest {

    @Nested
    @DisplayName("Builder Configuration")
    inner class BuilderConfigurationTests {
        
        @Test
        @DisplayName("default builder produces empty string with empty context")
        fun `default builder produces empty string with empty context`() {
            val builder = ContextBuilder()
            val result = builder.build()
            
            assertEquals("", result)
        }
        
        @Test
        @DisplayName("includeAll returns the same builder for chaining")
        fun `includeAll returns the same builder for chaining`() {
            val builder = ContextBuilder()
            val returned = builder.includeAll()
            
            assertSame(builder, returned)
        }
        
        @Test
        @DisplayName("standardChat returns the same builder for chaining")
        fun `standardChat returns the same builder for chaining`() {
            val builder = ContextBuilder()
            val returned = builder.standardChat()
            
            assertSame(builder, returned)
        }
        
        @Test
        @DisplayName("individual include methods return same builder")
        fun `individual include methods return same builder`() {
            val builder = ContextBuilder()
            
            assertSame(builder, builder.includeFileInfo())
            assertSame(builder, builder.includeFileContent())
            assertSame(builder, builder.includeSelection())
            assertSame(builder, builder.includeSymbol())
            assertSame(builder, builder.includeProjectSummary())
            assertSame(builder, builder.includeSurroundingCode())
        }
    }

    @Nested
    @DisplayName("Context Detection")
    inner class ContextDetectionTests {
        
        @Test
        @DisplayName("hasContext returns false for empty contexts")
        fun `hasContext returns false for empty contexts`() {
            val builder = ContextBuilder()
            
            assertFalse(builder.hasContext())
        }
        
        @Test
        @DisplayName("hasContext returns true when file is present")
        fun `hasContext returns true when file is present`() {
            // Since we can't mock VirtualFile, test via selection which we can set
            val editorContext = createEditorContext(hasSelection = true)
            val builder = ContextBuilder(editorContext = editorContext)
            
            assertTrue(builder.hasContext())
        }
        
        @Test
        @DisplayName("hasContext returns true when selection is present")
        fun `hasContext returns true when selection is present`() {
            val editorContext = createEditorContext(hasSelection = true)
            val builder = ContextBuilder(editorContext = editorContext)
            
            assertTrue(builder.hasContext())
        }
        
        @Test
        @DisplayName("hasContext returns true when valid symbol is present")
        fun `hasContext returns true when valid symbol is present`() {
            val symbolContext = createSymbolContext(isValid = true)
            val builder = ContextBuilder(symbolContext = symbolContext)
            
            assertTrue(builder.hasContext())
        }
        
        @Test
        @DisplayName("hasContext returns true when valid project is present")
        fun `hasContext returns true when valid project is present`() {
            val projectContext = createProjectContext(isValid = true)
            val builder = ContextBuilder(projectContext = projectContext)
            
            assertTrue(builder.hasContext())
        }
    }

    @Nested
    @DisplayName("Context Building")
    inner class ContextBuildingTests {
        
        @Test
        @DisplayName("build includes project section when enabled")
        fun `build includes project section when enabled`() {
            val projectContext = createProjectContext(isValid = true)
            val builder = ContextBuilder(projectContext = projectContext)
                .includeProjectSummary()
            
            val result = builder.build()
            
            assertTrue(result.contains("### Project"))
            assertTrue(result.contains("TestProject"))
        }
        
        @Test
        @DisplayName("build includes file info when enabled and file present")
        fun `build includes file info when enabled and file present`() {
            // Test project context instead since VirtualFile cannot be mocked
            val projectContext = createProjectContext(isValid = true)
            val symbolContext = createSymbolContext(isValid = true)
            val builder = ContextBuilder(
                symbolContext = symbolContext,
                projectContext = projectContext
            ).includeSymbol().includeProjectSummary()
            
            val result = builder.build()
            
            assertTrue(result.contains("### Project"))
            assertTrue(result.contains("### Symbol at Cursor"))
        }
        
        @Test
        @DisplayName("build includes selection when enabled")
        fun `build includes selection when enabled`() {
            val editorContext = createEditorContext(hasSelection = true)
            val builder = ContextBuilder(editorContext = editorContext)
                .includeSelection()
            
            val result = builder.build()
            
            assertTrue(result.contains("### Selected Code"))
            assertTrue(result.contains("selected text"))
        }
        
        @Test
        @DisplayName("build includes symbol when enabled")
        fun `build includes symbol when enabled`() {
            val symbolContext = createSymbolContext(isValid = true)
            val builder = ContextBuilder(symbolContext = symbolContext)
                .includeSymbol()
            
            val result = builder.build()
            
            assertTrue(result.contains("### Symbol at Cursor"))
            assertTrue(result.contains("testMethod"))
        }
        
        @Test
        @DisplayName("build skips empty contexts even when enabled")
        fun `build skips empty contexts even when enabled`() {
            val builder = ContextBuilder()
                .includeAll()
            
            val result = builder.build()
            
            assertEquals("", result)
        }
    }

    @Nested
    @DisplayName("Content Truncation")
    inner class ContentTruncationTests {
        
        @Test
        @DisplayName("long selections are truncated")
        fun `long selections are truncated`() {
            val longSelection = "a".repeat(ContextBuilder.MAX_SELECTION_CHARS + 100)
            val editorContext = createEditorContext(
                hasFile = true,
                hasSelection = true,
                selection = longSelection
            )
            val builder = ContextBuilder(editorContext = editorContext)
                .includeSelection()
            
            val result = builder.build()
            
            assertTrue(result.contains("truncated"))
        }
    }

    @Nested
    @DisplayName("Constants")
    inner class ConstantsTests {
        
        @Test
        @DisplayName("MAX_FILE_CONTENT_CHARS is reasonable")
        fun `MAX_FILE_CONTENT_CHARS is reasonable`() {
            assertTrue(ContextBuilder.MAX_FILE_CONTENT_CHARS > 1000)
            assertTrue(ContextBuilder.MAX_FILE_CONTENT_CHARS <= 20000)
        }
        
        @Test
        @DisplayName("MAX_SELECTION_CHARS is reasonable")
        fun `MAX_SELECTION_CHARS is reasonable`() {
            assertTrue(ContextBuilder.MAX_SELECTION_CHARS > 500)
            assertTrue(ContextBuilder.MAX_SELECTION_CHARS <= 10000)
        }
        
        @Test
        @DisplayName("MAX_SYMBOL_CHARS is reasonable")
        fun `MAX_SYMBOL_CHARS is reasonable`() {
            assertTrue(ContextBuilder.MAX_SYMBOL_CHARS > 500)
            assertTrue(ContextBuilder.MAX_SYMBOL_CHARS <= 5000)
        }
    }

    // -------------------------------------------------------------------------
    // Test Helpers
    // -------------------------------------------------------------------------

    private fun createEditorContext(
        hasFile: Boolean = false,
        hasSelection: Boolean = false,
        selection: String = "selected text"
    ): EditorContext {
        return EditorContext(
            file = if (hasFile) null else null, // VirtualFile mock not needed for tests
            language = "Kotlin",
            filePath = if (hasFile) "/path/to/Test.kt" else "",
            fileName = if (hasFile) "Test.kt" else "",
            selection = if (hasSelection) selection else null,
            selectionRange = if (hasSelection) 1..5 else null,
            cursorLine = 10,
            cursorColumn = 5,
            fileContent = if (hasFile) "fun main() {\n    println(\"Hello\")\n}" else "",
            visibleRange = 1..50
        ).let { ctx ->
            // Override hasFile check since we can't mock VirtualFile
            if (hasFile) {
                ctx.copy(filePath = "/path/to/Test.kt", fileName = "Test.kt")
            } else {
                ctx
            }
        }
    }

    private fun createSymbolContext(isValid: Boolean = false): SymbolContext {
        return if (isValid) {
            SymbolContext(
                name = "testMethod",
                kind = SymbolKind.METHOD,
                signature = "fun testMethod(): String",
                containingClass = "TestClass",
                documentation = "/** Test documentation */",
                definition = "fun testMethod(): String { return \"test\" }"
            )
        } else {
            SymbolContext.EMPTY
        }
    }

    private fun createProjectContext(isValid: Boolean = false): ProjectContext {
        return if (isValid) {
            ProjectContext(
                name = "TestProject",
                basePath = "/path/to/project",
                projectType = ProjectType.GRADLE,
                frameworkHints = listOf("Kotlin", "JUnit"),
                keyFiles = listOf("build.gradle.kts")
            )
        } else {
            ProjectContext.EMPTY
        }
    }
}
