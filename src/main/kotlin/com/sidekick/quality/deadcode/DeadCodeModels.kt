package com.sidekick.quality.deadcode

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * # Dead Code Cemetery Models
 *
 * Data structures for detecting and managing unused code symbols.
 * Part of Sidekick v0.6.4 Dead Code Cemetery feature.
 *
 * ## Overview
 *
 * These models support:
 * - Detection of unused symbols (classes, methods, fields, etc.)
 * - Confidence-based analysis
 * - Safe deletion tracking
 * - Bulk management operations
 *
 * @since 0.6.4
 */

/**
 * An unused symbol detected in code.
 *
 * @property name The symbol name
 * @property qualifiedName Fully qualified name (e.g., com.example.MyClass.method)
 * @property type The symbol type (class, method, field, etc.)
 * @property location Where the symbol is defined
 * @property usageCount Number of usages found (0 = unused)
 * @property lastUsedDate When the symbol was last used (from VCS if available)
 * @property confidence Confidence that the symbol is truly dead (0.0 - 1.0)
 * @property canSafeDelete Whether the symbol can be safely deleted
 * @property visibility The visibility modifier
 * @property codeSize Approximate lines of code
 */
data class DeadCodeSymbol(
    val name: String,
    val qualifiedName: String,
    val type: SymbolType,
    val location: SymbolLocation,
    val usageCount: Int,
    val lastUsedDate: Instant?,
    val confidence: Float,
    val canSafeDelete: Boolean,
    val visibility: SymbolVisibility = SymbolVisibility.PRIVATE,
    val codeSize: Int = 1
) {
    /**
     * Whether this symbol is truly unused (no usages found).
     */
    val isUnused: Boolean get() = usageCount == 0

    /**
     * Whether this symbol has high confidence of being dead.
     */
    val isHighConfidence: Boolean get() = confidence >= 0.8f

    /**
     * Days since the symbol was last used.
     */
    val daysSinceLastUsed: Long?
        get() = lastUsedDate?.let {
            ChronoUnit.DAYS.between(
                it.atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now()
            )
        }

    /**
     * Display string for the symbol.
     */
    val displayString: String
        get() = "${type.icon} $name (${type.displayName})"

    /**
     * Confidence as a percentage string.
     */
    val confidencePercent: String
        get() = "${(confidence * 100).toInt()}%"

    companion object {
        /**
         * Creates a simple symbol for testing.
         */
        fun simple(
            name: String,
            type: SymbolType = SymbolType.METHOD,
            confidence: Float = 1.0f,
            canSafeDelete: Boolean = true
        ) = DeadCodeSymbol(
            name = name,
            qualifiedName = "com.example.$name",
            type = type,
            location = SymbolLocation.empty(),
            usageCount = 0,
            lastUsedDate = null,
            confidence = confidence,
            canSafeDelete = canSafeDelete
        )
    }
}

/**
 * Location of a symbol in source code.
 *
 * @property filePath Absolute path to the file
 * @property line 1-based line number
 * @property className Containing class name (if any)
 * @property memberName Member name within the class (if any)
 */
data class SymbolLocation(
    val filePath: String,
    val line: Int,
    val className: String?,
    val memberName: String?
) {
    /**
     * File name without path.
     */
    val fileName: String get() = filePath.substringAfterLast("/")

    /**
     * Display string for location.
     */
    val displayString: String get() = "$fileName:$line"

    /**
     * Full qualified location string.
     */
    val qualifiedString: String
        get() = buildString {
            append(fileName)
            if (className != null) {
                append(" → $className")
                if (memberName != null) {
                    append(".$memberName")
                }
            }
            append(":$line")
        }

    companion object {
        fun empty() = SymbolLocation("", 0, null, null)
    }
}

/**
 * Types of code symbols that can be detected as dead.
 *
 * @property displayName Human-readable name
 * @property icon Emoji icon for display
 * @property deletable Whether this type can typically be safely deleted
 */
enum class SymbolType(
    val displayName: String,
    val icon: String,
    val deletable: Boolean
) {
    /** Class definition */
    CLASS("Class", "📦", true),

    /** Interface definition */
    INTERFACE("Interface", "🔌", true),

    /** Enum definition */
    ENUM("Enum", "🔢", true),

    /** Method or function */
    METHOD("Method", "⚙️", true),

    /** Property (getter/setter) */
    PROPERTY("Property", "📋", true),

    /** Field or member variable */
    FIELD("Field", "🏷️", true),

    /** Constructor */
    CONSTRUCTOR("Constructor", "🏗️", false),

    /** Parameter of a method */
    PARAMETER("Parameter", "📥", true),

    /** Local variable */
    LOCAL_VARIABLE("Local Variable", "📍", true),

    /** Import statement */
    IMPORT("Import", "📎", true),

    /** Object declaration (Kotlin) */
    OBJECT("Object", "🎯", true),

    /** Type alias */
    TYPE_ALIAS("Type Alias", "🔗", true);

    override fun toString(): String = displayName

    companion object {
        /**
         * Symbol types that represent declarations.
         */
        val DECLARATIONS = listOf(CLASS, INTERFACE, ENUM, OBJECT)

        /**
         * Symbol types that represent members.
         */
        val MEMBERS = listOf(METHOD, PROPERTY, FIELD, CONSTRUCTOR)

        /**
         * Symbol types that are typically safe to delete.
         */
        val SAFE_DELETABLE = entries.filter { it.deletable }

        /**
         * Finds type by name (case-insensitive).
         */
        fun byName(name: String): SymbolType? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Visibility modifiers for symbols.
 */
enum class SymbolVisibility(val displayName: String, val deletionRisk: DeletionRisk) {
    /** Private to the containing class */
    PRIVATE("private", DeletionRisk.LOW),

    /** Internal to the module */
    INTERNAL("internal", DeletionRisk.MEDIUM),

    /** Protected within class hierarchy */
    PROTECTED("protected", DeletionRisk.MEDIUM),

    /** Publicly accessible */
    PUBLIC("public", DeletionRisk.HIGH),

    /** Package-private (default in Java) */
    PACKAGE_PRIVATE("package", DeletionRisk.MEDIUM);

    companion object {
        fun byName(name: String): SymbolVisibility {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: PRIVATE
        }
    }
}

/**
 * Risk level for deleting a symbol.
 */
enum class DeletionRisk(val displayName: String, val weight: Int) {
    /** Low risk - private symbols with no usages */
    LOW("Low", 1),

    /** Medium risk - internal/protected symbols */
    MEDIUM("Medium", 2),

    /** High risk - public API symbols */
    HIGH("High", 3);

    companion object {
        fun fromSymbol(symbol: DeadCodeSymbol): DeletionRisk {
            // Public symbols are always high risk
            if (symbol.visibility == SymbolVisibility.PUBLIC) return HIGH

            // Low confidence increases risk
            if (symbol.confidence < 0.5f) return HIGH
            if (symbol.confidence < 0.8f) return MEDIUM

            return symbol.visibility.deletionRisk
        }
    }
}

/**
 * Configuration for dead code detection.
 *
 * @property enabled Whether the feature is active
 * @property includePrivate Include private symbols
 * @property includeInternal Include internal symbols
 * @property excludePublicApi Exclude public API from detection
 * @property excludePatterns Glob patterns for names to exclude
 * @property minConfidence Minimum confidence threshold
 * @property scanOnOpen Whether to scan when project opens
 */
data class DeadCodeConfig(
    val enabled: Boolean = true,
    val includePrivate: Boolean = true,
    val includeInternal: Boolean = true,
    val excludePublicApi: Boolean = true,
    val excludePatterns: List<String> = listOf("*Test*", "*Mock*", "*Fake*", "*Stub*"),
    val minConfidence: Float = 0.8f,
    val scanOnOpen: Boolean = false,
    val enabledTypes: Set<SymbolType> = SymbolType.entries.toSet()
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with pattern added.
     */
    fun withPattern(pattern: String) = copy(excludePatterns = excludePatterns + pattern)

    /**
     * Returns config with pattern removed.
     */
    fun withoutPattern(pattern: String) = copy(excludePatterns = excludePatterns - pattern)

    /**
     * Checks if a symbol should be excluded based on patterns.
     */
    fun shouldExclude(symbolName: String): Boolean {
        return excludePatterns.any { pattern ->
            val regex = pattern.replace("*", ".*").toRegex(RegexOption.IGNORE_CASE)
            regex.matches(symbolName)
        }
    }

    /**
     * Checks if a symbol should be analyzed based on config.
     */
    fun shouldAnalyze(symbol: DeadCodeSymbol): Boolean {
        if (!enabled) return false
        if (symbol.type !in enabledTypes) return false
        if (shouldExclude(symbol.name)) return false
        if (symbol.confidence < minConfidence) return false

        return when (symbol.visibility) {
            SymbolVisibility.PRIVATE -> includePrivate
            SymbolVisibility.INTERNAL -> includeInternal
            SymbolVisibility.PUBLIC -> !excludePublicApi
            else -> true
        }
    }

    companion object {
        /** Disabled configuration */
        val DISABLED = DeadCodeConfig(enabled = false)

        /** Strict mode - all symbols */
        val STRICT = DeadCodeConfig(
            excludePublicApi = false,
            minConfidence = 0.5f
        )

        /** Conservative mode - only high confidence private */
        val CONSERVATIVE = DeadCodeConfig(
            includeInternal = false,
            excludePublicApi = true,
            minConfidence = 0.95f
        )
    }
}

/**
 * Result of dead code analysis.
 *
 * @property symbols List of dead code symbols found
 * @property totalSymbolsAnalyzed Total symbols scanned
 * @property totalLines Total lines in analyzed files
 * @property deadCodeLines Approximate lines of dead code
 * @property analysisTimeMs Time taken for analysis
 * @property scope Scope of the analysis (file, module, project)
 */
data class DeadCodeAnalysisResult(
    val symbols: List<DeadCodeSymbol>,
    val totalSymbolsAnalyzed: Int,
    val totalLines: Int,
    val deadCodeLines: Int,
    val analysisTimeMs: Long = 0,
    val scope: AnalysisScope = AnalysisScope.PROJECT
) {
    /**
     * Percentage of code that is dead.
     */
    val deadCodePercentage: Float
        get() = if (totalLines > 0) (deadCodeLines.toFloat() / totalLines * 100) else 0f

    /**
     * Grouping by symbol type.
     */
    val byType: Map<SymbolType, List<DeadCodeSymbol>>
        get() = symbols.groupBy { it.type }

    /**
     * Grouping by visibility.
     */
    val byVisibility: Map<SymbolVisibility, List<DeadCodeSymbol>>
        get() = symbols.groupBy { it.visibility }

    /**
     * Grouping by file.
     */
    val byFile: Map<String, List<DeadCodeSymbol>>
        get() = symbols.groupBy { it.location.filePath }

    /**
     * Symbols that can be safely deleted.
     */
    val safeToDelete: List<DeadCodeSymbol>
        get() = symbols.filter { it.canSafeDelete }

    /**
     * Count of symbols that can be safely deleted.
     */
    val safeDeleteCount: Int get() = safeToDelete.size

    /**
     * High confidence symbols only.
     */
    val highConfidence: List<DeadCodeSymbol>
        get() = symbols.filter { it.isHighConfidence }

    companion object {
        fun empty() = DeadCodeAnalysisResult(
            symbols = emptyList(),
            totalSymbolsAnalyzed = 0,
            totalLines = 0,
            deadCodeLines = 0
        )
    }
}

/**
 * Scope of dead code analysis.
 */
enum class AnalysisScope(val displayName: String) {
    /** Single file */
    FILE("File"),

    /** Module or package */
    MODULE("Module"),

    /** Entire project */
    PROJECT("Project");
}

/**
 * Summary statistics for dead code analysis.
 */
data class DeadCodeSummary(
    val totalSymbols: Int,
    val byType: Map<SymbolType, Int>,
    val byRisk: Map<DeletionRisk, Int>,
    val estimatedLines: Int,
    val safeDeleteCount: Int
) {
    /**
     * Whether there are symbols to clean up.
     */
    val hasDeadCode: Boolean get() = totalSymbols > 0

    /**
     * Most common symbol type.
     */
    val mostCommonType: SymbolType?
        get() = byType.maxByOrNull { it.value }?.key

    companion object {
        fun from(symbols: List<DeadCodeSymbol>): DeadCodeSummary {
            return DeadCodeSummary(
                totalSymbols = symbols.size,
                byType = symbols.groupingBy { it.type }.eachCount(),
                byRisk = symbols.groupingBy { DeletionRisk.fromSymbol(it) }.eachCount(),
                estimatedLines = symbols.sumOf { it.codeSize },
                safeDeleteCount = symbols.count { it.canSafeDelete }
            )
        }

        val EMPTY = DeadCodeSummary(0, emptyMap(), emptyMap(), 0, 0)
    }
}

/**
 * Filter options for dead code listing.
 */
enum class DeadCodeFilter(val displayName: String) {
    ALL("All"),
    SAFE_DELETE("Safe to Delete"),
    HIGH_CONFIDENCE("High Confidence"),
    CLASSES("Classes"),
    METHODS("Methods"),
    FIELDS("Fields"),
    IMPORTS("Imports"),
    LOW_RISK("Low Risk"),
    HIGH_RISK("High Risk");

    override fun toString(): String = displayName
}
