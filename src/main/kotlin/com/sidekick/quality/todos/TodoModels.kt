package com.sidekick.quality.todos

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * # TODO Tracker Models
 *
 * Data structures for deadline-aware TODO management.
 * Part of Sidekick v0.6.2 TODO Tracker feature.
 *
 * ## Overview
 *
 * These models support:
 * - TODO extraction from code comments
 * - Deadline tracking and status calculation
 * - Priority and type classification
 * - Author and tag extraction
 *
 * @since 0.6.2
 */

/**
 * A TODO item extracted from code.
 *
 * @property id Unique identifier for the TODO
 * @property text The TODO text content (cleaned)
 * @property type Type of TODO marker (TODO, FIXME, etc.)
 * @property priority Priority level
 * @property location Location in source code
 * @property deadline Optional due date
 * @property author Optional author (from @mentions)
 * @property createdDate Optional creation date
 * @property tags List of tags (from #hashtags)
 */
data class TodoItem(
    val id: String,
    val text: String,
    val type: TodoType,
    val priority: TodoPriority,
    val location: TodoLocation,
    val deadline: LocalDate?,
    val author: String?,
    val createdDate: LocalDate?,
    val tags: List<String>
) {
    /**
     * Whether this TODO is past its deadline.
     */
    val isOverdue: Boolean
        get() = deadline?.let { LocalDate.now().isAfter(it) } ?: false

    /**
     * Days until deadline (negative if overdue).
     * Null if no deadline is set.
     */
    val daysUntilDue: Long?
        get() = deadline?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    /**
     * Calculated status based on deadline.
     */
    val status: TodoStatus
        get() = when {
            isOverdue -> TodoStatus.OVERDUE
            daysUntilDue?.let { it <= 3 } == true -> TodoStatus.DUE_SOON
            else -> TodoStatus.OPEN
        }

    /**
     * Whether this TODO has a deadline.
     */
    val hasDeadline: Boolean get() = deadline != null

    /**
     * Display string for the deadline.
     */
    val deadlineDisplay: String
        get() = when {
            deadline == null -> "No deadline"
            isOverdue -> "Overdue by ${-daysUntilDue!!} days"
            daysUntilDue == 0L -> "Due today"
            daysUntilDue == 1L -> "Due tomorrow"
            else -> "Due in $daysUntilDue days"
        }

    companion object {
        /**
         * Creates a minimal TODO for testing.
         */
        fun simple(
            text: String,
            type: TodoType = TodoType.TODO,
            priority: TodoPriority = TodoPriority.MEDIUM,
            deadline: LocalDate? = null
        ) = TodoItem(
            id = java.util.UUID.randomUUID().toString(),
            text = text,
            type = type,
            priority = priority,
            location = TodoLocation("", 0, 0, null),
            deadline = deadline,
            author = null,
            createdDate = null,
            tags = emptyList()
        )
    }
}

/**
 * TODO location in code.
 *
 * @property filePath Absolute path to the source file
 * @property line 1-based line number
 * @property column 0-based column offset
 * @property contextSnippet Optional surrounding code context
 */
data class TodoLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val contextSnippet: String?
) {
    /**
     * File name without path.
     */
    val fileName: String get() = filePath.substringAfterLast("/")

    /**
     * Display string for location.
     */
    val displayString: String get() = "$fileName:$line"
}

/**
 * Types of TODO markers.
 *
 * @property patterns Text patterns that identify this type
 * @property icon Emoji icon for display
 * @property description Human-readable description
 */
enum class TodoType(
    val patterns: List<String>,
    val icon: String,
    val description: String
) {
    /** Standard TODO - work to be done */
    TODO(listOf("TODO", "TODO:"), "📝", "Work to be done"),

    /** FIXME - broken code that needs fixing */
    FIXME(listOf("FIXME", "FIXME:"), "🔧", "Needs fixing"),

    /** HACK - temporary workaround */
    HACK(listOf("HACK", "HACK:"), "⚠️", "Temporary workaround"),

    /** BUG - known bug */
    BUG(listOf("BUG", "BUG:"), "🐛", "Known bug"),

    /** NOTE - documentation note */
    NOTE(listOf("NOTE", "NOTE:"), "📌", "Documentation note"),

    /** OPTIMIZE - performance improvement needed */
    OPTIMIZE(listOf("OPTIMIZE", "PERF:"), "⚡", "Performance improvement"),

    /** REVIEW - needs code review */
    REVIEW(listOf("REVIEW", "REVIEW:"), "👀", "Needs review"),

    /** DEPRECATED - marked for removal */
    DEPRECATED(listOf("DEPRECATED"), "🚫", "Marked for removal");

    override fun toString(): String = name

    companion object {
        /**
         * Detects TODO type from text.
         * Returns TODO as default if no pattern matches.
         */
        fun detect(text: String): TodoType {
            val upper = text.uppercase()
            return entries.find { type ->
                type.patterns.any { pattern -> upper.startsWith(pattern) }
            } ?: TODO
        }

        /**
         * All types in display order.
         */
        val ALL = entries.toList()

        /**
         * Finds type by name (case-insensitive).
         */
        fun byName(name: String): TodoType {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: TODO
        }

        /**
         * Checks if text contains any TODO pattern.
         */
        fun containsPattern(text: String): Boolean {
            val upper = text.uppercase()
            return entries.any { type ->
                type.patterns.any { pattern -> upper.contains(pattern) }
            }
        }
    }
}

/**
 * Priority levels for TODOs.
 *
 * @property weight Numeric weight for sorting (higher = more important)
 * @property displayName Human-readable name
 */
enum class TodoPriority(val weight: Int, val displayName: String) {
    /** Urgent, must be done immediately */
    CRITICAL(4, "Critical"),

    /** Important, should be done soon */
    HIGH(3, "High"),

    /** Normal priority */
    MEDIUM(2, "Medium"),

    /** Can be deferred */
    LOW(1, "Low");

    override fun toString(): String = displayName

    companion object {
        /**
         * Detects priority from text content.
         * Looks for priority markers like CRITICAL, URGENT, !!, !!!
         */
        fun detect(text: String): TodoPriority {
            val upper = text.uppercase()
            return when {
                upper.contains("CRITICAL") || upper.contains("!!!") -> CRITICAL
                upper.contains("URGENT") || upper.contains("!!") -> HIGH
                upper.contains("LOW") || upper.contains("MINOR") -> LOW
                else -> MEDIUM
            }
        }

        /**
         * All priorities in weight order (highest first).
         */
        val ALL = entries.sortedByDescending { it.weight }

        /**
         * Finds priority by name (case-insensitive).
         */
        fun byName(name: String): TodoPriority {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
        }
    }
}

/**
 * TODO status based on deadline.
 *
 * @property displayName Human-readable name
 * @property isUrgent Whether this status requires attention
 */
enum class TodoStatus(val displayName: String, val isUrgent: Boolean) {
    /** No deadline or deadline far away */
    OPEN("Open", false),

    /** Deadline within 3 days */
    DUE_SOON("Due Soon", true),

    /** Past deadline */
    OVERDUE("Overdue", true),

    /** Marked as completed */
    COMPLETED("Completed", false);

    override fun toString(): String = displayName

    companion object {
        /**
         * Finds status by name (case-insensitive).
         */
        fun byName(name: String): TodoStatus {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OPEN
        }
    }
}

/**
 * Configuration for TODO tracking.
 *
 * @property enabled Whether the feature is active
 * @property scanOnOpen Scan files when opened in editor
 * @property showNotifications Show notifications for important TODOs
 * @property overdueNotifications Show notifications for overdue TODOs
 * @property dueSoonDays Days before deadline to consider "due soon"
 * @property customPatterns Additional TODO patterns to detect
 * @property datePatterns Regex patterns for extracting deadlines
 */
data class TodoConfig(
    val enabled: Boolean = true,
    val scanOnOpen: Boolean = true,
    val showNotifications: Boolean = true,
    val overdueNotifications: Boolean = true,
    val dueSoonDays: Int = 3,
    val customPatterns: List<String> = emptyList(),
    val datePatterns: List<String> = listOf(
        """(\d{4}-\d{2}-\d{2})""",           // 2024-12-31
        """by\s+(\d{1,2}/\d{1,2}/\d{4})"""   // by 12/31/2024
    )
) {
    /**
     * Returns config with feature toggled.
     */
    fun toggle() = copy(enabled = !enabled)

    /**
     * Returns config with notifications toggled.
     */
    fun toggleNotifications() = copy(showNotifications = !showNotifications)

    /**
     * Returns config with custom pattern added.
     */
    fun withPattern(pattern: String) = copy(
        customPatterns = customPatterns + pattern
    )

    companion object {
        /** Disabled configuration */
        val DISABLED = TodoConfig(enabled = false)

        /** Silent mode - no notifications */
        val SILENT = TodoConfig(showNotifications = false, overdueNotifications = false)
    }
}

/**
 * Summary statistics for TODO collection.
 */
data class TodoSummary(
    val total: Int,
    val overdue: Int,
    val dueSoon: Int,
    val byType: Map<TodoType, Int>,
    val byPriority: Map<TodoPriority, Int>
) {
    /**
     * Whether there are urgent items requiring attention.
     */
    val hasUrgent: Boolean get() = overdue > 0 || dueSoon > 0

    companion object {
        /**
         * Computes summary from a list of TODOs.
         */
        fun from(todos: List<TodoItem>): TodoSummary {
            return TodoSummary(
                total = todos.size,
                overdue = todos.count { it.isOverdue },
                dueSoon = todos.count { it.status == TodoStatus.DUE_SOON },
                byType = todos.groupingBy { it.type }.eachCount(),
                byPriority = todos.groupingBy { it.priority }.eachCount()
            )
        }

        val EMPTY = TodoSummary(0, 0, 0, emptyMap(), emptyMap())
    }
}
