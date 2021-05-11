package dev.gmetal.metador

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.hamcrest.CoreMatchers.`is` as _is

@ExperimentalCoroutinesApi
class ResourceParserTest {
    @MockK
    lateinit var mockResourceParserDelegate: ResourceParserDelegate

    private lateinit var objectInTest: ResourceParser

    private val testCoroutineDispatcher = TestCoroutineDispatcher()

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        objectInTest = ResourceParser(testCoroutineDispatcher)
    }

    @Test
    fun `the resource parser delegates it's work to the supplied ResourceParserDelegate and returns the result to the caller`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            val expectedResult = mapOf("key" to "value")
            every { mockResourceParserDelegate.parseResource(fakeResource) } returns expectedResult

            val result = objectInTest.parseResource(mockResourceParserDelegate, fakeResource)

            verify { mockResourceParserDelegate.parseResource(fakeResource) }
            assertThat(result, _is(expectedResult))
        }

    @Test
    fun `exceptions thrown by the ResourceParserDelegate are propagated to the caller`() =
        runBlockingTest {
            val fakeResource = "fake_resource"
            every { mockResourceParserDelegate.parseResource(fakeResource) } throws RuntimeException(
                "Error"
            )

            assertThrows<RuntimeException> {
                objectInTest.parseResource(mockResourceParserDelegate, fakeResource)
            }
        }
}
