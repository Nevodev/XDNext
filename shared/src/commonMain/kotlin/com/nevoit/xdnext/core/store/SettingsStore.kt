package com.nevoit.xdnext.core.store

import com.russhwolf.settings.Settings

/**
 * Thin typed wrapper over [Settings] for non-secret preferences.
 *
 * The original declared a `Preference` enum carrying a `type` per key and threw `WrongTypeException`
 * on a mismatch. That caught real bugs, so the same idea is kept: callers must go through a typed
 * accessor rather than the untyped map.
 *
 * Secrets do **not** belong here — use [SecureStore].
 */
class SettingsStore(private val settings: Settings) {

    fun getString(key: String): String? = settings.getStringOrNull(key)

    fun putString(key: String, value: String) {
        settings.putString(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        settings.getBoolean(key, defaultValue)

    fun putBoolean(key: String, value: Boolean) {
        settings.putBoolean(key, value)
    }

    fun getInt(key: String, defaultValue: Int = 0): Int = settings.getInt(key, defaultValue)

    fun putInt(key: String, value: Int) {
        settings.putInt(key, value)
    }

    fun contains(key: String): Boolean = settings.hasKey(key)

    fun remove(key: String) {
        settings.remove(key)
    }
}

/** Preference keys that are not secrets. */
object SettingsKeys {
    /**
     * IDS "trusted device" fingerprint: 32 uppercase hex characters.
     *
     * The original names this a browser fingerprint but it is simply 16 secure-random bytes; it is
     * persisted independently of the cookie jar and re-registered before every login.
     */
    const val IDS_BROWSER_FINGERPRINT = "ids.browserFingerprint"

    /**
     * Set once the user has completed a successful login at least once.
     *
     * Written and removed with the credentials themselves — see `CredentialStore` — and read
     * **synchronously** before the first frame to decide whether the app opens on the login screen or
     * on the shell. It lives here rather than in the secure store for exactly that reason: the
     * keystore read is suspend, and this question is asked before there is a frame to wait on.
     */
    const val HAS_COMPLETED_LOGIN = "ids.hasCompletedLogin"

    /**
     * Whether to warn when the dorm's electricity balance runs low.
     *
     * Absent means **enabled**: the original treated a missing key as "on", so a user who never opened
     * the settings is still warned.
     */
    const val LOW_ELECTRICITY_WARNING_ENABLED = "energy.lowElectricityWarningEnabled"

    /** The balance, in kWh, below which the warning fires. Absent or non-positive means the default. */
    const val LOW_ELECTRICITY_WARNING_THRESHOLD = "energy.lowElectricityWarningThreshold"

    /**
     * The academic semester the app is showing, as the registrar codes it (`2025-2026-1`).
     *
     * Deliberately not named after the timetable: every academic module reads the same semester, and
     * the original kept one shared `currentSemester` preference for exactly that reason. It holds the
     * *reconciled* value — the newer of the registrar's current semester and the last one used — which
     * is what lets a user keep looking at a future semester they chose.
     */
    const val ACADEMIC_SEMESTER_CODE = "academic.semesterCode"

    /**
     * How the timetable grid draws itself: its current-time line and its finished-class styling.
     *
     * The four keys are the original's own (`Preference.currentTimeIndicatorEnabled`, …), which is why
     * they are named in its camelCase rather than this app's dotted convention: they are the names the
     * settings page it shipped wrote, and keeping them is what makes the mapping between the two apps'
     * settings a one-to-one reading rather than a translation.
     *
     * The original's *numeric* keys beside them — the HSL factors of `classStyleActive…` and
     * `classStyleCompleted…` — have no counterpart here, because the grid's colours are generated rather
     * than hand-picked: there is no saturation to nudge when the tone comes from the system accent. See
     * `TimetableAppearance`.
     */
    const val TIMETABLE_TIME_INDICATOR = "currentTimeIndicatorEnabled"
    const val TIMETABLE_TIME_INDICATOR_LABEL = "currentTimeIndicatorShowTimeLabel"
    const val TIMETABLE_TODAY_HIGHLIGHT = "currentTimeIndicatorShowTodayColumnHighlight"
    const val TIMETABLE_COMPLETED_CLASS_STYLE = "classStyleCompletedEnabled"
}
