package dev.gmetal.metador

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.Elements

private const val META_ELEMENT = "meta"
private const val CONTENT_ATTRIBUTE = "content"
private const val NAME_ATTRIBUTE = "name"
private const val PROPERTY_ATTRIBUTE = "property"
private const val ITEMPROP_ATTRIBUTE = "itemprop"

/**
 * Parses an HTML document and produces a Map<String, String> containing the
 * supported name-value pairs
 */
class HtmlMetaExtractor : ResourceParserDelegate {

    private fun parse(resource: String): Map<String, String> {
        val body = Jsoup.parse(resource)
        val elements: Elements = body.head().getElementsByTag(META_ELEMENT)

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
