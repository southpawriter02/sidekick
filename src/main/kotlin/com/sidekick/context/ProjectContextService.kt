// =============================================================================
// ProjectContextService.kt
// =============================================================================
// Project-level service for analyzing and summarizing project structure.
//
// This service:
// - Detects project type (dotnet, gradle, npm, etc.)
// - Identifies frameworks and key files
// - Provides project summary for AI context
//
// DESIGN NOTES:
// - Project-level service (one per project)
// - Caches analysis results for performance
// - Scans project on first access or manual refresh
// - Skips common non-essential directories
// =============================================================================

package com.sidekick.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Service for analyzing and summarizing project structure.
 *
 * Provides project-level context including type detection,
 * framework identification, and key file discovery.
 *
 * ## Usage
 *
 * ```kotlin
 * val service = ProjectContextService.getInstance(project)
 * val context = service.getProjectContext()
 * println("Project type: ${context.projectType}")
 * ```
 */
@Service(Service.Level.PROJECT)
class ProjectContextService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(ProjectContextService::class.java)
        
        /**
         * Maximum number of key files to collect.
         */
        private const val MAX_KEY_FILES = 15
        
        /**
         * Maximum depth to scan for project files.
         */
        private const val MAX_SCAN_DEPTH = 5
        
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): ProjectContextService {
            return project.getService(ProjectContextService::class.java)
        }
    }
    
    // -------------------------------------------------------------------------
    // Cached State
    // -------------------------------------------------------------------------
    
    /**
     * Cached project context (thread-safe).
     */
    private val cachedContext = AtomicReference<ProjectContext?>(null)

    // -------------------------------------------------------------------------
    // Public Methods
    // -------------------------------------------------------------------------
    
    /**
     * Gets the project context, analyzing if not cached.
     *
     * @return ProjectContext with type, frameworks, and key files
     */
    fun getProjectContext(): ProjectContext {
        return cachedContext.get() ?: analyzeProject().also { cachedContext.set(it) }
    }
    
    /**
     * Forces a refresh of the project context.
     *
     * @return Newly analyzed ProjectContext
     */
    fun refreshContext(): ProjectContext {
        cachedContext.set(null)
        return getProjectContext()
    }
    
    /**
     * Gets a summary string suitable for including in LLM prompts.
     *
     * @return Human-readable project summary
     */
    fun getProjectSummary(): String {
        return getProjectContext().toPromptSummary()
    }
    
    /**
     * Checks if the project is of a specific type.
     *
     * @param type The project type to check
     * @return true if project matches the type
     */
    fun isProjectType(type: ProjectType): Boolean {
        return getProjectContext().projectType == type
    }

    // -------------------------------------------------------------------------
    // Private Methods - Analysis
    // -------------------------------------------------------------------------
    
    /**
     * Analyzes the project structure to determine type and key files.
     */
    private fun analyzeProject(): ProjectContext {
        LOG.info("Analyzing project structure: ${project.name}")
        
        val basePath = project.basePath ?: return ProjectContext.EMPTY
        
        return ReadAction.compute<ProjectContext, RuntimeException> {
            try {
                performAnalysis(basePath)
            } catch (e: Exception) {
                LOG.warn("Failed to analyze project: ${e.message}")
                ProjectContext.EMPTY
            }
        }
    }
    
    /**
     * Performs the actual project analysis.
     */
    private fun performAnalysis(basePath: String): ProjectContext {
        val baseDir = VfsUtil.findFileByIoFile(File(basePath), true)
            ?: return ProjectContext.EMPTY
        
        val keyFiles = mutableListOf<String>()
        val frameworkHints = mutableSetOf<String>()
        var projectType = ProjectType.UNKNOWN
        
        // Recursive scan with depth limit
        scanDirectory(baseDir, 0, keyFiles, frameworkHints) { type ->
            if (projectType == ProjectType.UNKNOWN || type.ordinal < projectType.ordinal) {
                projectType = type
            }
        }
        
        val result = ProjectContext(
            name = project.name,
            basePath = basePath,
            projectType = projectType,
            frameworkHints = frameworkHints.toList(),
            keyFiles = keyFiles.take(MAX_KEY_FILES)
        )
        
        LOG.info("Project analysis complete: ${result.toSummary()}")
        return result
    }
    
    /**
     * Recursively scans a directory for project indicators.
     */
    private fun scanDirectory(
        dir: VirtualFile,
        depth: Int,
        keyFiles: MutableList<String>,
        frameworkHints: MutableSet<String>,
        onTypeDetected: (ProjectType) -> Unit
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        if (keyFiles.size >= MAX_KEY_FILES) return
        
        for (child in dir.children) {
            if (child.isDirectory) {
                // Skip non-essential directories
                if (child.name in ProjectType.SKIP_DIRECTORIES) continue
                if (child.name.startsWith(".")) continue
                
                // Check for Unity project structure
                if (child.name == "Assets" && depth == 0) {
                    val hasProjectSettings = dir.findChild("ProjectSettings") != null
                    if (hasProjectSettings) {
                        onTypeDetected(ProjectType.UNITY)
                        frameworkHints.add("Unity")
                    }
                }
                
                // Recurse into subdirectory
                scanDirectory(child, depth + 1, keyFiles, frameworkHints, onTypeDetected)
            } else {
                // Check file against project type indicators
                analyzeFile(child, keyFiles, frameworkHints, onTypeDetected)
            }
        }
    }
    
    /**
     * Analyzes a single file for project type indicators.
     */
    private fun analyzeFile(
        file: VirtualFile,
        keyFiles: MutableList<String>,
        frameworkHints: MutableSet<String>,
        onTypeDetected: (ProjectType) -> Unit
    ) {
        val name = file.name
        val extension = ".${file.extension ?: ""}"
        
        // Check each project type's indicators
        for ((type, indicators) in ProjectType.FILE_INDICATORS) {
            val matches = indicators.any { pattern ->
                if (pattern.startsWith(".")) {
                    extension.equals(pattern, ignoreCase = true)
                } else {
                    name.equals(pattern, ignoreCase = true)
                }
            }
            
            if (matches) {
                onTypeDetected(type)
                keyFiles.add(file.path)
                
                // Extract framework hints from specific files
                extractFrameworkHints(file, type, frameworkHints)
                break
            }
        }
    }
    
    /**
     * Extracts framework hints from project files.
     */
    private fun extractFrameworkHints(
        file: VirtualFile,
        type: ProjectType,
        hints: MutableSet<String>
    ) {
        try {
            // Only read small files for hints
            if (file.length > 100_000) return
            
            val content = String(file.contentsToByteArray())
            
            when (type) {
                ProjectType.DOTNET -> extractDotNetHints(content, hints)
                ProjectType.NPM -> extractNpmHints(content, hints)
                ProjectType.GRADLE -> extractGradleHints(content, hints)
                ProjectType.PYTHON -> extractPythonHints(content, hints)
                else -> {}
            }
        } catch (e: Exception) {
            LOG.debug("Failed to extract hints from ${file.name}: ${e.message}")
        }
    }
    
    /**
     * Extracts hints from .NET project files.
     */
    private fun extractDotNetHints(content: String, hints: MutableSet<String>) {
        if ("Microsoft.NET.Sdk.Web" in content) hints.add("ASP.NET Core")
        if ("Microsoft.NET.Sdk.BlazorWebAssembly" in content) hints.add("Blazor")
        if ("Microsoft.NET.Sdk.Razor" in content) hints.add("Razor")
        if ("Xamarin" in content) hints.add("Xamarin")
        if ("WPF" in content || "WindowsBase" in content) hints.add("WPF")
        if ("WindowsForms" in content) hints.add("Windows Forms")
        if ("MAUI" in content) hints.add(".NET MAUI")
        if ("NUnit" in content) hints.add("NUnit")
        if ("xunit" in content.lowercase()) hints.add("xUnit")
        if ("MSTest" in content) hints.add("MSTest")
        if ("EntityFramework" in content) hints.add("Entity Framework")
    }
    
    /**
     * Extracts hints from package.json files.
     */
    private fun extractNpmHints(content: String, hints: MutableSet<String>) {
        if ("\"react\"" in content) hints.add("React")
        if ("\"vue\"" in content) hints.add("Vue")
        if ("\"@angular" in content) hints.add("Angular")
        if ("\"next\"" in content) hints.add("Next.js")
        if ("\"svelte\"" in content) hints.add("Svelte")
        if ("\"express\"" in content) hints.add("Express")
        if ("\"typescript\"" in content) hints.add("TypeScript")
        if ("\"jest\"" in content) hints.add("Jest")
        if ("\"vitest\"" in content) hints.add("Vitest")
        if ("\"electron\"" in content) hints.add("Electron")
        if ("\"tailwindcss\"" in content) hints.add("Tailwind CSS")
    }
    
    /**
     * Extracts hints from Gradle build files.
     */
    private fun extractGradleHints(content: String, hints: MutableSet<String>) {
        if ("org.springframework" in content) hints.add("Spring")
        if ("org.jetbrains.kotlin" in content) hints.add("Kotlin")
        if ("android" in content.lowercase()) hints.add("Android")
        if ("intellij" in content.lowercase()) hints.add("IntelliJ Plugin")
        if ("ktor" in content.lowercase()) hints.add("Ktor")
        if ("junit" in content.lowercase()) hints.add("JUnit")
        if ("kotest" in content.lowercase()) hints.add("Kotest")
    }
    
    /**
     * Extracts hints from Python project files.
     */
    private fun extractPythonHints(content: String, hints: MutableSet<String>) {
        if ("django" in content.lowercase()) hints.add("Django")
        if ("flask" in content.lowercase()) hints.add("Flask")
        if ("fastapi" in content.lowercase()) hints.add("FastAPI")
        if ("pytest" in content.lowercase()) hints.add("pytest")
        if ("tensorflow" in content.lowercase()) hints.add("TensorFlow")
        if ("pytorch" in content.lowercase() || "torch" in content.lowercase()) hints.add("PyTorch")
        if ("pandas" in content.lowercase()) hints.add("pandas")
        if ("numpy" in content.lowercase()) hints.add("NumPy")
    }
}
