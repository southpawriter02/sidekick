package com.sidekick.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for ExtensionManager.
 *
 * Note: Tests for getInstance() and extension points are skipped as they require
 * IntelliJ Platform test infrastructure. Core logic is tested here.
 */
@DisplayName("ExtensionManager Tests")
class ExtensionManagerTest {

    private lateinit var manager: ExtensionManager

    @BeforeEach
    fun setup() {
        manager = ExtensionManager()
        manager.disposeAll()
    }

    // =========================================================================
    // Extension Registration Tests
    // =========================================================================

    @Nested
    @DisplayName("Extension Registration")
    inner class RegistrationTests {

        @Test
        @DisplayName("registerExtension adds extension")
        fun registerExtensionAddsExtension() {
            val extension = TestPromptExtension()

            assertTrue(manager.registerExtension(extension))
            assertTrue(manager.isExtensionRegistered(extension.id))
        }

        @Test
        @DisplayName("registerExtension calls initialize")
        fun registerExtensionCallsInitialize() {
            val extension = TestPromptExtension()

            manager.registerExtension(extension)

            assertTrue(extension.initialized)
        }

        @Test
        @DisplayName("registerExtension rejects duplicate")
        fun registerExtensionRejectsDuplicate() {
            val extension = TestPromptExtension()

            assertTrue(manager.registerExtension(extension))
            assertFalse(manager.registerExtension(extension))
        }

        @Test
        @DisplayName("unregisterExtension removes extension")
        fun unregisterExtensionRemovesExtension() {
            val extension = TestPromptExtension()
            manager.registerExtension(extension)

            assertTrue(manager.unregisterExtension(extension.id))
            assertFalse(manager.isExtensionRegistered(extension.id))
        }

        @Test
        @DisplayName("unregisterExtension calls dispose")
        fun unregisterExtensionCallsDispose() {
            val extension = TestPromptExtension()
            manager.registerExtension(extension)

            manager.unregisterExtension(extension.id)

            assertTrue(extension.disposed)
        }

        @Test
        @DisplayName("unregisterExtension returns false for unknown")
        fun unregisterExtensionReturnsFalseForUnknown() {
            assertFalse(manager.unregisterExtension("unknown"))
        }
    }

    // =========================================================================
    // Extension Info Tests
    // =========================================================================

    @Nested
    @DisplayName("Extension Info")
    inner class ExtensionInfoTests {

        @Test
        @DisplayName("getExtensionInfo returns info for registered")
        fun getExtensionInfoReturnsInfoForRegistered() {
            val extension = TestPromptExtension()
            manager.registerExtension(extension)

            val info = manager.getExtensionInfo(extension.id)

            assertNotNull(info)
            assertEquals(extension.name, info?.name)
            assertEquals(extension.version, info?.version)
        }

        @Test
        @DisplayName("getExtensionInfo returns null for unknown")
        fun getExtensionInfoReturnsNullForUnknown() {
            assertNull(manager.getExtensionInfo("unknown"))
        }

        @Test
        @DisplayName("getAllExtensionInfo returns all registered")
        fun getAllExtensionInfoReturnsAllRegistered() {
            manager.registerExtension(TestPromptExtension())
            manager.registerExtension(TestToolExtension())

            val all = manager.getAllExtensionInfo()

            assertEquals(2, all.size)
        }

        @Test
        @DisplayName("getExtensionCount returns correct count")
        fun getExtensionCountReturnsCorrectCount() {
            assertEquals(0, manager.getExtensionCount())

            manager.registerExtension(TestPromptExtension())
            assertEquals(1, manager.getExtensionCount())

            manager.registerExtension(TestToolExtension())
            assertEquals(2, manager.getExtensionCount())
        }
    }

    // =========================================================================
    // Prompt Template Aggregation Tests
    // =========================================================================

    @Nested
    @DisplayName("Prompt Template Aggregation")
    inner class PromptTemplateTests {

        @Test
        @DisplayName("getAllPromptTemplates aggregates from extensions")
        fun getAllPromptTemplatesAggregatesFromExtensions() {
            manager.registerExtension(TestPromptExtension())

            val templates = manager.getAllPromptTemplates()

            assertTrue(templates.isNotEmpty())
            assertTrue(templates.any { it.id == "test-template" })
        }

        @Test
        @DisplayName("getPromptTemplatesByCategory filters correctly")
        fun getPromptTemplatesByCategoryFiltersCorrectly() {
            manager.registerExtension(TestPromptExtension())

            val templates = manager.getPromptTemplatesByCategory("testing")

            assertTrue(templates.all { it.category == "testing" })
        }

        @Test
        @DisplayName("getPromptTemplate finds by ID")
        fun getPromptTemplateFindsById() {
            manager.registerExtension(TestPromptExtension())

            val template = manager.getPromptTemplate("test-template")

            assertNotNull(template)
            assertEquals("Test Template", template?.name)
        }

        @Test
        @DisplayName("getPromptTemplateCategories returns unique categories")
        fun getPromptTemplateCategoriesReturnsUniqueCategories() {
            manager.registerExtension(TestPromptExtension())

            val categories = manager.getPromptTemplateCategories()

            assertTrue(categories.contains("testing"))
        }
    }

    // =========================================================================
    // Agent Tool Aggregation Tests
    // =========================================================================

    @Nested
    @DisplayName("Agent Tool Aggregation")
    inner class AgentToolTests {

        @Test
        @DisplayName("getAllAgentTools aggregates from extensions")
        fun getAllAgentToolsAggregatesFromExtensions() {
            manager.registerExtension(TestToolExtension())

            val tools = manager.getAllAgentTools()

            assertTrue(tools.isNotEmpty())
            assertTrue(tools.any { it.name == "test_tool" })
        }

        @Test
        @DisplayName("getAgentTool finds by name")
        fun getAgentToolFindsByName() {
            manager.registerExtension(TestToolExtension())

            val tool = manager.getAgentTool("test_tool")

            assertNotNull(tool)
            assertEquals("Test Tool", tool?.description)
        }

        @Test
        @DisplayName("getToolSchemas returns schemas for all tools")
        fun getToolSchemasReturnsSchemasForAllTools() {
            manager.registerExtension(TestToolExtension())

            val schemas = manager.getToolSchemas()

            assertTrue(schemas.isNotEmpty())
            assertTrue(schemas.any { it["name"] == "test_tool" })
        }
    }

    // =========================================================================
    // Visual Enhancement Aggregation Tests
    // =========================================================================

    @Nested
    @DisplayName("Visual Enhancement Aggregation")
    inner class VisualEnhancementTests {

        @Test
        @DisplayName("getAllVisualEnhancements aggregates from extensions")
        fun getAllVisualEnhancementsAggregatesFromExtensions() {
            manager.registerExtension(TestVisualExtension())

            val enhancements = manager.getAllVisualEnhancements()

            assertTrue(enhancements.isNotEmpty())
            assertTrue(enhancements.any { it.id == "test-visual" })
        }

        @Test
        @DisplayName("getVisualEnhancementsByType filters correctly")
        fun getVisualEnhancementsByTypeFiltersCorrectly() {
            manager.registerExtension(TestVisualExtension())

            val markers = manager.getVisualEnhancementsByType(EnhancementType.LINE_MARKER)

            assertTrue(markers.all { it.type == EnhancementType.LINE_MARKER })
        }

        @Test
        @DisplayName("getVisualEnhancement finds by ID")
        fun getVisualEnhancementFindsById() {
            manager.registerExtension(TestVisualExtension())

            val enhancement = manager.getVisualEnhancement("test-visual")

            assertNotNull(enhancement)
            assertEquals("Test Visual", enhancement?.name)
        }
    }

    // =========================================================================
    // Event Listener Tests
    // =========================================================================

    @Nested
    @DisplayName("Event Listeners")
    inner class EventListenerTests {

        @Test
        @DisplayName("listeners receive registration events")
        fun listenersReceiveRegistrationEvents() {
            var receivedEvent: ExtensionEvent? = null
            manager.addListener { receivedEvent = it }

            manager.registerExtension(TestPromptExtension())

            assertTrue(receivedEvent is ExtensionEvent.Registered)
        }

        @Test
        @DisplayName("listeners receive unregistration events")
        fun listenersReceiveUnregistrationEvents() {
            val extension = TestPromptExtension()
            manager.registerExtension(extension)

            var receivedEvent: ExtensionEvent? = null
            manager.addListener { receivedEvent = it }

            manager.unregisterExtension(extension.id)

            assertTrue(receivedEvent is ExtensionEvent.Unregistered)
        }

        @Test
        @DisplayName("removeListener stops notifications")
        fun removeListenerStopsNotifications() {
            var eventCount = 0
            val listener: (ExtensionEvent) -> Unit = { eventCount++ }
            manager.addListener(listener)

            manager.registerExtension(TestPromptExtension())
            assertEquals(1, eventCount)

            manager.removeListener(listener)
            manager.registerExtension(TestToolExtension())
            assertEquals(1, eventCount) // Still 1, no new events
        }
    }

    // =========================================================================
    // Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle")
    inner class LifecycleTests {

        @Test
        @DisplayName("disposeAll removes all extensions")
        fun disposeAllRemovesAllExtensions() {
            manager.registerExtension(TestPromptExtension())
            manager.registerExtension(TestToolExtension())

            manager.disposeAll()

            assertEquals(0, manager.getExtensionCount())
        }

        @Test
        @DisplayName("disposeAll calls dispose on each")
        fun disposeAllCallsDisposeOnEach() {
            val ext1 = TestPromptExtension()
            val ext2 = TestToolExtension()
            manager.registerExtension(ext1)
            manager.registerExtension(ext2)

            manager.disposeAll()

            assertTrue(ext1.disposed)
            assertTrue(ext2.disposed)
        }
    }

    // =========================================================================
    // Reporting Tests
    // =========================================================================

    @Nested
    @DisplayName("Reporting")
    inner class ReportingTests {

        @Test
        @DisplayName("getExtensionReport includes extensions")
        fun getExtensionReportIncludesExtensions() {
            manager.registerExtension(TestPromptExtension())

            val report = manager.getExtensionReport()

            assertTrue(report.contains("Extension Report"))
            assertTrue(report.contains("Test Prompt Extension"))
        }

        @Test
        @DisplayName("getExtensionReport includes templates")
        fun getExtensionReportIncludesTemplates() {
            manager.registerExtension(TestPromptExtension())

            val report = manager.getExtensionReport()

            assertTrue(report.contains("Prompt Templates"))
        }

        @Test
        @DisplayName("getExtensionReport includes tools")
        fun getExtensionReportIncludesTools() {
            manager.registerExtension(TestToolExtension())

            val report = manager.getExtensionReport()

            assertTrue(report.contains("Agent Tools"))
        }
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    private class TestPromptExtension : PromptTemplateExtension {
        override val id = "test-prompt"
        override val name = "Test Prompt Extension"
        override val version = "1.0.0"
        override val description = "Test prompt extension"

        var initialized = false
        var disposed = false

        override fun initialize() { initialized = true }
        override fun dispose() { disposed = true }

        override fun getTemplates() = listOf(
            CustomPromptTemplate(
                id = "test-template",
                name = "Test Template",
                description = "A test template",
                template = "Hello {{name}}!",
                category = "testing",
                variables = listOf(TemplateVariable.string("name", "Name"))
            )
        )
    }

    private class TestToolExtension : AgentToolExtension {
        override val id = "test-tool-ext"
        override val name = "Test Tool Extension"
        override val version = "1.0.0"
        override val description = "Test tool extension"

        var initialized = false
        var disposed = false

        override fun initialize() { initialized = true }
        override fun dispose() { disposed = true }

        override fun getTools() = listOf(
            ExtensionTool(
                name = "test_tool",
                description = "Test Tool",
                parameters = mapOf("input" to ToolParameter.string("Input")),
                handler = { ToolResult.success("ok") }
            )
        )
    }

    private class TestVisualExtension : VisualExtension {
        override val id = "test-visual-ext"
        override val name = "Test Visual Extension"
        override val version = "1.0.0"
        override val description = "Test visual extension"

        var initialized = false
        var disposed = false

        override fun initialize() { initialized = true }
        override fun dispose() { disposed = true }

        override fun getEnhancements() = listOf(
            VisualEnhancement(
                id = "test-visual",
                name = "Test Visual",
                type = EnhancementType.LINE_MARKER,
                renderer = Any()
            )
        )
    }
}
