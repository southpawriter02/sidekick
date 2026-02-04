# Sidekick v0.3.x – Code Generation Phase

> **Phase Goal:** Intelligent code generation and transformation tools  
> **Building On:** v0.2.x Context Awareness (Editor/Project/Symbol context, Code Actions, Prompt Templates, Chat History)

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.3.1 | Documentation Generator | XML doc generation for methods/classes |
| v0.3.2 | Test Scaffolder | Unit test stub generation with frameworks |
| v0.3.3 | Commit Message Generator | Conventional commits from staged changes |
| v0.3.4 | Variable Naming Assistant | Context-aware naming suggestions |
| v0.3.5 | JSON → DTO Generator | Paste JSON to generate C#/Kotlin classes |

---

## v0.3.1 — Documentation Generator

### v0.3.1a — DocGenRequest Data Model

**Goal:** Define data structures for documentation generation requests.

#### DocGenRequest.kt

```kotlin
package com.sidekick.generation.docs

import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind

/**
 * Request for generating documentation.
 *
 * @property symbol The symbol to document
 * @property style Documentation style (XML, KDoc, JSDoc)
 * @property includeParams Whether to include parameter docs
 * @property includeReturns Whether to include return value docs
 * @property includeExceptions Whether to document thrown exceptions
 * @property includeExamples Whether to generate usage examples
 */
data class DocGenRequest(
    val symbol: SymbolContext,
    val style: DocStyle = DocStyle.AUTO,
    val includeParams: Boolean = true,
    val includeReturns: Boolean = true,
    val includeExceptions: Boolean = true,
    val includeExamples: Boolean = false
) {
    /**
     * Whether this request is valid for generation.
     */
    fun isValid(): Boolean {
        return symbol.name.isNotBlank() && 
               symbol.kind in DOCUMENTABLE_KINDS
    }
    
    companion object {
        val DOCUMENTABLE_KINDS = setOf(
            SymbolKind.CLASS,
            SymbolKind.INTERFACE,
            SymbolKind.STRUCT,
            SymbolKind.METHOD,
            SymbolKind.FUNCTION,
            SymbolKind.PROPERTY,
            SymbolKind.ENUM
        )
    }
}

/**
 * Documentation style formats.
 */
enum class DocStyle(val displayName: String) {
    AUTO("Auto-detect"),
    XML_DOC("XML Documentation (C#)"),
    KDOC("KDoc (Kotlin)"),
    JAVADOC("JavaDoc (Java)"),
    JSDOC("JSDoc (JavaScript/TypeScript)"),
    PYDOC("Docstring (Python)");
    
    companion object {
        fun fromLanguage(language: String): DocStyle {
            return when (language.lowercase()) {
                "c#", "csharp" -> XML_DOC
                "kotlin" -> KDOC
                "java" -> JAVADOC
                "javascript", "typescript" -> JSDOC
                "python" -> PYDOC
                else -> XML_DOC
            }
        }
    }
}

/**
 * Result of documentation generation.
 */
data class DocGenResult(
    val documentation: String,
    val style: DocStyle,
    val insertPosition: InsertPosition,
    val success: Boolean,
    val error: String? = null
)

/**
 * Where to insert generated documentation.
 */
enum class InsertPosition {
    BEFORE_SYMBOL,
    REPLACE_EXISTING,
    AFTER_SYMBOL
}
```

#### Acceptance Criteria

- [ ] DocGenRequest captures symbol and style options
- [ ] DocStyle enum covers major languages
- [ ] DocGenResult contains insertable documentation

---

### v0.3.1b — DocGenService

**Goal:** Service to generate documentation using LLM.

#### DocGenService.kt

```kotlin
package com.sidekick.generation.docs

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.context.SymbolContext
import com.sidekick.prompts.PromptTemplate
import com.sidekick.prompts.PromptTemplateService
import com.sidekick.services.ollama.OllamaService

@Service(Service.Level.PROJECT)
class DocGenService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(DocGenService::class.java)
        
        fun getInstance(project: Project): DocGenService {
            return project.getService(DocGenService::class.java)
        }
        
        /**
         * Prompt template for documentation generation.
         */
        val DOC_GEN_TEMPLATE = """
            Generate {{STYLE}} documentation for the following {{SYMBOL_KIND}}:
            
            ```{{LANGUAGE}}
            {{DEFINITION}}
            ```
            
            Requirements:
            - Write a clear, concise summary (1-2 sentences)
            {{#if INCLUDE_PARAMS}}- Document each parameter with type and purpose{{/if}}
            {{#if INCLUDE_RETURNS}}- Document the return value{{/if}}
            {{#if INCLUDE_EXCEPTIONS}}- Document any exceptions that may be thrown{{/if}}
            {{#if INCLUDE_EXAMPLES}}- Include a brief usage example{{/if}}
            
            Output ONLY the documentation block, no explanation.
        """.trimIndent()
    }

    /**
     * Generates documentation for the symbol at cursor.
     */
    suspend fun generateForCurrentSymbol(): DocGenResult {
        val editorService = EditorContextService.getInstance(project)
        val symbol = editorService.getSymbolAtCursor()
        
        if (!symbol.isValid) {
            return DocGenResult(
                documentation = "",
                style = DocStyle.AUTO,
                insertPosition = InsertPosition.BEFORE_SYMBOL,
                success = false,
                error = "No documentable symbol at cursor"
            )
        }
        
        val editorContext = editorService.getCurrentContext()
        val style = DocStyle.fromLanguage(editorContext.language)
        
        return generate(DocGenRequest(symbol, style))
    }

    /**
     * Generates documentation from a request.
     */
    suspend fun generate(request: DocGenRequest): DocGenResult {
        if (!request.isValid()) {
            return DocGenResult(
                documentation = "",
                style = request.style,
                insertPosition = InsertPosition.BEFORE_SYMBOL,
                success = false,
                error = "Invalid documentation request"
            )
        }
        
        val prompt = buildPrompt(request)
        val response = callLLM(prompt)
        
        return DocGenResult(
            documentation = formatDocumentation(response, request.style),
            style = request.style,
            insertPosition = InsertPosition.BEFORE_SYMBOL,
            success = true
        )
    }
    
    private fun buildPrompt(request: DocGenRequest): String {
        // Build prompt using template substitution
        return DOC_GEN_TEMPLATE
            .replace("{{STYLE}}", request.style.displayName)
            .replace("{{SYMBOL_KIND}}", request.symbol.kind.displayName)
            .replace("{{LANGUAGE}}", getLanguageForStyle(request.style))
            .replace("{{DEFINITION}}", request.symbol.definition)
            .replace("{{#if INCLUDE_PARAMS}}", if (request.includeParams) "" else "<!-- ")
            .replace("{{/if}}", if (request.includeParams) "" else " -->")
    }
    
    private suspend fun callLLM(prompt: String): String {
        // Implementation uses OllamaService
        return ""  // Placeholder
    }
    
    private fun formatDocumentation(raw: String, style: DocStyle): String {
        // Clean up and format the response
        return raw.trim()
    }
    
    private fun getLanguageForStyle(style: DocStyle): String {
        return when (style) {
            DocStyle.XML_DOC -> "csharp"
            DocStyle.KDOC -> "kotlin"
            DocStyle.JAVADOC -> "java"
            DocStyle.JSDOC -> "typescript"
            DocStyle.PYDOC -> "python"
            DocStyle.AUTO -> "text"
        }
    }
}
```

#### Acceptance Criteria

- [ ] Generates documentation for methods, classes, properties
- [ ] Respects language-specific documentation formats
- [ ] Handles parameter, return, and exception documentation

---

### v0.3.1c — DocGenAction

**Goal:** Editor action to trigger documentation generation.

#### GenerateDocumentationAction.kt

```kotlin
package com.sidekick.generation.docs

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking

/**
 * Action to generate documentation for the symbol at cursor.
 *
 * Available from:
 * - Editor context menu
 * - Generate menu (Alt+Insert)
 * - Keyboard shortcut (configurable)
 */
class GenerateDocumentationAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        runBlocking {
            val service = DocGenService.getInstance(project)
            val result = service.generateForCurrentSymbol()
            
            if (result.success) {
                insertDocumentation(project, editor, result)
            } else {
                showError(project, result.error ?: "Generation failed")
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }
    
    private fun insertDocumentation(
        project: Project,
        editor: com.intellij.openapi.editor.Editor,
        result: DocGenResult
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val offset = findInsertOffset(editor, result.insertPosition)
            document.insertString(offset, result.documentation + "\n")
        }
    }
    
    private fun findInsertOffset(
        editor: com.intellij.openapi.editor.Editor,
        position: InsertPosition
    ): Int {
        // Find the appropriate insertion point
        val caretOffset = editor.caretModel.offset
        val document = editor.document
        val lineNumber = document.getLineNumber(caretOffset)
        return document.getLineStartOffset(lineNumber)
    }
    
    private fun showError(project: Project, message: String) {
        // Show notification balloon
    }
}
```

#### plugin.xml Additions

```xml
<actions>
    <!-- Documentation Generator (v0.3.1) -->
    <action id="Sidekick.GenerateDocumentation"
            class="com.sidekick.generation.docs.GenerateDocumentationAction"
            text="Generate Documentation"
            description="Generate documentation for symbol at cursor">
        <add-to-group group-id="GenerateGroup" anchor="first"/>
        <add-to-group group-id="Sidekick.ActionGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift D"/>
    </action>
</actions>
```

#### Acceptance Criteria

- [ ] Action available in Generate menu and context menu
- [ ] Inserts documentation before symbol
- [ ] Shows error for non-documentable positions

---

### v0.3.1d — Unit Tests

#### DocGenRequestTest.kt

```kotlin
package com.sidekick.generation.docs

import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocGenRequestTest {

    @Test
    fun `isValid returns true for method symbol`() {
        val symbol = SymbolContext(
            name = "calculate",
            kind = SymbolKind.METHOD,
            signature = "fun calculate(x: Int): Int",
            containingClass = "Calculator",
            documentation = null,
            definition = "fun calculate(x: Int): Int { return x * 2 }"
        )
        
        val request = DocGenRequest(symbol)
        assertTrue(request.isValid())
    }
    
    @Test
    fun `isValid returns false for empty symbol`() {
        val request = DocGenRequest(SymbolContext.EMPTY)
        assertFalse(request.isValid())
    }
    
    @Test
    fun `fromLanguage returns correct style`() {
        assertEquals(DocStyle.XML_DOC, DocStyle.fromLanguage("csharp"))
        assertEquals(DocStyle.KDOC, DocStyle.fromLanguage("kotlin"))
        assertEquals(DocStyle.JAVADOC, DocStyle.fromLanguage("java"))
    }
}
```

---

## v0.3.2 — Test Scaffolder

### v0.3.2a — TestGenRequest Data Model

**Goal:** Define data structures for test generation.

#### TestGenRequest.kt

```kotlin
package com.sidekick.generation.tests

import com.sidekick.context.SymbolContext
import com.sidekick.context.ProjectContext

/**
 * Request for generating unit tests.
 */
data class TestGenRequest(
    val symbol: SymbolContext,
    val projectContext: ProjectContext,
    val framework: TestFramework = TestFramework.AUTO,
    val style: TestStyle = TestStyle.AAA,
    val includeMocks: Boolean = true,
    val includeEdgeCases: Boolean = true,
    val testCount: Int = 3
)

/**
 * Supported test frameworks.
 */
enum class TestFramework(val displayName: String, val imports: List<String>) {
    AUTO("Auto-detect", emptyList()),
    XUNIT("xUnit", listOf("Xunit")),
    NUNIT("NUnit", listOf("NUnit.Framework")),
    MSTEST("MSTest", listOf("Microsoft.VisualStudio.TestTools.UnitTesting")),
    JUNIT5("JUnit 5", listOf("org.junit.jupiter.api.*")),
    KOTEST("Kotest", listOf("io.kotest.core.spec.style.*")),
    JEST("Jest", emptyList()),
    PYTEST("pytest", listOf("pytest"));
    
    companion object {
        fun detect(projectContext: ProjectContext): TestFramework {
            val hints = projectContext.frameworkHints.map { it.lowercase() }
            return when {
                "xunit" in hints -> XUNIT
                "nunit" in hints -> NUNIT
                "mstest" in hints -> MSTEST
                "junit" in hints -> JUNIT5
                "kotest" in hints -> KOTEST
                "jest" in hints -> JEST
                "pytest" in hints -> PYTEST
                else -> when (projectContext.projectType) {
                    com.sidekick.context.ProjectType.DOTNET -> XUNIT
                    com.sidekick.context.ProjectType.GRADLE -> JUNIT5
                    com.sidekick.context.ProjectType.NPM -> JEST
                    com.sidekick.context.ProjectType.PYTHON -> PYTEST
                    else -> XUNIT
                }
            }
        }
    }
}

/**
 * Test structure styles.
 */
enum class TestStyle(val displayName: String) {
    AAA("Arrange-Act-Assert"),
    GWT("Given-When-Then"),
    SIMPLE("Simple assertions")
}

/**
 * Generated test result.
 */
data class TestGenResult(
    val testCode: String,
    val testFileName: String,
    val framework: TestFramework,
    val testCount: Int,
    val success: Boolean,
    val error: String? = null
)
```

---

### v0.3.2b — TestGenService

**Goal:** Service to generate unit tests using LLM.

#### TestGenService.kt

```kotlin
package com.sidekick.generation.tests

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContextService

@Service(Service.Level.PROJECT)
class TestGenService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): TestGenService {
            return project.getService(TestGenService::class.java)
        }
        
        val TEST_GEN_TEMPLATE = """
            Generate {{TEST_COUNT}} unit tests for this {{LANGUAGE}} {{SYMBOL_KIND}} using {{FRAMEWORK}}:
            
            ```{{LANGUAGE}}
            {{DEFINITION}}
            ```
            
            Requirements:
            - Use {{STYLE}} pattern
            - Each test should have a descriptive name
            {{#if INCLUDE_MOCKS}}- Use mocking for dependencies{{/if}}
            {{#if INCLUDE_EDGE_CASES}}- Include edge case tests (null, empty, boundary){{/if}}
            
            Generate only the test code with proper imports.
        """.trimIndent()
    }

    /**
     * Generates tests for the current selection or symbol.
     */
    suspend fun generateForCurrentContext(): TestGenResult {
        val editorService = EditorContextService.getInstance(project)
        val projectService = ProjectContextService.getInstance(project)
        
        val editorContext = editorService.getCurrentContext()
        val projectContext = projectService.getProjectContext()
        val symbol = editorService.getSymbolAtCursor()
        
        // Use selection if available, otherwise symbol
        val codeToTest = editorContext.selection ?: symbol.definition
        
        val framework = TestFramework.detect(projectContext)
        
        val request = TestGenRequest(
            symbol = symbol,
            projectContext = projectContext,
            framework = framework
        )
        
        return generate(request)
    }

    suspend fun generate(request: TestGenRequest): TestGenResult {
        val prompt = buildPrompt(request)
        val response = callLLM(prompt)
        
        val testFileName = generateTestFileName(request.symbol.name)
        
        return TestGenResult(
            testCode = response,
            testFileName = testFileName,
            framework = request.framework,
            testCount = request.testCount,
            success = true
        )
    }
    
    private fun generateTestFileName(symbolName: String): String {
        return "${symbolName}Tests"
    }
    
    private fun buildPrompt(request: TestGenRequest): String {
        return TEST_GEN_TEMPLATE
    }
    
    private suspend fun callLLM(prompt: String): String {
        return ""  // Placeholder
    }
}
```

---

### v0.3.2c — TestGenAction

**Goal:** Action to trigger test generation with options dialog.

#### GenerateTestsAction.kt

```kotlin
package com.sidekick.generation.tests

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Action to generate unit tests for selected code.
 */
class GenerateTestsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Show options dialog
        val dialog = TestGenOptionsDialog(project)
        if (dialog.showAndGet()) {
            // Generate tests with selected options
        }
    }
}

/**
 * Options dialog for test generation.
 */
class TestGenOptionsDialog(project: com.intellij.openapi.project.Project) : DialogWrapper(project) {
    
    // Framework selector
    // Style selector  
    // Include mocks checkbox
    // Include edge cases checkbox
    // Test count spinner
    
    init {
        title = "Generate Tests"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return JPanel()  // Build UI
    }
}
```

---

## v0.3.3 — Commit Message Generator

### v0.3.3a — CommitAnalysis Data Model

**Goal:** Data structures for analyzing git changes.

#### CommitAnalysis.kt

```kotlin
package com.sidekick.generation.commit

/**
 * Analysis of staged git changes.
 */
data class CommitAnalysis(
    val files: List<FileChange>,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val primaryScope: String?,
    val changeType: ConventionalType,
    val isBreakingChange: Boolean
)

/**
 * Individual file change.
 */
data class FileChange(
    val path: String,
    val status: ChangeStatus,
    val additions: Int,
    val deletions: Int,
    val diff: String
)

enum class ChangeStatus {
    ADDED, MODIFIED, DELETED, RENAMED
}

/**
 * Conventional commit types.
 */
enum class ConventionalType(val prefix: String, val description: String) {
    FEAT("feat", "A new feature"),
    FIX("fix", "A bug fix"),
    DOCS("docs", "Documentation only changes"),
    STYLE("style", "Code style changes (formatting, semicolons)"),
    REFACTOR("refactor", "Code refactoring"),
    PERF("perf", "Performance improvements"),
    TEST("test", "Adding or updating tests"),
    BUILD("build", "Build system or dependencies"),
    CI("ci", "CI/CD configuration"),
    CHORE("chore", "Other changes");
    
    companion object {
        fun detect(files: List<FileChange>): ConventionalType {
            val paths = files.map { it.path.lowercase() }
            return when {
                paths.all { "test" in it } -> TEST
                paths.all { "doc" in it || it.endsWith(".md") } -> DOCS
                paths.any { "ci" in it || ".github" in it } -> CI
                paths.any { "build" in it || "gradle" in it } -> BUILD
                else -> FEAT
            }
        }
    }
}

/**
 * Generated commit message.
 */
data class CommitMessage(
    val type: ConventionalType,
    val scope: String?,
    val subject: String,
    val body: String?,
    val footer: String?,
    val isBreaking: Boolean
) {
    fun format(): String = buildString {
        append(type.prefix)
        if (scope != null) append("($scope)")
        if (isBreaking) append("!")
        append(": ")
        append(subject)
        if (body != null) {
            append("\n\n")
            append(body)
        }
        if (footer != null) {
            append("\n\n")
            append(footer)
        }
    }
}
```

---

### v0.3.3b — CommitGenService

**Goal:** Service to analyze changes and generate commit messages.

#### CommitGenService.kt

```kotlin
package com.sidekick.generation.commit

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
class CommitGenService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): CommitGenService {
            return project.getService(CommitGenService::class.java)
        }
        
        val COMMIT_GEN_TEMPLATE = """
            Generate a conventional commit message for these changes:
            
            Files changed:
            {{FILE_LIST}}
            
            Diff summary:
            {{DIFF_SUMMARY}}
            
            Requirements:
            - Use conventional commits format: type(scope): subject
            - Subject should be imperative mood, max 72 chars
            - Detect if this is a breaking change
            - Add body if changes are complex
            
            Output format:
            TYPE: [type]
            SCOPE: [scope or empty]
            SUBJECT: [subject line]
            BODY: [optional body]
            BREAKING: [yes/no]
        """.trimIndent()
    }

    /**
     * Analyzes staged changes and generates a commit message.
     */
    suspend fun generateCommitMessage(): CommitMessage {
        val analysis = analyzeStaged()
        return generate(analysis)
    }
    
    /**
     * Analyzes currently staged git changes.
     */
    fun analyzeStaged(): CommitAnalysis {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repos = repoManager.repositories
        
        if (repos.isEmpty()) {
            return CommitAnalysis(
                files = emptyList(),
                totalAdditions = 0,
                totalDeletions = 0,
                primaryScope = null,
                changeType = ConventionalType.CHORE,
                isBreakingChange = false
            )
        }
        
        // Get staged changes from git
        val files = getStagedFiles(repos.first())
        val scope = detectScope(files)
        val type = ConventionalType.detect(files)
        
        return CommitAnalysis(
            files = files,
            totalAdditions = files.sumOf { it.additions },
            totalDeletions = files.sumOf { it.deletions },
            primaryScope = scope,
            changeType = type,
            isBreakingChange = false
        )
    }
    
    private fun getStagedFiles(repo: git4idea.repo.GitRepository): List<FileChange> {
        // Implementation to get staged files from git
        return emptyList()
    }
    
    private fun detectScope(files: List<FileChange>): String? {
        // Detect scope from common directory
        if (files.isEmpty()) return null
        
        val dirs = files.map { 
            it.path.substringBeforeLast("/").substringAfterLast("/") 
        }.distinct()
        
        return if (dirs.size == 1) dirs.first() else null
    }
    
    private suspend fun generate(analysis: CommitAnalysis): CommitMessage {
        val prompt = buildPrompt(analysis)
        val response = callLLM(prompt)
        return parseResponse(response)
    }
    
    private fun buildPrompt(analysis: CommitAnalysis): String {
        return COMMIT_GEN_TEMPLATE
    }
    
    private suspend fun callLLM(prompt: String): String {
        return ""
    }
    
    private fun parseResponse(response: String): CommitMessage {
        // Parse structured LLM response
        return CommitMessage(
            type = ConventionalType.FEAT,
            scope = null,
            subject = "generated message",
            body = null,
            footer = null,
            isBreaking = false
        )
    }
}
```

---

### v0.3.3c — CommitGenAction

**Goal:** Integration with VCS commit dialog.

#### GenerateCommitMessageAction.kt

```kotlin
package com.sidekick.generation.commit

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsDataKeys
import kotlinx.coroutines.runBlocking

/**
 * Action to generate commit message from staged changes.
 * 
 * Integrates with:
 * - VCS commit dialog
 * - Toolbar button in commit panel
 */
class GenerateCommitMessageAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        
        runBlocking {
            val service = CommitGenService.getInstance(project)
            val message = service.generateCommitMessage()
            commitMessage.setCommitMessage(message.format())
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
```

---

## v0.3.4 — Variable Naming Assistant

### v0.3.4a — NamingRequest Data Model

#### NamingRequest.kt

```kotlin
package com.sidekick.generation.naming

import com.sidekick.context.EditorContext
import com.sidekick.context.SymbolContext

/**
 * Request for variable/method naming suggestions.
 */
data class NamingRequest(
    val currentName: String,
    val context: EditorContext,
    val symbolContext: SymbolContext?,
    val namingStyle: NamingStyle = NamingStyle.AUTO,
    val suggestionCount: Int = 5
)

/**
 * Naming convention styles.
 */
enum class NamingStyle(val displayName: String) {
    AUTO("Auto-detect"),
    CAMEL_CASE("camelCase"),
    PASCAL_CASE("PascalCase"),
    SNAKE_CASE("snake_case"),
    SCREAMING_SNAKE("SCREAMING_SNAKE_CASE"),
    KEBAB_CASE("kebab-case")
}

/**
 * Naming suggestion with confidence score.
 */
data class NamingSuggestion(
    val name: String,
    val confidence: Float,
    val reasoning: String
)

/**
 * Result of naming generation.
 */
data class NamingResult(
    val suggestions: List<NamingSuggestion>,
    val style: NamingStyle,
    val success: Boolean
)
```

---

### v0.3.4b — NamingService

#### NamingService.kt

```kotlin
package com.sidekick.generation.naming

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService

@Service(Service.Level.PROJECT)
class NamingService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): NamingService {
            return project.getService(NamingService::class.java)
        }
        
        val NAMING_TEMPLATE = """
            Suggest {{COUNT}} better names for this {{LANGUAGE}} identifier:
            
            Current name: {{CURRENT_NAME}}
            
            Context:
            ```{{LANGUAGE}}
            {{CONTEXT}}
            ```
            
            Requirements:
            - Use {{STYLE}} naming convention
            - Names should be descriptive and concise
            - Follow {{LANGUAGE}} conventions
            
            For each suggestion, provide:
            1. The suggested name
            2. Confidence (0.0-1.0)
            3. Brief reasoning
        """.trimIndent()
    }

    /**
     * Gets naming suggestions for word at cursor.
     */
    suspend fun suggestForCursor(): NamingResult {
        val editorService = EditorContextService.getInstance(project)
        val context = editorService.getCurrentContext()
        val symbol = editorService.getSymbolAtCursor()
        
        val request = NamingRequest(
            currentName = symbol.name,
            context = context,
            symbolContext = symbol
        )
        
        return suggest(request)
    }

    suspend fun suggest(request: NamingRequest): NamingResult {
        val prompt = buildPrompt(request)
        val response = callLLM(prompt)
        val suggestions = parseResponse(response)
        
        return NamingResult(
            suggestions = suggestions,
            style = request.namingStyle,
            success = suggestions.isNotEmpty()
        )
    }
    
    private fun buildPrompt(request: NamingRequest): String {
        return NAMING_TEMPLATE
    }
    
    private suspend fun callLLM(prompt: String): String {
        return ""
    }
    
    private fun parseResponse(response: String): List<NamingSuggestion> {
        return emptyList()
    }
}
```

---

## v0.3.5 — JSON → DTO Generator

### v0.3.5a — DtoGenRequest Data Model

#### DtoGenRequest.kt

```kotlin
package com.sidekick.generation.dto

/**
 * Request for DTO generation from JSON.
 */
data class DtoGenRequest(
    val json: String,
    val targetLanguage: TargetLanguage,
    val className: String,
    val options: DtoOptions = DtoOptions()
)

/**
 * Target language for DTO generation.
 */
enum class TargetLanguage(val displayName: String) {
    CSHARP_RECORD("C# Record"),
    CSHARP_CLASS("C# Class"),
    KOTLIN_DATA_CLASS("Kotlin Data Class"),
    JAVA_RECORD("Java Record"),
    TYPESCRIPT_INTERFACE("TypeScript Interface"),
    PYTHON_DATACLASS("Python Dataclass")
}

/**
 * DTO generation options.
 */
data class DtoOptions(
    val useNullableTypes: Boolean = true,
    val addJsonAttributes: Boolean = true,
    val generateBuilder: Boolean = false,
    val makeImmutable: Boolean = true,
    val addValidation: Boolean = false
)

/**
 * Generated DTO result.
 */
data class DtoGenResult(
    val code: String,
    val className: String,
    val nestedClasses: List<String>,
    val imports: List<String>,
    val success: Boolean,
    val error: String? = null
)
```

---

### v0.3.5b — DtoGenService

#### DtoGenService.kt

```kotlin
package com.sidekick.generation.dto

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class DtoGenService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): DtoGenService {
            return project.getService(DtoGenService::class.java)
        }
        
        private val objectMapper = ObjectMapper()
        
        val DTO_GEN_TEMPLATE = """
            Generate a {{TARGET_LANGUAGE}} class/record from this JSON:
            
            ```json
            {{JSON}}
            ```
            
            Class name: {{CLASS_NAME}}
            
            Requirements:
            {{#if NULLABLE}}- Use nullable types where appropriate{{/if}}
            {{#if JSON_ATTRS}}- Add JSON serialization attributes{{/if}}
            {{#if IMMUTABLE}}- Make the class immutable{{/if}}
            {{#if VALIDATION}}- Add validation attributes{{/if}}
            
            Generate complete, compilable code with necessary imports.
        """.trimIndent()
    }

    /**
     * Generates DTO from JSON in clipboard.
     */
    suspend fun generateFromClipboard(): DtoGenResult {
        val clipboard = getClipboardContent()
        if (!isValidJson(clipboard)) {
            return DtoGenResult(
                code = "",
                className = "",
                nestedClasses = emptyList(),
                imports = emptyList(),
                success = false,
                error = "Clipboard does not contain valid JSON"
            )
        }
        
        val request = DtoGenRequest(
            json = clipboard,
            targetLanguage = detectTargetLanguage(),
            className = inferClassName(clipboard)
        )
        
        return generate(request)
    }

    suspend fun generate(request: DtoGenRequest): DtoGenResult {
        if (!isValidJson(request.json)) {
            return DtoGenResult(
                code = "",
                className = request.className,
                nestedClasses = emptyList(),
                imports = emptyList(),
                success = false,
                error = "Invalid JSON"
            )
        }
        
        val prompt = buildPrompt(request)
        val response = callLLM(prompt)
        
        return DtoGenResult(
            code = response,
            className = request.className,
            nestedClasses = extractNestedClasses(response),
            imports = extractImports(response),
            success = true
        )
    }
    
    private fun isValidJson(text: String): Boolean {
        return try {
            objectMapper.readTree(text)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun inferClassName(json: String): String {
        // Try to infer name from JSON structure
        return "GeneratedDto"
    }
    
    private fun detectTargetLanguage(): TargetLanguage {
        // Detect from current file context
        return TargetLanguage.CSHARP_RECORD
    }
    
    private fun getClipboardContent(): String {
        return java.awt.Toolkit.getDefaultToolkit()
            .systemClipboard
            .getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String ?: ""
    }
    
    private fun buildPrompt(request: DtoGenRequest): String {
        return DTO_GEN_TEMPLATE
    }
    
    private suspend fun callLLM(prompt: String): String {
        return ""
    }
    
    private fun extractNestedClasses(code: String): List<String> {
        return emptyList()
    }
    
    private fun extractImports(code: String): List<String> {
        return emptyList()
    }
}
```

---

## plugin.xml Additions (Complete)

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Code Generation Services (v0.3.x) -->
    <projectService serviceImplementation="com.sidekick.generation.docs.DocGenService"/>
    <projectService serviceImplementation="com.sidekick.generation.tests.TestGenService"/>
    <projectService serviceImplementation="com.sidekick.generation.commit.CommitGenService"/>
    <projectService serviceImplementation="com.sidekick.generation.naming.NamingService"/>
    <projectService serviceImplementation="com.sidekick.generation.dto.DtoGenService"/>
</extensions>

<actions>
    <!-- Documentation Generator (v0.3.1) -->
    <action id="Sidekick.GenerateDocumentation"
            class="com.sidekick.generation.docs.GenerateDocumentationAction"
            text="Generate Documentation"
            description="Generate documentation for symbol">
        <add-to-group group-id="GenerateGroup" anchor="first"/>
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift D"/>
    </action>
    
    <!-- Test Scaffolder (v0.3.2) -->
    <action id="Sidekick.GenerateTests"
            class="com.sidekick.generation.tests.GenerateTestsAction"
            text="Generate Unit Tests"
            description="Generate unit tests for code">
        <add-to-group group-id="GenerateGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift G"/>
    </action>
    
    <!-- Commit Message Generator (v0.3.3) -->
    <action id="Sidekick.GenerateCommitMessage"
            class="com.sidekick.generation.commit.GenerateCommitMessageAction"
            text="Generate Commit Message"
            description="Generate commit message from changes">
        <add-to-group group-id="Vcs.MessageActionGroup"/>
    </action>
    
    <!-- Variable Naming (v0.3.4) -->
    <action id="Sidekick.SuggestNames"
            class="com.sidekick.generation.naming.SuggestNamesAction"
            text="Suggest Names"
            description="Suggest better names for identifier">
        <add-to-group group-id="RefactoringMenu"/>
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift N"/>
    </action>
    
    <!-- DTO Generator (v0.3.5) -->
    <action id="Sidekick.GenerateDto"
            class="com.sidekick.generation.dto.GenerateDtoAction"
            text="Generate DTO from JSON"
            description="Generate DTO class from JSON">
        <add-to-group group-id="GenerateGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift J"/>
    </action>
</actions>
```

---

## Verification Plan

### Automated Tests

```bash
# Run all tests
./gradlew test

# Run v0.3.x specific tests
./gradlew test --tests "com.sidekick.generation.*"
```

### Manual Verification

| Version | Step | Expected Result |
|---------|------|-----------------|
| v0.3.1 | Place cursor on method, Alt+Shift+D | XML/KDoc generated and inserted |
| v0.3.2 | Select method, Alt+Shift+G | Test dialog appears, tests generated |
| v0.3.3 | Stage changes, open commit dialog | Commit message generated |
| v0.3.4 | Place cursor on variable, Alt+Shift+N | Naming suggestions popup |
| v0.3.5 | Copy JSON, Alt+Shift+J | DTO class generated |

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Documentation generation accuracy | >90% compilable output |
| Test scaffold coverage | 3+ tests per method |
| Commit message format compliance | 100% conventional commits |
| Naming suggestion relevance | >80% user acceptance |
| DTO type inference accuracy | >95% correct types |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.3.0 | 2026-02-04 | Ryan | Initial v0.3.x design specification |
