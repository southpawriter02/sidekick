# Sidekick v0.2.x – Context Awareness Phase

> **Phase Goal:** Rich AI chat experience with context awareness  
> **Building On:** v0.1.x Foundation (Ollama client, Chat UI, Settings)

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.2.1 | Editor Context Service | Current file, selection, and symbol context |
| v0.2.2 | Context Injection | Automatic context in chat prompts |
| v0.2.3 | Code Actions | "Explain", "Refactor", "Test" intentions |
| v0.2.4 | Prompt Templates | Variable substitution and template system |
| v0.2.5 | Chat History | SQLite persistence and search |

---

## v0.2.1 — Editor Context Service

### v0.2.1a — EditorContext Data Model

**Goal:** Define data classes for representing editor context.

#### EditorContext.kt

```kotlin
package com.sidekick.context

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Represents the current context of the user's editor.
 *
 * @property file The current file being edited
 * @property language The programming language (e.g., "csharp", "kotlin")
 * @property filePath Absolute path to the file
 * @property fileName Base name of the file
 * @property selection Currently selected text (if any)
 * @property selectionRange Line range of selection
 * @property cursorLine Current cursor line number (1-indexed)
 * @property cursorColumn Current cursor column (1-indexed)
 * @property fileContent Full content of the file
 * @property visibleRange Lines currently visible in editor viewport
 */
data class EditorContext(
    val file: VirtualFile?,
    val language: String,
    val filePath: String,
    val fileName: String,
    val selection: String?,
    val selectionRange: IntRange?,
    val cursorLine: Int,
    val cursorColumn: Int,
    val fileContent: String,
    val visibleRange: IntRange?
) {
    companion object {
        val EMPTY = EditorContext(
            file = null,
            language = "text",
            filePath = "",
            fileName = "",
            selection = null,
            selectionRange = null,
            cursorLine = 0,
            cursorColumn = 0,
            fileContent = "",
            visibleRange = null
        )
    }
    
    val hasSelection: Boolean get() = !selection.isNullOrEmpty()
    val hasFile: Boolean get() = file != null
}
```

#### SymbolContext.kt

```kotlin
package com.sidekick.context

/**
 * Represents context about the symbol at the cursor.
 *
 * @property name Symbol name (function, class, variable)
 * @property kind Type of symbol (function, class, property, etc.)
 * @property signature Full signature if applicable
 * @property containingClass Parent class if applicable
 * @property documentation Existing documentation if present
 * @property definition Full definition text
 */
data class SymbolContext(
    val name: String,
    val kind: SymbolKind,
    val signature: String?,
    val containingClass: String?,
    val documentation: String?,
    val definition: String
) {
    companion object {
        val EMPTY = SymbolContext(
            name = "",
            kind = SymbolKind.UNKNOWN,
            signature = null,
            containingClass = null,
            documentation = null,
            definition = ""
        )
    }
}

/**
 * Types of code symbols.
 */
enum class SymbolKind {
    CLASS,
    INTERFACE,
    STRUCT,
    ENUM,
    FUNCTION,
    METHOD,
    PROPERTY,
    FIELD,
    VARIABLE,
    PARAMETER,
    NAMESPACE,
    UNKNOWN
}
```

#### ProjectContext.kt

```kotlin
package com.sidekick.context

/**
 * Summary of the project structure for context.
 *
 * @property name Project name
 * @property basePath Project root directory
 * @property projectType Type of project (dotnet, gradle, npm, etc.)
 * @property frameworkHints Detected frameworks (ASP.NET, Unity, etc.)
 * @property keyFiles Important files like .csproj, package.json
 */
data class ProjectContext(
    val name: String,
    val basePath: String,
    val projectType: ProjectType,
    val frameworkHints: List<String>,
    val keyFiles: List<String>
) {
    companion object {
        val EMPTY = ProjectContext(
            name = "",
            basePath = "",
            projectType = ProjectType.UNKNOWN,
            frameworkHints = emptyList(),
            keyFiles = emptyList()
        )
    }
}

/**
 * Types of projects.
 */
enum class ProjectType {
    DOTNET,      // .NET / C# projects
    GRADLE,      // Gradle/Kotlin/Java projects
    NPM,         // Node.js projects
    PYTHON,      // Python projects
    UNITY,       // Unity game projects
    UNKNOWN
}
```

#### Acceptance Criteria

- [ ] EditorContext captures file, selection, and cursor info
- [ ] SymbolContext captures symbol metadata
- [ ] ProjectContext captures project type information

---

### v0.2.1b — EditorContextService

**Goal:** Service to extract context from the current editor state.

#### EditorContextService.kt

```kotlin
package com.sidekick.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

@Service(Service.Level.PROJECT)
class EditorContextService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(EditorContextService::class.java)
        
        fun getInstance(project: Project): EditorContextService {
            return project.getService(EditorContextService::class.java)
        }
    }
    
    /**
     * Gets the current editor context.
     */
    fun getCurrentContext(): EditorContext {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return EditorContext.EMPTY
        
        return getContextFromEditor(editor)
    }
    
    /**
     * Gets context from a specific editor.
     */
    fun getContextFromEditor(editor: Editor): EditorContext {
        val document = editor.document
        val file = com.intellij.openapi.fileEditor.FileDocumentManager
            .getInstance()
            .getFile(document)
        
        val psiFile = PsiDocumentManager.getInstance(project)
            .getPsiFile(document)
        
        val selectionModel = editor.selectionModel
        val selection = if (selectionModel.hasSelection()) {
            selectionModel.selectedText
        } else null
        
        val selectionRange = if (selectionModel.hasSelection()) {
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            startLine..endLine
        } else null
        
        val caretModel = editor.caretModel
        val cursorLine = document.getLineNumber(caretModel.offset) + 1
        val cursorColumn = caretModel.logicalPosition.column + 1
        
        // Visible range
        val visibleArea = editor.scrollingModel.visibleArea
        val startVisibleLine = editor.xyToLogicalPosition(
            java.awt.Point(0, visibleArea.y)
        ).line + 1
        val endVisibleLine = editor.xyToLogicalPosition(
            java.awt.Point(0, visibleArea.y + visibleArea.height)
        ).line + 1
        
        return EditorContext(
            file = file,
            language = psiFile?.language?.id?.lowercase() ?: "text",
            filePath = file?.path ?: "",
            fileName = file?.name ?: "",
            selection = selection,
            selectionRange = selectionRange,
            cursorLine = cursorLine,
            cursorColumn = cursorColumn,
            fileContent = document.text,
            visibleRange = startVisibleLine..endVisibleLine
        )
    }
    
    /**
     * Gets the symbol at the current cursor position.
     */
    fun getSymbolAtCursor(): SymbolContext {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return SymbolContext.EMPTY
        
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project)
            .getPsiFile(document)
            ?: return SymbolContext.EMPTY
        
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
            ?: return SymbolContext.EMPTY
        
        return extractSymbolContext(element)
    }
    
    /**
     * Extracts symbol context from a PSI element.
     */
    private fun extractSymbolContext(element: com.intellij.psi.PsiElement): SymbolContext {
        // Walk up to find a meaningful parent (method, class, etc.)
        var current: com.intellij.psi.PsiElement? = element
        while (current != null) {
            val context = tryExtractSymbol(current)
            if (context != null) return context
            current = current.parent
        }
        
        return SymbolContext.EMPTY
    }
    
    /**
     * Tries to extract symbol info from a specific element.
     */
    private fun tryExtractSymbol(element: com.intellij.psi.PsiElement): SymbolContext? {
        // This is a simplified implementation - language-specific handlers
        // would provide better extraction in a production system
        
        val text = element.text
        if (text.length > 1000) return null  // Skip large elements
        
        // Use element type name as a heuristic for kind
        val typeName = element.javaClass.simpleName.lowercase()
        val kind = when {
            "class" in typeName -> SymbolKind.CLASS
            "interface" in typeName -> SymbolKind.INTERFACE
            "method" in typeName || "function" in typeName -> SymbolKind.METHOD
            "property" in typeName -> SymbolKind.PROPERTY
            "field" in typeName -> SymbolKind.FIELD
            "variable" in typeName -> SymbolKind.VARIABLE
            "enum" in typeName -> SymbolKind.ENUM
            else -> return null  // Not a recognized symbol type
        }
        
        // Extract name from the element (simplified)
        val name = element.firstChild?.text?.take(100) ?: ""
        if (name.isBlank()) return null
        
        return SymbolContext(
            name = name,
            kind = kind,
            signature = null,
            containingClass = null,
            documentation = null,
            definition = text.take(500)
        )
    }
}
```

#### Acceptance Criteria

- [ ] Service registered at project level
- [ ] getCurrentContext() returns file/selection data
- [ ] getSymbolAtCursor() returns symbol information

---

### v0.2.1c — ProjectContextService

**Goal:** Service to analyze and summarize project structure.

#### ProjectContextService.kt

```kotlin
package com.sidekick.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ProjectContextService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(ProjectContextService::class.java)
        
        // Project type indicators
        private val DOTNET_FILES = setOf("*.csproj", "*.sln", "*.fsproj")
        private val GRADLE_FILES = setOf("build.gradle", "build.gradle.kts", "settings.gradle")
        private val NPM_FILES = setOf("package.json")
        private val PYTHON_FILES = setOf("pyproject.toml", "setup.py", "requirements.txt")
        private val UNITY_FILES = setOf("ProjectSettings", "Assets")
        
        fun getInstance(project: Project): ProjectContextService {
            return project.getService(ProjectContextService::class.java)
        }
    }
    
    // Cached context (refreshed on demand)
    private var cachedContext: ProjectContext? = null
    
    /**
     * Gets the project context, using cache if available.
     */
    fun getProjectContext(): ProjectContext {
        return cachedContext ?: analyzeProject().also { cachedContext = it }
    }
    
    /**
     * Forces a refresh of project context.
     */
    fun refreshContext(): ProjectContext {
        cachedContext = null
        return getProjectContext()
    }
    
    /**
     * Analyzes the project structure.
     */
    private fun analyzeProject(): ProjectContext {
        val basePath = project.basePath ?: return ProjectContext.EMPTY
        val baseDir = VfsUtil.findFileByIoFile(java.io.File(basePath), true)
            ?: return ProjectContext.EMPTY
        
        val keyFiles = mutableListOf<String>()
        val frameworkHints = mutableListOf<String>()
        var projectType = ProjectType.UNKNOWN
        
        // Scan for project indicators
        VfsUtil.iterateChildrenRecursively(baseDir, { 
            // Skip common non-essential directories
            it.name !in setOf("node_modules", ".git", "bin", "obj", "build", ".gradle")
        }) { file ->
            val name = file.name
            
            // Check for project type indicators
            when {
                name.endsWith(".csproj") || name.endsWith(".sln") -> {
                    projectType = ProjectType.DOTNET
                    keyFiles.add(file.path)
                    
                    // Check for framework hints
                    if (file.extension == "csproj") {
                        val content = String(file.contentsToByteArray())
                        if ("Microsoft.NET.Sdk.Web" in content) frameworkHints.add("ASP.NET")
                        if ("Xamarin" in content) frameworkHints.add("Xamarin")
                        if ("Unity" in content) frameworkHints.add("Unity")
                    }
                }
                name == "build.gradle.kts" || name == "build.gradle" -> {
                    if (projectType == ProjectType.UNKNOWN) projectType = ProjectType.GRADLE
                    keyFiles.add(file.path)
                }
                name == "package.json" -> {
                    if (projectType == ProjectType.UNKNOWN) projectType = ProjectType.NPM
                    keyFiles.add(file.path)
                }
                name == "pyproject.toml" || name == "setup.py" -> {
                    if (projectType == ProjectType.UNKNOWN) projectType = ProjectType.PYTHON
                    keyFiles.add(file.path)
                }
            }
            
            // Limit file collection
            keyFiles.size < 20
        }
        
        return ProjectContext(
            name = project.name,
            basePath = basePath,
            projectType = projectType,
            frameworkHints = frameworkHints.distinct(),
            keyFiles = keyFiles.take(10)
        )
    }
    
    /**
     * Gets a summary string suitable for including in prompts.
     */
    fun getProjectSummary(): String {
        val context = getProjectContext()
        return buildString {
            append("Project: ${context.name}")
            append(" (${context.projectType.name.lowercase()})")
            if (context.frameworkHints.isNotEmpty()) {
                append(" [${context.frameworkHints.joinToString(", ")}]")
            }
        }
    }
}
```

#### Acceptance Criteria

- [ ] Detects .NET, Gradle, NPM, Python projects
- [ ] Identifies framework hints (ASP.NET, Unity, etc.)
- [ ] Caches results for performance

---

### v0.2.1d — Unit Tests

**Goal:** Comprehensive tests for context data models.

#### EditorContextTest.kt

```kotlin
package com.sidekick.context

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EditorContext")
class EditorContextTest {

    @Test
    @DisplayName("EMPTY has expected default values")
    fun `EMPTY has expected default values`() {
        val empty = EditorContext.EMPTY
        
        assertNull(empty.file)
        assertEquals("text", empty.language)
        assertEquals("", empty.filePath)
        assertEquals("", empty.fileName)
        assertNull(empty.selection)
        assertFalse(empty.hasSelection)
        assertFalse(empty.hasFile)
    }
    
    @Test
    @DisplayName("hasSelection returns true when selection exists")
    fun `hasSelection returns true when selection exists`() {
        val context = EditorContext.EMPTY.copy(selection = "some text")
        
        assertTrue(context.hasSelection)
    }
}

@DisplayName("SymbolContext")
class SymbolContextTest {
    
    @Test
    @DisplayName("EMPTY has expected default values")
    fun `EMPTY has expected default values`() {
        val empty = SymbolContext.EMPTY
        
        assertEquals("", empty.name)
        assertEquals(SymbolKind.UNKNOWN, empty.kind)
        assertNull(empty.signature)
        assertEquals("", empty.definition)
    }
}

@DisplayName("ProjectContext")
class ProjectContextTest {
    
    @Test
    @DisplayName("EMPTY has expected default values")
    fun `EMPTY has expected default values`() {
        val empty = ProjectContext.EMPTY
        
        assertEquals("", empty.name)
        assertEquals(ProjectType.UNKNOWN, empty.projectType)
        assertTrue(empty.frameworkHints.isEmpty())
    }
}

@DisplayName("SymbolKind")
class SymbolKindTest {
    
    @Test
    @DisplayName("has all expected values")
    fun `has all expected values`() {
        val kinds = SymbolKind.values()
        
        assertTrue(SymbolKind.CLASS in kinds)
        assertTrue(SymbolKind.METHOD in kinds)
        assertTrue(SymbolKind.PROPERTY in kinds)
        assertTrue(SymbolKind.UNKNOWN in kinds)
    }
}

@DisplayName("ProjectType")
class ProjectTypeTest {
    
    @Test
    @DisplayName("has all expected values")
    fun `has all expected values`() {
        val types = ProjectType.values()
        
        assertTrue(ProjectType.DOTNET in types)
        assertTrue(ProjectType.GRADLE in types)
        assertTrue(ProjectType.NPM in types)
        assertTrue(ProjectType.UNKNOWN in types)
    }
}
```

#### Acceptance Criteria

- [ ] Data model tests pass
- [ ] EMPTY instances work correctly
- [ ] Enum values verified

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Context Services (v0.2.1) -->
    <projectService 
        serviceImplementation="com.sidekick.context.EditorContextService"/>
    <projectService 
        serviceImplementation="com.sidekick.context.ProjectContextService"/>
</extensions>
```

---

## Verification Plan

### Automated Tests

```bash
# Run all tests
./gradlew test

# Build plugin
./gradlew buildPlugin
```

### Manual Verification

| Step | Expected Result |
|------|-----------------|
| Open a .cs file | EditorContextService returns C# language |
| Select text | EditorContext.selection contains selected text |
| Place cursor on method | SymbolContext shows method info |
| Open .NET project | ProjectContext detects DOTNET type |

---

## Future Versions (v0.2.2-v0.2.5)

After v0.2.1 is complete, subsequent versions will build on this context infrastructure:

- **v0.2.2** — Context Injection into chat prompts
- **v0.2.3** — Code Actions ("Explain", "Refactor", "Test")
- **v0.2.4** — Prompt Templates with variable substitution
- **v0.2.5** — Chat History persistence

