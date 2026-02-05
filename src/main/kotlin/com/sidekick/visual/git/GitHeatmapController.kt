package com.sidekick.visual.git

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.application.ApplicationManager

/**
 * # Git Heatmap Controller
 *
 * Manages the application of Git Heatmap indicators to editors.
 * Listens for file opens and applies highlighting.
 */
class GitHeatmapController(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val service = GitHeatmapService.getInstance(project)
        if (!service.config.enabled) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val stats = service.getFileStats(file) ?: return@executeOnPooledThread
            
            ApplicationManager.getApplication().invokeLater {
                source.getAllEditors(file).forEach { editor ->
                    if (editor is TextEditor) {
                        applyHeatmap(editor.editor, stats, service.config)
                    }
                }
            }
        }
    }
    
    companion object {
        fun applyHeatmap(editor: Editor, stats: FileGitStats, config: GitHeatmapConfig) {
            val markup = editor.markupModel
            // Clear existing (rudimentary, ideally track them)
            // For now, we just add new ones. In a real app we'd clear old ones first.
            // Using a specific layer or user data to track would be better.
            
            val document = editor.document
            
            stats.lineStats.forEach { (lineNum, lineStat) ->
                // lineNum is 1-based from blame
                // document lines are 0-based
                val docLine = lineNum - 1
                if (docLine >= document.lineCount) return@forEach
                
                val renderer = GitHeatmapGutterRenderer(lineStat, config)
                
                val highlighter = markup.addRangeHighlighter(
                    document.getLineStartOffset(docLine),
                    document.getLineEndOffset(docLine),
                    HighlighterLayer.FIRST,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                highlighter.gutterIconRenderer = renderer
            }
        }
    }
}
