package dev.gmetal.metador

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResourceParser(
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun parseResource(
        parserDelegate: ResourceParserDelegate,
        resource: String
    ): Map<String, String> =
        withContext(parserDispatcher) {
            parserDelegate.parseResource(resource)
        }
}