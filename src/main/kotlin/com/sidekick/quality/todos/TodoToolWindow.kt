package com.sidekick.quality.todos

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer
import java.awt.Component
import java.awt.Color

/**
 * # TODO Tool Window
 *
 * IDE tool window for TODO management and navigation.
 * Part of Sidekick v0.6.2 TODO Tracker feature.
 *
 * ## Features
 *
 * - Table view of all project TODOs
 * - Filtering by type, status, priority
 * - Click-to-navigate to TODO location
 * - Status bar with counts
 * - Color-coded urgency indicators
 *
 * @since 0.6.2
 */
class TodoToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TodoPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Main panel for the TODO tool window.
 */
class TodoPanel(private val project: Project) : JBPanel<TodoPanel>() {

    private val service = TodoService.getInstance(project)

    private val tableModel = object : DefaultTableModel(
        arrayOf("", "Type", "Priority", "Text", "File", "Line", "Due"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val table = JBTable(tableModel).apply {
        // Set column widths
        columnModel.getColumn(0).preferredWidth = 30  // Status icon
        columnModel.getColumn(1).preferredWidth = 80  // Type
        columnModel.getColumn(2).preferredWidth = 70  // Priority
        columnModel.getColumn(3).preferredWidth = 300 // Text
        columnModel.getColumn(4).preferredWidth = 150 // File
        columnModel.getColumn(5).preferredWidth = 50  // Line
        columnModel.getColumn(6).preferredWidth = 100 // Due

        // Custom renderer for status column
        columnModel.getColumn(0).cellRenderer = StatusCellRenderer()
        columnModel.getColumn(6).cellRenderer = DeadlineCellRenderer()
    }

    private var currentFilter: TodoFilter = TodoFilter.ALL
    private var cachedTodos: List<TodoItem> = emptyList()

    init {
        layout = BorderLayout()
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)

        // Double-click to navigate
        table.selectionModel.addListSelectionListener { 
            if (!it.valueIsAdjusting) {
                // Single click handling if needed
            }
        }

        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelected()
                }
            }
        })

        refreshTodos()
    }

    private fun createToolbar(): JPanel = panel {
        row {
            button("Refresh") { refreshTodos() }
            button("Scan Project") { scanProject() }
            label(" | Filter: ")
            comboBox(TodoFilter.entries.toList()).applyToComponent {
                addActionListener {
                    currentFilter = selectedItem as? TodoFilter ?: TodoFilter.ALL
                    applyFilter()
                }
            }
        }
    }

    private fun createStatusBar(): JPanel {
        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

        val summary = service.getSummary()
        val statusText = buildString {
            append("Total: ${summary.total}")
            if (summary.overdue > 0) append(" | ⚠️ Overdue: ${summary.overdue}")
            if (summary.dueSoon > 0) append(" | ⏰ Due Soon: ${summary.dueSoon}")
        }

        statusPanel.add(JLabel(statusText), BorderLayout.WEST)
        return statusPanel
    }

    /**
     * Refreshes the TODO list from the service.
     */
    fun refreshTodos() {
        cachedTodos = service.getAllTodos()
        applyFilter()
        updateStatusBar()
    }

    private fun applyFilter() {
        tableModel.rowCount = 0

        val filtered = when (currentFilter) {
            TodoFilter.ALL -> cachedTodos
            TodoFilter.OVERDUE -> cachedTodos.filter { it.isOverdue }
            TodoFilter.DUE_SOON -> cachedTodos.filter { it.status == TodoStatus.DUE_SOON }
            TodoFilter.TODO -> cachedTodos.filter { it.type == TodoType.TODO }
            TodoFilter.FIXME -> cachedTodos.filter { it.type == TodoType.FIXME }
            TodoFilter.BUG -> cachedTodos.filter { it.type == TodoType.BUG }
            TodoFilter.CRITICAL -> cachedTodos.filter { it.priority == TodoPriority.CRITICAL }
            TodoFilter.HIGH -> cachedTodos.filter { it.priority == TodoPriority.HIGH }
        }

        // Sort by priority (highest first), then by deadline
        val sorted = filtered.sortedWith(
            compareByDescending<TodoItem> { it.priority.weight }
                .thenBy { it.deadline ?: LocalDate.MAX }
        )

        sorted.forEach { todo ->
            tableModel.addRow(arrayOf(
                todo.status.displayName,
                "${todo.type.icon} ${todo.type.name}",
                todo.priority.name,
                todo.text.take(80),
                todo.location.fileName,
                todo.location.line,
                todo.deadlineDisplay
            ))
        }
    }

    private fun updateStatusBar() {
        // Would need to refresh the status bar panel
        // For simplicity, the status bar is created once
    }

    private fun scanProject() {
        // Would scan all project files for TODOs
        // This requires iterating through project files
        refreshTodos()
    }

    private fun navigateToSelected() {
        val row = table.selectedRow
        if (row < 0) return

        val filtered = getFilteredTodos()
        if (row >= filtered.size) return

        val todo = filtered[row]
        LocalFileSystem.getInstance().findFileByPath(todo.location.filePath)?.let { vf ->
            val descriptor = OpenFileDescriptor(project, vf, todo.location.line - 1, 0)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }

    private fun getFilteredTodos(): List<TodoItem> {
        val filtered = when (currentFilter) {
            TodoFilter.ALL -> cachedTodos
            TodoFilter.OVERDUE -> cachedTodos.filter { it.isOverdue }
            TodoFilter.DUE_SOON -> cachedTodos.filter { it.status == TodoStatus.DUE_SOON }
            TodoFilter.TODO -> cachedTodos.filter { it.type == TodoType.TODO }
            TodoFilter.FIXME -> cachedTodos.filter { it.type == TodoType.FIXME }
            TodoFilter.BUG -> cachedTodos.filter { it.type == TodoType.BUG }
            TodoFilter.CRITICAL -> cachedTodos.filter { it.priority == TodoPriority.CRITICAL }
            TodoFilter.HIGH -> cachedTodos.filter { it.priority == TodoPriority.HIGH }
        }

        return filtered.sortedWith(
            compareByDescending<TodoItem> { it.priority.weight }
                .thenBy { it.deadline ?: LocalDate.MAX }
        )
    }

    companion object {
        private val LocalDate.Companion.MAX: LocalDate
            get() = LocalDate.of(9999, 12, 31)
    }
}

/**
 * Filter options for the TODO list.
 */
enum class TodoFilter(val displayName: String) {
    ALL("All"),
    OVERDUE("Overdue"),
    DUE_SOON("Due Soon"),
    TODO("TODOs"),
    FIXME("FIXMEs"),
    BUG("Bugs"),
    CRITICAL("Critical"),
    HIGH("High Priority");

    override fun toString(): String = displayName
}

/**
 * Cell renderer for status column with color coding.
 */
private class StatusCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (!isSelected) {
            background = when (value?.toString()) {
                "Overdue" -> Color(255, 200, 200)
                "Due Soon" -> Color(255, 235, 200)
                else -> table.background
            }
        }

        return component
    }
}

/**
 * Cell renderer for deadline column with urgency colors.
 */
private class DeadlineCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (!isSelected) {
            val text = value?.toString() ?: ""
            foreground = when {
                text.contains("Overdue") -> Color(200, 50, 50)
                text.contains("today") -> Color(200, 100, 50)
                text.contains("tomorrow") -> Color(200, 150, 50)
                else -> table.foreground
            }
        }

        return component
    }
}

// Import for LocalDate
private typealias LocalDate = java.time.LocalDate
