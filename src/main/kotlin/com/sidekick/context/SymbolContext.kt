// =============================================================================
// SymbolContext.kt
// =============================================================================
// Data class representing context about a code symbol (class, method, etc.).
//
// This captures:
// - Symbol name and kind
// - Signature and containing class
// - Documentation and definition text
//
// DESIGN NOTES:
// - Immutable data class for thread-safe passing
// - Kind enum covers common symbol types
// - Used for "explain this" and "document this" features
// =============================================================================

package com.sidekick.context

/**
 * Represents context about a code symbol (class, method, variable, etc.).
 *
 * Used to provide detailed information about the symbol at the cursor
 * for AI-assisted explanations, documentation, and refactoring.
 *
 * @property name Symbol name (e.g., "calculateTotal", "UserService")
 * @property kind Type of symbol (function, class, property, etc.)
 * @property signature Full signature if applicable (e.g., "fun foo(x: Int): String")
 * @property containingClass Parent class name if applicable
 * @property documentation Existing documentation comment if present
 * @property definition Full definition text (truncated for large symbols)
 */
data class SymbolContext(
    val name: String,
    val kind: SymbolKind,
    val signature: String?,
    val containingClass: String?,
    val documentation: String?,
    val definition: String
) {
    companion object {
        /**
         * Empty context representing no symbol.
         */
        val EMPTY = SymbolContext(
            name = "",
            kind = SymbolKind.UNKNOWN,
            signature = null,
            containingClass = null,
            documentation = null,
            definition = ""
        )
        
        /**
         * Maximum definition length to store (to avoid memory issues).
         */
        const val MAX_DEFINITION_LENGTH = 2000
    }
    
    // -------------------------------------------------------------------------
    // Computed Properties
    // -------------------------------------------------------------------------
    
    /**
     * Whether this represents a valid symbol.
     */
    val isValid: Boolean
        get() = name.isNotEmpty() && kind != SymbolKind.UNKNOWN
    
    /**
     * Whether this symbol has existing documentation.
     */
    val hasDocumentation: Boolean
        get() = !documentation.isNullOrBlank()
    
    /**
     * Fully qualified name (containingClass.name if available).
     */
    val qualifiedName: String
        get() = if (containingClass != null) "$containingClass.$name" else name
    
    /**
     * Gets a compact summary for display.
     */
    fun toSummary(): String = buildString {
        append("${kind.displayName}: $name")
        if (containingClass != null) {
            append(" (in $containingClass)")
        }
    }
}

/**
 * Types of code symbols.
 */
enum class SymbolKind(val displayName: String) {
    CLASS("Class"),
    INTERFACE("Interface"),
    STRUCT("Struct"),
    ENUM("Enum"),
    FUNCTION("Function"),
    METHOD("Method"),
    CONSTRUCTOR("Constructor"),
    PROPERTY("Property"),
    FIELD("Field"),
    VARIABLE("Variable"),
    PARAMETER("Parameter"),
    NAMESPACE("Namespace"),
    TYPE_ALIAS("Type Alias"),
    CONSTANT("Constant"),
    UNKNOWN("Unknown");
    
    companion object {
        /**
         * Gets SymbolKind from a type name string (case-insensitive).
         */
        fun fromTypeName(typeName: String): SymbolKind {
            val lower = typeName.lowercase()
            return values().find { lower.contains(it.name.lowercase()) } ?: UNKNOWN
        }
    }
}
