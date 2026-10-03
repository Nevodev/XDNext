package com.nevoit.xdnext.data.experiment

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the lab site's HTML.
 *
 * The markup here is written in the shape the original navigates — a `Orders_ctl00` table whose cells it
 * indexes, a `plan1_ExpeList` select, and `plan1_PlanGrid_TimeN_*`/`_Teacher_*` spans — because that is
 * the only record of it: there is no captured page for this site, unlike the energy system's recorded
 * wire bytes. What is pinned is therefore what a *reading of the original* can pin: which cell each
 * field comes from, that the two lab sessions are told apart by the marker the original sniffed, and
 * that a row which does not fit the shape is dropped instead of throwing.
 */
class ExperimentParserTest {

    // --- The booking table ------------------------------------------------------------------------

    @Test
    fun readsTheFiveFieldsFromTheColumnsTheOriginalIndexed() {
        val rows = ExperimentParser.rows(bookings())

        assertEquals(2, rows.size, "The header row and the short row are not bookings.")

        val first = rows[0]
        assertEquals("示波器的使用", first.subject, "The `（3学时）` suffix is stripped.")
        assertEquals("星期三下午15:55-18:10", first.timeText)
        assertEquals(LocalDate(2026, 3, 5), first.date)
        assertEquals("实验楼A201", first.classroom)
        assertEquals("实验讲义第三章", first.reference)

        val second = rows[1]
        assertEquals("霍尔效应", second.subject, "A half-width `(3学时)` is stripped too.")
        assertEquals("实验楼B105", second.classroom)
        assertNull(second.reference, "An empty cell is no reference rather than an empty one.")
    }

    @Test
    fun theSitesDateIsMonthDayYearProvedByTheWeekdayItsOwnColumnPrints() {
        // Measured on a device, and the reading that was wrong first: the page writes `9/15/2026` for
        // the 15th of September, and its time column says 星期二. Each of these three is the weekday that
        // order produces — 2026-09-15 is a Tuesday — and the other order produces a different day for
        // every one of them. A date read the wrong way round is not a wrong date on screen: it is a
        // booking that never reaches the grid.
        val samples = listOf(
            Triple("9/15/2026", LocalDate(2026, 9, 15), "星期二"),
            Triple("9/24/2026", LocalDate(2026, 9, 24), "星期四"),
            Triple("10/14/2026", LocalDate(2026, 10, 14), "星期三"),
        )

        samples.forEach { (text, expected, weekday) ->
            val row = ExperimentParser.rows(bookingOn(date = text, time = "${weekday}晚上18:30-20:45")).single()

            assertEquals(expected, row.date, "$text is the 15th of the month it names, as `$weekday` says.")
            assertEquals(
                expected.dayOfWeek.isoDayNumber,
                row.date.dayOfWeek.isoDayNumber,
                "The parsed date and the page's own weekday name the same day.",
            )
        }
    }

    @Test
    fun aPageThatSpellsTheDateTheOtherWayRoundIsStillRead() {
        // Not a shape the site has been seen to send; recognised by the year's own length so that a
        // change there is a date rather than a lost booking.
        val row = ExperimentParser.rows(bookingOn(date = "2026/9/15", time = "星期二晚上18:30-20:45")).single()

        assertEquals(LocalDate(2026, 9, 15), row.date)
    }

    @Test
    fun skipsARowItCannotReadRatherThanLosingTheList() {
        // The original indexed `expTds[4]` and parsed the date with `int.parse`, so a row the site spells
        // differently threw and took every other booking with it. The unreadable row is one row here.
        val html = """
            <table id="Orders_ctl00">
              <tr><td>1</td><td><span class="linkSmallBold">无日期的实验（3学时）</span></td><td>-</td>
                  <td><span>星期三下午15:55-18:10</span></td><td><span>待定</span></td><td><span>A101</span></td>
                  <td>d</td><td>e</td><td>f</td><td><span></span></td></tr>
              <tr><td>2</td><td><span class="linkSmallBold">正常的实验（3学时）</span></td><td>-</td>
                  <td><span>星期三下午15:55-18:10</span></td><td><span>3/5/2026</span></td><td><span>A102</span></td>
                  <td>d</td><td>e</td><td>f</td><td><span></span></td></tr>
            </table>
        """

        val rows = ExperimentParser.rows(html)

        assertEquals(listOf("正常的实验"), rows.map { it.subject })
    }

    @Test
    fun aPageWithoutTheTableIsAnEmptyList() {
        assertTrue(ExperimentParser.rows("<html><body>系统维护中</body></html>").isEmpty())
        assertTrue(ExperimentParser.rows("").isEmpty())
    }

    // --- The two sessions -------------------------------------------------------------------------

    @Test
    fun theAfternoonMarkerPicksTheAfternoonSitting() {
        val range = ExperimentParser.timeRangeOf(LocalDate(2026, 3, 5), "星期三下午15:55-18:10")

        assertEquals(LocalTime(15, 55), range.start.time)
        assertEquals(LocalTime(18, 10), range.stop.time)
    }

    @Test
    fun anythingElseIsTheEveningSitting() {
        val range = ExperimentParser.timeRangeOf(LocalDate(2026, 3, 6), "星期四晚上18:30-20:45")

        assertEquals(LocalTime(18, 30), range.start.time)
        assertEquals(LocalTime(20, 45), range.stop.time)
        assertEquals(LocalDate(2026, 3, 6), range.start.date)
    }

    // --- The plan grid ----------------------------------------------------------------------------

    @Test
    fun readsTheCourseListAndTheTeacherGrid() {
        assertEquals(
            mapOf("示波器的使用" to "0", "霍尔效应" to "1"),
            ExperimentParser.subjectOptions(course()),
        )
        assertEquals(
            listOf(
                TeacherSlot("星期三下午15:55-18:10", "张三"),
                TeacherSlot("星期四晚上18:30-20:45", "李四"),
            ),
            ExperimentParser.teacherSlots(course()),
        )
    }

    @Test
    fun findsTheTeacherOfTheSlotWhoseTimeTheBookingNames() {
        assertEquals("李四", ExperimentParser.teacherFor(course(), "星期四晚上18:30-20:45"))
        assertNull(ExperimentParser.teacherFor(course(), "星期五下午15:55-18:10"))
        assertNull(
            ExperimentParser.teacherFor(course(), ""),
            "A blank time text matches every slot with `contains`, so it is refused instead.",
        )
    }

    @Test
    fun pairsTheTwoSpansByTheirIdRatherThanByPosition() {
        // The original took the nth time span and the nth teacher span. A page that omits one row's
        // teacher would then give every later slot the wrong one.
        val html = """
            <span id="plan1_PlanGrid_TimeN_0">星期三下午15:55-18:10</span>
            <span id="plan1_PlanGrid_TimeN_2">星期五下午15:55-18:10</span>
            <span id="plan1_PlanGrid_Teacher_2">王五</span>
        """

        assertEquals("王五", ExperimentParser.teacherFor(html, "星期五下午15:55-18:10"))
        assertNull(ExperimentParser.teacherFor(html, "星期三下午15:55-18:10"))
    }

    // --- The login page's own complaint -----------------------------------------------------------

    @Test
    fun stripsTheMarkupFromTheLoginPagesMessage() {
        val html = """
            <span id="login1_Label1"><font color="Red">用户名或密码错误<br></font></span>
        """

        assertEquals("用户名或密码错误。", ExperimentParser.loginError(html))
        assertNull(ExperimentParser.loginError("<html><body>nothing</body></html>"))
    }

    // --- Fixtures ---------------------------------------------------------------------------------

    private fun bookings(): String = """
        <table id="Orders_ctl00">
          <tr><td>序号</td><td>实验名称</td><td>实验类型</td><td>上课时间</td><td>上课日期</td>
              <td>上课教室</td><td>教师</td><td>状态</td><td>操作</td><td>参考资料</td></tr>
          <tr><td>1</td><td><span class="linkSmallBold">示波器的使用（3学时）</span></td><td>物理</td>
              <td><span>星期三下午15:55-18:10</span></td><td><span>3/5/2026</span></td>
              <td><span>实验楼A201</span></td><td>g</td><td>h</td><td>i</td>
              <td><span>实验讲义第三章</span></td></tr>
          <tr><td>2</td><td><span class="linkSmallBold">霍尔效应(3学时)</span></td><td>物理</td>
              <td><span>星期四晚上18:30-20:45</span></td><td><span>3/6/2026</span></td>
              <td><span>实验楼B105</span></td><td>g</td><td>h</td><td>i</td>
              <td><span> </span></td></tr>
          <tr><td>3</td><td><span class="linkSmallBold">被截断的行</span></td></tr>
        </table>
    """

    /** One booking row in the shape the site prints, for the cases that vary a single cell. */
    private fun bookingOn(date: String, time: String): String = """
        <table id="Orders_ctl00">
          <tr><td>1</td><td><span class="linkSmallBold">示波器的使用（3学时）</span></td><td>物理</td>
              <td><span>$time</span></td><td><span>$date</span></td><td><span>实验楼A201</span></td>
              <td>g</td><td>h</td><td>i</td><td><span></span></td></tr>
        </table>
    """

    private fun course(): String = """
        <select id="plan1_ExpeList">
          <option value="0">示波器的使用</option>
          <option value="1">霍尔效应</option>
        </select>
        <span id="plan1_PlanGrid_TimeN_0">星期三下午15:55-18:10</span>
        <span id="plan1_PlanGrid_Teacher_0">张三</span>
        <span id="plan1_PlanGrid_TimeN_1">星期四晚上18:30-20:45</span>
        <span id="plan1_PlanGrid_Teacher_1">李四</span>
    """
}
