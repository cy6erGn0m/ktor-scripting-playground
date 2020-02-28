package io.ktor.web.plugins.scripting

import io.ktor.web.plugins.model.*
import kotlinx.html.*
import org.slf4j.*
import java.io.*
import java.util.concurrent.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.*
import kotlin.script.experimental.jvmhost.*

object Lookup {
    private val logger = LoggerFactory.getLogger("PageScriptCompiler")!!

    private val host = BasicJvmScriptingHost()
    private val compiler = host.compiler

    private val fileContentCache = ConcurrentHashMap<String, String>()
    private val compiledCache = ConcurrentHashMap<String, KClass<PageScript>>()

    private val pageScriptClass = PageScript::class

    suspend fun scriptClassFor(name: String): KClass<PageScript> {
        val content = fileContentCache.computeIfAbsent(name) { fileForName(name).readText() }
        return (compiledCache[name] ?: run {
            compileScript(name, content).also {
                compiledCache[name] = it
            }
        })
    }

    suspend fun scriptFor(name: String, html: HTML, model: AppModel): PageScript {
        return scriptClassFor(name).primaryConstructor!!.call(model, html)
    }

    private suspend fun compileScript(name: String, content: String?): KClass<PageScript> {
        val file = fileForName(name)
        val source: SourceCode = FileScriptSource(file, content)

        logger.info("Compiling ${source.name ?: name}")

        val result = compiler(source, PageScriptCompilationConfig)

        result.reports.forEach { report ->
            val text = report.render(withSeverity = false, withStackTrace = false, withException = false)

            when (report.severity) {
                ScriptDiagnostic.Severity.DEBUG -> logger.debug(text, report.exception)
                ScriptDiagnostic.Severity.INFO -> logger.info(text, report.exception)
                ScriptDiagnostic.Severity.WARNING -> logger.warn(text, report.exception)
                ScriptDiagnostic.Severity.ERROR -> logger.error(text, report.exception)
                ScriptDiagnostic.Severity.FATAL -> logger.error(text, report.exception)
            }
        }

        val rawClass = result.valueOrThrow().getClass(null).valueOrThrow()

        if (rawClass.isSubclassOf(pageScriptClass)) {
            @Suppress("UNCHECKED_CAST")
            return rawClass as KClass<PageScript>
        } else {
            error("The resulting compiled script class $rawClass is not a subclass of PageScript class.")
        }
    }

    private fun fileForName(name: String): File = listOf(name, "pages/$name.page.kts")
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?: error("No page with name $name found.")
}
