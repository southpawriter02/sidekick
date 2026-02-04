// =============================================================================
// DocGenModels.kt
// =============================================================================
// Data models for documentation generation.
//
// This includes:
// - DocGenRequest - request for generating documentation
// - DocStyle - documentation format styles
// - DocGenResult - generation result with documentation text
//
// DESIGN NOTES:
// - Immutable data classes for thread safety
// - Language-specific style detection
// - Validation for documentable symbols
// =============================================================================

package com.sidekick.generation.docs

import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind

/**
 * Request for generating documentation.
 *
 * @property symbol The symbol to document
 * @property style Documentation style (XML, KDoc, JSDoc, etc.)
 * @property includeParams Whether to include parameter docs
 * @property includeReturns Whether to include return value docs
 * @property includeExceptions Whether to document thrown exceptions
 * @property includeExamples Whether to generate usage examples
 */
data class DocGenRequest(
    val symbol: SymbolContext,
    val style: DocStyle = DocStyle.AUTO,
    val includeParams: Boolean = true,
    val includeReturns: Boolean = true,
    val includeExceptions: Boolean = true,
    val includeExamples: Boolean = false
) {
    /**
     * Whether this request is valid for generation.
     */
    fun isValid(): Boolean {
        return symbol.name.isNotBlank() && 
               symbol.kind in DOCUMENTABLE_KINDS
    }
    
    /**
     * Gets a descriptive reason if the request is invalid.
     */
    fun getInvalidReason(): String? {
        return when {
            symbol.name.isBlank() -> "No symbol name"
            symbol.kind !in DOCUMENTABLE_KINDS -> "Symbol type '${symbol.kind}' is not documentable"
            else -> null
        }
    }
    
    companion object {
        /**
         * Symbol kinds that can have documentation generated.
         */
        val DOCUMENTABLE_KINDS = setOf(
            SymbolKind.CLASS,
            SymbolKind.INTERFACE,
            SymbolKind.STRUCT,
            SymbolKind.METHOD,
            SymbolKind.FUNCTION,
            SymbolKind.PROPERTY,
            SymbolKind.ENUM
        )
        
        /**
         * Creates a request for a symbol with auto-detected style.
         */
        fun forSymbol(symbol: SymbolContext, language: String): DocGenRequest {
            return DocGenRequest(
                symbol = symbol,
                style = DocStyle.fromLanguage(language)
            )
        }
    }
}

/**
 * Documentation style formats.
 */
enum class DocStyle(
    val displayName: String,
    val languageId: String,
    val prefix: String,
    val suffix: String
) {
    AUTO("Auto-detect", "text", "", ""),
    
    XML_DOC(
        displayName = "XML Documentation (C#)",
        languageId = "csharp",
        prefix = "/// <summary>",
        suffix = "/// </summary>"
    ),
    
    KDOC(
        displayName = "KDoc (Kotlin)",
        languageId = "kotlin",
        prefix = "/**",
        suffix = " */"
    ),
    
    JAVADOC(
        displayName = "JavaDoc (Java)",
        languageId = "java",
        prefix = "/**",
        suffix = " */"
    ),
    
    JSDOC(
        displayName = "JSDoc (JavaScript/TypeScript)",
        languageId = "typescript",
        prefix = "/**",
        suffix = " */"
    ),
    
    PYDOC(
        displayName = "Docstring (Python)",
        languageId = "python",
        prefix = "\"\"\"",
        suffix = "\"\"\""
    );
    
    companion object {
        /**
         * Detects documentation style from language ID.
         */
        fun fromLanguage(language: String): DocStyle {
            return when (language.lowercase()) {
                "c#", "csharp", "cs" -> XML_DOC
                "kotlin", "kt" -> KDOC
                "java" -> JAVADOC
                "javascript", "js", "typescript", "ts" -> JSDOC
                "python", "py" -> PYDOC
                else -> XML_DOC  // Default to XML for .NET projects
            }
        }
        
        /**
         * Gets all non-AUTO styles.
         */
        fun allStyles(): List<DocStyle> = entries.filter { it != AUTO }
    }
}

/**
 * Result of documentation generation.
 *
 * @property documentation The generated documentation text
 * @property style The documentation style used
 * @property insertPosition Where to insert the documentation
 * @property success Whether generation succeeded
 * @property error Error message if generation failed
 */
data class DocGenResult(
    val documentation: String,
    val style: DocStyle,
    val insertPosition: InsertPosition,
    val success: Boolean,
    val error: String? = null
) {
    companion object {
        /**
         * Creates a successful result.
         */
        fun success(documentation: String, style: DocStyle): DocGenResult {
            return DocGenResult(
                documentation = documentation,
                style = style,
                insertPosition = InsertPosition.BEFORE_SYMBOL,
                success = true
            )
        }
        
        /**
         * Creates a failure result.
         */
        fun failure(error: String, style: DocStyle = DocStyle.AUTO): DocGenResult {
            return DocGenResult(
                documentation = "",
                style = style,
                insertPosition = InsertPosition.BEFORE_SYMBOL,
                success = false,
                error = error
            )
        }
    }
}

/**
 * Where to insert generated documentation.
 */
enum class InsertPosition {
    /** Insert before the symbol declaration. */
    BEFORE_SYMBOL,
    
    /** Replace existing documentation. */
    REPLACE_EXISTING,
    
    /** Insert after the symbol (for inline comments). */
    AFTER_SYMBOL
}
