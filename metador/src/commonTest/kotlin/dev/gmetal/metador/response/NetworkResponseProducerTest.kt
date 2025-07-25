package dev.gmetal.metador.response

import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceNotFoundException
import dev.gmetal.metador.ResourceParserDelegate
import dev.gmetal.metador.ResourceRetriever
import dev.gmetal.metador.defaultResourceParserDelegate
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.mockk.called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
class NetworkResponseProducerTest : BehaviorSpec({
    lateinit var mockResourceRetriever: ResourceRetriever
    lateinit var mockResourceParserDelegate: ResourceParserDelegate
    lateinit var mockSuccessCallback: Metador.SuccessCallback
    lateinit var mockFailureCallback: Metador.FailureCallback
    lateinit var networkResponseProducerInTest: NetworkResponseProducer
    val testCoroutineDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    fun defaultRequest(
        uri: String,
        resourceParserDelegate: ResourceParserDelegate = defaultResourceParserDelegate()
    ): Metador.Request =
        Metador.Request.Builder(uri)
            .withResourceParser(resourceParserDelegate)
            .onSuccess(mockSuccessCallback)
            .onFailure(mockFailureCallback)
            .build()

    beforeContainer {
        mockResourceRetriever = mockk()
        mockResourceParserDelegate = mockk()
        mockSuccessCallback = mockk()
        mockFailureCallback = mockk()
        networkResponseProducerInTest =
            NetworkResponseProducer(mockResourceRetriever, testCoroutineDispatcher)
    }

    Given("a NetworkResponseProducer") {
        val fakeResource = "fake_resource"
        When("it is asked whether it can handle a request") {
            Then("it always responds that it can") {
                networkResponseProducerInTest.canHandleRequest(defaultRequest("")) shouldBe true
                networkResponseProducerInTest.canHandleRequest(defaultRequest("asdf")) shouldBe true
                networkResponseProducerInTest.canHandleRequest(defaultRequest("http://localhost/fake_uri")) shouldBe true
            }
        }

        When("it retrieves a network resource") {
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns emptyMap()
            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest(
                    "http://localhost/resource_parser_delegate",
                    mockResourceParserDelegate
                )
            )
            Then("it delegates the parsing to the request's ResourceParserDelegate") {
                result.getOrNull() shouldBe emptyMap()
                verify { mockFailureCallback wasNot called }
                verify { mockSuccessCallback wasNot called }
            }
        }

        When("an exception is thrown by the resource retriever") {
            coEvery { mockResourceRetriever.retrieveResource(any()) } throws ResourceNotFoundException

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest("http://localhost/test")
            )
            Then("it is encapsulated in a result and returned") {
                result.exceptionOrNull() shouldBe ResourceNotFoundException
                verify { mockFailureCallback wasNot called }
                verify { mockSuccessCallback wasNot called }
            }
        }

        When("an exception is thrown by the resource parser") {
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } throws RuntimeException(
                "Unknown error"
            )

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest("http://localhost/test", mockResourceParserDelegate)
            )
            Then("it is propagated to the failure callback") {
                result.exceptionOrNull() shouldBe instanceOf(RuntimeException::class)
                verify { mockFailureCallback wasNot called }
                verify { mockSuccessCallback wasNot called }
            }
        }

        When("a result is acquired") {
            val expectedResult = mapOf("key" to "value")
            coEvery { mockResourceRetriever.retrieveResource(any()) } returns fakeResource
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns expectedResult

            val result = networkResponseProducerInTest.produceResponse(
                defaultRequest("http://localhost/test", mockResourceParserDelegate)
            )

            Then("it is propagated to the success callback") {
                result.getOrNull() shouldBe expectedResult
                verify { mockFailureCallback wasNot called }
                verify { mockSuccessCallback wasNot called }
            }
        }
    }
})
