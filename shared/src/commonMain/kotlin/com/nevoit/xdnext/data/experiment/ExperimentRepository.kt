package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * Everything the experiment page and the timetable's overlay draw from.
 *
 * A single immutable snapshot rather than the original's set of separate signals — the same decision
 * `TimetableState` and `EnergyState` make, and for the same reason: a frame must not be composable from
 * two different fetches. The original read `physicsExperiments`, `isPhysicsExperimentFromCache` and
 * `physicsExperimentCacheHintKey` one at a time while a refresh could land between them.
 *
 * [data] is present from the first frame when a cache exists, which is the point of reading the cache in
 * the constructor: the lab site is only reachable from the campus network, so off campus the cache may
 * never be anything but the answer.
 */
data class ExperimentState(
    /** True while an attempt is in flight; the previous value stays on screen. */
    val isLoading: Boolean = true,
    /** The bookings, from the network or from the cache. */
    val data: List<ExperimentEntry>? = null,
    val isFromCache: Boolean = false,
    /** When [data] was obtained — for a cached value, when it was written. */
    val fetchTime: Instant? = null,
    /** Why the list on screen is a cached one. */
    val cacheHint: ExperimentCacheHint? = null,
    /** The last attempt's failure, when there was nothing at all to display. */
    val error: Throwable? = null,
) {

    val entries: List<ExperimentEntry> get() = data.orEmpty()

    /** Whether this device has a list — cached or just fetched. */
    val hasData: Boolean get() = data != null

    /**
     * Whether the page should offer to set the credentials.
     *
     * True both when the fetch failed for want of a password and when the list on screen was fetched
     * with one that is no longer stored, because the remedy is the same in both cases.
     */
    val needsPassword: Boolean
        get() = error is ExperimentNoPasswordException ||
                cacheHint == ExperimentCacheHint.MISSING_PASSWORD

    /** Whether the page should offer its error summary, which is the same condition as `TimetableState`. */
    val hasError: Boolean get() = error != null || cacheHint != null
}

/**
 * The experiment module's state holder.
 *
 * Ported from `lib/controller/physics_experiment_controller.dart`, keeping that controller's two
 * decisions:
 *
 *  - **a refresh is not an error until it has nothing to show.** The original moved its state to
 *    `dataRefreshing(previous)` and only fell to `error` when the call itself failed, which is why a
 *    list stays up while a refresh runs;
 *  - **the cache is read synchronously in the constructor**, so the first frame draws the last known
 *    bookings rather than a spinner.
 *
 * What it does not carry over is the original's semester effect, which deleted the cached list **and
 * the stored password** whenever the registrar's semester changed. Losing the credential is not a
 * consequence a semester boundary should have; the list is refreshed when the page asks, which is what
 * makes it current.
 */
class ExperimentRepository(
    private val source: ExperimentDataSource,
) {

    private val _state = MutableStateFlow(ExperimentState())

    /** The experiments, their freshness and the last error. */
    val state: StateFlow<ExperimentState> = _state.asStateFlow()

    init {
        loadCachedExperiments()
    }

    /**
     * Reads a new list, keeping the displayed one on screen while the request is in flight.
     *
     * A failure with a cached list is **not** an error state: the cache hint carries the reason instead.
     * Only a failure with nothing to show sets [ExperimentState.error].
     */
    suspend fun refresh() {
        val previous = _state.value
        _state.value = previous.copy(isLoading = true, error = null)
        try {
            applyFetch(source.getExperiments())
        } catch (cancelled: CancellationException) {
            // Put the state back before letting the cancellation through — the repository is a
            // singleton that outlives whoever asked for the refresh, so a state left saying "loading"
            // would strand every later reader on a spinner for a request that is no longer running.
            _state.value = previous
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[ExperimentRepository] Could not refresh the experiments" }
            _state.value = previous.copy(isLoading = false, error = error)
        }
    }

    /**
     * Drops the cached list and asks again, for a change of credentials.
     *
     * The cache describes *an account's* bookings, so a list fetched with the old password is not a
     * fallback worth keeping: it would be shown under a heading about the new one. The state is emptied
     * rather than kept, and the refresh that follows is what fills it.
     */
    suspend fun onCredentialsChanged() {
        source.invalidateCache()
        _state.value = ExperimentState(isLoading = true)
        refresh()
    }

    /**
     * One score image's bytes, or null when it cannot be read.
     *
     * A page asks for this to show *what the mark's picture looks like*, which is the one thing that
     * makes an unrecognised score actionable. A failure is null rather than an exception because the
     * dialog that calls it is a detail, not the page: a picture that will not load must not take the
     * page down with it.
     */
    suspend fun scoreImage(url: String): ByteArray? = try {
        source.getScoreImage(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        appLog.w(error) { "[ExperimentRepository] Could not fetch a score image" }
        null
    }

    private fun loadCachedExperiments() {
        val cached = source.getCachedExperiments() ?: return
        _state.value = _state.value.copy(
            isLoading = false,
            data = cached.data,
            isFromCache = true,
            fetchTime = cached.fetchTime,
            cacheHint = hintFor(cached.hintKey),
        )
    }

    private fun applyFetch(result: FetchResult<List<ExperimentEntry>>) {
        _state.value = _state.value.copy(
            isLoading = false,
            data = result.data,
            isFromCache = result.isCache,
            fetchTime = result.fetchTime,
            cacheHint = hintFor(result.hintKey),
            error = null,
        )
    }

    private fun hintFor(key: String?): ExperimentCacheHint? =
        key?.let { stored -> ExperimentCacheHint.entries.firstOrNull { it.key == stored } }
}
