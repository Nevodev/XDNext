package com.nevoit.xdnext.ui.home

import androidx.compose.runtime.Immutable

/**
 * What a campus tile does when it is opened.
 *
 * The tiles are drawn by `FocusScreen` and the routes live in `AppNavHost`; this is the seam between
 * them. It exists so a tile can be opened without the page knowing a navigator exists — `FocusScreen`
 * takes this value and calls it, and nothing below the shell imports a destination.
 *
 * One value rather than one callback parameter per tile, because the list already grows with every
 * feature: a new tile adds a field here and one line where the shell builds it, instead of another
 * parameter threaded through `HomeScreen` and `FocusScreen` both.
 *
 * `@Immutable` because it is held in a `remember` and passed down: the callbacks are stable, so a
 * recomposition of the shell does not invalidate every tile.
 */
@Immutable
class TileActions(
    val openTimetable: () -> Unit,
)
