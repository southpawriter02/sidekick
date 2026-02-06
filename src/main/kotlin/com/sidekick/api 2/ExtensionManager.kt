// =============================================================================
// ExtensionManager.kt
// =============================================================================
// Application-level service for managing Sidekick extensions.
//
// Provides:
// - Extension point aggregation
// - Programmatic extension registration
// - Extension lifecycle management
// - Extension discovery and querying
//
// @since v1.0.4
// =============================================================================

package com.sidekick.api

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application-level service for managing Sidekick extensions.
 *
 * Aggregates extensions from IntelliJ extension points and allows
 * programmatic registration for dynamic extensions.
 *
 * ## Usage
 * ```kotlin
 * val manager = ExtensionManager.getInstance()
 *
 * // Get all templates from all extensions
 * val templates = manager.getAllPromptTemplates()
 *
 * // Register a dynamic extension
 * manager.registerExtension(MyExtension())
 * ```
 *
 * ## Extension Points
 * Extensions can be registered via plugin.xml:
 * ```xml
 * <extensions defaultExtensionNs="com.sidekick">
 *     <promptTemplateExtension implementation="com.example.MyTemplates"/>
 * </extensions>
 * ```
 */
@Service(Service.Level.APP)
class ExtensionManager {

    private val log = Logger.getInstance(ExtensionManager::class.java)

    /**
     * Programmatically registered extensions (not from extension points).
     */
    private val loadedExtensions = ConcurrentHashMap<String, SidekickExtension>()

    /**
     * Extension info cache for quick lookups.
     */
    private val extensionInfoCache = ConcurrentHashMap<String, ExtensionInfo>()

    /**
     * Event listeners for extension lifecycle events.
     */
    private val eventListeners = CopyOnWriteArrayList<(ExtensionEvent) -> Unit>()

    companion object {
        /**
         * Extension point for prompt template extensions.
         */
        val PROMPT_TEMPLATE_EP: ExtensionPointName<PromptTemplateExtension> =
            ExtensionPointName.create("com.sidekick.promptTemplateExtension")

        /**
         * Extension point for agent tool extensions.
         */
        val AGENT_TOOL_EP: ExtensionPointName<AgentToolExtension> =
            ExtensionPointName.create("com.sidekick.agentToolExtension")

        /**
         * Extension point for visual extensions.
         */
        val VISUAL_EP: ExtensionPointName<VisualExtension> =
            ExtensionPointName.create("com.sidekick.visualExtension")

        /**
         * Gets the singleton ExtensionManager instance.
         */
        fun getInstance(): ExtensionManager {
            return ApplicationManager.getApplication().getService(ExtensionManager::class.java)
        }
    }

    // =========================================================================
    // Prompt Template Extensions
    // =========================================================================

    /**
     * Gets all prompt templates from all extensions.
     *
     * Includes both extension point extensions and programmatically registered ones.
     *
     * @return List of all available prompt templates
     */
    fun getAllPromptTemplates(): List<CustomPromptTemplate> {
        val templates = mutableListOf<CustomPromptTemplate>()

        // From extension points
        try {
            templates.addAll(PROMPT_TEMPLATE_EP.extensionList.flatMap { it.getTemplates() })
        } catch (e: Exception) {
            log.warn("Error loading prompt template extensions", e)
        }

        // From programmatic registration
        loadedExtensions.values
            .filterIsInstance<PromptTemplateExtension>()
            .forEach { templates.addAll(it.getTemplates()) }

        return templates
    }

    /**
     * Gets templates by category.
     */
    fun getPromptTemplatesByCategory(category: String): List<CustomPromptTemplate> =
        getAllPromptTemplates().filter { it.category == category }

    /**
     * Gets a template by ID.
     */
    fun getPromptTemplate(id: String): CustomPromptTemplate? =
        getAllPromptTemplates().find { it.id == id }

    /**
     * Gets all unique categories.
     */
    fun getPromptTemplateCategories(): List<String> =
        getAllPromptTemplates().map { it.category }.distinct().sorted()

    // =========================================================================
    // Agent Tool Extensions
    // =========================================================================

    /**
     * Gets all agent tools from all extensions.
     *
     * @return List of all available agent tools
     */
    fun getAllAgentTools(): List<ExtensionTool> {
        val tools = mutableListOf<ExtensionTool>()

        // From extension points
        try {
            tools.addAll(AGENT_TOOL_EP.extensionList.flatMap { it.getTools() })
        } catch (e: Exception) {
            log.warn("Error loading agent tool extensions", e)
        }

        // From programmatic registration
        loadedExtensions.values
            .filterIsInstance<AgentToolExtension>()
            .forEach { tools.addAll(it.getTools()) }

        return tools
    }

    /**
     * Gets a tool by name.
     */
    fun getAgentTool(name: String): ExtensionTool? =
        getAllAgentTools().find { it.name == name }

    /**
     * Gets all tool schemas for LLM function calling.
     */
    fun getToolSchemas(): List<Map<String, Any>> =
        getAllAgentTools().map { it.toSchema() }

    // =========================================================================
    // Visual Extensions
    // =========================================================================

    /**
     * Gets all visual enhancements from all extensions.
     *
     * @return List of all available visual enhancements
     */
    fun getAllVisualEnhancements(): List<VisualEnhancement> {
        val enhancements = mutableListOf<VisualEnhancement>()

        // From extension points
        try {
            enhancements.addAll(VISUAL_EP.extensionList.flatMap { it.getEnhancements() })
        } catch (e: Exception) {
            log.warn("Error loading visual extensions", e)
        }

        // From programmatic registration
        loadedExtensions.values
            .filterIsInstance<VisualExtension>()
            .forEach { enhancements.addAll(it.getEnhancements()) }

        return enhancements
    }

    /**
     * Gets enhancements by type.
     */
    fun getVisualEnhancementsByType(type: EnhancementType): List<VisualEnhancement> =
        getAllVisualEnhancements().filter { it.type == type }

    /**
     * Gets a visual enhancement by ID.
     */
    fun getVisualEnhancement(id: String): VisualEnhancement? =
        getAllVisualEnhancements().find { it.id == id }

    // =========================================================================
    // Extension Registration
    // =========================================================================

    /**
     * Registers an extension programmatically.
     *
     * The extension's [SidekickExtension.initialize] method is called.
     *
     * @param extension The extension to register
     * @return true if registration succeeded
     */
    fun registerExtension(extension: SidekickExtension): Boolean {
        if (loadedExtensions.containsKey(extension.id)) {
            log.warn("Extension already registered: ${extension.id}")
            return false
        }

        try {
            extension.initialize()
            loadedExtensions[extension.id] = extension

            val info = ExtensionInfo.from(extension)
            extensionInfoCache[extension.id] = info

            log.info("Registered extension: ${extension.id}")
            notifyListeners(ExtensionEvent.Registered(info))

            return true
        } catch (e: Exception) {
            log.error("Failed to initialize extension: ${extension.id}", e)
            notifyListeners(ExtensionEvent.InitializationFailed(extension.id, e.message ?: "Unknown error"))
            return false
        }
    }

    /**
     * Unregisters an extension.
     *
     * The extension's [SidekickExtension.dispose] method is called.
     *
     * @param extensionId ID of the extension to unregister
     * @return true if the extension was found and unregistered
     */
    fun unregisterExtension(extensionId: String): Boolean {
        val extension = loadedExtensions.remove(extensionId)
        if (extension == null) {
            log.warn("Extension not found: $extensionId")
            return false
        }

        try {
            extension.dispose()
        } catch (e: Exception) {
            log.warn("Error disposing extension: $extensionId", e)
        }

        extensionInfoCache.remove(extensionId)
        log.info("Unregistered extension: $extensionId")
        notifyListeners(ExtensionEvent.Unregistered(extensionId))

        return true
    }

    /**
     * Checks if an extension is registered.
     */
    fun isExtensionRegistered(extensionId: String): Boolean =
        loadedExtensions.containsKey(extensionId)

    /**
     * Gets info about a registered extension.
     */
    fun getExtensionInfo(extensionId: String): ExtensionInfo? =
        extensionInfoCache[extensionId]

    /**
     * Gets all registered extensions info.
     */
    fun getAllExtensionInfo(): List<ExtensionInfo> =
        extensionInfoCache.values.toList()

    /**
     * Gets the count of registered extensions.
     */
    fun getExtensionCount(): Int = loadedExtensions.size

    // =========================================================================
    // Event Listeners
    // =========================================================================

    /**
     * Adds an event listener for extension lifecycle events.
     */
    fun addListener(listener: (ExtensionEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (ExtensionEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    private fun notifyListeners(event: ExtensionEvent) {
        eventListeners.forEach { listener ->
            try {
                listener(event)
            } catch (e: Exception) {
                log.warn("Error notifying extension listener", e)
            }
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Disposes all programmatically registered extensions.
     */
    fun disposeAll() {
        loadedExtensions.keys.toList().forEach { id ->
            unregisterExtension(id)
        }
    }

    /**
     * Reloads all extensions from extension points.
     */
    fun reload() {
        log.info("Reloading extensions")
        // Extension points reload automatically
        // Just clear caches
        extensionInfoCache.clear()
        loadedExtensions.forEach { (id, ext) ->
            extensionInfoCache[id] = ExtensionInfo.from(ext)
        }
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Generates a report of all loaded extensions.
     */
    fun getExtensionReport(): String = buildString {
        appendLine("=== Extension Report ===")
        appendLine()
        appendLine("Registered Extensions: ${loadedExtensions.size}")
        if (loadedExtensions.isNotEmpty()) {
            loadedExtensions.values.forEach { ext ->
                appendLine("  - ${ext.name} (${ext.id}) v${ext.version}")
            }
        }
        appendLine()

        val templates = getAllPromptTemplates()
        appendLine("Prompt Templates: ${templates.size}")
        if (templates.isNotEmpty()) {
            templates.take(5).forEach { t ->
                appendLine("  - ${t.name} [${t.category}]")
            }
            if (templates.size > 5) appendLine("  ... and ${templates.size - 5} more")
        }
        appendLine()

        val tools = getAllAgentTools()
        appendLine("Agent Tools: ${tools.size}")
        if (tools.isNotEmpty()) {
            tools.take(5).forEach { t ->
                appendLine("  - ${t.name}")
            }
            if (tools.size > 5) appendLine("  ... and ${tools.size - 5} more")
        }
        appendLine()

        val visuals = getAllVisualEnhancements()
        appendLine("Visual Enhancements: ${visuals.size}")
        if (visuals.isNotEmpty()) {
            visuals.take(5).forEach { v ->
                appendLine("  - ${v.name} (${v.type.displayName})")
            }
            if (visuals.size > 5) appendLine("  ... and ${visuals.size - 5} more")
        }
    }
}
