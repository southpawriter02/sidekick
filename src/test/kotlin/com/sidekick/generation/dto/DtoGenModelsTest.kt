// =============================================================================
// DtoGenModelsTest.kt
// =============================================================================
// Unit tests for DTO generation data models.
// =============================================================================

package com.sidekick.generation.dto

import com.sidekick.context.ProjectType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for DtoGenModels.
 */
class DtoGenModelsTest {

    @Nested
    @DisplayName("DtoGenRequest")
    inner class DtoGenRequestTests {
        
        @Test
        @DisplayName("isValid returns true for valid request")
        fun `isValid returns true for valid request`() {
            val request = DtoGenRequest(
                json = """{"name": "test"}""",
                targetLanguage = TargetLanguage.KOTLIN_DATA_CLASS,
                className = "TestDto"
            )
            assertTrue(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for blank json")
        fun `isValid returns false for blank json`() {
            val request = DtoGenRequest(
                json = "",
                targetLanguage = TargetLanguage.KOTLIN_DATA_CLASS,
                className = "TestDto"
            )
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for blank className")
        fun `isValid returns false for blank className`() {
            val request = DtoGenRequest(
                json = """{"name": "test"}""",
                targetLanguage = TargetLanguage.KOTLIN_DATA_CLASS,
                className = ""
            )
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("fromJson creates request with correct language")
        fun `fromJson creates request with correct language`() {
            val request = DtoGenRequest.fromJson(
                """{"name": "test"}""",
                "TestDto",
                ProjectType.DOTNET
            )
            assertEquals(TargetLanguage.CSHARP_RECORD, request.targetLanguage)
        }
        
        @Test
        @DisplayName("simple creates basic request")
        fun `simple creates basic request`() {
            val request = DtoGenRequest.simple("""{"id": 1}""", "MyDto")
            assertEquals(TargetLanguage.KOTLIN_DATA_CLASS, request.targetLanguage)
            assertEquals("MyDto", request.className)
        }
    }

    @Nested
    @DisplayName("TargetLanguage")
    inner class TargetLanguageTests {
        
        @Test
        @DisplayName("fromProjectType returns correct language")
        fun `fromProjectType returns correct language`() {
            assertEquals(TargetLanguage.CSHARP_RECORD, TargetLanguage.fromProjectType(ProjectType.DOTNET))
            assertEquals(TargetLanguage.KOTLIN_DATA_CLASS, TargetLanguage.fromProjectType(ProjectType.GRADLE))
            assertEquals(TargetLanguage.TYPESCRIPT_INTERFACE, TargetLanguage.fromProjectType(ProjectType.NPM))
            assertEquals(TargetLanguage.PYTHON_DATACLASS, TargetLanguage.fromProjectType(ProjectType.PYTHON))
        }
        
        @Test
        @DisplayName("fromExtension returns correct language")
        fun `fromExtension returns correct language`() {
            assertEquals(TargetLanguage.CSHARP_RECORD, TargetLanguage.fromExtension("cs"))
            assertEquals(TargetLanguage.KOTLIN_DATA_CLASS, TargetLanguage.fromExtension("kt"))
            assertEquals(TargetLanguage.JAVA_RECORD, TargetLanguage.fromExtension("java"))
            assertEquals(TargetLanguage.TYPESCRIPT_INTERFACE, TargetLanguage.fromExtension("ts"))
            assertEquals(TargetLanguage.PYTHON_DATACLASS, TargetLanguage.fromExtension("py"))
        }
        
        @Test
        @DisplayName("has correct file extensions")
        fun `has correct file extensions`() {
            assertEquals("cs", TargetLanguage.CSHARP_RECORD.fileExtension)
            assertEquals("kt", TargetLanguage.KOTLIN_DATA_CLASS.fileExtension)
            assertEquals("java", TargetLanguage.JAVA_RECORD.fileExtension)
            assertEquals("ts", TargetLanguage.TYPESCRIPT_INTERFACE.fileExtension)
            assertEquals("py", TargetLanguage.PYTHON_DATACLASS.fileExtension)
        }
        
        @Test
        @DisplayName("collection contains expected options")
        fun `collection contains expected options`() {
            assertTrue(TargetLanguage.CSHARP_OPTIONS.contains(TargetLanguage.CSHARP_RECORD))
            assertTrue(TargetLanguage.CSHARP_OPTIONS.contains(TargetLanguage.CSHARP_CLASS))
            assertTrue(TargetLanguage.JAVA_OPTIONS.contains(TargetLanguage.JAVA_RECORD))
        }
    }

    @Nested
    @DisplayName("DtoOptions")
    inner class DtoOptionsTests {
        
        @Test
        @DisplayName("default options are reasonable")
        fun `default options are reasonable`() {
            val options = DtoOptions()
            assertTrue(options.useNullableTypes)
            assertTrue(options.addJsonAttributes)
            assertTrue(options.makeImmutable)
            assertFalse(options.generateBuilder)
        }
        
        @Test
        @DisplayName("API_RESPONSE preset is configured correctly")
        fun `API_RESPONSE preset is configured correctly`() {
            val options = DtoOptions.API_RESPONSE
            assertTrue(options.useNullableTypes)
            assertTrue(options.addJsonAttributes)
            assertTrue(options.makeImmutable)
        }
        
        @Test
        @DisplayName("MUTABLE preset is configured correctly")
        fun `MUTABLE preset is configured correctly`() {
            val options = DtoOptions.MUTABLE
            assertFalse(options.makeImmutable)
            assertTrue(options.generateEquals)
        }
        
        @Test
        @DisplayName("MINIMAL preset is configured correctly")
        fun `MINIMAL preset is configured correctly`() {
            val options = DtoOptions.MINIMAL
            assertFalse(options.addJsonAttributes)
            assertTrue(options.makeImmutable)
        }
    }

    @Nested
    @DisplayName("JsonType")
    inner class JsonTypeTests {
        
        @Test
        @DisplayName("infer detects string")
        fun `infer detects string`() {
            assertEquals(JsonType.STRING, JsonType.infer("hello"))
        }
        
        @Test
        @DisplayName("infer detects boolean")
        fun `infer detects boolean`() {
            assertEquals(JsonType.BOOLEAN, JsonType.infer(true))
        }
        
        @Test
        @DisplayName("infer detects integer")
        fun `infer detects integer`() {
            assertEquals(JsonType.INTEGER, JsonType.infer(42))
        }
        
        @Test
        @DisplayName("infer detects long")
        fun `infer detects long`() {
            assertEquals(JsonType.LONG, JsonType.infer(9999999999L))
        }
        
        @Test
        @DisplayName("infer detects double")
        fun `infer detects double`() {
            assertEquals(JsonType.DOUBLE, JsonType.infer(3.14))
        }
        
        @Test
        @DisplayName("infer detects null")
        fun `infer detects null`() {
            assertEquals(JsonType.NULL, JsonType.infer(null))
        }
        
        @Test
        @DisplayName("infer detects array")
        fun `infer detects array`() {
            assertEquals(JsonType.ARRAY, JsonType.infer(listOf(1, 2, 3)))
        }
        
        @Test
        @DisplayName("infer detects object")
        fun `infer detects object`() {
            assertEquals(JsonType.OBJECT, JsonType.infer(mapOf("key" to "value")))
        }
    }

    @Nested
    @DisplayName("InferredProperty")
    inner class InferredPropertyTests {
        
        @Test
        @DisplayName("typeString returns correct Kotlin type")
        fun `typeString returns correct Kotlin type`() {
            val prop = InferredProperty(
                jsonName = "user_name",
                propertyName = "userName",
                type = JsonType.STRING,
                isNullable = true
            )
            assertEquals("String?", prop.typeString(TargetLanguage.KOTLIN_DATA_CLASS))
        }
        
        @Test
        @DisplayName("typeString returns correct C# type")
        fun `typeString returns correct C# type`() {
            val prop = InferredProperty(
                jsonName = "count",
                propertyName = "count",
                type = JsonType.INTEGER,
                isNullable = true
            )
            assertEquals("int?", prop.typeString(TargetLanguage.CSHARP_RECORD))
        }
        
        @Test
        @DisplayName("typeString returns correct TypeScript type")
        fun `typeString returns correct TypeScript type`() {
            val prop = InferredProperty(
                jsonName = "price",
                propertyName = "price",
                type = JsonType.DOUBLE,
                isNullable = true
            )
            assertEquals("number | null", prop.typeString(TargetLanguage.TYPESCRIPT_INTERFACE))
        }
        
        @Test
        @DisplayName("typeString returns correct Python type")
        fun `typeString returns correct Python type`() {
            val prop = InferredProperty(
                jsonName = "active",
                propertyName = "active",
                type = JsonType.BOOLEAN,
                isNullable = true
            )
            assertEquals("Optional[bool]", prop.typeString(TargetLanguage.PYTHON_DATACLASS))
        }
        
        @Test
        @DisplayName("array type includes element type")
        fun `array type includes element type`() {
            val prop = InferredProperty(
                jsonName = "ids",
                propertyName = "ids",
                type = JsonType.ARRAY,
                isNullable = false,
                arrayElementType = JsonType.INTEGER
            )
            assertEquals("List<Int>", prop.typeString(TargetLanguage.KOTLIN_DATA_CLASS))
            assertEquals("List<int>", prop.typeString(TargetLanguage.CSHARP_RECORD))
            assertEquals("number[]", prop.typeString(TargetLanguage.TYPESCRIPT_INTERFACE))
        }
    }

    @Nested
    @DisplayName("DtoGenResult")
    inner class DtoGenResultTests {
        
        @Test
        @DisplayName("success creates successful result")
        fun `success creates successful result`() {
            val result = DtoGenResult.success(
                code = "data class Test(val id: Int)",
                className = "Test"
            )
            assertTrue(result.success)
            assertEquals("Test", result.className)
            assertNull(result.error)
        }
        
        @Test
        @DisplayName("failure creates failed result")
        fun `failure creates failed result`() {
            val result = DtoGenResult.failure("Invalid JSON")
            assertFalse(result.success)
            assertEquals("Invalid JSON", result.error)
        }
        
        @Test
        @DisplayName("fullCode includes imports")
        fun `fullCode includes imports`() {
            val result = DtoGenResult.success(
                code = "data class Test(val id: Int)",
                className = "Test",
                imports = listOf("import kotlinx.serialization.*")
            )
            val full = result.fullCode()
            assertTrue(full.contains("import kotlinx.serialization.*"))
            assertTrue(full.contains("data class Test"))
        }
    }

    @Nested
    @DisplayName("NamingUtils")
    inner class NamingUtilsTests {
        
        @Test
        @DisplayName("toCamelCase converts snake_case")
        fun `toCamelCase converts snake_case`() {
            assertEquals("userName", NamingUtils.toCamelCase("user_name"))
            assertEquals("firstName", NamingUtils.toCamelCase("first_name"))
        }
        
        @Test
        @DisplayName("toCamelCase converts kebab-case")
        fun `toCamelCase converts kebab-case`() {
            assertEquals("userId", NamingUtils.toCamelCase("user-id"))
        }
        
        @Test
        @DisplayName("toCamelCase handles single word")
        fun `toCamelCase handles single word`() {
            assertEquals("name", NamingUtils.toCamelCase("name"))
        }
        
        @Test
        @DisplayName("toPascalCase converts snake_case")
        fun `toPascalCase converts snake_case`() {
            assertEquals("UserName", NamingUtils.toPascalCase("user_name"))
        }
        
        @Test
        @DisplayName("inferClassName finds type hint")
        fun `inferClassName finds type hint`() {
            val json = """{"@type": "user_profile", "id": 1}"""
            assertEquals("UserProfile", NamingUtils.inferClassName(json))
        }
        
        @Test
        @DisplayName("inferClassName returns default for no hint")
        fun `inferClassName returns default for no hint`() {
            val json = """{"id": 1, "value": 42}"""
            assertEquals("GeneratedDto", NamingUtils.inferClassName(json))
        }
    }
}
