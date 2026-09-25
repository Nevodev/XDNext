package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests for the card page's state holder.
 *
 * Three rules are worth pinning, and all three are about the *window*:
 *
 *  - the range and the rows move together, so changing the days never leaves an earlier window's list
 *    under a heading that names the new one;
 *  - the cache is read for the new range too, so changing back to days that were read before does not have
 *    to wait for the network;
 *  - a failure with nothing cached is an error, and a failure with something cached is a cached answer with
 *    a reason — the same policy as the campus tile's own card.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, to inspect the state while a query is parked.
class SchoolCardFlowsRepositoryTest {

    private val today = LocalDate(2026, 3, 5)
    private val todayRange = SchoolCardRange(today, today)
    private val week = SchoolCardRange(LocalDate(2026, 3, 1), today)

    private fun repository(
        source: FakeSource,
    ): SchoolCardFlowsRepository = SchoolCardFlowsRepository(
        source = source,
        clock = FixedClock(today),
        // The zone matters: "today" is read from the clock in it, and the tests name a day.
        timeZone = TimeZone.UTC,
    )

    @Test
    fun opensOnTodayAndShowsTheCachedRowsBeforeAnythingIsFetched() {
        // The page's first frame: the card at the top is 今日支出, so the window is today, and whatever was
        // remembered for it is already on screen.
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        val state = repository(FakeSource(cached = mapOf(todayRange to cachedAt(1, rows)))).state.value

        assertEquals(todayRange, state.range)
        assertEquals(rows, state.transactions)
        assertTrue(state.isFromCache)
        assertFalse(state.isLoading, "A cached list means there is nothing to wait for.")
        assertEquals(1230L, state.expenseCents, "The figure is the rows' own sum, not a second reading.")
        assertNull(state.error)
    }

    @Test
    fun opensWithNoRowsWhenNothingWasEverCached() = runTest {
        // Nothing read and nothing in flight: the page is then free to ask, and must not sit on a spinner
        // that nothing is behind.
        val state = repository(FakeSource()).state.value

        assertEquals(todayRange, state.range)
        assertEquals(emptyList(), state.transactions)
        assertFalse(state.isLoading)
        assertFalse(state.isRead, "Nothing was read, so 0.00 would be a claim nobody made.")
    }

    @Test
    fun aQueryReplacesTheCachedRowsAndTheirFreshnessTogether() = runTest {
        val source = FakeSource(cached = mapOf(todayRange to cachedAt(1, emptyList())))
        source.next = { fresh(listOf(SchoolCardTransaction("食堂", "2026-03-05 08:00:00", -350))) }
        val repository = repository(source)

        repository.refresh()

        val state = repository.state.value
        assertEquals(listOf("食堂"), state.transactions.map { it.merchant })
        assertFalse(state.isFromCache)
        assertNull(state.cacheHint)
        assertEquals(350L, state.expenseCents)
        assertTrue(state.isRead)
    }

    @Test
    fun changingTheRangeClearsTheOldRowsBeforeTheNewAnswerArrives() = runTest {
        // Rows describe a window. Redrawing today's list under a heading that says the first of the month
        // would be the same wrong answer a stale total would be, and no query is faster than the frame that
        // follows the tap.
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        val source = FakeSource(cached = mapOf(todayRange to cachedAt(1, rows)))
        source.next = { fresh(rows) }
        val repository = repository(source)
        repository.refresh()

        // The next query never answers, so what the state holds while it runs is what is inspected.
        source.hangs = true
        val query = launch { repository.selectRange(week) }
        runCurrent()

        val state = repository.state.value
        assertEquals(week, state.range)
        assertEquals(emptyList(), state.transactions, "The new window starts with nothing to show.")
        assertFalse(state.isFromCache)
        assertTrue(state.isLoading)

        query.cancel()
    }

    @Test
    fun changingBackToARangeReadsItsCacheWithoutWaitingForTheQuery() = runTest {
        // The stored window is what makes this worth doing: days that were read before are answered from
        // disk on the very next frame, off campus included.
        val rows = listOf(SchoolCardTransaction("食堂", "2026-03-02 12:00:00", -800))
        val source = FakeSource(cached = mapOf(week to cachedAt(7, rows)))
        source.next = { fresh(emptyList()) }
        val repository = repository(source)
        repository.refresh()

        source.hangs = true
        val query = launch { repository.selectRange(week) }
        runCurrent()

        val state = repository.state.value
        assertEquals(rows, state.transactions)
        assertTrue(state.isFromCache)
        assertEquals(Instant.fromEpochMilliseconds(7), state.fetchTime)

        query.cancel()
    }

    @Test
    fun selectingTheRangeAlreadyShownRefreshesRatherThanEmptyingTheList() = runTest {
        // Tapping the same two days is a request for fresh rows, not a reset: clearing them would blink the
        // list away to say the same thing.
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        val source = FakeSource(cached = mapOf(todayRange to cachedAt(1, rows)))
        source.next = { fresh(rows) }
        val repository = repository(source)
        repository.refresh()

        source.hangs = true
        val query = launch { repository.selectRange(todayRange) }
        runCurrent()

        assertEquals(rows, repository.state.value.transactions, "The rows stay while the refresh runs.")
        assertTrue(repository.state.value.isLoading)

        query.cancel()
    }

    @Test
    fun aFailureWithNothingCachedIsAnError() = runTest {
        val source = FakeSource()
        source.next = { throw IllegalStateException("no campus network") }
        val repository = repository(source)

        repository.refresh()

        val state = repository.state.value
        assertFalse(state.isLoading)
        assertEquals(emptyList(), state.transactions)
        assertFalse(state.isRead, "Nothing was read, so 0.00 would be a claim nobody made.")
        assertEquals("no campus network", state.error?.message)
    }

    @Test
    fun aFailureKeepsTheRowsOnScreen() = runTest {
        // A refresh that fails must not throw away rows that are already there — the same rule the campus
        // tile follows with its balance. The failure is recorded beside them; whether it is drawn as an
        // error or as *why the list is cached* is the difference the next test pins.
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        val source = FakeSource(cached = mapOf(todayRange to cachedAt(1, rows)))
        source.next = { throw IllegalStateException("no campus network") }
        val repository = repository(source)

        repository.refresh()

        val state = repository.state.value
        assertEquals(rows, state.transactions)
        assertFalse(state.isLoading)
        assertEquals("no campus network", state.error?.message)
    }

    @Test
    fun aCachedAnswerWithAReasonIsNotAnError() = runTest {
        // What the session hands back when its query fails and its cache covers the range: the rows with a
        // reason. Recording that as an error would draw the page's failure state over rows it still has.
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        val source = FakeSource()
        source.next = {
            FetchResult.cache(
                fetchTime = Instant.fromEpochMilliseconds(1),
                data = rows,
                hintKey = SchoolCardCacheHint.NETWORK_FAILED.key,
            )
        }
        val repository = repository(source)

        repository.refresh()

        val state = repository.state.value
        assertEquals(rows, state.transactions)
        assertTrue(state.isFromCache)
        assertEquals(SchoolCardCacheHint.NETWORK_FAILED, state.cacheHint)
        assertNull(state.error, "A cached list with a reason is not an error state.")
    }

    @Test
    fun aCancelledRefreshLeavesTheQuestionAsItWasAsked() = runTest {
        // This holder outlives the screen that started the query, so a cancelled one must not leave the page
        // claiming it is still loading — nor claim the cancelled attempt failed.
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        val source = FakeSource(cached = mapOf(todayRange to cachedAt(1, rows)))
        source.next = { throw CancellationException("left the page") }
        val repository = repository(source)

        assertFailsWith<CancellationException> { repository.refresh() }

        val state = repository.state.value
        assertFalse(state.isLoading)
        assertEquals(rows, state.transactions)
        assertNull(state.error)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun fresh(rows: List<SchoolCardTransaction>, at: Long = 100) = FetchResult.fresh(
        fetchTime = Instant.fromEpochMilliseconds(at),
        data = rows,
    )

    private fun cachedAt(at: Long, rows: List<SchoolCardTransaction>) = FetchResult.cache(
        fetchTime = Instant.fromEpochMilliseconds(at),
        data = rows,
    )

    /**
     * A source that answers what the test staged, from the cache map it was built with.
     *
     * [hangs] is the third possibility a query has, and the one the state holder's two rules need: a
     * request that has neither answered nor failed, so the state can be read while it is in flight.
     */
    private class FakeSource(
        private val cached: Map<SchoolCardRange, FetchResult<List<SchoolCardTransaction>>> = emptyMap(),
    ) : SchoolCardDataSource {

        var next: (() -> FetchResult<List<SchoolCardTransaction>>)? = null
        var hangs: Boolean = false

        override fun getCachedCard(today: LocalDate): FetchResult<SchoolCardSnapshot>? = null

        override suspend fun getSchoolCard(): FetchResult<SchoolCardSnapshot> =
            throw UnsupportedOperationException("the flow list does not read the card's own totals")

        override fun getCachedTransactions(
            range: SchoolCardRange,
        ): FetchResult<List<SchoolCardTransaction>>? = cached[range]

        override suspend fun getTransactions(
            range: SchoolCardRange,
        ): FetchResult<List<SchoolCardTransaction>> {
            if (hangs) awaitCancellation()
            return requireNotNull(next) { "the test did not stage a fetch" }.invoke()
        }
    }

    private class FixedClock(private val today: LocalDate) : Clock {
        override fun now(): Instant = Instant.parse("${today}T08:00:00Z")
    }
}
