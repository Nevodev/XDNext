package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
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
 * Tests for the electricity query's state holder.
 *
 * The history rules are the part worth testing: the original keyed its series by the *meter's* reading
 * day rather than by when the app ran, trimmed at a fixed length, and treated a failure with a cached
 * reading as a success with a notice rather than as an error. Each of those is a decision that is
 * invisible in a screenshot and easy to get subtly wrong.
 */
class EnergyRepositoryTest {

    // --- cache and history ----------------------------------------------------------------------

    @Test
    fun showsTheCachedReadingBeforeAnythingIsFetched() {
        val cached = reading(LocalDate(2026, 4, 22), remain = 88.0)
        val source = FakeSource(cached = cacheOf(cached, at = 1_700_000_000_000))

        val repository = EnergyRepository(source, settings())

        val state = repository.state.value
        assertEquals(cached, state.info)
        assertTrue(state.isFromCache)
        assertFalse(state.isLoading, "A cached reading means there is nothing to wait for.")
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000), state.fetchTime)
        assertNull(state.error)
    }

    @Test
    fun appendsOneHistoryPointPerReadingDay() = runTest {
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())

        source.next = {
            FetchResult.fresh(
                Instant.fromEpochMilliseconds(1),
                reading(LocalDate(2026, 4, 22), 88.0)
            )
        }
        repository.refreshElectricityInfo()
        repository.refreshElectricityInfo()

        assertEquals(
            1,
            repository.state.value.history.size,
            "The same reading day is one point, not two."
        )
        // Written the way the card shows it: a whole balance carries no ".0".
        assertEquals("88", repository.state.value.history.single().remain)

        source.next = {
            FetchResult.fresh(
                Instant.fromEpochMilliseconds(2),
                reading(LocalDate(2026, 4, 23), 60.5)
            )
        }
        repository.refreshElectricityInfo()

        assertEquals(
            listOf(LocalDate(2026, 4, 22), LocalDate(2026, 4, 23)),
            repository.state.value.history.map { it.fetchDay },
        )
        assertEquals(repository.state.value.history, source.savedHistory)
    }

    @Test
    fun keepsTheLastFifteenPoints() = runTest {
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())

        repeat(20) { day ->
            source.next = {
                FetchResult.fresh(
                    Instant.fromEpochMilliseconds(day.toLong()),
                    reading(LocalDate(2026, 1, 1 + day), 100.0 - day),
                )
            }
            repository.refreshElectricityInfo()
        }

        val history = repository.state.value.history
        assertEquals(15, history.size, "The original trimmed at fourteen before appending.")
        assertEquals(
            LocalDate(2026, 1, 6),
            history.first().fetchDay,
            "The oldest points are dropped."
        )
        assertEquals(LocalDate(2026, 1, 20), history.last().fetchDay)
    }

    @Test
    fun doesNotRecordHistoryFromACachedResult() = runTest {
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())

        source.next = { cacheOf(reading(LocalDate(2026, 4, 22), 88.0), at = 1) }
        repository.refreshElectricityInfo()

        assertTrue(
            repository.state.value.history.isEmpty(),
            "A cache hit is not a new observation."
        )
        assertTrue(repository.state.value.isFromCache)
    }

    // --- failure --------------------------------------------------------------------------------

    @Test
    fun showsTheCacheHintWhenTheSessionFellBackToTheCache() = runTest {
        // The session (not the repository) decides to fall back; what the repository must do is keep
        // the value on screen and carry the reason, rather than showing an error over usable data.
        val cached = reading(LocalDate(2026, 4, 22), 12.0)
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())

        source.next = {
            FetchResult.cache(
                fetchTime = Instant.fromEpochMilliseconds(1_700_000_000_000),
                data = cached,
                hintKey = EnergyCacheHint.NETWORK_FAILED.key,
            )
        }
        repository.refreshElectricityInfo()

        val state = repository.state.value
        assertEquals(cached, state.info)
        assertEquals(EnergyCacheHint.NETWORK_FAILED, state.cacheHint)
        assertTrue(state.isFromCache)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun reportsAFailureWhenThereIsNothingToFallBackTo() = runTest {
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())

        source.next = { throw EnergyNetworkException("no campus network") }
        repository.refreshElectricityInfo()

        val state = repository.state.value
        assertNull(state.info)
        assertTrue(state.error is EnergyNetworkException)
        assertFalse(state.isLoading)
    }

    @Test
    fun aFailedRefreshKeepsTheReadingThatWasAlreadyOnScreen() = runTest {
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())

        source.next = {
            FetchResult.fresh(
                Instant.fromEpochMilliseconds(1),
                reading(LocalDate(2026, 4, 22), 88.0)
            )
        }
        repository.refreshElectricityInfo()

        source.next = { throw EnergyNetworkException("gone") }
        repository.refreshElectricityInfo()

        assertEquals(88.0, repository.state.value.info?.electricityRemain)
        assertFalse(repository.state.value.isLoading)
    }

    // --- the low-balance reminder ---------------------------------------------------------------

    @Test
    fun defaultsTheReminderToOnAtTwentyKilowattHours() {
        val warning = EnergyRepository(FakeSource(), settings()).lowElectricityWarning.value

        assertTrue(warning.enabled, "An absent key means on: the original treated it that way.")
        assertEquals(20, warning.threshold)
        assertTrue(warning.isTriggeredBy(19.5))
        assertFalse(warning.isTriggeredBy(20.0), "The comparison is strictly below the threshold.")
    }

    @Test
    fun disablesTheReminder() {
        val store = settings()
        val repository = EnergyRepository(FakeSource(), store)

        repository.setLowElectricityWarningEnabled(false)
        assertFalse(repository.lowElectricityWarning.value.enabled)
        assertFalse(repository.lowElectricityWarning.value.isTriggeredBy(1.0))

        repository.setLowElectricityWarningEnabled(true)
        assertTrue(repository.lowElectricityWarning.value.enabled)
    }

    @Test
    fun rejectsANonPositiveThreshold() {
        val store = settings()
        val repository = EnergyRepository(FakeSource(), store)

        repository.setLowElectricityWarningThreshold(0)
        assertEquals(20, repository.lowElectricityWarning.value.threshold)
        assertEquals(20, store.getInt(SettingsKeys.LOW_ELECTRICITY_WARNING_THRESHOLD))

        repository.setLowElectricityWarningThreshold(5)
        assertEquals(5, repository.lowElectricityWarning.value.threshold)
        assertEquals(5, store.getInt(SettingsKeys.LOW_ELECTRICITY_WARNING_THRESHOLD))
    }

    @Test
    fun treatsAStoredNonPositiveThresholdAsTheDefault() {
        // A hand-edited or migrated preference file must not leave the warning permanently silent.
        val store = settings()
        store.putInt(SettingsKeys.LOW_ELECTRICITY_WARNING_THRESHOLD, -3)

        assertEquals(
            20,
            EnergyRepository(FakeSource(), store).lowElectricityWarning.value.threshold
        )
    }

    // --- clearing -------------------------------------------------------------------------------

    @Test
    fun clearsTheHistoryInBothPlaces() = runTest {
        val source = FakeSource()
        val repository = EnergyRepository(source, settings())
        source.next = {
            FetchResult.fresh(
                Instant.fromEpochMilliseconds(1),
                reading(LocalDate(2026, 4, 22), 88.0)
            )
        }
        repository.refreshElectricityInfo()

        repository.clearElectricityHistory()

        assertTrue(repository.state.value.history.isEmpty())
        assertTrue(source.clearedHistory)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun settings() = SettingsStore(InMemorySettings())

    private fun reading(day: LocalDate, remain: Double) = EnergyInfo(
        lastReadDate = day,
        electricityRemain = remain,
        electricityMeterList = listOf(MeterInfo(day, 1.0, 0.0, 1.0)),
    )

    private fun cacheOf(info: EnergyInfo, at: Long) =
        FetchResult.cache(fetchTime = Instant.fromEpochMilliseconds(at), data = info)

    private class FakeSource(
        var cached: FetchResult<EnergyInfo>? = null,
        private var history: List<ElectricityHistoryInfo> = emptyList(),
        var savedHistory: List<ElectricityHistoryInfo>? = null,
        var clearedHistory: Boolean = false,
    ) : EnergyDataSource {

        var next: (() -> FetchResult<EnergyInfo>)? = null

        override fun getCache(): FetchResult<EnergyInfo>? = cached

        override suspend fun getElectricityInfo(): FetchResult<EnergyInfo> =
            requireNotNull(next) { "the test did not stage a fetch" }.invoke()

        override fun getElectricityHistory(): List<ElectricityHistoryInfo> = history

        override fun saveElectricityHistory(history: List<ElectricityHistoryInfo>) {
            savedHistory = history
            this.history = history
        }

        override fun clearElectricityHistory() {
            clearedHistory = true
            history = emptyList()
        }

        override fun deleteCache() {
            cached = null
        }
    }
}
