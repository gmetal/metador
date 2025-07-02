package dev.gmetal.metador

fun interface ResourceParserDelegate {
    fun parseResource(resource: String): Map<String, String>
}