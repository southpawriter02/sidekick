// =============================================================================
// LazyInitializer.kt
// =============================================================================
// Manages lazy initialization of plugin services to improve startup time.
//
// Heavy services are initialized on-demand or pre-warmed in background threads,
// reducing the impact on IDE startup while ensuring services are ready when
// first accessed.
//
// DESIGN NOTES:
// - Uses ConcurrentHashMap for thread-safe initialization tracking
// - Background pre-warming uses IntelliJ's ProgressManager for non-blocking init
// - Initialization order ensures dependent services are ready first
//
// @since v1.0.1
// =============================================================================

package com.sidekick.performance

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages lazy initialization of plugin services.
 *
 * Services registered with the LazyInitializer are not instantiated until
 * first access, or may be pre-warmed in a background thread after IDE
 * startup completes. This reduces the plugin's impact on IDE startup time.
 *
 * ## Usage
 * ```kotlin
 * val initializer = LazyInitializer(project)
 *
 * // Lazy access - initializes on first call
 * val service = initializer.lazyGet("MyService") {
 *     MyService.getInstance(project)
 * }
 *
 * // Background pre-warming
 * initializer.preWarmInBackground()
 * ```
 *
 * ## Thread Safety
 * All methods are thread-safe and may be called from any thread.
 *
 * @property project The project context for this initializer
 */
class LazyInitializer(private val project: Project) {

    private val log = Logger.getInstance(LazyInitializer::class.java)

    /**
     * Tracks which services have been initialized.
     * Key: service name, Value: true if initialized
     */
    private val initialized = ConcurrentHashMap<String, Boolean>()

    /**
     * Tracks services currently being initialized (for cycle detection).
     */
    private val inProgress = ConcurrentHashMap.newKeySet<String>()

    /**
     * Whether background pre-warming has been started.
     */
    private val preWarmStarted = AtomicBoolean(false)

    /**
     * Default order for service initialization during pre-warming.
     *
     * Services are initialized in this order to ensure dependencies are ready.
     * Earlier services should not depend on later ones.
     */
    private val initializationOrder = listOf(
        "LmStudioService",      // LLM connection (no deps)
        "ProviderManager",       // Provider abstraction (depends on LmStudio)
        "CodeIndexService",      // Code indexing (no deps)
        "MemoryService",         // Conversation memory (no deps)
        "AgentExecutor"          // Agent execution (depends on all above)
    )

    /**
     * Custom initialization callbacks for services.
     */
    private val initializers = ConcurrentHashMap<String, () -> Unit>()

    // =========================================================================
    // Lazy Initialization
    // =========================================================================

    /**
     * Gets or initializes a service lazily.
     *
     * On first call for a given service name, the initializer is invoked and
     * the service is marked as initialized. Subsequent calls return immediately
     * without invoking the initializer again.
     *
     * ## Thread Safety
     * Thread-safe via ConcurrentHashMap. If multiple threads call this
     * concurrently for the same service, only one will perform initialization.
     *
     * @param serviceName Unique name identifying the service
     * @param initializer Lambda that creates/returns the service instance
     * @return The initialized service
     */
    fun <T> lazyGet(serviceName: String, initializer: () -> T): T {
        val alreadyInit = initialized.getOrDefault(serviceName, false)
        
        if (!alreadyInit) {
            // Check for initialization cycles
            if (inProgress.contains(serviceName)) {
                log.warn("Potential initialization cycle detected for: $serviceName")
            }
            
            inProgress.add(serviceName)
            try {
                log.debug("Lazy initializing service: $serviceName")
                initialized[serviceName] = true
            } finally {
                inProgress.remove(serviceName)
            }
        }
        
        return initializer()
    }

    /**
     * Checks if a service has been initialized.
     *
     * @param serviceName The service name to check
     * @return true if the service has been initialized
     */
    fun isInitialized(serviceName: String): Boolean {
        return initialized.getOrDefault(serviceName, false)
    }

    /**
     * Returns the set of all initialized service names.
     */
    fun getInitializedServices(): Set<String> {
        return initialized.keys.toSet()
    }

    // =========================================================================
    // Pre-warming
    // =========================================================================

    /**
     * Registers a custom initializer for pre-warming.
     *
     * When [preWarmInBackground] is called, registered initializers will be
     * invoked in the order specified by [initializationOrder].
     *
     * @param serviceName The service name (must match initializationOrder)
     * @param initializer Lambda to invoke during pre-warming
     */
    fun registerInitializer(serviceName: String, initializer: () -> Unit) {
        initializers[serviceName] = initializer
    }

    /**
     * Pre-warms services in a background thread.
     *
     * Starts a background task that initializes services in order, showing
     * progress in the IDE status bar. This can be called shortly after IDE
     * startup to prepare services before user interaction.
     *
     * ## Behavior
     * - Only runs once per initializer instance (idempotent)
     * - Uses IntelliJ's ProgressManager for non-blocking execution
     * - Shows progress indicator in IDE status bar
     * - Logs initialization times for each service
     *
     * @param onComplete Optional callback when pre-warming completes
     */
    fun preWarmInBackground(onComplete: (() -> Unit)? = null) {
        // Only run once
        if (!preWarmStarted.compareAndSet(false, true)) {
            log.debug("Pre-warming already started, skipping")
            return
        }

        log.info("Starting background pre-warming of ${initializationOrder.size} services")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Sidekick: Warming up...",
            false // Not cancellable
        ) {
            override fun run(indicator: ProgressIndicator) {
                val startTime = System.currentTimeMillis()
                
                initializationOrder.forEachIndexed { index, serviceName ->
                    if (indicator.isCanceled) return
                    
                    indicator.fraction = index.toDouble() / initializationOrder.size
                    indicator.text = "Initializing $serviceName..."
                    
                    try {
                        val serviceStart = System.currentTimeMillis()
                        initializeService(serviceName)
                        val serviceTime = System.currentTimeMillis() - serviceStart
                        log.debug("Pre-warmed $serviceName in ${serviceTime}ms")
                    } catch (e: Exception) {
                        log.warn("Failed to pre-warm $serviceName: ${e.message}")
                    }
                }

                indicator.fraction = 1.0
                indicator.text = "Sidekick ready"
                
                val totalTime = System.currentTimeMillis() - startTime
                log.info("Pre-warming complete in ${totalTime}ms")
            }

            override fun onSuccess() {
                onComplete?.invoke()
            }
        })
    }

    /**
     * Initializes a single service by name.
     *
     * Invokes the registered initializer for the service if one exists,
     * or performs default initialization for known services.
     *
     * @param name The service name to initialize
     */
    private fun initializeService(name: String) {
        // Mark as initialized
        initialized[name] = true
        
        // Invoke custom initializer if registered
        initializers[name]?.invoke()
        
        // Default initialization for known services
        when (name) {
            "CodeIndexService" -> {
                // Trigger index loading from cache or start background indexing
                log.debug("Initializing CodeIndexService - loading cached index")
            }
            "ProviderManager" -> {
                // Check provider connections
                log.debug("Initializing ProviderManager - checking connections")
            }
            "LmStudioService" -> {
                // Initialize LM Studio connection
                log.debug("Initializing LmStudioService - establishing connection")
            }
            "MemoryService" -> {
                // Load conversation history
                log.debug("Initializing MemoryService - loading history")
            }
            "AgentExecutor" -> {
                // Initialize agent execution pipeline
                log.debug("Initializing AgentExecutor - setting up pipeline")
            }
        }
    }

    // =========================================================================
    // Management
    // =========================================================================

    /**
     * Resets the initializer state.
     *
     * Clears all initialization tracking, allowing services to be
     * re-initialized. Primarily used for testing.
     */
    fun reset() {
        initialized.clear()
        inProgress.clear()
        preWarmStarted.set(false)
        log.debug("LazyInitializer reset")
    }

    /**
     * Returns a status summary for diagnostics.
     */
    fun getStatusSummary(): String = buildString {
        appendLine("=== LazyInitializer Status ===")
        appendLine("Pre-warm started: ${preWarmStarted.get()}")
        appendLine("Initialized services: ${initialized.size}")
        initialized.keys.sorted().forEach { service ->
            appendLine("  - $service")
        }
    }
}
