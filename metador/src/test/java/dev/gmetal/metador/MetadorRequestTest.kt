package dev.gmetal.metador

import io.mockk.mockk
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.hamcrest.CoreMatchers.`is` as _is

class MetadorRequestTest {

    lateinit var mockSuccess: Metador.SuccessCallback
    lateinit var mockFailure: Metador.FailureCallback

    @BeforeEach
    fun setup() {
        mockSuccess = mockk()
        mockFailure = mockk()
    }

    @Test
    fun `building a metador request without a success callback results in a RuntimeException`() {
        val url = "http://localhost/test"

        assertThrows<RuntimeException> {
            Metador.Request.Builder(url)
                .build()
        }
    }

    @Test
    fun `metador requests require a URL and a success callback, all other parameters have default values`() {
        val url = "http://localhost/test"

        val request = Metador.Request.Builder(url)
            .onSuccess(mockSuccess)
            .build()

        assertThat(request.uri, _is(url))
        assertThat(request.maxSecondsCached, _is(DEFAULT_MAX_AGE_CACHE_SECONDS))
        assertThat(request.successCallback, _is(mockSuccess))
        assertThat(request.failureCallback, _is(not(mockFailure)))
    }

    @Test
    fun `a metador request is created with all parameters passed to a builder`() {
        val url = "http://localhost/test"
        val maxAgeSecondsCached = 50

        val request = Metador.Request.Builder(url)
            .maximumSecondsCached(maxAgeSecondsCached)
            .onSuccess(mockSuccess)
            .onFailure(mockFailure)
            .build()

        assertThat(request.uri, _is(url))
        assertThat(request.maxSecondsCached, _is(maxAgeSecondsCached))
        assertThat(request.successCallback, _is(mockSuccess))
        assertThat(request.failureCallback, _is(mockFailure))
    }

    @Test
    fun `the request method creates a default Request Builder for the specified URI`() {
        val url = "http://localhost/test"

        val requestBuilder: Metador.Request.Builder = Metador.request(url)

        assertThat(requestBuilder.url, _is(url))
    }
}
