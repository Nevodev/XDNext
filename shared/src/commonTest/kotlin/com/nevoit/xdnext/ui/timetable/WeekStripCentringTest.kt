package com.nevoit.xdnext.ui.timetable

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekStripCentringTest {
    private val phone = WeekStripGeometry(viewportWidth = 360, itemWidth = 72, edgePadding = 12)

    @Test
    fun startAlignmentKeepsTheVisibleTwelvePixelInset() {
        assertEquals(132, phone.scrollOffset(WeekStripAlignment.Start))
        assertEquals(12, screenStart(phone, WeekStripAlignment.Start))
    }

    @Test
    fun centreUsesTheScreenNotThePaddedContentArea() {
        assertEquals(144, phone.beforeContentPadding)
        assertEquals(276, phone.afterContentPadding)
        assertEquals(0, phone.scrollOffset(WeekStripAlignment.Center))
        assertEquals(180, screenStart(phone, WeekStripAlignment.Center) + phone.itemWidth / 2)
    }

    @Test
    fun centringIsAnAbsoluteAnchorNotAScrollDelta() {
        // Current item offset must not be passed to animateScrollToItem as its absolute anchor.
        // Starting at either side, in the leading padding, or off screen must give the same result.
        for (currentScreenStart in listOf(-400, -20, 12, 144, 264, 348, 800)) {
            val desired = screenStart(phone, WeekStripAlignment.Center)
            val delta = currentScreenStart - desired
            assertEquals(180, currentScreenStart - delta + phone.itemWidth / 2)
        }
    }

    @Test
    fun allWeeksCanReachBothAnchorsIncludingTheFirstAndLast() {
        for (geometry in listOf(phone, WeekStripGeometry(1080, 216, 36), WeekStripGeometry(801, 145, 24))) {
            for (count in listOf(1, 2, 19, 30)) {
                val gap = geometry.edgePadding
                val step = geometry.itemWidth + gap
                val contentSize = geometry.beforeContentPadding + count * geometry.itemWidth +
                    (count - 1) * gap + geometry.afterContentPadding
                val maxScroll = (contentSize - geometry.viewportWidth).coerceAtLeast(0)

                for (week in 0 until count) {
                    for (alignment in WeekStripAlignment.entries) {
                        val requestedScroll = week * step + geometry.scrollOffset(alignment)
                        val actualScroll = requestedScroll.coerceIn(0, maxScroll)
                        assertEquals(
                            requestedScroll, actualScroll,
                            "Week $week/$count, $alignment, $geometry must not hit a scroll boundary",
                        )
                        val actualScreenStart = geometry.beforeContentPadding + week * step - actualScroll
                        assertEquals(screenStart(geometry, alignment), actualScreenStart)
                        assertTrue(actualScreenStart >= 0)
                        assertTrue(actualScreenStart + geometry.itemWidth <= geometry.viewportWidth)
                    }
                }
            }
        }
    }

    @Test
    fun centringRoundsWithinHalfAPixelAtOddWidths() {
        for (width in listOf(359, 360, 361, 801)) {
            for (itemWidth in listOf(71, 72, 73)) {
                val geometry = WeekStripGeometry(width, itemWidth, 12)
                val middle = screenStart(geometry, WeekStripAlignment.Center) + itemWidth / 2.0
                assertTrue(abs(middle - width / 2.0) <= 0.5)
            }
        }
    }

    @Test
    fun densityAndWindowSizeChangesRecalculateBothAnchors() {
        val dense = WeekStripGeometry(1080, 216, 36)
        assertEquals(36, screenStart(dense, WeekStripAlignment.Start))
        assertEquals(540, screenStart(dense, WeekStripAlignment.Center) + 108)

        val wide = phone.copy(viewportWidth = 840)
        assertEquals(12, screenStart(wide, WeekStripAlignment.Start))
        assertEquals(420, screenStart(wide, WeekStripAlignment.Center) + 36)
    }

    @Test
    fun manualFlingSnapsToTheSameCentreDespiteAsymmetricPadding() {
        for (geometry in listOf(phone, WeekStripGeometry(801, 145, 24))) {
            val itemOffset = WeekStripCenterSnapPosition.position(
                layoutSize = geometry.viewportWidth,
                itemSize = geometry.itemWidth,
                beforeContentPadding = geometry.beforeContentPadding,
                afterContentPadding = geometry.afterContentPadding,
                itemIndex = 7,
                itemCount = 19,
            )
            assertEquals(-geometry.scrollOffset(WeekStripAlignment.Center), itemOffset)
            val screenMiddle = geometry.beforeContentPadding + itemOffset + geometry.itemWidth / 2.0
            assertTrue(abs(screenMiddle - geometry.viewportWidth / 2.0) <= 0.5)
        }
    }

    private fun screenStart(geometry: WeekStripGeometry, alignment: WeekStripAlignment): Int =
        geometry.beforeContentPadding - geometry.scrollOffset(alignment)
}
