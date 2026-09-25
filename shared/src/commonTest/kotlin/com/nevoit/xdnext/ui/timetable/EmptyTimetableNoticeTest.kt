package com.nevoit.xdnext.ui.timetable

import com.nevoit.xdnext.data.timetable.ClassTableData
import com.nevoit.xdnext.data.timetable.TimetableState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for what the timetable page says when it has no grid to draw.
 *
 * The distinction worth pinning is a request that is *still running* against a semester that really has
 * nothing: the first sign-in has no cache, so the page is drawn while the first fetch is in flight, and
 * telling the user "this semester has no courses" at that moment is what sent them looking for a refresh
 * button. A page that says it is working is what makes the grid appearing on its own believable.
 */
class EmptyTimetableNoticeTest {

    @Test
    fun saysItIsWorkingWhileTheFirstRequestIsInFlight() {
        val notice = emptyTimetableNotice(TimetableState(isLoading = true))

        assertEquals(TimetableStrings.bannerLoading(TimetableStrings.SOURCE_CLASS_TABLE), notice)
    }

    @Test
    fun namesTheSemesterOnceNothingIsRunning() {
        // A failed first fetch: nothing is running and there is nothing to draw, so the title bar's
        // 加载错误 button is where the reason is. There is no semester code to name, which is why the
        // sentence opens with a blank — the original's own behaviour for a load that never arrived.
        val notice = emptyTimetableNotice(TimetableState(isLoading = false))

        assertEquals(TimetableStrings.emptyNoCourse(""), notice)
    }

    @Test
    fun namesTheSemesterWhenTheRegistrarHasPublishedNothingForIt() {
        // A term the registrar has opened but filled with nothing: there *is* a timetable — a term start
        // day and a semester code — so the page says which semester is empty rather than that it is busy.
        val notice = emptyTimetableNotice(
            TimetableState(
                isLoading = false,
                data = ClassTableData(semesterCode = "2025-2026-1", termStartDay = "2025-09-01"),
            ),
        )

        assertEquals(TimetableStrings.emptyNoCourse("2025-2026-1"), notice)
    }
}
