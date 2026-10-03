package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
 * Tests for the experiment list's cache and its state holder.
 *
 * The cache's rule is the one every cache in this app follows: an unreadable file is a *miss*, not an
 * error. That matters more here than anywhere else — the lab site is only reachable from the campus
 * network, so off campus the cached list is the only one there will ever be, and degrading it to a
 * failure would empty a page that had a perfectly good list a moment ago.
 *
 * The state holder's rule is the original controller's: a refresh that fails keeps the list on screen and
 * says why it is stale, and only a failure with nothing to fall back on is an error. The one thing that
 * *does* throw the cache away is a change of credentials, because those are what the list is a list of.
 */
class ExperimentCacheAndRepositoryTest {

    // --- The cache --------------------------------------------------------------------------------

    @Test
    fun hasNothingToSayBeforeAnythingIsStored() {
        assertNull(cache().read())
    }

    @Test
    fun roundTripsABookingWithItsSittingsAndItsMark() {
        val cache = cache()
        cache.save(sampleEntries())

        val result = cache.read()!!

        assertTrue(result.isCache)
        val entry = result.data.single()
        assertEquals("示波器的使用", entry.name)
        assertEquals("实验楼A201", entry.classroom)
        assertEquals("张三", entry.teacher)
        assertEquals("实验讲义第三章", entry.reference)
        assertEquals(ExperimentType.Physics, entry.type)
        assertEquals(LocalDateTime(2026, 3, 5, 15, 55), entry.timeRanges.single().start)
        assertEquals(LocalDateTime(2026, 3, 5, 18, 10), entry.timeRanges.single().stop)
        assertEquals("9.5", entry.score?.label)
        assertTrue(entry.score!!.found)
    }

    @Test
    fun writesTheOriginalFile() {
        // The original's own name, so a captured PhysicsExperiment.json is recognisable.
        val directory = directory()
        ExperimentCache(FileSystem.SYSTEM, directory, JSON).save(sampleEntries())

        assertTrue(FileSystem.SYSTEM.exists(directory / "PhysicsExperiment.json"))
    }

    @Test
    fun anUnreadableFileIsAMissRatherThanAFailure() {
        val directory = directory()
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(directory / "PhysicsExperiment.json") { writeUtf8("{ this is not json") }

        assertNull(ExperimentCache(FileSystem.SYSTEM, directory, JSON).read())
    }

    @Test
    fun deletingTheCacheForgetsTheList() {
        val directory = directory()
        val cache = ExperimentCache(FileSystem.SYSTEM, directory, JSON)
        cache.save(sampleEntries())

        cache.delete()

        assertNull(cache.read())
    }

    // --- The state holder -------------------------------------------------------------------------

    @Test
    fun showsTheCachedListBeforeAnythingIsFetched() {
        val repository = ExperimentRepository(FakeSource(cached = cachedAt(1_700_000_000_000)))

        val state = repository.state.value

        assertEquals("示波器的使用", state.entries.single().name)
        assertTrue(state.isFromCache)
        assertFalse(state.isLoading, "A cached list means there is nothing to wait for.")
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000), state.fetchTime)
        assertNull(state.error)
    }

    @Test
    fun aFailedRefreshKeepsTheCachedListAndSaysWhy() = runTest {
        val source = FakeSource(cached = cachedAt(1))
        val repository = ExperimentRepository(source)
        source.next = {
            FetchResult.cache(
                fetchTime = Instant.fromEpochMilliseconds(1),
                data = sampleEntries(),
                hintKey = ExperimentCacheHint.NETWORK_FAILED.key,
            )
        }

        repository.refresh()

        val state = repository.state.value
        assertEquals(1, state.entries.size, "The list stays on screen.")
        assertEquals(ExperimentCacheHint.NETWORK_FAILED, state.cacheHint)
        assertNull(state.error, "A stale list with a reason is not an error state.")
        assertTrue(state.isFromCache)
    }

    @Test
    fun aFailureWithNothingCachedIsAnError() = runTest {
        val source = FakeSource()
        val repository = ExperimentRepository(source)
        source.next = { throw ExperimentNetworkException("物理实验网络请求失败") }

        repository.refresh()

        assertTrue(repository.state.value.error is ExperimentNetworkException)
        assertTrue(repository.state.value.hasError)
        assertFalse(repository.state.value.hasData)
    }

    @Test
    fun aMissingPasswordIsRecognisedAsSomethingThePageCanFix() = runTest {
        val source = FakeSource()
        val repository = ExperimentRepository(source)
        source.next = { throw ExperimentNoPasswordException() }

        repository.refresh()

        assertTrue(repository.state.value.needsPassword, "The page offers the settings that fix this.")
    }

    @Test
    fun aFreshListReplacesTheCachedOne() = runTest {
        val source = FakeSource(cached = cachedAt(1))
        val repository = ExperimentRepository(source)
        source.next = { FetchResult.fresh(Instant.fromEpochMilliseconds(2), sampleEntries()) }

        repository.refresh()

        val state = repository.state.value
        assertFalse(state.isFromCache)
        assertNull(state.cacheHint)
        assertEquals(Instant.fromEpochMilliseconds(2), state.fetchTime)
    }

    @Test
    fun changingTheCredentialsThrowsTheOldAccountsListAway() = runTest {
        // The list is an account's bookings: keeping it as a fallback while a *different* account is
        // being read would draw someone else's experiments under the new account's name.
        val directory = directory()
        val cache = ExperimentCache(FileSystem.SYSTEM, directory, JSON)
        cache.save(sampleEntries())

        val source = FakeSource(
            cached = cache.read(),
            onInvalidate = { cache.delete() },
        )
        val repository = ExperimentRepository(source)
        source.next = { FetchResult.fresh(Instant.fromEpochMilliseconds(2), emptyList()) }

        repository.onCredentialsChanged()

        assertNull(cache.read(), "The file itself is gone, not merely ignored.")
        assertTrue(repository.state.value.entries.isEmpty())
    }

    @Test
    fun aScoreImageThatWillNotLoadIsNotAFailure() = runTest {
        val source = FakeSource()
        val repository = ExperimentRepository(source)
        source.nextImage = { throw ExperimentNetworkException("下载成绩图片 请求失败") }

        assertNull(repository.scoreImage("http://wlsy.xidian.edu.cn/score/1.png"))
    }

    @Test
    fun everyFailureIsClassifiedForTheCacheNotice() {
        assertEquals(
            ExperimentCacheHint.MISSING_PASSWORD,
            experimentCacheHintFor(ExperimentNoPasswordException()),
        )
        assertEquals(
            ExperimentCacheHint.LOGIN_FAILED,
            experimentCacheHintFor(ExperimentLoginFailedException("用户名或密码错误。")),
        )
        assertEquals(
            ExperimentCacheHint.NETWORK_FAILED,
            experimentCacheHintFor(ExperimentNetworkException("请求失败")),
        )
        assertEquals(
            ExperimentCacheHint.UNKNOWN_ERROR,
            experimentCacheHintFor(ExperimentProtocolException("报告系统未返回会话标识")),
        )
    }

    // --- Fixtures ---------------------------------------------------------------------------------

    private fun directory(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-experiment-cache-${instances++}"

    private fun cache(): ExperimentCache = ExperimentCache(FileSystem.SYSTEM, directory(), JSON)

    private fun cachedAt(millis: Long): FetchResult<List<ExperimentEntry>> =
        FetchResult.cache(Instant.fromEpochMilliseconds(millis), sampleEntries())

    private fun sampleEntries(): List<ExperimentEntry> = listOf(
        ExperimentEntry(
            name = "示波器的使用",
            classroom = "实验楼A201",
            timeRanges = listOf(
                ExperimentTimeRange(
                    start = LocalDateTime(LocalDate(2026, 3, 5), LocalTime(15, 55)),
                    stop = LocalDateTime(LocalDate(2026, 3, 5), LocalTime(18, 10)),
                ),
            ),
            teacher = "张三",
            reference = "实验讲义第三章",
            score = ExperimentScore(label = "9.5", found = true, rawUrl = "http://example/1.png"),
        ),
    )

    /** A source whose next answer the test decides. */
    private class FakeSource(
        private val cached: FetchResult<List<ExperimentEntry>>? = null,
        private val onInvalidate: () -> Unit = {},
    ) : ExperimentDataSource {

        var next: () -> FetchResult<List<ExperimentEntry>> = { error("No answer was arranged") }
        var nextImage: () -> ByteArray = { error("No image was arranged") }

        override fun getCachedExperiments(): FetchResult<List<ExperimentEntry>>? = cached

        override suspend fun getExperiments(): FetchResult<List<ExperimentEntry>> = next()

        override suspend fun getScoreImage(url: String): ByteArray = nextImage()

        override fun invalidateCache() = onInvalidate()
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
