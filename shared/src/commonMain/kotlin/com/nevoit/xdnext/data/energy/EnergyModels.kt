package com.nevoit.xdnext.data.energy

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor

/**
 * One meter reading, as `GetMetRead` reports it.
 *
 * Ported from `lib/model/xidian_ids/energy.dart`. The wire names are capitalised (`ReadTime`,
 * `ReadNum`, …) and the original also used them for its on-disk cache, so they are kept verbatim:
 * a change here would silently invalidate every cached value.
 *
 * [readTime] is a [LocalDate] rather than the original's full `DateTime`. The dates the server
 * reports are whole days, the screens only ever format them as `yyyy-MM-dd`, and the "same day as"
 * comparison the history makes is what `LocalDate` equality *is*. The time component would be
 * dropped at every single call site of the original.
 *
 * The wire type of these numbers is not stable: `LastNum` in the meter list is parsed with
 * `num.parse(...toString())` in the original because the server has been seen quoting it. Reading
 * meters is therefore parsed leniently — see [parseMeterInfo].
 */
@Serializable
data class MeterInfo(
    @SerialName("ReadTime") val readTime: LocalDate,
    @SerialName("ReadNum") val readNum: Double,
    @SerialName("StartNum") val startNum: Double,
    @SerialName("EndNum") val endNum: Double,
)

/**
 * The electricity account's current state, as returned by [EnergySession].
 *
 * [electricityRemain] is the meter's `LastNum` — the remaining balance in kWh, not a reading.
 * [waterMeterList] is null when the water meter query failed, which the original tolerated on
 * purpose: a missing water reading must not take the electricity reading down with it.
 */
@Serializable
data class EnergyInfo(
    val lastReadDate: LocalDate,
    val electricityRemain: Double,
    val electricityMeterList: List<MeterInfo>,
    val waterMeterList: List<MeterInfo>? = null,
) {

    /**
     * The balance as the original wrote it on screen.
     *
     * The original appended Dart's `num.toString()`, and a JSON integer arrives there as an `int`, so
     * 25 printed as `25`. Kotlin's `Double.toString()` would print `25.0`, which is the same number and
     * a visibly different sentence — hence [asPlainNumber].
     */
    val electricityRemainText: String get() = electricityRemain.asPlainNumber()

    /**
     * The date shown under the balance: the **first** reading's day.
     *
     * That is what the original formatted there, rather than the meter's own last read date. It indexed
     * `electricityMeterList.first`, which would throw on an empty list; a window with no readings at
     * all falls back to [lastReadDate] here, which is the field the query window was built from.
     */
    val lastReadText: String
        get() = (electricityMeterList.firstOrNull()?.readTime ?: lastReadDate).format(ReadDay)
}

/**
 * The card's date: `4月22日`.
 *
 * A file-level value rather than one built per [EnergyInfo], because a reading is decoded every time the
 * cache is read and a fresh one every time the page refreshes; the format itself is immutable and shared.
 *
 * `Padding.NONE` on the month **and** the day is not decoration: both `monthNumber()` and `day()` pad
 * by default, so the obvious spelling of this format prints `04月05日`.
 */
private val ReadDay = LocalDate.Format {
    monthNumber(Padding.NONE)
    chars("月")
    day(Padding.NONE)
    chars("日")
}

/**
 * Renders a number the way Dart's `num.toString()` did for the values this protocol carries: a whole
 * number has no decimal part, anything else keeps the shortest form Kotlin produces.
 *
 * The guard matters: above 2^53 a `Double` cannot represent every integer, so converting one to `Long`
 * would print digits the server never sent.
 */
internal fun Double.asPlainNumber(): String =
    if (isFinite() && this == floor(this) && abs(this) <= MAX_EXACT_INTEGRAL) {
        toLong().toString()
    } else {
        toString()
    }

/** 2^53: the largest magnitude up to which every integer is exactly representable. */
private const val MAX_EXACT_INTEGRAL = 9_007_199_254_740_992.0

/**
 * One point of the locally-kept "remaining electricity over time" series.
 *
 * [remain] is a string because the original stored `electricityRemain.toString()`; it is carried
 * through unchanged so the two are comparable and no rounding is invented here.
 */
@Serializable
data class ElectricityHistoryInfo(
    val fetchDay: LocalDate,
    val remain: String,
)

/**
 * Why a cached reading is being shown instead of a fresh one.
 *
 * Ported from `EnergySession._cacheHintFromError`. The original answered a translation key here
 * because it had an i18n layer; this app has none yet, so each variant carries both the original's
 * key — kept intact so the original's translations can be reused verbatim later — and the sentence
 * the UI shows today.
 *
 * One variant of the original is deliberately absent. It could report "不在校园网" because its own
 * HTTP layer raised a dedicated `NotSchoolNetworkException`; nothing in this port can tell that
 * failure apart from any other transport failure, so it is reported as [NETWORK_FAILED] — whose
 * message names the campus network, which is the actionable part.
 */
enum class EnergyCacheHint(val key: String, val message: String) {
    ACCOUNT_MISSING(
        "electricity.cache_hint_account_missing",
        "尚未保存账号密码，显示的是上次的用电信息",
    ),
    ACCOUNT_PARSE_FAILED(
        "electricity.cache_hint_account_parse_failed",
        "账号信息解析失败，显示的是上次的用电信息",
    ),
    CAPTCHA_FAILED(
        "electricity.cache_hint_captcha_failed",
        "验证码校验失败，显示的是上次的用电信息",
    ),
    PASSWORD_WRONG(
        "electricity.cache_hint_password_wrong",
        "用户名或密码有误，显示的是上次的用电信息",
    ),
    LOGIN_FAILED(
        "electricity.cache_hint_login_failed",
        "登录失败，显示的是上次的用电信息",
    ),
    NETWORK_FAILED(
        "electricity.cache_hint_network_failed",
        "网络请求失败，请确认已连接校园网，显示的是上次的用电信息",
    ),
    UNKNOWN_ERROR(
        "electricity.cache_hint_unknown_error",
        "未能获取用电信息，显示的是上次的用电信息",
    ),
}
