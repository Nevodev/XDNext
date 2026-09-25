package com.nevoit.xdnext.core.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Platform services the shared code cannot express itself.
 *
 * Deliberately tiny. Everything else — storage, crypto, HTTP — is either pure common code or
 * supplied through dependency injection, so this file is the whole platform surface of the login
 * slice.
 */

/**
 * Wall-clock milliseconds since the Unix epoch.
 *
 * Both the CAS login form and the captcha endpoints require a cache-busting `_` query parameter, and
 * the captcha track format needs real elapsed milliseconds. A dedicated expect avoids depending on a
 * clock API that has moved between `kotlinx-datetime` and `kotlin.time` in recent releases.
 */
expect fun currentTimeMillis(): Long

/**
 * Absolute path of the directory this app owns for support files.
 *
 * Analogous to `getApplicationSupportDirectory()` in the original; the cookie store lives beneath it.
 */
expect fun supportDirectoryPath(): String

/**
 * Creates an [HttpClient] using the platform's engine.
 *
 * Android uses OkHttp (via `ktor-client-okhttp`), matching the plan for this project; iOS and desktop
 * will follow with their own engines without touching shared code.
 */
expect fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient
