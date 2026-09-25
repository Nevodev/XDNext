package com.nevoit.xdnext.data.schoolcard

import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

/**
 * The campus card's on-disk cache: the balance, and — only while it is still true — the day's totals.
 *
 * `SchoolCard.json` in the support directory, following the energy module's conventions: the same
 * directory, the same "modification time is the fetched-at stamp" trick, and the same rule that a read
 * failure is a cache *miss* rather than an error.
 *
 * ### Why the day is stored with the money
 *
 * A balance stays true across a failed refresh. A *day's* spending does not: the number was computed
 * for one date, and drawing it tomorrow under the label 今日支出 would be wrong in a way no timestamp on
 * screen repairs. So the totals are stored together with [SchoolCardCacheSnapshot.knownDate] — the day
 * they describe — and [read] only hands them back when that day is still the day being asked about.
 * A cache from yesterday therefore yields a balance and an *unknown* spending figure, which is what the
 * tile needs in order to say so instead of showing a stale number.
 *
 * The field is optional on decode so a file written before it existed still loads, as a balance with no
 * spending — the conservative reading, since the old file had no way to say which day its numbers were
 * for.
 */
class SchoolCardCache(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val json: Json,
) {

    private val path: Path = directory / SCHOOL_CARD_FILE

    /**
     * The cached card as of [today], or null when nothing has ever been cached.
     *
     * [today] is a parameter rather than a clock read so the cache has no opinion about the time: the
     * session owns the clock, and a test can ask what the cache would say on any given day.
     */
    fun read(today: LocalDate): FetchResult<SchoolCardSnapshot>? {
        if (!fileSystem.exists(path)) return null
        appLog.i { "[SchoolCardCache] Checking out cache from $SCHOOL_CARD_FILE" }
        val text = runCatching { fileSystem.read(path) { readUtf8() } }
            .onFailure { appLog.w(it) { "Could not read $path" } }
            .getOrNull()
            ?: return null
        val stored = runCatching { json.decodeFromString<SchoolCardCacheSnapshot>(text) }
            .onFailure { appLog.w(it) { "Discarding unreadable $SCHOOL_CARD_FILE" } }
            .getOrNull()
            ?: return null
        val balance = stored.balance?.takeIf { it.isNotEmpty() } ?: return null

        // Today's figures survive only from the same day. Anything else is dropped rather than shown,
        // and the balance is kept: the two parts of the card age differently.
        val sameDay = stored.knownDate == today
        return FetchResult.cache(
            fetchTime = lastModified(),
            data = SchoolCardSnapshot(
                balance = balance,
                todayExpenseCents = stored.todayExpenseCents.takeIf { sameDay },
                todayIncomeCents = if (sameDay) stored.todayIncomeCents else 0,
            ),
        )
    }

    /**
     * Writes the card, stamping the totals with the day they were read for.
     *
     * [knownDate] is null when the spending could not be read at all, in which case only the balance is
     * stored — a null date is "these totals describe no day", which is exactly what a failed query
     * means.
     */
    fun save(snapshot: SchoolCardSnapshot, knownDate: LocalDate?) {
        val stored = SchoolCardCacheSnapshot(
            balance = snapshot.balance,
            knownDate = snapshot.todayExpenseCents?.let { knownDate },
            todayExpenseCents = snapshot.todayExpenseCents,
            todayIncomeCents = snapshot.todayIncomeCents,
        )
        val encoded = runCatching { json.encodeToString(stored) }.getOrNull() ?: return
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

    private companion object {
        const val SCHOOL_CARD_FILE = "SchoolCard.json"
    }
}

/**
 * The card as it sits on disk.
 *
 * [balance] is nullable because a read that produced only transactions is still worth storing half of;
 * [knownDate] because a read that produced only a balance has no day to name.
 */
@Serializable
internal data class SchoolCardCacheSnapshot(
    val balance: String? = null,
    val knownDate: LocalDate? = null,
    val todayExpenseCents: Long? = null,
    val todayIncomeCents: Long = 0,
)
