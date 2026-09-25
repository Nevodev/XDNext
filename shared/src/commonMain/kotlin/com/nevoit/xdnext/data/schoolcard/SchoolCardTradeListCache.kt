package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

/**
 * The card page's list on disk: the rows of a query, stored **with the days they were asked for**.
 *
 * `SchoolCardTradeList.json` in the support directory, beside the card's own `SchoolCard.json` and under
 * the same conventions: the file's modification time is the fetched-at stamp, and a read failure is a
 * cache *miss* rather than an error.
 *
 * ### Why the range is part of the file
 *
 * The rows are not "the card's transactions" — they are the answer to a question, and the question is a
 * window of days. A list of rows without its window is the same mistake as a spending figure without its
 * day: a week's rows would be drawn under a heading that said today, and nothing on screen could reveal
 * it. So [SchoolCardRange] is stored in the file, and [read] only answers when the stored window
 * **covers** the one being asked about — see [SchoolCardRange.covers].
 *
 * Covering is what makes the cache useful rather than merely safe: the page opens on today, and a
 * previous visit that asked for the whole month has already read today's rows as part of it, so they can
 * be drawn straight away instead of costing another request off campus.
 *
 * Unlike the card's own totals, rows do not expire by the day passing: a day's rows were that day's rows
 * before the day ended, and their window says so.
 */
class SchoolCardTradeListCache(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val json: Json,
) {

    private val path: Path = directory / TRADE_LIST_FILE

    /**
     * The rows cached for [range], or null when nothing is stored or what is stored does not cover it.
     *
     * The returned value is a [FetchResult] because the rows may be old — the caller draws them and says
     * where they came from, which is the same contract the card's own cache has.
     */
    fun read(range: SchoolCardRange): FetchResult<List<SchoolCardTransaction>>? {
        if (!fileSystem.exists(path)) return null
        appLog.i { "[SchoolCardTradeListCache] Checking out cache from $TRADE_LIST_FILE" }
        val text = runCatching { fileSystem.read(path) { readUtf8() } }
            .onFailure { appLog.w(it) { "Could not read $path" } }
            .getOrNull()
            ?: return null
        val stored = runCatching { json.decodeFromString<SchoolCardTradeListSnapshot>(text) }
            .onFailure { appLog.w(it) { "Discarding unreadable $TRADE_LIST_FILE" } }
            .getOrNull()
            ?: return null

        val storedRange = stored.range ?: return null
        if (!storedRange.covers(range)) {
            appLog.i {
                "[SchoolCardTradeListCache] $TRADE_LIST_FILE holds " +
                        "${storedRange.from}..${storedRange.to}, which does not cover " +
                        "${range.from}..${range.to}"
            }
            return null
        }

        return FetchResult.cache(fetchTime = lastModified(), data = stored.transactions)
    }

    /** Writes [transactions] as the answer for [range], stamping the file with when it was read. */
    fun save(range: SchoolCardRange, transactions: List<SchoolCardTransaction>) {
        val encoded = runCatching {
            json.encodeToString(SchoolCardTradeListSnapshot(range = range, transactions = transactions))
        }.getOrNull() ?: return
        runCatching {
            fileSystem.createDirectories(directory)
            fileSystem.write(path) { writeUtf8(encoded) }
        }.onFailure {
            // Losing the cache degrades to "the next visit asks again", which is recoverable.
            appLog.w(it) { "Could not write $path" }
        }
    }

    private fun lastModified(): Instant {
        val millis = runCatching { fileSystem.metadata(path).lastModifiedAtMillis }.getOrNull()
        return Instant.fromEpochMilliseconds(millis ?: 0L)
    }

    private companion object {
        const val TRADE_LIST_FILE = "SchoolCardTradeList.json"
    }
}

/**
 * The flow list as it sits on disk.
 *
 * [range] is nullable so a file written by a version that had no window still *loads* — and loads as a
 * miss, because rows with no window are exactly the thing this file exists to refuse.
 */
@Serializable
internal data class SchoolCardTradeListSnapshot(
    val range: SchoolCardRange? = null,
    val transactions: List<SchoolCardTransaction> = emptyList(),
)
