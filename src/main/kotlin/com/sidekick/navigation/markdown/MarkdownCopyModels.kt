package com.sidekick.navigation.markdown

import java.time.Instant

/**
 * # Markdown Copy Models
 *
 * Data structures for copying code as formatted Markdown.
 * Part of Sidekick v0.4.5 Copy as Markdown feature.
 *
 * ## Overview
 *
 * These models support:
 * - Formatting code selections as Markdown code blocks
 * - Configurable options (line numbers, file paths, etc.)
 * - Collapsible `<details>` wrapping
 * - Language hint for syntax highlighting
 *
 * @since 0.4.5
 */

/**
 * Options for markdown code copying.
 *
 * Configures how the code block is formatted when copying.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val options = MarkdownCopyOptions(
 *     includeFilePath = true,
 *     includeLineNumbers = true,
 *     wrapInDetails = false
 * )
 * ```
 *
 * @property includeFilePath Whether to include file path above code block
 * @property includeLineNumbers Whether to add line numbers to each line
 * @property includeLanguage Whether to include language hint in code fence
 * @property wrapInDetails Whether to wrap in collapsible `<details>` tag
 * @property maxLines Maximum lines to include (null = no limit)
 * @property startLineNumber Starting line number (for accurate line numbering)
 */
data class MarkdownCopyOptions(
    val includeFilePath: Boolean = true,
    val includeLineNumbers: Boolean = false,
    val includeLanguage: Boolean = true,
    val wrapInDetails: Boolean = false,
    val maxLines: Int? = null,
    val startLineNumber: Int = 1
) {
    /**
     * Returns options with file path toggled.
     */
    fun toggleFilePath() = copy(includeFilePath = !includeFilePath)

    /**
     * Returns options with line numbers toggled.
     */
    fun toggleLineNumbers() = copy(includeLineNumbers = !includeLineNumbers)

    /**
     * Returns options with language toggled.
     */
    fun toggleLanguage() = copy(includeLanguage = !includeLanguage)

    /**
     * Returns options with details wrapping toggled.
     */
    fun toggleDetails() = copy(wrapInDetails = !wrapInDetails)

    /**
     * Returns options with max lines set.
     */
    fun withMaxLines(max: Int?) = copy(maxLines = max?.coerceAtLeast(1))

    /**
     * Returns options with starting line number set.
     */
    fun withStartLine(line: Int) = copy(startLineNumber = line.coerceAtLeast(1))

    /**
     * Creates a preset for minimal output.
     */
    companion object {
        /**
         * Minimal: just the code block.
         */
        val MINIMAL = MarkdownCopyOptions(
            includeFilePath = false,
            includeLineNumbers = false,
            includeLanguage = true,
            wrapInDetails = false
        )

        /**
         * Full: all metadata included.
         */
        val FULL = MarkdownCopyOptions(
            includeFilePath = true,
            includeLineNumbers = true,
            includeLanguage = true,
            wrapInDetails = false
        )

        /**
         * Collapsible: wrapped in details tag.
         */
        val COLLAPSIBLE = MarkdownCopyOptions(
            includeFilePath = true,
            includeLineNumbers = false,
            includeLanguage = true,
            wrapInDetails = true
        )
    }
}

/**
 * Output format options.
 */
enum class MarkdownFormat(val displayName: String) {
    /** Standard GitHub-flavored markdown */
    GITHUB("GitHub"),

    /** GitLab-flavored markdown */
    GITLAB("GitLab"),

    /** Markdown with HTML extensions */
    HTML_EXTENDED("HTML Extended");

    override fun toString(): String = displayName
}

/**
 * Formatted markdown result.
 *
 * Contains the formatted markdown string along with metadata
 * about the original code.
 *
 * @property markdown The formatted markdown string
 * @property lineCount Number of lines in the original code
 * @property language Language hint (file extension)
 * @property wasTruncated Whether the output was truncated
 * @property originalLength Original character count
 */
data class MarkdownCode(
    val markdown: String,
    val lineCount: Int,
    val language: String?,
    val wasTruncated: Boolean = false,
    val originalLength: Int = 0
) {
    /**
     * Character count of the markdown output.
     */
    val markdownLength: Int get() = markdown.length

    /**
     * Summary for status bar display.
     */
    val summary: String
        get() = buildString {
            append("$lineCount lines")
            language?.let { append(" ($it)") }
            if (wasTruncated) append(" [truncated]")
        }

    /**
     * Returns true if the output is empty.
     */
    val isEmpty: Boolean get() = markdown.isBlank()

    companion object {
        /**
         * Formats code as markdown with the given options.
         *
         * @param code The source code to format
         * @param language Language hint (usually file extension)
         * @param filePath Optional file path to include
         * @param options Formatting options
         * @param lineRange Optional line range for accurate numbering
         * @return Formatted markdown result
         */
        fun format(
            code: String,
            language: String? = null,
            filePath: String? = null,
            options: MarkdownCopyOptions = MarkdownCopyOptions(),
            lineRange: IntRange? = null
        ): MarkdownCode {
            if (code.isEmpty()) {
                val lang = language?.let { mapLanguage(it) } ?: ""
                return MarkdownCode(
                    markdown = "```$lang\n```",
                    lineCount = 0,
                    language = language,
                    originalLength = 0
                )
            }

            val lines = code.lines()
            val truncated = options.maxLines?.let { lines.take(it) } ?: lines
            val wasTruncated = options.maxLines != null && lines.size > options.maxLines

            val startLine = lineRange?.first?.plus(1) ?: options.startLineNumber
            val lineNumberWidth = (startLine + truncated.size - 1).toString().length.coerceAtLeast(3)

            val content = buildString {
                // File path header
                if (options.includeFilePath && filePath != null) {
                    val displayPath = filePath.substringAfterLast('/')
                    appendLine("**`$displayPath`**")
                    appendLine()
                }

                // Details wrapper start
                if (options.wrapInDetails) {
                    val summaryText = language?.let { "Code ($it)" } ?: "Code"
                    appendLine("<details>")
                    appendLine("<summary>$summaryText</summary>")
                    appendLine()
                }

                // Code fence start
                append("```")
                if (options.includeLanguage && language != null) {
                    append(mapLanguage(language))
                }
                appendLine()

                // Code content
                if (options.includeLineNumbers) {
                    truncated.forEachIndexed { i, line ->
                        val lineNum = (startLine + i).toString().padStart(lineNumberWidth)
                        appendLine("$lineNum | $line")
                    }
                } else {
                    truncated.forEach { appendLine(it) }
                }

                // Truncation notice
                if (wasTruncated) {
                    appendLine("// ... ${lines.size - truncated.size} more lines")
                }

                // Code fence end
                appendLine("```")

                // Details wrapper end
                if (options.wrapInDetails) {
                    appendLine()
                    appendLine("</details>")
                }
            }

            return MarkdownCode(
                markdown = content.trimEnd(),
                lineCount = lines.size,
                language = language,
                wasTruncated = wasTruncated,
                originalLength = code.length
            )
        }

        /**
         * Maps file extensions to markdown language hints.
         */
        private fun mapLanguage(extension: String): String {
            return when (extension.lowercase()) {
                "kt", "kts" -> "kotlin"
                "java" -> "java"
                "cs" -> "csharp"
                "py" -> "python"
                "js", "mjs" -> "javascript"
                "ts", "tsx" -> "typescript"
                "jsx" -> "jsx"
                "rb" -> "ruby"
                "go" -> "go"
                "rs" -> "rust"
                "swift" -> "swift"
                "c", "h" -> "c"
                "cpp", "hpp", "cc" -> "cpp"
                "xml" -> "xml"
                "html", "htm" -> "html"
                "css" -> "css"
                "scss", "sass" -> "scss"
                "json" -> "json"
                "yaml", "yml" -> "yaml"
                "toml" -> "toml"
                "md" -> "markdown"
                "sh", "bash", "zsh" -> "bash"
                "ps1" -> "powershell"
                "sql" -> "sql"
                "graphql", "gql" -> "graphql"
                "diff", "patch" -> "diff"
                else -> extension.lowercase()
            }
        }
    }
}

/**
 * Result of a markdown copy operation.
 */
sealed class MarkdownCopyResult {
    data class Success(val code: MarkdownCode) : MarkdownCopyResult()
    data class NoSelection(val message: String = "No text selected") : MarkdownCopyResult()
    data class Error(val message: String) : MarkdownCopyResult()

    val isSuccess: Boolean get() = this is Success
}

/**
 * Preset configurations for common use cases.
 */
enum class MarkdownPreset(
    val displayName: String,
    val options: MarkdownCopyOptions
) {
    /** Quick copy with just code */
    QUICK("Quick", MarkdownCopyOptions.MINIMAL),

    /** Standard with file path */
    STANDARD("Standard", MarkdownCopyOptions()),

    /** Full with line numbers */
    DETAILED("Detailed", MarkdownCopyOptions.FULL),

    /** Collapsible for long code */
    COLLAPSIBLE("Collapsible", MarkdownCopyOptions.COLLAPSIBLE);

    override fun toString(): String = displayName
}
