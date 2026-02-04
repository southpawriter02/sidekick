// =============================================================================
// SidekickConfigurable.kt
// =============================================================================
// Settings UI panel for the Sidekick plugin.
//
// This class creates the settings page that appears under:
// Preferences/Settings → Tools → Sidekick
//
// DESIGN NOTES:
// - Uses IntelliJ's Kotlin UI DSL for declarative UI building
// - Implements Configurable for integration with IDE settings
// - Provides connection testing functionality
// - Loads available models from Ollama for dropdown selection
// =============================================================================

package com.sidekick.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import com.sidekick.core.SidekickBundle
import com.sidekick.services.ollama.OllamaService
import kotlinx.coroutines.*

/**
 * Configurable settings panel for the Sidekick plugin.
 *
 * Creates the UI that appears in IDE preferences under Tools → Sidekick.
 * Handles saving and loading settings, and provides connection testing.
 */
class SidekickConfigurable : BoundConfigurable(
    SidekickBundle.message("settings.title")
) {
    
    companion object {
        private val LOG = Logger.getInstance(SidekickConfigurable::class.java)
    }

    // -------------------------------------------------------------------------
    // Settings Reference
    // -------------------------------------------------------------------------
    
    private val settings = SidekickSettings.getInstance()
    
    // -------------------------------------------------------------------------
    // Coroutine Scope
    // -------------------------------------------------------------------------
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // -------------------------------------------------------------------------
    // Configurable Implementation
    // -------------------------------------------------------------------------
    
    override fun createPanel(): DialogPanel {
        LOG.debug("Creating Sidekick settings panel")
        
        return panel {
            // -----------------------------------------------------------------
            // Ollama Connection Section
            // -----------------------------------------------------------------
            group(SidekickBundle.message("settings.group.ollama")) {
                row(SidekickBundle.message("settings.ollama.url")) {
                    textField()
                        .bindText(settings::ollamaUrl)
                        .columns(COLUMNS_LARGE)
                        .comment(SidekickBundle.message("settings.ollama.url.comment"))
                        .validationOnApply {
                            if (it.text.isBlank()) {
                                error("Server URL cannot be empty")
                            } else if (!it.text.startsWith("http://") && !it.text.startsWith("https://")) {
                                error("URL must start with http:// or https://")
                            } else {
                                null
                            }
                        }
                }
                
                row {
                    button(SidekickBundle.message("settings.ollama.test")) {
                        testConnection()
                    }
                }
            }
            
            // -----------------------------------------------------------------
            // Model Settings Section
            // -----------------------------------------------------------------
            group(SidekickBundle.message("settings.group.model")) {
                row(SidekickBundle.message("settings.model.default")) {
                    textField()
                        .bindText(settings::defaultModel)
                        .columns(COLUMNS_MEDIUM)
                        .comment("e.g., llama3.2, codellama, mistral")
                }
                
                row(SidekickBundle.message("settings.model.temperature")) {
                    slider(0, 200, 10, 50)
                        .bindValue(
                            { (settings.temperature * 100).toInt() },
                            { settings.temperature = it / 100.0 }
                        )
                        .comment("0 = deterministic, 200 = very creative")
                }
                
                row("Max Tokens:") {
                    intTextField(1..32768)
                        .bindIntText(settings::maxTokens)
                        .columns(COLUMNS_SHORT)
                        .comment("Maximum tokens to generate per response")
                }
            }
            
            // -----------------------------------------------------------------
            // Advanced Settings Section
            // -----------------------------------------------------------------
            collapsibleGroup("Advanced") {
                row("System Prompt:") {
                    textArea()
                        .bindText(settings::systemPrompt)
                        .rows(4)
                        .columns(COLUMNS_LARGE)
                        .comment("Optional system prompt prepended to all conversations")
                }
                
                row {
                    checkBox("Enable streaming responses")
                        .bindSelected(settings::streamingEnabled)
                        .comment("Show response tokens as they're generated")
                }
                
                row {
                    checkBox("Auto-connect on startup")
                        .bindSelected(settings::autoConnect)
                        .comment("Automatically connect to Ollama when IDE starts")
                }
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // Connection Testing
    // -------------------------------------------------------------------------
    
    /**
     * Tests the connection to the Ollama server.
     */
    private fun testConnection() {
        LOG.info("Testing connection to Ollama...")
        
        scope.launch {
            try {
                val service = ApplicationManager.getApplication()
                    .getService(OllamaService::class.java)
                
                // Configure with the current URL (may not be applied yet)
                service.configure(settings.ollamaUrl)
                
                // Check connection
                val status = service.getConnectionStatus()
                
                // Get model count for success message
                val modelResult = service.listModels()
                
                withContext(Dispatchers.Main) {
                    if (status == com.sidekick.services.ollama.models.ConnectionStatus.CONNECTED) {
                        val modelCount = modelResult.getOrNull()?.size ?: 0
                        Messages.showInfoMessage(
                            "Successfully connected to Ollama!\n\nFound $modelCount model(s) available.",
                            "Connection Successful"
                        )
                    } else {
                        Messages.showWarningDialog(
                            "Could not connect to Ollama at:\n${settings.ollamaUrl}\n\n" +
                            "Make sure Ollama is running and the URL is correct.",
                            "Connection Failed"
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Connection test failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    Messages.showErrorDialog(
                        "Error testing connection:\n${e.message}",
                        "Connection Error"
                    )
                }
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------
    
    override fun disposeUIResources() {
        super.disposeUIResources()
        scope.cancel()
    }
}
