package com.nevoit.xdnext.ui.home

import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.schoolcard.SchoolCardRepository
import com.nevoit.xdnext.data.timetable.TimetableRepository

/**
 * Every module backed by a cache, refreshed once per process.
 *
 * This is the list a new feature adds itself to, and the only place that decides *when* a module is
 * fetched. A tile or a page reads its repository and draws; it does not fetch. Keeping the two apart is
 * what makes "refresh everything once at start-up" a one-line idea rather than a hunt through the
 * screens.
 *
 * The name is the campus page's, where it started, but the rule is the whole shell's: the timetable
 * fetches its term the same way the electricity card fetches its meter, and for the same reasons. The
 * original loaded both at start-up too — its semester sync drove a class-table load before any screen
 * asked for one.
 *
 * ### Why the guard lives here and not in a composition
 *
 * The focus page is **unmounted** when the user switches tabs — `CrossfadeNavContainer` drops the
 * outgoing page once its fade finishes — and remounted on the way back. A `LaunchedEffect` inside it
 * therefore runs again on every visit, which is exactly what made the electricity card re-query every
 * time the page was opened. A `remember` would not help: it dies with the composition too.
 *
 * What survives is this object, because it holds app-scoped repositories. [hasLoaded] is therefore the
 * one place in the UI that can say "this process has already filled its modules", and it says it once.
 *
 * The repositories keep their own state across the same unmount, so the first frame after a tab switch
 * draws what is already loaded — no placeholder, no request. The same is true of the timetable page,
 * which is opened over the shell rather than alongside it: it is built fresh on every open, and it must
 * draw the loaded term rather than ask for one.
 *
 * A failed load is **not** retried by this guard: a module keeps what it had and the user's tap is the
 * retry — the timetable's own 刷新日程表, a card's own tap. That is deliberate: a launch away from
 * campus would otherwise re-query on every tab switch. It is also why nothing here schedules a periodic
 * refresh; if the app is left running across a data change, a tap is the way to see it.
 */
class CardRefresher(
    private val energy: EnergyRepository,
    private val schoolCard: SchoolCardRepository,
    private val timetable: TimetableRepository,
) {

    /** Whether this process has already asked for every module. */
    var hasLoaded: Boolean = false
        private set

    /**
     * Loads every module, once per process.
     *
     * The refreshes run in sequence because they share one session and one HTTP client; each repository
     * still owns its own request and its own in-flight state, so none waits on another's data. What the
     * order decides is **which module is slow**: every one of them logs in for its own CAS service before
     * it reads anything, those logins are serialised, and the timetable is a login plus five calls on top
     * of that. So whoever goes last waits for everything before it.
     *
     * Which module that should be depends on what is already on screen:
     *
     *  - **no timetable** — a fresh install, or the first launch after signing in. There is nothing to
     *    draw and the grid is what the user is waiting for, so the term goes first and the two cards
     *    follow it. This is the case that used to need a tap on 刷新日程表 to get a timetable at all.
     *  - **a timetable already** — the cache drew it on the first frame, so reading the term again is a
     *    background update. The two cards are what the user is looking at, and they go first.
     *
     * The condition is read **once, before any of them runs**: fetching the term is what makes a
     * timetable exist, so asking again afterwards would answer a different question than the one this
     * order was chosen for.
     */
    suspend fun loadOnce() {
        if (hasLoaded) return
        hasLoaded = true

        if (timetable.state.value.hasTimetable) {
            energy.refreshElectricityInfo()
            schoolCard.refreshSchoolCard()
            timetable.refresh()
        } else {
            timetable.refresh()
            energy.refreshElectricityInfo()
            schoolCard.refreshSchoolCard()
        }
    }
}
