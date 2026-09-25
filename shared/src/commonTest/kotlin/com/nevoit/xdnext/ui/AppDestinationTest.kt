package com.nevoit.xdnext.ui

import androidx.compose.runtime.saveable.SaverScope
import com.nevoit.material.navigation.Navigator
import com.nevoit.material.navigation.navigatorSaver
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for the app's destination type.
 *
 * The routing itself is a `when` over this type, so what is worth pinning is what the navigator needs
 * *from* it: that every page encodes — the back stack survives a process death by encoding each
 * destination, and a destination that cannot be encoded takes the whole stack down with it — and that
 * the root is the shell, since a stack whose root is anything else cannot be restored.
 */
class AppDestinationTest {

    @Test
    fun everyPageRoundTripsThroughTheSavedStack() {
        // This is the property the navigator's `listSaver` depends on; a variant added without a
        // `@Serializable` annotation fails here rather than as a stack that silently resets to the root.
        val pages = listOf(
            AppDestination.Home,
            AppDestination.Timetable,
            AppDestination.Energy,
            AppDestination.WaterFee,
            AppDestination.SchoolCard,
            AppDestination.NotArrangedClasses,
            AppDestination.ClassChanges,
        )
        val navigator = Navigator<AppDestination>(AppDestination.Home)
        pages.drop(1).forEach(navigator::open)

        val saver = navigatorSaver(AppDestination.Home, jsonSerializer())
        val saved = with(saver) { with(AlwaysSaveable) { save(navigator) } }
        val restored = saved?.let(saver::restore)

        assertNotNull(restored, "The stack must survive being saved and restored.")
        assertEquals(pages, restored.backStack.map { it.destination })
    }

    @Test
    fun theRootIsTheShell() {
        // A restored stack is only accepted when its first entry is the root, so the shell has to be the
        // value the host remembers as the root — see `AppNavHost`.
        assertEquals(AppDestination.Home, Navigator(AppDestination.Home).current.destination)
    }

    @Test
    fun theShellCannotBeClosed() {
        // Every page is opened *over* the shell, and back must return to it rather than leaving the app.
        val navigator = Navigator<AppDestination>(AppDestination.Home)
        navigator.open(AppDestination.Timetable)

        assertNotNull(navigator.closeTop(), "The timetable closes back to the shell.")
        assertEquals(AppDestination.Home, navigator.current.destination)
        assertEquals(null, navigator.closeTop(), "The shell itself is the floor.")
    }

    @Test
    fun aDestinationSurvivesItsOwnEncoding() {
        // The encoding is JSON, so a page is compared by value after the trip — which is what lets the
        // host recognise a page it is already showing instead of pushing a duplicate.
        val encoded = Json.encodeToString(jsonSerializer(), AppDestination.Timetable)
        assertEquals(AppDestination.Timetable, Json.decodeFromString(jsonSerializer(), encoded))
    }

    private fun jsonSerializer() = serializer<AppDestination>()

    private object AlwaysSaveable : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
