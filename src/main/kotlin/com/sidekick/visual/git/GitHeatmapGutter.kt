package com.sidekick.visual.git

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

/**
 * # Git Heatmap Gutter Renderer
 *
 * Renders Git heatmap indicators in the editor gutter.
 * Part of Sidekick v0.5.5 Git Diff Heatmap feature.
 *
 * @since 0.5.5
 */
class GitHeatmapGutterRenderer(
    private val lineStats: LineGitStats,
    private val config: GitHeatmapConfig
) : GutterIconRenderer() {

    override fun getIcon(): Icon = HeatmapIcon(lineStats, config)

    override fun getTooltipText(): String {
        return buildString {
            append("Line ${lineStats.lineNumber}")
            lineStats.lastAuthor?.let { append(" • $it") }
            append("\n")
            lineStats.lastCommitDate?.let { 
                // Simple formatting
                val date = java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault())
                append("Changed: ${date.toLocalDate()}")
            }
            if (lineStats.commitHash != null) {
                append("\nCommit: ${lineStats.commitHash.take(7)}")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GitHeatmapGutterRenderer) return false
        return lineStats == other.lineStats && config == other.config
    }

    override fun hashCode(): Int {
        var result = lineStats.hashCode()
        result = 31 * result + config.hashCode()
        return result
    }
}

/**
 * Custom icon for heatmap intensity.
 */
class HeatmapIcon(
    private val stats: LineGitStats,
    private val config: GitHeatmapConfig
) : Icon {
    
    companion object {
        const val WIDTH = 4
        const val HEIGHT = 14
    }

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2d = g.create() as Graphics2D
        try {
            GraphicsUtil.setupAAPainting(g2d)
            
            val intensity = stats.getIntensity(config.metricType)
            val color = config.colorScheme.colorForIntensity(intensity)
            
            g2d.color = color
            
            // Draw a small bar
            g2d.fillRect(x, y + 2, WIDTH, HEIGHT - 4)
            
        } finally {
            g2d.dispose()
        }
    }

    override fun getIconWidth(): Int = WIDTH
    override fun getIconHeight(): Int = HEIGHT
}

/**
 * Toggles Git Heatmap on/off.
 */
class ToggleGitHeatmapAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitHeatmapService.getInstance(project)
        val enabled = service.toggle()

        val message = if (enabled) "Git heatmap enabled" else "Git heatmap disabled"
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = message
        
        // In a real implementation, we would trigger editor repaint/update here
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        if (project != null) {
            val service = GitHeatmapService.getInstance(project)
            e.presentation.text = if (service.config.enabled) {
                "Sidekick: Disable Git Heatmap"
            } else {
                "Sidekick: Enable Git Heatmap"
            }
        }
    }
}

/**
 * Selects heatmap metric.
 */
class SelectHeatmapMetricAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitHeatmapService.getInstance(project)
        
        val metric = if (service.config.metricType == HeatmapMetric.COMMIT_COUNT) {
            HeatmapMetric.LAST_CHANGED
        } else {
            HeatmapMetric.COMMIT_COUNT
        }
        
        service.updateConfig(service.config.withMetric(metric))
        
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = "Heatmap metric: ${metric.displayName}"
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        if (project != null) {
            val service = GitHeatmapService.getInstance(project)
            e.presentation.text = "Sidekick: Switch Metric (Current: ${service.config.metricType.displayName})"
        }
    }
}

/**
 * Selects heatmap color scheme.
 */
class SelectHeatmapSchemeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitHeatmapService.getInstance(project)
        
        val nextScheme = when (service.config.colorScheme) {
            HeatmapColorScheme.FIRE -> HeatmapColorScheme.PLASMA
            HeatmapColorScheme.PLASMA -> HeatmapColorScheme.VIRIDIS
            HeatmapColorScheme.VIRIDIS -> HeatmapColorScheme.FIRE
        }
        
        service.updateConfig(service.config.withScheme(nextScheme))
        
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = "Heatmap scheme: ${nextScheme.displayName}"
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        if (project != null) {
            val service = GitHeatmapService.getInstance(project)
            e.presentation.text = "Sidekick: Switch Scheme (Current: ${service.config.colorScheme.displayName})"
        }
    }
}
