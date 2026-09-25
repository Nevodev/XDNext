package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.timetable.ChangeType
import com.nevoit.xdnext.data.timetable.ClassChange
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.ui.home.ListRow
import com.nevoit.xdnext.ui.shared.AppTopBar
import org.koin.compose.koinInject

/**
 * 课程调整 — the moves, cancellations and make-up sessions the registrar issued.
 *
 * Ported from `class_change_list.dart`. The timetable itself already *contains* these: the parser folds
 * them into the week grid before anything is drawn. This page is the audit trail — a student who
 * wonders why Thursday's class is missing finds the 停课 entry here rather than inferring it.
 *
 * Every sentence is assembled the way the original assembled it, including the two oddities:
 *
 *  - the weeks are printed **one-based** (`index + 1`) and comma-separated, and an adjustment that
 *    affects no week prints nothing at all rather than "第 周";
 *  - the weekday is a single character — 一 through 日 — and an adjustment with no day prints an empty
 *    one, which is how a 补课 the registrar left undated reads;
 *  - the teacher line is only shown for a 调课, since a cancellation has no new teacher to compare with
 *    and a make-up session's is not in question.
 */
@Composable
fun ClassChangeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: TimetableRepository = koinInject()
    val state by repository.state.collectAsState()
    val changes = state.data?.classChanges.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.pageBackground),
    ) {
        AppTopBar(
            title = TimetableStrings.CLASS_CHANGE_TITLE,
            onBack = onBack,
        )

        if (changes.isEmpty()) {
            EmptyPageNotice(TimetableStrings.CLASS_CHANGE_EMPTY)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
                    changes.forEach { change ->
                        ListRow(
                            title = change.className,
                            description = TimetableStrings.classChangeInfo(
                                classCode = change.classCode,
                                classNumber = change.classNumber,
                                classChange = describe(change),
                                teacherChange = if (change.type == ChangeType.Change) {
                                    "\n" + teacherChangeOf(change)
                                } else {
                                    ""
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sentence describing where the class moved from and to.
 *
 * Every space is removed from the result, which is the original's own last step before drawing it:
 * `classChange.replaceAll(" ", '')`. Its translation values are written with spaces where a Chinese
 * sentence would not have them — they came from a YAML entry folded across lines — so the page took
 * them out. The spaces are kept in [TimetableStrings] so those values still line up with the
 * original's, and removed here, which is where the original removed them.
 */
internal fun describe(change: ClassChange): String {
    val originalWeeks = change.originalAffectedWeekIndices.joinToString(", ") { "${it + 1}" }
    val newWeeks = change.newAffectedWeekIndices.joinToString(", ") { "${it + 1}" }

    val sentence = when (change.type) {
        ChangeType.Change -> TimetableStrings.changeClassMessage(
            originalWeeks = originalWeeks,
            originalWeekChar = TimetableStrings.weekChar(change.originalWeek),
            originalStart = change.originalClassRange.getOrElse(0) { -1 }.toString(),
            originalEnd = change.originalClassRange.getOrElse(1) { -1 }.toString(),
            newWeeks = newWeeks,
            newWeekChar = TimetableStrings.weekChar(change.newWeek),
            newStart = change.newClassRange.getOrElse(0) { -1 }.toString(),
            newStop = change.newClassRange.getOrElse(1) { -1 }.toString(),
            newClassroom = (change.newClassroom ?: change.originalClassroom).orEmpty(),
        )

        ChangeType.Patch -> TimetableStrings.patchClassMessage(
            newWeeks = newWeeks,
            newWeekChar = TimetableStrings.weekChar(change.newWeek),
            newStart = change.newClassRange.getOrElse(0) { -1 }.toString(),
            newStop = change.newClassRange.getOrElse(1) { -1 }.toString(),
            newClassroom = change.newClassroom.orEmpty(),
        )

        ChangeType.Stop -> TimetableStrings.stopClassMessage(
            originalWeeks = originalWeeks,
            originalWeekChar = TimetableStrings.weekChar(change.originalWeek),
            originalStart = change.originalClassRange.getOrElse(0) { -1 }.toString(),
            originalEnd = change.originalClassRange.getOrElse(1) { -1 }.toString(),
        )
    }

    return sentence.replace(" ", "")
}

/**
 * The teacher line, which shows the *raw* new teacher data rather than the cleaned name.
 *
 * That is the original's own choice, and it is not an oversight that can be corrected here without
 * changing what the page says: the raw value carries the registrar's numbering, which is the only way
 * to tell one 张三 from another.
 */
private fun teacherChangeOf(change: ClassChange): String =
    if (change.isTeacherChanged && change.newTeacher != null) {
        TimetableStrings.teacherChange(
            previous = change.originalTeacher ?: TimetableStrings.NO_INFO,
            new = change.newTeacherData.orEmpty(),
        )
    } else {
        TimetableStrings.NO_TEACHER_CHANGE
    }
