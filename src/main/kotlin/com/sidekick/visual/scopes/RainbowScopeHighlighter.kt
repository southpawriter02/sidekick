package com.sidekick.visual.scopes

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile

/**
 * # Rainbow Scope Highlighter
 *
 * Applies rainbow scope highlighting to an editor.
 * Part of Sidekick v0.5.2 Rainbow Scopes feature.
 *
 * @since 0.5.2
 */
class RainbowScopeHighlighter(
    private val editor: Editor,
    private val psiFile: PsiFile
) {
    private val logger = Logger.getInstance(RainbowScopeHighlighter::class.java)
    private val markupModel = editor.markupModel
    private val highlighters = mutableListOf<RangeHighlighter>()

    /**
     * Applies scope highlighting.
     */
    fun apply() {
        clear()

        val project = psiFile.project
        val service = RainbowScopeService.getInstance(project)
        val config = service.config

        if (!config.enabled) return

        val result = service.detectScopes(psiFile)
        if (!result.isSuccess) return

        val scopes = result.scopesOrEmpty()

        scopes.forEach { scope ->
            try {
                val color = config.colorScheme.colorForLevel(scope.nestingLevel, config.opacity)

                val attributes = TextAttributes().apply {
                    backgroundColor = color
                }

                val highlighter = markupModel.addRangeHighlighter(
                    scope.startOffset,
                    scope.endOffset.coerceAtMost(editor.document.textLength),
                    HighlighterLayer.FIRST - scope.nestingLevel,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE
                )

                highlighters.add(highlighter)
            } catch (e: Exception) {
                logger.debug("Failed to add highlighter for scope: ${scope.summary}", e)
            }
        }
    }

    /**
     * Clears all scope highlighting.
     */
    fun clear() {
        highlighters.forEach { highlighter ->
            try {
                markupModel.removeHighlighter(highlighter)
            } catch (e: Exception) {
                // Highlighter may already be invalid
            }
        }
        highlighters.clear()
    }

    /**
     * Refreshes highlighting (clears and reapplies).
     */
    fun refresh() {
        clear()
        apply()
    }
}

/**
 * Toggles rainbow scope highlighting on/off.
 */
class ToggleRainbowScopesAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RainbowScopeService.getInstance(project)
        val enabled = service.toggle()

        val message = if (enabled) "Rainbow scopes enabled" else "Rainbow scopes disabled"
        com.intellij.openapi.wm.WindowManager.getInstance()
            .getStatusBar(project)?.info = message
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null

        if (project != null) {
            val service = RainbowScopeService.getInstance(project)
            e.presentation.text = if (service.isEnabled) {
                "Sidekick: Disable Rainbow Scopes"
            } else {
                "Sidekick: Enable Rainbow Scopes"
            }
        }
    }
}

/**
 * Shows color scheme selector popup.
 */
class SelectScopeSchemeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = RainbowScopeService.getInstance(project)

        val step = object : BaseListPopupStep<ScopeColorScheme>(
            "Select Scope Scheme",
            ScopeColorScheme.ALL
        ) {
            override fun getTextFor(value: ScopeColorScheme): String = value.displayName

            override fun onChosen(selectedValue: ScopeColorScheme, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    service.setScheme(selectedValue)
                    com.intellij.openapi.wm.WindowManager.getInstance()
                        .getStatusBar(project)?.info = "Scope scheme set to ${selectedValue.displayName}"
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
 * Shows scope info for current cursor position.
 */
class ShowCurrentScopeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val service = RainbowScopeService.getInstance(project)
        val offset = editor.caretModel.offset
        val scope = service.getScopeAtOffset(psiFile, offset)

        val message = scope?.summary ?: "Not in a scope"

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            message,
            "Current Scope"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}

/**
 * Shows scope statistics for current file.
 */
class ShowScopeStatsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val service = RainbowScopeService.getInstance(project)
        val stats = service.getStats(psiFile)

        val details = buildString {
            appendLine("Total Scopes: ${stats.totalScopes}")
            appendLine("Max Nesting: ${stats.maxNesting}")
            appendLine()
            appendLine("By Type:")
            stats.scopesByType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                appendLine("  ${type.displayName}: $count")
            }
        }

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            details,
            "Scope Statistics: ${psiFile.name}"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null && e.getData(CommonDataKeys.PSI_FILE) != null
    }
}
