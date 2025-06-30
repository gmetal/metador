package dev.gmetal.metador

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

/**
 * The default response cache age, which is 3600 seconds (1 hour)
 */
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
     * Processes [Metador.Request]s and produces the corresponding responses
     *
     * @param request the [Metador.Request] to process
     */
    fun process(request: Request) {
        metadorScope.launch {
            withContext(backgroundDispatcher) {
                val responseResult: Pair<Result<Map<String, String>>, Boolean> = when {
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

    private fun Pair<Result<Map<String, String>>, Boolean>.isNotCached() = !this.second

    /**
     * A builder that is used for creating [Metador] instances
     */
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
         *
         * @param resourceRetriever the [ResourceRetriever] instance to use
         * @return this [Metador.Builder] instance
         */
        fun withResourceRetriever(resourceRetriever: ResourceRetriever): Builder = apply {
            this.resourceRetriever = resourceRetriever
        }

        /**
         * Specify the default cache directory to use for caching downloaded resources. This is
         * used by the ResourceRetriever
         *
         * @param cacheDirectory the directory location to be used internally for caching
         * @return this [Metador.Builder] instance
         */
        fun withCacheDirectory(cacheDirectory: String): Builder = apply {
            this.cacheDirectory = cacheDirectory
        }

        /**
         * Specify the cache size (in bytes) of the physical cache - the cache used for storing
         * downloaded resources by the Resource Retriever
         *
         * @return this [Metador.Builder] instance
         */
        fun withPhysicalCacheSize(physicalCacheSizeBytes: Long): Builder = apply {
            this.physicalCacheSize = physicalCacheSizeBytes
        }

        /**
         * Specify the maximum number of responses to cache - the cached responses (url, meta map)
         * produced for the specified URL
         *
         * @param responseCacheSize and Int containing the max number of cached responses to store
         * @return this [Metador.Builder] instance
         */
        fun withResponseCacheSize(responseCacheSize: Int): Builder = apply {
            this.responseCacheSize = responseCacheSize
        }

        /**
         * Specify the Coroutine Dispatcher to be used when executing the background
         * operations
         *
         * @param backgroundDispatcher the background [CoroutineDispatcher] to use
         * @return this [Metador.Builder] instance
         */
        internal fun withBackgroundDispatcher(backgroundDispatcher: CoroutineDispatcher): Builder =
            apply {
                this.backgroundDispatcher = backgroundDispatcher
            }

        /**
         * Specify the response producer responsible for controlling access to cached responses.
         * This is mainly used for testing purposes. Using this method will override the
         * responseCacheSize variable
         *
         * @param cachedResponseProducer the overridden [CachedResponseProducer] to use
         * @return this [Metador.Builder] instance
         */
        internal fun withCachedResponseProducer(cachedResponseProducer: CachedResponseProducer) =
            apply {
                this.cachedResponseProducer = cachedResponseProducer
            }

        /**
         * Specify the network response producer, responsible for controlling access to network
         * responses. This is mainly used for testing purposes. Using this method will override
         * the resource retriever
         *
         * @param networkResponseProducer the overridden [ResponseProducer] to use
         * @return this [Metador.Builder] instance
         */
        internal fun withNetworkResponseProducer(networkResponseProducer: ResponseProducer) =
            apply {
                this.networkResponseProducer = networkResponseProducer
            }

        /**
         * Creates an returns a [Metador] instance
         *
         * @return a new [Metador] instance as configured through this builder
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
        /**
         * Convenience method for quickly creating a [Request.Builder] from the specified URL
         *
         * @param url a string to the resource's URL for which a [Request] will be built
         */
        @JvmStatic
        fun request(url: String): Request.Builder =
            Request.Builder(url)
    }

    /**
     * A [Metador] request that can be used to retrieve the metadata of a specified network resource
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

         * @return true if a cache response can be used, or false otherwise
         */
        fun cachedResponseAllowed(): Boolean = (maxSecondsCached > 0)

        /**
         * Create a convenient Builder instance to configure a new [Metador.Request] object
         *
         * @param url the url that contains the resource which will be used for extracting data
         */
        class Builder(val url: String) {
            private var successCallback: SuccessCallback? = null
            private var failureCallback: FailureCallback? = null
            private var maxSecondsCached: Int = DEFAULT_MAX_AGE_CACHE_SECONDS
            private var resourceParserDelegate: ResourceParserDelegate = HtmlMetaExtractor()

            /**
             * The callback to be used for notifying about a successful result
             * This is required.
             *
             * @param success the [SuccessCallback] that will receive a success value
             * @return this [Request.Builder] instance
             */
            fun onSuccess(success: SuccessCallback): Builder = apply {
                successCallback = success
            }

            /**
             * An optional callback to be used for notifying on failed request.
             *
             * @param failure the [FailureCallback] to be called upon failure
             * @return this [Request.Builder] instance
             */
            fun onFailure(failure: FailureCallback): Builder = apply {
                failureCallback = failure
            }

            /**
             * Override the default [ResourceParserDelegate] with the specified implementation
             *
             * @param resourceParserDelegate the custom [ResourceParserDelegate] to use when
             *        processing the request
             * @return this [Request.Builder] instance
             */
            fun withResourceParser(resourceParserDelegate: ResourceParserDelegate): Builder =
                apply {
                    this.resourceParserDelegate = resourceParserDelegate
                }

            /**
             * The maximum number of seconds that a cached response can be reused.
             * Default value is 3600 seconds (1 hour)
             *
             * @param age an integer, the number of seconds until a cached response is acceptable
             * @return this [Request.Builder] instance
             */
            fun maximumSecondsCached(age: Int = DEFAULT_MAX_AGE_CACHE_SECONDS): Builder = apply {
                maxSecondsCached = age
            }

            /**
             * Create a request that will completely bypass any cached responses and retrieve data
             * from the network
             *
             * @return this [Request.Builder] instance
             */
            fun requestFromNetwork(): Builder = apply {
                maxSecondsCached = 0
            }

            /**
             * Creates a [Metador.Request] instance
             *
             * @return the [Metador.Request] as configured with this builder
             */
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
        /**
         * Called upon successful completion of the resource downloading and parsing
         *
         * @param result a [Map]<[String], [String]> that contains the extracted data
         */
        fun onSuccess(result: Map<String, String>)
    }

    /**
     * Callback interface for receiving errors
     */
    fun interface FailureCallback {
        /**
         * Called in case an error occurs while a Request is in progress. The error may be
         * related with the resource downloading or the parsing.
         *
         * @param throwable the [Throwable] instance of the occurred error
         */
        fun onError(throwable: Throwable)
    }
}
