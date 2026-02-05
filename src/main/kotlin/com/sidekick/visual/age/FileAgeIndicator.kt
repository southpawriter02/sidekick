package com.sidekick.visual.age

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * # File Age Tab Color Provider
 *
 * Provides editor tab colors based on file age.
 * Part of Sidekick v0.5.4 File Age Indicator feature.
 *
 * @since 0.5.4
 */
class FileAgeTabColorProvider : EditorTabColorProvider {

    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val service = FileAgeService.getInstance(project)
        return service.getTabColor(file)
    }

    override fun getProjectViewColor(project: Project, file: VirtualFile): Color? {
        return getEditorTabColor(project, file)
    }
}

/**
 * Toggles file age indicator on/off.
 */
class ToggleFileAgeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = FileAgeService.getInstance(project)
        val enabled = service.toggle()

        val message = if (enabled) "File age indicator enabled" else "File age indicator disabled"
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = message
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null

        if (project != null) {
            val service = FileAgeService.getInstance(project)
            e.presentation.text = if (service.isEnabled) {
                "Sidekick: Disable File Age Indicator"
            } else {
                "Sidekick: Enable File Age Indicator"
            }
        }
    }
}

/**
 * Shows color scheme selector popup.
 */
class SelectAgeSchemeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = FileAgeService.getInstance(project)

        val step = object : BaseListPopupStep<AgeColorScheme>(
            "Select Age Color Scheme",
            AgeColorScheme.ALL
        ) {
            override fun getTextFor(value: AgeColorScheme): String = value.displayName

            override fun onChosen(selectedValue: AgeColorScheme, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    service.setScheme(selectedValue)
                    com.intellij.openapi.wm.WindowManager.getInstance()
                        .getStatusBar(project)?.info = "Age scheme set to ${selectedValue.displayName}"
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
 * Toggles git time usage.
 */
class ToggleGitTimeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = FileAgeService.getInstance(project)
        val useGit = service.toggleGitTime()

        val message = if (useGit) "Using Git commit time" else "Using file system time"
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = message
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null

        if (project != null) {
            val service = FileAgeService.getInstance(project)
            val config = service.config
            e.presentation.text = if (config.useGitTime) {
                "Sidekick: Use File System Time"
            } else {
                "Sidekick: Use Git Commit Time"
            }
        }
    }
}

/**
 * Shows age info for current file.
 */
class ShowFileAgeInfoAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val service = FileAgeService.getInstance(project)
        val result = service.getFileAge(file)

        val message = when (result) {
            is AgeDetectionResult.Success -> {
                val info = result.ageInfo
                buildString {
                    appendLine("File: ${file.name}")
                    appendLine()
                    appendLine("Category: ${info.category.displayName}")
                    appendLine("Age: ${info.ageDescription}")
                    appendLine("Source: ${info.source.displayName}")
                    appendLine("Modified: ${info.lastModified}")
                }
            }
            is AgeDetectionResult.Disabled -> "File age indicator is disabled"
            is AgeDetectionResult.Error -> "Error: ${result.message}"
        }

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            message,
            "File Age: ${file.name}"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }
}

/**
 * Clears the file age cache.
 */
class ClearAgeCacheAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = FileAgeService.getInstance(project)
        service.clearCache()

        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = "File age cache cleared"
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
