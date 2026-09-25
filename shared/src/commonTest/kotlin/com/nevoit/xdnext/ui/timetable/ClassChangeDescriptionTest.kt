package com.nevoit.xdnext.ui.timetable

import com.nevoit.xdnext.data.timetable.ChangeType
import com.nevoit.xdnext.data.timetable.ClassChange
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the sentence a schedule adjustment is described with.
 *
 * Two things are worth pinning. The weeks are printed **one-based** — the model keeps zero-based
 * indices, and off-by-one there reads as "the wrong week" rather than as an error. And the sentence is
 * drawn with **every space removed**, which is the original's own last step: its translation values
 * carry spaces a Chinese sentence would not have, and the page took them out before drawing.
 */
class ClassChangeDescriptionTest {

    @Test
    fun aMoveNamesBothSlotsAndTheRoomItMovedTo() {
        val described = describe(
            change(
                type = ChangeType.Change,
                originalWeeks = listOf(true, true, false, false),
                originalWeek = 5,
                originalRange = listOf(7, 8),
                newWeeks = listOf(false, false, true, false),
                newWeek = 3,
                newRange = listOf(5, 6),
                newClassroom = "B-306",
            ),
        )

        // Weeks 1 and 2 moved to week 3 — one-based, and with no spaces anywhere.
        assertEquals(
            "调课信息，从第1,2周星期五的7-8节调整为第3周星期三的5-6节，B-306教室上课",
            described,
        )
    }

    @Test
    fun aCancellationOnlyNamesWhereTheClassWas() {
        val described = describe(
            change(
                type = ChangeType.Stop,
                originalWeeks = listOf(false, true, false, false),
                originalWeek = 2,
                originalRange = listOf(1, 2),
            ),
        )

        assertEquals("停课信息，第2周星期二的1-2节停课", described)
    }

    @Test
    fun aMakeUpSessionNamesWhereItWasAdded() {
        val described = describe(
            change(
                type = ChangeType.Patch,
                newWeeks = listOf(false, false, false, true),
                newWeek = 6,
                newRange = listOf(9, 11),
                newClassroom = "A-101",
            ),
        )

        assertEquals(
            "补课信息，第4周星期六的9-11节，A-101补课",
            described,
        )
    }

    @Test
    fun anAdjustmentTheRegistrarLeftUndatedNamesNoWeekRatherThanAPlaceholder() {
        // A make-up session with no weeks and no day is a real answer from this endpoint. The original's
        // `weekChar` answered an empty string for anything that was not 1..7 and the week list rendered as
        // nothing, so the gaps are simply empty — no "第 周", no substitute character.
        val described = describe(
            change(
                type = ChangeType.Patch,
                newWeeks = null,
                newWeek = null,
                newRange = listOf(9, 11),
                newClassroom = "A-101",
            ),
        )

        assertEquals("补课信息，第周星期的9-11节，A-101补课", described)
    }

    private fun change(
        type: ChangeType,
        classCode: String = "24CE5046",
        className: String = "电路分析基础",
        originalWeeks: List<Boolean>? = null,
        originalWeek: Int? = null,
        originalRange: List<Int> = emptyList(),
        originalClassroom: String? = null,
        newWeeks: List<Boolean>? = null,
        newWeek: Int? = null,
        newRange: List<Int> = emptyList(),
        newClassroom: String? = null,
    ) = ClassChange(
        type = type,
        classCode = classCode,
        classNumber = "02",
        className = className,
        originalAffectedWeeks = originalWeeks,
        newAffectedWeeks = newWeeks,
        originalClassRange = originalRange,
        newClassRange = newRange,
        originalWeek = originalWeek,
        newWeek = newWeek,
        originalClassroom = originalClassroom,
        newClassroom = newClassroom,
    )
}
