package com.sidekick.navigation.testjump

/**
 * # Test Navigation Models
 *
 * Data structures for bi-directional source↔test file navigation.
 * Part of Sidekick v0.4.1 Jump-to-Test feature.
 *
 * ## Overview
 *
 * These models support:
 * - Mapping between source files and their corresponding test files
 * - Multiple test naming conventions (Tests, Test, Spec, etc.)
 * - Configurable test directory structures
 * - Navigation outcomes (found, not found, multiple matches)
 *
 * @since 0.4.1
 */

/**
 * Represents a source-test file mapping relationship.
 *
 * This class captures the relationship between a source file and its
 * corresponding test file, including whether the test file exists and
 * which naming convention was used to derive it.
 *
 * @property sourceFile Absolute path to the source file
 * @property testFile Absolute path to the corresponding test file (may not exist)
 * @property convention The naming convention used to derive the test file name
 * @property exists Whether the test file currently exists on disk
 */
data class TestMapping(
    val sourceFile: String,
    val testFile: String?,
    val convention: TestConvention,
    val exists: Boolean
) {
    /**
     * Returns true if navigation to the test file is possible (file exists).
     */
    val canNavigate: Boolean get() = testFile != null && exists

    /**
     * Returns true if a test file can be created (path known but file doesn't exist).
     */
    val canCreate: Boolean get() = testFile != null && !exists

    /**
     * Gets the test file name without path, or null if no test file is mapped.
     */
    val testFileName: String?
        get() = testFile?.substringAfterLast('/')

    /**
     * Gets the source file name without path.
     */
    val sourceFileName: String
        get() = sourceFile.substringAfterLast('/')
}

/**
 * Supported test file naming conventions.
 *
 * Each convention defines:
 * - A display name for UI presentation
 * - A suffix used to identify test files
 * - A pattern for generating test file names from source file names
 *
 * ## Supported Conventions
 *
 * | Convention | Example Source | Example Test |
 * |------------|----------------|--------------|
 * | TESTS | UserService | UserServiceTests |
 * | TEST | UserService | UserServiceTest |
 * | SPEC | UserService | UserServiceSpec |
 * | SHOULD | UserService | UserServiceShould |
 * | UNDERSCORE_TEST | user_service | user_service_test |
 *
 * @property displayName Human-readable name for UI display
 * @property suffix The suffix added to source names to create test names
 * @property pattern Template pattern where {Name} is replaced with source name
 */
enum class TestConvention(
    val displayName: String,
    val suffix: String,
    val pattern: String
) {
    /** "Tests" suffix - common in .NET (e.g., UserServiceTests) */
    TESTS("Tests suffix", "Tests", "{Name}Tests"),

    /** "Test" suffix - common in Java (e.g., UserServiceTest) */
    TEST("Test suffix", "Test", "{Name}Test"),

    /** "Spec" suffix - common in BDD/Ruby (e.g., UserServiceSpec) */
    SPEC("Spec suffix", "Spec", "{Name}Spec"),

    /** "Should" suffix - BDD style (e.g., UserServiceShould) */
    SHOULD("Should suffix", "Should", "{Name}Should"),

    /** Underscore test suffix - Python style (e.g., user_service_test) */
    UNDERSCORE_TEST("Underscore test", "_test", "{Name}_test");

    /**
     * Generates a test file name from a source file name.
     *
     * @param sourceName The source file name (without extension)
     * @return The corresponding test file name (without extension)
     */
    fun toTestName(sourceName: String): String {
        return pattern.replace("{Name}", sourceName)
    }

    /**
     * Extracts the source file name from a test file name.
     *
     * @param testName The test file name (without extension)
     * @return The corresponding source file name, or null if pattern doesn't match
     */
    fun toSourceName(testName: String): String? {
        return when {
            testName.endsWith(suffix) -> testName.dropLast(suffix.length)
            else -> null
        }
    }

    /**
     * Checks if a file name matches this convention's test pattern.
     *
     * @param fileName The file name to check (without extension)
     * @return True if the file name ends with this convention's suffix
     */
    fun matches(fileName: String): Boolean = fileName.endsWith(suffix)

    companion object {
        /**
         * Detects which convention a test file name follows.
         *
         * @param testFileName The test file name to analyze (without extension)
         * @return The matching convention, or null if no convention matches
         */
        fun detect(testFileName: String): TestConvention? {
            // Check in order of specificity (longer suffixes first)
            return entries
                .sortedByDescending { it.suffix.length }
                .find { testFileName.endsWith(it.suffix) }
        }

        /**
         * Default convention to use when none is specified.
         */
        val DEFAULT = TESTS

        /**
         * All suffix patterns for quick matching.
         */
        val ALL_SUFFIXES: List<String> = entries.map { it.suffix }
    }
}

/**
 * Configuration for test directory structure.
 *
 * Defines how source directories map to test directories within a project.
 * Supports mirroring package/namespace structure between source and test roots.
 *
 * @property sourceRoot Root directory for source files (e.g., "src/main/kotlin")
 * @property testRoot Root directory for test files (e.g., "src/test/kotlin")
 * @property convention Default naming convention for this configuration
 * @property mirrorPackageStructure Whether to preserve package structure in tests
 */
data class TestDirectoryConfig(
    val sourceRoot: String,
    val testRoot: String,
    val convention: TestConvention,
    val mirrorPackageStructure: Boolean = true
) {
    /**
     * Computes the test file path for a given source file path.
     *
     * @param sourceFilePath Absolute path to the source file
     * @return The corresponding test file path, or null if path is outside sourceRoot
     */
    fun computeTestPath(sourceFilePath: String): String? {
        if (!sourceFilePath.contains(sourceRoot)) return null

        val relativePath = sourceFilePath.substringAfter(sourceRoot)
        val sourceFileName = sourceFilePath.substringAfterLast('/').substringBeforeLast('.')
        val testFileName = convention.toTestName(sourceFileName)

        val testPath = if (mirrorPackageStructure) {
            "$testRoot$relativePath"
        } else {
            val fileExtension = sourceFilePath.substringAfterLast('.', "")
            "$testRoot/$testFileName.$fileExtension"
        }

        return testPath.replace(sourceFileName, testFileName)
    }

    /**
     * Computes the source file path for a given test file path.
     *
     * @param testFilePath Absolute path to the test file
     * @return The corresponding source file path, or null if conversion fails
     */
    fun computeSourcePath(testFilePath: String): String? {
        if (!testFilePath.contains(testRoot)) return null

        val testFileName = testFilePath.substringAfterLast('/').substringBeforeLast('.')
        val sourceFileName = convention.toSourceName(testFileName) ?: return null
        val extension = testFilePath.substringAfterLast('.', "")

        val relativePath = testFilePath.substringAfter(testRoot)
        val sourcePath = "$sourceRoot$relativePath"

        return sourcePath.replace(testFileName, sourceFileName)
    }

    companion object {
        /**
         * Default configuration for Kotlin/Java Gradle projects.
         */
        val KOTLIN_GRADLE = TestDirectoryConfig(
            sourceRoot = "src/main/kotlin",
            testRoot = "src/test/kotlin",
            convention = TestConvention.TEST
        )

        /**
         * Default configuration for .NET projects.
         */
        val DOTNET = TestDirectoryConfig(
            sourceRoot = "src",
            testRoot = "tests",
            convention = TestConvention.TESTS
        )

        /**
         * Default configuration for Python projects.
         */
        val PYTHON = TestDirectoryConfig(
            sourceRoot = "src",
            testRoot = "tests",
            convention = TestConvention.UNDERSCORE_TEST
        )
    }
}

/**
 * Result of a navigation operation.
 *
 * Sealed class hierarchy representing all possible outcomes when
 * attempting to navigate between source and test files.
 */
sealed class NavigationResult {
    /**
     * Navigation succeeded - target file was found.
     *
     * @property path Absolute path to the found file
     */
    data class Found(val path: String) : NavigationResult() {
        override fun toString() = "Found: $path"
    }

    /**
     * Target file does not exist, but a path is suggested for creation.
     *
     * @property suggestedPath Suggested path where the file should be created
     */
    data class NotFound(val suggestedPath: String) : NavigationResult() {
        override fun toString() = "NotFound: suggested=$suggestedPath"
    }

    /**
     * Multiple matching files were found (e.g., multiple test files for one source).
     *
     * @property options List of possible file paths
     */
    data class Multiple(val options: List<String>) : NavigationResult() {
        /** Number of matching options */
        val count: Int get() = options.size

        override fun toString() = "Multiple: ${options.size} options"
    }

    /**
     * Navigation failed due to an error.
     *
     * @property message Human-readable error description
     */
    data class Error(val message: String) : NavigationResult() {
        override fun toString() = "Error: $message"
    }

    /**
     * Returns true if navigation can proceed (file was found).
     */
    val isSuccess: Boolean get() = this is Found

    /**
     * Returns true if file creation is suggested.
     */
    val canCreate: Boolean get() = this is NotFound

    /**
     * Gets the path if found, null otherwise.
     */
    val pathOrNull: String?
        get() = when (this) {
            is Found -> path
            else -> null
        }
}
