// =============================================================================
// ErrorExplainService.kt
// =============================================================================
// Service for explaining error messages using LLM.
//
// This service:
// - Parses error messages
// - Generates detailed explanations
// - Suggests fixes
//
// DESIGN NOTES:
// - Project-level service
// - Language-aware explanations
// - Fallback for common error patterns
// =============================================================================

package com.sidekick.generation.errorexplain

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.UnifiedChatRequest
import com.sidekick.llm.provider.UnifiedMessage
import com.sidekick.settings.SidekickSettings

/**
 * Service for explaining error messages.
 *
 * Provides LLM-powered explanations of error messages with
 * suggested fixes and relevant documentation.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = ErrorExplainService.getInstance(project)
 * val result = service.explainError(errorMessage)
 * if (result.success) {
 *     println(result.explanation?.summary)
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class ErrorExplainService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(ErrorExplainService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): ErrorExplainService {
            return project.getService(ErrorExplainService::class.java)
        }
        
        /**
         * Prompt template for error explanation.
         */
        private val EXPLAIN_PROMPT = """
            Explain this %s error and suggest fixes:
            
            Error: %s
            %s
            Language: %s
            
            Context (code around error):
            ```
            %s
            ```
            
            Provide:
            1. A clear, beginner-friendly explanation of what this error means
            2. Why this error typically occurs
            3. 2-3 specific ways to fix it with code examples
            
            Reply with ONLY a JSON object (no markdown):
            {
                "summary": "brief 1-sentence explanation",
                "details": "fuller explanation of why this happens",
                "fixes": [
                    {"description": "how to fix", "code": "example code if applicable"}
                ]
            }
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Explains an error message.
     */
    suspend fun explainError(errorMessage: String): ErrorExplainResult {
        val editorService = EditorContextService.getInstance(project)
        val context = editorService.getCurrentContext()
        
        val errorContext = ErrorContext.fromMessage(errorMessage, context.language)
        return explain(errorContext)
    }

    /**
     * Explains an error with full context.
     */
    suspend fun explain(errorContext: ErrorContext): ErrorExplainResult {
        LOG.info("Explaining error: ${errorContext.summary()}")
        
        if (!errorContext.isValid()) {
            return ErrorExplainResult.failure("No error message provided", errorContext)
        }
        
        return try {
            val prompt = buildPrompt(errorContext)
            val response = callLLM(prompt)
            val explanation = parseResponse(response, errorContext)
            
            LOG.info("Generated explanation for ${errorContext.source.displayName} error")
            ErrorExplainResult.success(explanation, errorContext)
            
        } catch (e: Exception) {
            LOG.warn("Error explanation failed: ${e.message}", e)
            
            // Fallback to pattern-based explanation
            val fallback = generateFallback(errorContext)
            ErrorExplainResult.success(fallback, errorContext)
        }
    }

    /**
     * Explains an error from selected text.
     */
    suspend fun explainFromSelection(): ErrorExplainResult {
        val editorService = EditorContextService.getInstance(project)
        val context = editorService.getCurrentContext()
        
        val selection = context.selection
        if (selection.isNullOrBlank()) {
            return ErrorExplainResult.failure(
                "No text selected. Select an error message to explain.",
                ErrorContext.fromMessage("")
            )
        }
        
        return explainError(selection)
    }

    /**
     * Generates quick explanation without LLM.
     */
    fun quickExplain(errorContext: ErrorContext): ErrorExplanation {
        return generateFallback(errorContext)
    }

    /**
     * Detects the category of an error.
     */
    fun categorize(errorMessage: String): ErrorCategory {
        return ErrorCategory.detect(errorMessage)
    }

    // -------------------------------------------------------------------------
    // Private Methods - LLM
    // -------------------------------------------------------------------------

    private fun buildPrompt(errorContext: ErrorContext): String {
        val errorCodeInfo = if (errorContext.errorCode != null) {
            "Error Code: ${errorContext.errorCode}"
        } else {
            ""
        }
        
        return EXPLAIN_PROMPT.format(
            errorContext.source.displayName,
            errorContext.message,
            errorCodeInfo,
            errorContext.language.ifBlank { "unknown" },
            errorContext.surroundingCode.ifBlank { "(not available)" }
        )
    }

    private suspend fun callLLM(prompt: String): String {
        val providerManager = ProviderManager.getInstance()
        val settings = SidekickSettings.getInstance()

        val request = UnifiedChatRequest(
            model = settings.defaultModel.ifEmpty { "llama3.2" },
            messages = listOf(UnifiedMessage.user(prompt)),
            systemPrompt = "You are an expert programming assistant explaining errors. Reply only with JSON, no markdown.",
            temperature = 0.3f,
            maxTokens = 600,
            stream = false
        )

        val response = providerManager.chat(request)
        return response.content ?: ""
    }

    private fun parseResponse(response: String, errorContext: ErrorContext): ErrorExplanation {
        // Extract JSON object
        val jsonRegex = Regex("""\{[\s\S]*}""")
        val jsonMatch = jsonRegex.find(response)
        
        val category = ErrorCategory.detect(errorContext.message)
        
        if (jsonMatch != null) {
            val json = jsonMatch.value
            
            val summary = extractJsonString(json, "summary") ?: "Error explanation"
            val details = extractJsonString(json, "details") ?: ""
            
            // Parse fixes array
            val fixes = mutableListOf<FixSuggestion>()
            val fixesRegex = Regex(""""fixes"\s*:\s*\[([\s\S]*?)]""")
            val fixesMatch = fixesRegex.find(json)
            
            if (fixesMatch != null) {
                val fixObjectRegex = Regex("""\{[^}]+}""")
                fixObjectRegex.findAll(fixesMatch.groupValues[1]).forEach { match ->
                    val fixJson = match.value
                    val description = extractJsonString(fixJson, "description")
                    val code = extractJsonString(fixJson, "code") ?: ""
                    
                    if (description != null) {
                        fixes.add(FixSuggestion(description, code, 0.8))
                    }
                }
            }
            
            return ErrorExplanation(
                summary = summary,
                details = details,
                category = category,
                fixes = fixes
            )
        }
        
        // Fallback if parsing fails
        return generateFallback(errorContext)
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")
    }

    // -------------------------------------------------------------------------
    // Private Methods - Fallback
    // -------------------------------------------------------------------------

    private fun generateFallback(errorContext: ErrorContext): ErrorExplanation {
        val category = ErrorCategory.detect(errorContext.message)
        val fixes = generatePatternFixes(errorContext, category)
        
        return ErrorExplanation(
            summary = "${category.displayName}: ${errorContext.message.take(100)}",
            details = buildFallbackDetails(errorContext, category),
            category = category,
            fixes = fixes
        )
    }

    private fun buildFallbackDetails(errorContext: ErrorContext, category: ErrorCategory): String {
        return buildString {
            appendLine("This appears to be a ${category.displayName.lowercase()}.")
            appendLine()
            appendLine(category.description)
            appendLine()
            if (category.commonCauses.isNotEmpty()) {
                appendLine("Common causes include:")
                category.commonCauses.forEach { cause ->
                    appendLine("• $cause")
                }
            }
        }
    }

    private fun generatePatternFixes(errorContext: ErrorContext, category: ErrorCategory): List<FixSuggestion> {
        val message = errorContext.message.lowercase()
        val fixes = mutableListOf<FixSuggestion>()
        
        // Category-specific suggestions
        when (category) {
            ErrorCategory.NULL_REFERENCE -> {
                fixes.add(FixSuggestion.confident(
                    "Add null check before accessing the value",
                    "if (value != null) { /* use value */ }"
                ))
                fixes.add(FixSuggestion.likely(
                    "Use safe navigation operator",
                    "value?.property"
                ))
            }
            
            ErrorCategory.UNDEFINED_REFERENCE -> {
                fixes.add(FixSuggestion.confident(
                    "Check the spelling of the symbol name"
                ))
                fixes.add(FixSuggestion.likely(
                    "Add missing import statement"
                ))
            }
            
            ErrorCategory.TYPE_MISMATCH -> {
                fixes.add(FixSuggestion.confident(
                    "Convert the value to the expected type"
                ))
                fixes.add(FixSuggestion.likely(
                    "Check if using the correct variable"
                ))
            }
            
            ErrorCategory.MISSING_IMPORT -> {
                fixes.add(FixSuggestion.confident(
                    "Add the missing import statement"
                ))
                fixes.add(FixSuggestion.likely(
                    "Check if the dependency is installed"
                ))
            }
            
            ErrorCategory.SYNTAX -> {
                if (";" in message) {
                    fixes.add(FixSuggestion.confident(
                        "Add missing semicolon"
                    ))
                }
                if ("}" in message || "{" in message) {
                    fixes.add(FixSuggestion.confident(
                        "Check matching braces"
                    ))
                }
                fixes.add(FixSuggestion.likely(
                    "Review the code structure around this line"
                ))
            }
            
            else -> {
                // Generic fixes based on category causes
                category.commonCauses.take(2).forEachIndexed { i, cause ->
                    fixes.add(FixSuggestion(
                        description = "Check for: $cause",
                        confidence = 0.6 - (i * 0.1)
                    ))
                }
            }
        }
        
        return fixes.take(3)
    }
}
