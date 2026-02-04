// =============================================================================
// DtoGenModels.kt
// =============================================================================
// Data models for JSON to DTO generation.
//
// This includes:
// - DtoGenRequest - input JSON and options
// - TargetLanguage - output language
// - DtoOptions - generation options
// - DtoGenResult - generated code
//
// DESIGN NOTES:
// - Supports multiple target languages
// - Handles nested JSON structures
// - Configurable nullability and attributes
// =============================================================================

package com.sidekick.generation.dto

import com.sidekick.context.ProjectType

/**
 * Request for DTO generation from JSON.
 *
 * @property json The JSON string to convert
 * @property targetLanguage Target programming language
 * @property className Root class name for generated DTO
 * @property options Generation options
 */
data class DtoGenRequest(
    val json: String,
    val targetLanguage: TargetLanguage,
    val className: String,
    val options: DtoOptions = DtoOptions()
) {
    /**
     * Whether this request is valid.
     */
    fun isValid(): Boolean {
        return json.isNotBlank() && className.isNotBlank()
    }
    
    companion object {
        /**
         * Creates a request with auto-detected language.
         */
        fun fromJson(json: String, className: String, projectType: ProjectType): DtoGenRequest {
            return DtoGenRequest(
                json = json,
                targetLanguage = TargetLanguage.fromProjectType(projectType),
                className = className
            )
        }
        
        /**
         * Creates a request with default settings.
         */
        fun simple(json: String, className: String): DtoGenRequest {
            return DtoGenRequest(
                json = json,
                targetLanguage = TargetLanguage.KOTLIN_DATA_CLASS,
                className = className
            )
        }
    }
}

/**
 * Target language for DTO generation.
 */
enum class TargetLanguage(
    val displayName: String,
    val fileExtension: String,
    val keywords: Set<String>
) {
    CSHARP_RECORD(
        displayName = "C# Record",
        fileExtension = "cs",
        keywords = setOf("record", "init", "required")
    ),
    
    CSHARP_CLASS(
        displayName = "C# Class",
        fileExtension = "cs",
        keywords = setOf("class", "get", "set")
    ),
    
    KOTLIN_DATA_CLASS(
        displayName = "Kotlin Data Class",
        fileExtension = "kt",
        keywords = setOf("data class", "val", "var")
    ),
    
    JAVA_RECORD(
        displayName = "Java Record",
        fileExtension = "java",
        keywords = setOf("record", "final")
    ),
    
    JAVA_CLASS(
        displayName = "Java Class",
        fileExtension = "java",
        keywords = setOf("class", "private", "getter", "setter")
    ),
    
    TYPESCRIPT_INTERFACE(
        displayName = "TypeScript Interface",
        fileExtension = "ts",
        keywords = setOf("interface", "readonly")
    ),
    
    TYPESCRIPT_CLASS(
        displayName = "TypeScript Class",
        fileExtension = "ts",
        keywords = setOf("class", "readonly")
    ),
    
    PYTHON_DATACLASS(
        displayName = "Python Dataclass",
        fileExtension = "py",
        keywords = setOf("@dataclass", "Optional")
    );
    
    companion object {
        /**
         * Detects target language from project type.
         */
        fun fromProjectType(projectType: ProjectType): TargetLanguage {
            return when (projectType) {
                ProjectType.DOTNET -> CSHARP_RECORD
                ProjectType.UNITY -> CSHARP_CLASS
                ProjectType.GRADLE -> KOTLIN_DATA_CLASS
                ProjectType.NPM -> TYPESCRIPT_INTERFACE
                ProjectType.PYTHON -> PYTHON_DATACLASS
                else -> KOTLIN_DATA_CLASS
            }
        }
        
        /**
         * Detects from file extension.
         */
        fun fromExtension(extension: String): TargetLanguage {
            return when (extension.lowercase()) {
                "cs" -> CSHARP_RECORD
                "kt", "kts" -> KOTLIN_DATA_CLASS
                "java" -> JAVA_RECORD
                "ts", "tsx" -> TYPESCRIPT_INTERFACE
                "js", "jsx" -> TYPESCRIPT_INTERFACE
                "py" -> PYTHON_DATACLASS
                else -> KOTLIN_DATA_CLASS
            }
        }
        
        /**
         * All C# options.
         */
        val CSHARP_OPTIONS = listOf(CSHARP_RECORD, CSHARP_CLASS)
        
        /**
         * All Java options.
         */
        val JAVA_OPTIONS = listOf(JAVA_RECORD, JAVA_CLASS)
        
        /**
         * All TypeScript options.
         */
        val TYPESCRIPT_OPTIONS = listOf(TYPESCRIPT_INTERFACE, TYPESCRIPT_CLASS)
    }
}

/**
 * DTO generation options.
 *
 * @property useNullableTypes Make properties nullable for optional fields
 * @property addJsonAttributes Add JSON serialization/deserialization attributes
 * @property generateBuilder Generate a builder pattern (where applicable)
 * @property makeImmutable Make the DTO immutable (val vs var, readonly, etc.)
 * @property addValidation Add validation attributes/decorators
 * @property generateEquals Generate equals/hashCode (if not built-in)
 * @property camelCaseProperties Convert snake_case JSON to camelCase properties
 */
data class DtoOptions(
    val useNullableTypes: Boolean = true,
    val addJsonAttributes: Boolean = true,
    val generateBuilder: Boolean = false,
    val makeImmutable: Boolean = true,
    val addValidation: Boolean = false,
    val generateEquals: Boolean = false,
    val camelCaseProperties: Boolean = true
) {
    companion object {
        /**
         * Default options for API responses.
         */
        val API_RESPONSE = DtoOptions(
            useNullableTypes = true,
            addJsonAttributes = true,
            makeImmutable = true
        )
        
        /**
         * Options for mutable domain objects.
         */
        val MUTABLE = DtoOptions(
            useNullableTypes = false,
            addJsonAttributes = false,
            makeImmutable = false,
            generateEquals = true
        )
        
        /**
         * Minimal options.
         */
        val MINIMAL = DtoOptions(
            useNullableTypes = false,
            addJsonAttributes = false,
            generateBuilder = false,
            makeImmutable = true,
            addValidation = false
        )
    }
}

/**
 * Inferred JSON type.
 */
enum class JsonType(val displayName: String) {
    STRING("String"),
    INTEGER("Integer"),
    LONG("Long"),
    DOUBLE("Double"),
    BOOLEAN("Boolean"),
    ARRAY("Array"),
    OBJECT("Object"),
    NULL("Null"),
    UNKNOWN("Unknown");
    
    companion object {
        /**
         * Infers type from a JSON value.
         */
        fun infer(value: Any?): JsonType {
            return when (value) {
                null -> NULL
                is String -> STRING
                is Boolean -> BOOLEAN
                is Int -> INTEGER
                is Long -> LONG
                is Double, is Float -> DOUBLE
                is Number -> {
                    val d = value.toDouble()
                    when {
                        d == d.toLong().toDouble() -> {
                            if (d <= Int.MAX_VALUE && d >= Int.MIN_VALUE) INTEGER else LONG
                        }
                        else -> DOUBLE
                    }
                }
                is List<*> -> ARRAY
                is Map<*, *> -> OBJECT
                else -> UNKNOWN
            }
        }
    }
}

/**
 * A property inferred from JSON.
 */
data class InferredProperty(
    val jsonName: String,
    val propertyName: String,
    val type: JsonType,
    val isNullable: Boolean,
    val nestedTypeName: String? = null,
    val arrayElementType: JsonType? = null
) {
    /**
     * Gets the type string for target language.
     */
    fun typeString(language: TargetLanguage, nullable: Boolean = isNullable): String {
        val baseType = when (language) {
            TargetLanguage.CSHARP_RECORD, TargetLanguage.CSHARP_CLASS -> csharpType()
            TargetLanguage.KOTLIN_DATA_CLASS -> kotlinType()
            TargetLanguage.JAVA_RECORD, TargetLanguage.JAVA_CLASS -> javaType()
            TargetLanguage.TYPESCRIPT_INTERFACE, TargetLanguage.TYPESCRIPT_CLASS -> typescriptType()
            TargetLanguage.PYTHON_DATACLASS -> pythonType()
        }
        
        return when (language) {
            TargetLanguage.KOTLIN_DATA_CLASS -> if (nullable) "$baseType?" else baseType
            TargetLanguage.CSHARP_RECORD, TargetLanguage.CSHARP_CLASS -> 
                if (nullable && type != JsonType.STRING) "$baseType?" else baseType
            TargetLanguage.TYPESCRIPT_INTERFACE, TargetLanguage.TYPESCRIPT_CLASS -> 
                if (nullable) "$baseType | null" else baseType
            TargetLanguage.PYTHON_DATACLASS -> 
                if (nullable) "Optional[$baseType]" else baseType
            else -> baseType
        }
    }
    
    private fun csharpType(): String = when (type) {
        JsonType.STRING -> "string"
        JsonType.INTEGER -> "int"
        JsonType.LONG -> "long"
        JsonType.DOUBLE -> "double"
        JsonType.BOOLEAN -> "bool"
        JsonType.ARRAY -> "List<${nestedTypeName ?: arrayElementType?.let { elementType(it, TargetLanguage.CSHARP_RECORD) } ?: "object"}>"
        JsonType.OBJECT -> nestedTypeName ?: "object"
        JsonType.NULL -> "object"
        JsonType.UNKNOWN -> "object"
    }
    
    private fun kotlinType(): String = when (type) {
        JsonType.STRING -> "String"
        JsonType.INTEGER -> "Int"
        JsonType.LONG -> "Long"
        JsonType.DOUBLE -> "Double"
        JsonType.BOOLEAN -> "Boolean"
        JsonType.ARRAY -> "List<${nestedTypeName ?: arrayElementType?.let { elementType(it, TargetLanguage.KOTLIN_DATA_CLASS) } ?: "Any"}>"
        JsonType.OBJECT -> nestedTypeName ?: "Any"
        JsonType.NULL -> "Any"
        JsonType.UNKNOWN -> "Any"
    }
    
    private fun javaType(): String = when (type) {
        JsonType.STRING -> "String"
        JsonType.INTEGER -> "Integer"
        JsonType.LONG -> "Long"
        JsonType.DOUBLE -> "Double"
        JsonType.BOOLEAN -> "Boolean"
        JsonType.ARRAY -> "List<${nestedTypeName ?: arrayElementType?.let { elementType(it, TargetLanguage.JAVA_RECORD) } ?: "Object"}>"
        JsonType.OBJECT -> nestedTypeName ?: "Object"
        JsonType.NULL -> "Object"
        JsonType.UNKNOWN -> "Object"
    }
    
    private fun typescriptType(): String = when (type) {
        JsonType.STRING -> "string"
        JsonType.INTEGER, JsonType.LONG, JsonType.DOUBLE -> "number"
        JsonType.BOOLEAN -> "boolean"
        JsonType.ARRAY -> "${nestedTypeName ?: arrayElementType?.let { elementType(it, TargetLanguage.TYPESCRIPT_INTERFACE) } ?: "any"}[]"
        JsonType.OBJECT -> nestedTypeName ?: "object"
        JsonType.NULL -> "null"
        JsonType.UNKNOWN -> "any"
    }
    
    private fun pythonType(): String = when (type) {
        JsonType.STRING -> "str"
        JsonType.INTEGER -> "int"
        JsonType.LONG -> "int"
        JsonType.DOUBLE -> "float"
        JsonType.BOOLEAN -> "bool"
        JsonType.ARRAY -> "list[${nestedTypeName ?: arrayElementType?.let { elementType(it, TargetLanguage.PYTHON_DATACLASS) } ?: "Any"}]"
        JsonType.OBJECT -> nestedTypeName ?: "dict"
        JsonType.NULL -> "None"
        JsonType.UNKNOWN -> "Any"
    }
    
    private fun elementType(type: JsonType, language: TargetLanguage): String = when (language) {
        TargetLanguage.CSHARP_RECORD, TargetLanguage.CSHARP_CLASS -> when (type) {
            JsonType.STRING -> "string"
            JsonType.INTEGER -> "int"
            JsonType.LONG -> "long"
            JsonType.DOUBLE -> "double"
            JsonType.BOOLEAN -> "bool"
            else -> "object"
        }
        TargetLanguage.KOTLIN_DATA_CLASS -> when (type) {
            JsonType.STRING -> "String"
            JsonType.INTEGER -> "Int"
            JsonType.LONG -> "Long"
            JsonType.DOUBLE -> "Double"
            JsonType.BOOLEAN -> "Boolean"
            else -> "Any"
        }
        TargetLanguage.JAVA_RECORD, TargetLanguage.JAVA_CLASS -> when (type) {
            JsonType.STRING -> "String"
            JsonType.INTEGER -> "Integer"
            JsonType.LONG -> "Long"
            JsonType.DOUBLE -> "Double"
            JsonType.BOOLEAN -> "Boolean"
            else -> "Object"
        }
        TargetLanguage.TYPESCRIPT_INTERFACE, TargetLanguage.TYPESCRIPT_CLASS -> when (type) {
            JsonType.STRING -> "string"
            JsonType.INTEGER, JsonType.LONG, JsonType.DOUBLE -> "number"
            JsonType.BOOLEAN -> "boolean"
            else -> "any"
        }
        TargetLanguage.PYTHON_DATACLASS -> when (type) {
            JsonType.STRING -> "str"
            JsonType.INTEGER, JsonType.LONG -> "int"
            JsonType.DOUBLE -> "float"
            JsonType.BOOLEAN -> "bool"
            else -> "Any"
        }
    }
}

/**
 * Generated DTO result.
 *
 * @property code The generated code
 * @property className Root class name
 * @property nestedClasses Names of nested classes generated
 * @property imports Required imports
 * @property success Whether generation succeeded
 * @property error Error message if failed
 */
data class DtoGenResult(
    val code: String,
    val className: String,
    val nestedClasses: List<String>,
    val imports: List<String>,
    val success: Boolean,
    val error: String? = null
) {
    /**
     * Full code with imports.
     */
    fun fullCode(): String = buildString {
        if (imports.isNotEmpty()) {
            imports.forEach { appendLine(it) }
            appendLine()
        }
        append(code)
    }
    
    companion object {
        fun success(
            code: String,
            className: String,
            nestedClasses: List<String> = emptyList(),
            imports: List<String> = emptyList()
        ): DtoGenResult {
            return DtoGenResult(
                code = code,
                className = className,
                nestedClasses = nestedClasses,
                imports = imports,
                success = true
            )
        }
        
        fun failure(error: String, className: String = ""): DtoGenResult {
            return DtoGenResult(
                code = "",
                className = className,
                nestedClasses = emptyList(),
                imports = emptyList(),
                success = false,
                error = error
            )
        }
    }
}

/**
 * Utility for converting naming conventions.
 */
object NamingUtils {
    /**
     * Converts snake_case or kebab-case to camelCase.
     */
    fun toCamelCase(name: String): String {
        if (name.isBlank()) return name
        
        val parts = name.split(Regex("[_-]"))
        if (parts.size == 1) return name
        
        return parts.mapIndexed { index, part ->
            if (index == 0) part.lowercase()
            else part.lowercase().replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }
    
    /**
     * Converts to PascalCase.
     */
    fun toPascalCase(name: String): String {
        if (name.isBlank()) return name
        
        return name.split(Regex("[_\\-\\s]+"))
            .joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    }
    
    /**
     * Infers a class name from JSON content.
     */
    fun inferClassName(json: String): String {
        // Try to find a hint in the JSON
        val hints = listOf("name", "type", "id", "@type", "_type")
        for (hint in hints) {
            val pattern = """"$hint"\s*:\s*"(\w+)"""".toRegex()
            pattern.find(json)?.groupValues?.get(1)?.let { value ->
                if (value.length in 2..30) {
                    return toPascalCase(value)
                }
            }
        }
        return "GeneratedDto"
    }
}
