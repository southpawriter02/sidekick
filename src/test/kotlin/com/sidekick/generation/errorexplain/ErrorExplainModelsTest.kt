// =============================================================================
// ErrorExplainModelsTest.kt
// =============================================================================
// Unit tests for error explanation data models.
// =============================================================================

package com.sidekick.generation.errorexplain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for ErrorExplainModels.
 */
class ErrorExplainModelsTest {

    @Nested
    @DisplayName("ErrorContext")
    inner class ErrorContextTests {
        
        @Test
        @DisplayName("isValid returns true when message present")
        fun `isValid returns true when message present`() {
            val context = ErrorContext(
                message = "Undefined variable 'x'",
                source = ErrorSource.COMPILER
            )
            assertTrue(context.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for blank message")
        fun `isValid returns false for blank message`() {
            val context = ErrorContext(message = "", source = ErrorSource.UNKNOWN)
            assertFalse(context.isValid())
        }
        
        @Test
        @DisplayName("format includes error code when present")
        fun `format includes error code when present`() {
            val context = ErrorContext(
                message = "Cannot convert type",
                source = ErrorSource.COMPILER,
                errorCode = "CS0029"
            )
            val formatted = context.format()
            assertTrue("[CS0029]" in formatted)
        }
        
        @Test
        @DisplayName("format includes file and line when present")
        fun `format includes file and line when present`() {
            val context = ErrorContext(
                message = "Error",
                source = ErrorSource.COMPILER,
                filePath = "/path/to/file.kt",
                lineNumber = 42
            )
            val formatted = context.format()
            assertTrue("file.kt:42" in formatted)
        }
        
        @Test
        @DisplayName("summary truncates long messages")
        fun `summary truncates long messages`() {
            val longMessage = "A".repeat(100)
            val context = ErrorContext(message = longMessage, source = ErrorSource.UNKNOWN)
            val summary = context.summary()
            assertTrue(summary.length <= 83)
            assertTrue(summary.endsWith("..."))
        }
        
        @Test
        @DisplayName("fromMessage extracts error code")
        fun `fromMessage extracts error code`() {
            val context = ErrorContext.fromMessage("error CS0029: Cannot convert type")
            assertEquals("CS0029", context.errorCode)
        }
        
        @Test
        @DisplayName("fromCompilerOutput extracts line number")
        fun `fromCompilerOutput extracts line number`() {
            val output = "file.kt:42:10: error: undefined reference"
            val context = ErrorContext.fromCompilerOutput(output)
            assertEquals(42, context.lineNumber)
        }
    }

    @Nested
    @DisplayName("ErrorSource")
    inner class ErrorSourceTests {
        
        @Test
        @DisplayName("detect identifies compiler errors")
        fun `detect identifies compiler errors`() {
            assertEquals(ErrorSource.COMPILER, ErrorSource.detect("compile error: cannot build"))
            assertEquals(ErrorSource.COMPILER, ErrorSource.detect("syntax check failed"))
        }
        
        @Test
        @DisplayName("detect identifies runtime errors")
        fun `detect identifies runtime errors`() {
            assertEquals(ErrorSource.RUNTIME, ErrorSource.detect("RuntimeException"))
            assertEquals(ErrorSource.RUNTIME, ErrorSource.detect("Exception in thread"))
        }
        
        @Test
        @DisplayName("detect identifies linter errors")
        fun `detect identifies linter errors`() {
            assertEquals(ErrorSource.LINTER, ErrorSource.detect("ESLint error"))
            assertEquals(ErrorSource.LINTER, ErrorSource.detect("ktlint warning"))
        }
        
        @Test
        @DisplayName("detect returns UNKNOWN for unrecognized")
        fun `detect returns UNKNOWN for unrecognized`() {
            assertEquals(ErrorSource.UNKNOWN, ErrorSource.detect("some error"))
        }
    }

    @Nested
    @DisplayName("ErrorCategory")
    inner class ErrorCategoryTests {
        
        @Test
        @DisplayName("detect identifies syntax errors")
        fun `detect identifies syntax errors`() {
            assertEquals(ErrorCategory.SYNTAX, ErrorCategory.detect("Syntax error: unexpected token"))
            assertEquals(ErrorCategory.SYNTAX, ErrorCategory.detect("expected ';' before '}'"))
        }
        
        @Test
        @DisplayName("detect identifies type mismatch")
        fun `detect identifies type mismatch`() {
            assertEquals(ErrorCategory.TYPE_MISMATCH, ErrorCategory.detect("Type mismatch: expected Int got String"))
            assertEquals(ErrorCategory.TYPE_MISMATCH, ErrorCategory.detect("incompatible type assignment"))
        }
        
        @Test
        @DisplayName("detect identifies null reference")
        fun `detect identifies null reference`() {
            assertEquals(ErrorCategory.NULL_REFERENCE, ErrorCategory.detect("NullPointerException"))
            assertEquals(ErrorCategory.NULL_REFERENCE, ErrorCategory.detect("Cannot read property of null"))
        }
        
        @Test
        @DisplayName("detect identifies undefined reference")
        fun `detect identifies undefined reference`() {
            assertEquals(ErrorCategory.UNDEFINED_REFERENCE, ErrorCategory.detect("symbol does not exist in scope"))
            assertEquals(ErrorCategory.UNDEFINED_REFERENCE, ErrorCategory.detect("reference 'x' cannot find symbol"))
        }
        
        @Test
        @DisplayName("detect identifies missing import")
        fun `detect identifies missing import`() {
            assertEquals(ErrorCategory.MISSING_IMPORT, ErrorCategory.detect("import statement failed"))
            assertEquals(ErrorCategory.MISSING_IMPORT, ErrorCategory.detect("cannot resolve symbol: add import"))
        }
        
        @Test
        @DisplayName("detect identifies access violation")
        fun `detect identifies access violation`() {
            assertEquals(ErrorCategory.ACCESS_VIOLATION, ErrorCategory.detect("Cannot access private member"))
        }
        
        @Test
        @DisplayName("detect returns OTHER for unrecognized")
        fun `detect returns OTHER for unrecognized`() {
            assertEquals(ErrorCategory.OTHER, ErrorCategory.detect("something went wrong"))
        }
        
        @Test
        @DisplayName("commonCauses is populated for known categories")
        fun `commonCauses is populated for known categories`() {
            assertTrue(ErrorCategory.SYNTAX.commonCauses.isNotEmpty())
            assertTrue(ErrorCategory.NULL_REFERENCE.commonCauses.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("ErrorExplanation")
    inner class ErrorExplanationTests {
        
        @Test
        @DisplayName("bestFix returns highest confidence fix")
        fun `bestFix returns highest confidence fix`() {
            val explanation = ErrorExplanation(
                summary = "Error",
                details = "Details",
                category = ErrorCategory.SYNTAX,
                fixes = listOf(
                    FixSuggestion("Low", "", 0.3),
                    FixSuggestion("High", "", 0.9),
                    FixSuggestion("Medium", "", 0.6)
                )
            )
            
            assertEquals("High", explanation.bestFix()?.description)
        }
        
        @Test
        @DisplayName("formatForDisplay includes all sections")
        fun `formatForDisplay includes all sections`() {
            val explanation = ErrorExplanation(
                summary = "Brief explanation",
                details = "Detailed explanation",
                category = ErrorCategory.NULL_REFERENCE,
                fixes = listOf(FixSuggestion("Add null check"))
            )
            
            val formatted = explanation.formatForDisplay()
            assertTrue("Null Reference" in formatted)
            assertTrue("Brief explanation" in formatted)
            assertTrue("Add null check" in formatted)
        }
        
        @Test
        @DisplayName("simple creates basic explanation")
        fun `simple creates basic explanation`() {
            val explanation = ErrorExplanation.simple(
                ErrorCategory.TYPE_MISMATCH,
                "Cannot convert String to Int"
            )
            
            assertEquals(ErrorCategory.TYPE_MISMATCH, explanation.category)
            assertTrue(explanation.fixes.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("FixSuggestion")
    inner class FixSuggestionTests {
        
        @Test
        @DisplayName("confident creates high confidence fix")
        fun `confident creates high confidence fix`() {
            val fix = FixSuggestion.confident("Add null check", "if (x != null)")
            assertEquals(0.9, fix.confidence)
            assertEquals("if (x != null)", fix.codeSnippet)
        }
        
        @Test
        @DisplayName("likely creates medium confidence fix")
        fun `likely creates medium confidence fix`() {
            val fix = FixSuggestion.likely("Check spelling")
            assertEquals(0.7, fix.confidence)
        }
    }

    @Nested
    @DisplayName("ErrorExplainResult")
    inner class ErrorExplainResultTests {
        
        @Test
        @DisplayName("success creates successful result")
        fun `success creates successful result`() {
            val explanation = ErrorExplanation.simple(ErrorCategory.SYNTAX, "Error")
            val context = ErrorContext.fromMessage("Syntax error")
            
            val result = ErrorExplainResult.success(explanation, context)
            
            assertTrue(result.success)
            assertNotNull(result.explanation)
            assertNull(result.error)
        }
        
        @Test
        @DisplayName("failure creates failed result")
        fun `failure creates failed result`() {
            val context = ErrorContext.fromMessage("")
            val result = ErrorExplainResult.failure("No error message", context)
            
            assertFalse(result.success)
            assertNull(result.explanation)
            assertEquals("No error message", result.error)
        }
    }
}
