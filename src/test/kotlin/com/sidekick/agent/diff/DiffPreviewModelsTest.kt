package com.sidekick.agent.diff

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for DiffPreviewModels.
 */
@DisplayName("Diff Preview Models Tests")
class DiffPreviewModelsTest {

    // =========================================================================
    // ApprovalPolicy Tests
    // =========================================================================

    @Nested
    @DisplayName("ApprovalPolicy")
    inner class ApprovalPolicyTests {

        @Test
        @DisplayName("has three values")
        fun hasThreeValues() {
            assertEquals(3, ApprovalPolicy.entries.size)
        }

        @Test
        @DisplayName("DEFAULT is AGENT_DECIDES")
        fun defaultIsAgentDecides() {
            assertEquals(ApprovalPolicy.AGENT_DECIDES, ApprovalPolicy.DEFAULT)
        }

        @Test
        @DisplayName("byName resolves valid names")
        fun byNameResolvesValidNames() {
            assertEquals(ApprovalPolicy.ALWAYS_PROCEED, ApprovalPolicy.byName("ALWAYS_PROCEED"))
            assertEquals(ApprovalPolicy.AGENT_DECIDES, ApprovalPolicy.byName("AGENT_DECIDES"))
            assertEquals(ApprovalPolicy.REQUEST_REVIEW, ApprovalPolicy.byName("REQUEST_REVIEW"))
        }

        @Test
        @DisplayName("byName returns null for invalid name")
        fun byNameReturnsNullForInvalid() {
            assertNull(ApprovalPolicy.byName("INVALID"))
        }

        @Test
        @DisplayName("displayName is human-readable")
        fun displayNameIsHumanReadable() {
            assertEquals("Always Proceed", ApprovalPolicy.ALWAYS_PROCEED.displayName)
            assertEquals("Agent Decides", ApprovalPolicy.AGENT_DECIDES.displayName)
            assertEquals("Request Review", ApprovalPolicy.REQUEST_REVIEW.displayName)
        }
    }

    // =========================================================================
    // DiffHunk Tests
    // =========================================================================

    @Nested
    @DisplayName("DiffHunk")
    inner class DiffHunkTests {

        @Test
        @DisplayName("isInsertion when no original lines")
        fun isInsertion() {
            val hunk = DiffHunk(
                originalStartLine = 5,
                originalLineCount = 0,
                modifiedStartLine = 5,
                modifiedLineCount = 2,
                originalLines = emptyList(),
                modifiedLines = listOf("new line 1", "new line 2")
            )
            assertTrue(hunk.isInsertion)
            assertFalse(hunk.isDeletion)
            assertFalse(hunk.isModification)
        }

        @Test
        @DisplayName("isDeletion when no modified lines")
        fun isDeletion() {
            val hunk = DiffHunk(
                originalStartLine = 5,
                originalLineCount = 2,
                modifiedStartLine = 5,
                modifiedLineCount = 0,
                originalLines = listOf("old 1", "old 2"),
                modifiedLines = emptyList()
            )
            assertFalse(hunk.isInsertion)
            assertTrue(hunk.isDeletion)
            assertFalse(hunk.isModification)
        }

        @Test
        @DisplayName("isModification when both original and modified exist")
        fun isModification() {
            val hunk = DiffHunk(
                originalStartLine = 5,
                originalLineCount = 1,
                modifiedStartLine = 5,
                modifiedLineCount = 1,
                originalLines = listOf("old"),
                modifiedLines = listOf("new")
            )
            assertFalse(hunk.isInsertion)
            assertFalse(hunk.isDeletion)
            assertTrue(hunk.isModification)
        }

        @Test
        @DisplayName("default status is PENDING")
        fun defaultStatusIsPending() {
            val hunk = DiffHunk(
                originalStartLine = 1, originalLineCount = 0,
                modifiedStartLine = 1, modifiedLineCount = 0,
                originalLines = emptyList(), modifiedLines = emptyList()
            )
            assertEquals(HunkStatus.PENDING, hunk.status)
        }

        @Test
        @DisplayName("withStatus creates copy with new status")
        fun withStatusCreatesCopy() {
            val hunk = DiffHunk(
                originalStartLine = 1, originalLineCount = 1,
                modifiedStartLine = 1, modifiedLineCount = 1,
                originalLines = listOf("old"), modifiedLines = listOf("new")
            )
            val accepted = hunk.withStatus(HunkStatus.ACCEPTED)
            assertEquals(HunkStatus.ACCEPTED, accepted.status)
            assertEquals(HunkStatus.PENDING, hunk.status) // Original unchanged
            assertEquals(hunk.id, accepted.id)
        }

        @Test
        @DisplayName("toUnifiedDiffString produces correct format")
        fun toUnifiedDiffString() {
            val hunk = DiffHunk(
                originalStartLine = 5,
                originalLineCount = 1,
                modifiedStartLine = 5,
                modifiedLineCount = 1,
                originalLines = listOf("old line"),
                modifiedLines = listOf("new line")
            )
            val diff = hunk.toUnifiedDiffString()
            assertTrue(diff.contains("@@ -5,1 +5,1 @@"))
            assertTrue(diff.contains("-old line"))
            assertTrue(diff.contains("+new line"))
        }
    }

    // =========================================================================
    // FileChange Tests
    // =========================================================================

    @Nested
    @DisplayName("FileChange")
    inner class FileChangeTests {

        @Test
        @DisplayName("newFile creates CREATE change")
        fun newFileCreatesCreateChange() {
            val change = FileChange.newFile("/path/file.kt", "line 1\nline 2")
            assertEquals(ChangeType.CREATE, change.changeType)
            assertNull(change.originalContent)
            assertEquals(1, change.hunks.size)
            assertTrue(change.hunks.first().isInsertion)
        }

        @Test
        @DisplayName("deleteFile creates DELETE change")
        fun deleteFileCreatesDeleteChange() {
            val change = FileChange.deleteFile("/path/file.kt", "line 1\nline 2")
            assertEquals(ChangeType.DELETE, change.changeType)
            assertNotNull(change.originalContent)
            assertEquals(1, change.hunks.size)
            assertTrue(change.hunks.first().isDeletion)
        }

        @Test
        @DisplayName("fileName extracts basename")
        fun fileNameExtractsBasename() {
            val change = FileChange.newFile("/path/to/deep/MyFile.kt", "content")
            assertEquals("MyFile.kt", change.fileName)
        }

        @Test
        @DisplayName("linesAdded sums modified line counts")
        fun linesAddedSums() {
            val change = FileChange.newFile("/f.kt", "a\nb\nc")
            assertEquals(3, change.linesAdded)
        }

        @Test
        @DisplayName("linesRemoved sums original line counts")
        fun linesRemovedSums() {
            val change = FileChange.deleteFile("/f.kt", "a\nb")
            assertEquals(2, change.linesRemoved)
        }

        @Test
        @DisplayName("pendingHunkCount reflects status")
        fun pendingHunkCount() {
            val change = FileChange.newFile("/f.kt", "content")
            assertEquals(1, change.pendingHunkCount)
            assertFalse(change.isFullyReviewed)
        }
    }

    // =========================================================================
    // DiffReviewDecision Tests
    // =========================================================================

    @Nested
    @DisplayName("DiffReviewDecision")
    inner class DiffReviewDecisionTests {

        @Test
        @DisplayName("approveAll creates full approval")
        fun approveAllCreatesFullApproval() {
            val decision = DiffReviewDecision.approveAll("req-1", setOf("h1", "h2"))
            assertTrue(decision.approved)
            assertTrue(decision.isFullApproval)
            assertFalse(decision.isPartialApproval)
            assertFalse(decision.isFullRejection)
            assertEquals(setOf("h1", "h2"), decision.acceptedHunkIds)
            assertTrue(decision.rejectedHunkIds.isEmpty())
        }

        @Test
        @DisplayName("rejectAll creates full rejection")
        fun rejectAllCreatesFullRejection() {
            val decision = DiffReviewDecision.rejectAll("req-1", setOf("h1", "h2"))
            assertFalse(decision.approved)
            assertTrue(decision.isFullRejection)
            assertFalse(decision.isFullApproval)
            assertFalse(decision.isPartialApproval)
            assertEquals(setOf("h1", "h2"), decision.rejectedHunkIds)
            assertTrue(decision.acceptedHunkIds.isEmpty())
        }

        @Test
        @DisplayName("partial approval has both accepted and rejected")
        fun partialApproval() {
            val decision = DiffReviewDecision(
                requestId = "req-1",
                approved = true,
                acceptedHunkIds = setOf("h1"),
                rejectedHunkIds = setOf("h2")
            )
            assertTrue(decision.approved)
            assertFalse(decision.isFullApproval)
            assertTrue(decision.isPartialApproval)
            assertFalse(decision.isFullRejection)
        }
    }

    // =========================================================================
    // DiffReviewRequest Tests
    // =========================================================================

    @Nested
    @DisplayName("DiffReviewRequest")
    inner class DiffReviewRequestTests {

        @Test
        @DisplayName("fileCount and totalHunkCount are correct")
        fun countsAreCorrect() {
            val request = DiffReviewRequest(
                taskId = "task-1",
                stepId = 0,
                changes = listOf(
                    FileChange.newFile("/a.kt", "a"),
                    FileChange.newFile("/b.kt", "b\nc")
                ),
                toolName = "write_file"
            )
            assertEquals(2, request.fileCount)
            assertEquals(2, request.totalHunkCount)
        }

        @Test
        @DisplayName("summary describes file and hunk counts")
        fun summaryDescribesCountsf() {
            val request = DiffReviewRequest(
                taskId = "task-1",
                stepId = 0,
                changes = listOf(FileChange.newFile("/a.kt", "content")),
                toolName = "write_file"
            )
            assertEquals("1 file(s), 1 hunk(s)", request.summary)
        }
    }
}
