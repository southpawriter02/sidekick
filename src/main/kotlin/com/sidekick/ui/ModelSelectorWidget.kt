// =============================================================================
// ModelSelectorWidget.kt
// =============================================================================
// Dropdown widget for selecting the active Ollama model.
//
// This component provides a combo box that:
// - Loads available models from the Ollama server
// - Allows quick model switching without opening settings
// - Remembers the last selected model
// - Shows loading and error states
//
// DESIGN NOTES:
// - Uses coroutines for async model loading
// - Integrates with SidekickSettings for persistence
// - Provides refresh capability when models change
// =============================================================================

package com.sidekick.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.sidekick.services.ollama.OllamaService
import com.sidekick.settings.SidekickSettings
import kotlinx.coroutines.*
import javax.swing.SwingUtilities
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * Dropdown widget for selecting the active Ollama model.
 *
 * Provides quick model switching in the chat toolbar.
 * Automatically loads available models from the configured Ollama server.
 */
class ModelSelectorWidget : JPanel(BorderLayout()), Disposable {

    companion object {
        private val LOG = Logger.getInstance(ModelSelectorWidget::class.java)
        
        private const val LOADING_TEXT = "Loading..."
        private const val NO_MODELS_TEXT = "No models found"
        private const val ERROR_TEXT = "Error loading"
    }

    // -------------------------------------------------------------------------
    // Components
    // -------------------------------------------------------------------------
    
    /**
     * Label showing "Model:" prefix.
     */
    private val label = JBLabel("Model: ").apply {
        border = JBUI.Borders.emptyRight(4)
    }
    
    /**
     * Combo box for model selection.
     */
    private val comboBox = JComboBox<String>().apply {
        model = DefaultComboBoxModel(arrayOf(LOADING_TEXT))
        isEnabled = false
        
        addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                onModelSelected(event.item as? String)
            }
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    
    /**
     * Coroutine scope for async operations.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Currently loaded models.
     */
    private var availableModels: List<String> = emptyList()
    
    /**
     * Callback when a model is selected.
     */
    var onModelChanged: ((String) -> Unit)? = null

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    
    init {
        add(label, BorderLayout.WEST)
        add(comboBox, BorderLayout.CENTER)
        
        // Initial load
        refreshModels()
    }

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------
    
    /**
     * Refreshes the model list from the Ollama server.
     */
    fun refreshModels() {
        LOG.debug("Refreshing model list")
        
        comboBox.model = DefaultComboBoxModel(arrayOf(LOADING_TEXT))
        comboBox.isEnabled = false
        
        scope.launch {
            loadModels()
        }
    }
    
    /**
     * Gets the currently selected model name.
     *
     * @return The selected model name, or null if none selected
     */
    fun getSelectedModel(): String? {
        val selected = comboBox.selectedItem as? String
        return if (selected in listOf(LOADING_TEXT, NO_MODELS_TEXT, ERROR_TEXT)) {
            null
        } else {
            selected
        }
    }
    
    /**
     * Sets the selected model.
     *
     * @param modelName The model to select
     */
    fun setSelectedModel(modelName: String) {
        if (modelName in availableModels) {
            comboBox.selectedItem = modelName
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------
    
    /**
     * Loads models from the Ollama service.
     */
    private suspend fun loadModels() {
        try {
            val service = ApplicationManager.getApplication()
                .getService(OllamaService::class.java)
            
            val settings = SidekickSettings.getInstance()
            
            // Ensure service is configured
            service.configure(settings.ollamaUrl)
            
            val result = service.listModels()
            
            result
                .onSuccess { models ->
                    availableModels = models.map { it.name }
                    SwingUtilities.invokeLater {
                        updateComboBox(availableModels)
                        
                        // Select default model if configured
                        val defaultModel = settings.defaultModel
                        if (defaultModel.isNotEmpty() && defaultModel in availableModels) {
                            comboBox.selectedItem = defaultModel
                        } else if (availableModels.isNotEmpty()) {
                            comboBox.selectedIndex = 0
                        }
                    }
                    
                    LOG.debug("Loaded ${availableModels.size} models")
                }
                .onFailure { e ->
                    LOG.warn("Failed to load models: ${e.message}")
                    SwingUtilities.invokeLater {
                        comboBox.model = DefaultComboBoxModel(arrayOf(ERROR_TEXT))
                        comboBox.isEnabled = false
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Error loading models: ${e.message}", e)
            SwingUtilities.invokeLater {
                comboBox.model = DefaultComboBoxModel(arrayOf(ERROR_TEXT))
                comboBox.isEnabled = false
            }
        }
    }
    
    /**
     * Updates the combo box with the given model names.
     */
    private fun updateComboBox(models: List<String>) {
        if (models.isEmpty()) {
            comboBox.model = DefaultComboBoxModel(arrayOf(NO_MODELS_TEXT))
            comboBox.isEnabled = false
        } else {
            comboBox.model = DefaultComboBoxModel(models.toTypedArray())
            comboBox.isEnabled = true
        }
    }
    
    /**
     * Called when a model is selected from the dropdown.
     */
    private fun onModelSelected(modelName: String?) {
        if (modelName == null) return
        if (modelName in listOf(LOADING_TEXT, NO_MODELS_TEXT, ERROR_TEXT)) return
        
        LOG.debug("Model selected: $modelName")
        
        // Save as default
        SidekickSettings.getInstance().defaultModel = modelName
        
        // Notify listener
        onModelChanged?.invoke(modelName)
    }

    // -------------------------------------------------------------------------
    // Disposable Implementation
    // -------------------------------------------------------------------------
    
    override fun dispose() {
        LOG.debug("Disposing ModelSelectorWidget")
        scope.cancel()
    }
}
