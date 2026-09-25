package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.gestures.snapping.SnapPosition

internal enum class WeekStripAlignment { Start, Center }

// Deliberately not a data class: tapping the same week again must re-centre it after browsing the strip.
internal class WeekStripScrollRequest(
    val week: Int,
    val alignment: WeekStripAlignment = WeekStripAlignment.Start,
    val animated: Boolean = true,
) {
    fun followingPagerPage(page: Int): WeekStripScrollRequest =
        if (page == week) this else WeekStripScrollRequest(page)
}

/** All dimensions are pixels, in the same coordinate system as LazyRow's scroll offsets. */
internal data class WeekStripGeometry(
    val viewportWidth: Int,
    val itemWidth: Int,
    val edgePadding: Int,
) {
    private val centeredItemStart = (viewportWidth - itemWidth) / 2

    // Keep enough scroll range to centre even week 1 and to left-align even the final week.
    // The visible start-aligned inset remains edgePadding; the extra padding is scrollable space.
    val beforeContentPadding = maxOf(edgePadding, centeredItemStart)
    val afterContentPadding = maxOf(beforeContentPadding, viewportWidth - itemWidth - edgePadding)

    fun scrollOffset(alignment: WeekStripAlignment): Int {
        val desiredScreenStart = when (alignment) {
            WeekStripAlignment.Start -> edgePadding
            WeekStripAlignment.Center -> centeredItemStart
        }
        // scrollToItem's offset is an absolute anchor, NOT a delta from the item's current position.
        // Item screen start = beforeContentPadding - scrollOffset.
        return beforeContentPadding - desiredScreenStart
    }
}

/** Snap to the screen centre, not the centre of the asymmetrically padded content area. */
internal object WeekStripCenterSnapPosition : SnapPosition {
    override fun position(
        layoutSize: Int,
        itemSize: Int,
        beforeContentPadding: Int,
        afterContentPadding: Int,
        itemIndex: Int,
        itemCount: Int,
    ): Int = (layoutSize - itemSize) / 2 - beforeContentPadding
}
