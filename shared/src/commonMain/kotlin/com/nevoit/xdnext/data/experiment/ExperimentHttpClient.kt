package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.platform.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/**
 * The HTTP engine the lab site is read with.
 *
 * Its own client rather than the IDS one, for the same reason the experiment system has its own login:
 * `wlsy.xidian.edu.cn` authenticates with cookies the IDS jar knows nothing about, and one jar for
 * both would let a request to either host overwrite the other's session.
 *
 * Three settings are ports of the original's `Dio` configuration rather than preferences:
 *
 *  - **redirects are disabled.** A login that works answers `302`, and that status *is* the verdict —
 *    following it would turn a refused login and an accepted one into the same response;
 *  - **non-2xx responses do not throw**, for the same reason: the failure is read from the status and
 *    the body, not from an exception;
 *  - **no cookie jar is installed.** The original built its `Cookie` header by hand from the login
 *    response — dropping the `HttpOnly` cookies the engine could not read and substituting the student
 *    name — and [ExperimentScheduleApi] does the same. A jar would store the cookies *and* send them,
 *    and the two would disagree about which ones exist.
 */
class ExperimentHttpClient {

    val client: HttpClient = createPlatformHttpClient {
        followRedirects = false
        expectSuccess = false

        install(HttpTimeout) {
            // The same 10s connect / 30s receive the IDS client uses; these pages are heavier than the
            // registrar's JSON, so the receive timeout is what matters.
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }
}
