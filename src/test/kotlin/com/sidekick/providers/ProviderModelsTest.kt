package com.sidekick.providers

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Provider Models.
 */
@DisplayName("Provider Models Tests")
class ProviderModelsTest {

    // =========================================================================
    // ProviderConfig.Ollama Tests
    // =========================================================================

    @Nested
    @DisplayName("Ollama Config")
    inner class OllamaConfigTests {

        @Test
        @DisplayName("defaults are correct")
        fun defaultsAreCorrect() {
            val config = ProviderConfig.Ollama()

            assertTrue(config.enabled)
            assertEquals(1, config.priority)
            assertEquals("localhost", config.host)
            assertEquals(11434, config.port)
        }

        @Test
        @DisplayName("getBaseUrl returns correct URL")
        fun getBaseUrlReturnsCorrectUrl() {
            val config = ProviderConfig.Ollama(host = "myhost", port = 8080)
            assertEquals("http://myhost:8080", config.getBaseUrl())
        }

        @Test
        @DisplayName("getApiUrl returns correct URL")
        fun getApiUrlReturnsCorrectUrl() {
            val config = ProviderConfig.Ollama()
            assertEquals("http://localhost:11434/api/tags", config.getApiUrl("tags"))
        }

        @Test
        @DisplayName("validate catches invalid port")
        fun validateCatchesInvalidPort() {
            val config = ProviderConfig.Ollama(port = 99999)
            val errors = config.validate()
            assertTrue(errors.isNotEmpty())
        }

        @Test
        @DisplayName("withEnabled toggles state")
        fun withEnabledTogglesState() {
            val config = ProviderConfig.Ollama(enabled = true)
            assertFalse((config.withEnabled(false) as ProviderConfig.Ollama).enabled)
        }
    }

    // =========================================================================
    // ProviderConfig.LmStudio Tests
    // =========================================================================

    @Nested
    @DisplayName("LM Studio Config")
    inner class LmStudioConfigTests {

        @Test
        @DisplayName("defaults are correct")
        fun defaultsAreCorrect() {
            val config = ProviderConfig.LmStudio()

            assertTrue(config.enabled)
            assertEquals(2, config.priority)
            assertEquals(1234, config.port)
        }

        @Test
        @DisplayName("getBaseUrl includes v1 path")
        fun getBaseUrlIncludesV1Path() {
            val config = ProviderConfig.LmStudio()
            assertTrue(config.getBaseUrl().endsWith("/v1"))
        }
    }

    // =========================================================================
    // ProviderConfig.OpenAI Tests
    // =========================================================================

    @Nested
    @DisplayName("OpenAI Config")
    inner class OpenAIConfigTests {

        @Test
        @DisplayName("defaults are disabled")
        fun defaultsAreDisabled() {
            val config = ProviderConfig.OpenAI()

            assertFalse(config.enabled)
            assertEquals(3, config.priority)
            assertFalse(config.hasApiKey)
        }

        @Test
        @DisplayName("validate requires API key when enabled")
        fun validateRequiresApiKeyWhenEnabled() {
            val config = ProviderConfig.OpenAI(enabled = true)
            val errors = config.validate()
            assertTrue(errors.any { it.contains("API key") })
        }

        @Test
        @DisplayName("getHeaders includes authorization")
        fun getHeadersIncludesAuthorization() {
            val config = ProviderConfig.OpenAI(apiKey = "sk-test")
            val headers = config.getHeaders()
            assertTrue(headers["Authorization"]?.startsWith("Bearer") == true)
        }

        @Test
        @DisplayName("getHeaders includes organization when set")
        fun getHeadersIncludesOrganizationWhenSet() {
            val config = ProviderConfig.OpenAI(apiKey = "sk-test", organization = "org-123")
            val headers = config.getHeaders()
            assertEquals("org-123", headers["OpenAI-Organization"])
        }
    }

    // =========================================================================
    // ProviderConfig.Anthropic Tests
    // =========================================================================

    @Nested
    @DisplayName("Anthropic Config")
    inner class AnthropicConfigTests {

        @Test
        @DisplayName("defaults are disabled")
        fun defaultsAreDisabled() {
            val config = ProviderConfig.Anthropic()

            assertFalse(config.enabled)
            assertEquals(4, config.priority)
        }

        @Test
        @DisplayName("getHeaders includes required headers")
        fun getHeadersIncludesRequiredHeaders() {
            val config = ProviderConfig.Anthropic(apiKey = "key")
            val headers = config.getHeaders()

            assertEquals("key", headers["x-api-key"])
            assertTrue(headers.containsKey("anthropic-version"))
            assertEquals("application/json", headers["Content-Type"])
        }
    }

    // =========================================================================
    // ProviderConfig.AzureOpenAI Tests
    // =========================================================================

    @Nested
    @DisplayName("Azure OpenAI Config")
    inner class AzureOpenAIConfigTests {

        @Test
        @DisplayName("defaults are disabled")
        fun defaultsAreDisabled() {
            val config = ProviderConfig.AzureOpenAI()

            assertFalse(config.enabled)
            assertEquals(5, config.priority)
        }

        @Test
        @DisplayName("validate requires all fields when enabled")
        fun validateRequiresAllFieldsWhenEnabled() {
            val config = ProviderConfig.AzureOpenAI(enabled = true)
            val errors = config.validate()

            assertTrue(errors.size >= 3)
        }

        @Test
        @DisplayName("getChatCompletionsUrl includes api-version")
        fun getChatCompletionsUrlIncludesApiVersion() {
            val config = ProviderConfig.AzureOpenAI(
                endpoint = "https://myresource.openai.azure.com",
                deploymentId = "gpt-4"
            )
            val url = config.getChatCompletionsUrl()

            assertTrue(url.contains("api-version"))
            assertTrue(url.contains("gpt-4"))
        }
    }

    // =========================================================================
    // ProviderConfig.Custom Tests
    // =========================================================================

    @Nested
    @DisplayName("Custom Config")
    inner class CustomConfigTests {

        @Test
        @DisplayName("validate requires name and URL")
        fun validateRequiresNameAndUrl() {
            val config = ProviderConfig.Custom(
                enabled = true,
                name = "",
                baseUrl = ""
            )
            val errors = config.validate()
            assertEquals(2, errors.size)
        }

        @Test
        @DisplayName("getAllHeaders includes API key")
        fun getAllHeadersIncludesApiKey() {
            val config = ProviderConfig.Custom(
                name = "Test",
                baseUrl = "http://example.com",
                apiKey = "secret"
            )
            val headers = config.getAllHeaders()
            assertTrue(headers["Authorization"]?.contains("secret") == true)
        }

        @Test
        @DisplayName("getAllHeaders includes custom headers")
        fun getAllHeadersIncludesCustomHeaders() {
            val config = ProviderConfig.Custom(
                name = "Test",
                baseUrl = "http://example.com",
                headers = mapOf("X-Custom" to "value")
            )
            val headers = config.getAllHeaders()
            assertEquals("value", headers["X-Custom"])
        }
    }

    // =========================================================================
    // ProviderCapabilities Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Capabilities")
    inner class ProviderCapabilitiesTests {

        @Test
        @DisplayName("meetsRequirements checks all flags")
        fun meetsRequirementsChecksAllFlags() {
            val caps = ProviderCapabilities.FULL

            assertTrue(caps.meetsRequirements(streaming = true))
            assertTrue(caps.meetsRequirements(functionCalling = true))
            assertTrue(caps.meetsRequirements(vision = true))
            assertTrue(caps.meetsRequirements(minContext = 100000))
        }

        @Test
        @DisplayName("meetsRequirements fails on missing capability")
        fun meetsRequirementsFailsOnMissingCapability() {
            val caps = ProviderCapabilities.BASIC

            assertFalse(caps.meetsRequirements(functionCalling = true))
            assertFalse(caps.meetsRequirements(vision = true))
        }

        @Test
        @DisplayName("isFree detects local providers")
        fun isFreeDetectsLocalProviders() {
            assertTrue(ProviderCapabilities.BASIC.isFree)
            assertFalse(ProviderCapabilities.FULL.isFree)
        }

        @Test
        @DisplayName("format produces readable output")
        fun formatProducesReadableOutput() {
            val formatted = ProviderCapabilities.FULL.format()

            assertTrue(formatted.contains("streaming"))
            assertTrue(formatted.contains("context"))
        }
    }

    // =========================================================================
    // ProviderInfo Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Info")
    inner class ProviderInfoTests {

        @Test
        @DisplayName("isAvailable requires enabled and healthy")
        fun isAvailableRequiresEnabledAndHealthy() {
            val enabledHealthy = ProviderInfo(
                id = "test",
                config = ProviderConfig.Ollama(enabled = true),
                healthy = true,
                lastHealthCheck = null,
                models = emptyList(),
                capabilities = ProviderCapabilities.BASIC
            )

            val disabledHealthy = enabledHealthy.copy(
                config = ProviderConfig.Ollama(enabled = false)
            )

            val enabledUnhealthy = enabledHealthy.copy(healthy = false)

            assertTrue(enabledHealthy.isAvailable)
            assertFalse(disabledHealthy.isAvailable)
            assertFalse(enabledUnhealthy.isAvailable)
        }

        @Test
        @DisplayName("fromConfig creates with unknown health")
        fun fromConfigCreatesWithUnknownHealth() {
            val info = ProviderInfo.fromConfig("test", ProviderConfig.Ollama())

            assertFalse(info.healthy)
            assertNull(info.lastHealthCheck)
        }
    }

    // =========================================================================
    // ConnectionTestResult Tests
    // =========================================================================

    @Nested
    @DisplayName("Connection Test Result")
    inner class ConnectionTestResultTests {

        @Test
        @DisplayName("connected factory creates success")
        fun connectedFactoryCreatesSuccess() {
            val result = ConnectionTestResult.connected("OK", 100L, listOf("model1"))

            assertTrue(result.success)
            assertEquals(100L, result.latencyMs)
            assertEquals(1, result.models.size)
        }

        @Test
        @DisplayName("failed factory creates failure")
        fun failedFactoryCreatesFailure() {
            val result = ConnectionTestResult.failed("Network error")

            assertFalse(result.success)
            assertEquals("Network error", result.error)
        }

        @Test
        @DisplayName("format shows status indicator")
        fun formatShowsStatusIndicator() {
            val success = ConnectionTestResult.connected("OK")
            val failure = ConnectionTestResult.failed("Error")

            assertTrue(success.format().startsWith("✓"))
            assertTrue(failure.format().startsWith("✗"))
        }
    }
}
