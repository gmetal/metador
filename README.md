# Metador - the HTML metadata retriever library

Metador is an easy to use library for retrieving the META tags of an HTML page, for use in an Android
mobile application. It can be used in scenarios where the developer would have to extract the
information from a list of HTML pages. HTML metadata may contain very useful information, such as
a title, a text summary, an associated image, etc.

To use Metador, you create a Metador object through the associated Metador.Builder. Whenever you need,
to retrieve the metadata of an HTML page, you create a Metador.Request and you load it in the Metador
object. Metador will take the request, fetch the HTML page, extract the metadata it finds, and return
them in a Map. In case an error occurs, the caller is notified accordingly. Metador is extensible -
you can even use it to extract other key-value data from a generic URI.

Metador attempts to be speedy by caching results. To this end, it supports a two-level cache,
comprised of an in-memory cache and a disk cache. The in-memory cache stores the parsed META map
for a specific URL. The disk cache stores the HTML page, which can be reprocessed, if it has not
been modified in the server.

![Main branch status](https://github.com/gmetal/metador/actions/workflows/main-build.yml/badge.svg?branch=main)
