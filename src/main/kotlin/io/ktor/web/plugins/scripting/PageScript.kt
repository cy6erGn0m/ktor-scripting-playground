package io.ktor.web.plugins.scripting

import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.web.plugins.model.*
import kotlinx.html.*
import kotlin.reflect.full.*
import kotlin.script.experimental.annotations.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.*

// it is referenced in META-INF
@Suppress("unused")
@KotlinScript(
    "Web page script",
    fileExtension = "page.kts",
    compilationConfiguration = PageScriptCompilationConfig::class
)
abstract class PageScript(val model: AppModel)

object PageScriptCompilationConfig : ScriptCompilationConfiguration({
    baseClass(PageScript::class)

    defaultImports(
        "kotlinx.html.*",
        "io.ktor.web.plugins.model.*",
        "io.ktor.web.plugins.scripting.*",
        "io.ktor.web.plugins.pages.*",
        "io.ktor.util.getValue"
    )

    implicitReceivers(HTML::class)

    jvm {
        dependenciesFromClassContext(
            PageScriptCompilationConfig::class,
            "kotlinx-html-jvm", "kotlin-stdlib",
            "ktor-utils-jvm", "ktor-http-jvm", "ktor-server-core", "ktor-locations",
            "main"
        )

    }

    compilerOptions.append("-Xexperimental=kotlinExperimental,io.ktor.util.KtorExperimentalAPI")

    refineConfiguration {
        onAnnotations(PageLocation::class, handler = PageLocationRefinement)
        onAnnotations(PageRoute::class, handler = PageRouteRefinement)
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Project)
    }
})

object PageLocationRefinement : RefineScriptCompilationConfigurationHandler {
    override fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val locations = context.collectedData
            ?.get(ScriptCollectedData.foundAnnotations)
            ?.filterIsInstance<PageLocation>()
            ?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        if (locations.size > 1) {
            return context.compilationConfiguration.asSuccess(
                listOf(ScriptDiagnostic("It should be a single @PageLocation annotation."))
            )
        }

        val locationClass = try {
            Class.forName(locations.single().locationClassFqName).kotlin
        } catch (cause: Throwable) {
            return context.compilationConfiguration.asSuccess(
                listOf(
                    ScriptDiagnostic(
                        "Failed to find location class ${locations.single().locationClassFqName} ",
                        exception = cause
                    )
                )
            )
        }

        locationClass.findAnnotation<Location>() ?: return context.compilationConfiguration.asSuccess(
            listOf(
                ScriptDiagnostic(
                    "Class specified in @PageLocation is not " +
                            "a location class (not marked with @Location)"
                )
            )
        )

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            providedProperties("location" to KotlinType(locationClass))
        }.asSuccess()
    }
}

object PageRouteRefinement : RefineScriptCompilationConfigurationHandler {
    override fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val locations = context.collectedData
            ?.get(ScriptCollectedData.foundAnnotations)
            ?.filterIsInstance<PageRoute>()
            ?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        if (locations.size > 1) {
            return context.compilationConfiguration.asSuccess(
                listOf(ScriptDiagnostic("It should be a single @PageRoute annotation."))
            )
        }

        val location = locations.single()
        val path = location.routePath

        val parsed = try {
            RoutingPath.parse(path)
        } catch (cause: Throwable) {
            return context.compilationConfiguration.asSuccess(
                listOf(ScriptDiagnostic("Failed to parse path $path", exception = cause))
            )
        }

        val parameterNames = parsed.parts.filter { it.kind == RoutingPathSegmentKind.Parameter }
            .mapNotNull {
                it.value.substringAfter("{").substringBefore("}")
                    .trim().removeSuffix("?")
                    .removeSuffix("...").trim()
                    .takeIf { it.isNotEmpty() }
            }

        if (parameterNames.isEmpty()) {
            return context.compilationConfiguration.asSuccess()
        }

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            parameterNames.forEach { parameterName ->
                providedProperties(parameterName to KotlinType(String::class))
            }
        }.asSuccess()
    }
}