// =============================================================================
// ErrorExplainModels.kt
// =============================================================================
// Data models for error message explanation.
//
// This includes:
// - ErrorContext - captured error information
// - ErrorCategory - type of error
// - ErrorExplanation - LLM-generated explanation
// - FixSuggestion - proposed fix
//
// DESIGN NOTES:
// - Supports multiple error sources (compiler, runtime, linter)
// - Language-aware explanations
// - Actionable fix suggestions
// =============================================================================

package com.sidekick.generation.errorexplain

/**
 * Captured error information for explanation.
 *
 * @property message Original error message
 * @property source Where the error came from (compiler, runtime, etc.)
 * @property errorCode Error code if available (e.g., CS0001, E0001)
 * @property filePath File where error occurred
 * @property lineNumber Line number of error
 * @property language Programming language
 * @property surroundingCode Code context around the error
 */
data class ErrorContext(
    val message: String,
    val source: ErrorSource,
    val errorCode: String? = null,
    val filePath: String? = null,
    val lineNumber: Int? = null,
    val language: String = "",
    val surroundingCode: String = ""
) {
    /**
     * Whether this error has enough context for explanation.
     */
    fun isValid(): Boolean {
        return message.isNotBlank()
    }
    
    /**
     * Formats the error for display.
     */
    fun format(): String = buildString {
        if (errorCode != null) {
            append("[$errorCode] ")
        }
        append(message)
        if (filePath != null && lineNumber != null) {
            append(" at ${filePath.substringAfterLast("/")}:$lineNumber")
        }
    }
    
    /**
     * Gets a short summary for display.
     */
    fun summary(): String {
        val shortMessage = if (message.length > 80) {
            message.take(77) + "..."
        } else {
            message
        }
        return if (errorCode != null) "[$errorCode] $shortMessage" else shortMessage
    }
    
    companion object {
        /**
         * Creates an error context from a simple message.
         */
        fun fromMessage(message: String, language: String = ""): ErrorContext {
            val category = ErrorCategory.detect(message)
            return ErrorContext(
                message = message,
                source = ErrorSource.UNKNOWN,
                errorCode = extractErrorCode(message),
                language = language
            )
        }
        
        /**
         * Creates an error context from compiler output.
         */
        fun fromCompilerOutput(
            output: String,
            filePath: String? = null,
            language: String = ""
        ): ErrorContext {
            val lines = output.lines()
            val errorLine = lines.firstOrNull { it.contains("error", ignoreCase = true) } ?: output
            val lineNumber = extractLineNumber(output)
            
            return ErrorContext(
                message = errorLine.trim(),
                source = ErrorSource.COMPILER,
                errorCode = extractErrorCode(output),
                filePath = filePath,
                lineNumber = lineNumber,
                language = language
            )
        }
        
        private fun extractErrorCode(text: String): String? {
            // Common error code patterns: CS0001, E0001, TS2304, error[E0001]
            val patterns = listOf(
                Regex("""([A-Z]{2,}\d+)"""),
                Regex("""error\[([A-Z]\d+)]"""),
                Regex("""([A-Z]+_[A-Z_]+)""")
            )
            
            for (pattern in patterns) {
                pattern.find(text)?.let { return it.groupValues[1] }
            }
            return null
        }
        
        private fun extractLineNumber(text: String): Int? {
            // Common patterns: :42:, line 42, (42,
            val patterns = listOf(
                Regex(""":(\d+):"""),
                Regex("""line\s+(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""\((\d+),""")
            )
            
            for (pattern in patterns) {
                pattern.find(text)?.let { return it.groupValues[1].toIntOrNull() }
            }
            return null
        }
    }
}

/**
 * Source of an error.
 */
enum class ErrorSource(val displayName: String) {
    COMPILER("Compiler"),
    RUNTIME("Runtime"),
    LINTER("Linter"),
    TYPE_CHECKER("Type Checker"),
    BUILD_TOOL("Build Tool"),
    TEST_RUNNER("Test Runner"),
    UNKNOWN("Unknown");
    
    companion object {
        fun detect(message: String): ErrorSource {
            val lower = message.lowercase()
            return when {
                "compile" in lower || "syntax" in lower -> COMPILER
                "runtime" in lower || "exception" in lower -> RUNTIME
                "lint" in lower || "eslint" in lower || "ktlint" in lower -> LINTER
                "type" in lower && "error" in lower -> TYPE_CHECKER
                "gradle" in lower || "maven" in lower || "npm" in lower -> BUILD_TOOL
                "test" in lower && "fail" in lower -> TEST_RUNNER
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Category of an error.
 */
enum class ErrorCategory(
    val displayName: String,
    val description: String,
    val commonCauses: List<String>
) {
    SYNTAX(
        displayName = "Syntax Error",
        description = "Invalid code structure",
        commonCauses = listOf(
            "Missing semicolon or bracket",
            "Typo in keyword",
            "Invalid character"
        )
    ),
    
    TYPE_MISMATCH(
        displayName = "Type Mismatch",
        description = "Incompatible types",
        commonCauses = listOf(
            "Wrong argument type",
            "Incompatible assignment",
            "Missing type conversion"
        )
    ),
    
    NULL_REFERENCE(
        displayName = "Null Reference",
        description = "Accessing a null value",
        commonCauses = listOf(
            "Uninitialized variable",
            "Missing null check",
            "Unexpected null return"
        )
    ),
    
    UNDEFINED_REFERENCE(
        displayName = "Undefined Reference",
        description = "Symbol not found",
        commonCauses = listOf(
            "Typo in name",
            "Missing import",
            "Out of scope"
        )
    ),
    
    MISSING_IMPORT(
        displayName = "Missing Import",
        description = "Required import not present",
        commonCauses = listOf(
            "Forgot to import",
            "Wrong package name",
            "Missing dependency"
        )
    ),
    
    ACCESS_VIOLATION(
        displayName = "Access Violation",
        description = "Invalid access to member",
        commonCauses = listOf(
            "Private member access",
            "Protected visibility",
            "Internal/friend access"
        )
    ),
    
    CONCURRENCY(
        displayName = "Concurrency Error",
        description = "Threading or async issue",
        commonCauses = listOf(
            "Race condition",
            "Deadlock potential",
            "Missing synchronization"
        )
    ),
    
    RESOURCE(
        displayName = "Resource Error",
        description = "Resource handling issue",
        commonCauses = listOf(
            "Resource leak",
            "File not found",
            "Connection failure"
        )
    ),
    
    CONFIGURATION(
        displayName = "Configuration Error",
        description = "Configuration or setup issue",
        commonCauses = listOf(
            "Missing configuration",
            "Invalid settings",
            "Environment variable"
        )
    ),
    
    OTHER(
        displayName = "Other",
        description = "Uncategorized error",
        commonCauses = emptyList()
    );
    
    companion object {
        /**
         * Detects the error category from the message.
         */
        fun detect(message: String): ErrorCategory {
            val lower = message.lowercase()
            
            return when {
                // Syntax errors
                "syntax" in lower || "unexpected token" in lower || 
                "expected" in lower && (";" in lower || ")" in lower || "}" in lower) -> SYNTAX
                
                // Type errors
                "type" in lower && ("mismatch" in lower || "cannot convert" in lower) ||
                "incompatible type" in lower -> TYPE_MISMATCH
                
                // Null errors
                "null" in lower || "nil" in lower || 
                "undefined" in lower && "not" in lower -> NULL_REFERENCE
                
                // Undefined symbol
                "undefined" in lower || "not found" in lower || 
                "cannot find" in lower || "does not exist" in lower -> UNDEFINED_REFERENCE
                
                // Import errors
                "import" in lower || "module" in lower && "not found" in lower ||
                "cannot resolve" in lower -> MISSING_IMPORT
                
                // Access errors
                "private" in lower || "protected" in lower || 
                "inaccessible" in lower || "access" in lower -> ACCESS_VIOLATION
                
                // Concurrency
                "thread" in lower || "async" in lower || 
                "deadlock" in lower || "race" in lower -> CONCURRENCY
                
                // Resource
                "file" in lower && "not found" in lower ||
                "resource" in lower || "connection" in lower -> RESOURCE
                
                // Configuration
                "config" in lower || "setting" in lower ||
                "environment" in lower -> CONFIGURATION
                
                else -> OTHER
            }
        }
    }
}

/**
 * LLM-generated explanation of an error.
 *
 * @property summary Brief explanation
 * @property details Detailed explanation
 * @property category Detected error category
 * @property fixes Suggested fixes
 * @property relatedDocs Links to relevant documentation
 */
data class ErrorExplanation(
    val summary: String,
    val details: String,
    val category: ErrorCategory,
    val fixes: List<FixSuggestion>,
    val relatedDocs: List<String> = emptyList()
) {
    /**
     * Gets the best fix suggestion.
     */
    fun bestFix(): FixSuggestion? = fixes.maxByOrNull { it.confidence }
    
    /**
     * Formats for display in IDE.
     */
    fun formatForDisplay(): String = buildString {
        appendLine("## ${category.displayName}")
        appendLine()
        appendLine(summary)
        appendLine()
        if (details.isNotBlank()) {
            appendLine("### Details")
            appendLine(details)
            appendLine()
        }
        if (fixes.isNotEmpty()) {
            appendLine("### Suggested Fixes")
            fixes.forEachIndexed { index, fix ->
                appendLine("${index + 1}. ${fix.description}")
                if (fix.codeSnippet.isNotBlank()) {
                    appendLine("```")
                    appendLine(fix.codeSnippet)
                    appendLine("```")
                }
            }
        }
    }
    
    companion object {
        /**
         * Creates an explanation from parsed LLM response.
         */
        fun fromParsed(
            summary: String,
            details: String,
            category: ErrorCategory,
            fixes: List<FixSuggestion>
        ): ErrorExplanation {
            return ErrorExplanation(
                summary = summary,
                details = details,
                category = category,
                fixes = fixes
            )
        }
        
        /**
         * Creates a simple explanation without LLM.
         */
        fun simple(category: ErrorCategory, message: String): ErrorExplanation {
            return ErrorExplanation(
                summary = "${category.displayName}: ${category.description}",
                details = message,
                category = category,
                fixes = category.commonCauses.mapIndexed { i, cause ->
                    FixSuggestion(
                        description = "Check for: $cause",
                        codeSnippet = "",
                        confidence = 0.5 - (i * 0.1)
                    )
                }
            )
        }
    }
}

/**
 * A suggested fix for an error.
 *
 * @property description Human-readable fix description
 * @property codeSnippet Code example if applicable
 * @property confidence Confidence score (0.0-1.0)
 */
data class FixSuggestion(
    val description: String,
    val codeSnippet: String = "",
    val confidence: Double = 0.5
) {
    companion object {
        fun confident(description: String, snippet: String = ""): FixSuggestion {
            return FixSuggestion(description, snippet, 0.9)
        }
        
        fun likely(description: String, snippet: String = ""): FixSuggestion {
            return FixSuggestion(description, snippet, 0.7)
        }
    }
}

/**
 * Result of error explanation.
 */
data class ErrorExplainResult(
    val explanation: ErrorExplanation?,
    val originalError: ErrorContext,
    val success: Boolean,
    val error: String? = null
) {
    companion object {
        fun success(explanation: ErrorExplanation, context: ErrorContext): ErrorExplainResult {
            return ErrorExplainResult(
                explanation = explanation,
                originalError = context,
                success = true
            )
        }
        
        fun failure(error: String, context: ErrorContext): ErrorExplainResult {
            return ErrorExplainResult(
                explanation = null,
                originalError = context,
                success = false,
                error = error
            )
        }
    }
}
