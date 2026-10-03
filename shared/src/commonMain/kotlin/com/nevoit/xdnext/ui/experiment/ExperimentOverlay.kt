package com.nevoit.xdnext.ui.experiment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nevoit.xdnext.data.experiment.ExperimentRepository
import com.nevoit.xdnext.data.experiment.experimentDayBlocks
import com.nevoit.xdnext.data.timetable.TimetableOverlay
import org.koin.compose.koinInject

/**
 * The experiment bookings, as an overlay the timetable's grid can draw.
 *
 * The overlay is a **snapshot** of the bookings the repository holds, and a new one is built whenever
 * they change. That is what makes the grid notice: `TimetableGrid` is remembered on the overlay list, so
 * an overlay that read the repository itself at draw time would leave the grid drawing the bookings it
 * saw when its page was composed. The timetable page is opened over the shell and is built fresh every
 * time, but it is *not* rebuilt when a background refresh lands.
 *
 * This lives in the experiment module rather than in the timetable's route, because how a booking
 * becomes a rectangle is the experiment module's business — `TimetableOverlay` is the seam, and this is
 * the adapter that fits this module into it.
 */
@Composable
fun rememberExperimentOverlays(): List<TimetableOverlay> {
    val repository: ExperimentRepository = koinInject()
    val state by repository.state.collectAsState()
    val entries = state.entries

    // One overlay, whose identity changes with the list. `TimetableOverlay` is a `fun interface`, so the
    // lambda is the implementation and the captures are the snapshot.
    return remember(entries) {
        listOf(TimetableOverlay { weekStart -> experimentDayBlocks(entries, weekStart) })
    }
}
