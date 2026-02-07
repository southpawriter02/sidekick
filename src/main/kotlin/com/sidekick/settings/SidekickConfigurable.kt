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
// - Provides connection testing for all providers via ProviderManager
// - Unified settings for Ollama and LM Studio on the same page
// =============================================================================

package com.sidekick.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import com.sidekick.core.SidekickBundle
import com.sidekick.llm.lmstudio.LmStudioConfig
import com.sidekick.llm.lmstudio.LmStudioService
import com.sidekick.llm.provider.ProviderManager
import com.sidekick.llm.provider.ProviderType
import kotlinx.coroutines.*

/**
 * Configurable settings panel for the Sidekick plugin.
 *
 * Creates the UI that appears in IDE preferences under Tools → Sidekick.
 * Handles saving and loading settings, and provides connection testing
 * for all registered LLM providers.
 */
class SidekickConfigurable : BoundConfigurable(
    SidekickBundle.message("settings.title")
) {

    companion object {
        private val LOG = Logger.getInstance(SidekickConfigurable::class.java)
    }

    // -------------------------------------------------------------------------
    // Settings References
    // -------------------------------------------------------------------------

    private val settings = SidekickSettings.getInstance()
    private val lmStudioService = LmStudioService.getInstance()

    // Mutable copies for LM Studio URL binding (combined host:port → URL)
    private var lmStudioUrl: String
        get() {
            val config = lmStudioService.config
            return "http://${config.host}:${config.port}"
        }
        set(value) {
            // Parse URL into host and port
            val parsed = parseUrl(value)
            val newConfig = LmStudioConfig(
                host = parsed.first,
                port = parsed.second,
                autoConnect = lmStudioService.config.autoConnect,
                autoDiscover = lmStudioService.config.autoDiscover,
                connectionTimeoutMs = lmStudioService.config.connectionTimeoutMs,
                requestTimeoutMs = lmStudioService.config.requestTimeoutMs
            )
            lmStudioService.updateConfig(newConfig)
        }

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
            group("Ollama Connection") {
                row("Server URL:") {
                    textField()
                        .bindText(settings::ollamaUrl)
                        .columns(COLUMNS_LARGE)
                        .comment("Default: http://localhost:11434")
                        .validationOnApply {
                            validateUrl(it.text)
                        }
                }

                row {
                    button("Test Connection") {
                        testProviderConnection(ProviderType.OLLAMA)
                    }
                }
            }

            // -----------------------------------------------------------------
            // LM Studio Connection Section
            // -----------------------------------------------------------------
            group("LM Studio Connection") {
                row("Server URL:") {
                    textField()
                        .bindText(::lmStudioUrl)
                        .columns(COLUMNS_LARGE)
                        .comment("Default: http://localhost:1234")
                        .validationOnApply {
                            validateUrl(it.text)
                        }
                }

                row {
                    button("Test Connection") {
                        testProviderConnection(ProviderType.LM_STUDIO)
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
                        .comment("Automatically connect to providers when IDE starts")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection Testing
    // -------------------------------------------------------------------------

    /**
     * Tests the connection to a specific provider.
     */
    private fun testProviderConnection(providerType: ProviderType) {
        LOG.info("Testing connection to ${providerType.displayName}...")

        scope.launch {
            try {
                val providerManager = ProviderManager.getInstance()
                val provider = providerManager.getProvider(providerType)

                if (provider == null) {
                    withContext(Dispatchers.Main) {
                        Messages.showWarningDialog(
                            "${providerType.displayName} provider is not registered.",
                            "Provider Not Found"
                        )
                    }
                    return@launch
                }

                val health = provider.checkHealth()
                val models = try { provider.listModels() } catch (_: Exception) { emptyList() }

                withContext(Dispatchers.Main) {
                    if (health.healthy) {
                        Messages.showInfoMessage(
                            "Successfully connected to ${providerType.displayName}!\n\n" +
                            "Found ${models.size} model(s) available.",
                            "Connection Successful"
                        )
                    } else {
                        Messages.showWarningDialog(
                            "Could not connect to ${providerType.displayName}.\n\n" +
                            "Error: ${health.error ?: "Unknown error"}\n\n" +
                            "Make sure the server is running and the URL is correct.",
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
    // Validation
    // -------------------------------------------------------------------------

    private fun validateUrl(url: String): com.intellij.openapi.ui.ValidationInfo? {
        return if (url.isBlank()) {
            com.intellij.openapi.ui.ValidationInfo("Server URL cannot be empty")
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            com.intellij.openapi.ui.ValidationInfo("URL must start with http:// or https://")
        } else {
            null
        }
    }

    // -------------------------------------------------------------------------
    // URL Parsing
    // -------------------------------------------------------------------------

    private fun parseUrl(url: String): Pair<String, Int> {
        return try {
            val parsed = java.net.URL(url)
            val host = parsed.host ?: "localhost"
            val port = if (parsed.port > 0) parsed.port else 1234
            host to port
        } catch (_: Exception) {
            "localhost" to 1234
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
