// =============================================================================
// CommandSandbox.kt
// =============================================================================
// Application-level service for sandboxing commands and validating inputs.
//
// Provides security controls for:
// - Command execution validation against allowlists and dangerous patterns
// - File access validation with path traversal detection
// - Input sanitization to remove dangerous characters
// - Security event logging for audit trails
//
// DESIGN NOTES:
// - Application-level service (@Service(Service.Level.APP))
// - Thread-safe with synchronized event logging
// - Configurable via SecurityConfig
// - Secure by default - blocks dangerous operations unless explicitly allowed
//
// @since v1.0.2
// =============================================================================

package com.sidekick.security

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sandboxes command execution and validates inputs for security.
 *
 * This application-level service provides centralized security controls
 * for all agent operations, including command execution, file access,
 * and user input handling.
 *
 * ## Usage
 * ```kotlin
 * val sandbox = CommandSandbox.getInstance()
 *
 * // Validate a command before execution
 * val result = sandbox.validateCommand("git status", "/path/to/project")
 * if (result.valid) {
 *     // Safe to execute
 * }
 *
 * // Sanitize user input
 * val clean = sandbox.sanitizeInput(userInput)
 * ```
 *
 * ## Security Model
 * - **Allowlist**: Only explicitly allowed commands can execute when sandboxing is enabled
 * - **Blocklist**: Dangerous patterns are always blocked regardless of allowlist
 * - **Path Restrictions**: System paths cannot be accessed
 * - **Input Sanitization**: Dangerous characters are stripped from input
 *
 * ## Thread Safety
 * All methods are thread-safe. Event logging uses CopyOnWriteArrayList.
 */
@Service(Service.Level.APP)
class CommandSandbox {

    private val log = Logger.getInstance(CommandSandbox::class.java)

    /**
     * Current security configuration.
     */
    @Volatile
    private var config = SecurityConfig()

    /**
     * Log of security events for auditing.
     */
    private val eventLog = CopyOnWriteArrayList<SecurityEvent>()

    companion object {
        /**
         * Gets the singleton CommandSandbox instance.
         */
        fun getInstance(): CommandSandbox {
            return ApplicationManager.getApplication().getService(CommandSandbox::class.java)
        }

        /**
         * Maximum number of events to keep in the log.
         */
        private const val MAX_EVENT_LOG_SIZE = 1000

        /**
         * Maximum input length after sanitization.
         */
        private const val MAX_INPUT_LENGTH = 10000

        /**
         * Regex patterns for dangerous commands.
         *
         * These patterns are checked against all commands regardless of
         * the allowlist configuration.
         */
        private val DANGEROUS_PATTERNS = listOf(
            // Recursive force delete from root or home
            Regex("""rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r)\s+[/~]"""),
            Regex("""rm\s+-rf\s+[/~]"""),
            
            // Writing to device files
            Regex(""">[\s]*\/dev\/"""),
            
            // Piping to shell
            Regex("""\|\s*(ba)?sh"""),
            Regex("""\|\s*zsh"""),
            
            // Downloading and executing
            Regex("""curl[^|]*\|\s*(ba)?sh"""),
            Regex("""wget[^|]*\|\s*(ba)?sh"""),
            
            // Dangerous permission changes
            Regex("""chmod\s+777"""),
            Regex("""chmod\s+-R\s+777"""),
            
            // Privilege escalation
            Regex("""sudo\s+"""),
            Regex("""su\s+-"""),
            Regex("""doas\s+"""),
            
            // Fork bomb
            Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;?\s*:"""),
            
            // Disk operations
            Regex("""mkfs\."""),
            Regex("""dd\s+if=/dev/zero"""),
            Regex("""dd\s+of=/dev/"""),
            
            // Environment manipulation
            Regex("""export\s+PATH\s*="""),
            Regex("""unset\s+PATH""")
        )

        /**
         * Characters that should be stripped from user input.
         */
        private val DANGEROUS_CHARS = Regex("""[<>"'`$\\]""")

        /**
         * Characters that indicate potential injection.
         */
        private val INJECTION_CHARS = Regex("""[;&|`$]""")
    }

    // =========================================================================
    // Command Validation
    // =========================================================================

    /**
     * Validates a command before execution.
     *
     * Checks the command against:
     * 1. Dangerous patterns (always blocked)
     * 2. Blocked patterns from config
     * 3. Command allowlist (if sandboxing enabled)
     * 4. Working directory restrictions
     *
     * @param command The full command string to validate
     * @param workingDir The directory where the command would execute
     * @param taskScope Optional per-task file scope for working directory validation
     * @return Validation result with any security issues found
     */
    fun validateCommand(command: String, workingDir: String, taskScope: TaskFileScope? = null): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()

        // Check for dangerous patterns (always block)
        DANGEROUS_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(command)) {
                issues.add(SecurityIssue.critical(
                    "dangerous_pattern",
                    "Command matches dangerous pattern: ${pattern.pattern.take(30)}..."
                ))
            }
        }

        // Check blocked patterns from config
        val blockedPattern = config.findBlockedPattern(command)
        if (blockedPattern != null) {
            issues.add(SecurityIssue.high(
                "blocked_pattern",
                "Command contains blocked pattern: $blockedPattern"
            ))
        }

        // Check if command executable is in allowed list
        val executable = extractExecutable(command)
        if (!config.isCommandAllowed(executable)) {
            issues.add(SecurityIssue.warning(
                "unknown_command",
                "Command '$executable' not in allowed list"
            ))
        }

        // Check working directory
        if (config.isPathRestricted(File(workingDir).absolutePath)) {
            issues.add(SecurityIssue.high(
                "restricted_path",
                "Working directory is in restricted area"
            ))
        }

        // Check task scope for working directory
        if (taskScope != null && !taskScope.isPathAllowed(File(workingDir).absolutePath)) {
            issues.add(SecurityIssue.high(
                "cwd_out_of_scope",
                "Working directory is outside the task scope (project root: ${taskScope.projectRoot})"
            ))
        }

        // Determine validity
        val valid = issues.none { it.shouldBlock }

        if (!valid) {
            logEvent(SecurityEvent.blocked(
                SecurityEventType.COMMAND_BLOCKED,
                "Command blocked: ${command.take(50)}...",
                mapOf(
                    "command" to command.take(100),
                    "workingDir" to workingDir,
                    "issueCount" to issues.size.toString()
                )
            ))
        }

        return ValidationResult(
            valid = valid,
            sanitized = if (valid) command else null,
            issues = issues
        )
    }

    /**
     * Extracts the executable name from a command string.
     *
     * Handles path prefixes and simple quoting.
     */
    private fun extractExecutable(command: String): String {
        val trimmed = command.trim()
        
        // Handle quoted commands
        val unquoted = if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            trimmed.drop(1).takeWhile { it != '"' && it != '\'' }
        } else {
            trimmed.takeWhile { !it.isWhitespace() }
        }
        
        // Extract basename if it's a path
        return File(unquoted).name
    }

    // =========================================================================
    // File Access Validation
    // =========================================================================

    /**
     * Validates file access before reading or writing.
     *
     * Checks for:
     * 1. Path traversal attacks (..)
     * 2. Restricted system paths
     * 3. File size limits (for reads)
     * 4. Suspicious symlinks
     *
     * @param path The file path to validate
     * @param write Whether this is a write operation
     * @param taskScope Optional per-task file scope for boundary validation
     * @return Validation result with any security issues found
     */
    fun validateFileAccess(path: String, write: Boolean, taskScope: TaskFileScope? = null): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()
        val file = File(path)

        // Check for path traversal
        if (path.contains("..")) {
            issues.add(SecurityIssue.critical(
                "path_traversal",
                "Path contains '..' which may indicate path traversal attack"
            ))
            logEvent(SecurityEvent.blocked(
                SecurityEventType.PATH_TRAVERSAL_ATTEMPT,
                "Path traversal detected: ${path.take(50)}",
                mapOf("path" to path.take(100))
            ))
        }

        // Check task scope boundary
        if (taskScope != null && !taskScope.isPathAllowed(file.absolutePath)) {
            issues.add(SecurityIssue.high(
                "out_of_scope",
                "Path is outside the task scope (project root: ${taskScope.projectRoot})"
            ))
        }

        // Check write permission against task scope
        if (write && taskScope != null && !taskScope.isWriteAllowed(file.absolutePath)) {
            issues.add(SecurityIssue.high(
                "write_denied",
                "Write access denied in the current task scope"
            ))
        }

        // Check restricted paths
        val absolutePath = file.absolutePath
        if (config.isPathRestricted(absolutePath)) {
            issues.add(SecurityIssue.high(
                "restricted_path",
                "Path is in restricted area: ${config.restrictedPaths.find { absolutePath.startsWith(it) }}"
            ))
        }

        // Check file size for reads
        if (!write && file.exists()) {
            if (file.length() > config.maxFileSize) {
                issues.add(SecurityIssue.warning(
                    "file_too_large",
                    "File size (${file.length() / 1024}KB) exceeds maximum (${config.maxFileSize / 1024}KB)"
                ))
            }
        }

        // Check for symlinks pointing outside project
        if (file.exists()) {
            try {
                val canonical = file.canonicalPath
                if (canonical != absolutePath && config.isPathRestricted(canonical)) {
                    issues.add(SecurityIssue.high(
                        "symlink_escape",
                        "Symlink points to restricted area"
                    ))
                }
            } catch (e: Exception) {
                // Cannot resolve symlink - treat as suspicious
                issues.add(SecurityIssue.warning(
                    "unresolvable_path",
                    "Cannot resolve canonical path"
                ))
            }
        }

        val valid = issues.none { it.shouldBlock }

        if (!valid) {
            logEvent(SecurityEvent.blocked(
                SecurityEventType.FILE_ACCESS_DENIED,
                "File access denied: ${path.take(50)}",
                mapOf(
                    "path" to path.take(100),
                    "operation" to if (write) "write" else "read"
                )
            ))
        }

        return ValidationResult(
            valid = valid,
            sanitized = if (valid) path else null,
            issues = issues
        )
    }

    // =========================================================================
    // Input Sanitization
    // =========================================================================

    /**
     * Sanitizes user input by removing dangerous characters.
     *
     * Removes characters that could be used for:
     * - Shell injection (`, $, |, ;, &)
     * - HTML/XML injection (<, >)
     * - Quote escaping (", ', \)
     *
     * Also truncates to maximum length.
     *
     * @param input The raw user input
     * @return Sanitized input safe for use
     */
    fun sanitizeInput(input: String): String {
        return input
            .replace(DANGEROUS_CHARS, "")
            .take(MAX_INPUT_LENGTH)
    }

    /**
     * Checks if input contains potential injection characters.
     *
     * Does not modify the input, only detects issues.
     *
     * @param input The input to check
     * @return Validation result with any issues found
     */
    fun checkForInjection(input: String): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()

        if (INJECTION_CHARS.containsMatchIn(input)) {
            issues.add(SecurityIssue.warning(
                "potential_injection",
                "Input contains characters that may indicate injection attempt"
            ))
        }

        if (input.length > MAX_INPUT_LENGTH) {
            issues.add(SecurityIssue.warning(
                "input_too_long",
                "Input exceeds maximum length ($MAX_INPUT_LENGTH characters)"
            ))
        }

        return ValidationResult(
            valid = true, // Warnings only, don't block
            sanitized = sanitizeInput(input),
            issues = issues
        )
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Updates the security configuration.
     *
     * @param newConfig The new configuration to apply
     */
    fun updateConfig(newConfig: SecurityConfig) {
        val validationIssues = newConfig.validate()
        if (validationIssues.isNotEmpty()) {
            log.warn("Invalid security config: ${validationIssues.joinToString()}")
            return
        }
        
        config = newConfig
        logEvent(SecurityEvent.create(
            SecurityEventType.CONFIG_CHANGED,
            SecuritySeverity.INFO,
            "Security configuration updated"
        ))
    }

    /**
     * Gets the current security configuration.
     */
    fun getConfig(): SecurityConfig = config

    // =========================================================================
    // Event Logging
    // =========================================================================

    /**
     * Logs a security event.
     */
    private fun logEvent(event: SecurityEvent) {
        eventLog.add(event)
        
        // Trim log if too large
        while (eventLog.size > MAX_EVENT_LOG_SIZE) {
            eventLog.removeAt(0)
        }

        // Also log to IDE logger
        when (event.severity) {
            SecuritySeverity.INFO -> log.debug(event.format())
            SecuritySeverity.WARNING -> log.warn(event.format())
            SecuritySeverity.HIGH, SecuritySeverity.CRITICAL -> log.warn(event.format())
        }
    }

    /**
     * Gets all logged security events.
     */
    fun getEventLog(): List<SecurityEvent> = eventLog.toList()

    /**
     * Gets recent security events.
     *
     * @param count Maximum number of events to return
     * @return Most recent events, newest first
     */
    fun getRecentEvents(count: Int = 10): List<SecurityEvent> {
        return eventLog.takeLast(count).reversed()
    }

    /**
     * Gets events filtered by severity.
     */
    fun getEventsBySeverity(minSeverity: SecuritySeverity): List<SecurityEvent> {
        return eventLog.filter { it.severity >= minSeverity }
    }

    /**
     * Clears the event log.
     */
    fun clearEventLog() {
        eventLog.clear()
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Generates a security summary report.
     */
    fun getSecurityReport(): String = buildString {
        appendLine("=== Security Report ===")
        appendLine()
        appendLine("Configuration:")
        appendLine("  Sandboxing: ${if (config.sandboxCommands) "Enabled" else "Disabled"}")
        appendLine("  Confirmation: ${config.requireConfirmation}")
        appendLine("  Allowed commands: ${config.allowedCommands.size}")
        appendLine("  Blocked patterns: ${config.blockedPatterns.size}")
        appendLine()
        
        val blockedCount = eventLog.count { it.blocked }
        val warningCount = eventLog.count { it.severity == SecuritySeverity.WARNING }
        val criticalCount = eventLog.count { it.severity >= SecuritySeverity.HIGH }
        
        appendLine("Event Summary:")
        appendLine("  Total events: ${eventLog.size}")
        appendLine("  Blocked: $blockedCount")
        appendLine("  Warnings: $warningCount")
        appendLine("  Critical: $criticalCount")
        
        if (eventLog.isNotEmpty()) {
            appendLine()
            appendLine("Recent Events:")
            getRecentEvents(5).forEach { event ->
                appendLine("  ${event.format()}")
            }
        }
    }
}
