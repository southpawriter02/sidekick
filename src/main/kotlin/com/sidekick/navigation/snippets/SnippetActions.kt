package com.sidekick.navigation.snippets

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JList

/**
 * # Snippet Actions
 *
 * Editor actions for the snippet pocket feature.
 * Part of Sidekick v0.4.3 Snippet Pocket feature.
 *
 * ## Actions
 *
 * - CaptureSnippetAction: Capture selection to snippet pocket
 * - PasteSnippetAction: Paste from a specific slot
 * - ShowSnippetPocketAction: Show popup with all snippets
 * - ClearSnippetAction: Clear a slot or all slots
 *
 * @since 0.4.3
 */

/**
 * Capture current selection to snippet pocket.
 *
 * Captures the selected text from the editor to the next available
 * slot in the snippet pocket, pushing older snippets down.
 */
class CaptureSnippetAction : AnAction() {

    private val logger = Logger.getInstance(CaptureSnippetAction::class.java)

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

        val language = file?.extension
        val lines = editor.selectionModel.let { model ->
            val doc = editor.document
            val start = doc.getLineNumber(model.selectionStart)
            val end = doc.getLineNumber(model.selectionEnd)
            start..end
        }

        val service = SnippetService.getInstance()
        val snippet = service.captureSelection(
            content = selection,
            language = language,
            file = file?.path,
            lines = lines
        )

        logger.info("Captured snippet: ${snippet.preview}")

        // Show confirmation
        val message = "Captured ${snippet.lineCount} lines to snippet pocket"
        showNotification(e, message)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor?.selectionModel?.hasSelection() == true
    }

    private fun showNotification(e: AnActionEvent, message: String) {
        e.project?.let { project ->
            com.intellij.openapi.ui.MessageType.INFO.let { type ->
                com.intellij.openapi.wm.WindowManager.getInstance()
                    .getStatusBar(project)
                    ?.info = message
            }
        }
    }
}

/**
 * Paste snippet from a specific slot.
 *
 * Inserts the content from the specified slot at the current
 * caret position in the editor.
 *
 * @param slot The slot index (0-9) to paste from
 */
class PasteSnippetAction(private val slot: Int) : AnAction() {

    private val logger = Logger.getInstance(PasteSnippetAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val service = SnippetService.getInstance()

        when (val result = service.getSnippetContent(slot)) {
            is SnippetResult.Success -> {
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.insertString(
                        editor.caretModel.offset,
                        result.snippet.content
                    )
                }
                logger.info("Pasted snippet from slot $slot")
            }
            is SnippetResult.SlotEmpty -> {
                Messages.showWarningDialog(
                    project,
                    "Slot $slot is empty.",
                    "Empty Slot"
                )
            }
            is SnippetResult.Error -> {
                Messages.showErrorDialog(project, result.message, "Paste Error")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null &&
            e.getData(CommonDataKeys.EDITOR) != null
    }
}

/**
 * Show the snippet pocket popup.
 *
 * Displays a popup listing all saved snippets with previews,
 * allowing quick selection and paste.
 */
class ShowSnippetPocketAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val service = SnippetService.getInstance()
        val snippets = service.getIndexedSnippets()

        if (snippets.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "Snippet pocket is empty. Use Capture Snippet to save selections.",
                "Empty Pocket"
            )
            return
        }

        val model = DefaultListModel<String>()
        snippets.forEach { (index, snippet) ->
            model.addElement("[$index] ${snippet.displayName}: ${snippet.preview}")
        }

        val list = JList(model)
        val popup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle("Snippet Pocket (${service.getPocketSummary()})")
            .setItemChoosenCallback {
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0 && editor != null) {
                    val (slot, snippet) = snippets[selectedIndex]
                    WriteCommandAction.runWriteCommandAction(project) {
                        editor.document.insertString(
                            editor.caretModel.offset,
                            snippet.content
                        )
                    }
                }
            }
            .createPopup()

        // Show at mouse location or center
        val component = e.inputEvent
        if (component is MouseEvent) {
            popup.show(RelativePoint(component))
        } else {
            popup.showCenteredInCurrentWindow(project)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Clear a snippet slot or all slots.
 */
class ClearSnippetPocketAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = SnippetService.getInstance()

        val confirm = Messages.showYesNoDialog(
            project,
            "Clear all snippets from the pocket?",
            "Clear Snippet Pocket",
            Messages.getWarningIcon()
        )

        if (confirm == Messages.YES) {
            service.clearAll()
            Messages.showInfoMessage(project, "Snippet pocket cleared.", "Cleared")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Capture selection to a specific slot.
 */
class CaptureToSlotAction(private val slot: Int) : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selection = editor.selectionModel.selectedText ?: return

        val language = file?.extension
        val lines = editor.selectionModel.let { model ->
            val doc = editor.document
            val start = doc.getLineNumber(model.selectionStart)
            val end = doc.getLineNumber(model.selectionEnd)
            start..end
        }

        SnippetService.getInstance().captureToSlot(
            slot = slot,
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
