package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.UnevenRoundedRectangle
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.modifier.thenIf
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.ui.home.rememberCardValueFontFamily
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun TimetableDateRow(
    weekStart: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                colors.cardBackground,
                UnevenRoundedRectangle(topStart = 12.dp, topEnd = 12.dp)
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(TimetableSpecs.LeftColumnWidth),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (weekStart.month.ordinal + 1).toString(),
                style = MaterialTheme.type.subHeadline.copy(fontFamily = rememberCardValueFontFamily())
            )
        }

        for (dayIndex in 1..7) {
            DayColumnHeader(
                modifier = Modifier.weight(1f),
                date = weekStart.plus(dayIndex - 1, DateTimeUnit.DAY),
                today = today
            )
        }
    }
}

@Composable
private fun DayColumnHeader(
    modifier: Modifier = Modifier,
    date: LocalDate,
    today: LocalDate
) {
    val colors = MaterialTheme.colors
    val isToday = date == today

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = TimetableStrings.weekday(date.dayOfWeek.ordinal + 1),
            style = MaterialTheme.type.footnote,
            color = colors.contentVariant
        )
        VGap(2.dp)
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(20.dp).thenIf(isToday) {
                    background(colors.secondaryContainer, Capsule())
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.type.footnote.copy(fontFamily = rememberCardValueFontFamily()),
                color = if (isToday) colors.onSecondaryContainer else colors.content,
            )
        }
    }
}
