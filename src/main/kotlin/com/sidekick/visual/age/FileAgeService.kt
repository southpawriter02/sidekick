package com.sidekick.visual.age

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * # File Age Service
 *
 * Project-level service for tracking file ages.
 * Part of Sidekick v0.5.4 File Age Indicator feature.
 *
 * ## Features
 *
 * - Calculates file age from modification time
 * - Git integration for commit-based timestamps
 * - Caches age information for performance
 * - Persists configuration per project
 *
 * @since 0.5.4
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickFileAge", storages = [Storage("sidekick-fileage.xml")])
class FileAgeService(private val project: Project) : PersistentStateComponent<FileAgeService.State> {

    private val logger = Logger.getInstance(FileAgeService::class.java)
    private var state = State()
    private val ageCache = mutableMapOf<String, CachedAgeInfo>()

    private data class CachedAgeInfo(
        val info: FileAgeInfo,
        val cachedAt: Instant
    ) {
        fun isExpired(): Boolean {
            return Duration.between(cachedAt, Instant.now()) > Duration.ofMinutes(5)
        }
    }

    /**
     * Persistent state for age configuration.
     */
    data class State(
        var enabled: Boolean = true,
        var schemeName: String = "Warmth",
        var freshMinutes: Long = 60,
        var recentHours: Long = 24,
        var staleDays: Long = 7,
        var useGitTime: Boolean = true,
        var showAgeInTooltip: Boolean = true
    ) {
        constructor() : this(true, "Warmth", 60, 24, 7, true, true)

        fun toConfig(): FileAgeConfig = FileAgeConfig(
            enabled = enabled,
            colorScheme = AgeColorScheme.byName(schemeName),
            freshThreshold = Duration.ofMinutes(freshMinutes),
            recentThreshold = Duration.ofHours(recentHours),
            staleThreshold = Duration.ofDays(staleDays),
            useGitTime = useGitTime,
            showAgeInTooltip = showAgeInTooltip
        )

        companion object {
            fun from(config: FileAgeConfig) = State(
                enabled = config.enabled,
                schemeName = config.colorScheme.displayName,
                freshMinutes = config.freshThreshold.toMinutes(),
                recentHours = config.recentThreshold.toHours(),
                staleDays = config.staleThreshold.toDays(),
                useGitTime = config.useGitTime,
                showAgeInTooltip = config.showAgeInTooltip
            )
        }
    }

    companion object {
        fun getInstance(project: Project): FileAgeService {
            return project.getService(FileAgeService::class.java)
        }
    }

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded file age config: scheme=${state.schemeName}, enabled=${state.enabled}")
    }

    /**
     * Current configuration.
     */
    val config: FileAgeConfig get() = state.toConfig()

    /**
     * Whether age indicator is enabled.
     */
    val isEnabled: Boolean get() = state.enabled

    /**
     * Gets age information for a file.
     */
    fun getFileAge(file: VirtualFile): AgeDetectionResult {
        if (!state.enabled) {
            return AgeDetectionResult.Disabled()
        }

        val path = file.path

        // Check cache
        ageCache[path]?.let { cached ->
            if (!cached.isExpired()) {
                return AgeDetectionResult.Success(cached.info)
            }
        }

        return try {
            val (timestamp, source) = getModificationTime(file)
            val now = Instant.now()
            val age = Duration.between(timestamp, now)
            val category = state.toConfig().categorize(age)

            val info = FileAgeInfo(
                filePath = path,
                lastModified = timestamp,
                age = age,
                category = category,
                source = source
            )

            // Cache the result
            ageCache[path] = CachedAgeInfo(info, now)

            AgeDetectionResult.Success(info)
        } catch (e: Exception) {
            logger.warn("Failed to get age for $path", e)
            AgeDetectionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Gets the tab color for a file based on its age.
     */
    fun getTabColor(file: VirtualFile): Color? {
        val result = getFileAge(file)
        return result.ageInfoOrNull()?.colorWith(state.toConfig().colorScheme)
    }

    /**
     * Gets a tooltip text for a file's age.
     */
    fun getTooltipText(file: VirtualFile): String? {
        if (!state.showAgeInTooltip) return null
        return getFileAge(file).ageInfoOrNull()?.summary
    }

    /**
     * Clears the age cache.
     */
    fun clearCache() {
        ageCache.clear()
        logger.info("File age cache cleared")
    }

    /**
     * Updates the configuration.
     */
    fun updateConfig(config: FileAgeConfig) {
        state = State.from(config)
        clearCache()
        logger.info("Updated file age config: scheme=${config.colorScheme.displayName}")
    }

    /**
     * Toggles age indicator on/off.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        logger.info("File age indicator ${if (state.enabled) "enabled" else "disabled"}")
        return state.enabled
    }

    /**
     * Toggles git time usage.
     */
    fun toggleGitTime(): Boolean {
        state.useGitTime = !state.useGitTime
        clearCache()
        logger.info("Git time ${if (state.useGitTime) "enabled" else "disabled"}")
        return state.useGitTime
    }

    /**
     * Sets the color scheme.
     */
    fun setScheme(scheme: AgeColorScheme) {
        state.schemeName = scheme.displayName
        logger.info("Set age scheme to ${scheme.displayName}")
    }

    private fun getModificationTime(file: VirtualFile): Pair<Instant, AgeSource> {
        if (state.useGitTime) {
            val gitTime = getGitModificationTime(file)
            if (gitTime != null) {
                return gitTime to AgeSource.GIT
            }
        }

        // Fallback to file system time
        val fsTime = Instant.ofEpochMilli(file.timeStamp)
        return fsTime to AgeSource.FILESYSTEM
    }

    private fun getGitModificationTime(file: VirtualFile): Instant? {
        return try {
            val projectPath = project.basePath ?: return null
            val relativePath = file.path.removePrefix(projectPath).removePrefix("/")

            val process = ProcessBuilder("git", "log", "-1", "--format=%ct", "--", relativePath)
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (output.isNotEmpty() && process.exitValue() == 0) {
                Instant.ofEpochSecond(output.toLong())
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug("Git time lookup failed: ${e.message}")
            null
        }
    }
}
