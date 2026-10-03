package com.nevoit.xdnext.data.experiment

import com.fleeksoft.charset.Charset
import com.fleeksoft.charset.Charsets
import com.fleeksoft.charset.decodeToString
import com.nevoit.xdnext.core.log.appLog

/**
 * The charset the lab site's pages are written in.
 *
 * `wlsy.xidian.edu.cn` is a WebForms application from before UTF-8 was the default, and every page it
 * serves — the login form, the booking table, the plan grid — is **GB18030**. Reading any of them as
 * UTF-8 turns every Chinese name on them into replacement characters, which is why the bytes are
 * fetched and decoded here rather than through Ktor's text body: the original asked its own HTTP layer
 * to decode with the first available charset whose name contains `18030`, and this is the same
 * decision with the name written down.
 *
 * The fallback chain is not a preference: `GBK` is the same encoding for everything the site serves
 * and is what some Android builds expose under that name instead, and UTF-8 stands in for a platform
 * that has neither — which would decode the pages wrongly rather than not at all, and is the only
 * branch a log line will show.
 */
internal val SiteCharset: Charset by lazy {
    listOf("GB18030", "GBK")
        .firstNotNullOfOrNull { name -> runCatching { Charsets.forName(name) }.getOrNull() }
        .also { if (it == null) appLog.w { "[Experiment] No GB18030 or GBK charset on this platform" } }
        ?: Charsets.UTF8
}

/** The site's bytes as text. */
internal fun ByteArray.decodeSiteText(): String = decodeToString(SiteCharset)
