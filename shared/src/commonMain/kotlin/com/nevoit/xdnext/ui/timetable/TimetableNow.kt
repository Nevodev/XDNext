package com.nevoit.xdnext.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The current local time, updated on the minute.
 *
 * The original read this from a `GlobalTimerController` singleton whose timer fired at the next whole
 * minute **plus 100 ms**, then rescheduled itself for the following one. The overshoot is deliberate:
 * firing exactly on the boundary races the clock and can read the previous minute back. Both the
 * alignment and the 100 ms are reproduced, because the current-time indicator's whole job is to sit
 * where the clock says it should, and a timer that drifts reads a minute stale.
 *
 * The page needs the time for three things that must agree: the indicator line, today's column, and
 * which classes count as over. One value, read once per minute, is what makes them agree.
 */
@Composable
fun rememberTimetableNow(): LocalDateTime {
    var now by remember { mutableStateOf(currentLocalTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            val current = currentLocalTime()
            now = current
            val intoTheMinute = current.second * 1_000L + current.nanosecond / 1_000_000L
            delay(60_000L - intoTheMinute + 100L)
        }
    }

    return now
}

private fun currentLocalTime(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
