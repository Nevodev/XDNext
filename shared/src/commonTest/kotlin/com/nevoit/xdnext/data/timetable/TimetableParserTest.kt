package com.nevoit.xdnext.data.timetable

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for turning the registrar's rows into a timetable.
 *
 * Two halves. The first is the straightforward one — read the rows, decode the week flags, de-duplicate
 * the courses. The second is `ClassTableSession`'s schedule-adjustment merge, which is the most
 * intricate thing in the module and the one where a mistake is invisible: a 调课 that half-applies
 * leaves a class on the wrong day, and a 补课 that applies twice leaves one class with a "还有 1 个
 * 日程" marker on it.
 */
class TimetableParserTest {

    // --- Reading the rows ------------------------------------------------------------------------

    @Test
    fun readsTheRowsIntoCoursesAndArrangements() {
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "高等数学", code = "MA1001", number = "01", day = 1, start = 1, stop = 2, weeks = "1000"),
                // The same course, a second session: one detail, two arrangements, both pointing at it.
                courseRow(name = "高等数学", code = "MA1001", number = "01", day = 3, start = 5, stop = 6, weeks = "0100"),
            ),
            notArrangedRows = listOf(notArrangedRow(name = "大学物理", code = "PH1001", number = "02")),
        )

        assertEquals("2025-2026-1", data.semesterCode)
        assertEquals("2025-09-01", data.termStartDay)
        assertEquals(1, data.classDetail.size, "Two rows of one course are one course.")
        assertEquals(2, data.timeArrangement.size)
        assertEquals(listOf(true, false, false, false), data.timeArrangement.first().weekList)
        assertEquals(0, data.timeArrangement.first().index, "Both sessions point at the one detail.")
        assertEquals("大学物理", data.notArranged.single().name)
        assertEquals(4, data.semesterLength, "The longest week flag string is the semester's length.")
    }

    @Test
    fun asksTheCourseDetailByPositionRatherThanByIndex() {
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "10"),
                courseRow(name = "B", code = "B1", day = 1, start = 1, stop = 2, weeks = "10"),
            ),
        )

        assertEquals("A", data.detailForArrangementAt(0).name)
        assertEquals("B", data.detailForArrangementAt(1).name)
    }

    @Test
    fun skipsARowItCannotReadRatherThanFailingTheWholeRefresh() {
        // The original indexed straight into these fields and would have thrown on a row the registrar
        // answered differently that semester — losing every other row with it.
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "Good", code = "G1", day = 1, start = 1, stop = 2, weeks = "10"),
                row("""{"KCM":"No weeks","KCH":"X1","KSJC":"1","JSJC":"2","SKXQ":"1"}"""),
                row("""{"KCM":"No periods","KCH":"X2","SKXQ":"1","SKZC":"10"}"""),
            ),
        )

        assertEquals(listOf("Good"), data.classDetail.map { it.name })
        assertEquals(1, data.timeArrangement.size)
    }

    @Test
    fun anUnpublishedSemesterStillNamesItself() {
        val data = TimetableParser.parseEhall(
            semesterCode = "2025-2026-1",
            termStartDay = "2025-09-01",
            classTableRows = emptyList(),
            notArrangedRows = emptyList(),
            classChangeRows = emptyList(),
        )

        assertEquals("2025-2026-1", data.semesterCode)
        assertEquals("2025-09-01", data.termStartDay)
        assertFalse(data.hasClasses)
    }

    // --- Folding in the schedule adjustments -----------------------------------------------------

    @Test
    fun aMakeUpSessionForAnUnknownCourseBringsTheCourseWithIt() {
        // How a class that only ever meets as a make-up session ever appears at all.
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "10"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "03",
                    code = "C9",
                    name = "C",
                    newDay = 4,
                    newStart = 7,
                    newStop = 8,
                    newWeeks = "1000",
                ),
            ),
        )

        assertEquals(listOf("A", "C"), data.classDetail.map { it.name })
        val added = data.timeArrangement.single { data.detailFor(it).name == "C" }
        assertEquals(4, added.day)
        assertEquals(7, added.start)
        assertEquals(8, added.stop)
        assertEquals(listOf(true, false, false, false), added.weekList)
    }

    @Test
    fun aMakeUpSessionForAKnownCourseIsAddedExactlyOnce() {
        // The original left this entry in its work list after applying it, so its loop ran again and
        // added the session a second time — and the two copies then merged into one card claiming
        // "还有 1 个日程" about a class the student has once.
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "1000"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "03",
                    code = "A1",
                    name = "A",
                    originalDay = 1,
                    originalStart = 1,
                    originalStop = 2,
                    originalWeeks = "1000",
                    newDay = 5,
                    newStart = 1,
                    newStop = 2,
                    newWeeks = "0100",
                ),
            ),
        )

        assertEquals(
            2,
            data.timeArrangement.size,
            "One original session and one make-up session, not two make-up sessions.",
        )
        assertEquals(1, data.timeArrangement.count { it.day == 5 })
    }

    @Test
    fun aCancellationClearsTheWeeksItNames() {
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "1100"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "02",
                    code = "A1",
                    name = "A",
                    originalDay = 1,
                    originalStart = 1,
                    originalStop = 2,
                    originalWeeks = "1000",
                ),
            ),
        )

        assertEquals(
            listOf(false, true, false, false),
            data.timeArrangement.single().weekList,
            "Only the named week is cancelled; the rest of the term is untouched.",
        )
    }

    @Test
    fun aMoveClearsTheOriginalWeeksAndAddsTheNewOnes() {
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "1000"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "01",
                    code = "A1",
                    name = "A",
                    originalDay = 1,
                    originalStart = 1,
                    originalStop = 2,
                    originalWeeks = "1000",
                    newDay = 4,
                    newStart = 7,
                    newStop = 8,
                    newWeeks = "1000",
                    newClassroom = "B203",
                ),
            ),
        )

        val moved = data.timeArrangement.single { it.day == 4 }
        assertEquals(7, moved.start)
        assertEquals("B203", moved.classroom)
        assertEquals(
            0,
            moved.index,
            "The moved session still belongs to the same course, so the card keeps its colour.",
        )
        assertFalse(data.timeArrangement.first { it.day == 1 }.weekList[0])
    }

    @Test
    fun aMoveIssuedBothWaysCancelsOutInsteadOfDrawingTwoClasses() {
        // The registrar's habit: it publishes A moved to B, and separately B moved back to A. Both are
        // applied in order, and the second one is recognised as the first one's return trip, so the
        // class ends up where it started rather than in both places.
        val data = parse(
            classTableRows = listOf(
                // The class does not actually meet in week 1, which is what leaves the first move's
                // clearing a no-op and lets it be remembered as pending.
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "0100"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "01",
                    code = "A1",
                    name = "A",
                    originalDay = 1,
                    originalStart = 1,
                    originalStop = 2,
                    originalWeeks = "1000",
                    newDay = 3,
                    newStart = 1,
                    newStop = 2,
                    newWeeks = "1000",
                ),
                changeRow(
                    type = "01",
                    code = "A1",
                    name = "A",
                    originalDay = 3,
                    originalStart = 1,
                    originalStop = 2,
                    originalWeeks = "1000",
                    newDay = 1,
                    newStart = 1,
                    newStop = 2,
                    newWeeks = "1000",
                ),
            ),
        )

        assertTrue(
            data.timeArrangement.none { it.day == 1 && it.weekList[0] },
            "The move back must not resurrect the class in a week the timetable never placed it in.",
        )
    }

    @Test
    fun aChangeNamingAWeekPastTheEndOfTheListGrowsTheListAndTheSemester() {
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "1100"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "02",
                    code = "A1",
                    name = "A",
                    originalDay = 1,
                    originalStart = 1,
                    originalStop = 2,
                    // Week 5 of a list that only reaches week 3.
                    originalWeeks = "000001",
                ),
            ),
        )

        assertEquals(
            6,
            data.timeArrangement.single().weekList.size,
            "A week list has to reach the week named before it can be cleared.",
        )
        assertEquals(6, data.semesterLength)
        assertEquals(
            listOf(true, true, false, false, false, false),
            data.timeArrangement.single().weekList,
            "The weeks it did not name are untouched.",
        )
    }

    @Test
    fun aChangeWithNoMatchingArrangementIsDropped() {
        // The registrar occasionally names a class, a day and a period combination the timetable does
        // not have. Applying it would add a session to nothing; the original skipped it, and skipping
        // it is also what stops the merge from having to guess which arrangement was meant.
        val data = parse(
            classTableRows = listOf(
                courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "1000"),
            ),
            classChangeRows = listOf(
                changeRow(
                    type = "02",
                    code = "A1",
                    name = "A",
                    originalDay = 6,
                    originalStart = 9,
                    originalStop = 10,
                    originalWeeks = "1000",
                ),
            ),
        )

        assertEquals(listOf(true, false, false, false), data.timeArrangement.single().weekList)
    }

    @Test
    fun aMakeUpSessionPrefersTheNewTeacherOnlyWhenTheTeacherActuallyChanged() {
        // The registrar sends both spellings on every row, so preferring the new one unconditionally
        // would relabel a class's teacher with another rendering of the same person.
        val unchanged = parse(
            classTableRows = listOf(courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "10")),
            classChangeRows = listOf(
                changeRow(
                    type = "03",
                    code = "A1",
                    name = "A",
                    newDay = 2,
                    newStart = 1,
                    newStop = 2,
                    newWeeks = "10",
                    newTeacherData = "1234/张三",
                    originalTeacherData = "1234/张三",
                ),
            ),
        )
        // The teacher an arrangement carries is the *cleaned* name — the registrar's numbering is
        // stripped, which is what the original's `originalTeacher` getter did.
        assertEquals("张三", unchanged.timeArrangement.single { it.day == 2 }.teacher)

        val changed = parse(
            classTableRows = listOf(courseRow(name = "A", code = "A1", day = 1, start = 1, stop = 2, weeks = "10")),
            classChangeRows = listOf(
                changeRow(
                    type = "03",
                    code = "A1",
                    name = "A",
                    newDay = 2,
                    newStart = 1,
                    newStop = 2,
                    newWeeks = "10",
                    newTeacherData = "5678/李四",
                    originalTeacherData = "1234/张三",
                ),
            ),
        )
        assertEquals("李四", changed.timeArrangement.single { it.day == 2 }.teacher)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun parse(
        classTableRows: List<JsonObject> = emptyList(),
        notArrangedRows: List<JsonObject> = emptyList(),
        classChangeRows: List<JsonObject> = emptyList(),
    ): ClassTableData = TimetableParser.parseEhall(
        semesterCode = "2025-2026-1",
        termStartDay = "2025-09-01",
        classTableRows = classTableRows,
        notArrangedRows = notArrangedRows,
        classChangeRows = classChangeRows,
    )

    private fun row(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private fun courseRow(
        name: String,
        code: String,
        number: String = "01",
        day: Int,
        start: Int,
        stop: Int,
        weeks: String,
        classroom: String = "A101",
        teacher: String = "1234/张三",
    ): JsonObject = row(
        """{"KCM":"$name","KCH":"$code","KXH":"$number","KSJC":"$start","JSJC":"$stop",""" +
                """"SKXQ":"$day","SKZC":"$weeks","JASMC":"$classroom","SKJS":"$teacher"}""",
    )

    private fun notArrangedRow(name: String, code: String, number: String): JsonObject = row(
        """{"KCM":"$name","KCH":"$code","KXH":"$number","SKJS":"1234/张三"}""",
    )

    /**
     * One adjustment row.
     *
     * `type` is the registrar's own code: `01` 调课, `02` 停课, and anything else 补课.
     */
    private fun changeRow(
        type: String,
        code: String,
        name: String,
        originalDay: Int? = null,
        originalStart: Int? = null,
        originalStop: Int? = null,
        originalWeeks: String? = null,
        newDay: Int? = null,
        newStart: Int? = null,
        newStop: Int? = null,
        newWeeks: String? = null,
        newClassroom: String? = null,
        originalTeacherData: String? = null,
        newTeacherData: String? = null,
    ): JsonObject {
        val fields = buildMap {
            put("TKLXDM", "\"$type\"")
            put("KCH", "\"$code\"")
            put("KXH", "\"01\"")
            put("KCM", "\"$name\"")
            originalDay?.let { put("SKXQ", "\"$it\"") }
            originalStart?.let { put("KSJC", "\"$it\"") }
            originalStop?.let { put("JSJC", "\"$it\"") }
            originalWeeks?.let { put("SKZC", "\"$it\"") }
            newDay?.let { put("XSKXQ", "\"$it\"") }
            newStart?.let { put("XKSJC", "\"$it\"") }
            newStop?.let { put("XJSJC", "\"$it\"") }
            newWeeks?.let { put("XSKZC", "\"$it\"") }
            newClassroom?.let { put("XJASMC", "\"$it\"") }
            originalTeacherData?.let { put("YSKJS", "\"$it\"") }
            newTeacherData?.let { put("XSKJS", "\"$it\"") }
        }
        return row(fields.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" })
    }
}
