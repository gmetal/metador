package dev.gmetal.metador

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
class ResourceParserTest : BehaviorSpec({
    lateinit var mockResourceParserDelegate: ResourceParserDelegate
    lateinit var objectInTest: ResourceParser
    val testCoroutineDispatcher = UnconfinedTestDispatcher()

    beforeContainer {
        mockResourceParserDelegate = mockk()
        objectInTest = ResourceParser(testCoroutineDispatcher)
    }

    Given("a ResourceParser") {
        When("it is requested to parse a resource") {
            val fakeResource = "fake_resource"
            val expectedResult = mapOf("key" to "value")
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns expectedResult

            val result = objectInTest.parseResource(mockResourceParserDelegate, fakeResource)
            Then("it produces the result by delegating its work to the supplied ResourceParserDelegate") {
                verify { mockResourceParserDelegate.parseResource(fakeResource) }
                result shouldBe expectedResult
            }
        }

        When("an exception is thrown by the ResourceParserDelegate") {
            val fakeResource = "fake_resource"
            every { mockResourceParserDelegate.parseResource(fakeResource) } throws RuntimeException(
                "Error"
            )
            Then("it is propagated to the caller") {
                shouldThrow<RuntimeException> {
                    objectInTest.parseResource(mockResourceParserDelegate, fakeResource)
                }
            }
        }
    }
})
