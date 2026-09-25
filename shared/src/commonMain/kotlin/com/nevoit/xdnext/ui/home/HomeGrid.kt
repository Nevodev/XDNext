package com.nevoit.xdnext.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How wide the campus page's grid is, in cells. */
const val HomeGridColumns = 4

/** The gutter between two cells, and between two tiles. */
val HomeGridSpacing = 12.dp

/** A tile's footprint, in grid cells. Nothing here is square: a widget is two cells by two. */
data class TileSpan(val columns: Int = 1, val rows: Int = 1)

/** Where a tile ended up, and how much room it takes there. */
data class TilePlacement(val column: Int, val row: Int, val span: TileSpan)

/**
 * Lays tiles out in a fixed-width grid, top-left first free cell wins.
 *
 * This is the packing rule a phone's home screen uses, and it is the reason the page does not use
 * `LazyVerticalGrid`: that grid spans items *horizontally* only, so a two-by-two widget cannot sit in
 * it without leaving a hole, and it stretches whatever shares its row to the widget's height. A widget
 * here is two cells wide **and** two tall, and the small tiles slot into the space beside it.
 *
 * The scan for a free spot always restarts at the first row, so the output depends only on the spans —
 * a tile added later cannot displace an earlier one, and the result is stable across recompositions.
 * Tiles are collected in the order they are given; a tile wider than the grid, or with no rows, is a
 * programming error rather than something to lay out badly.
 */
fun packTiles(spans: List<TileSpan>, columns: Int = HomeGridColumns): List<TilePlacement> {
    require(columns > 0) { "the grid needs at least one column but was given $columns" }
    if (spans.isEmpty()) return emptyList()

    val occupied = HashSet<Long>()
    val placements = ArrayList<TilePlacement>(spans.size)
    var rowsUsed = 0

    for (span in spans) {
        require(span.columns in 1..columns) {
            "a tile ${span.columns} columns wide does not fit a $columns-column grid"
        }
        require(span.rows >= 1) { "a tile must be at least one row tall but was ${span.rows}" }

        var row = 0
        var column = -1
        while (column < 0) {
            for (candidate in 0..(columns - span.columns)) {
                if (isFree(occupied, candidate, row, span, columns)) {
                    column = candidate
                    break
                }
            }
            if (column < 0) row++
        }

        occupy(occupied, column, row, span, columns)
        placements += TilePlacement(column = column, row = row, span = span)
        rowsUsed = maxOf(rowsUsed, row + span.rows)
    }

    return placements
}

private fun isFree(
    occupied: Set<Long>,
    column: Int,
    row: Int,
    span: TileSpan,
    columns: Int,
): Boolean {
    for (r in row until row + span.rows) {
        for (c in column until column + span.columns) {
            if (cellKey(c, r, columns) in occupied) return false
        }
    }
    return true
}

private fun occupy(
    occupied: MutableSet<Long>,
    column: Int,
    row: Int,
    span: TileSpan,
    columns: Int,
) {
    for (r in row until row + span.rows) {
        for (c in column until column + span.columns) {
            occupied += cellKey(c, r, columns)
        }
    }
}

private fun cellKey(column: Int, row: Int, columns: Int): Long = row.toLong() * columns + column

@Composable
fun HomeGrid(
    spans: List<TileSpan>,
    modifier: Modifier = Modifier,
    columns: Int = HomeGridColumns,
    spacing: Dp = HomeGridSpacing,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == spans.size) {
            "${measurables.size} tiles but ${spans.size} spans: every child needs exactly one span"
        }

        val gap = spacing.roundToPx()
        val cell = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val placements = packTiles(spans, columns)

        val placeables = measurables.mapIndexed { index, measurable ->
            val span = placements[index].span
            measurable.measure(
                Constraints.fixed(
                    width = cell * span.columns + gap * (span.columns - 1),
                    height = cell * span.rows + gap * (span.rows - 1),
                ),
            )
        }

        val rows = placements.maxOfOrNull { it.row + it.span.rows } ?: 0
        val height = if (rows == 0) 0 else rows * cell + (rows - 1) * gap

        layout(constraints.maxWidth, height) {
            placements.forEachIndexed { index, placement ->
                placeables[index].place(
                    x = placement.column * (cell + gap),
                    y = placement.row * (cell + gap),
                )
            }
        }
    }
}
