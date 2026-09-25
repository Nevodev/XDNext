package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for the timetable's cache and its state holder.
 *
 * The cache's rule is the one every cache in this app follows: an unreadable file is a *miss*, not an
 * error, because losing the cache degrades to "the next launch asks again" while a read failure that
 * propagates empties a screen that had a perfectly good timetable in it a moment ago.
 *
 * The state holder's rule is the original controller's: a refresh that fails keeps the timetable on
 * screen and says why it is stale, and only a failure with nothing to fall back on is an error.
 */
class TimetableCacheAndRepositoryTest {

    @Test
    fun hasNothingToSayBeforeAnythingIsStored() {
        assertNull(cache().read())
    }

    @Test
    fun roundTripsATimetable() {
        val cache = cache()
        cache.save(sampleData())

        val result = cache.read()!!

        assertTrue(result.isCache)
        assertEquals("2025-2026-1", result.data.semesterCode)
        assertEquals("2025-09-01", result.data.termStartDay)
        assertEquals(listOf("高等数学"), result.data.classDetail.map { it.name })
        assertEquals(listOf(true, false, false), result.data.timeArrangement.single().weekList)
        assertEquals("B203", result.data.timeArrangement.single().classroom)
    }

    @Test
    fun writesTheOriginalFile() {
        // The name and the bare-object contents are the original's own, so a captured ClassTable.json can
        // be diffed against this one.
        val directory = directory()
        val cache = TimetableCache(FileSystem.SYSTEM, directory, JSON)
        cache.save(sampleData())

        assertTrue(FileSystem.SYSTEM.exists(directory / "ClassTable.json"))
    }

    @Test
    fun anUnreadableFileIsAMissRatherThanAFailure() {
        val directory = directory()
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(directory / "ClassTable.json") { writeUtf8("{ this is not json") }

        assertNull(TimetableCache(FileSystem.SYSTEM, directory, JSON).read())
    }

    @Test
    fun showsTheCachedTimetableBeforeAnythingIsFetched() {
        val source = FakeSource(cached = cachedAt(1_700_000_000_000))

        val repository = TimetableRepository(source)

        val state = repository.state.value
        assertEquals("2025-2026-1", state.semesterCode)
        assertTrue(state.isFromCache)
        assertFalse(state.isLoading, "A cached timetable means there is nothing to wait for.")
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000), state.fetchTime)
        assertNull(state.error)
        assertTrue(state.hasClasses)
    }

    @Test
    fun aFailedRefreshKeepsTheCachedTimetableAndSaysWhy() = runTest {
        val source = FakeSource(cached = cachedAt(1))
        val repository = TimetableRepository(source)
        source.next = { FetchResult.cache(Instant.fromEpochMilliseconds(1), sampleData(), TimetableCacheHint.NETWORK_FAILED.key) }

        repository.refresh()

        val state = repository.state.value
        assertTrue(state.isFromCache)
        assertEquals(TimetableCacheHint.NETWORK_FAILED, state.cacheHint)
        assertNull(state.error, "A cached timetable is not an error state.")
        assertEquals(emptyList(), state.errorWithoutCacheSources)
        assertEquals(listOf(TimetableSource.ClassTable), state.errorWithCacheSources)
    }

    @Test
    fun aFailedRefreshWithNothingToShowIsAnError() = runTest {
        val source = FakeSource()
        val repository = TimetableRepository(source)
        source.next = { throw TimetableNetworkException("off campus") }

        repository.refresh()

        val state = repository.state.value
        assertNull(state.data)
        assertTrue(state.error is TimetableNetworkException)
        assertEquals(listOf(TimetableSource.ClassTable), state.errorWithoutCacheSources)
        assertEquals(emptyList(), state.errorWithCacheSources)
        assertFalse(state.isLoading)
    }

    @Test
    fun aFreshFetchReplacesTheCachedOne() = runTest {
        val source = FakeSource(cached = cachedAt(1))
        val repository = TimetableRepository(source)
        source.next = { FetchResult.fresh(Instant.fromEpochMilliseconds(2), sampleData()) }

        repository.refresh()

        val state = repository.state.value
        assertFalse(state.isFromCache)
        assertNull(state.cacheHint)
        assertEquals(Instant.fromEpochMilliseconds(2), state.fetchTime)
    }

    @Test
    fun theWeekIsReadFromTheTermStartDay() {
        val repository = TimetableRepository(FakeSource(cached = cachedAt(1)))

        assertEquals(
            kotlinx.datetime.LocalDate(2025, 9, 1),
            repository.state.value.termStartDay,
        )
        assertNull(TimetableRepository(FakeSource()).state.value.termStartDay)
    }

    @Test
    fun aChangedTimetableReachesWhoeverIsWatching() = runTest {
        // What the page depends on: it draws whatever the state holds, so a refresh that brought a
        // different timetable has to publish a different state — otherwise the page would keep the old
        // grid until it was left and reopened. `TimetableScreen` collects this flow; this is the same
        // subscription, driven without a composition.
        val source = FakeSource(cached = cachedAt(1))
        val repository = TimetableRepository(source)
        val seen = mutableListOf<Int>()
        val watcher = backgroundScope.launch {
            repository.state.collect { seen += it.data?.semesterLength ?: -1 }
        }
        runCurrent()
        assertEquals(listOf(3), seen, "The cached timetable is what the page opens on.")

        source.next = {
            FetchResult.fresh(
                fetchTime = Instant.fromEpochMilliseconds(2),
                data = sampleData().copy(semesterLength = 20),
            )
        }
        repository.refresh()
        runCurrent()
        watcher.cancel()

        assertEquals(listOf(3, 20), seen, "The refreshed semester length must reach the page.")
    }

    @Test
    fun aCancelledRefreshLeavesTheStateAsItWas() = runTest {
        // The repositories are singletons and outlive the screen that started a refresh — a tab switch
        // unmounts the page that ran the start-up load. A state left saying "loading" would strand every
        // later reader on a spinner for a request that is no longer running.
        val source = FakeSource(cached = cachedAt(1))
        val repository = TimetableRepository(source)
        source.next = { throw CancellationException("the page that asked went away") }

        assertFailsWith<CancellationException> { repository.refresh() }

        val state = repository.state.value
        assertFalse(state.isLoading, "Nothing is running any more.")
        assertEquals("2025-2026-1", state.semesterCode, "The cached timetable is still on screen.")
        assertTrue(state.isFromCache)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun directory(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-timetable-cache-${instances++}"

    private fun cache(): TimetableCache = TimetableCache(FileSystem.SYSTEM, directory(), JSON)

    private fun cachedAt(millis: Long): FetchResult<ClassTableData> =
        FetchResult.cache(Instant.fromEpochMilliseconds(millis), sampleData())

    private fun sampleData(): ClassTableData = ClassTableData(
        semesterLength = 3,
        semesterCode = "2025-2026-1",
        termStartDay = "2025-09-01",
        classDetail = listOf(ClassDetail(name = "高等数学", code = "MA1001", number = "01")),
        notArranged = listOf(NotArrangementClassDetail(name = "大学物理", code = "PH1001")),
        timeArrangement = listOf(
            TimeArrangement(
                index = 0,
                weekList = listOf(true, false, false),
                day = 1,
                start = 1,
                stop = 2,
                classroom = "B203",
                teacher = "1234/张三",
            ),
        ),
        classChanges = listOf(
            ClassChange(
                type = ChangeType.Stop,
                classCode = "MA1001",
                classNumber = "01",
                className = "高等数学",
                originalAffectedWeeks = listOf(true, false, false),
                originalClassRange = listOf(1, 2),
                originalWeek = 1,
            ),
        ),
    )

    /** A source whose next answer the test decides. */
    private class FakeSource(
        private val cached: FetchResult<ClassTableData>? = null,
    ) : TimetableDataSource {

        var next: () -> FetchResult<ClassTableData> = { error("No answer was arranged") }

        override fun getCachedClassTable(): FetchResult<ClassTableData>? = cached

        override suspend fun getClassTable(): FetchResult<ClassTableData> = next()
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
