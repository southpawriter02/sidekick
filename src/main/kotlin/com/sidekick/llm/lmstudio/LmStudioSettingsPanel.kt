package com.sidekick.llm.lmstudio

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import javax.swing.*

/**
 * # LM Studio Settings Panel
 *
 * Settings configurable for LM Studio integration.
 * Part of Sidekick v0.8.1 LM Studio Connection feature.
 *
 * ## Features
 *
 * - Connection settings (host, port)
 * - Auto-connect and auto-discover options
 * - Model selection
 * - Connection testing
 * - Server discovery
 *
 * @since 0.8.1
 */
class LmStudioSettingsConfigurable : Configurable {

    private val service = LmStudioService.getInstance()

    // UI Components
    private var hostField = JBTextField()
    private var portField = JBTextField()
    private var autoConnectCheckbox = JBCheckBox("Auto-connect on startup")
    private var autoDiscoverCheckbox = JBCheckBox("Auto-discover servers")
    private var connectionTimeoutField = JBTextField()
    private var requestTimeoutField = JBTextField()
    private var modelComboBox = com.intellij.openapi.ui.ComboBox<String>()
    private var statusLabel = JBLabel("Not connected")

    private var mainPanel: DialogPanel? = null

    override fun getDisplayName(): String = "LM Studio"

    override fun createComponent(): JComponent {
        loadCurrentSettings()

        mainPanel = panel {
            group("Connection") {
                row("Host:") {
                    cell(hostField)
                        .columns(COLUMNS_MEDIUM)
                        .comment("LM Studio server hostname")
                }
                row("Port:") {
                    cell(portField)
                        .columns(COLUMNS_SHORT)
                        .comment("Default: 1234")
                }
                row {
                    cell(autoConnectCheckbox)
                }
                row {
                    cell(autoDiscoverCheckbox)
                }
                row {
                    button("Test Connection") { testConnection() }
                    button("Discover Servers") { discoverServers() }
                }
                row {
                    cell(statusLabel)
                        .comment("Connection status")
                }
            }

            group("Model Selection") {
                row("Preferred Model:") {
                    cell(modelComboBox)
                        .columns(COLUMNS_LARGE)
                }
                row {
                    button("Refresh Models") { refreshModels() }
                }
            }

            group("Timeouts") {
                row("Connection Timeout (ms):") {
                    cell(connectionTimeoutField)
                        .columns(COLUMNS_SHORT)
                }
                row("Request Timeout (ms):") {
                    cell(requestTimeoutField)
                        .columns(COLUMNS_SHORT)
                }
            }
        }

        return mainPanel!!
    }

    private fun loadCurrentSettings() {
        val config = service.config
        hostField.text = config.host
        portField.text = config.port.toString()
        autoConnectCheckbox.isSelected = config.autoConnect
        autoDiscoverCheckbox.isSelected = config.autoDiscover
        connectionTimeoutField.text = config.connectionTimeoutMs.toString()
        requestTimeoutField.text = config.requestTimeoutMs.toString()

        // Load models
        refreshModels()
    }

    private fun testConnection() {
        statusLabel.text = "Testing..."
        statusLabel.icon = null

        Thread {
            runBlocking {
                val status = service.checkConnection()
                SwingUtilities.invokeLater {
                    statusLabel.text = status.displayStatus
                    statusLabel.icon = null
                }
            }
        }.start()
    }

    private fun discoverServers() {
        statusLabel.text = "Discovering..."

        Thread {
            runBlocking {
                val result = service.discover()
                SwingUtilities.invokeLater {
                    if (result.hasServers) {
                        val server = result.servers.first()
                        hostField.text = server.host
                        portField.text = server.port.toString()
                        statusLabel.text = "Found ${result.servers.size} server(s)"
                    } else {
                        statusLabel.text = "No servers found"
                    }
                }
            }
        }.start()
    }

    private fun refreshModels() {
        modelComboBox.removeAllItems()
        modelComboBox.addItem("(Loading...)")

        Thread {
            runBlocking {
                val models = service.listModels(forceRefresh = true)
                SwingUtilities.invokeLater {
                    modelComboBox.removeAllItems()
                    if (models.isEmpty()) {
                        modelComboBox.addItem("(No models available)")
                    } else {
                        models.forEach { model ->
                            modelComboBox.addItem(model.id)
                        }
                        // Select preferred if exists
                        service.preferredModel?.let { preferred ->
                            modelComboBox.selectedItem = preferred
                        }
                    }
                }
            }
        }.start()
    }

    override fun isModified(): Boolean {
        val config = service.config
        return hostField.text != config.host ||
                portField.text != config.port.toString() ||
                autoConnectCheckbox.isSelected != config.autoConnect ||
                autoDiscoverCheckbox.isSelected != config.autoDiscover ||
                connectionTimeoutField.text != config.connectionTimeoutMs.toString() ||
                requestTimeoutField.text != config.requestTimeoutMs.toString() ||
                modelComboBox.selectedItem?.toString() != service.preferredModel
    }

    override fun apply() {
        val newConfig = LmStudioConfig(
            host = hostField.text,
            port = portField.text.toIntOrNull() ?: 1234,
            autoConnect = autoConnectCheckbox.isSelected,
            autoDiscover = autoDiscoverCheckbox.isSelected,
            connectionTimeoutMs = connectionTimeoutField.text.toLongOrNull() ?: 5000,
            requestTimeoutMs = requestTimeoutField.text.toLongOrNull() ?: 120000
        )

        val selectedModel = modelComboBox.selectedItem?.toString()?.takeIf {
            !it.startsWith("(")
        }

        service.updateConfig(newConfig, selectedModel)
    }

    override fun reset() {
        loadCurrentSettings()
    }
}

/**
 * Tool window for LM Studio status and quick actions.
 */
class LmStudioStatusPanel : JBPanel<LmStudioStatusPanel>() {

    private val service = LmStudioService.getInstance()
    private val statusLabel = JBLabel()
    private val modelLabel = JBLabel()
    private val connectButton = JButton("Connect")

    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(8)

        add(createStatusPanel(), BorderLayout.NORTH)
        add(createActionsPanel(), BorderLayout.CENTER)

        refresh()
    }

    private fun createStatusPanel(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("LM Studio").apply { font = font.deriveFont(14f) })
            add(Box.createVerticalStrut(4))
            add(statusLabel)
            add(modelLabel)
        }
    }

    private fun createActionsPanel(): JPanel {
        return JPanel().apply {
            add(connectButton.apply {
                addActionListener { toggleConnection() }
            })
            add(JButton("Refresh").apply {
                addActionListener { refresh() }
            })
        }
    }

    private fun refresh() {
        val status = service.getStatus()
        statusLabel.text = "Status: ${status.displayStatus}"
        modelLabel.text = "Model: ${status.loadedModel ?: "None"}"
        connectButton.text = if (status.connected) "Disconnect" else "Connect"
    }

    private fun toggleConnection() {
        Thread {
            runBlocking {
                if (service.getStatus().connected) {
                    service.disconnect()
                } else {
                    service.connect()
                }
                SwingUtilities.invokeLater { refresh() }
            }
        }.start()
    }
}
