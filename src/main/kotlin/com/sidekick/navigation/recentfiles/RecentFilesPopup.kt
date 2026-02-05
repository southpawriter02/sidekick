package com.sidekick.navigation.recentfiles

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * # Recent Files Popup
 *
 * UI components for the recent files grid popup.
 * Part of Sidekick v0.4.4 Recent Files Grid feature.
 *
 * ## Actions
 *
 * - ShowRecentFilesGridAction: Display the grid popup
 * - ClearRecentFilesAction: Clear the history
 * - TogglePinFileAction: Pin/unpin current file
 *
 * @since 0.4.4
 */

/**
 * Shows the recent files grid popup.
 *
 * Displays a grid of recently opened files with previews,
 * grouping, and quick navigation.
 */
class ShowRecentFilesGridAction : AnAction() {

    private val logger = Logger.getInstance(ShowRecentFilesGridAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RecentFilesService.getInstance()
        val options = GridViewOptions()
        val files = service.getRecentFiles(options)

        if (files.isEmpty()) {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "No recent files found.",
                "Recent Files"
            )
            return
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(
                JBScrollPane(RecentFilesGrid(project, files, options)),
                null
            )
            .setTitle("Recent Files (${files.size})")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            .setMinSize(Dimension(400, 300))
            .createPopup()

        popup.showCenteredInCurrentWindow(project)
        logger.info("Showing recent files grid with ${files.size} files")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Grid panel displaying recent files as cards.
 */
class RecentFilesGrid(
    private val project: Project,
    private val files: List<RecentFileEntry>,
    private val options: GridViewOptions
) : JBPanel<RecentFilesGrid>(GridLayout(0, options.gridColumns, 8, 8)) {

    init {
        border = EmptyBorder(8, 8, 8, 8)
        files.forEach { entry ->
            add(createFileCard(entry))
        }
    }

    /**
     * Creates a card component for a file entry.
     */
    private fun createFileCard(entry: RecentFileEntry): JComponent {
        val card = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                EmptyBorder(8, 8, 8, 8)
            )
            preferredSize = Dimension(120, 80)

            // Icon and name
            val iconLabel = JBLabel(entry.iconHint)
            iconLabel.font = iconLabel.font.deriveFont(18f)
            add(iconLabel, BorderLayout.WEST)

            val nameLabel = JBLabel(entry.displayName).apply {
                toolTipText = entry.summary
            }
            add(nameLabel, BorderLayout.CENTER)

            // Folder info
            val folderLabel = JBLabel(entry.folderName).apply {
                font = font.deriveFont(font.size - 2f)
                foreground = Color.GRAY
            }
            add(folderLabel, BorderLayout.SOUTH)

            // Click handler
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 1) {
                        openFile(entry)
                    }
                }

                override fun mouseEntered(e: MouseEvent) {
                    background = UIManager.getColor("List.selectionBackground")
                }

                override fun mouseExited(e: MouseEvent) {
                    background = UIManager.getColor("Panel.background")
                }
            })

            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        return card
    }

    /**
     * Opens the file in the editor.
     */
    private fun openFile(entry: RecentFileEntry) {
        LocalFileSystem.getInstance().findFileByPath(entry.path)?.let { vf ->
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }
}

/**
 * Clears the recent files history.
 */
class ClearRecentFilesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val confirm = com.intellij.openapi.ui.Messages.showYesNoDialog(
            project,
            "Clear all recent files history?",
            "Clear Recent Files",
            com.intellij.openapi.ui.Messages.getWarningIcon()
        )

        if (confirm == com.intellij.openapi.ui.Messages.YES) {
            RecentFilesService.getInstance().clearAll()
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Recent files history cleared.",
                "Cleared"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Toggles pin status for the current file.
 */
class TogglePinFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = RecentFilesService.getInstance()

        val toggled = service.togglePin(file.path)
        if (toggled) {
            val entry = service.getEntry(file.path)
            val status = if (entry?.pinned == true) "Pinned" else "Unpinned"
            e.project?.let { project ->
                com.intellij.openapi.wm.WindowManager.getInstance()
                    .getStatusBar(project)?.info = "$status: ${file.name}"
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && e.project != null

        // Update text based on current state
        if (file != null) {
            val service = RecentFilesService.getInstance()
            val entry = service.getEntry(file.path)
            e.presentation.text = if (entry?.pinned == true) {
                "Sidekick: Unpin File"
            } else {
                "Sidekick: Pin File"
            }
        }
    }
}

/**
 * Shows grouped recent files.
 */
class ShowGroupedRecentFilesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RecentFilesService.getInstance()

        // Show grouping options
        val groupings = FileGrouping.entries.map { it.displayName }.toTypedArray()
        val selected = com.intellij.openapi.ui.Messages.showChooseDialog(
            project,
            "Select grouping:",
            "Group Recent Files",
            com.intellij.openapi.ui.Messages.getQuestionIcon(),
            groupings,
            groupings[0]
        )

        if (selected >= 0) {
            val grouping = FileGrouping.entries[selected]
            val options = GridViewOptions(grouping = grouping)
            val groups = service.getGroupedFiles(options)

            // Build message
            val message = groups.joinToString("\n\n") { group ->
                "${group.name} (${group.count}):\n" +
                    group.files.take(5).joinToString("\n") { "  • ${it.displayName}" } +
                    if (group.count > 5) "\n  ... and ${group.count - 5} more" else ""
            }

            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                message,
                "Recent Files by ${grouping.displayName}"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
