package dev.gmetal.metador.response

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceParser
import dev.gmetal.metador.ResourceRetriever
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A ResponseProducer subclass that produces responses from the network
 *
 * @param resourceRetriever the ResourceRetriever to use
 * @param backgroundDispatcher the CoroutineDispatcher to use for doing all operations
 */
class NetworkResponseProducer(
    private val resourceRetriever: ResourceRetriever,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ResponseProducer {
    private val resourceParser: ResourceParser = ResourceParser(backgroundDispatcher)
    override fun canHandleRequest(request: Metador.Request): Boolean = true

    override suspend fun produceResponse(request: Metador.Request): Result<Map<String, String>, Throwable> =
        withContext(backgroundDispatcher) {
            val resource = try {
                resourceRetriever.retrieveResource(request)
            } catch (exc: Exception) {
                return@withContext Err(exc)
            }

            val parsedResponse = try {
                resourceParser.parseResource(request.resourceParserDelegate, resource)
            } catch (exc: Exception) {
                return@withContext Err(exc)
            }

            return@withContext Ok(parsedResponse)
        }
}
