package com.nevoit.xdnext.ui.timetable

import com.nevoit.xdnext.core.store.InMemorySettings
import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the grid's appearance preferences.
 *
 * The two things worth pinning are the two ends of this file: the **defaults** are the original's own
 * (its finished-class styling was off, its current-time line and today-column tint were on), and the
 * **keys** are the original's own names, because they are what the settings page it shipped wrote.
 *
 * What is *not* here is a test of the drawing: how the grid looks with a given value is
 * `TimetableAppearance`'s and `ClassPalette`'s business, and both have their own tests.
 */
class TimetableAppearanceStoreTest {

    @Test
    fun readsTheOriginalsDefaultsWhenNothingIsStored() {
        val appearance = TimetableAppearanceStore(SettingsStore(InMemorySettings())).appearance.value

        assertEquals(TimetableAppearance(), appearance)
        assertTrue(appearance.currentTimeIndicatorEnabled)
        assertTrue(appearance.showTimeLabel)
        assertTrue(appearance.showTodayColumnHighlight)
        assertFalse(appearance.completedClassStyleEnabled)
    }

    @Test
    fun readsWhatTheOriginalWroteUnderItsOwnKeys() {
        // The keys the original's settings page wrote, with values a user could have chosen there.
        val settings = SettingsStore(
            InMemorySettings(
                mapOf(
                    "currentTimeIndicatorEnabled" to false,
                    "currentTimeIndicatorShowTimeLabel" to false,
                    "currentTimeIndicatorShowTodayColumnHighlight" to false,
                    "classStyleCompletedEnabled" to true,
                ),
            ),
        )

        val appearance = TimetableAppearanceStore(settings).appearance.value

        assertFalse(appearance.currentTimeIndicatorEnabled)
        assertFalse(appearance.showTimeLabel)
        assertFalse(appearance.showTodayColumnHighlight)
        assertTrue(appearance.completedClassStyleEnabled)
    }

    @Test
    fun everySwitchIsWrittenThroughToThePreferences() {
        val settings = SettingsStore(InMemorySettings())
        val store = TimetableAppearanceStore(settings)

        store.update { it.copy(currentTimeIndicatorEnabled = false, completedClassStyleEnabled = true) }

        assertFalse(settings.getBoolean(SettingsKeys.TIMETABLE_TIME_INDICATOR, true))
        assertTrue(settings.getBoolean(SettingsKeys.TIMETABLE_COMPLETED_CLASS_STYLE, false))
        assertEquals(
            settings.getBoolean(SettingsKeys.TIMETABLE_TIME_INDICATOR, true),
            store.appearance.value.currentTimeIndicatorEnabled,
            "What is on disk and what is drawn are the same answer.",
        )
    }

    @Test
    fun theValueSurvivesAReload() {
        // A second store over the same preferences is what the next launch is: the switches are read back
        // as they were set rather than reverting to the defaults.
        val settings = SettingsStore(InMemorySettings())
        TimetableAppearanceStore(settings).update { it.copy(showTodayColumnHighlight = false) }

        assertFalse(TimetableAppearanceStore(settings).appearance.value.showTodayColumnHighlight)
    }

    @Test
    fun anUpdateThatChangesNothingWritesNothing() {
        val settings = SettingsStore(InMemorySettings())
        val store = TimetableAppearanceStore(settings)

        store.update { it }

        assertFalse(
            settings.contains(SettingsKeys.TIMETABLE_TIME_INDICATOR),
            "A no-op update must not create preference entries that were never set.",
        )
    }
}
