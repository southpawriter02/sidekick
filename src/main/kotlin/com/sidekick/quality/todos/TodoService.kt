package com.sidekick.quality.todos

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * # TODO Tracker Service
 *
 * Project-level service for scanning, managing, and querying TODOs.
 * Part of Sidekick v0.6.2 TODO Tracker feature.
 *
 * ## Features
 *
 * - PSI-based comment scanning
 * - Deadline extraction from various formats
 * - Author and tag extraction
 * - Grouping and filtering queries
 * - Persistent storage of TODO state
 *
 * @since 0.6.2
 */
@Service(Service.Level.PROJECT)
@State(name = "SidekickTodos", storages = [Storage("sidekick-todos.xml")])
class TodoService(private val project: Project) : PersistentStateComponent<TodoService.State> {

    private val logger = Logger.getInstance(TodoService::class.java)
    private var state = State()

    /**
     * Persistent state for the TODO tracker.
     */
    data class State(
        var enabled: Boolean = true,
        var scanOnOpen: Boolean = true,
        var showNotifications: Boolean = true,
        var overdueNotifications: Boolean = true,
        var dueSoonDays: Int = 3,
        var customPatterns: MutableList<String> = mutableListOf(),
        var todos: MutableList<SerializedTodoItem> = mutableListOf()
    ) {
        // No-arg constructor for serialization
        constructor() : this(true, true, true, true, 3, mutableListOf(), mutableListOf())

        fun toConfig(): TodoConfig {
            return TodoConfig(
                enabled = enabled,
                scanOnOpen = scanOnOpen,
                showNotifications = showNotifications,
                overdueNotifications = overdueNotifications,
                dueSoonDays = dueSoonDays,
                customPatterns = customPatterns.toList()
            )
        }

        companion object {
            fun from(config: TodoConfig) = State(
                enabled = config.enabled,
                scanOnOpen = config.scanOnOpen,
                showNotifications = config.showNotifications,
                overdueNotifications = config.overdueNotifications,
                dueSoonDays = config.dueSoonDays,
                customPatterns = config.customPatterns.toMutableList()
            )
        }
    }

    /**
     * Serializable TODO item for persistence.
     */
    data class SerializedTodoItem(
        var id: String = "",
        var text: String = "",
        var typeName: String = "TODO",
        var priorityName: String = "MEDIUM",
        var filePath: String = "",
        var line: Int = 0,
        var column: Int = 0,
        var deadline: String? = null,
        var author: String? = null,
        var createdDate: String? = null,
        var tags: MutableList<String> = mutableListOf()
    ) {
        // No-arg constructor for serialization
        constructor() : this("", "", "TODO", "MEDIUM", "", 0, 0, null, null, null, mutableListOf())

        fun toTodoItem(): TodoItem {
            return TodoItem(
                id = id,
                text = text,
                type = TodoType.byName(typeName),
                priority = TodoPriority.byName(priorityName),
                location = TodoLocation(filePath, line, column, null),
                deadline = deadline?.let { LocalDate.parse(it) },
                author = author,
                createdDate = createdDate?.let { LocalDate.parse(it) },
                tags = tags.toList()
            )
        }

        companion object {
            fun from(item: TodoItem) = SerializedTodoItem(
                id = item.id,
                text = item.text,
                typeName = item.type.name,
                priorityName = item.priority.name,
                filePath = item.location.filePath,
                line = item.location.line,
                column = item.location.column,
                deadline = item.deadline?.toString(),
                author = item.author,
                createdDate = item.createdDate?.toString(),
                tags = item.tags.toMutableList()
            )
        }
    }

    companion object {
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): TodoService {
            return project.getService(TodoService::class.java)
        }

        /**
         * Date formats for parsing deadlines.
         */
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2024-12-31
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),           // 12/31/2024
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),           // 31/12/2024
            DateTimeFormatter.ofPattern("MMM d, yyyy"),          // Dec 31, 2024
            DateTimeFormatter.ofPattern("MMMM d, yyyy")          // December 31, 2024
        )

        /**
         * Default date patterns for extraction.
         */
        private val DEFAULT_DATE_PATTERNS = listOf(
            """(\d{4}-\d{2}-\d{2})""",                // 2024-12-31
            """by\s+(\d{1,2}/\d{1,2}/\d{4})""",      // by 12/31/2024
            """due:?\s*(\d{1,2}/\d{1,2}/\d{4})""",   // due: 12/31/2024
            """deadline:?\s*(\d{4}-\d{2}-\d{2})"""   // deadline: 2024-12-31
        )
    }

    override fun getState() = state

    override fun loadState(state: State) {
        this.state = state
        logger.info("Loaded ${state.todos.size} TODOs from storage")
    }

    /**
     * Current configuration.
     */
    val config: TodoConfig get() = state.toConfig()

    // -------------------------------------------------------------------------
    // Scanning Methods
    // -------------------------------------------------------------------------

    /**
     * Scans a file for TODOs.
     *
     * @param psiFile The file to scan
     * @return List of TODOs found in the file
     */
    fun scanFile(psiFile: PsiFile): List<TodoItem> {
        if (!state.enabled) return emptyList()

        val filePath = psiFile.virtualFile?.path ?: return emptyList()
        val todos = mutableListOf<TodoItem>()
        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)

        comments.forEach { comment ->
            val todo = parseComment(comment, psiFile)
            if (todo != null) todos.add(todo)
        }

        // Update stored todos for this file
        state.todos.removeIf { it.filePath == filePath }
        state.todos.addAll(todos.map { SerializedTodoItem.from(it) })

        logger.debug("Scanned $filePath: found ${todos.size} TODOs")
        return todos
    }

    /**
     * Removes all TODOs for a file.
     */
    fun clearFile(filePath: String) {
        state.todos.removeIf { it.filePath == filePath }
    }

    // -------------------------------------------------------------------------
    // Query Methods
    // -------------------------------------------------------------------------

    /**
     * Gets all TODOs across the project.
     */
    fun getAllTodos(): List<TodoItem> {
        return state.todos.map { it.toTodoItem() }
    }

    /**
     * Gets overdue TODOs.
     */
    fun getOverdueTodos(): List<TodoItem> {
        return getAllTodos().filter { it.isOverdue }
    }

    /**
     * Gets TODOs due within N days.
     *
     * @param days Number of days to look ahead
     */
    fun getTodosDueSoon(days: Int = state.dueSoonDays): List<TodoItem> {
        return getAllTodos().filter { todo ->
            todo.daysUntilDue?.let { it in 0..days } ?: false
        }
    }

    /**
     * Gets TODOs grouped by type.
     */
    fun getTodosByType(): Map<TodoType, List<TodoItem>> {
        return getAllTodos().groupBy { it.type }
    }

    /**
     * Gets TODOs grouped by file.
     */
    fun getTodosByFile(): Map<String, List<TodoItem>> {
        return getAllTodos().groupBy { it.location.filePath }
    }

    /**
     * Gets TODOs grouped by priority.
     */
    fun getTodosByPriority(): Map<TodoPriority, List<TodoItem>> {
        return getAllTodos().groupBy { it.priority }
    }

    /**
     * Gets TODOs grouped by status.
     */
    fun getTodosByStatus(): Map<TodoStatus, List<TodoItem>> {
        return getAllTodos().groupBy { it.status }
    }

    /**
     * Gets TODOs for a specific file.
     */
    fun getTodosForFile(filePath: String): List<TodoItem> {
        return getAllTodos().filter { it.location.filePath == filePath }
    }

    /**
     * Gets TODOs by author.
     */
    fun getTodosByAuthor(author: String): List<TodoItem> {
        return getAllTodos().filter { it.author == author }
    }

    /**
     * Gets TODOs with a specific tag.
     */
    fun getTodosWithTag(tag: String): List<TodoItem> {
        return getAllTodos().filter { tag in it.tags }
    }

    /**
     * Searches TODOs by text content.
     */
    fun searchTodos(query: String): List<TodoItem> {
        val lowerQuery = query.lowercase()
        return getAllTodos().filter { it.text.lowercase().contains(lowerQuery) }
    }

    /**
     * Gets summary statistics.
     */
    fun getSummary(): TodoSummary {
        return TodoSummary.from(getAllTodos())
    }

    // -------------------------------------------------------------------------
    // Mutation Methods
    // -------------------------------------------------------------------------

    /**
     * Marks a TODO as completed (removes it).
     *
     * @param todoId The TODO ID to mark complete
     * @return true if found and removed
     */
    fun markCompleted(todoId: String): Boolean {
        val removed = state.todos.removeIf { it.id == todoId }
        if (removed) {
            logger.info("Marked TODO $todoId as completed")
        }
        return removed
    }

    /**
     * Updates configuration.
     */
    fun updateConfig(config: TodoConfig) {
        state.enabled = config.enabled
        state.scanOnOpen = config.scanOnOpen
        state.showNotifications = config.showNotifications
        state.overdueNotifications = config.overdueNotifications
        state.dueSoonDays = config.dueSoonDays
        state.customPatterns = config.customPatterns.toMutableList()
        logger.info("Updated TODO config")
    }

    /**
     * Toggles the enabled state.
     */
    fun toggle(): Boolean {
        state.enabled = !state.enabled
        return state.enabled
    }

    /**
     * Clears all stored TODOs.
     */
    fun clearAll() {
        state.todos.clear()
        logger.info("Cleared all TODOs")
    }

    // -------------------------------------------------------------------------
    // Parsing Methods
    // -------------------------------------------------------------------------

    /**
     * Parses a comment into a TodoItem.
     */
    private fun parseComment(comment: PsiComment, file: PsiFile): TodoItem? {
        val text = comment.text.trim()

        // Must contain a TODO pattern
        if (!TodoType.containsPattern(text)) {
            return null
        }

        val doc = file.viewProvider.document
        val offset = comment.textRange?.startOffset ?: 0
        val line = doc?.getLineNumber(offset)?.plus(1) ?: 0

        return TodoItem(
            id = UUID.randomUUID().toString(),
            text = cleanTodoText(text),
            type = TodoType.detect(text),
            priority = TodoPriority.detect(text),
            location = TodoLocation(
                filePath = file.virtualFile?.path ?: "",
                line = line,
                column = 0,
                contextSnippet = null
            ),
            deadline = extractDeadline(text),
            author = extractAuthor(text),
            createdDate = null,
            tags = extractTags(text)
        )
    }

    /**
     * Cleans TODO text by removing comment markers and TODO keywords.
     */
    internal fun cleanTodoText(text: String): String {
        return text
            .removePrefix("//")
            .removePrefix("/*")
            .replace(Regex("""\s*\*/\s*$"""), "")
            .removePrefix("*")
            .replace(Regex("""TODO:?|FIXME:?|HACK:?|BUG:?|NOTE:?|OPTIMIZE:?|PERF:?|REVIEW:?|DEPRECATED:?""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * Extracts deadline from text using date patterns.
     */
    internal fun extractDeadline(text: String): LocalDate? {
        val patterns = DEFAULT_DATE_PATTERNS + state.toConfig().datePatterns

        for (pattern in patterns) {
            val match = Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            val dateStr = match?.groupValues?.getOrNull(1) ?: continue

            for (format in DATE_FORMATS) {
                try {
                    return LocalDate.parse(dateStr, format)
                } catch (_: Exception) {
                    // Try next format
                }
            }
        }
        return null
    }

    /**
     * Extracts author from @mention.
     */
    internal fun extractAuthor(text: String): String? {
        val match = Regex("""@(\w+)""").find(text)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts tags from #hashtags.
     */
    internal fun extractTags(text: String): List<String> {
        return Regex("""#(\w+)""").findAll(text).map { it.groupValues[1] }.toList()
    }
}
