package dev.gmetal.metador

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.core.Is.`is` as _is

@ExperimentalCoroutinesApi
class HtmlMetaExtractorTest {
    lateinit var resourceParserInTest: HtmlMetaExtractor

    @BeforeEach
    fun setup() {
        resourceParserInTest = HtmlMetaExtractor()
    }

    @Test
    fun `empty string inputs, produce a success with an empty map`() = runBlockingTest {
        val response = resourceParserInTest.parseResource("")

        assertThat(response, _is(emptyMap()))
    }

    @Test
    fun `an HTML input without any meta elements, produces an empty map`() = runBlockingTest {
        val response = resourceParserInTest.parseResource(HTML_DOCUMENT_WITHOUT_META)

        assertThat(response, _is(emptyMap()))
    }

    @Test
    fun `an HTML input where the meta key attribute cannot be determined, produces an empty map`() =
        runBlockingTest {
            val response =
                resourceParserInTest.parseResource(HTML_DOCUMENT_WITH_UNKNOWN_ATTRIBUTE_META)

            assertThat(response, _is(emptyMap()))
        }

    @Test
    fun `an HTML input with all supported the meta key attributes, produces a map with entries for each meta element`() =
        runBlockingTest {
            val response =
                resourceParserInTest.parseResource(HTML_DOCUMENT_WITH_ALL_SUPPORTED_TYPES_META)

            assertThat(
                response,
                _is(
                    mapOf(
                        "key-1" to "value",
                        "key-2" to "value",
                        "key-3" to "value"
                    )

                )
            )
        }

    @Test
    fun `only meta elements with both key and content attributes are supported`() =
        runBlockingTest {
            val response =
                resourceParserInTest.parseResource(
                    HTML_DOCUMENT_WITH_META_WITHOUT_THE_CONTENT_ATTRIBUTE
                )

            assertThat(response, _is(emptyMap()))
        }
}
