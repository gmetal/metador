package dev.gmetal.metador

internal actual fun defaultResourceParserDelegate(): ResourceParserDelegate =
    HtmlMetaExtractor()

internal actual fun defaultResourceRetriever(): ResourceRetriever = OkHttp3ResourceRetriever()
