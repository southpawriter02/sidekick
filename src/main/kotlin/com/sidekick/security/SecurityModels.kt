// =============================================================================
// SecurityModels.kt
// =============================================================================
// Data models for security hardening in the Sidekick plugin.
//
// This file contains all data contracts used by the security subsystem:
// - SecurityConfig: Configuration options for sandboxing
// - SecurityEvent: Logged security events
// - ValidationResult: Result of security validation
// - SecurityIssue: Individual security concern
//
// DESIGN NOTES:
// - All models are immutable data classes for thread-safety
// - Enums provide clear categorization of severity and event types
// - Default configurations are secure by default
//
// @since v1.0.2
// =============================================================================

package com.sidekick.security

import java.time.Instant
import java.util.UUID

// =============================================================================
// Security Configuration
// =============================================================================

/**
 * Configuration for security hardening features.
 *
 * Controls command sandboxing, file access restrictions, and input validation.
 * All defaults are set to provide secure-by-default behavior.
 *
 * ## Default Behavior
 * - Commands are sandboxed to an allowlist
 * - Dangerous patterns like `rm -rf`, `sudo` are blocked
 * - System paths (`/etc`, `/usr`, `/bin`) are restricted
 * - Files over 10MB are not read by default
 *
 * ## Custom Configuration
 * ```kotlin
 * val config = SecurityConfig(
 *     sandboxCommands = true,
 *     allowedCommands = setOf("git", "npm", "yarn"),
 *     requireConfirmation = ConfirmationLevel.ALL
 * )
 * ```
 *
 * @property sandboxCommands Whether to restrict commands to allowlist
 * @property allowedCommands Commands permitted when sandboxing is enabled
 * @property blockedPatterns String patterns that are always blocked
 * @property maxFileSize Maximum file size for read operations (bytes)
 * @property restrictedPaths System paths that cannot be accessed
 * @property requireConfirmation When to prompt user for confirmation
 */
data class SecurityConfig(
    val sandboxCommands: Boolean = true,
    val allowedCommands: Set<String> = DEFAULT_ALLOWED_COMMANDS,
    val blockedPatterns: Set<String> = DEFAULT_BLOCKED_PATTERNS,
    val maxFileSize: Long = 10 * 1024 * 1024, // 10MB
    val restrictedPaths: Set<String> = DEFAULT_RESTRICTED_PATHS,
    val requireConfirmation: ConfirmationLevel = ConfirmationLevel.DESTRUCTIVE
) {
    /**
     * Checks if a command executable is in the allowed list.
     *
     * @param executable The command name (e.g., "git", "npm")
     * @return true if allowed or sandboxing is disabled
     */
    fun isCommandAllowed(executable: String): Boolean {
        return !sandboxCommands || executable in allowedCommands
    }

    /**
     * Checks if a path is restricted.
     *
     * @param absolutePath The absolute path to check
     * @return true if the path is in a restricted area
     */
    fun isPathRestricted(absolutePath: String): Boolean {
        return restrictedPaths.any { absolutePath.startsWith(it) }
    }

    /**
     * Checks if input contains any blocked patterns.
     *
     * @param input The input string to check
     * @return The first matching blocked pattern, or null if clean
     */
    fun findBlockedPattern(input: String): String? {
        return blockedPatterns.find { input.contains(it) }
    }

    /**
     * Creates a more restrictive configuration.
     */
    fun harden() = copy(
        sandboxCommands = true,
        requireConfirmation = ConfirmationLevel.ALL,
        maxFileSize = 5 * 1024 * 1024, // 5MB
        restrictedPaths = restrictedPaths + setOf("/System", "/Library", "/private")
    )

    /**
     * Creates a less restrictive configuration for trusted environments.
     */
    fun relax() = copy(
        sandboxCommands = false,
        requireConfirmation = ConfirmationLevel.DESTRUCTIVE,
        maxFileSize = 50 * 1024 * 1024 // 50MB
    )

    /**
     * Validates the configuration.
     *
     * @return List of validation issues, empty if valid
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()
        if (maxFileSize <= 0) issues.add("maxFileSize must be positive")
        if (sandboxCommands && allowedCommands.isEmpty()) {
            issues.add("allowedCommands cannot be empty when sandboxing is enabled")
        }
        return issues
    }

    companion object {
        /**
         * Default allowed commands for development workflows.
         */
        val DEFAULT_ALLOWED_COMMANDS = setOf(
            "git", "dotnet", "npm", "npx", "yarn", "pnpm",
            "gradle", "gradlew", "mvn", "cargo", "rustc",
            "python", "python3", "pip", "pip3",
            "node", "deno", "bun",
            "go", "make", "cmake"
        )

        /**
         * Patterns that are always blocked for safety.
         */
        val DEFAULT_BLOCKED_PATTERNS = setOf(
            "rm -rf /",
            "rm -rf ~",
            "rm -rf /*",
            "sudo ",
            "chmod 777",
            "> /dev/",
            "| sh",
            "| bash",
            "curl | sh",
            "wget | sh",
            ":(){:|:&};:",  // Fork bomb
            "mkfs.",
            "dd if=/dev/zero"
        )

        /**
         * System paths that should not be modified.
         */
        val DEFAULT_RESTRICTED_PATHS = setOf(
            "/etc",
            "/usr",
            "/bin",
            "/sbin",
            "/var",
            "/System",
            "/Library"
        )

        /** Default secure configuration. */
        val DEFAULT = SecurityConfig()

        /** Minimal configuration for testing. */
        val PERMISSIVE = SecurityConfig(
            sandboxCommands = false,
            blockedPatterns = emptySet(),
            restrictedPaths = emptySet(),
            requireConfirmation = ConfirmationLevel.NONE
        )
    }
    /**
     * Creates a SecurityConfig layered with per-task file scope restrictions.
     *
     * Merges the task scope's denied directories into restrictedPaths so that
     * both global and per-task restrictions are enforced together.
     *
     * @param scope The task-scoped file access boundary
     * @return A new SecurityConfig with additional restrictions from the scope
     */
    fun withTaskScope(scope: TaskFileScope): SecurityConfig {
        return copy(
            restrictedPaths = restrictedPaths + scope.effectiveRestrictedPaths()
        )
    }
}

// =============================================================================
// Task File Scope
// =============================================================================

/**
 * Defines the file access boundary for a single agent task.
 *
 * Confines the agent to the project root directory (and any explicitly
 * approved additional directories) for the duration of one task execution.
 * This limits blast radius if a malicious model response attempts to access
 * sensitive files like `~/.ssh/` or `/etc/passwd`.
 *
 * ## Security Model
 * - All paths are canonicalized before checking to prevent traversal attacks
 * - Sensitive directories (`.ssh`, `.gnupg`, `.aws`, etc.) are always denied
 * - The scope is *additive* on top of global [SecurityConfig] restrictions
 *
 * ## Usage
 * ```kotlin
 * val scope = TaskFileScope(
 *     projectRoot = "/home/user/project",
 *     allowedDirectories = setOf("/home/user/.gradle")
 * )
 * scope.isPathAllowed("/home/user/project/src/Main.kt")  // true
 * scope.isPathAllowed("/home/user/.ssh/id_rsa")           // false
 * ```
 *
 * @property projectRoot Canonical path to the project root directory
 * @property allowedDirectories Additional directories the task may access
 * @property denyPatterns Path suffixes/segments that are always denied
 * @property readOnly Whether the scope only permits read operations
 */
data class TaskFileScope(
    val projectRoot: String,
    val allowedDirectories: Set<String> = emptySet(),
    val denyPatterns: Set<String> = DEFAULT_DENY_PATTERNS,
    val readOnly: Boolean = false
) {
    /**
     * Checks if the given absolute path is within the task scope.
     *
     * The path is canonicalized to resolve symlinks and `..` segments,
     * then verified against the project root and allowed directories.
     * Sensitive deny patterns are always checked.
     *
     * @param absolutePath The absolute file path to check
     * @return true if the path is allowed by this scope
     */
    fun isPathAllowed(absolutePath: String): Boolean {
        val canonical = try {
            java.io.File(absolutePath).canonicalPath
        } catch (e: Exception) {
            return false // Cannot resolve → deny
        }

        // Always deny sensitive patterns
        if (matchesDenyPattern(canonical)) return false

        // Check project root
        if (canonical.startsWith(canonicalProjectRoot)) return true

        // Check additional allowed directories
        return allowedDirectories.any { dir ->
            val canonicalDir = try {
                java.io.File(dir).canonicalPath
            } catch (e: Exception) {
                return@any false
            }
            canonical.startsWith(canonicalDir)
        }
    }

    /**
     * Checks if a write operation is allowed for the given path.
     *
     * @param absolutePath The absolute file path to check
     * @return true if writing is permitted
     */
    fun isWriteAllowed(absolutePath: String): Boolean {
        return !readOnly && isPathAllowed(absolutePath)
    }

    /**
     * Returns a copy with an additional allowed directory.
     *
     * @param directory The directory to add to the allowed set
     * @return New scope with the directory added
     */
    fun withAdditionalDirectory(directory: String): TaskFileScope {
        return copy(allowedDirectories = allowedDirectories + directory)
    }

    /**
     * Gets the set of restricted paths that this scope implies,
     * for merging into a [SecurityConfig].
     *
     * Returns the sensitive directory paths based on deny patterns.
     */
    fun effectiveRestrictedPaths(): Set<String> {
        val home = System.getProperty("user.home") ?: return emptySet()
        return SENSITIVE_DIRECTORIES.map { "$home/$it" }.toSet()
    }

    /**
     * Validates the scope configuration.
     *
     * @return List of validation issues, empty if valid
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()
        if (projectRoot.isBlank()) {
            issues.add("projectRoot must not be blank")
        }
        val rootFile = java.io.File(projectRoot)
        if (!rootFile.isAbsolute) {
            issues.add("projectRoot must be an absolute path")
        }
        allowedDirectories.forEach { dir ->
            if (!java.io.File(dir).isAbsolute) {
                issues.add("allowedDirectory must be absolute: $dir")
            }
        }
        return issues
    }

    // Cached canonical project root for performance
    private val canonicalProjectRoot: String by lazy {
        try {
            java.io.File(projectRoot).canonicalPath
        } catch (e: Exception) {
            projectRoot
        }
    }

    /**
     * Checks if a canonical path matches any deny pattern.
     */
    private fun matchesDenyPattern(canonicalPath: String): Boolean {
        return denyPatterns.any { pattern ->
            canonicalPath.contains("/$pattern/") ||
                canonicalPath.endsWith("/$pattern")
        }
    }

    companion object {
        /**
         * Sensitive directory names that are always denied regardless of scope.
         */
        val SENSITIVE_DIRECTORIES = setOf(
            ".ssh",
            ".gnupg",
            ".gpg",
            ".aws",
            ".azure",
            ".gcloud",
            ".config/gcloud",
            ".docker",
            ".kube",
            ".npmrc",
            ".pypirc",
            ".netrc",
            ".env",
            ".credentials"
        )

        /**
         * Default deny patterns — path segments that are always blocked.
         */
        val DEFAULT_DENY_PATTERNS: Set<String> = SENSITIVE_DIRECTORIES

        /**
         * Creates a scope rooted at the given project path with default settings.
         *
         * @param projectRoot Absolute path to the project root
         * @return A new task file scope
         */
        fun forProject(projectRoot: String): TaskFileScope {
            return TaskFileScope(projectRoot = projectRoot)
        }

        /**
         * Creates a read-only scope (agent can read but not write).
         *
         * @param projectRoot Absolute path to the project root
         * @return A new read-only task file scope
         */
        fun readOnly(projectRoot: String): TaskFileScope {
            return TaskFileScope(projectRoot = projectRoot, readOnly = true)
        }
    }
}

// =============================================================================
// Confirmation Level
// =============================================================================

/**
 * Level of user confirmation required for operations.
 *
 * Controls when the user must explicitly approve actions performed
 * by the agent.
 */
enum class ConfirmationLevel {
    /**
     * No confirmation required - auto-execute all operations.
     * Use only in fully trusted environments.
     */
    NONE,

    /**
     * Confirm only destructive operations (delete, overwrite, etc.).
     * Recommended default for most users.
     */
    DESTRUCTIVE,

    /**
     * Confirm all operations including reads.
     * Maximum security for sensitive environments.
     */
    ALL;

    /**
     * Checks if confirmation is required for an operation.
     *
     * @param isDestructive Whether the operation modifies or deletes data
     * @return true if user confirmation should be requested
     */
    fun requiresConfirmation(isDestructive: Boolean): Boolean = when (this) {
        NONE -> false
        DESTRUCTIVE -> isDestructive
        ALL -> true
    }
}

// =============================================================================
// Security Events
// =============================================================================

/**
 * Types of security events that can be logged.
 */
enum class SecurityEventType(val displayName: String, val logLevel: String) {
    /** A command was blocked by the sandbox. */
    COMMAND_BLOCKED("Command Blocked", "WARN"),

    /** File access was denied due to restrictions. */
    FILE_ACCESS_DENIED("File Access Denied", "WARN"),

    /** A path traversal attack was detected. */
    PATH_TRAVERSAL_ATTEMPT("Path Traversal Attempt", "ERROR"),

    /** Rate limiting was triggered. */
    RATE_LIMIT_EXCEEDED("Rate Limit Exceeded", "WARN"),

    /** Invalid or malformed input was detected. */
    INVALID_INPUT("Invalid Input", "INFO"),

    /** A suspicious pattern was detected in input. */
    SUSPICIOUS_PATTERN("Suspicious Pattern", "WARN"),

    /** Configuration was modified. */
    CONFIG_CHANGED("Configuration Changed", "INFO"),

    /** Security check passed. */
    VALIDATION_PASSED("Validation Passed", "DEBUG")
}

/**
 * Severity level for security issues.
 */
enum class SecuritySeverity(val level: Int, val icon: String) {
    /** Informational - no action required. */
    INFO(0, "ℹ️"),

    /** Warning - should be reviewed. */
    WARNING(1, "⚠️"),

    /** High severity - likely needs blocking. */
    HIGH(2, "🔴"),

    /** Critical - must be blocked immediately. */
    CRITICAL(3, "🚨");

    /**
     * Checks if this severity should cause a block.
     */
    val shouldBlock: Boolean
        get() = this >= HIGH

    companion object {
        /**
         * Gets the maximum severity from a list.
         */
        fun max(severities: List<SecuritySeverity>): SecuritySeverity {
            return severities.maxByOrNull { it.level } ?: INFO
        }
    }
}

/**
 * A logged security event.
 *
 * Captures details about a security-relevant action, whether it was
 * blocked, and contextual information for auditing.
 *
 * @property id Unique identifier for this event
 * @property type Category of security event
 * @property severity How severe the issue is
 * @property description Human-readable description
 * @property context Additional key-value context (sanitized)
 * @property timestamp When the event occurred
 * @property blocked Whether the action was blocked
 */
data class SecurityEvent(
    val id: String,
    val type: SecurityEventType,
    val severity: SecuritySeverity,
    val description: String,
    val context: Map<String, String>,
    val timestamp: Instant,
    val blocked: Boolean
) {
    /**
     * Formats the event for logging.
     */
    fun format(): String {
        val status = if (blocked) "BLOCKED" else "ALLOWED"
        return "[${severity.icon} $status] ${type.displayName}: $description"
    }

    /**
     * Checks if this event should be reported to the user.
     */
    val isUserVisible: Boolean
        get() = severity >= SecuritySeverity.WARNING || blocked

    companion object {
        /**
         * Creates a new security event with auto-generated ID and timestamp.
         */
        fun create(
            type: SecurityEventType,
            severity: SecuritySeverity,
            description: String,
            context: Map<String, String> = emptyMap(),
            blocked: Boolean = false
        ) = SecurityEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            severity = severity,
            description = description,
            context = context,
            timestamp = Instant.now(),
            blocked = blocked
        )

        /**
         * Creates a blocked event.
         */
        fun blocked(
            type: SecurityEventType,
            description: String,
            context: Map<String, String> = emptyMap()
        ) = create(
            type = type,
            severity = SecuritySeverity.HIGH,
            description = description,
            context = context,
            blocked = true
        )
    }
}

// =============================================================================
// Validation Results
// =============================================================================

/**
 * Individual security issue found during validation.
 *
 * @property type Category of the issue (e.g., "dangerous_pattern")
 * @property description Human-readable explanation
 * @property severity How severe this issue is
 */
data class SecurityIssue(
    val type: String,
    val description: String,
    val severity: SecuritySeverity
) {
    /**
     * Checks if this issue should block the operation.
     */
    val shouldBlock: Boolean
        get() = severity.shouldBlock

    /**
     * Formats for display.
     */
    fun format(): String = "${severity.icon} [$type] $description"

    companion object {
        /** Creates an info-level issue. */
        fun info(type: String, description: String) =
            SecurityIssue(type, description, SecuritySeverity.INFO)

        /** Creates a warning-level issue. */
        fun warning(type: String, description: String) =
            SecurityIssue(type, description, SecuritySeverity.WARNING)

        /** Creates a high-severity issue. */
        fun high(type: String, description: String) =
            SecurityIssue(type, description, SecuritySeverity.HIGH)

        /** Creates a critical issue. */
        fun critical(type: String, description: String) =
            SecurityIssue(type, description, SecuritySeverity.CRITICAL)
    }
}

/**
 * Result of a security validation check.
 *
 * Contains whether the input is valid, a sanitized version if applicable,
 * and a list of any issues found.
 *
 * @property valid Whether the validation passed (no blocking issues)
 * @property sanitized The sanitized/cleaned input, null if invalid
 * @property issues List of security issues found
 */
data class ValidationResult(
    val valid: Boolean,
    val sanitized: String?,
    val issues: List<SecurityIssue>
) {
    /**
     * The highest severity issue found.
     */
    val maxSeverity: SecuritySeverity
        get() = SecuritySeverity.max(issues.map { it.severity })

    /**
     * Issues that would cause blocking.
     */
    val blockingIssues: List<SecurityIssue>
        get() = issues.filter { it.shouldBlock }

    /**
     * Whether there are any issues at all.
     */
    val hasIssues: Boolean
        get() = issues.isNotEmpty()

    /**
     * Formats all issues for display.
     */
    fun formatIssues(): String = issues.joinToString("\n") { it.format() }

    companion object {
        /**
         * Creates a valid result with no issues.
         */
        fun valid(sanitized: String) = ValidationResult(
            valid = true,
            sanitized = sanitized,
            issues = emptyList()
        )

        /**
         * Creates a valid result with warnings (non-blocking issues).
         */
        fun validWithWarnings(sanitized: String, issues: List<SecurityIssue>) = ValidationResult(
            valid = true,
            sanitized = sanitized,
            issues = issues.filter { !it.shouldBlock }
        )

        /**
         * Creates an invalid result with blocking issues.
         */
        fun invalid(issues: List<SecurityIssue>) = ValidationResult(
            valid = false,
            sanitized = null,
            issues = issues
        )

        /**
         * Creates an invalid result with a single issue.
         */
        fun blocked(type: String, description: String, severity: SecuritySeverity = SecuritySeverity.HIGH) =
            invalid(listOf(SecurityIssue(type, description, severity)))
    }
}
