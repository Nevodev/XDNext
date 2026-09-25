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
 * Tests for what the card page's list remembers between visits.
 *
 * The file holds a *window's* rows, so everything worth pinning is about the window: which questions the
 * stored answer may serve, and which it must refuse. A cache that answered a wider question than it was
 * read for would put a partial list on screen under a heading that claimed completeness — the rows would
 * look like an answer, and nothing on screen could reveal the days that were missing.
 */
class SchoolCardTradeListCacheTest {

    private val today = LocalDate(2026, 3, 5)
    private val week = SchoolCardRange(LocalDate(2026, 3, 1), today)

    @Test
    fun keepsTheRowsAndTheWindowTheyAnswerFor() {
        val (direction, cache) = cacheIn()
        val rows = listOf(
            SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230),
            SchoolCardTransaction("圈存", "2026-03-02 09:00:00", 20000),
        )

        cache.save(week, rows)
        val result = cache.read(week)!!

        assertTrue(result.isCache)
        assertEquals(rows, result.data)
        assertEquals(
            week,
            storedRange(direction),
            "The window is written down with the rows: a list without one cannot be checked, only trusted.",
        )
    }

    @Test
    fun answersANarrowerWindowFromAWiderOne() {
        // The useful half of the rule, and the reason the window is stored at all: the page opens on today,
        // and a visit that already read the month can serve it without another request off campus.
        val (_, cache) = cacheIn()
        val rows = listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230))
        cache.save(SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)), rows)

        assertEquals(rows, cache.read(week)!!.data, "A narrower window is inside the stored one.")
        assertEquals(rows, cache.read(today.let { SchoolCardRange(it, it) })!!.data)
    }

    @Test
    fun refusesAWindowTheStoredOneDoesNotCover() {
        // The rows are the rows *in* a window, so anything reaching outside it would be answered with a
        // list quietly missing those days — which looks exactly like a window with nothing in it.
        val (_, cache) = cacheIn()
        cache.save(week, listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230)))

        assertNull(cache.read(SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))), "Wider.")
        assertNull(cache.read(SchoolCardRange(LocalDate(2026, 2, 28), today)), "Overlapping on the left.")
        assertNull(cache.read(SchoolCardRange(today, LocalDate(2026, 3, 9))), "Overlapping on the right.")
        assertNull(cache.read(SchoolCardRange(LocalDate(2026, 3, 6), LocalDate(2026, 3, 7))), "Disjoint.")
    }

    @Test
    fun remembersAnEmptyWindowAsAnAnswer() {
        // "Nothing was spent in these days" is a real answer and is worth remembering; it must come back as
        // an empty list with the window beside it, not as a miss that costs another query.
        val (_, cache) = cacheIn()
        cache.save(week, emptyList())

        val result = cache.read(week)!!

        assertEquals(emptyList(), result.data)
        assertEquals(emptyList(), cache.read(today.let { SchoolCardRange(it, it) })!!.data)
    }

    @Test
    fun aMissingFileIsAMissRatherThanAnEmptyList() {
        // The distinction the page rests on: null is "not read", and an empty list is "read, and there was
        // nothing". Returning the second for the first would answer a question nobody asked.
        val (_, cache) = cacheIn()

        assertNull(cache.read(week))
    }

    @Test
    fun anUnreadableFileIsAMissRatherThanAnError() {
        val (direction, cache) = cacheIn()
        write(direction, "{ not json")

        assertNull(cache.read(week))
    }

    @Test
    fun readsAFileThatHasNoWindowAsAMiss() {
        // A file written by a version that stored rows without saying which days they were: it loads, and
        // then refuses to answer, because rows with no window are exactly what this file exists to reject.
        val (direction, cache) = cacheIn()
        write(
            direction,
            """{"transactions":[{"merchant":"超市","time":"2026-03-05 12:03:11","amountCents":-1230}]}""",
        )

        assertNull(cache.read(week))
    }

    @Test
    fun dropsRowsOfADayThatHasSincePassedOnlyWhenTheWindowSaysSo() {
        // There is no "the day passed" rule here, deliberately: a day's rows were that day's rows before
        // the day ended, and the window they were read for is what says whether they still answer. Asking
        // for a window that starts after them is refused because it is not *covered*, not because it is
        // old — the same reason the stored answer for a range keeps working tomorrow.
        val (_, cache) = cacheIn()
        cache.save(week, listOf(SchoolCardTransaction("超市", "2026-03-05 12:03:11", -1230)))

        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        assertNull(
            cache.read(SchoolCardRange(tomorrow, tomorrow)),
            "Tomorrow is not inside last week, so there is nothing to answer with.",
        )
        assertEquals(1, cache.read(week)!!.data.size, "The window itself still answers — it is not stale data.")
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun cacheIn(): Pair<okio.Path, SchoolCardTradeListCache> {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-card-flows-${instances++}"
        return directory to SchoolCardTradeListCache(FileSystem.SYSTEM, directory, JSON)
    }

    private fun write(directory: okio.Path, text: String) {
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(directory / "SchoolCardTradeList.json") { writeUtf8(text) }
    }

    /** The window the file says its rows belong to, so a test can assert it was stored at all. */
    private fun storedRange(directory: okio.Path): SchoolCardRange? {
        val text = FileSystem.SYSTEM.read(directory / "SchoolCardTradeList.json") { readUtf8() }
        return JSON.decodeFromString<SchoolCardTradeListSnapshot>(text).range
    }

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
