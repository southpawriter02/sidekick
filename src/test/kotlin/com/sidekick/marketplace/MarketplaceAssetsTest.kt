package com.sidekick.marketplace

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive unit tests for Marketplace Assets.
 */
@DisplayName("Marketplace Assets Tests")
class MarketplaceAssetsTest {

    // =========================================================================
    // MarketplaceMetadata Tests
    // =========================================================================

    @Nested
    @DisplayName("MarketplaceMetadata")
    inner class MarketplaceMetadataTests {

        @Test
        @DisplayName("defaults are correct")
        fun defaultsAreCorrect() {
            val metadata = MarketplaceMetadata()

            assertEquals("com.sidekick", metadata.pluginId)
            assertEquals("Sidekick - Local AI Coding Assistant", metadata.name)
            assertEquals("Sidekick", metadata.vendor)
        }

        @Test
        @DisplayName("validate catches missing fields")
        fun validateCatchesMissingFields() {
            val metadata = MarketplaceMetadata(
                pluginId = "",
                name = "",
                version = ""
            )
            val errors = metadata.validate()

            assertTrue(errors.any { it.contains("Plugin ID") })
            assertTrue(errors.any { it.contains("Name") })
            assertTrue(errors.any { it.contains("Version") })
        }

        @Test
        @DisplayName("validate checks version format")
        fun validateChecksVersionFormat() {
            val metadata = MarketplaceMetadata(version = "invalid")
            val errors = metadata.validate()

            assertTrue(errors.any { it.contains("semantic") })
        }

        @Test
        @DisplayName("validate passes for valid metadata")
        fun validatePassesForValidMetadata() {
            val metadata = MarketplaceMetadata()
            val errors = metadata.validate()

            assertTrue(errors.isEmpty())
        }

        @Test
        @DisplayName("toPluginXml generates valid XML")
        fun toPluginXmlGeneratesValidXml() {
            val metadata = MarketplaceMetadata(version = "1.2.3")
            val xml = metadata.toPluginXml()

            assertTrue(xml.contains("<idea-plugin>"))
            assertTrue(xml.contains("1.2.3"))
            assertTrue(xml.contains("com.sidekick"))
        }

        @Test
        @DisplayName("getTagString joins tags")
        fun getTagStringJoinsTags() {
            val metadata = MarketplaceMetadata()
            val tagString = metadata.getTagString()

            assertTrue(tagString.contains("AI"))
            assertTrue(tagString.contains(","))
        }

        @Test
        @DisplayName("getMarketplaceUrl returns correct URL")
        fun getMarketplaceUrlReturnsCorrectUrl() {
            val metadata = MarketplaceMetadata()
            val url = metadata.getMarketplaceUrl()

            assertTrue(url.startsWith("https://plugins.jetbrains.com"))
            assertTrue(url.contains(metadata.pluginId))
        }

        @Test
        @DisplayName("forVersion creates with specific version")
        fun forVersionCreatesWithSpecificVersion() {
            val metadata = MarketplaceMetadata.forVersion("2.0.0", "<p>New version</p>")

            assertEquals("2.0.0", metadata.version)
            assertEquals("<p>New version</p>", metadata.changeNotes)
        }
    }

    // =========================================================================
    // DocumentationSite Tests
    // =========================================================================

    @Nested
    @DisplayName("DocumentationSite")
    inner class DocumentationSiteTests {

        @Test
        @DisplayName("defaults include sections")
        fun defaultsIncludeSections() {
            val site = DocumentationSite()

            assertTrue(site.sections.isNotEmpty())
            assertTrue(site.sections.any { it.id == "getting-started" })
        }

        @Test
        @DisplayName("getAllPages returns all pages")
        fun getAllPagesReturnsAllPages() {
            val site = DocumentationSite()
            val pages = site.getAllPages()

            assertTrue(pages.isNotEmpty())
            assertEquals(site.getPageCount(), pages.size)
        }

        @Test
        @DisplayName("findPage finds by ID")
        fun findPageFindsById() {
            val site = DocumentationSite()
            val page = site.findPage("installation")

            assertNotNull(page)
            assertEquals("Installation", page?.title)
        }

        @Test
        @DisplayName("findPage returns null for unknown")
        fun findPageReturnsNullForUnknown() {
            val site = DocumentationSite()
            assertNull(site.findPage("nonexistent"))
        }

        @Test
        @DisplayName("findSectionForPage finds correct section")
        fun findSectionForPageFindsCorrectSection() {
            val site = DocumentationSite()
            val section = site.findSectionForPage("installation")

            assertNotNull(section)
            assertEquals("getting-started", section?.id)
        }

        @Test
        @DisplayName("getPageUrl generates correct URL")
        fun getPageUrlGeneratesCorrectUrl() {
            val site = DocumentationSite()
            val url = site.getPageUrl("features", "chat")

            assertEquals("https://sidekick.dev/docs/features/chat", url)
        }

        @Test
        @DisplayName("generateToc creates markdown")
        fun generateTocCreatesMarkdown() {
            val site = DocumentationSite()
            val toc = site.generateToc()

            assertTrue(toc.contains("##"))
            assertTrue(toc.contains("["))
            assertTrue(toc.contains("]("))
        }
    }

    // =========================================================================
    // DocSection Tests
    // =========================================================================

    @Nested
    @DisplayName("DocSection")
    inner class DocSectionTests {

        @Test
        @DisplayName("pageCount returns correct count")
        fun pageCountReturnsCorrectCount() {
            val section = DocSection(
                id = "test",
                title = "Test",
                pages = listOf(
                    DocPage("a", "A"),
                    DocPage("b", "B")
                )
            )

            assertEquals(2, section.pageCount)
        }

        @Test
        @DisplayName("format includes title and count")
        fun formatIncludesTitleAndCount() {
            val section = DocSection(
                id = "test",
                title = "Test Section",
                pages = listOf(DocPage("a", "A"))
            )
            val formatted = section.format()

            assertTrue(formatted.contains("Test Section"))
            assertTrue(formatted.contains("1 pages"))
        }
    }

    // =========================================================================
    // DocPage Tests
    // =========================================================================

    @Nested
    @DisplayName("DocPage")
    inner class DocPageTests {

        @Test
        @DisplayName("toMarkdownLink generates link")
        fun toMarkdownLinkGeneratesLink() {
            val page = DocPage("test", "Test Page")
            val link = page.toMarkdownLink("https://docs.example.com", "section")

            assertEquals("[Test Page](https://docs.example.com/section/test)", link)
        }
    }

    // =========================================================================
    // ScreenshotAsset Tests
    // =========================================================================

    @Nested
    @DisplayName("ScreenshotAsset")
    inner class ScreenshotAssetTests {

        @Test
        @DisplayName("aspectRatio calculates correctly")
        fun aspectRatioCalculatesCorrectly() {
            val screenshot = ScreenshotAsset(
                id = "test",
                title = "Test",
                description = "Test",
                filename = "test.png",
                width = 1920,
                height = 1080
            )

            assertEquals(1920f / 1080f, screenshot.aspectRatio, 0.01f)
        }

        @Test
        @DisplayName("validateDimensions checks minimum size")
        fun validateDimensionsChecksMinimumSize() {
            val valid = ScreenshotAsset("a", "A", "A", "a.png", 1280, 800)
            val invalid = ScreenshotAsset("b", "B", "B", "b.png", 640, 480)

            assertTrue(valid.validateDimensions())
            assertFalse(invalid.validateDimensions())
        }

        @Test
        @DisplayName("RECOMMENDED has minimum screenshots")
        fun recommendedHasMinimumScreenshots() {
            assertTrue(ScreenshotAsset.RECOMMENDED.size >= 3)
        }
    }

    // =========================================================================
    // MarketplaceSubmission Tests
    // =========================================================================

    @Nested
    @DisplayName("MarketplaceSubmission")
    inner class MarketplaceSubmissionTests {

        @Test
        @DisplayName("defaults are incomplete")
        fun defaultsAreIncomplete() {
            val submission = MarketplaceSubmission()

            assertFalse(submission.isComplete)
            assertEquals(0, submission.completionPercent)
        }

        @Test
        @DisplayName("allComplete is 100%")
        fun allCompleteIs100Percent() {
            val submission = MarketplaceSubmission.allComplete()

            assertTrue(submission.isComplete)
            assertEquals(100, submission.completionPercent)
        }

        @Test
        @DisplayName("complete marks item done")
        fun completeMarksItemDone() {
            val submission = MarketplaceSubmission()
            val updated = submission.complete("Plugin builds without errors")

            assertTrue(updated.items["Plugin builds without errors"] == true)
        }

        @Test
        @DisplayName("getIncompleteItems returns pending")
        fun getIncompleteItemsReturnsPending() {
            val submission = MarketplaceSubmission()
            val incomplete = submission.getIncompleteItems()

            assertEquals(MarketplaceSubmission.DEFAULT_ITEMS.size, incomplete.size)
        }

        @Test
        @DisplayName("format produces checklist")
        fun formatProducesChecklist() {
            val submission = MarketplaceSubmission()
            val formatted = submission.format()

            assertTrue(formatted.contains("Checklist"))
            assertTrue(formatted.contains("[ ]"))
        }
    }

    // =========================================================================
    // Tutorial Tests
    // =========================================================================

    @Nested
    @DisplayName("Tutorial")
    inner class TutorialTests {

        @Test
        @DisplayName("stepCount returns correct count")
        fun stepCountReturnsCorrectCount() {
            val tutorial = Tutorial.QUICK_START

            assertEquals(tutorial.steps.size, tutorial.stepCount)
        }

        @Test
        @DisplayName("format includes details")
        fun formatIncludesDetails() {
            val tutorial = Tutorial.QUICK_START
            val formatted = tutorial.format()

            assertTrue(formatted.contains(tutorial.title))
            assertTrue(formatted.contains("steps"))
        }

        @Test
        @DisplayName("QUICK_START has steps")
        fun quickStartHasSteps() {
            assertTrue(Tutorial.QUICK_START.steps.isNotEmpty())
            assertTrue(Tutorial.QUICK_START.estimatedMinutes > 0)
        }
    }
}
