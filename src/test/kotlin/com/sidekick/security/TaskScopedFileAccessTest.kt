package com.sidekick.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Comprehensive unit tests for TaskScopedFileAccess and TaskFileScope.
 */
@DisplayName("Task Scoped File Access Tests")
class TaskScopedFileAccessTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var projectRoot: File
    private lateinit var scope: TaskFileScope
    private lateinit var access: TaskScopedFileAccess

    @BeforeEach
    fun setup() {
        projectRoot = File(tempDir, "project").apply { mkdirs() }
        File(projectRoot, "src").mkdirs()
        File(projectRoot, "src/Main.kt").writeText("fun main() {}")
        scope = TaskFileScope.forProject(projectRoot.absolutePath)
        // Use empty restrictedPaths to avoid macOS temp dirs (/var/folders) being
        // blocked by DEFAULT_RESTRICTED_PATHS which includes "/var"
        access = TaskScopedFileAccess(scope, SecurityConfig(restrictedPaths = emptySet()))
    }

    // =========================================================================
    // TaskFileScope Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskFileScope")
    inner class TaskFileScopeTests {

        @Test
        @DisplayName("allows paths within project root")
        fun allowsPathsWithinProjectRoot() {
            val path = File(projectRoot, "src/Main.kt").absolutePath
            assertTrue(scope.isPathAllowed(path))
        }

        @Test
        @DisplayName("blocks paths outside project root")
        fun blocksPathsOutsideProjectRoot() {
            val outsidePath = File(tempDir, "other/file.txt").absolutePath
            assertFalse(scope.isPathAllowed(outsidePath))
        }

        @Test
        @DisplayName("blocks sensitive directories")
        fun blocksSensitiveDirectories() {
            val sshPath = File(tempDir, "project/.ssh/id_rsa").absolutePath
            // Even if .ssh is inside project root, deny patterns block it
            File(projectRoot, ".ssh").mkdirs()
            File(projectRoot, ".ssh/id_rsa").writeText("secret")
            assertFalse(scope.isPathAllowed(File(projectRoot, ".ssh/id_rsa").absolutePath))
        }

        @Test
        @DisplayName("blocks .aws directory")
        fun blocksAwsDirectory() {
            File(projectRoot, ".aws").mkdirs()
            File(projectRoot, ".aws/credentials").writeText("secret")
            assertFalse(scope.isPathAllowed(File(projectRoot, ".aws/credentials").absolutePath))
        }

        @Test
        @DisplayName("blocks .gnupg directory")
        fun blocksGnupgDirectory() {
            File(projectRoot, ".gnupg").mkdirs()
            File(projectRoot, ".gnupg/private-keys").writeText("secret")
            assertFalse(scope.isPathAllowed(File(projectRoot, ".gnupg/private-keys").absolutePath))
        }

        @Test
        @DisplayName("allows paths in additional directories")
        fun allowsPathsInAdditionalDirectories() {
            val extraDir = File(tempDir, "extra-lib").apply { mkdirs() }
            File(extraDir, "lib.jar").writeText("binary")
            val scopeWithExtra = scope.withAdditionalDirectory(extraDir.absolutePath)
            assertTrue(scopeWithExtra.isPathAllowed(File(extraDir, "lib.jar").absolutePath))
        }

        @Test
        @DisplayName("withAdditionalDirectory preserves existing scope")
        fun withAdditionalDirectoryPreservesExistingScope() {
            val extraDir = File(tempDir, "extra").apply { mkdirs() }
            val extended = scope.withAdditionalDirectory(extraDir.absolutePath)

            // Original project root still allowed
            assertTrue(extended.isPathAllowed(File(projectRoot, "src/Main.kt").absolutePath))
            // Extra dir allowed
            assertTrue(extended.isPathAllowed(extraDir.absolutePath))
        }

        @Test
        @DisplayName("read-only scope blocks writes")
        fun readOnlyScopeBlocksWrites() {
            val readOnlyScope = TaskFileScope.readOnly(projectRoot.absolutePath)
            val path = File(projectRoot, "src/Main.kt").absolutePath

            assertTrue(readOnlyScope.isPathAllowed(path))
            assertFalse(readOnlyScope.isWriteAllowed(path))
        }

        @Test
        @DisplayName("writable scope allows writes within scope")
        fun writableScopeAllowsWritesWithinScope() {
            val path = File(projectRoot, "src/Main.kt").absolutePath
            assertTrue(scope.isWriteAllowed(path))
        }

        @Test
        @DisplayName("write blocked for out-of-scope path even if writable")
        fun writeBlockedForOutOfScopePath() {
            val outsidePath = File(tempDir, "other/file.txt").absolutePath
            assertFalse(scope.isWriteAllowed(outsidePath))
        }

        @Test
        @DisplayName("forProject creates scope with default settings")
        fun forProjectCreatesDefaultScope() {
            val s = TaskFileScope.forProject("/tmp/myproject")
            assertEquals("/tmp/myproject", s.projectRoot)
            assertTrue(s.allowedDirectories.isEmpty())
            assertFalse(s.readOnly)
            assertEquals(TaskFileScope.DEFAULT_DENY_PATTERNS, s.denyPatterns)
        }

        @Test
        @DisplayName("readOnly creates read-only scope")
        fun readOnlyFactoryCreatesReadOnlyScope() {
            val s = TaskFileScope.readOnly("/tmp/myproject")
            assertTrue(s.readOnly)
        }

        @Test
        @DisplayName("default deny patterns include all sensitive directories")
        fun defaultDenyPatternsIncludeAllSensitiveDirectories() {
            assertTrue(TaskFileScope.DEFAULT_DENY_PATTERNS.contains(".ssh"))
            assertTrue(TaskFileScope.DEFAULT_DENY_PATTERNS.contains(".gnupg"))
            assertTrue(TaskFileScope.DEFAULT_DENY_PATTERNS.contains(".aws"))
            assertTrue(TaskFileScope.DEFAULT_DENY_PATTERNS.contains(".docker"))
            assertTrue(TaskFileScope.DEFAULT_DENY_PATTERNS.contains(".kube"))
        }

        @Test
        @DisplayName("validate detects blank project root")
        fun validateDetectsBlankProjectRoot() {
            val s = TaskFileScope(projectRoot = "")
            val issues = s.validate()
            assertTrue(issues.any { it.contains("projectRoot") })
        }

        @Test
        @DisplayName("validate detects relative project root")
        fun validateDetectsRelativeProjectRoot() {
            val s = TaskFileScope(projectRoot = "relative/path")
            val issues = s.validate()
            assertTrue(issues.any { it.contains("absolute") })
        }

        @Test
        @DisplayName("validate detects relative allowed directory")
        fun validateDetectsRelativeAllowedDirectory() {
            val s = TaskFileScope(
                projectRoot = "/tmp/project",
                allowedDirectories = setOf("relative/dir")
            )
            val issues = s.validate()
            assertTrue(issues.any { it.contains("allowedDirectory") })
        }

        @Test
        @DisplayName("validate passes for valid scope")
        fun validatePassesForValidScope() {
            val issues = scope.validate()
            assertTrue(issues.isEmpty())
        }

        @Test
        @DisplayName("denies path with unresolvable canonical path")
        fun deniesUnresolvablePath() {
            // Null byte in path should cause resolution failure
            assertFalse(scope.isPathAllowed("/path/with/\u0000null"))
        }
    }

    // =========================================================================
    // TaskScopedFileAccess Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskScopedFileAccess.validateAccess")
    inner class ValidateAccessTests {

        @Test
        @DisplayName("allows read within project root")
        fun allowsReadWithinProjectRoot() {
            val path = File(projectRoot, "src/Main.kt").absolutePath
            val result = access.validateAccess(path, write = false)
            assertTrue(result.valid)
        }

        @Test
        @DisplayName("blocks read outside project root")
        fun blocksReadOutsideProjectRoot() {
            val outsideFile = File(tempDir, "outside.txt").apply { writeText("nope") }
            val result = access.validateAccess(outsideFile.absolutePath, write = false)
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "out_of_scope" })
        }

        @Test
        @DisplayName("blocks read of sensitive directory files")
        fun blocksReadOfSensitiveDirectoryFiles() {
            File(projectRoot, ".ssh").mkdirs()
            val sshFile = File(projectRoot, ".ssh/id_rsa").apply { writeText("secret") }
            val result = access.validateAccess(sshFile.absolutePath, write = false)
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "out_of_scope" })
        }

        @Test
        @DisplayName("blocks path traversal")
        fun blocksPathTraversal() {
            val traversalPath = "${projectRoot.absolutePath}/../../../etc/passwd"
            val result = access.validateAccess(traversalPath, write = false)
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "path_traversal" })
        }

        @Test
        @DisplayName("blocks write when scope is read-only")
        fun blocksWriteWhenReadOnly() {
            val readOnlyAccess = TaskScopedFileAccess(
                TaskFileScope.readOnly(projectRoot.absolutePath)
            )
            val path = File(projectRoot, "src/Main.kt").absolutePath
            val result = readOnlyAccess.validateAccess(path, write = true)
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "scope_read_only" })
        }

        @Test
        @DisplayName("allows write within writable scope")
        fun allowsWriteWithinWritableScope() {
            val path = File(projectRoot, "src/Main.kt").absolutePath
            val result = access.validateAccess(path, write = true)
            assertTrue(result.valid)
        }

        @Test
        @DisplayName("checks global restricted paths too")
        fun checksGlobalRestrictedPaths() {
            // Even with a scope that includes /etc, global config blocks it
            val broadScope = TaskFileScope(
                projectRoot = "/",
                denyPatterns = emptySet()
            )
            val broadAccess = TaskScopedFileAccess(broadScope, SecurityConfig())
            val result = broadAccess.validateAccess("/etc/passwd", write = false)
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "restricted_path" })
        }

        @Test
        @DisplayName("warns on large files for reads")
        fun warnsOnLargeFilesForReads() {
            val smallLimitAccess = TaskScopedFileAccess(
                scope,
                SecurityConfig(maxFileSize = 1, restrictedPaths = emptySet()) // 1 byte limit, no global restrictions
            )
            // Create a file larger than 1 byte
            val testFile = File(projectRoot, "test.txt").apply { writeText("hello world") }
            val result = smallLimitAccess.validateAccess(testFile.absolutePath, write = false)
            // Should be valid (warning only, not blocking) but have issues
            assertTrue(result.valid)
            assertTrue(result.issues.any { it.type == "file_too_large" })
        }
    }

    // =========================================================================
    // validateCommandWorkingDir Tests
    // =========================================================================

    @Nested
    @DisplayName("TaskScopedFileAccess.validateCommandWorkingDir")
    inner class ValidateCommandWorkingDirTests {

        @Test
        @DisplayName("allows CWD within project root")
        fun allowsCwdWithinProjectRoot() {
            // Use access with empty restricted paths (set up in @BeforeEach)
            val result = access.validateCommandWorkingDir(projectRoot.absolutePath)
            assertTrue(result.valid)
        }

        @Test
        @DisplayName("allows CWD in subdirectory of project root")
        fun allowsCwdInSubdirectory() {
            val srcDir = File(projectRoot, "src").absolutePath
            val result = access.validateCommandWorkingDir(srcDir)
            assertTrue(result.valid)
        }

        @Test
        @DisplayName("blocks CWD outside project root")
        fun blocksCwdOutsideProjectRoot() {
            val outsideDir = File(tempDir, "other").apply { mkdirs() }.absolutePath
            val result = access.validateCommandWorkingDir(outsideDir)
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "cwd_out_of_scope" })
        }

        @Test
        @DisplayName("blocks CWD in globally restricted area")
        fun blocksCwdInRestrictedArea() {
            val broadScope = TaskFileScope(
                projectRoot = "/",
                denyPatterns = emptySet()
            )
            val broadAccess = TaskScopedFileAccess(broadScope, SecurityConfig())
            val result = broadAccess.validateCommandWorkingDir("/etc")
            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "restricted_path" })
        }
    }

    // =========================================================================
    // SecurityConfig.withTaskScope Tests
    // =========================================================================

    @Nested
    @DisplayName("SecurityConfig.withTaskScope")
    inner class WithTaskScopeTests {

        @Test
        @DisplayName("merges scope restricted paths into config")
        fun mergesScopeRestrictedPaths() {
            val config = SecurityConfig()
            val scoped = config.withTaskScope(scope)

            // Should have more restricted paths than the original
            assertTrue(scoped.restrictedPaths.size >= config.restrictedPaths.size)
        }

        @Test
        @DisplayName("preserves original global restricted paths")
        fun preservesGlobalRestrictedPaths() {
            val config = SecurityConfig()
            val scoped = config.withTaskScope(scope)

            // All original paths should still be present
            config.restrictedPaths.forEach { path ->
                assertTrue(scoped.restrictedPaths.contains(path))
            }
        }
    }
}
