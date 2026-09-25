package com.nevoit.xdnext.core.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.nevoit.material.navigation.Navigator
import com.nevoit.material.navigation.PageEntry
import com.nevoit.material.navigation.navigatorSaver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The navigator is a state machine with one interesting piece of encoding: the back stack is
 * serialised to primitives so that it survives `rememberSaveable` on every platform. That encoding is
 * the part the Compose Multiplatform port changed, so it is the part worth pinning — along with the
 * guards that decide a restored stack is not trustworthy.
 */
class NavigatorTest {

    @Serializable
    private sealed interface Route {
        @Serializable
        data object Home : Route

        @Serializable
        data object Settings : Route

        @Serializable
        data class Detail(val id: Long) : Route
    }

    private val routeSerializer: KSerializer<Route> = serializer<Route>()

    private fun navigator(vararg destinations: Route): Navigator<Route> =
        Navigator(destinations.first()).apply {
            destinations.drop(1).forEach(::open)
        }

    private fun save(navigator: Navigator<Route>): Any? {
        val saver = navigatorSaver(Route.Home, routeSerializer)
        return with(saver) { with(AlwaysSaveable) { save(navigator) } }
    }

    private fun restore(saved: Any?): Navigator<Route>? {
        val saver: Saver<Navigator<Route>, Any> = navigatorSaver(Route.Home, routeSerializer)
        return saved?.let(saver::restore)
    }

    @Test
    fun openPushesAndEveryEntryGetsItsOwnId() {
        val navigator = Navigator<Route>(Route.Home)

        val settings = navigator.open(Route.Settings)
        val detail = navigator.open(Route.Detail(7L))

        assertEquals(listOf(0L, 1L, 2L), navigator.backStack.map(PageEntry<Route>::id))
        assertEquals(Route.Detail(7L), navigator.current.destination)
        assertTrue(settings.id < detail.id)
    }

    @Test
    fun closeTopRefusesTheRootAndStaleEntries() {
        val navigator = Navigator<Route>(Route.Home)
        assertNull(navigator.closeTop(), "the root page cannot be closed")

        val settings = navigator.open(Route.Settings)
        navigator.open(Route.Detail(1L))
        // The host closes by entry id so that a page pushed during the exit animation is not the page
        // that gets removed.
        assertNull(navigator.closeTop(expectedEntryId = settings.id))
        assertEquals(3, navigator.backStack.size)

        assertEquals(Route.Detail(1L), navigator.closeTop()?.destination)
        assertEquals(Route.Settings, navigator.current.destination)
    }

    @Test
    fun theStackRoundTripsThroughTheSaver() {
        val navigator = navigator(Route.Home, Route.Settings, Route.Detail(42L))

        val restored = restore(save(navigator))

        assertNotNull(restored)
        assertEquals(
            navigator.backStack.map(PageEntry<Route>::destination),
            restored.backStack.map(PageEntry<Route>::destination),
        )
        assertEquals(
            navigator.backStack.map(PageEntry<Route>::id),
            restored.backStack.map(PageEntry<Route>::id)
        )
        // A page pushed after the restore must not reuse a restored id.
        assertEquals(3L, restored.open(Route.Settings).id)
    }

    @Test
    fun aStackWhoseRootIsNotTheExpectedOneIsRejected() {
        val navigator = navigator(Route.Home, Route.Settings)
        val saver: Saver<Navigator<Route>, Any> = navigatorSaver(Route.Settings, routeSerializer)

        val saved = with(saver) { with(AlwaysSaveable) { save(navigator) } }

        assertNull(saved?.let(saver::restore))
    }

    @Test
    fun aStackThatRepeatsTheRootIsRejected() {
        val navigator = navigator(Route.Home, Route.Settings, Route.Home)

        assertNull(restore(save(navigator)))
    }

    @Test
    fun unreadableSavedStateIsRejectedRatherThanThrowing() {
        assertNull(navigatorSaver(Route.Home, routeSerializer).restore(emptyList<Any>()))
        assertNull(navigatorSaver(Route.Home, routeSerializer).restore(listOf("nonsense")))
        assertNull(
            navigatorSaver(Route.Home, routeSerializer).restore(
                listOf(
                    5L,
                    0L,
                    "{not json}"
                )
            )
        )
    }
}

private object AlwaysSaveable : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}
