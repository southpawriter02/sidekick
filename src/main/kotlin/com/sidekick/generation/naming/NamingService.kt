// =============================================================================
// NamingService.kt
// =============================================================================
// Service for generating variable naming suggestions using LLM.
//
// This service:
// - Generates context-aware naming suggestions
// - Supports multiple naming conventions
// - Uses Ollama for intelligent naming
//
// DESIGN NOTES:
// - Project-level service
// - Language-aware suggestions
// - Fallback patterns for common types
// =============================================================================

package com.sidekick.generation.naming

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.services.ollama.OllamaService
import com.sidekick.services.ollama.models.ChatMessage
import com.sidekick.services.ollama.models.ChatOptions
import com.sidekick.services.ollama.models.ChatRequest
import com.sidekick.settings.SidekickSettings
import kotlinx.coroutines.flow.toList

/**
 * Service for generating variable naming suggestions.
 *
 * Generates context-aware naming suggestions based on code context,
 * variable type, and language conventions.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = NamingService.getInstance(project)
 * val result = service.suggestForCurrentContext()
 * result.suggestions.forEach { println(it.name) }
 * ```
 */
@Service(Service.Level.PROJECT)
class NamingService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(NamingService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): NamingService {
            return project.getService(NamingService::class.java)
        }
        
        /**
         * Prompt template for naming suggestions.
         */
        private val NAMING_PROMPT = """
            Suggest %d variable names for this context:
            
            Context: %s
            Current name: %s
            Type: %s
            Language: %s
            Naming convention: %s (%s)
            
            Requirements:
            - Names must follow %s convention
            - Names should be descriptive and meaningful
            - Consider the variable's purpose from context
            - Avoid abbreviations unless very common (e.g., id, url, max)
            - For booleans, use prefixes like: is, has, can, should
            - For collections, use plural or suffix like List, Set
            
            Reply with ONLY a JSON array of suggestions:
            [{"name": "suggestedName", "rationale": "brief reason"}]
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Suggests names for the variable/symbol at cursor.
     */
    suspend fun suggestForCurrentContext(): NamingResult {
        LOG.info("Suggesting names for current context")
        
        val editorService = EditorContextService.getInstance(project)
        val symbol = editorService.getSymbolAtCursor()
        val context = editorService.getCurrentContext()
        
        if (symbol.name.isBlank()) {
            return NamingResult.failure("No symbol at cursor position")
        }
        
        val request = NamingRequest.forSymbol(symbol, context.language)
        return suggest(request)
    }

    /**
     * Generates naming suggestions from a request.
     */
    suspend fun suggest(request: NamingRequest): NamingResult {
        val convention = request.getEffectiveConvention()
        LOG.info("Generating ${request.suggestionCount} name suggestions using ${convention.displayName}")
        
        if (!request.isValid()) {
            return NamingResult.failure("Invalid request: context is required")
        }
        
        return try {
            val prompt = buildPrompt(request, convention)
            val response = callLLM(prompt)
            val suggestions = parseResponse(response, convention)
            
            LOG.info("Generated ${suggestions.size} name suggestions")
            NamingResult.success(suggestions, request.type)
            
        } catch (e: Exception) {
            LOG.warn("Name suggestion failed: ${e.message}", e)
            
            // Fallback to pattern-based suggestions
            val fallbackSuggestions = generateFallback(request, convention)
            NamingResult.success(fallbackSuggestions, request.type)
        }
    }

    /**
     * Generates quick suggestions without LLM based on type patterns.
     */
    fun suggestFromPatterns(request: NamingRequest): NamingResult {
        val convention = request.getEffectiveConvention()
        val suggestions = generateFallback(request, convention)
        
        return if (suggestions.isNotEmpty()) {
            NamingResult.success(suggestions, request.type)
        } else {
            NamingResult.failure("No pattern-based suggestions available")
        }
    }

    /**
     * Converts a name to a different convention.
     */
    fun convertName(name: String, toConvention: NamingConvention): String {
        return toConvention.convert(name)
    }

    /**
     * Detects the convention used by a name.
     */
    fun detectConvention(name: String): NamingConvention {
        return NamingConvention.detectFromName(name)
    }

    // -------------------------------------------------------------------------
    // Private Methods - LLM
    // -------------------------------------------------------------------------

    private fun buildPrompt(request: NamingRequest, convention: NamingConvention): String {
        return NAMING_PROMPT.format(
            request.suggestionCount,
            request.context.take(500),  // Limit context size
            request.currentName.ifBlank { "(none)" },
            request.type.ifBlank { "unknown" },
            request.language,
            convention.displayName,
            convention.example,
            convention.displayName
        )
    }

    private suspend fun callLLM(prompt: String): String {
        val ollamaService = ApplicationManager.getApplication()
            .getService(OllamaService::class.java)
        val settings = SidekickSettings.getInstance()
        
        val messages = listOf(
            ChatMessage.system("You are a naming assistant. Reply only with JSON arrays, no markdown."),
            ChatMessage.user(prompt)
        )
        
        val options = ChatOptions(
            temperature = 0.5,  // Slightly higher for variety
            numPredict = 400
        )
        
        val request = ChatRequest(
            model = settings.defaultModel.ifEmpty { "llama3.2" },
            messages = messages,
            stream = false,
            options = options
        )
        
        val responses = ollamaService.chat(request).toList()
        return responses.joinToString("") { it.message.content }
    }

    private fun parseResponse(response: String, convention: NamingConvention): List<NameSuggestion> {
        val suggestions = mutableListOf<NameSuggestion>()
        
        // Extract JSON array
        val arrayRegex = Regex("""\[[\s\S]*?]""")
        val arrayMatch = arrayRegex.find(response) ?: return suggestions
        
        // Parse individual objects
        val objectRegex = Regex("""\{[^}]+}""")
        objectRegex.findAll(arrayMatch.value).forEach { match ->
            val json = match.value
            val name = extractJsonString(json, "name")
            val rationale = extractJsonString(json, "rationale") ?: ""
            
            if (name != null && name.isNotBlank()) {
                suggestions.add(NameSuggestion(
                    name = name,
                    convention = convention,
                    rationale = rationale,
                    confidence = 0.8
                ))
            }
        }
        
        return suggestions
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    // -------------------------------------------------------------------------
    // Private Methods - Fallback
    // -------------------------------------------------------------------------

    private fun generateFallback(request: NamingRequest, convention: NamingConvention): List<NameSuggestion> {
        val suggestions = mutableListOf<NameSuggestion>()
        val type = request.type
        
        // Type-based suggestions
        NamingPatterns.suggestForType(type).take(3).forEach { baseName ->
            val name = convention.convert(baseName)
            suggestions.add(NameSuggestion.likely(
                name = name,
                convention = convention,
                rationale = "Common pattern for $type"
            ))
        }
        
        // Boolean-specific suggestions
        if (type.equals("Boolean", ignoreCase = true) || type.equals("Bool", ignoreCase = true)) {
            val prefix = NamingPatterns.suggestBooleanPrefix(request.context)
            val contextWord = extractContextWord(request.context)
            if (contextWord.isNotBlank()) {
                val name = convention.convert("${prefix}${contextWord.capitalize()}")
                suggestions.add(NameSuggestion.confident(
                    name = name,
                    convention = convention,
                    rationale = "Boolean naming pattern"
                ))
            }
        }
        
        // Context-based suggestions
        val contextWords = extractKeywords(request.context)
        contextWords.take(2).forEach { word ->
            val name = convention.convert(word)
            suggestions.add(NameSuggestion(
                name = name,
                convention = convention,
                rationale = "Derived from context",
                confidence = 0.5
            ))
        }
        
        return suggestions.distinctBy { it.name }.take(request.suggestionCount)
    }

    private fun extractContextWord(context: String): String {
        // Extract a meaningful word from context for boolean naming
        val words = context.split(Regex("\\W+"))
            .filter { it.length > 2 && it.all { c -> c.isLetter() } }
            .filterNot { it.lowercase() in SKIP_WORDS }
        
        return words.firstOrNull()?.lowercase() ?: ""
    }

    private fun extractKeywords(context: String): List<String> {
        return context.split(Regex("\\W+"))
            .filter { it.length > 3 && it.all { c -> c.isLetter() } }
            .filterNot { it.lowercase() in SKIP_WORDS }
            .distinct()
    }

    private val SKIP_WORDS = setOf(
        "this", "that", "with", "from", "return", "void", "null",
        "true", "false", "private", "public", "protected", "class",
        "function", "fun", "val", "var", "const", "let"
    )
}

// Extension for String.capitalize() which is deprecated
private fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
