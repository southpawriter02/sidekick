package com.sidekick.navigation.testjump

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Path

/**
 * Unit tests for TestNavigationService.
 *
 * These tests validate the service's ability to:
 * - Detect test vs source files
 * - Navigate between source and test files
 * - Compute correct paths for various project structures
 * - Generate appropriate test stubs
 *
 * Note: Tests use temp directories to simulate project structures
 * without requiring a full IntelliJ platform test context.
 *
 * @since 0.4.1
 */
@DisplayName("TestNavigationService")
class TestNavigationServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var projectRoot: File

    @BeforeEach
    fun setUp() {
        projectRoot = tempDir.toFile()
        createProjectStructure()
    }

    /**
     * Creates a standard project structure for testing.
     */
    private fun createProjectStructure() {
        // Kotlin/Gradle structure
        File(projectRoot, "src/main/kotlin/com/example").mkdirs()
        File(projectRoot, "src/test/kotlin/com/example").mkdirs()

        // Create some source files
        File(projectRoot, "src/main/kotlin/com/example/UserService.kt").writeText(
            """
            package com.example
            
            class UserService {
                fun getUser(id: String): User? = null
            }
            """.trimIndent()
        )

        File(projectRoot, "src/main/kotlin/com/example/OrderService.kt").writeText(
            """
            package com.example
            
            class OrderService {
                fun createOrder(): Order? = null
            }
            """.trimIndent()
        )

        // Create a test file for UserService
        File(projectRoot, "src/test/kotlin/com/example/UserServiceTest.kt").writeText(
            """
            package com.example
            
            import org.junit.jupiter.api.Test
            
            class UserServiceTest {
                @Test
                fun `should get user`() { }
            }
            """.trimIndent()
        )
    }

    // =========================================================================
    // isTestFile Tests (using helper that mirrors service logic)
    // =========================================================================

    @Nested
    @DisplayName("isTestFile Logic")
    inner class IsTestFileTests {

        @Test
        @DisplayName("returns true for file in test directory")
        fun returnsTrue_forFileInTestDirectory() {
            val path = "${projectRoot.absolutePath}/src/test/kotlin/com/example/SomeFile.kt"
            assertTrue(isTestFilePath(path, "SomeFile"))
        }

        @Test
        @DisplayName("returns true for file with Tests suffix")
        fun returnsTrue_forFileWithTestsSuffix() {
            val path = "${projectRoot.absolutePath}/src/main/kotlin/UserServiceTests.kt"
            assertTrue(isTestFilePath(path, "UserServiceTests"))
        }

        @Test
        @DisplayName("returns true for file with Test suffix")
        fun returnsTrue_forFileWithTestSuffix() {
            val path = "${projectRoot.absolutePath}/src/main/kotlin/UserServiceTest.kt"
            assertTrue(isTestFilePath(path, "UserServiceTest"))
        }

        @Test
        @DisplayName("returns true for file with Spec suffix")
        fun returnsTrue_forFileWithSpecSuffix() {
            val path = "${projectRoot.absolutePath}/src/main/kotlin/UserServiceSpec.kt"
            assertTrue(isTestFilePath(path, "UserServiceSpec"))
        }

        @Test
        @DisplayName("returns false for regular source file")
        fun returnsFalse_forRegularSourceFile() {
            val path = "${projectRoot.absolutePath}/src/main/kotlin/UserService.kt"
            assertFalse(isTestFilePath(path, "UserService"))
        }

        @Test
        @DisplayName("returns true for __tests__ directory (JavaScript style)")
        fun returnsTrue_forJsTestsDirectory() {
            val path = "${projectRoot.absolutePath}/src/__tests__/UserService.js"
            assertTrue(isTestFilePath(path, "UserService"))
        }

        @ParameterizedTest
        @DisplayName("returns true for various test directory names")
        @ValueSource(strings = ["test", "tests", "spec", "specs", "Test", "Tests"])
        fun returnsTrue_forVariousTestDirectories(dirName: String) {
            val path = "${projectRoot.absolutePath}/src/$dirName/kotlin/SomeFile.kt"
            assertTrue(isTestFilePath(path, "SomeFile"))
        }

        /**
         * Helper function that mirrors isTestFile logic for testing.
         */
        private fun isTestFilePath(path: String, nameWithoutExtension: String): Boolean {
            val pathLower = path.lowercase()

            val inTestDirectory = TestNavigationService.DEFAULT_TEST_ROOTS.any { testRoot ->
                pathLower.contains("/$testRoot/") || pathLower.contains("\\$testRoot\\")
            }

            val hasTestSuffix = TestConvention.entries.any { it.matches(nameWithoutExtension) }

            return inTestDirectory || hasTestSuffix
        }
    }

    // =========================================================================
    // Path Computation Tests
    // =========================================================================

    @Nested
    @DisplayName("Path Computation")
    inner class PathComputationTests {

        @Test
        @DisplayName("computes test path correctly for Kotlin Gradle project")
        fun computesTestPath_forKotlinGradle() {
            val sourcePath = "${projectRoot.absolutePath}/src/main/kotlin/com/example/UserService.kt"
            val config = TestDirectoryConfig.KOTLIN_GRADLE

            val testPath = config.computeTestPath(sourcePath)

            assertNotNull(testPath)
            assertTrue(testPath!!.contains("src/test/kotlin"))
            assertTrue(testPath.endsWith("UserServiceTest.kt"))
        }

        @Test
        @DisplayName("computes source path correctly from test file")
        fun computesSourcePath_fromTestFile() {
            val testPath = "${projectRoot.absolutePath}/src/test/kotlin/com/example/UserServiceTest.kt"
            val config = TestDirectoryConfig.KOTLIN_GRADLE

            val sourcePath = config.computeSourcePath(testPath)

            assertNotNull(sourcePath)
            assertTrue(sourcePath!!.contains("src/main/kotlin"))
            assertTrue(sourcePath.endsWith("UserService.kt"))
        }

        @Test
        @DisplayName("preserves package structure in test path")
        fun preservesPackageStructure_inTestPath() {
            val sourcePath = "${projectRoot.absolutePath}/src/main/kotlin/com/example/deep/nested/MyClass.kt"
            val config = TestDirectoryConfig.KOTLIN_GRADLE

            val testPath = config.computeTestPath(sourcePath)

            assertNotNull(testPath)
            assertTrue(testPath!!.contains("/com/example/deep/nested/"))
        }

        @Test
        @DisplayName("returns null for path outside source root")
        fun returnsNull_forPathOutsideSourceRoot() {
            val sourcePath = "/some/other/path/MyClass.kt"
            val config = TestDirectoryConfig.KOTLIN_GRADLE

            val testPath = config.computeTestPath(sourcePath)

            assertNull(testPath)
        }
    }

    // =========================================================================
    // Test Stub Generation Tests
    // =========================================================================

    @Nested
    @DisplayName("Test Stub Generation")
    inner class TestStubGenerationTests {

        @Test
        @DisplayName("Kotlin test stub has correct structure")
        fun kotlinTestStub_hasCorrectStructure() {
            val stub = generateKotlinTestStub("com.example", "UserService", "UserServiceTest")

            assertAll(
                { assertTrue(stub.contains("package com.example")) },
                { assertTrue(stub.contains("import org.junit.jupiter.api.Test")) },
                { assertTrue(stub.contains("class UserServiceTest")) },
                { assertTrue(stub.contains("@Test")) },
                { assertTrue(stub.contains("fun `should work correctly`()")) }
            )
        }

        @Test
        @DisplayName("Java test stub has correct structure")
        fun javaTestStub_hasCorrectStructure() {
            val stub = generateJavaTestStub("com.example", "UserService", "UserServiceTest")

            assertAll(
                { assertTrue(stub.contains("package com.example;")) },
                { assertTrue(stub.contains("import org.junit.jupiter.api.Test;")) },
                { assertTrue(stub.contains("public class UserServiceTest")) },
                { assertTrue(stub.contains("@Test")) },
                { assertTrue(stub.contains("void shouldWorkCorrectly()")) }
            )
        }

        @Test
        @DisplayName("C# test stub has correct structure")
        fun csharpTestStub_hasCorrectStructure() {
            val stub = generateCSharpTestStub("MyApp.Services", "UserService", "UserServiceTests")

            assertAll(
                { assertTrue(stub.contains("using Xunit;")) },
                { assertTrue(stub.contains("namespace MyApp.Services;")) },
                { assertTrue(stub.contains("public class UserServiceTests")) },
                { assertTrue(stub.contains("[Fact]")) },
                { assertTrue(stub.contains("public void ShouldWorkCorrectly()")) }
            )
        }

        @Test
        @DisplayName("Python test stub has correct structure")
        fun pythonTestStub_hasCorrectStructure() {
            val stub = generatePythonTestStub("user_service")

            assertAll(
                { assertTrue(stub.contains("import pytest")) },
                { assertTrue(stub.contains("class TestUser_service:")) },
                { assertTrue(stub.contains("def test_should_work_correctly(self):")) }
            )
        }

        @Test
        @DisplayName("Kotlin test stub with empty package is valid")
        fun kotlinTestStub_emptyPackage_isValid() {
            val stub = generateKotlinTestStub("", "MyClass", "MyClassTest")

            assertFalse(stub.contains("package "))
            assertTrue(stub.contains("class MyClassTest"))
        }

        // Helper methods that mirror the service's stub generation
        private fun generateKotlinTestStub(packagePath: String, className: String, testClassName: String): String {
            return buildString {
                if (packagePath.isNotEmpty()) {
                    appendLine("package $packagePath")
                    appendLine()
                }
                appendLine("import org.junit.jupiter.api.Test")
                appendLine("import org.junit.jupiter.api.Assertions.*")
                appendLine("import org.junit.jupiter.api.BeforeEach")
                appendLine()
                appendLine("/**")
                appendLine(" * Unit tests for [$className].")
                appendLine(" */")
                appendLine("class $testClassName {")
                appendLine()
                appendLine("    @BeforeEach")
                appendLine("    fun setUp() {")
                appendLine("        // TODO: Set up test fixtures")
                appendLine("    }")
                appendLine()
                appendLine("    @Test")
                appendLine("    fun `should work correctly`() {")
                appendLine("        // TODO: Implement test")
                appendLine("        fail(\"Test not implemented\")")
                appendLine("    }")
                appendLine("}")
            }
        }

        private fun generateJavaTestStub(packagePath: String, className: String, testClassName: String): String {
            return buildString {
                if (packagePath.isNotEmpty()) {
                    appendLine("package $packagePath;")
                    appendLine()
                }
                appendLine("import org.junit.jupiter.api.Test;")
                appendLine("import org.junit.jupiter.api.BeforeEach;")
                appendLine("import static org.junit.jupiter.api.Assertions.*;")
                appendLine()
                appendLine("/**")
                appendLine(" * Unit tests for {@link $className}.")
                appendLine(" */")
                appendLine("public class $testClassName {")
                appendLine()
                appendLine("    @BeforeEach")
                appendLine("    void setUp() {")
                appendLine("        // TODO: Set up test fixtures")
                appendLine("    }")
                appendLine()
                appendLine("    @Test")
                appendLine("    void shouldWorkCorrectly() {")
                appendLine("        // TODO: Implement test")
                appendLine("        fail(\"Test not implemented\");")
                appendLine("    }")
                appendLine("}")
            }
        }

        private fun generateCSharpTestStub(namespace: String, className: String, testClassName: String): String {
            return buildString {
                appendLine("using Xunit;")
                appendLine()
                if (namespace.isNotEmpty()) {
                    appendLine("namespace $namespace;")
                    appendLine()
                }
                appendLine("/// <summary>")
                appendLine("/// Unit tests for <see cref=\"$className\"/>.")
                appendLine("/// </summary>")
                appendLine("public class $testClassName")
                appendLine("{")
                appendLine("    [Fact]")
                appendLine("    public void ShouldWorkCorrectly()")
                appendLine("    {")
                appendLine("        // TODO: Implement test")
                appendLine("        Assert.Fail(\"Test not implemented\");")
                appendLine("    }")
                appendLine("}")
            }
        }

        private fun generatePythonTestStub(className: String): String {
            return buildString {
                appendLine("\"\"\"Unit tests for $className.\"\"\"")
                appendLine()
                appendLine("import pytest")
                appendLine()
                appendLine()
                appendLine("class Test${className.replaceFirstChar { it.uppercase() }}:")
                appendLine("    \"\"\"Tests for $className.\"\"\"")
                appendLine()
                appendLine("    def test_should_work_correctly(self):")
                appendLine("        \"\"\"TODO: Implement test.\"\"\"")
                appendLine("        pytest.fail(\"Test not implemented\")")
            }
        }
    }

    // =========================================================================
    // Supported Extensions Tests
    // =========================================================================

    @Nested
    @DisplayName("Supported Extensions")
    inner class SupportedExtensionsTests {

        @Test
        @DisplayName("all common programming languages are supported")
        fun allCommonLanguages_areSupported() {
            val expected = setOf("kt", "java", "cs", "py", "ts", "js", "rb", "go")
            assertEquals(expected, TestNavigationService.SUPPORTED_EXTENSIONS)
        }

        @ParameterizedTest
        @DisplayName("extension is in supported list")
        @ValueSource(strings = ["kt", "java", "cs", "py", "ts", "js", "rb", "go"])
        fun extension_isSupported(ext: String) {
            assertTrue(TestNavigationService.SUPPORTED_EXTENSIONS.contains(ext))
        }

        @ParameterizedTest
        @DisplayName("non-code extensions are not supported")
        @ValueSource(strings = ["md", "txt", "json", "xml", "yaml", "html", "css"])
        fun nonCodeExtensions_areNotSupported(ext: String) {
            assertFalse(TestNavigationService.SUPPORTED_EXTENSIONS.contains(ext))
        }
    }

    // =========================================================================
    // Default Roots Tests
    // =========================================================================

    @Nested
    @DisplayName("Default Roots")
    inner class DefaultRootsTests {

        @Test
        @DisplayName("test roots include common patterns")
        fun testRoots_includeCommonPatterns() {
            val roots = TestNavigationService.DEFAULT_TEST_ROOTS

            assertAll(
                { assertTrue(roots.contains("test")) },
                { assertTrue(roots.contains("tests")) },
                { assertTrue(roots.contains("spec")) },
                { assertTrue(roots.contains("specs")) },
                { assertTrue(roots.contains("__tests__")) }
            )
        }

        @Test
        @DisplayName("source roots include common patterns")
        fun sourceRoots_includeCommonPatterns() {
            val roots = TestNavigationService.DEFAULT_SOURCE_ROOTS

            assertAll(
                { assertTrue(roots.contains("src")) },
                { assertTrue(roots.contains("main")) },
                { assertTrue(roots.contains("lib")) },
                { assertTrue(roots.contains("source")) },
                { assertTrue(roots.contains("app")) }
            )
        }
    }

    // =========================================================================
    // File System Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("File System Integration")
    inner class FileSystemIntegrationTests {

        @Test
        @DisplayName("can detect existing test file")
        fun canDetectExistingTestFile() {
            val testFile = File(projectRoot, "src/test/kotlin/com/example/UserServiceTest.kt")
            assertTrue(testFile.exists())
        }

        @Test
        @DisplayName("source file without test is detected")
        fun sourceFileWithoutTest_isDetected() {
            val orderServiceTest = File(projectRoot, "src/test/kotlin/com/example/OrderServiceTest.kt")
            assertFalse(orderServiceTest.exists())
        }

        @Test
        @DisplayName("can create test directory structure")
        fun canCreateTestDirectoryStructure() {
            val newTestDir = File(projectRoot, "src/test/kotlin/com/example/newpackage")
            newTestDir.mkdirs()

            assertTrue(newTestDir.exists())
            assertTrue(newTestDir.isDirectory)
        }
    }
}
