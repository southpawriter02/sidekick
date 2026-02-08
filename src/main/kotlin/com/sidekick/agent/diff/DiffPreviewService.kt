// =============================================================================
// DiffPreviewService.kt
// =============================================================================
// Service for generating unified diffs, managing hunk-level review, and
// producing merged content from partial approvals.
//
// This is a pure logic class (not an IntelliJ service) for testability.
//
// @since v1.2.0
// =============================================================================

package com.sidekick.agent.diff

import java.io.File

/**
 * Generates diffs and applies hunk-level decisions for agent code changes.
 *
 * ## Usage
 * ```kotlin
 * val service = DiffPreviewService()
 * val change = service.createFileChange("/path/to/file.kt", "new content here")
 * val formatted = service.formatUnifiedDiff(change)
 * // ... user reviews hunks ...
 * val merged = service.applyDecision(change, decision)
 * ```
 */
class DiffPreviewService {

    // =========================================================================
    // Diff Generation
    // =========================================================================

    /**
     * Generates a list of diff hunks between original and modified content.
     *
     * Uses a simple line-based diff algorithm that groups consecutive changes
     * into hunks with surrounding context lines.
     *
     * @param original The original text (empty string for new files)
     * @param modified The proposed text
     * @param contextLines Number of context lines around each hunk
     * @return List of [DiffHunk]s representing the changes
     */
    fun generateDiff(
        original: String,
        modified: String,
        contextLines: Int = 3
    ): List<DiffHunk> {
        if (original == modified) return emptyList()

        // "".lines() returns [""] in Kotlin — normalize to empty list
        val originalLines = if (original.isEmpty()) emptyList() else original.lines()
        val modifiedLines = if (modified.isEmpty()) emptyList() else modified.lines()

        // Compute the edit script (LCS-based)
        val editOps = computeEditOperations(originalLines, modifiedLines)

        // Group consecutive edits into hunks with context
        return groupIntoHunks(originalLines, modifiedLines, editOps, contextLines)
    }

    /**
     * Creates a [FileChange] for a proposed modification to an existing file.
     *
     * Reads the current file content and generates hunks.
     *
     * @param filePath Absolute path to the file
     * @param proposedContent The new file content
     * @return A [FileChange] with computed diff hunks
     */
    fun createFileChange(filePath: String, proposedContent: String): FileChange {
        val file = File(filePath)

        return if (file.exists()) {
            val originalContent = file.readText()
            val hunks = generateDiff(originalContent, proposedContent)
            FileChange(
                filePath = filePath,
                originalContent = originalContent,
                proposedContent = proposedContent,
                hunks = hunks,
                changeType = if (hunks.isEmpty()) ChangeType.MODIFY else ChangeType.MODIFY
            )
        } else {
            FileChange.newFile(filePath, proposedContent)
        }
    }

    // =========================================================================
    // Decision Application
    // =========================================================================

    /**
     * Applies a review decision, producing final content with only accepted hunks.
     *
     * For a full approval, returns the proposed content as-is.
     * For a full rejection, returns the original content.
     * For partial approval, merges only accepted hunks into the original.
     *
     * @param change The file change being reviewed
     * @param decision The user's review decision
     * @return The final merged content
     */
    fun applyDecision(change: FileChange, decision: DiffReviewDecision): String {
        // Full rejection → keep original
        if (decision.isFullRejection) {
            return change.originalContent ?: ""
        }

        // Full approval → use proposed
        if (decision.isFullApproval) {
            return change.proposedContent
        }

        // Partial approval → merge accepted hunks into original
        return mergeAcceptedHunks(change, decision.acceptedHunkIds)
    }

    /**
     * Merges only accepted hunks into the original content.
     *
     * Processes hunks in reverse order (bottom-up) so that line numbers
     * of earlier hunks aren't shifted by later insertions/deletions.
     */
    private fun mergeAcceptedHunks(change: FileChange, acceptedHunkIds: Set<String>): String {
        val originalLines = (change.originalContent ?: "").lines().toMutableList()

        // Process hunks bottom-up to preserve line numbers
        val acceptedHunks = change.hunks
            .filter { it.id in acceptedHunkIds }
            .sortedByDescending { it.originalStartLine }

        for (hunk in acceptedHunks) {
            val startIdx = (hunk.originalStartLine - 1).coerceAtLeast(0)
            val endIdx = (startIdx + hunk.originalLineCount).coerceAtMost(originalLines.size)

            // Remove original lines and insert modified lines
            for (i in (startIdx until endIdx).reversed()) {
                originalLines.removeAt(i)
            }
            originalLines.addAll(startIdx, hunk.modifiedLines)
        }

        return originalLines.joinToString("\n")
    }

    // =========================================================================
    // Diff Formatting
    // =========================================================================

    /**
     * Formats a [FileChange] as standard unified diff text.
     *
     * @param change The file change to format
     * @return Unified diff string suitable for display
     */
    fun formatUnifiedDiff(change: FileChange): String = buildString {
        // Header
        val originalPath = if (change.changeType == ChangeType.CREATE) "/dev/null" else change.filePath
        val modifiedPath = change.filePath

        appendLine("--- $originalPath")
        appendLine("+++ $modifiedPath")

        // Hunks
        for (hunk in change.hunks) {
            append(hunk.toUnifiedDiffString())
        }
    }

    /**
     * Formats a list of file changes as a multi-file unified diff.
     */
    fun formatMultiFileDiff(changes: List<FileChange>): String = buildString {
        for ((index, change) in changes.withIndex()) {
            if (index > 0) appendLine()
            append(formatUnifiedDiff(change))
        }
    }

    // =========================================================================
    // Edit Operations (LCS-based diff)
    // =========================================================================

    /**
     * Edit operation types for the diff algorithm.
     */
    internal enum class EditOp {
        EQUAL,   // Line exists in both
        INSERT,  // Line added in modified
        DELETE   // Line removed from original
    }

    /**
     * Represents a single line-level edit.
     */
    internal data class Edit(
        val op: EditOp,
        val originalLine: Int,  // 1-based index in original (-1 if INSERT)
        val modifiedLine: Int,  // 1-based index in modified (-1 if DELETE)
        val text: String
    )

    /**
     * Computes the edit operations between two lists of lines using LCS.
     */
    internal fun computeEditOperations(
        original: List<String>,
        modified: List<String>
    ): List<Edit> {
        val m = original.size
        val n = modified.size

        // Build LCS table
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (original[i - 1] == modified[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to produce edit operations
        val edits = mutableListOf<Edit>()
        var i = m
        var j = n

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && original[i - 1] == modified[j - 1] -> {
                    edits.add(Edit(EditOp.EQUAL, i, j, original[i - 1]))
                    i--
                    j--
                }
                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    edits.add(Edit(EditOp.INSERT, -1, j, modified[j - 1]))
                    j--
                }
                i > 0 -> {
                    edits.add(Edit(EditOp.DELETE, i, -1, original[i - 1]))
                    i--
                }
            }
        }

        return edits.reversed()
    }

    /**
     * Groups consecutive edit operations into [DiffHunk]s with context.
     */
    internal fun groupIntoHunks(
        originalLines: List<String>,
        modifiedLines: List<String>,
        edits: List<Edit>,
        contextLines: Int
    ): List<DiffHunk> {
        // Find ranges of changes (non-EQUAL edits)
        data class ChangeRange(
            val startIdx: Int,  // Index in edits list
            val endIdx: Int     // Exclusive end index in edits list
        )

        val changeRanges = mutableListOf<ChangeRange>()
        var rangeStart = -1

        for ((idx, edit) in edits.withIndex()) {
            if (edit.op != EditOp.EQUAL) {
                if (rangeStart == -1) rangeStart = idx
            } else {
                if (rangeStart != -1) {
                    changeRanges.add(ChangeRange(rangeStart, idx))
                    rangeStart = -1
                }
            }
        }
        if (rangeStart != -1) {
            changeRanges.add(ChangeRange(rangeStart, edits.size))
        }

        if (changeRanges.isEmpty()) return emptyList()

        // Merge nearby ranges (within 2 * contextLines of each other)
        val mergedRanges = mutableListOf(changeRanges.first())
        for (i in 1 until changeRanges.size) {
            val prev = mergedRanges.last()
            val curr = changeRanges[i]

            // Count EQUAL lines between prev.endIdx and curr.startIdx
            val gapSize = (prev.endIdx until curr.startIdx).count { edits[it].op == EditOp.EQUAL }

            if (gapSize <= contextLines * 2) {
                // Merge
                mergedRanges[mergedRanges.lastIndex] = ChangeRange(prev.startIdx, curr.endIdx)
            } else {
                mergedRanges.add(curr)
            }
        }

        // Build hunks from merged ranges
        return mergedRanges.map { range ->
            val changedEdits = edits.subList(range.startIdx, range.endIdx)

            val deletedLines = changedEdits.filter { it.op == EditOp.DELETE }.map { it.text }
            val insertedLines = changedEdits.filter { it.op == EditOp.INSERT }.map { it.text }

            // Determine original/modified line positions
            val firstOrigLine = changedEdits.firstOrNull { it.op == EditOp.DELETE }?.originalLine
                ?: changedEdits.firstOrNull { it.op == EditOp.EQUAL }?.originalLine
                ?: 1
            val firstModLine = changedEdits.firstOrNull { it.op == EditOp.INSERT }?.modifiedLine
                ?: changedEdits.firstOrNull { it.op == EditOp.EQUAL }?.modifiedLine
                ?: 1

            // Gather context before
            val contextBeforeStart = (range.startIdx - contextLines).coerceAtLeast(0)
            val ctxBefore = (contextBeforeStart until range.startIdx)
                .filter { edits[it].op == EditOp.EQUAL }
                .map { edits[it].text }

            // Gather context after
            val contextAfterEnd = (range.endIdx + contextLines).coerceAtMost(edits.size)
            val ctxAfter = (range.endIdx until contextAfterEnd)
                .filter { edits[it].op == EditOp.EQUAL }
                .map { edits[it].text }

            DiffHunk(
                originalStartLine = firstOrigLine,
                originalLineCount = deletedLines.size,
                modifiedStartLine = firstModLine,
                modifiedLineCount = insertedLines.size,
                originalLines = deletedLines,
                modifiedLines = insertedLines,
                contextBefore = ctxBefore,
                contextAfter = ctxAfter
            )
        }
    }
}
