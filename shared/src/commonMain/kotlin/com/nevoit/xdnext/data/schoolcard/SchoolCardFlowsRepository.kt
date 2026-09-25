package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Everything the card page draws itself from: the days it is showing, and the flows in them.
 *
 * One immutable snapshot rather than separate signals, for the reason the card's own state is one: a
 * frame must not be composable from two different queries. The [range] is part of that snapshot rather
 * than a field beside it, because the rows and the days they belong to are only meaningful together —
 * see [SchoolCardFlowsState.range].
 */
data class SchoolCardFlowsState(
    /** True while a query is in flight; the previous value stays on screen when the range is the same. */
    val isLoading: Boolean = true,
    /**
     * The days [transactions] describe — the page's selection, and what its heading claims.
     *
     * The two are never updated apart: a new range is a new answer, so the rows are cleared with it
     * instead of being drawn under a heading that no longer describes them.
     */
    val range: SchoolCardRange,
    /** The flows of [range], newest first. Empty means "nothing in these days", not "not read". */
    val transactions: List<SchoolCardTransaction> = emptyList(),
    val isFromCache: Boolean = false,
    /** When [transactions] were fetched — for a cached list, when it was written. */
    val fetchTime: Instant? = null,
    /** Why the list on screen is a cached one. */
    val cacheHint: SchoolCardCacheHint? = null,
    /** The last attempt's failure, when there was nothing at all to display. */
    val error: Throwable? = null,
) {

    /** What the range's rows cost, summed from the very rows on screen. */
    val expenseCents: Long get() = transactions.expenseCents()

    /** What was added to the card in the range, from the same rows. */
    val incomeCents: Long get() = transactions.incomeCents()

    /** Whether a query has ever answered for [range] — false is "not read", which is not zero. */
    val isRead: Boolean get() = fetchTime != null
}

/**
 * The card page's state holder.
 *
 * The page asks a *question* — "what did I spend between these two days" — so this holds the question as
 * well as the answer, and [selectRange] is the one way the question changes. Two decisions are worth
 * naming:
 *
 *  - **a new range clears the rows.** A refresh of the same range keeps them on screen while it runs
 *    (that is what "refreshing" means), but yesterday's answer under a heading that says last month is
 *    the same wrong answer a stale total would be, so the two move together.
 *  - **the cache is read synchronously in the constructor**, for today, so the page can draw a list on
 *    the very first frame before any coroutine has run. Reading it is not a decision to *stop* there: the
 *    page asks for a query when it opens, and the cache is what covers the seconds that query takes.
 *
 * The default range is **today**, which is the card's own day: the page opens on the figure the campus
 * tile shows, and widening it is a tap. The original opened on the whole month instead, and its picker
 * could reach any window at all — both are the same query here, so what the page opens on is a choice
 * about what to show first rather than about what can be asked.
 *
 * The clock is injected because "today" is not a question a cache can answer on its own, as in
 * [SchoolCardRepository].
 */
class SchoolCardFlowsRepository(
    private val source: SchoolCardDataSource,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    private val _state = MutableStateFlow(SchoolCardFlowsState(range = todayRange()))

    /** The list, the days it covers, its freshness and the last error. */
    val state: StateFlow<SchoolCardFlowsState> = _state.asStateFlow()

    init {
        showCachedOrKeepWaiting(_state.value.range)
    }

    /**
     * Re-reads the days currently on screen, keeping them on screen while the request is in flight.
     *
     * A failure with a cached answer is **not** an error state: the cache hint carries the reason
     * instead. Only a failure with nothing to show sets [SchoolCardFlowsState.error].
     */
    suspend fun refresh() {
        val range = _state.value.range
        val previous = _state.value
        _state.value = previous.copy(isLoading = true, error = null)
        try {
            applyFetch(source.getTransactions(range))
        } catch (cancelled: CancellationException) {
            // As in `SchoolCardRepository.refresh`: this repository outlives the screen that started it,
            // and a cancelled refresh must not leave the page claiming it is still querying.
            _state.value = previous
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[SchoolCardFlowsRepository] Could not read the flows of $range" }
            _state.value = previous.copy(isLoading = false, error = error)
        }
    }

    /**
     * Shows [range] and reads it: the cache first if it covers those days, the query after it.
     *
     * Selecting the range already on screen is a refresh rather than a reset, so tapping the same two
     * days twice does not blink the list away.
     */
    suspend fun selectRange(range: SchoolCardRange) {
        if (range == _state.value.range) {
            refresh()
            return
        }

        // A fresh state, not a copy: the rows, the freshness and the last failure all describe the old
        // range and none of them may be drawn under the new one.
        _state.value = SchoolCardFlowsState(range = range, isLoading = true)
        showCachedOrKeepWaiting(range)
        refresh()
    }

    /** Puts the cached rows of [range] on screen when there are any, and hands the question back when not. */
    private fun showCachedOrKeepWaiting(range: SchoolCardRange) {
        val cached = source.getCachedTransactions(range)
        _state.value = _state.value.copy(
            // Never left true here: this method answers from disk or not at all, and a state that claimed
            // a request was in flight would be a spinner with nothing behind it — the page would then
            // wait for a query that nobody started.
            isLoading = false,
            range = range,
            transactions = cached?.data.orEmpty(),
            isFromCache = cached != null,
            fetchTime = cached?.fetchTime,
            cacheHint = hintFor(cached?.hintKey),
        )
    }

    private fun applyFetch(result: FetchResult<List<SchoolCardTransaction>>) {
        _state.value = _state.value.copy(
            isLoading = false,
            transactions = result.data,
            isFromCache = result.isCache,
            fetchTime = result.fetchTime,
            cacheHint = hintFor(result.hintKey),
            error = null,
        )
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    private fun todayRange(): SchoolCardRange = today().let { SchoolCardRange(it, it) }

    private fun hintFor(key: String?): SchoolCardCacheHint? =
        key?.let { stored -> SchoolCardCacheHint.entries.firstOrNull { it.key == stored } }
}
