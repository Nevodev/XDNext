package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for the campus card's state holder.
 *
 * Two decisions are worth pinning, both inherited from the original's controller: a cached balance is
 * shown on the first frame and is *not* an error, and a failed refresh keeps whatever is on screen
 * while recording why it is stale. The original's own `dataRefreshing(previous)` state is the same
 * policy; what is asserted here is that this port did not lose it.
 */
class SchoolCardRepositoryTest {

    @Test
    fun showsTheCachedCardBeforeAnythingIsFetched() {
        val source = FakeSource(cached = cacheOf("88.00", expense = 1230, at = 1_700_000_000_000))

        val repository = SchoolCardRepository(source)

        val state = repository.state.value
        assertEquals("88.00", state.snapshot?.balance)
        assertEquals(1230L, state.snapshot?.todayExpenseCents, "The day's totals come back too.")
        assertTrue(state.isFromCache)
        assertFalse(state.isLoading, "A cached card means there is nothing to wait for.")
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000), state.fetchTime)
        assertNull(state.error)
    }

    @Test
    fun aCachedCardFromAnotherDayCarriesNoSpendingFigure() {
        // The cache holds a balance and a day's totals; only the balance survives the day, because a
        // zero where a figure is missing would be a claim about today that nothing supports.
        val repository = SchoolCardRepository(
            FakeSource(cached = cacheOf("88.00", expense = null, at = 1)),
        )

        val snapshot = repository.state.value.snapshot

        assertNull(snapshot?.todayExpenseCents)
    }

    @Test
    fun keepsThePreviousValueOnScreenWhileRefreshing() = runTest {
        val source = FakeSource()
        val repository = SchoolCardRepository(source)
        source.next = { snapshot("25.60", expense = 1230, at = 1) }
        repository.refreshSchoolCard()

        // The next refresh blocks until the test releases it, so the in-flight state can be inspected.
        source.next = { snapshot("25.60", expense = 1230, at = 2) }
        repository.refreshSchoolCard()

        val state = repository.state.value
        assertEquals(1230L, state.snapshot?.todayExpenseCents)
        assertFalse(state.isFromCache)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun aFailureWithNothingToShowIsAnError() = runTest {
        val source = FakeSource()
        val repository = SchoolCardRepository(source)
        source.next = { throw IllegalStateException("no campus network") }

        repository.refreshSchoolCard()

        val state = repository.state.value
        assertFalse(state.isLoading)
        assertNull(state.snapshot)
        assertEquals("no campus network", state.error?.message)
    }

    @Test
    fun aCachedResultIsNotAnError() = runTest {
        val source = FakeSource()
        val repository = SchoolCardRepository(source)
        source.next = {
            FetchResult.cache(
                fetchTime = Instant.fromEpochMilliseconds(7),
                data = SchoolCardSnapshot(
                    balance = "88.00",
                    todayExpenseCents = null,
                    todayIncomeCents = 0,
                ),
                hintKey = SchoolCardCacheHint.NETWORK_FAILED.key,
            )
        }

        repository.refreshSchoolCard()

        val state = repository.state.value
        assertTrue(state.isFromCache)
        assertEquals(SchoolCardCacheHint.NETWORK_FAILED, state.cacheHint)
        assertNull(state.error, "A stale balance with a reason is not an error state.")
        assertEquals("88.00", state.snapshot?.balance)
    }

    @Test
    fun clearsTheErrorOnASuccessfulRefresh() = runTest {
        val source = FakeSource()
        val repository = SchoolCardRepository(source)
        source.next = { throw IllegalStateException("no campus network") }
        repository.refreshSchoolCard()

        source.next = { snapshot("25.60", expense = 1230, at = 1) }
        repository.refreshSchoolCard()

        val state = repository.state.value
        assertNull(state.error)
        assertEquals(1230L, state.snapshot?.todayExpenseCents)
        assertNull(state.cacheHint)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun snapshot(balance: String, expense: Long, at: Long) = FetchResult.fresh(
        fetchTime = Instant.fromEpochMilliseconds(at),
        data = SchoolCardSnapshot(
            balance = balance,
            todayExpenseCents = expense,
            todayIncomeCents = 0,
        ),
    )

    private fun cacheOf(balance: String, expense: Long?, at: Long) = FetchResult.cache(
        fetchTime = Instant.fromEpochMilliseconds(at),
        data = SchoolCardSnapshot(
            balance = balance,
            todayExpenseCents = expense,
            todayIncomeCents = 0,
        ),
    )

    private class FakeSource(
        var cached: FetchResult<SchoolCardSnapshot>? = null,
    ) : SchoolCardDataSource {

        var next: (() -> FetchResult<SchoolCardSnapshot>)? = null

        override fun getCachedCard(today: LocalDate): FetchResult<SchoolCardSnapshot>? = cached

        override suspend fun getSchoolCard(): FetchResult<SchoolCardSnapshot> =
            requireNotNull(next) { "the test did not stage a fetch" }.invoke()
    }
}
