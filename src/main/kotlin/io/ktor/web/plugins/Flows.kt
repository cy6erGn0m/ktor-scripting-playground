package io.ktor.web.plugins

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.*
import java.util.concurrent.atomic.*
import kotlin.time.*


private fun <T> Flow<Deferred<T>>.awaitingSuccessful0(): Flow<T> {
    val completed = Channel<T>()
    val running = AtomicInteger(0)
    val finishing = AtomicBoolean(false)

    GlobalScope.launch {
        val jobScope = this

        onCompletion {
            finishing.set(true)
            if (running.get() == 0) {
                completed.close()
            }
        }.collect { deferred ->
            running.incrementAndGet()

            deferred.invokeOnCompletion { failure ->
                val remainingCount = running.decrementAndGet()
                if (failure == null) {
                    completed.sendBlocking(deferred.getCompleted())
                }

                if (remainingCount == 0 && finishing.get()) {
                    completed.close()
                }
            }
        }
    }

    return completed.consumeAsFlow()
}

@UseExperimental(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<Deferred<T>>.awaitingSuccessful(): Flow<T> {
    return channelFlow {
        collect { deferred ->
            launch {
                try {
                    send(deferred.await())
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }
}

private fun <T> Flow<Deferred<T>>.awaitingSuccessful2(): Flow<T> {
    return channelFlow<T> {
        val running = AtomicInteger(0)
        val finishing = AtomicBoolean(false)
        val finishLatch = CompletableDeferred<Unit>()

        onCompletion {
            finishing.set(true)
            if (running.get() > 0) {
                finishLatch.await()
            }
        }.collect { deferred ->
            running.incrementAndGet()
            deferred.invokeOnCompletion { failure ->
                val remainingCount = running.decrementAndGet()

                if (failure == null) {
                    try {
                        sendBlocking(deferred.getCompleted())
                    } catch (_: Throwable) {
                    }
                }

                if (remainingCount == 0 && finishing.get()) {
                    finishLatch.complete(Unit)
                }
            }
        }
    }
}

@UseExperimental(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
internal fun <T : Any> Flow<T>.debounceChanges(
    time: Duration,
    comparator: (T, T) -> Boolean = { a, b -> a == b }
): Flow<T> {
    return channelFlow<T> {
        val incoming = Channel<T>(Channel.BUFFERED)

        launch {
            collect {
                incoming.send(it)
            }
        }

        var last: T = incoming.receiveOrNull() ?: return@channelFlow

        while (isActive) {
            val element = select<T?> {
                incoming.onReceiveOrNull()
                onTimeout(time.toLongMilliseconds()) {
                    null
                }
            }

            last = if (element == null) {
                send(last)

                if (incoming.isClosedForReceive) {
                    break
                }

                incoming.receiveOrNull() ?: break
            } else if (last === element || comparator(last, element)) {
                element
            } else {
                send(last)
                element
            }
        }
    }
}
