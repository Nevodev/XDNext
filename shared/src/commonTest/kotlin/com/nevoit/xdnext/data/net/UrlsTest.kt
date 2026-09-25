package com.nevoit.xdnext.data.net

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the `Location` resolver used by the CAS redirect walker.
 *
 * The redirect chain is the login protocol, so this helper is on the critical path: a wrong hop means
 * a service never receives its ticket, and the failure surfaces much later as an unrelated-looking
 * "not logged in" error from a business module.
 */
class UrlsTest {

    @Test
    fun keepsAbsoluteUrlsUntouched() {
        assertEquals(
            "https://ehall.xidian.edu.cn/new/index.html",
            resolveUrl(
                base = "https://ids.xidian.edu.cn/authserver/login",
                location = "https://ehall.xidian.edu.cn/new/index.html",
            ),
        )
    }

    @Test
    fun resolvesAbsolutePathsAgainstTheOrigin() {
        assertEquals(
            "https://ids.xidian.edu.cn/authserver/login?service=x",
            resolveUrl(
                base = "https://ids.xidian.edu.cn/authserver/login",
                location = "/authserver/login?service=x",
            ),
        )
    }

    @Test
    fun resolvesRelativePathsAgainstTheOrigin() {
        assertEquals(
            "https://ids.xidian.edu.cn/authserver/common/openSliderCaptcha.htl",
            resolveUrl(
                base = "https://ids.xidian.edu.cn/authserver/login",
                location = "authserver/common/openSliderCaptcha.htl",
            ),
        )
    }

    @Test
    fun omitsThePortWhenItIsTheProtocolDefault() {
        assertEquals(
            "https://ids.xidian.edu.cn/x",
            resolveUrl(base = "https://ids.xidian.edu.cn:443/authserver/login", location = "/x"),
        )
    }

    @Test
    fun keepsANonDefaultPort() {
        assertEquals(
            "https://ids.xidian.edu.cn:8443/x",
            resolveUrl(base = "https://ids.xidian.edu.cn:8443/authserver/login", location = "/x"),
        )
    }
}
