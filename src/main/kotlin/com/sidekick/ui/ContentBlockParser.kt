// =============================================================================
// ContentBlockParser.kt
// =============================================================================
// Parses raw message text into structured content blocks.
//
// Detects fenced code blocks (triple-backtick) and splits the message into
// an ordered list of Text and CodeFence blocks. This is pure parsing logic
// with no Swing or IntelliJ dependencies, making it easily unit-testable.
//
// @since 1.1.2
// =============================================================================

package com.sidekick.ui

// =============================================================================
// Content Block Model
// =============================================================================

/**
 * A discrete section of a chat message, either plain text or a fenced code block.
 */
sealed class ContentBlock {

    /**
     * A run of plain (non-code) text.
     */
    data class Text(val content: String) : ContentBlock()

    /**
     * A fenced code block with an optional language tag.
     *
     * @property language  The language identifier after the opening ```, or empty string.
     * @property code      The raw code content (without the fence markers).
     * @property lineCount Number of lines in [code].
     */
    data class CodeFence(
        val language: String,
        val code: String,
        val lineCount: Int
    ) : ContentBlock()
}

// =============================================================================
// Parser
// =============================================================================

/**
 * Splits raw message text into an ordered list of [ContentBlock] instances.
 *
 * ## Algorithm
 *
 * Scans line-by-line looking for triple-backtick fence markers.
 * Everything outside a fence becomes a [ContentBlock.Text] block;
 * everything inside becomes a [ContentBlock.CodeFence].
 *
 * ```kotlin
 * val blocks = ContentBlockParser.parse(messageText)
 * blocks.forEach { block ->
 *     when (block) {
 *         is ContentBlock.Text     -> renderText(block.content)
 *         is ContentBlock.CodeFence -> renderCode(block)
 *     }
 * }
 * ```
 *
 * @since 1.1.2
 */
object ContentBlockParser {

    /**
     * Code blocks with at least this many lines are considered "large"
     * and should be rendered as collapsible sections.
     */
    const val COLLAPSE_THRESHOLD = 8

    // Regex for the opening fence: ``` optionally followed by a language tag
    private val FENCE_OPEN_REGEX = Regex("""^`{3,}(\w*)$""")

    /**
     * Parses [text] into an ordered list of [ContentBlock] instances.
     *
     * @param text The raw message text to parse.
     * @return Ordered list of content blocks; never empty for non-blank input.
     */
    fun parse(text: String): List<ContentBlock> {
        if (text.isBlank()) return emptyList()

        val blocks = mutableListOf<ContentBlock>()
        val lines = text.lines()

        val textAccum = StringBuilder()   // accumulates plain-text lines
        val codeAccum = StringBuilder()   // accumulates code lines
        var inFence = false
        var fenceLanguage = ""
        var fenceBacktickCount = 3        // number of backticks in the opening fence

        for (line in lines) {
            if (!inFence) {
                // Check for fence opening
                val match = FENCE_OPEN_REGEX.matchEntire(line.trim())
                if (match != null) {
                    // Flush accumulated text
                    flushText(textAccum, blocks)

                    inFence = true
                    fenceLanguage = match.groupValues[1]
                    fenceBacktickCount = line.trim().takeWhile { it == '`' }.length
                    codeAccum.clear()
                } else {
                    textAccum.appendLine(line)
                }
            } else {
                // Check for fence closing: same or more backticks, nothing else
                val trimmed = line.trim()
                if (trimmed.length >= fenceBacktickCount &&
                    trimmed.all { it == '`' }
                ) {
                    // Close the fence
                    val code = codeAccum.toString().trimEnd('\n')
                    val lineCount = if (code.isEmpty()) 0 else code.lines().size
                    blocks.add(ContentBlock.CodeFence(fenceLanguage, code, lineCount))
                    inFence = false
                    codeAccum.clear()
                } else {
                    codeAccum.appendLine(line)
                }
            }
        }

        // Handle unclosed fence — treat remaining code as text
        if (inFence) {
            // Re-emit the opening fence marker + accumulated code as plain text
            textAccum.append("```$fenceLanguage\n")
            textAccum.append(codeAccum)
        }

        // Flush any trailing text
        flushText(textAccum, blocks)

        return blocks
    }

    /**
     * Checks whether a [ContentBlock.CodeFence] is large enough to collapse.
     */
    fun shouldCollapse(block: ContentBlock.CodeFence): Boolean =
        block.lineCount >= COLLAPSE_THRESHOLD

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun flushText(buffer: StringBuilder, blocks: MutableList<ContentBlock>) {
        if (buffer.isNotEmpty()) {
            val content = buffer.toString().trimEnd('\n')
            if (content.isNotEmpty()) {
                blocks.add(ContentBlock.Text(content))
            }
            buffer.clear()
        }
    }
}
