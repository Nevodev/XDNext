package com.nevoit.xdnext.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the tile registry.
 *
 * The registry is what a saved order will be written against, so the things that would silently rot are
 * worth pinning now: a tile added to the enum but not to the order, a tile with no renderer, and the
 * assumption that the order — and nothing else — decides the layout.
 */
class HomeTileRegistryTest {

    @Test
    fun theDefaultOrderCoversEveryTileExactlyOnce() {
        assertEquals(
            HomeTileId.entries.size,
            DefaultHomeOrder.size,
            "a tile is missing from the default order, or listed twice",
        )
        assertEquals(HomeTileId.entries.toSet(), DefaultHomeOrder.toSet())
    }

    @Test
    fun everySingleCellTileHasAShortcutSpec() {
        // The rule is the span, not a hand-kept list: whatever the page renders as a single cell must
        // have something to render it with.
        assertEquals(
            HomeTileId.entries.filter { it.span.columns == 1 }.toSet(),
            ShortcutSpecs.keys,
        )
        ShortcutSpecs.forEach { (id, spec) ->
            assertTrue(spec.symbol.isNotBlank(), "$id has no icon")
            assertTrue(spec.label.isNotBlank(), "$id has no label")
        }
    }

    @Test
    fun theDefaultOrderIsThePagesLayout() {
        // The banner owns rows 0-1, the three square widgets then go down in pairs (0,2 / 2,2 / 0,4), and
        // the shortcuts fill the free half beside the third widget before running along the bottom two
        // rows. Thirteen tiles: one banner, three widgets, nine shortcuts.
        assertEquals(
            "0,0 4x2 | 0,2 2x2 | 2,2 2x2 | 0,4 2x2 | " +
                    "2,4 1x1 | 3,4 1x1 | 2,5 1x1 | 3,5 1x1 | " +
                    "0,6 1x1 | 1,6 1x1 | 2,6 1x1 | 3,6 1x1 | 0,7 1x1",
            DefaultHomeOrder.map { it.span }.let(::packTiles).render(),
        )
    }

    @Test
    fun theLayoutFollowsTheOrder() {
        // The whole point of addressing tiles by id: moving one in the list moves it on the page, with no
        // other change. Sent to the end, the banner can only start once the widgets and the shortcuts
        // that used to sit under it have taken their own room.
        val bannerLast =
            DefaultHomeOrder.filter { it != HomeTileId.Timetable } + HomeTileId.Timetable
        val placements = packTiles(bannerLast.map { it.span })

        assertEquals(TilePlacement(0, 0, TileSpan(2, 2)), placements.first())
        assertEquals(
            HomeTileId.Energy.span,
            placements.first().span,
            "the first tile is now the widget"
        )
        assertEquals(
            TilePlacement(0, 6, TileSpan(4, 2)),
            placements.last(),
            "the other twelve tiles reach row 6, which is the first row the banner still fits in.",
        )
        assertEquals(HomeTileId.Timetable.span, placements.last().span)
    }
}

/** `column,row WxH` per tile, so a failure reads as the layout rather than as a list index. */
private fun List<TilePlacement>.render(): String =
    joinToString(" | ") { "${it.column},${it.row} ${it.span.columns}x${it.span.rows}" }
