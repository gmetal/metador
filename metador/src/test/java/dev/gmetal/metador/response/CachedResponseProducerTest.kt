package dev.gmetal.metador.response

import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceNotFoundException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk

private const val RESPONSE_CACHE_SIZE = 2

class CachedResponseProductTest : BehaviorSpec({
    lateinit var mockSuccessCallback: Metador.SuccessCallback

    lateinit var mockFailureCallback: Metador.FailureCallback

    lateinit var cachedResponseProducerInTest: CachedResponseProducer

    beforeContainer {
        mockSuccessCallback = mockk()
        mockFailureCallback = mockk()
        cachedResponseProducerInTest =
            CachedResponseProducer(RESPONSE_CACHE_SIZE) { System.currentTimeMillis() }
    }
    val fakeUri = "http://localhost/fake_uri"
    val fakeData = mapOf("a" to "d")
    fun defaultRequest(uri: String): Metador.Request =
        Metador.Request.Builder(uri)
            .onSuccess(mockSuccessCallback)
            .onFailure(mockFailureCallback)
            .build()

    Given("a CachedResponseProducer") {
        And("it is empty") {
            cachedResponseProducerInTest =
                CachedResponseProducer(RESPONSE_CACHE_SIZE) { System.currentTimeMillis() }

            When("a request is made") {
                val canHandle =
                    cachedResponseProducerInTest.canHandleRequest(defaultRequest(fakeUri))
                Then("it cannot be handled") {
                    canHandle shouldBe false
                }
            }
        }

        And(" it is not empty") {
            beforeContainer {
                cachedResponseProducerInTest.cacheResponse(fakeUri, fakeData)
            }
            When("the incoming request must be served from the network") {
                val canHandle = cachedResponseProducerInTest.canHandleRequest(
                    Metador.Request.Builder(fakeUri)
                        .onSuccess(mockSuccessCallback)
                        .onFailure(mockFailureCallback)
                        .requestFromNetwork()
                        .build()
                )
                Then("it cannot be handled") {
                    canHandle shouldBe false
                }
            }

            When("the incoming request can be served from the cache and its URI is cached") {
                val canHandle =
                    cachedResponseProducerInTest.canHandleRequest(defaultRequest(fakeUri))
                Then("it can be handled") {
                    canHandle shouldBe true
                }
            }

            When("a new item is added in the cache") {
                val fakeUriNew = "${fakeUri}2"
                val request = defaultRequest(fakeUriNew)
                val initiallyCached = cachedResponseProducerInTest.canHandleRequest(request)

                cachedResponseProducerInTest.cacheResponse(fakeUriNew, fakeData)
                val afterBeingCached = cachedResponseProducerInTest.canHandleRequest(request)
                Then("it exists in the cache and be retrieved") {
                    initiallyCached shouldBe false
                    afterBeingCached shouldNotBe initiallyCached
                }
            }

            When("the cache fills and a new item must be cached") {
                val fakeUri2 = "${fakeUri}2"
                val fakeUri3 = "${fakeUri}3"
                val fakeRequest = defaultRequest(fakeUri)
                val fakeRequest2 = defaultRequest(fakeUri2)
                val fakeRequest3 = defaultRequest(fakeUri3)
                cachedResponseProducerInTest.cacheResponse(fakeUri2, fakeData)
                val couldHandleFakeUriRequest =
                    cachedResponseProducerInTest.canHandleRequest(fakeRequest)
                cachedResponseProducerInTest.cacheResponse(fakeUri3, fakeData)

                Then("the old item is removed and the new item is cached") {
                    cachedResponseProducerInTest.canHandleRequest(fakeRequest) shouldBe false
                    cachedResponseProducerInTest.canHandleRequest(fakeRequest) shouldNotBe couldHandleFakeUriRequest
                    cachedResponseProducerInTest.canHandleRequest(fakeRequest2) shouldBe true
                    cachedResponseProducerInTest.canHandleRequest(fakeRequest3) shouldBe true
                }
            }

            When("a request cannot be handled but it is requested from the cache") {
                val fakeUri2 = "${fakeUri}2"
                val request = defaultRequest(fakeUri2)
                val canHandle = cachedResponseProducerInTest.canHandleRequest(request)
                val response = cachedResponseProducerInTest.produceResponse(request)

                Then("a ResultNotFoundException is returned") {
                    canHandle shouldBe false
                    response.exceptionOrNull() shouldBe ResourceNotFoundException
                }
            }

            When("a request can be handled") {
                val request = defaultRequest(fakeUri)
                val canHandle = cachedResponseProducerInTest.canHandleRequest(request)
                val response = cachedResponseProducerInTest.produceResponse(request)

                Then("a successful result is returned with the cached data") {
                    canHandle shouldBe true
                    response.getOrNull() shouldBe fakeData
                }
            }
        }
    }
})
