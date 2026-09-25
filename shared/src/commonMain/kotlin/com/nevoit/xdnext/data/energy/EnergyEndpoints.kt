package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.data.energy.EnergyEndpoints.CONTENT_IV
import com.nevoit.xdnext.data.energy.EnergyEndpoints.CONTENT_KEY
import com.nevoit.xdnext.data.energy.EnergyEndpoints.GET_SIGNATURE


/**
 * Endpoints and constants of the new energy management system.
 *
 * Ported from `lib/repository/ids_session/energy_session.dart`. The system went online on 2026-04-22
 * and, unlike the rest of the school's services, is **only reachable from the campus network**.
 *
 * Two things about this protocol are easy to get wrong and are pinned here rather than in the call
 * sites:
 *
 *  - every business call is an AES-encrypted `content` parameter *and* carries a signature that is
 *    minted per request by [GET_SIGNATURE] — the signature is not derived from the payload, so a
 *    caller that forgets to re-mint it after a retry sends a stale header pair;
 *  - the fixed key and IV are equal and are the ASCII digits `1234567812345678`. This is obfuscation,
 *    not encryption: the key ships in the client. It is reproduced because the server decrypts with
 *    it, nothing more.
 */
object EnergyEndpoints {

    const val ENERGY_ORIGIN = "https://ignypt.xidian.edu.cn"

    /** Mints the per-request `timestamp`/`signature` header pair. */
    const val GET_SIGNATURE = "$ENERGY_ORIGIN/baseNew/api/User/GetSignature"

    /** Exchanges the CAS-issued `code` for a WeChat-side user identity. */
    const val OAUTH_GET_USER_INFO = "$ENERGY_ORIGIN/estManage/api/WeChat/V2/OauthGetUserInfo"

    /** Signs in to the energy system as the IDS account and yields the node that owns the meters. */
    const val H5_USER_ID_LOGIN = "$ENERGY_ORIGIN/estManage/api/WeChat/V2/H5UserIDLogIn"

    /** Lists the meters under a node: the electricity reading and, when present, the water one. */
    const val H5_QUERY_METER_LIST = "$ENERGY_ORIGIN/estManage/api/WeChat/V2/H5QueryMeterList"

    /** Reads one meter's readings over a date range. */
    const val GET_METER_READ = "$ENERGY_ORIGIN/estManage/api/WeChat/V2/GetMetRead"

    /** The business code the system expects in the body, the signature request and the headers. */
    const val OP_CODE = "MPAY"

    /** Content-encryption key. Equal to [CONTENT_IV], as in the original. */
    const val CONTENT_KEY = "1234567812345678"

    /** Content-encryption IV. Equal to [CONTENT_KEY], as in the original. */
    const val CONTENT_IV = "1234567812345678"

    /**
     * The CAS service the energy system is entered through.
     *
     * The nested `redirect` parameter is part of the service string, and its `appid` identifies this
     * application to the energy system's OAuth endpoint. Reproduced verbatim.
     */
    const val LOGIN_TARGET =
        "https://xxcapp.xidian.edu.cn/uc/api/oauth/index?" +
                "redirect=https://ignypt.xidian.edu.cn/revenueH5/login?" +
                "opcode=MPAY&appid=200260318155520600&state=12312312312312&qrcode=0"

    /**
     * `MediumCode` of the electricity meter, as the server numbers them.
     *
     * The original did not match on this value directly: it read the *first* row's code and assumed
     * the other row was the other medium. See [com.nevoit.xdnext.data.energy.EnergySession].
     */
    const val ELECTRICITY_MEDIUM_CODE = "2"
}
