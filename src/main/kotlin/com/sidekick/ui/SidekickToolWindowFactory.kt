// =============================================================================
// SidekickToolWindowFactory.kt
// =============================================================================
// Factory for creating the Sidekick chat tool window.
//
// This registers the Sidekick panel as a tool window in the IDE, accessible
// from View → Tool Windows → Sidekick (or the right sidebar).
//
// DESIGN NOTES:
// - Uses project-level tool window (one per project)
// - Anchored to the right side by default (matches AI assistant conventions)
// - Creates a single Chat tab with the ChatPanel component
// =============================================================================

package com.sidekick.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.sidekick.core.SidekickBundle

/**
 * Factory for creating the Sidekick chat tool window.
 *
 * This class is registered in plugin.xml and is instantiated by the
 * IntelliJ Platform when the tool window is first accessed.
 *
 * Implements [DumbAware] to indicate the tool window can be created
 * even during indexing operations.
 */
class SidekickToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        private val LOG = Logger.getInstance(SidekickToolWindowFactory::class.java)
    }

    /**
     * Creates the tool window content when the window is first opened.
     *
     * This method is called by the platform when the user opens the
     * Sidekick tool window for the first time in a project.
     *
     * @param project The project this tool window belongs to
     * @param toolWindow The tool window instance to populate
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        LOG.info("Creating Sidekick tool window for project: ${project.name}")
        
        // Create the main chat panel
        val chatPanel = ChatPanel(project)
        
        // Wrap in a content container
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            chatPanel,
            SidekickBundle.message("toolwindow.chat.tab"),
            false  // Not lockable
        )
        
        // Add to the tool window
        toolWindow.contentManager.addContent(content)
        
        LOG.debug("Sidekick tool window created successfully")
    }

    /**
     * Determines whether the tool window should be available for a project.
     *
     * Always returns true - Sidekick is available for all project types.
     */
    override fun shouldBeAvailable(project: Project): Boolean = true
}
