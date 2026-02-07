// =============================================================================
// DtoGenService.kt
// =============================================================================
// Service for generating DTOs from JSON.
//
// This service:
// - Parses JSON to infer types
// - Generates language-specific code
// - Handles nested objects and arrays
//
// DESIGN NOTES:
// - Project-level service
// - Uses LLM for complex inference
// - Falls back to rule-based generation
// =============================================================================

package com.sidekick.generation.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sidekick.context.EditorContextService
import com.sidekick.context.ProjectContextService
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.UnifiedChatRequest
import com.sidekick.llm.provider.UnifiedMessage
import com.sidekick.settings.SidekickSettings
import kotlinx.serialization.json.*

/**
 * Service for generating DTOs from JSON.
 *
 * Parses JSON and generates type-safe DTOs in various languages.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = DtoGenService.getInstance(project)
 * val result = service.generateFromClipboard()
 * if (result.success) {
 *     println(result.code)
 * }
 * ```
 */
@Service(Service.Level.PROJECT)
class DtoGenService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(DtoGenService::class.java)
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): DtoGenService {
            return project.getService(DtoGenService::class.java)
        }
        
        /**
         * Prompt template for DTO generation.
         */
        private val DTO_GEN_PROMPT = """
            Generate a %s from this JSON:
            
            ```json
            %s
            ```
            
            Class name: %s
            
            Requirements:
            %s
            
            Generate ONLY the code, no explanation. Include imports.
        """.trimIndent()
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------

    /**
     * Generates DTO from clipboard content.
     */
    suspend fun generateFromClipboard(): DtoGenResult {
        val clipboard = getClipboardContent()
        
        if (clipboard.isBlank()) {
            return DtoGenResult.failure("Clipboard is empty")
        }
        
        if (!isValidJson(clipboard)) {
            return DtoGenResult.failure("Clipboard does not contain valid JSON")
        }
        
        val projectService = ProjectContextService.getInstance(project)
        val projectContext = projectService.getProjectContext()
        val targetLanguage = TargetLanguage.fromProjectType(projectContext.projectType)
        val className = NamingUtils.inferClassName(clipboard)
        
        val request = DtoGenRequest(
            json = clipboard,
            targetLanguage = targetLanguage,
            className = className
        )
        
        return generate(request)
    }

    /**
     * Generates DTO from the provided JSON.
     */
    suspend fun generate(request: DtoGenRequest): DtoGenResult {
        LOG.info("Generating ${request.targetLanguage.displayName} DTO: ${request.className}")
        
        if (!request.isValid()) {
            return DtoGenResult.failure("Invalid request: JSON and class name required")
        }
        
        if (!isValidJson(request.json)) {
            return DtoGenResult.failure("Invalid JSON format", request.className)
        }
        
        return try {
            // Try LLM-based generation first
            val code = generateWithLLM(request)
            val imports = extractImports(code, request.targetLanguage)
            val nested = extractNestedClasses(code)
            
            DtoGenResult.success(
                code = code,
                className = request.className,
                nestedClasses = nested,
                imports = imports
            )
        } catch (e: Exception) {
            LOG.warn("LLM generation failed, using fallback: ${e.message}")
            
            // Fallback to rule-based generation
            generateFallback(request)
        }
    }

    /**
     * Generates using rule-based approach (no LLM).
     */
    fun generateQuick(request: DtoGenRequest): DtoGenResult {
        return generateFallback(request)
    }

    /**
     * Validates if text is valid JSON.
     */
    fun isValidJson(text: String): Boolean {
        return try {
            json.parseToJsonElement(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Infers properties from JSON.
     */
    fun inferProperties(jsonText: String, options: DtoOptions): List<InferredProperty> {
        val element = try {
            json.parseToJsonElement(jsonText)
        } catch (e: Exception) {
            return emptyList()
        }
        
        return when (element) {
            is JsonObject -> inferFromObject(element, options)
            is JsonArray -> {
                val first = element.firstOrNull()
                if (first is JsonObject) inferFromObject(first, options) else emptyList()
            }
            else -> emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods - LLM
    // -------------------------------------------------------------------------

    private suspend fun generateWithLLM(request: DtoGenRequest): String {
        val requirements = buildRequirements(request.options)
        val prompt = DTO_GEN_PROMPT.format(
            request.targetLanguage.displayName,
            request.json.take(2000),  // Limit JSON size
            request.className,
            requirements
        )
        
        return callLLM(prompt)
    }

    private fun buildRequirements(options: DtoOptions): String = buildString {
        if (options.useNullableTypes) appendLine("- Use nullable types for optional fields")
        if (options.addJsonAttributes) appendLine("- Add JSON serialization attributes/decorators")
        if (options.makeImmutable) appendLine("- Make the class immutable (val/readonly)")
        if (options.generateBuilder) appendLine("- Include a builder pattern")
        if (options.addValidation) appendLine("- Add validation annotations")
        if (options.camelCaseProperties) appendLine("- Convert snake_case to camelCase")
    }

    private suspend fun callLLM(prompt: String): String {
        val providerManager = ProviderManager.getInstance()
        val settings = SidekickSettings.getInstance()

        val request = UnifiedChatRequest(
            model = settings.defaultModel.ifEmpty { "llama3.2" },
            messages = listOf(UnifiedMessage.user(prompt)),
            systemPrompt = "You are a code generator. Output only code, no explanation or markdown.",
            temperature = 0.2f,
            maxTokens = 1000,
            stream = false
        )

        val response = providerManager.chat(request)
        val content = response.content ?: ""

        // Clean up markdown if present
        return content
            .replace(Regex("```\\w*\\n?"), "")
            .replace("```", "")
            .trim()
    }

    // -------------------------------------------------------------------------
    // Private Methods - Fallback Generation
    // -------------------------------------------------------------------------

    private fun generateFallback(request: DtoGenRequest): DtoGenResult {
        val properties = inferProperties(request.json, request.options)
        
        if (properties.isEmpty()) {
            return DtoGenResult.failure("Could not infer properties from JSON", request.className)
        }
        
        val code = when (request.targetLanguage) {
            TargetLanguage.KOTLIN_DATA_CLASS -> generateKotlin(request.className, properties, request.options)
            TargetLanguage.CSHARP_RECORD -> generateCSharpRecord(request.className, properties, request.options)
            TargetLanguage.CSHARP_CLASS -> generateCSharpClass(request.className, properties, request.options)
            TargetLanguage.JAVA_RECORD -> generateJavaRecord(request.className, properties, request.options)
            TargetLanguage.JAVA_CLASS -> generateJavaClass(request.className, properties, request.options)
            TargetLanguage.TYPESCRIPT_INTERFACE -> generateTypeScript(request.className, properties, request.options, isInterface = true)
            TargetLanguage.TYPESCRIPT_CLASS -> generateTypeScript(request.className, properties, request.options, isInterface = false)
            TargetLanguage.PYTHON_DATACLASS -> generatePython(request.className, properties, request.options)
        }
        
        val imports = getImports(request.targetLanguage, request.options)
        
        return DtoGenResult.success(
            code = code,
            className = request.className,
            imports = imports
        )
    }

    private fun inferFromObject(obj: JsonObject, options: DtoOptions): List<InferredProperty> {
        return obj.entries.map { (key, value) ->
            val propertyName = if (options.camelCaseProperties) {
                NamingUtils.toCamelCase(key)
            } else {
                key
            }
            
            val (type, nestedType, arrayType) = inferType(value)
            
            InferredProperty(
                jsonName = key,
                propertyName = propertyName,
                type = type,
                isNullable = value is JsonNull || options.useNullableTypes,
                nestedTypeName = nestedType,
                arrayElementType = arrayType
            )
        }
    }

    private fun inferType(element: JsonElement): Triple<JsonType, String?, JsonType?> {
        return when (element) {
            is JsonNull -> Triple(JsonType.NULL, null, null)
            is JsonPrimitive -> {
                val type = when {
                    element.isString -> JsonType.STRING
                    element.booleanOrNull != null -> JsonType.BOOLEAN
                    element.longOrNull != null -> {
                        val v = element.long
                        if (v in Int.MIN_VALUE..Int.MAX_VALUE) JsonType.INTEGER else JsonType.LONG
                    }
                    element.doubleOrNull != null -> JsonType.DOUBLE
                    else -> JsonType.STRING
                }
                Triple(type, null, null)
            }
            is JsonArray -> {
                val first = element.firstOrNull()
                val (elemType, nested, _) = if (first != null) inferType(first) else Triple(JsonType.UNKNOWN, null, null)
                Triple(JsonType.ARRAY, nested, elemType)
            }
            is JsonObject -> Triple(JsonType.OBJECT, NamingUtils.toPascalCase("nested"), null)
        }
    }

    // -------------------------------------------------------------------------
    // Code Generators
    // -------------------------------------------------------------------------

    private fun generateKotlin(className: String, props: List<InferredProperty>, options: DtoOptions): String {
        val propDefs = props.joinToString(",\n    ") { prop ->
            val annotation = if (options.addJsonAttributes && prop.jsonName != prop.propertyName) {
                "@SerializedName(\"${prop.jsonName}\") "
            } else ""
            val modifier = if (options.makeImmutable) "val" else "var"
            "$annotation$modifier ${prop.propertyName}: ${prop.typeString(TargetLanguage.KOTLIN_DATA_CLASS)}"
        }
        return "data class $className(\n    $propDefs\n)"
    }

    private fun generateCSharpRecord(className: String, props: List<InferredProperty>, options: DtoOptions): String {
        val propDefs = props.joinToString(",\n    ") { prop ->
            val annotation = if (options.addJsonAttributes && prop.jsonName != prop.propertyName) {
                "[JsonPropertyName(\"${prop.jsonName}\")] "
            } else ""
            "$annotation${prop.typeString(TargetLanguage.CSHARP_RECORD)} ${NamingUtils.toPascalCase(prop.propertyName)}"
        }
        return "public record $className(\n    $propDefs\n);"
    }

    private fun generateCSharpClass(className: String, props: List<InferredProperty>, options: DtoOptions): String {
        val propDefs = props.joinToString("\n    ") { prop ->
            val annotation = if (options.addJsonAttributes && prop.jsonName != prop.propertyName) {
                "[JsonPropertyName(\"${prop.jsonName}\")]\n    "
            } else ""
            val accessor = if (options.makeImmutable) "{ get; init; }" else "{ get; set; }"
            "${annotation}public ${prop.typeString(TargetLanguage.CSHARP_CLASS)} ${NamingUtils.toPascalCase(prop.propertyName)} $accessor"
        }
        return "public class $className\n{\n    $propDefs\n}"
    }

    private fun generateJavaRecord(className: String, props: List<InferredProperty>, options: DtoOptions): String {
        val propDefs = props.joinToString(",\n    ") { prop ->
            val annotation = if (options.addJsonAttributes && prop.jsonName != prop.propertyName) {
                "@JsonProperty(\"${prop.jsonName}\") "
            } else ""
            "$annotation${prop.typeString(TargetLanguage.JAVA_RECORD)} ${prop.propertyName}"
        }
        return "public record $className(\n    $propDefs\n) {}"
    }

    private fun generateJavaClass(className: String, props: List<InferredProperty>, options: DtoOptions): String {
        val fields = props.joinToString("\n    ") { prop ->
            val annotation = if (options.addJsonAttributes && prop.jsonName != prop.propertyName) {
                "@JsonProperty(\"${prop.jsonName}\")\n    "
            } else ""
            "${annotation}private ${prop.typeString(TargetLanguage.JAVA_CLASS)} ${prop.propertyName};"
        }
        
        val gettersSetters = props.joinToString("\n\n    ") { prop ->
            val pascalName = NamingUtils.toPascalCase(prop.propertyName)
            val type = prop.typeString(TargetLanguage.JAVA_CLASS)
            val getter = "public $type get$pascalName() { return ${prop.propertyName}; }"
            val setter = if (!options.makeImmutable) {
                "\n    public void set$pascalName($type value) { this.${prop.propertyName} = value; }"
            } else ""
            getter + setter
        }
        
        return "public class $className {\n    $fields\n\n    $gettersSetters\n}"
    }

    private fun generateTypeScript(className: String, props: List<InferredProperty>, options: DtoOptions, isInterface: Boolean): String {
        val keyword = if (isInterface) "interface" else "class"
        val readonly = if (options.makeImmutable) "readonly " else ""
        
        val propDefs = props.joinToString("\n    ") { prop ->
            val optional = if (prop.isNullable) "?" else ""
            "$readonly${prop.propertyName}$optional: ${prop.typeString(TargetLanguage.TYPESCRIPT_INTERFACE)};"
        }
        
        return "export $keyword $className {\n    $propDefs\n}"
    }

    private fun generatePython(className: String, props: List<InferredProperty>, options: DtoOptions): String {
        val decorator = if (options.makeImmutable) "@dataclass(frozen=True)" else "@dataclass"
        
        val propDefs = props.joinToString("\n    ") { prop ->
            "${prop.propertyName}: ${prop.typeString(TargetLanguage.PYTHON_DATACLASS)}"
        }
        
        return "$decorator\nclass $className:\n    $propDefs"
    }

    // -------------------------------------------------------------------------
    // Utility Methods
    // -------------------------------------------------------------------------

    private fun getImports(language: TargetLanguage, options: DtoOptions): List<String> {
        return when (language) {
            TargetLanguage.KOTLIN_DATA_CLASS -> {
                if (options.addJsonAttributes) listOf("import com.google.gson.annotations.SerializedName")
                else emptyList()
            }
            TargetLanguage.CSHARP_RECORD, TargetLanguage.CSHARP_CLASS -> {
                if (options.addJsonAttributes) listOf("using System.Text.Json.Serialization;")
                else emptyList()
            }
            TargetLanguage.JAVA_RECORD, TargetLanguage.JAVA_CLASS -> {
                if (options.addJsonAttributes) listOf("import com.fasterxml.jackson.annotation.JsonProperty;")
                else emptyList()
            }
            TargetLanguage.PYTHON_DATACLASS -> {
                val imports = mutableListOf("from dataclasses import dataclass")
                if (options.useNullableTypes) imports.add("from typing import Optional")
                imports
            }
            else -> emptyList()
        }
    }

    private fun extractImports(code: String, language: TargetLanguage): List<String> {
        val pattern = when (language) {
            TargetLanguage.CSHARP_RECORD, TargetLanguage.CSHARP_CLASS -> Regex("""using\s+[\w.]+;""")
            TargetLanguage.KOTLIN_DATA_CLASS -> Regex("""import\s+[\w.]+""")
            TargetLanguage.JAVA_RECORD, TargetLanguage.JAVA_CLASS -> Regex("""import\s+[\w.]+;""")
            TargetLanguage.PYTHON_DATACLASS -> Regex("""(?:from|import)\s+[\w.]+.*""")
            else -> return emptyList()
        }
        return pattern.findAll(code).map { it.value }.toList()
    }

    private fun extractNestedClasses(code: String): List<String> {
        val pattern = Regex("""(?:class|record|interface|data class)\s+(\w+)""")
        return pattern.findAll(code).drop(1).map { it.groupValues[1] }.toList()
    }

    private fun getClipboardContent(): String {
        return try {
            java.awt.Toolkit.getDefaultToolkit()
                .systemClipboard
                .getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
