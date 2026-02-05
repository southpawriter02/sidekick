package com.sidekick.navigation.markdown

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

/**
 * # Copy as Markdown Actions
 *
 * Editor actions for copying code as formatted Markdown.
 * Part of Sidekick v0.4.5 Copy as Markdown feature.
 *
 * ## Actions
 *
 * - CopyAsMarkdownAction: Quick copy with default options
 * - CopyAsMarkdownWithOptionsAction: Copy with options dialog
 * - CopyAsMarkdownMinimalAction: Copy without metadata
 * - CopyAsMarkdownCollapsibleAction: Copy wrapped in details tag
 *
 * @since 0.4.5
 */

/**
 * Copy selected code as markdown with default options.
 *
 * Uses standard formatting with file path and language hint.
 */
class CopyAsMarkdownAction : AnAction() {

    private val logger = Logger.getInstance(CopyAsMarkdownAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText

        if (selection.isNullOrEmpty()) {
            Messages.showWarningDialog(
                e.project,
                "Please select some text first.",
                "No Selection"
            )
            return
        }

        // Get line range for accurate numbering
        val doc = editor.document
        val startLine = doc.getLineNumber(editor.selectionModel.selectionStart)
        val endLine = doc.getLineNumber(editor.selectionModel.selectionEnd)

        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = file?.path,
            options = MarkdownCopyOptions(),
            lineRange = startLine..endLine
        )

        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))

        logger.info("Copied ${result.lineCount} lines as markdown")
        showNotification(e, "Copied ${result.summary} as Markdown")
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }

    private fun showNotification(e: AnActionEvent, message: String) {
        e.project?.let { project ->
            com.intellij.openapi.wm.WindowManager.getInstance()
                .getStatusBar(project)?.info = message
        }
    }
}

/**
 * Copy as markdown with options dialog.
 *
 * Shows a dialog to configure formatting options before copying.
 */
class CopyAsMarkdownWithOptionsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return

        // Show options dialog
        val dialog = MarkdownOptionsDialog()
        if (!dialog.showAndGet()) return

        val options = dialog.getOptions()

        // Get line range
        val doc = editor.document
        val startLine = doc.getLineNumber(editor.selectionModel.selectionStart)

        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = file?.path,
            options = options.withStartLine(startLine + 1),
            lineRange = null
        )

        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))

        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = "Copied ${result.summary} as Markdown"
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}

/**
 * Dialog for configuring markdown copy options.
 */
class MarkdownOptionsDialog : DialogWrapper(true) {

    private val includeFilePath = JBCheckBox("Include file path", true)
    private val includeLineNumbers = JBCheckBox("Include line numbers", false)
    private val includeLanguage = JBCheckBox("Include language hint", true)
    private val wrapInDetails = JBCheckBox("Wrap in collapsible <details>", false)

    init {
        title = "Copy as Markdown Options"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            group("Formatting Options") {
                row { cell(includeFilePath) }
                row { cell(includeLineNumbers) }
                row { cell(includeLanguage) }
                row { cell(wrapInDetails) }
            }
        }
    }

    fun getOptions(): MarkdownCopyOptions {
        return MarkdownCopyOptions(
            includeFilePath = includeFilePath.isSelected,
            includeLineNumbers = includeLineNumbers.isSelected,
            includeLanguage = includeLanguage.isSelected,
            wrapInDetails = wrapInDetails.isSelected
        )
    }
}

/**
 * Copy as markdown with minimal formatting.
 *
 * Just the code block, no file path or extras.
 */
class CopyAsMarkdownMinimalAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return

        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = null,
            options = MarkdownCopyOptions.MINIMAL
        )

        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))

        e.project?.let { project ->
            com.intellij.openapi.wm.WindowManager.getInstance()
                .getStatusBar(project)?.info = "Copied ${result.summary} as Markdown"
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}

/**
 * Copy as markdown wrapped in collapsible details tag.
 *
 * Useful for long code snippets in documentation.
 */
class CopyAsMarkdownCollapsibleAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return

        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = file?.path,
            options = MarkdownCopyOptions.COLLAPSIBLE
        )

        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))

        e.project?.let { project ->
            com.intellij.openapi.wm.WindowManager.getInstance()
                .getStatusBar(project)?.info = "Copied ${result.summary} as collapsible Markdown"
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}

/**
 * Copy as markdown with line numbers.
 */
class CopyAsMarkdownWithLinesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return

        val doc = editor.document
        val startLine = doc.getLineNumber(editor.selectionModel.selectionStart)

        val result = MarkdownCode.format(
            code = selection,
            language = file?.extension,
            filePath = file?.path,
            options = MarkdownCopyOptions.FULL.withStartLine(startLine + 1)
        )

        CopyPasteManager.getInstance().setContents(StringSelection(result.markdown))

        e.project?.let { project ->
            com.intellij.openapi.wm.WindowManager.getInstance()
                .getStatusBar(project)?.info = "Copied ${result.summary} with line numbers"
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }
}
