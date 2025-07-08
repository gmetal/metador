package dev.gmetal.metador.resource.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import dev.gmetal.metador.ResourceParserDelegate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private const val META_ELEMENT = "meta"
private const val CONTENT_ATTRIBUTE = "content"
private const val NAME_ATTRIBUTE = "name"
private const val PROPERTY_ATTRIBUTE = "property"
private const val ITEMPROP_ATTRIBUTE = "itemprop"

class KsoupResourceParserDelegate(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ResourceParserDelegate {

    private fun parse(resource: String): Map<String, String> {
        val body = Ksoup.parse(resource)
        val elements = body.head().getElementsByTag(META_ELEMENT)

        return elements
            .asList()
            .filter { it.isMetaElement() }
            .associate { element -> element.metaName() to element.metaValue() }
    }

    override fun parseResource(resource: String): Map<String, String> =
        parse(resource)

    private fun Node.isMetaElement(): Boolean =
        (hasAttr(NAME_ATTRIBUTE) || hasAttr(PROPERTY_ATTRIBUTE) || hasAttr(ITEMPROP_ATTRIBUTE)) && hasAttr(
            CONTENT_ATTRIBUTE
        )

    private fun Element.metaName(): String = when {
        hasAttr(PROPERTY_ATTRIBUTE) -> attr(PROPERTY_ATTRIBUTE)
        hasAttr(NAME_ATTRIBUTE) -> attr(NAME_ATTRIBUTE)
        hasAttr(ITEMPROP_ATTRIBUTE) -> attr(ITEMPROP_ATTRIBUTE)
        else -> ""
    }

    private fun Element.metaValue(): String = attr(CONTENT_ATTRIBUTE)
}
