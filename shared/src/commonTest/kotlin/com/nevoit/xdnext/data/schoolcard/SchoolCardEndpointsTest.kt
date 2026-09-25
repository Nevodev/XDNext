package com.nevoit.xdnext.data.schoolcard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the card system's URLs.
 *
 * The CAS `service` is the one value in this module that was **measured wrong**. The original handed
 * IDS the card system's OAuth entry point, and the live server answers that with HTTP 200 and its
 * 「应用未注册」 page — a redirector is not a registered application — so the app's parser reported a
 * missing field on a page that was never a login form.
 *
 * The correct service is a URL nested three deep, and one wrong escape makes IDS serve the login page
 * for a *different* application, which then fails much later as a missing `openid`. That is why the
 * built string is pinned here, character for character, against the value the live redirect chain
 * produced.
 */
class SchoolCardEndpointsTest {

    @Test
    fun buildsTheCasServiceTheLiveChainUses() {
        assertEquals(
            "https://xxcapp.xidian.edu.cn/a_xidian/api/cas-login/index" +
                    "?redirect=https%3A%2F%2Fxxcapp.xidian.edu.cn%2F%2Fuc%2Fapi%2Foauth%2Findex" +
                    "%3Fredirect%3Dhttps%253A%252F%252Fv8scan.xidian.edu.cn%252Fhome%252FopenHomePage" +
                    "%26appid%3D200200923164459783%26state%3DSTATE" +
                    "&from=wap",
            SchoolCardEndpoints.CAS_LOGIN_TARGET,
        )
    }

    @Test
    fun escapesEachNestedLayerExactlyOnce() {
        val service = SchoolCardEndpoints.CAS_LOGIN_TARGET

        // The outer layer escapes the campus app's OAuth URL, so its own query separator survives as
        // %3F and its parameter separators as %26.
        assertTrue(service.contains("%3Fredirect%3D"), "The inner query must be escaped.")
        // The inner layer escapes the card callback, so *its* separators survive double-escaped.
        assertTrue(
            service.contains("%253A%252F%252Fv8scan.xidian.edu.cn"),
            "The card callback must be escaped twice, once per layer.",
        )
        assertFalse(
            service.contains("v8scan.xidian.edu.cn/home/openHomePage&"),
            "A bare separator here would end the outer parameter early.",
        )
    }

    @Test
    fun keepsTheEntryPointAndTheCallbackApart() {
        // The entry point is where the chain starts, the callback is where it ends and where the handle
        // is. Using the first as the CAS service is the defect this module was fixed for.
        assertEquals(
            "https://v8scan.xidian.edu.cn/home/openXDOAuth2Page",
            SchoolCardEndpoints.OAUTH_ENTRY,
        )
        assertEquals(
            "https://v8scan.xidian.edu.cn/home/openHomePage",
            SchoolCardEndpoints.OAUTH_CALLBACK,
        )
        assertTrue(SchoolCardEndpoints.CAS_LOGIN_TARGET.contains(SchoolCardEndpoints.APP_ID))
    }
}
