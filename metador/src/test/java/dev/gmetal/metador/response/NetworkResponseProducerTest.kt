package dev.gmetal.metador.response

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import dev.gmetal.metador.HtmlMetaExtractor
import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceNotFoundException
import dev.gmetal.metador.ResourceParserDelegate
import dev.gmetal.metador.ResourceRetriever
import io.mockk.MockKAnnotations
import io.mockk.called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsInstanceOf.instanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.core.Is.`is` as _is

@ExperimentalCoroutinesApi
class NetworkResponseProducerTest {
    lateinit var networkResponseProducerInTest: NetworkResponseProducer

    @MockK
    lateinit var mockResourceRetriever: ResourceRetriever

    @MockK
    lateinit var mockResourceParserDelegate: ResourceParserDelegate

    @MockK
    lateinit var mockSuccessCallback: Metador.SuccessCallback

    @MockK
    lateinit var mockFailureCallback: Metador.FailureCallback

    private val testCoroutineDispatcher: CoroutineDispatcher = TestCoroutineDispatcher()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        networkResponseProducerInTest =
            NetworkResponseProducer(mockResourceRetriever, testCoroutineDispatcher)
    }

    @Test
    fun `a NetworkResponseProducer can always handle requests`() {
        assertThat(
            networkResponseProducerInTest.canHandleRequest(defaultRequest("fake_uri")),
            _is(true)
        )
    }

    @Test
    fun `the parsing of retrieved resources is delegated to the request's ResourceParserDelegate`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns emptyMap()

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest(
                    "http://localhost/resource_parser_delegate",
                    mockResourceParserDelegate
                )
            )

            assertThat(result.get(), _is(emptyMap()))
            verify { mockFailureCallback wasNot called }
            verify { mockSuccessCallback wasNot called }
        }

    @Test
    fun `exceptions thrown by the resource retriever are encapsulated in the result and returned`() =
        runBlockingTest {
            coEvery { mockResourceRetriever.retrieveResource(any()) } throws ResourceNotFoundException

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest("http://localhost/test")
            )

            assertThat(result.getError(), _is(ResourceNotFoundException))
            verify { mockFailureCallback wasNot called }
            verify { mockSuccessCallback wasNot called }
        }

    @Test
    fun `exceptions thrown by the resource parser are propagated to the failure callback`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } throws RuntimeException(
                "Unknown error"
            )

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest("http://localhost/test", mockResourceParserDelegate)
            )

            assertThat(result.getError(), instanceOf(RuntimeException::class.java))
            verify { mockFailureCallback wasNot called }
            verify { mockSuccessCallback wasNot called }
        }

    @Test
    fun `the successful result is returned to the caller`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            val expectedResult = mapOf("key" to "value")
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns expectedResult

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest("http://localhost/test", mockResourceParserDelegate)
            )

            assertThat(result.get(), _is(expectedResult))
            verify { mockFailureCallback wasNot called }
            verify { mockSuccessCallback wasNot called }
        }

    private fun defaultRequest(
        uri: String,
        resourceParserDelegate: ResourceParserDelegate = HtmlMetaExtractor()
    ): Metador.Request =
        Metador.Request.Builder(uri)
            .withResourceParser(resourceParserDelegate)
            .onSuccess(mockSuccessCallback)
            .onFailure(mockFailureCallback)
            .build()
}
