package com.nevoit.xdnext.ui.timetable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WeekStripScrollRequestTest {
    @Test
    fun initialPagerEmissionDoesNotAnimateOrResetTheStrip() {
        val initial = WeekStripScrollRequest(8, animated = false)
        assertSame(initial, initial.followingPagerPage(8))
    }

    @Test
    fun pagerAcknowledgingATapDoesNotReplaceCentringWithStartAlignment() {
        val tap = WeekStripScrollRequest(12, WeekStripAlignment.Center)
        assertSame(tap, tap.followingPagerPage(12))
    }

    @Test
    fun swipingAfterATapResumesStartAlignmentImmediately() {
        val tap = WeekStripScrollRequest(12, WeekStripAlignment.Center)
        val swipe = tap.followingPagerPage(13)
        assertEquals(13, swipe.week)
        assertEquals(WeekStripAlignment.Start, swipe.alignment)
        assertTrue(swipe.animated)
    }

    @Test
    fun consecutiveSwipesAndDirectionReversalsAlwaysFollowTheLatestPage() {
        var request = WeekStripScrollRequest(0, animated = false)
        for (page in listOf(1, 2, 3, 8, 7, 6, 0, 18)) {
            request = request.followingPagerPage(page)
            assertEquals(page, request.week)
            assertEquals(WeekStripAlignment.Start, request.alignment)
            assertTrue(request.animated)
        }
    }

    @Test
    fun repeatedTapsAreNewRequestsSoABrowsedAwaySelectionCanCentreAgain() {
        val first = WeekStripScrollRequest(4, WeekStripAlignment.Center)
        val second = WeekStripScrollRequest(4, WeekStripAlignment.Center)
        assertNotEquals(first, second)
        assertSame(second, second.followingPagerPage(4))
    }
}
