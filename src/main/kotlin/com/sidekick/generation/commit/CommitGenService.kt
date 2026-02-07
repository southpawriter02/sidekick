// =============================================================================
// CommitGenService.kt
// =============================================================================
// Service for generating commit messages using LLM.
//
// This service:
// - Analyzes staged git changes
// - Generates conventional commit messages
// - Uses Ollama for intelligent message generation
//
// DESIGN NOTES:
// - Project-level service
// - Integrates with Git4Idea plugin
// - Fallback for when Git integration unavailable
// =============================================================================

package com.sidekick.generation.commit

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.UnifiedChatRequest
import com.sidekick.llm.provider.UnifiedMessage
import com.sidekick.settings.SidekickSettings

/**
 * Service for generating commit messages using LLM.
 *
 * Analyzes staged changes and generates conventional commit messages
 * based on the changes detected.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = CommitGenService.getInstance(project)
 * val result = service.generateCommitMessage()
 * if (result.success) {
 *     // Use result.message.format() as commit message
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class CommitGenService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(CommitGenService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): CommitGenService {
            return project.getService(CommitGenService::class.java)
        }
        
        /**
         * Prompt template for commit message generation.
         */
        private val COMMIT_GEN_PROMPT = """
            Generate a conventional commit message for these changes:
            
            Files changed (%d files, +%d -%d lines):
            %s
            
            Diff summary:
            %s
            
            Requirements:
            - Use conventional commits format: type(scope): subject
            - Subject should be imperative mood ("add" not "added"), max 72 chars
            - Type must be one of: feat, fix, docs, style, refactor, perf, test, build, ci, chore
            - Scope is optional but recommended (derive from file paths)
            - Detect if this is a breaking change (add ! after scope)
            - Add body only if changes are complex
            
            Reply with ONLY a JSON object in this format (no markdown):
            {"type": "feat", "scope": "optional", "subject": "short description", "body": "optional longer description", "breaking": false}
        """.trimIndent()
        
        /** Maximum diff size to include in prompt */
        private const val MAX_DIFF_SIZE = 2000
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Analyzes staged changes and generates a commit message.
     */
    suspend fun generateCommitMessage(): CommitGenResult {
        LOG.info("Generating commit message for staged changes")
        
        val analysis = analyzeStaged()
        
        if (!analysis.hasChanges) {
            return CommitGenResult.failure("No staged changes found", analysis)
        }
        
        return generate(analysis)
    }

    /**
     * Generates a commit message from an analysis.
     */
    suspend fun generate(analysis: CommitAnalysis): CommitGenResult {
        LOG.info("Generating commit message for ${analysis.fileCount} files")
        
        if (!analysis.hasChanges) {
            return CommitGenResult.failure("No changes to commit", analysis)
        }
        
        return try {
            val prompt = buildPrompt(analysis)
            val response = callLLM(prompt)
            val message = parseResponse(response, analysis)
            
            LOG.info("Generated commit message: ${message.header()}")
            CommitGenResult.success(message, analysis)
            
        } catch (e: Exception) {
            LOG.warn("Commit message generation failed: ${e.message}", e)
            
            // Fallback to auto-generated message
            val fallbackMessage = generateFallback(analysis)
            CommitGenResult.success(fallbackMessage, analysis)
        }
    }

    /**
     * Analyzes currently staged git changes.
     */
    fun analyzeStaged(): CommitAnalysis {
        LOG.debug("Analyzing staged changes")
        
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.allChanges
            
            if (changes.isEmpty()) {
                LOG.debug("No changes found")
                return CommitAnalysis.EMPTY
            }
            
            val files = changes.mapNotNull { change ->
                val path = change.afterRevision?.file?.path 
                    ?: change.beforeRevision?.file?.path
                    ?: return@mapNotNull null
                
                val status = when {
                    change.beforeRevision == null -> ChangeStatus.ADDED
                    change.afterRevision == null -> ChangeStatus.DELETED
                    else -> ChangeStatus.MODIFIED
                }
                
                // Get line counts (simplified - actual implementation would parse diff)
                val (additions, deletions) = estimateLineChanges(
                    change.beforeRevision,
                    change.afterRevision
                )
                
                FileChange(
                    path = path,
                    status = status,
                    additions = additions,
                    deletions = deletions,
                    diff = "" // Diff populated on demand
                )
            }
            
            if (files.isEmpty()) {
                return CommitAnalysis.EMPTY
            }
            
            val scope = detectScope(files)
            val type = ConventionalType.detect(files)
            
            CommitAnalysis(
                files = files,
                totalAdditions = files.sumOf { it.additions },
                totalDeletions = files.sumOf { it.deletions },
                primaryScope = scope,
                changeType = type,
                isBreakingChange = false
            )
            
        } catch (e: Exception) {
            LOG.warn("Failed to analyze staged changes: ${e.message}", e)
            CommitAnalysis.EMPTY
        }
    }

    /**
     * Generates a simple fallback commit message without LLM.
     */
    fun generateFallback(analysis: CommitAnalysis): CommitMessage {
        val type = analysis.changeType
        val scope = analysis.primaryScope
        
        val subject = when {
            analysis.fileCount == 1 -> {
                val file = analysis.files.first()
                when (file.status) {
                    ChangeStatus.ADDED -> "add ${file.fileName}"
                    ChangeStatus.DELETED -> "remove ${file.fileName}"
                    ChangeStatus.MODIFIED -> "update ${file.fileName}"
                    ChangeStatus.RENAMED -> "rename ${file.fileName}"
                    ChangeStatus.COPIED -> "copy ${file.fileName}"
                }
            }
            else -> {
                "${analysis.files.first().status.displayName.lowercase()} ${analysis.fileCount} files"
            }
        }
        
        return CommitMessage(
            type = type,
            scope = scope,
            subject = subject,
            body = null,
            footer = null,
            isBreaking = analysis.isBreakingChange
        )
    }

    // -------------------------------------------------------------------------
    // Private Methods - Analysis
    // -------------------------------------------------------------------------

    private fun estimateLineChanges(
        before: ContentRevision?,
        after: ContentRevision?
    ): Pair<Int, Int> {
        // Simplified estimation - real implementation would diff content
        return try {
            val beforeLines = before?.content?.lines()?.size ?: 0
            val afterLines = after?.content?.lines()?.size ?: 0
            
            val additions = maxOf(0, afterLines - beforeLines)
            val deletions = maxOf(0, beforeLines - afterLines)
            
            additions to deletions
        } catch (e: Exception) {
            0 to 0
        }
    }

    private fun detectScope(files: List<FileChange>): String? {
        if (files.isEmpty()) return null
        
        // Try to find common directory
        val directories = files.map { it.directory }.filter { it.isNotBlank() }
        if (directories.isEmpty()) return null
        
        // If all files in same directory, use it as scope
        if (directories.distinct().size == 1) {
            return directories.first().substringAfterLast("/")
        }
        
        // Find common prefix
        val commonPrefix = directories.reduce { acc, dir ->
            acc.commonPrefixWith(dir)
        }.trimEnd('/')
        
        if (commonPrefix.isNotBlank()) {
            return commonPrefix.substringAfterLast("/")
        }
        
        return null
    }

    // -------------------------------------------------------------------------
    // Private Methods - LLM
    // -------------------------------------------------------------------------

    private fun buildPrompt(analysis: CommitAnalysis): String {
        val fileList = analysis.files.joinToString("\n") { file ->
            "  ${file.status.symbol} ${file.path} (+${file.additions} -${file.deletions})"
        }
        
        // Build diff summary (truncated if too long)
        val diffSummary = analysis.files
            .filter { it.diff.isNotBlank() }
            .joinToString("\n---\n") { "${it.path}:\n${it.diff}" }
            .take(MAX_DIFF_SIZE)
        
        return COMMIT_GEN_PROMPT.format(
            analysis.fileCount,
            analysis.totalAdditions,
            analysis.totalDeletions,
            fileList,
            diffSummary.ifBlank { "(diff not available)" }
        )
    }

    private suspend fun callLLM(prompt: String): String {
        val providerManager = ProviderManager.getInstance()
        val settings = SidekickSettings.getInstance()

        val request = UnifiedChatRequest(
            model = settings.defaultModel.ifEmpty { "llama3.2" },
            messages = listOf(UnifiedMessage.user(prompt)),
            systemPrompt = "You are a commit message generator. Reply only with JSON, no markdown.",
            temperature = 0.3f,
            maxTokens = 300,
            stream = false
        )

        val response = providerManager.chat(request)
        return response.content ?: ""
    }

    private fun parseResponse(response: String, analysis: CommitAnalysis): CommitMessage {
        // Try to parse JSON response
        val jsonRegex = Regex("""\{[^}]+}""")
        val jsonMatch = jsonRegex.find(response)
        
        if (jsonMatch != null) {
            val json = jsonMatch.value
            
            // Simple JSON parsing (avoiding dependency on JSON library for this)
            val type = extractJsonString(json, "type")?.let { typeName ->
                ConventionalType.entries.find { it.prefix == typeName }
            } ?: analysis.changeType
            
            val scope = extractJsonString(json, "scope")
            val subject = extractJsonString(json, "subject") ?: "update code"
            val body = extractJsonString(json, "body")
            val breaking = json.contains("\"breaking\":true") || json.contains("\"breaking\": true")
            
            return CommitMessage(
                type = type,
                scope = scope?.takeIf { it.isNotBlank() && it != "optional" },
                subject = subject.take(72),
                body = body?.takeIf { it.isNotBlank() },
                footer = null,
                isBreaking = breaking
            )
        }
        
        // Fallback if JSON parsing fails
        return generateFallback(analysis)
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}
