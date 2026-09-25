package com.nevoit.xdnext.data.schoolcard

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the campus card's on-disk cache.
 *
 * The rule worth pinning is that a balance and a day's spending age differently. A balance read
 * yesterday is still the balance today; "spent 12.30 today" is not, and drawing it tomorrow under the
 * label 今日支出 is a wrong answer that no timestamp on screen repairs. So the cache stores the day its
 * totals describe and hands them back only while that day is the one being asked about.
 */
class SchoolCardCacheTest {

    private val today = LocalDate(2026, 3, 5)

    @Test
    fun hasNothingToSayBeforeAnythingIsStored() {
        assertNull(cache().read(today))
    }

    @Test
    fun keepsTheBalanceAndTheDaysTotalsWhenTheyAreFromTheSameDay() {
        val cache = cache()
        cache.save(snapshot(balance = "25.60", expense = 1230), knownDate = today)

        val result = cache.read(today)!!

        assertTrue(result.isCache)
        assertEquals("25.60", result.data.balance)
        assertEquals(1230L, result.data.todayExpenseCents)
    }

    @Test
    fun keepsTheBalanceAndDropsTheDayWhenTheRefreshOnlyReadOneOfThem() {
        // The two parts of a card age differently: the balance is still the balance, and a day's total
        // with no day beside it is not a total. Nothing here carries rows, either — the card page's list
        // belongs to its own file and its own window, see `SchoolCardTradeListCache`.
        val cache = cache()
        cache.save(snapshot(balance = "25.60", expense = null), knownDate = null)

        val result = cache.read(today)!!

        assertEquals("25.60", result.data.balance)
        assertNull(result.data.todayExpenseCents)
    }

    @Test
    fun dropsTheDaysTotalsOnceTheDayHasPassed() {
        // The whole point: the balance survives, the day's figure does not, and the tile is then able to
        // say the spending is unknown instead of showing yesterday's under 今日支出.
        val cache = cache()
        cache.save(snapshot(balance = "25.60", expense = 1230), knownDate = today)

        val tomorrow = cache.read(today.plus(1, DateTimeUnit.DAY))!!

        assertEquals("25.60", tomorrow.data.balance)
        assertNull(tomorrow.data.todayExpenseCents)
    }

    @Test
    fun keepsTheBalanceWhenTheTotalsWereNeverRead() {
        // A refresh that read the balance but whose transaction query failed stores a balance with no day
        // attached — and that must still come back, or a failure would cost the user the number that did
        // arrive.
        val cache = cache()
        cache.save(snapshot(balance = "25.60", expense = null), knownDate = null)

        val result = cache.read(today)!!

        assertEquals("25.60", result.data.balance)
        assertNull(result.data.todayExpenseCents)
    }

    @Test
    fun readsAFileWrittenBeforeTheTotalsWereStored() {
        // The previous format held the balance alone. An upgrade must not lose it, and must not invent a
        // day for numbers it never had.
        val (direction, cache) = cacheIn()
        write(direction, """{"balance":"88.00"}""")
        val result = cache.read(today)!!

        assertEquals("88.00", result.data.balance)
        assertNull(result.data.todayExpenseCents)
    }

    @Test
    fun readsAFileWrittenBeforeTheFlowsWereStored() {
        // The flow list used to live in this very file, with the day's figures. Such a file must still
        // load: the balance and the day's totals are what this cache is for now, and the rows it also
        // happens to carry are ignored rather than taken as *this* file's answer — a list asked for a
        // window is `SchoolCardTradeListCache`'s, and reading one out of a file whose day has passed
        // would be exactly the mistake the two files exist to avoid.
        val (direction, cache) = cacheIn()
        write(
            direction,
            """{"balance":"88.00","knownDate":"2026-03-05","todayExpenseCents":1230,""" +
                    """"transactions":[{"merchant":"超市","time":"2026-03-05 12:03:11","amountCents":-1230}]}""",
        )

        val result = cache.read(today)!!

        assertEquals("88.00", result.data.balance)
        assertEquals(1230L, result.data.todayExpenseCents)
    }

    @Test
    fun anUnreadableFileIsAMissRatherThanAnError() {
        // A truncated cache must cost one refresh, not a broken tile.
        val (direction, cache) = cacheIn()
        write(direction, "{ not json")

        assertNull(cache.read(today))
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun cache(): SchoolCardCache = cacheIn().second

    /** A cache and the directory it owns, for tests that plant a file before reading. */
    private fun cacheIn(): Pair<okio.Path, SchoolCardCache> {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-card-cache-${instances++}"
        return directory to SchoolCardCache(FileSystem.SYSTEM, directory, JSON)
    }

    private fun write(directory: okio.Path, text: String) {
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(directory / "SchoolCard.json") { writeUtf8(text) }
    }

    private fun snapshot(balance: String?, expense: Long?) = SchoolCardSnapshot(
        balance = balance,
        todayExpenseCents = expense,
        todayIncomeCents = 0,
    )

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /** One directory per cache, so no two cases share a file. */
        var instances = 0
    }
}
