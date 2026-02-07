// =============================================================================
// ChatExportService.kt
// =============================================================================
// Pure formatting logic for exporting chat conversations as Markdown.
//
// This service has no UI or IntelliJ dependencies, making it easily
// unit-testable. It provides two capabilities:
//
// 1. Format a single message bubble as Markdown (for per-bubble clipboard copy)
// 2. Format an entire conversation with metadata header (for file export)
//
// @since 1.1.1
// =============================================================================

package com.sidekick.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// =============================================================================
// Data Transfer Object
// =============================================================================

/**
 * Lightweight DTO representing a single chat message for export.
 *
 * Extracted from [MessageBubble] state so that formatting logic stays
 * decoupled from Swing components.
 *
 * @property text    The raw text content of the message.
 * @property isUser  True if the message was sent by the user.
 * @property isError True if the message is an error notification.
 */
data class ExportableMessage(
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false
)

// =============================================================================
// Export Service
// =============================================================================

/**
 * Formats chat messages and conversations as Markdown.
 *
 * ## Per-Bubble Copy
 *
 * ```kotlin
 * val md = ChatExportService.formatSingleMessage("Hello!", isUser = true)
 * // => "**You:**\n\nHello!"
 * ```
 *
 * ## Full Conversation Export
 *
 * ```kotlin
 * val md = ChatExportService.formatConversation(
 *     messages = listOf(ExportableMessage("Hi", true), ExportableMessage("Hello!", false)),
 *     projectName = "MyApp",
 *     modelName = "llama3.2"
 * )
 * ```
 *
 * @since 1.1.1
 */
object ChatExportService {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private const val USER_PREFIX = "**You:**"
    private const val ASSISTANT_PREFIX = "**Sidekick:**"
    private const val ERROR_PREFIX = "> ⚠️ **Error:**"
    private const val MESSAGE_SEPARATOR = "\n\n---\n\n"
    private val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // -------------------------------------------------------------------------
    // Single Message Formatting
    // -------------------------------------------------------------------------

    /**
     * Formats a single chat message as Markdown.
     *
     * - **User** messages are prefixed with `**You:**`
     * - **Assistant** messages are prefixed with `**Sidekick:**`
     * - **Error** messages are wrapped in a Markdown block-quote with a ⚠️ icon
     *
     * @param text    The raw text content of the message.
     * @param isUser  Whether the message was sent by the user.
     * @param isError Whether the message is an error notification.
     * @return The formatted Markdown string.
     */
    fun formatSingleMessage(
        text: String,
        isUser: Boolean,
        isError: Boolean = false
    ): String {
        if (text.isBlank()) return ""

        return when {
            isError -> buildErrorBlock(text)
            isUser  -> "$USER_PREFIX\n\n$text"
            else    -> "$ASSISTANT_PREFIX\n\n$text"
        }
    }

    // -------------------------------------------------------------------------
    // Full Conversation Formatting
    // -------------------------------------------------------------------------

    /**
     * Formats an entire conversation as a Markdown document.
     *
     * The output contains:
     * 1. A YAML-style metadata header (project name, date, model)
     * 2. Each message separated by horizontal rules
     *
     * @param messages    The ordered list of messages to export.
     * @param projectName The name of the current project (optional).
     * @param modelName   The LLM model used for this conversation (optional).
     * @param timestamp   The export timestamp (defaults to now).
     * @return The complete Markdown document as a string.
     */
    fun formatConversation(
        messages: List<ExportableMessage>,
        projectName: String? = null,
        modelName: String? = null,
        timestamp: LocalDateTime = LocalDateTime.now()
    ): String {
        if (messages.isEmpty()) return buildHeader(projectName, modelName, timestamp)

        val header = buildHeader(projectName, modelName, timestamp)
        val body = messages.joinToString(MESSAGE_SEPARATOR) { msg ->
            formatSingleMessage(msg.text, msg.isUser, msg.isError)
        }

        return "$header\n\n$body\n"
    }

    // -------------------------------------------------------------------------
    // Internal Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the YAML-style metadata header for the Markdown document.
     */
    private fun buildHeader(
        projectName: String?,
        modelName: String?,
        timestamp: LocalDateTime
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Sidekick Chat Export")
        sb.appendLine()

        val meta = mutableListOf<String>()
        meta.add("- **Exported:** ${timestamp.format(DATE_FORMATTER)}")
        if (!projectName.isNullOrBlank()) {
            meta.add("- **Project:** $projectName")
        }
        if (!modelName.isNullOrBlank()) {
            meta.add("- **Model:** $modelName")
        }

        sb.appendLine(meta.joinToString("\n"))
        sb.appendLine()
        sb.append("---")

        return sb.toString()
    }

    /**
     * Wraps error text in a Markdown block-quote with a warning icon.
     *
     * Multi-line errors have each line prefixed with `>` to maintain
     * the block-quote across the entire message.
     */
    private fun buildErrorBlock(text: String): String {
        val lines = text.lines()
        return if (lines.size == 1) {
            "$ERROR_PREFIX $text"
        } else {
            val first = "$ERROR_PREFIX ${lines.first()}"
            val rest = lines.drop(1).joinToString("\n") { "> $it" }
            "$first\n$rest"
        }
    }
}
