// =============================================================================
// MessageBubble.kt
// =============================================================================
// Styled message bubble component for displaying chat messages.
//
// Renders messages with rounded corners and different styling based on
// whether it's a user message, assistant message, or error.
//
// DESIGN NOTES:
// - User messages: Right-aligned with blue background (accent color)
// - Assistant messages: Left-aligned with gray background
// - Error messages: Left-aligned with red/orange background
// - Supports text appending for streaming responses
// - Uses antialiased rendering for smooth corners
// - Text wraps within the bubble at 80% of the parent's width
// =============================================================================

package com.sidekick.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * A styled message bubble for displaying chat messages.
 *
 * Supports both user and assistant messages with distinct visual styling.
 * Can be used in streaming mode where text is appended incrementally.
 *
 * @property initialText The initial message text
 * @property isUser Whether this is a user message (affects alignment and color)
 * @property isError Whether this is an error message (affects color)
 */
class MessageBubble(
    initialText: String,
    internal val isUser: Boolean,
    internal val isError: Boolean = false
) : JPanel() {

    companion object {
        // Color scheme for light and dark themes
        private val USER_BG = JBColor(
            Color(0x2196F3),  // Light theme: Material Blue
            Color(0x1976D2)   // Dark theme: Darker blue
        )
        
        private val ASSISTANT_BG = JBColor(
            Color(0xF5F5F5),  // Light theme: Light gray
            Color(0x3C3F41)   // Dark theme: IntelliJ dark panel
        )
        
        private val ERROR_BG = JBColor(
            Color(0xFFCDD2),  // Light theme: Light red
            Color(0x5D3A3A)   // Dark theme: Dark red
        )
        
        private val USER_FG = JBColor(
            Color.WHITE,      // Light theme
            Color.WHITE       // Dark theme
        )
        
        private val ASSISTANT_FG = JBColor(
            Color(0x212121),  // Light theme: Dark gray
            Color(0xBBBBBB)   // Dark theme: Light gray
        )
        
        private val ERROR_FG = JBColor(
            Color(0xB71C1C),  // Light theme: Dark red
            Color(0xFFCDD2)   // Dark theme: Light red
        )
        
        // Layout constants
        private const val CORNER_RADIUS = 12
        private const val MAX_WIDTH_RATIO = 0.8  // Max 80% of parent width
    }

    // -------------------------------------------------------------------------
    // Components
    // -------------------------------------------------------------------------
    
    /**
     * The text pane displaying the message content.
     * Text wrapping is handled by constraining the pane's width in doLayout.
     */
    private val textPane = JTextPane().apply {
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty(10, 14)
        font = JBUI.Fonts.label(13f)
        
        // Set text color based on message type
        foreground = when {
            isError -> ERROR_FG
            isUser -> USER_FG
            else -> ASSISTANT_FG
        }
    }
    
    /**
     * Content panel that holds the text pane with a rounded background.
     */
    /**
     * "Copy as Markdown" button, shown after streaming completes.
     * Hidden by default; revealed by [markComplete].
     *
     * @since 1.1.1
     */
    private val copyButton = JButton(AllIcons.Actions.Copy).apply {
        toolTipText = "Copy as Markdown"
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isVisible = false  // hidden until markComplete()
        preferredSize = Dimension(24, 24)
        addActionListener { copyAsMarkdown() }
    }

    private val contentPanel = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )
            
            // Fill with rounded rectangle
            g2.color = getBackgroundColor()
            g2.fillRoundRect(0, 0, width, height, CORNER_RADIUS, CORNER_RADIUS)
        }
    }.apply {
        isOpaque = false
        add(textPane, BorderLayout.CENTER)

        // Bottom bar with the copy button, right-aligned
        val bottomBar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(copyButton)
        }
        add(bottomBar, BorderLayout.SOUTH)
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    
    init {
        isOpaque = false
        layout = BorderLayout()
        border = JBUI.Borders.empty(4, 8)
        
        // Use a simple wrapper that respects alignment via BorderLayout position
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(contentPanel, if (isUser) BorderLayout.EAST else BorderLayout.WEST)
        }
        
        add(wrapper, BorderLayout.CENTER)
        
        // Set initial text
        if (initialText.isNotEmpty()) {
            textPane.text = initialText
        }
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------
    
    /**
     * Appends text to the message (used for streaming responses).
     *
     * @param text The text to append
     */
    fun appendText(text: String) {
        val doc = textPane.styledDocument
        val attrs = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, textPane.foreground)
        }
        doc.insertString(doc.length, text, attrs)
        revalidate()
        repaint()
    }
    
    /**
     * Marks the message as complete.
     *
     * Reveals the "Copy as Markdown" button so users can copy the
     * fully-streamed response to the clipboard.
     *
     * @since 1.1.1
     */
    fun markComplete() {
        copyButton.isVisible = true
        revalidate()
        repaint()
    }
    
    /**
     * Gets the full message text.
     */
    fun getText(): String = textPane.text

    /**
     * Creates an [ExportableMessage] DTO from this bubble's state.
     *
     * @since 1.1.1
     */
    fun toExportableMessage(): ExportableMessage =
        ExportableMessage(text = getText(), isUser = isUser, isError = isError)

    // -------------------------------------------------------------------------
    // Clipboard Copy (v1.1.1)
    // -------------------------------------------------------------------------

    /**
     * Formats the bubble's content as Markdown and copies it to the clipboard.
     * Shows a brief tooltip confirmation.
     */
    private fun copyAsMarkdown() {
        val markdown = ChatExportService.formatSingleMessage(getText(), isUser, isError)
        CopyPasteManager.getInstance().setContents(StringSelection(markdown))

        // Flash the tooltip as confirmation
        val original = copyButton.toolTipText
        copyButton.toolTipText = "Copied!"
        Timer(1500) { copyButton.toolTipText = original }.apply {
            isRepeats = false
            start()
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------
    
    /**
     * Returns the appropriate background color based on message type.
     */
    private fun getBackgroundColor(): Color = when {
        isError -> ERROR_BG
        isUser -> USER_BG
        else -> ASSISTANT_BG
    }
    
    // -------------------------------------------------------------------------
    // Layout — constrain bubble width so JTextPane wraps text
    // -------------------------------------------------------------------------
    
    override fun getPreferredSize(): Dimension {
        val parentWidth = parent?.width ?: 400
        val maxBubbleWidth = (parentWidth * MAX_WIDTH_RATIO).toInt()
        
        // Give the text pane a constrained width so it can compute wrapped height
        textPane.setSize(maxBubbleWidth - 32, Short.MAX_VALUE.toInt())
        val textPref = textPane.preferredSize
        
        // Bubble width is the lesser of natural width or max
        val bubbleWidth = minOf(textPref.width + 32, maxBubbleWidth)
        val bubbleHeight = textPref.height + 8  // padding for border
        
        return Dimension(parentWidth, bubbleHeight)
    }
    
    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(Int.MAX_VALUE, pref.height)
    }
}
