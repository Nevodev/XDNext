package com.nevoit.xdnext.data.net

import com.nevoit.xdnext.core.log.appLog
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * A cookie store that survives process restarts.
 *
 * The original used `PersistCookieJar(persistSession: true, ...)`, which is the whole reason
 * "automatic login" worked at all: the CAS session cookie was written to
 * `<support dir>/cookie/general` and replayed on the next launch, so a valid session meant no
 * password, no captcha, and no network round trip beyond a single redirect probe.
 *
 * Two behaviours are deliberate ports:
 *  - session cookies are persisted too (the Dart jar set `persistSession: true`);
 *  - [cookieHeaderFor] exposes a raw `Cookie` header, because the original bypassed its jar to build
 *    that header by hand for the slider-captcha endpoints.
 *
 * The store is JSON-backed rather than Netscape-format, because nothing else reads the file. That is
 * a deliberate break from the original's on-disk format.
 */
class FileCookieStorage(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : CookiesStorage {

    private val mutex = Mutex()

    /** domain -> cookies. Grouped for cheap persistence and for `clear`. */
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    private var loaded = false

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        ensureLoaded()
        cookies.values.asSequence()
            .flatten()
            .filter { it.matches(requestUrl) }
            .toList()
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        ensureLoaded()

        // The Dart jar filled domain/path defaults from the request; Ktor leaves them null when the
        // server omitted the attributes, and `Cookie.matches` throws on nulls.
        val domain = cookie.domain?.takeIf { it.isNotBlank() } ?: requestUrl.host
        val stored = cookie.copy(domain = domain, path = cookie.path ?: "/")

        val bucket = cookies.getOrPut(domain) { mutableListOf() }
        bucket.removeAll { it.name == stored.name && it.path == stored.path }
        bucket.add(stored)
        persist()
    }

    /** Number of cookies currently held; used by tests and diagnostics. */
    suspend fun size(): Int = mutex.withLock {
        ensureLoaded()
        cookies.values.sumOf { it.size }
    }

    /**
     * Builds a raw `Cookie` header for [url].
     *
     * This mirrors what the original assembled for the slider-captcha client, which sent the header
     * explicitly instead of relying on the cookie jar.
     */
    suspend fun cookieHeaderFor(url: Url): String = mutex.withLock {
        ensureLoaded()
        cookies.values.asSequence()
            .flatten()
            .filter { it.matches(url) }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** Drops every cookie, in memory and on disk. */
    suspend fun clearAll() = mutex.withLock {
        ensureLoaded()
        cookies.clear()
        runCatching { fileSystem.delete(path) }
            .onFailure { appLog.w(it) { "Could not delete cookie file $path" } }
        Unit
    }

    override fun close() {
        // Nothing to release: writes happen inline. Present because CookiesStorage is Closeable.
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!fileSystem.exists(path)) return
        runCatching {
            val text = fileSystem.read(path) { readUtf8() }
            if (text.isBlank()) return@runCatching
            val stored = json.decodeFromString<List<StoredCookie>>(text)
            for (item in stored) {
                val cookie = item.toCookie()
                cookies.getOrPut(item.domain) { mutableListOf() }.add(cookie)
            }
        }.onFailure {
            // A corrupt cookie file must not brick the app: the user can simply log in again.
            appLog.w(it) { "Discarding unreadable cookie file $path" }
            cookies.clear()
        }
    }

    private fun persist() {
        runCatching {
            val parent = path.parent
            if (parent != null) fileSystem.createDirectories(parent)
            val snapshot = cookies.values.asSequence().flatten().map(StoredCookie::from).toList()
            fileSystem.write(path) { writeUtf8(json.encodeToString(snapshot)) }
        }.onFailure {
            // Losing persistence degrades to a session-only cookie jar, which is recoverable.
            appLog.w(it) { "Could not persist cookies to $path" }
        }
    }

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        // Kept so an expiring cookie is not resurrected forever; epoch millis, null = session cookie.
        val expiresAtMillis: Long? = null,
    ) {
        fun toCookie(): Cookie = Cookie(
            name = name,
            value = value,
            domain = domain,
            path = path,
            secure = secure,
            httpOnly = httpOnly,
            expires = expiresAtMillis?.let { GMTDate(it) },
        )

        companion object {
            fun from(cookie: Cookie): StoredCookie = StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain ?: "",
                path = cookie.path ?: "/",
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                expiresAtMillis = cookie.expires?.timestamp,
            )
        }
    }
}
