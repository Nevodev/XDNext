package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.core.log.appLog
import kotlinx.serialization.json.JsonObject

/**
 * Turning the registrar's wire rows into a [ClassTableData].
 *
 * Ported from `ClassTableSession._getEhall`, split out of the session for one reason: almost all of
 * what that method did was arithmetic over rows, and none of it needed a network, a cookie or a
 * coroutine. Here it is a pure function of the rows, which is what makes the two parts that are
 * genuinely tricky — the week-flag decoding and the schedule-adjustment merge — testable at all.
 *
 * ### What is shared and what is not
 *
 * [parseEhall] is the undergraduate branch. The postgraduate branch (`_getYjspt`) differs in three
 * ways, and this is where they belong when it is written: it reads differently-named row fields
 * (`KCMC`/`KCDM`/`KSJCDM`/`JSJCDM`/`XQ`/`ZCBH`), it takes the term start day from a school-calendar
 * call rather than from `cxjcs`, and it runs one extra post-pass that splits a class whose periods are
 * not contiguous into separate arrangements. Everything else — [buildChanges], [mergeChanges], the
 * week-list handling — is shared, which is why those are top-level functions rather than steps inside
 * one long method.
 *
 * ### Rows that cannot be read are skipped, not fatal
 *
 * The original indexed straight into `int.parse(i["KSJC"])` and would throw on a row whose field was
 * spelled differently, on a week-flag string with a stray character, and on any row the registrar
 * chose to answer differently that semester. A timetable is one screen of a much larger refresh; one
 * unreadable row is logged and dropped here so the other thirty still draw.
 */
object TimetableParser {

    /**
     * The undergraduate timetable.
     *
     * [classTableRows] is empty for a semester the registrar has not published, which yields a
     * [ClassTableData] carrying the semester code and the term start day but nothing else — that is
     * what `NotPublished` means, and the page has an empty state for exactly it.
     */
    fun parseEhall(
        semesterCode: String,
        termStartDay: String,
        classTableRows: List<JsonObject>,
        notArrangedRows: List<JsonObject>,
        classChangeRows: List<JsonObject>,
    ): ClassTableData {
        val classDetail = mutableListOf<ClassDetail>()
        val timeArrangement = mutableListOf<TimeArrangement>()
        var semesterLength = 1

        for (row in classTableRows) {
            val name = row.text("KCM")
            val start = row.int("KSJC")
            val stop = row.int("JSJC")
            val day = row.int("SKXQ")
            val weekFlags = row.text("SKZC")
            if (name.isNullOrBlank() || start == null || stop == null || day == null || weekFlags == null) {
                appLog.w { "[TimetableParser] Skipping an unreadable timetable row: $row" }
                continue
            }

            val detail = ClassDetail(name = name, code = row.text("KCH"), number = row.text("KXH"))
            // `contains` and `indexOf` are both name-based, because `ClassDetail`'s equality is — see
            // its own documentation. That is what keeps two rows of one course pointing at one detail.
            if (detail !in classDetail) classDetail.add(detail)

            val weekList = decodeWeekFlags(weekFlags)
            if (weekList.size > semesterLength) semesterLength = weekList.size

            timeArrangement.add(
                TimeArrangement(
                    source = ClassSource.School,
                    index = classDetail.indexOf(detail),
                    weekList = weekList,
                    teacher = row.text("SKJS"),
                    day = day,
                    start = start,
                    stop = stop,
                    classroom = row.text("JASMC"),
                ),
            )
        }

        val notArranged = notArrangedRows.mapNotNull { row ->
            val name = row.text("KCM") ?: return@mapNotNull null
            NotArrangementClassDetail(
                name = name,
                code = row.text("KCH"),
                number = row.text("KXH"),
                teacher = row.text("SKJS"),
            )
        }

        val data = ClassTableData(
            semesterLength = semesterLength,
            semesterCode = semesterCode,
            termStartDay = termStartDay,
            classDetail = classDetail,
            notArranged = notArranged,
            timeArrangement = timeArrangement,
            classChanges = buildChanges(classChangeRows),
        )

        return mergeChanges(data)
    }

    /**
     * Decodes a week-flag string: `"0110"` means weeks 2 and 3, and the list is one flag per teaching
     * week, zero-based.
     *
     * A character that is neither `0` nor `1` reads as `false` rather than raising, which is the one
     * concession to the registrar's occasional stray whitespace.
     */
    internal fun decodeWeekFlags(flags: String): List<Boolean> = flags.map { it == '1' }

    /** The registrar's adjustment rows, as [ClassChange]s. */
    internal fun buildChanges(rows: List<JsonObject>): List<ClassChange> = rows.mapNotNull { row ->
        val classCode = row.text("KCH")
        val className = row.text("KCM")
        if (classCode.isNullOrBlank() || className.isNullOrBlank()) {
            appLog.w { "[TimetableParser] Skipping an unreadable schedule change: $row" }
            return@mapNotNull null
        }
        ClassChange(
            type = changeTypeOf(row.text("TKLXDM")),
            classCode = classCode,
            classNumber = row.text("KXH").orEmpty(),
            className = className,
            originalAffectedWeeks = row.text("SKZC")?.let(::decodeWeekFlags),
            newAffectedWeeks = row.text("XSKZC")?.let(::decodeWeekFlags),
            originalTeacherData = row.text("YSKJS"),
            newTeacherData = row.text("XSKJS"),
            originalClassRange = listOf(
                row.int("KSJC") ?: -1,
                row.int("JSJC") ?: -1,
            ),
            newClassRange = listOf(
                row.int("XKSJC") ?: -1,
                row.int("XJSJC") ?: -1,
            ),
            originalWeek = row.int("SKXQ"),
            newWeek = row.int("XSKXQ"),
            originalClassroom = row.text("JASMC"),
            newClassroom = row.text("XJASMC"),
        )
    }

    /** `01` is 调课, `02` is 停课, and everything else the registrar sends means 补课. */
    private fun changeTypeOf(code: String?): ChangeType = when (code) {
        "01" -> ChangeType.Change
        "02" -> ChangeType.Stop
        else -> ChangeType.Patch
    }

    /**
     * Folds the registrar's adjustments into the timetable.
     *
     * Ported from the merge loop at the end of `_getEhall`, which is the part of that file worth
     * reading twice. It walks the adjustments once, and for each one:
     *
     *  - **补课** for a course that is not in the timetable at all: the course is added as a detail and
     *    the new session as an arrangement. This is how a class that only ever meets as a make-up
     *    session ever appears.
     *  - **补课** for a course that is: only the arrangement is added.
     *  - **停课**: the listed weeks are cleared on every arrangement matching the course, its day and
     *    its periods.
     *  - **调课**: the same clearing, and then the class is added again at its new day, periods, room
     *    and weeks, still pointing at the same course.
     *
     * Two things about the original's own version are not reproduced, and both are the kind that
     * produces a *wrong screen* rather than a different one:
     *
     *  - **An entry is removed from the work list once it has been applied.** In the original a 补课
     *    for a course already in the timetable `continue`d without being removed, so its loop re-ran
     *    and added the same session a second time; the loop only stopped because a "no progress" guard
     *    had been added to stop it spinning. Since every other branch *did* remove its entry, that
     *    guard was doing nothing else — here the loop is a single pass and cannot fail to terminate.
     *    A duplicated session is visible: the two copies merge into one grid block, and the block then
     *    claims "还有 1 个日程" about a class the student has once.
     *  - **The class a made-up arrangement points at is taken from the matched arrangement**, not from
     *    a value that is sometimes a list position and sometimes a detail index. The original reused
     *    one local variable for both, and compared it against a list position to decide whether it had
     *    cleared any week — a comparison that is only meaningful by accident. The decision it made is
     *    kept exactly ("nothing was cleared, so remember this adjustment as pending"), it is just made
     *    from a boolean now.
     *
     * The pending list itself is the original's `cache`, and it is what makes a 调课 that the registrar
     * has issued *both ways* — A moved to B, and B moved to A — cancel out instead of producing two
     * arrangements where there is one class.
     */
    internal fun mergeChanges(data: ClassTableData): ClassTableData {
        if (data.classChanges.isEmpty()) return data

        val classDetail = data.classDetail.toMutableList()
        val timeArrangement = data.timeArrangement.toMutableList()
        var semesterLength = data.semesterLength
        val pending = mutableListOf<ClassChange>()

        for (change in data.classChanges) {
            val detailIndices = classDetail.indices.filter { classDetail[it].code == change.classCode }

            if (detailIndices.isEmpty()) {
                // A course the timetable never mentioned. Only a make-up session can conjure one into
                // existence; a cancellation or a move of a course nobody is enrolled in is dropped.
                if (change.type == ChangeType.Patch) {
                    appLog.i {
                        "[TimetableParser] ${change.className} (${change.classCode}) is not in the " +
                                "timetable; adding it for its make-up session"
                    }
                    classDetail.add(
                        ClassDetail(
                            name = change.className,
                            code = change.classCode,
                            number = change.classNumber,
                        ),
                    )
                    semesterLength = addArrangement(
                        timeArrangement = timeArrangement,
                        index = classDetail.size - 1,
                        change = change,
                        semesterLength = semesterLength,
                    )
                } else {
                    appLog.w {
                        "[TimetableParser] ${change.className} (${change.classCode}) is not in the " +
                                "timetable; dropping its ${change.changeTypeText} entry"
                    }
                }
                continue
            }

            if (change.type == ChangeType.Patch) {
                semesterLength = addArrangement(
                    timeArrangement = timeArrangement,
                    index = detailIndices.first(),
                    change = change,
                    semesterLength = semesterLength,
                )
                continue
            }

            // Every arrangement of this course that sits exactly where the adjustment says it did.
            val matching = timeArrangement.indices.filter { i ->
                timeArrangement[i].index in detailIndices &&
                        timeArrangement[i].day == change.originalWeek &&
                        timeArrangement[i].start == change.originalClassRange.getOrNull(0) &&
                        timeArrangement[i].stop == change.originalClassRange.getOrNull(1)
            }
            if (matching.isEmpty()) {
                appLog.w {
                    "[TimetableParser] No arrangement matches ${change.className}'s " +
                            "${change.changeTypeText}: day=${change.originalWeek}, " +
                            "periods=${change.originalClassRange}"
                }
                continue
            }

            val detailIndex = timeArrangement[matching.first()].index
            var clearedAnyWeek = false
            for (i in matching) {
                val cleared = clearWeeks(
                    weekList = timeArrangement[i].weekList,
                    weeks = change.originalAffectedWeekIndices,
                )
                timeArrangement[i] = timeArrangement[i].copy(weekList = cleared.weekList)
                clearedAnyWeek = clearedAnyWeek || cleared.anyWeekWasSet
                if (cleared.grewTo != null && cleared.grewTo > semesterLength) {
                    semesterLength = cleared.grewTo
                }
            }

            if (!clearedAnyWeek) pending.add(change)

            val mirror = pending.firstOrNull { it.isMirrorOf(change) }
            if (mirror != null) {
                pending.remove(mirror)
                continue
            }

            if (change.type == ChangeType.Change) {
                semesterLength = addArrangement(
                    timeArrangement = timeArrangement,
                    index = detailIndex,
                    change = change,
                    semesterLength = semesterLength,
                )
            }
        }

        return data.copy(
            semesterLength = semesterLength,
            classDetail = classDetail,
            timeArrangement = timeArrangement,
        )
    }

    /**
     * Appends the arrangement a 调课 or 补课 describes, and answers the possibly-grown semester length.
     *
     * The teacher is the new one only when it actually changed: the registrar sends both spellings on
     * every row, and preferring the new one unconditionally would relabel a class's teacher with a
     * rendering of the same person.
     */
    private fun addArrangement(
        timeArrangement: MutableList<TimeArrangement>,
        index: Int,
        change: ClassChange,
        semesterLength: Int,
    ): Int {
        val weekList = change.newAffectedWeeks ?: change.originalAffectedWeeks ?: emptyList()
        timeArrangement.add(
            TimeArrangement(
                source = ClassSource.School,
                index = index,
                weekList = weekList,
                day = change.newWeek ?: change.originalWeek ?: 0,
                start = change.newClassRange.getOrElse(0) { -1 },
                stop = change.newClassRange.getOrElse(1) { -1 },
                classroom = change.newClassroom ?: change.originalClassroom,
                teacher = if (change.isTeacherChanged) change.newTeacher else change.originalTeacher,
            ),
        )
        return maxOf(semesterLength, weekList.size)
    }

    /**
     * Turns the listed weeks off, growing the list when the adjustment names a week past its end.
     *
     * A short week list is normal: a class that stops in week 12 has twelve flags while the semester
     * runs sixteen, and an adjustment that affects week 15 has to extend it before it can be cleared.
     */
    private fun clearWeeks(weekList: List<Boolean>, weeks: List<Int>): WeekClear {
        var list = weekList
        var grewTo: Int? = null
        var anyWeekWasSet = false
        for (week in weeks) {
            if (week < 0) continue
            if (week >= list.size) {
                list = list + List(week + 1 - list.size) { false }
                grewTo = list.size
            }
            if (list[week]) {
                list = list.toMutableList().also { it[week] = false }
                anyWeekWasSet = true
            }
        }
        return WeekClear(weekList = list, grewTo = grewTo, anyWeekWasSet = anyWeekWasSet)
    }

    /**
     * The result of clearing an adjustment's weeks.
     *
     * [grewTo] is null unless the list had to be extended — the caller widens the semester only when
     * it actually grew, which is the original's own condition. [anyWeekWasSet] is kept apart from
     * "the list changed", because extending a list past its end with `false` entries changes it
     * without clearing anything, and the two cases lead to different branches.
     */
    private data class WeekClear(
        val weekList: List<Boolean>,
        val grewTo: Int?,
        val anyWeekWasSet: Boolean,
    )

    /**
     * Whether [other] is the return trip of this adjustment.
     *
     * True when the two describe the same course moving between the same two slots in opposite
     * directions — the original's own field-by-field test, kept as it stands.
     */
    private fun ClassChange.isMirrorOf(other: ClassChange): Boolean =
        className == other.className &&
                classCode == other.classCode &&
                originalClassRange == other.newClassRange &&
                originalAffectedWeekIndices == other.newAffectedWeekIndices &&
                originalWeek == other.newWeek &&
                originalClassroom == other.newClassroom &&
                originalTeacherData == other.newTeacherData
}
