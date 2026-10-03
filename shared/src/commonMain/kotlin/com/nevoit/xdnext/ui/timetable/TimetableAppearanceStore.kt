package com.nevoit.xdnext.ui.timetable

import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the grid's appearance comes from, and what writes it.
 *
 * The original kept this in two mutable statics — `CurrentTimeIndicatorConfig` and
 * `CompletedClassStyleConfig` — each of which read itself out of the preference file in a getter and
 * wrote itself back in a setter, so nothing could draw a grid without a preference store. The value
 * itself is [TimetableAppearance] here (see its documentation), and this is the thing that owns it:
 * a settings page writes through [update], and the timetable page draws what [appearance] says.
 *
 * A [StateFlow] rather than a plain read, because the two are on different pages: the settings page is
 * pushed *over* the timetable, and when it is closed the timetable is still composed underneath it.
 * Reading the store at composition time would leave the grid drawn with the settings it had when its
 * page was opened — the change would only appear the next time the page was built, which is exactly the
 * behaviour a settings screen must not have.
 *
 * The defaults are [TimetableAppearance]'s own, which is why [read] starts from a default instance
 * rather than from a list of `true`s: the two must not be able to disagree.
 */
class TimetableAppearanceStore(private val settings: SettingsStore) {

    private val _appearance = MutableStateFlow(read())

    /** How the grid should draw itself, from the first frame the page is composed. */
    val appearance: StateFlow<TimetableAppearance> = _appearance.asStateFlow()

    /** Replaces the appearance, writing the difference through to the preference file. */
    fun update(transform: (TimetableAppearance) -> TimetableAppearance) {
        val updated = transform(_appearance.value)
        if (updated == _appearance.value) return

        settings.putBoolean(SettingsKeys.TIMETABLE_TIME_INDICATOR, updated.currentTimeIndicatorEnabled)
        settings.putBoolean(SettingsKeys.TIMETABLE_TIME_INDICATOR_LABEL, updated.showTimeLabel)
        settings.putBoolean(SettingsKeys.TIMETABLE_TODAY_HIGHLIGHT, updated.showTodayColumnHighlight)
        settings.putBoolean(
            SettingsKeys.TIMETABLE_COMPLETED_CLASS_STYLE,
            updated.completedClassStyleEnabled,
        )
        _appearance.value = updated
    }

    private fun read(): TimetableAppearance {
        val defaults = TimetableAppearance()
        return TimetableAppearance(
            currentTimeIndicatorEnabled = settings.getBoolean(
                SettingsKeys.TIMETABLE_TIME_INDICATOR,
                defaults.currentTimeIndicatorEnabled,
            ),
            showTimeLabel = settings.getBoolean(
                SettingsKeys.TIMETABLE_TIME_INDICATOR_LABEL,
                defaults.showTimeLabel,
            ),
            showTodayColumnHighlight = settings.getBoolean(
                SettingsKeys.TIMETABLE_TODAY_HIGHLIGHT,
                defaults.showTodayColumnHighlight,
            ),
            completedClassStyleEnabled = settings.getBoolean(
                SettingsKeys.TIMETABLE_COMPLETED_CLASS_STYLE,
                defaults.completedClassStyleEnabled,
            ),
        )
    }
}
