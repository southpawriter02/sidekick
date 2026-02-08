// =============================================================================
// TaskScopedFileAccess.kt
// =============================================================================
// Per-task file access validation combining TaskFileScope with global
// SecurityConfig restrictions.
//
// This is a pure-logic class (not an IntelliJ service) for easy testing.
// It bridges the per-task scope with the global security configuration,
// producing unified ValidationResult objects.
//
// @since v1.1.0
// =============================================================================

package com.sidekick.security

import java.io.File

/**
 * Validates file access against both a per-task [TaskFileScope] and the
 * global [SecurityConfig].
 *
 * This class combines two layers of protection:
 * 1. **Task scope** — confines access to the project root and approved dirs
 * 2. **Global config** — enforces system-wide restricted paths and file size limits
 *
 * ## Usage
 * ```kotlin
 * val scope = TaskFileScope.forProject("/home/user/project")
 * val access = TaskScopedFileAccess(scope, SecurityConfig())
 *
 * val result = access.validateAccess("/home/user/project/src/Main.kt", write = false)
 * assert(result.valid)
 *
 * val blocked = access.validateAccess("/home/user/.ssh/id_rsa", write = false)
 * assert(!blocked.valid)
 * ```
 *
 * @property scope The per-task file access boundary
 * @property globalConfig The global security configuration
 */
class TaskScopedFileAccess(
    private val scope: TaskFileScope,
    private val globalConfig: SecurityConfig = SecurityConfig()
) {

    /**
     * Validates file access against both task scope and global restrictions.
     *
     * Checks performed (in order):
     * 1. Path traversal detection (`..` in path)
     * 2. Task scope boundary check (project root / allowed dirs)
     * 3. Sensitive directory deny patterns
     * 4. Write permission check (if scope is read-only)
     * 5. Global restricted paths check
     * 6. File size limit check (for reads)
     *
     * @param path The absolute file path to validate
     * @param write Whether this is a write operation
     * @return Validation result with any security issues found
     */
    fun validateAccess(path: String, write: Boolean): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()

        // 1. Check for path traversal
        if (path.contains("..")) {
            issues.add(SecurityIssue.critical(
                "path_traversal",
                "Path contains '..' which may indicate path traversal attack"
            ))
        }

        // 2. Check task scope boundary
        if (!scope.isPathAllowed(path)) {
            issues.add(SecurityIssue.high(
                "out_of_scope",
                "Path is outside the task scope (project root: ${scope.projectRoot})"
            ))
        }

        // 3. Write permission check
        if (write && scope.readOnly) {
            issues.add(SecurityIssue.high(
                "scope_read_only",
                "Task scope is read-only; write operations are not permitted"
            ))
        } else if (write && !scope.isWriteAllowed(path)) {
            issues.add(SecurityIssue.high(
                "write_denied",
                "Write access denied for this path in the current task scope"
            ))
        }

        // 4. Check global restricted paths
        val absolutePath = File(path).absolutePath
        if (globalConfig.isPathRestricted(absolutePath)) {
            issues.add(SecurityIssue.high(
                "restricted_path",
                "Path is in a globally restricted area: " +
                    "${globalConfig.restrictedPaths.find { absolutePath.startsWith(it) }}"
            ))
        }

        // 5. File size limit for reads
        if (!write) {
            val file = File(path)
            if (file.exists() && file.length() > globalConfig.maxFileSize) {
                issues.add(SecurityIssue.warning(
                    "file_too_large",
                    "File size (${file.length() / 1024}KB) exceeds maximum " +
                        "(${globalConfig.maxFileSize / 1024}KB)"
                ))
            }
        }

        val valid = issues.none { it.shouldBlock }
        return ValidationResult(
            valid = valid,
            sanitized = if (valid) path else null,
            issues = issues
        )
    }

    /**
     * Validates that a command's working directory is within the task scope.
     *
     * @param workingDir The directory where a command would execute
     * @return Validation result with any issues found
     */
    fun validateCommandWorkingDir(workingDir: String): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()

        if (!scope.isPathAllowed(workingDir)) {
            issues.add(SecurityIssue.high(
                "cwd_out_of_scope",
                "Command working directory is outside the task scope " +
                    "(project root: ${scope.projectRoot})"
            ))
        }

        if (globalConfig.isPathRestricted(File(workingDir).absolutePath)) {
            issues.add(SecurityIssue.high(
                "restricted_path",
                "Command working directory is in a restricted area"
            ))
        }

        val valid = issues.none { it.shouldBlock }
        return ValidationResult(
            valid = valid,
            sanitized = if (valid) workingDir else null,
            issues = issues
        )
    }

    /**
     * Returns the active scope.
     */
    fun getScope(): TaskFileScope = scope
}
