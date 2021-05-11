package dev.gmetal.metador

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import dev.gmetal.metador.Metador.FailureCallback
import dev.gmetal.metador.response.CachedResponseProducer
import dev.gmetal.metador.response.NetworkResponseProducer
import dev.gmetal.metador.response.ResponseProducer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_MAX_AGE_CACHE_SECONDS = 60 * 60

/**
 * Metador provides an easy-to-use utility class for retrieving HTML-metadata information from a
 * remote resource (HTML document). One Metador instance can handle multiple requests. Internally,
 * it  can cache responses (if the remote resource is unchanged) and quickly return them.
 *
 * @param backgroundDispatcher the CoroutineDispatcher to use for doing all background operations
 * @param cachedResponseProducer the Response Producer that serves cached responses
 * @param networkResponseProducer the Resource Producers that retrieves responses from the network
 */
class Metador private constructor(
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val cachedResponseProducer: CachedResponseProducer,
    internal val networkResponseProducer: ResponseProducer,
) {
    private val metadorScope: CoroutineScope = MainScope()

    /**
     * Processes the given request and produces the appropriate response
     */
    fun process(request: Request) {
        metadorScope.launch {
            withContext(backgroundDispatcher) {
                val responseResult: Pair<Result<Map<String, String>, Throwable>, Boolean> = when {
                    cachedResponseProducer.canHandleRequest(request) ->
                        cachedResponseProducer.produceResponse(request) to true
                    else -> networkResponseProducer.produceResponse(request) to false
                }

                withContext(Dispatchers.Main) {
                    responseResult.first.fold(
                        { success ->
                            if (responseResult.isNotCached()) {
                                cachedResponseProducer.cacheResponse(
                                    request.uri,
                                    success,
                                    request.maxSecondsCached * 1000L
                                )
                            }
                            request.successCallback.onSuccess(success)
                        },
                        { failure ->
                            request.failureCallback.onError(failure)
                        }
                    )
                }
            }
        }
    }

    private fun Pair<Result<Map<String, String>, Throwable>, Boolean>.isNotCached() = !this.second

    class Builder {
        private var resourceRetriever: ResourceRetriever = OkHttp3ResourceRetriever()
        private var cacheDirectory: String = ""
        private var physicalCacheSize: Long = DEFAULT_PHYSICAL_CACHE_SIZE_BYTES
        private var responseCacheSize: Int = DEFAULT_MAX_RESPONSE_CACHE_SIZE
        private var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var cachedResponseProducer: CachedResponseProducer? = null
        private var networkResponseProducer: ResponseProducer? = null

        /**
         * Override the default ResourceRetriever with the specified implementation
         */
        fun withResourceRetriever(resourceRetriever: ResourceRetriever): Builder = apply {
            this.resourceRetriever = resourceRetriever
        }

        /**
         * Specify the default cache directory to use for caching downloaded resources. This is
         * used by the ResourceRetriever
         */
        fun withCacheDirectory(cacheDirectory: String): Builder = apply {
            this.cacheDirectory = cacheDirectory
        }

        /**
         * Specify the cache size (in bytes) of the physical cache - the cache used for storing
         * downloaded resources by the Resource Retriever
         */
        fun withPhysicalCacheSize(physicalCacheSizeBytes: Long): Builder = apply {
            this.physicalCacheSize = physicalCacheSizeBytes
        }

        /**
         * Specify the maximum number of responses to cache - the cached responses (url, meta map)
         * produced for the specified URL
         */
        fun withResponseCacheSize(responseCacheSize: Int): Builder = apply {
            this.responseCacheSize = responseCacheSize
        }

        /**
         * Specify the Coroutine Dispatcher to be used when executing the background
         * operations
         */
        internal fun withBackgroundDispatcher(backgroundDispatcher: CoroutineDispatcher): Builder =
            apply {
                this.backgroundDispatcher = backgroundDispatcher
            }

        /**
         * Specify the response producer responsible for controlling access to cached responses.
         * This is mainly used for testing purposes. Using this method will override the
         * responseCacheSize variable
         */
        internal fun withCachedResponseProducer(cachedResponseProducer: CachedResponseProducer) =
            apply {
                this.cachedResponseProducer = cachedResponseProducer
            }

        /**
         * Specify the network response producer, responsible for controlling access to network
         * responses. This is mainly used for testing purposes. Using this method will override
         * the resource retriever
         */
        internal fun withNetworkResponseProducer(networkResponseProducer: ResponseProducer) =
            apply {
                this.networkResponseProducer = networkResponseProducer
            }

        /**
         * Creates an returns a Metador instance
         */
        fun build(): Metador =
            Metador(
                backgroundDispatcher = backgroundDispatcher,
                cachedResponseProducer = cachedResponseProducer
                    ?: CachedResponseProducer(responseCacheSize) { System.currentTimeMillis() },
                networkResponseProducer = networkResponseProducer ?: NetworkResponseProducer(
                    resourceRetriever.apply {
                        configureCache(cacheDirectory, physicalCacheSize)
                    },
                    backgroundDispatcher
                )
            )
    }

    companion object {
        @JvmStatic
        fun request(url: String): Request.Builder =
            Request.Builder(url)
    }

    /**
     * A Metador request that can be used to retrieve the metadata of a specified network resource
     */
    class Request internal constructor(
        val uri: String,
        val maxSecondsCached: Int,
        val resourceParserDelegate: ResourceParserDelegate,
        val successCallback: SuccessCallback,
        val failureCallback: FailureCallback
    ) {
        /**
         * Convenience method to return whether a cached response is acceptable or whether we should
         * request a response from the server
         */
        fun cachedResponseAllowed(): Boolean = (maxSecondsCached > 0)

        class Builder(val url: String) {
            private var successCallback: SuccessCallback? = null
            private var failureCallback: FailureCallback? = null
            private var maxSecondsCached: Int = DEFAULT_MAX_AGE_CACHE_SECONDS
            private var resourceParserDelegate: ResourceParserDelegate = HtmlMetaExtractor()

            /**
             * The callback to be used for notifying about a successful result
             * This is required.
             */
            fun onSuccess(success: SuccessCallback): Builder = apply {
                successCallback = success
            }

            /**
             * An optional callback to be used for notifying on failed request.
             */
            fun onFailure(failure: FailureCallback): Builder = apply {
                failureCallback = failure
            }

            /**
             * Override the default ResourceParserDelegate with the specified implementation
             */
            fun withResourceParser(resourceParserDelegate: ResourceParserDelegate): Builder =
                apply {
                    this.resourceParserDelegate = resourceParserDelegate
                }

            /**
             * The maximum number of seconds that a cached response can be reused.
             * Default value is 3600 seconds (1 hour)
             */
            fun maximumSecondsCached(age: Int = DEFAULT_MAX_AGE_CACHE_SECONDS): Builder = apply {
                maxSecondsCached = age
            }

            /**
             * Create a request that will completely bypass any cached responses and retrieve data
             * from the network
             */
            fun requestFromNetwork(): Builder = apply {
                maxSecondsCached = 0
            }

            fun build(): Request =
                Request(
                    url,
                    maxSecondsCached,
                    resourceParserDelegate,
                    successCallback
                        ?: throw RuntimeException("Success callback is required"),
                    failureCallback ?: FailureCallback { }
                )
        }
    }

    /**
     * Callback interface for receiving the parsed Meta elements
     */
    fun interface SuccessCallback {
        fun onSuccess(result: Map<String, String>)
    }

    /**
     * Callback interface for receiving errors
     */
    fun interface FailureCallback {
        fun onError(throwable: Throwable)
    }
}
