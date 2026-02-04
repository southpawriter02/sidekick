// =============================================================================
// SidekickPlugin.kt
// =============================================================================
// The main entry point and lifecycle manager for the Sidekick plugin.
//
// This application-level service is instantiated once when the IDE starts
// (or when the plugin is dynamically loaded). It serves as the central
// coordination point for all Sidekick functionality.
//
// DESIGN NOTES:
// - We use IntelliJ's @Service annotation for automatic DI registration
// - Logging uses IntelliJ's Logger, which integrates with IDE log settings
// - This is an APPLICATION service, not PROJECT - one instance for the IDE
//
// FUTURE VERSIONS:
// - v0.1.1: Will add OllamaService initialization here
// - v0.1.2: Will add ChatController coordination
// - v0.1.3: Will add settings change listeners
// =============================================================================

package com.sidekick

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

/**
 * Main application service for the Sidekick plugin.
 *
 * This service manages the plugin lifecycle and provides a central access point
 * for other components. As an application-level service, there is exactly one
 * instance shared across all projects in the IDE.
 *
 * ## Accessing This Service
 *
 * From Kotlin:
 * ```kotlin
 * val sidekick = SidekickPlugin.getInstance()
 * ```
 *
 * From Java:
 * ```java
 * SidekickPlugin sidekick = SidekickPlugin.getInstance();
 * ```
 *
 * ## Lifecycle
 *
 * 1. **Creation**: When the IDE starts or plugin loads
 * 2. **Active**: Throughout the IDE session
 * 3. **Disposal**: When the IDE shuts down or plugin unloads
 *
 * @see com.intellij.openapi.components.Service
 */
@Service
class SidekickPlugin {

    // -------------------------------------------------------------------------
    // Companion Object - Static Access & Constants
    // -------------------------------------------------------------------------
    
    companion object {
        /**
         * Logger instance for the Sidekick plugin.
         *
         * Uses IntelliJ's logging infrastructure, which:
         * - Writes to idea.log in the IDE's log directory
         * - Respects the user's log level settings
         * - Can be viewed via Help → Show Log in Finder/Explorer
         *
         * Log levels:
         * - LOG.trace() - Very detailed debugging (usually disabled)
         * - LOG.debug() - Development debugging
         * - LOG.info()  - Normal operational messages
         * - LOG.warn()  - Potential issues that don't prevent operation
         * - LOG.error() - Errors that need attention
         */
        private val LOG = Logger.getInstance(SidekickPlugin::class.java)

        /**
         * Plugin version constant.
         * This should match the version in build.gradle.kts and plugin.xml.
         */
        const val VERSION = "0.1.0"

        /**
         * Retrieves the singleton instance of the Sidekick plugin service.
         *
         * This is the canonical way to access the plugin from anywhere in the
         * codebase. The ApplicationManager handles service lifecycle and ensures
         * thread-safe access.
         *
         * @return The SidekickPlugin application service instance
         * @throws IllegalStateException if called before the application is initialized
         */
        fun getInstance(): SidekickPlugin {
            return ApplicationManager.getApplication().getService(SidekickPlugin::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Instance State
    // -------------------------------------------------------------------------
    
    /**
     * Flag indicating whether the plugin has completed initialization.
     * This can be used by other components to check readiness.
     */
    private var initialized: Boolean = false

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    
    /**
     * Service constructor - called by the IntelliJ Platform when creating the service.
     *
     * This is invoked lazily on first access, or eagerly if configured in plugin.xml.
     * Keep initialization lightweight here; heavy setup should be deferred.
     */
    init {
        LOG.info("=".repeat(60))
        LOG.info("Sidekick Plugin v$VERSION - Initializing")
        LOG.info("=".repeat(60))
        
        // Log some diagnostic information that helps with troubleshooting
        LOG.info("Sidekick is a local LLM coding companion for JetBrains Rider")
        LOG.info("For issues and documentation: https://github.com/ryan/sidekick")
        
        // Mark initialization as complete
        // In future versions, this will happen after async setup completes
        initialized = true
        
        LOG.info("Sidekick Plugin initialization complete")
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    
    /**
     * Checks if the plugin has completed its initialization sequence.
     *
     * Other components can use this to verify the plugin is ready before
     * attempting to use its services.
     *
     * @return true if initialization is complete, false otherwise
     */
    fun isInitialized(): Boolean = initialized
}
