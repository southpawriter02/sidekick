// =============================================================================
// DiffPreviewModels.kt
// =============================================================================
// Data models for inline diff preview and hunk-level approval.
//
// @since v1.2.0
// =============================================================================

package com.sidekick.agent.diff

import java.time.Instant
import java.util.UUID

// =============================================================================
// Approval Policy
// =============================================================================

/**
 * Controls when the agent must seek user approval for code changes.
 *
 * - [ALWAYS_PROCEED]: Apply all changes immediately (no review)
 * - [AGENT_DECIDES]: Agent applies non-destructive changes; prompts for destructive ones
 * - [REQUEST_REVIEW]: Always show diff and wait for user decision
 */
enum class ApprovalPolicy(val displayName: String) {
    ALWAYS_PROCEED("Always Proceed"),
    AGENT_DECIDES("Agent Decides"),
    REQUEST_REVIEW("Request Review");

    override fun toString(): String = displayName

    companion object {
        val DEFAULT = AGENT_DECIDES

        fun byName(name: String): ApprovalPolicy? {
            return entries.find { it.name == name }
        }
    }
}

// =============================================================================
// Diff Hunk
// =============================================================================

/**
 * A single contiguous block of changes within a file diff.
 *
 * @property id Unique hunk identifier
 * @property originalStartLine Starting line in the original file (1-based)
 * @property originalLineCount Number of lines from the original
 * @property modifiedStartLine Starting line in the modified file (1-based)
 * @property modifiedLineCount Number of lines in the modified version
 * @property originalLines The original lines of text
 * @property modifiedLines The replacement lines of text
 * @property contextBefore Lines of context before the change
 * @property contextAfter Lines of context after the change
 * @property status Whether this hunk is accepted, rejected, or pending review
 */
data class DiffHunk(
    val id: String = UUID.randomUUID().toString(),
    val originalStartLine: Int,
    val originalLineCount: Int,
    val modifiedStartLine: Int,
    val modifiedLineCount: Int,
    val originalLines: List<String>,
    val modifiedLines: List<String>,
    val contextBefore: List<String> = emptyList(),
    val contextAfter: List<String> = emptyList(),
    val status: HunkStatus = HunkStatus.PENDING
) {
    /** Whether this hunk represents an insertion (no original lines). */
    val isInsertion: Boolean get() = originalLines.isEmpty() && modifiedLines.isNotEmpty()

    /** Whether this hunk represents a deletion (no modified lines). */
    val isDeletion: Boolean get() = originalLines.isNotEmpty() && modifiedLines.isEmpty()

    /** Whether this hunk represents a modification. */
    val isModification: Boolean get() = originalLines.isNotEmpty() && modifiedLines.isNotEmpty()

    /** Creates a copy with the given status. */
    fun withStatus(newStatus: HunkStatus): DiffHunk = copy(status = newStatus)

    /** Formats this hunk as unified diff text. */
    fun toUnifiedDiffString(): String = buildString {
        appendLine("@@ -$originalStartLine,$originalLineCount +$modifiedStartLine,$modifiedLineCount @@")
        contextBefore.forEach { appendLine(" $it") }
        originalLines.forEach { appendLine("-$it") }
        modifiedLines.forEach { appendLine("+$it") }
        contextAfter.forEach { appendLine(" $it") }
    }
}

/**
 * Review status for an individual diff hunk.
 */
enum class HunkStatus(val displayName: String) {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    override fun toString(): String = displayName
}

// =============================================================================
// File Change
// =============================================================================

/**
 * Represents a proposed change to a single file.
 *
 * @property filePath Absolute path to the file
 * @property originalContent The file's content before the change (null for new files)
 * @property proposedContent The proposed new content
 * @property hunks Individual diff hunks for cherry-picking
 * @property changeType Whether this is a create, modify, or delete
 */
data class FileChange(
    val filePath: String,
    val originalContent: String?,
    val proposedContent: String,
    val hunks: List<DiffHunk>,
    val changeType: ChangeType
) {
    /** Total lines added across all hunks. */
    val linesAdded: Int get() = hunks.sumOf { it.modifiedLineCount }

    /** Total lines removed across all hunks. */
    val linesRemoved: Int get() = hunks.sumOf { it.originalLineCount }

    /** Net line count change. */
    val netLineChange: Int get() = linesAdded - linesRemoved

    /** Number of hunks pending review. */
    val pendingHunkCount: Int get() = hunks.count { it.status == HunkStatus.PENDING }

    /** Whether all hunks have been reviewed. */
    val isFullyReviewed: Boolean get() = hunks.none { it.status == HunkStatus.PENDING }

    /** File name (basename) for display. */
    val fileName: String get() = filePath.substringAfterLast('/')

    companion object {
        /** Creates a FileChange for a new file (no original content). */
        fun newFile(filePath: String, content: String): FileChange {
            val lines = content.lines()
            val hunk = DiffHunk(
                originalStartLine = 0,
                originalLineCount = 0,
                modifiedStartLine = 1,
                modifiedLineCount = lines.size,
                originalLines = emptyList(),
                modifiedLines = lines
            )
            return FileChange(
                filePath = filePath,
                originalContent = null,
                proposedContent = content,
                hunks = listOf(hunk),
                changeType = ChangeType.CREATE
            )
        }

        /** Creates a FileChange for deleting a file. */
        fun deleteFile(filePath: String, originalContent: String): FileChange {
            val lines = originalContent.lines()
            val hunk = DiffHunk(
                originalStartLine = 1,
                originalLineCount = lines.size,
                modifiedStartLine = 0,
                modifiedLineCount = 0,
                originalLines = lines,
                modifiedLines = emptyList()
            )
            return FileChange(
                filePath = filePath,
                originalContent = originalContent,
                proposedContent = "",
                hunks = listOf(hunk),
                changeType = ChangeType.DELETE
            )
        }
    }
}

/**
 * Type of file change.
 */
enum class ChangeType(val displayName: String) {
    CREATE("New File"),
    MODIFY("Modified"),
    DELETE("Deleted");

    override fun toString(): String = displayName
}

// =============================================================================
// Diff Review Request / Decision
// =============================================================================

/**
 * A request for the user to review proposed changes before application.
 *
 * Emitted when the approval policy requires user review. Contains all
 * information needed to display a diff preview panel.
 *
 * @property id Unique request identifier
 * @property taskId ID of the parent agent task
 * @property stepId Step number within the task
 * @property changes List of file changes to review
 * @property toolName The tool that proposed these changes
 * @property toolArgs The tool arguments
 * @property createdAt When this review was requested
 */
data class DiffReviewRequest(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val stepId: Int,
    val changes: List<FileChange>,
    val toolName: String,
    val toolArgs: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now()
) {
    /** Total files affected. */
    val fileCount: Int get() = changes.size

    /** Total hunks across all files. */
    val totalHunkCount: Int get() = changes.sumOf { it.hunks.size }

    /** Whether all changes have been reviewed. */
    val isFullyReviewed: Boolean get() = changes.all { it.isFullyReviewed }

    /** Summary for display (e.g. "3 files, 7 hunks"). */
    val summary: String get() = "$fileCount file(s), $totalHunkCount hunk(s)"
}

/**
 * The user's decision after reviewing a diff.
 *
 * @property requestId ID of the [DiffReviewRequest] being responded to
 * @property approved Whether the overall change is approved
 * @property acceptedHunkIds IDs of hunks the user accepted
 * @property rejectedHunkIds IDs of hunks the user rejected
 * @property decidedAt When the decision was made
 */
data class DiffReviewDecision(
    val requestId: String,
    val approved: Boolean,
    val acceptedHunkIds: Set<String> = emptySet(),
    val rejectedHunkIds: Set<String> = emptySet(),
    val decidedAt: Instant = Instant.now()
) {
    /** Whether this was a full approval (no rejections). */
    val isFullApproval: Boolean get() = approved && rejectedHunkIds.isEmpty()

    /** Whether this is a partial approval (some hunks accepted, some rejected). */
    val isPartialApproval: Boolean get() = approved && rejectedHunkIds.isNotEmpty()

    /** Whether this is a full rejection. */
    val isFullRejection: Boolean get() = !approved && acceptedHunkIds.isEmpty()

    companion object {
        /** Approves all changes unconditionally. */
        fun approveAll(requestId: String, hunkIds: Set<String>) = DiffReviewDecision(
            requestId = requestId,
            approved = true,
            acceptedHunkIds = hunkIds
        )

        /** Rejects all changes. */
        fun rejectAll(requestId: String, hunkIds: Set<String>) = DiffReviewDecision(
            requestId = requestId,
            approved = false,
            rejectedHunkIds = hunkIds
        )
    }
}
