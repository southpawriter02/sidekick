// =============================================================================
// GenerateTestsAction.kt
// =============================================================================
// Editor action to trigger test generation.
//
// Available from:
// - Editor context menu (Sidekick > Generate Tests)
// - Generate menu (Alt+Insert)
// - Keyboard shortcut (Alt+Shift+G)
//
// DESIGN NOTES:
// - Background thread for LLM calls
// - Options dialog for framework selection
// - Creates or opens test file
// =============================================================================

package com.sidekick.generation.tests

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Action to generate unit tests for selected code or symbol at cursor.
 *
 * This action analyzes the selection or symbol and generates appropriate
 * unit tests using the detected or selected test framework.
 *
 * ## Keyboard Shortcut
 * Default: Alt+Shift+G
 *
 * ## Context Menu
 * Available under: Sidekick > Generate Tests
 */
class GenerateTestsAction : AnAction() {

    companion object {
        private val LOG = Logger.getInstance(GenerateTestsAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        LOG.info("Generate Tests action triggered")

        // Run in background with progress
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating Tests...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing code..."
                indicator.fraction = 0.2

                val result = runBlocking {
                    val service = TestGenService.getInstance(project)
                    service.generateForCurrentContext()
                }

                indicator.fraction = 0.8

                if (result.success) {
                    indicator.text = "Creating test file..."
                    createTestFile(project, result)
                    showSuccessNotification(project, result)
                } else {
                    showErrorNotification(project, result.error ?: "Generation failed")
                }

                indicator.fraction = 1.0
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)

        e.presentation.isEnabledAndVisible = project != null && editor != null
    }

    // -------------------------------------------------------------------------
    // Private Methods - File Creation
    // -------------------------------------------------------------------------

    private fun createTestFile(project: Project, result: TestGenResult) {
        val basePath = project.basePath ?: return
        
        // Find or create test directory
        val testDir = findTestDirectory(basePath, result.framework)
        
        val testFile = File(testDir, result.testFileName)
        
        try {
            // Create directory if needed
            testFile.parentFile?.mkdirs()
            
            // Write test file
            testFile.writeText(result.testCode)
            
            LOG.info("Created test file: ${testFile.absolutePath}")
            
            // Refresh and open the file
            VfsUtil.findFileByIoFile(testFile, true)?.let { vFile ->
                openFile(project, vFile)
            }
            
        } catch (e: Exception) {
            LOG.warn("Failed to create test file: ${e.message}", e)
        }
    }

    private fun findTestDirectory(basePath: String, framework: TestFramework): File {
        // Common test directory patterns
        val testDirCandidates = when (framework) {
            TestFramework.XUNIT, TestFramework.NUNIT, TestFramework.MSTEST -> listOf(
                "tests",
                "test",
                "Tests",
                "src/test"
            )
            TestFramework.JUNIT5 -> listOf(
                "src/test/java",
                "test",
                "tests"
            )
            TestFramework.JUNIT_KOTLIN, TestFramework.KOTEST -> listOf(
                "src/test/kotlin",
                "test",
                "tests"
            )
            TestFramework.JEST -> listOf(
                "__tests__",
                "tests",
                "test",
                "spec"
            )
            TestFramework.PYTEST -> listOf(
                "tests",
                "test"
            )
            TestFramework.AUTO -> listOf(
                "tests",
                "test"
            )
        }
        
        // Find existing test directory or create one
        for (candidate in testDirCandidates) {
            val dir = File(basePath, candidate)
            if (dir.exists() && dir.isDirectory) {
                return dir
            }
        }
        
        // Default: create tests directory
        val defaultDir = File(basePath, testDirCandidates.first())
        defaultDir.mkdirs()
        return defaultDir
    }

    private fun openFile(project: Project, file: VirtualFile) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - Notifications
    // -------------------------------------------------------------------------

    private fun showSuccessNotification(project: Project, result: TestGenResult) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Tests Generated",
                    "Created ${result.testCount} test(s) using ${result.framework.displayName} in ${result.testFileName}",
                    NotificationType.INFORMATION
                )
                .notify(project)
        } catch (e: Exception) {
            LOG.debug("Could not show notification: ${e.message}")
        }
    }

    private fun showErrorNotification(project: Project, message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sidekick.Notifications")
                .createNotification(
                    "Test Generation Failed",
                    message,
                    NotificationType.WARNING
                )
                .notify(project)
        } catch (e: Exception) {
            LOG.warn("Could not show error notification: ${e.message}")
        }
    }
}

/**
 * Action to generate test stub without LLM.
 * Useful for offline mode or quick scaffolding.
 */
class GenerateTestStubAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val service = TestGenService.getInstance(project)
        val editorService = com.sidekick.context.EditorContextService.getInstance(project)
        val projectService = com.sidekick.context.ProjectContextService.getInstance(project)
        
        val symbol = editorService.getSymbolAtCursor()
        val projectContext = projectService.getProjectContext()
        
        val request = TestGenRequest(
            symbol = symbol,
            projectContext = projectContext
        )
        
        val result = service.generateStub(request)

        if (result.success) {
            // Copy to clipboard
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(result.testCode)
            clipboard.setContents(selection, selection)
            
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Sidekick.Notifications")
                    .createNotification(
                        "Test Stub Generated",
                        "Test stub copied to clipboard",
                        NotificationType.INFORMATION
                    )
                    .notify(project)
            } catch (_: Exception) {}
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }
}
