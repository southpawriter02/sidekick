// =============================================================================
// ModelSelectorWidget.kt
// =============================================================================
// Dropdown widget for selecting the active LLM model.
//
// This component provides a combo box that:
// - Loads available models from all connected LLM providers
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.UnifiedModel
import com.sidekick.settings.SidekickSettings
import kotlinx.coroutines.*
import javax.swing.SwingUtilities
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * Dropdown widget for selecting the active LLM model.
 *
 * Provides quick model switching in the chat toolbar.
 * Automatically loads available models from all connected providers.
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
     * Currently loaded models from all providers.
     */
    private var availableModels: List<UnifiedModel> = emptyList()

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
     * Refreshes the model list from all available providers.
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
     * Gets the currently selected model ID.
     *
     * @return The selected model ID, or null if none selected
     */
    fun getSelectedModel(): String? {
        val selected = comboBox.selectedItem as? String
        if (selected in listOf(LOADING_TEXT, NO_MODELS_TEXT, ERROR_TEXT)) return null

        val selectedIndex = comboBox.selectedIndex
        return availableModels.getOrNull(selectedIndex)?.id
    }

    /**
     * Sets the selected model by ID.
     *
     * @param modelName The model ID to select
     */
    fun setSelectedModel(modelName: String) {
        val index = availableModels.indexOfFirst { it.id == modelName }
        if (index >= 0) {
            comboBox.selectedIndex = index
        }
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------

    /**
     * Loads models from all available providers via ProviderManager.
     */
    private suspend fun loadModels() {
        try {
            val providerManager = ProviderManager.getInstance()
            val models = providerManager.listAllModels()

            availableModels = models

            // Build display names with provider prefix for disambiguation
            val displayNames = models.map { model ->
                "${model.provider.displayName}: ${model.displayName}"
            }

            SwingUtilities.invokeLater {
                updateComboBox(displayNames)

                // Select default model if configured
                val settings = SidekickSettings.getInstance()
                val defaultModel = settings.defaultModel
                if (defaultModel.isNotEmpty()) {
                    val matchIndex = models.indexOfFirst { it.id == defaultModel }
                    if (matchIndex >= 0) {
                        comboBox.selectedIndex = matchIndex
                    } else if (displayNames.isNotEmpty()) {
                        comboBox.selectedIndex = 0
                    }
                } else if (displayNames.isNotEmpty()) {
                    comboBox.selectedIndex = 0
                }
            }

            LOG.debug("Loaded ${models.size} models from ${models.map { it.provider }.distinct().size} providers")

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

        val selectedIndex = comboBox.selectedIndex
        val model = availableModels.getOrNull(selectedIndex) ?: return

        LOG.debug("Model selected: ${model.displayName} from ${model.provider.displayName}")

        // Set the active provider to match the selected model's provider
        ProviderManager.getInstance().setActiveProvider(model.provider)

        // Save model ID as default
        SidekickSettings.getInstance().defaultModel = model.id

        // Notify listener with the model ID
        onModelChanged?.invoke(model.id)
    }

    // -------------------------------------------------------------------------
    // Disposable Implementation
    // -------------------------------------------------------------------------

    override fun dispose() {
        LOG.debug("Disposing ModelSelectorWidget")
        scope.cancel()
    }
}
