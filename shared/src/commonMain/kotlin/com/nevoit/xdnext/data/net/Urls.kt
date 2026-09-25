package com.nevoit.xdnext.data.net

import io.ktor.http.Url

/**
 * Resolves a `Location` header against the URL it arrived from.
 *
 * Ktor's redirect plugin is disabled for the CAS client because the redirect chain *is* the protocol
 * and each hop needs inspection, so relative locations have to be resolved here.
 *
 * Only the two forms a CAS server actually emits are handled: absolute URLs, and paths beginning with
 * `/`. A relative path without a leading slash is treated as rooted at the origin, which is correct
 * for every hop observed in this flow.
 */
internal fun resolveUrl(base: String, location: String): String {
    if (location.startsWith("http://", ignoreCase = true) ||
        location.startsWith("https://", ignoreCase = true)
    ) {
        return location
    }

    val url = Url(base)
    val port = if (url.port == url.protocol.defaultPort) "" else ":${url.port}"
    val origin = "${url.protocol.name}://${url.host}$port"

    return if (location.startsWith("/")) origin + location else "$origin/$location"
}
