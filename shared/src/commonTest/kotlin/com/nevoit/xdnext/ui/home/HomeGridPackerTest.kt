package com.nevoit.xdnext.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for the home-screen packing rule.
 *
 * This is the whole reason the campus page does not use `LazyVerticalGrid`: a tile two cells wide *and*
 * two tall cannot be expressed there, so where each tile lands is computed here instead. The layout is
 * the kind of thing that looks right in one screenshot and wrong on the next device, so the positions
 * are asserted rather than eyeballed.
 */
class HomeGridPackerTest {

    @Test
    fun laysOutTheCampusPage() {
        // The same shape the page itself uses, spelled out rather than read from the registry: this test
        // is about the packing rule, and `HomeTileRegistryTest` is the one that pins the page's order.
        val spans = listOf(
            TileSpan(columns = 4, rows = 2),
            TileSpan(columns = 2, rows = 2),
            TileSpan(columns = 2, rows = 2),
            TileSpan(columns = 2, rows = 2),
        ) + List(8) { TileSpan() }

        assertEquals(
            // The banner owns rows 0-1, the three widgets stack down the left in rows 2-5, and the
            // shortcuts fill the free half beside the third widget before starting the last row.
            "0,0 4x2 | 0,2 2x2 | 2,2 2x2 | 0,4 2x2 | " +
                    "2,4 1x1 | 3,4 1x1 | 2,5 1x1 | 3,5 1x1 | 0,6 1x1 | 1,6 1x1 | 2,6 1x1 | 3,6 1x1",
            packTiles(spans).render(),
        )
    }

    @Test
    fun leavesTheBannerFullWidth() {
        val placements = packTiles(listOf(TileSpan(columns = 4, rows = 2), TileSpan()))

        assertEquals(TilePlacement(0, 0, TileSpan(4, 2)), placements.first())
        // The shortcut cannot sit beside a full-width banner, so it starts the row below it.
        assertEquals(TilePlacement(0, 2, TileSpan()), placements.last())
    }

    @Test
    fun fillsTheGapBesideASquareWidget() {
        val placements = packTiles(listOf(TileSpan(2, 2), TileSpan(), TileSpan()))

        assertEquals(TilePlacement(0, 0, TileSpan(2, 2)), placements[0])
        assertEquals(TilePlacement(2, 0, TileSpan()), placements[1], "Beside it, not below it.")
        assertEquals(TilePlacement(3, 0, TileSpan()), placements[2])
    }

    @Test
    fun putsTheThirdWidgetOnItsOwnRow() {
        // Three squares do not tile a four-column grid: the third starts a new row, and the shortcuts
        // that follow it take the cells to its right.
        assertEquals(
            "0,0 2x2 | 2,0 2x2 | 0,2 2x2 | 2,2 1x1",
            packTiles(listOf(TileSpan(2, 2), TileSpan(2, 2), TileSpan(2, 2), TileSpan())).render(),
        )
    }

    @Test
    fun neverOverlapsAndNeverOverflowstheGrid() {
        val spans = listOf(TileSpan(4, 2), TileSpan(2, 2), TileSpan(2, 2), TileSpan(2, 2)) +
                List(8) { TileSpan() }
        val placements = packTiles(spans)

        val cells = mutableSetOf<Long>()
        placements.forEach { placement ->
            for (row in placement.row until placement.row + placement.span.rows) {
                for (column in placement.column until placement.column + placement.span.columns) {
                    assertTrue(column in 0 until HomeGridColumns, "column $column is off the grid")
                    assertTrue(
                        cells.add(row * HomeGridColumns.toLong() + column),
                        "two tiles share a cell"
                    )
                }
            }
        }
        assertEquals(
            spans.sumOf { it.columns * it.rows },
            cells.size,
            "every tile takes its own room"
        )
        assertEquals(placements.size, spans.size, "no tile was dropped")
    }

    @Test
    fun packsARaggedSequenceWithoutLeavingHolesBehindIt() {
        // A one-cell tile before a wide one must be stepped over rather than blocking it.
        assertEquals(
            "0,0 1x1 | 1,0 3x1 | 0,1 1x1",
            packTiles(listOf(TileSpan(), TileSpan(3, 1), TileSpan())).render(),
        )
    }

    @Test
    fun handlesAnEmptyPage() {
        assertEquals(emptyList(), packTiles(emptyList()))
    }

    @Test
    fun rejectsATileWiderThanTheGrid() {
        // A five-wide tile in a four-column grid has nowhere to go, and laying it out "somewhere" would
        // put it off the right edge.
        assertFailsWith<IllegalArgumentException> { packTiles(listOf(TileSpan(columns = 5))) }
        assertFailsWith<IllegalArgumentException> { packTiles(emptyList(), columns = 0) }
    }

    @Test
    fun rejectsATileWithNoHeight() {
        assertFailsWith<IllegalArgumentException> { packTiles(listOf(TileSpan(rows = 0))) }
    }
}

/** `column,row WxH` per tile, so a failure reads as the layout rather than as a list index. */
private fun List<TilePlacement>.render(): String =
    joinToString(" | ") { "${it.column},${it.row} ${it.span.columns}x${it.span.rows}" }
