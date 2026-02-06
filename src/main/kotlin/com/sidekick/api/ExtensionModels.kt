// =============================================================================
// ExtensionModels.kt
// =============================================================================
// Public API models for third-party plugin extensions.
//
// This file contains all interfaces and data contracts for the Sidekick
// extension system:
// - SidekickExtension: Base interface for all extensions
// - PromptTemplateExtension: Custom prompt templates
// - AgentToolExtension: Custom agent tools
// - VisualExtension: Visual enhancements
//
// EXTENSION DEVELOPMENT:
// Third-party plugins can implement these interfaces to extend Sidekick's
// functionality without modifying core code.
//
// @since v1.0.4
// =============================================================================

package com.sidekick.api

import java.time.Instant

// =============================================================================
// Base Extension Interface
// =============================================================================

/**
 * Base interface for all Sidekick extensions.
 *
 * Extensions are third-party plugins that add functionality to Sidekick.
 * Each extension must have a unique ID and implement lifecycle methods.
 *
 * ## Lifecycle
 * 1. Extension is discovered via extension point or programmatic registration
 * 2. [initialize] is called when the extension is activated
 * 3. Extension provides its features via specialized interfaces
 * 4. [dispose] is called when the extension is deactivated
 *
 * ## Implementation
 * ```kotlin
 * class MyExtension : SidekickExtension {
 *     override val id = "com.example.my-extension"
 *     override val name = "My Extension"
 *     override val version = "1.0.0"
 *     override val description = "Adds cool features"
 *
 *     override fun initialize() { /* Setup */ }
 *     override fun dispose() { /* Cleanup */ }
 * }
 * ```
 *
 * @see PromptTemplateExtension
 * @see AgentToolExtension
 * @see VisualExtension
 */
interface SidekickExtension {
    /**
     * Unique identifier for this extension.
     * Should follow reverse domain notation (e.g., "com.example.my-extension")
     */
    val id: String

    /**
     * Human-readable name for display.
     */
    val name: String

    /**
     * Semantic version string (e.g., "1.0.0").
     */
    val version: String

    /**
     * Brief description of what this extension does.
     */
    val description: String

    /**
     * Called when the extension is activated.
     * Perform initialization, register listeners, etc.
     */
    fun initialize()

    /**
     * Called when the extension is deactivated.
     * Clean up resources, unregister listeners, etc.
     */
    fun dispose()
}

/**
 * Extension metadata for registration and display.
 *
 * @property id Unique extension identifier
 * @property name Human-readable name
 * @property version Semantic version
 * @property description Brief description
 * @property author Extension author/publisher
 * @property homepage URL for more information
 * @property registeredAt When the extension was registered
 */
data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String? = null,
    val homepage: String? = null,
    val registeredAt: Instant = Instant.now()
) {
    /**
     * Creates info from an extension instance.
     */
    companion object {
        fun from(extension: SidekickExtension) = ExtensionInfo(
            id = extension.id,
            name = extension.name,
            version = extension.version,
            description = extension.description
        )
    }

    /**
     * Formats for display.
     */
    fun format(): String = "$name v$version - $description"
}

// =============================================================================
// Prompt Template Extension
// =============================================================================

/**
 * Extension that provides custom prompt templates.
 *
 * Prompt templates allow users to create reusable prompts with
 * variable substitution for common tasks.
 *
 * ## Implementation
 * ```kotlin
 * class MyPromptExtension : PromptTemplateExtension {
 *     // ... base properties ...
 *
 *     override fun getTemplates() = listOf(
 *         CustomPromptTemplate(
 *             id = "refactor",
 *             name = "Refactor Code",
 *             template = "Refactor this code to {{goal}}:\n{{code}}",
 *             variables = listOf(
 *                 TemplateVariable("goal", "Refactoring goal", VariableType.STRING),
 *                 TemplateVariable("code", "Code to refactor", VariableType.CODE)
 *             )
 *         )
 *     )
 * }
 * ```
 */
interface PromptTemplateExtension : SidekickExtension {
    /**
     * Returns the list of prompt templates provided by this extension.
     */
    fun getTemplates(): List<CustomPromptTemplate>
}

/**
 * A custom prompt template with variable substitution.
 *
 * @property id Unique template identifier
 * @property name Human-readable template name
 * @property description What this template does
 * @property template The template string with {{variable}} placeholders
 * @property category Category for organization (e.g., "refactoring", "docs")
 * @property variables Variables that can be substituted
 */
data class CustomPromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val template: String,
    val category: String,
    val variables: List<TemplateVariable>
) {
    /**
     * Validates that all required variables are present.
     */
    fun validateVariables(values: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        variables.filter { it.required }.forEach { variable ->
            if (values[variable.name].isNullOrBlank()) {
                errors.add("Missing required variable: ${variable.name}")
            }
        }
        return errors
    }

    /**
     * Applies variable substitution to create the final prompt.
     */
    fun apply(values: Map<String, String>): String {
        var result = template
        variables.forEach { variable ->
            val value = values[variable.name] ?: variable.default ?: ""
            result = result.replace("{{${variable.name}}}", value)
        }
        return result
    }

    /**
     * Gets required variables.
     */
    val requiredVariables: List<TemplateVariable>
        get() = variables.filter { it.required }

    /**
     * Gets optional variables.
     */
    val optionalVariables: List<TemplateVariable>
        get() = variables.filter { !it.required }

    companion object {
        /**
         * Creates a simple template with no variables.
         */
        fun simple(id: String, name: String, template: String, category: String = "general") =
            CustomPromptTemplate(
                id = id,
                name = name,
                description = name,
                template = template,
                category = category,
                variables = emptyList()
            )
    }
}

/**
 * A variable in a prompt template.
 *
 * @property name Variable name (used in {{name}} placeholders)
 * @property description Human-readable description
 * @property type Type of value expected
 * @property required Whether this variable must be provided
 * @property default Default value if not provided
 */
data class TemplateVariable(
    val name: String,
    val description: String,
    val type: VariableType,
    val required: Boolean = true,
    val default: String? = null
) {
    /**
     * Validates a value for this variable.
     */
    fun validate(value: String?): String? {
        if (required && value.isNullOrBlank()) {
            return "Variable '$name' is required"
        }
        return null // No error
    }

    companion object {
        /**
         * Creates a required string variable.
         */
        fun string(name: String, description: String) =
            TemplateVariable(name, description, VariableType.STRING, true)

        /**
         * Creates a required code variable.
         */
        fun code(name: String, description: String = "Code block") =
            TemplateVariable(name, description, VariableType.CODE, true)

        /**
         * Creates an optional variable with default.
         */
        fun optional(name: String, description: String, default: String) =
            TemplateVariable(name, description, VariableType.STRING, false, default)
    }
}

/**
 * Types of template variables.
 */
enum class VariableType(val displayName: String, val placeholder: String) {
    /**
     * Plain text string.
     */
    STRING("Text", "Enter text..."),

    /**
     * Code block (preserves formatting).
     */
    CODE("Code", "Paste code..."),

    /**
     * File path.
     */
    FILE_PATH("File Path", "Select file..."),

    /**
     * Current editor selection.
     */
    SELECTION("Selection", "[Current selection]"),

    /**
     * Code symbol (class, function, etc.).
     */
    SYMBOL("Symbol", "Select symbol...");

    /**
     * Whether this type requires special handling.
     */
    val isSpecial: Boolean
        get() = this != STRING
}

// =============================================================================
// Agent Tool Extension
// =============================================================================

/**
 * Extension that provides custom agent tools.
 *
 * Agent tools are callable functions that extend the AI agent's
 * capabilities beyond built-in tools.
 *
 * ## Implementation
 * ```kotlin
 * class MyToolExtension : AgentToolExtension {
 *     // ... base properties ...
 *
 *     override fun getTools() = listOf(
 *         ExtensionTool(
 *             name = "search_docs",
 *             description = "Search documentation",
 *             parameters = mapOf(
 *                 "query" to ToolParameter("string", "Search query", true)
 *             ),
 *             handler = { params ->
 *                 val query = params["query"] as String
 *                 ToolResult(true, searchDocs(query))
 *             }
 *         )
 *     )
 * }
 * ```
 */
interface AgentToolExtension : SidekickExtension {
    /**
     * Returns the list of tools provided by this extension.
     */
    fun getTools(): List<ExtensionTool>
}

/**
 * A custom tool for the AI agent.
 *
 * @property name Tool name (used in function calls)
 * @property description Human-readable description for the AI
 * @property parameters Parameter definitions
 * @property handler Async function that executes the tool
 */
data class ExtensionTool(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>,
    val handler: suspend (Map<String, Any>) -> ToolResult
) {
    /**
     * Gets required parameters.
     */
    val requiredParameters: List<String>
        get() = parameters.filter { it.value.required }.keys.toList()

    /**
     * Validates parameters before execution.
     */
    fun validateParameters(params: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        parameters.filter { it.value.required }.forEach { (name, _) ->
            if (!params.containsKey(name)) {
                errors.add("Missing required parameter: $name")
            }
        }
        return errors
    }

    /**
     * Generates a schema for this tool (for LLM function calling).
     */
    fun toSchema(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "parameters" to mapOf(
            "type" to "object",
            "properties" to parameters.mapValues { (_, param) ->
                mapOf(
                    "type" to param.type,
                    "description" to param.description
                )
            },
            "required" to requiredParameters
        )
    )
}

/**
 * A parameter for a tool.
 *
 * @property type JSON type (string, number, boolean, array, object)
 * @property description Human-readable description
 * @property required Whether the parameter must be provided
 */
data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean
) {
    companion object {
        /**
         * Creates a required string parameter.
         */
        fun string(description: String) = ToolParameter("string", description, true)

        /**
         * Creates a required number parameter.
         */
        fun number(description: String) = ToolParameter("number", description, true)

        /**
         * Creates a required boolean parameter.
         */
        fun boolean(description: String) = ToolParameter("boolean", description, true)

        /**
         * Creates an optional parameter.
         */
        fun optional(type: String, description: String) = ToolParameter(type, description, false)
    }
}

/**
 * Result of a tool execution.
 *
 * @property success Whether the tool executed successfully
 * @property output Output from the tool (shown to AI)
 * @property error Error message if failed
 */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
) {
    companion object {
        /**
         * Creates a successful result.
         */
        fun success(output: String) = ToolResult(true, output, null)

        /**
         * Creates a failed result.
         */
        fun failure(error: String) = ToolResult(false, "", error)

        /**
         * Creates a result from a try-catch block.
         */
        inline fun <T> runCatching(block: () -> T): ToolResult {
            return try {
                val result = block()
                success(result.toString())
            } catch (e: Exception) {
                failure(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Formats for display.
     */
    fun format(): String = if (success) output else "Error: $error"
}

// =============================================================================
// Visual Extension
// =============================================================================

/**
 * Extension that provides visual enhancements.
 *
 * Visual extensions can add custom UI elements to the editor,
 * such as highlighters, markers, and gutter icons.
 *
 * ## Implementation
 * ```kotlin
 * class MyVisualExtension : VisualExtension {
 *     // ... base properties ...
 *
 *     override fun getEnhancements() = listOf(
 *         VisualEnhancement(
 *             id = "ai-suggestions",
 *             name = "AI Suggestions",
 *             type = EnhancementType.LINE_MARKER,
 *             renderer = MyLineMarkerProvider()
 *         )
 *     )
 * }
 * ```
 */
interface VisualExtension : SidekickExtension {
    /**
     * Returns the list of visual enhancements provided by this extension.
     */
    fun getEnhancements(): List<VisualEnhancement>
}

/**
 * A visual enhancement for the editor.
 *
 * @property id Unique enhancement identifier
 * @property name Human-readable name
 * @property type Type of visual enhancement
 * @property renderer The renderer object (type depends on enhancement type)
 * @property enabled Whether this enhancement is enabled
 */
data class VisualEnhancement(
    val id: String,
    val name: String,
    val type: EnhancementType,
    val renderer: Any,
    val enabled: Boolean = true
) {
    /**
     * Creates a copy with enabled/disabled state.
     */
    fun withEnabled(enabled: Boolean) = copy(enabled = enabled)

    /**
     * Formats for display.
     */
    fun format(): String = "$name (${type.displayName})"
}

/**
 * Types of visual enhancements.
 */
enum class EnhancementType(val displayName: String, val description: String) {
    /**
     * Syntax highlighter that colors code spans.
     */
    HIGHLIGHTER("Highlighter", "Custom syntax highlighting"),

    /**
     * Line marker that adds icons in the gutter.
     */
    LINE_MARKER("Line Marker", "Clickable gutter icons"),

    /**
     * Gutter icon that displays status or actions.
     */
    GUTTER_ICON("Gutter Icon", "Status indicators in gutter"),

    /**
     * Tab color that highlights editor tabs.
     */
    TAB_COLOR("Tab Color", "Editor tab coloring");

    /**
     * Whether this type requires editor integration.
     */
    val requiresEditorIntegration: Boolean
        get() = this != TAB_COLOR
}

// =============================================================================
// Extension Events
// =============================================================================

/**
 * Events emitted by the extension system.
 */
sealed class ExtensionEvent {
    /**
     * Fired when an extension is registered.
     */
    data class Registered(val info: ExtensionInfo) : ExtensionEvent()

    /**
     * Fired when an extension is unregistered.
     */
    data class Unregistered(val extensionId: String) : ExtensionEvent()

    /**
     * Fired when an extension initialization fails.
     */
    data class InitializationFailed(val extensionId: String, val error: String) : ExtensionEvent()
}
