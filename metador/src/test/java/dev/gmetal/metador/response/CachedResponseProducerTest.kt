package dev.gmetal.metador.response

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceNotFoundException
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsNot.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.core.Is.`is` as _is

private const val RESPONSE_CACHE_SIZE = 2

@ExperimentalCoroutinesApi
class CachedResponseProducerTest {
    @MockK
    lateinit var mockSuccessCallback: Metador.SuccessCallback

    @MockK
    lateinit var mockFailureCallback: Metador.FailureCallback

    lateinit var cachedResponseProducerInTest: CachedResponseProducer

    @BeforeEach
    fun setup() {
        cachedResponseProducerInTest =
            CachedResponseProducer(RESPONSE_CACHE_SIZE) { System.currentTimeMillis() }
        MockKAnnotations.init(this)
    }

    @Test
    fun `the CachedResponseProducer does not handles requests when it is empty`() {
        val fakeUri = "http://localhost/fake_uri"

        val canHandle = cachedResponseProducerInTest.canHandleRequest(defaultRequest(fakeUri))

        assertThat(canHandle, _is(false))
    }

    @Test
    fun `the CachedResponseProducer does not handle requests that require data from the network`() {
        val fakeUri = "http://localhost/fake_uri"
        val fakeData = mapOf("a" to "d")
        cachedResponseProducerInTest.cacheResponse(fakeUri, fakeData)

        val canHandle = cachedResponseProducerInTest.canHandleRequest(
            Metador.Request.Builder(fakeUri)
                .onSuccess(mockSuccessCallback)
                .onFailure(mockFailureCallback)
                .requestFromNetwork()
                .build()
        )

        assertThat(canHandle, _is(false))
    }

    @Test
    fun `the CachedResponseProducer handles requests that allow cached content and their URI is already cached`() {
        val fakeUri = "http://localhost/fake_uri"
        val fakeData = mapOf("a" to "d")
        cachedResponseProducerInTest.cacheResponse(fakeUri, fakeData)

        val canHandle = cachedResponseProducerInTest.canHandleRequest(defaultRequest(fakeUri))

        assertThat(canHandle, _is(true))
    }

    @Test
    fun `the cacheResponse method adds an item in the cache`() {
        val fakeUri = "http://localhost/fake_uri"
        val fakeData = mapOf("a" to "d")
        val request = defaultRequest(fakeUri)
        val initialResponsePresence = cachedResponseProducerInTest.canHandleRequest(request)

        cachedResponseProducerInTest.cacheResponse(fakeUri, fakeData)
        val afterCacheResponsePresence = cachedResponseProducerInTest.canHandleRequest(request)

        assertThat(initialResponsePresence, _is(false))
        assertThat(afterCacheResponsePresence, _is(not(initialResponsePresence)))
    }

    @Test
    fun `when caching more items than the response cache can handle, the oldest items are removed in favour of the newest ones`() {
        val fakeUri = "http://localhost/fake_uri"
        val fakeUri2 = "http://localhost/fake_uri2"
        val fakeUri3 = "http://localhost/fake_uri3"
        val fakeRequest = defaultRequest(fakeUri)
        val fakeRequest2 = defaultRequest(fakeUri2)
        val fakeRequest3 = defaultRequest(fakeUri3)
        val fakeData = mapOf("a" to "d")

        cachedResponseProducerInTest.cacheResponse(fakeUri, fakeData)
        cachedResponseProducerInTest.cacheResponse(fakeUri2, fakeData)
        val couldHandleFakeUriRequest = cachedResponseProducerInTest.canHandleRequest(fakeRequest)
        cachedResponseProducerInTest.cacheResponse(fakeUri3, fakeData)

        assertThat(cachedResponseProducerInTest.canHandleRequest(fakeRequest), _is(false))
        assertThat(
            cachedResponseProducerInTest.canHandleRequest(fakeRequest),
            _is(not(couldHandleFakeUriRequest))
        )
        assertThat(cachedResponseProducerInTest.canHandleRequest(fakeRequest2), _is(true))
        assertThat(cachedResponseProducerInTest.canHandleRequest(fakeRequest3), _is(true))
    }

    @Test
    fun `requests that cannot be handled will not produce ResultNotFound failure`() =
        runBlockingTest {
            val fakeUri = "http://localhost/fake_uri"
            val request = defaultRequest(fakeUri)

            val canHandle = cachedResponseProducerInTest.canHandleRequest(request)
            val response = cachedResponseProducerInTest.produceResponse(request)

            assertThat(canHandle, _is(false))
            assertThat(response.getError(), _is(ResourceNotFoundException))
        }

    @Test
    fun `requests that can be handled will produce a successful result with the stored data`() =
        runBlockingTest {
            val fakeUri = "http://localhost/fake_uri"
            val fakeData = mapOf("fakeKey" to "fakeData")
            val request = defaultRequest(fakeUri)
            cachedResponseProducerInTest.cacheResponse(fakeUri, fakeData)

            val canHandle = cachedResponseProducerInTest.canHandleRequest(request)
            val response = cachedResponseProducerInTest.produceResponse(request)

            assertThat(canHandle, _is(true))
            assertThat(response.get(), _is(fakeData))
        }

    private fun defaultRequest(uri: String): Metador.Request =
        Metador.Request.Builder(uri)
            .onSuccess(mockSuccessCallback)
            .onFailure(mockFailureCallback)
            .build()
}
