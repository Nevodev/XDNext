package com.nevoit.xdnext.ui.home

import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.energy.ElectricityHistoryInfo
import com.nevoit.xdnext.data.energy.EnergyDataSource
import com.nevoit.xdnext.data.energy.EnergyInfo
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.energy.MeterInfo
import com.nevoit.xdnext.data.fetch.FetchResult
import com.nevoit.xdnext.data.schoolcard.SchoolCardDataSource
import com.nevoit.xdnext.data.schoolcard.SchoolCardRepository
import com.nevoit.xdnext.data.schoolcard.SchoolCardSnapshot
import com.nevoit.xdnext.data.timetable.ClassTableData
import com.nevoit.xdnext.data.timetable.TimetableDataSource
import com.nevoit.xdnext.data.timetable.TimetableRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for the once-per-process load.
 *
 * This exists because of a real defect: the focus page is **unmounted** when the user switches tabs and
 * rebuilt on the way back, so a `LaunchedEffect` inside it ran on every visit and the electricity card
 * re-queried each time the page was opened. The guard therefore cannot live in the composition — and a
 * test that only called `loadOnce` twice in a row would not have caught it, so the second call here is
 * made on a *new* composition, which is what a tab switch produces.
 *
 * The timetable is in this list for the same reason the cards are: its page is built fresh on every
 * open, and it must draw the term this load fetched rather than ask for another one.
 */
class CardRefresherTest {

    @Test
    fun loadsEveryModuleOnceAndNeverAgain() = runTest {
        val energy = FakeEnergySource()
        val schoolCard = FakeSchoolCardSource()
        val timetable = FakeTimetableSource()
        val refresher = CardRefresher(
            EnergyRepository(energy, settings()),
            SchoolCardRepository(schoolCard),
            TimetableRepository(timetable),
        )

        refresher.loadOnce()
        refresher.loadOnce()
        refresher.loadOnce()

        assertEquals(1, energy.fetches, "A second visit to the page must not re-query the meter.")
        assertEquals(1, schoolCard.fetches, "Nor the card.")
        assertEquals(1, timetable.fetches, "Nor the term.")
        assertTrue(refresher.hasLoaded)
    }

    @Test
    fun theGuardOutlivesThePageThatTriggeredIt() = runTest {
        // The guard belongs to the object, not to a composition: this is what makes a tab switch — which
        // destroys and rebuilds the page — a no-op rather than a refresh. The timetable page is rebuilt
        // the same way on every open, and it draws what this load left behind.
        val energy = FakeEnergySource()
        val schoolCard = FakeSchoolCardSource()
        val timetable = FakeTimetableSource()
        val refresher = CardRefresher(
            EnergyRepository(energy, settings()),
            SchoolCardRepository(schoolCard),
            TimetableRepository(timetable),
        )

        refresher.loadOnce()
        val afterFirstVisit = refresher.hasLoaded

        // A rebuilt page holds the same refresher, so it sees the flag the first visit set.
        assertTrue(afterFirstVisit)
        refresher.loadOnce()

        assertEquals(
            3,
            energy.fetches + schoolCard.fetches + timetable.fetches,
            "Three modules, one fetch each — not six."
        )
    }

    @Test
    fun aFreshProcessStartsWithNoCardsLoaded() {
        // Rotating the screen or a process death rebuilds the graph, and the modules are loaded again —
        // the rule is once per process, not once per installation.
        val refresher = CardRefresher(
            EnergyRepository(FakeEnergySource(), settings()),
            SchoolCardRepository(FakeSchoolCardSource()),
            TimetableRepository(FakeTimetableSource()),
        )

        assertFalse(refresher.hasLoaded)
    }

    @Test
    fun withoutATimetableTheTermIsFetchedFirst() = runTest {
        // A fresh install, or the first launch after signing in: there is no grid to draw, and the
        // module that answers it must not queue behind two other logins to get its own.
        val order = mutableListOf<String>()
        val refresher = CardRefresher(
            EnergyRepository(FakeEnergySource(order), settings()),
            SchoolCardRepository(FakeSchoolCardSource(order)),
            TimetableRepository(FakeTimetableSource(order)),
        )

        refresher.loadOnce()

        assertEquals(listOf(TIMETABLE, ENERGY, SCHOOL_CARD), order)
    }

    @Test
    fun withACachedTimetableTheCardsAreFetchedFirst() = runTest {
        // The cache drew the grid on the first frame, so re-reading the term is a background update:
        // the two cards the user is looking at go first and the slowest module goes last, as before.
        val order = mutableListOf<String>()
        val refresher = CardRefresher(
            EnergyRepository(FakeEnergySource(order), settings()),
            SchoolCardRepository(FakeSchoolCardSource(order)),
            TimetableRepository(FakeTimetableSource(order, cached = true)),
        )

        refresher.loadOnce()

        assertEquals(listOf(ENERGY, SCHOOL_CARD, TIMETABLE), order)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun settings() = SettingsStore(InMemorySettings())

    private class FakeEnergySource(
        private val order: MutableList<String> = mutableListOf(),
    ) : EnergyDataSource {
        var fetches = 0

        override fun getCache(): FetchResult<EnergyInfo>? = null

        override suspend fun getElectricityInfo(): FetchResult<EnergyInfo> {
            fetches++
            order += ENERGY
            return FetchResult.fresh(
                fetchTime = Instant.fromEpochMilliseconds(1),
                data = EnergyInfo(
                    lastReadDate = LocalDate(2026, 3, 5),
                    electricityRemain = 88.0,
                    electricityMeterList = listOf(
                        MeterInfo(
                            LocalDate(2026, 3, 5),
                            88.0,
                            0.0,
                            88.0
                        )
                    ),
                ),
            )
        }

        override fun getElectricityHistory(): List<ElectricityHistoryInfo> = emptyList()

        override fun saveElectricityHistory(history: List<ElectricityHistoryInfo>) = Unit

        override fun clearElectricityHistory() = Unit

        override fun deleteCache() = Unit
    }

    private class FakeSchoolCardSource(
        private val order: MutableList<String> = mutableListOf(),
    ) : SchoolCardDataSource {
        var fetches = 0

        override fun getCachedCard(today: LocalDate): FetchResult<SchoolCardSnapshot>? = null

        override suspend fun getSchoolCard(): FetchResult<SchoolCardSnapshot> {
            fetches++
            order += SCHOOL_CARD
            return FetchResult.fresh(
                fetchTime = Instant.fromEpochMilliseconds(1),
                data = SchoolCardSnapshot(
                    balance = "25.60",
                    todayExpenseCents = 0,
                    todayIncomeCents = 0,
                ),
            )
        }
    }

    /**
     * @param cached when true the fixture answers from a cache, which is what a *returning* device has:
     *   `TimetableRepository` reads it in its constructor, so the state already has a timetable before
     *   anything is refreshed.
     */
    private class FakeTimetableSource(
        private val order: MutableList<String> = mutableListOf(),
        private val cached: Boolean = false,
    ) : TimetableDataSource {
        var fetches = 0

        override fun getCachedClassTable(): FetchResult<ClassTableData>? =
            if (cached) {
                FetchResult.cache(fetchTime = Instant.fromEpochMilliseconds(1), data = classTable())
            } else {
                null
            }

        override suspend fun getClassTable(): FetchResult<ClassTableData> {
            fetches++
            order += TIMETABLE
            return FetchResult.fresh(
                fetchTime = Instant.fromEpochMilliseconds(1),
                data = classTable(),
            )
        }
    }
}

private const val ENERGY = "energy"
private const val SCHOOL_CARD = "schoolCard"
private const val TIMETABLE = "timetable"

private fun classTable() = ClassTableData(
    semesterCode = "2025-2026-1",
    termStartDay = "2025-09-01",
)
