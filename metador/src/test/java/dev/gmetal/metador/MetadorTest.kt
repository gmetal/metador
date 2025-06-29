package dev.gmetal.metador

import com.github.michaelbull.result.Ok
import dev.gmetal.metador.response.CachedResponseProducer
import dev.gmetal.metador.response.ResponseProducer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@ExperimentalCoroutinesApi
class MetadorTest : BehaviorSpec({
    lateinit var mockResourceRetriever: ResourceRetriever
    lateinit var mockSuccessCallback: Metador.SuccessCallback
    lateinit var mockFailureCallback: Metador.FailureCallback
    lateinit var mockCachedResponseProducer: CachedResponseProducer
    lateinit var mockNetworkResponseProducer: ResponseProducer

    beforeContainer {
        mockResourceRetriever = mockk()
        mockSuccessCallback = mockk()
        mockFailureCallback = mockk()
        mockCachedResponseProducer = mockk()
        mockNetworkResponseProducer = mockk()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterContainer {
        Dispatchers.resetMain()
    }

    Given("a set of Metador configuration values") {
        val fakeCacheDirectory = "/tmp/cache"
        val fakeCacheSize = 1024L
        val fakeResponseCacheSize = 50

        beforeContainer {
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
        }

        When("a Metador instance is created") {
            val metador = metadorBuilder(
                cacheDirectory = fakeCacheDirectory,
                physicalCacheSize = fakeCacheSize,
                responseCacheSize = fakeResponseCacheSize,
                resourceRetriever = mockResourceRetriever,
            )

            Then("the physical cache settings are set in the resource retriever") {
                verify { mockResourceRetriever.configureCache(fakeCacheDirectory, fakeCacheSize) }
            }
            Then("the response cache size is set in the CachedResponseProducer") {
                metador.cachedResponseProducer.responseCacheSize shouldBe fakeResponseCacheSize
            }
        }
    }

    Given("a Metador instance") {
        val fakeResource = "fake_resource"
        val expectedResult = mapOf("key" to "value")
        lateinit var request: Metador.Request
        lateinit var metador: Metador

        beforeContainer {
            every { mockResourceRetriever.configureCache(any(), any()) } just Runs
            every { mockCachedResponseProducer.cacheResponse(any(), any(), any()) } just Runs
            every { mockCachedResponseProducer.canHandleRequest(any()) } returns false
            every { mockSuccessCallback.onSuccess(expectedResult) } just Runs
            metador = metadorBuilder(
                resourceRetriever = mockResourceRetriever,
                cachedResponseProducer = mockCachedResponseProducer,
                networkResponseProducer = mockNetworkResponseProducer
            )
            request = defaultRequest(
                "http://localhost/resource_parser_delegate",
                mockSuccessCallback,
                mockFailureCallback
            )
        }

        When("a cached response is available") {
            every { mockCachedResponseProducer.canHandleRequest(any()) } returns true
            coEvery { mockCachedResponseProducer.produceResponse(request) } returns Ok(
                expectedResult
            )

            metador.process(request)

            Then("it is returned and not cached again") {
                verify { mockCachedResponseProducer.canHandleRequest(eq(request)) }
                verify { mockSuccessCallback.onSuccess(expectedResult) }
                verify { mockNetworkResponseProducer wasNot called }
                coVerify(exactly = 1) { mockCachedResponseProducer.produceResponse(request) }
                verify(exactly = 0) {
                    mockCachedResponseProducer.cacheResponse(
                        any(),
                        any(),
                        any()
                    )
                }
            }
        }

        When("no cached responses are available") {
            every { mockCachedResponseProducer.cacheResponse(any(), any(), any()) } just Runs
            every { mockCachedResponseProducer.canHandleRequest(any()) } returns false
            coEvery { mockNetworkResponseProducer.produceResponse(any()) } returns Ok(
                expectedResult
            )

            metador.process(request)

            Then("the remote resource will be retrieved from the network and cached") {
                verifyAll {
                    mockCachedResponseProducer.canHandleRequest(request)
                    mockCachedResponseProducer.cacheResponse(
                        request.uri,
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
        }
    }
})

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
    .withBackgroundDispatcher(UnconfinedTestDispatcher())
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

private fun defaultRequest(
    uri: String,
    successCallback: Metador.SuccessCallback,
    failureCallback: Metador.FailureCallback,
    resourceParserDelegate: ResourceParserDelegate = HtmlMetaExtractor()
): Metador.Request =
    Metador.Request.Builder(uri)
        .withResourceParser(resourceParserDelegate)
        .onSuccess(successCallback)
        .onFailure(failureCallback)
        .build()
