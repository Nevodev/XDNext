package com.nevoit.xdnext.data.schoolcard

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the campus card's arithmetic, its reading of the wire format, and the range it answers for.
 *
 * The money is the part worth pinning. The original summed `double.parse`d strings and printed the
 * result of `toStringAsFixed(2)`, which is a rounding error away from the wrong balance in a way no
 * screenshot reveals, and its "today" filter compared the server's date string against a
 * `DateFormat("yyyy-MM-dd")` rendering — so a `txdate` carrying a time would have counted as no day at
 * all and silently produced a spending figure of zero.
 *
 * The sums are read through [transactionsIn] rather than beside it, because that is how the app reads
 * them: a window's figure and the rows under it come from one pass, and a test that summed the records
 * itself would pass while the two disagreed.
 */
class SchoolCardModelsTest {

    private val today = LocalDate(2026, 3, 5)

    /** The window a page opens on, which is also the card's own day. */
    private fun todayOnly() = SchoolCardRange(today, today)

    // --- the window's spending ------------------------------------------------------------------

    @Test
    fun countsOnlyTheRangesPurchases() {
        val records = listOf(
            record("2026-03-05", "-12.30"),
            record("2026-03-04", "-99.00"),
            record("2026-03-05", "-0.70"),
        )

        assertEquals(1300L, transactionsIn(records, todayOnly()).expenseCents())
    }

    @Test
    fun acceptsATimestampAsWellAsABareDate() {
        // The server's own `txdate` format is not pinned by the protocol; the original rendered the
        // query's dates itself and compared the answer as a string, so a time component would have
        // dropped the record. The date part is what decides here.
        val records = listOf(record("2026-03-05 12:30:01", "-5.00"))

        assertEquals(500L, transactionsIn(records, todayOnly()).expenseCents())
    }

    @Test
    fun sumsWhateverWindowIsAskedFor() {
        // The whole point of the range query: the same records answer "what did today cost" and "what did
        // March cost", and the difference is the window rather than a second read of the card.
        val records = listOf(
            record("2026-02-28", "-50.00"),
            record("2026-03-01", "-10.00"),
            record("2026-03-05", "-12.30"),
            record("2026-03-31", "-7.70"),
            record("2026-04-01", "-1.00"),
        )

        assertEquals(
            3000L,
            transactionsIn(records, SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)))
                .expenseCents(),
        )
        assertEquals(1230L, transactionsIn(records, todayOnly()).expenseCents())
        assertEquals(
            2000L,
            transactionsIn(records, SchoolCardRange(today, LocalDate(2026, 3, 31))).expenseCents(),
            "Today is inside this window too: 12.30 today and 7.70 on the last day.",
        )
    }

    @Test
    fun keepsTopUpsOutOfTheSpendingTotal() {
        // A window that spent 12.30 and topped up 200 is a 12.30 window, not a -187.70 one. The original
        // netted the two and then chose its wording from the sign, which turns a purchase into income as
        // soon as a top-up is larger.
        val records = listOf(
            record("2026-03-05", "-12.30"),
            record("2026-03-05", "200.00"),
        )
        val flows = transactionsIn(records, todayOnly())

        assertEquals(1230L, flows.expenseCents(), "Just the 12.30 spent; the 200 stays out.")
        assertEquals(20000L, flows.incomeCents())
    }

    @Test
    fun sumsTheCentsTheServerSentRatherThanADouble() {
        // 0.1 + 0.2 in binary floating point is 0.30000000000000004, and a day of small purchases
        // accumulates the error into a visibly wrong total.
        val records = listOf(
            record("2026-03-05", "-0.10"),
            record("2026-03-05", "-0.20"),
        )

        val flows = transactionsIn(records, todayOnly())

        assertEquals(30L, flows.expenseCents())
        assertEquals("0.30", centsToYuanText(flows.expenseCents()))
    }

    @Test
    fun anEmptyWindowIsZeroAndNotUnknown() {
        // Zero, not null: a window with no rows in it is one with nothing spent. Only a *failed* query is
        // unknown, and that is the whole reason the card's own value is nullable while this is not.
        assertEquals(0L, transactionsIn(emptyList(), todayOnly()).expenseCents())
    }

    // --- the window itself ----------------------------------------------------------------------

    @Test
    fun ordersTwoPickedDaysWhicheverWayTheyWerePicked() {
        // The picker's second tap decides which end is which, and picking the later day first means the
        // same days as picking them the other way round.
        assertEquals(
            SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 5)),
            SchoolCardRange.of(LocalDate(2026, 3, 5), LocalDate(2026, 3, 1)),
        )
    }

    @Test
    fun refusesARangeThatEndsBeforeItStarts() {
        // Built by hand the wrong way round, it fails loudly: a silently swapped pair would hide which
        // caller was wrong, and the two ends are a user's own two taps.
        assertFailsWith<IllegalArgumentException> {
            SchoolCardRange(LocalDate(2026, 3, 5), LocalDate(2026, 3, 1))
        }
    }

    @Test
    fun countsAndContainsItsDaysInclusively() {
        val range = SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 5))

        assertEquals(5, range.dayCount, "Both ends are in the window.")
        assertEquals(1, SchoolCardRange(today, today).dayCount)
        assertTrue(range.isSingleDay.not())
        assertTrue(SchoolCardRange(today, today).isSingleDay)
        assertTrue(range.contains(LocalDate(2026, 3, 1)))
        assertTrue(range.contains(today))
        assertFalse(range.contains(LocalDate(2026, 2, 28)))
        assertFalse(range.contains(LocalDate(2026, 3, 6)))
    }

    @Test
    fun aCachedAnswerIsReusedOnlyWhenItCoversTheQuestion() {
        // The rule the flow cache rests on: a stored window answers a narrower one, and never a wider one.
        // Rows are the rows *in* a window, so a partial overlap would answer with a list quietly missing
        // days — which is the same class of mistake as a day's total under the wrong date.
        val month = SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))

        assertTrue(month.covers(todayOnly()))
        assertTrue(month.covers(month))
        assertTrue(month.covers(SchoolCardRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 2))))
        assertFalse(month.covers(SchoolCardRange(LocalDate(2026, 2, 28), LocalDate(2026, 3, 2))))
        assertFalse(month.covers(SchoolCardRange(LocalDate(2026, 3, 30), LocalDate(2026, 4, 2))))
    }

    // --- the window's flows ---------------------------------------------------------------------

    @Test
    fun listsTheWindowsFlowsNewestFirst() {
        // A list is read from the top, and the last thing bought is what the page is opened for. The
        // stamp is a date with an optional time, so ordering the strings orders the moments.
        val records = listOf(
            record(date = "2026-03-05 08:10:00", amount = "-3.50", merchant = "食堂"),
            record(date = "2026-03-05 12:03:11", amount = "-12.30", merchant = "超市"),
            record(date = "2026-03-04 23:59:59", amount = "-99.00", merchant = "昨天"),
            record(date = "2026-03-05", amount = "200.00", merchant = "圈存"),
        )

        assertEquals(
            listOf("超市", "食堂", "圈存", "昨天"),
            transactionsIn(records, SchoolCardRange(LocalDate(2026, 3, 4), today)).map { it.merchant },
            "Newest first, and a bare date — no time to compare — sorts behind the same day's stamps.",
        )
        assertEquals(
            listOf("超市", "食堂", "圈存"),
            transactionsIn(records, todayOnly()).map { it.merchant },
            "The day's own list leaves yesterday out.",
        )
    }

    @Test
    fun keepsEachRowsAmountAndItsSign() {
        // The row's amount is parsed once, here, and everything downstream prints the cents: parsing the
        // wire string again in the UI is how a row and its window's total start disagreeing.
        val flows = transactionsIn(
            listOf(
                record("2026-03-05 12:03:11", "-12.30"),
                record("2026-03-05 00:01:00", "200.00"),
            ),
            todayOnly(),
        )

        assertEquals(listOf(-1230L, 20000L), flows.map { it.amountCents })
        assertEquals(listOf(true, false), flows.map { it.isExpense })
        assertEquals(listOf("-12.30", "+200.00"), flows.map { it.amountText })
    }

    @Test
    fun dropsARowItCannotReadRatherThanCountingItAsZero() {
        // The same drop as `parseCardRecord`'s: a row with no amount counts for neither the figure nor the
        // list, so the two keep agreeing about the malformed record.
        val flows = transactionsIn(
            listOf(
                record("2026-03-05", "-12.30"),
                record("2026-03-05", "--"),
            ),
            todayOnly(),
        )

        assertEquals(1, flows.size)
        assertEquals(1230L, flows.expenseCents())
    }

    @Test
    fun dropsARowWhoseDateCannotBeRead() {
        // A row placed in the wrong window is a wrong total under a heading that names other days, so a
        // stamp that is not a date counts for nothing rather than for "probably today".
        val flows = transactionsIn(listOf(record("", "-12.30")), todayOnly())

        assertEquals(emptyList(), flows)
    }

    @Test
    fun printsTheClockTimeOfAStampThatHasOne() {
        val stamped = flow(time = "2026-03-05 12:03:11")
        val bare = flow(time = "2026-03-05")

        assertEquals("12:03:11", stamped.timeText, "The date is the page's own heading, not the cell's.")
        assertEquals("2026-03-05", bare.timeText, "With no time to show, the stamp is kept rather than blanked.")
    }

    @Test
    fun printsTheDayTooWhenTheListCoversMoreThanOne() {
        // A window's list has to say which day each row belongs to; the year goes, because every window a
        // person picks sits inside one, and the time needs those four characters more.
        val stamped = flow(time = "2026-03-05 12:03:11")

        assertEquals("03-05 12:03:11", stamped.stampText(withDate = true))
        assertEquals("12:03:11", stamped.stampText(withDate = false))
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
        assertEquals(1230L, transactionsIn(listOf(record!!), todayOnly()).expenseCents())
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

    private fun record(date: String, amount: String, merchant: String = "食堂") =
        SchoolCardRecord(merchant = merchant, date = date, amount = amount)

    /** A flow whose only interesting field is its stamp, for the display rules. */
    private fun flow(time: String) =
        SchoolCardTransaction(merchant = "食堂", time = time, amountCents = -1230)

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject
}
