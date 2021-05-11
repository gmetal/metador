package dev.gmetal.metador

import com.github.michaelbull.result.Ok
import dev.gmetal.metador.response.CachedResponseProducer
import dev.gmetal.metador.response.ResponseProducer
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import io.mockk.verifyAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.CoreMatchers.`is` as _is

@ExperimentalCoroutinesApi
class MetadorTest {
    @MockK
    lateinit var mockResourceRetriever: ResourceRetriever

    @MockK
    lateinit var mockResourceParserDelegate: ResourceParserDelegate

    @MockK
    lateinit var mockSuccessCallback: Metador.SuccessCallback

    @MockK
    lateinit var mockFailureCallback: Metador.FailureCallback

    @MockK
    lateinit var mockCachedResponseProducer: CachedResponseProducer

    @MockK
    lateinit var mockNetworkResponseProducer: ResponseProducer

    private val testCoroutineDispatcher = TestCoroutineDispatcher()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testCoroutineDispatcher)
    }

    @Test
    fun `metador's physical cache and physical size settings are set to the resource retriever, and the response cache size is used in the CachedResponseProducer`() {
        val fakeCacheDirectory = "/tmp/cache"
        val fakeCacheSize = 1024L
        val fakeResponseCacheSize = 50
        every { mockResourceRetriever.configureCache(any(), any()) } just Runs

        val metador = metadorBuilder(
            cacheDirectory = fakeCacheDirectory,
            physicalCacheSize = fakeCacheSize,
            responseCacheSize = fakeResponseCacheSize,
            resourceRetriever = mockResourceRetriever,
        )

        verify { mockResourceRetriever.configureCache(fakeCacheDirectory, fakeCacheSize) }
        assertThat(metador.cachedResponseProducer.responseCacheSize, _is(fakeResponseCacheSize))
    }

    @Test
    fun `the parsing of retrieved resources is delegated to the request's ResourceParserDelegate`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns emptyMap()
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
            val metador = metadorBuilder(resourceRetriever = mockResourceRetriever)

            metador.process(
                defaultRequest(
                    "http://localhost/resource_parser_delegate",
                    mockResourceParserDelegate
                )
            )

            verifyAll {
                mockFailureCallback wasNot called
                mockResourceParserDelegate.parseResource(fakeResource)
            }
        }

    @Test
    fun `exceptions thrown by the resource retriever are propagated to the failure callback`() =
        runBlockingTest {
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
            coEvery { mockResourceRetriever.retrieveResource(any()) } throws ResourceNotFoundException
            val metador = metadorBuilder(resourceRetriever = mockResourceRetriever)

            metador.process(defaultRequest("http://localhost/test"))

            verifyAll {
                mockFailureCallback.onError(ResourceNotFoundException)
                mockSuccessCallback wasNot called
            }
        }

    @Test
    fun `exceptions thrown by the resource parser are propagated to the failure callback`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
            every { mockResourceParserDelegate.parseResource(fakeResource) } throws RuntimeException(
                "Unknown error"
            )
            val metador = metadorBuilder(resourceRetriever = mockResourceRetriever)

            metador.process(
                defaultRequest("http://localhost/test", mockResourceParserDelegate)
            )

            verifyAll {
                mockFailureCallback.onError(any<RuntimeException>())
                mockSuccessCallback wasNot called
            }
        }

    @Test
    fun `the successful result is sent to the success callback and cached`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            val expectedResult = mapOf("key" to "value")
            every { mockSuccessCallback.onSuccess(expectedResult) } just Runs
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
            every { mockCachedResponseProducer.cacheResponse(any(), any(), any()) } just Runs
            every { mockCachedResponseProducer.canHandleRequest(any()) } returns false
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns expectedResult
            val metador = metadorBuilder(
                resourceRetriever = mockResourceRetriever,
                cachedResponseProducer = mockCachedResponseProducer
            )

            metador.process(defaultRequest("http://localhost/test", mockResourceParserDelegate))

            verifyAll {
                mockSuccessCallback.onSuccess(expectedResult)
                mockFailureCallback wasNot called
            }
        }

    @Test
    fun `cached responses will be preferred if available and the result is not cached again`() =
        runBlockingTest {
            val fakeUri = "http://localhost/test"
            val expectedResult = mapOf("key" to "value")
            val request = defaultRequest(fakeUri, mockResourceParserDelegate)
            every { mockSuccessCallback.onSuccess(expectedResult) } just Runs
            every { mockCachedResponseProducer.canHandleRequest(any()) } returns true
            coEvery { mockCachedResponseProducer.produceResponse(request) } returns Ok(
                expectedResult
            )
            val metador = metadorBuilder(
                resourceRetriever = mockResourceRetriever,
                cachedResponseProducer = mockCachedResponseProducer,
                networkResponseProducer = mockNetworkResponseProducer
            )

            metador.process(request)

            verify {mockCachedResponseProducer.canHandleRequest(eq(request))}
            verify { mockSuccessCallback.onSuccess(expectedResult) }
            verify {mockNetworkResponseProducer wasNot called}
            coVerify(exactly = 1) { mockCachedResponseProducer.produceResponse(request) }
            verify(exactly = 0) { mockCachedResponseProducer.cacheResponse(any(), any(), any()) }
        }

    @Test
    fun `when no cached responses are available then the response will be requested from the network`() =
        runBlockingTest {
            val fakeUri = "http://localhost/test"
            val expectedResult = mapOf("key" to "value")
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
            every { mockCachedResponseProducer.cacheResponse(any(), any(), any()) } just Runs
            every { mockSuccessCallback.onSuccess(any()) } just Runs
            every { mockCachedResponseProducer.canHandleRequest(any()) } returns false
            coEvery { mockNetworkResponseProducer.produceResponse(any()) } returns Ok(
                expectedResult
            )
            val metador = metadorBuilder(
                cachedResponseProducer = mockCachedResponseProducer,
                networkResponseProducer = mockNetworkResponseProducer
            )
            val request = defaultRequest(fakeUri, mockResourceParserDelegate)

            metador.process(request)

            verifyAll {
                mockCachedResponseProducer.canHandleRequest(request)
                mockCachedResponseProducer.cacheResponse(
                    fakeUri,
                    expectedResult,
                    request.maxSecondsCached * 1000L
                )
                mockSuccessCallback.onSuccess(expectedResult)
            }
            coVerifyAll {
                mockNetworkResponseProducer.produceResponse(request)
            }
            coVerify(exactly = 0) { mockCachedResponseProducer.produceResponse(any()) }
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

    private fun metadorBuilder(
        resourceRetriever: ResourceRetriever = OkHttp3ResourceRetriever(),
        cacheDirectory: String = "",
        physicalCacheSize: Long = DEFAULT_PHYSICAL_CACHE_SIZE_BYTES,
        responseCacheSize: Int = DEFAULT_MAX_RESPONSE_CACHE_SIZE,
        cachedResponseProducer: CachedResponseProducer? = null,
        networkResponseProducer: ResponseProducer? = null
    ) = Metador.Builder()
        .withCacheDirectory(cacheDirectory)
        .withPhysicalCacheSize(physicalCacheSize)
        .withResponseCacheSize(responseCacheSize)
        .withResourceRetriever(resourceRetriever)
        .withBackgroundDispatcher(testCoroutineDispatcher)
        .apply {
            if (cachedResponseProducer != null) {
                withCachedResponseProducer(cachedResponseProducer)
            }
        }
        .apply {
            if (networkResponseProducer != null) {
                withNetworkResponseProducer(networkResponseProducer)
            }
        }
        .build()
}
