package dev.gmetal.metador

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.util.concurrent.TimeUnit

class OkHttp3ResourceRetriever(
    private val retrieverDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ResourceRetriever {
    private var okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    override fun configureCache(
        cacheDirectory: String,
        physicalCacheSize: Long
    ) {
        val cacheDir = File(cacheDirectory)
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdir()) {
                return
            }
        }
        okHttpClient = OkHttpClient.Builder()
            .cache(Cache(cacheDir, physicalCacheSize))
            .build()
    }

    private fun parseSuccess(response: Response): String {
        return when {
            response.isNotSuccessful() -> throw response.toResourceRetrieverException()
            response.body == null -> throw EmptyResponseException()
            response.code == HTTP_NO_CONTENT -> throw EmptyResponseException()
            else -> response.body?.string() ?: throw EmptyResponseException()
        }.also {
            response.close()
        }
    }

    private fun parseFailure(exception: Exception) = UnknownNetworkException(exception)

    override suspend fun retrieveResource(request: Metador.Request): String =
        withContext(retrieverDispatcher) {
            val response = try {
                okHttpClient
                    .newCall(request.toOkHttpRequest())
                    .execute()
            } catch (exc: Exception) {
                throw parseFailure(exc)
            }
            parseSuccess(response)
        }

    private fun Metador.Request.toOkHttpRequest(): Request =
        Request.Builder()
            .url(uri)
            .cacheControl(asCacheControl())
            .build()

    private fun Metador.Request.requestFromNetwork(): Boolean = (maxSecondsCached <= 0)

    private fun Metador.Request.asCacheControl() =
        if (requestFromNetwork()) {
            FORCE_NETWORK
        } else {
            CacheControl.Builder().maxAge(maxSecondsCached, TimeUnit.SECONDS).build()
        }

    private fun Response.isNotSuccessful(): Boolean = !isSuccessful

    private fun Response.toResourceRetrieverException(): NetworkException =
        when (code) {
            HTTP_NOT_FOUND -> ResourceNotFoundException
            in 500..599 -> ServerErrorException(code)
            else -> UnknownNetworkException()
        }
}
