package dev.gmetal.metador.resource.retriever

import dev.gmetal.metador.DEFAULT_MAX_AGE_CACHE_SECONDS
import dev.gmetal.metador.EmptyResponseException
import dev.gmetal.metador.HTML_DOCUMENT_WITHOUT_META
import dev.gmetal.metador.Metador
import dev.gmetal.metador.REQUEST_FRESH_COPY
import dev.gmetal.metador.ResourceNotFoundException
import dev.gmetal.metador.ResourceParserDelegate
import dev.gmetal.metador.ServerErrorException
import dev.gmetal.metador.UnknownNetworkException
import dev.gmetal.metador.mockserver.BASE_URL
import dev.gmetal.metador.mockserver.MOCK_WEB_SERVER_PORT
import dev.gmetal.metador.mockserver.errorMockResponse
import dev.gmetal.metador.mockserver.successMockResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempdir
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.mockserver.client.MockServerClient
import org.mockserver.configuration.ConfigurationProperties
import org.mockserver.mock.Expectation
import org.mockserver.model.ExpectationId
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.RequestDefinition

@OptIn(ExperimentalTime::class)
@ExperimentalCoroutinesApi
class KtorClientResourceRetrieverTest : BehaviorSpec({
    lateinit var mockMetadorSuccess: Metador.SuccessCallback
    lateinit var mockMetadorFailure: Metador.FailureCallback
    lateinit var mockResourceParserDelegate: ResourceParserDelegate
    lateinit var mockFileStorage: FileStorage

    lateinit var resourceRetrieverInTest: KtorClientResourceRetriever

    val testCoroutineDispatcher = UnconfinedTestDispatcher()
    fun metadorRequest(uri: String, maxSecondsCached: Int = DEFAULT_MAX_AGE_CACHE_SECONDS) =
        Metador.Request(
            uri,
            maxSecondsCached,
            mockResourceParserDelegate,
            mockMetadorSuccess,
            mockMetadorFailure
        )

    var listener: MockServerListener = listener(MockServerListener(MOCK_WEB_SERVER_PORT))
    val mockServerClient = MockServerClient("localhost", MOCK_WEB_SERVER_PORT)

    ConfigurationProperties.logLevel("DEBUG")
    ConfigurationProperties.disableSystemOut(true)

    beforeContainer {
        mockMetadorSuccess = mockk()
        mockMetadorFailure = mockk()
        mockResourceParserDelegate = mockk()
        mockFileStorage = mockk()
        resourceRetrieverInTest = KtorClientResourceRetriever(
            { mockFileStorage }, testCoroutineDispatcher
        )
    }

    afterContainer {
        testCoroutineDispatcher.cancel()
    }
    lateinit var currentExpections: Array<Expectation>

    afterTest {
        currentExpections.forEach { expectation ->
            mockServerClient.clear(ExpectationId().withId(expectation.id))
        }
    }
    Given("a remote resource") {
        val remoteResource = "remote_resource"
        When("it cannot be found") {
            val notFoundUrl = responseUrl(remoteResource)
            beforeTest {
                currentExpections = mockServerClient.addExpectation(
                    remoteResource,
                    errorMockResponse(HttpStatusCode.NotFound.value)
                )
            }
            Then("a ResourceNotFoundException is thrown") {
                val exception = shouldThrow<ResourceNotFoundException> {
                    resourceRetrieverInTest.retrieveResource(metadorRequest(notFoundUrl))
                }
                exception shouldBe instanceOf(ResourceNotFoundException::class)
            }
        }

        When("the server encounters an internal error") {
            val serverErrorUrl = responseUrl(remoteResource)
            beforeTest {
                currentExpections = mockServerClient.addExpectation(
                    remoteResource,
                    errorMockResponse(HttpStatusCode.InternalServerError.value)
                )
            }
            Then("a ServerErrorException is thrown") {
                val exception = shouldThrow<ServerErrorException> {
                    resourceRetrieverInTest.retrieveResource(metadorRequest(serverErrorUrl))
                }

                exception.httpCode shouldBe HttpStatusCode.InternalServerError.value
            }
        }

        When("the server returns an empty response") {
            val emptyResponseUrl = responseUrl(remoteResource)
            beforeTest {
                currentExpections = mockServerClient.addExpectation(
                    remoteResource,
                    successMockResponse(204)
                )
            }

            Then("An EmptyResponseException is thrown") {
                shouldThrow<EmptyResponseException> {
                    resourceRetrieverInTest.retrieveResource(metadorRequest(emptyResponseUrl))
                }
            }
        }

        withData(
            ts = listOf(
                400,
                401,
                402,
                403,
                405,
                406,
                407,
                408,
                409,
                410,
                411,
                412,
                413,
                414,
                415
            )
        ) { responseCode ->
            beforeContainer {
                currentExpections = mockServerClient.addExpectation(
                    remoteResource,
                    errorMockResponse(responseCode)
                )
            }
            When("the server returns the $responseCode response code") {
                val emptyResponseUrl = responseUrl(remoteResource)

                Then("an UnknownNetworkException is thrown") {
                    val exception = shouldThrow<UnknownNetworkException> {
                        resourceRetrieverInTest.retrieveResource(metadorRequest(emptyResponseUrl))
                    }
                    exception shouldBe instanceOf(UnknownNetworkException::class)
                }
            }
        }

        When("the Metador.Request instructs us to bypass the cache") {
            val noCacheUrl = responseUrl(remoteResource)
            currentExpections = mockServerClient.addExpectation(
                remoteResource,
                successMockResponse(resourceBody = HTML_DOCUMENT_WITHOUT_META)
            )

            val response = resourceRetrieverInTest.retrieveResource(
                metadorRequest(
                    noCacheUrl,
                    REQUEST_FRESH_COPY
                )
            )

            Then("the resource retriever requests the resource from the network") {
                response shouldBe HTML_DOCUMENT_WITHOUT_META
                with(
                    listener.mockServer?.retrieveRecordedRequests(
                        request()
                            .withPath("/$remoteResource")
                    ) as Array<RequestDefinition>
                ) {
                    size shouldBe 1
                    (this[0] as HttpRequest).headers.getValues("Cache-Control") shouldBe listOf("no-cache")
                }
            }
        }

        When("the Metador.Request instructs us to use a cached copy if available") {
            val cachedUrl = responseUrl(remoteResource)
            val time = Clock.System.now().toEpochMilliseconds()
            every { mockFileStorage.isUsable() } returns true
            coJustRun { mockFileStorage.store(any(), any()) }
            resourceRetrieverInTest.configureCache(tempdir().absolutePath, 1024 * 1024)
            coEvery { mockFileStorage.findAll(any()) } coAnswers {
                setOf(
                    CachedResponseData(
                        url = invocation.args[0] as Url,
                        statusCode = HttpStatusCode.OK,
                        requestTime = GMTDate(time),
                        responseTime = GMTDate(time),
                        version = HttpProtocolVersion.HTTP_1_1,
                        expires = GMTDate(time.plus(60000L)),
                        headers = Headers.Empty,
                        varyKeys = emptyMap(),
                        body = HTML_DOCUMENT_WITHOUT_META.toByteArray()
                    )
                )
            }
            val secondResponse =
                resourceRetrieverInTest.retrieveResource(metadorRequest(cachedUrl, 6000))

            Then("the resource retriever will use the cached copy") {
                secondResponse shouldBe HTML_DOCUMENT_WITHOUT_META
                with(
                    listener.mockServer?.retrieveRecordedRequests(
                        request()
                            .withPath("/$remoteResource")
                    ) as Array<RequestDefinition>
                ) {
                    size shouldBe 0
                }
            }
        }
    }
})

private fun responseUrl(url: String) = "$BASE_URL/$url"
private fun MockServerClient.addExpectation(
    resource: String,
    toResponse: HttpResponse
): Array<Expectation> {
    return this.`when`(
        request()
            .withMethod("GET")
            .withPath("/$resource")
    ).respond(
        toResponse
    )
}
