package com.nevoit.xdnext.data.schoolcard

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.serialization.Serializable
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
 * "nothing spent", so a record *count* would be a third way to say the same two things — and the page
 * that does want the count counts the rows it is drawing.
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
 * A window of days the card system is asked about: the card page's own selection, and the key its cache
 * answers under.
 *
 * Ported from the original's card page, which opened with `[now.firstDayOfMonth, now]` and re-queried
 * whenever the range picker came back — its two date parameters are the whole of the feature.
 *
 * A range is a *value*, and that is the point: the rows on screen are only meaningful together with the
 * days they were asked for, so the two travel as one thing and the cache is keyed by it — which is why
 * this is `@Serializable`: a stored answer is written down with the window it belongs to.
 */
@Serializable
data class SchoolCardRange(
    val from: LocalDate,
    val to: LocalDate,
) {

    init {
        // Loudly, rather than by swapping the ends behind the caller's back: a reversed range is a bug
        // in whoever built it, and silently "fixing" it hides which end was wrong.
        require(from <= to) { "a range's first day cannot be after its last: $from > $to" }
    }

    /** Whether the range is a single day — the shape the campus tile's own figure describes. */
    val isSingleDay: Boolean get() = from == to

    /** How many days the range covers, both ends included. */
    val dayCount: Int get() = from.daysUntil(to) + 1

    /** Whether [date] is one of the range's days. */
    fun contains(date: LocalDate): Boolean = date >= from && date <= to

    /**
     * Whether this range covers [other] entirely — which is what a cached answer must do to be reused.
     *
     * Covering, not merely overlapping: the rows behind a range are the rows *in* it, so a cache that
     * held only part of the days would answer with a list that is quietly missing some of them.
     */
    fun covers(other: SchoolCardRange): Boolean = from <= other.from && other.to <= to

    companion object {
        /**
         * The range between two days in either order, as two taps on a calendar hand them over.
         *
         * The picker's second tap is what decides which end is which, and a user who picks the later day
         * first means the same days as one who picks them the other way round.
         */
        fun of(first: LocalDate, second: LocalDate): SchoolCardRange =
            if (first <= second) SchoolCardRange(first, second) else SchoolCardRange(second, first)
    }
}

/**
 * One flow on the card page: where the money was spent or added, when, and how much.
 *
 * The wire row ([SchoolCardRecord]) as a *person* reads it, with the amount already in cents: the page
 * prints these, sums these, and never re-reads the wire string, which is what makes a row's amount and
 * the range's total the same number rather than two parsings of it.
 *
 * [amountCents] carries the direction as its sign — a purchase is negative, a top-up positive — as the
 * original's own rule had it. There is no separate "is income" field, because a second way to say what
 * the sign says is a second thing to keep in step.
 *
 * Serialisable because the cache stores a query's rows beside the range they answer; see
 * [SchoolCardTradeListCache].
 */
@Serializable
data class SchoolCardTransaction(
    /** The merchant as the card system names it. Empty when the row carried none. */
    val merchant: String,
    /** The wire `txdate` verbatim, e.g. `2026-03-05 12:03:11`. */
    val time: String,
    /** The amount in whole cents, negative for spending. */
    val amountCents: Long,
) {

    /** Whether this row is money out. */
    val isExpense: Boolean get() = amountCents < 0

    /**
     * The amount as the list prints it: `-12.30` for a purchase, `+200.00` for a top-up.
     *
     * The sign is always written, so a column of amounts says which way each one went without being
     * read against a colour — the original printed the wire string through, which left a top-up and a
     * purchase distinguishable only by a minus sign that the server may or may not send.
     */
    val amountText: String
        get() = if (amountCents > 0) "+${centsToYuanText(amountCents)}" else centsToYuanText(amountCents)

    /**
     * The stamp as a single day's list prints it: `12:03:11` for a stamp that carries a time, and the
     * whole stamp otherwise.
     *
     * The date part is dropped because the list's own heading names the day, and repeating it on every
     * row would leave the column that matters — the time — a few characters wide. A row whose stamp is a
     * bare date keeps it: something is better than a blank cell, and the fallback says which shape the
     * server sent.
     */
    val timeText: String
        get() = time.substringAfter(' ', "").trim().ifEmpty { time }

    /**
     * The stamp as a list covering more than one day prints it: `03-05 12:03:11`.
     *
     * A multi-day list has to say which day each row belongs to, and the year is left off for the same
     * reason the date is left off a single day's: every range a person picks sits inside one year, and
     * the four characters it costs are four the time needs.
     */
    fun stampText(withDate: Boolean): String =
        if (!withDate) timeText else time.removePrefix("${time.take(4)}-").ifEmpty { time }
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
 * make it a display string, and [transactionsIn] parses it a second time — which turns `-12.30`
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
 * The rows a range covers, newest first — what the page draws and what its figures are summed from.
 *
 * This is the one place that decides which rows belong to which days, and both the totals and the list
 * come through it, so a page's 支出 and its own rows cannot disagree about a record. The original had
 * that filter twice: `moneySunUp` summed whatever the *previous* query had returned, and the table under
 * it drew the same list, which is only the same answer while nothing else touches it.
 *
 * The date is *parsed* rather than compared as a string. The server's stamp is a date with an optional
 * time appended — `2026-03-05` or `2026-03-05 12:03:11` — and a row whose date cannot be read is
 * dropped here rather than guessed at, because a row put in the wrong day is a wrong total under a
 * heading that says otherwise.
 *
 * Ordering is by the wire stamp, so comparing the strings compares the moments: the newest row leads,
 * which is the one a user opens the list for. A stamp without a time sorts behind the same day's
 * timestamped rows, which is what "the time is unknown, so it goes last" should look like.
 */
internal fun transactionsIn(
    records: List<SchoolCardRecord>,
    range: SchoolCardRange,
): List<SchoolCardTransaction> =
    records.filter { record -> record.date.toLocalDateOrNull()?.let(range::contains) == true }
        .mapNotNull { record ->
            parseYuanToCents(record.amount)?.let { cents ->
                SchoolCardTransaction(
                    merchant = record.merchant,
                    time = record.date,
                    amountCents = cents,
                )
            }
        }
        .sortedByDescending { it.time }

/**
 * What the rows cost, in whole cents and as a positive number.
 *
 * ### The unit
 *
 * The arithmetic is in whole cents, exactly, because a sum of `Double`s is where money goes wrong:
 * `0.1 + 0.2` is not `0.3`, and a day of small purchases would print a total the server never sent.
 *
 * ### The sign
 *
 * Spending and top-ups are kept apart instead of one netting against the other, which is where this
 * leaves the original: it summed `moneySunUp`'s amounts together and chose its *wording* from the sign,
 * so a 200 元 top-up turned a 12.30 元 day into an income of 187.70 元 under a heading that said 合计.
 *
 * The negation is per amount rather than on the total: `Long.MIN_VALUE` cannot be negated, and one
 * absurd row must not take the whole sum with it.
 */
internal fun List<SchoolCardTransaction>.expenseCents(): Long =
    filter { it.isExpense }.sumOf { -it.amountCents }

/** What was added to the card, the counterpart to [expenseCents]. */
internal fun List<SchoolCardTransaction>.incomeCents(): Long =
    filter { it.amountCents > 0 }.sumOf { it.amountCents }

/**
 * The date part of a wire stamp as a day, or null when it is not one.
 *
 * Ten characters rather than a split on `-`, because the two shapes the server sends are a bare date and
 * a date with a time: what comes before the first ten characters is a day in both, and anything that is
 * not one is refused instead of being read as a day anyway.
 */
private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(take(10)) }.getOrNull()

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
 * Why a cached answer is being shown instead of a fresh one.
 *
 * The energy module's shape, and the original's own idea — it answered a translation key here. Each
 * variant carries both that key, kept intact so the original's translations can be reused verbatim
 * when this app grows an i18n layer, and the sentences the UI shows today. The variants are the energy
 * ones because the failures are the same ones: both modules sit behind the same IDS session.
 *
 * Two sentences, because the card page shows two things that can be stale independently: the card's
 * balance ([message]) and the flow list ([flowsMessage]). The failure is the same one; what it leaves on
 * screen is not.
 */
enum class SchoolCardCacheHint(
    val key: String,
    val message: String,
    val flowsMessage: String,
) {
    ACCOUNT_MISSING(
        "school_card.cache_hint_account_missing",
        "尚未保存账号密码，显示的是上次的余额",
        "尚未保存账号密码，显示的是上次的流水",
    ),
    ACCOUNT_PARSE_FAILED(
        "school_card.cache_hint_account_parse_failed",
        "校园卡信息解析失败，显示的是上次的余额",
        "校园卡信息解析失败，显示的是上次的流水",
    ),
    CAPTCHA_FAILED(
        "school_card.cache_hint_captcha_failed",
        "验证码校验失败，显示的是上次的余额",
        "验证码校验失败，显示的是上次的流水",
    ),
    PASSWORD_WRONG(
        "school_card.cache_hint_password_wrong",
        "用户名或密码有误，显示的是上次的余额",
        "用户名或密码有误，显示的是上次的流水",
    ),
    LOGIN_FAILED(
        "school_card.cache_hint_login_failed",
        "登录失败，显示的是上次的余额",
        "登录失败，显示的是上次的流水",
    ),
    NETWORK_FAILED(
        "school_card.cache_hint_network_failed",
        "网络请求失败，请确认已连接校园网，显示的是上次的余额",
        "网络请求失败，请确认已连接校园网，显示的是上次的流水",
    ),
    UNKNOWN_ERROR(
        "school_card.cache_hint_unknown_error",
        "未能获取校园卡信息，显示的是上次的余额",
        "未能获取校园卡信息，显示的是上次的流水",
    ),
}
