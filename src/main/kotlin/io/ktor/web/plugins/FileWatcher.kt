package io.ktor.web.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.file.*

fun fileChanges(rootDir: File, predicate: (File) -> Boolean): Flow<File> = flow<File> {
    check(rootDir.isDirectory)

    do {
        val watcher = FileSystems.getDefault().newWatchService()!!
        val rootKey = rootDir.toPath().registerAll(watcher)
        val keys = HashMap<Path, WatchKey>()

        rootDir.walkTopDown()
            .onEnter {
                if (it != rootDir && predicate(it)) {
                    it.toPath().toAbsolutePath().let { path -> keys[path] = path.registerAll(watcher) }
                    true
                } else it == rootDir
            }
            .filter { predicate(it) }
            .forEach {
                emit(it)
            }

        try {
            do {
                val selectedKey = watcher.take()
                val basePath = selectedKey.watchable() as? Path

                selectedKey.pollEvents().forEach { event ->
                    val path = (event.context() as? Path)?.let { sub -> basePath?.let { sup ->
                        sup.resolve(sub)
                    }}?.toAbsolutePath()

                    val file = path?.toFile()
                    if (file != null && predicate(file)) {
                        when (event.kind()) {
                            StandardWatchEventKinds.ENTRY_CREATE -> {
                                if (file.isDirectory) {
                                    keys[path] = path.registerAll(watcher)
                                }
                            }
                            StandardWatchEventKinds.ENTRY_DELETE -> {
                                keys[path]?.cancel()
                            }
                            StandardWatchEventKinds.OVERFLOW -> {
                                throw OverflowException()
                            }
                        }

                        emit(file)
                    }
                }

                selectedKey.reset()
            } while (true)
        } catch (_: OverflowException) {
            // restart
        } finally {
            rootKey.cancel()
            keys.values.forEach { it.cancel() }
            watcher.close()
        }
    } while (true)
}.flowOn(Dispatchers.IO)

private fun Path.registerAll(watchService: WatchService): WatchKey {
    println("Registering $this")
    return toAbsolutePath().register(watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY)
}

private class OverflowException : IllegalStateException()
