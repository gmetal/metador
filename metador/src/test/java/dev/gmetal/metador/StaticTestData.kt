package dev.gmetal.metador

const val HTML_DOCUMENT_WITHOUT_META = "<html><head></head><body></body></html>"
const val HTML_DOCUMENT_WITH_UNKNOWN_ATTRIBUTE_META = """
    <html>
        <head>
            <meta attr="key" content="value">
        </head>
        <body></body>
    </html>"""
const val HTML_DOCUMENT_WITH_META = """
    <html>
        <head>
            <meta name="key" content="value">
        </head>
        <body></body>
    </html>
    """
const val HTML_DOCUMENT_WITH_ALL_SUPPORTED_TYPES_META = """
    <html>
        <head>
            <meta name="key-1" content="value">
            <meta property="key-2" content="value">
            <meta itemprop="key-3" content="value">
        </head>
        <body></body>
    </html>
    """
const val HTML_DOCUMENT_WITH_META_WITHOUT_THE_CONTENT_ATTRIBUTE = """
    <html>
        <head>
            <meta name="key-1" value="value">
            <meta property="key-2" value="value">
            <meta itemprop="key-3" value="value">
        </head>
        <body></body>
    </html>
    """
