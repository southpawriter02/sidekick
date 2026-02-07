// =============================================================================
// CollapsibleSection.kt
// =============================================================================
// A Swing panel that renders a clickable header with a collapsible content area.
//
// Used by MessageBubble to collapse large code blocks so the chat remains
// readable. Initially collapsed; click the header to expand/collapse.
//
// DESIGN NOTES:
// - Header shows: ▶/▼ icon + title + line count badge
// - Content area: monospaced JTextArea for code
// - Theme-aware colors derived from the parent bubble's foreground
// - Smooth toggle without animation (instant show/hide)
//
// @since 1.1.2
// =============================================================================

package com.sidekick.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * A collapsible panel that hides its content behind a clickable header.
 *
 * @param title      The header text (e.g., "kotlin" or "Code")
 * @param content    The text content to show when expanded
 * @param lineCount  Number of lines in the content (displayed as a badge)
 * @param foreground The text color, inherited from the parent bubble
 *
 * @since 1.1.2
 */
class CollapsibleSection(
    private val title: String,
    private val content: String,
    private val lineCount: Int,
    foreground: Color
) : JPanel(BorderLayout()) {

    companion object {
        private const val COLLAPSED_ICON = "▶"
        private const val EXPANDED_ICON = "▼"

        // Subtle background tint for the header
        private val HEADER_BG = JBColor(
            Color(0x00000000, true),   // Light theme: transparent
            Color(0x00000000, true)    // Dark theme: transparent
        )

        // Slightly dimmed color for the line-count badge
        private val BADGE_FG = JBColor(
            Color(0x757575),  // Light: medium gray
            Color(0x888888)   // Dark: medium gray
        )

        private val CODE_FONT = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private var expanded = false

    // -------------------------------------------------------------------------
    // Components
    // -------------------------------------------------------------------------

    private val headerLabel = JLabel("$COLLAPSED_ICON $title  ($lineCount lines)").apply {
        this.foreground = foreground
        font = JBUI.Fonts.label(12f).deriveFont(Font.BOLD)
        border = JBUI.Borders.empty(4, 8)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private val contentArea = JTextArea(content).apply {
        isEditable = false
        isOpaque = false
        this.foreground = foreground
        font = CODE_FONT
        border = JBUI.Borders.empty(4, 16, 8, 8)
        lineWrap = false
        tabSize = 4
        isVisible = false  // initially collapsed
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    init {
        isOpaque = false

        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(headerLabel, BorderLayout.WEST)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(contentArea, BorderLayout.CENTER)

        // Toggle on click
        headerLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggle()
            }
        })
    }

    // -------------------------------------------------------------------------
    // Toggle Logic
    // -------------------------------------------------------------------------

    /**
     * Toggles the section between collapsed and expanded.
     */
    private fun toggle() {
        expanded = !expanded
        contentArea.isVisible = expanded

        val icon = if (expanded) EXPANDED_ICON else COLLAPSED_ICON
        headerLabel.text = "$icon $title  ($lineCount lines)"

        revalidate()
        repaint()

        // Bubble up so the parent scroll pane adjusts
        var parent: Container? = parent
        while (parent != null) {
            parent.revalidate()
            parent.repaint()
            parent = parent.parent
        }
    }

    /**
     * Returns the raw content text (used by export; the content is always
     * available regardless of collapsed/expanded state).
     */
    fun getContentText(): String = content
}
