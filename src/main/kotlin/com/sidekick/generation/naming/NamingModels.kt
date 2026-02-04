// =============================================================================
// NamingModels.kt
// =============================================================================
// Data models for variable naming suggestions.
//
// This includes:
// - NamingRequest - request for naming suggestions
// - NamingConvention - supported naming conventions
// - NameSuggestion - individual name suggestion
// - NamingResult - collection of suggestions
//
// DESIGN NOTES:
// - Supports multiple naming conventions
// - Language-aware suggestions
// - Context-based naming
// =============================================================================

package com.sidekick.generation.naming

import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind

/**
 * Request for variable naming suggestions.
 *
 * @property context Contextual information about the variable
 * @property currentName Current name (if renaming)
 * @property type Variable/parameter type
 * @property convention Preferred naming convention
 * @property language Programming language
 * @property suggestionCount Number of suggestions to generate
 */
data class NamingRequest(
    val context: String,
    val currentName: String = "",
    val type: String = "",
    val convention: NamingConvention = NamingConvention.AUTO,
    val language: String = "kotlin",
    val suggestionCount: Int = 5
) {
    /**
     * Whether this request is valid for generation.
     */
    fun isValid(): Boolean {
        return context.isNotBlank() && 
               suggestionCount in 1..10
    }
    
    /**
     * Gets the effective naming convention.
     */
    fun getEffectiveConvention(): NamingConvention {
        return if (convention == NamingConvention.AUTO) {
            NamingConvention.detect(language)
        } else {
            convention
        }
    }
    
    companion object {
        /**
         * Creates a request from a symbol context.
         */
        fun forSymbol(symbol: SymbolContext, language: String): NamingRequest {
            return NamingRequest(
                context = symbol.definition,
                currentName = symbol.name,
                type = extractType(symbol),
                convention = NamingConvention.detect(language),
                language = language
            )
        }
        
        /**
         * Creates a request for renaming a variable.
         */
        fun forRename(
            currentName: String,
            type: String,
            context: String,
            language: String
        ): NamingRequest {
            return NamingRequest(
                context = context,
                currentName = currentName,
                type = type,
                convention = NamingConvention.detect(language),
                language = language
            )
        }
        
        private fun extractType(symbol: SymbolContext): String {
            val signature = symbol.signature ?: return ""
            
            // Try to extract return type or variable type
            return when {
                ": " in signature -> signature.substringAfterLast(": ").substringBefore(" ")
                " -> " in signature -> signature.substringAfterLast(" -> ")
                else -> ""
            }
        }
    }
}

/**
 * Supported naming conventions.
 */
enum class NamingConvention(
    val displayName: String,
    val pattern: String,
    val example: String
) {
    AUTO(
        displayName = "Auto-detect",
        pattern = "",
        example = ""
    ),
    
    CAMEL_CASE(
        displayName = "camelCase",
        pattern = "^[a-z][a-zA-Z0-9]*$",
        example = "userName"
    ),
    
    PASCAL_CASE(
        displayName = "PascalCase",
        pattern = "^[A-Z][a-zA-Z0-9]*$",
        example = "UserName"
    ),
    
    SNAKE_CASE(
        displayName = "snake_case",
        pattern = "^[a-z][a-z0-9_]*$",
        example = "user_name"
    ),
    
    SCREAMING_SNAKE_CASE(
        displayName = "SCREAMING_SNAKE_CASE",
        pattern = "^[A-Z][A-Z0-9_]*$",
        example = "USER_NAME"
    ),
    
    KEBAB_CASE(
        displayName = "kebab-case",
        pattern = "^[a-z][a-z0-9-]*$",
        example = "user-name"
    ),
    
    HUNGARIAN(
        displayName = "Hungarian Notation",
        pattern = "^[a-z]+[A-Z][a-zA-Z0-9]*$",
        example = "strUserName"
    );
    
    /**
     * Converts a name to this convention.
     */
    fun convert(name: String): String {
        val words = splitIntoWords(name)
        
        return when (this) {
            AUTO -> name
            CAMEL_CASE -> words.mapIndexed { i, w -> 
                if (i == 0) w.lowercase() else w.capitalize() 
            }.joinToString("")
            PASCAL_CASE -> words.joinToString("") { it.capitalize() }
            SNAKE_CASE -> words.joinToString("_") { it.lowercase() }
            SCREAMING_SNAKE_CASE -> words.joinToString("_") { it.uppercase() }
            KEBAB_CASE -> words.joinToString("-") { it.lowercase() }
            HUNGARIAN -> name // Complex, return as-is
        }
    }
    
    /**
     * Checks if a name matches this convention.
     */
    fun matches(name: String): Boolean {
        if (pattern.isEmpty()) return true
        return Regex(pattern).matches(name)
    }
    
    companion object {
        /**
         * Detects the appropriate convention for a language.
         */
        fun detect(language: String): NamingConvention {
            return when (language.lowercase()) {
                "kotlin", "java", "javascript", "typescript", "dart" -> CAMEL_CASE
                "python", "ruby", "rust" -> SNAKE_CASE
                "c#", "csharp" -> PASCAL_CASE
                "go" -> CAMEL_CASE
                else -> CAMEL_CASE
            }
        }
        
        /**
         * Detects the convention of an existing name.
         */
        fun detectFromName(name: String): NamingConvention {
            return when {
                name.contains("_") && name.all { it.isUpperCase() || it == '_' || it.isDigit() } -> SCREAMING_SNAKE_CASE
                name.contains("_") -> SNAKE_CASE
                name.contains("-") -> KEBAB_CASE
                name.first().isUpperCase() -> PASCAL_CASE
                else -> CAMEL_CASE
            }
        }
        
        /**
         * Splits a name into words.
         */
        fun splitIntoWords(name: String): List<String> {
            return when {
                // snake_case or SCREAMING_SNAKE_CASE
                name.contains("_") -> name.split("_").filter { it.isNotBlank() }
                
                // kebab-case
                name.contains("-") -> name.split("-").filter { it.isNotBlank() }
                
                // camelCase or PascalCase
                else -> name.split(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
                    .filter { it.isNotBlank() }
            }
        }
    }
}

/**
 * Individual name suggestion.
 *
 * @property name Suggested name
 * @property convention Convention used
 * @property rationale Reason for this suggestion
 * @property confidence Confidence score (0.0-1.0)
 */
data class NameSuggestion(
    val name: String,
    val convention: NamingConvention,
    val rationale: String,
    val confidence: Double
) {
    /**
     * Formats the suggestion for display.
     */
    fun display(): String {
        return "$name (${convention.displayName})"
    }
    
    companion object {
        /**
         * Creates a suggestion with high confidence.
         */
        fun confident(name: String, convention: NamingConvention, rationale: String): NameSuggestion {
            return NameSuggestion(name, convention, rationale, 0.9)
        }
        
        /**
         * Creates a suggestion with medium confidence.
         */
        fun likely(name: String, convention: NamingConvention, rationale: String): NameSuggestion {
            return NameSuggestion(name, convention, rationale, 0.7)
        }
    }
}

/**
 * Result of name suggestion generation.
 *
 * @property suggestions List of suggestions (ordered by confidence)
 * @property forType Type of variable being named
 * @property success Whether generation succeeded
 * @property error Error message if generation failed
 */
data class NamingResult(
    val suggestions: List<NameSuggestion>,
    val forType: String,
    val success: Boolean,
    val error: String? = null
) {
    /**
     * Gets the best suggestion.
     */
    fun bestSuggestion(): NameSuggestion? {
        return suggestions.maxByOrNull { it.confidence }
    }
    
    /**
     * Gets suggestions matching a convention.
     */
    fun forConvention(convention: NamingConvention): List<NameSuggestion> {
        return suggestions.filter { it.convention == convention }
    }
    
    companion object {
        /**
         * Creates a successful result.
         */
        fun success(suggestions: List<NameSuggestion>, forType: String = ""): NamingResult {
            return NamingResult(
                suggestions = suggestions.sortedByDescending { it.confidence },
                forType = forType,
                success = true
            )
        }
        
        /**
         * Creates a failure result.
         */
        fun failure(error: String): NamingResult {
            return NamingResult(
                suggestions = emptyList(),
                forType = "",
                success = false,
                error = error
            )
        }
    }
}

/**
 * Common variable type patterns for contextual naming.
 */
object NamingPatterns {
    /**
     * Common prefixes for boolean variables.
     */
    val BOOLEAN_PREFIXES = listOf("is", "has", "can", "should", "will", "did", "was")
    
    /**
     * Common suffixes for collection variables.
     */
    val COLLECTION_SUFFIXES = listOf("List", "Set", "Map", "Array", "Collection", "Items", "s")
    
    /**
     * Common type-based naming patterns.
     */
    val TYPE_PATTERNS = mapOf(
        "String" to listOf("name", "text", "value", "content", "message"),
        "Int" to listOf("count", "index", "size", "length", "number", "id"),
        "Boolean" to BOOLEAN_PREFIXES.map { "${it}Valid" },
        "Date" to listOf("date", "timestamp", "createdAt", "updatedAt"),
        "List" to listOf("items", "elements", "entries", "records")
    )
    
    /**
     * Gets suggestions based on type.
     */
    fun suggestForType(type: String): List<String> {
        val baseType = type.substringBefore("<").substringBefore("?")
        return TYPE_PATTERNS[baseType] ?: emptyList()
    }
    
    /**
     * Suggests a boolean prefix based on context.
     */
    fun suggestBooleanPrefix(context: String): String {
        val words = context.lowercase()
        return when {
            "has" in words || "contain" in words -> "has"
            "can" in words || "able" in words -> "can"
            "should" in words || "must" in words -> "should"
            "will" in words || "going" in words -> "will"
            else -> "is"
        }
    }
}

// Extension for String.capitalize() which is deprecated
private fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
