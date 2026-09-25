package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Everything the campus card tile draws itself from.
 *
 * A single immutable snapshot rather than a set of separate signals, for the reason
 * [com.nevoit.xdnext.data.energy.EnergyState] is one: a frame must not be composable from two
 * different fetches.
 *
 * The two numbers inside [snapshot] can legitimately disagree about their provenance — the balance may
 * be cached while the day's spending is not known at all — which is why the "not known" case is
 * spelled `null` there rather than as a zero.
 */
data class SchoolCardState(
    /** True while an attempt is in flight; the previous value stays on screen. */
    val isLoading: Boolean = true,
    /** The card to display, from the network or from the cache. */
    val snapshot: SchoolCardSnapshot? = null,
    val isFromCache: Boolean = false,
    /** When [snapshot] was obtained — for a cached balance, when it was written. */
    val fetchTime: Instant? = null,
    /** Why the balance on screen is a cached one. */
    val cacheHint: SchoolCardCacheHint? = null,
    /** The last attempt's failure, when there was nothing at all to display. */
    val error: Throwable? = null,
)

/**
 * The campus card's state holder.
 *
 * Ported from `lib/controller/school_card_controller.dart`, which owned exactly one signal — the
 * balance — and left the day's transactions to the card page's own `FutureBuilder`. This holds both,
 * because the tile shows both, and it keeps the controller's two decisions:
 *
 *  - **a refresh is not an error until it has nothing to show.** The original set its state to
 *    `dataRefreshing(previous)` and only fell to `error` when the call failed, which is why the card
 *    keeps its number while a refresh is in flight.
 *  - **the cache is read synchronously in the constructor**, so the tile can draw a balance on the very
 *    first frame, before any coroutine has run.
 *
 * What it does not inherit is the original's habit of fetching on every home-page rebuild: the refresh
 * is triggered once per process from outside (see `CardRefresh`), and every later update is a tap.
 *
 * The clock is injected because the cache holds a *day's* totals as well as a balance, and "is that day
 * still today" is not a question a cache can answer on its own.
 */
class SchoolCardRepository(
    private val source: SchoolCardDataSource,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    private val _state = MutableStateFlow(SchoolCardState())

    /** The card, its freshness and the last error. */
    val state: StateFlow<SchoolCardState> = _state.asStateFlow()

    init {
        loadCachedCard()
    }

    /**
     * Reads a new value, keeping the displayed one on screen while the request is in flight.
     *
     * A failure with a cached balance is **not** an error state: the cache hint carries the reason
     * instead. Only a failure with nothing to show sets [SchoolCardState.error].
     */
    suspend fun refreshSchoolCard() {
        val previous = _state.value
        _state.value = previous.copy(isLoading = true, error = null)
        try {
            applyFetch(source.getSchoolCard())
        } catch (cancelled: CancellationException) {
            // See `TimetableRepository.refresh`: a cancelled refresh must not leave the state claiming it
            // is still running, because this repository outlives the screen that started it — the focus
            // page is unmounted on a tab switch — and the card would then say 正在查询 forever.
            _state.value = previous
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[SchoolCardRepository] Could not refresh the campus card" }
            _state.value = previous.copy(isLoading = false, error = error)
        }
    }

    private fun loadCachedCard() {
        val cached = source.getCachedCard(today()) ?: return
        _state.value = _state.value.copy(
            isLoading = false,
            snapshot = cached.data,
            isFromCache = true,
            fetchTime = cached.fetchTime,
            cacheHint = hintFor(cached.hintKey),
        )
    }

    private fun applyFetch(result: FetchResult<SchoolCardSnapshot>) {
        _state.value = _state.value.copy(
            isLoading = false,
            snapshot = result.data,
            isFromCache = result.isCache,
            fetchTime = result.fetchTime,
            cacheHint = hintFor(result.hintKey),
            error = null,
        )
    }

    private fun today() = clock.now().toLocalDateTime(timeZone).date

    private fun hintFor(key: String?): SchoolCardCacheHint? =
        key?.let { stored -> SchoolCardCacheHint.entries.firstOrNull { it.key == stored } }
}
