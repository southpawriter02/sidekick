// =============================================================================
// DocGenModelsTest.kt
// =============================================================================
// Unit tests for documentation generation data models.
// =============================================================================

package com.sidekick.generation.docs

import com.sidekick.context.SymbolContext
import com.sidekick.context.SymbolKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for DocGenModels.
 */
class DocGenModelsTest {

    @Nested
    @DisplayName("DocGenRequest")
    inner class DocGenRequestTests {
        
        private fun createMethodSymbol(): SymbolContext {
            return SymbolContext(
                name = "calculate",
                kind = SymbolKind.METHOD,
                signature = "fun calculate(x: Int, y: Int): Int",
                containingClass = "Calculator",
                documentation = null,
                definition = "fun calculate(x: Int, y: Int): Int { return x + y }"
            )
        }
        
        @Test
        @DisplayName("isValid returns true for method symbol")
        fun `isValid returns true for method symbol`() {
            val request = DocGenRequest(createMethodSymbol())
            assertTrue(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns true for class symbol")
        fun `isValid returns true for class symbol`() {
            val symbol = SymbolContext(
                name = "Calculator",
                kind = SymbolKind.CLASS,
                signature = null,
                containingClass = null,
                documentation = null,
                definition = "class Calculator { }"
            )
            
            val request = DocGenRequest(symbol)
            assertTrue(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for empty symbol")
        fun `isValid returns false for empty symbol`() {
            val request = DocGenRequest(SymbolContext.EMPTY)
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("isValid returns false for unknown symbol kind")
        fun `isValid returns false for unknown symbol kind`() {
            val symbol = SymbolContext(
                name = "something",
                kind = SymbolKind.UNKNOWN,
                signature = null,
                containingClass = null,
                documentation = null,
                definition = "something"
            )
            
            val request = DocGenRequest(symbol)
            assertFalse(request.isValid())
        }
        
        @Test
        @DisplayName("getInvalidReason returns null for valid request")
        fun `getInvalidReason returns null for valid request`() {
            val request = DocGenRequest(createMethodSymbol())
            assertNull(request.getInvalidReason())
        }
        
        @Test
        @DisplayName("getInvalidReason returns reason for empty name")
        fun `getInvalidReason returns reason for empty name`() {
            val symbol = SymbolContext(
                name = "",
                kind = SymbolKind.METHOD,
                signature = null,
                containingClass = null,
                documentation = null,
                definition = ""
            )
            
            val request = DocGenRequest(symbol)
            assertEquals("No symbol name", request.getInvalidReason())
        }
        
        @Test
        @DisplayName("forSymbol creates request with detected style")
        fun `forSymbol creates request with detected style`() {
            val symbol = createMethodSymbol()
            
            val request = DocGenRequest.forSymbol(symbol, "kotlin")
            
            assertEquals(DocStyle.KDOC, request.style)
        }
        
        @Test
        @DisplayName("DOCUMENTABLE_KINDS contains expected kinds")
        fun `DOCUMENTABLE_KINDS contains expected kinds`() {
            assertTrue(SymbolKind.METHOD in DocGenRequest.DOCUMENTABLE_KINDS)
            assertTrue(SymbolKind.CLASS in DocGenRequest.DOCUMENTABLE_KINDS)
            assertTrue(SymbolKind.INTERFACE in DocGenRequest.DOCUMENTABLE_KINDS)
            assertTrue(SymbolKind.PROPERTY in DocGenRequest.DOCUMENTABLE_KINDS)
            assertFalse(SymbolKind.VARIABLE in DocGenRequest.DOCUMENTABLE_KINDS)
            assertFalse(SymbolKind.UNKNOWN in DocGenRequest.DOCUMENTABLE_KINDS)
        }
    }

    @Nested
    @DisplayName("DocStyle")
    inner class DocStyleTests {
        
        @Test
        @DisplayName("fromLanguage returns XML_DOC for csharp")
        fun `fromLanguage returns XML_DOC for csharp`() {
            assertEquals(DocStyle.XML_DOC, DocStyle.fromLanguage("csharp"))
            assertEquals(DocStyle.XML_DOC, DocStyle.fromLanguage("C#"))
            assertEquals(DocStyle.XML_DOC, DocStyle.fromLanguage("cs"))
        }
        
        @Test
        @DisplayName("fromLanguage returns KDOC for kotlin")
        fun `fromLanguage returns KDOC for kotlin`() {
            assertEquals(DocStyle.KDOC, DocStyle.fromLanguage("kotlin"))
            assertEquals(DocStyle.KDOC, DocStyle.fromLanguage("kt"))
        }
        
        @Test
        @DisplayName("fromLanguage returns JAVADOC for java")
        fun `fromLanguage returns JAVADOC for java`() {
            assertEquals(DocStyle.JAVADOC, DocStyle.fromLanguage("java"))
        }
        
        @Test
        @DisplayName("fromLanguage returns JSDOC for javascript")
        fun `fromLanguage returns JSDOC for javascript`() {
            assertEquals(DocStyle.JSDOC, DocStyle.fromLanguage("javascript"))
            assertEquals(DocStyle.JSDOC, DocStyle.fromLanguage("typescript"))
            assertEquals(DocStyle.JSDOC, DocStyle.fromLanguage("js"))
        }
        
        @Test
        @DisplayName("fromLanguage returns PYDOC for python")
        fun `fromLanguage returns PYDOC for python`() {
            assertEquals(DocStyle.PYDOC, DocStyle.fromLanguage("python"))
            assertEquals(DocStyle.PYDOC, DocStyle.fromLanguage("py"))
        }
        
        @Test
        @DisplayName("fromLanguage returns XML_DOC for unknown")
        fun `fromLanguage returns XML_DOC for unknown`() {
            assertEquals(DocStyle.XML_DOC, DocStyle.fromLanguage("unknown"))
        }
        
        @Test
        @DisplayName("allStyles excludes AUTO")
        fun `allStyles excludes AUTO`() {
            val styles = DocStyle.allStyles()
            assertFalse(DocStyle.AUTO in styles)
            assertTrue(DocStyle.XML_DOC in styles)
            assertEquals(5, styles.size)
        }
        
        @Test
        @DisplayName("displayName is human readable")
        fun `displayName is human readable`() {
            assertEquals("XML Documentation (C#)", DocStyle.XML_DOC.displayName)
            assertEquals("KDoc (Kotlin)", DocStyle.KDOC.displayName)
        }
    }

    @Nested
    @DisplayName("DocGenResult")
    inner class DocGenResultTests {
        
        @Test
        @DisplayName("success creates successful result")
        fun `success creates successful result`() {
            val result = DocGenResult.success("/// Documentation", DocStyle.XML_DOC)
            
            assertTrue(result.success)
            assertEquals("/// Documentation", result.documentation)
            assertEquals(DocStyle.XML_DOC, result.style)
            assertNull(result.error)
        }
        
        @Test
        @DisplayName("failure creates failed result")
        fun `failure creates failed result`() {
            val result = DocGenResult.failure("Error message")
            
            assertFalse(result.success)
            assertEquals("", result.documentation)
            assertEquals("Error message", result.error)
        }
        
        @Test
        @DisplayName("insertPosition defaults to BEFORE_SYMBOL")
        fun `insertPosition defaults to BEFORE_SYMBOL`() {
            val result = DocGenResult.success("docs", DocStyle.KDOC)
            assertEquals(InsertPosition.BEFORE_SYMBOL, result.insertPosition)
        }
    }

    @Nested
    @DisplayName("InsertPosition")
    inner class InsertPositionTests {
        
        @Test
        @DisplayName("has all expected values")
        fun `has all expected values`() {
            val positions = InsertPosition.entries
            
            assertTrue(positions.any { it == InsertPosition.BEFORE_SYMBOL })
            assertTrue(positions.any { it == InsertPosition.REPLACE_EXISTING })
            assertTrue(positions.any { it == InsertPosition.AFTER_SYMBOL })
            assertEquals(3, positions.size)
        }
    }
}
