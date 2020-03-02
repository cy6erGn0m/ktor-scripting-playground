package io.ktor.web.plugins.scripting

import io.ktor.web.plugins.fileWatcherFlow
import io.ktor.web.plugins.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.html.*
import org.slf4j.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.*
import kotlin.script.experimental.jvmhost.*
import kotlin.script.experimental.util.PropertiesCollection

object Lookup : CoroutineScope {
    class CompiledPage(
        val pageClass: KClass<PageScript>,
        val route: String?
    )

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private val logger = LoggerFactory.getLogger("PageScriptCompiler")!!

    private val host = BasicJvmScriptingHost()
    private val compiler = host.compiler

    private val fileContentCache = ConcurrentHashMap<String, String>()
    private val compiledCache = ConcurrentHashMap<String, Deferred<CompiledPage>>()

    private val pageScriptClass = PageScript::class

    private val pagesRoot = File("pages").absoluteFile
    private val watchJob = fileWatcherFlow(File("pages")) { it.isDirectory || it.name.endsWith(".page.kts")}
        .onEach { file ->
            if (file.exists()) {
                val newDeferred = async {
                    compileScript(file.relativeTo(pagesRoot)!!.path.removeSuffix(".page.kts"), null)
                }

                val old = compiledCache.put(file.path, newDeferred)
                old?.cancel()
            } else {
                compiledCache.remove(file.path)?.cancel()
            }
        }
        .launchIn(this)

    suspend fun scriptClassFor(name: String): CompiledPage {
        do {
            val result = compiledCache.computeIfAbsent(name) {
                async {
                    compileScript(name, null)
                }
            }
            try {
                return result.await()
            } catch (cause: CancellationException) {
                if (!result.isCancelled) {
                    throw cause
                }
            }
        } while (true)
    }

    @Deprecated("")
    suspend fun scriptFor(name: String, html: HTML, model: AppModel): PageScript {
        return scriptClassFor(name).pageClass.primaryConstructor!!.call(model, html)
    }

    private suspend fun compileScript(name: String, content: String?): CompiledPage {
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

        val config = result.valueOrThrow().compilationConfiguration
        val path0: String? = config[PropertiesCollection.Key<String>("route", null)]
        val rawClass = result.valueOrThrow().getClass(null).valueOrThrow()

        logger.info("Compiled successfully.")

        if (rawClass.isSubclassOf(pageScriptClass)) {
            @Suppress("UNCHECKED_CAST")
            val pageClass = rawClass as KClass<PageScript>
            return CompiledPage(pageClass, path0)
        } else {
            error("The resulting compiled script class $rawClass is not a subclass of PageScript class.")
        }
    }

    private fun fileForName(name: String): File = listOf(name, "pages/$name.page.kts")
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?: error("No page with name $name found.")
}
