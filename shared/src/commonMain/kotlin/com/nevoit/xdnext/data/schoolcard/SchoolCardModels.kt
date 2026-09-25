package com.nevoit.xdnext.data.schoolcard

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One row of `queryCardSelfTradeList`, with the transaction's own amount rather than a balance.
 *
 * Ported from `lib/model/xidian_ids/paid_record.dart`, whose fields are the wire names verbatim:
 * `mername`, `txdate`, `txamt`. The original kept all three as `String` and parsed the amount with
 * `double.parse` at the point of use; the amount stays a string here for the same reason the energy
 * module keeps its wire values loose — the server has been seen quoting numbers and sending them
 * bare, and [parseCardRecord] reads both.
 */
internal data class SchoolCardRecord(
    val merchant: String,
    val date: String,
    val amount: String,
)

/**
 * The campus card's last known state: the balance, and what has been spent today.
 *
 * Two numbers that come from two calls. The original queried them in two entirely separate places —
 * the balance on the home page (`school_card_info_card.dart`, via `openMyAccount`) and the day's
 * transactions on the card page (`school_card_window.dart`, via `queryCardSelfTradeList`) — so this
 * holds both without pretending one implies the other.
 *
 * Nothing counts transactions. A `null` [todayExpenseCents] already says "not read", and a `0` says
 * "nothing spent", so a record *count* would be a third way to say the same two things — and the tile
 * shows the balance on the line below regardless, so it had no reader.
 */
data class SchoolCardSnapshot(
    /** The card's balance, as the account page spells it. Null when only the transactions could be read. */
    val balance: String?,
    /** Today's spending in cents, or null when the transaction query failed. */
    val todayExpenseCents: Long?,
    /** Today's top-ups in cents, tracked so a day that both spent and added reads honestly. */
    val todayIncomeCents: Long,
)

/**
 * The balance in yuan: `12.34`, `0.00`, and never a half-written number.
 *
 * The original printed the scraped string straight through, which is why the card read "卡里 25 元"
 * for a whole number and "卡里 25.6 元" for one decimal. Two decimals always is what an amount of
 * money should look like; the loss of the original's brevity is deliberate.
 */
fun centsToYuanText(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val magnitude = if (cents < 0) -cents else cents
    return "$sign${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')}"
}

/**
 * Parses an amount of yuan into whole cents.
 *
 * Rounds half away from zero, `BigDecimal`'s and `Math.round`'s own rule, rather than Kotlin's
 * `roundToLong`, which rounds half toward positive infinity and would turn `-0.005` into `-0.00`.
 *
 * Returns null for anything that is not a number, so a blank or malformed field becomes "this record
 * has no amount" instead of being read as zero — the latter would quietly understate a day's spending.
 */
internal fun parseYuanToCents(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val negative = text.startsWith("-")
    val digits = text.removePrefix("-").removePrefix("+")
    val whole = digits.substringBefore('.')
    val fraction = digits.substringAfter('.', "")
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (!whole.all { it.isDigit() } || !fraction.all { it.isDigit() }) return null

    val wholePart = whole.ifEmpty { "0" }.toLongOrNull() ?: return null
    val wholeCents = wholePart * 100
    val fractionCents = when {
        fraction.length <= 2 -> fraction.padEnd(2, '0').toLongOrNull() ?: return null
        else -> {
            val kept = fraction.take(2).toLongOrNull() ?: return null
            if (fraction[2] >= '5') kept + 1 else kept
        }
    }

    val total = wholeCents + fractionCents
    return if (negative) -total else total
}

/**
 * Parses one row, or null when it carries no amount.
 *
 * A row without a usable amount is dropped rather than defaulted to zero: the sum is a statement
 * about money, and a fabricated zero is a wrong answer rather than a missing one.
 *
 * The amount is validated here but kept **as the wire wrote it**. Normalising it to two decimals would
 * make it a display string, and [todayExpenseCents] parses it a second time — which turns `-12.30`
 * into `-12.30` cents and a purchase into income.
 */
internal fun parseCardRecord(row: JsonObject): SchoolCardRecord? {
    val raw = (row["txamt"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: return null
    parseYuanToCents(raw) ?: return null
    return SchoolCardRecord(
        merchant = (row["mername"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty(),
        date = (row["txdate"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty(),
        amount = raw.trim(),
    )
}

/**
 * Today's spending, as the original summed it.
 *
 * The original's rule, in `school_card_window.dart`'s `moneySunUp`, is a plain sum of the records'
 * amounts with the sign deciding how the total is *worded*: negative prints 支出, anything else prints
 * 收入. That is what is reproduced here — a purchase is negative, a top-up positive — with one
 * difference: the two are kept apart instead of one netting against the other, because the tile is
 * labelled 今日支出 and a 200 元 top-up must not read as a 187.70 元 day of spending.
 *
 * The comparison is on the date *part* of `txdate`, so the server's own timestamp format — `2026-03-05`,
 * or the same day with a time appended — does not decide whether a record counts.
 *
 * ### The unit
 *
 * The arithmetic is in whole cents, exactly, because a sum of `Double`s is where money goes wrong:
 * `0.1 + 0.2` is not `0.3`, and a day of small purchases would print a total the server never sent.
 */
internal fun todayExpenseCents(records: List<SchoolCardRecord>, today: LocalDate): Long =
    records.filter { it.date.take(10) == today.toString() }
        .mapNotNull { parseYuanToCents(it.amount) }
        .filter { it < 0 }
        // Negated per amount rather than on the total: `Long.MIN_VALUE` cannot be negated, and one
        // absurd record must not take the whole sum with it.
        .sumOf { -it }

/** Today's top-ups in cents, the counterpart to [todayExpenseCents]. */
internal fun todayIncomeCents(records: List<SchoolCardRecord>, today: LocalDate): Long =
    records.filter { it.date.take(10) == today.toString() }
        .mapNotNull { parseYuanToCents(it.amount) }
        .filter { it > 0 }
        .sum()

/**
 * The wire date format: `2026-03-05`.
 *
 * `Padding.NONE` on every field would drop the leading zeros the server's own query wants, so the month
 * and day are padded explicitly rather than left to the default: spelled out here so the format is
 * pinned by [SchoolCardModelsTest] instead of assumed.
 */
private val CardQueryDate = LocalDate.Format {
    year()
    chars("-")
    monthNumber(Padding.ZERO)
    chars("-")
    day(Padding.ZERO)
}

/** A [LocalDate] as the query wants it: `2026-03-05`. */
internal fun LocalDate.toCardQueryDate(): String = format(CardQueryDate)

/**
 * Why a cached balance is being shown instead of a fresh one.
 *
 * The energy module's shape, and the original's own idea — it answered a translation key here. Each
 * variant carries both that key, kept intact so the original's translations can be reused verbatim
 * when this app grows an i18n layer, and the sentence the UI shows today. The variants are the energy
 * ones because the failures are the same ones: both modules sit behind the same IDS session.
 */
enum class SchoolCardCacheHint(val key: String, val message: String) {
    ACCOUNT_MISSING(
        "school_card.cache_hint_account_missing",
        "尚未保存账号密码，显示的是上次的余额",
    ),
    ACCOUNT_PARSE_FAILED(
        "school_card.cache_hint_account_parse_failed",
        "校园卡信息解析失败，显示的是上次的余额",
    ),
    CAPTCHA_FAILED(
        "school_card.cache_hint_captcha_failed",
        "验证码校验失败，显示的是上次的余额",
    ),
    PASSWORD_WRONG(
        "school_card.cache_hint_password_wrong",
        "用户名或密码有误，显示的是上次的余额",
    ),
    LOGIN_FAILED(
        "school_card.cache_hint_login_failed",
        "登录失败，显示的是上次的余额",
    ),
    NETWORK_FAILED(
        "school_card.cache_hint_network_failed",
        "网络请求失败，请确认已连接校园网，显示的是上次的余额",
    ),
    UNKNOWN_ERROR(
        "school_card.cache_hint_unknown_error",
        "未能获取校园卡信息，显示的是上次的余额",
    ),
}
