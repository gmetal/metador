package dev.gmetal.metador

const val DEFAULT_MAX_RESPONSE_CACHE_SIZE = 100
const val DEFAULT_PHYSICAL_CACHE_SIZE_BYTES = 10 * 1024 * 1024L
const val REQUEST_FRESH_COPY = 0

interface ResourceRetriever {
    fun configureCache(
        cacheDirectory: String,
        physicalCacheSize: Long = DEFAULT_PHYSICAL_CACHE_SIZE_BYTES
    )

    suspend fun retrieveResource(request: Metador.Request): String
}
