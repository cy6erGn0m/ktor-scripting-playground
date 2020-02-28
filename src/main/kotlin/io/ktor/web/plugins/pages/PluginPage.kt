package io.ktor.web.plugins.pages

import io.ktor.web.plugins.model.*
import kotlinx.html.*

fun buildPluginPage(pluginDescriptor: PluginDescriptor): String = buildPage("Plugin ${pluginDescriptor.title}") {
    h1 {
        text("Plugin ${pluginDescriptor.title}")
    }

    p {
        text("Website: ")
        a(href = pluginDescriptor.webSite) {
            text(pluginDescriptor.webSite)
        }
    }

    versions(pluginDescriptor)
    description(pluginDescriptor)
}

private fun BODY.versions(pluginDescriptor: PluginDescriptor) {
    if (pluginDescriptor.versionsMappings.size == 1) {
        p {
            text("Maven coordinates: ${pluginDescriptor.versionsMappings.single().coordinates}")
        }
    } else {
        p {
            table {
                thead {
                    tr {
                        th {
                            text("ktor")
                        }
                        th {
                            text(pluginDescriptor.id)
                        }
                    }
                }
                tbody {
                    // TODO sort properly
                    pluginDescriptor.versionsMappings.sortedBy { it.ktorVersionRange }.forEach {
                        tr {
                            td {
                                text(it.ktorVersionRange)
                            }
                            td {
                                text(it.coordinates)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun BODY.description(pluginDescriptor: PluginDescriptor) {
    val collected = ArrayList<StringBuilder>()
    collected.add(StringBuilder())

    pluginDescriptor.description.lines().forEach { line ->
        if (line.isBlank()) {
            if (collected.last().isNotBlank()) {
                collected.add(StringBuilder())
            }
        } else {
            collected.last().append(line).append('\n')
        }
    }

    if (collected.last().isBlank()) {
        collected.removeAt(collected.lastIndex)
    }

    if (collected.isEmpty()) {
        collected.add(StringBuilder("No description provided."))
    }

    div {
        collected.forEach {
            p {
                text(it.toString().trim())
            }
        }
    }
}