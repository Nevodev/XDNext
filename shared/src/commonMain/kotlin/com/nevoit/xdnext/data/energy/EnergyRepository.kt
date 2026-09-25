package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.energy.EnergyRepository.Companion.DEFAULT_LOW_ELECTRICITY_WARNING_THRESHOLD
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * The low-electricity reminder's settings.
 *
 * The original encoded "off" as a sentinel threshold of `-1` and derived two signals from it. A
 * boolean plus a threshold carries the same two facts without the sentinel.
 */
data class LowElectricityWarning(
    val enabled: Boolean = true,
    val threshold: Int = EnergyRepository.DEFAULT_LOW_ELECTRICITY_WARNING_THRESHOLD,
) {
    /** Whether a balance of [remain] kWh should be flagged. */
    fun isTriggeredBy(remain: Double): Boolean = enabled && remain < threshold
}

/**
 * Everything the electricity card draws itself from.
 *
 * A single immutable snapshot rather than the original's four separate signals
 * (`energyInfoStateSignal`, `displayEnergyInfo`, `isEnergyInfoFromCache`, the history list), so a
 * frame can never be composed from two different fetches — which the original's separate signals did
 * allow, between a refresh finishing and the screen reading each of them in turn.
 */
data class EnergyState(
    /** True while an attempt is in flight; the previous value stays on screen. */
    val isLoading: Boolean = true,
    /** The reading to display, from the network or from the cache. */
    val info: EnergyInfo? = null,
    val isFromCache: Boolean = false,
    /** When [info] was obtained — for a cached value, when it was written. */
    val fetchTime: Instant? = null,
    /** Why the displayed value is a cached one. */
    val cacheHint: EnergyCacheHint? = null,
    /** The last attempt's failure, when there was nothing at all to display. */
    val error: Throwable? = null,
    /** The locally accumulated remain-over-time series, oldest first. */
    val history: List<ElectricityHistoryInfo> = emptyList(),
)

/**
 * The electricity query's state holder.
 *
 * Ported from `lib/controller/energy_controller.dart`. Three things it owns:
 *
 *  - the state the card reads ([state]);
 *  - the accumulated history (at most fifteen points, appended once per distinct reading day);
 *  - the low-balance reminder's settings, which live in [SettingsStore] rather than in memory.
 *
 * The original's `refreshElectricityInfo({force})` took a `force` flag that nothing read; it is not
 * carried over. Its preference writes returned futures only because its preference layer was async —
 * [SettingsStore] is synchronous, so these setters are too.
 *
 * The constructor loads the cache synchronously, exactly as the original's did: the card must be able
 * to show the last reading on the very first frame, before any coroutine has run.
 */
class EnergyRepository(
    private val source: EnergyDataSource,
    private val settings: SettingsStore,
) {

    private val _state = MutableStateFlow(EnergyState())

    /** The reading, its freshness, the history and the last error. */
    val state: StateFlow<EnergyState> = _state.asStateFlow()

    private val _lowElectricityWarning = MutableStateFlow(readLowElectricityWarning())

    /** The low-balance reminder's settings. */
    val lowElectricityWarning: StateFlow<LowElectricityWarning> =
        _lowElectricityWarning.asStateFlow()

    init {
        loadCachedReading()
        _state.value = _state.value.copy(history = source.getElectricityHistory())
    }

    /**
     * Reads a new value, keeping the displayed one on screen while the request is in flight.
     *
     * A failure with a cached reading available is **not** an error state: the cache notice carries
     * the reason instead. Only a failure with nothing to show sets [EnergyState.error].
     */
    suspend fun refreshElectricityInfo() {
        val previous = _state.value
        _state.value = previous.copy(isLoading = true, error = null)
        try {
            applyFetch(source.getElectricityInfo())
        } catch (cancelled: CancellationException) {
            // See `TimetableRepository.refresh`: a cancelled refresh must not leave the state claiming it
            // is still running, because this repository outlives the screen that started it — the focus
            // page is unmounted on a tab switch — and the card would then say 正在查询 forever.
            _state.value = previous
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[EnergyRepository] Could not refresh the electricity reading" }
            _state.value = previous.copy(isLoading = false, error = error)
        }
    }

    /** Empties the remain-over-time series. The cached reading itself is untouched. */
    fun clearElectricityHistory() {
        source.clearElectricityHistory()
        _state.value = _state.value.copy(history = emptyList())
    }

    // --- The low-balance reminder ---------------------------------------------------------------

    fun setLowElectricityWarningEnabled(enabled: Boolean) {
        settings.putBoolean(SettingsKeys.LOW_ELECTRICITY_WARNING_ENABLED, enabled)
        _lowElectricityWarning.value = readLowElectricityWarning()
    }

    fun setLowElectricityWarningThreshold(threshold: Int) {
        settings.putInt(
            SettingsKeys.LOW_ELECTRICITY_WARNING_THRESHOLD,
            if (threshold > 0) threshold else DEFAULT_LOW_ELECTRICITY_WARNING_THRESHOLD,
        )
        _lowElectricityWarning.value = readLowElectricityWarning()
    }

    /**
     * Reads the reminder's settings.
     *
     * Both defaults are the original's: an absent "enabled" key means **on** — a user who has never
     * opened the settings is still warned — and a threshold that is absent or non-positive falls back
     * to [DEFAULT_LOW_ELECTRICITY_WARNING_THRESHOLD].
     */
    private fun readLowElectricityWarning(): LowElectricityWarning {
        val enabled = !settings.contains(SettingsKeys.LOW_ELECTRICITY_WARNING_ENABLED) ||
                settings.getBoolean(SettingsKeys.LOW_ELECTRICITY_WARNING_ENABLED)
        if (!enabled) return LowElectricityWarning(enabled = false)

        if (!settings.contains(SettingsKeys.LOW_ELECTRICITY_WARNING_THRESHOLD)) {
            return LowElectricityWarning(enabled = true)
        }
        val stored = settings.getInt(SettingsKeys.LOW_ELECTRICITY_WARNING_THRESHOLD)
        return LowElectricityWarning(
            enabled = true,
            threshold = stored.takeIf { it > 0 } ?: DEFAULT_LOW_ELECTRICITY_WARNING_THRESHOLD,
        )
    }

    // --- Internals ------------------------------------------------------------------------------

    private fun loadCachedReading() {
        val cached = source.getCache() ?: return
        _state.value = _state.value.copy(
            isLoading = false,
            info = cached.data,
            isFromCache = true,
            fetchTime = cached.fetchTime,
            cacheHint = hintFor(cached.hintKey),
        )
    }

    private fun applyFetch(result: FetchResult<EnergyInfo>) {
        val history = if (result.isCache) _state.value.history else recordHistory(result.data)
        _state.value = _state.value.copy(
            isLoading = false,
            info = result.data,
            isFromCache = result.isCache,
            fetchTime = result.fetchTime,
            cacheHint = hintFor(result.hintKey),
            error = null,
            history = history,
        )
    }

    /**
     * Appends one history point, unless the newest point is already from this reading day.
     *
     * The series is keyed by the meter's own reading day, not by when the app ran: refreshing twice in
     * one day must not produce two points, but a meter that reports a reading taken yesterday must
     * still contribute it.
     *
     * Fifteen points are kept, as in the original: it trimmed whenever the list was longer than
     * fourteen, *before* appending, which leaves a maximum of fifteen.
     */
    private fun recordHistory(info: EnergyInfo): List<ElectricityHistoryInfo> {
        val history = _state.value.history
        if (history.lastOrNull()?.fetchDay == info.lastReadDate) return history

        val trimmed = if (history.size > MAX_HISTORY_POINTS - 1) history.drop(1) else history
        val updated = trimmed + ElectricityHistoryInfo(
            fetchDay = info.lastReadDate,
            // The original stored `electricityRemain.toString()`, so the series is written the way the
            // card is: a whole balance has no decimal part.
            remain = info.electricityRemainText,
        )
        source.saveElectricityHistory(updated)
        return updated
    }

    private fun hintFor(key: String?): EnergyCacheHint? =
        key?.let { stored -> EnergyCacheHint.entries.firstOrNull { it.key == stored } }

    companion object {
        /** The original's default: warn below 20 kWh. */
        const val DEFAULT_LOW_ELECTRICITY_WARNING_THRESHOLD = 20

        /** The original kept fifteen points: it trimmed when the list exceeded fourteen. */
        private const val MAX_HISTORY_POINTS = 15
    }
}
