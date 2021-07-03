package dev.gmetal.metador.mockserver

import org.mockserver.model.Headers
import org.mockserver.model.HttpResponse

const val MOCK_WEB_SERVER_PORT = 44444
const val BASE_URL = "http://localhost:$MOCK_WEB_SERVER_PORT"

internal fun errorMockResponse(httpStatus: Int): HttpResponse =
    HttpResponse.response().withStatusCode(httpStatus)

internal fun successMockResponse(
    httpStatus: Int = 200,
    resourceBody: String = "",
    headers: Headers = Headers()
): HttpResponse =
    HttpResponse()
        .withHeaders(headers)
        .withStatusCode(httpStatus)
        .withBody(resourceBody)
