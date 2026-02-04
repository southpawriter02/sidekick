# Sidekick v0.1.x Design Specification

> **Phase 0: Foundation**
> Establish plugin skeleton, core infrastructure, and basic Ollama connectivity.

---

## Version Overview

| Version | Focus | Duration |
|---------|-------|----------|
| **v0.1.0** | Plugin Skeleton & Project Setup | 3-4 days |
| **v0.1.1** | Ollama Client Infrastructure | 4-5 days |
| **v0.1.2** | Chat Tool Window UI | 4-5 days |
| **v0.1.3** | Settings & Configuration | 2-3 days |

**Total Phase Duration:** ~2-3 weeks

---

## v0.1.0 — Plugin Skeleton & Project Setup

### v0.1.0a — Gradle Project Initialization

**Goal:** Create the base IntelliJ Platform plugin project with proper Gradle configuration.

#### Deliverables

- [ ] Initialize Gradle Kotlin DSL project
- [ ] Configure IntelliJ Platform Gradle Plugin
- [ ] Set up Rider SDK dependencies
- [ ] Configure Kotlin compiler settings

#### Files to Create

```
sidekick/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    └── main/
        ├── kotlin/
        │   └── com/
        │       └── sidekick/
        │           └── .gitkeep
        └── resources/
            └── META-INF/
                └── plugin.xml
```

#### build.gradle.kts

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.sidekick"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2024.1")
    type.set("RD") // Rider
    plugins.set(listOf(/* Rider-specific plugins if needed */))
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("243.*")
    }
}
```

#### plugin.xml

```xml
<idea-plugin>
    <id>com.sidekick</id>
    <name>Sidekick</name>
    <vendor email="support@sidekick.dev" url="https://sidekick.dev">
        Sidekick
    </vendor>

    <description><![CDATA[
        AI-powered coding companion with local LLM integration for JetBrains Rider.
    ]]></description>

    <depends>com.intellij.modules.rider</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- Extensions will be added in subsequent versions -->
    </extensions>

    <actions>
        <!-- Actions will be added in subsequent versions -->
    </actions>
</idea-plugin>
```

#### Acceptance Criteria

- [ ] `./gradlew build` succeeds without errors
- [ ] `./gradlew runIde` launches Rider with plugin loaded
- [ ] Plugin appears in Settings → Plugins → Installed

---

### v0.1.0b — Core Package Structure

**Goal:** Establish the modular package architecture for the plugin.

#### Package Structure

```
src/main/kotlin/com/sidekick/
├── SidekickPlugin.kt              # Plugin lifecycle
├── core/
│   ├── SidekickBundle.kt          # Localization bundle
│   └── SidekickIcons.kt           # Icon registry
├── services/
│   └── .gitkeep                   # Service implementations (v0.1.1+)
├── ui/
│   └── .gitkeep                   # UI components (v0.1.2+)
├── settings/
│   └── .gitkeep                   # Settings (v0.1.3+)
└── util/
    └── .gitkeep                   # Utility classes
```

#### SidekickPlugin.kt

```kotlin
package com.sidekick

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

@Service
class SidekickPlugin {
    
    companion object {
        private val LOG = Logger.getInstance(SidekickPlugin::class.java)
        
        fun getInstance(): SidekickPlugin = 
            ApplicationManager.getApplication().getService(SidekickPlugin::class.java)
    }
    
    init {
        LOG.info("Sidekick plugin initialized")
    }
}
```

#### SidekickBundle.kt

```kotlin
package com.sidekick.core

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.SidekickBundle"

object SidekickBundle : DynamicBundle(BUNDLE) {
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String = getMessage(key, *params)
}
```

#### SidekickIcons.kt

```kotlin
package com.sidekick.core

import com.intellij.openapi.util.IconLoader

object SidekickIcons {
    @JvmField val SIDEKICK = IconLoader.getIcon("/icons/sidekick.svg", javaClass)
    @JvmField val CHAT = IconLoader.getIcon("/icons/chat.svg", javaClass)
    @JvmField val SEND = IconLoader.getIcon("/icons/send.svg", javaClass)
}
```

#### Resource Files

```
src/main/resources/
├── messages/
│   └── SidekickBundle.properties
└── icons/
    ├── sidekick.svg
    ├── chat.svg
    └── send.svg
```

#### Acceptance Criteria

- [ ] All packages compile without errors
- [ ] Plugin still loads and runs in IDE
- [ ] Logging output shows "Sidekick plugin initialized"

---

### v0.1.0c — Testing Infrastructure

**Goal:** Set up unit and integration testing framework.

#### Test Dependencies (build.gradle.kts additions)

```kotlin
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.test {
    useJUnitPlatform()
}
```

#### Test Structure

```
src/test/kotlin/com/sidekick/
├── SidekickPluginTest.kt
├── core/
│   └── SidekickBundleTest.kt
└── testutil/
    └── TestBase.kt
```

#### TestBase.kt

```kotlin
package com.sidekick.testutil

import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class SidekickTestBase : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"
}
```

#### Acceptance Criteria

- [ ] `./gradlew test` runs without errors
- [ ] Sample test passes
- [ ] Test coverage reporting configured

---

## v0.1.1 — Ollama Client Infrastructure

### v0.1.1a — HTTP Client Setup

**Goal:** Configure Ktor HTTP client for Ollama API communication.

#### Dependencies (build.gradle.kts)

```kotlin
val ktorVersion = "2.3.8"

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

#### OllamaClient.kt

```kotlin
package com.sidekick.services.ollama

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        engine {
            requestTimeout = 60_000
        }
    }
    
    suspend fun isConnected(): Boolean {
        return try {
            // HEAD request to /api/tags
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun close() {
        client.close()
    }
}
```

#### Acceptance Criteria

- [ ] Ktor client initializes without errors
- [ ] Connection check works against running Ollama
- [ ] Proper timeout handling

---

### v0.1.1b — Ollama Data Models

**Goal:** Define Kotlin data classes for Ollama API request/response structures.

#### Models Package

```kotlin
// com/sidekick/services/ollama/models/

@Serializable
data class OllamaModel(
    val name: String,
    val modifiedAt: String,
    val size: Long,
    val digest: String,
    val details: ModelDetails? = null
)

@Serializable
data class ModelDetails(
    val format: String,
    val family: String,
    val families: List<String>? = null,
    val parameterSize: String,
    val quantizationLevel: String
)

@Serializable
data class ListModelsResponse(
    val models: List<OllamaModel>
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val options: ChatOptions? = null
)

@Serializable
data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
data class ChatOptions(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val numPredict: Int? = null
)

@Serializable
data class ChatResponse(
    val model: String,
    val createdAt: String,
    val message: ChatMessage,
    val done: Boolean,
    val totalDuration: Long? = null,
    val evalCount: Int? = null
)
```

#### Acceptance Criteria

- [ ] All models serialize/deserialize correctly
- [ ] JSON samples from Ollama API parse without errors

---

### v0.1.1c — API Endpoints Implementation

**Goal:** Implement core Ollama API endpoint methods.

#### OllamaService.kt

```kotlin
package com.sidekick.services.ollama

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Service
class OllamaService {
    
    companion object {
        private val LOG = Logger.getInstance(OllamaService::class.java)
    }
    
    private var client: OllamaClient? = null
    
    fun configure(baseUrl: String) {
        client?.close()
        client = OllamaClient(baseUrl)
        LOG.info("Ollama client configured for: $baseUrl")
    }
    
    suspend fun listModels(): Result<List<OllamaModel>> {
        val c = client ?: return Result.failure(
            IllegalStateException("Ollama client not configured")
        )
        return c.listModels()
    }
    
    fun chat(request: ChatRequest): Flow<ChatResponse> = flow {
        val c = client ?: throw IllegalStateException("Ollama client not configured")
        c.chatStream(request).collect { emit(it) }
    }
    
    suspend fun getConnectionStatus(): ConnectionStatus {
        val c = client ?: return ConnectionStatus.NOT_CONFIGURED
        return if (c.isConnected()) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.DISCONNECTED
        }
    }
}

enum class ConnectionStatus {
    NOT_CONFIGURED,
    CONNECTED,
    DISCONNECTED
}
```

#### Acceptance Criteria

- [ ] `listModels()` returns installed models
- [ ] `chat()` streams responses token-by-token
- [ ] Connection status accurately reflects Ollama state

---

### v0.1.1d — Streaming Response Handler

**Goal:** Implement SSE parsing for streaming chat responses.

#### StreamingHandler.kt

```kotlin
package com.sidekick.services.ollama

import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class StreamingHandler(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    
    fun parseStream(response: HttpResponse): Flow<ChatResponse> = flow {
        val channel: ByteReadChannel = response.bodyAsChannel()
        
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue
            
            try {
                val chatResponse = json.decodeFromString<ChatResponse>(line)
                emit(chatResponse)
                
                if (chatResponse.done) break
            } catch (e: Exception) {
                // Log and continue on parse errors
            }
        }
    }
}
```

#### Acceptance Criteria

- [ ] Streaming tokens emit in real-time
- [ ] Handles connection drops gracefully
- [ ] `done: true` terminates the flow

---

### v0.1.1e — Unit Tests for Ollama Client

**Goal:** Comprehensive test coverage for Ollama integration.

#### Test Files

```kotlin
// OllamaClientTest.kt
class OllamaClientTest {
    @Test
    fun `listModels parses response correctly`() { }
    
    @Test
    fun `chat streams tokens`() { }
    
    @Test
    fun `handles connection timeout`() { }
    
    @Test
    fun `handles malformed JSON gracefully`() { }
}

// StreamingHandlerTest.kt
class StreamingHandlerTest {
    @Test
    fun `parses newline-delimited JSON`() { }
    
    @Test
    fun `emits done when stream completes`() { }
    
    @Test
    fun `handles partial lines`() { }
}
```

#### Acceptance Criteria

- [ ] >80% code coverage for Ollama package
- [ ] All edge cases covered (timeouts, errors, malformed data)

---

## v0.1.2 — Chat Tool Window UI

### v0.1.2a — Tool Window Registration

**Goal:** Register the Sidekick chat panel as an IDE tool window.

#### plugin.xml additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <toolWindow id="Sidekick"
                anchor="right"
                icon="/icons/sidekick.svg"
                factoryClass="com.sidekick.ui.SidekickToolWindowFactory"/>
</extensions>
```

#### SidekickToolWindowFactory.kt

```kotlin
package com.sidekick.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class SidekickToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val chatPanel = ChatPanel(project)
        val content = contentFactory.createContent(chatPanel, "Chat", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}
```

#### Acceptance Criteria

- [ ] Tool window appears in right sidebar
- [ ] Icon displays correctly
- [ ] Window opens/closes properly

---

### v0.1.2b — Chat Panel Layout

**Goal:** Create the main chat UI with message list and input area.

#### ChatPanel.kt

```kotlin
package com.sidekick.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val messageList = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    
    private val scrollPane = JBScrollPane(messageList).apply {
        border = JBUI.Borders.empty()
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }
    
    private val inputArea = JBTextArea(3, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8)
    }
    
    private val sendButton = JButton("Send").apply {
        addActionListener { onSendMessage() }
    }
    
    init {
        add(scrollPane, BorderLayout.CENTER)
        add(createInputPanel(), BorderLayout.SOUTH)
    }
    
    private fun createInputPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
            border = JBUI.Borders.empty(8)
        }
    }
    
    private fun onSendMessage() {
        val text = inputArea.text.trim()
        if (text.isNotEmpty()) {
            addUserMessage(text)
            inputArea.text = ""
            // TODO: Send to Ollama service
        }
    }
    
    private fun addUserMessage(text: String) {
        messageList.add(MessageBubble(text, isUser = true))
        messageList.revalidate()
        scrollToBottom()
    }
    
    fun addAssistantMessage(text: String) {
        messageList.add(MessageBubble(text, isUser = false))
        messageList.revalidate()
        scrollToBottom()
    }
    
    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val scrollBar = scrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }
}
```

#### Acceptance Criteria

- [ ] Message list scrolls properly
- [ ] Input area accepts multi-line text
- [ ] Send button triggers message handling

---

### v0.1.2c — Message Bubble Component

**Goal:** Styled message bubbles for user and assistant messages.

#### MessageBubble.kt

```kotlin
package com.sidekick.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JPanel
import javax.swing.JTextPane

class MessageBubble(
    message: String,
    private val isUser: Boolean
) : JPanel(BorderLayout()) {
    
    private val textPane = JTextPane().apply {
        text = message
        isEditable = false
        background = if (isUser) USER_BG else ASSISTANT_BG
        foreground = JBColor.foreground()
        border = JBUI.Borders.empty(8, 12)
    }
    
    init {
        isOpaque = false
        border = JBUI.Borders.empty(4, 8)
        add(textPane, if (isUser) BorderLayout.EAST else BorderLayout.WEST)
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        g2.color = if (isUser) USER_BG else ASSISTANT_BG
        g2.fillRoundRect(0, 0, width, height, 12, 12)
    }
    
    companion object {
        private val USER_BG = JBColor(Color(0x2196F3), Color(0x1976D2))
        private val ASSISTANT_BG = JBColor(Color(0xE0E0E0), Color(0x424242))
    }
}
```

#### Acceptance Criteria

- [ ] User messages right-aligned with blue background
- [ ] Assistant messages left-aligned with gray background
- [ ] Rounded corners render correctly

---

### v0.1.2d — Chat Service Integration

**Goal:** Connect ChatPanel to OllamaService for real conversations.

#### ChatController.kt

```kotlin
package com.sidekick.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sidekick.services.ollama.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class ChatController(private val project: Project) {
    
    private val ollamaService = service<OllamaService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val messageHistory = mutableListOf<ChatMessage>()
    
    var onResponseToken: ((String) -> Unit)? = null
    var onResponseComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    fun sendMessage(userMessage: String, model: String) {
        messageHistory.add(ChatMessage("user", userMessage))
        
        val request = ChatRequest(
            model = model,
            messages = messageHistory.toList()
        )
        
        scope.launch {
            try {
                val responseBuilder = StringBuilder()
                
                ollamaService.chat(request).collect { response ->
                    val token = response.message.content
                    responseBuilder.append(token)
                    onResponseToken?.invoke(token)
                    
                    if (response.done) {
                        messageHistory.add(ChatMessage("assistant", responseBuilder.toString()))
                        onResponseComplete?.invoke()
                    }
                }
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "Unknown error")
            }
        }
    }
    
    fun dispose() {
        scope.cancel()
    }
}
```

#### Acceptance Criteria

- [ ] User messages sent to Ollama
- [ ] Streaming tokens update UI in real-time
- [ ] Message history maintained for context

---

### v0.1.2e — Markdown Rendering

**Goal:** Render assistant responses with markdown formatting.

#### Dependencies

```kotlin
dependencies {
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
}
```

#### MarkdownRenderer.kt

```kotlin
package com.sidekick.ui

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownRenderer {
    
    private val options = MutableDataSet()
    private val parser = Parser.builder(options).build()
    private val renderer = HtmlRenderer.builder(options).build()
    
    fun toHtml(markdown: String): String {
        val document = parser.parse(markdown)
        return renderer.render(document)
    }
}
```

#### Acceptance Criteria

- [ ] Code blocks render with syntax highlighting
- [ ] Headers, lists, and emphasis render correctly
- [ ] Links are clickable

---

## v0.1.3 — Settings & Configuration

### v0.1.3a — Settings Data Model

**Goal:** Define persistent settings structure.

#### SidekickSettings.kt

```kotlin
package com.sidekick.settings

import com.intellij.openapi.components.*

@Service
@State(
    name = "SidekickSettings",
    storages = [Storage("sidekick.xml")]
)
class SidekickSettings : PersistentStateComponent<SidekickSettings.State> {
    
    data class State(
        var ollamaUrl: String = "http://localhost:11434",
        var defaultModel: String = "",
        var streamingEnabled: Boolean = true,
        var temperature: Double = 0.7,
        var maxTokens: Int = 2048
    )
    
    private var state = State()
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    companion object {
        fun getInstance(): SidekickSettings = service()
    }
}
```

#### Acceptance Criteria

- [ ] Settings persist across IDE restarts
- [ ] Defaults applied on first run

---

### v0.1.3b — Settings UI Panel

**Goal:** Create settings UI in IDE preferences.

#### plugin.xml additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <applicationConfigurable
        parentId="tools"
        instance="com.sidekick.settings.SidekickConfigurable"
        id="com.sidekick.settings"
        displayName="Sidekick"/>
</extensions>
```

#### SidekickConfigurable.kt

```kotlin
package com.sidekick.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class SidekickConfigurable : Configurable {
    
    private var panel: JComponent? = null
    private val settings = SidekickSettings.getInstance()
    
    private var ollamaUrl = settings.state.ollamaUrl
    private var defaultModel = settings.state.defaultModel
    private var temperature = settings.state.temperature
    
    override fun getDisplayName(): String = "Sidekick"
    
    override fun createComponent(): JComponent {
        panel = panel {
            group("Ollama Connection") {
                row("Server URL:") {
                    textField()
                        .bindText(::ollamaUrl)
                        .columns(COLUMNS_LARGE)
                        .comment("Default: http://localhost:11434")
                }
                row {
                    button("Test Connection") { testConnection() }
                }
            }
            
            group("Model Settings") {
                row("Default Model:") {
                    textField()
                        .bindText(::defaultModel)
                        .columns(COLUMNS_MEDIUM)
                }
                row("Temperature:") {
                    slider(0, 100, 10, 25)
                        .bindValue(
                            { (temperature * 100).toInt() },
                            { temperature = it / 100.0 }
                        )
                }
            }
        }
        return panel!!
    }
    
    private fun testConnection() {
        // TODO: Implement connection test
    }
    
    override fun isModified(): Boolean {
        return ollamaUrl != settings.state.ollamaUrl ||
               defaultModel != settings.state.defaultModel ||
               temperature != settings.state.temperature
    }
    
    override fun apply() {
        settings.state.ollamaUrl = ollamaUrl
        settings.state.defaultModel = defaultModel
        settings.state.temperature = temperature
    }
    
    override fun reset() {
        ollamaUrl = settings.state.ollamaUrl
        defaultModel = settings.state.defaultModel
        temperature = settings.state.temperature
    }
}
```

#### Acceptance Criteria

- [ ] Settings appear under Tools → Sidekick
- [ ] All fields save and load correctly
- [ ] Connection test button functional

---

### v0.1.3c — Model Selector Widget

**Goal:** Quick model switching in the chat toolbar.

#### ModelSelectorWidget.kt

```kotlin
package com.sidekick.ui

import com.intellij.openapi.components.service
import com.sidekick.services.ollama.OllamaService
import kotlinx.coroutines.*
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

class ModelSelectorWidget : JComboBox<String>() {
    
    private val ollamaService = service<OllamaService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        model = DefaultComboBoxModel(arrayOf("Loading..."))
        refreshModels()
    }
    
    fun refreshModels() {
        scope.launch {
            ollamaService.listModels()
                .onSuccess { models ->
                    val modelNames = models.map { it.name }.toTypedArray()
                    withContext(Dispatchers.Main) {
                        model = DefaultComboBoxModel(modelNames)
                        if (modelNames.isNotEmpty()) {
                            selectedIndex = 0
                        }
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        model = DefaultComboBoxModel(arrayOf("No models found"))
                    }
                }
        }
    }
    
    fun getSelectedModel(): String? = selectedItem as? String
}
```

#### Acceptance Criteria

- [ ] Dropdown populated with installed Ollama models
- [ ] Refreshes on settings change
- [ ] Selection persists during session

---

### v0.1.3d — Connection Status Indicator

**Goal:** Visual indicator of Ollama connection health.

#### ConnectionStatusWidget.kt

```kotlin
package com.sidekick.ui

import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.sidekick.services.ollama.ConnectionStatus
import com.sidekick.services.ollama.OllamaService
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.JPanel
import javax.swing.Timer

class ConnectionStatusWidget : JPanel() {
    
    private val ollamaService = service<OllamaService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var status = ConnectionStatus.NOT_CONFIGURED
    
    init {
        preferredSize = Dimension(12, 12)
        toolTipText = "Ollama: Checking..."
        
        // Poll connection status every 30 seconds
        Timer(30_000) { checkConnection() }.apply {
            isRepeats = true
            start()
        }
        
        checkConnection()
    }
    
    private fun checkConnection() {
        scope.launch {
            val newStatus = ollamaService.getConnectionStatus()
            withContext(Dispatchers.Main) {
                status = newStatus
                toolTipText = when (status) {
                    ConnectionStatus.CONNECTED -> "Ollama: Connected"
                    ConnectionStatus.DISCONNECTED -> "Ollama: Disconnected"
                    ConnectionStatus.NOT_CONFIGURED -> "Ollama: Not Configured"
                }
                repaint()
            }
        }
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        g2.color = when (status) {
            ConnectionStatus.CONNECTED -> JBColor.GREEN
            ConnectionStatus.DISCONNECTED -> JBColor.RED
            ConnectionStatus.NOT_CONFIGURED -> JBColor.YELLOW
        }
        g2.fillOval(0, 0, 10, 10)
    }
}
```

#### Acceptance Criteria

- [ ] Green dot when connected
- [ ] Red dot when disconnected
- [ ] Yellow dot when not configured
- [ ] Tooltip shows status text

---

## Verification Plan

### Automated Tests

```bash
# Run all unit tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Verify plugin builds
./gradlew buildPlugin
```

### Manual Verification

| Step | Expected Result |
|------|-----------------|
| Open Rider with plugin | No errors, plugin loads |
| Open Sidekick tool window | Chat panel appears |
| Configure Ollama URL in settings | Settings persist |
| Type message and click Send | Message appears in chat |
| Receive response from Ollama | Tokens stream in real-time |
| Switch models in dropdown | Chat uses selected model |
| Check connection indicator | Shows correct status |

---

## Appendix: File Manifest

| Version | Files Created |
|---------|---------------|
| v0.1.0a | `build.gradle.kts`, `settings.gradle.kts`, `plugin.xml` |
| v0.1.0b | `SidekickPlugin.kt`, `SidekickBundle.kt`, `SidekickIcons.kt` |
| v0.1.0c | `SidekickTestBase.kt`, `SidekickPluginTest.kt` |
| v0.1.1a | `OllamaClient.kt` |
| v0.1.1b | `models/*.kt` (6 data classes) |
| v0.1.1c | `OllamaService.kt` |
| v0.1.1d | `StreamingHandler.kt` |
| v0.1.1e | `OllamaClientTest.kt`, `StreamingHandlerTest.kt` |
| v0.1.2a | `SidekickToolWindowFactory.kt` |
| v0.1.2b | `ChatPanel.kt` |
| v0.1.2c | `MessageBubble.kt` |
| v0.1.2d | `ChatController.kt` |
| v0.1.2e | `MarkdownRenderer.kt` |
| v0.1.3a | `SidekickSettings.kt` |
| v0.1.3b | `SidekickConfigurable.kt` |
| v0.1.3c | `ModelSelectorWidget.kt` |
| v0.1.3d | `ConnectionStatusWidget.kt` |

**Total: ~20 files across 16 sub-versions**
