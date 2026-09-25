package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.ui.home.ListRow
import com.nevoit.xdnext.ui.shared.AppTopBar
import org.koin.compose.koinInject

/**
 * 没有时间安排的科目 — the courses the registrar knows about but never placed in the week.
 *
 * Ported from `not_arranged_class_list.dart`. It reads the timetable repository rather than taking the
 * list as an argument, because it is a destination of its own: `AppDestination` carries no payload, so
 * a page is opened and reads what it needs. That also means the page is correct after a refresh that
 * happened while it was covered.
 *
 * A course here has a code and a class number, and so does one on the grid — but the teacher is the
 * only place it says who teaches it, which is why the line is worth opening at all.
 */
@Composable
fun NotArrangedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: TimetableRepository = koinInject()
    val state by repository.state.collectAsState()
    val courses = state.data?.notArranged.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.pageBackground),
    ) {
        AppTopBar(
            title = TimetableStrings.NOT_ARRANGED_TITLE,
            onBack = onBack,
        )

        if (courses.isEmpty()) {
            EmptyPageNotice(TimetableStrings.NOT_ARRANGED_EMPTY)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
                    courses.forEach { course ->
                        ListRow(
                            title = course.name,
                            description = TimetableStrings.notArrangedContent(
                                code = course.code.orEmpty(),
                                number = course.number.orEmpty(),
                                teacher = course.teacher ?: TimetableStrings.NO_INFO,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The centred sentence a list page shows when it has nothing to list. */
@Composable
internal fun EmptyPageNotice(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.type.body,
            color = MaterialTheme.colors.contentVariant,
            textAlign = TextAlign.Center,
        )
    }
}
