# Sidekick v1.0.x – Production Polish & Ecosystem

> **Phase Goal:** Production-ready release with performance optimization, ecosystem integration, and marketplace submission  
> **Building On:** v0.9.x Advanced Agent Capabilities

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v1.0.1 | Performance Optimization | Lazy loading, profiling, startup optimization |
| v1.0.2 | Security Hardening | Sandboxing, input validation, safe execution |
| v1.0.3 | Telemetry (Opt-In) | Analytics, crash reporting, usage tracking |
| v1.0.4 | Extensibility API | Public API, extension points, hooks |
| v1.0.5 | Multi-Provider Support | OpenAI, Anthropic, Azure, custom providers |
| v1.0.6 | Marketplace & Documentation | Submission, marketing, tutorials |

---

## v1.0.1 — Performance Optimization

### v1.0.1a — PerformanceModels

```kotlin
package com.sidekick.performance

import java.time.Duration
import java.time.Instant

/**
 * Performance metrics.
 */
data class PerformanceMetrics(
    val startupTime: Duration,
    val indexingTime: Duration,
    val averageResponseTime: Duration,
    val memoryUsage: MemoryUsage,
    val cacheHitRate: Float,
    val activeConnections: Int
)

data class MemoryUsage(
    val heapUsed: Long,
    val heapMax: Long,
    val nonHeapUsed: Long,
    val gcCount: Long,
    val gcTime: Duration
) {
    val heapPercentage: Float get() = heapUsed.toFloat() / heapMax
}

/**
 * Startup phases for measurement.
 */
enum class StartupPhase(val displayName: String) {
    PLUGIN_LOAD("Plugin Load"),
    SERVICE_INIT("Service Initialization"),
    CONNECTION_CHECK("Connection Check"),
    INDEX_LOAD("Index Load"),
    UI_INIT("UI Initialization")
}

data class StartupTiming(
    val phase: StartupPhase,
    val startTime: Instant,
    val endTime: Instant?
) {
    val duration: Duration? get() = endTime?.let { Duration.between(startTime, it) }
}

/**
 * Cache statistics.
 */
data class CacheStats(
    val name: String,
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val evictions: Long
) {
    val hitRate: Float get() = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f
}

/**
 * Performance configuration.
 */
data class PerformanceConfig(
    val lazyInitialization: Boolean = true,
    val backgroundIndexing: Boolean = true,
    val cacheEnabled: Boolean = true,
    val maxCacheSize: Int = 1000,
    val connectionPoolSize: Int = 5,
    val requestTimeout: Duration = Duration.ofSeconds(30)
)
```

---

### v1.0.1b — LazyInitializer

```kotlin
package com.sidekick.performance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages lazy initialization of services.
 */
class LazyInitializer(private val project: Project) {

    private val initialized = ConcurrentHashMap<String, Boolean>()
    private val initializationOrder = listOf(
        "LmStudioService",
        "ProviderManager",
        "CodeIndexService",
        "MemoryService",
        "AgentExecutor"
    )

    /**
     * Initializes services on first use.
     */
    fun <T> lazyGet(serviceName: String, initializer: () -> T): T {
        if (!initialized.getOrDefault(serviceName, false)) {
            initialized[serviceName] = true
            // Perform initialization in background if heavy
        }
        return initializer()
    }

    /**
     * Pre-warms services in background.
     */
    fun preWarmInBackground() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Sidekick: Warming up...", false) {
            override fun run(indicator: ProgressIndicator) {
                initializationOrder.forEachIndexed { index, service ->
                    indicator.fraction = index.toDouble() / initializationOrder.size
                    indicator.text = "Initializing $service..."
                    initializeService(service)
                }
            }
        })
    }

    private fun initializeService(name: String) {
        // Trigger service initialization
        when (name) {
            "CodeIndexService" -> {
                // Load cached index or start background indexing
            }
            "ProviderManager" -> {
                // Check provider connections
            }
        }
    }
}
```

---

### v1.0.1c — PerformanceMonitor

```kotlin
package com.sidekick.performance

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.time.Duration
import java.time.Instant

@Service(Service.Level.PROJECT)
class PerformanceMonitor(private val project: Project) {

    private val timings = mutableListOf<StartupTiming>()
    private val caches = mutableMapOf<String, CacheStats>()
    private var lastSnapshot: PerformanceMetrics? = null

    companion object {
        fun getInstance(project: Project) = project.getService(PerformanceMonitor::class.java)
    }

    /**
     * Records a phase start.
     */
    fun startPhase(phase: StartupPhase) {
        timings.add(StartupTiming(phase, Instant.now(), null))
    }

    /**
     * Records a phase end.
     */
    fun endPhase(phase: StartupPhase) {
        val timing = timings.find { it.phase == phase && it.endTime == null }
        timing?.let {
            val index = timings.indexOf(it)
            timings[index] = it.copy(endTime = Instant.now())
        }
    }

    /**
     * Captures current metrics.
     */
    fun captureMetrics(): PerformanceMetrics {
        val runtime = Runtime.getRuntime()
        
        val metrics = PerformanceMetrics(
            startupTime = calculateTotalStartupTime(),
            indexingTime = Duration.ZERO, // From CodeIndexService
            averageResponseTime = Duration.ZERO, // From request history
            memoryUsage = MemoryUsage(
                heapUsed = runtime.totalMemory() - runtime.freeMemory(),
                heapMax = runtime.maxMemory(),
                nonHeapUsed = 0,
                gcCount = 0,
                gcTime = Duration.ZERO
            ),
            cacheHitRate = calculateAverageCacheHitRate(),
            activeConnections = 0
        )
        
        lastSnapshot = metrics
        return metrics
    }

    /**
     * Gets startup report.
     */
    fun getStartupReport(): String = buildString {
        appendLine("=== Sidekick Startup Report ===")
        timings.forEach { timing ->
            timing.duration?.let {
                appendLine("${timing.phase.displayName}: ${it.toMillis()}ms")
            }
        }
        appendLine("Total: ${calculateTotalStartupTime().toMillis()}ms")
    }

    fun registerCache(name: String, stats: CacheStats) {
        caches[name] = stats
    }

    private fun calculateTotalStartupTime(): Duration {
        val first = timings.minByOrNull { it.startTime }?.startTime ?: return Duration.ZERO
        val last = timings.mapNotNull { it.endTime }.maxOrNull() ?: return Duration.ZERO
        return Duration.between(first, last)
    }

    private fun calculateAverageCacheHitRate(): Float {
        if (caches.isEmpty()) return 0f
        return caches.values.map { it.hitRate }.average().toFloat()
    }
}
```

---

## v1.0.2 — Security Hardening

### v1.0.2a — SecurityModels

```kotlin
package com.sidekick.security

/**
 * Security configuration.
 */
data class SecurityConfig(
    val sandboxCommands: Boolean = true,
    val allowedCommands: Set<String> = DEFAULT_ALLOWED_COMMANDS,
    val blockedPatterns: Set<String> = DEFAULT_BLOCKED_PATTERNS,
    val maxFileSize: Long = 10 * 1024 * 1024, // 10MB
    val restrictedPaths: Set<String> = setOf("/etc", "/usr", "/bin"),
    val requireConfirmation: ConfirmationLevel = ConfirmationLevel.DESTRUCTIVE
) {
    companion object {
        val DEFAULT_ALLOWED_COMMANDS = setOf("git", "dotnet", "npm", "gradle", "mvn", "cargo")
        val DEFAULT_BLOCKED_PATTERNS = setOf("rm -rf", "sudo", "chmod 777", "> /dev")
    }
}

enum class ConfirmationLevel { NONE, DESTRUCTIVE, ALL }

/**
 * Security event.
 */
data class SecurityEvent(
    val id: String,
    val type: SecurityEventType,
    val severity: SecuritySeverity,
    val description: String,
    val context: Map<String, String>,
    val timestamp: java.time.Instant,
    val blocked: Boolean
)

enum class SecurityEventType {
    COMMAND_BLOCKED, FILE_ACCESS_DENIED, PATH_TRAVERSAL_ATTEMPT,
    RATE_LIMIT_EXCEEDED, INVALID_INPUT, SUSPICIOUS_PATTERN
}

enum class SecuritySeverity { INFO, WARNING, HIGH, CRITICAL }

/**
 * Validation result.
 */
data class ValidationResult(
    val valid: Boolean,
    val sanitized: String?,
    val issues: List<SecurityIssue>
)

data class SecurityIssue(
    val type: String,
    val description: String,
    val severity: SecuritySeverity
)
```

---

### v1.0.2b — CommandSandbox

```kotlin
package com.sidekick.security

import com.intellij.openapi.components.Service
import java.io.File

@Service(Service.Level.APP)
class CommandSandbox {

    private var config = SecurityConfig()
    private val eventLog = mutableListOf<SecurityEvent>()

    companion object {
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication().getService(CommandSandbox::class.java)
        
        private val DANGEROUS_PATTERNS = listOf(
            Regex("""rm\s+-rf\s+[/~]"""),
            Regex(""">\s*/dev/"""),
            Regex("""\|\s*sh"""),
            Regex("""curl.*\|\s*(ba)?sh"""),
            Regex("""chmod\s+777"""),
            Regex("""sudo\s+""")
        )
    }

    /**
     * Validates a command before execution.
     */
    fun validateCommand(command: String, workingDir: String): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()

        // Check for dangerous patterns
        DANGEROUS_PATTERNS.forEach { pattern ->
            if (pattern.containsMatchIn(command)) {
                issues.add(SecurityIssue("dangerous_pattern", "Command contains dangerous pattern: $pattern", SecuritySeverity.CRITICAL))
            }
        }

        // Check blocked patterns
        config.blockedPatterns.forEach { blocked ->
            if (command.contains(blocked)) {
                issues.add(SecurityIssue("blocked_pattern", "Command contains blocked pattern: $blocked", SecuritySeverity.HIGH))
            }
        }

        // Check if command is in allowed list
        val executable = command.split(" ").firstOrNull()
        if (config.sandboxCommands && executable !in config.allowedCommands) {
            issues.add(SecurityIssue("unknown_command", "Command '$executable' not in allowed list", SecuritySeverity.WARNING))
        }

        // Check working directory
        if (!isPathAllowed(workingDir)) {
            issues.add(SecurityIssue("restricted_path", "Working directory is restricted", SecuritySeverity.HIGH))
        }

        val valid = issues.none { it.severity in listOf(SecuritySeverity.HIGH, SecuritySeverity.CRITICAL) }
        
        if (!valid) {
            logEvent(SecurityEventType.COMMAND_BLOCKED, "Command blocked: $command", issues)
        }

        return ValidationResult(valid, if (valid) command else null, issues)
    }

    /**
     * Validates file access.
     */
    fun validateFileAccess(path: String, write: Boolean): ValidationResult {
        val issues = mutableListOf<SecurityIssue>()
        val file = File(path)

        // Check path traversal
        if (path.contains("..")) {
            issues.add(SecurityIssue("path_traversal", "Path contains '..'", SecuritySeverity.CRITICAL))
        }

        // Check restricted paths
        config.restrictedPaths.forEach { restricted ->
            if (file.absolutePath.startsWith(restricted)) {
                issues.add(SecurityIssue("restricted_path", "Path is in restricted area: $restricted", SecuritySeverity.HIGH))
            }
        }

        // Check file size for reads
        if (!write && file.exists() && file.length() > config.maxFileSize) {
            issues.add(SecurityIssue("file_too_large", "File exceeds maximum size", SecuritySeverity.WARNING))
        }

        return ValidationResult(issues.isEmpty(), path, issues)
    }

    /**
     * Sanitizes user input.
     */
    fun sanitizeInput(input: String): String {
        return input
            .replace(Regex("""[<>\"'`$\\]"""), "")
            .take(10000) // Max length
    }

    private fun isPathAllowed(path: String): Boolean {
        val absolutePath = File(path).absolutePath
        return config.restrictedPaths.none { absolutePath.startsWith(it) }
    }

    private fun logEvent(type: SecurityEventType, description: String, issues: List<SecurityIssue>) {
        eventLog.add(SecurityEvent(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            severity = issues.maxOfOrNull { it.severity } ?: SecuritySeverity.INFO,
            description = description,
            context = emptyMap(),
            timestamp = java.time.Instant.now(),
            blocked = true
        ))
    }
}
```

---

## v1.0.3 — Telemetry (Opt-In)

### v1.0.3a — TelemetryModels

```kotlin
package com.sidekick.telemetry

import java.time.Instant

/**
 * Telemetry configuration.
 */
data class TelemetryConfig(
    val enabled: Boolean = false, // Opt-in only
    val anonymousUsage: Boolean = true,
    val crashReporting: Boolean = true,
    val featureTracking: Boolean = true,
    val performanceMetrics: Boolean = true
)

/**
 * Telemetry event.
 */
data class TelemetryEvent(
    val id: String,
    val type: EventType,
    val name: String,
    val properties: Map<String, String>,
    val metrics: Map<String, Double>,
    val timestamp: Instant,
    val sessionId: String
)

enum class EventType { FEATURE_USED, ERROR, PERFORMANCE, SESSION, FEEDBACK }

/**
 * Crash report.
 */
data class CrashReport(
    val id: String,
    val exception: String,
    val stackTrace: String,
    val context: CrashContext,
    val timestamp: Instant
)

data class CrashContext(
    val pluginVersion: String,
    val ideVersion: String,
    val osName: String,
    val javaVersion: String,
    val lastAction: String?
)

/**
 * Usage statistics (anonymized).
 */
data class UsageStats(
    val featuresUsed: Map<String, Int>,
    val averageSessionLength: Long,
    val totalSessions: Int,
    val providerUsage: Map<String, Int>,
    val errorCount: Int
)
```

---

### v1.0.3b — TelemetryService

```kotlin
package com.sidekick.telemetry

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import java.util.UUID

@Service(Service.Level.APP)
@State(name = "SidekickTelemetry", storages = [Storage("sidekick-telemetry.xml")])
class TelemetryService : PersistentStateComponent<TelemetryService.State> {

    data class State(
        var config: TelemetryConfig = TelemetryConfig(),
        var installId: String = UUID.randomUUID().toString(),
        var events: MutableList<TelemetryEvent> = mutableListOf()
    )

    private var state = State()
    private var sessionId = UUID.randomUUID().toString()
    private val logger = Logger.getInstance(TelemetryService::class.java)

    companion object {
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication().getService(TelemetryService::class.java)
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Tracks a feature usage.
     */
    fun trackFeature(featureName: String, properties: Map<String, String> = emptyMap()) {
        if (!state.config.enabled || !state.config.featureTracking) return
        
        queueEvent(TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.FEATURE_USED,
            name = featureName,
            properties = properties,
            metrics = emptyMap(),
            timestamp = java.time.Instant.now(),
            sessionId = sessionId
        ))
    }

    /**
     * Tracks an error (anonymized).
     */
    fun trackError(error: Throwable, context: String? = null) {
        if (!state.config.enabled || !state.config.crashReporting) return
        
        queueEvent(TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.ERROR,
            name = error.javaClass.simpleName,
            properties = mapOf(
                "message" to (error.message?.take(100) ?: ""),
                "context" to (context ?: "")
            ),
            metrics = emptyMap(),
            timestamp = java.time.Instant.now(),
            sessionId = sessionId
        ))
    }

    /**
     * Tracks performance metrics.
     */
    fun trackPerformance(operation: String, durationMs: Long, success: Boolean) {
        if (!state.config.enabled || !state.config.performanceMetrics) return
        
        queueEvent(TelemetryEvent(
            id = UUID.randomUUID().toString(),
            type = EventType.PERFORMANCE,
            name = operation,
            properties = mapOf("success" to success.toString()),
            metrics = mapOf("duration_ms" to durationMs.toDouble()),
            timestamp = java.time.Instant.now(),
            sessionId = sessionId
        ))
    }

    /**
     * Flushes events to backend.
     */
    suspend fun flush() {
        if (state.events.isEmpty()) return
        // Send to anonymous telemetry endpoint
        state.events.clear()
    }

    private fun queueEvent(event: TelemetryEvent) {
        state.events.add(event)
        if (state.events.size >= 50) {
            // Trigger flush
        }
    }
}
```

---

## v1.0.4 — Extensibility API

### v1.0.4a — ExtensionModels

```kotlin
package com.sidekick.api

/**
 * Extension point for third-party plugins.
 */
interface SidekickExtension {
    val id: String
    val name: String
    val version: String
    val description: String
    
    fun initialize()
    fun dispose()
}

/**
 * Custom prompt template extension.
 */
interface PromptTemplateExtension : SidekickExtension {
    fun getTemplates(): List<CustomPromptTemplate>
}

data class CustomPromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val template: String,
    val category: String,
    val variables: List<TemplateVariable>
)

data class TemplateVariable(
    val name: String,
    val description: String,
    val type: VariableType,
    val required: Boolean = true,
    val default: String? = null
)

enum class VariableType { STRING, CODE, FILE_PATH, SELECTION, SYMBOL }

/**
 * Custom agent tool extension.
 */
interface AgentToolExtension : SidekickExtension {
    fun getTools(): List<ExtensionTool>
}

data class ExtensionTool(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>,
    val handler: suspend (Map<String, Any>) -> ToolResult
)

data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean
)

data class ToolResult(val success: Boolean, val output: String, val error: String? = null)

/**
 * Visual enhancement extension.
 */
interface VisualExtension : SidekickExtension {
    fun getEnhancements(): List<VisualEnhancement>
}

data class VisualEnhancement(
    val id: String,
    val name: String,
    val type: EnhancementType,
    val renderer: Any // EditorHighlighter, LineMarkerProvider, etc.
)

enum class EnhancementType { HIGHLIGHTER, LINE_MARKER, GUTTER_ICON, TAB_COLOR }
```

---

### v1.0.4b — ExtensionManager

```kotlin
package com.sidekick.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName

@Service(Service.Level.APP)
class ExtensionManager {

    companion object {
        val PROMPT_TEMPLATE_EP = ExtensionPointName.create<PromptTemplateExtension>("com.sidekick.promptTemplateExtension")
        val AGENT_TOOL_EP = ExtensionPointName.create<AgentToolExtension>("com.sidekick.agentToolExtension")
        val VISUAL_EP = ExtensionPointName.create<VisualExtension>("com.sidekick.visualExtension")

        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication().getService(ExtensionManager::class.java)
    }

    private val loadedExtensions = mutableMapOf<String, SidekickExtension>()

    /**
     * Gets all prompt templates including extensions.
     */
    fun getAllPromptTemplates(): List<CustomPromptTemplate> {
        return PROMPT_TEMPLATE_EP.extensionList.flatMap { it.getTemplates() }
    }

    /**
     * Gets all agent tools including extensions.
     */
    fun getAllAgentTools(): List<ExtensionTool> {
        return AGENT_TOOL_EP.extensionList.flatMap { it.getTools() }
    }

    /**
     * Gets all visual enhancements including extensions.
     */
    fun getAllVisualEnhancements(): List<VisualEnhancement> {
        return VISUAL_EP.extensionList.flatMap { it.getEnhancements() }
    }

    /**
     * Registers an extension programmatically.
     */
    fun registerExtension(extension: SidekickExtension) {
        extension.initialize()
        loadedExtensions[extension.id] = extension
    }

    /**
     * Unregisters an extension.
     */
    fun unregisterExtension(extensionId: String) {
        loadedExtensions.remove(extensionId)?.dispose()
    }
}
```

---

## v1.0.5 — Multi-Provider Support

### v1.0.5a — ProviderModels

```kotlin
package com.sidekick.providers

/**
 * Provider configuration.
 */
sealed class ProviderConfig {
    abstract val enabled: Boolean
    abstract val priority: Int
    
    data class Ollama(
        override val enabled: Boolean = true,
        override val priority: Int = 1,
        val host: String = "localhost",
        val port: Int = 11434
    ) : ProviderConfig()
    
    data class LmStudio(
        override val enabled: Boolean = true,
        override val priority: Int = 2,
        val host: String = "localhost",
        val port: Int = 1234
    ) : ProviderConfig()
    
    data class OpenAI(
        override val enabled: Boolean = false,
        override val priority: Int = 3,
        val apiKey: String = "",
        val organization: String? = null,
        val baseUrl: String = "https://api.openai.com/v1"
    ) : ProviderConfig()
    
    data class Anthropic(
        override val enabled: Boolean = false,
        override val priority: Int = 4,
        val apiKey: String = "",
        val baseUrl: String = "https://api.anthropic.com"
    ) : ProviderConfig()
    
    data class AzureOpenAI(
        override val enabled: Boolean = false,
        override val priority: Int = 5,
        val endpoint: String = "",
        val apiKey: String = "",
        val deploymentId: String = "",
        val apiVersion: String = "2024-02-01"
    ) : ProviderConfig()
    
    data class Custom(
        override val enabled: Boolean = false,
        override val priority: Int = 10,
        val name: String,
        val baseUrl: String,
        val apiKey: String?,
        val headers: Map<String, String> = emptyMap()
    ) : ProviderConfig()
}

/**
 * Model capabilities by provider.
 */
data class ProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsFunctionCalling: Boolean,
    val supportsVision: Boolean,
    val supportsEmbeddings: Boolean,
    val maxContextLength: Int,
    val costPerMillionTokens: Float?
)
```

---

### v1.0.5b — MultiProviderService

```kotlin
package com.sidekick.providers

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "SidekickProviders", storages = [Storage("sidekick-providers.xml")])
class MultiProviderService : PersistentStateComponent<MultiProviderService.State> {

    data class State(
        var providers: MutableMap<String, ProviderConfig> = mutableMapOf(
            "ollama" to ProviderConfig.Ollama(),
            "lmstudio" to ProviderConfig.LmStudio()
        ),
        var defaultProvider: String = "ollama",
        var fallbackEnabled: Boolean = true
    )

    private var state = State()
    private val healthCache = mutableMapOf<String, Boolean>()

    companion object {
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication().getService(MultiProviderService::class.java)
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Gets the best available provider.
     */
    suspend fun getBestProvider(): String {
        // Try default first
        if (isHealthy(state.defaultProvider)) {
            return state.defaultProvider
        }
        
        // Fallback to others by priority
        if (state.fallbackEnabled) {
            return state.providers.entries
                .filter { it.value.enabled }
                .sortedBy { it.value.priority }
                .firstOrNull { isHealthy(it.key) }?.key
                ?: state.defaultProvider
        }
        
        return state.defaultProvider
    }

    /**
     * Adds a custom provider.
     */
    fun addCustomProvider(name: String, config: ProviderConfig.Custom) {
        state.providers[name] = config
    }

    /**
     * Tests a provider connection.
     */
    suspend fun testConnection(providerId: String): ConnectionTestResult {
        val config = state.providers[providerId] ?: return ConnectionTestResult(false, "Provider not found")
        
        return try {
            // Attempt connection based on provider type
            when (config) {
                is ProviderConfig.Ollama -> testOllamaConnection(config)
                is ProviderConfig.LmStudio -> testLmStudioConnection(config)
                is ProviderConfig.OpenAI -> testOpenAIConnection(config)
                else -> ConnectionTestResult(false, "Unknown provider type")
            }
        } catch (e: Exception) {
            ConnectionTestResult(false, e.message ?: "Connection failed")
        }
    }

    private suspend fun isHealthy(providerId: String): Boolean {
        return healthCache.getOrPut(providerId) {
            testConnection(providerId).success
        }
    }

    private suspend fun testOllamaConnection(config: ProviderConfig.Ollama): ConnectionTestResult {
        // HTTP GET to /api/tags
        return ConnectionTestResult(true, "Connected")
    }

    private suspend fun testLmStudioConnection(config: ProviderConfig.LmStudio): ConnectionTestResult {
        // HTTP GET to /v1/models
        return ConnectionTestResult(true, "Connected")
    }

    private suspend fun testOpenAIConnection(config: ProviderConfig.OpenAI): ConnectionTestResult {
        // HTTP GET to /v1/models with API key
        return ConnectionTestResult(true, "Connected")
    }
}

data class ConnectionTestResult(val success: Boolean, val message: String)
```

---

## v1.0.6 — Marketplace & Documentation

### v1.0.6a — MarketplaceAssets

```kotlin
package com.sidekick.marketplace

/**
 * Plugin marketplace metadata.
 */
data class MarketplaceMetadata(
    val pluginId: String = "com.sidekick",
    val name: String = "Sidekick - Local AI Coding Assistant",
    val vendor: String = "Sidekick",
    val version: String = "1.0.0",
    val sinceBuild: String = "241.0",
    val untilBuild: String = "243.*",
    val description: String = PLUGIN_DESCRIPTION,
    val changeNotes: String = CHANGE_NOTES,
    val tags: List<String> = listOf("AI", "LLM", "Code Assistant", "Ollama", "Local AI"),
    val category: String = "Code tools"
) {
    companion object {
        const val PLUGIN_DESCRIPTION = """
            <p>Sidekick brings the power of local LLMs to your IDE. Chat with AI, generate code, 
            refactor, and boost your productivity—all while keeping your code private.</p>
            
            <h3>Features</h3>
            <ul>
                <li>🤖 Local LLM integration (Ollama, LM Studio)</li>
                <li>💬 Context-aware chat with code understanding</li>
                <li>✨ AI-powered code generation and refactoring</li>
                <li>📝 Documentation generation</li>
                <li>🔍 Smart code search and navigation</li>
                <li>🎮 Gamification with stats and achievements</li>
                <li>🔒 100% private—your code never leaves your machine</li>
            </ul>
        """
        
        const val CHANGE_NOTES = """
            <h3>1.0.0 - Initial Release</h3>
            <ul>
                <li>Full LM Studio and Ollama support</li>
                <li>Agentic coding capabilities</li>
                <li>Multi-provider support</li>
                <li>Extensibility API</li>
            </ul>
        """
    }
}

/**
 * Documentation structure.
 */
data class DocumentationSite(
    val baseUrl: String = "https://sidekick.dev/docs",
    val sections: List<DocSection> = listOf(
        DocSection("getting-started", "Getting Started", listOf(
            DocPage("installation", "Installation"),
            DocPage("configuration", "Configuration"),
            DocPage("first-chat", "Your First Chat")
        )),
        DocSection("features", "Features", listOf(
            DocPage("chat", "AI Chat"),
            DocPage("code-actions", "Code Actions"),
            DocPage("agent", "Coding Agent")
        )),
        DocSection("api", "API Reference", listOf(
            DocPage("extensions", "Creating Extensions"),
            DocPage("tools", "Custom Tools"),
            DocPage("templates", "Prompt Templates")
        ))
    )
)

data class DocSection(val id: String, val title: String, val pages: List<DocPage>)
data class DocPage(val id: String, val title: String)
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Production Services (v1.0.x) -->
    <projectService serviceImplementation="com.sidekick.performance.PerformanceMonitor"/>
    <applicationService serviceImplementation="com.sidekick.security.CommandSandbox"/>
    <applicationService serviceImplementation="com.sidekick.telemetry.TelemetryService"/>
    <applicationService serviceImplementation="com.sidekick.api.ExtensionManager"/>
    <applicationService serviceImplementation="com.sidekick.providers.MultiProviderService"/>
    
    <!-- Extension Points -->
    <extensionPoint name="promptTemplateExtension" interface="com.sidekick.api.PromptTemplateExtension"/>
    <extensionPoint name="agentToolExtension" interface="com.sidekick.api.AgentToolExtension"/>
    <extensionPoint name="visualExtension" interface="com.sidekick.api.VisualExtension"/>
</extensions>
```

---

## Verification Plan

### Automated Tests

```bash
./gradlew test --tests "com.sidekick.performance.*"
./gradlew test --tests "com.sidekick.security.*"
./gradlew test --tests "com.sidekick.providers.*"
```

### Performance Benchmarks

| Metric | Target | Measurement |
|--------|--------|-------------|
| Plugin startup | <200ms | PerformanceMonitor |
| First response | <500ms | After warm-up |
| Memory baseline | <50MB | HeapDump |
| Index load | <1s | For 10k files |

### Marketplace Checklist

- [ ] Plugin builds without errors
- [ ] All tests pass
- [ ] Plugin.xml validates
- [ ] Screenshots prepared
- [ ] Description formatted
- [ ] Change notes complete
- [ ] Signed with certificate

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-02-04 | Ryan | Initial v1.0.x design specification |
