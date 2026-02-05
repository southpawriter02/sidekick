# Sidekick v0.6.x – Code Quality Phase

> **Phase Goal:** Proactive code quality enhancements and analysis tools  
> **Building On:** v0.5.x Visual Enhancements

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.6.1 | Exception Hunter | Highlight potentially unhandled exceptions |
| v0.6.2 | TODO Tracker | Deadline-aware TODO management |
| v0.6.3 | Performance Linter | Flag common performance anti-patterns |
| v0.6.4 | Dead Code Cemetery | Bulk management of unused symbols |

---

## v0.6.1 — Exception Hunter

### v0.6.1a — ExceptionHunterModels

**Goal:** Data structures for exception analysis.

#### ExceptionHunterModels.kt

```kotlin
package com.sidekick.quality.exceptions

import com.intellij.psi.PsiElement

/**
 * An unhandled exception detected in code.
 */
data class UnhandledException(
    val exceptionType: String,
    val location: ExceptionLocation,
    val callChain: List<CallSite>,
    val severity: ExceptionSeverity,
    val suggestion: String
) {
    val isRuntime: Boolean get() = exceptionType.contains("Runtime") || 
                                    exceptionType.contains("Unchecked")
}

/**
 * Location of an exception.
 */
data class ExceptionLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val methodName: String?,
    val className: String?
)

/**
 * A call site in the exception chain.
 */
data class CallSite(
    val methodName: String,
    val className: String,
    val throwsDeclaration: List<String>
)

/**
 * Exception severity levels.
 */
enum class ExceptionSeverity(val displayName: String, val priority: Int) {
    CRITICAL("Critical", 1),     // NullPointerException, OutOfMemory
    HIGH("High", 2),             // IOException, SQLException
    MEDIUM("Medium", 3),         // Custom exceptions
    LOW("Low", 4),               // Unlikely to occur
    INFO("Info", 5);             // Documented/expected

    companion object {
        fun fromExceptionType(type: String): ExceptionSeverity {
            return when {
                type.contains("NullPointer") -> CRITICAL
                type.contains("OutOfMemory") -> CRITICAL
                type.contains("StackOverflow") -> CRITICAL
                type.contains("IO") -> HIGH
                type.contains("SQL") -> HIGH
                type.contains("Security") -> HIGH
                type.contains("Illegal") -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * Configuration for exception hunting.
 */
data class ExceptionHunterConfig(
    val enabled: Boolean = true,
    val minSeverity: ExceptionSeverity = ExceptionSeverity.MEDIUM,
    val showInGutter: Boolean = true,
    val highlightInEditor: Boolean = true,
    val traverseCallChain: Boolean = true,
    val maxCallChainDepth: Int = 5,
    val ignoredExceptions: Set<String> = emptySet()
)

/**
 * Analysis result for a file.
 */
data class ExceptionAnalysisResult(
    val filePath: String,
    val exceptions: List<UnhandledException>,
    val analyzedMethods: Int,
    val analysisTimeMs: Long
) {
    val hasIssues: Boolean get() = exceptions.isNotEmpty()
    val criticalCount: Int get() = exceptions.count { it.severity == ExceptionSeverity.CRITICAL }
}
```

---

### v0.6.1b — ExceptionHunterService

**Goal:** Service to analyze and detect unhandled exceptions.

#### ExceptionHunterService.kt

```kotlin
package com.sidekick.quality.exceptions

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

@Service(Service.Level.PROJECT)
@State(name = "SidekickExceptionHunter", storages = [Storage("sidekick-exceptions.xml")])
class ExceptionHunterService(private val project: Project) : PersistentStateComponent<ExceptionHunterService.State> {

    data class State(var config: ExceptionHunterConfig = ExceptionHunterConfig())
    private var state = State()
    
    private val cache = mutableMapOf<String, ExceptionAnalysisResult>()

    companion object {
        fun getInstance(project: Project): ExceptionHunterService {
            return project.getService(ExceptionHunterService::class.java)
        }
        
        private val COMMON_EXCEPTIONS = setOf(
            "NullPointerException", "IllegalArgumentException", "IllegalStateException",
            "IOException", "SQLException", "SecurityException", "RuntimeException"
        )
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Analyzes a file for unhandled exceptions.
     */
    fun analyzeFile(psiFile: PsiFile): ExceptionAnalysisResult {
        if (!state.config.enabled) {
            return ExceptionAnalysisResult(psiFile.virtualFile?.path ?: "", emptyList(), 0, 0)
        }

        val startTime = System.currentTimeMillis()
        val exceptions = mutableListOf<UnhandledException>()
        var methodCount = 0

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (isMethodElement(element)) {
                    methodCount++
                    analyzeMethod(element, exceptions)
                }
                super.visitElement(element)
            }
        })

        val filtered = exceptions.filter { 
            it.severity.priority <= state.config.minSeverity.priority &&
            it.exceptionType !in state.config.ignoredExceptions
        }

        return ExceptionAnalysisResult(
            filePath = psiFile.virtualFile?.path ?: "",
            exceptions = filtered,
            analyzedMethods = methodCount,
            analysisTimeMs = System.currentTimeMillis() - startTime
        ).also { cache[it.filePath] = it }
    }

    /**
     * Gets cached analysis for a file.
     */
    fun getCachedAnalysis(filePath: String): ExceptionAnalysisResult? = cache[filePath]

    /**
     * Gets exceptions at a specific line.
     */
    fun getExceptionsAtLine(filePath: String, line: Int): List<UnhandledException> {
        return cache[filePath]?.exceptions?.filter { it.location.line == line } ?: emptyList()
    }

    private fun isMethodElement(element: PsiElement): Boolean {
        val type = element.node?.elementType?.toString() ?: return false
        return type.contains("METHOD") || type.contains("FUN") || type.contains("FUNCTION")
    }

    private fun analyzeMethod(method: PsiElement, results: MutableList<UnhandledException>) {
        // Find method calls that can throw
        method.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val throwingCall = detectThrowingCall(element)
                if (throwingCall != null && !isHandled(element, throwingCall)) {
                    results.add(createUnhandledException(element, throwingCall))
                }
                super.visitElement(element)
            }
        })
    }

    private fun detectThrowingCall(element: PsiElement): String? {
        val text = element.text ?: return null
        // Simplified detection - real impl would use PSI analysis
        return when {
            text.contains("throw ") -> extractExceptionType(text)
            text.contains(".read(") -> "IOException"
            text.contains(".write(") -> "IOException"
            text.contains(".execute(") -> "SQLException"
            text.contains("!!") -> "NullPointerException"
            else -> null
        }
    }

    private fun extractExceptionType(text: String): String {
        val match = Regex("""throw\s+(?:new\s+)?(\w+)""").find(text)
        return match?.groupValues?.get(1) ?: "Exception"
    }

    private fun isHandled(element: PsiElement, exceptionType: String): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            val type = current.node?.elementType?.toString() ?: ""
            if (type.contains("TRY")) return true
            if (type.contains("METHOD") && hasThrowsDeclaration(current, exceptionType)) return true
            current = current.parent
        }
        return false
    }

    private fun hasThrowsDeclaration(method: PsiElement, exceptionType: String): Boolean {
        return method.text?.contains("throws $exceptionType") == true ||
               method.text?.contains("@Throws") == true
    }

    private fun createUnhandledException(element: PsiElement, exceptionType: String): UnhandledException {
        val file = element.containingFile
        val doc = file?.viewProvider?.document
        val line = doc?.getLineNumber(element.textRange.startOffset) ?: 0

        return UnhandledException(
            exceptionType = exceptionType,
            location = ExceptionLocation(
                filePath = file?.virtualFile?.path ?: "",
                line = line + 1,
                column = 0,
                methodName = null,
                className = null
            ),
            callChain = emptyList(),
            severity = ExceptionSeverity.fromExceptionType(exceptionType),
            suggestion = "Consider wrapping in try-catch or declaring throws"
        )
    }
}
```

---

### v0.6.1c — ExceptionHunterInspection

**Goal:** IDE inspection that highlights unhandled exceptions.

#### ExceptionHunterInspection.kt

```kotlin
package com.sidekick.quality.exceptions

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.markup.*
import com.intellij.psi.PsiFile

/**
 * Inspection for unhandled exceptions.
 */
class ExceptionHunterInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Sidekick: Unhandled Exception"
    override fun getGroupDisplayName() = "Sidekick"
    override fun getShortName() = "SidekickUnhandledException"

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val service = ExceptionHunterService.getInstance(file.project)
        val result = service.analyzeFile(file)
        
        return result.exceptions.mapNotNull { exception ->
            val element = findElementAtLine(file, exception.location.line)
            element?.let {
                manager.createProblemDescriptor(
                    it,
                    "Unhandled ${exception.exceptionType}: ${exception.suggestion}",
                    true,
                    arrayOf(
                        AddTryCatchFix(exception),
                        AddThrowsDeclarationFix(exception),
                        SuppressExceptionFix(exception)
                    ),
                    when (exception.severity) {
                        ExceptionSeverity.CRITICAL -> ProblemHighlightType.ERROR
                        ExceptionSeverity.HIGH -> ProblemHighlightType.WARNING
                        else -> ProblemHighlightType.WEAK_WARNING
                    }
                )
            }
        }.toTypedArray()
    }

    private fun findElementAtLine(file: PsiFile, line: Int): com.intellij.psi.PsiElement? {
        val doc = file.viewProvider.document ?: return null
        if (line < 1 || line > doc.lineCount) return null
        val offset = doc.getLineStartOffset(line - 1)
        return file.findElementAt(offset)
    }
}

/**
 * Quick fix to add try-catch.
 */
class AddTryCatchFix(private val exception: UnhandledException) : LocalQuickFix {
    override fun getFamilyName() = "Wrap in try-catch"
    override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
        // Add try-catch around the statement
    }
}

/**
 * Quick fix to add throws declaration.
 */
class AddThrowsDeclarationFix(private val exception: UnhandledException) : LocalQuickFix {
    override fun getFamilyName() = "Add throws declaration"
    override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
        // Add throws to method signature
    }
}

/**
 * Quick fix to suppress the warning.
 */
class SuppressExceptionFix(private val exception: UnhandledException) : LocalQuickFix {
    override fun getFamilyName() = "Suppress this warning"
    override fun applyFix(project: com.intellij.openapi.project.Project, descriptor: ProblemDescriptor) {
        // Add to ignored exceptions list
    }
}
```

---

## v0.6.2 — TODO Tracker

### v0.6.2a — TodoModels

**Goal:** Data structures for TODO management.

#### TodoModels.kt

```kotlin
package com.sidekick.quality.todos

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A TODO item extracted from code.
 */
data class TodoItem(
    val id: String,
    val text: String,
    val type: TodoType,
    val priority: TodoPriority,
    val location: TodoLocation,
    val deadline: LocalDate?,
    val author: String?,
    val createdDate: LocalDate?,
    val tags: List<String>
) {
    val isOverdue: Boolean get() = deadline?.let { LocalDate.now().isAfter(it) } ?: false
    val daysUntilDue: Long? get() = deadline?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
    val status: TodoStatus get() = when {
        isOverdue -> TodoStatus.OVERDUE
        daysUntilDue?.let { it <= 3 } == true -> TodoStatus.DUE_SOON
        else -> TodoStatus.OPEN
    }
}

/**
 * TODO location in code.
 */
data class TodoLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val contextSnippet: String?
)

/**
 * Types of TODO markers.
 */
enum class TodoType(val patterns: List<String>, val icon: String) {
    TODO(listOf("TODO", "TODO:"), "📝"),
    FIXME(listOf("FIXME", "FIXME:"), "🔧"),
    HACK(listOf("HACK", "HACK:"), "⚠️"),
    BUG(listOf("BUG", "BUG:"), "🐛"),
    NOTE(listOf("NOTE", "NOTE:"), "📌"),
    OPTIMIZE(listOf("OPTIMIZE", "PERF:"), "⚡"),
    REVIEW(listOf("REVIEW", "REVIEW:"), "👀"),
    DEPRECATED(listOf("DEPRECATED"), "🚫");

    companion object {
        fun detect(text: String): TodoType {
            val upper = text.uppercase()
            return entries.find { type -> 
                type.patterns.any { upper.startsWith(it) }
            } ?: TODO
        }
    }
}

/**
 * Priority levels.
 */
enum class TodoPriority(val weight: Int) {
    CRITICAL(4),
    HIGH(3),
    MEDIUM(2),
    LOW(1);

    companion object {
        fun detect(text: String): TodoPriority {
            val upper = text.uppercase()
            return when {
                upper.contains("CRITICAL") || upper.contains("!!!") -> CRITICAL
                upper.contains("URGENT") || upper.contains("!!") -> HIGH
                upper.contains("LOW") || upper.contains("MINOR") -> LOW
                else -> MEDIUM
            }
        }
    }
}

/**
 * TODO status.
 */
enum class TodoStatus(val displayName: String) {
    OPEN("Open"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue"),
    COMPLETED("Completed")
}

/**
 * Configuration for TODO tracking.
 */
data class TodoConfig(
    val enabled: Boolean = true,
    val scanOnOpen: Boolean = true,
    val showNotifications: Boolean = true,
    val overdueNotifications: Boolean = true,
    val customPatterns: List<String> = emptyList(),
    val datePatterns: List<String> = listOf(
        """(\d{4}-\d{2}-\d{2})""",        // 2024-12-31
        """by\s+(\d{1,2}/\d{1,2}/\d{4})""" // by 12/31/2024
    )
)
```

---

### v0.6.2b — TodoService

**Goal:** Service to extract and manage TODOs.

#### TodoService.kt

```kotlin
package com.sidekick.quality.todos

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(name = "SidekickTodos", storages = [Storage("sidekick-todos.xml")])
class TodoService(private val project: Project) : PersistentStateComponent<TodoService.State> {

    data class State(
        var config: TodoConfig = TodoConfig(),
        var todos: MutableList<TodoItem> = mutableListOf()
    )
    
    private var state = State()

    companion object {
        fun getInstance(project: Project): TodoService {
            return project.getService(TodoService::class.java)
        }
        
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Scans a file for TODOs.
     */
    fun scanFile(psiFile: PsiFile): List<TodoItem> {
        if (!state.config.enabled) return emptyList()

        val todos = mutableListOf<TodoItem>()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)

        comments.forEach { comment ->
            val todo = parseComment(comment, psiFile)
            if (todo != null) todos.add(todo)
        }

        // Update stored todos for this file
        state.todos.removeIf { it.location.filePath == psiFile.virtualFile?.path }
        state.todos.addAll(todos)

        return todos
    }

    /**
     * Gets all TODOs across the project.
     */
    fun getAllTodos(): List<TodoItem> = state.todos.toList()

    /**
     * Gets overdue TODOs.
     */
    fun getOverdueTodos(): List<TodoItem> = state.todos.filter { it.isOverdue }

    /**
     * Gets TODOs due within N days.
     */
    fun getTodosDueSoon(days: Int = 7): List<TodoItem> {
        return state.todos.filter { todo ->
            todo.daysUntilDue?.let { it in 0..days } ?: false
        }
    }

    /**
     * Gets TODOs grouped by type.
     */
    fun getTodosByType(): Map<TodoType, List<TodoItem>> {
        return state.todos.groupBy { it.type }
    }

    /**
     * Gets TODOs grouped by file.
     */
    fun getTodosByFile(): Map<String, List<TodoItem>> {
        return state.todos.groupBy { it.location.filePath }
    }

    /**
     * Marks a TODO as completed (removes it).
     */
    fun markCompleted(todoId: String) {
        state.todos.removeIf { it.id == todoId }
    }

    private fun parseComment(comment: PsiComment, file: PsiFile): TodoItem? {
        val text = comment.text.trim()
        val type = TodoType.detect(text)
        
        // Must match a TODO pattern
        if (!TodoType.entries.any { t -> t.patterns.any { text.uppercase().contains(it) } }) {
            return null
        }

        val doc = file.viewProvider.document
        val line = doc?.getLineNumber(comment.textRange.startOffset)?.plus(1) ?: 0

        return TodoItem(
            id = UUID.randomUUID().toString(),
            text = cleanTodoText(text),
            type = type,
            priority = TodoPriority.detect(text),
            location = TodoLocation(
                filePath = file.virtualFile?.path ?: "",
                line = line,
                column = 0,
                contextSnippet = null
            ),
            deadline = extractDeadline(text),
            author = extractAuthor(text),
            createdDate = null,
            tags = extractTags(text)
        )
    }

    private fun cleanTodoText(text: String): String {
        return text
            .removePrefix("//").removePrefix("/*").removeSuffix("*/")
            .replace(Regex("""TODO:?|FIXME:?|HACK:?|BUG:?|NOTE:?""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun extractDeadline(text: String): LocalDate? {
        state.config.datePatterns.forEach { pattern ->
            Regex(pattern).find(text)?.groupValues?.get(1)?.let { dateStr ->
                DATE_FORMATS.forEach { format ->
                    try { return LocalDate.parse(dateStr, format) }
                    catch (_: Exception) {}
                }
            }
        }
        return null
    }

    private fun extractAuthor(text: String): String? {
        val match = Regex("""@(\w+)""").find(text)
        return match?.groupValues?.get(1)
    }

    private fun extractTags(text: String): List<String> {
        return Regex("""#(\w+)""").findAll(text).map { it.groupValues[1] }.toList()
    }
}
```

---

### v0.6.2c — TodoToolWindow

**Goal:** Tool window for TODO management.

#### TodoToolWindow.kt

```kotlin
package com.sidekick.quality.todos

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import javax.swing.*
import javax.swing.table.DefaultTableModel

class TodoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TodoPanel(project)
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(panel, "", false)
        )
    }
}

class TodoPanel(private val project: Project) : JBPanel<TodoPanel>() {
    private val service = TodoService.getInstance(project)
    private val tableModel = DefaultTableModel(
        arrayOf("Type", "Priority", "Text", "File", "Line", "Due"),
        0
    )
    private val table = JBTable(tableModel)

    init {
        layout = java.awt.BorderLayout()
        add(createToolbar(), java.awt.BorderLayout.NORTH)
        add(JBScrollPane(table), java.awt.BorderLayout.CENTER)
        add(createStatusBar(), java.awt.BorderLayout.SOUTH)
        
        table.selectionModel.addListSelectionListener { navigateToSelected() }
        refreshTodos()
    }

    private fun createToolbar(): JPanel = panel {
        row {
            button("Refresh") { refreshTodos() }
            button("Scan Project") { scanProject() }
            label(" | Filter: ")
            comboBox(listOf("All", "Overdue", "Due Soon", "FIXME", "TODO"))
        }
    }

    private fun createStatusBar(): JPanel = panel {
        row {
            val todos = service.getAllTodos()
            val overdue = service.getOverdueTodos().size
            label("Total: ${todos.size} | Overdue: $overdue")
        }
    }

    private fun refreshTodos() {
        tableModel.rowCount = 0
        service.getAllTodos().sortedBy { it.priority.weight }.forEach { todo ->
            tableModel.addRow(arrayOf(
                "${todo.type.icon} ${todo.type.name}",
                todo.priority.name,
                todo.text.take(50),
                todo.location.filePath.substringAfterLast("/"),
                todo.location.line,
                todo.deadline?.toString() ?: "-"
            ))
        }
    }

    private fun scanProject() {
        // Scan all files in project for TODOs
    }

    private fun navigateToSelected() {
        val row = table.selectedRow
        if (row < 0) return
        
        val todos = service.getAllTodos().sortedBy { it.priority.weight }
        if (row >= todos.size) return
        
        val todo = todos[row]
        LocalFileSystem.getInstance().findFileByPath(todo.location.filePath)?.let { vf ->
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }
}
```

---

## v0.6.3 — Performance Linter

### v0.6.3a — PerformanceLinterModels

**Goal:** Data structures for performance analysis.

#### PerformanceLinterModels.kt

```kotlin
package com.sidekick.quality.performance

/**
 * A detected performance issue.
 */
data class PerformanceIssue(
    val type: IssueType,
    val severity: IssueSeverity,
    val location: IssueLocation,
    val description: String,
    val suggestion: String,
    val estimatedImpact: String?
)

/**
 * Location of an issue.
 */
data class IssueLocation(
    val filePath: String,
    val line: Int,
    val codeSnippet: String
)

/**
 * Types of performance issues.
 */
enum class IssueType(val displayName: String, val pattern: String?) {
    STRING_CONCAT_LOOP("String concatenation in loop", """for.*\+="""),
    LINQ_IN_HOT_PATH("LINQ in hot path", """\.(Where|Select|Any)\("""),
    ALLOCATION_IN_LOOP("Allocation in loop", """for.*new\s+\w+"""),
    ASYNC_VOID("Async void method", """async\s+void"""),
    LARGE_OBJECT_HEAP("Large object heap allocation", """new\s+byte\["""),
    BOXING("Boxing operation", null),
    REGEX_NOT_COMPILED("Uncompiled Regex", """new\s+Regex\("""),
    UNBOUNDED_COLLECTION("Unbounded collection growth", """\.Add\("""),
    N_PLUS_ONE("Potential N+1 query", """foreach.*\.Include"""),
    SYNC_OVER_ASYNC("Sync over async", """\.Result|\.Wait\(\)""");
    
    companion object {
        fun detect(code: String): List<IssueType> {
            return entries.filter { type ->
                type.pattern?.let { Regex(it).containsMatchIn(code) } ?: false
            }
        }
    }
}

/**
 * Issue severity.
 */
enum class IssueSeverity(val weight: Int) {
    CRITICAL(4),   // Definite performance problem
    HIGH(3),       // Likely performance problem
    MEDIUM(2),     // Potential performance problem
    LOW(1);        // Minor optimization opportunity
}

/**
 * Configuration for performance linting.
 */
data class PerformanceLinterConfig(
    val enabled: Boolean = true,
    val minSeverity: IssueSeverity = IssueSeverity.MEDIUM,
    val enabledRules: Set<IssueType> = IssueType.entries.toSet(),
    val ignoreTestFiles: Boolean = true,
    val hotPathAnnotations: Set<String> = setOf("HotPath", "PerformanceCritical")
)
```

---

### v0.6.3b — PerformanceLinterService

**Goal:** Service to detect performance anti-patterns.

#### PerformanceLinterService.kt

```kotlin
package com.sidekick.quality.performance

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiElement

@Service(Service.Level.PROJECT)
@State(name = "SidekickPerformanceLinter", storages = [Storage("sidekick-perf.xml")])
class PerformanceLinterService(private val project: Project) : PersistentStateComponent<PerformanceLinterService.State> {

    data class State(var config: PerformanceLinterConfig = PerformanceLinterConfig())
    private var state = State()

    companion object {
        fun getInstance(project: Project): PerformanceLinterService {
            return project.getService(PerformanceLinterService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Analyzes a file for performance issues.
     */
    fun analyzeFile(psiFile: PsiFile): List<PerformanceIssue> {
        if (!state.config.enabled) return emptyList()
        if (state.config.ignoreTestFiles && isTestFile(psiFile)) return emptyList()

        val issues = mutableListOf<PerformanceIssue>()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                analyzeElement(element, issues)
                super.visitElement(element)
            }
        })

        return issues.filter { 
            it.severity.weight >= state.config.minSeverity.weight &&
            it.type in state.config.enabledRules
        }
    }

    private fun analyzeElement(element: PsiElement, issues: MutableList<PerformanceIssue>) {
        val text = element.text ?: return
        val file = element.containingFile
        val doc = file?.viewProvider?.document
        val line = doc?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: 0

        val detectedTypes = IssueType.detect(text)
        
        detectedTypes.forEach { type ->
            issues.add(PerformanceIssue(
                type = type,
                severity = getSeverity(type),
                location = IssueLocation(
                    filePath = file?.virtualFile?.path ?: "",
                    line = line,
                    codeSnippet = text.take(100)
                ),
                description = getDescription(type),
                suggestion = getSuggestion(type),
                estimatedImpact = getImpact(type)
            ))
        }
    }

    private fun getSeverity(type: IssueType): IssueSeverity {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> IssueSeverity.HIGH
            IssueType.ASYNC_VOID -> IssueSeverity.CRITICAL
            IssueType.SYNC_OVER_ASYNC -> IssueSeverity.CRITICAL
            IssueType.N_PLUS_ONE -> IssueSeverity.HIGH
            IssueType.ALLOCATION_IN_LOOP -> IssueSeverity.MEDIUM
            else -> IssueSeverity.MEDIUM
        }
    }

    private fun getDescription(type: IssueType): String {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> "String concatenation in loop creates many temporary objects"
            IssueType.ASYNC_VOID -> "Async void methods cannot be awaited and exceptions are unhandled"
            IssueType.SYNC_OVER_ASYNC -> "Blocking on async code can cause deadlocks"
            IssueType.LINQ_IN_HOT_PATH -> "LINQ operations allocate enumerators and delegates"
            IssueType.N_PLUS_ONE -> "Potential N+1 query pattern detected"
            else -> type.displayName
        }
    }

    private fun getSuggestion(type: IssueType): String {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> "Use StringBuilder instead"
            IssueType.ASYNC_VOID -> "Change to async Task"
            IssueType.SYNC_OVER_ASYNC -> "Use await instead of .Result or .Wait()"
            IssueType.LINQ_IN_HOT_PATH -> "Consider using a for loop or caching results"
            IssueType.N_PLUS_ONE -> "Use eager loading with Include() outside the loop"
            IssueType.REGEX_NOT_COMPILED -> "Use RegexOptions.Compiled or static Regex"
            else -> "Review this code for optimization"
        }
    }

    private fun getImpact(type: IssueType): String? {
        return when (type) {
            IssueType.STRING_CONCAT_LOOP -> "O(n²) allocations"
            IssueType.N_PLUS_ONE -> "n additional database queries"
            else -> null
        }
    }

    private fun isTestFile(file: PsiFile): Boolean {
        val path = file.virtualFile?.path?.lowercase() ?: return false
        return path.contains("/test") || path.contains("test.") || path.contains("spec.")
    }
}
```

---

## v0.6.4 — Dead Code Cemetery

### v0.6.4a — DeadCodeModels

**Goal:** Data structures for unused symbol management.

#### DeadCodeModels.kt

```kotlin
package com.sidekick.quality.deadcode

import java.time.Instant

/**
 * An unused symbol detected in code.
 */
data class DeadCodeSymbol(
    val name: String,
    val type: SymbolType,
    val location: SymbolLocation,
    val usageCount: Int,
    val lastUsedDate: Instant?,
    val confidence: Float,
    val canSafeDelete: Boolean
)

/**
 * Symbol location.
 */
data class SymbolLocation(
    val filePath: String,
    val line: Int,
    val className: String?,
    val memberName: String?
)

/**
 * Types of symbols.
 */
enum class SymbolType(val displayName: String) {
    CLASS("Class"),
    METHOD("Method"),
    PROPERTY("Property"),
    FIELD("Field"),
    PARAMETER("Parameter"),
    LOCAL_VARIABLE("Local Variable"),
    IMPORT("Import"),
    INTERFACE("Interface"),
    ENUM("Enum")
}

/**
 * Configuration for dead code detection.
 */
data class DeadCodeConfig(
    val enabled: Boolean = true,
    val includePrivate: Boolean = true,
    val includeInternal: Boolean = true,
    val excludePublicApi: Boolean = true,
    val excludePatterns: List<String> = listOf("*Test*", "*Mock*"),
    val minConfidence: Float = 0.8f
)

/**
 * Analysis result.
 */
data class DeadCodeAnalysisResult(
    val symbols: List<DeadCodeSymbol>,
    val totalLines: Int,
    val deadCodeLines: Int,
    val deadCodePercentage: Float
) {
    val byType: Map<SymbolType, List<DeadCodeSymbol>> get() = symbols.groupBy { it.type }
}
```

---

### v0.6.4b — DeadCodeService

**Goal:** Service to detect and manage unused code.

#### DeadCodeService.kt

```kotlin
package com.sidekick.quality.deadcode

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.safeDelete.SafeDeleteHandler

@Service(Service.Level.PROJECT)
@State(name = "SidekickDeadCode", storages = [Storage("sidekick-deadcode.xml")])
class DeadCodeService(private val project: Project) : PersistentStateComponent<DeadCodeService.State> {

    data class State(
        var config: DeadCodeConfig = DeadCodeConfig(),
        var knownDeadCode: MutableList<DeadCodeSymbol> = mutableListOf()
    )
    
    private var state = State()

    companion object {
        fun getInstance(project: Project): DeadCodeService {
            return project.getService(DeadCodeService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Analyzes the project for dead code.
     */
    fun analyzeProject(): DeadCodeAnalysisResult {
        if (!state.config.enabled) {
            return DeadCodeAnalysisResult(emptyList(), 0, 0, 0f)
        }

        val symbols = mutableListOf<DeadCodeSymbol>()
        
        // This would integrate with IDE's unused symbol analysis
        // For now, return cached results
        
        return DeadCodeAnalysisResult(
            symbols = state.knownDeadCode,
            totalLines = 0,
            deadCodeLines = state.knownDeadCode.size * 5, // Estimate
            deadCodePercentage = 0f
        )
    }

    /**
     * Gets all known dead code.
     */
    fun getDeadCode(): List<DeadCodeSymbol> = state.knownDeadCode.toList()

    /**
     * Gets dead code by type.
     */
    fun getDeadCodeByType(type: SymbolType): List<DeadCodeSymbol> {
        return state.knownDeadCode.filter { it.type == type }
    }

    /**
     * Safely deletes a symbol.
     */
    fun deleteSymbol(symbol: DeadCodeSymbol): Boolean {
        if (!symbol.canSafeDelete) return false
        
        // Would use SafeDeleteHandler from IDE
        state.knownDeadCode.removeIf { it.name == symbol.name }
        return true
    }

    /**
     * Batch deletes multiple symbols.
     */
    fun batchDelete(symbols: List<DeadCodeSymbol>): Int {
        var deleted = 0
        symbols.filter { it.canSafeDelete }.forEach { symbol ->
            if (deleteSymbol(symbol)) deleted++
        }
        return deleted
    }

    /**
     * Excludes a symbol from dead code detection.
     */
    fun excludeSymbol(symbol: DeadCodeSymbol) {
        state.knownDeadCode.removeIf { it.name == symbol.name }
    }

    /**
     * Refreshes dead code analysis.
     */
    fun refresh() {
        // Re-run analysis
    }
}
```

---

### v0.6.4c — DeadCodeToolWindow

**Goal:** Tool window for dead code management.

#### DeadCodeToolWindow.kt

```kotlin
package com.sidekick.quality.deadcode

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import javax.swing.*
import javax.swing.table.DefaultTableModel

class DeadCodeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DeadCodePanel(project)
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(panel, "", false)
        )
    }
}

class DeadCodePanel(private val project: Project) : JBPanel<DeadCodePanel>() {
    private val service = DeadCodeService.getInstance(project)
    private val tableModel = DefaultTableModel(
        arrayOf("", "Type", "Name", "Location", "Confidence"),
        0
    )
    private val table = JBTable(tableModel)

    init {
        layout = java.awt.BorderLayout()
        add(createToolbar(), java.awt.BorderLayout.NORTH)
        add(JBScrollPane(table), java.awt.BorderLayout.CENTER)
        add(createActionPanel(), java.awt.BorderLayout.SOUTH)
        
        refreshList()
    }

    private fun createToolbar(): JPanel = panel {
        row {
            button("Analyze Project") { analyzeProject() }
            button("Refresh") { refreshList() }
            label(" | Filter: ")
            comboBox(SymbolType.entries.map { it.displayName })
        }
    }

    private fun createActionPanel(): JPanel = panel {
        row {
            button("Delete Selected") { deleteSelected() }
            button("Delete All Safe") { deleteAllSafe() }
            button("Exclude Selected") { excludeSelected() }
            
            val result = service.analyzeProject()
            label("Total: ${result.symbols.size} symbols | ${result.deadCodePercentage}% dead code")
        }
    }

    private fun refreshList() {
        tableModel.rowCount = 0
        service.getDeadCode().forEach { symbol ->
            tableModel.addRow(arrayOf(
                if (symbol.canSafeDelete) "✓" else "⚠",
                symbol.type.displayName,
                symbol.name,
                "${symbol.location.filePath.substringAfterLast("/")}:${symbol.location.line}",
                "${(symbol.confidence * 100).toInt()}%"
            ))
        }
    }

    private fun analyzeProject() {
        service.analyzeProject()
        refreshList()
    }

    private fun deleteSelected() {
        val rows = table.selectedRows
        val symbols = service.getDeadCode()
        rows.reversed().forEach { row ->
            if (row < symbols.size) {
                service.deleteSymbol(symbols[row])
            }
        }
        refreshList()
    }

    private fun deleteAllSafe() {
        val safeSymbols = service.getDeadCode().filter { it.canSafeDelete }
        val count = service.batchDelete(safeSymbols)
        JOptionPane.showMessageDialog(this, "Deleted $count symbols")
        refreshList()
    }

    private fun excludeSelected() {
        val rows = table.selectedRows
        val symbols = service.getDeadCode()
        rows.forEach { row ->
            if (row < symbols.size) {
                service.excludeSymbol(symbols[row])
            }
        }
        refreshList()
    }
}
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Code Quality Services (v0.6.x) -->
    <projectService serviceImplementation="com.sidekick.quality.exceptions.ExceptionHunterService"/>
    <projectService serviceImplementation="com.sidekick.quality.todos.TodoService"/>
    <projectService serviceImplementation="com.sidekick.quality.performance.PerformanceLinterService"/>
    <projectService serviceImplementation="com.sidekick.quality.deadcode.DeadCodeService"/>
    
    <!-- Inspections -->
    <localInspection 
        implementationClass="com.sidekick.quality.exceptions.ExceptionHunterInspection"
        displayName="Unhandled Exception"
        groupName="Sidekick"
        enabledByDefault="true"
        level="WARNING"/>
    
    <localInspection 
        implementationClass="com.sidekick.quality.performance.PerformanceLinterInspection"
        displayName="Performance Issue"
        groupName="Sidekick"
        enabledByDefault="true"
        level="WEAK WARNING"/>
    
    <!-- Tool Windows -->
    <toolWindow id="TODO Tracker" 
                icon="/icons/todo.svg"
                anchor="bottom"
                factoryClass="com.sidekick.quality.todos.TodoToolWindowFactory"/>
                
    <toolWindow id="Dead Code" 
                icon="/icons/deadcode.svg"
                anchor="bottom"
                factoryClass="com.sidekick.quality.deadcode.DeadCodeToolWindowFactory"/>
</extensions>

<actions>
    <action id="Sidekick.AnalyzeExceptions"
            class="com.sidekick.quality.exceptions.AnalyzeExceptionsAction"
            text="Analyze Unhandled Exceptions">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.ScanTodos"
            class="com.sidekick.quality.todos.ScanTodosAction"
            text="Scan for TODOs">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.AnalyzePerformance"
            class="com.sidekick.quality.performance.AnalyzePerformanceAction"
            text="Analyze Performance Issues">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.FindDeadCode"
            class="com.sidekick.quality.deadcode.FindDeadCodeAction"
            text="Find Dead Code">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
</actions>
```

---

## Verification Plan

### Automated Tests

```bash
# Run all v0.6.x tests
./gradlew test --tests "com.sidekick.quality.*"
```

### Manual Verification

| Version | Step | Expected Result |
|---------|------|-----------------|
| v0.6.1 | Open file with throw statement | Gutter warning appears |
| v0.6.1 | Click quick fix | Try-catch added |
| v0.6.2 | Open TODO tool window | All TODOs listed |
| v0.6.2 | Add TODO with deadline | Appears with due date |
| v0.6.3 | Write string concat in loop | Warning appears |
| v0.6.4 | Run dead code analysis | Unused symbols listed |

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Exception detection false positive rate | <5% |
| TODO extraction accuracy | >99% |
| Performance linter false positive rate | <10% |
| Dead code detection accuracy | >90% |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.6.0 | 2026-02-04 | Ryan | Initial v0.6.x design specification |
