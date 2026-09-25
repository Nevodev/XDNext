package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

/**
 * The timetable's on-disk cache: `ClassTable.json` in the support directory.
 *
 * The same file, in the same place, holding the same JSON the original wrote — its
 * `ClassTableSession._schoolClassDataCache` was `"${supportPath.path}/ClassTable.json"` and its
 * contents were `jsonEncode(data.toJson())`, which is a bare [ClassTableData] rather than an envelope.
 * That matters beyond tidiness: the original's shipped home-screen widget reads that file, and anyone
 * comparing a captured file with this app's should be able to diff them.
 *
 * The freshness stamp is the file's **modification time**, not a field inside it — the convention the
 * energy and campus-card caches follow, and the one that makes a copied file unable to lie about its
 * age.
 *
 * A read that fails is a cache *miss*, never an error: an unreadable or half-written file has to
 * degrade to "the next refresh asks again", which is recoverable, rather than to a screen that cannot
 * draw the timetable it is holding.
 */
class TimetableCache(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val json: Json,
) {

    private val path: Path = directory / CLASS_TABLE_FILE

    /**
     * The cached timetable, or null when there is none to read.
     *
     * A file that decodes but carries no weeks is still returned: an empty timetable is a real answer
     * — a semester the registrar has not published, or one the student has no classes in — and the
     * page has states for both.
     */
    fun read(): FetchResult<ClassTableData>? {
        if (!fileSystem.exists(path)) return null
        appLog.i { "[TimetableCache] Checking out cache from $CLASS_TABLE_FILE" }
        val text = runCatching { fileSystem.read(path) { readUtf8() } }
            .onFailure { appLog.w(it) { "Could not read $path" } }
            .getOrNull()
            ?: return null
        val data = runCatching { json.decodeFromString<ClassTableData>(text) }
            .onFailure { appLog.w(it) { "Discarding unreadable $CLASS_TABLE_FILE" } }
            .getOrNull()
            ?: return null
        return FetchResult.cache(fetchTime = lastModified(), data = data)
    }

    /** Writes the timetable, which is also what stamps its fetched-at time. */
    fun save(data: ClassTableData) {
        val encoded = runCatching { json.encodeToString(data) }
            .onFailure { appLog.w(it) { "Could not encode the timetable for $CLASS_TABLE_FILE" } }
            .getOrNull()
            ?: return
        runCatching {
            fileSystem.createDirectories(directory)
            fileSystem.write(path) { writeUtf8(encoded) }
        }.onFailure {
            // Losing the cache degrades to "the next launch asks again", which is recoverable.
            appLog.w(it) { "Could not write $path" }
        }
    }

    private fun lastModified(): Instant {
        val millis = runCatching { fileSystem.metadata(path).lastModifiedAtMillis }.getOrNull()
        return Instant.fromEpochMilliseconds(millis ?: 0L)
    }

    companion object {
        /** The original's own file name, kept so a captured file is recognisable. */
        const val CLASS_TABLE_FILE = "ClassTable.json"
    }
}
