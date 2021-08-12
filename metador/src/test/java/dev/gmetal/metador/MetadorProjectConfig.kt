package dev.gmetal.metador

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.listeners.Listener
import io.kotest.extensions.junitxml.JunitXmlReporter

@Suppress("unused")
object MetadorProjectConfig : AbstractProjectConfig() {
    override fun listeners(): List<Listener> = listOf(
        JunitXmlReporter(
            includeContainers = false,
            useTestPathAsName = true
        )
    )
}
