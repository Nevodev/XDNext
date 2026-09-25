package com.nevoit.xdnext.ui

import kotlinx.serialization.Serializable

/**
 * Every page this app can show, as a value.
 *
 * The navigator addresses pages by this type and never by a route string, so a page cannot be reached
 * with an argument it does not expect and a typo is a compile error rather than an empty screen. It is
 * `@Serializable` because the back stack is saved by encoding each destination — see
 * `rememberNavigator` — and every variant is a `data object` or a `data class` so the encoding is
 * stable and comparable.
 *
 * [Home] is the root and therefore the only page that cannot be closed: the shell is what everything
 * else is opened from.
 *
 * There is deliberately no login page here. The login screen is a **gate**, not a page: whether it or
 * the shell composes is decided from what is on disk before the first frame, and signing in swaps the
 * two rather than navigating between them — see `App`. A `Login` destination would make the login
 * screen something to navigate *away* from, which is the one behaviour it must not have: on a warm
 * start the shell would be drawn first and the login page pushed over it, or the reverse.
 */
@Serializable
sealed interface AppDestination {

    /** The home shell: the bottom bar and its three tabs. The root of the stack. */
    @Serializable
    data object Home : AppDestination

    /** 课程表 — the timetable. */
    @Serializable
    data object Timetable : AppDestination

    /** 没有时间安排的科目 — the timetable's courses with no place in the week. */
    @Serializable
    data object NotArrangedClasses : AppDestination

    /** 课程调整 — the registrar's moves, cancellations and make-up sessions. */
    @Serializable
    data object ClassChanges : AppDestination
}
