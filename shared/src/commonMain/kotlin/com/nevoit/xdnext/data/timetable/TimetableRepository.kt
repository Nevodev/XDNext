package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Who the timetable page is waiting on.
 *
 * The original's page had four of these — 课表, 考试, 物理实验 and 其他实验 — because its grid projects
 * all four onto one week, and its status banner names whichever are loading or cached. Only the
 * timetable exists here so far, and this type is a list's element rather than a flag precisely so the
 * other three slot in without the banner, the error summary or their translations changing shape.
 * The labels are the original's own (`classtable.status_source.*`).
 */
enum class TimetableSource(val label: String) {
    ClassTable("课表"),
}

/**
 * Everything the timetable page draws itself from.
 *
 * A single immutable snapshot rather than the original's set of separate signals — the same decision
 * `EnergyState` and `SchoolCardState` make, and for the same reason: a frame must not be composable
 * from two different fetches. The original read `classTableComputedSignal`,
 * `isClassTableFromCacheComputedSignal` and `classTableCacheHintKeyComputedSignal` one at a time while
 * a refresh could land between them.
 *
 * [data] is present from the first frame when a cache exists, which is the point of reading the cache
 * in the constructor: the timetable is usable before any coroutine has run, and off campus it may
 * never be anything else.
 */
data class TimetableState(
    /** True while an attempt is in flight; the previous value stays on screen. */
    val isLoading: Boolean = true,
    /** The timetable to draw, from the network or from the cache. */
    val data: ClassTableData? = null,
    val isFromCache: Boolean = false,
    /** When [data] was obtained — for a cached value, when it was written. */
    val fetchTime: Instant? = null,
    /** Why the timetable on screen is a cached one. */
    val cacheHint: TimetableCacheHint? = null,
    /** The last attempt's failure, when there was nothing at all to display. */
    val error: Throwable? = null,
) {

    /** The semester being shown, which the empty page names and the title bar can show. */
    val semesterCode: String get() = data?.semesterCode.orEmpty()

    /** The day the semester starts teaching, or null when it is unknown or unreadable. */
    val termStartDay: LocalDate? get() = data?.termStartDay?.let(TimetableCalendar::termStartDay)

    /** Whether there is a grid to draw at all. */
    val hasClasses: Boolean get() = data?.hasClasses == true

    /**
     * Whether this device has a timetable — cached or just fetched.
     *
     * Deliberately coarser than [hasClasses]: a semester the registrar has not opened yet answers with a
     * term start day and no rows, and that page is still a timetable worth drawing. This is the question
     * "is the term already here", which is what decides whether the term or the two cards is fetched
     * first at start-up — see `CardRefresher`.
     */
    val hasTimetable: Boolean get() = data != null

    /**
     * Who is being waited on, for the banner.
     *
     * Non-empty for the whole of a refresh, cached timetable or not: the original's banner announced
     * 正在更新 whenever the controller's state was loading, and a refresh that is running with a
     * timetable already on screen is exactly the case that notice is for.
     */
    val loadingSources: List<TimetableSource>
        get() = if (isLoading) listOf(TimetableSource.ClassTable) else emptyList()

    /** Who is being served from the cache, for the banner. */
    val cacheSources: List<TimetableSource>
        get() = if (isFromCache) listOf(TimetableSource.ClassTable) else emptyList()

    /**
     * Sources whose refresh failed with nothing to fall back to.
     *
     * Non-empty only in the case where the session had no cache to serve: a refresh that failed *with*
     * one does not set [error] at all, it annotates the cached timetable with a [cacheHint] — which is
     * why the two lists below can never both name the same source.
     */
    val errorWithoutCacheSources: List<TimetableSource>
        get() = if (error != null) listOf(TimetableSource.ClassTable) else emptyList()

    /** Sources being served from the cache *because* their refresh failed. */
    val errorWithCacheSources: List<TimetableSource>
        get() = if (error == null && isFromCache && cacheHint != null) {
            listOf(TimetableSource.ClassTable)
        } else {
            emptyList()
        }
}

/**
 * The timetable's state holder.
 *
 * Ported from `lib/controller/classtable_controller.dart`, keeping that controller's two decisions:
 *
 *  - **a refresh is not an error until it has nothing to show.** The original moved its state to
 *    `dataRefreshing(previous)` and only fell to `error` when the call itself failed, which is why the
 *    grid stays up while a refresh runs.
 *  - **the cache is read synchronously in the constructor**, so the first frame draws the last
 *    timetable rather than a spinner.
 *
 * What it does not carry over is the original's signal graph: `classTableComputedSignal` also folded
 * in the sports-classroom merge and the user-defined classes, both of which are separate modules here
 * that will project onto the grid through an overlay rather than by rewriting this data.
 */
class TimetableRepository(
    private val source: TimetableDataSource,
) {

    private val _state = MutableStateFlow(TimetableState())

    /** The timetable, its freshness and the last error. */
    val state: StateFlow<TimetableState> = _state.asStateFlow()

    init {
        loadCachedClassTable()
    }

    /**
     * Reads a new timetable, keeping the displayed one on screen while the request is in flight.
     *
     * A failure with a cached timetable is **not** an error state: the cache hint carries the reason
     * instead. Only a failure with nothing to show sets [TimetableState.error].
     */
    suspend fun refresh() {
        val previous = _state.value
        _state.value = previous.copy(isLoading = true, error = null)
        try {
            applyFetch(source.getClassTable())
        } catch (cancelled: CancellationException) {
            // Put the state back before letting the cancellation through. These repositories are
            // singletons and outlive whoever asked for the refresh — a tab switch unmounts the page that
            // started the start-up load — so a state left saying "loading" would strand every later
            // reader on a spinner for a request that is no longer running, and the timetable page would
            // draw its empty body with no way to tell it apart from "this semester has no courses".
            _state.value = previous
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[TimetableRepository] Could not refresh the timetable" }
            _state.value = previous.copy(isLoading = false, error = error)
        }
    }

    private fun loadCachedClassTable() {
        val cached = source.getCachedClassTable() ?: return
        _state.value = _state.value.copy(
            isLoading = false,
            data = cached.data,
            isFromCache = true,
            fetchTime = cached.fetchTime,
            cacheHint = hintFor(cached.hintKey),
        )
    }

    private fun applyFetch(result: FetchResult<ClassTableData>) {
        _state.value = _state.value.copy(
            isLoading = false,
            data = result.data,
            isFromCache = result.isCache,
            fetchTime = result.fetchTime,
            cacheHint = hintFor(result.hintKey),
            error = null,
        )
    }

    private fun hintFor(key: String?): TimetableCacheHint? =
        key?.let { stored -> TimetableCacheHint.entries.firstOrNull { it.key == stored } }
}
