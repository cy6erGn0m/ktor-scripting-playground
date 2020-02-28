package io.ktor.web.plugins.pages

import io.ktor.locations.*
import io.ktor.web.plugins.model.*

object WebSite {
    fun mainPage(): String = "/index.html"
    fun pluginPage(plugin: PluginDescriptor): String = "/plugins/${plugin.id}.html"
}

@Location("/plugin/{pluginId}.html")
class PluginPage(val pluginId: String)