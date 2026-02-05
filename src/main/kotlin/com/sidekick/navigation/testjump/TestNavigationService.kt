package com.sidekick.navigation.testjump

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.io.File

/**
 * # Test Navigation Service
 *
 * Service for discovering and navigating between source and test files.
 * Part of Sidekick v0.4.1 Jump-to-Test feature.
 *
 * ## Features
 *
 * - Bi-directional navigation: source → test and test → source
 * - Automatic test file detection using multiple naming conventions
 * - Project structure-aware path computation
 * - Test file creation with appropriate stubs
 *
 * ## Usage
 *
 * ```kotlin
 * val service = TestNavigationService.getInstance(project)
 * when (val result = service.findTestForSource(sourceFile)) {
 *     is NavigationResult.Found -> openFile(result.path)
 *     is NavigationResult.NotFound -> promptCreateTest(result.suggestedPath)
 *     is NavigationResult.Error -> showError(result.message)
 * }
 * ```
 *
 * @since 0.4.1
 */
@Service(Service.Level.PROJECT)
class TestNavigationService(private val project: Project) {

    private val logger = Logger.getInstance(TestNavigationService::class.java)

    companion object {
        /**
         * Gets the service instance for a project.
         */
        fun getInstance(project: Project): TestNavigationService {
            return project.getService(TestNavigationService::class.java)
        }

        /** Common test directory names */
        val DEFAULT_TEST_ROOTS = listOf("test", "tests", "spec", "specs", "__tests__", "Test", "Tests")

        /** Common source directory names */
        val DEFAULT_SOURCE_ROOTS = listOf("src", "main", "lib", "source", "app")

        /** File extensions to consider for navigation */
        val SUPPORTED_EXTENSIONS = setOf("kt", "java", "cs", "py", "ts", "js", "rb", "go")
    }

    /**
     * Finds the corresponding test file for a source file.
     *
     * Attempts to locate a test file using the project's detected configuration
     * and naming conventions. If multiple test files exist (e.g., unit and integration),
     * returns a [NavigationResult.Multiple].
     *
     * @param sourceFile The source file to find tests for
     * @return Navigation result indicating found file, suggested path, or error
     */
    fun findTestForSource(sourceFile: VirtualFile): NavigationResult {
        logger.info("Finding test for source: ${sourceFile.path}")

        if (!isSupportedFile(sourceFile)) {
            return NavigationResult.Error("File type not supported: ${sourceFile.extension}")
        }

        if (isTestFile(sourceFile)) {
            return NavigationResult.Error("File is already a test file")
        }

        val config = detectConfig(sourceFile)
        val testPaths = computeAllTestPaths(sourceFile, config)

        val existingTests = testPaths.filter { fileExists(it) }

        return when {
            existingTests.size == 1 -> NavigationResult.Found(existingTests.first())
            existingTests.size > 1 -> NavigationResult.Multiple(existingTests)
            testPaths.isNotEmpty() -> NavigationResult.NotFound(testPaths.first())
            else -> NavigationResult.Error("Cannot determine test path for ${sourceFile.name}")
        }
    }

    /**
     * Finds the corresponding source file for a test file.
     *
     * Detects the naming convention used by the test file and attempts to
     * locate the corresponding source file.
     *
     * @param testFile The test file to find the source for
     * @return Navigation result indicating found file, suggested path, or error
     */
    fun findSourceForTest(testFile: VirtualFile): NavigationResult {
        logger.info("Finding source for test: ${testFile.path}")

        if (!isSupportedFile(testFile)) {
            return NavigationResult.Error("File type not supported: ${testFile.extension}")
        }

        if (!isTestFile(testFile)) {
            return NavigationResult.Error("File is not a test file")
        }

        val convention = TestConvention.detect(testFile.nameWithoutExtension)
            ?: return NavigationResult.Error("Unknown test naming convention")

        val sourceName = convention.toSourceName(testFile.nameWithoutExtension)
            ?: return NavigationResult.Error("Cannot extract source name from ${testFile.name}")

        val sourcePaths = computeAllSourcePaths(testFile, sourceName)
        val existingSources = sourcePaths.filter { fileExists(it) }

        return when {
            existingSources.size == 1 -> NavigationResult.Found(existingSources.first())
            existingSources.size > 1 -> NavigationResult.Multiple(existingSources)
            sourcePaths.isNotEmpty() -> NavigationResult.NotFound(sourcePaths.first())
            else -> NavigationResult.Error("Cannot determine source path for ${testFile.name}")
        }
    }

    /**
     * Determines if a file is a test file.
     *
     * Uses both path-based detection (file in test directory) and
     * name-based detection (file name ends with test suffix).
     *
     * @param file The file to check
     * @return True if the file is identified as a test file
     */
    fun isTestFile(file: VirtualFile): Boolean {
        val path = file.path.lowercase()
        val name = file.nameWithoutExtension

        // Check if in a test directory
        val inTestDirectory = DEFAULT_TEST_ROOTS.any { testRoot ->
            path.contains("/$testRoot/") || path.contains("\\$testRoot\\")
        }

        // Check if name follows test convention
        val hasTestSuffix = TestConvention.entries.any { it.matches(name) }

        return inTestDirectory || hasTestSuffix
    }

    /**
     * Checks if navigation is available from the current file.
     *
     * @param file The file to check
     * @return True if navigation is possible (file is supported and has a target)
     */
    fun canNavigate(file: VirtualFile): Boolean {
        if (!isSupportedFile(file)) return false

        val result = if (isTestFile(file)) {
            findSourceForTest(file)
        } else {
            findTestForSource(file)
        }

        return result.isSuccess || result.canCreate
    }

    /**
     * Creates a test file for the given source file.
     *
     * Generates a test stub with appropriate imports and structure based on
     * the source file's content and the project's testing framework.
     *
     * @param sourceFile The source file to create a test for
     * @return Navigation result pointing to the created file, or error if creation fails
     */
    fun createTestFile(sourceFile: VirtualFile): NavigationResult {
        val config = detectConfig(sourceFile)
        val testPath = computeTestPath(sourceFile, config)
            ?: return NavigationResult.Error("Cannot determine test path")

        logger.info("Creating test file at: $testPath")

        return try {
            val testContent = generateTestStub(sourceFile, config)
            createFileWithDirectories(testPath, testContent)
            NavigationResult.Found(testPath)
        } catch (e: Exception) {
            logger.error("Failed to create test file", e)
            NavigationResult.Error("Failed to create test file: ${e.message}")
        }
    }

    /**
     * Gets the mapping between a source file and its test file.
     *
     * @param sourceFile The source file
     * @return Test mapping with current state information
     */
    fun getTestMapping(sourceFile: VirtualFile): TestMapping {
        val config = detectConfig(sourceFile)
        val testPath = computeTestPath(sourceFile, config)

        return TestMapping(
            sourceFile = sourceFile.path,
            testFile = testPath,
            convention = config.convention,
            exists = testPath?.let { fileExists(it) } ?: false
        )
    }

    // ===== Private Implementation =====

    /**
     * Detects the project configuration for test directory structure.
     */
    private fun detectConfig(file: VirtualFile): TestDirectoryConfig {
        val path = file.path

        // Try to detect based on common patterns
        return when {
            // Kotlin/Java Gradle project
            path.contains("/src/main/kotlin/") -> TestDirectoryConfig.KOTLIN_GRADLE
            path.contains("/src/main/java/") -> TestDirectoryConfig(
                sourceRoot = "src/main/java",
                testRoot = "src/test/java",
                convention = TestConvention.TEST
            )
            // .NET project
            path.contains(".cs") && !path.contains("/test") -> TestDirectoryConfig.DOTNET
            // Python project
            path.endsWith(".py") -> TestDirectoryConfig.PYTHON
            // Default fallback
            else -> TestDirectoryConfig(
                sourceRoot = "src/main",
                testRoot = "src/test",
                convention = TestConvention.DEFAULT
            )
        }
    }

    /**
     * Computes the primary test path for a source file.
     */
    private fun computeTestPath(source: VirtualFile, config: TestDirectoryConfig): String? {
        val sourcePath = source.path

        // If source path doesn't contain the source root, try alternative detection
        if (!sourcePath.contains(config.sourceRoot)) {
            return computeAlternativeTestPath(source, config)
        }

        val relativePath = sourcePath.substringAfter(config.sourceRoot)
        val testName = config.convention.toTestName(source.nameWithoutExtension)
        val extension = source.extension ?: return null

        // Compute project base path
        val projectBase = sourcePath.substringBefore(config.sourceRoot)

        return "$projectBase${config.testRoot}$relativePath"
            .replaceBeforeLast('/', "$projectBase${config.testRoot}${relativePath.substringBeforeLast('/')}")
            .let { path ->
                val dir = path.substringBeforeLast('/')
                "$dir/$testName.$extension"
            }
    }

    /**
     * Computes all possible test paths using multiple conventions.
     */
    private fun computeAllTestPaths(source: VirtualFile, config: TestDirectoryConfig): List<String> {
        val basePath = computeTestPath(source, config) ?: return emptyList()
        val extension = source.extension ?: return emptyList()
        val baseDir = basePath.substringBeforeLast('/')

        return TestConvention.entries.map { convention ->
            val testName = convention.toTestName(source.nameWithoutExtension)
            "$baseDir/$testName.$extension"
        }.distinct()
    }

    /**
     * Computes alternative test path when source root doesn't match exactly.
     */
    private fun computeAlternativeTestPath(source: VirtualFile, config: TestDirectoryConfig): String? {
        // Try to find test directory at same level
        var current = source.parent
        while (current != null) {
            val siblingTest = current.parent?.findChild("test")
                ?: current.parent?.findChild("tests")
            if (siblingTest != null) {
                val relativePath = source.path.substringAfter(current.path)
                val testName = config.convention.toTestName(source.nameWithoutExtension)
                return "${siblingTest.path}$relativePath".replace(
                    source.nameWithoutExtension,
                    testName
                )
            }
            current = current.parent
        }
        return null
    }

    /**
     * Computes all possible source paths for a test file.
     */
    private fun computeAllSourcePaths(test: VirtualFile, sourceName: String): List<String> {
        val testPath = test.path
        val extension = test.extension ?: return emptyList()

        val paths = mutableListOf<String>()

        // Try replacing test roots with source roots
        for (testRoot in DEFAULT_TEST_ROOTS) {
            if (testPath.contains("/$testRoot/")) {
                for (sourceRoot in DEFAULT_SOURCE_ROOTS) {
                    val sourcePath = testPath
                        .replace("/$testRoot/", "/$sourceRoot/main/")
                        .let { path ->
                            val dir = path.substringBeforeLast('/')
                            "$dir/$sourceName.$extension"
                        }
                    paths.add(sourcePath)

                    // Also try without /main/
                    val altPath = testPath
                        .replace("/$testRoot/", "/$sourceRoot/")
                        .let { path ->
                            val dir = path.substringBeforeLast('/')
                            "$dir/$sourceName.$extension"
                        }
                    paths.add(altPath)
                }
            }
        }

        return paths.distinct()
    }

    /**
     * Checks if a file extension is supported for navigation.
     */
    private fun isSupportedFile(file: VirtualFile): Boolean {
        return file.extension?.lowercase() in SUPPORTED_EXTENSIONS
    }

    /**
     * Checks if a file exists at the given path.
     */
    private fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * Creates a file with its parent directories.
     */
    private fun createFileWithDirectories(path: String, content: String) {
        val file = File(path)
        file.parentFile.mkdirs()
        file.writeText(content)

        // Refresh VFS so IntelliJ sees the new file
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    }

    /**
     * Generates a test stub based on the source file.
     */
    private fun generateTestStub(source: VirtualFile, config: TestDirectoryConfig): String {
        val className = source.nameWithoutExtension
        val testClassName = config.convention.toTestName(className)
        val packagePath = extractPackage(source)

        return when (source.extension?.lowercase()) {
            "kt" -> generateKotlinTestStub(packagePath, className, testClassName)
            "java" -> generateJavaTestStub(packagePath, className, testClassName)
            "cs" -> generateCSharpTestStub(packagePath, className, testClassName)
            "py" -> generatePythonTestStub(className)
            else -> "// TODO: Add tests for $className"
        }
    }

    /**
     * Extracts the package/namespace from a source file.
     */
    private fun extractPackage(source: VirtualFile): String {
        val psiFile = PsiManager.getInstance(project).findFile(source)
        val text = psiFile?.text ?: return ""

        // Look for package declaration
        val packageMatch = Regex("""package\s+([\w.]+)""").find(text)
        return packageMatch?.groupValues?.get(1) ?: ""
    }

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
            appendLine("class Test$className:")
            appendLine("    \"\"\"Tests for $className.\"\"\"")
            appendLine()
            appendLine("    def test_should_work_correctly(self):")
            appendLine("        \"\"\"TODO: Implement test.\"\"\"")
            appendLine("        pytest.fail(\"Test not implemented\")")
        }
    }
}
