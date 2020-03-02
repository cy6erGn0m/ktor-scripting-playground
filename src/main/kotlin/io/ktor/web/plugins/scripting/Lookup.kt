package io.ktor.web.plugins.scripting

import io.ktor.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.*
import java.io.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.*
import kotlin.script.experimental.jvmhost.*
import kotlin.script.experimental.util.*

object Lookup : CoroutineScope {
    class CompiledPage(
        val pageName: String,
        val pageClass: KClass<PageScript>,
        val routePath: String
    ) {
        val route: RoutingPath = RoutingPath.parse(routePath)
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private val logger = LoggerFactory.getLogger("PageScriptCompiler")!!

    private val host = BasicJvmScriptingHost()
    private val compiler = host.compiler

    private val fileContentCache = ConcurrentHashMap<String, String>()
    private val compiledCache = ConcurrentHashMap<String, Deferred<CompiledPage>>()

    private val pageScriptClass = PageScript::class

    val pagesRoot: File = File("pages").absoluteFile

    fun pageNames(): List<String> = compiledCache.keys().toList().map { File(it).relativeTo(pagesRoot).path.removeSuffix(".page.kts") }

    fun collecting(input: Flow<File>): Flow<Deferred<CompiledPage>> = input.mapNotNull { file ->
        if (file.exists()) {
            val before = compiledCache[file.path]
            val newDeferred = pageCompilationAsync(file, before)

            val old = compiledCache.put(file.path, newDeferred)
            old?.cancel(RestartCancellationException())

            newDeferred
        } else {
            null
        }
    }

    suspend fun scriptClassFor(name: String): CompiledPage? {
        val file = fileForName(name)
        if (!file.exists()) {
            return null
        }

        do {
            val result = compiledCache.computeIfAbsent(file.path) {
                pageCompilationAsync(file)
            }
            try {
                return result.await()
            } catch (notFound: FileNotFoundException) {
                compiledCache.remove(name, result)
                return null
            } catch (cause: Throwable) {
                if (!fileForName(name).exists()) {
                    return null
                }

                if (!result.isCancelled || result.getCompletionExceptionOrNull() !is RestartCancellationException) {
                    throw cause
                }
            } catch (cause: kotlinx.coroutines.CancellationException) {
                if (!result.isCancelled) {
                    throw cause
                }
            }
        } while (true)
    }

    private fun pageCompilationAsync(
        file: File,
        before: Deferred<CompiledPage>? = null
    ): Deferred<CompiledPage> {
        return async {
            val pageName = file.absoluteFile.relativeTo(pagesRoot).path
                .removeSuffix(".page.kts")
            val content = file.readText()
            val cachedContent = fileContentCache.put(pageName, content)

            try {
                if (cachedContent == content && before != null && before.isCompleted && !before.isCancelled) {
                    return@async before.await()
                }
            } catch (_: Throwable) {
            }

            compileScript(pageName, content).takeIf { file.exists() }
                ?: throw CancellationException("Script file disappeared: $file", null)
        }
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

        val routePath = when {
            path0 != null -> path0
            // TODO location
            name == "index" -> "/"
            else -> "$name.html"
        }

        logger.info("Compiled successfully.")

        if (rawClass.isSubclassOf(pageScriptClass)) {
            @Suppress("UNCHECKED_CAST")
            val pageClass = rawClass as KClass<PageScript>
            return CompiledPage(name, pageClass, routePath)
        } else {
            error("The resulting compiled script class $rawClass is not a subclass of PageScript class.")
        }
    }

    private fun fileForName(name: String): File = listOf(name, "pages/$name.page.kts")
        .map { File(it) }
        .firstOrNull { it.exists() }
        ?.absoluteFile
        ?: error("No page with name $name found.")
}

private class RestartCancellationException : CancellationException("Compilation cancelled due to script change.")