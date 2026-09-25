package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

/**
 * The on-disk cache for the energy query.
 *
 * Ported from the file half of `EnergySession`. Two files, both in the support directory, both named
 * exactly as the original named them:
 *
 *  - `EnergyInfo.json` — the last successful reading. Its **modification time** is the "fetched at"
 *    stamp, which is the original's own trick: it means a cached value carries no timestamp field
 *    that could disagree with the file, and a file copied between devices cannot lie about its age.
 *  - `ElectricityHistory.json` — the locally accumulated remain-over-time series, at most fifteen
 *    points, appended once per distinct reading day by [EnergyRepository].
 *
 * Every read failure is a cache *miss*, never an error: a truncated file must cost the user one
 * refresh, not a broken screen. That is the original's behavior, and it is the right one.
 */
class EnergyCache(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val json: Json,
) {

    private val infoPath: Path = directory / ENERGY_INFO_FILE
    private val historyPath: Path = directory / ELECTRICITY_HISTORY_FILE

    fun getEnergyInfo(): FetchResult<EnergyInfo>? {
        if (!fileSystem.exists(infoPath)) return null
        appLog.i { "[EnergyCache] Checking out cache from $ENERGY_INFO_FILE" }
        val text = readText(infoPath) ?: return null
        val info = runCatching { json.decodeFromString<EnergyInfo>(text) }
            .onFailure { appLog.w(it) { "Discarding unreadable $ENERGY_INFO_FILE" } }
            .getOrNull()
            ?: return null
        return FetchResult.cache(fetchTime = lastModified(infoPath), data = info)
    }

    fun saveEnergyInfo(info: EnergyInfo) {
        writeText(infoPath, runCatching { json.encodeToString(info) }.getOrNull() ?: return)
    }

    fun deleteEnergyInfo() {
        runCatching { fileSystem.delete(infoPath) }
            .onFailure { appLog.w(it) { "Could not delete $ENERGY_INFO_FILE" } }
    }

    /**
     * The history, oldest first.
     *
     * The original sorted on load rather than trusting append order, because the file is JSON that
     * anyone can edit and the chart plots it as a time series.
     */
    fun getElectricityHistory(): List<ElectricityHistoryInfo> {
        if (!fileSystem.exists(historyPath)) return emptyList()
        val text = readText(historyPath) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ElectricityHistoryInfo>>(text) }
            .onFailure { appLog.w(it) { "Discarding unreadable $ELECTRICITY_HISTORY_FILE" } }
            .getOrDefault(emptyList())
            .sortedBy { it.fetchDay }
    }

    fun saveElectricityHistory(history: List<ElectricityHistoryInfo>) {
        writeText(historyPath, runCatching { json.encodeToString(history) }.getOrNull() ?: return)
    }

    /** Empties the history without removing the file, matching the original's `clear`. */
    fun clearElectricityHistory() {
        writeText(historyPath, "[]")
    }

    private fun readText(path: Path): String? = runCatching { fileSystem.read(path) { readUtf8() } }
        .onFailure { appLog.w(it) { "Could not read $path" } }
        .getOrNull()

    private fun writeText(path: Path, text: String) {
        runCatching {
            fileSystem.createDirectories(directory)
            fileSystem.write(path) { writeUtf8(text) }
        }.onFailure {
            // Losing the cache degrades to "the next launch asks again", which is recoverable.
            appLog.w(it) { "Could not write $path" }
        }
    }

    private fun lastModified(path: Path): Instant {
        val millis = runCatching { fileSystem.metadata(path).lastModifiedAtMillis }.getOrNull()
        return Instant.fromEpochMilliseconds(millis ?: 0L)
    }

    private companion object {
        const val ENERGY_INFO_FILE = "EnergyInfo.json"
        const val ELECTRICITY_HISTORY_FILE = "ElectricityHistory.json"
    }
}
