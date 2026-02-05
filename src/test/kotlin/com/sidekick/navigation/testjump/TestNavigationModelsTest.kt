package com.sidekick.navigation.testjump

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

/**
 * Comprehensive unit tests for Test Navigation Models.
 *
 * Tests cover:
 * - TestMapping data class and properties
 * - TestConvention enum and name transformations
 * - TestDirectoryConfig path computations
 * - NavigationResult sealed class hierarchy
 *
 * @since 0.4.1
 */
@DisplayName("Test Navigation Models")
class TestNavigationModelsTest {

    // =========================================================================
    // TestMapping Tests
    // =========================================================================

    @Nested
    @DisplayName("TestMapping")
    inner class TestMappingTests {

        @Test
        @DisplayName("canNavigate returns true when test file exists")
        fun canNavigate_whenTestExists_returnsTrue() {
            val mapping = TestMapping(
                sourceFile = "/project/src/UserService.kt",
                testFile = "/project/test/UserServiceTests.kt",
                convention = TestConvention.TESTS,
                exists = true
            )

            assertTrue(mapping.canNavigate)
            assertFalse(mapping.canCreate)
        }

        @Test
        @DisplayName("canCreate returns true when test file path known but doesn't exist")
        fun canCreate_whenTestDoesNotExist_returnsTrue() {
            val mapping = TestMapping(
                sourceFile = "/project/src/UserService.kt",
                testFile = "/project/test/UserServiceTests.kt",
                convention = TestConvention.TESTS,
                exists = false
            )

            assertFalse(mapping.canNavigate)
            assertTrue(mapping.canCreate)
        }

        @Test
        @DisplayName("canNavigate and canCreate both false when testFile is null")
        fun bothFalse_whenTestFileNull() {
            val mapping = TestMapping(
                sourceFile = "/project/src/UserService.kt",
                testFile = null,
                convention = TestConvention.TESTS,
                exists = false
            )

            assertFalse(mapping.canNavigate)
            assertFalse(mapping.canCreate)
        }

        @Test
        @DisplayName("testFileName extracts just the file name")
        fun testFileName_extractsFileName() {
            val mapping = TestMapping(
                sourceFile = "/project/src/com/example/UserService.kt",
                testFile = "/project/test/com/example/UserServiceTests.kt",
                convention = TestConvention.TESTS,
                exists = true
            )

            assertEquals("UserServiceTests.kt", mapping.testFileName)
        }

        @Test
        @DisplayName("sourceFileName extracts just the file name")
        fun sourceFileName_extractsFileName() {
            val mapping = TestMapping(
                sourceFile = "/project/src/com/example/UserService.kt",
                testFile = null,
                convention = TestConvention.TESTS,
                exists = false
            )

            assertEquals("UserService.kt", mapping.sourceFileName)
        }

        @Test
        @DisplayName("testFileName returns null when testFile is null")
        fun testFileName_returnsNull_whenTestFileNull() {
            val mapping = TestMapping(
                sourceFile = "/project/src/UserService.kt",
                testFile = null,
                convention = TestConvention.TESTS,
                exists = false
            )

            assertNull(mapping.testFileName)
        }
    }

    // =========================================================================
    // TestConvention Tests
    // =========================================================================

    @Nested
    @DisplayName("TestConvention")
    inner class TestConventionTests {

        @ParameterizedTest(name = "{0}: {1} -> {2}")
        @DisplayName("toTestName generates correct test names")
        @CsvSource(
            "TESTS, UserService, UserServiceTests",
            "TEST, UserService, UserServiceTest",
            "SPEC, UserService, UserServiceSpec",
            "SHOULD, UserService, UserServiceShould",
            "UNDERSCORE_TEST, user_service, user_service_test"
        )
        fun toTestName_generatesCorrectName(convention: TestConvention, source: String, expected: String) {
            assertEquals(expected, convention.toTestName(source))
        }

        @ParameterizedTest(name = "{0}: {1} -> {2}")
        @DisplayName("toSourceName extracts correct source names")
        @CsvSource(
            "TESTS, UserServiceTests, UserService",
            "TEST, UserServiceTest, UserService",
            "SPEC, UserServiceSpec, UserService",
            "SHOULD, UserServiceShould, UserService",
            "UNDERSCORE_TEST, user_service_test, user_service"
        )
        fun toSourceName_extractsCorrectName(convention: TestConvention, test: String, expected: String) {
            assertEquals(expected, convention.toSourceName(test))
        }

        @ParameterizedTest
        @DisplayName("toSourceName returns null for non-matching names")
        @EnumSource(TestConvention::class)
        fun toSourceName_returnsNull_whenNoMatch(convention: TestConvention) {
            assertNull(convention.toSourceName("RegularClassName"))
        }

        @Test
        @DisplayName("detect identifies TESTS convention")
        fun detect_identifiesTestsConvention() {
            assertEquals(TestConvention.TESTS, TestConvention.detect("UserServiceTests"))
        }

        @Test
        @DisplayName("detect identifies TEST convention")
        fun detect_identifiesTestConvention() {
            assertEquals(TestConvention.TEST, TestConvention.detect("UserServiceTest"))
        }

        @Test
        @DisplayName("detect identifies SPEC convention")
        fun detect_identifiesSpecConvention() {
            assertEquals(TestConvention.SPEC, TestConvention.detect("UserServiceSpec"))
        }

        @Test
        @DisplayName("detect identifies SHOULD convention")
        fun detect_identifiesShouldConvention() {
            assertEquals(TestConvention.SHOULD, TestConvention.detect("UserServiceShould"))
        }

        @Test
        @DisplayName("detect identifies UNDERSCORE_TEST convention")
        fun detect_identifiesUnderscoreTestConvention() {
            assertEquals(TestConvention.UNDERSCORE_TEST, TestConvention.detect("user_service_test"))
        }

        @Test
        @DisplayName("detect returns null for non-test names")
        fun detect_returnsNull_forNonTestNames() {
            assertNull(TestConvention.detect("UserService"))
            assertNull(TestConvention.detect("MainApplication"))
        }

        @Test
        @DisplayName("detect prefers longer suffix (Tests over Test)")
        fun detect_prefersLongerSuffix() {
            // "UserServiceTests" ends with both "Tests" and "Test"
            // Should prefer "Tests" (longer)
            assertEquals(TestConvention.TESTS, TestConvention.detect("UserServiceTests"))
        }

        @Test
        @DisplayName("matches correctly identifies matching file names")
        fun matches_identifiesMatchingNames() {
            assertTrue(TestConvention.TESTS.matches("UserServiceTests"))
            assertFalse(TestConvention.TESTS.matches("UserServiceTest"))
            assertTrue(TestConvention.TEST.matches("UserServiceTest"))
        }

        @Test
        @DisplayName("DEFAULT is TESTS convention")
        fun default_isTestsConvention() {
            assertEquals(TestConvention.TESTS, TestConvention.DEFAULT)
        }

        @Test
        @DisplayName("ALL_SUFFIXES contains all convention suffixes")
        fun allSuffixes_containsAllSuffixes() {
            val expected = listOf("Tests", "Test", "Spec", "Should", "_test")
            assertEquals(expected.sorted(), TestConvention.ALL_SUFFIXES.sorted())
        }
    }

    // =========================================================================
    // TestDirectoryConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("TestDirectoryConfig")
    inner class TestDirectoryConfigTests {

        @Test
        @DisplayName("KOTLIN_GRADLE preset has correct values")
        fun kotlinGradlePreset_hasCorrectValues() {
            val config = TestDirectoryConfig.KOTLIN_GRADLE

            assertEquals("src/main/kotlin", config.sourceRoot)
            assertEquals("src/test/kotlin", config.testRoot)
            assertEquals(TestConvention.TEST, config.convention)
            assertTrue(config.mirrorPackageStructure)
        }

        @Test
        @DisplayName("DOTNET preset has correct values")
        fun dotnetPreset_hasCorrectValues() {
            val config = TestDirectoryConfig.DOTNET

            assertEquals("src", config.sourceRoot)
            assertEquals("tests", config.testRoot)
            assertEquals(TestConvention.TESTS, config.convention)
        }

        @Test
        @DisplayName("PYTHON preset has correct values")
        fun pythonPreset_hasCorrectValues() {
            val config = TestDirectoryConfig.PYTHON

            assertEquals("src", config.sourceRoot)
            assertEquals("tests", config.testRoot)
            assertEquals(TestConvention.UNDERSCORE_TEST, config.convention)
        }

        @Test
        @DisplayName("computeTestPath generates correct path for Kotlin file")
        fun computeTestPath_generatesCorrectPath_forKotlin() {
            val config = TestDirectoryConfig.KOTLIN_GRADLE
            val sourcePath = "/project/src/main/kotlin/com/example/UserService.kt"

            val testPath = config.computeTestPath(sourcePath)

            assertNotNull(testPath)
            assertTrue(testPath!!.contains("src/test/kotlin"))
            assertTrue(testPath.contains("UserServiceTest"))
        }

        @Test
        @DisplayName("computeTestPath returns null for path outside source root")
        fun computeTestPath_returnsNull_forPathOutsideSourceRoot() {
            val config = TestDirectoryConfig.KOTLIN_GRADLE
            val sourcePath = "/other/location/UserService.kt"

            val testPath = config.computeTestPath(sourcePath)

            assertNull(testPath)
        }

        @Test
        @DisplayName("computeSourcePath generates correct path for test file")
        fun computeSourcePath_generatesCorrectPath() {
            val config = TestDirectoryConfig.KOTLIN_GRADLE
            val testPath = "/project/src/test/kotlin/com/example/UserServiceTest.kt"

            val sourcePath = config.computeSourcePath(testPath)

            assertNotNull(sourcePath)
            assertTrue(sourcePath!!.contains("src/main/kotlin"))
            assertTrue(sourcePath.contains("UserService"))
        }

        @Test
        @DisplayName("computeSourcePath returns null for path outside test root")
        fun computeSourcePath_returnsNull_forPathOutsideTestRoot() {
            val config = TestDirectoryConfig.KOTLIN_GRADLE
            val testPath = "/other/location/UserServiceTest.kt"

            val sourcePath = config.computeSourcePath(testPath)

            assertNull(sourcePath)
        }
    }

    // =========================================================================
    // NavigationResult Tests
    // =========================================================================

    @Nested
    @DisplayName("NavigationResult")
    inner class NavigationResultTests {

        @Test
        @DisplayName("Found has isSuccess true")
        fun found_hasIsSuccessTrue() {
            val result = NavigationResult.Found("/path/to/file.kt")

            assertTrue(result.isSuccess)
            assertFalse(result.canCreate)
            assertEquals("/path/to/file.kt", result.pathOrNull)
        }

        @Test
        @DisplayName("NotFound has canCreate true")
        fun notFound_hasCanCreateTrue() {
            val result = NavigationResult.NotFound("/suggested/path.kt")

            assertFalse(result.isSuccess)
            assertTrue(result.canCreate)
            assertNull(result.pathOrNull)
        }

        @Test
        @DisplayName("Multiple has count property")
        fun multiple_hasCountProperty() {
            val options = listOf("/path/one.kt", "/path/two.kt", "/path/three.kt")
            val result = NavigationResult.Multiple(options)

            assertEquals(3, result.count)
            assertFalse(result.isSuccess)
            assertFalse(result.canCreate)
        }

        @Test
        @DisplayName("Error has descriptive message")
        fun error_hasDescriptiveMessage() {
            val result = NavigationResult.Error("Something went wrong")

            assertFalse(result.isSuccess)
            assertFalse(result.canCreate)
            assertEquals("Something went wrong", result.message)
        }

        @Test
        @DisplayName("toString provides helpful debugging output")
        fun toString_providesHelpfulOutput() {
            assertEquals("Found: /path/to/file.kt", NavigationResult.Found("/path/to/file.kt").toString())
            assertEquals("NotFound: suggested=/suggested/path.kt", NavigationResult.NotFound("/suggested/path.kt").toString())
            assertEquals("Multiple: 2 options", NavigationResult.Multiple(listOf("a", "b")).toString())
            assertEquals("Error: oops", NavigationResult.Error("oops").toString())
        }

        @Test
        @DisplayName("Pattern matching works correctly")
        fun patternMatching_worksCorrectly() {
            val results = listOf(
                NavigationResult.Found("/found.kt"),
                NavigationResult.NotFound("/suggested.kt"),
                NavigationResult.Multiple(listOf("/a.kt", "/b.kt")),
                NavigationResult.Error("error")
            )

            val messages = results.map { result ->
                when (result) {
                    is NavigationResult.Found -> "found: ${result.path}"
                    is NavigationResult.NotFound -> "not found, suggest: ${result.suggestedPath}"
                    is NavigationResult.Multiple -> "multiple: ${result.count}"
                    is NavigationResult.Error -> "error: ${result.message}"
                }
            }

            assertEquals(4, messages.size)
            assertTrue(messages[0].startsWith("found:"))
            assertTrue(messages[1].startsWith("not found"))
            assertTrue(messages[2].startsWith("multiple:"))
            assertTrue(messages[3].startsWith("error:"))
        }
    }

    // =========================================================================
    // Edge Cases and Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Empty source name still generates valid test name")
        fun emptySourceName_generatesValidTestName() {
            // Edge case: empty string
            val testName = TestConvention.TESTS.toTestName("")
            assertEquals("Tests", testName)
        }

        @Test
        @DisplayName("Source name with special characters handled correctly")
        fun specialCharacters_handledCorrectly() {
            val testName = TestConvention.TESTS.toTestName("User_Service_Impl")
            assertEquals("User_Service_ImplTests", testName)
        }

        @Test
        @DisplayName("Very long file names handled correctly")
        fun longFileNames_handledCorrectly() {
            val longName = "VeryLongClassNameThatExceedsNormalNamingConventions"
            val testName = TestConvention.TESTS.toTestName(longName)
            assertEquals("${longName}Tests", testName)
        }

        @Test
        @DisplayName("Round-trip conversion preserves name")
        fun roundTrip_preservesName() {
            val original = "UserService"
            val testName = TestConvention.TESTS.toTestName(original)
            val recovered = TestConvention.TESTS.toSourceName(testName)

            assertEquals(original, recovered)
        }

        @Test
        @DisplayName("Multiple suffix detection handles similar suffixes")
        fun multipleSuffixes_handledCorrectly() {
            // "Tests" contains "Test" - ensure correct detection
            val detected = TestConvention.detect("ServiceTests")
            assertEquals(TestConvention.TESTS, detected)

            // Explicitly ends with "Test" not "Tests"
            val detected2 = TestConvention.detect("ServiceTest")
            assertEquals(TestConvention.TEST, detected2)
        }
    }
}
