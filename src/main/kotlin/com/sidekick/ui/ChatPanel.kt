// =============================================================================
// ChatPanel.kt
// =============================================================================
// Main chat interface panel for the Sidekick tool window.
//
// This panel provides:
// - Scrollable message list showing conversation history
// - Multi-line input area for composing messages
// - Send button to submit messages
// - Connection status indicator
//
// DESIGN NOTES:
// - Uses Swing components for IntelliJ Platform compatibility
// - Delegates business logic to ChatController
// - Messages are displayed as styled MessageBubble components
// - Supports real-time streaming response updates
// =============================================================================

package com.sidekick.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.sidekick.core.SidekickBundle
import com.sidekick.core.SidekickIcons
import com.sidekick.services.ollama.models.ConnectionStatus
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Main chat interface panel for the Sidekick tool window.
 *
 * Provides the UI for conversing with the local LLM via Ollama.
 * Handles message display, input, and real-time streaming responses.
 *
 * @property project The current project context
 */
class ChatPanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    companion object {
        private val LOG = Logger.getInstance(ChatPanel::class.java)
        
        // UI Constants
        private const val INPUT_ROWS = 3
        private const val INPUT_COLS = 40
    }

    // -------------------------------------------------------------------------
    // UI Components
    // -------------------------------------------------------------------------
    
    /**
     * Container for message bubbles, laid out vertically.
     */
    private val messageListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor.PanelBackground
    }
    
    /**
     * Scroll pane wrapping the message list for overflow handling.
     */
    private val scrollPane = JBScrollPane(messageListPanel).apply {
        border = JBUI.Borders.empty()
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }
    
    /**
     * Multi-line text input for composing messages.
     */
    private val inputArea = JBTextArea(INPUT_ROWS, INPUT_COLS).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8)
        emptyText.text = SidekickBundle.message("chat.input.placeholder")
    }
    
    /**
     * Button to send the current message.
     */
    private val sendButton = JButton().apply {
        icon = SidekickIcons.SEND
        toolTipText = SidekickBundle.message("chat.send.button")
        addActionListener { onSendMessage() }
    }
    
    /**
     * Status label showing connection state.
     */
    private val statusLabel = JBLabel().apply {
        border = JBUI.Borders.empty(4, 8)
    }

    // -------------------------------------------------------------------------
    // Controller
    // -------------------------------------------------------------------------
    
    /**
     * Controller handling chat business logic and Ollama communication.
     */
    private val controller = ChatController(project, this)

    // -------------------------------------------------------------------------
    // Current streaming message
    // -------------------------------------------------------------------------
    
    /**
     * The message bubble currently being streamed to (for appending tokens).
     */
    private var currentStreamingBubble: MessageBubble? = null

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    
    init {
        LOG.info("Initializing ChatPanel for project: ${project.name}")
        
        // Register for disposal
        Disposer.register(this, controller)
        
        // Build the UI layout
        setupLayout()
        
        // Add keyboard shortcuts
        setupKeyboardShortcuts()
        
        // Show welcome message
        showWelcomeMessage()
        
        // Update initial connection status
        updateConnectionStatus(ConnectionStatus.NOT_CONFIGURED)
    }

    /**
     * Sets up the panel layout with header, message area, and input.
     */
    private fun setupLayout() {
        // Header with status
        val headerPanel = createHeaderPanel()
        add(headerPanel, BorderLayout.NORTH)
        
        // Message area (center)
        add(scrollPane, BorderLayout.CENTER)
        
        // Input area (bottom)
        val inputPanel = createInputPanel()
        add(inputPanel, BorderLayout.SOUTH)
    }
    
    /**
     * Creates the header panel with title and status indicator.
     */
    private fun createHeaderPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            background = JBColor.PanelBackground
            
            val titleLabel = JBLabel(SidekickBundle.message("plugin.name")).apply {
                font = font.deriveFont(Font.BOLD, 14f)
                icon = SidekickIcons.SIDEKICK
            }
            
            add(titleLabel, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
        }
    }
    
    /**
     * Creates the input panel with text area and send button.
     */
    private fun createInputPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            background = JBColor.PanelBackground
            
            // Input text area in a scroll pane
            val inputScrollPane = JBScrollPane(inputArea).apply {
                border = JBUI.Borders.customLine(JBColor.border(), 1)
            }
            
            add(inputScrollPane, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
    }
    
    /**
     * Sets up keyboard shortcuts for the input area.
     */
    private fun setupKeyboardShortcuts() {
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                // Send on Enter (without Shift)
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    onSendMessage()
                }
                // Shift+Enter for newline (default behavior)
            }
        })
    }

    // -------------------------------------------------------------------------
    // Welcome Message
    // -------------------------------------------------------------------------
    
    /**
     * Shows the initial welcome message when the panel is created.
     */
    private fun showWelcomeMessage() {
        val welcomePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.PanelBackground
            border = JBUI.Borders.empty(32)
            alignmentX = Component.CENTER_ALIGNMENT
            
            val titleLabel = JBLabel(SidekickBundle.message("chat.welcome.title")).apply {
                font = font.deriveFont(Font.BOLD, 16f)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            
            val messageLabel = JBLabel("<html><center>${SidekickBundle.message("chat.welcome.message")}</center></html>").apply {
                alignmentX = Component.CENTER_ALIGNMENT
                border = JBUI.Borders.emptyTop(8)
            }
            
            add(titleLabel)
            add(messageLabel)
        }
        
        messageListPanel.add(welcomePanel)
    }

    // -------------------------------------------------------------------------
    // Message Handling
    // -------------------------------------------------------------------------
    
    /**
     * Handles the send button click or Enter key press.
     */
    private fun onSendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        
        LOG.debug("Sending message: ${text.take(50)}...")
        
        // Clear the input
        inputArea.text = ""
        
        // Clear welcome message if present
        clearWelcomeIfNeeded()
        
        // Add user message bubble
        addUserMessage(text)
        
        // Send to controller
        controller.sendMessage(text)
    }
    
    /**
     * Removes the welcome panel if it's still showing.
     */
    private fun clearWelcomeIfNeeded() {
        if (messageListPanel.componentCount == 1 && 
            messageListPanel.getComponent(0) !is MessageBubble) {
            messageListPanel.removeAll()
        }
    }
    
    /**
     * Adds a user message bubble to the chat.
     */
    private fun addUserMessage(text: String) {
        val bubble = MessageBubble(text, isUser = true)
        messageListPanel.add(bubble)
        messageListPanel.revalidate()
        scrollToBottom()
    }
    
    /**
     * Starts a new assistant message bubble for streaming.
     * Called when the first token of a response is received.
     */
    fun startAssistantMessage() {
        SwingUtilities.invokeLater {
            val bubble = MessageBubble("", isUser = false)
            currentStreamingBubble = bubble
            messageListPanel.add(bubble)
            messageListPanel.revalidate()
            scrollToBottom()
        }
    }
    
    /**
     * Appends a token to the current streaming message.
     *
     * @param token The token text to append
     */
    fun appendToken(token: String) {
        SwingUtilities.invokeLater {
            currentStreamingBubble?.appendText(token)
            scrollToBottom()
        }
    }
    
    /**
     * Marks the current streaming message as complete.
     */
    fun completeAssistantMessage() {
        SwingUtilities.invokeLater {
            currentStreamingBubble?.markComplete()
            currentStreamingBubble = null
        }
    }
    
    /**
     * Shows an error message in the chat.
     *
     * @param error The error message to display
     */
    fun showError(error: String) {
        SwingUtilities.invokeLater {
            val errorBubble = MessageBubble(
                "⚠️ Error: $error",
                isUser = false,
                isError = true
            )
            messageListPanel.add(errorBubble)
            messageListPanel.revalidate()
            scrollToBottom()
            
            // Clear any streaming state
            currentStreamingBubble = null
        }
    }
    
    /**
     * Updates the connection status indicator.
     *
     * @param status The current connection status
     */
    fun updateConnectionStatus(status: ConnectionStatus) {
        SwingUtilities.invokeLater {
            val (text, color) = when (status) {
                ConnectionStatus.CONNECTED -> 
                    SidekickBundle.message("status.connected") to JBColor.GREEN
                ConnectionStatus.DISCONNECTED -> 
                    SidekickBundle.message("status.disconnected") to JBColor.RED
                ConnectionStatus.CONNECTING -> 
                    SidekickBundle.message("status.connecting") to JBColor.YELLOW
                ConnectionStatus.NOT_CONFIGURED -> 
                    SidekickBundle.message("status.not.configured") to JBColor.GRAY
            }
            
            statusLabel.text = text
            statusLabel.foreground = color
        }
    }
    
    /**
     * Enables or disables the input controls.
     *
     * @param enabled Whether input should be enabled
     */
    fun setInputEnabled(enabled: Boolean) {
        SwingUtilities.invokeLater {
            inputArea.isEnabled = enabled
            sendButton.isEnabled = enabled
        }
    }
    
    /**
     * Scrolls the message list to the bottom.
     */
    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val scrollBar = scrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }

    // -------------------------------------------------------------------------
    // Disposable Implementation
    // -------------------------------------------------------------------------
    
    override fun dispose() {
        LOG.debug("Disposing ChatPanel")
        // Controller will be disposed via Disposer registration
    }
}
