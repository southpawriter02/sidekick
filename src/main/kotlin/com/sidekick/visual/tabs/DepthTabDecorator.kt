package com.sidekick.visual.tabs

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * # Depth Tab Decorator
 *
 * Editor tab color provider for depth-based coloring.
 * Part of Sidekick v0.5.1 Depth-Coded Tabs feature.
 *
 * ## Integration
 *
 * Registered as an EditorTabColorProvider extension in plugin.xml.
 * IntelliJ calls getEditorTabColor() for each open tab.
 *
 * @since 0.5.1
 */
class DepthTabColorProvider : EditorTabColorProvider {

    private val logger = Logger.getInstance(DepthTabColorProvider::class.java)

    /**
     * Provides the color for an editor tab.
     *
     * @param project Current project
     * @param file File in the tab
     * @return Color for the tab, or null for default
     */
    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        return try {
            val service = DepthTabService.getInstance(project)
            service.getTabColor(file)
        } catch (e: Exception) {
            logger.error("Failed to get tab color", e)
            null
        }
    }
}

/**
 * Toggles depth-coded tabs on/off.
 */
class ToggleDepthTabsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DepthTabService.getInstance(project)
        val enabled = service.toggle()

        val message = if (enabled) "Depth-coded tabs enabled" else "Depth-coded tabs disabled"
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = message
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null

        if (project != null) {
            val service = DepthTabService.getInstance(project)
            e.presentation.text = if (service.isEnabled) {
                "Sidekick: Disable Depth-Coded Tabs"
            } else {
                "Sidekick: Enable Depth-Coded Tabs"
            }
        }
    }
}

/**
 * Shows palette selector popup.
 */
class SelectDepthPaletteAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DepthTabService.getInstance(project)

        val step = object : BaseListPopupStep<ColorPalette>(
            "Select Palette",
            ColorPalette.ALL
        ) {
            override fun getTextFor(value: ColorPalette): String = value.name

            override fun onChosen(selectedValue: ColorPalette, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    service.setPalette(selectedValue)
                    com.intellij.openapi.wm.WindowManager.getInstance()
                        .getStatusBar(project)?.info = "Palette set to ${selectedValue.name}"
                }
                return PopupStep.FINAL_CHOICE
            }
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showCenteredInCurrentWindow(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Shows depth info for current file.
 */
class ShowFileDepthAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = DepthTabService.getInstance(project)

        val result = service.calculateDepth(file)

        val message = when (result) {
            is DepthResult.Success -> result.info.summary
            is DepthResult.Disabled -> "Depth coding is disabled"
            is DepthResult.Error -> "Error: ${result.message}"
        }

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            message,
            "File Depth: ${file.name}"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = 
            e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }
}
