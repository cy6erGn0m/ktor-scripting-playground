package io.ktor.web.plugins.scripting

import io.ktor.http.Parameters
import io.ktor.web.plugins.model.AppModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    fileExtension = "data.kts",
    displayName = "Data provider script.",
    compilationConfiguration = DataScriptCompilationConfiguration::class
)
abstract class DataScript(val model: AppModel, val page: Lookup.CompiledPage) {
    private var _builder: () -> Flow<Parameters> = {
        if (page.routeParameterNames.isEmpty()) {
            flowOf(Parameters.Empty)
        } else {
            emptyFlow()
        }
    }
    val builder: () -> Flow<Parameters>
        get() = _builder

    protected fun produce(block: () -> Flow<Parameters>) {
        _builder = block
    }
}

object DataScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        "kotlinx.coroutines.flow.*",
        "io.ktor.web.plugins.model.*",
        "io.ktor.web.plugins.scripting.*",
        "io.ktor.web.plugins.pages.*",
        "io.ktor.http.parametersOf",
        "io.ktor.http.Parameters"
    )


    compilerOptions.append("-Xexperimental=kotlinExperimental,kotlinx.coroutines.FlowPreview")

    jvm {
        dependenciesFromClassContext(
            DataScriptCompilationConfiguration::class,
            "kotlin-stdlib", "kotlinx-coroutines-core",
            "ktor-utils-jvm", "ktor-http-jvm",
            "main"
        )
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Project)
    }
})
