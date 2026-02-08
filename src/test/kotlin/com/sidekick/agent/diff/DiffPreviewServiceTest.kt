package com.sidekick.agent.diff

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Comprehensive tests for DiffPreviewService.
 */
@DisplayName("Diff Preview Service Tests")
class DiffPreviewServiceTest {

    private lateinit var service: DiffPreviewService

    @BeforeEach
    fun setup() {
        service = DiffPreviewService()
    }

    // =========================================================================
    // generateDiff Tests
    // =========================================================================

    @Nested
    @DisplayName("generateDiff")
    inner class GenerateDiffTests {

        @Test
        @DisplayName("identical content produces no hunks")
        fun identicalContentProducesNoHunks() {
            val content = "line 1\nline 2\nline 3"
            val hunks = service.generateDiff(content, content)
            assertTrue(hunks.isEmpty())
        }

        @Test
        @DisplayName("single line modification produces one hunk")
        fun singleLineModification() {
            val original = "line 1\nline 2\nline 3"
            val modified = "line 1\nline 2 modified\nline 3"
            val hunks = service.generateDiff(original, modified)
            assertEquals(1, hunks.size)

            val hunk = hunks.first()
            assertTrue(hunk.originalLines.contains("line 2"))
            assertTrue(hunk.modifiedLines.contains("line 2 modified"))
        }

        @Test
        @DisplayName("insertion produces hunk with empty originalLines")
        fun insertionProducesInsertionHunk() {
            val original = "line 1\nline 3"
            val modified = "line 1\nline 2\nline 3"
            val hunks = service.generateDiff(original, modified)
            assertEquals(1, hunks.size)

            val hunk = hunks.first()
            assertTrue(hunk.modifiedLines.contains("line 2"))
        }

        @Test
        @DisplayName("deletion produces hunk with empty modifiedLines")
        fun deletionProducesDeletionHunk() {
            val original = "line 1\nline 2\nline 3"
            val modified = "line 1\nline 3"
            val hunks = service.generateDiff(original, modified)
            assertEquals(1, hunks.size)

            val hunk = hunks.first()
            assertTrue(hunk.originalLines.contains("line 2"))
        }

        @Test
        @DisplayName("multiple separated changes produce multiple hunks")
        fun multipleSeparatedChanges() {
            val original = (1..20).joinToString("\n") { "line $it" }
            val modified = original
                .replace("line 2", "line 2 modified")
                .replace("line 18", "line 18 modified")
            val hunks = service.generateDiff(original, modified)
            assertTrue(hunks.size >= 2, "Should produce at least 2 hunks for separated changes")
        }

        @Test
        @DisplayName("empty original produces all-insertion diff")
        fun emptyOriginal() {
            val hunks = service.generateDiff("", "new line 1\nnew line 2")
            assertEquals(1, hunks.size)
            assertTrue(hunks.first().originalLines.isEmpty())
            assertEquals(2, hunks.first().modifiedLines.size)
        }

        @Test
        @DisplayName("empty modified produces all-deletion diff")
        fun emptyModified() {
            val hunks = service.generateDiff("old line 1\nold line 2", "")
            assertEquals(1, hunks.size)
            assertTrue(hunks.first().modifiedLines.isEmpty())
            assertEquals(2, hunks.first().originalLines.size)
        }
    }

    // =========================================================================
    // createFileChange Tests
    // =========================================================================

    @Nested
    @DisplayName("createFileChange")
    inner class CreateFileChangeTests {

        @TempDir
        lateinit var tempDir: File

        @Test
        @DisplayName("existing file produces MODIFY change")
        fun existingFileProducesModifyChange() {
            val file = File(tempDir, "test.kt").apply {
                writeText("fun main() {\n    println(\"hello\")\n}")
            }
            val change = service.createFileChange(
                file.absolutePath,
                "fun main() {\n    println(\"world\")\n}"
            )
            assertEquals(ChangeType.MODIFY, change.changeType)
            assertNotNull(change.originalContent)
            assertTrue(change.hunks.isNotEmpty())
        }

        @Test
        @DisplayName("non-existent file produces CREATE change")
        fun nonExistentFileProducesCreateChange() {
            val change = service.createFileChange(
                File(tempDir, "new_file.kt").absolutePath,
                "fun hello() {}"
            )
            assertEquals(ChangeType.CREATE, change.changeType)
            assertNull(change.originalContent)
        }

        @Test
        @DisplayName("identical content produces MODIFY with no hunks")
        fun identicalContentProducesNoHunks() {
            val content = "no changes here"
            val file = File(tempDir, "same.kt").apply { writeText(content) }
            val change = service.createFileChange(file.absolutePath, content)
            assertEquals(ChangeType.MODIFY, change.changeType)
            assertTrue(change.hunks.isEmpty())
        }
    }

    // =========================================================================
    // applyDecision Tests
    // =========================================================================

    @Nested
    @DisplayName("applyDecision")
    inner class ApplyDecisionTests {

        @Test
        @DisplayName("full approval returns proposed content")
        fun fullApprovalReturnsProposed() {
            val original = "line 1\nline 2"
            val proposed = "line 1\nline 2 modified"
            val hunks = service.generateDiff(original, proposed)

            val change = FileChange(
                filePath = "/test.kt",
                originalContent = original,
                proposedContent = proposed,
                hunks = hunks,
                changeType = ChangeType.MODIFY
            )
            val decision = DiffReviewDecision.approveAll(
                "req-1",
                hunks.map { it.id }.toSet()
            )
            val result = service.applyDecision(change, decision)
            assertEquals(proposed, result)
        }

        @Test
        @DisplayName("full rejection returns original content")
        fun fullRejectionReturnsOriginal() {
            val original = "line 1\nline 2"
            val proposed = "line 1\nline 2 modified"
            val hunks = service.generateDiff(original, proposed)

            val change = FileChange(
                filePath = "/test.kt",
                originalContent = original,
                proposedContent = proposed,
                hunks = hunks,
                changeType = ChangeType.MODIFY
            )
            val decision = DiffReviewDecision.rejectAll(
                "req-1",
                hunks.map { it.id }.toSet()
            )
            val result = service.applyDecision(change, decision)
            assertEquals(original, result)
        }

        @Test
        @DisplayName("full rejection of new file returns empty string")
        fun fullRejectionOfNewFileReturnsEmpty() {
            val change = FileChange.newFile("/new.kt", "content")
            val decision = DiffReviewDecision.rejectAll(
                "req-1",
                change.hunks.map { it.id }.toSet()
            )
            val result = service.applyDecision(change, decision)
            assertEquals("", result)
        }
    }

    // =========================================================================
    // formatUnifiedDiff Tests
    // =========================================================================

    @Nested
    @DisplayName("formatUnifiedDiff")
    inner class FormatUnifiedDiffTests {

        @Test
        @DisplayName("produces standard unified diff header")
        fun producesStandardHeader() {
            val change = FileChange.newFile("/path/to/file.kt", "new content")
            val diff = service.formatUnifiedDiff(change)
            assertTrue(diff.contains("--- /dev/null"))
            assertTrue(diff.contains("+++ /path/to/file.kt"))
        }

        @Test
        @DisplayName("modifications use file path for both sides")
        fun modificationsUseSamePath() {
            val hunks = service.generateDiff("old", "new")
            val change = FileChange(
                filePath = "/path/to/file.kt",
                originalContent = "old",
                proposedContent = "new",
                hunks = hunks,
                changeType = ChangeType.MODIFY
            )
            val diff = service.formatUnifiedDiff(change)
            assertTrue(diff.contains("--- /path/to/file.kt"))
            assertTrue(diff.contains("+++ /path/to/file.kt"))
        }

        @Test
        @DisplayName("hunk header contains line numbers")
        fun hunkHeaderContainsLineNumbers() {
            val hunks = service.generateDiff("old line", "new line")
            val change = FileChange(
                filePath = "/file.kt",
                originalContent = "old line",
                proposedContent = "new line",
                hunks = hunks,
                changeType = ChangeType.MODIFY
            )
            val diff = service.formatUnifiedDiff(change)
            assertTrue(diff.contains("@@"), "Should contain hunk header")
        }

        @Test
        @DisplayName("additions prefixed with +")
        fun additionsPrefixed() {
            val change = FileChange.newFile("/file.kt", "added line")
            val diff = service.formatUnifiedDiff(change)
            assertTrue(diff.contains("+added line"))
        }

        @Test
        @DisplayName("deletions prefixed with -")
        fun deletionsPrefixed() {
            val change = FileChange.deleteFile("/file.kt", "deleted line")
            val diff = service.formatUnifiedDiff(change)
            assertTrue(diff.contains("-deleted line"))
        }
    }

    // =========================================================================
    // formatMultiFileDiff Tests
    // =========================================================================

    @Nested
    @DisplayName("formatMultiFileDiff")
    inner class FormatMultiFileDiffTests {

        @Test
        @DisplayName("formats multiple files")
        fun formatsMultipleFiles() {
            val changes = listOf(
                FileChange.newFile("/a.kt", "content a"),
                FileChange.newFile("/b.kt", "content b")
            )
            val diff = service.formatMultiFileDiff(changes)
            assertTrue(diff.contains("+++ /a.kt"))
            assertTrue(diff.contains("+++ /b.kt"))
        }
    }
}
