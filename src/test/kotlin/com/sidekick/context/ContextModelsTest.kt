// =============================================================================
// ContextModelsTest.kt
// =============================================================================
// Unit tests for context data models.
//
// Tests cover:
// - EditorContext data class and computed properties
// - SymbolContext and SymbolKind enum
// - ProjectContext and ProjectType enum
// =============================================================================

package com.sidekick.context

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for context data models.
 */
class ContextModelsTest {

    @Nested
    @DisplayName("EditorContext")
    inner class EditorContextTests {
        
        @Test
        @DisplayName("EMPTY has expected default values")
        fun `EMPTY has expected default values`() {
            val empty = EditorContext.EMPTY
            
            assertNull(empty.file)
            assertEquals("text", empty.language)
            assertEquals("", empty.filePath)
            assertEquals("", empty.fileName)
            assertNull(empty.selection)
            assertNull(empty.selectionRange)
            assertEquals(0, empty.cursorLine)
            assertEquals(0, empty.cursorColumn)
            assertEquals("", empty.fileContent)
            assertNull(empty.visibleRange)
        }
        
        @Test
        @DisplayName("hasSelection returns false for EMPTY")
        fun `hasSelection returns false for EMPTY`() {
            assertFalse(EditorContext.EMPTY.hasSelection)
        }
        
        @Test
        @DisplayName("hasSelection returns true when selection exists")
        fun `hasSelection returns true when selection exists`() {
            val context = EditorContext.EMPTY.copy(selection = "some text")
            assertTrue(context.hasSelection)
        }
        
        @Test
        @DisplayName("hasSelection returns false for empty string selection")
        fun `hasSelection returns false for empty string selection`() {
            val context = EditorContext.EMPTY.copy(selection = "")
            assertFalse(context.hasSelection)
        }
        
        @Test
        @DisplayName("hasFile returns false for EMPTY")
        fun `hasFile returns false for EMPTY`() {
            assertFalse(EditorContext.EMPTY.hasFile)
        }
        
        @Test
        @DisplayName("fileExtension returns empty for EMPTY")
        fun `fileExtension returns empty for EMPTY`() {
            assertEquals("", EditorContext.EMPTY.fileExtension)
        }
        
        @Test
        @DisplayName("lineCount returns 0 for empty content")
        fun `lineCount returns 0 for empty content`() {
            assertEquals(0, EditorContext.EMPTY.lineCount)
        }
        
        @Test
        @DisplayName("lineCount returns correct count for multi-line content")
        fun `lineCount returns correct count for multi-line content`() {
            val context = EditorContext.EMPTY.copy(
                fileContent = "line1\nline2\nline3"
            )
            assertEquals(3, context.lineCount)
        }
        
        @Test
        @DisplayName("getSurroundingText returns null for EMPTY")
        fun `getSurroundingText returns null for EMPTY`() {
            assertNull(EditorContext.EMPTY.getSurroundingText())
        }
        
        @Test
        @DisplayName("toSummary produces readable output")
        fun `toSummary produces readable output`() {
            val context = EditorContext.EMPTY.copy(
                fileName = "Test.kt",
                language = "Kotlin",
                cursorLine = 10
            )
            val summary = context.toSummary()
            
            assertTrue(summary.contains("Test.kt"))
            assertTrue(summary.contains("Kotlin"))
            assertTrue(summary.contains("10"))
        }
    }

    @Nested
    @DisplayName("SymbolContext")
    inner class SymbolContextTests {
        
        @Test
        @DisplayName("EMPTY has expected default values")
        fun `EMPTY has expected default values`() {
            val empty = SymbolContext.EMPTY
            
            assertEquals("", empty.name)
            assertEquals(SymbolKind.UNKNOWN, empty.kind)
            assertNull(empty.signature)
            assertNull(empty.containingClass)
            assertNull(empty.documentation)
            assertEquals("", empty.definition)
        }
        
        @Test
        @DisplayName("isValid returns false for EMPTY")
        fun `isValid returns false for EMPTY`() {
            assertFalse(SymbolContext.EMPTY.isValid)
        }
        
        @Test
        @DisplayName("isValid returns true for valid symbol")
        fun `isValid returns true for valid symbol`() {
            val context = SymbolContext(
                name = "myMethod",
                kind = SymbolKind.METHOD,
                signature = null,
                containingClass = null,
                documentation = null,
                definition = "fun myMethod() {}"
            )
            assertTrue(context.isValid)
        }
        
        @Test
        @DisplayName("hasDocumentation returns false for null")
        fun `hasDocumentation returns false for null`() {
            assertFalse(SymbolContext.EMPTY.hasDocumentation)
        }
        
        @Test
        @DisplayName("hasDocumentation returns true when present")
        fun `hasDocumentation returns true when present`() {
            val context = SymbolContext.EMPTY.copy(
                documentation = "/** Some docs */"
            )
            assertTrue(context.hasDocumentation)
        }
        
        @Test
        @DisplayName("qualifiedName includes containing class")
        fun `qualifiedName includes containing class`() {
            val context = SymbolContext(
                name = "doSomething",
                kind = SymbolKind.METHOD,
                signature = null,
                containingClass = "MyClass",
                documentation = null,
                definition = ""
            )
            assertEquals("MyClass.doSomething", context.qualifiedName)
        }
        
        @Test
        @DisplayName("qualifiedName is just name when no containing class")
        fun `qualifiedName is just name when no containing class`() {
            val context = SymbolContext.EMPTY.copy(name = "topLevel")
            assertEquals("topLevel", context.qualifiedName)
        }
        
        @Test
        @DisplayName("toSummary produces readable output")
        fun `toSummary produces readable output`() {
            val context = SymbolContext(
                name = "calculate",
                kind = SymbolKind.METHOD,
                signature = null,
                containingClass = "Calculator",
                documentation = null,
                definition = ""
            )
            val summary = context.toSummary()
            
            assertTrue(summary.contains("Method"))
            assertTrue(summary.contains("calculate"))
            assertTrue(summary.contains("Calculator"))
        }
    }

    @Nested
    @DisplayName("SymbolKind")
    inner class SymbolKindTests {
        
        @Test
        @DisplayName("has all expected values")
        fun `has all expected values`() {
            val kinds = SymbolKind.values()
            
            assertTrue(SymbolKind.CLASS in kinds)
            assertTrue(SymbolKind.INTERFACE in kinds)
            assertTrue(SymbolKind.METHOD in kinds)
            assertTrue(SymbolKind.PROPERTY in kinds)
            assertTrue(SymbolKind.FIELD in kinds)
            assertTrue(SymbolKind.VARIABLE in kinds)
            assertTrue(SymbolKind.UNKNOWN in kinds)
        }
        
        @Test
        @DisplayName("displayName is human readable")
        fun `displayName is human readable`() {
            assertEquals("Class", SymbolKind.CLASS.displayName)
            assertEquals("Method", SymbolKind.METHOD.displayName)
            assertEquals("Unknown", SymbolKind.UNKNOWN.displayName)
        }
        
        @Test
        @DisplayName("fromTypeName finds matching kind")
        fun `fromTypeName finds matching kind`() {
            assertEquals(SymbolKind.CLASS, SymbolKind.fromTypeName("ClassDeclaration"))
            assertEquals(SymbolKind.METHOD, SymbolKind.fromTypeName("MethodDefinition"))
        }
        
        @Test
        @DisplayName("fromTypeName returns UNKNOWN for no match")
        fun `fromTypeName returns UNKNOWN for no match`() {
            assertEquals(SymbolKind.UNKNOWN, SymbolKind.fromTypeName("SomethingRandom"))
        }
    }

    @Nested
    @DisplayName("ProjectContext")
    inner class ProjectContextTests {
        
        @Test
        @DisplayName("EMPTY has expected default values")
        fun `EMPTY has expected default values`() {
            val empty = ProjectContext.EMPTY
            
            assertEquals("", empty.name)
            assertEquals("", empty.basePath)
            assertEquals(ProjectType.UNKNOWN, empty.projectType)
            assertTrue(empty.frameworkHints.isEmpty())
            assertTrue(empty.keyFiles.isEmpty())
        }
        
        @Test
        @DisplayName("isValid returns false for EMPTY")
        fun `isValid returns false for EMPTY`() {
            assertFalse(ProjectContext.EMPTY.isValid)
        }
        
        @Test
        @DisplayName("isValid returns true for valid project")
        fun `isValid returns true for valid project`() {
            val context = ProjectContext(
                name = "MyProject",
                basePath = "/path/to/project",
                projectType = ProjectType.DOTNET,
                frameworkHints = emptyList(),
                keyFiles = emptyList()
            )
            assertTrue(context.isValid)
        }
        
        @Test
        @DisplayName("hasKnownType returns false for UNKNOWN")
        fun `hasKnownType returns false for UNKNOWN`() {
            assertFalse(ProjectContext.EMPTY.hasKnownType)
        }
        
        @Test
        @DisplayName("hasKnownType returns true for known type")
        fun `hasKnownType returns true for known type`() {
            val context = ProjectContext.EMPTY.copy(projectType = ProjectType.GRADLE)
            assertTrue(context.hasKnownType)
        }
        
        @Test
        @DisplayName("primaryLanguage returns correct language")
        fun `primaryLanguage returns correct language`() {
            assertEquals("C#", ProjectContext.EMPTY.copy(projectType = ProjectType.DOTNET).primaryLanguage)
            assertEquals("Kotlin/Java", ProjectContext.EMPTY.copy(projectType = ProjectType.GRADLE).primaryLanguage)
            assertEquals("TypeScript/JavaScript", ProjectContext.EMPTY.copy(projectType = ProjectType.NPM).primaryLanguage)
            assertEquals("Python", ProjectContext.EMPTY.copy(projectType = ProjectType.PYTHON).primaryLanguage)
        }
        
        @Test
        @DisplayName("toPromptSummary produces readable output")
        fun `toPromptSummary produces readable output`() {
            val context = ProjectContext(
                name = "MyApp",
                basePath = "/path",
                projectType = ProjectType.DOTNET,
                frameworkHints = listOf("ASP.NET Core", "Entity Framework"),
                keyFiles = emptyList()
            )
            val summary = context.toPromptSummary()
            
            assertTrue(summary.contains("MyApp"))
            assertTrue(summary.contains("C#"))
            assertTrue(summary.contains("ASP.NET Core"))
        }
    }

    @Nested
    @DisplayName("ProjectType")
    inner class ProjectTypeTests {
        
        @Test
        @DisplayName("has all expected values")
        fun `has all expected values`() {
            val types = ProjectType.values()
            
            assertTrue(ProjectType.DOTNET in types)
            assertTrue(ProjectType.GRADLE in types)
            assertTrue(ProjectType.NPM in types)
            assertTrue(ProjectType.PYTHON in types)
            assertTrue(ProjectType.UNITY in types)
            assertTrue(ProjectType.UNKNOWN in types)
        }
        
        @Test
        @DisplayName("displayName is human readable")
        fun `displayName is human readable`() {
            assertEquals(".NET", ProjectType.DOTNET.displayName)
            assertEquals("Gradle", ProjectType.GRADLE.displayName)
            assertEquals("Node.js", ProjectType.NPM.displayName)
        }
        
        @Test
        @DisplayName("FILE_INDICATORS contains expected patterns")
        fun `FILE_INDICATORS contains expected patterns`() {
            val dotnetIndicators = ProjectType.FILE_INDICATORS[ProjectType.DOTNET]!!
            assertTrue(".csproj" in dotnetIndicators)
            assertTrue(".sln" in dotnetIndicators)
            
            val gradleIndicators = ProjectType.FILE_INDICATORS[ProjectType.GRADLE]!!
            assertTrue("build.gradle" in gradleIndicators)
            assertTrue("build.gradle.kts" in gradleIndicators)
        }
        
        @Test
        @DisplayName("SKIP_DIRECTORIES contains common excludes")
        fun `SKIP_DIRECTORIES contains common excludes`() {
            assertTrue("node_modules" in ProjectType.SKIP_DIRECTORIES)
            assertTrue(".git" in ProjectType.SKIP_DIRECTORIES)
            assertTrue("bin" in ProjectType.SKIP_DIRECTORIES)
            assertTrue("obj" in ProjectType.SKIP_DIRECTORIES)
        }
    }
}
