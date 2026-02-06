package com.sidekick.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.time.Instant

/**
 * Comprehensive unit tests for Security Models.
 */
@DisplayName("Security Models Tests")
class SecurityModelsTest {

    // =========================================================================
    // SecurityConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("SecurityConfig")
    inner class SecurityConfigTests {

        @Test
        @DisplayName("default values are secure")
        fun defaultValuesAreSecure() {
            val config = SecurityConfig()

            assertTrue(config.sandboxCommands)
            assertEquals(ConfirmationLevel.DESTRUCTIVE, config.requireConfirmation)
            assertTrue(config.restrictedPaths.isNotEmpty())
            assertTrue(config.blockedPatterns.isNotEmpty())
        }

        @Test
        @DisplayName("isCommandAllowed respects sandbox setting")
        fun isCommandAllowedRespectsSandboxSetting() {
            val sandboxed = SecurityConfig(sandboxCommands = true, allowedCommands = setOf("git"))
            val unsandboxed = SecurityConfig(sandboxCommands = false)

            assertTrue(sandboxed.isCommandAllowed("git"))
            assertFalse(sandboxed.isCommandAllowed("rm"))

            assertTrue(unsandboxed.isCommandAllowed("anything"))
        }

        @Test
        @DisplayName("isPathRestricted detects system paths")
        fun isPathRestrictedDetectsSystemPaths() {
            val config = SecurityConfig(restrictedPaths = setOf("/etc", "/usr"))

            assertTrue(config.isPathRestricted("/etc/passwd"))
            assertTrue(config.isPathRestricted("/usr/bin/sh"))
            assertFalse(config.isPathRestricted("/home/user/project"))
        }

        @Test
        @DisplayName("findBlockedPattern detects blocked patterns")
        fun findBlockedPatternDetectsBlockedPatterns() {
            val config = SecurityConfig(blockedPatterns = setOf("rm -rf", "sudo"))

            assertEquals("rm -rf", config.findBlockedPattern("rm -rf /"))
            assertEquals("sudo", config.findBlockedPattern("sudo apt install"))
            assertNull(config.findBlockedPattern("git status"))
        }

        @Test
        @DisplayName("harden creates more restrictive config")
        fun hardenCreatesMoreRestrictiveConfig() {
            val hardened = SecurityConfig().harden()

            assertTrue(hardened.sandboxCommands)
            assertEquals(ConfirmationLevel.ALL, hardened.requireConfirmation)
            assertTrue(hardened.maxFileSize < SecurityConfig().maxFileSize)
        }

        @Test
        @DisplayName("relax creates less restrictive config")
        fun relaxCreatesLessRestrictiveConfig() {
            val relaxed = SecurityConfig().relax()

            assertFalse(relaxed.sandboxCommands)
            assertEquals(ConfirmationLevel.DESTRUCTIVE, relaxed.requireConfirmation)
            assertTrue(relaxed.maxFileSize > SecurityConfig().maxFileSize)
        }

        @Test
        @DisplayName("validate detects invalid maxFileSize")
        fun validateDetectsInvalidMaxFileSize() {
            val config = SecurityConfig(maxFileSize = -1)
            val issues = config.validate()

            assertTrue(issues.any { it.contains("maxFileSize") })
        }

        @Test
        @DisplayName("validate detects empty allowedCommands when sandboxed")
        fun validateDetectsEmptyAllowedCommandsWhenSandboxed() {
            val config = SecurityConfig(sandboxCommands = true, allowedCommands = emptySet())
            val issues = config.validate()

            assertTrue(issues.any { it.contains("allowedCommands") })
        }

        @Test
        @DisplayName("PERMISSIVE has all protections disabled")
        fun permissiveHasAllProtectionsDisabled() {
            val config = SecurityConfig.PERMISSIVE

            assertFalse(config.sandboxCommands)
            assertTrue(config.blockedPatterns.isEmpty())
            assertTrue(config.restrictedPaths.isEmpty())
            assertEquals(ConfirmationLevel.NONE, config.requireConfirmation)
        }
    }

    // =========================================================================
    // ConfirmationLevel Tests
    // =========================================================================

    @Nested
    @DisplayName("ConfirmationLevel")
    inner class ConfirmationLevelTests {

        @Test
        @DisplayName("NONE never requires confirmation")
        fun noneNeverRequiresConfirmation() {
            assertFalse(ConfirmationLevel.NONE.requiresConfirmation(false))
            assertFalse(ConfirmationLevel.NONE.requiresConfirmation(true))
        }

        @Test
        @DisplayName("DESTRUCTIVE requires confirmation only for destructive ops")
        fun destructiveRequiresConfirmationOnlyForDestructiveOps() {
            assertFalse(ConfirmationLevel.DESTRUCTIVE.requiresConfirmation(false))
            assertTrue(ConfirmationLevel.DESTRUCTIVE.requiresConfirmation(true))
        }

        @Test
        @DisplayName("ALL always requires confirmation")
        fun allAlwaysRequiresConfirmation() {
            assertTrue(ConfirmationLevel.ALL.requiresConfirmation(false))
            assertTrue(ConfirmationLevel.ALL.requiresConfirmation(true))
        }
    }

    // =========================================================================
    // SecuritySeverity Tests
    // =========================================================================

    @Nested
    @DisplayName("SecuritySeverity")
    inner class SecuritySeverityTests {

        @Test
        @DisplayName("levels are ordered correctly")
        fun levelsAreOrderedCorrectly() {
            assertTrue(SecuritySeverity.INFO.level < SecuritySeverity.WARNING.level)
            assertTrue(SecuritySeverity.WARNING.level < SecuritySeverity.HIGH.level)
            assertTrue(SecuritySeverity.HIGH.level < SecuritySeverity.CRITICAL.level)
        }

        @Test
        @DisplayName("shouldBlock is true for HIGH and CRITICAL")
        fun shouldBlockIsTrueForHighAndCritical() {
            assertFalse(SecuritySeverity.INFO.shouldBlock)
            assertFalse(SecuritySeverity.WARNING.shouldBlock)
            assertTrue(SecuritySeverity.HIGH.shouldBlock)
            assertTrue(SecuritySeverity.CRITICAL.shouldBlock)
        }

        @Test
        @DisplayName("max returns highest severity")
        fun maxReturnsHighestSeverity() {
            val mixed = listOf(SecuritySeverity.INFO, SecuritySeverity.HIGH, SecuritySeverity.WARNING)
            assertEquals(SecuritySeverity.HIGH, SecuritySeverity.max(mixed))
        }

        @Test
        @DisplayName("max returns INFO for empty list")
        fun maxReturnsInfoForEmptyList() {
            assertEquals(SecuritySeverity.INFO, SecuritySeverity.max(emptyList()))
        }

        @Test
        @DisplayName("all severities have icons")
        fun allSeveritiesHaveIcons() {
            SecuritySeverity.entries.forEach { severity ->
                assertTrue(severity.icon.isNotBlank())
            }
        }
    }

    // =========================================================================
    // SecurityEventType Tests
    // =========================================================================

    @Nested
    @DisplayName("SecurityEventType")
    inner class SecurityEventTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            SecurityEventType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("all types have log levels")
        fun allTypesHaveLogLevels() {
            SecurityEventType.entries.forEach { type ->
                assertTrue(type.logLevel.isNotBlank())
            }
        }
    }

    // =========================================================================
    // SecurityEvent Tests
    // =========================================================================

    @Nested
    @DisplayName("SecurityEvent")
    inner class SecurityEventTests {

        @Test
        @DisplayName("create generates unique IDs")
        fun createGeneratesUniqueIds() {
            val event1 = SecurityEvent.create(SecurityEventType.COMMAND_BLOCKED, SecuritySeverity.HIGH, "test")
            val event2 = SecurityEvent.create(SecurityEventType.COMMAND_BLOCKED, SecuritySeverity.HIGH, "test")

            assertNotEquals(event1.id, event2.id)
        }

        @Test
        @DisplayName("create sets timestamp")
        fun createSetsTimestamp() {
            val before = Instant.now()
            val event = SecurityEvent.create(SecurityEventType.COMMAND_BLOCKED, SecuritySeverity.HIGH, "test")
            val after = Instant.now()

            assertTrue(event.timestamp >= before)
            assertTrue(event.timestamp <= after)
        }

        @Test
        @DisplayName("blocked factory creates blocked event")
        fun blockedFactoryCreatesBlockedEvent() {
            val event = SecurityEvent.blocked(SecurityEventType.FILE_ACCESS_DENIED, "test")

            assertTrue(event.blocked)
            assertEquals(SecuritySeverity.HIGH, event.severity)
        }

        @Test
        @DisplayName("format produces readable output")
        fun formatProducesReadableOutput() {
            val event = SecurityEvent.blocked(SecurityEventType.COMMAND_BLOCKED, "rm -rf /")

            val formatted = event.format()
            assertTrue(formatted.contains("BLOCKED"))
            assertTrue(formatted.contains("Command Blocked"))
        }

        @Test
        @DisplayName("isUserVisible is true for warnings and blocked events")
        fun isUserVisibleIsTrueForWarningsAndBlockedEvents() {
            val blocked = SecurityEvent.blocked(SecurityEventType.COMMAND_BLOCKED, "test")
            val warning = SecurityEvent.create(SecurityEventType.SUSPICIOUS_PATTERN, SecuritySeverity.WARNING, "test")
            val info = SecurityEvent.create(SecurityEventType.VALIDATION_PASSED, SecuritySeverity.INFO, "test")

            assertTrue(blocked.isUserVisible)
            assertTrue(warning.isUserVisible)
            assertFalse(info.isUserVisible)
        }
    }

    // =========================================================================
    // SecurityIssue Tests
    // =========================================================================

    @Nested
    @DisplayName("SecurityIssue")
    inner class SecurityIssueTests {

        @Test
        @DisplayName("shouldBlock reflects severity")
        fun shouldBlockReflectsSeverity() {
            assertFalse(SecurityIssue.info("test", "desc").shouldBlock)
            assertFalse(SecurityIssue.warning("test", "desc").shouldBlock)
            assertTrue(SecurityIssue.high("test", "desc").shouldBlock)
            assertTrue(SecurityIssue.critical("test", "desc").shouldBlock)
        }

        @Test
        @DisplayName("factory methods set correct severity")
        fun factoryMethodsSetCorrectSeverity() {
            assertEquals(SecuritySeverity.INFO, SecurityIssue.info("t", "d").severity)
            assertEquals(SecuritySeverity.WARNING, SecurityIssue.warning("t", "d").severity)
            assertEquals(SecuritySeverity.HIGH, SecurityIssue.high("t", "d").severity)
            assertEquals(SecuritySeverity.CRITICAL, SecurityIssue.critical("t", "d").severity)
        }

        @Test
        @DisplayName("format includes type and description")
        fun formatIncludesTypeAndDescription() {
            val issue = SecurityIssue("test_type", "Test description", SecuritySeverity.WARNING)
            val formatted = issue.format()

            assertTrue(formatted.contains("test_type"))
            assertTrue(formatted.contains("Test description"))
        }
    }

    // =========================================================================
    // ValidationResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ValidationResult")
    inner class ValidationResultTests {

        @Test
        @DisplayName("valid factory creates valid result")
        fun validFactoryCreatesValidResult() {
            val result = ValidationResult.valid("clean input")

            assertTrue(result.valid)
            assertEquals("clean input", result.sanitized)
            assertTrue(result.issues.isEmpty())
        }

        @Test
        @DisplayName("invalid factory creates invalid result")
        fun invalidFactoryCreatesInvalidResult() {
            val issues = listOf(SecurityIssue.high("test", "blocked"))
            val result = ValidationResult.invalid(issues)

            assertFalse(result.valid)
            assertNull(result.sanitized)
            assertEquals(1, result.issues.size)
        }

        @Test
        @DisplayName("blocked factory creates single-issue result")
        fun blockedFactoryCreatesSingleIssueResult() {
            val result = ValidationResult.blocked("type", "desc")

            assertFalse(result.valid)
            assertEquals(1, result.issues.size)
            assertEquals("type", result.issues[0].type)
        }

        @Test
        @DisplayName("maxSeverity returns highest issue severity")
        fun maxSeverityReturnsHighestIssueSeverity() {
            val result = ValidationResult(
                valid = false,
                sanitized = null,
                issues = listOf(
                    SecurityIssue.warning("w", "warning"),
                    SecurityIssue.critical("c", "critical"),
                    SecurityIssue.info("i", "info")
                )
            )

            assertEquals(SecuritySeverity.CRITICAL, result.maxSeverity)
        }

        @Test
        @DisplayName("blockingIssues filters to blocking only")
        fun blockingIssuesFiltersToBlockingOnly() {
            val result = ValidationResult(
                valid = false,
                sanitized = null,
                issues = listOf(
                    SecurityIssue.warning("w", "warning"),
                    SecurityIssue.high("h", "high"),
                    SecurityIssue.info("i", "info")
                )
            )

            assertEquals(1, result.blockingIssues.size)
            assertEquals("h", result.blockingIssues[0].type)
        }

        @Test
        @DisplayName("hasIssues is true when issues exist")
        fun hasIssuesIsTrueWhenIssuesExist() {
            val withIssues = ValidationResult.blocked("t", "d")
            val noIssues = ValidationResult.valid("clean")

            assertTrue(withIssues.hasIssues)
            assertFalse(noIssues.hasIssues)
        }

        @Test
        @DisplayName("validWithWarnings keeps non-blocking issues")
        fun validWithWarningsKeepsNonBlockingIssues() {
            val issues = listOf(
                SecurityIssue.warning("w", "warning"),
                SecurityIssue.high("h", "high") // Should be filtered
            )
            val result = ValidationResult.validWithWarnings("clean", issues)

            assertTrue(result.valid)
            assertEquals(1, result.issues.size)
            assertEquals("w", result.issues[0].type)
        }
    }
}
