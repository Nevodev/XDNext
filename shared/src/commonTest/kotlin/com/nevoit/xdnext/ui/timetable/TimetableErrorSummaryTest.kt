package com.nevoit.xdnext.ui.timetable

import com.nevoit.xdnext.data.timetable.ClassTableData
import com.nevoit.xdnext.data.timetable.TimetableCacheHint
import com.nevoit.xdnext.data.timetable.TimetableSource
import com.nevoit.xdnext.data.timetable.TimetableState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the error summary the title bar's error button opens.
 *
 * The dialog is the only place a user can find out *why* the timetable on screen is not today's, so its
 * shape is worth pinning: the sources that failed outright, then — separated by a blank line — the ones
 * being served from a cache and the reason. A failure with no cache has no reason to show, which is why
 * its line is always the generic network sentence; that is the original's behaviour rather than an
 * omission here.
 */
class TimetableErrorSummaryTest {

    @Test
    fun saysNothingWhenNothingHasFailed() {
        assertEquals("", loadErrorSummary(TimetableState(isLoading = false)))
    }

    @Test
    fun namesASourceThatFailedWithNothingToFallBackOn() {
        val summary = loadErrorSummary(
            TimetableState(isLoading = false, error = RuntimeException("off campus")),
        ).trimEnd()

        assertEquals(
            "以下信息加载失败：课表\n课表: ${TimetableStrings.NETWORK_ERROR}",
            summary,
        )
    }

    @Test
    fun namesASourceThatIsBeingServedFromACacheAndWhy() {
        val summary = loadErrorSummary(
            TimetableState(
                isLoading = false,
                data = ClassTableData(semesterCode = "2025-2026-1"),
                isFromCache = true,
                cacheHint = TimetableCacheHint.LOGIN_FAILED,
            ),
        ).trimEnd()

        assertEquals(
            "当前使用缓存：课表\n课表: ${TimetableCacheHint.LOGIN_FAILED.message}",
            summary,
        )
    }

    @Test
    fun aCachedSourceWithNoReasonIsNotAnErrorAtAll() {
        // The reason is the *session's* classification of the failure. A cache with no reason is a cache
        // that was read successfully and simply is not a fresh fetch — it is not a failure, so the title
        // bar shows no error button and the dialog has nothing to say. The banner still names it as a
        // cache, which is the whole point of the two being separate notices.
        val state = TimetableState(
            isLoading = false,
            data = ClassTableData(semesterCode = "2025-2026-1"),
            isFromCache = true,
            cacheHint = null,
        )

        assertEquals("", loadErrorSummary(state))
        assertEquals(listOf(TimetableSource.ClassTable), state.cacheSources)
    }
}
