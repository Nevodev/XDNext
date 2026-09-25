package com.nevoit.xdnext.core.startup

import kotlin.time.TimeSource

/**
 * When this launch began, so that every startup log line can be read against the same zero.
 *
 * Startup is spread across three places that cannot see each other — `XdNextApplication.onCreate`, the
 * background warm-up it starts, and the first composition — and "the first compose takes a long time"
 * is not actionable until the log says which of them the time is in. Each phase therefore reports the
 * time *it* finished, and the numbers add up rather than overlap.
 *
 * The mark is taken when this object is first touched, which is the first thing the Application does;
 * from there the clock is [TimeSource.Monotonic], because a measurement of startup must not move when
 * the device's wall clock is corrected underneath it.
 */
object StartupClock {

    private val start = TimeSource.Monotonic.markNow()

    /** Milliseconds since this launch began. */
    fun elapsedMs(): Long = start.elapsedNow().inWholeMilliseconds
}
