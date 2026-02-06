package com.sidekick.quality.deadcode

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * # Dead Code Tool Window
 *
 * Tool window for managing unused code symbols.
 * Part of Sidekick v0.6.4 Dead Code Cemetery feature.
 *
 * ## Features
 *
 * - Table view of all dead code symbols
 * - Filtering by type and risk
 * - Batch delete operations
 * - Exclusion management
 * - Navigation to source
 *
 * @since 0.6.4
 */
class DeadCodeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DeadCodePanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Main panel for the Dead Code tool window.
 */
class DeadCodePanel(private val project: Project) : JBPanel<DeadCodePanel>() {
    private val service = DeadCodeService.getInstance(project)

    private val columnNames = arrayOf("", "Type", "Name", "Location", "Confidence", "Risk")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(tableModel)
    private val statusLabel = JBLabel("Ready")
    private var currentFilter: DeadCodeFilter = DeadCodeFilter.ALL

    init {
        layout = BorderLayout()

        // Configure table
        setupTable()

        // Build UI
        add(createToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(createActionPanel(), BorderLayout.SOUTH)

        // Load data
        refreshList()
    }

    private fun setupTable() {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.autoCreateRowSorter = true

        // Column widths
        table.columnModel.getColumn(0).preferredWidth = 30   // Safe delete icon
        table.columnModel.getColumn(1).preferredWidth = 80   // Type
        table.columnModel.getColumn(2).preferredWidth = 200  // Name
        table.columnModel.getColumn(3).preferredWidth = 150  // Location
        table.columnModel.getColumn(4).preferredWidth = 80   // Confidence
        table.columnModel.getColumn(5).preferredWidth = 60   // Risk

        // Custom renderers
        table.columnModel.getColumn(5).cellRenderer = RiskCellRenderer()

        // Double-click to navigate
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelected()
                }
            }
        })
    }

    private fun createToolbar() = panel {
        row {
            button("🔍 Analyze") { analyzeProject() }
            button("🔄 Refresh") { refreshList() }

            label(" Filter: ")
            comboBox(DeadCodeFilter.entries.toList()).applyToComponent {
                addActionListener {
                    currentFilter = selectedItem as? DeadCodeFilter ?: DeadCodeFilter.ALL
                    refreshList()
                }
            }
        }
    }

    private fun createActionPanel() = panel {
        row {
            button("🗑️ Delete Selected") { deleteSelected() }
            button("🗑️ Delete All Safe") { deleteAllSafe() }
            button("🚫 Exclude Selected") { excludeSelected() }
            button("📍 Go To") { navigateToSelected() }
        }
        row {
            cell(statusLabel)
        }
    }

    // -------------------------------------------------------------------------
    // Data Operations
    // -------------------------------------------------------------------------

    private fun refreshList() {
        tableModel.rowCount = 0

        val symbols = when (currentFilter) {
            DeadCodeFilter.ALL -> service.getDeadCode()
            DeadCodeFilter.SAFE_DELETE -> service.getSafeToDelete()
            DeadCodeFilter.HIGH_CONFIDENCE -> service.getHighConfidence()
            DeadCodeFilter.CLASSES -> service.getDeadCodeByType(SymbolType.CLASS)
            DeadCodeFilter.METHODS -> service.getDeadCodeByType(SymbolType.METHOD)
            DeadCodeFilter.FIELDS -> service.getDeadCodeByType(SymbolType.FIELD)
            DeadCodeFilter.IMPORTS -> service.getDeadCodeByType(SymbolType.IMPORT)
            DeadCodeFilter.LOW_RISK -> service.getDeadCode().filter {
                DeletionRisk.fromSymbol(it) == DeletionRisk.LOW
            }
            DeadCodeFilter.HIGH_RISK -> service.getDeadCode().filter {
                DeletionRisk.fromSymbol(it) == DeletionRisk.HIGH
            }
        }

        symbols.forEach { symbol ->
            tableModel.addRow(arrayOf(
                if (symbol.canSafeDelete) "✓" else "⚠",
                symbol.type.displayName,
                symbol.name,
                symbol.location.displayString,
                symbol.confidencePercent,
                DeletionRisk.fromSymbol(symbol).displayName
            ))
        }

        updateStatus()
    }

    private fun updateStatus() {
        val result = service.analyzeProject()
        val summary = service.getSummary()

        statusLabel.text = buildString {
            append("Total: ${summary.totalSymbols} symbols")
            append(" | Safe to delete: ${summary.safeDeleteCount}")
            if (summary.estimatedLines > 0) {
                append(" | ~${summary.estimatedLines} lines")
            }
            if (result.deadCodePercentage > 0) {
                append(" | ${String.format("%.1f", result.deadCodePercentage)}% dead code")
            }
        }
    }

    private fun analyzeProject() {
        statusLabel.text = "Analyzing..."
        val result = service.analyzeProject()
        refreshList()
        statusLabel.text = "Analysis complete: ${result.symbols.size} symbols found"
    }

    // -------------------------------------------------------------------------
    // Action Handlers
    // -------------------------------------------------------------------------

    private fun deleteSelected() {
        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No symbols selected")
            return
        }

        val symbols = service.getDeadCode()
        val toDelete = selectedRows
            .map { table.convertRowIndexToModel(it) }
            .filter { it < symbols.size }
            .map { symbols[it] }

        val safeCount = toDelete.count { it.canSafeDelete }
        val unsafeCount = toDelete.size - safeCount

        if (unsafeCount > 0) {
            val message = "Delete $safeCount safe symbols? ($unsafeCount cannot be safely deleted)"
            val confirm = JOptionPane.showConfirmDialog(
                this, message, "Confirm Delete", JOptionPane.YES_NO_OPTION
            )
            if (confirm != JOptionPane.YES_OPTION) return
        }

        val deleted = service.batchDelete(toDelete)
        JOptionPane.showMessageDialog(this, "Deleted $deleted symbols")
        refreshList()
    }

    private fun deleteAllSafe() {
        val safeSymbols = service.getSafeToDelete()
        if (safeSymbols.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No symbols safe to delete")
            return
        }

        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete all ${safeSymbols.size} safe symbols?",
            "Confirm Delete All",
            JOptionPane.YES_NO_OPTION
        )

        if (confirm == JOptionPane.YES_OPTION) {
            val deleted = service.deleteAllSafe()
            JOptionPane.showMessageDialog(this, "Deleted $deleted symbols")
            refreshList()
        }
    }

    private fun excludeSelected() {
        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No symbols selected")
            return
        }

        val symbols = service.getDeadCode()
        selectedRows
            .map { table.convertRowIndexToModel(it) }
            .filter { it < symbols.size }
            .forEach { service.excludeSymbol(symbols[it]) }

        JOptionPane.showMessageDialog(this, "Excluded ${selectedRows.size} symbols")
        refreshList()
    }

    private fun navigateToSelected() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return

        val modelRow = table.convertRowIndexToModel(selectedRow)
        val symbols = service.getDeadCode()
        if (modelRow >= symbols.size) return

        val symbol = symbols[modelRow]
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(symbol.location.filePath)

        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openTextEditor(
                OpenFileDescriptor(project, virtualFile, symbol.location.line - 1, 0),
                true
            )
        }
    }

    // -------------------------------------------------------------------------
    // Cell Renderers
    // -------------------------------------------------------------------------

    /**
     * Custom renderer for risk column with color coding.
     */
    private class RiskCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column
            )

            if (!isSelected) {
                background = when (value?.toString()) {
                    "High" -> Color(255, 200, 200)  // Light red
                    "Medium" -> Color(255, 235, 200)  // Light orange
                    "Low" -> Color(200, 255, 200)  // Light green
                    else -> table?.background
                }
            }

            horizontalAlignment = CENTER
            return component
        }
    }
}
