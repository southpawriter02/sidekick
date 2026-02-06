package com.sidekick.performance

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import com.intellij.openapi.project.Project

/**
 * Unit tests for LazyInitializer.
 *
 * Note: Tests that require ProgressManager are excluded as they need
 * IntelliJ Platform test infrastructure. Core logic is tested here.
 */
@DisplayName("LazyInitializer Tests")
class LazyInitializerTest {

    private lateinit var mockProject: Project
    private lateinit var initializer: LazyInitializer

    @BeforeEach
    fun setup() {
        mockProject = mockk(relaxed = true)
        initializer = LazyInitializer(mockProject)
    }

    // =========================================================================
    // Lazy Get Tests
    // =========================================================================

    @Nested
    @DisplayName("lazyGet")
    inner class LazyGetTests {

        @Test
        @DisplayName("marks service as initialized on first call")
        fun marksServiceAsInitializedOnFirstCall() {
            assertFalse(initializer.isInitialized("TestService"))

            initializer.lazyGet("TestService") { "value" }

            assertTrue(initializer.isInitialized("TestService"))
        }

        @Test
        @DisplayName("returns initializer result")
        fun returnsInitializerResult() {
            val result = initializer.lazyGet("TestService") { "test-value" }

            assertEquals("test-value", result)
        }

        @Test
        @DisplayName("calls initializer every time for value")
        fun callsInitializerEveryTimeForValue() {
            var callCount = 0

            initializer.lazyGet("TestService") { callCount++; "value1" }
            initializer.lazyGet("TestService") { callCount++; "value2" }
            initializer.lazyGet("TestService") { callCount++; "value3" }

            // The initializer lambda is called each time to get the value
            // but initialization tracking only happens once
            assertEquals(3, callCount)
        }

        @Test
        @DisplayName("tracks multiple services independently")
        fun tracksMultipleServicesIndependently() {
            initializer.lazyGet("ServiceA") { "a" }

            assertTrue(initializer.isInitialized("ServiceA"))
            assertFalse(initializer.isInitialized("ServiceB"))

            initializer.lazyGet("ServiceB") { "b" }

            assertTrue(initializer.isInitialized("ServiceA"))
            assertTrue(initializer.isInitialized("ServiceB"))
        }

        @Test
        @DisplayName("works with different return types")
        fun worksWithDifferentReturnTypes() {
            val stringVal = initializer.lazyGet("StringService") { "hello" }
            val intVal = initializer.lazyGet("IntService") { 42 }
            val listVal = initializer.lazyGet("ListService") { listOf(1, 2, 3) }

            assertEquals("hello", stringVal)
            assertEquals(42, intVal)
            assertEquals(listOf(1, 2, 3), listVal)
        }
    }

    // =========================================================================
    // Initialization Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("Initialization Tracking")
    inner class InitializationTrackingTests {

        @Test
        @DisplayName("isInitialized returns false for unknown service")
        fun isInitializedReturnsFalseForUnknownService() {
            assertFalse(initializer.isInitialized("NonExistent"))
        }

        @Test
        @DisplayName("getInitializedServices returns empty set initially")
        fun getInitializedServicesReturnsEmptySetInitially() {
            assertTrue(initializer.getInitializedServices().isEmpty())
        }

        @Test
        @DisplayName("getInitializedServices returns all initialized services")
        fun getInitializedServicesReturnsAllInitializedServices() {
            initializer.lazyGet("ServiceA") { "a" }
            initializer.lazyGet("ServiceB") { "b" }
            initializer.lazyGet("ServiceC") { "c" }

            val services = initializer.getInitializedServices()

            assertEquals(3, services.size)
            assertTrue(services.contains("ServiceA"))
            assertTrue(services.contains("ServiceB"))
            assertTrue(services.contains("ServiceC"))
        }
    }

    // =========================================================================
    // Custom Initializer Registration Tests
    // =========================================================================

    @Nested
    @DisplayName("Custom Initializers")
    inner class CustomInitializerTests {

        @Test
        @DisplayName("registerInitializer stores callback")
        fun registerInitializerStoresCallback() {
            var called = false
            initializer.registerInitializer("CustomService") { called = true }

            // Registration alone doesn't call it
            assertFalse(called)
        }
    }

    // =========================================================================
    // Reset Tests
    // =========================================================================

    @Nested
    @DisplayName("Reset")
    inner class ResetTests {

        @Test
        @DisplayName("reset clears all initialization state")
        fun resetClearsAllInitializationState() {
            initializer.lazyGet("ServiceA") { "a" }
            initializer.lazyGet("ServiceB") { "b" }

            assertTrue(initializer.isInitialized("ServiceA"))
            assertTrue(initializer.isInitialized("ServiceB"))

            initializer.reset()

            assertFalse(initializer.isInitialized("ServiceA"))
            assertFalse(initializer.isInitialized("ServiceB"))
            assertTrue(initializer.getInitializedServices().isEmpty())
        }
    }

    // =========================================================================
    // Status Summary Tests
    // =========================================================================

    @Nested
    @DisplayName("Status Summary")
    inner class StatusSummaryTests {

        @Test
        @DisplayName("getStatusSummary includes header")
        fun getStatusSummaryIncludesHeader() {
            val summary = initializer.getStatusSummary()

            assertTrue(summary.contains("LazyInitializer Status"))
        }

        @Test
        @DisplayName("getStatusSummary lists initialized services")
        fun getStatusSummaryListsInitializedServices() {
            initializer.lazyGet("MyService") { "value" }

            val summary = initializer.getStatusSummary()

            assertTrue(summary.contains("MyService"))
            assertTrue(summary.contains("Initialized services: 1"))
        }

        @Test
        @DisplayName("getStatusSummary shows pre-warm status")
        fun getStatusSummaryShowsPreWarmStatus() {
            val summary = initializer.getStatusSummary()

            assertTrue(summary.contains("Pre-warm started: false"))
        }
    }
}
