package dev.gmetal.metador.response

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.gmetal.metador.Clock
import dev.gmetal.metador.DEFAULT_MAX_AGE_CACHE_SECONDS
import dev.gmetal.metador.Metador
import dev.gmetal.metador.ResourceNotFoundException

const val DEFAULT_CACHE_ENTRY_INTERVAL = DEFAULT_MAX_AGE_CACHE_SECONDS * 1000L

/**
 * A ResponseProducer subclass that produces responses from cached data
 *
 * @param responseCacheSize the maximum size of items to store in the cache
 * @param clock an entity that produces the current unix time
 */
class CachedResponseProducer(
    internal val responseCacheSize: Int,
    internal val clock: Clock
) : ResponseProducer {
    private val parsedResponseCache: FridgeCache<String, Map<String, String>> =
        FridgeCache(clock, DEFAULT_CACHE_ENTRY_INTERVAL, responseCacheSize)

    override fun canHandleRequest(request: Metador.Request): Boolean =
        request.cachedResponseAllowed() && parsedResponseCache.containsKey(request.uri)

    override suspend fun produceResponse(request: Metador.Request): Result<Map<String, String>, Throwable> =
        when {
            parsedResponseCache.containsKey(request.uri) -> Ok(parsedResponseCache[request.uri]!!)
            else -> Err(ResourceNotFoundException)
        }

    fun cacheResponse(
        uri: String,
        data: Map<String, String>,
        cacheForMsec: Long = DEFAULT_CACHE_ENTRY_INTERVAL
    ) {
        parsedResponseCache.put(uri, data, cacheForMsec)
    }
}
