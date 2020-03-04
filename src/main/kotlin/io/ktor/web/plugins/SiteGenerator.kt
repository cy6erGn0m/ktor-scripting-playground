package io.ktor.web.plugins

import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import io.ktor.routing.RoutingPathSegmentKind
import io.ktor.web.plugins.model.AppModel
import io.ktor.web.plugins.scripting.Lookup
import kotlinx.coroutines.flow.*
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import org.slf4j.LoggerFactory
import java.io.File

suspend fun generateSite(
    docRoot: File,
    model: AppModel,
    pageNames: List<String>
) {
    docRoot.deleteRecursively()
    docRoot.mkdirs()

    val logger = LoggerFactory.getLogger("SiteGenerator")!!
    pageNames.forEach { pageName ->
        Lookup.scriptClassFor(pageName)?.let { page ->
            paramFlow(pageName, model)
                .filter { parameters -> page.routeParameterNames.all { it in parameters } }
                .collect { parameters ->
                    val pathSubstituted = page.route.parts.joinToString("/") { segment ->
                        when (segment.kind) {
                            RoutingPathSegmentKind.Constant -> segment.value
                            RoutingPathSegmentKind.Parameter -> buildString {
                                append(segment.value.substringBefore("{"))

                                val parameterName = segment.value.substringAfter("{")
                                    .substringBefore("}")
                                    .trimStart().trimEnd(' ', '.', '?')

                                append(parameters[parameterName])

                                append(segment.value.substringAfter("}"))
                            }
                        }
                    }.takeUnless { it.isEmpty() && pageName == "index" } ?: "index.html"

                    val file = File(docRoot, pathSubstituted)
                    file.parentFile.mkdirs()

                    logger.info("Generating ${file.path}")
                    file.bufferedWriter().use { writer ->
                        writer.append("<!DOCTYPE html>\n")

                        writer.appendHTML().html {
                            generatePageHtml(
                                page, model, this, null,
                                parameters.names().toList()
                                    .sortedBy { page.routePath.indexOf(it) },
                                parameters
                            )
                        }
                    }
                }
        }
    }
    logger.info("Done generating site.")
}

fun paramFlow(pageName: String, model: AppModel): Flow<Parameters> {
    return when (pageName) {
        "plugin" -> pluginsFlow(model)
        "tag" -> tagsFlow(model)
        else -> flowOf(Parameters.Empty)
    }
}

private fun pluginsFlow(model: AppModel): Flow<Parameters> {
    return flow {
        model.allPlugins.forEach { plugin ->
            emit(parametersOf("pluginId", plugin.id))
        }
    }
}

private fun tagsFlow(model: AppModel): Flow<Parameters> =
    model.tags.keys.asFlow().map { parametersOf("tag", it) }
