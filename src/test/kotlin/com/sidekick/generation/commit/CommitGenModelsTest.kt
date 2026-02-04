// =============================================================================
// CommitGenModelsTest.kt
// =============================================================================
// Unit tests for commit message generation data models.
// =============================================================================

package com.sidekick.generation.commit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for CommitGenModels.
 */
class CommitGenModelsTest {

    @Nested
    @DisplayName("CommitAnalysis")
    inner class CommitAnalysisTests {
        
        @Test
        @DisplayName("EMPTY has no changes")
        fun `EMPTY has no changes`() {
            val analysis = CommitAnalysis.EMPTY
            
            assertFalse(analysis.hasChanges)
            assertEquals(0, analysis.fileCount)
            assertEquals(0, analysis.totalAdditions)
            assertEquals(0, analysis.totalDeletions)
        }
        
        @Test
        @DisplayName("hasChanges returns true when files exist")
        fun `hasChanges returns true when files exist`() {
            val analysis = CommitAnalysis(
                files = listOf(
                    FileChange("src/main.kt", ChangeStatus.MODIFIED, 10, 5)
                ),
                totalAdditions = 10,
                totalDeletions = 5,
                primaryScope = "src",
                changeType = ConventionalType.FEAT,
                isBreakingChange = false
            )
            
            assertTrue(analysis.hasChanges)
            assertEquals(1, analysis.fileCount)
        }
        
        @Test
        @DisplayName("summary formats correctly")
        fun `summary formats correctly`() {
            val analysis = CommitAnalysis(
                files = listOf(
                    FileChange("src/main.kt", ChangeStatus.MODIFIED, 10, 5),
                    FileChange("src/util.kt", ChangeStatus.ADDED, 20, 0)
                ),
                totalAdditions = 30,
                totalDeletions = 5,
                primaryScope = "src",
                changeType = ConventionalType.FEAT,
                isBreakingChange = false
            )
            
            val summary = analysis.summary()
            assertTrue("2 file(s)" in summary)
            assertTrue("+30 -5" in summary)
            assertTrue("(src)" in summary)
        }
    }

    @Nested
    @DisplayName("FileChange")
    inner class FileChangeTests {
        
        @Test
        @DisplayName("fileName extracts file name from path")
        fun `fileName extracts file name from path`() {
            val change = FileChange("src/main/kotlin/App.kt", ChangeStatus.MODIFIED, 5, 2)
            assertEquals("App.kt", change.fileName)
        }
        
        @Test
        @DisplayName("extension extracts file extension")
        fun `extension extracts file extension`() {
            val change = FileChange("src/main/kotlin/App.kt", ChangeStatus.MODIFIED, 5, 2)
            assertEquals("kt", change.extension)
        }
        
        @Test
        @DisplayName("directory extracts parent directory")
        fun `directory extracts parent directory`() {
            val change = FileChange("src/main/kotlin/App.kt", ChangeStatus.MODIFIED, 5, 2)
            assertEquals("src/main/kotlin", change.directory)
        }
        
        @Test
        @DisplayName("handles root-level files")
        fun `handles root-level files`() {
            val change = FileChange("README.md", ChangeStatus.MODIFIED, 1, 1)
            assertEquals("README.md", change.fileName)
            assertEquals("md", change.extension)
            assertEquals("", change.directory)
        }
    }

    @Nested
    @DisplayName("ChangeStatus")
    inner class ChangeStatusTests {
        
        @Test
        @DisplayName("fromSymbol parses known symbols")
        fun `fromSymbol parses known symbols`() {
            assertEquals(ChangeStatus.ADDED, ChangeStatus.fromSymbol("A"))
            assertEquals(ChangeStatus.MODIFIED, ChangeStatus.fromSymbol("M"))
            assertEquals(ChangeStatus.DELETED, ChangeStatus.fromSymbol("D"))
            assertEquals(ChangeStatus.RENAMED, ChangeStatus.fromSymbol("R"))
        }
        
        @Test
        @DisplayName("fromSymbol returns MODIFIED for unknown")
        fun `fromSymbol returns MODIFIED for unknown`() {
            assertEquals(ChangeStatus.MODIFIED, ChangeStatus.fromSymbol("X"))
        }
        
        @Test
        @DisplayName("has correct display names")
        fun `has correct display names`() {
            assertEquals("Added", ChangeStatus.ADDED.displayName)
            assertEquals("Modified", ChangeStatus.MODIFIED.displayName)
            assertEquals("Deleted", ChangeStatus.DELETED.displayName)
        }
    }

    @Nested
    @DisplayName("ConventionalType")
    inner class ConventionalTypeTests {
        
        @Test
        @DisplayName("detect returns TEST for test files")
        fun `detect returns TEST for test files`() {
            val files = listOf(
                FileChange("src/test/AppTest.kt", ChangeStatus.MODIFIED, 10, 5)
            )
            assertEquals(ConventionalType.TEST, ConventionalType.detect(files))
        }
        
        @Test
        @DisplayName("detect returns DOCS for markdown files")
        fun `detect returns DOCS for markdown files`() {
            val files = listOf(
                FileChange("docs/README.md", ChangeStatus.MODIFIED, 10, 5)
            )
            assertEquals(ConventionalType.DOCS, ConventionalType.detect(files))
        }
        
        @Test
        @DisplayName("detect returns CI for github workflow files")
        fun `detect returns CI for github workflow files`() {
            val files = listOf(
                FileChange(".github/workflows/ci.yml", ChangeStatus.MODIFIED, 10, 5)
            )
            assertEquals(ConventionalType.CI, ConventionalType.detect(files))
        }
        
        @Test
        @DisplayName("detect returns BUILD for gradle files")
        fun `detect returns BUILD for gradle files`() {
            val files = listOf(
                FileChange("build.gradle.kts", ChangeStatus.MODIFIED, 10, 5)
            )
            assertEquals(ConventionalType.BUILD, ConventionalType.detect(files))
        }
        
        @Test
        @DisplayName("detect returns FEAT for code files")
        fun `detect returns FEAT for code files`() {
            val files = listOf(
                FileChange("src/main/App.kt", ChangeStatus.ADDED, 100, 0)
            )
            assertEquals(ConventionalType.FEAT, ConventionalType.detect(files))
        }
        
        @Test
        @DisplayName("detect returns CHORE for empty list")
        fun `detect returns CHORE for empty list`() {
            assertEquals(ConventionalType.CHORE, ConventionalType.detect(emptyList()))
        }
        
        @Test
        @DisplayName("all returns all types")
        fun `all returns all types`() {
            val all = ConventionalType.all()
            assertTrue(all.size >= 10)
            assertTrue(ConventionalType.FEAT in all)
            assertTrue(ConventionalType.FIX in all)
        }
        
        @Test
        @DisplayName("has correct prefixes")
        fun `has correct prefixes`() {
            assertEquals("feat", ConventionalType.FEAT.prefix)
            assertEquals("fix", ConventionalType.FIX.prefix)
            assertEquals("docs", ConventionalType.DOCS.prefix)
            assertEquals("refactor", ConventionalType.REFACTOR.prefix)
        }
    }

    @Nested
    @DisplayName("CommitMessage")
    inner class CommitMessageTests {
        
        @Test
        @DisplayName("format produces conventional commit format")
        fun `format produces conventional commit format`() {
            val message = CommitMessage(
                type = ConventionalType.FEAT,
                scope = "auth",
                subject = "add login page",
                body = null,
                footer = null,
                isBreaking = false
            )
            
            assertEquals("feat(auth): add login page", message.format())
        }
        
        @Test
        @DisplayName("format includes breaking change marker")
        fun `format includes breaking change marker`() {
            val message = CommitMessage(
                type = ConventionalType.FEAT,
                scope = "api",
                subject = "change response format",
                body = null,
                footer = null,
                isBreaking = true
            )
            
            assertEquals("feat(api)!: change response format", message.format())
        }
        
        @Test
        @DisplayName("format includes body and footer")
        fun `format includes body and footer`() {
            val message = CommitMessage(
                type = ConventionalType.FIX,
                scope = null,
                subject = "fix null pointer",
                body = "The issue was caused by uninitialized variable.",
                footer = "Closes #123",
                isBreaking = false
            )
            
            val formatted = message.format()
            assertTrue("fix: fix null pointer" in formatted)
            assertTrue("The issue was caused by uninitialized variable." in formatted)
            assertTrue("Closes #123" in formatted)
        }
        
        @Test
        @DisplayName("formatWithEmoji adds emoji prefix")
        fun `formatWithEmoji adds emoji prefix`() {
            val message = CommitMessage.simple(ConventionalType.FEAT, "add feature")
            
            val formatted = message.formatWithEmoji()
            assertTrue(formatted.startsWith("✨"))
        }
        
        @Test
        @DisplayName("header returns only first line")
        fun `header returns only first line`() {
            val message = CommitMessage(
                type = ConventionalType.FEAT,
                scope = "ui",
                subject = "add button",
                body = "Long description here",
                footer = null,
                isBreaking = false
            )
            
            assertEquals("feat(ui): add button", message.header())
        }
        
        @Test
        @DisplayName("simple creates basic message")
        fun `simple creates basic message`() {
            val message = CommitMessage.simple(ConventionalType.FIX, "fix bug")
            
            assertEquals(ConventionalType.FIX, message.type)
            assertNull(message.scope)
            assertEquals("fix bug", message.subject)
            assertFalse(message.isBreaking)
        }
        
        @Test
        @DisplayName("withScope creates message with scope")
        fun `withScope creates message with scope`() {
            val message = CommitMessage.withScope(ConventionalType.REFACTOR, "core", "simplify logic")
            
            assertEquals("core", message.scope)
            assertEquals("refactor(core): simplify logic", message.format())
        }
    }

    @Nested
    @DisplayName("CommitGenResult")
    inner class CommitGenResultTests {
        
        @Test
        @DisplayName("success creates successful result")
        fun `success creates successful result`() {
            val message = CommitMessage.simple(ConventionalType.FEAT, "add feature")
            val analysis = CommitAnalysis.EMPTY
            
            val result = CommitGenResult.success(message, analysis)
            
            assertTrue(result.success)
            assertNotNull(result.message)
            assertNull(result.error)
        }
        
        @Test
        @DisplayName("failure creates failed result")
        fun `failure creates failed result`() {
            val result = CommitGenResult.failure("No staged changes")
            
            assertFalse(result.success)
            assertNull(result.message)
            assertEquals("No staged changes", result.error)
        }
    }
}
