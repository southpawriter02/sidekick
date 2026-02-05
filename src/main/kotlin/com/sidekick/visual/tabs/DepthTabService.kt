package com.sidekick.visual.tabs

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

/**
 * # Depth Tab Service
 *
 * Project-level service for calculating file depth and assigning colors.
 * Part of Sidekick v0.5.1 Depth-Coded Tabs feature.
 *
 * ## Features
 *
 * - Calculates file depth relative to project root
 * - Extracts namespace/package from source files
 * - Assigns colors based on configured palette
 * - Persists configuration per project
 *
 * @since 0.5.1
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickDepthTabs", storages = [Storage("sidekick-tabs.xml")])
class DepthTabService(private val project: Project) : PersistentStateComponent<DepthTabService.State> {

    private val logger = Logger.getInstance(DepthTabService::class.java)
    private var state = State()

    /**
     * Persistent state for depth tab configuration.
     */
    data class State(
        var enabled: Boolean = true,
        var paletteName: String = "Ocean",
        var maxDepth: Int = 6,
        var baseDirectory: String? = null,
        var opacity: Float = 0.3f
    ) {
        constructor() : this(true, "Ocean", 6, null, 0.3f)

        fun toConfig(): DepthTabConfig = DepthTabConfig(
            enabled = enabled,
            colorPalette = ColorPalette.byName(paletteName),
            maxDepth = maxDepth,
            baseDirectory = baseDirectory,
            opacity = opacity
        )

        companion object {
            fun from(config: DepthTabConfig) = State(
                enabled = config.enabled,
                paletteName = config.colorPalette.name,
                maxDepth = config.maxDepth,
                baseDirectory = config.baseDirectory,
                opacity = config.opacity
            )
        }
    }

    companion object {
        private val CSHARP_NAMESPACE_REGEX = Regex("""namespace\s+([\w.]+)""")
        private val JAVA_PACKAGE_REGEX = Regex("""package\s+([\w.]+)""")

        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): DepthTabService {
            return project.getService(DepthTabService::class.java)
        }
    }

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded depth tab config: palette=${state.paletteName}, enabled=${state.enabled}")
    }

    /**
     * Current configuration.
     */
    val config: DepthTabConfig get() = state.toConfig()

    /**
     * Whether depth coding is enabled.
     */
    val isEnabled: Boolean get() = state.enabled

    /**
     * Calculates depth info for a file.
     *
     * @param file The file to analyze
     * @return Depth information including color
     */
    fun getDepthInfo(file: VirtualFile): FileDepthInfo {
        val basePath = state.baseDirectory ?: project.basePath ?: ""
        val relativePath = file.path.removePrefix(basePath).trim('/')
        val segments = relativePath.split('/').dropLast(1) // Exclude filename
        val depth = segments.size.coerceAtMost(state.maxDepth)
        val namespace = extractNamespace(file)
        val palette = ColorPalette.byName(state.paletteName)
        val color = palette.colorForDepth(depth, state.opacity)

        return FileDepthInfo(
            filePath = file.path,
            depth = depth,
            namespace = namespace,
            color = color,
            segments = segments
        )
    }

    /**
     * Gets the tab color for a file.
     *
     * @param file The file to get color for
     * @return Color for the tab, or null if disabled
     */
    fun getTabColor(file: VirtualFile): Color? {
        if (!state.enabled) return null
        return getDepthInfo(file).color
    }

    /**
     * Calculates depth with full result.
     *
     * @param file The file to analyze
     * @return DepthResult with success/error info
     */
    fun calculateDepth(file: VirtualFile): DepthResult {
        if (!state.enabled) {
            return DepthResult.Disabled()
        }

        return try {
            DepthResult.Success(getDepthInfo(file))
        } catch (e: Exception) {
            logger.error("Failed to calculate depth", e)
            DepthResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Updates the configuration.
     *
     * @param config New configuration to apply
     */
    fun updateConfig(config: DepthTabConfig) {
        state = State.from(config)
        logger.info("Updated depth tab config: palette=${config.colorPalette.name}")
    }

    /**
     * Toggles depth coding on/off.
     *
     * @return New enabled state
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        logger.info("Depth tabs ${if (state.enabled) "enabled" else "disabled"}")
        return state.enabled
    }

    /**
     * Sets the color palette.
     *
     * @param palette Palette to use
     */
    fun setPalette(palette: ColorPalette) {
        state.paletteName = palette.name
        logger.info("Set palette to ${palette.name}")
    }

    /**
     * Extracts namespace from a source file.
     */
    private fun extractNamespace(file: VirtualFile): String? {
        return when (file.extension?.lowercase()) {
            "cs" -> extractCSharpNamespace(file)
            "kt", "java" -> extractJavaPackage(file)
            "py" -> extractPythonModule(file)
            "ts", "tsx", "js", "jsx" -> extractJsModule(file)
            else -> null
        }
    }

    private fun extractCSharpNamespace(file: VirtualFile): String? {
        return try {
            val content = String(file.contentsToByteArray()).take(2000)
            CSHARP_NAMESPACE_REGEX.find(content)?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.debug("Failed to extract C# namespace", e)
            null
        }
    }

    private fun extractJavaPackage(file: VirtualFile): String? {
        return try {
            val content = String(file.contentsToByteArray()).take(1000)
            JAVA_PACKAGE_REGEX.find(content)?.groupValues?.get(1)
        } catch (e: Exception) {
            logger.debug("Failed to extract Java/Kotlin package", e)
            null
        }
    }

    private fun extractPythonModule(file: VirtualFile): String? {
        // Python module is implicit from directory structure
        val basePath = project.basePath ?: return null
        val relativePath = file.path.removePrefix(basePath).trim('/')
        return relativePath.replace('/', '.').removeSuffix(".py")
    }

    private fun extractJsModule(file: VirtualFile): String? {
        // JS/TS uses relative path as module
        val basePath = project.basePath ?: return null
        val relativePath = file.path.removePrefix(basePath).trim('/')
        return relativePath.substringBeforeLast('.')
    }
}

