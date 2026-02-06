// =============================================================================
// MarketplaceAssets.kt
// =============================================================================
// Assets for JetBrains Marketplace submission and documentation.
//
// This file contains all data contracts for marketplace and docs:
// - MarketplaceMetadata: Plugin listing metadata
// - DocumentationSite: Documentation structure
// - ScreenshotAsset: Marketing screenshots
// - MarketplaceSubmission: Submission checklist
//
// @since v1.0.6
// =============================================================================

package com.sidekick.marketplace

// =============================================================================
// Marketplace Metadata
// =============================================================================

/**
 * Plugin marketplace metadata for JetBrains Marketplace listing.
 *
 * This data class contains all information needed for the plugin.xml
 * and marketplace submission.
 *
 * @property pluginId Unique plugin identifier
 * @property name Plugin display name
 * @property vendor Vendor/publisher name
 * @property version Current version string
 * @property sinceBuild Minimum supported IDE build
 * @property untilBuild Maximum supported IDE build
 * @property description HTML description for listing
 * @property changeNotes HTML release notes
 * @property tags Search tags for discovery
 * @property category Plugin category
 */
data class MarketplaceMetadata(
    val pluginId: String = DEFAULT_PLUGIN_ID,
    val name: String = DEFAULT_NAME,
    val vendor: String = DEFAULT_VENDOR,
    val version: String = "1.0.0",
    val sinceBuild: String = "241.0",
    val untilBuild: String = "243.*",
    val description: String = PLUGIN_DESCRIPTION,
    val changeNotes: String = CHANGE_NOTES,
    val tags: List<String> = DEFAULT_TAGS,
    val category: String = DEFAULT_CATEGORY
) {
    /**
     * Validates the metadata.
     * @return List of validation errors (empty if valid)
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (pluginId.isBlank()) errors.add("Plugin ID is required")
        if (name.isBlank()) errors.add("Name is required")
        if (version.isBlank()) errors.add("Version is required")
        if (!version.matches(Regex("""\d+\.\d+\.\d+"""))) {
            errors.add("Version must be semantic (x.y.z)")
        }
        if (description.length < 100) errors.add("Description too short (min 100 chars)")
        return errors
    }

    /**
     * Generates plugin.xml content.
     */
    fun toPluginXml(): String = """
        |<idea-plugin>
        |    <id>$pluginId</id>
        |    <name>$name</name>
        |    <vendor>$vendor</vendor>
        |    <version>$version</version>
        |    <idea-version since-build="$sinceBuild" until-build="$untilBuild"/>
        |    <description><![CDATA[$description]]></description>
        |    <change-notes><![CDATA[$changeNotes]]></change-notes>
        |</idea-plugin>
    """.trimMargin()

    /**
     * Gets formatted tag string.
     */
    fun getTagString(): String = tags.joinToString(", ")

    /**
     * Gets the marketplace URL.
     */
    fun getMarketplaceUrl(): String = "https://plugins.jetbrains.com/plugin/$pluginId"

    companion object {
        const val DEFAULT_PLUGIN_ID = "com.sidekick"
        const val DEFAULT_NAME = "Sidekick - Local AI Coding Assistant"
        const val DEFAULT_VENDOR = "Sidekick"
        const val DEFAULT_CATEGORY = "Code tools"

        val DEFAULT_TAGS = listOf(
            "AI", "LLM", "Code Assistant", "Ollama", "Local AI",
            "Code Generation", "Refactoring", "Chat"
        )

        const val PLUGIN_DESCRIPTION = """
            <p>Sidekick brings the power of local LLMs to your IDE. Chat with AI, generate code, 
            refactor, and boost your productivity—all while keeping your code private.</p>
            
            <h3>Features</h3>
            <ul>
                <li>🤖 Local LLM integration (Ollama, LM Studio)</li>
                <li>💬 Context-aware chat with code understanding</li>
                <li>✨ AI-powered code generation and refactoring</li>
                <li>📝 Documentation generation</li>
                <li>🔍 Smart code search and navigation</li>
                <li>🎮 Gamification with stats and achievements</li>
                <li>🔒 100% private—your code never leaves your machine</li>
            </ul>
            
            <h3>Getting Started</h3>
            <ol>
                <li>Install Ollama or LM Studio</li>
                <li>Download a model (e.g., codellama, llama2)</li>
                <li>Open the Sidekick panel and start chatting!</li>
            </ol>
        """

        const val CHANGE_NOTES = """
            <h3>1.0.0 - Initial Release</h3>
            <ul>
                <li>Full LM Studio and Ollama support</li>
                <li>Agentic coding capabilities</li>
                <li>Multi-provider support (OpenAI, Anthropic, Azure)</li>
                <li>Extensibility API for custom tools</li>
                <li>Security hardening with command sandboxing</li>
                <li>Opt-in telemetry</li>
            </ul>
        """

        /**
         * Creates metadata for a specific version.
         */
        fun forVersion(version: String, changeNotes: String) = MarketplaceMetadata(
            version = version,
            changeNotes = changeNotes
        )
    }
}

// =============================================================================
// Documentation Structure
// =============================================================================

/**
 * Documentation site structure.
 *
 * @property baseUrl Documentation site URL
 * @property sections List of documentation sections
 */
data class DocumentationSite(
    val baseUrl: String = DEFAULT_BASE_URL,
    val sections: List<DocSection> = DEFAULT_SECTIONS
) {
    /**
     * Gets all pages across all sections.
     */
    fun getAllPages(): List<DocPage> = sections.flatMap { it.pages }

    /**
     * Gets total page count.
     */
    fun getPageCount(): Int = getAllPages().size

    /**
     * Finds a page by ID.
     */
    fun findPage(pageId: String): DocPage? = getAllPages().find { it.id == pageId }

    /**
     * Finds the section containing a page.
     */
    fun findSectionForPage(pageId: String): DocSection? =
        sections.find { section -> section.pages.any { it.id == pageId } }

    /**
     * Gets URL for a page.
     */
    fun getPageUrl(sectionId: String, pageId: String): String =
        "$baseUrl/$sectionId/$pageId"

    /**
     * Generates a table of contents.
     */
    fun generateToc(): String = buildString {
        sections.forEach { section ->
            appendLine("## ${section.title}")
            section.pages.forEach { page ->
                appendLine("- [${page.title}](${getPageUrl(section.id, page.id)})")
            }
            appendLine()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://sidekick.dev/docs"

        val DEFAULT_SECTIONS = listOf(
            DocSection(
                id = "getting-started",
                title = "Getting Started",
                description = "Installation and setup guides",
                pages = listOf(
                    DocPage("installation", "Installation", "How to install Sidekick"),
                    DocPage("configuration", "Configuration", "Configure providers and preferences"),
                    DocPage("first-chat", "Your First Chat", "Start chatting with AI")
                )
            ),
            DocSection(
                id = "features",
                title = "Features",
                description = "Core plugin features",
                pages = listOf(
                    DocPage("chat", "AI Chat", "Context-aware conversations"),
                    DocPage("code-actions", "Code Actions", "Generate, refactor, and explain code"),
                    DocPage("agent", "Coding Agent", "Autonomous coding assistant")
                )
            ),
            DocSection(
                id = "providers",
                title = "Providers",
                description = "LLM provider setup",
                pages = listOf(
                    DocPage("ollama", "Ollama", "Local Ollama setup"),
                    DocPage("lm-studio", "LM Studio", "LM Studio configuration"),
                    DocPage("cloud", "Cloud Providers", "OpenAI, Anthropic, Azure")
                )
            ),
            DocSection(
                id = "api",
                title = "API Reference",
                description = "Extension development",
                pages = listOf(
                    DocPage("extensions", "Creating Extensions", "Build custom extensions"),
                    DocPage("tools", "Custom Tools", "Add agent tools"),
                    DocPage("templates", "Prompt Templates", "Create reusable prompts")
                )
            )
        )
    }
}

/**
 * A documentation section containing related pages.
 *
 * @property id Section identifier (URL-safe)
 * @property title Human-readable title
 * @property description Brief description
 * @property pages Pages in this section
 */
data class DocSection(
    val id: String,
    val title: String,
    val description: String = "",
    val pages: List<DocPage> = emptyList()
) {
    /**
     * Gets page count.
     */
    val pageCount: Int get() = pages.size

    /**
     * Formats for navigation.
     */
    fun format(): String = "$title ($pageCount pages)"
}

/**
 * A single documentation page.
 *
 * @property id Page identifier (URL-safe)
 * @property title Human-readable title
 * @property description Brief description
 */
data class DocPage(
    val id: String,
    val title: String,
    val description: String = ""
) {
    /**
     * Generates a markdown link.
     */
    fun toMarkdownLink(baseUrl: String, sectionId: String): String =
        "[$title]($baseUrl/$sectionId/$id)"
}

// =============================================================================
// Marketing Assets
// =============================================================================

/**
 * Screenshot asset for marketplace listing.
 *
 * @property id Screenshot identifier
 * @property title Screenshot caption
 * @property description Alt text / description
 * @property filename Image filename
 * @property width Image width
 * @property height Image height
 */
data class ScreenshotAsset(
    val id: String,
    val title: String,
    val description: String,
    val filename: String,
    val width: Int = 1280,
    val height: Int = 800
) {
    /**
     * Gets the aspect ratio.
     */
    val aspectRatio: Float get() = width.toFloat() / height

    /**
     * Validates dimensions for marketplace.
     */
    fun validateDimensions(): Boolean = width >= 1280 && height >= 800

    companion object {
        /**
         * Recommended screenshots for submission.
         */
        val RECOMMENDED = listOf(
            ScreenshotAsset(
                id = "chat",
                title = "AI Chat Panel",
                description = "Context-aware chat with code understanding",
                filename = "screenshot-chat.png"
            ),
            ScreenshotAsset(
                id = "agent",
                title = "Coding Agent",
                description = "Autonomous code generation and refactoring",
                filename = "screenshot-agent.png"
            ),
            ScreenshotAsset(
                id = "providers",
                title = "Provider Configuration",
                description = "Configure local and cloud providers",
                filename = "screenshot-providers.png"
            ),
            ScreenshotAsset(
                id = "gamification",
                title = "Stats & Achievements",
                description = "Track your coding stats and unlock achievements",
                filename = "screenshot-gamification.png"
            )
        )
    }
}

// =============================================================================
// Submission Checklist
// =============================================================================

/**
 * Marketplace submission checklist.
 *
 * @property items Checklist items with completion status
 */
data class MarketplaceSubmission(
    val items: Map<String, Boolean> = DEFAULT_ITEMS
) {
    /**
     * Checks if all items are complete.
     */
    val isComplete: Boolean get() = items.values.all { it }

    /**
     * Gets completion percentage.
     */
    val completionPercent: Int get() = 
        if (items.isEmpty()) 0 else (items.values.count { it } * 100 / items.size)

    /**
     * Gets incomplete items.
     */
    fun getIncompleteItems(): List<String> = items.filter { !it.value }.keys.toList()

    /**
     * Marks an item as complete.
     */
    fun complete(item: String): MarketplaceSubmission =
        copy(items = items + (item to true))

    /**
     * Formats as a checklist.
     */
    fun format(): String = buildString {
        appendLine("## Marketplace Submission Checklist (${completionPercent}%)")
        appendLine()
        items.forEach { (item, done) ->
            val check = if (done) "x" else " "
            appendLine("- [$check] $item")
        }
    }

    companion object {
        val DEFAULT_ITEMS = mapOf(
            "Plugin builds without errors" to false,
            "All tests pass" to false,
            "Plugin.xml validates" to false,
            "Screenshots prepared (min 3)" to false,
            "Description formatted (HTML)" to false,
            "Change notes complete" to false,
            "Icon created (40x40 SVG)" to false,
            "Signed with certificate" to false,
            "README updated" to false,
            "License file included" to false
        )

        /**
         * Creates a fully complete checklist.
         */
        fun allComplete() = MarketplaceSubmission(DEFAULT_ITEMS.mapValues { true })
    }
}

// =============================================================================
// Tutorial Content
// =============================================================================

/**
 * Tutorial definition for getting started guides.
 *
 * @property id Tutorial identifier
 * @property title Tutorial title
 * @property description Brief description
 * @property steps Tutorial steps
 * @property estimatedMinutes Estimated completion time
 */
data class Tutorial(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<TutorialStep>,
    val estimatedMinutes: Int
) {
    /**
     * Gets step count.
     */
    val stepCount: Int get() = steps.size

    /**
     * Formats for display.
     */
    fun format(): String = "$title ($stepCount steps, ~${estimatedMinutes}m)"

    companion object {
        /**
         * Quick start tutorial.
         */
        val QUICK_START = Tutorial(
            id = "quick-start",
            title = "Quick Start Guide",
            description = "Get up and running in 5 minutes",
            estimatedMinutes = 5,
            steps = listOf(
                TutorialStep("Install Ollama", "Download and install Ollama from ollama.ai"),
                TutorialStep("Pull a model", "Run: ollama pull codellama"),
                TutorialStep("Open Sidekick", "Click the Sidekick icon in the tool window bar"),
                TutorialStep("Start chatting", "Type a message and press Enter!")
            )
        )
    }
}

/**
 * A single step in a tutorial.
 *
 * @property title Step title
 * @property description Step instructions
 * @property codeSnippet Optional code example
 */
data class TutorialStep(
    val title: String,
    val description: String,
    val codeSnippet: String? = null
)
