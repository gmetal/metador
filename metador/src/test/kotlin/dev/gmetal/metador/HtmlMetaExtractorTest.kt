package dev.gmetal.metador

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class HtmlMetaExtractorTest : BehaviorSpec({
    lateinit var htmlMetaExtractorInTest: HtmlMetaExtractor

    beforeContainer {
        htmlMetaExtractorInTest = HtmlMetaExtractor()
    }

    Given("an HtmlMetaExtractor") {
        When("it attempts to parse an empty string resource") {
            val response = htmlMetaExtractorInTest.parseResource("")

            Then("it returns an empty map") {
                response shouldBe emptyMap()
            }
        }

        When("it attempts to parse an HTML resource without any META elements") {
            val response = htmlMetaExtractorInTest.parseResource(HTML_DOCUMENT_WITHOUT_META)
            Then("it returns an empty map") {
                response shouldBe emptyMap()
            }
        }

        When("the HTML input contains an unknown META key attribute") {
            val response =
                htmlMetaExtractorInTest.parseResource(HTML_DOCUMENT_WITH_UNKNOWN_ATTRIBUTE_META)

            Then("it returns an empty map") {
                response shouldBe emptyMap()
            }
        }

        When("the HTML input contains supported META key attributes") {
            val response =
                htmlMetaExtractorInTest.parseResource(HTML_DOCUMENT_WITH_ALL_SUPPORTED_TYPES_META)
            Then("it returns a map containing all META key-value pairs") {
                response shouldBe mapOf("key-1" to "value", "key-2" to "value", "key-3" to "value")
            }
        }

        When("the HTML input contains only META elements without the content attribute") {
            val response =
                htmlMetaExtractorInTest.parseResource(
                    HTML_DOCUMENT_WITH_META_WITHOUT_THE_CONTENT_ATTRIBUTE
                )
            Then("it returns an empty map") {
                response shouldBe emptyMap()
            }
        }
    }
})
