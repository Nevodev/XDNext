package com.nevoit.xdnext.data.timetable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the semester code's ordering.
 *
 * The code is `2025-2026-1`, the ordering is by the first four characters glued to the last one, and
 * that is the whole of the original's `parseSemesterCodeToInt`. It matters because the semester the app
 * shows is the *newer* of the registrar's current one and the last one used on the device — a
 * comparison that silently picks the wrong one if the ordering is off by a term, and the wrong semester
 * looks exactly like a timetable with no classes in it.
 */
class SemesterCodeTest {

    @Test
    fun ordersByAcademicYearAndThenByTerm() {
        assertTrue(SemesterCode.order("2025-2026-1") > SemesterCode.order("2024-2025-2"))
        assertTrue(SemesterCode.order("2025-2026-2") > SemesterCode.order("2025-2026-1"))
        assertEquals(20251, SemesterCode.order("2025-2026-1"))
    }

    @Test
    fun keepsTheNewerOfTwoCodes() {
        assertEquals(
            "2025-2026-1",
            SemesterCode.latest("2025-2026-1", "2024-2025-2"),
        )
        assertEquals(
            "2025-2026-1",
            SemesterCode.latest("2024-2025-2", "2025-2026-1"),
            "Order of arguments must not decide it.",
        )
    }

    @Test
    fun aCodeThatSaysNothingLosesToARealOne() {
        // An absent or malformed local code must not win over the registrar's answer — otherwise a
        // first launch would keep asking for a semester nobody named.
        val nothing = SemesterCode.order("")
        assertEquals(-1, nothing)
        assertTrue(nothing < SemesterCode.order("2025-2026-1"))
        assertEquals("2025-2026-1", SemesterCode.latest("2025-2026-1", ""))
        assertEquals("2025-2026-1", SemesterCode.latest("", "2025-2026-1"))
        assertEquals(-1, SemesterCode.order("abcd-efgh-1"))
    }

    @Test
    fun splitsACodeTheWayTheTermStartEndpointWantsIt() {
        assertEquals("2025-2026" to "1", SemesterCode.academicYearAndTerm("2025-2026-1"))
        assertNull(SemesterCode.academicYearAndTerm("2025-2026"))
        assertNull(SemesterCode.academicYearAndTerm(""))
    }
}
