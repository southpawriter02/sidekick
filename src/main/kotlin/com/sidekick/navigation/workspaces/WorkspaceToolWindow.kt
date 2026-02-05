package com.sidekick.navigation.workspaces

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * # Workspace Tool Window
 *
 * UI panel for managing bookmark/breakpoint workspaces.
 * Part of Sidekick v0.4.2 Bookmark Workspaces feature.
 *
 * ## Features
 *
 * - List all workspaces with status
 * - Create, save, restore, and delete workspaces
 * - View workspace contents (bookmarks/breakpoints)
 *
 * @since 0.4.2
 */
class WorkspaceToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WorkspacePanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Main panel for workspace management.
 */
class WorkspacePanel(private val project: Project) : JBPanel<WorkspacePanel>(BorderLayout()) {

    private val logger = Logger.getInstance(WorkspacePanel::class.java)
    private val service = WorkspaceService.getInstance(project)
    private val tableModel = WorkspaceTableModel()
    private val table = JBTable(tableModel)

    init {
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(createStatusPanel(), BorderLayout.SOUTH)
        configureTable()
        refreshList()
    }

    /**
     * Configures the workspace table.
     */
    private fun configureTable() {
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.columnModel.getColumn(0).preferredWidth = 150
        table.columnModel.getColumn(1).preferredWidth = 100
        table.columnModel.getColumn(2).preferredWidth = 100

        // Custom renderer for the active workspace indicator
        table.columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                val workspace = tableModel.getWorkspaceAt(row)
                val activeId = service.getActiveWorkspaceId()
                if (workspace.id == activeId) {
                    text = "● $value"
                }
                return component
            }
        }

        // Double-click to restore
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && table.selectedRow >= 0) {
                    restoreSelectedWorkspace()
                }
            }
        })
    }

    /**
     * Creates the toolbar with action buttons.
     */
    private fun createToolbar(): JPanel {
        return panel {
            row {
                button("New") { createWorkspace() }
                button("Save") { saveCurrentWorkspace() }
                button("Restore") { restoreSelectedWorkspace() }
                button("Delete") { deleteSelectedWorkspace() }
                button("Refresh") { refreshList() }
            }
        }
    }

    /**
     * Creates the status panel at the bottom.
     */
    private fun createStatusPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBLabel("Double-click to restore workspace"), BorderLayout.WEST)
        }
    }

    /**
     * Refreshes the workspace list from the service.
     */
    fun refreshList() {
        tableModel.setWorkspaces(service.getWorkspaces())
    }

    /**
     * Creates a new workspace.
     */
    private fun createWorkspace() {
        val name = Messages.showInputDialog(
            project,
            "Enter workspace name:",
            "Create Workspace",
            Messages.getQuestionIcon()
        )
        if (!name.isNullOrBlank()) {
            service.createWorkspace(name)
            refreshList()
            logger.info("Created workspace: $name")
        }
    }

    /**
     * Saves current bookmarks/breakpoints to the selected workspace.
     */
    private fun saveCurrentWorkspace() {
        val workspace = getSelectedWorkspace() ?: run {
            Messages.showWarningDialog(project, "Please select a workspace first.", "No Selection")
            return
        }

        val result = service.saveCurrentState(workspace.id)
        when (result) {
            is WorkspaceResult.Success -> {
                refreshList()
                Messages.showInfoMessage(project, result.message, "Saved")
            }
            is WorkspaceResult.Error -> {
                Messages.showErrorDialog(project, result.message, "Save Failed")
            }
            else -> {}
        }
    }

    /**
     * Restores the selected workspace.
     */
    private fun restoreSelectedWorkspace() {
        val workspace = getSelectedWorkspace() ?: run {
            Messages.showWarningDialog(project, "Please select a workspace first.", "No Selection")
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            "Restore workspace '${workspace.name}'?\nThis will replace current bookmarks and breakpoints.",
            "Confirm Restore",
            Messages.getQuestionIcon()
        )

        if (confirm == Messages.YES) {
            val result = service.restoreWorkspace(workspace.id)
            when (result) {
                is WorkspaceResult.Success -> {
                    refreshList()
                    Messages.showInfoMessage(project, result.message, "Restored")
                }
                is WorkspaceResult.Error -> {
                    Messages.showErrorDialog(project, result.message, "Restore Failed")
                }
                else -> {}
            }
        }
    }

    /**
     * Deletes the selected workspace.
     */
    private fun deleteSelectedWorkspace() {
        val workspace = getSelectedWorkspace() ?: run {
            Messages.showWarningDialog(project, "Please select a workspace first.", "No Selection")
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            "Delete workspace '${workspace.name}'?",
            "Confirm Delete",
            Messages.getWarningIcon()
        )

        if (confirm == Messages.YES) {
            service.deleteWorkspace(workspace.id)
            refreshList()
        }
    }

    /**
     * Gets the currently selected workspace.
     */
    private fun getSelectedWorkspace(): BookmarkWorkspace? {
        val row = table.selectedRow
        return if (row >= 0) tableModel.getWorkspaceAt(row) else null
    }
}

/**
 * Table model for displaying workspaces.
 */
class WorkspaceTableModel : AbstractTableModel() {

    private val columns = arrayOf("Name", "Items", "Modified")
    private var workspaces: List<BookmarkWorkspace> = emptyList()

    fun setWorkspaces(workspaces: List<BookmarkWorkspace>) {
        this.workspaces = workspaces
        fireTableDataChanged()
    }

    fun getWorkspaceAt(row: Int): BookmarkWorkspace = workspaces[row]

    override fun getRowCount(): Int = workspaces.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val workspace = workspaces[rowIndex]
        return when (columnIndex) {
            0 -> workspace.name
            1 -> workspace.summary
            2 -> workspace.modifiedAtFormatted
            else -> ""
        }
    }
}

// =============================================================================
// Actions
// =============================================================================

/**
 * Action to create a new workspace.
 */
class CreateWorkspaceAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = WorkspaceService.getInstance(project)

        val name = Messages.showInputDialog(
            project,
            "Enter workspace name:",
            "Create Workspace",
            Messages.getQuestionIcon()
        )

        if (!name.isNullOrBlank()) {
            service.createWorkspace(name)
            Messages.showInfoMessage(project, "Workspace '$name' created.", "Created")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Action to save current state to a workspace.
 */
class SaveToWorkspaceAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = WorkspaceService.getInstance(project)
        val workspaces = service.getWorkspaces()

        if (workspaces.isEmpty()) {
            Messages.showWarningDialog(project, "No workspaces exist. Create one first.", "No Workspaces")
            return
        }

        val names = workspaces.map { it.name }.toTypedArray()
        val selected = Messages.showChooseDialog(
            project,
            "Select workspace to save to:",
            "Save to Workspace",
            Messages.getQuestionIcon(),
            names,
            names.first()
        )

        if (selected >= 0) {
            val workspace = workspaces[selected]
            val result = service.saveCurrentState(workspace.id)
            when (result) {
                is WorkspaceResult.Success -> {
                    Messages.showInfoMessage(project, result.message, "Saved")
                }
                is WorkspaceResult.Error -> {
                    Messages.showErrorDialog(project, result.message, "Save Failed")
                }
                else -> {}
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Action to restore a workspace.
 */
class RestoreWorkspaceAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = WorkspaceService.getInstance(project)
        val workspaces = service.getWorkspaces()

        if (workspaces.isEmpty()) {
            Messages.showWarningDialog(project, "No workspaces exist.", "No Workspaces")
            return
        }

        val names = workspaces.map { "${it.name} (${it.summary})" }.toTypedArray()
        val selected = Messages.showChooseDialog(
            project,
            "Select workspace to restore:",
            "Restore Workspace",
            Messages.getQuestionIcon(),
            names,
            names.first()
        )

        if (selected >= 0) {
            val workspace = workspaces[selected]
            val result = service.restoreWorkspace(workspace.id)
            when (result) {
                is WorkspaceResult.Success -> {
                    Messages.showInfoMessage(project, result.message, "Restored")
                }
                is WorkspaceResult.Error -> {
                    Messages.showErrorDialog(project, result.message, "Restore Failed")
                }
                else -> {}
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
