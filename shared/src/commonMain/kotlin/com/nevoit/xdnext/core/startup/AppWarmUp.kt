package com.nevoit.xdnext.core.startup

import com.nevoit.xdnext.core.log.appLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds the app's singletons before the first composition asks for them.
 *
 * Koin's bindings are lazy, so each one is constructed wherever it is first asked for — and the first
 * thing that asks is the composition: `App` injects the login view model and the Home shell injects three
 * repositories. That put a Ktor client, a KeyStore read and three cache reads with their JSON decodes on
 * the critical path of the first frame, synchronously, on the main thread.
 *
 * None of that work can simply be *deferred*, which is why it is moved instead of made asynchronous. The
 * three repositories read their caches in their constructors on purpose: a cached card belongs on the
 * first frame, the timetable is usable before any coroutine has run, and off campus it may never be
 * anything else. Deferring would trade a slow first frame for a first frame that is empty and then fills
 * in, which is a worse trade at exactly the moment there is nothing else on screen.
 *
 * So [start] builds them early, off the main thread and concurrently, in the gap between
 * `Application.onCreate` and the first composition. A step that has not finished by then is not a
 * failure: the composition asks Koin for the same singleton and waits on Koin's own lock for the
 * remainder, which is still no worse than having done the work there. The steps are independent — three
 * cache files and a login graph that shares only an HTTP client with them — so the slowest one sets the
 * wall time rather than the sum.
 *
 * A step that throws is logged and dropped. A warm-up is an optimisation; it must never be able to fail
 * a launch, and one unreadable cache file must not stop the other three from being read.
 */
class AppWarmUp(
    /** The singletons to build, in the order they should be logged. */
    private val steps: List<Step>,
) {

    /**
     * One singleton to build, and what to call it in the log.
     *
     * A name and a thunk rather than the value itself, because taking the value here would construct it
     * wherever *this* is constructed — which is the main thread, in `startKoin`.
     */
    class Step(
        val name: String,
        val resolve: () -> Any,
    )

    /**
     * The warm-up's own lifetime, which is the process's.
     *
     * It is never cancelled: the work it does is the work the first frame would otherwise do, so
     * cancelling it would put the cost back on the main thread with nothing to show for it.
     *
     * [Dispatchers.Default] rather than an IO dispatcher because the work is shared code's, and because
     * it is as much JSON decoding as file reading. Either is a large improvement on the main thread it
     * used to run on.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Starts the warm-up and returns immediately.
     *
     * The [Deferred] completes with the names of the steps that were built, and exists for a caller —
     * in practice a test — that wants to wait for the graph to be warm and know what happened. The app
     * deliberately does not wait: waiting is the one thing a warm-up must not do.
     */
    fun start(): Deferred<List<String>> = scope.async {
        val built = coroutineScope {
            steps.map { step ->
                async {
                    val startedAt = StartupClock.elapsedMs()
                    runCatching { step.resolve() }
                        .onSuccess {
                            appLog.i {
                                "[Startup] ${step.name} built in " +
                                    "${StartupClock.elapsedMs() - startedAt}ms"
                            }
                        }
                        .onFailure { error ->
                            appLog.w(error) {
                                "[Startup] ${step.name} was not built; the first composition will " +
                                    "build it instead"
                            }
                        }
                        .fold(onSuccess = { step.name }, onFailure = { null })
                }
            }.awaitAll().filterNotNull()
        }

        appLog.i { "[Startup] graph warm at ${StartupClock.elapsedMs()}ms" }
        built
    }
}
