package dev.gmetal.metador.response

import dev.gmetal.metador.Metador

/**
 * Common interface of all Response producing entities
 */
interface ResponseProducer {
    /**
     * Returns whether this entity can handle the specified request
     *
     * @param request the request to check
     * @return true if this ResponseProducer can handle the request, false otherwise
     */
    fun canHandleRequest(request: Metador.Request): Boolean

    /**
     * Produces a response from the specified request. The response will always be produced,
     * but it may or may not be successful
     *
     * @param request the request to check
     * @return a Result-wrapped map, that indicates if the response was successful or not
     */
    suspend fun produceResponse(request: Metador.Request): Result<Map<String, String>>
}
