package com.nevoit.xdnext.data.energy

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the two strings the card puts on screen.
 *
 * Both exist because the original's Dart and this port's Kotlin disagree about how a number and a date
 * look, and both were wrong before they were written down: `25.0` where the original printed `25`, and a
 * `.first()` on a meter list the server is allowed to return empty.
 */
class EnergyModelsTest {

    @Test
    fun printsAWholeBalanceWithoutADecimalPart() {
        // Dart decoded a JSON integer as an `int`, so `toString()` gave "25"; Kotlin's Double gives
        // "25.0" unless it is trimmed.
        assertEquals("25", 25.0.asPlainNumber())
        assertEquals("0", 0.0.asPlainNumber())
        assertEquals("-3", (-3.0).asPlainNumber())
    }

    @Test
    fun keepsAFractionalBalance() {
        assertEquals("25.5", 25.5.asPlainNumber())
        assertEquals("-0.25", (-0.25).asPlainNumber())
        assertEquals("18.63", 18.63.asPlainNumber())
    }

    @Test
    fun leavesValuesItCannotPrintExactlyAlone() {
        // Above 2^53 a Double cannot hold every integer, so trimming the decimal part would invent
        // digits the server never sent. Nothing here defers to `toLong`.
        assertEquals(1.0E16.toString(), 1.0E16.asPlainNumber())
        assertEquals("NaN", Double.NaN.asPlainNumber())
        assertEquals("Infinity", Double.POSITIVE_INFINITY.asPlainNumber())
    }

    @Test
    fun theCardShowsTheFirstReadingDate() {
        val info = reading(
            lastReadDate = LocalDate(2026, 4, 22),
            readings = listOf(LocalDate(2026, 4, 20), LocalDate(2026, 4, 21)),
        )

        assertEquals("4月20日", info.lastReadText, "The original formatted `.first`, not the last.")
        assertEquals("25", info.electricityRemainText)
    }

    @Test
    fun anEmptyReadingWindowFallsBackToTheMetersOwnDate() {
        // The original indexed `.first` and would have thrown; the server is allowed to answer with an
        // empty window, and a card is not the place to find that out.
        val info = reading(lastReadDate = LocalDate(2026, 4, 22), readings = emptyList())

        assertEquals("4月22日", info.lastReadText)
    }

    @Test
    fun aSingleDigitMonthIsNotZeroPadded() {
        // `monthNumber()` pads by default, so this is the case that catches it: 04月5日 is not how a date
        // is written in Chinese.
        val info =
            reading(lastReadDate = LocalDate(2025, 1, 1), readings = listOf(LocalDate(2026, 1, 5)))

        assertEquals("1月5日", info.lastReadText)
    }

    private fun reading(lastReadDate: LocalDate, readings: List<LocalDate>) = EnergyInfo(
        lastReadDate = lastReadDate,
        electricityRemain = 25.0,
        electricityMeterList = readings.map {
            MeterInfo(
                it,
                readNum = 1.0,
                startNum = 0.0,
                endNum = 1.0
            )
        },
    )
}
