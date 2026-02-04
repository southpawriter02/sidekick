// =============================================================================
// NamingModelsTest.kt
// =============================================================================
// Unit tests for naming data models.
// =============================================================================

package com.sidekick.generation.naming

import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for NamingModels.
 */
class NamingModelsTest {

    @Nested
    @DisplayName("NamingRequest")
    inner class NamingRequestTests {
        
        @Test
        @DisplayName("isValid returns true for valid context")
        fun `isValid returns true for valid context`() {
            val request = NamingRequest(
                context = "val count = items.size",
                currentName = "count",
                type = "Int"
            )
            assertTrue(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for blank context")
        fun `isValid returns false for blank context`() {
            val request = NamingRequest(context = "")
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for invalid suggestion count")
        fun `isValid returns false for invalid suggestion count`() {
            val request = NamingRequest(
                context = "some code",
                suggestionCount = 20  // Max is 10
            )
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("getEffectiveConvention auto-detects from language")
        fun `getEffectiveConvention auto-detects from language`() {
            val request = NamingRequest(
                context = "code",
                convention = NamingConvention.AUTO,
                language = "python"
            )
            assertEquals(NamingConvention.SNAKE_CASE, request.getEffectiveConvention())
        }
        
        @Test
        @DisplayName("getEffectiveConvention returns explicit convention")
        fun `getEffectiveConvention returns explicit convention`() {
            val request = NamingRequest(
                context = "code",
                convention = NamingConvention.PASCAL_CASE,
                language = "kotlin"
            )
            assertEquals(NamingConvention.PASCAL_CASE, request.getEffectiveConvention())
        }
        
        @Test
        @DisplayName("forSymbol creates request from symbol")
        fun `forSymbol creates request from symbol`() {
            val symbol = SymbolContext(
                name = "calculate",
                kind = SymbolKind.METHOD,
                signature = "fun calculate(x: Int): Int",
                containingClass = "Math",
                documentation = null,
                definition = "fun calculate(x: Int): Int"
            )
            
            val request = NamingRequest.forSymbol(symbol, "kotlin")
            
            assertEquals("calculate", request.currentName)
            assertEquals(NamingConvention.CAMEL_CASE, request.convention)
        }
        
        @Test
        @DisplayName("forRename creates rename request")
        fun `forRename creates rename request`() {
            val request = NamingRequest.forRename(
                currentName = "cnt",
                type = "Int",
                context = "counting items",
                language = "java"
            )
            
            assertEquals("cnt", request.currentName)
            assertEquals("Int", request.type)
        }
    }

    @Nested
    @DisplayName("NamingConvention")
    inner class NamingConventionTests {
        
        @Test
        @DisplayName("detect returns CAMEL_CASE for kotlin")
        fun `detect returns CAMEL_CASE for kotlin`() {
            assertEquals(NamingConvention.CAMEL_CASE, NamingConvention.detect("kotlin"))
            assertEquals(NamingConvention.CAMEL_CASE, NamingConvention.detect("java"))
            assertEquals(NamingConvention.CAMEL_CASE, NamingConvention.detect("javascript"))
        }
        
        @Test
        @DisplayName("detect returns SNAKE_CASE for python")
        fun `detect returns SNAKE_CASE for python`() {
            assertEquals(NamingConvention.SNAKE_CASE, NamingConvention.detect("python"))
            assertEquals(NamingConvention.SNAKE_CASE, NamingConvention.detect("ruby"))
            assertEquals(NamingConvention.SNAKE_CASE, NamingConvention.detect("rust"))
        }
        
        @Test
        @DisplayName("detect returns PASCAL_CASE for csharp")
        fun `detect returns PASCAL_CASE for csharp`() {
            assertEquals(NamingConvention.PASCAL_CASE, NamingConvention.detect("c#"))
            assertEquals(NamingConvention.PASCAL_CASE, NamingConvention.detect("csharp"))
        }
        
        @Test
        @DisplayName("detectFromName identifies camelCase")
        fun `detectFromName identifies camelCase`() {
            assertEquals(NamingConvention.CAMEL_CASE, NamingConvention.detectFromName("userName"))
            assertEquals(NamingConvention.CAMEL_CASE, NamingConvention.detectFromName("isValid"))
        }
        
        @Test
        @DisplayName("detectFromName identifies PascalCase")
        fun `detectFromName identifies PascalCase`() {
            assertEquals(NamingConvention.PASCAL_CASE, NamingConvention.detectFromName("UserName"))
            assertEquals(NamingConvention.PASCAL_CASE, NamingConvention.detectFromName("IsValid"))
        }
        
        @Test
        @DisplayName("detectFromName identifies snake_case")
        fun `detectFromName identifies snake_case`() {
            assertEquals(NamingConvention.SNAKE_CASE, NamingConvention.detectFromName("user_name"))
        }
        
        @Test
        @DisplayName("detectFromName identifies SCREAMING_SNAKE_CASE")
        fun `detectFromName identifies SCREAMING_SNAKE_CASE`() {
            assertEquals(NamingConvention.SCREAMING_SNAKE_CASE, NamingConvention.detectFromName("MAX_VALUE"))
        }
        
        @Test
        @DisplayName("detectFromName identifies kebab-case")
        fun `detectFromName identifies kebab-case`() {
            assertEquals(NamingConvention.KEBAB_CASE, NamingConvention.detectFromName("user-name"))
        }
        
        @Test
        @DisplayName("convert transforms to camelCase")
        fun `convert transforms to camelCase`() {
            assertEquals("userName", NamingConvention.CAMEL_CASE.convert("user_name"))
            assertEquals("userName", NamingConvention.CAMEL_CASE.convert("UserName"))
        }
        
        @Test
        @DisplayName("convert transforms to PascalCase")
        fun `convert transforms to PascalCase`() {
            assertEquals("UserName", NamingConvention.PASCAL_CASE.convert("user_name"))
            assertEquals("UserName", NamingConvention.PASCAL_CASE.convert("userName"))
        }
        
        @Test
        @DisplayName("convert transforms to snake_case")
        fun `convert transforms to snake_case`() {
            assertEquals("user_name", NamingConvention.SNAKE_CASE.convert("userName"))
            assertEquals("user_name", NamingConvention.SNAKE_CASE.convert("UserName"))
        }
        
        @Test
        @DisplayName("convert transforms to SCREAMING_SNAKE_CASE")
        fun `convert transforms to SCREAMING_SNAKE_CASE`() {
            assertEquals("USER_NAME", NamingConvention.SCREAMING_SNAKE_CASE.convert("userName"))
        }
        
        @Test
        @DisplayName("splitIntoWords splits camelCase")
        fun `splitIntoWords splits camelCase`() {
            val words = NamingConvention.splitIntoWords("userName")
            assertEquals(listOf("user", "Name"), words)
        }
        
        @Test
        @DisplayName("splitIntoWords splits snake_case")
        fun `splitIntoWords splits snake_case`() {
            val words = NamingConvention.splitIntoWords("user_name")
            assertEquals(listOf("user", "name"), words)
        }
        
        @Test
        @DisplayName("matches validates convention")
        fun `matches validates convention`() {
            assertTrue(NamingConvention.CAMEL_CASE.matches("userName"))
            assertFalse(NamingConvention.CAMEL_CASE.matches("UserName"))
            assertTrue(NamingConvention.SNAKE_CASE.matches("user_name"))
        }
    }

    @Nested
    @DisplayName("NameSuggestion")
    inner class NameSuggestionTests {
        
        @Test
        @DisplayName("display formats correctly")
        fun `display formats correctly`() {
            val suggestion = NameSuggestion(
                name = "userName",
                convention = NamingConvention.CAMEL_CASE,
                rationale = "Descriptive name",
                confidence = 0.9
            )
            
            assertEquals("userName (camelCase)", suggestion.display())
        }
        
        @Test
        @DisplayName("confident creates high confidence suggestion")
        fun `confident creates high confidence suggestion`() {
            val suggestion = NameSuggestion.confident(
                name = "count",
                convention = NamingConvention.CAMEL_CASE,
                rationale = "Common pattern"
            )
            
            assertEquals(0.9, suggestion.confidence)
        }
        
        @Test
        @DisplayName("likely creates medium confidence suggestion")
        fun `likely creates medium confidence suggestion`() {
            val suggestion = NameSuggestion.likely(
                name = "items",
                convention = NamingConvention.CAMEL_CASE,
                rationale = "From context"
            )
            
            assertEquals(0.7, suggestion.confidence)
        }
    }

    @Nested
    @DisplayName("NamingResult")
    inner class NamingResultTests {
        
        @Test
        @DisplayName("success creates successful result")
        fun `success creates successful result`() {
            val suggestions = listOf(
                NameSuggestion.confident("count", NamingConvention.CAMEL_CASE, "test")
            )
            
            val result = NamingResult.success(suggestions, "Int")
            
            assertTrue(result.success)
            assertEquals(1, result.suggestions.size)
            assertEquals("Int", result.forType)
        }
        
        @Test
        @DisplayName("failure creates failed result")
        fun `failure creates failed result`() {
            val result = NamingResult.failure("No context")
            
            assertFalse(result.success)
            assertTrue(result.suggestions.isEmpty())
            assertEquals("No context", result.error)
        }
        
        @Test
        @DisplayName("bestSuggestion returns highest confidence")
        fun `bestSuggestion returns highest confidence`() {
            val suggestions = listOf(
                NameSuggestion("low", NamingConvention.CAMEL_CASE, "", 0.3),
                NameSuggestion("high", NamingConvention.CAMEL_CASE, "", 0.9),
                NameSuggestion("medium", NamingConvention.CAMEL_CASE, "", 0.6)
            )
            
            val result = NamingResult.success(suggestions)
            
            assertEquals("high", result.bestSuggestion()?.name)
        }
        
        @Test
        @DisplayName("forConvention filters by convention")
        fun `forConvention filters by convention`() {
            val suggestions = listOf(
                NameSuggestion("camelName", NamingConvention.CAMEL_CASE, "", 0.8),
                NameSuggestion("snake_name", NamingConvention.SNAKE_CASE, "", 0.7),
                NameSuggestion("anotherCamel", NamingConvention.CAMEL_CASE, "", 0.6)
            )
            
            val result = NamingResult.success(suggestions)
            val camelOnly = result.forConvention(NamingConvention.CAMEL_CASE)
            
            assertEquals(2, camelOnly.size)
        }
    }

    @Nested
    @DisplayName("NamingPatterns")
    inner class NamingPatternsTests {
        
        @Test
        @DisplayName("suggestForType returns patterns for String")
        fun `suggestForType returns patterns for String`() {
            val suggestions = NamingPatterns.suggestForType("String")
            assertTrue(suggestions.isNotEmpty())
            assertTrue("name" in suggestions)
        }
        
        @Test
        @DisplayName("suggestForType returns patterns for Int")
        fun `suggestForType returns patterns for Int`() {
            val suggestions = NamingPatterns.suggestForType("Int")
            assertTrue(suggestions.isNotEmpty())
            assertTrue("count" in suggestions)
        }
        
        @Test
        @DisplayName("suggestForType returns empty for unknown type")
        fun `suggestForType returns empty for unknown type`() {
            val suggestions = NamingPatterns.suggestForType("CustomClass")
            assertTrue(suggestions.isEmpty())
        }
        
        @Test
        @DisplayName("suggestBooleanPrefix returns appropriate prefix")
        fun `suggestBooleanPrefix returns appropriate prefix`() {
            assertEquals("has", NamingPatterns.suggestBooleanPrefix("contains items"))
            assertEquals("can", NamingPatterns.suggestBooleanPrefix("is able to edit"))
            assertEquals("is", NamingPatterns.suggestBooleanPrefix("checking validity"))
        }
        
        @Test
        @DisplayName("BOOLEAN_PREFIXES contains common prefixes")
        fun `BOOLEAN_PREFIXES contains common prefixes`() {
            assertTrue("is" in NamingPatterns.BOOLEAN_PREFIXES)
            assertTrue("has" in NamingPatterns.BOOLEAN_PREFIXES)
            assertTrue("can" in NamingPatterns.BOOLEAN_PREFIXES)
        }
    }
}
