package com.sidekick.visual.git

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.diagnostic.Logger
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * # Git Heatmap Service
 *
 * Project-level service for computing Git statistics.
 * Part of Sidekick v0.5.5 Git Diff Heatmap feature.
 *
 * ## Features
 *
 * - Integration with IntelliJ Git4Idea plugin
 * - Git blame execution and parsing
 * - Heatmap statistic calculation
 * - Caching of results per file
 *
 * @since 0.5.5
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickGitHeatmap", storages = [Storage("sidekick-heatmap.xml")])
class GitHeatmapService(private val project: Project) : PersistentStateComponent<GitHeatmapService.State> {

    private val logger = Logger.getInstance(GitHeatmapService::class.java)
    private var state = State()
    
    // Simple in-memory cache
    private val cache = mutableMapOf<String, FileGitStats>()

    /**
     * Persistent state.
     */
    data class State(
        var enabled: Boolean = true,
        var schemeName: String = "Fire",
        var metricName: String = "Commit Count",
        var showInGutter: Boolean = true
    ) {
        constructor() : this(true, "Fire", "Commit Count", true)

        fun toConfig(): GitHeatmapConfig {
            return GitHeatmapConfig(
                enabled = enabled,
                showInGutter = showInGutter,
                colorScheme = HeatmapColorScheme.byName(schemeName),
                metricType = HeatmapMetric.byName(metricName)
            )
        }

        companion object {
            fun from(config: GitHeatmapConfig) = State(
                enabled = config.enabled,
                schemeName = config.colorScheme.displayName,
                metricName = config.metricType.displayName,
                showInGutter = config.showInGutter
            )
        }
    }

    companion object {
        fun getInstance(project: Project): GitHeatmapService {
            return project.getService(GitHeatmapService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded git heatmap config: ${state.schemeName}")
    }

    /**
     * Current configuration.
     */
    val config: GitHeatmapConfig get() = state.toConfig()

    /**
     * Gets Git stats for a file.
     * Uses cache if available.
     */
    fun getFileStats(file: VirtualFile): FileGitStats? {
        if (!state.enabled) return null
        
        return cache.getOrPut(file.path) {
            computeFileStats(file)
        }
    }

    /**
     * Gets line-level stats.
     */
    fun getLineStats(file: VirtualFile, line: Int): LineGitStats? {
        return getFileStats(file)?.lineStats?.get(line)
    }

    /**
     * Invalidates cache for a file (e.g., after commit).
     */
    fun invalidate(file: VirtualFile) {
        cache.remove(file.path)
    }
    
    /**
     * Clears entire cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Updates configuration.
     */
    fun updateConfig(config: GitHeatmapConfig) {
        state = State.from(config)
        logger.info("Updated heatmap config")
        // No need to clear cache if only display params changed, 
        // but simple to just clear to ensure consistency
    }

    /**
     * Toggles heatmap enabled state.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        return state.enabled
    }

    private fun computeFileStats(file: VirtualFile): FileGitStats {
        val lineStats = mutableMapOf<Int, LineGitStats>()
        
        try {
            // Run git blame to get line-level info
            val blameEntries = runGitBlame(file)
            
            blameEntries.forEach { entry ->
                lineStats[entry.lineNumber] = entry
            }
        } catch (e: Exception) {
            logger.warn("Failed to compute git stats for ${file.path}", e)
        }
        
        // Compute hotspots
        val hotspots = lineStats.values
            .sortedByDescending { it.commitCount } // Using commitCount or recency
            .take(10)
            .map { it.lineNumber }

        return FileGitStats(
            filePath = file.path,
            totalCommits = lineStats.size, // Approximation
            lineStats = lineStats,
            hotspotLines = hotspots
        )
    }

    private fun runGitBlame(file: VirtualFile): List<LineGitStats> {
        val repo = GitRepositoryManager.getInstance(project)
            .getRepositoryForFile(file) ?: return emptyList()
        
        val handler = GitLineHandler(project, repo.root, GitCommand.BLAME)
        handler.setStdoutSuppressed(true)
        // Porcelain format is easier to parse reliably
        handler.addParameters("--porcelain", file.name)
        
        // Since we need relative path from repo root
        // But git blame usually works with file relative to cwd (repo root)
        
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            logger.warn("Git blame failed: ${result.errorOutput}")
            return emptyList()
        }
        
        return parsePorcelainBlame(result.outputAsJoinedString)
    }

    private fun parsePorcelainBlame(output: String): List<LineGitStats> {
        val stats = mutableListOf<LineGitStats>()
        val lines = output.lines()
        
        var currentHash: String? = null
        var currentAuthor: String? = null
        var currentTimestamp: Long? = null
        var currentOriginalLine = -1
        var currentFinalLine = -1
        
        // Porcelain format:
        // 63c9d5a6 1 1 1
        // author Ryan
        // author-time 1720000000
        // ...
        // filename foo.txt
        // \tline content
        
        // Since porcelain format includes repeated headers for same commit,
        // we might buffer commits. 
        // Simple parser strategy: read line by line.
        
        // This is a simplified parser. 
        // In reality, git blame --porcelain outputs headers for each line, 
        // but minimal headers if commit is repeated.
        
        val commitInfoMap = mutableMapOf<String, CommitInfo>()
        
        var i = 0
        while (i < lines.size) {
            val headerLine = lines[i]
            if (headerLine.isBlank()) {
                i++
                continue
            }
            
            // First line of block: hash orig_line final_line count
            val parts = headerLine.split(" ")
            if (parts.size < 3) {
                // Usually line content line starts with \t, catch that
                if (headerLine.startsWith("\t")) {
                    i++
                    continue
                }
                i++
                continue
            }
            
            val hash = parts[0]
            val finalLine = parts[2].toIntOrNull() ?: -1
            
            // Read headers until line content
            i++
            while (i < lines.size && !lines[i].startsWith("\t")) {
                val metaLine = lines[i]
                if (metaLine.startsWith("author ")) {
                    // Update commit info map
                    if (!commitInfoMap.containsKey(hash)) {
                        commitInfoMap[hash] = CommitInfo(author = metaLine.removePrefix("author "))
                    } else {
                        val info = commitInfoMap[hash]!!
                        if (info.author == null) info.author = metaLine.removePrefix("author ")
                    }
                } else if (metaLine.startsWith("author-time ")) {
                    val time = metaLine.removePrefix("author-time ").toLongOrNull()
                    if (time != null) {
                        if (!commitInfoMap.containsKey(hash)) {
                            commitInfoMap[hash] = CommitInfo(timestamp = time)
                        } else {
                            val info = commitInfoMap[hash]!!
                            if (info.timestamp == null) info.timestamp = time
                        }
                    }
                }
                i++
            }
            
            // Line content
            if (i < lines.size && lines[i].startsWith("\t")) {
                // This is the line content. We have processed one actual line of the file.
                // Construct stats for this line.
                val info = commitInfoMap[hash]
                
                if (finalLine != -1 && info != null) {
                    stats.add(LineGitStats(
                        lineNumber = finalLine,
                        commitCount = 1, // Defaulting to 1 as blame doesn't give count
                        lastCommitDate = info.timestamp?.let { Instant.ofEpochSecond(it) },
                        lastAuthor = info.author,
                        authorCount = 1,
                        commitHash = hash
                    ))
                }
                i++
            }
        }
        
        return stats
    }
    
    private data class CommitInfo(
        var author: String? = null,
        var timestamp: Long? = null
    )
}
