package dev.gmetal.metador

import dev.gmetal.metador.resource.parser.KsoupResourceParserDelegate
import dev.gmetal.metador.resource.retriever.FileStorage
import dev.gmetal.metador.resource.retriever.KtorClientResourceRetriever
import kotlinx.io.files.SystemFileSystem

internal actual fun defaultResourceRetriever(): ResourceRetriever =
    KtorClientResourceRetriever(
        cacheProvider = { cacheDir ->
            FileStorage(cacheDir, SystemFileSystem)
        }
    )

internal actual fun defaultResourceParserDelegate(): ResourceParserDelegate =
    KsoupResourceParserDelegate()