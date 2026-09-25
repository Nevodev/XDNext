package com.nevoit.xdnext.core.concurrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the concurrent-call coalescer.
 *
 * Ported from `lib/repository/single_flight.dart`, where it exists so that a screen refreshing on every
 * recomposition produces one login and one query instead of one per frame. The two properties that
 * matter are that overlapping calls share an answer and that a *later* call does not — the original
 * deliberately did not cache the completed result.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent / advanceUntilIdle, to park and release callers
class SingleFlightTest {

    @Test
    fun overlappingCallsShareOneExecution() = runTest {
        val flight = SingleFlight<Int>()
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val results = mutableListOf<Int>()

        val callers = List(3) {
            launch { results += flight.run { runs++; gate.await(); 7 } }
        }
        runCurrent() // Let all three reach the shared operation before releasing it.
        assertTrue(flight.isRunning)

        gate.complete(Unit)
        callers.forEach { it.join() }

        assertEquals(1, runs, "Three overlapping callers must produce one execution.")
        assertEquals(listOf(7, 7, 7), results)
        assertFalse(flight.isRunning)
    }

    @Test
    fun aLaterCallStartsANewOperation() = runTest {
        val flight = SingleFlight<Int>()
        var runs = 0

        assertEquals(1, flight.run { ++runs })
        assertEquals(2, flight.run { ++runs }, "The completed result is not reused.")
    }

    @Test
    fun callersShareTheFailure() = runTest {
        val flight = SingleFlight<Int>()
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val failures = mutableListOf<Throwable>()

        val callers = List(2) {
            launch {
                try {
                    flight.run {
                        runs++
                        gate.await()
                        error("boom")
                    }
                } catch (error: Throwable) {
                    failures += error
                }
            }
        }
        runCurrent()
        gate.complete(Unit)
        callers.forEach { it.join() }

        assertEquals(1, runs)
        assertEquals(
            2,
            failures.size,
            "A joiner sees the failure too, as awaiting the shared Future did."
        )
        assertTrue(failures.all { it.message == "boom" })
        assertFalse(flight.isRunning)
    }

    @Test
    fun aFailureDoesNotWedgeTheSlot() = runTest {
        val flight = SingleFlight<Int>()

        runCatching { flight.run { error("boom") } }
        advanceUntilIdle()

        assertFalse(
            flight.isRunning,
            "The next call must be able to start, not await a dead operation."
        )
        assertEquals(1, flight.run { 1 })
    }
}
