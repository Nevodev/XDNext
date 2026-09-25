package com.nevoit.xdnext.data.timetable

/**
 * The registrar's semester codes, e.g. `2025-2026-1`.
 *
 * Ported from `lib/repository/preference.dart`'s `parseSemesterCodeToInt` and
 * `lib/controller/semester_controller.dart`'s `_latestSemester`. The code is the year range followed
 * by the term, and the original ordered two codes by gluing the first four characters onto the last
 * one — `2025-2026-1` → `20251` — which sorts by academic year and then by term.
 *
 * The original threw on a code shorter than five characters or without a leading number. Nothing here
 * does: a code arrives from the network or from a preference file, and neither is a place to raise.
 * [order] answers `-1` for anything it cannot read, which is the same thing the original's own
 * `_semesterOrder` did for the empty string, so a malformed local code still loses to a valid remote
 * one instead of replacing it.
 */
object SemesterCode {

    /** How a code sorts against another; higher is newer. `-1` when [code] says nothing. */
    fun order(code: String): Int {
        if (code.length < 5) return -1
        return (code.take(4) + code.last()).toIntOrNull() ?: -1
    }

    /** The newer of two codes. A tie keeps [a], as the original's `>=` did. */
    fun latest(a: String, b: String): String = if (order(a) >= order(b)) a else b

    /**
     * The `XN` and `XQ` parameters `cxjcs` is asked with: `2025-2026-1` → `2025-2026` and `1`.
     *
     * Null when the code has no three parts, which the caller reports as a protocol failure rather
     * than crashing on the split. The original indexed straight into `split('-')`.
     */
    fun academicYearAndTerm(code: String): Pair<String, String>? {
        val parts = code.split('-')
        if (parts.size < 3) return null
        return "${parts[0]}-${parts[1]}" to parts[2]
    }
}
