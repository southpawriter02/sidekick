package com.sidekick.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for Agent Tools.
 */
@DisplayName("Agent Tools Tests")
class AgentToolsTest {

    // =========================================================================
    // AgentTool Tests
    // =========================================================================

    @Nested
    @DisplayName("AgentTool")
    inner class AgentToolTests {

        @Test
        @DisplayName("tool has correct metadata")
        fun toolHasCorrectMetadata() {
            assertEquals("read_file", BuiltInTools.READ_FILE.name)
            assertTrue(BuiltInTools.READ_FILE.description.isNotBlank())
            assertEquals(ToolCategory.FILE_SYSTEM, BuiltInTools.READ_FILE.category)
            assertFalse(BuiltInTools.READ_FILE.isDestructive)
        }

        @Test
        @DisplayName("destructive tools are marked")
        fun destructiveToolsAreMarked() {
            assertTrue(BuiltInTools.WRITE_FILE.isDestructive)
            assertTrue(BuiltInTools.EDIT_FILE.isDestructive)
            assertTrue(BuiltInTools.RUN_COMMAND.isDestructive)
            assertFalse(BuiltInTools.READ_FILE.isDestructive)
            assertFalse(BuiltInTools.SEARCH_CODE.isDestructive)
        }

        @Test
        @DisplayName("toProviderTool converts correctly")
        fun toProviderToolConvertsCorrectly() {
            val providerTool = BuiltInTools.READ_FILE.toProviderTool()
            assertEquals("read_file", providerTool.name)
            assertTrue(providerTool.parameters.properties.containsKey("path"))
        }
    }

    // =========================================================================
    // ToolCategory Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolCategory")
    inner class ToolCategoryTests {

        @Test
        @DisplayName("all categories have display names")
        fun allCategoriesHaveDisplayNames() {
            ToolCategory.entries.forEach { category ->
                assertTrue(category.displayName.isNotBlank())
            }
        }
    }

    // =========================================================================
    // ToolParameters Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolParameters")
    inner class ToolParametersTests {

        @Test
        @DisplayName("simple factory creates correct parameters")
        fun simpleFactoryCreatesCorrectParameters() {
            val params = ToolParameters.simple(
                "path" to "File path",
                "content" to "File content"
            )

            assertEquals("object", params.type)
            assertEquals(2, params.properties.size)
            assertEquals(2, params.required.size)
            assertEquals("string", params.properties["path"]?.type)
        }

        @Test
        @DisplayName("default type is object")
        fun defaultTypeIsObject() {
            val params = ToolParameters(
                properties = mapOf("test" to ParameterSchema.string("A test"))
            )
            assertEquals("object", params.type)
        }
    }

    // =========================================================================
    // ParameterSchema Tests
    // =========================================================================

    @Nested
    @DisplayName("ParameterSchema")
    inner class ParameterSchemaTests {

        @Test
        @DisplayName("factory methods create correct types")
        fun factoryMethodsCreateCorrectTypes() {
            assertEquals("string", ParameterSchema.string("desc").type)
            assertEquals("integer", ParameterSchema.integer("desc").type)
            assertEquals("boolean", ParameterSchema.boolean("desc").type)
            assertEquals("array", ParameterSchema.array("desc").type)
        }
    }

    // =========================================================================
    // ToolResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolResult")
    inner class ToolResultTests {

        @Test
        @DisplayName("success creates success result")
        fun successCreatesSuccessResult() {
            val result = ToolResult.success("Output", mapOf("key" to "value"))
            assertTrue(result.success)
            assertEquals("Output", result.output)
            assertNull(result.error)
            assertTrue(result.metadata.containsKey("key"))
        }

        @Test
        @DisplayName("failure creates failure result")
        fun failureCreatesFailureResult() {
            val result = ToolResult.failure("Error message")
            assertFalse(result.success)
            assertEquals("", result.output)
            assertEquals("Error message", result.error)
        }
    }

    // =========================================================================
    // ToolCallRequest Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolCallRequest")
    inner class ToolCallRequestTests {

        @Test
        @DisplayName("getString extracts string argument")
        fun getStringExtractsStringArgument() {
            val request = ToolCallRequest(
                id = "call-1",
                name = "read_file",
                arguments = mapOf("path" to "/test.txt")
            )

            assertEquals("/test.txt", request.getString("path"))
            assertNull(request.getString("missing"))
            assertEquals("default", request.getString("missing", "default"))
        }

        @Test
        @DisplayName("getInt extracts integer argument")
        fun getIntExtractsIntegerArgument() {
            val request = ToolCallRequest(
                id = "call-2",
                name = "read_file",
                arguments = mapOf("startLine" to 10, "endLine" to 20.5)
            )

            assertEquals(10, request.getInt("startLine"))
            assertEquals(20, request.getInt("endLine"))
            assertNull(request.getInt("missing"))
        }

        @Test
        @DisplayName("getBoolean extracts boolean argument")
        fun getBooleanExtractsBooleanArgument() {
            val request = ToolCallRequest(
                id = "call-3",
                name = "list_files",
                arguments = mapOf("recursive" to true)
            )

            assertEquals(true, request.getBoolean("recursive"))
            assertNull(request.getBoolean("missing"))
        }
    }

    // =========================================================================
    // BuiltInTools Tests
    // =========================================================================

    @Nested
    @DisplayName("BuiltInTools")
    inner class BuiltInToolsTests {

        @Test
        @DisplayName("ALL contains expected tools")
        fun allContainsExpectedTools() {
            val names = BuiltInTools.ALL.map { it.name }
            assertTrue("read_file" in names)
            assertTrue("write_file" in names)
            assertTrue("edit_file" in names)
            assertTrue("list_files" in names)
            assertTrue("search_code" in names)
            assertTrue("run_command" in names)
        }

        @Test
        @DisplayName("findByName returns correct tool")
        fun findByNameReturnsCorrectTool() {
            val tool = BuiltInTools.findByName("read_file")
            assertNotNull(tool)
            assertEquals("read_file", tool?.name)

            assertNull(BuiltInTools.findByName("unknown_tool"))
        }

        @Test
        @DisplayName("BY_CATEGORY groups tools correctly")
        fun byCategoryGroupsToolsCorrectly() {
            val fileTools = BuiltInTools.BY_CATEGORY[ToolCategory.FILE_SYSTEM]
            assertNotNull(fileTools)
            assertTrue(fileTools!!.isNotEmpty())
            assertTrue(fileTools.all { it.category == ToolCategory.FILE_SYSTEM })
        }

        @Test
        @DisplayName("SAFE_TOOLS excludes destructive tools")
        fun safeToolsExcludesDestructive() {
            BuiltInTools.SAFE_TOOLS.forEach { tool ->
                assertFalse(tool.isDestructive)
            }
        }

        @Test
        @DisplayName("DESTRUCTIVE_TOOLS includes only destructive tools")
        fun destructiveToolsIncludesOnlyDestructive() {
            BuiltInTools.DESTRUCTIVE_TOOLS.forEach { tool ->
                assertTrue(tool.isDestructive)
            }
        }
    }

    // =========================================================================
    // Tool Handler Tests
    // =========================================================================

    @Nested
    @DisplayName("Tool Handlers")
    inner class ToolHandlerTests {

        @TempDir
        lateinit var tempDir: Path

        @Test
        @DisplayName("read_file reads existing file")
        fun readFileReadsExistingFile() = runBlocking {
            val file = tempDir.resolve("test.txt").toFile()
            file.writeText("Hello, World!\nLine 2\nLine 3")

            val result = BuiltInTools.READ_FILE.handler(mapOf("path" to file.absolutePath))

            assertTrue(result.success)
            assertTrue(result.output.contains("Hello, World!"))
        }

        @Test
        @DisplayName("read_file with line range")
        fun readFileWithLineRange() = runBlocking {
            val file = tempDir.resolve("test.txt").toFile()
            file.writeText("Line 1\nLine 2\nLine 3\nLine 4\nLine 5")

            val result = BuiltInTools.READ_FILE.handler(mapOf(
                "path" to file.absolutePath,
                "startLine" to 2,
                "endLine" to 4
            ))

            assertTrue(result.success)
            assertTrue(result.output.contains("Line 2"))
            assertTrue(result.output.contains("Line 4"))
            assertFalse(result.output.contains("Line 1"))
        }

        @Test
        @DisplayName("read_file fails for missing file")
        fun readFileFailsForMissingFile() = runBlocking {
            val result = BuiltInTools.READ_FILE.handler(mapOf("path" to "/nonexistent/file.txt"))

            assertFalse(result.success)
            assertTrue(result.error?.contains("not found") == true)
        }

        @Test
        @DisplayName("write_file creates new file")
        fun writeFileCreatesNewFile() = runBlocking {
            val file = tempDir.resolve("new_file.txt").toFile()

            val result = BuiltInTools.WRITE_FILE.handler(mapOf(
                "path" to file.absolutePath,
                "content" to "New content"
            ))

            assertTrue(result.success)
            assertTrue(file.exists())
            assertEquals("New content", file.readText())
        }

        @Test
        @DisplayName("edit_file replaces text")
        fun editFileReplacesText() = runBlocking {
            val file = tempDir.resolve("edit.txt").toFile()
            file.writeText("Hello, World!")

            val result = BuiltInTools.EDIT_FILE.handler(mapOf(
                "path" to file.absolutePath,
                "oldText" to "World",
                "newText" to "Kotlin"
            ))

            assertTrue(result.success)
            assertEquals("Hello, Kotlin!", file.readText())
        }

        @Test
        @DisplayName("edit_file fails when text not found")
        fun editFileFailsWhenTextNotFound() = runBlocking {
            val file = tempDir.resolve("edit.txt").toFile()
            file.writeText("Hello, World!")

            val result = BuiltInTools.EDIT_FILE.handler(mapOf(
                "path" to file.absolutePath,
                "oldText" to "NotPresent",
                "newText" to "Replacement"
            ))

            assertFalse(result.success)
            assertTrue(result.error?.contains("not found") == true)
        }

        @Test
        @DisplayName("list_files lists directory contents")
        fun listFilesListsContents() = runBlocking {
            val dir = tempDir.toFile()
            File(dir, "file1.txt").writeText("1")
            File(dir, "file2.txt").writeText("2")
            File(dir, "subdir").mkdir()

            val result = BuiltInTools.LIST_FILES.handler(mapOf("path" to dir.absolutePath))

            assertTrue(result.success)
            assertTrue(result.output.contains("file1.txt"))
            assertTrue(result.output.contains("file2.txt"))
            assertTrue(result.output.contains("subdir"))
        }

        @Test
        @DisplayName("list_files fails for missing directory")
        fun listFilesFailsForMissingDirectory() = runBlocking {
            val result = BuiltInTools.LIST_FILES.handler(mapOf("path" to "/nonexistent/dir"))

            assertFalse(result.success)
        }
    }
}
