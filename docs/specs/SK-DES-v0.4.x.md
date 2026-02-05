# Sidekick v0.4.x – Navigation & Productivity Phase

> **Phase Goal:** Enhanced code navigation and developer workflow acceleration  
> **Building On:** v0.3.x Code Generation (DocGen, TestGen, CommitGen, Naming, DTO)

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.4.1 | Jump-to-Test | Bi-directional source↔test navigation |
| v0.4.2 | Bookmark Workspaces | Named bookmark/breakpoint collections |
| v0.4.3 | Snippet Pocket | Multi-slot clipboard with persistence |
| v0.4.4 | Recent Files Grid | Visual file browser with preview |
| v0.4.5 | Copy as Markdown | Syntax-highlighted code copying |

---

## v0.4.1 — Jump-to-Test

### v0.4.1a — TestNavigationModels

**Goal:** Data structures for test file discovery and navigation.

#### TestNavigationModels.kt

```kotlin
package com.sidekick.navigation.testjump

/**
 * Represents a source-test file mapping.
 */
data class TestMapping(
    val sourceFile: String,
    val testFile: String?,
    val convention: TestConvention,
    val exists: Boolean
) {
    val canNavigate: Boolean get() = testFile != null && exists
    val canCreate: Boolean get() = testFile != null && !exists
}

/**
 * Test file naming conventions.
 */
enum class TestConvention(
    val displayName: String,
    val suffix: String,
    val pattern: String
) {
    TESTS("Tests suffix", "Tests", "{Name}Tests"),
    TEST("Test suffix", "Test", "{Name}Test"),
    SPEC("Spec suffix", "Spec", "{Name}Spec"),
    SHOULD("Should suffix", "Should", "{Name}Should"),
    UNDERSCORE_TEST("Underscore test", "_test", "{Name}_test");
    
    fun toTestName(sourceName: String): String {
        return pattern.replace("{Name}", sourceName)
    }
    
    fun toSourceName(testName: String): String? {
        return when {
            testName.endsWith(suffix) -> testName.dropLast(suffix.length)
            else -> null
        }
    }
    
    companion object {
        fun detect(testFileName: String): TestConvention? {
            return entries.find { testFileName.endsWith(it.suffix) }
        }
        
        val DEFAULT = TESTS
    }
}

/**
 * Test directory configuration.
 */
data class TestDirectoryConfig(
    val sourceRoot: String,
    val testRoot: String,
    val convention: TestConvention,
    val mirrorPackageStructure: Boolean = true
)

/**
 * Navigation result.
 */
sealed class NavigationResult {
    data class Found(val path: String) : NavigationResult()
    data class NotFound(val suggestedPath: String) : NavigationResult()
    data class Multiple(val options: List<String>) : NavigationResult()
    data class Error(val message: String) : NavigationResult()
}
```

#### Acceptance Criteria

- [ ] TestMapping captures source/test relationship
- [ ] TestConvention covers major naming patterns
- [ ] NavigationResult handles all navigation outcomes

---

### v0.4.1b — TestNavigationService

**Goal:** Service to discover and navigate between source and test files.

#### TestNavigationService.kt

```kotlin
package com.sidekick.navigation.testjump

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

@Service(Service.Level.PROJECT)
class TestNavigationService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): TestNavigationService {
            return project.getService(TestNavigationService::class.java)
        }
        
        val DEFAULT_TEST_ROOTS = listOf("test", "tests", "spec", "specs", "__tests__")
        val DEFAULT_SOURCE_ROOTS = listOf("src", "main", "lib", "source")
    }

    /**
     * Finds the corresponding test file for a source file.
     */
    fun findTestForSource(sourceFile: VirtualFile): NavigationResult {
        val config = detectConfig(sourceFile)
        val testPath = computeTestPath(sourceFile, config)
        
        return when {
            testPath == null -> NavigationResult.Error("Cannot determine test path")
            fileExists(testPath) -> NavigationResult.Found(testPath)
            else -> NavigationResult.NotFound(testPath)
        }
    }

    /**
     * Finds the corresponding source file for a test file.
     */
    fun findSourceForTest(testFile: VirtualFile): NavigationResult {
        val convention = TestConvention.detect(testFile.nameWithoutExtension)
            ?: return NavigationResult.Error("Unknown test convention")
        
        val sourceName = convention.toSourceName(testFile.nameWithoutExtension)
            ?: return NavigationResult.Error("Cannot extract source name")
        
        val sourcePath = computeSourcePath(testFile, sourceName)
        
        return when {
            sourcePath == null -> NavigationResult.Error("Cannot determine source path")
            fileExists(sourcePath) -> NavigationResult.Found(sourcePath)
            else -> NavigationResult.NotFound(sourcePath)
        }
    }

    /**
     * Determines if current file is a test file.
     */
    fun isTestFile(file: VirtualFile): Boolean {
        val path = file.path.lowercase()
        val name = file.nameWithoutExtension
        
        return DEFAULT_TEST_ROOTS.any { path.contains("/$it/") } ||
               TestConvention.entries.any { name.endsWith(it.suffix) }
    }

    /**
     * Creates a test file for the given source.
     */
    fun createTestFile(sourceFile: VirtualFile): NavigationResult {
        val config = detectConfig(sourceFile)
        val testPath = computeTestPath(sourceFile, config)
            ?: return NavigationResult.Error("Cannot determine test path")
        
        // Create directory structure and file
        createFile(testPath, generateTestStub(sourceFile, config))
        
        return NavigationResult.Found(testPath)
    }

    private fun detectConfig(file: VirtualFile): TestDirectoryConfig {
        // Detect based on project structure
        return TestDirectoryConfig(
            sourceRoot = "src/main",
            testRoot = "src/test",
            convention = TestConvention.DEFAULT
        )
    }

    private fun computeTestPath(source: VirtualFile, config: TestDirectoryConfig): String? {
        val sourcePath = source.path
        val relativePath = sourcePath.substringAfter(config.sourceRoot)
        val testName = config.convention.toTestName(source.nameWithoutExtension)
        return "${config.testRoot}$relativePath".replace(
            source.nameWithoutExtension,
            testName
        )
    }

    private fun computeSourcePath(test: VirtualFile, sourceName: String): String? {
        val testPath = test.path
        val sourceRoot = testPath.replace(Regex("/tests?/"), "/main/")
        return sourceRoot.replace(test.nameWithoutExtension, sourceName)
    }

    private fun fileExists(path: String): Boolean = java.io.File(path).exists()
    private fun createFile(path: String, content: String) { /* impl */ }
    private fun generateTestStub(source: VirtualFile, config: TestDirectoryConfig): String = ""
}
```

---

### v0.4.1c — JumpToTestAction

**Goal:** Editor action with keyboard shortcut for test navigation.

#### JumpToTestAction.kt

```kotlin
package com.sidekick.navigation.testjump

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Action to toggle between source and test file.
 * 
 * Keyboard: Alt+Shift+T
 */
class JumpToTestAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = TestNavigationService.getInstance(project)
        
        val result = if (service.isTestFile(file)) {
            service.findSourceForTest(file)
        } else {
            service.findTestForSource(file)
        }
        
        when (result) {
            is NavigationResult.Found -> {
                openFile(project, result.path)
            }
            is NavigationResult.NotFound -> {
                val create = Messages.showYesNoDialog(
                    project,
                    "Test file not found. Create ${result.suggestedPath}?",
                    "Create Test File",
                    Messages.getQuestionIcon()
                )
                if (create == Messages.YES) {
                    service.createTestFile(file)
                    openFile(project, result.suggestedPath)
                }
            }
            is NavigationResult.Multiple -> {
                // Show popup for selection
            }
            is NavigationResult.Error -> {
                Messages.showErrorDialog(project, result.message, "Navigation Error")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && 
            e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    private fun openFile(project: com.intellij.openapi.project.Project, path: String) {
        LocalFileSystem.getInstance().findFileByPath(path)?.let { vf ->
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }
}
```

#### plugin.xml

```xml
<action id="Sidekick.JumpToTest"
        class="com.sidekick.navigation.testjump.JumpToTestAction"
        text="Jump to Test/Source"
        description="Toggle between source and test file">
    <add-to-group group-id="GoToMenu" anchor="after" relative-to-action="GotoTest"/>
    <add-to-group group-id="Sidekick.ActionGroup"/>
    <keyboard-shortcut keymap="$default" first-keystroke="alt shift T"/>
</action>
```

---

## v0.4.2 — Bookmark Workspaces

### v0.4.2a — WorkspaceModels

**Goal:** Data structures for bookmark/breakpoint workspaces.

#### WorkspaceModels.kt

```kotlin
package com.sidekick.navigation.workspaces

import java.time.Instant
import java.util.UUID

/**
 * A named collection of bookmarks and breakpoints.
 */
data class BookmarkWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val bookmarks: List<SavedBookmark> = emptyList(),
    val breakpoints: List<SavedBreakpoint> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val modifiedAt: Instant = Instant.now()
) {
    val isEmpty: Boolean get() = bookmarks.isEmpty() && breakpoints.isEmpty()
    val totalItems: Int get() = bookmarks.size + breakpoints.size
}

/**
 * A saved bookmark.
 */
data class SavedBookmark(
    val filePath: String,
    val line: Int,
    val mnemonic: Char? = null,
    val description: String = ""
)

/**
 * A saved breakpoint.
 */
data class SavedBreakpoint(
    val filePath: String,
    val line: Int,
    val condition: String? = null,
    val logExpression: String? = null,
    val enabled: Boolean = true
)

/**
 * Workspace export format.
 */
data class WorkspaceExport(
    val version: Int = 1,
    val workspaces: List<BookmarkWorkspace>,
    val exportedAt: Instant = Instant.now()
)
```

---

### v0.4.2b — WorkspaceService

**Goal:** Service to manage bookmark workspaces.

#### WorkspaceService.kt

```kotlin
package com.sidekick.navigation.workspaces

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.ide.bookmark.BookmarksManager

@Service(Service.Level.PROJECT)
@State(name = "SidekickWorkspaces", storages = [Storage("sidekick-workspaces.xml")])
class WorkspaceService(private val project: Project) : PersistentStateComponent<WorkspaceService.State> {

    data class State(
        var workspaces: MutableList<BookmarkWorkspace> = mutableListOf(),
        var activeWorkspaceId: String? = null
    )

    private var state = State()

    companion object {
        fun getInstance(project: Project): WorkspaceService {
            return project.getService(WorkspaceService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    fun getWorkspaces(): List<BookmarkWorkspace> = state.workspaces.toList()
    
    fun createWorkspace(name: String): BookmarkWorkspace {
        val workspace = BookmarkWorkspace(name = name)
        state.workspaces.add(workspace)
        return workspace
    }

    fun saveCurrentState(workspaceId: String) {
        val workspace = state.workspaces.find { it.id == workspaceId } ?: return
        val bookmarks = captureBookmarks()
        val breakpoints = captureBreakpoints()
        
        val updated = workspace.copy(
            bookmarks = bookmarks,
            breakpoints = breakpoints,
            modifiedAt = java.time.Instant.now()
        )
        
        state.workspaces.replaceAll { if (it.id == workspaceId) updated else it }
    }

    fun restoreWorkspace(workspaceId: String) {
        val workspace = state.workspaces.find { it.id == workspaceId } ?: return
        clearCurrentBookmarks()
        clearCurrentBreakpoints()
        workspace.bookmarks.forEach { restoreBookmark(it) }
        workspace.breakpoints.forEach { restoreBreakpoint(it) }
        state.activeWorkspaceId = workspaceId
    }

    fun deleteWorkspace(workspaceId: String) {
        state.workspaces.removeIf { it.id == workspaceId }
    }

    fun exportWorkspaces(): WorkspaceExport {
        return WorkspaceExport(workspaces = state.workspaces)
    }

    fun importWorkspaces(export: WorkspaceExport) {
        state.workspaces.addAll(export.workspaces)
    }

    private fun captureBookmarks(): List<SavedBookmark> = emptyList() // IDE API
    private fun captureBreakpoints(): List<SavedBreakpoint> = emptyList() // IDE API
    private fun clearCurrentBookmarks() { /* IDE API */ }
    private fun clearCurrentBreakpoints() { /* IDE API */ }
    private fun restoreBookmark(bookmark: SavedBookmark) { /* IDE API */ }
    private fun restoreBreakpoint(breakpoint: SavedBreakpoint) { /* IDE API */ }
}
```

---

### v0.4.2c — WorkspaceToolWindow

**Goal:** UI panel for workspace management.

#### WorkspaceToolWindow.kt

```kotlin
package com.sidekick.navigation.workspaces

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import javax.swing.*

class WorkspaceToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WorkspacePanel(project)
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(panel, "", false)
        )
    }
}

class WorkspacePanel(private val project: Project) : JBPanel<WorkspacePanel>() {
    private val service = WorkspaceService.getInstance(project)
    private val listModel = DefaultListModel<BookmarkWorkspace>()
    private val workspaceList = JBList(listModel)

    init {
        layout = java.awt.BorderLayout()
        add(createToolbar(), java.awt.BorderLayout.NORTH)
        add(JBScrollPane(workspaceList), java.awt.BorderLayout.CENTER)
        refreshList()
    }

    private fun createToolbar(): JPanel = panel {
        row {
            button("New") { createWorkspace() }
            button("Save") { saveCurrentWorkspace() }
            button("Restore") { restoreSelectedWorkspace() }
            button("Delete") { deleteSelectedWorkspace() }
        }
    }

    private fun refreshList() {
        listModel.clear()
        service.getWorkspaces().forEach { listModel.addElement(it) }
    }

    private fun createWorkspace() {
        val name = JOptionPane.showInputDialog("Workspace name:")
        if (!name.isNullOrBlank()) {
            service.createWorkspace(name)
            refreshList()
        }
    }

    private fun saveCurrentWorkspace() {
        workspaceList.selectedValue?.let { service.saveCurrentState(it.id) }
    }

    private fun restoreSelectedWorkspace() {
        workspaceList.selectedValue?.let { service.restoreWorkspace(it.id) }
    }

    private fun deleteSelectedWorkspace() {
        workspaceList.selectedValue?.let {
            service.deleteWorkspace(it.id)
            refreshList()
        }
    }
}
```

---

## v0.4.3 — Snippet Pocket

### v0.4.3a — SnippetModels

#### SnippetModels.kt

```kotlin
package com.sidekick.navigation.snippets

import java.time.Instant

/**
 * A saved code snippet.
 */
data class Snippet(
    val id: Int,
    val content: String,
    val language: String?,
    val sourceFile: String?,
    val lineRange: IntRange?,
    val savedAt: Instant = Instant.now(),
    val label: String? = null
) {
    val preview: String get() = content.take(100).replace("\n", " ")
}

/**
 * Snippet pocket with multiple slots.
 */
data class SnippetPocket(
    val slots: List<Snippet?> = List(10) { null },
    val maxSlots: Int = 10
) {
    fun add(snippet: Snippet): SnippetPocket {
        val newSlots = slots.toMutableList()
        newSlots.add(0, snippet)
        return copy(slots = newSlots.take(maxSlots))
    }

    fun get(index: Int): Snippet? = slots.getOrNull(index)
    
    fun setSlot(index: Int, snippet: Snippet): SnippetPocket {
        val newSlots = slots.toMutableList()
        newSlots[index] = snippet
        return copy(slots = newSlots)
    }
}
```

---

### v0.4.3b — SnippetService

#### SnippetService.kt

```kotlin
package com.sidekick.navigation.snippets

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import java.awt.Toolkit
import java.awt.datatransfer.*

@Service(Service.Level.APP)
@State(name = "SidekickSnippets", storages = [Storage("sidekick-snippets.xml")])
class SnippetService : PersistentStateComponent<SnippetService.State> {

    data class State(var pocket: SnippetPocket = SnippetPocket())
    private var state = State()

    companion object {
        fun getInstance(): SnippetService {
            return ApplicationManager.getApplication().getService(SnippetService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    fun captureSelection(content: String, language: String?, file: String?, lines: IntRange?) {
        val snippet = Snippet(
            id = System.currentTimeMillis().toInt(),
            content = content,
            language = language,
            sourceFile = file,
            lineRange = lines
        )
        state.pocket = state.pocket.add(snippet)
    }

    fun getSnippet(slot: Int): Snippet? = state.pocket.get(slot)

    fun pasteSnippet(slot: Int): String? {
        val snippet = getSnippet(slot) ?: return null
        setClipboard(snippet.content)
        return snippet.content
    }

    fun getAllSnippets(): List<Snippet?> = state.pocket.slots

    private fun setClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
}
```

---

### v0.4.3c — SnippetActions

#### SnippetActions.kt

```kotlin
package com.sidekick.navigation.snippets

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor

/**
 * Capture current selection to snippet pocket.
 * Keyboard: Ctrl+Shift+C (then 0-9 for slot)
 */
class CaptureSnippetAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return
        
        val language = file?.extension
        val lines = editor.selectionModel.let {
            val doc = editor.document
            val start = doc.getLineNumber(it.selectionStart)
            val end = doc.getLineNumber(it.selectionEnd)
            start..end
        }
        
        SnippetService.getInstance().captureSelection(
            content = selection,
            language = language,
            file = file?.path,
            lines = lines
        )
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}

/**
 * Paste snippet from slot.
 * Keyboard: Ctrl+Shift+V, then 0-9
 */
class PasteSnippetAction(private val slot: Int) : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val content = SnippetService.getInstance().pasteSnippet(slot) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(e.project) {
            editor.document.insertString(editor.caretModel.offset, content)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
```

---

## v0.4.4 — Recent Files Grid

### v0.4.4a — RecentFilesModels

#### RecentFilesModels.kt

```kotlin
package com.sidekick.navigation.recentfiles

import java.time.Instant

/**
 * A recent file entry with metadata.
 */
data class RecentFileEntry(
    val path: String,
    val name: String,
    val extension: String?,
    val projectPath: String?,
    val lastOpened: Instant,
    val openCount: Int = 1,
    val pinned: Boolean = false
) {
    val displayName: String get() = if (pinned) "📌 $name" else name
}

/**
 * Grouping options for recent files.
 */
enum class FileGrouping {
    NONE,
    BY_FOLDER,
    BY_PROJECT,
    BY_EXTENSION,
    BY_DATE
}

/**
 * View options for the grid.
 */
data class GridViewOptions(
    val grouping: FileGrouping = FileGrouping.BY_FOLDER,
    val showPreview: Boolean = true,
    val gridColumns: Int = 4,
    val showHiddenFiles: Boolean = false
)
```

---

### v0.4.4b — RecentFilesService

#### RecentFilesService.kt

```kotlin
package com.sidekick.navigation.recentfiles

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.APP)
@State(name = "SidekickRecentFiles", storages = [Storage("sidekick-recent.xml")])
class RecentFilesService : PersistentStateComponent<RecentFilesService.State> {

    data class State(
        var entries: MutableList<RecentFileEntry> = mutableListOf(),
        var maxEntries: Int = 50
    )

    private var state = State()

    companion object {
        fun getInstance(): RecentFilesService {
            return ApplicationManager.getApplication().getService(RecentFilesService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    fun recordFileOpen(file: VirtualFile, project: Project) {
        val existing = state.entries.find { it.path == file.path }
        
        val entry = if (existing != null) {
            existing.copy(
                lastOpened = java.time.Instant.now(),
                openCount = existing.openCount + 1
            )
        } else {
            RecentFileEntry(
                path = file.path,
                name = file.name,
                extension = file.extension,
                projectPath = project.basePath,
                lastOpened = java.time.Instant.now()
            )
        }
        
        state.entries.removeIf { it.path == file.path }
        state.entries.add(0, entry)
        
        if (state.entries.size > state.maxEntries) {
            state.entries = state.entries.take(state.maxEntries).toMutableList()
        }
    }

    fun getRecentFiles(options: GridViewOptions): List<RecentFileEntry> {
        val files = state.entries.filter { options.showHiddenFiles || !it.name.startsWith(".") }
        return when (options.grouping) {
            FileGrouping.BY_DATE -> files.sortedByDescending { it.lastOpened }
            FileGrouping.BY_FOLDER -> files.sortedBy { it.path.substringBeforeLast("/") }
            else -> files
        }
    }

    fun togglePin(path: String) {
        state.entries.replaceAll { 
            if (it.path == path) it.copy(pinned = !it.pinned) else it 
        }
    }

    fun removeEntry(path: String) {
        state.entries.removeIf { it.path == path }
    }
}
```

---

### v0.4.4c — RecentFilesPopup

#### RecentFilesPopup.kt

```kotlin
package com.sidekick.navigation.recentfiles

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.*
import javax.swing.*

/**
 * Show recent files grid popup.
 * Keyboard: Ctrl+Shift+R
 */
class ShowRecentFilesGridAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RecentFilesService.getInstance()
        val files = service.getRecentFiles(GridViewOptions())
        
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(RecentFilesGrid(project, files), null)
            .setTitle("Recent Files")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .createPopup()
        
        popup.showCenteredInCurrentWindow(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

class RecentFilesGrid(
    private val project: com.intellij.openapi.project.Project,
    private val files: List<RecentFileEntry>
) : JBPanel<RecentFilesGrid>() {
    
    init {
        layout = java.awt.GridLayout(0, 4, 8, 8)
        files.forEach { entry ->
            add(createFileCard(entry))
        }
    }

    private fun createFileCard(entry: RecentFileEntry): JComponent {
        return JButton(entry.displayName).apply {
            addActionListener {
                LocalFileSystem.getInstance().findFileByPath(entry.path)?.let { vf ->
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            }
        }
    }
}
```

---

## v0.4.5 — Copy as Markdown

### v0.4.5a — MarkdownCopyModels

#### MarkdownCopyModels.kt

```kotlin
package com.sidekick.navigation.markdown

/**
 * Options for markdown code copying.
 */
data class MarkdownCopyOptions(
    val includeFilePath: Boolean = true,
    val includeLineNumbers: Boolean = false,
    val includeLanguage: Boolean = true,
    val wrapInDetails: Boolean = false,
    val maxLines: Int? = null
)

/**
 * Formatted markdown result.
 */
data class MarkdownCode(
    val markdown: String,
    val lineCount: Int,
    val language: String?
) {
    companion object {
        fun format(
            code: String,
            language: String?,
            filePath: String?,
            options: MarkdownCopyOptions
        ): MarkdownCode {
            val lines = code.lines()
            val truncated = options.maxLines?.let { lines.take(it) } ?: lines
            
            val content = buildString {
                if (options.includeFilePath && filePath != null) {
                    appendLine("**`$filePath`**")
                    appendLine()
                }
                
                if (options.wrapInDetails) {
                    appendLine("<details>")
                    appendLine("<summary>Code</summary>")
                    appendLine()
                }
                
                append("```")
                if (options.includeLanguage && language != null) append(language)
                appendLine()
                
                if (options.includeLineNumbers) {
                    truncated.forEachIndexed { i, line ->
                        appendLine("${(i + 1).toString().padStart(3)} | $line")
                    }
                } else {
                    truncated.forEach { appendLine(it) }
                }
                
                appendLine("```")
                
                if (options.wrapInDetails) {
                    appendLine("</details>")
                }
            }
            
            return MarkdownCode(content, lines.size, language)
        }
    }
}
```

---

### v0.4.5b — CopyAsMarkdownAction

#### CopyAsMarkdownAction.kt

```kotlin
package com.sidekick.navigation.markdown

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/**
 * Copy selected code as markdown.
 * Keyboard: Ctrl+Shift+M
 */
class CopyAsMarkdownAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return
        
        val options = MarkdownCopyOptions()
        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = file?.path,
            options = options
        )
        
        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}

/**
 * Copy as markdown with options dialog.
 */
class CopyAsMarkdownWithOptionsAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return
        
        // Show options dialog, then copy
        val options = showOptionsDialog() ?: return
        
        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = file?.path,
            options = options
        )
        
        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }

    private fun showOptionsDialog(): MarkdownCopyOptions? {
        // Show dialog with checkboxes for options
        return MarkdownCopyOptions()
    }
}
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Navigation Services (v0.4.x) -->
    <projectService serviceImplementation="com.sidekick.navigation.testjump.TestNavigationService"/>
    <projectService serviceImplementation="com.sidekick.navigation.workspaces.WorkspaceService"/>
    <applicationService serviceImplementation="com.sidekick.navigation.snippets.SnippetService"/>
    <applicationService serviceImplementation="com.sidekick.navigation.recentfiles.RecentFilesService"/>
    
    <!-- Tool Window -->
    <toolWindow id="Bookmark Workspaces" 
                anchor="right"
                factoryClass="com.sidekick.navigation.workspaces.WorkspaceToolWindowFactory"/>
</extensions>

<actions>
    <!-- Jump to Test (v0.4.1) -->
    <action id="Sidekick.JumpToTest"
            class="com.sidekick.navigation.testjump.JumpToTestAction"
            text="Jump to Test/Source"
            description="Toggle between source and test file">
        <add-to-group group-id="GoToMenu"/>
        <add-to-group group-id="Sidekick.ActionGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="alt shift T"/>
    </action>
    
    <!-- Snippet Pocket (v0.4.3) -->
    <action id="Sidekick.CaptureSnippet"
            class="com.sidekick.navigation.snippets.CaptureSnippetAction"
            text="Capture to Snippet Pocket">
        <add-to-group group-id="Sidekick.ActionGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift C"/>
    </action>
    
    <!-- Recent Files Grid (v0.4.4) -->
    <action id="Sidekick.RecentFilesGrid"
            class="com.sidekick.navigation.recentfiles.ShowRecentFilesGridAction"
            text="Recent Files Grid">
        <add-to-group group-id="Sidekick.ActionGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift R"/>
    </action>
    
    <!-- Copy as Markdown (v0.4.5) -->
    <action id="Sidekick.CopyAsMarkdown"
            class="com.sidekick.navigation.markdown.CopyAsMarkdownAction"
            text="Copy as Markdown">
        <add-to-group group-id="EditorPopupMenu"/>
        <add-to-group group-id="Sidekick.ActionGroup"/>
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift M"/>
    </action>
</actions>
```

---

## Verification Plan

### Automated Tests

```bash
# Run all v0.4.x tests
./gradlew test --tests "com.sidekick.navigation.*"
```

### Manual Verification

| Version | Step | Expected Result |
|---------|------|-----------------|
| v0.4.1 | Open source file, Alt+Shift+T | Navigates to test file |
| v0.4.1 | Open test file, Alt+Shift+T | Navigates to source file |
| v0.4.2 | Create workspace, add bookmarks, restore | Bookmarks restored |
| v0.4.3 | Select code, Ctrl+Shift+C | Snippet captured |
| v0.4.4 | Ctrl+Shift+R | Grid popup with recent files |
| v0.4.5 | Select code, Ctrl+Shift+M | Markdown copied to clipboard |

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Jump-to-test accuracy | >95% for conventional patterns |
| Workspace restore time | <100ms |
| Snippet pocket slots | 10 persistent slots |
| Recent files display | <50ms popup |
| Markdown format compliance | 100% GitHub-compatible |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.4.0 | 2026-02-04 | Ryan | Initial v0.4.x design specification |
