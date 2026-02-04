// =============================================================================
// DocGenService.kt
// =============================================================================
// Service for generating documentation using LLM.
//
// This service:
// - Generates documentation for symbols at cursor
// - Supports multiple documentation styles (XML, KDoc, etc.)
// - Uses Ollama for intelligent documentation generation
//
// DESIGN NOTES:
// - Project-level service
// - Uses PromptTemplate system for generation
// - Graceful fallback for generation failures
// =============================================================================

package com.sidekick.generation.docs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind
import com.sidekick.services.ollama.OllamaService
import com.sidekick.services.ollama.models.ChatMessage
import com.sidekick.services.ollama.models.ChatOptions
import com.sidekick.services.ollama.models.ChatRequest
import com.sidekick.settings.SidekickSettings
import kotlinx.coroutines.flow.toList

/**
 * Service for generating documentation using LLM.
 *
 * Handles documentation generation for methods, classes, properties,
 * and other documentable code elements.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = DocGenService.getInstance(project)
 * val result = service.generateForCurrentSymbol()
 * if (result.success) {
 *     // Insert result.documentation into editor
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class DocGenService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(DocGenService::class.java)
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): DocGenService {
            return project.getService(DocGenService::class.java)
        }
        
        /**
         * Prompt template for documentation generation.
         */
        private val DOC_GEN_PROMPT = """
            Generate %s documentation for this %s %s:
            
            ```%s
            %s
            ```
            
            Requirements:
            - Write a clear, concise summary describing what this %s does
            - Include documentation for all parameters with their types and purposes
            - Document the return value if applicable
            - Note any exceptions that may be thrown
            - Use proper %s format
            
            Output ONLY the documentation comment block, nothing else.
            Do not include the code itself, only the documentation.
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Generates documentation for the symbol at the current cursor position.
     */
    suspend fun generateForCurrentSymbol(): DocGenResult {
        LOG.info("Generating documentation for symbol at cursor")
        
        val editorService = EditorContextService.getInstance(project)
        val symbol = editorService.getSymbolAtCursor()
        
        if (symbol == SymbolContext.EMPTY || symbol.name.isBlank()) {
            LOG.debug("No documentable symbol at cursor")
            return DocGenResult.failure("No documentable symbol at cursor position")
        }
        
        val editorContext = editorService.getCurrentContext()
        val style = DocStyle.fromLanguage(editorContext.language)
        
        val request = DocGenRequest(
            symbol = symbol,
            style = style
        )
        
        return generate(request)
    }

    /**
     * Generates documentation from a request.
     */
    suspend fun generate(request: DocGenRequest): DocGenResult {
        LOG.info("Generating ${request.style.displayName} documentation for ${request.symbol.name}")
        
        if (!request.isValid()) {
            val reason = request.getInvalidReason() ?: "Invalid request"
            LOG.debug("Invalid request: $reason")
            return DocGenResult.failure(reason, request.style)
        }
        
        return try {
            val prompt = buildPrompt(request)
            val response = callLLM(prompt)
            val documentation = formatDocumentation(response, request.style)
            
            LOG.info("Successfully generated documentation (${documentation.length} chars)")
            DocGenResult.success(documentation, request.style)
            
        } catch (e: Exception) {
            LOG.warn("Documentation generation failed: ${e.message}", e)
            DocGenResult.failure(e.message ?: "Unknown error", request.style)
        }
    }

    /**
     * Generates a quick documentation stub without LLM.
     * Useful as a fallback or for offline mode.
     */
    fun generateStub(request: DocGenRequest): DocGenResult {
        val symbol = request.symbol
        val style = if (request.style == DocStyle.AUTO) DocStyle.XML_DOC else request.style
        
        val stub = when (style) {
            DocStyle.XML_DOC -> generateXmlDocStub(symbol)
            DocStyle.KDOC -> generateKDocStub(symbol)
            DocStyle.JAVADOC -> generateJavaDocStub(symbol)
            DocStyle.JSDOC -> generateJsDocStub(symbol)
            DocStyle.PYDOC -> generatePyDocStub(symbol)
            DocStyle.AUTO -> generateXmlDocStub(symbol)
        }
        
        return DocGenResult.success(stub, style)
    }

    // -------------------------------------------------------------------------
    // Private Methods - Prompting
    // -------------------------------------------------------------------------

    private fun buildPrompt(request: DocGenRequest): String {
        val symbol = request.symbol
        val style = if (request.style == DocStyle.AUTO) DocStyle.XML_DOC else request.style
        
        return DOC_GEN_PROMPT.format(
            style.displayName,
            symbol.kind.name.lowercase(),
            symbol.name,
            style.languageId,
            symbol.definition,
            symbol.kind.name.lowercase(),
            style.displayName
        )
    }

    private suspend fun callLLM(prompt: String): String {
        val ollamaService = ApplicationManager.getApplication()
            .getService(OllamaService::class.java)
        val settings = SidekickSettings.getInstance()
        
        val messages = listOf(
            ChatMessage.system("You are a documentation generator. Generate only documentation comments, no explanations."),
            ChatMessage.user(prompt)
        )
        
        val options = ChatOptions(
            temperature = 0.3,  // Lower temperature for more focused output
            numPredict = 500    // Limit response length
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

    private fun formatDocumentation(raw: String, style: DocStyle): String {
        var result = raw.trim()
        
        // Remove markdown code fences if present
        if (result.startsWith("```")) {
            result = result.substringAfter("\n").substringBeforeLast("```").trim()
        }
        
        // Ensure proper line endings
        result = result.replace("\r\n", "\n")
        
        return result
    }

    // -------------------------------------------------------------------------
    // Private Methods - Stub Generation
    // -------------------------------------------------------------------------

    private fun generateXmlDocStub(symbol: SymbolContext): String {
        return buildString {
            appendLine("/// <summary>")
            appendLine("/// TODO: Add description for ${symbol.name}")
            appendLine("/// </summary>")
            
            // Add parameter placeholders for methods
            if (symbol.kind == SymbolKind.METHOD || symbol.kind == SymbolKind.FUNCTION) {
                extractParameters(symbol.signature).forEach { param ->
                    appendLine("/// <param name=\"$param\">TODO: Describe parameter</param>")
                }
                
                if (!isVoidReturn(symbol.signature)) {
                    appendLine("/// <returns>TODO: Describe return value</returns>")
                }
            }
        }.trim()
    }

    private fun generateKDocStub(symbol: SymbolContext): String {
        return buildString {
            appendLine("/**")
            appendLine(" * TODO: Add description for ${symbol.name}")
            
            if (symbol.kind == SymbolKind.METHOD || symbol.kind == SymbolKind.FUNCTION) {
                extractParameters(symbol.signature).forEach { param ->
                    appendLine(" * @param $param TODO: Describe parameter")
                }
                
                if (!isVoidReturn(symbol.signature)) {
                    appendLine(" * @return TODO: Describe return value")
                }
            }
            
            appendLine(" */")
        }.trim()
    }

    private fun generateJavaDocStub(symbol: SymbolContext): String {
        return generateKDocStub(symbol)  // Same format as KDoc
    }

    private fun generateJsDocStub(symbol: SymbolContext): String {
        return buildString {
            appendLine("/**")
            appendLine(" * TODO: Add description for ${symbol.name}")
            
            if (symbol.kind == SymbolKind.METHOD || symbol.kind == SymbolKind.FUNCTION) {
                extractParameters(symbol.signature).forEach { param ->
                    appendLine(" * @param {*} $param TODO: Describe parameter")
                }
                
                appendLine(" * @returns {*} TODO: Describe return value")
            }
            
            appendLine(" */")
        }.trim()
    }

    private fun generatePyDocStub(symbol: SymbolContext): String {
        return buildString {
            appendLine("\"\"\"")
            appendLine("TODO: Add description for ${symbol.name}")
            appendLine()
            
            if (symbol.kind == SymbolKind.METHOD || symbol.kind == SymbolKind.FUNCTION) {
                val params = extractParameters(symbol.signature)
                if (params.isNotEmpty()) {
                    appendLine("Args:")
                    params.forEach { param ->
                        appendLine("    $param: TODO: Describe parameter")
                    }
                    appendLine()
                }
                
                appendLine("Returns:")
                appendLine("    TODO: Describe return value")
            }
            
            appendLine("\"\"\"")
        }.trim()
    }

    // -------------------------------------------------------------------------
    // Private Methods - Utilities
    // -------------------------------------------------------------------------

    private fun extractParameters(signature: String?): List<String> {
        if (signature.isNullOrBlank()) return emptyList()
        
        // Simple parameter extraction from signature
        val paramsMatch = Regex("\\(([^)]+)\\)").find(signature)
        if (paramsMatch == null) return emptyList()
        
        val paramsString = paramsMatch.groupValues[1]
        if (paramsString.isBlank()) return emptyList()
        
        return paramsString.split(",")
            .map { it.trim() }
            .mapNotNull { param ->
                // Extract parameter name (handles various formats)
                param.split(Regex("[:\\s]+"))
                    .firstOrNull { it.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")) }
            }
    }

    private fun isVoidReturn(signature: String?): Boolean {
        if (signature.isNullOrBlank()) return true
        val lower = signature.lowercase()
        return "void" in lower || 
               ": unit" in lower || 
               "-> none" in lower ||
               (!signature.contains(":") && !signature.contains("->"))
    }
}
