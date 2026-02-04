// =============================================================================
// ProjectContext.kt
// =============================================================================
// Data class representing the project structure and type.
//
// This captures:
// - Project name and base path
// - Project type (dotnet, gradle, npm, etc.)
// - Framework hints (ASP.NET, Unity, etc.)
// - Key project files
//
// DESIGN NOTES:
// - Cached at project level for performance
// - Helps LLM understand the project context
// - Used for better code suggestions and answers
// =============================================================================

package com.sidekick.context

/**
 * Summary of the project structure for context injection.
 *
 * Provides high-level information about the project that helps
 * the LLM understand the codebase and provide relevant suggestions.
 *
 * @property name Project name
 * @property basePath Project root directory path
 * @property projectType Type of project (dotnet, gradle, npm, etc.)
 * @property frameworkHints Detected frameworks (ASP.NET, Unity, WPF, etc.)
 * @property keyFiles Important project files (.csproj, package.json, etc.)
 */
data class ProjectContext(
    val name: String,
    val basePath: String,
    val projectType: ProjectType,
    val frameworkHints: List<String>,
    val keyFiles: List<String>
) {
    companion object {
        /**
         * Empty context for when project info is unavailable.
         */
        val EMPTY = ProjectContext(
            name = "",
            basePath = "",
            projectType = ProjectType.UNKNOWN,
            frameworkHints = emptyList(),
            keyFiles = emptyList()
        )
    }
    
    // -------------------------------------------------------------------------
    // Computed Properties
    // -------------------------------------------------------------------------
    
    /**
     * Whether this represents a valid project.
     */
    val isValid: Boolean
        get() = name.isNotEmpty() && basePath.isNotEmpty()
    
    /**
     * Whether project type was detected.
     */
    val hasKnownType: Boolean
        get() = projectType != ProjectType.UNKNOWN
    
    /**
     * Gets the primary language for this project type.
     */
    val primaryLanguage: String
        get() = when (projectType) {
            ProjectType.DOTNET -> "C#"
            ProjectType.GRADLE -> "Kotlin/Java"
            ProjectType.NPM -> "TypeScript/JavaScript"
            ProjectType.PYTHON -> "Python"
            ProjectType.UNITY -> "C#"
            ProjectType.RUST -> "Rust"
            ProjectType.GO -> "Go"
            ProjectType.UNKNOWN -> "Unknown"
        }
    
    /**
     * Gets a compact summary for inclusion in prompts.
     */
    fun toPromptSummary(): String = buildString {
        append("Project: $name")
        if (hasKnownType) {
            append(" ($primaryLanguage, ${projectType.displayName})")
        }
        if (frameworkHints.isNotEmpty()) {
            append("\nFrameworks: ${frameworkHints.joinToString(", ")}")
        }
    }
    
    /**
     * Gets a compact summary for logging/display.
     */
    fun toSummary(): String = buildString {
        append("ProjectContext(")
        append("name=$name")
        append(", type=${projectType.name}")
        if (frameworkHints.isNotEmpty()) {
            append(", frameworks=${frameworkHints.take(3)}")
        }
        append(")")
    }
}

/**
 * Types of projects that Sidekick can detect.
 */
enum class ProjectType(val displayName: String) {
    DOTNET(".NET"),
    GRADLE("Gradle"),
    NPM("Node.js"),
    PYTHON("Python"),
    UNITY("Unity"),
    RUST("Rust"),
    GO("Go"),
    UNKNOWN("Unknown");
    
    companion object {
        /**
         * File patterns that indicate each project type.
         */
        val FILE_INDICATORS: Map<ProjectType, Set<String>> = mapOf(
            DOTNET to setOf(".csproj", ".sln", ".fsproj", ".vbproj"),
            GRADLE to setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"),
            NPM to setOf("package.json"),
            PYTHON to setOf("pyproject.toml", "setup.py", "requirements.txt", "Pipfile"),
            UNITY to setOf("ProjectSettings", "Assets"),
            RUST to setOf("Cargo.toml"),
            GO to setOf("go.mod", "go.sum")
        )
        
        /**
         * Directories to skip during project analysis.
         */
        val SKIP_DIRECTORIES = setOf(
            "node_modules", ".git", "bin", "obj", "build", ".gradle",
            "__pycache__", ".idea", ".vs", "target", "dist", ".next"
        )
    }
}
