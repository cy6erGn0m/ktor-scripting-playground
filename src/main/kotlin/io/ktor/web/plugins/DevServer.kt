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
import io.ktor.web.plugins.scripting.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.html.*
import kotlinx.html.stream.*
import org.slf4j.*
import java.io.*
import java.util.concurrent.atomic.*
import kotlin.time.*

@UseExperimental(KtorExperimentalAPI::class, ExperimentalTime::class)
fun main() {
    val configDir = File("plugins.d")
    val model = AtomicReference(collectModel(configDir).first)

    fileChanges(configDir) { it.isDirectory || it.extension == "json" }
        .debounceChanges(300.milliseconds)
        .onEach {
            try {
                LoggerFactory.getLogger("Config").info("Config reloading...")
                model.set(collectModel(configDir).first)
            } catch (cause: Throwable) {
                cause.printStackTrace()
            }
        }.launchIn(GlobalScope)

    val server = embeddedServer(CIO, port = 8080) {
//        install(ConditionalHeaders)
        install(DefaultHeaders)
        install(AutoHeadResponse)
        install(Locations)

        install(StatusPages) {
            status(HttpStatusCode.NotFound) {
                call.respondHtml {
                    head {
                        title("404 Page not found")
                    }
                    body {
                        h3 {
                            text("404 Page not found")
                        }
                    }
                }
            }

            status(HttpStatusCode.InternalServerError) {
                call.respondHtml {
                    head {
                        title("Internal server error")
                    }
                    body {
                        h3 {
                            text("Server failed to respond to the request due to an internal error.")
                        }
                    }
                }
            }
        }

        val pageScriptChanges = fileChanges(Lookup.pagesRoot) {
            it.isDirectory || it.name.endsWith(".page.kts")
        }

        routing {
            Lookup.collecting(pageScriptChanges)
                .awaitingSuccessful()
                .collectAndRegisterRoutes(this) { model.get() }
        }
    }

    server.addShutdownHook {
        println("Shutting down...")
    }
    server.start(wait = true)
}

internal suspend fun ApplicationCall.respondHtml(
    status: HttpStatusCode = HttpStatusCode.OK,
    builder: suspend HTML.() -> Unit
) {
    respondText("<!DOCTYPE html>\n" + createHTML().html {
        runBlocking {
            builder()
        }
    }, ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}
