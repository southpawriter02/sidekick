package com.sidekick.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Extension Models.
 */
@DisplayName("Extension Models Tests")
class ExtensionModelsTest {

    // =========================================================================
    // ExtensionInfo Tests
    // =========================================================================

    @Nested
    @DisplayName("ExtensionInfo")
    inner class ExtensionInfoTests {

        @Test
        @DisplayName("from creates info from extension")
        fun fromCreatesInfoFromExtension() {
            val extension = TestExtension("test-ext", "Test Extension", "1.0.0", "Test desc")
            val info = ExtensionInfo.from(extension)

            assertEquals("test-ext", info.id)
            assertEquals("Test Extension", info.name)
            assertEquals("1.0.0", info.version)
            assertEquals("Test desc", info.description)
        }

        @Test
        @DisplayName("format produces readable output")
        fun formatProducesReadableOutput() {
            val info = ExtensionInfo(
                id = "test",
                name = "Test",
                version = "1.0.0",
                description = "A test extension"
            )

            val formatted = info.format()
            assertTrue(formatted.contains("Test"))
            assertTrue(formatted.contains("1.0.0"))
        }
    }

    // =========================================================================
    // CustomPromptTemplate Tests
    // =========================================================================

    @Nested
    @DisplayName("CustomPromptTemplate")
    inner class CustomPromptTemplateTests {

        @Test
        @DisplayName("apply substitutes variables")
        fun applySubstitutesVariables() {
            val template = CustomPromptTemplate(
                id = "test",
                name = "Test",
                description = "Test template",
                template = "Hello {{name}}! You are a {{role}}.",
                category = "test",
                variables = listOf(
                    TemplateVariable.string("name", "Name"),
                    TemplateVariable.string("role", "Role")
                )
            )

            val result = template.apply(mapOf("name" to "Alice", "role" to "developer"))
            assertEquals("Hello Alice! You are a developer.", result)
        }

        @Test
        @DisplayName("apply uses default values")
        fun applyUsesDefaultValues() {
            val template = CustomPromptTemplate(
                id = "test",
                name = "Test",
                description = "Test",
                template = "Hello {{name}}!",
                category = "test",
                variables = listOf(
                    TemplateVariable("name", "Name", VariableType.STRING, false, "World")
                )
            )

            val result = template.apply(emptyMap())
            assertEquals("Hello World!", result)
        }

        @Test
        @DisplayName("validateVariables detects missing required")
        fun validateVariablesDetectsMissingRequired() {
            val template = CustomPromptTemplate(
                id = "test",
                name = "Test",
                description = "Test",
                template = "{{a}} {{b}}",
                category = "test",
                variables = listOf(
                    TemplateVariable.string("a", "A"),
                    TemplateVariable.string("b", "B")
                )
            )

            val errors = template.validateVariables(mapOf("a" to "value"))
            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("b"))
        }

        @Test
        @DisplayName("requiredVariables filters correctly")
        fun requiredVariablesFiltersCorrectly() {
            val template = CustomPromptTemplate(
                id = "test",
                name = "Test",
                description = "Test",
                template = "{{a}} {{b}}",
                category = "test",
                variables = listOf(
                    TemplateVariable("a", "A", VariableType.STRING, true),
                    TemplateVariable("b", "B", VariableType.STRING, false)
                )
            )

            assertEquals(1, template.requiredVariables.size)
            assertEquals("a", template.requiredVariables[0].name)
        }

        @Test
        @DisplayName("simple factory creates valid template")
        fun simpleFactoryCreatesValidTemplate() {
            val template = CustomPromptTemplate.simple("id", "Name", "Hello!")

            assertEquals("id", template.id)
            assertTrue(template.variables.isEmpty())
            assertEquals("Hello!", template.apply(emptyMap()))
        }
    }

    // =========================================================================
    // TemplateVariable Tests
    // =========================================================================

    @Nested
    @DisplayName("TemplateVariable")
    inner class TemplateVariableTests {

        @Test
        @DisplayName("validate returns error for missing required")
        fun validateReturnsErrorForMissingRequired() {
            val variable = TemplateVariable.string("test", "Test")

            assertNotNull(variable.validate(null))
            assertNotNull(variable.validate(""))
            assertNull(variable.validate("value"))
        }

        @Test
        @DisplayName("factory methods create correct types")
        fun factoryMethodsCreateCorrectTypes() {
            val string = TemplateVariable.string("s", "String")
            val code = TemplateVariable.code("c")
            val optional = TemplateVariable.optional("o", "Optional", "default")

            assertEquals(VariableType.STRING, string.type)
            assertEquals(VariableType.CODE, code.type)
            assertFalse(optional.required)
            assertEquals("default", optional.default)
        }
    }

    // =========================================================================
    // VariableType Tests
    // =========================================================================

    @Nested
    @DisplayName("VariableType")
    inner class VariableTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            VariableType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("all types have placeholders")
        fun allTypesHavePlaceholders() {
            VariableType.entries.forEach { type ->
                assertTrue(type.placeholder.isNotBlank())
            }
        }

        @Test
        @DisplayName("isSpecial identifies non-string types")
        fun isSpecialIdentifiesNonStringTypes() {
            assertFalse(VariableType.STRING.isSpecial)
            assertTrue(VariableType.CODE.isSpecial)
            assertTrue(VariableType.FILE_PATH.isSpecial)
            assertTrue(VariableType.SELECTION.isSpecial)
            assertTrue(VariableType.SYMBOL.isSpecial)
        }
    }

    // =========================================================================
    // ExtensionTool Tests
    // =========================================================================

    @Nested
    @DisplayName("ExtensionTool")
    inner class ExtensionToolTests {

        @Test
        @DisplayName("requiredParameters lists required only")
        fun requiredParametersListsRequiredOnly() {
            val tool = ExtensionTool(
                name = "test",
                description = "Test tool",
                parameters = mapOf(
                    "required" to ToolParameter.string("Required"),
                    "optional" to ToolParameter.optional("string", "Optional")
                ),
                handler = { ToolResult.success("ok") }
            )

            assertEquals(listOf("required"), tool.requiredParameters)
        }

        @Test
        @DisplayName("validateParameters detects missing required")
        fun validateParametersDetectsMissingRequired() {
            val tool = ExtensionTool(
                name = "test",
                description = "Test",
                parameters = mapOf(
                    "a" to ToolParameter.string("A"),
                    "b" to ToolParameter.string("B")
                ),
                handler = { ToolResult.success("ok") }
            )

            val errors = tool.validateParameters(mapOf("a" to "value"))
            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("b"))
        }

        @Test
        @DisplayName("toSchema generates valid schema")
        fun toSchemaGeneratesValidSchema() {
            val tool = ExtensionTool(
                name = "my_tool",
                description = "My tool description",
                parameters = mapOf(
                    "query" to ToolParameter.string("Search query")
                ),
                handler = { ToolResult.success("ok") }
            )

            val schema = tool.toSchema()
            assertEquals("my_tool", schema["name"])
            assertEquals("My tool description", schema["description"])
            assertTrue(schema.containsKey("parameters"))
        }
    }

    // =========================================================================
    // ToolParameter Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolParameter")
    inner class ToolParameterTests {

        @Test
        @DisplayName("factory methods create correct parameters")
        fun factoryMethodsCreateCorrectParameters() {
            val string = ToolParameter.string("String")
            val number = ToolParameter.number("Number")
            val boolean = ToolParameter.boolean("Boolean")
            val optional = ToolParameter.optional("string", "Optional")

            assertEquals("string", string.type)
            assertTrue(string.required)
            assertEquals("number", number.type)
            assertEquals("boolean", boolean.type)
            assertFalse(optional.required)
        }
    }

    // =========================================================================
    // ToolResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ToolResult")
    inner class ToolResultTests {

        @Test
        @DisplayName("success factory creates successful result")
        fun successFactoryCreatesSuccessfulResult() {
            val result = ToolResult.success("output")

            assertTrue(result.success)
            assertEquals("output", result.output)
            assertNull(result.error)
        }

        @Test
        @DisplayName("failure factory creates failed result")
        fun failureFactoryCreatesFailedResult() {
            val result = ToolResult.failure("error message")

            assertFalse(result.success)
            assertEquals("error message", result.error)
        }

        @Test
        @DisplayName("runCatching captures success")
        fun runCatchingCapturesSuccess() {
            val result = ToolResult.runCatching { "success" }

            assertTrue(result.success)
            assertEquals("success", result.output)
        }

        @Test
        @DisplayName("runCatching captures failure")
        fun runCatchingCapturesFailure() {
            val result = ToolResult.runCatching { throw RuntimeException("fail") }

            assertFalse(result.success)
            assertEquals("fail", result.error)
        }

        @Test
        @DisplayName("format shows output or error")
        fun formatShowsOutputOrError() {
            val success = ToolResult.success("data")
            val failure = ToolResult.failure("error")

            assertEquals("data", success.format())
            assertTrue(failure.format().contains("Error"))
        }
    }

    // =========================================================================
    // VisualEnhancement Tests
    // =========================================================================

    @Nested
    @DisplayName("VisualEnhancement")
    inner class VisualEnhancementTests {

        @Test
        @DisplayName("withEnabled toggles state")
        fun withEnabledTogglesState() {
            val enhancement = VisualEnhancement(
                id = "test",
                name = "Test",
                type = EnhancementType.LINE_MARKER,
                renderer = Any()
            )

            assertTrue(enhancement.enabled)
            assertFalse(enhancement.withEnabled(false).enabled)
        }

        @Test
        @DisplayName("format produces readable output")
        fun formatProducesReadableOutput() {
            val enhancement = VisualEnhancement(
                id = "test",
                name = "Test Enhancement",
                type = EnhancementType.GUTTER_ICON,
                renderer = Any()
            )

            val formatted = enhancement.format()
            assertTrue(formatted.contains("Test Enhancement"))
            assertTrue(formatted.contains("Gutter Icon"))
        }
    }

    // =========================================================================
    // EnhancementType Tests
    // =========================================================================

    @Nested
    @DisplayName("EnhancementType")
    inner class EnhancementTypeTests {

        @Test
        @DisplayName("all types have display names")
        fun allTypesHaveDisplayNames() {
            EnhancementType.entries.forEach { type ->
                assertTrue(type.displayName.isNotBlank())
            }
        }

        @Test
        @DisplayName("requiresEditorIntegration is correct")
        fun requiresEditorIntegrationIsCorrect() {
            assertTrue(EnhancementType.HIGHLIGHTER.requiresEditorIntegration)
            assertTrue(EnhancementType.LINE_MARKER.requiresEditorIntegration)
            assertTrue(EnhancementType.GUTTER_ICON.requiresEditorIntegration)
            assertFalse(EnhancementType.TAB_COLOR.requiresEditorIntegration)
        }
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    private class TestExtension(
        override val id: String,
        override val name: String,
        override val version: String,
        override val description: String
    ) : SidekickExtension {
        override fun initialize() {}
        override fun dispose() {}
    }
}
