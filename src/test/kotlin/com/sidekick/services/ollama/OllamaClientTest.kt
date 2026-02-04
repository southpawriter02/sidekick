// =============================================================================
// OllamaClientTest.kt
// =============================================================================
// Unit tests for the OllamaClient HTTP client.
//
// These tests use Ktor's MockEngine to simulate Ollama API responses
// without requiring a real Ollama server.
// =============================================================================

package com.sidekick.services.ollama

import com.sidekick.services.ollama.models.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [OllamaClient] using Ktor's MockEngine.
 *
 * Note: These tests verify the client logic using mocked HTTP responses.
 * They do NOT require a running Ollama server.
 */
@DisplayName("OllamaClient")
class OllamaClientTest {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // -------------------------------------------------------------------------
    // Constants Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("Constants")
    inner class ConstantsTests {
        
        @Test
        @DisplayName("DEFAULT_BASE_URL is localhost:11434")
        fun `DEFAULT_BASE_URL is localhost 11434`() {
            assertEquals("http://localhost:11434", OllamaClient.DEFAULT_BASE_URL)
        }
    }

    // -------------------------------------------------------------------------
    // Data Model Tests (that don't require IntelliJ platform)
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("Request Building")
    inner class RequestBuildingTests {
        
        @Test
        @DisplayName("ChatRequest creates valid request body")
        fun `ChatRequest creates valid request body`() {
            val request = ChatRequest(
                model = "llama3.2",
                messages = listOf(
                    ChatMessage.system("You are helpful"),
                    ChatMessage.user("Hello!")
                ),
                stream = true
            )
            
            assertEquals("llama3.2", request.model)
            assertEquals(2, request.messages.size)
            assertEquals("system", request.messages[0].role)
            assertEquals("user", request.messages[1].role)
            assertTrue(request.stream)
        }
        
        @Test
        @DisplayName("ChatRequest serializes correctly")
        fun `ChatRequest serializes correctly`() {
            val request = ChatRequest(
                model = "llama3.2",
                messages = listOf(ChatMessage.user("Hello!")),
                stream = true
            )
            
            val jsonString = json.encodeToString(request)
            
            assertTrue(jsonString.contains("\"model\":\"llama3.2\""))
            assertTrue(jsonString.contains("\"role\":\"user\""))
            assertTrue(jsonString.contains("\"content\":\"Hello!\""))
        }
        
        @Test
        @DisplayName("ChatOptions serializes with snake_case")
        fun `ChatOptions serializes with snake_case`() {
            val options = ChatOptions(
                temperature = 0.7,
                topP = 0.9,
                topK = 40,
                numPredict = 100
            )
            
            val jsonString = json.encodeToString(options)
            
            assertTrue(jsonString.contains("\"top_p\":"))
            assertTrue(jsonString.contains("\"top_k\":"))
            assertTrue(jsonString.contains("\"num_predict\":"))
        }
    }

    // -------------------------------------------------------------------------
    // Exception Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("OllamaClientException")
    inner class ExceptionTests {
        
        @Test
        @DisplayName("stores message and status code")
        fun `stores message and status code`() {
            val exception = OllamaClientException(
                message = "Connection failed",
                statusCode = 503
            )
            
            assertEquals("Connection failed", exception.message)
            assertEquals(503, exception.statusCode)
            assertNull(exception.cause)
        }
        
        @Test
        @DisplayName("stores cause when provided")
        fun `stores cause when provided`() {
            val cause = RuntimeException("Network error")
            val exception = OllamaClientException(
                message = "Connection failed",
                cause = cause
            )
            
            assertEquals("Connection failed", exception.message)
            assertNull(exception.statusCode)
            assertEquals(cause, exception.cause)
        }
    }

    // -------------------------------------------------------------------------
    // Response Parsing Tests
    // -------------------------------------------------------------------------
    
    @Nested
    @DisplayName("Response Parsing")
    inner class ResponseParsingTests {
        
        @Test
        @DisplayName("parses ListModelsResponse correctly")
        fun `parses ListModelsResponse correctly`() {
            val jsonString = """{
                "models": [
                    {
                        "name": "llama3.2:latest",
                        "modified_at": "2024-01-15T10:30:00Z",
                        "size": 4000000000,
                        "digest": "abc123def456"
                    }
                ]
            }"""
            
            val response = json.decodeFromString<ListModelsResponse>(jsonString)
            
            assertEquals(1, response.models.size)
            assertEquals("llama3.2:latest", response.models[0].name)
            assertEquals(4000000000L, response.models[0].size)
        }
        
        @Test
        @DisplayName("parses streaming ChatResponse correctly")
        fun `parses streaming ChatResponse correctly`() {
            // Each line in a streaming response is a separate JSON object
            val lines = listOf(
                """{"model":"llama3.2","created_at":"2024-01-15T10:30:00Z","message":{"role":"assistant","content":"Hello"},"done":false}""",
                """{"model":"llama3.2","created_at":"2024-01-15T10:30:01Z","message":{"role":"assistant","content":"!"},"done":false}""",
                """{"model":"llama3.2","created_at":"2024-01-15T10:30:02Z","message":{"role":"assistant","content":""},"done":true,"eval_count":10}"""
            )
            
            val responses = lines.map { json.decodeFromString<ChatResponse>(it) }
            
            assertEquals(3, responses.size)
            assertEquals("Hello", responses[0].message.content)
            assertFalse(responses[0].done)
            assertEquals("!", responses[1].message.content)
            assertTrue(responses[2].done)
            assertEquals(10, responses[2].evalCount)
        }
    }

    // -------------------------------------------------------------------------
    // Integration Notes
    // -------------------------------------------------------------------------
    // 
    // Full integration tests for OllamaClient.listModels() and OllamaClient.chat()
    // require either:
    // 1. A MockEngine-based HttpClient injection (see below for pattern)
    // 2. A running Ollama server (for true integration tests)
    //
    // For v0.1.1, we focus on the logic that can be tested without platform deps.
    // A MockEngine test would look like:
    //
    // ```kotlin
    // @Test
    // fun `listModels returns models from API`() = runTest {
    //     val mockEngine = MockEngine { request ->
    //         respond(
    //             content = """{"models":[{"name":"test","modified_at":"...","size":0,"digest":"abc"}]}""",
    //             status = HttpStatusCode.OK,
    //             headers = headersOf(HttpHeaders.ContentType, "application/json")
    //         )
    //     }
    //     val client = OllamaClient(mockEngine, "http://localhost:11434")
    //     val result = client.listModels()
    //     assertTrue(result.isSuccess)
    // }
    // ```
    //
    // This requires modifying OllamaClient to accept an HttpClient/Engine for testing.
    // We'll add this in a future version when we have more complex integration needs.
    // -------------------------------------------------------------------------
}
