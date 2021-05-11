package dev.gmetal.metador.mockserver

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

const val MOCK_WEB_SERVER_PORT = 44444
const val BASE_URL = "http://localhost:$MOCK_WEB_SERVER_PORT"

internal class MetadorDispatcher : Dispatcher() {
    private val requestResponseTable = hashMapOf<String, MockResponse>()
    val capturedRequestHeadersTable = hashMapOf<String, Map<String, String>>()
    var noOfInteractions: Int = 0

    fun addResponse(request: String, response: MockResponse) {
        requestResponseTable[request.removePrefix(BASE_URL)] = response
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        println("${request.path} is contained in the response table = ${request.path in requestResponseTable}")
        println(request.headers.toMap())
        noOfInteractions++
        if (request.path in requestResponseTable) {
            capturedRequestHeadersTable[request.path.orEmpty()] = request.headers.toMap()
            return requestResponseTable[request.path]!!
        }

        throw IllegalArgumentException("Request not found")
    }
}

internal fun metadorDispatcher(configure: MetadorDispatcher.() -> Unit): Dispatcher =
    MetadorDispatcher().apply(configure)

internal fun errorMockResponse(httpStatus: Int): MockResponse =
    MockResponse().setResponseCode(httpStatus)

internal fun successMockResponse(httpStatus: Int = 200, resourceBody: String): MockResponse =
    MockResponse()
        .setResponseCode(httpStatus)
        .setBody(resourceBody)
