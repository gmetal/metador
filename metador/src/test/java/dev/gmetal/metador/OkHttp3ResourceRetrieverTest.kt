package dev.gmetal.metador

import dev.gmetal.metador.mockserver.BASE_URL
import dev.gmetal.metador.mockserver.MOCK_WEB_SERVER_PORT
import dev.gmetal.metador.mockserver.MetadorDispatcher
import dev.gmetal.metador.mockserver.errorMockResponse
import dev.gmetal.metador.mockserver.metadorDispatcher
import dev.gmetal.metador.mockserver.successMockResponse
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.hamcrest.CoreMatchers.`is` as _is

@ExperimentalCoroutinesApi
class OkHttp3ResourceRetrieverTest {

    @MockK
    lateinit var mockMetadorSuccess: Metador.SuccessCallback

    @MockK
    lateinit var mockMetadorFailure: Metador.FailureCallback

    @MockK
    lateinit var mockResourceParserDelegate: ResourceParserDelegate

    private lateinit var mockWebServer: MockWebServer
    private lateinit var resourceRetrieverInTest: OkHttp3ResourceRetriever
    private val testCoroutineDispatcher = TestCoroutineDispatcher()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockWebServer = MockWebServer()
        mockWebServer.start(MOCK_WEB_SERVER_PORT)
        resourceRetrieverInTest = OkHttp3ResourceRetriever(testCoroutineDispatcher)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        testCoroutineDispatcher.cleanupTestCoroutines()
    }

    @Timeout(5, unit = TimeUnit.SECONDS)
    @Test
    fun `a ResourceNotFoundException is thrown when the remote resource cannot be found`() =
        runBlockingTest {
            val notFoundUrl = urlToResponse("asdf", errorMockResponse(HTTP_NOT_FOUND))

            val exception = assertThrows<ResourceNotFoundException> {
                resourceRetrieverInTest.retrieveResource(metadorRequest(notFoundUrl))
            }
            assertThat(exception, instanceOf(ResourceNotFoundException::class.java))
        }

    @Timeout(5, unit = TimeUnit.SECONDS)
    @Test
    fun `a ServerErrorException is thrown when the remote server returns a 5XX response status`() =
        runBlockingTest {
            val serverErrorUrl = urlToResponse("asdf", errorMockResponse(HTTP_INTERNAL_ERROR))

            val exception = assertThrows<ServerErrorException> {
                resourceRetrieverInTest.retrieveResource(metadorRequest(serverErrorUrl))
            }

            with(exception) {
                assertAll(
                    { assertThat(httpCode, _is(HTTP_INTERNAL_ERROR)) }
                )
            }
        }

    @Timeout(5, unit = TimeUnit.SECONDS)
    @Test
    fun `an EmptyResponseException is thrown when the remote server returns an empty response`() =
        runBlockingTest {
            val emptyResponseUrl = urlToResponse("empty_response", successMockResponse(204, ""))

            assertThrows<EmptyResponseException>("Should throw EmptyResponseException") {
                resourceRetrieverInTest.retrieveResource(metadorRequest(emptyResponseUrl))
            }
        }

    @Timeout(5, unit = TimeUnit.SECONDS)
    @ParameterizedTest
    @ValueSource(ints = [400, 401, 402, 403, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415])
    fun `an UnknownNetworkException is thrown when the remote server returns an unknown error response that cannot be handled`(
        unknownResponseCode: Int
    ) =
        runBlockingTest {
            val emptyResponseUrl =
                urlToResponse("unknown_response", errorMockResponse(unknownResponseCode))

            val exception = assertThrows<UnknownNetworkException> {
                resourceRetrieverInTest.retrieveResource(metadorRequest(emptyResponseUrl))
            }

            assertThat(exception, instanceOf(UnknownNetworkException::class.java))
        }

    @Test
    fun `the OkHttp resource retriever can be instructed to bypass the cache`() =
        runBlockingTest {
            val noCacheUrl = urlToResponse(
                "no_cache",
                successMockResponse(resourceBody = HTML_DOCUMENT_WITHOUT_META)
            )

            val response = resourceRetrieverInTest.retrieveResource(
                metadorRequest(
                    noCacheUrl,
                    REQUEST_FRESH_COPY
                )
            )

            assertThat(response, _is(HTML_DOCUMENT_WITHOUT_META))
            assertThat(
                (mockWebServer.dispatcher as MetadorDispatcher).capturedRequestHeadersTable["/no_cache"]?.get(
                    "Cache-Control"
                ),
                _is("no-cache")
            )
        }

    @Test
    fun `the OkHttp resource retriever will use a previously cached copy when instructed to do so, honouring the max-age header`(
        @TempDir tempDir: Path
    ) = runBlockingTest {
        val noCacheUrl = urlToResponse(
            "fetch_from_network_cache",
            successMockResponse(resourceBody = HTML_DOCUMENT_WITHOUT_META)
                .addHeader("cache-control: max-age=6000")
        )
        resourceRetrieverInTest.configureCache(tempDir.toAbsolutePath().toString(), 1024 * 1024)

        val firstResponse =
            resourceRetrieverInTest.retrieveResource(metadorRequest(noCacheUrl, 100))
        val secondResponse =
            resourceRetrieverInTest.retrieveResource(metadorRequest(noCacheUrl, 100))

        assertThat(firstResponse, _is(HTML_DOCUMENT_WITHOUT_META))
        assertThat(secondResponse, _is(HTML_DOCUMENT_WITHOUT_META))
        assertThat((mockWebServer.dispatcher as MetadorDispatcher).noOfInteractions, _is(1))
    }

    private fun urlToResponse(url: String, toResponse: MockResponse): String {
        return "$BASE_URL/$url".apply {
            mockWebServer.dispatcher = metadorDispatcher {
                addResponse(this@apply, toResponse)
            }
        }
    }

    private fun metadorRequest(uri: String, maxSecondsCached: Int = DEFAULT_MAX_AGE_CACHE_SECONDS) =
        Metador.Request(
            uri,
            maxSecondsCached,
            mockResourceParserDelegate,
            mockMetadorSuccess,
            mockMetadorFailure
        )
}
