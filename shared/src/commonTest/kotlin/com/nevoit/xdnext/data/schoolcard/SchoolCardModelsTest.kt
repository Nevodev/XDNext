package com.nevoit.xdnext.data.schoolcard

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for the campus card's arithmetic and its reading of the wire format.
 *
 * The money is the part worth pinning. The original summed `double.parse`d strings and printed the
 * result of `toStringAsFixed(2)`, which is a rounding error away from the wrong balance in a way no
 * screenshot reveals, and its "today" filter compared the server's date string against a
 * `DateFormat("yyyy-MM-dd")` rendering — so a `txdate` carrying a time would have counted as no day at
 * all and silently produced a spending figure of zero.
 */
class SchoolCardModelsTest {

    private val today = LocalDate(2026, 3, 5)

    // --- the day's spending ---------------------------------------------------------------------

    @Test
    fun countsOnlyTodaysPurchases() {
        val records = listOf(
            record("2026-03-05", "-12.30"),
            record("2026-03-04", "-99.00"),
            record("2026-03-05", "-0.70"),
        )

        assertEquals(1300L, todayExpenseCents(records, today))
    }

    @Test
    fun acceptsATimestampAsWellAsABareDate() {
        // The server's own `txdate` format is not pinned by the protocol; the original rendered the
        // query's dates itself and compared the answer as a string, so a time component would have
        // dropped the record. The date part is what decides here.
        val records = listOf(record("2026-03-05 12:30:01", "-5.00"))

        assertEquals(500L, todayExpenseCents(records, today))
    }

    @Test
    fun keepsTopUpsOutOfTheSpendingTotal() {
        // A day that spent 12.30 and topped up 200 is a 12.30 day, not a -187.70 one. The original
        // netted the two and then chose its wording from the sign, which turns a purchase into income
        // as soon as a top-up is larger.
        val records = listOf(
            record("2026-03-05", "-12.30"),
            record("2026-03-05", "200.00"),
        )

        assertEquals(
            1230L,
            todayExpenseCents(records, today),
            "Just the 12.30 spent; the 200 stays out."
        )
        assertEquals(20000L, todayIncomeCents(records, today))
    }

    @Test
    fun sumsTheCentsTheServerSentRatherThanADouble() {
        // 0.1 + 0.2 in binary floating point is 0.30000000000000004, and a day of small purchases
        // accumulates the error into a visibly wrong total.
        val records = listOf(
            record("2026-03-05", "-0.10"),
            record("2026-03-05", "-0.20"),
        )

        assertEquals(30L, todayExpenseCents(records, today))
        assertEquals("0.30", centsToYuanText(todayExpenseCents(records, today)))
    }

    @Test
    fun anEmptyDayIsZeroAndNotUnknown() {
        // Zero, not null: a day with no rows in it is a day with nothing spent. Only a *failed* query is
        // unknown, and that is the whole reason the tile's value is nullable while this is not.
        assertEquals(0L, todayExpenseCents(emptyList(), today))
    }

    // --- amounts --------------------------------------------------------------------------------

    @Test
    fun writesAmountsWithTwoDecimals() {
        assertEquals("0.00", centsToYuanText(0))
        assertEquals("12.30", centsToYuanText(1230))
        assertEquals("0.05", centsToYuanText(5))
        assertEquals("125.00", centsToYuanText(12500))
        assertEquals("-12.30", centsToYuanText(-1230), "A refund keeps its sign.")
    }

    @Test
    fun parsesAmountsWhicheverWayTheyAreWritten() {
        assertEquals(1230L, parseYuanToCents("12.30"))
        assertEquals(
            -1230L,
            parseYuanToCents("-12.3"),
            "The sign is the difference between spending and income."
        )
        assertEquals(1200L, parseYuanToCents("12"))
        assertEquals(-50L, parseYuanToCents("-0.5"))
        assertEquals(5L, parseYuanToCents("0.05"))
        assertEquals(0L, parseYuanToCents("0"))
    }

    @Test
    fun roundsASubCentAmountAwayFromZero() {
        // Rounding half up would make -0.005 into -0.00, which reads as a refund of nothing.
        assertEquals(1L, parseYuanToCents("0.005"))
        assertEquals(-1L, parseYuanToCents("-0.005"))
        assertEquals(123L, parseYuanToCents("1.234"))
        assertEquals(124L, parseYuanToCents("1.235"))
    }

    @Test
    fun refusesAnAmountItCannotReadInsteadOfCallingItZero() {
        assertNull(parseYuanToCents(""))
        assertNull(parseYuanToCents("  "))
        assertNull(parseYuanToCents("abc"))
        assertNull(parseYuanToCents("12.3.4"))
        assertNull(parseYuanToCents("1,230"))
    }

    // --- wire rows ------------------------------------------------------------------------------

    @Test
    fun readsARowWithItsWiresFieldNames() {
        val record =
            parseCardRecord(json("""{"mername":"食堂","txdate":"2026-03-05","txamt":"-12.30"}"""))

        assertEquals("食堂", record?.merchant)
        assertEquals("2026-03-05", record?.date)
        assertEquals("-12.30", record?.amount)
    }

    @Test
    fun readsAnAmountTheServerLeftUnquoted() {
        // The row keeps the amount as the wire wrote it — kotlinx normalises a bare `-12.3` to the
        // string "-12.3" — and the sum parses it. Normalising it to "-12.30" *here* would make the sum
        // parse it twice and read a purchase of 12.30 as an income of 12.30.
        val record =
            parseCardRecord(json("""{"mername":"食堂","txdate":"2026-03-05","txamt":-12.3}"""))

        assertEquals("-12.3", record?.amount)
        assertEquals(1230L, todayExpenseCents(listOf(record!!), today))
    }

    @Test
    fun dropsARowWithNoUsableAmount() {
        // A fabricated zero would understate the day silently, which is worse than a row that is
        // simply absent.
        assertNull(parseCardRecord(json("""{"mername":"食堂","txdate":"2026-03-05"}""")))
        assertNull(parseCardRecord(json("""{"mername":"食堂","txdate":"2026-03-05","txamt":null}""")))
        assertNull(parseCardRecord(json("""{"mername":"食堂","txdate":"2026-03-05","txamt":"--"}""")))
    }

    @Test
    fun toleratesAMissingMerchantOrDate() {
        val record = parseCardRecord(json("""{"txamt":"-1.00"}"""))

        assertEquals("", record?.merchant)
        assertEquals("", record?.date)
    }

    // --- the query's own dates ------------------------------------------------------------------

    @Test
    fun rendersQueryDatesWithPaddedFields() {
        // kotlinx-datetime pads numeric format fields by default, and the server's own query wants
        // `2026-03-05` rather than `2026-3-5`.
        assertEquals("2026-03-05", LocalDate(2026, 3, 5).toCardQueryDate())
        assertEquals("2026-12-31", LocalDate(2026, 12, 31).toCardQueryDate())
        assertEquals("2026-01-01", LocalDate(2026, 1, 1).toCardQueryDate())
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun record(date: String, amount: String) =
        SchoolCardRecord(merchant = "食堂", date = date, amount = amount)

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject
}
