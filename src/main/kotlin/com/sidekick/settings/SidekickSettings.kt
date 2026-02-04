// =============================================================================
// SidekickSettings.kt
// =============================================================================
// Persistent settings for the Sidekick plugin.
//
// This service stores user configuration that persists across IDE restarts:
// - Ollama server URL
// - Default model selection
// - Generation parameters (temperature, max tokens, etc.)
// - UI preferences
//
// DESIGN NOTES:
// - Application-level settings (shared across all projects)
// - Stored in sidekick.xml in the IDE config directory
// - Uses IntelliJ's PersistentStateComponent for automatic serialization
// - Settings changes are notified via SettingsChangeListener
// =============================================================================

package com.sidekick.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persistent settings service for the Sidekick plugin.
 *
 * Stores user preferences and configuration that persists across IDE sessions.
 * Access via [getInstance] static method.
 *
 * ## Example Usage
 *
 * ```kotlin
 * val settings = SidekickSettings.getInstance()
 * val url = settings.ollamaUrl
 * settings.defaultModel = "llama3.2"
 * ```
 */
@Service
@State(
    name = "SidekickSettings",
    storages = [Storage("sidekick.xml")]
)
class SidekickSettings : PersistentStateComponent<SidekickSettings.State> {

    companion object {
        private val LOG = Logger.getInstance(SidekickSettings::class.java)
        
        /**
         * Default Ollama server URL.
         */
        const val DEFAULT_OLLAMA_URL = "http://localhost:11434"
        
        /**
         * Default temperature for generation.
         */
        const val DEFAULT_TEMPERATURE = 0.7
        
        /**
         * Default maximum tokens for generation.
         */
        const val DEFAULT_MAX_TOKENS = 2048
        
        /**
         * Gets the singleton settings instance.
         */
        fun getInstance(): SidekickSettings {
            return ApplicationManager.getApplication().getService(SidekickSettings::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // State Class
    // -------------------------------------------------------------------------
    
    /**
     * Serializable state container for settings.
     *
     * All fields use `var` for XML serialization compatibility.
     * Default values are applied on first run.
     */
    data class State(
        /**
         * Ollama server URL.
         */
        var ollamaUrl: String = DEFAULT_OLLAMA_URL,
        
        /**
         * Default model to use for chat.
         * Empty string means "use first available".
         */
        var defaultModel: String = "",
        
        /**
         * Whether streaming is enabled for responses.
         */
        var streamingEnabled: Boolean = true,
        
        /**
         * Generation temperature (0.0 - 1.0).
         * Higher values = more creative, lower = more deterministic.
         */
        var temperature: Double = DEFAULT_TEMPERATURE,
        
        /**
         * Maximum tokens to generate per response.
         */
        var maxTokens: Int = DEFAULT_MAX_TOKENS,
        
        /**
         * System prompt to prepend to conversations.
         * Empty string means no system prompt.
         */
        var systemPrompt: String = "",
        
        /**
         * Whether to auto-connect on plugin startup.
         */
        var autoConnect: Boolean = true
    )

    // -------------------------------------------------------------------------
    // Instance State
    // -------------------------------------------------------------------------
    
    /**
     * The current settings state.
     */
    private var state = State()
    
    /**
     * Listeners for settings changes.
     */
    private val changeListeners = CopyOnWriteArrayList<SettingsChangeListener>()

    // -------------------------------------------------------------------------
    // PersistentStateComponent Implementation
    // -------------------------------------------------------------------------
    
    /**
     * Returns the current state for serialization.
     */
    override fun getState(): State = state
    
    /**
     * Loads state from persisted XML.
     */
    override fun loadState(state: State) {
        LOG.debug("Loading Sidekick settings")
        XmlSerializerUtil.copyBean(state, this.state)
    }

    // -------------------------------------------------------------------------
    // Convenience Accessors
    // -------------------------------------------------------------------------
    
    /**
     * The Ollama server URL.
     */
    var ollamaUrl: String
        get() = state.ollamaUrl
        set(value) {
            if (state.ollamaUrl != value) {
                state.ollamaUrl = value
                notifyListeners()
            }
        }
    
    /**
     * The default model to use.
     */
    var defaultModel: String
        get() = state.defaultModel
        set(value) {
            if (state.defaultModel != value) {
                state.defaultModel = value
                notifyListeners()
            }
        }
    
    /**
     * Whether streaming is enabled.
     */
    var streamingEnabled: Boolean
        get() = state.streamingEnabled
        set(value) {
            if (state.streamingEnabled != value) {
                state.streamingEnabled = value
                notifyListeners()
            }
        }
    
    /**
     * The generation temperature.
     */
    var temperature: Double
        get() = state.temperature
        set(value) {
            val clamped = value.coerceIn(0.0, 2.0)
            if (state.temperature != clamped) {
                state.temperature = clamped
                notifyListeners()
            }
        }
    
    /**
     * The maximum tokens to generate.
     */
    var maxTokens: Int
        get() = state.maxTokens
        set(value) {
            val clamped = value.coerceIn(1, 32768)
            if (state.maxTokens != clamped) {
                state.maxTokens = clamped
                notifyListeners()
            }
        }
    
    /**
     * The system prompt.
     */
    var systemPrompt: String
        get() = state.systemPrompt
        set(value) {
            if (state.systemPrompt != value) {
                state.systemPrompt = value
                notifyListeners()
            }
        }
    
    /**
     * Whether to auto-connect on startup.
     */
    var autoConnect: Boolean
        get() = state.autoConnect
        set(value) {
            if (state.autoConnect != value) {
                state.autoConnect = value
                notifyListeners()
            }
        }

    // -------------------------------------------------------------------------
    // Change Listeners
    // -------------------------------------------------------------------------
    
    /**
     * Adds a listener for settings changes.
     */
    fun addChangeListener(listener: SettingsChangeListener) {
        changeListeners.add(listener)
    }
    
    /**
     * Removes a settings change listener.
     */
    fun removeChangeListener(listener: SettingsChangeListener) {
        changeListeners.remove(listener)
    }
    
    /**
     * Notifies all listeners of a settings change.
     */
    private fun notifyListeners() {
        LOG.debug("Settings changed, notifying ${changeListeners.size} listeners")
        changeListeners.forEach { it.onSettingsChanged(this) }
    }
    
    /**
     * Listener interface for settings changes.
     */
    fun interface SettingsChangeListener {
        fun onSettingsChanged(settings: SidekickSettings)
    }
}
