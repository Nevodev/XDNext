package com.nevoit.xdnext.data.experiment

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the three sections the page draws.
 *
 * The page's headings are claims about the clock — 正在进行实验, 未完成实验, 已完成实验 — so what is worth
 * pinning is the boundary: a sitting is in progress from its first minute and over from its last, and the
 * two must not both be true of it. The original compared against **now plus a day** here, which put a
 * sitting that ends this evening under 已完成 from midnight; that is not reproduced, and the boundary
 * tests are what say so.
 */
class ExperimentGroupingTest {

    private val now = LocalDateTime(2026, 3, 5, 16, 0)

    @Test
    fun sortsEachSittingIntoItsOwnSection() {
        val groups = groupExperiments(
            listOf(
                entry("正在进行的", LocalDate(2026, 3, 5), 15, 55, 18, 10),
                entry("还没开始的", LocalDate(2026, 3, 6), 18, 30, 20, 45),
                entry("已经结束的", LocalDate(2026, 3, 4), 15, 55, 18, 10),
            ),
            now,
        )

        assertEquals(listOf("正在进行的"), groups.ongoing.map { it.name })
        assertEquals(listOf("还没开始的"), groups.upcoming.map { it.name })
        assertEquals(listOf("已经结束的"), groups.finished.map { it.name })
    }

    @Test
    fun aSittingIsInProgressFromItsFirstMinuteAndOverFromItsLast() {
        val groups = groupExperiments(
            listOf(
                entry("刚开始", LocalDate(2026, 3, 5), 16, 0, 17, 0),
                entry("刚好结束", LocalDate(2026, 3, 5), 15, 0, 16, 0),
            ),
            now,
        )

        assertEquals(listOf("刚开始"), groups.ongoing.map { it.name })
        assertEquals(listOf("刚好结束"), groups.finished.map { it.name })
        assertTrue(groups.upcoming.isEmpty())
    }

    @Test
    fun aBookingWithSittingsOnBothSidesOfNowIsInBothSections() {
        // A week-long experiment: Monday's sitting is over and Thursday's has not started. Each section
        // shows the booking with **only** the sitting that put it there, which is what the original did
        // when it filtered the ranges before drawing the card.
        val spread = ExperimentEntry(
            name = "一周的实验",
            classroom = "实验楼A201",
            timeRanges = listOf(
                range(LocalDate(2026, 3, 2), 15, 55, 18, 10),
                range(LocalDate(2026, 3, 6), 15, 55, 18, 10),
            ),
            teacher = "张三",
        )

        val groups = groupExperiments(listOf(spread), now)

        assertEquals(listOf(LocalDate(2026, 3, 2)), groups.finished.single().timeRanges.map { it.start.date })
        assertEquals(listOf(LocalDate(2026, 3, 6)), groups.upcoming.single().timeRanges.map { it.start.date })
        assertTrue(groups.ongoing.isEmpty())
    }

    @Test
    fun eachSectionIsOrderedByItsEarliestSitting() {
        val groups = groupExperiments(
            listOf(
                entry("后一场", LocalDate(2026, 3, 6), 18, 30, 20, 45),
                entry("先一场", LocalDate(2026, 3, 6), 15, 55, 18, 10),
            ),
            now,
        )

        assertEquals(listOf("先一场", "后一场"), groups.upcoming.map { it.name })
    }

    @Test
    fun anEmptyListIsEmptyInEverySection() {
        val groups = groupExperiments(emptyList(), now)

        assertTrue(groups.isEmpty)
    }

    // --- Fixtures ---------------------------------------------------------------------------------

    private fun entry(
        name: String,
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        stopHour: Int,
        stopMinute: Int,
    ) = ExperimentEntry(
        name = name,
        classroom = "实验楼A201",
        timeRanges = listOf(range(date, startHour, startMinute, stopHour, stopMinute)),
        teacher = "张三",
    )

    private fun range(
        date: LocalDate,
        startHour: Int,
        startMinute: Int,
        stopHour: Int,
        stopMinute: Int,
    ) = ExperimentTimeRange(
        start = LocalDateTime(date, LocalTime(startHour, startMinute)),
        stop = LocalDateTime(date, LocalTime(stopHour, stopMinute)),
    )
}
