// =============================================================================
// OllamaModelsTest.kt
// =============================================================================
// Unit tests for Ollama API data models.
//
// These tests verify:
// - JSON serialization/deserialization works correctly
// - Factory methods create valid instances
// - Model properties are correctly mapped
// =============================================================================

package com.sidekick.services.ollama.models

import com.sidekick.models.ConnectionStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for Ollama API data models.
 */
@DisplayName("Ollama Models")
class OllamaModelsTest {
    
    // JSON configuration matching OllamaClient
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // -------------------------------------------------------------------------
    // ChatMessage Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("ChatMessage")
    inner class ChatMessageTests {
        
        @Test
        @DisplayName("system() creates message with correct role")
        fun `system creates message with correct role`() {
            val message = ChatMessage.system("You are a helpful assistant")
            
            assertEquals("system", message.role)
            assertEquals("You are a helpful assistant", message.content)
        }
        
        @Test
        @DisplayName("user() creates message with correct role")
        fun `user creates message with correct role`() {
            val message = ChatMessage.user("Hello!")
            
            assertEquals("user", message.role)
            assertEquals("Hello!", message.content)
        }
        
        @Test
        @DisplayName("assistant() creates message with correct role")
        fun `assistant creates message with correct role`() {
            val message = ChatMessage.assistant("Hi there!")
            
            assertEquals("assistant", message.role)
            assertEquals("Hi there!", message.content)
        }
        
        @Test
        @DisplayName("serializes to JSON correctly")
        fun `serializes to JSON correctly`() {
            val message = ChatMessage.user("Hello!")
            val jsonString = json.encodeToString(message)
            
            assertTrue(jsonString.contains("\"role\":\"user\""))
            assertTrue(jsonString.contains("\"content\":\"Hello!\""))
        }
        
        @Test
        @DisplayName("deserializes from JSON correctly")
        fun `deserializes from JSON correctly`() {
            val jsonString = """{"role":"assistant","content":"Hi there!"}"""
            val message = json.decodeFromString<ChatMessage>(jsonString)
            
            assertEquals("assistant", message.role)
            assertEquals("Hi there!", message.content)
        }
    }

    // -------------------------------------------------------------------------
    // ChatRequest Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("ChatRequest")
    inner class ChatRequestTests {
        
        @Test
        @DisplayName("creates request with default stream=true")
        fun `creates request with default stream true`() {
            val request = ChatRequest(
                model = "llama3.2",
                messages = listOf(ChatMessage.user("Hello"))
            )
            
            assertTrue(request.stream)
        }
        
        @Test
        @DisplayName("serializes with all required fields")
        fun `serializes with all required fields`() {
            val request = ChatRequest(
                model = "llama3.2",
                messages = listOf(
                    ChatMessage.system("Be helpful"),
                    ChatMessage.user("Hello")
                )
            )
            
            val jsonString = json.encodeToString(request)
            
            // Note: with encodeDefaults=false, stream=true (the default) is not encoded
            assertTrue(jsonString.contains("\"model\":\"llama3.2\""))
            assertTrue(jsonString.contains("\"messages\""))
            assertTrue(jsonString.contains("\"role\":\"system\""))
            assertTrue(jsonString.contains("\"role\":\"user\""))
        }
        
        @Test
        @DisplayName("includes options when specified")
        fun `includes options when specified`() {
            val request = ChatRequest(
                model = "llama3.2",
                messages = listOf(ChatMessage.user("Hello")),
                options = ChatOptions(
                    temperature = 0.7,
                    topP = 0.9,
                    numPredict = 100
                )
            )
            
            val jsonString = json.encodeToString(request)
            
            assertTrue(jsonString.contains("\"temperature\":0.7"))
            assertTrue(jsonString.contains("\"top_p\":0.9"))
            assertTrue(jsonString.contains("\"num_predict\":100"))
        }
    }

    // -------------------------------------------------------------------------
    // ChatResponse Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("ChatResponse")
    inner class ChatResponseTests {
        
        @Test
        @DisplayName("deserializes streaming response correctly")
        fun `deserializes streaming response correctly`() {
            val jsonString = """{
                "model": "llama3.2",
                "created_at": "2024-01-15T10:30:00Z",
                "message": {"role": "assistant", "content": "Hi"},
                "done": false
            }"""
            
            val response = json.decodeFromString<ChatResponse>(jsonString)
            
            assertEquals("llama3.2", response.model)
            assertEquals("Hi", response.message.content)
            assertFalse(response.done)
            assertNull(response.evalCount)
        }
        
        @Test
        @DisplayName("deserializes final response with stats")
        fun `deserializes final response with stats`() {
            val jsonString = """{
                "model": "llama3.2",
                "created_at": "2024-01-15T10:30:00Z",
                "message": {"role": "assistant", "content": ""},
                "done": true,
                "total_duration": 5000000000,
                "eval_count": 42,
                "eval_duration": 4000000000
            }"""
            
            val response = json.decodeFromString<ChatResponse>(jsonString)
            
            assertTrue(response.done)
            assertEquals(5000000000L, response.totalDuration)
            assertEquals(42, response.evalCount)
            assertEquals(4000000000L, response.evalDuration)
        }
        
        @Test
        @DisplayName("ignores unknown fields")
        fun `ignores unknown fields`() {
            val jsonString = """{
                "model": "llama3.2",
                "created_at": "2024-01-15T10:30:00Z",
                "message": {"role": "assistant", "content": "Hi"},
                "done": false,
                "unknown_field": "should be ignored"
            }"""
            
            // Should not throw
            val response = json.decodeFromString<ChatResponse>(jsonString)
            assertEquals("llama3.2", response.model)
        }
    }

    // -------------------------------------------------------------------------
    // OllamaModel Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("OllamaModel")
    inner class OllamaModelTests {
        
        @Test
        @DisplayName("deserializes model list response")
        fun `deserializes model list response`() {
            val jsonString = """{
                "models": [
                    {
                        "name": "llama3.2:latest",
                        "modified_at": "2024-01-15T10:30:00Z",
                        "size": 4000000000,
                        "digest": "abc123"
                    },
                    {
                        "name": "codellama:7b",
                        "modified_at": "2024-01-14T09:00:00Z",
                        "size": 3500000000,
                        "digest": "def456"
                    }
                ]
            }"""
            
            val response = json.decodeFromString<ListModelsResponse>(jsonString)
            
            assertEquals(2, response.models.size)
            assertEquals("llama3.2:latest", response.models[0].name)
            assertEquals(4000000000L, response.models[0].size)
        }
        
        @Test
        @DisplayName("handles model with details")
        fun `handles model with details`() {
            val jsonString = """{
                "name": "llama3.2:latest",
                "modified_at": "2024-01-15T10:30:00Z",
                "size": 4000000000,
                "digest": "abc123",
                "details": {
                    "format": "gguf",
                    "family": "llama",
                    "parameter_size": "7B",
                    "quantization_level": "Q4_0"
                }
            }"""
            
            val model = json.decodeFromString<OllamaModel>(jsonString)
            
            assertNotNull(model.details)
            assertEquals("gguf", model.details?.format)
            assertEquals("llama", model.details?.family)
            assertEquals("7B", model.details?.parameterSize)
            assertEquals("Q4_0", model.details?.quantizationLevel)
        }
    }

    // -------------------------------------------------------------------------
    // ConnectionStatus Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("ConnectionStatus")
    inner class ConnectionStatusTests {
        
        @Test
        @DisplayName("has all expected values")
        fun `has all expected values`() {
            val values = ConnectionStatus.entries
            
            assertTrue(values.contains(ConnectionStatus.NOT_CONFIGURED))
            assertTrue(values.contains(ConnectionStatus.CONNECTING))
            assertTrue(values.contains(ConnectionStatus.CONNECTED))
            assertTrue(values.contains(ConnectionStatus.DISCONNECTED))
            assertEquals(4, values.size)
        }
    }
}
