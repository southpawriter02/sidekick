# Sidekick v0.7.x – Gamification Phase

> **Phase Goal:** Add engagement and achievement features to boost developer motivation  
> **Building On:** v0.6.x Code Quality

---

## Version Overview

| Version | Focus | Key Deliverables |
|---------|-------|------------------|
| v0.7.1 | Daily Stats Dashboard | Activity metrics and visualizations |
| v0.7.2 | Weekly Digest | Automated weekly summary reports |
| v0.7.3 | Code Combo | Typing burst effects and multipliers |
| v0.7.4 | Developer XP | XP system, levels, and achievements |

---

## v0.7.1 — Daily Stats Dashboard

### v0.7.1a — StatsModels

**Goal:** Data structures for activity tracking and statistics.

#### StatsModels.kt

```kotlin
package com.sidekick.gamification.stats

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Daily coding statistics.
 */
data class DailyStats(
    val date: LocalDate,
    val commits: Int = 0,
    val linesAdded: Int = 0,
    val linesRemoved: Int = 0,
    val filesModified: Int = 0,
    val timeInIde: Duration = Duration.ZERO,
    val keystrokes: Long = 0,
    val buildCount: Int = 0,
    val testRuns: Int = 0,
    val testsPassedRate: Float = 0f,
    val mostActiveFiles: List<FileActivity> = emptyList(),
    val languageBreakdown: Map<String, Duration> = emptyMap(),
    val hourlyActivity: Map<Int, Int> = emptyMap()
) {
    val netLines: Int get() = linesAdded - linesRemoved
    val productivity: Float get() = if (timeInIde.toMinutes() > 0) {
        (linesAdded + commits * 10).toFloat() / timeInIde.toMinutes()
    } else 0f
    
    fun isStreak(previousDay: DailyStats?): Boolean = commits > 0 || linesAdded > 10
}

/**
 * File activity tracking.
 */
data class FileActivity(
    val filePath: String,
    val timeSpent: Duration,
    val edits: Int,
    val language: String?
)

/**
 * Streak information.
 */
data class StreakInfo(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActiveDate: LocalDate?,
    val streakStart: LocalDate?
) {
    val isActive: Boolean get() = lastActiveDate == LocalDate.now() || 
                                   lastActiveDate == LocalDate.now().minusDays(1)
}

/**
 * Aggregate statistics.
 */
data class AggregateStats(
    val period: StatsPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalCommits: Int,
    val totalLinesAdded: Int,
    val totalLinesRemoved: Int,
    val totalTimeInIde: Duration,
    val averageDailyTime: Duration,
    val peakProductivityHour: Int?,
    val topLanguages: List<Pair<String, Duration>>,
    val topFiles: List<FileActivity>
)

/**
 * Stats period for aggregation.
 */
enum class StatsPeriod(val days: Int) {
    DAY(1),
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    YEAR(365),
    ALL_TIME(Int.MAX_VALUE)
}

/**
 * Configuration for stats tracking.
 */
data class StatsConfig(
    val enabled: Boolean = true,
    val trackKeystrokes: Boolean = true,
    val trackTimeInIde: Boolean = true,
    val trackGitActivity: Boolean = true,
    val idleTimeoutMinutes: Int = 5,
    val retentionDays: Int = 365
)
```

---

### v0.7.1b — StatsService

**Goal:** Service to collect and aggregate statistics.

#### StatsService.kt

```kotlin
package com.sidekick.gamification.stats

import com.intellij.openapi.components.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.time.*

@Service(Service.Level.PROJECT)
@State(name = "SidekickStats", storages = [Storage("sidekick-stats.xml")])
class StatsService(private val project: Project) : PersistentStateComponent<StatsService.State> {

    data class State(
        var config: StatsConfig = StatsConfig(),
        var dailyStats: MutableMap<String, DailyStats> = mutableMapOf(),
        var streak: StreakInfo = StreakInfo(0, 0, null, null)
    )

    private var state = State()
    private var sessionStartTime: Instant = Instant.now()
    private var lastActivityTime: Instant = Instant.now()
    private var sessionKeystrokes: Long = 0

    companion object {
        fun getInstance(project: Project): StatsService {
            return project.getService(StatsService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Initializes activity listeners.
     */
    fun initialize() {
        sessionStartTime = Instant.now()
        
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    recordEdit(event)
                }
            }, project
        )
    }

    /**
     * Gets today's stats.
     */
    fun getTodayStats(): DailyStats {
        val key = LocalDate.now().toString()
        return state.dailyStats.getOrPut(key) { DailyStats(date = LocalDate.now()) }
    }

    /**
     * Gets stats for a date range.
     */
    fun getStats(period: StatsPeriod): AggregateStats {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(period.days.toLong())
        
        val dailyStatsList = state.dailyStats
            .filter { LocalDate.parse(it.key) in startDate..endDate }
            .values.toList()

        return AggregateStats(
            period = period,
            startDate = startDate,
            endDate = endDate,
            totalCommits = dailyStatsList.sumOf { it.commits },
            totalLinesAdded = dailyStatsList.sumOf { it.linesAdded },
            totalLinesRemoved = dailyStatsList.sumOf { it.linesRemoved },
            totalTimeInIde = dailyStatsList.map { it.timeInIde }.fold(Duration.ZERO) { a, b -> a.plus(b) },
            averageDailyTime = if (dailyStatsList.isNotEmpty()) {
                dailyStatsList.map { it.timeInIde }.fold(Duration.ZERO) { a, b -> a.plus(b) }
                    .dividedBy(dailyStatsList.size.toLong())
            } else Duration.ZERO,
            peakProductivityHour = findPeakHour(dailyStatsList),
            topLanguages = aggregateLanguages(dailyStatsList),
            topFiles = aggregateFiles(dailyStatsList)
        )
    }

    /**
     * Gets current streak info.
     */
    fun getStreak(): StreakInfo = state.streak

    /**
     * Records a commit.
     */
    fun recordCommit() {
        updateToday { it.copy(commits = it.commits + 1) }
        updateStreak()
    }

    /**
     * Records lines changed.
     */
    fun recordLinesChanged(added: Int, removed: Int) {
        updateToday { it.copy(linesAdded = it.linesAdded + added, linesRemoved = it.linesRemoved + removed) }
    }

    /**
     * Records a build.
     */
    fun recordBuild(success: Boolean) {
        updateToday { it.copy(buildCount = it.buildCount + 1) }
    }

    /**
     * Records a test run.
     */
    fun recordTestRun(passed: Int, total: Int) {
        updateToday { it.copy(
            testRuns = it.testRuns + 1,
            testsPassedRate = if (total > 0) passed.toFloat() / total else it.testsPassedRate
        )}
    }

    /**
     * Flushes the current session time.
     */
    fun flushSession() {
        val sessionDuration = Duration.between(sessionStartTime, Instant.now())
        updateToday { it.copy(
            timeInIde = it.timeInIde.plus(sessionDuration),
            keystrokes = it.keystrokes + sessionKeystrokes
        )}
        sessionStartTime = Instant.now()
        sessionKeystrokes = 0
    }

    private fun recordEdit(event: DocumentEvent) {
        sessionKeystrokes++
        lastActivityTime = Instant.now()
        
        val added = event.newFragment.count { it == '\n' }
        val removed = event.oldFragment.count { it == '\n' }
        if (added > 0 || removed > 0) {
            recordLinesChanged(added, removed)
        }
    }

    private fun updateToday(update: (DailyStats) -> DailyStats) {
        val key = LocalDate.now().toString()
        val current = state.dailyStats.getOrPut(key) { DailyStats(date = LocalDate.now()) }
        state.dailyStats[key] = update(current)
    }

    private fun updateStreak() {
        val today = LocalDate.now()
        val last = state.streak.lastActiveDate
        
        state.streak = when {
            last == null -> StreakInfo(1, 1, today, today)
            last == today -> state.streak
            last == today.minusDays(1) -> {
                val newCurrent = state.streak.currentStreak + 1
                StreakInfo(
                    currentStreak = newCurrent,
                    longestStreak = maxOf(newCurrent, state.streak.longestStreak),
                    lastActiveDate = today,
                    streakStart = state.streak.streakStart
                )
            }
            else -> StreakInfo(1, state.streak.longestStreak, today, today)
        }
    }

    private fun findPeakHour(stats: List<DailyStats>): Int? {
        val hourlyTotals = mutableMapOf<Int, Int>()
        stats.forEach { day ->
            day.hourlyActivity.forEach { (hour, count) ->
                hourlyTotals[hour] = hourlyTotals.getOrDefault(hour, 0) + count
            }
        }
        return hourlyTotals.maxByOrNull { it.value }?.key
    }

    private fun aggregateLanguages(stats: List<DailyStats>): List<Pair<String, Duration>> {
        val totals = mutableMapOf<String, Duration>()
        stats.forEach { day ->
            day.languageBreakdown.forEach { (lang, time) ->
                totals[lang] = totals.getOrDefault(lang, Duration.ZERO).plus(time)
            }
        }
        return totals.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    }

    private fun aggregateFiles(stats: List<DailyStats>): List<FileActivity> {
        return stats.flatMap { it.mostActiveFiles }
            .groupBy { it.filePath }
            .map { (path, activities) ->
                FileActivity(
                    filePath = path,
                    timeSpent = activities.map { it.timeSpent }.fold(Duration.ZERO) { a, b -> a.plus(b) },
                    edits = activities.sumOf { it.edits },
                    language = activities.firstOrNull()?.language
                )
            }
            .sortedByDescending { it.timeSpent }
            .take(10)
    }
}
```

---

### v0.7.1c — StatsDashboard

**Goal:** Visual dashboard for statistics display.

#### StatsDashboard.kt

```kotlin
package com.sidekick.gamification.stats

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import java.awt.*
import javax.swing.*

class StatsDashboardFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = StatsDashboardPanel(project)
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(panel, "", false)
        )
    }
}

class StatsDashboardPanel(private val project: Project) : JBPanel<StatsDashboardPanel>() {
    private val service = StatsService.getInstance(project)

    init {
        layout = BorderLayout()
        add(createHeader(), BorderLayout.NORTH)
        add(createStatsGrid(), BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)
    }

    private fun createHeader(): JPanel {
        val stats = service.getTodayStats()
        val streak = service.getStreak()
        
        return panel {
            row {
                label("📊 Daily Stats Dashboard").bold()
                label("🔥 ${streak.currentStreak} day streak").visible(streak.isActive)
            }
        }
    }

    private fun createStatsGrid(): JPanel {
        val stats = service.getTodayStats()
        
        return panel {
            group("Today") {
                row {
                    statsCard("Commits", "📝", stats.commits.toString())
                    statsCard("Lines Added", "➕", stats.linesAdded.toString())
                    statsCard("Lines Removed", "➖", stats.linesRemoved.toString())
                }
                row {
                    statsCard("Time in IDE", "⏱️", formatDuration(stats.timeInIde))
                    statsCard("Files Modified", "📁", stats.filesModified.toString())
                    statsCard("Builds", "🔨", stats.buildCount.toString())
                }
            }
            
            group("This Week") {
                val weekStats = service.getStats(StatsPeriod.WEEK)
                row {
                    statsCard("Total Commits", "📝", weekStats.totalCommits.toString())
                    statsCard("Net Lines", "📈", (weekStats.totalLinesAdded - weekStats.totalLinesRemoved).toString())
                    statsCard("Avg Daily Time", "⏱️", formatDuration(weekStats.averageDailyTime))
                }
            }
            
            group("Activity Chart") {
                row {
                    cell(ActivityChart(service.getTodayStats()))
                        .align(Align.FILL)
                }
            }
        }
    }

    private fun createFooter(): JPanel = panel {
        row {
            val streak = service.getStreak()
            label("Longest streak: ${streak.longestStreak} days")
            label("Peak hour: ${service.getStats(StatsPeriod.WEEK).peakProductivityHour ?: "N/A"}:00")
        }
    }

    private fun Row.statsCard(title: String, icon: String, value: String) {
        panel {
            row { label("$icon $value").bold() }
            row { label(title) }
        }
    }

    private fun formatDuration(duration: java.time.Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/**
 * Simple activity chart component.
 */
class ActivityChart(private val stats: DailyStats) : JPanel() {
    
    init {
        preferredSize = Dimension(400, 100)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        val barWidth = width / 24
        val maxActivity = stats.hourlyActivity.values.maxOrNull() ?: 1
        
        (0..23).forEach { hour ->
            val activity = stats.hourlyActivity[hour] ?: 0
            val barHeight = (activity.toFloat() / maxActivity * (height - 20)).toInt()
            
            g2d.color = Color(76, 175, 80, 180)
            g2d.fillRect(hour * barWidth + 2, height - barHeight - 15, barWidth - 4, barHeight)
            
            g2d.color = Color.GRAY
            g2d.drawString("${hour}", hour * barWidth + 4, height - 2)
        }
    }
}
```

---

## v0.7.2 — Weekly Digest

### v0.7.2a — DigestModels

**Goal:** Data structures for weekly summary reports.

#### DigestModels.kt

```kotlin
package com.sidekick.gamification.digest

import java.time.LocalDate
import com.sidekick.gamification.stats.AggregateStats

/**
 * Weekly digest report.
 */
data class WeeklyDigest(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val stats: AggregateStats,
    val highlights: List<DigestHighlight>,
    val achievements: List<WeeklyAchievement>,
    val comparison: WeekComparison?,
    val recommendations: List<String>
)

/**
 * A highlight from the week.
 */
data class DigestHighlight(
    val icon: String,
    val title: String,
    val description: String,
    val metric: String?
)

/**
 * Weekly achievement.
 */
data class WeeklyAchievement(
    val name: String,
    val description: String,
    val icon: String,
    val isNew: Boolean
)

/**
 * Week-over-week comparison.
 */
data class WeekComparison(
    val commitsChange: Int,
    val linesChange: Int,
    val timeChange: java.time.Duration,
    val trend: Trend
)

/**
 * Trend direction.
 */
enum class Trend(val icon: String) {
    UP("📈"),
    DOWN("📉"),
    STABLE("➡️")
}

/**
 * Digest configuration.
 */
data class DigestConfig(
    val enabled: Boolean = true,
    val dayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
    val showNotification: Boolean = true,
    val emailDigest: Boolean = false,
    val emailAddress: String? = null
)
```

---

### v0.7.2b — DigestService

**Goal:** Service to generate weekly digests.

#### DigestService.kt

```kotlin
package com.sidekick.gamification.digest

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.sidekick.gamification.stats.StatsService
import com.sidekick.gamification.stats.StatsPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Service(Service.Level.PROJECT)
@State(name = "SidekickDigest", storages = [Storage("sidekick-digest.xml")])
class DigestService(private val project: Project) : PersistentStateComponent<DigestService.State> {

    data class State(
        var config: DigestConfig = DigestConfig(),
        var lastDigestDate: String? = null,
        var digests: MutableList<WeeklyDigest> = mutableListOf()
    )

    private var state = State()

    companion object {
        fun getInstance(project: Project): DigestService {
            return project.getService(DigestService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Generates the weekly digest.
     */
    fun generateDigest(): WeeklyDigest {
        val statsService = StatsService.getInstance(project)
        val weekStats = statsService.getStats(StatsPeriod.WEEK)
        
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val previousWeekStats = statsService.getStats(StatsPeriod.WEEK) // Would need offset

        val digest = WeeklyDigest(
            weekStart = weekStart,
            weekEnd = weekEnd,
            stats = weekStats,
            highlights = generateHighlights(weekStats),
            achievements = checkAchievements(weekStats),
            comparison = generateComparison(weekStats, previousWeekStats),
            recommendations = generateRecommendations(weekStats)
        )

        state.digests.add(digest)
        state.lastDigestDate = today.toString()

        return digest
    }

    /**
     * Gets the latest digest.
     */
    fun getLatestDigest(): WeeklyDigest? = state.digests.lastOrNull()

    /**
     * Checks if a new digest should be shown.
     */
    fun shouldShowDigest(): Boolean {
        if (!state.config.enabled) return false
        
        val today = LocalDate.now()
        if (today.dayOfWeek != state.config.dayOfWeek) return false
        
        val lastDigest = state.lastDigestDate?.let { LocalDate.parse(it) }
        return lastDigest == null || lastDigest.isBefore(today.minusDays(6))
    }

    private fun generateHighlights(stats: com.sidekick.gamification.stats.AggregateStats): List<DigestHighlight> {
        val highlights = mutableListOf<DigestHighlight>()
        
        if (stats.totalCommits > 20) {
            highlights.add(DigestHighlight(
                icon = "🎯",
                title = "Prolific Week",
                description = "You made ${stats.totalCommits} commits this week!",
                metric = "${stats.totalCommits} commits"
            ))
        }
        
        if (stats.totalLinesAdded > 500) {
            highlights.add(DigestHighlight(
                icon = "📝",
                title = "Code Machine",
                description = "You wrote ${stats.totalLinesAdded} lines of code",
                metric = "${stats.totalLinesAdded} lines"
            ))
        }
        
        stats.peakProductivityHour?.let { hour ->
            highlights.add(DigestHighlight(
                icon = "⚡",
                title = "Peak Hour",
                description = "You're most productive around ${hour}:00",
                metric = null
            ))
        }
        
        return highlights
    }

    private fun checkAchievements(stats: com.sidekick.gamification.stats.AggregateStats): List<WeeklyAchievement> {
        val achievements = mutableListOf<WeeklyAchievement>()
        
        if (stats.totalCommits >= 50) {
            achievements.add(WeeklyAchievement(
                name = "Commit Champion",
                description = "50+ commits in a week",
                icon = "🏆",
                isNew = true
            ))
        }
        
        if (stats.totalTimeInIde.toHours() >= 40) {
            achievements.add(WeeklyAchievement(
                name = "Dedicated Developer",
                description = "40+ hours in IDE",
                icon = "💪",
                isNew = true
            ))
        }
        
        return achievements
    }

    private fun generateComparison(
        current: com.sidekick.gamification.stats.AggregateStats,
        previous: com.sidekick.gamification.stats.AggregateStats
    ): WeekComparison {
        val commitsChange = current.totalCommits - previous.totalCommits
        val linesChange = current.totalLinesAdded - previous.totalLinesAdded
        val timeChange = current.totalTimeInIde.minus(previous.totalTimeInIde)
        
        val trend = when {
            commitsChange > 5 && linesChange > 100 -> Trend.UP
            commitsChange < -5 && linesChange < -100 -> Trend.DOWN
            else -> Trend.STABLE
        }
        
        return WeekComparison(commitsChange, linesChange, timeChange, trend)
    }

    private fun generateRecommendations(stats: com.sidekick.gamification.stats.AggregateStats): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (stats.averageDailyTime.toHours() < 2) {
            recommendations.add("Try to spend more focused time coding each day")
        }
        
        if (stats.totalCommits < 5) {
            recommendations.add("Consider making smaller, more frequent commits")
        }
        
        return recommendations
    }
}
```

---

### v0.7.2c — DigestNotification

**Goal:** Notification popup for weekly digest.

#### DigestNotification.kt

```kotlin
package com.sidekick.gamification.digest

import com.intellij.notification.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Shows the weekly digest notification.
 */
class DigestNotificationService(private val project: Project) {

    companion object {
        private val NOTIFICATION_GROUP = NotificationGroupManager.getInstance()
            .getNotificationGroup("Sidekick Weekly Digest")
    }

    fun showDigestNotification(digest: WeeklyDigest) {
        val content = buildString {
            appendLine("📊 **Your Week: ${digest.weekStart} - ${digest.weekEnd}**")
            appendLine()
            appendLine("• Commits: ${digest.stats.totalCommits}")
            appendLine("• Lines written: ${digest.stats.totalLinesAdded}")
            appendLine("• Time in IDE: ${digest.stats.totalTimeInIde.toHours()}h")
            
            if (digest.achievements.isNotEmpty()) {
                appendLine()
                appendLine("🏆 Achievements:")
                digest.achievements.forEach { achievement ->
                    appendLine("  ${achievement.icon} ${achievement.name}")
                }
            }
        }

        NOTIFICATION_GROUP.createNotification(
            "Weekly Digest",
            content,
            NotificationType.INFORMATION
        ).addAction(object : AnAction("View Full Report") {
            override fun actionPerformed(e: AnActionEvent) {
                // Open digest tool window
            }
        }).notify(project)
    }
}
```

---

## v0.7.3 — Code Combo

### v0.7.3a — ComboModels

**Goal:** Data structures for combo system.

#### ComboModels.kt

```kotlin
package com.sidekick.gamification.combo

import java.time.Instant

/**
 * Current combo state.
 */
data class ComboState(
    val currentCombo: Int = 0,
    val multiplier: Float = 1.0f,
    val lastKeystroke: Instant? = null,
    val comboStartTime: Instant? = null,
    val bestCombo: Int = 0,
    val totalCombos: Int = 0
) {
    val isActive: Boolean get() = currentCombo > 0
    val tier: ComboTier get() = ComboTier.forCombo(currentCombo)
}

/**
 * Combo tiers with visual effects.
 */
enum class ComboTier(
    val minCombo: Int,
    val multiplier: Float,
    val color: java.awt.Color,
    val effectName: String
) {
    NONE(0, 1.0f, java.awt.Color.WHITE, ""),
    WARM(10, 1.1f, java.awt.Color(255, 200, 100), "Warm Up"),
    HOT(25, 1.25f, java.awt.Color(255, 150, 50), "Getting Hot"),
    FIRE(50, 1.5f, java.awt.Color(255, 100, 0), "On Fire!"),
    ULTRA(100, 2.0f, java.awt.Color(255, 50, 50), "ULTRA COMBO!"),
    LEGENDARY(200, 3.0f, java.awt.Color(200, 0, 255), "LEGENDARY!");

    companion object {
        fun forCombo(combo: Int): ComboTier {
            return entries.reversed().find { combo >= it.minCombo } ?: NONE
        }
    }
}

/**
 * Particle effect for combos.
 */
data class ComboParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: java.awt.Color,
    val size: Float
)

/**
 * Combo configuration.
 */
data class ComboConfig(
    val enabled: Boolean = true,
    val comboTimeoutMs: Long = 1500,
    val showParticles: Boolean = true,
    val showMultiplier: Boolean = true,
    val showComboCount: Boolean = true,
    val particleDensity: Int = 5, // particles per keystroke
    val soundEnabled: Boolean = false
)
```

---

### v0.7.3b — ComboService

**Goal:** Service to track and manage combos.

#### ComboService.kt

```kotlin
package com.sidekick.gamification.combo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import java.time.Duration
import java.time.Instant

@Service(Service.Level.APP)
@State(name = "SidekickCombo", storages = [Storage("sidekick-combo.xml")])
class ComboService : PersistentStateComponent<ComboService.State> {

    data class State(
        var config: ComboConfig = ComboConfig(),
        var bestCombo: Int = 0,
        var totalCombos: Int = 0
    )

    private var state = State()
    private var comboState = ComboState()
    private val listeners = mutableListOf<ComboListener>()

    companion object {
        fun getInstance(): ComboService {
            return ApplicationManager.getApplication().getService(ComboService::class.java)
        }
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Registers a combo listener.
     */
    fun addListener(listener: ComboListener) {
        listeners.add(listener)
    }

    /**
     * Gets current combo state.
     */
    fun getComboState(): ComboState = comboState

    /**
     * Records a keystroke.
     */
    fun recordKeystroke() {
        if (!state.config.enabled) return
        
        val now = Instant.now()
        val lastTime = comboState.lastKeystroke
        
        comboState = if (lastTime != null && 
            Duration.between(lastTime, now).toMillis() < state.config.comboTimeoutMs) {
            // Continue combo
            val newCombo = comboState.currentCombo + 1
            val newTier = ComboTier.forCombo(newCombo)
            
            comboState.copy(
                currentCombo = newCombo,
                multiplier = newTier.multiplier,
                lastKeystroke = now
            ).also {
                if (comboState.tier != newTier) {
                    notifyTierChange(newTier)
                }
                notifyComboUpdate(it)
            }
        } else {
            // Start new combo
            if (comboState.currentCombo > 0) {
                recordComboEnd(comboState)
            }
            ComboState(
                currentCombo = 1,
                multiplier = 1.0f,
                lastKeystroke = now,
                comboStartTime = now,
                bestCombo = state.bestCombo,
                totalCombos = state.totalCombos
            )
        }
    }

    /**
     * Checks for combo timeout.
     */
    fun checkTimeout() {
        val lastTime = comboState.lastKeystroke ?: return
        if (Duration.between(lastTime, Instant.now()).toMillis() > state.config.comboTimeoutMs) {
            if (comboState.currentCombo > 0) {
                recordComboEnd(comboState)
                comboState = ComboState(bestCombo = state.bestCombo, totalCombos = state.totalCombos)
                notifyComboEnd()
            }
        }
    }

    private fun recordComboEnd(combo: ComboState) {
        if (combo.currentCombo > state.bestCombo) {
            state.bestCombo = combo.currentCombo
        }
        state.totalCombos++
    }

    private fun notifyComboUpdate(combo: ComboState) {
        listeners.forEach { it.onComboUpdate(combo) }
    }

    private fun notifyTierChange(tier: ComboTier) {
        listeners.forEach { it.onTierChange(tier) }
    }

    private fun notifyComboEnd() {
        listeners.forEach { it.onComboEnd() }
    }
}

/**
 * Combo event listener.
 */
interface ComboListener {
    fun onComboUpdate(combo: ComboState)
    fun onTierChange(tier: ComboTier)
    fun onComboEnd()
}
```

---

### v0.7.3c — ComboOverlay

**Goal:** Visual overlay for combo effects.

#### ComboOverlay.kt

```kotlin
package com.sidekick.gamification.combo

import com.intellij.openapi.editor.Editor
import java.awt.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.*
import kotlin.random.Random

/**
 * Editor overlay for combo visualization.
 */
class ComboOverlay(private val editor: Editor) : JPanel(), ComboListener {
    
    private val particles = CopyOnWriteArrayList<ComboParticle>()
    private var comboState = ComboState()
    private val timer: Timer

    init {
        isOpaque = false
        ComboService.getInstance().addListener(this)
        
        timer = Timer(16) { // ~60 FPS
            updateParticles()
            repaint()
        }
        timer.start()
    }

    override fun onComboUpdate(combo: ComboState) {
        comboState = combo
        if (ComboService.getInstance().state.config.showParticles) {
            spawnParticles(combo.tier)
        }
    }

    override fun onTierChange(tier: ComboTier) {
        // Burst of particles on tier change
        repeat(20) { spawnParticles(tier) }
    }

    override fun onComboEnd() {
        comboState = ComboState()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Draw particles
        particles.forEach { particle ->
            g2d.color = Color(
                particle.color.red,
                particle.color.green,
                particle.color.blue,
                (particle.life * 255).toInt().coerceIn(0, 255)
            )
            g2d.fillOval(
                particle.x.toInt(),
                particle.y.toInt(),
                particle.size.toInt(),
                particle.size.toInt()
            )
        }
        
        // Draw combo counter
        if (comboState.isActive && ComboService.getInstance().state.config.showComboCount) {
            drawComboCounter(g2d)
        }
    }

    private fun drawComboCounter(g2d: Graphics2D) {
        val tier = comboState.tier
        g2d.font = Font("SansSerif", Font.BOLD, 24)
        g2d.color = tier.color
        
        val text = "${comboState.currentCombo}x"
        val metrics = g2d.fontMetrics
        val x = width - metrics.stringWidth(text) - 20
        val y = 40
        
        // Shadow
        g2d.color = Color.BLACK
        g2d.drawString(text, x + 2, y + 2)
        
        // Text
        g2d.color = tier.color
        g2d.drawString(text, x, y)
        
        // Tier name
        if (tier != ComboTier.NONE) {
            g2d.font = Font("SansSerif", Font.BOLD, 14)
            g2d.drawString(tier.effectName, x, y + 20)
        }
    }

    private fun spawnParticles(tier: ComboTier) {
        val config = ComboService.getInstance().state.config
        val caretPos = editor.visualPositionToXY(editor.caretModel.visualPosition)
        
        repeat(config.particleDensity) {
            particles.add(ComboParticle(
                x = caretPos.x.toFloat(),
                y = caretPos.y.toFloat(),
                vx = Random.nextFloat() * 4 - 2,
                vy = Random.nextFloat() * -3 - 1,
                life = 1.0f,
                color = tier.color,
                size = Random.nextFloat() * 4 + 2
            ))
        }
    }

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.1f // gravity
            p.life -= 0.02f
            
            if (p.life <= 0) {
                particles.remove(p)
            }
        }
    }

    fun dispose() {
        timer.stop()
    }
}
```

---

## v0.7.4 — Developer XP

### v0.7.4a — XpModels

**Goal:** Data structures for XP and leveling system.

#### XpModels.kt

```kotlin
package com.sidekick.gamification.xp

/**
 * Developer profile with XP.
 */
data class DeveloperProfile(
    val totalXp: Long = 0,
    val level: Int = 1,
    val skillPoints: Map<SkillTree, Int> = emptyMap(),
    val achievements: List<Achievement> = emptyList(),
    val badges: List<Badge> = emptyList()
) {
    val xpToNextLevel: Long get() = calculateXpForLevel(level + 1) - totalXp
    val levelProgress: Float get() {
        val currentLevelXp = calculateXpForLevel(level)
        val nextLevelXp = calculateXpForLevel(level + 1)
        return (totalXp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp)
    }

    companion object {
        fun calculateXpForLevel(level: Int): Long = (100 * level * level).toLong()
        fun levelForXp(xp: Long): Int {
            var level = 1
            while (calculateXpForLevel(level + 1) <= xp) level++
            return level
        }
    }
}

/**
 * XP earning activities.
 */
enum class XpActivity(val baseXp: Int, val description: String) {
    COMMIT(25, "Made a commit"),
    LINES_WRITTEN(1, "Per 10 lines written"),
    TEST_PASSED(10, "Test passed"),
    BUG_FIXED(50, "Fixed a bug"),
    REFACTOR(30, "Refactored code"),
    DOCUMENTATION(20, "Added documentation"),
    CODE_REVIEW(15, "Reviewed code"),
    BUILD_SUCCESS(5, "Successful build"),
    STREAK_BONUS(100, "Daily streak bonus")
}

/**
 * Skill trees for specialization.
 */
enum class SkillTree(val displayName: String, val icon: String) {
    TESTING("Testing Mastery", "🧪"),
    REFACTORING("Refactoring Guru", "♻️"),
    DOCUMENTATION("Documentation Expert", "📚"),
    DEBUGGING("Bug Hunter", "🐛"),
    PERFORMANCE("Performance Wizard", "⚡")
}

/**
 * Achievement definition.
 */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val xpReward: Int,
    val isSecret: Boolean = false,
    val unlockedAt: java.time.Instant? = null
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}

/**
 * Badge for display.
 */
data class Badge(
    val id: String,
    val name: String,
    val icon: String,
    val tier: BadgeTier,
    val category: String
)

/**
 * Badge tiers.
 */
enum class BadgeTier(val color: java.awt.Color) {
    BRONZE(java.awt.Color(205, 127, 50)),
    SILVER(java.awt.Color(192, 192, 192)),
    GOLD(java.awt.Color(255, 215, 0)),
    PLATINUM(java.awt.Color(229, 228, 226)),
    DIAMOND(java.awt.Color(185, 242, 255))
}

/**
 * XP configuration.
 */
data class XpConfig(
    val enabled: Boolean = true,
    val showLevelUpNotification: Boolean = true,
    val showXpGain: Boolean = true,
    val xpMultiplier: Float = 1.0f
)
```

---

### v0.7.4b — XpService

**Goal:** Service to manage XP and achievements.

#### XpService.kt

```kotlin
package com.sidekick.gamification.xp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import java.time.Instant

@Service(Service.Level.APP)
@State(name = "SidekickXp", storages = [Storage("sidekick-xp.xml")])
class XpService : PersistentStateComponent<XpService.State> {

    data class State(
        var config: XpConfig = XpConfig(),
        var profile: DeveloperProfile = DeveloperProfile()
    )

    private var state = State()
    private val listeners = mutableListOf<XpListener>()

    companion object {
        fun getInstance(): XpService {
            return ApplicationManager.getApplication().getService(XpService::class.java)
        }
        
        val ALL_ACHIEVEMENTS = listOf(
            Achievement("first_commit", "First Steps", "Make your first commit", "🎯", 100),
            Achievement("100_commits", "Century", "Make 100 commits", "💯", 500),
            Achievement("1000_lines", "Prolific Writer", "Write 1000 lines of code", "✍️", 250),
            Achievement("streak_7", "Week Warrior", "7 day coding streak", "🔥", 300),
            Achievement("streak_30", "Monthly Master", "30 day coding streak", "🌟", 1000),
            Achievement("all_tests_pass", "Green Machine", "100 tests passed in one session", "✅", 200),
            Achievement("night_owl", "Night Owl", "Code after midnight", "🦉", 50, isSecret = true),
            Achievement("early_bird", "Early Bird", "Code before 6am", "🐦", 50, isSecret = true)
        )
    }

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    /**
     * Adds a listener.
     */
    fun addListener(listener: XpListener) {
        listeners.add(listener)
    }

    /**
     * Gets the current profile.
     */
    fun getProfile(): DeveloperProfile = state.profile

    /**
     * Awards XP for an activity.
     */
    fun awardXp(activity: XpActivity, multiplier: Float = 1.0f) {
        if (!state.config.enabled) return
        
        val xp = (activity.baseXp * multiplier * state.config.xpMultiplier).toLong()
        val oldLevel = state.profile.level
        
        state.profile = state.profile.copy(
            totalXp = state.profile.totalXp + xp,
            level = DeveloperProfile.levelForXp(state.profile.totalXp + xp)
        )
        
        if (state.config.showXpGain) {
            notifyXpGain(xp, activity)
        }
        
        if (state.profile.level > oldLevel) {
            notifyLevelUp(state.profile.level)
        }
        
        checkAchievements()
    }

    /**
     * Adds skill points.
     */
    fun addSkillPoint(tree: SkillTree) {
        val current = state.profile.skillPoints.getOrDefault(tree, 0)
        state.profile = state.profile.copy(
            skillPoints = state.profile.skillPoints + (tree to current + 1)
        )
    }

    /**
     * Unlocks an achievement.
     */
    fun unlockAchievement(achievementId: String) {
        val achievement = ALL_ACHIEVEMENTS.find { it.id == achievementId } ?: return
        if (state.profile.achievements.any { it.id == achievementId }) return
        
        val unlocked = achievement.copy(unlockedAt = Instant.now())
        state.profile = state.profile.copy(
            achievements = state.profile.achievements + unlocked,
            totalXp = state.profile.totalXp + achievement.xpReward
        )
        
        notifyAchievement(unlocked)
    }

    /**
     * Gets progress towards achievements.
     */
    fun getAchievementProgress(): Map<String, Float> {
        // Would track progress for each achievement
        return emptyMap()
    }

    private fun checkAchievements() {
        // Check criteria for each achievement
        // This would be called after various activities
    }

    private fun notifyXpGain(xp: Long, activity: XpActivity) {
        listeners.forEach { it.onXpGain(xp, activity) }
    }

    private fun notifyLevelUp(level: Int) {
        listeners.forEach { it.onLevelUp(level) }
    }

    private fun notifyAchievement(achievement: Achievement) {
        listeners.forEach { it.onAchievement(achievement) }
    }
}

/**
 * XP event listener.
 */
interface XpListener {
    fun onXpGain(xp: Long, activity: XpActivity)
    fun onLevelUp(level: Int)
    fun onAchievement(achievement: Achievement)
}
```

---

### v0.7.4c — XpProfilePanel

**Goal:** UI for viewing XP and achievements.

#### XpProfilePanel.kt

```kotlin
package com.sidekick.gamification.xp

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import java.awt.*
import javax.swing.*

class XpToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = XpProfilePanel()
        toolWindow.contentManager.addContent(
            toolWindow.contentManager.factory.createContent(panel, "", false)
        )
    }
}

class XpProfilePanel : JBPanel<XpProfilePanel>() {
    private val service = XpService.getInstance()

    init {
        layout = BorderLayout()
        add(createProfileSection(), BorderLayout.NORTH)
        add(createTabbedPane(), BorderLayout.CENTER)
    }

    private fun createProfileSection(): JPanel {
        val profile = service.getProfile()
        
        return panel {
            row {
                label("Level ${profile.level}").bold()
                cell(LevelProgressBar(profile)).align(Align.FILL)
            }
            row {
                label("Total XP: ${profile.totalXp}")
                label("Next level: ${profile.xpToNextLevel} XP")
            }
        }
    }

    private fun createTabbedPane(): JBTabbedPane {
        val tabs = JBTabbedPane()
        tabs.addTab("Achievements", createAchievementsPanel())
        tabs.addTab("Skill Trees", createSkillTreesPanel())
        tabs.addTab("Badges", createBadgesPanel())
        return tabs
    }

    private fun createAchievementsPanel(): JPanel {
        val profile = service.getProfile()
        
        return panel {
            group("Unlocked (${profile.achievements.size}/${XpService.ALL_ACHIEVEMENTS.filter { !it.isSecret }.size})") {
                XpService.ALL_ACHIEVEMENTS.filter { !it.isSecret }.forEach { achievement ->
                    val unlocked = profile.achievements.find { it.id == achievement.id }
                    row {
                        label(if (unlocked != null) achievement.icon else "🔒")
                        label(achievement.name).bold(unlocked != null)
                        label(if (unlocked != null) "✓" else "${achievement.xpReward} XP")
                    }
                }
            }
        }
    }

    private fun createSkillTreesPanel(): JPanel {
        val profile = service.getProfile()
        
        return panel {
            SkillTree.entries.forEach { tree ->
                val points = profile.skillPoints.getOrDefault(tree, 0)
                row {
                    label("${tree.icon} ${tree.displayName}")
                    cell(SkillBar(points, 10))
                }
            }
        }
    }

    private fun createBadgesPanel(): JPanel {
        val profile = service.getProfile()
        
        return panel {
            row {
                profile.badges.forEach { badge ->
                    cell(BadgeIcon(badge))
                }
                if (profile.badges.isEmpty()) {
                    label("No badges yet. Keep coding!")
                }
            }
        }
    }
}

/**
 * Level progress bar component.
 */
class LevelProgressBar(private val profile: DeveloperProfile) : JPanel() {
    init {
        preferredSize = Dimension(200, 20)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Background
        g2d.color = Color(60, 60, 60)
        g2d.fillRoundRect(0, 0, width, height, 10, 10)
        
        // Progress
        val progressWidth = (width * profile.levelProgress).toInt()
        g2d.color = Color(76, 175, 80)
        g2d.fillRoundRect(0, 0, progressWidth, height, 10, 10)
        
        // Text
        g2d.color = Color.WHITE
        g2d.font = Font("SansSerif", Font.BOLD, 12)
        val text = "${(profile.levelProgress * 100).toInt()}%"
        val metrics = g2d.fontMetrics
        g2d.drawString(text, (width - metrics.stringWidth(text)) / 2, (height + metrics.ascent) / 2 - 2)
    }
}

class SkillBar(private val points: Int, private val max: Int) : JPanel() {
    init { preferredSize = Dimension(150, 16) }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        
        repeat(max) { i ->
            g2d.color = if (i < points) Color(100, 200, 255) else Color(60, 60, 60)
            g2d.fillRect(i * 15, 0, 12, height)
        }
    }
}

class BadgeIcon(private val badge: Badge) : JPanel() {
    init { 
        preferredSize = Dimension(50, 60)
        toolTipText = badge.name
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.color = badge.tier.color
        g2d.fillOval(5, 5, 40, 40)
        g2d.color = Color.WHITE
        g2d.font = Font("SansSerif", Font.PLAIN, 20)
        g2d.drawString(badge.icon, 15, 32)
    }
}
```

---

## plugin.xml Additions

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Gamification Services (v0.7.x) -->
    <projectService serviceImplementation="com.sidekick.gamification.stats.StatsService"/>
    <projectService serviceImplementation="com.sidekick.gamification.digest.DigestService"/>
    <applicationService serviceImplementation="com.sidekick.gamification.combo.ComboService"/>
    <applicationService serviceImplementation="com.sidekick.gamification.xp.XpService"/>
    
    <!-- Tool Windows -->
    <toolWindow id="Daily Stats" 
                icon="/icons/stats.svg"
                anchor="right"
                factoryClass="com.sidekick.gamification.stats.StatsDashboardFactory"/>
                
    <toolWindow id="Developer XP" 
                icon="/icons/xp.svg"
                anchor="right"
                factoryClass="com.sidekick.gamification.xp.XpToolWindowFactory"/>
    
    <!-- Notifications -->
    <notificationGroup id="Sidekick Weekly Digest" displayType="BALLOON"/>
    <notificationGroup id="Sidekick Level Up" displayType="BALLOON"/>
    <notificationGroup id="Sidekick Achievement" displayType="BALLOON"/>
</extensions>

<actions>
    <action id="Sidekick.ShowStats"
            class="com.sidekick.gamification.stats.ShowStatsAction"
            text="Show Daily Stats">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.ShowDigest"
            class="com.sidekick.gamification.digest.ShowDigestAction"
            text="Show Weekly Digest">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
    
    <action id="Sidekick.ToggleCombo"
            class="com.sidekick.gamification.combo.ToggleComboAction"
            text="Toggle Code Combo">
        <add-to-group group-id="Sidekick.ActionGroup"/>
    </action>
</actions>
```

---

## Verification Plan

### Automated Tests

```bash
./gradlew test --tests "com.sidekick.gamification.*"
```

### Manual Verification

| Version | Step | Expected Result |
|---------|------|-----------------|
| v0.7.1 | Open Stats Dashboard | Shows today's stats |
| v0.7.1 | Write code for an hour | Time tracked |
| v0.7.2 | Wait for Monday | Digest notification appears |
| v0.7.3 | Type rapidly | Combo counter appears |
| v0.7.3 | Reach 50 combo | "On Fire!" effect triggers |
| v0.7.4 | Make a commit | XP awarded, notification shown |
| v0.7.4 | Level up | Level up notification shown |

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Stats accuracy | >95% vs actual Git data |
| Combo detection latency | <50ms |
| XP calculation accuracy | 100% |
| Memory overhead | <10MB |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 0.7.0 | 2026-02-04 | Ryan | Initial v0.7.x design specification |
