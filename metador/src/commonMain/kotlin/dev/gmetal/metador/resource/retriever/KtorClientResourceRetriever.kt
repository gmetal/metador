package dev.gmetal.metador.resource.retriever

import dev.gmetal.metador.EmptyResponseException
import dev.gmetal.metador.Metador
import dev.gmetal.metador.NetworkException
import dev.gmetal.metador.ResourceNotFoundException
import dev.gmetal.metador.ResourceRetriever
import dev.gmetal.metador.ServerErrorException
import dev.gmetal.metador.UnknownNetworkException
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.util.date.GMTDate
import io.ktor.util.flattenEntries
import io.ktor.util.hex
import io.ktor.utils.io.core.readFully
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readLine
import kotlinx.io.writeString
import org.kotlincrypto.hash.sha2.SHA256

class KtorClientResourceRetriever(
    private val cacheProvider: (String) -> FileStorage,
    private val retrieverDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ResourceRetriever {
    private val httpClient by lazy {
        HttpClient(CIO) {
            plusAssign(httpClientConfig)
        }
    }
    private val httpClientConfig = HttpClientConfig<CIOEngineConfig>()

    override fun configureCache(cacheDirectory: String, physicalCacheSize: Long) {
        val cacheStorage = cacheProvider(cacheDirectory)
        if (cacheStorage.isUsable()) {
            httpClientConfig.apply {
                install(HttpCache) {
                    publicStorage(cacheStorage)
                }
            }
        }
    }

    private suspend fun parseSuccess(response: HttpResponse): String =
        when {
            !response.status.isSuccess() -> throw response.toResourceRetrieverException()
            response.status == HttpStatusCode.NoContent -> throw EmptyResponseException()
            else -> {
                val body = response.bodyAsText()
                body.ifBlank {
                    throw EmptyResponseException()
                }
            }
        }

    private fun parseFailure(exception: Exception) = UnknownNetworkException(exception)

    override suspend fun retrieveResource(request: Metador.Request): String =
        withContext(retrieverDispatcher) {

            val response = try {
                httpClient.get(request.uri) {
                    if (!request.cachedResponseAllowed()) {
                        headers[HttpHeaders.CacheControl] = "no-cache"
                    } else {
                        headers[HttpHeaders.CacheControl] = "max-age=${request.maxSecondsCached}"
                    }
                }
            } catch (exc: Exception) {
                throw parseFailure(exc)
            }
            parseSuccess(response)
        }

    private fun HttpResponse.toResourceRetrieverException(): NetworkException =
        when {
            status == HttpStatusCode.NotFound -> ResourceNotFoundException
            status.value >= 500 && status.value <= 599 -> ServerErrorException(status.value)
            else -> UnknownNetworkException()
        }
}

class FileStorage(
    val cacheDirectory: String,
    val fileSystem: FileSystem,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CacheStorage {
    private val path = Path(cacheDirectory)
    private val store: MutableMap<Url, Set<CachedResponseData>> = mutableMapOf()
    private val mutexes: MutableMap<String, Mutex> = mutableMapOf()

    fun isUsable(): Boolean {
        if (!fileSystem.exists(path)) {
            fileSystem.createDirectories(path, true)
        }

        return fileSystem.exists(path)
    }

    override suspend fun store(
        url: Url,
        data: CachedResponseData
    ) {
        val urlHex = key(url)
        updateCache(urlHex) { caches ->
            caches.filterNot { it.varyKeys == data.varyKeys } + data
        }
    }

    override suspend fun find(
        url: Url,
        varyKeys: Map<String, String>
    ): CachedResponseData? {
        val data = readCache(key(url))
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        return readCache(key(url)).toSet()
    }

    override suspend fun remove(url: Url, varyKeys: Map<String, String>) {
        val urlHex = key(url)
        updateCache(urlHex) { caches ->
            caches.filterNot { it.varyKeys == varyKeys }
        }
    }

    override suspend fun removeAll(url: Url) {
        val urlHex = key(url)
        deleteCache(urlHex)
    }

    private fun key(url: Url) =
        hex(SHA256().digest(url.toString().encodeToByteArray()))

    private suspend fun readCache(urlHex: String): Set<CachedResponseData> {
        val mutex = mutexes.getOrPut(urlHex) { Mutex() }
        return mutex.withLock { readCacheUnsafe(urlHex) }
    }

    private suspend inline fun updateCache(
        urlHex: String,
        transform: (Set<CachedResponseData>) -> List<CachedResponseData>
    ) {
        val mutex = mutexes.getOrPut(urlHex) { Mutex() }
        mutex.withLock<Unit> {
            val caches = readCacheUnsafe(urlHex)
            writeCacheUnsafe(urlHex, transform(caches))
        }
    }

    private suspend fun deleteCache(urlHex: String) {
        val mutex = mutexes.getOrPut(urlHex) { Mutex() }
        mutex.withLock {

            val file = Path(path, urlHex)
            if (!fileSystem.exists(file)) return@withLock

            try {
                fileSystem.delete(file)
            } catch (cause: Exception) {
                println("Exception during cache deletion in a file: ${cause.stackTraceToString()}")
            }
        }
    }

    private suspend fun writeCacheUnsafe(urlHex: String, caches: List<CachedResponseData>) =
        coroutineScope {
            try {
                val file = Path(path, urlHex)
                fileSystem.sink(file).buffered().use { output ->
                    launch {
                        output.writeInt(caches.size)
                        for (cache in caches) {
                            writeCache(output, cache)
                        }
                        //channel.close()
                    }
                    //channel.copyTo(output)
                }
            } catch (cause: Exception) {
                println("Exception during saving a cache to a file: ${cause.stackTraceToString()}")
            }
        }

    private suspend fun readCacheUnsafe(urlHex: String): Set<CachedResponseData> {
        val file = Path(path, urlHex)
        if (!fileSystem.exists(file)) return emptySet()

        try {
            fileSystem.source(file).buffered().use { source ->
                val requestsCount = source.readInt()
                val caches = mutableSetOf<CachedResponseData>()
                for (i in 0 until requestsCount) {
                    caches.add(readCache(source))
                }
                //channel.discard()
                return caches
            }
        } catch (cause: Exception) {
            //LOGGER.trace { "Exception during cache lookup in a file: ${cause.stackTraceToString()}" }
            return emptySet()
        }
    }

    private suspend fun writeCache(channel: Sink, cache: CachedResponseData) {
        channel.writeString(cache.url.toString() + "\n")
        channel.writeInt(cache.statusCode.value)
        channel.writeString(cache.statusCode.description + "\n")
        channel.writeString(cache.version.toString() + "\n")
        val headers = cache.headers.flattenEntries()
        channel.writeInt(headers.size)
        for ((key, value) in headers) {
            channel.writeString(key + "\n")
            channel.writeString(value + "\n")
        }
        channel.writeLong(cache.requestTime.timestamp)
        channel.writeLong(cache.responseTime.timestamp)
        channel.writeLong(cache.expires.timestamp)
        channel.writeInt(cache.varyKeys.size)
        for ((key, value) in cache.varyKeys) {
            channel.writeString(key + "\n")
            channel.writeString(value + "\n")
        }
        channel.writeInt(cache.body.size)
        channel.writeFully(cache.body)
    }

    private suspend fun readCache(buffer: Source): CachedResponseData {
        val url = buffer.readLine()!!
        val status = HttpStatusCode(buffer.readInt(), buffer.readLine()!!)
        val version = HttpProtocolVersion.parse(buffer.readLine()!!)
        val headersCount = buffer.readInt()
        val headers = HeadersBuilder()
        for (j in 0 until headersCount) {
            val key = buffer.readLine()!!
            val value = buffer.readLine()!!
            headers.append(key, value)
        }
        val requestTime = GMTDate(buffer.readLong())
        val responseTime = GMTDate(buffer.readLong())
        val expirationTime = GMTDate(buffer.readLong())
        val varyKeysCount = buffer.readInt()
        val varyKeys = buildMap {
            for (j in 0 until varyKeysCount) {
                val key = buffer.readLine()!!
                val value = buffer.readLine()!!
                put(key, value)
            }
        }
        val bodyCount = buffer.readInt()
        val body = ByteArray(bodyCount)
        buffer.readFully(body, 0)
        return CachedResponseData(
            url = Url(url),
            statusCode = status,
            requestTime = requestTime,
            responseTime = responseTime,
            version = version,
            expires = expirationTime,
            headers = headers.build(),
            varyKeys = varyKeys,
            body = body
        )
    }
}