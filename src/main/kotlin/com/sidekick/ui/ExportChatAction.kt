// =============================================================================
// ExportChatAction.kt
// =============================================================================
// IntelliJ action for the "Export Chat as Markdown" menu item.
//
// This action delegates to the ChatPanel's export functionality when invoked
// from the Sidekick action group / context menu.
//
// @since 1.1.1
// =============================================================================

package com.sidekick.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor

/**
 * Action that exports the current Sidekick chat conversation as a Markdown file.
 *
 * Registered in `plugin.xml` as `Sidekick.ExportChat` and added to the
 * `Sidekick.ActionGroup`. When invoked, it locates the active [ChatPanel]
 * from the Sidekick tool window and triggers the export flow.
 *
 * @since 1.1.1
 */
class ExportChatAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(ExportChatAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Find the Sidekick tool window and trigger export
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager
            .getInstance(project)
            .getToolWindow("Sidekick") ?: return

        val content = toolWindow.contentManager.getContent(0) ?: return
        val chatPanel = content.component as? ChatPanel

        if (chatPanel != null) {
            val bubbles = chatPanel.getMessageBubbles()
            if (bubbles.isEmpty()) {
                LOG.debug("No messages to export")
                return
            }

            val messages = bubbles.map { it.toExportableMessage() }
            val markdown = ChatExportService.formatConversation(
                messages = messages,
                projectName = project.name
            )

            val descriptor = FileSaverDescriptor(
                "Export Chat as Markdown",
                "Export conversation as Markdown file",
                "md"
            )
            val saveDialog = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, project)
            val wrapper = saveDialog.save(
                project.basePath?.let { java.nio.file.Path.of(it) },
                "sidekick-chat-export.md"
            )

            if (wrapper != null) {
                try {
                    wrapper.getFile().writeText(markdown)
                    LOG.info("Conversation exported to ${wrapper.getFile().absolutePath}")
                } catch (ex: Exception) {
                    LOG.error("Failed to export conversation", ex)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
