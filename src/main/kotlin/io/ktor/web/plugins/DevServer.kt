package io.ktor.web.plugins

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.web.plugins.model.*
import io.ktor.web.plugins.scripting.*
import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.html.stream.*
import org.slf4j.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.full.*

@UseExperimental(KtorExperimentalAPI::class)
fun main() {
    var (model, validation) = collectModel()

    val server = embeddedServer(CIO, port = 8080) {
//        install(ConditionalHeaders)
        install(DefaultHeaders)
        install(AutoHeadResponse)
        install(Locations)

        routing {
            bindPages { model }
//            get("/") {
//                call.respondPage(model, "index")
//            }
//            get("/dev") {
//                call.respondPage(model, "dev")
//            }
//            get("/plugins/{pluginId}.html") {
//                val pluginId: String by call.parameters
//                val pluginDescriptor = model.byPluginId[pluginId]
//                val status = if (pluginDescriptor == null) HttpStatusCode.NotFound else HttpStatusCode.OK
//                call.respondPage(model.copy(parameters = call.parameters), "plugin", status)
//            }
        }
    }

    server.addShutdownHook {
        println("Shutting down...")
    }
    server.start(wait = true)
}

private fun Route.bindPages(model: () -> AppModel) {
    val allNames = File("pages").listFiles().orEmpty().filter { it.name.endsWith(".page.kts") }
        .map { it.name.removeSuffix(".page.kts") }

    runBlocking {
        val logger = LoggerFactory.getLogger("routing")!!

        allNames.forEach { pageName ->
            val compiledPage = Lookup.scriptClassFor(pageName)
            val pageClass = compiledPage.pageClass

            val locationClass =
                pageClass.primaryConstructor?.parameters
                    ?.mapNotNull { it.type.classifier as? KClass<*> }
                    ?.singleOrNull { it.findAnnotation<Location>() != null }

            val locationPath = locationClass?.findAnnotation<Location>()?.path

            val routeAnnotation = compiledPage.route
            val parameterNames = routeAnnotation?.let { RoutingPath.parse(it) }
                ?.parts?.filter { it.kind == RoutingPathSegmentKind.Parameter }
                ?.mapNotNull {
                    it.value.substringAfter("{")
                        .substringBefore("}")
                        .trim().removeSuffix("?").trimEnd()
                        .removeSuffix("...").trimEnd()
                        .takeIf { it.isNotEmpty() }
                }.orEmpty()

            val pageRoute = when {
                routeAnnotation != null -> routeAnnotation
                locationPath != null -> locationPath
                pageName == "index" -> "/"
                else -> "/$pageName.html"
            }

            logger.info("Binding $pageName at $pageRoute")

            get(pageRoute) {
                val currentModel = when (locationPath) {
                    null -> model()
                    else -> model().copy(parameters = call.parameters)
                }
                // TODO controller logic?
                call.respondPage(currentModel, compiledPage, locationClass, parameterNames)
            }
        }
    }
}

private suspend fun ApplicationCall.respondHtml(
    status: HttpStatusCode = HttpStatusCode.OK,
    builder: suspend HTML.() -> Unit
) {
    respondText("<!DOCTYPE html>\n" + createHTML().html {
        runBlocking {
            builder()
        }
    }, ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}

private suspend fun ApplicationCall.respondPage(
    model: AppModel,
    compiledPage: Lookup.CompiledPage,
    locationClass: KClass<*>?,
    parameterNames: List<String>,
    status: HttpStatusCode = HttpStatusCode.OK
) {

    val instance = locationClass?.let { application.locations.resolve<Any>(it, model.parameters) }

    respondHtml(status = status) {
        val constructor = compiledPage.pageClass.primaryConstructor!!
        val args = mutableListOf<Any?>()
        args += model
        args += this
        if (instance != null) {
            args += instance
        }
        args.addAll(parameterNames.map { parameters[it] })

        constructor.call(*args.toTypedArray())
    }
}

private suspend fun ApplicationCall.respondHtmlText(text: String) {
    respondText(text, ContentType.Text.Html.withCharset(Charsets.UTF_8))
}
