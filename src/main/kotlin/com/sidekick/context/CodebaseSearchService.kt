// =============================================================================
// CodebaseSearchService.kt
// =============================================================================
// Project-level service for searching source files by keyword relevance.
//
// This service:
// - Walks the project's VFS tree to discover source files
// - Scores files against user query keywords (filename + content matching)
// - Returns top N matching files with relevant snippets
//
// DESIGN NOTES:
// - Uses IntelliJ VFS for fast file access (no disk I/O for cached files)
// - Respects ProjectType.SKIP_DIRECTORIES to avoid noise
// - Caps results at MAX_RESULTS files / MAX_TOTAL_CHARS to control token usage
// - Skips large files (>100 KB) and binary extensions
// - Thread-safe: all VFS reads go through ReadAction
// =============================================================================

package com.sidekick.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Service for searching the project codebase by keyword relevance.
 *
 * Scans source files and scores them against user query keywords.
 * Returns the top matching files with content snippets.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = CodebaseSearchService.getInstance(project)
 * val results = service.search("authentication login user")
 * results.forEach { println("${it.fileName}: ${it.score}") }
 * ```
 */
@Service(Service.Level.PROJECT)
class CodebaseSearchService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(CodebaseSearchService::class.java)

        /**
         * Maximum number of search results to return.
         */
        const val MAX_RESULTS = 5

        /**
         * Maximum total characters across all results.
         */
        const val MAX_TOTAL_CHARS = 12_000

        /**
         * Maximum file size to consider (bytes).
         * Files larger than this are skipped to avoid reading binaries/generated code.
         */
        private const val MAX_FILE_SIZE = 100_000L

        /**
         * Maximum depth to scan the project tree.
         */
        private const val MAX_SCAN_DEPTH = 8

        /**
         * Source file extensions to consider.
         */
        private val SOURCE_EXTENSIONS = setOf(
            // JVM
            "kt", "kts", "java", "scala", "groovy",
            // .NET
            "cs", "fs", "vb", "razor", "cshtml",
            // Web
            "ts", "tsx", "js", "jsx", "vue", "svelte",
            // Systems
            "go", "rs", "c", "cpp", "h", "hpp",
            // Scripting
            "py", "rb", "php", "lua", "sh", "bash",
            // Data/Config
            "xml", "json", "yaml", "yml", "toml",
            // Docs
            "md", "txt"
        )

        /**
         * Directories to always skip during search.
         */
        private val SKIP_DIRS = ProjectType.SKIP_DIRECTORIES + setOf(
            ".gradle", ".intellijPlatform", "out", "build",
            ".git", ".idea", "node_modules", "vendor",
            "__pycache__", ".venv", "venv"
        )

        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): CodebaseSearchService {
            return project.getService(CodebaseSearchService::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Data Classes
    // -------------------------------------------------------------------------

    /**
     * A single search result with file info, content snippet, and relevance score.
     *
     * @property filePath Absolute path to the file
     * @property fileName Base name of the file
     * @property snippet Relevant content snippet (may be truncated)
     * @property score Relevance score (higher = more relevant)
     * @property language Detected language based on extension
     */
    data class SearchResult(
        val filePath: String,
        val fileName: String,
        val snippet: String,
        val score: Double,
        val language: String
    )

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Searches the project codebase for files relevant to the given query.
     *
     * Extracts keywords from the query and scores each source file based on
     * filename and content matches. Returns the top N results.
     *
     * @param query The user's chat message or search query
     * @return List of search results sorted by relevance (highest first)
     */
    fun search(query: String): List<SearchResult> {
        val basePath = project.basePath ?: return emptyList()
        val keywords = extractKeywords(query)

        if (keywords.isEmpty()) {
            LOG.debug("No keywords extracted from query: ${query.take(50)}")
            return emptyList()
        }

        LOG.info("Searching codebase with ${keywords.size} keywords: ${keywords.take(5)}")

        return ReadAction.compute<List<SearchResult>, RuntimeException> {
            try {
                val baseDir = VfsUtil.findFileByIoFile(File(basePath), true)
                    ?: return@compute emptyList()

                val candidates = mutableListOf<SearchResult>()
                collectSourceFiles(baseDir, 0, keywords, candidates)

                // Sort by score descending and take top results
                val ranked = candidates
                    .sortedByDescending { it.score }
                    .take(MAX_RESULTS)

                // Trim snippets if total exceeds budget
                trimToCharBudget(ranked)
            } catch (e: Exception) {
                LOG.warn("Codebase search failed: ${e.message}")
                emptyList()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods — File Collection
    // -------------------------------------------------------------------------

    /**
     * Recursively collects and scores source files.
     */
    private fun collectSourceFiles(
        dir: VirtualFile,
        depth: Int,
        keywords: List<String>,
        results: MutableList<SearchResult>
    ) {
        if (depth > MAX_SCAN_DEPTH) return

        for (child in dir.children) {
            if (child.isDirectory) {
                if (child.name in SKIP_DIRS) continue
                if (child.name.startsWith(".")) continue
                collectSourceFiles(child, depth + 1, keywords, results)
            } else {
                val ext = child.extension?.lowercase() ?: continue
                if (ext !in SOURCE_EXTENSIONS) continue
                if (child.length > MAX_FILE_SIZE) continue

                scoreFile(child, keywords, results)
            }
        }
    }

    /**
     * Scores a single file against the search keywords.
     */
    private fun scoreFile(
        file: VirtualFile,
        keywords: List<String>,
        results: MutableList<SearchResult>
    ) {
        try {
            val fileName = file.nameWithoutExtension.lowercase()
            val content = String(file.contentsToByteArray())
            val contentLower = content.lowercase()

            var score = 0.0
            var bestMatchStart = -1
            var bestMatchKeyword = ""

            for (keyword in keywords) {
                val kw = keyword.lowercase()

                // Filename match (high value)
                if (kw in fileName) {
                    score += 10.0
                    // Exact filename match is even higher
                    if (fileName == kw || fileName.contains(kw) && kw.length >= 4) {
                        score += 5.0
                    }
                }

                // Content keyword match (proportional to occurrences, capped)
                val occurrences = countOccurrences(contentLower, kw)
                if (occurrences > 0) {
                    score += minOf(occurrences.toDouble(), 5.0)

                    // Track best match position for snippet extraction
                    if (bestMatchStart == -1 || kw.length > bestMatchKeyword.length) {
                        bestMatchStart = contentLower.indexOf(kw)
                        bestMatchKeyword = kw
                    }
                } else if (kw.length >= 4) {
                    // No exact match — try fuzzy match for typos
                    // Check if filename is close
                    if (levenshteinDistance(kw, fileName) <= 2) {
                        score += 3.0 // Partial score for fuzzy filename match
                    }
                }
            }

            if (score > 0) {
                val snippet = extractSnippet(content, bestMatchStart)
                val language = extensionToLanguage(file.extension ?: "")

                results.add(
                    SearchResult(
                        filePath = file.path,
                        fileName = file.name,
                        snippet = snippet,
                        score = score,
                        language = language
                    )
                )
            }
        } catch (e: Exception) {
            LOG.debug("Failed to score file ${file.name}: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods — Keyword Extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts meaningful keywords from a user query.
     *
     * Filters out common stop words, short tokens, and noise.
     */
    private fun extractKeywords(query: String): List<String> {
        val stopWords = setOf(
            "a", "an", "the", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "shall", "can",
            "this", "that", "these", "those", "it", "its", "my", "your",
            "his", "her", "our", "their", "what", "which", "who", "whom",
            "where", "when", "why", "how", "if", "or", "and", "but",
            "not", "no", "so", "up", "out", "on", "off", "over", "under",
            "in", "of", "to", "for", "with", "at", "by", "from", "about",
            "into", "through", "during", "before", "after", "above", "below",
            "between", "all", "each", "every", "both", "few", "more",
            "most", "other", "some", "such", "only", "own", "same", "than",
            "too", "very", "just", "because", "as", "until", "while",
            "also", "then", "than", "like", "get", "use", "used", "using",
            "tell", "me", "you", "i", "we", "they", "he", "she",
            "show", "find", "look", "see", "know", "think", "want",
            "need", "try", "ask", "make", "go", "take", "come", "give",
            "let", "help", "please", "thanks", "thank", "hi", "hey",
            "hello", "ok", "okay", "yes", "no", "yeah", "sure",
            "can", "review", "identify", "being", "explain", "describe",
            "any", "currently", "there"
        )

        return query
            .replace(Regex("[^a-zA-Z0-9_.]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
            .take(10)
    }

    // -------------------------------------------------------------------------
    // Private Methods — Scoring Helpers
    // -------------------------------------------------------------------------

    /**
     * Counts non-overlapping occurrences of a substring.
     */
    private fun countOccurrences(text: String, keyword: String): Int {
        var count = 0
        var startIndex = 0
        while (true) {
            val index = text.indexOf(keyword, startIndex)
            if (index < 0) break
            count++
            startIndex = index + keyword.length
        }
        return count
    }

    /**
     * Extracts a content snippet around the best match position.
     */
    private fun extractSnippet(content: String, matchStart: Int): String {
        if (matchStart < 0) {
            // No specific match — return the file header
            return content.take(MAX_TOTAL_CHARS / MAX_RESULTS)
        }

        val lines = content.lines()
        val charsBefore = content.substring(0, matchStart)
        val matchLine = charsBefore.count { it == '\n' }

        // Show a window of lines around the match
        val windowSize = 15
        val startLine = maxOf(0, matchLine - windowSize / 2)
        val endLine = minOf(lines.size, matchLine + windowSize / 2 + 1)

        return lines.subList(startLine, endLine).joinToString("\n")
    }

    /**
     * Trims snippets to fit within the total character budget.
     */
    private fun trimToCharBudget(results: List<SearchResult>): List<SearchResult> {
        var remaining = MAX_TOTAL_CHARS
        val perFileBudget = MAX_TOTAL_CHARS / maxOf(results.size, 1)

        return results.map { result ->
            val budget = minOf(perFileBudget, remaining)
            remaining -= minOf(result.snippet.length, budget)

            if (result.snippet.length <= budget) {
                result
            } else {
                result.copy(snippet = result.snippet.take(budget) + "\n// ... (truncated)")
            }
        }
    }

    /**
     * Maps file extensions to language names for code block formatting.
     */
    private fun extensionToLanguage(ext: String): String = when (ext.lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "cs" -> "csharp"
        "fs" -> "fsharp"
        "py" -> "python"
        "ts", "tsx" -> "typescript"
        "js", "jsx" -> "javascript"
        "go" -> "go"
        "rs" -> "rust"
        "c", "h" -> "c"
        "cpp", "hpp" -> "cpp"
        "rb" -> "ruby"
        "php" -> "php"
        "lua" -> "lua"
        "sh", "bash" -> "bash"
        "xml", "cshtml", "razor" -> "xml"
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "md" -> "markdown"
        "sql" -> "sql"
        "vue" -> "vue"
        "svelte" -> "svelte"
        else -> ext
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     * Used for fuzzy matching to catch typos.
     */
    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i

            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1

                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }

            val swap = cost
            cost = newCost
            newCost = swap
        }

        return cost[lhsLength]
    }
}
