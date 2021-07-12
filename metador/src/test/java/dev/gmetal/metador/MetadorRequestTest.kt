package dev.gmetal.metador

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class MetadorRequestTest : BehaviorSpec({
    lateinit var mockSuccess: Metador.SuccessCallback
    lateinit var mockFailure: Metador.FailureCallback

    beforeContainer {
        mockSuccess = mockk()
        mockFailure = mockk()
        Dispatchers.setMain(TestCoroutineDispatcher())
    }

    afterContainer {
        Dispatchers.resetMain()
    }

    Given("a String URL") {
        val url = "http://localhost/test"

        When("we attempt to create a Metador Request through the builder with a success callback") {
            Then("a RuntimeException is thrown") {
                shouldThrow<RuntimeException> {
                    Metador.Request.Builder(url)
                        .build()
                }
            }
        }

        When("we supply a success callback to the Metador Request builder") {
            val request = Metador.Request.Builder(url)
                .onSuccess(mockSuccess)
                .build()

            Then("the Metador Request created uses the supplied URI and success callback ") {
                request.uri shouldBe url
                request.successCallback shouldBe mockSuccess
            }

            Then("the Metador Request created uses the default maximum number of seconds cached value  ") {
                request.maxSecondsCached shouldBe DEFAULT_MAX_AGE_CACHE_SECONDS
            }

            Then("the Metador Request created uses a default failure callback") {
                request.failureCallback shouldNotBe mockFailure
            }
        }

        When("we supply all available parameters to the Metador Request builder") {
            val maxAgeSecondsCached = 50

            val request = Metador.Request.Builder(url)
                .maximumSecondsCached(maxAgeSecondsCached)
                .onSuccess(mockSuccess)
                .onFailure(mockFailure)
                .build()

            Then("the generated request contains the parameters used") {
                request.uri shouldBe url
                request.maxSecondsCached shouldBe maxAgeSecondsCached
                request.successCallback shouldBe mockSuccess
                request.failureCallback shouldBe mockFailure
            }
        }

        When("we use the Metador.request method") {
            val requestBuilder: Metador.Request.Builder = Metador.request(url)
            Then("we receive a default Metador Request builder instance ready for use") {
                requestBuilder.url shouldBe url
            }
        }
    }
})
