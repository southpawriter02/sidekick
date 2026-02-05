package com.sidekick.visual.separators

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * # Method Separator Painter
 *
 * Renders visual separator lines between methods.
 * Part of Sidekick v0.5.3 Method Separators feature.
 *
 * @since 0.5.3
 */
class MethodSeparatorPainter(
    private val editor: Editor,
    private val psiFile: PsiFile
) {
    private val logger = Logger.getInstance(MethodSeparatorPainter::class.java)
    private val highlighters = mutableListOf<RangeHighlighter>()

    /**
     * Applies separator lines to the editor.
     */
    fun apply() {
        clear()

        val project = psiFile.project
        val service = MethodSeparatorService.getInstance(project)
        val config = service.config

        if (!config.enabled) return

        val result = service.detectSeparators(psiFile)
        if (!result.isSuccess) return

        val positions = result.positionsOrEmpty()
        val markupModel = editor.markupModel

        positions.forEach { position ->
            try {
                val line = position.lineNumber - 1 // Convert to 0-indexed
                if (line < 0 || line >= editor.document.lineCount) return@forEach

                val startOffset = editor.document.getLineStartOffset(line)
                val endOffset = editor.document.getLineEndOffset(line)

                val renderer = SeparatorLineRenderer(config)

                val highlighter = markupModel.addRangeHighlighter(
                    startOffset,
                    endOffset,
                    HighlighterLayer.FIRST,
                    null,
                    HighlighterTargetArea.LINES_IN_RANGE
                )

                highlighter.lineMarkerRenderer = renderer
                highlighters.add(highlighter)
            } catch (e: Exception) {
                logger.debug("Failed to add separator at line ${position.lineNumber}", e)
            }
        }
    }

    /**
     * Clears all separator lines.
     */
    fun clear() {
        highlighters.forEach { highlighter ->
            try {
                editor.markupModel.removeHighlighter(highlighter)
            } catch (e: Exception) {
                // Highlighter may already be invalid
            }
        }
        highlighters.clear()
    }

    /**
     * Refreshes separators (clears and reapplies).
     */
    fun refresh() {
        clear()
        apply()
    }
}

/**
 * Custom line renderer for separator lines.
 */
class SeparatorLineRenderer(private val config: MethodSeparatorConfig) : LineMarkerRenderer {

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        val g2d = g as? Graphics2D ?: return

        g2d.color = config.lineColor

        val stroke = when (config.lineStyle) {
            SeparatorLineStyle.SOLID -> BasicStroke(config.lineThickness.toFloat())
            SeparatorLineStyle.DASHED -> BasicStroke(
                config.lineThickness.toFloat(),
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                10f,
                floatArrayOf(8f, 4f),
                0f
            )
            SeparatorLineStyle.DOTTED -> BasicStroke(
                config.lineThickness.toFloat(),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10f,
                floatArrayOf(2f, 4f),
                0f
            )
            SeparatorLineStyle.DOUBLE -> BasicStroke(config.lineThickness.toFloat())
        }

        g2d.stroke = stroke

        val y = r.y
        val startX = r.x + 10
        val endX = r.x + r.width - 10

        g2d.drawLine(startX, y, endX, y)

        if (config.lineStyle == SeparatorLineStyle.DOUBLE) {
            g2d.drawLine(startX, y + 3, endX, y + 3)
        }
    }
}

/**
 * Toggles method separator lines on/off.
 */
class ToggleMethodSeparatorsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = MethodSeparatorService.getInstance(project)
        val enabled = service.toggle()

        val message = if (enabled) "Method separators enabled" else "Method separators disabled"
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = message
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null

        if (project != null) {
            val service = MethodSeparatorService.getInstance(project)
            e.presentation.text = if (service.isEnabled) {
                "Sidekick: Disable Method Separators"
            } else {
                "Sidekick: Enable Method Separators"
            }
        }
    }
}

/**
 * Shows line style selector popup.
 */
class SelectSeparatorStyleAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = MethodSeparatorService.getInstance(project)

        val step = object : BaseListPopupStep<SeparatorLineStyle>(
            "Select Line Style",
            SeparatorLineStyle.ALL
        ) {
            override fun getTextFor(value: SeparatorLineStyle): String = value.displayName

            override fun onChosen(selectedValue: SeparatorLineStyle, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    service.setStyle(selectedValue)
                    com.intellij.openapi.wm.WindowManager.getInstance()
                        .getStatusBar(project)?.info = "Line style set to ${selectedValue.displayName}"
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
 * Shows color theme selector popup.
 */
class SelectSeparatorColorAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = MethodSeparatorService.getInstance(project)

        val step = object : BaseListPopupStep<SeparatorColorTheme>(
            "Select Color Theme",
            SeparatorColorTheme.ALL
        ) {
            override fun getTextFor(value: SeparatorColorTheme): String = value.displayName

            override fun onChosen(selectedValue: SeparatorColorTheme, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    service.setColorTheme(selectedValue)
                    com.intellij.openapi.wm.WindowManager.getInstance()
                        .getStatusBar(project)?.info = "Separator color set to ${selectedValue.displayName}"
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
 * Shows separator statistics for current file.
 */
class ShowSeparatorStatsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val service = MethodSeparatorService.getInstance(project)
        val stats = service.getStats(psiFile)

        val details = buildString {
            appendLine("Total Separators: ${stats.totalSeparators}")
            appendLine("Methods: ${stats.methodCount}")
            appendLine("Classes: ${stats.classCount}")
            appendLine("Properties: ${stats.propertyCount}")
        }

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            details,
            "Separator Statistics: ${psiFile.name}"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && e.getData(CommonDataKeys.PSI_FILE) != null
    }
}
