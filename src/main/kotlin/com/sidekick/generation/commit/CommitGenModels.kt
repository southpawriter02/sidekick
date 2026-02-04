// =============================================================================
// CommitGenModels.kt
// =============================================================================
// Data models for commit message generation.
//
// This includes:
// - CommitAnalysis - analysis of staged git changes
// - FileChange - individual file change
// - ConventionalType - conventional commit types
// - CommitMessage - generated commit message
//
// DESIGN NOTES:
// - Follows Conventional Commits specification
// - Supports breaking changes and scopes
// - Auto-detects commit type from file changes
// =============================================================================

package com.sidekick.generation.commit

/**
 * Analysis of staged git changes.
 *
 * @property files List of changed files
 * @property totalAdditions Total lines added
 * @property totalDeletions Total lines deleted
 * @property primaryScope Detected primary scope (e.g., module name)
 * @property changeType Detected commit type
 * @property isBreakingChange Whether this is a breaking change
 */
data class CommitAnalysis(
    val files: List<FileChange>,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val primaryScope: String?,
    val changeType: ConventionalType,
    val isBreakingChange: Boolean
) {
    /**
     * Whether there are any staged changes.
     */
    val hasChanges: Boolean get() = files.isNotEmpty()
    
    /**
     * Total number of files changed.
     */
    val fileCount: Int get() = files.size
    
    /**
     * Summary of changes for display.
     */
    fun summary(): String {
        return buildString {
            append("$fileCount file(s): ")
            append("+$totalAdditions -$totalDeletions")
            if (primaryScope != null) {
                append(" ($primaryScope)")
            }
        }
    }
    
    companion object {
        val EMPTY = CommitAnalysis(
            files = emptyList(),
            totalAdditions = 0,
            totalDeletions = 0,
            primaryScope = null,
            changeType = ConventionalType.CHORE,
            isBreakingChange = false
        )
    }
}

/**
 * Individual file change.
 *
 * @property path Relative path to the file
 * @property status Change status (added, modified, deleted, renamed)
 * @property additions Lines added
 * @property deletions Lines deleted
 * @property diff The diff content (may be truncated)
 */
data class FileChange(
    val path: String,
    val status: ChangeStatus,
    val additions: Int,
    val deletions: Int,
    val diff: String = ""
) {
    /**
     * File name without directory path.
     */
    val fileName: String get() = path.substringAfterLast("/")
    
    /**
     * File extension.
     */
    val extension: String get() = fileName.substringAfterLast(".", "")
    
    /**
     * Directory containing the file.
     */
    val directory: String get() = path.substringBeforeLast("/", "")
}

/**
 * Status of a file change.
 */
enum class ChangeStatus(val symbol: String, val displayName: String) {
    ADDED("A", "Added"),
    MODIFIED("M", "Modified"),
    DELETED("D", "Deleted"),
    RENAMED("R", "Renamed"),
    COPIED("C", "Copied");
    
    companion object {
        fun fromSymbol(symbol: String): ChangeStatus {
            return entries.find { it.symbol == symbol } ?: MODIFIED
        }
    }
}

/**
 * Conventional commit types.
 *
 * @see <a href="https://www.conventionalcommits.org/">Conventional Commits</a>
 */
enum class ConventionalType(
    val prefix: String,
    val displayName: String,
    val description: String,
    val emoji: String
) {
    FEAT(
        prefix = "feat",
        displayName = "Feature",
        description = "A new feature",
        emoji = "✨"
    ),
    
    FIX(
        prefix = "fix",
        displayName = "Bug Fix",
        description = "A bug fix",
        emoji = "🐛"
    ),
    
    DOCS(
        prefix = "docs",
        displayName = "Documentation",
        description = "Documentation only changes",
        emoji = "📚"
    ),
    
    STYLE(
        prefix = "style",
        displayName = "Style",
        description = "Code style changes (formatting, semicolons)",
        emoji = "💄"
    ),
    
    REFACTOR(
        prefix = "refactor",
        displayName = "Refactor",
        description = "Code refactoring without feature or fix",
        emoji = "♻️"
    ),
    
    PERF(
        prefix = "perf",
        displayName = "Performance",
        description = "Performance improvements",
        emoji = "⚡"
    ),
    
    TEST(
        prefix = "test",
        displayName = "Test",
        description = "Adding or updating tests",
        emoji = "✅"
    ),
    
    BUILD(
        prefix = "build",
        displayName = "Build",
        description = "Build system or dependencies",
        emoji = "🔧"
    ),
    
    CI(
        prefix = "ci",
        displayName = "CI/CD",
        description = "CI/CD configuration",
        emoji = "👷"
    ),
    
    CHORE(
        prefix = "chore",
        displayName = "Chore",
        description = "Other changes",
        emoji = "📦"
    ),
    
    REVERT(
        prefix = "revert",
        displayName = "Revert",
        description = "Reverts a previous commit",
        emoji = "⏪"
    );
    
    companion object {
        /**
         * Detects commit type from file changes.
         */
        fun detect(files: List<FileChange>): ConventionalType {
            if (files.isEmpty()) return CHORE
            
            val paths = files.map { it.path.lowercase() }
            val extensions = files.map { it.extension.lowercase() }
            
            return when {
                // All test files
                paths.all { "test" in it || "spec" in it } -> TEST
                
                // All documentation
                paths.all { "doc" in it || "readme" in it } ||
                extensions.all { it in setOf("md", "txt", "rst", "adoc") } -> DOCS
                
                // CI/CD files
                paths.any { ".github" in it || "ci" in it || ".gitlab" in it } -> CI
                
                // Build files
                paths.any { 
                    it.endsWith("build.gradle") || 
                    it.endsWith("build.gradle.kts") ||
                    it.endsWith(".csproj") ||
                    it.endsWith("package.json") ||
                    it.endsWith("cargo.toml")
                } -> BUILD
                
                // Config files only
                extensions.all { it in setOf("json", "yaml", "yml", "toml", "xml", "config") } -> CHORE
                
                // Default to feature for code changes
                else -> FEAT
            }
        }
        
        /**
         * Gets all conventional types.
         */
        fun all(): List<ConventionalType> = entries.toList()
    }
}

/**
 * Generated commit message.
 *
 * @property type Conventional commit type
 * @property scope Optional scope
 * @property subject Subject line (imperative mood)
 * @property body Optional extended description
 * @property footer Optional footer (e.g., "BREAKING CHANGE:", "Closes #123")
 * @property isBreaking Whether this is a breaking change
 */
data class CommitMessage(
    val type: ConventionalType,
    val scope: String?,
    val subject: String,
    val body: String?,
    val footer: String?,
    val isBreaking: Boolean
) {
    /**
     * Formats the commit message according to Conventional Commits spec.
     */
    fun format(): String = buildString {
        // Header: type(scope)!: subject
        append(type.prefix)
        if (!scope.isNullOrBlank()) {
            append("(${scope})")
        }
        if (isBreaking) {
            append("!")
        }
        append(": ")
        append(subject)
        
        // Body
        if (!body.isNullOrBlank()) {
            append("\n\n")
            append(body)
        }
        
        // Footer
        if (!footer.isNullOrBlank()) {
            append("\n\n")
            append(footer)
        }
    }
    
    /**
     * Formats with emoji prefix.
     */
    fun formatWithEmoji(): String {
        return "${type.emoji} ${format()}"
    }
    
    /**
     * Gets just the header line.
     */
    fun header(): String = buildString {
        append(type.prefix)
        if (!scope.isNullOrBlank()) {
            append("(${scope})")
        }
        if (isBreaking) {
            append("!")
        }
        append(": ")
        append(subject)
    }
    
    companion object {
        /**
         * Creates a simple commit message.
         */
        fun simple(type: ConventionalType, subject: String): CommitMessage {
            return CommitMessage(
                type = type,
                scope = null,
                subject = subject,
                body = null,
                footer = null,
                isBreaking = false
            )
        }
        
        /**
         * Creates a commit message with scope.
         */
        fun withScope(
            type: ConventionalType,
            scope: String,
            subject: String
        ): CommitMessage {
            return CommitMessage(
                type = type,
                scope = scope,
                subject = subject,
                body = null,
                footer = null,
                isBreaking = false
            )
        }
    }
}

/**
 * Result of commit message generation.
 */
data class CommitGenResult(
    val message: CommitMessage?,
    val analysis: CommitAnalysis,
    val success: Boolean,
    val error: String? = null
) {
    companion object {
        fun success(message: CommitMessage, analysis: CommitAnalysis): CommitGenResult {
            return CommitGenResult(
                message = message,
                analysis = analysis,
                success = true
            )
        }
        
        fun failure(error: String, analysis: CommitAnalysis = CommitAnalysis.EMPTY): CommitGenResult {
            return CommitGenResult(
                message = null,
                analysis = analysis,
                success = false,
                error = error
            )
        }
    }
}
