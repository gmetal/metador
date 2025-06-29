package dev.gmetal.metador.response

import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceParser
import dev.gmetal.metador.ResourceRetriever
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A ResponseProducer subclass that produces responses from the network
 *
 * @param resourceRetriever the [ResourceRetriever] to use
 * @param backgroundDispatcher the [CoroutineDispatcher] to use for doing all operations
 */
class NetworkResponseProducer(
    private val resourceRetriever: ResourceRetriever,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ResponseProducer {
    private val resourceParser: ResourceParser = ResourceParser(backgroundDispatcher)
    override fun canHandleRequest(request: Metador.Request): Boolean = true

    override suspend fun produceResponse(request: Metador.Request): Result<Map<String, String>> =
        withContext(backgroundDispatcher) {
            val resource = try {
                resourceRetriever.retrieveResource(request)
            } catch (exc: Exception) {
                return@withContext Result.failure(exc)
            }

            val parsedResponse = try {
                resourceParser.parseResource(request.resourceParserDelegate, resource)
            } catch (exc: Exception) {
                return@withContext Result.failure(exc)
            }

            return@withContext Result.success(parsedResponse)
        }
}
