package com.sidekick.navigation.testjump

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel

/**
 * # Jump to Test Action
 *
 * Editor action that toggles between source and test files.
 * Part of Sidekick v0.4.1 Jump-to-Test feature.
 *
 * ## Keyboard Shortcut
 *
 * `Alt+Shift+T` - Toggle between source and test
 *
 * ## Behavior
 *
 * - From source file: Navigate to corresponding test file
 * - From test file: Navigate to corresponding source file
 * - If test doesn't exist: Offer to create it
 * - If multiple tests exist: Show selection popup
 *
 * @since 0.4.1
 */
class JumpToTestAction : AnAction() {

    private val logger = Logger.getInstance(JumpToTestAction::class.java)

    /**
     * Specifies that action updates should run on background thread.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * Performs the navigation action.
     *
     * Determines whether the current file is a source or test file,
     * then navigates to the corresponding counterpart.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = TestNavigationService.getInstance(project)

        logger.info("Jump to test action triggered for: ${file.path}")

        // Determine navigation direction
        val result = if (service.isTestFile(file)) {
            logger.info("Navigating from test to source")
            service.findSourceForTest(file)
        } else {
            logger.info("Navigating from source to test")
            service.findTestForSource(file)
        }

        handleNavigationResult(project, file, result, service)
    }

    /**
     * Updates the action's enabled state based on current context.
     *
     * The action is enabled when:
     * - A project is open
     * - A file is selected in the editor
     * - The file has a supported extension
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        e.presentation.isEnabledAndVisible = project != null && file != null &&
                TestNavigationService.SUPPORTED_EXTENSIONS.contains(file.extension?.lowercase())
    }

    /**
     * Handles the navigation result and performs appropriate action.
     */
    private fun handleNavigationResult(
        project: Project,
        currentFile: com.intellij.openapi.vfs.VirtualFile,
        result: NavigationResult,
        service: TestNavigationService
    ) {
        when (result) {
            is NavigationResult.Found -> {
                logger.info("Found target file: ${result.path}")
                openFile(project, result.path)
            }

            is NavigationResult.NotFound -> {
                logger.info("Target not found, suggested: ${result.suggestedPath}")
                promptCreateFile(project, currentFile, result.suggestedPath, service)
            }

            is NavigationResult.Multiple -> {
                logger.info("Multiple targets found: ${result.options}")
                showSelectionPopup(project, result.options)
            }

            is NavigationResult.Error -> {
                logger.warn("Navigation error: ${result.message}")
                Messages.showErrorDialog(project, result.message, "Navigation Error")
            }
        }
    }

    /**
     * Prompts the user to create a missing test file.
     */
    private fun promptCreateFile(
        project: Project,
        sourceFile: com.intellij.openapi.vfs.VirtualFile,
        suggestedPath: String,
        service: TestNavigationService
    ) {
        val fileName = suggestedPath.substringAfterLast('/')
        val relativePath = suggestedPath.substringAfter(project.basePath ?: "")

        val create = Messages.showYesNoDialog(
            project,
            "Test file not found.\n\nCreate $fileName?\n\nPath: $relativePath",
            "Create Test File",
            "Create",
            "Cancel",
            Messages.getQuestionIcon()
        )

        if (create == Messages.YES) {
            val createResult = service.createTestFile(sourceFile)
            when (createResult) {
                is NavigationResult.Found -> {
                    logger.info("Created and opening: ${createResult.path}")
                    openFile(project, createResult.path)
                }
                is NavigationResult.Error -> {
                    Messages.showErrorDialog(project, createResult.message, "Failed to Create Test")
                }
                else -> {
                    Messages.showErrorDialog(project, "Unexpected error creating test file", "Error")
                }
            }
        }
    }

    /**
     * Shows a popup to select from multiple matching files.
     */
    private fun showSelectionPopup(project: Project, options: List<String>) {
        val model = DefaultListModel<String>().apply {
            options.forEach { addElement(it.substringAfterLast('/')) }
        }

        val list = JBList(model)

        JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle("Select Target File")
            .setItemChoosenCallback {
                val selectedIndex = list.selectedIndex
                if (selectedIndex >= 0 && selectedIndex < options.size) {
                    openFile(project, options[selectedIndex])
                }
            }
            .createPopup()
            .showInFocusCenter()
    }

    /**
     * Opens a file in the editor.
     */
    private fun openFile(project: Project, path: String) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
            logger.info("Opened file: $path")
        } else {
            logger.error("Could not find file: $path")
            Messages.showErrorDialog(project, "Could not open file: $path", "File Not Found")
        }
    }
}

/**
 * # Go to Source Action
 *
 * Dedicated action for navigating from test to source.
 * Provides explicit action when user wants to navigate specifically to source.
 *
 * @since 0.4.1
 */
class GoToSourceAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = TestNavigationService.getInstance(project)

        if (!service.isTestFile(file)) {
            Messages.showInfoMessage(project, "Current file is not a test file.", "Not a Test")
            return
        }

        when (val result = service.findSourceForTest(file)) {
            is NavigationResult.Found -> {
                LocalFileSystem.getInstance().findFileByPath(result.path)?.let { vf ->
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            }
            is NavigationResult.Error -> {
                Messages.showErrorDialog(project, result.message, "Navigation Error")
            }
            else -> {
                Messages.showInfoMessage(project, "Source file not found.", "Not Found")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isTest = file?.let {
            project?.let { p -> TestNavigationService.getInstance(p).isTestFile(it) }
        } ?: false

        e.presentation.isEnabledAndVisible = project != null && file != null && isTest
    }
}

/**
 * # Go to Test Action
 *
 * Dedicated action for navigating from source to test.
 * Provides explicit action when user wants to navigate specifically to test.
 *
 * @since 0.4.1
 */
class GoToTestAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = TestNavigationService.getInstance(project)

        if (service.isTestFile(file)) {
            Messages.showInfoMessage(project, "Current file is already a test file.", "Already a Test")
            return
        }

        when (val result = service.findTestForSource(file)) {
            is NavigationResult.Found -> {
                LocalFileSystem.getInstance().findFileByPath(result.path)?.let { vf ->
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            }
            is NavigationResult.NotFound -> {
                val create = Messages.showYesNoDialog(
                    project,
                    "Test file not found. Create it?",
                    "Create Test",
                    Messages.getQuestionIcon()
                )
                if (create == Messages.YES) {
                    val createResult = service.createTestFile(file)
                    if (createResult is NavigationResult.Found) {
                        LocalFileSystem.getInstance().findFileByPath(createResult.path)?.let { vf ->
                            FileEditorManager.getInstance(project).openFile(vf, true)
                        }
                    }
                }
            }
            is NavigationResult.Error -> {
                Messages.showErrorDialog(project, result.message, "Navigation Error")
            }
            else -> {}
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isSource = file?.let {
            project?.let { p -> !TestNavigationService.getInstance(p).isTestFile(it) }
        } ?: false

        e.presentation.isEnabledAndVisible = project != null && file != null && isSource
    }
}
