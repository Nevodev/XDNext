package com.nevoit.xdnext.core.startup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The warm-up's contract is short, and every part of it is invisible when it works: it builds every
 * step, one broken step does not stop the others, and it never makes its caller wait.
 *
 * The last one is the reason this class exists at all — a warm-up that blocked `Application.onCreate`
 * would be the very cost it is meant to remove, moved somewhere nobody is looking.
 *
 * Every test reads [AppWarmUp.start]'s own result rather than a list the steps append to: the steps run
 * on different threads, and a shared mutable collection between them is a race, not a test.
 */
class AppWarmUpTest {

    @Test
    fun buildsEveryStep() = runTest {
        val warmUp = AppWarmUp(
            listOf(
                AppWarmUp.Step("first") { },
                AppWarmUp.Step("second") { },
            ),
        )

        // The order is not guaranteed — the steps run concurrently — only that all of them ran.
        assertEquals(listOf("first", "second"), warmUp.start().await().sorted())
    }

    @Test
    fun oneBrokenStepDoesNotStopTheOthers() = runTest {
        val warmUp = AppWarmUp(
            listOf(
                AppWarmUp.Step("before") { },
                AppWarmUp.Step("broken") { error("an unreadable cache") },
                AppWarmUp.Step("after") { },
            ),
        )

        // The broken step is missing from the result rather than fatal to the two around it.
        assertEquals(listOf("after", "before"), warmUp.start().await().sorted())
    }

    @Test
    fun doesNotMakeItsCallerWaitForTheSteps() = runTest {
        // Parked until this test releases it, so the only way `start` can have returned is by not
        // waiting for its steps.
        val release = CompletableDeferred<Unit>()
        var ran = false
        val warmUp = AppWarmUp(
            listOf(
                AppWarmUp.Step("parked") {
                    ran = true
                    runBlocking { release.await() }
                },
            ),
        )

        val warm = warmUp.start()

        assertFalse(warm.isCompleted, "`start` waited for its steps.")

        release.complete(Unit)
        assertEquals(listOf("parked"), warm.await())
        assertTrue(ran, "The step never ran at all.")
    }
}
