package io.ktor.web.plugins

import io.ktor.web.plugins.model.*
import kotlinx.serialization.json.*
import java.io.*

fun collectPlugins(root: File, json: Json = Json(JsonConfiguration.Stable)): Sequence<PluginDescriptor> {
    return root.walk()
        .onEnter { !it.name.startsWith(".") }
        .filter { it.isFile && it.extension == "json" }
        .map {
            json.parse(PluginDescriptor.serializer(), it.readText()).apply {
                filePath = it.relativeTo(root).path
            }
        }
}
