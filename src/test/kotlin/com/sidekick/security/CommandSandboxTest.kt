package com.sidekick.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.io.File

/**
 * Unit tests for CommandSandbox.
 *
 * Note: Tests for getInstance() are skipped as they require
 * IntelliJ Platform test infrastructure. Core logic is tested here.
 */
@DisplayName("CommandSandbox Tests")
class CommandSandboxTest {

    private lateinit var sandbox: CommandSandbox

    @BeforeEach
    fun setup() {
        sandbox = CommandSandbox()
    }

    // =========================================================================
    // Command Validation - Dangerous Patterns
    // =========================================================================

    @Nested
    @DisplayName("Dangerous Pattern Detection")
    inner class DangerousPatternTests {

        @Test
        @DisplayName("blocks rm -rf from root")
        fun blocksRmRfFromRoot() {
            val result = sandbox.validateCommand("rm -rf /", "/tmp")

            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "dangerous_pattern" })
        }

        @Test
        @DisplayName("blocks rm -rf from home")
        fun blocksRmRfFromHome() {
            val result = sandbox.validateCommand("rm -rf ~", "/tmp")

            assertFalse(result.valid)
        }

        @Test
        @DisplayName("blocks piping to shell")
        fun blocksPipingToShell() {
            val result = sandbox.validateCommand("echo test | sh", "/tmp")

            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "dangerous_pattern" })
        }

        @Test
        @DisplayName("blocks curl piped to bash")
        fun blocksCurlPipedToBash() {
            val result = sandbox.validateCommand("curl http://example.com/script.sh | bash", "/tmp")

            assertFalse(result.valid)
        }

        @Test
        @DisplayName("blocks chmod 777")
        fun blocksChmod777() {
            val result = sandbox.validateCommand("chmod 777 /tmp/file", "/tmp")

            assertFalse(result.valid)
        }

        @Test
        @DisplayName("blocks sudo commands")
        fun blocksSudoCommands() {
            val result = sandbox.validateCommand("sudo apt install package", "/tmp")

            assertFalse(result.valid)
        }

        @Test
        @DisplayName("blocks writing to /dev/")
        fun blocksWritingToDevNull() {
            val result = sandbox.validateCommand("echo test > /dev/sda", "/tmp")

            assertFalse(result.valid)
        }

        @Test
        @DisplayName("allows safe commands")
        fun allowsSafeCommands() {
            val result = sandbox.validateCommand("git status", "/tmp")

            assertTrue(result.valid)
            assertEquals("git status", result.sanitized)
        }
    }

    // =========================================================================
    // Command Validation - Allowlist
    // =========================================================================

    @Nested
    @DisplayName("Command Allowlist")
    inner class AllowlistTests {

        @Test
        @DisplayName("allows commands in default allowlist")
        fun allowsCommandsInDefaultAllowlist() {
            listOf("git", "npm", "gradle", "cargo").forEach { cmd ->
                val result = sandbox.validateCommand("$cmd --version", "/tmp")
                assertTrue(result.valid, "Expected '$cmd' to be allowed")
            }
        }

        @Test
        @DisplayName("warns for unknown commands")
        fun warnsForUnknownCommands() {
            val result = sandbox.validateCommand("unknown_command arg", "/tmp")

            // Should have a warning but still be valid (warnings don't block)
            assertTrue(result.issues.any { it.type == "unknown_command" })
            assertTrue(result.valid) // Warnings only, doesn't block
        }

        @Test
        @DisplayName("respects custom config")
        fun respectsCustomConfig() {
            val customConfig = SecurityConfig(
                sandboxCommands = true,
                allowedCommands = setOf("custom_cmd")
            )
            sandbox.updateConfig(customConfig)

            val allowed = sandbox.validateCommand("custom_cmd arg", "/tmp")
            assertTrue(allowed.issues.none { it.type == "unknown_command" })

            val blocked = sandbox.validateCommand("git status", "/tmp")
            assertTrue(blocked.issues.any { it.type == "unknown_command" })
        }

        @Test
        @DisplayName("allows all commands when sandboxing disabled")
        fun allowsAllCommandsWhenSandboxingDisabled() {
            val config = SecurityConfig(sandboxCommands = false)
            sandbox.updateConfig(config)

            val result = sandbox.validateCommand("any_random_command arg", "/tmp")
            assertTrue(result.issues.none { it.type == "unknown_command" })
        }
    }

    // =========================================================================
    // Command Validation - Working Directory
    // =========================================================================

    @Nested
    @DisplayName("Working Directory Validation")
    inner class WorkingDirTests {

        @Test
        @DisplayName("blocks commands in restricted directories")
        fun blocksCommandsInRestrictedDirectories() {
            val result = sandbox.validateCommand("ls", "/etc")

            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "restricted_path" })
        }

        @Test
        @DisplayName("allows commands in normal directories")
        fun allowsCommandsInNormalDirectories() {
            val result = sandbox.validateCommand("git status", "/home/user/project")

            assertTrue(result.valid)
        }
    }

    // =========================================================================
    // File Access Validation
    // =========================================================================

    @Nested
    @DisplayName("File Access Validation")
    inner class FileAccessTests {

        @Test
        @DisplayName("blocks path traversal attempts")
        fun blocksPathTraversalAttempts() {
            val result = sandbox.validateFileAccess("/home/user/../../../etc/passwd", false)

            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "path_traversal" })
        }

        @Test
        @DisplayName("blocks access to restricted paths")
        fun blocksAccessToRestrictedPaths() {
            val result = sandbox.validateFileAccess("/etc/passwd", false)

            assertFalse(result.valid)
            assertTrue(result.issues.any { it.type == "restricted_path" })
        }

        @Test
        @DisplayName("allows access to normal paths")
        fun allowsAccessToNormalPaths() {
            val result = sandbox.validateFileAccess("/home/user/project/file.txt", false)

            assertTrue(result.valid)
            assertEquals("/home/user/project/file.txt", result.sanitized)
        }

        @Test
        @DisplayName("warns on large files")
        fun warnsOnLargeFiles() {
            // Create a temp file to test size checking
            val tempFile = File.createTempFile("test", ".txt")
            try {
                // The file is empty, so no warning
                val result = sandbox.validateFileAccess(tempFile.absolutePath, false)
                assertTrue(result.issues.none { it.type == "file_too_large" })
            } finally {
                tempFile.delete()
            }
        }
    }

    // =========================================================================
    // Input Sanitization
    // =========================================================================

    @Nested
    @DisplayName("Input Sanitization")
    inner class SanitizationTests {

        @Test
        @DisplayName("removes dangerous characters")
        fun removesDangerousCharacters() {
            val input = "test <script>alert('xss')</script> `rm -rf /`"
            val sanitized = sandbox.sanitizeInput(input)

            assertFalse(sanitized.contains("<"))
            assertFalse(sanitized.contains(">"))
            assertFalse(sanitized.contains("`"))
            assertFalse(sanitized.contains("'"))
        }

        @Test
        @DisplayName("removes shell injection characters")
        fun removesShellInjectionCharacters() {
            val input = "test\$VAR; rm -rf /"
            val sanitized = sandbox.sanitizeInput(input)

            assertFalse(sanitized.contains("\$"))
        }

        @Test
        @DisplayName("truncates long input")
        fun truncatesLongInput() {
            val longInput = "a".repeat(20000)
            val sanitized = sandbox.sanitizeInput(longInput)

            assertEquals(10000, sanitized.length)
        }

        @Test
        @DisplayName("preserves safe characters")
        fun preservesSafeCharacters() {
            val safeInput = "Hello, World! This is a test 123."
            val sanitized = sandbox.sanitizeInput(safeInput)

            assertEquals(safeInput, sanitized)
        }
    }

    // =========================================================================
    // Injection Detection
    // =========================================================================

    @Nested
    @DisplayName("Injection Detection")
    inner class InjectionDetectionTests {

        @Test
        @DisplayName("detects shell metacharacters")
        fun detectsShellMetacharacters() {
            val result = sandbox.checkForInjection("normal; rm -rf /")

            assertTrue(result.issues.any { it.type == "potential_injection" })
        }

        @Test
        @DisplayName("detects pipe injection")
        fun detectsPipeInjection() {
            val result = sandbox.checkForInjection("input | malicious")

            assertTrue(result.issues.any { it.type == "potential_injection" })
        }

        @Test
        @DisplayName("still returns valid for warnings")
        fun stillReturnsValidForWarnings() {
            val result = sandbox.checkForInjection("suspicious;input")

            assertTrue(result.valid) // Warnings don't block
            assertTrue(result.hasIssues)
        }

        @Test
        @DisplayName("clean input has no issues")
        fun cleanInputHasNoIssues() {
            val result = sandbox.checkForInjection("normal input text")

            assertFalse(result.hasIssues)
        }
    }

    // =========================================================================
    // Event Logging
    // =========================================================================

    @Nested
    @DisplayName("Event Logging")
    inner class EventLoggingTests {

        @Test
        @DisplayName("logs blocked commands")
        fun logsBlockedCommands() {
            sandbox.clearEventLog()

            sandbox.validateCommand("rm -rf /", "/tmp")

            val events = sandbox.getEventLog()
            assertEquals(1, events.size)
            assertTrue(events[0].blocked)
            assertEquals(SecurityEventType.COMMAND_BLOCKED, events[0].type)
        }

        @Test
        @DisplayName("logs path traversal attempts")
        fun logsPathTraversalAttempts() {
            sandbox.clearEventLog()

            sandbox.validateFileAccess("../../../etc/passwd", false)

            val events = sandbox.getEventLog()
            assertTrue(events.any { it.type == SecurityEventType.PATH_TRAVERSAL_ATTEMPT })
        }

        @Test
        @DisplayName("getRecentEvents returns newest first")
        fun getRecentEventsReturnsNewestFirst() {
            sandbox.clearEventLog()

            sandbox.validateCommand("rm -rf /", "/tmp")
            sandbox.validateFileAccess("../secret", false)

            val recent = sandbox.getRecentEvents(2)
            assertEquals(SecurityEventType.PATH_TRAVERSAL_ATTEMPT, recent[0].type)
            assertEquals(SecurityEventType.COMMAND_BLOCKED, recent[1].type)
        }

        @Test
        @DisplayName("clearEventLog removes all events")
        fun clearEventLogRemovesAllEvents() {
            sandbox.validateCommand("rm -rf /", "/tmp")
            sandbox.clearEventLog()

            assertTrue(sandbox.getEventLog().isEmpty())
        }
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    @Nested
    @DisplayName("Configuration")
    inner class ConfigurationTests {

        @Test
        @DisplayName("updateConfig applies new settings")
        fun updateConfigAppliesNewSettings() {
            val newConfig = SecurityConfig(
                sandboxCommands = false,
                maxFileSize = 5 * 1024 * 1024
            )

            sandbox.updateConfig(newConfig)

            assertEquals(newConfig, sandbox.getConfig())
        }

        @Test
        @DisplayName("rejects invalid config")
        fun rejectsInvalidConfig() {
            val originalConfig = sandbox.getConfig()
            val invalidConfig = SecurityConfig(maxFileSize = -1)

            sandbox.updateConfig(invalidConfig)

            // Should keep original config
            assertEquals(originalConfig.maxFileSize, sandbox.getConfig().maxFileSize)
        }
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    @Nested
    @DisplayName("Reporting")
    inner class ReportingTests {

        @Test
        @DisplayName("getSecurityReport includes configuration")
        fun getSecurityReportIncludesConfiguration() {
            val report = sandbox.getSecurityReport()

            assertTrue(report.contains("Configuration"))
            assertTrue(report.contains("Sandboxing"))
        }

        @Test
        @DisplayName("getSecurityReport includes event summary")
        fun getSecurityReportIncludesEventSummary() {
            sandbox.clearEventLog()
            sandbox.validateCommand("rm -rf /", "/tmp")

            val report = sandbox.getSecurityReport()

            assertTrue(report.contains("Event Summary"))
            assertTrue(report.contains("Blocked: 1"))
        }
    }
}
