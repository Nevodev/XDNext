package com.nevoit.xdnext.core.concurrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces concurrent calls into one in-flight operation.
 *
 * Ported from `lib/repository/single_flight.dart`. The completed result is **not** cached: a call
 * made after the operation settles starts a new one. Only calls that overlap share an answer.
 *
 * The original used `Future.whenComplete` with an identity check to clear the slot. A
 * [CompletableDeferred] plus a [Mutex] is the same thing with a memory model that does not require
 * the reader to reason about a `late final` variable escaping its own initialiser.
 *
 * Callers that join an in-flight operation observe its failure as their own, exactly as awaiting the
 * original's shared `Future` did.
 */
class SingleFlight<T> {

    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<T>? = null

    /** True while an operation is running; the original exposed the same for diagnostics. */
    val isRunning: Boolean
        get() = inFlight != null

    suspend fun run(block: suspend () -> T): T {
        val (deferred, isCaller) = mutex.withLock {
            val running = inFlight
            if (running != null) running to false else CompletableDeferred<T>().also {
                inFlight = it
            } to true
        }

        if (!isCaller) return deferred.await()

        try {
            val value = block()
            deferred.complete(value)
            return value
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            mutex.withLock { if (inFlight === deferred) inFlight = null }
        }
    }
}
