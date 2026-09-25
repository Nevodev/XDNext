package com.nevoit.xdnext.data.net

import com.nevoit.xdnext.core.platform.createPlatformHttpClient
import com.nevoit.xdnext.data.ids.IdsEndpoints
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the HTTP client used for Xidian's identity provider.
 *
 * Three settings are load-bearing and are ports of the original's `Dio` configuration rather than
 * preferences:
 *
 *  - **redirects are disabled.** The CAS flow *is* a redirect chain, and the original walked it by
 *    hand (up to 30 hops) because each hop may need a second-factor challenge injected and because
 *    the redirect target identifies whether the session was already valid. Letting the engine follow
 *    redirects would hide all of that.
 *  - **non-2xx responses do not throw.** A rejected login arrives as 401 with an HTML error body, and
 *    that body is the only source of the user-facing reason.
 *  - **the persistent cookie store is installed**, which is what makes automatic login work.
 */
class HttpClientFactory(private val cookieStorage: FileCookieStorage) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun createIdsClient(): HttpClient = createPlatformHttpClient {
        followRedirects = false
        expectSuccess = false

        install(HttpCookies) {
            storage = cookieStorage
        }

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            // The original allowed 10s to connect and 30s to receive.
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        defaultRequest {
            header(HttpHeaders.UserAgent, IdsEndpoints.USER_AGENT)
            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
        }
    }
}
