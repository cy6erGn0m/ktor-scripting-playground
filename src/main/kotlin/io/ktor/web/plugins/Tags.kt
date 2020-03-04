package io.ktor.web.plugins

import io.ktor.web.plugins.model.PluginDescriptor

fun collectTags(allPlugins: List<PluginDescriptor>): Map<String, List<PluginDescriptor>> {
    val allTags = allPlugins.flatMapTo(HashSet()) { it.tags }

    return allTags
        .map { Pair(it, allPlugins.filter { plugin -> it in plugin.tags }) }
        .toMap()
}
