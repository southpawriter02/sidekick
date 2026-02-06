package com.sidekick.agent.tools

import java.io.File

/**
 * # Agent Tools
 *
 * Built-in tools for agent task execution.
 * Part of Sidekick v0.8.3 Agent Task System feature.
 *
 * ## Overview
 *
 * Tools allow the agent to:
 * - Read and write files
 * - Search code
 * - Run commands
 * - Analyze symbols
 *
 * @since 0.8.3
 */

// =============================================================================
// Tool Definition
// =============================================================================

/**
 * Tool the agent can invoke.
 *
 * @property name Tool name (function name)
 * @property description What the tool does
 * @property parameters Parameter schema
 * @property category Tool category
 * @property isDestructive Whether tool can modify state
 * @property handler Execution handler
 */
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
    val category: ToolCategory = ToolCategory.OTHER,
    val isDestructive: Boolean = false,
    val handler: suspend (Map<String, Any>) -> ToolResult
) {
    /**
     * Converts to unified provider tool format.
     */
    fun toProviderTool(): com.sidekick.llm.provider.AgentTool {
        return com.sidekick.llm.provider.AgentTool(
            name = name,
            description = description,
            parameters = com.sidekick.llm.provider.ToolParameters(
                type = parameters.type,
                properties = parameters.properties.mapValues { (_, schema) ->
                    com.sidekick.llm.provider.ParameterSchema(
                        type = schema.type,
                        description = schema.description,
                        enum = schema.enum
                    )
                },
                required = parameters.required
            )
        )
    }
}

/**
 * Tool categories.
 */
enum class ToolCategory(val displayName: String) {
    FILE_SYSTEM("File System"),
    CODE_ANALYSIS("Code Analysis"),
    EXECUTION("Execution"),
    PROJECT("Project"),
    OTHER("Other");

    override fun toString(): String = displayName
}

// =============================================================================
// Tool Parameters
// =============================================================================

/**
 * Tool parameter schema (JSON Schema subset).
 */
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ParameterSchema>,
    val required: List<String> = emptyList()
) {
    companion object {
        /**
         * Creates simple parameters with string properties.
         */
        fun simple(vararg params: Pair<String, String>): ToolParameters {
            return ToolParameters(
                properties = params.associate { (name, desc) ->
                    name to ParameterSchema("string", desc)
                },
                required = params.map { it.first }
            )
        }
    }
}

/**
 * Individual parameter schema.
 */
data class ParameterSchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val default: Any? = null
) {
    companion object {
        fun string(description: String) = ParameterSchema("string", description)
        fun integer(description: String) = ParameterSchema("integer", description)
        fun boolean(description: String) = ParameterSchema("boolean", description)
        fun array(description: String) = ParameterSchema("array", description)
    }
}

// =============================================================================
// Tool Result
// =============================================================================

/**
 * Result from tool execution.
 *
 * @property success Whether the tool succeeded
 * @property output Tool output
 * @property error Error message if failed
 * @property metadata Additional result data
 */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(output: String, metadata: Map<String, Any> = emptyMap()) = ToolResult(
            success = true,
            output = output,
            metadata = metadata
        )

        fun failure(error: String) = ToolResult(
            success = false,
            output = "",
            error = error
        )
    }
}

/**
 * Tool call request from the model.
 */
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>
) {
    /**
     * Gets a string argument.
     */
    fun getString(key: String): String? = arguments[key] as? String

    /**
     * Gets a string argument with default.
     */
    fun getString(key: String, default: String): String = getString(key) ?: default

    /**
     * Gets an integer argument.
     */
    fun getInt(key: String): Int? = (arguments[key] as? Number)?.toInt()

    /**
     * Gets a boolean argument.
     */
    fun getBoolean(key: String): Boolean? = arguments[key] as? Boolean

    /**
     * Gets a list argument.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getList(key: String): List<T>? = arguments[key] as? List<T>
}

// =============================================================================
// Built-in Tools
// =============================================================================

/**
 * Built-in agent tools.
 */
object BuiltInTools {

    // -------------------------------------------------------------------------
    // File System Tools
    // -------------------------------------------------------------------------

    val READ_FILE = AgentTool(
        name = "read_file",
        description = "Read the contents of a file",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema.string("Absolute path to the file"),
                "startLine" to ParameterSchema.integer("Starting line number (1-indexed, optional)"),
                "endLine" to ParameterSchema.integer("Ending line number (1-indexed, optional)")
            ),
            required = listOf("path")
        ),
        category = ToolCategory.FILE_SYSTEM,
        isDestructive = false,
        handler = { args -> readFileHandler(args) }
    )

    val WRITE_FILE = AgentTool(
        name = "write_file",
        description = "Write content to a file (creates or overwrites)",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema.string("Absolute path"),
                "content" to ParameterSchema.string("File content")
            ),
            required = listOf("path", "content")
        ),
        category = ToolCategory.FILE_SYSTEM,
        isDestructive = true,
        handler = { args -> writeFileHandler(args) }
    )

    val EDIT_FILE = AgentTool(
        name = "edit_file",
        description = "Make targeted edits to a file by replacing text",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema.string("Absolute path"),
                "oldText" to ParameterSchema.string("Text to find and replace"),
                "newText" to ParameterSchema.string("Replacement text")
            ),
            required = listOf("path", "oldText", "newText")
        ),
        category = ToolCategory.FILE_SYSTEM,
        isDestructive = true,
        handler = { args -> editFileHandler(args) }
    )

    val LIST_FILES = AgentTool(
        name = "list_files",
        description = "List files in a directory",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema.string("Directory path"),
                "recursive" to ParameterSchema.boolean("Include subdirectories (default: false)"),
                "pattern" to ParameterSchema.string("File glob pattern filter (optional)")
            ),
            required = listOf("path")
        ),
        category = ToolCategory.FILE_SYSTEM,
        isDestructive = false,
        handler = { args -> listFilesHandler(args) }
    )

    // -------------------------------------------------------------------------
    // Code Analysis Tools
    // -------------------------------------------------------------------------

    val SEARCH_CODE = AgentTool(
        name = "search_code",
        description = "Search for code patterns in the project",
        parameters = ToolParameters(
            properties = mapOf(
                "query" to ParameterSchema.string("Search query or regex pattern"),
                "filePattern" to ParameterSchema.string("File glob pattern (e.g., *.kt)"),
                "caseSensitive" to ParameterSchema.boolean("Case sensitive search (default: false)")
            ),
            required = listOf("query")
        ),
        category = ToolCategory.CODE_ANALYSIS,
        isDestructive = false,
        handler = { args -> searchCodeHandler(args) }
    )

    val GET_SYMBOL_INFO = AgentTool(
        name = "get_symbol_info",
        description = "Get information about a code symbol (class, function, etc.)",
        parameters = ToolParameters(
            properties = mapOf(
                "symbol" to ParameterSchema.string("Symbol name (class, function, variable)"),
                "file" to ParameterSchema.string("File path to search in (optional)")
            ),
            required = listOf("symbol")
        ),
        category = ToolCategory.CODE_ANALYSIS,
        isDestructive = false,
        handler = { args -> getSymbolInfoHandler(args) }
    )

    val GET_FILE_OUTLINE = AgentTool(
        name = "get_file_outline",
        description = "Get the structure/outline of a file (classes, functions, etc.)",
        parameters = ToolParameters(
            properties = mapOf(
                "path" to ParameterSchema.string("File path")
            ),
            required = listOf("path")
        ),
        category = ToolCategory.CODE_ANALYSIS,
        isDestructive = false,
        handler = { args -> getFileOutlineHandler(args) }
    )

    // -------------------------------------------------------------------------
    // Execution Tools
    // -------------------------------------------------------------------------

    val RUN_COMMAND = AgentTool(
        name = "run_command",
        description = "Run a shell command",
        parameters = ToolParameters(
            properties = mapOf(
                "command" to ParameterSchema.string("Command to execute"),
                "cwd" to ParameterSchema.string("Working directory (optional)"),
                "timeout" to ParameterSchema.integer("Timeout in seconds (default: 30)")
            ),
            required = listOf("command")
        ),
        category = ToolCategory.EXECUTION,
        isDestructive = true,
        handler = { args -> runCommandHandler(args) }
    )

    // -------------------------------------------------------------------------
    // All Tools
    // -------------------------------------------------------------------------

    val ALL = listOf(
        READ_FILE,
        WRITE_FILE,
        EDIT_FILE,
        LIST_FILES,
        SEARCH_CODE,
        GET_SYMBOL_INFO,
        GET_FILE_OUTLINE,
        RUN_COMMAND
    )

    /**
     * Tools by category.
     */
    val BY_CATEGORY: Map<ToolCategory, List<AgentTool>> = ALL.groupBy { it.category }

    /**
     * Safe (non-destructive) tools.
     */
    val SAFE_TOOLS = ALL.filter { !it.isDestructive }

    /**
     * Destructive tools requiring confirmation.
     */
    val DESTRUCTIVE_TOOLS = ALL.filter { it.isDestructive }

    /**
     * Finds a tool by name.
     */
    fun findByName(name: String): AgentTool? = ALL.find { it.name == name }

    // -------------------------------------------------------------------------
    // Tool Handlers
    // -------------------------------------------------------------------------

    private suspend fun readFileHandler(args: Map<String, Any>): ToolResult {
        val path = args["path"] as? String ?: return ToolResult.failure("Missing path")
        val file = File(path)

        if (!file.exists()) {
            return ToolResult.failure("File not found: $path")
        }

        if (!file.isFile) {
            return ToolResult.failure("Not a file: $path")
        }

        return try {
            val lines = file.readLines()
            val startLine = (args["startLine"] as? Number)?.toInt()?.minus(1)?.coerceAtLeast(0) ?: 0
            val endLine = (args["endLine"] as? Number)?.toInt()?.coerceAtMost(lines.size) ?: lines.size

            val content = lines.subList(startLine, endLine).joinToString("\n")
            ToolResult.success(content, mapOf("lines" to lines.size))
        } catch (e: Exception) {
            ToolResult.failure("Error reading file: ${e.message}")
        }
    }

    private suspend fun writeFileHandler(args: Map<String, Any>): ToolResult {
        val path = args["path"] as? String ?: return ToolResult.failure("Missing path")
        val content = args["content"] as? String ?: return ToolResult.failure("Missing content")
        val file = File(path)

        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult.success("File written: $path", mapOf("bytes" to content.length))
        } catch (e: Exception) {
            ToolResult.failure("Error writing file: ${e.message}")
        }
    }

    private suspend fun editFileHandler(args: Map<String, Any>): ToolResult {
        val path = args["path"] as? String ?: return ToolResult.failure("Missing path")
        val oldText = args["oldText"] as? String ?: return ToolResult.failure("Missing oldText")
        val newText = args["newText"] as? String ?: return ToolResult.failure("Missing newText")
        val file = File(path)

        if (!file.exists()) {
            return ToolResult.failure("File not found: $path")
        }

        return try {
            val content = file.readText()
            if (!content.contains(oldText)) {
                return ToolResult.failure("Text not found in file")
            }

            val newContent = content.replace(oldText, newText)
            file.writeText(newContent)
            ToolResult.success("File edited: $path", mapOf("replacements" to 1))
        } catch (e: Exception) {
            ToolResult.failure("Error editing file: ${e.message}")
        }
    }

    private suspend fun listFilesHandler(args: Map<String, Any>): ToolResult {
        val path = args["path"] as? String ?: return ToolResult.failure("Missing path")
        val recursive = args["recursive"] as? Boolean ?: false
        val pattern = args["pattern"] as? String
        val dir = File(path)

        if (!dir.exists()) {
            return ToolResult.failure("Directory not found: $path")
        }

        if (!dir.isDirectory) {
            return ToolResult.failure("Not a directory: $path")
        }

        return try {
            val files = if (recursive) {
                dir.walkTopDown().toList()
            } else {
                dir.listFiles()?.toList() ?: emptyList()
            }

            val filtered = if (pattern != null) {
                val regex = Regex(pattern.replace("*", ".*").replace("?", "."))
                files.filter { regex.matches(it.name) }
            } else {
                files
            }

            val listing = filtered.take(100).joinToString("\n") { f ->
                val type = if (f.isDirectory) "[DIR]" else "[FILE]"
                "$type ${f.absolutePath}"
            }

            ToolResult.success(listing, mapOf("count" to filtered.size))
        } catch (e: Exception) {
            ToolResult.failure("Error listing files: ${e.message}")
        }
    }

    private suspend fun searchCodeHandler(args: Map<String, Any>): ToolResult {
        val query = args["query"] as? String ?: return ToolResult.failure("Missing query")
        val filePattern = args["filePattern"] as? String ?: "*"
        val caseSensitive = args["caseSensitive"] as? Boolean ?: false

        // Simplified implementation - in production would use proper grep/ripgrep
        return try {
            val regex = if (caseSensitive) {
                Regex(query)
            } else {
                Regex(query, RegexOption.IGNORE_CASE)
            }

            ToolResult.success(
                "Search for '$query' (pattern: $filePattern):\n[Would search project files]",
                mapOf("query" to query)
            )
        } catch (e: Exception) {
            ToolResult.failure("Error searching: ${e.message}")
        }
    }

    private suspend fun getSymbolInfoHandler(args: Map<String, Any>): ToolResult {
        val symbol = args["symbol"] as? String ?: return ToolResult.failure("Missing symbol")
        val file = args["file"] as? String

        // Simplified implementation - in production would use PSI
        return ToolResult.success(
            "Symbol: $symbol\n[Would use PSI to find definition and usages]",
            mapOf("symbol" to symbol)
        )
    }

    private suspend fun getFileOutlineHandler(args: Map<String, Any>): ToolResult {
        val path = args["path"] as? String ?: return ToolResult.failure("Missing path")
        val file = File(path)

        if (!file.exists()) {
            return ToolResult.failure("File not found: $path")
        }

        // Simplified implementation - in production would use PSI
        return ToolResult.success(
            "Outline for: $path\n[Would use PSI to extract structure]",
            mapOf("path" to path)
        )
    }

    private suspend fun runCommandHandler(args: Map<String, Any>): ToolResult {
        val command = args["command"] as? String ?: return ToolResult.failure("Missing command")
        val cwd = args["cwd"] as? String
        val timeout = (args["timeout"] as? Number)?.toInt() ?: 30

        return try {
            val workDir = cwd?.let { File(it) }

            val process = ProcessBuilder("sh", "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                ToolResult.success(output, mapOf("exitCode" to exitCode))
            } else {
                ToolResult.failure("Command failed with exit code $exitCode:\n$output")
            }
        } catch (e: Exception) {
            ToolResult.failure("Error running command: ${e.message}")
        }
    }
}
