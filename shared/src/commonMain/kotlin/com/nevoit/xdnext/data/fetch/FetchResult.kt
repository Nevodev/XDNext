package com.nevoit.xdnext.data.fetch

import kotlin.time.Instant

/**
 * A value that was either just fetched or served from the on-device cache.
 *
 * Ported from `lib/model/fetch_result.dart`, where a single type carries both cases so every caller
 * has to decide what to show when the network fails. [hintKey] is set only on the cache branch and
 * says *why* the fetch could not be refreshed — the original filled it in from the exception type.
 *
 * [T] is the payload; the app's own caches are JSON files, so the freshness of a cached value is the
 * file's modification time rather than a field inside it.
 *
 * Deliberately **not** a `data class`: the two factories are the only way to build one, and a
 * generated `copy` would both expose that private constructor (a warning today, an error in Kotlin
 * 2.5) and let a caller flip `isCache` on a value that was fetched.
 */
class FetchResult<T> private constructor(
    val isCache: Boolean,
    val fetchTime: Instant,
    val data: T,
    val hintKey: String?,
) {
    override fun toString(): String =
        "FetchResult(isCache=$isCache, fetchTime=$fetchTime, hintKey=$hintKey, data=$data)"

    companion object {
        fun <T> fresh(fetchTime: Instant, data: T): FetchResult<T> =
            FetchResult(isCache = false, fetchTime = fetchTime, data = data, hintKey = null)

        fun <T> cache(fetchTime: Instant, data: T, hintKey: String? = null): FetchResult<T> =
            FetchResult(isCache = true, fetchTime = fetchTime, data = data, hintKey = hintKey)
    }
}
