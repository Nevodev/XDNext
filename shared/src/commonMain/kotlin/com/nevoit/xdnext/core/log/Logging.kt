package com.nevoit.xdnext.core.log

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter

/**
 * Application-wide logger.
 *
 * The original Flutter app leaked plaintext passwords into its logs: the Dio logger printed request
 * bodies and only filtered by URL host and a handful of query keys. Logging here is centralised and
 * [redacted] exists so a value can be traced without becoming readable.
 */
val appLog: Logger = Logger.withTag("XDNext")

/**
 * Routes [appLog] to the platform's own log, so its output is not discarded.
 *
 * Kermit ships with **no** writers configured, which means every line this app logs goes nowhere until
 * this is called. That is not a small thing: a stuck request, a rejected login and a parser that
 * dropped a row all report themselves only through `appLog`, and a run on a device with the writers
 * unset is a run with no evidence at all.
 *
 * Called once from the platform entry point before anything else can log. It is idempotent, so a
 * second call is harmless.
 */
fun installPlatformLogging() {
    Logger.setLogWriters(listOf(platformLogWriter()))
}

/**
 * Renders a sensitive value as a fixed-width marker that still tells you whether it was present.
 *
 * Use this instead of interpolating a password, cookie, ticket or fingerprint directly.
 */
fun redacted(value: String?): String = when {
    value == null -> "<null>"
    value.isEmpty() -> "<empty>"
    // Length is not a secret; contents are. Knowing the length is what makes this useful in a log.
    else -> "<redacted:${value.length}>"
}
