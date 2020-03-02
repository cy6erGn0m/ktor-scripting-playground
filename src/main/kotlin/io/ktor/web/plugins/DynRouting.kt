package io.ktor.web.plugins

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.web.plugins.model.*
import io.ktor.web.plugins.scripting.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.full.*

@UseExperimental(ExperimentalCoroutinesApi::class)
fun Flow<Lookup.CompiledPage>.collectAndRegisterRoutes(root: Route, model: () -> AppModel): Job {
    val routeRegistration = newSingleThreadContext("route-registrar")
    val existingRoutes = HashMap<Route, DynRoutingEntry>()

    return onEach { page ->
        val routePath = page.routePath
        val route = root.createRouteFromPath(routePath)

        val entry = existingRoutes.getOrPut(route) {
            val entry = DynRoutingEntry(page.route)

            route.handle {
                entry.pageNames
                    .mapNotNull { Lookup.scriptClassFor(it) }
                    .firstOrNull { it.routePath == routePath }
                    ?.let { compiledPage ->
                        // TODO location class
                        // TODO status is always 200 OK
                        call.respondPage(model(), compiledPage, null, entry.parameterNames)
                    }
            }

            entry
        }

        if (entry.pageNames.add(page.pageName)) {
            root.application.log.info("Bound ${page.pageName} at $routePath")
        }
    }.onCompletion {
        routeRegistration.dispatch(EmptyCoroutineContext, Runnable {
            routeRegistration.close()
        })
    }.launchIn(GlobalScope + routeRegistration)
}

private class DynRoutingEntry(
    route: RoutingPath
) {
    val routePath = route.toString()
    val parameterNames = route.parts.filter { it.kind == RoutingPathSegmentKind.Parameter }
        .mapNotNull { segment ->
            segment.value.substringAfter("{")
                .substringBefore("}")
                .trimStart().trimEnd { it.isWhitespace() || it == '?' || it == '.' }
                .takeIf { it.isNotEmpty() }
        }

    val pageNames: MutableSet<String> = ConcurrentSkipListSet()

    override fun hashCode(): Int = routePath.hashCode()
    override fun equals(other: Any?): Boolean = other is DynRoutingEntry && other.routePath == routePath
    override fun toString(): String = routePath
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
