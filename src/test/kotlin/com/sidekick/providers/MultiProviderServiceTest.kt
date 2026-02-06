package com.sidekick.providers

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Unit tests for MultiProviderService.
 *
 * Note: Tests for getInstance() are skipped as they require IntelliJ Platform.
 */
@DisplayName("MultiProviderService Tests")
class MultiProviderServiceTest {

    private lateinit var service: MultiProviderService

    @BeforeEach
    fun setup() {
        service = MultiProviderService()
        service.reset()
    }

    // =========================================================================
    // Provider Selection Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Selection")
    inner class ProviderSelectionTests {

        @Test
        @DisplayName("getBestProvider returns default when healthy")
        fun getBestProviderReturnsDefaultWhenHealthy() = runBlocking {
            service.setDefaultProvider("ollama")
            val best = service.getBestProvider()
            assertEquals("ollama", best)
        }

        @Test
        @DisplayName("getProvidersByPriority sorts correctly")
        fun getProvidersByPrioritySortsCorrectly() {
            val providers = service.getProvidersByPriority()

            assertTrue(providers.isNotEmpty())
            // Should be sorted by priority
            val priorities = providers.map { it.second.priority }
            assertEquals(priorities.sorted(), priorities)
        }

        @Test
        @DisplayName("getDefaultProvider returns current default")
        fun getDefaultProviderReturnsCurrentDefault() {
            assertEquals("ollama", service.getDefaultProvider())
        }

        @Test
        @DisplayName("setDefaultProvider updates default")
        fun setDefaultProviderUpdatesDefault() {
            service.setDefaultProvider("lmstudio")
            assertEquals("lmstudio", service.getDefaultProvider())
        }

        @Test
        @DisplayName("setDefaultProvider ignores unknown provider")
        fun setDefaultProviderIgnoresUnknownProvider() {
            service.setDefaultProvider("unknown")
            assertEquals("ollama", service.getDefaultProvider())
        }
    }

    // =========================================================================
    // Provider Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Management")
    inner class ProviderManagementTests {

        @Test
        @DisplayName("getProvider returns config")
        fun getProviderReturnsConfig() {
            val config = service.getProvider("ollama")

            assertNotNull(config)
            assertTrue(config is ProviderConfig.Ollama)
        }

        @Test
        @DisplayName("getProvider returns null for unknown")
        fun getProviderReturnsNullForUnknown() {
            assertNull(service.getProvider("unknown"))
        }

        @Test
        @DisplayName("getAllProviders returns all configs")
        fun getAllProvidersReturnsAllConfigs() {
            val providers = service.getAllProviders()

            assertTrue(providers.containsKey("ollama"))
            assertTrue(providers.containsKey("lmstudio"))
        }

        @Test
        @DisplayName("updateProvider modifies config")
        fun updateProviderModifiesConfig() {
            service.updateProvider("ollama", ProviderConfig.Ollama(port = 9999))

            val config = service.getProvider("ollama") as ProviderConfig.Ollama
            assertEquals(9999, config.port)
        }

        @Test
        @DisplayName("addCustomProvider creates new provider")
        fun addCustomProviderCreatesNewProvider() {
            val custom = ProviderConfig.Custom(
                enabled = true,
                name = "MyProvider",
                baseUrl = "http://example.com"
            )

            assertTrue(service.addCustomProvider("MyProvider", custom))
            assertTrue(service.getProviderIds().any { it.startsWith("custom_") })
        }

        @Test
        @DisplayName("addCustomProvider rejects duplicate")
        fun addCustomProviderRejectsDuplicate() {
            val custom = ProviderConfig.Custom(name = "Test", baseUrl = "http://test.com")

            assertTrue(service.addCustomProvider("Test", custom))
            assertFalse(service.addCustomProvider("Test", custom))
        }

        @Test
        @DisplayName("removeProvider removes custom provider")
        fun removeProviderRemovesCustomProvider() {
            val custom = ProviderConfig.Custom(name = "ToRemove", baseUrl = "http://test.com")
            service.addCustomProvider("ToRemove", custom)
            val id = service.getProviderIds().first { it.startsWith("custom_") }

            assertTrue(service.removeProvider(id))
            assertFalse(service.getProviderIds().contains(id))
        }

        @Test
        @DisplayName("removeProvider rejects built-in providers")
        fun removeProviderRejectsBuiltInProviders() {
            assertFalse(service.removeProvider("ollama"))
            assertFalse(service.removeProvider("lmstudio"))
        }

        @Test
        @DisplayName("setProviderEnabled toggles state")
        fun setProviderEnabledTogglesState() {
            service.setProviderEnabled("ollama", false)

            val config = service.getProvider("ollama")!!
            assertFalse(config.enabled)
        }
    }

    // =========================================================================
    // Health Checking Tests
    // =========================================================================

    @Nested
    @DisplayName("Health Checking")
    inner class HealthCheckingTests {

        @Test
        @DisplayName("testConnection returns result for known provider")
        fun testConnectionReturnsResultForKnownProvider() = runBlocking {
            val result = service.testConnection("ollama")

            assertTrue(result.success)
            assertNotNull(result.latencyMs)
        }

        @Test
        @DisplayName("testConnection fails for unknown provider")
        fun testConnectionFailsForUnknownProvider() = runBlocking {
            val result = service.testConnection("unknown")

            assertFalse(result.success)
            assertTrue(result.error?.contains("not found") == true)
        }

        @Test
        @DisplayName("isHealthy caches results")
        fun isHealthyCachesResults() = runBlocking {
            // First call populates cache
            val first = service.isHealthy("ollama")
            assertTrue(first)

            // Second call should use cache
            val second = service.isHealthy("ollama")
            assertTrue(second)
        }

        @Test
        @DisplayName("invalidateHealthCache clears cache")
        fun invalidateHealthCacheClearsCache() = runBlocking {
            // Populate cache
            service.isHealthy("ollama")

            // Invalidate
            service.invalidateHealthCache("ollama")

            // Next call should test again
            val result = service.isHealthy("ollama")
            assertTrue(result)
        }
    }

    // =========================================================================
    // Provider Info Tests
    // =========================================================================

    @Nested
    @DisplayName("Provider Info")
    inner class ProviderInfoTests {

        @Test
        @DisplayName("getProviderInfo returns info")
        fun getProviderInfoReturnsInfo() = runBlocking {
            val info = service.getProviderInfo("ollama")

            assertNotNull(info)
            assertEquals("ollama", info?.id)
            assertTrue(info?.healthy == true)
        }

        @Test
        @DisplayName("getProviderInfo returns null for unknown")
        fun getProviderInfoReturnsNullForUnknown() = runBlocking {
            assertNull(service.getProviderInfo("unknown"))
        }

        @Test
        @DisplayName("getAllProviderInfo returns all providers")
        fun getAllProviderInfoReturnsAllProviders() = runBlocking {
            val all = service.getAllProviderInfo()

            assertTrue(all.size >= 2)
            assertTrue(all.any { it.id == "ollama" })
            assertTrue(all.any { it.id == "lmstudio" })
        }
    }

    // =========================================================================
    // Fallback Tests
    // =========================================================================

    @Nested
    @DisplayName("Fallback")
    inner class FallbackTests {

        @Test
        @DisplayName("isFallbackEnabled returns state")
        fun isFallbackEnabledReturnsState() {
            assertTrue(service.isFallbackEnabled())
        }

        @Test
        @DisplayName("setFallbackEnabled toggles state")
        fun setFallbackEnabledTogglesState() {
            service.setFallbackEnabled(false)
            assertFalse(service.isFallbackEnabled())
        }
    }

    // =========================================================================
    // Capabilities Tests
    // =========================================================================

    @Nested
    @DisplayName("Capabilities")
    inner class CapabilitiesTests {

        @Test
        @DisplayName("getCapabilities returns correct for Ollama")
        fun getCapabilitiesReturnsCorrectForOllama() {
            val caps = service.getCapabilities(ProviderConfig.Ollama())

            assertEquals(ProviderCapabilities.BASIC, caps)
        }

        @Test
        @DisplayName("getCapabilities returns FULL for OpenAI")
        fun getCapabilitiesReturnsFullForOpenAI() {
            val caps = service.getCapabilities(ProviderConfig.OpenAI())

            assertEquals(ProviderCapabilities.FULL, caps)
        }

        @Test
        @DisplayName("getCapabilities returns custom for Anthropic")
        fun getCapabilitiesReturnsCustomForAnthropic() {
            val caps = service.getCapabilities(ProviderConfig.Anthropic())

            assertTrue(caps.supportsVision)
            assertTrue(caps.maxContextLength > 100000)
        }
    }

    // =========================================================================
    // Reporting Tests
    // =========================================================================

    @Nested
    @DisplayName("Reporting")
    inner class ReportingTests {

        @Test
        @DisplayName("getStatusReport includes providers")
        fun getStatusReportIncludesProviders() = runBlocking {
            val report = service.getStatusReport()

            assertTrue(report.contains("Provider Status Report"))
            assertTrue(report.contains("Ollama"))
            assertTrue(report.contains("LM Studio"))
        }

        @Test
        @DisplayName("getStatusReport shows default provider")
        fun getStatusReportShowsDefaultProvider() = runBlocking {
            val report = service.getStatusReport()

            assertTrue(report.contains("Default Provider"))
            assertTrue(report.contains("[DEFAULT]"))
        }
    }

    // =========================================================================
    // Reset Tests
    // =========================================================================

    @Nested
    @DisplayName("Reset")
    inner class ResetTests {

        @Test
        @DisplayName("reset restores defaults")
        fun resetRestoresDefaults() {
            // Modify state
            service.setDefaultProvider("lmstudio")
            service.setFallbackEnabled(false)
            service.addCustomProvider("Test", ProviderConfig.Custom(name = "Test", baseUrl = "http://test.com"))

            // Reset
            service.reset()

            // Verify defaults
            assertEquals("ollama", service.getDefaultProvider())
            assertTrue(service.isFallbackEnabled())
            assertEquals(2, service.getAllProviders().size)
        }
    }
}
