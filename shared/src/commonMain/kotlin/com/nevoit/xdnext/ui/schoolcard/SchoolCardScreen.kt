package com.nevoit.xdnext.ui.schoolcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nevoit.material.core.component.Button
import com.nevoit.material.core.component.CircularProgressIndicator
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TopBarHeight
import com.nevoit.material.core.component.VDivider
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.layout.VerticalStack
import com.nevoit.material.core.layout.isScrolledPast
import com.nevoit.material.core.utility.NavigationBarSpacer
import com.nevoit.material.core.utility.StatusBarSpacer
import com.nevoit.material.theme.MaterialShapes
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.toShape
import com.nevoit.xdnext.data.schoolcard.SchoolCardFlowsRepository
import com.nevoit.xdnext.data.schoolcard.SchoolCardFlowsState
import com.nevoit.xdnext.data.schoolcard.SchoolCardTransaction
import com.nevoit.xdnext.data.schoolcard.centsToYuanText
import com.nevoit.xdnext.ui.home.Card
import com.nevoit.xdnext.ui.home.IconBox
import com.nevoit.xdnext.ui.shared.AppTopBar
import com.nevoit.xdnext.ui.shared.BlurShade
import com.nevoit.xdnext.ui.shared.SummaryCard
import com.nevoit.xdnext.ui.shared.rememberCardValueFontFamily
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * 校园卡 — the card's flows, over the days the user picks.
 *
 * The original's `school_card_window.dart`, kept whole: a window of days that the page queries, a button
 * that opens a calendar to change it, and the rows newest first. What it adds is the card the user asked
 * for at the top — 今日支出, the same figure the campus tile shows — and the cache the original never had.
 *
 * The page is drawn over the shell, so it carries its own back control, its own bar and the shade the
 * other overlay pages have. The arrangement — spacers, bar, shade — is `EnergyScreen`'s, deliberately, so
 * the overlay pages read as one app.
 *
 * The QR code the original's card page also carried is **not** here: paying needs the card system's
 * virtual-card page and a barcode to render it from, which is its own piece of work.
 */
@Composable
fun SchoolCardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: SchoolCardFlowsRepository = koinInject()
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { repository.refresh() } }
    var picking by remember { mutableStateOf(false) }

    val today =
        remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    // The page queries when it opens, whatever it already has: this is a question about money, which
    // changes while the app sits there, and the cache is what covers the seconds the query takes rather
    // than a reason to skip it — the original's own page re-queried on every open too. It is not a *retry*
    // either: nothing here runs twice, and a failed attempt leaves the cached rows on screen with the
    // reason beside them until the user asks again.
    LaunchedEffect(repository) {
        if (!repository.state.value.isLoading) repository.refresh()
    }

    val listState = rememberLazyListState()
    val colors = MaterialTheme.colors
    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = colors.pageBackground,
            topLeft = Offset(-this.size.width, -this.size.height),
            size = Size(this.size.width * 3, this.size.height * 3)
        )
        drawContent()
    }

    val shadeVisible by listState.isScrolledPast(0.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.pageBackground),
    ) {
        VerticalStack(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            item {
                StatusBarSpacer()
                VGap(TopBarHeight)
            }
            item {
                TodaySummary(state = state, today = today)
                VGap(24.dp)
            }
            item {
                RangeCard(
                    state = state,
                    today = today,
                    onOpenPicker = { picking = true },
                )
                VGap(24.dp)
            }
            item {
                FlowsCard(state = state, onRefresh = refresh)
                VGap()
            }
            item {
                NavigationBarSpacer()
            }
        }
        BlurShade(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(TopBarHeight),
            backdrop = backdrop,
            visible = shadeVisible
        )
        AppTopBar(
            title = "校园卡",
            onBack = onBack,
            trailing = {
                IconButton(
                    enabled = !state.isLoading,
                    onClick = refresh,
                ) {
                    SymbolIcon(name = Symbol.Refresh, contentDescription = "刷新校园卡")
                }
            },
        )
    }

    if (picking) {
        SchoolCardRangePickerDialog(
            initial = state.range,
            today = today,
            onDismiss = { picking = false },
            onConfirm = { range ->
                picking = false
                scope.launch { repository.selectRange(range) }
            },
        )
    }
}

/**
 * The page's reading: what the chosen days cost, as the shared [SummaryCard] with this screen's words in
 * it.
 *
 * The figure is summed from the rows under it rather than asked for separately, so the number and the
 * list cannot be two readings of the same days — and 今日支出 is not a second query either: it is what
 * this says while the range is the one day the campus tile counts.
 */
@Composable
private fun TodaySummary(state: SchoolCardFlowsState, today: LocalDate) {
    SummaryCard(
        value = when {
            state.isRead -> centsToYuanText(state.expenseCents)
            state.error != null -> "查询失败"
            else -> UnknownValue
        },
        unit = if (state.isRead) "元" else null,
        caption = summaryCaption(state, today),
        icon = Symbol.CreditCard,
        iconShape = MaterialShapes.Cookie4Sided.toShape(),
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/**
 * The line under the number: what the figure is, how many rows it covers, and why it is a cached one.
 *
 * The label says which window the number belongs to, because the card itself shows no label — and the
 * reason a cached list is on screen belongs on the same line as the value it describes, as it does on the
 * campus tile.
 */
private fun summaryCaption(state: SchoolCardFlowsState, today: LocalDate): String {
    if (!state.isRead) {
        return state.cacheHint?.flowsMessage ?: when {
            state.error != null -> "请检查网络后重试"
            else -> "正在查询流水"
        }
    }

    val line = "${rangeLabel(state.range, today)} · 共 ${state.transactions.size} 笔"
    return if (state.isFromCache) "$line · $CacheNote" else line
}

/** The button that opens the calendar, carrying the days currently being shown. */
@Composable
private fun RangeCard(
    state: SchoolCardFlowsState,
    today: LocalDate,
    onOpenPicker: () -> Unit,
) {
    Card(onClick = onOpenPicker, modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBox(
                icon = Symbol.Calendar,
                shape = MaterialShapes.Pill.toShape(),
                background = MaterialTheme.colors.primaryContainer,
                contentColor = MaterialTheme.colors.onPrimaryContainer,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatRangeText(state.range),
                    style = MaterialTheme.type.subHeadline,
                )
                Text(
                    text = when {
                        state.range.isSingleDay && state.range.from == today -> "点击选择其他日期"
                        else -> "共 ${state.range.dayCount} 天 · 点击修改区间"
                    },
                    style = MaterialTheme.type.footnote,
                    color = MaterialTheme.colors.contentVariant,
                )
            }
            SymbolIcon(
                name = Symbol.Chevron,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The flows and the four states a query can be in: rows, days read with nothing in them, a failure, and an
 * answer that has not arrived yet.
 *
 * Only the empty cases are decided here — a list that has rows keeps them while a refresh of the *same*
 * range is in flight, which is the same "the previous value stays on screen" rule the repositories follow.
 * A new range never reaches this point with old rows: the state holder clears them with the range.
 */
@Composable
private fun FlowsCard(
    state: SchoolCardFlowsState,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        when {
            state.transactions.isNotEmpty() -> FlowsTable(
                transactions = state.transactions,
                withDate = !state.range.isSingleDay,
                incomeCents = state.incomeCents,
                // A list that is on screen but may be out of date says why, in the card rather than in a
                // toast: the reason belongs beside the rows it describes.
                note = flowsNote(state),
            )

            // Days that were read and had nothing in them. Distinguished from "not read" by the fetch
            // time, which is what `isRead` reports — a failure leaves it unset.
            state.isRead -> FlowsMessage(
                title = if (state.range.isSingleDay) "这一天没有流水" else "这些天没有流水",
                detail = "统计的是校园卡的消费与充值",
            )

            state.cacheHint != null -> FlowsMessage(
                title = "尚未读到这个区间的流水",
                detail = state.cacheHint.flowsMessage,
                action = "重新查询",
                onAction = onRefresh,
            )

            state.error != null -> FlowsMessage(
                title = "流水查询失败",
                detail = "请检查网络后重试",
                action = "重新查询",
                onAction = onRefresh,
            )

            else -> FlowsMessage(
                title = "正在查询流水",
                detail = "首次查询可能需要一点时间",
                loading = true,
            )
        }
    }
}

/**
 * The rows as a table: a header, then one flow per line, newest at the top, and the range's income under
 * them when there is any.
 *
 * The columns are laid out by [FlowColumn], and the header and every row read the widths from there — the
 * same reason `WaterFeeTable` declares its own once: a header that places its cells independently of the
 * rows sits over none of them.
 *
 * The amounts are set in the cards' own face and with tabular figures, so a column of them lines up on the
 * decimal point rather than wandering with the digits' widths.
 *
 * The income is a line of its own rather than a fourth column: it belongs to the *window*, not to any row,
 * and the figure above the table is the window's spending — the pair is what the money in these days
 * actually did.
 */
@Composable
private fun FlowsTable(
    transactions: List<SchoolCardTransaction>,
    withDate: Boolean,
    incomeCents: Long,
    note: String?,
) {
    val colors = MaterialTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp)
        ) {
            FlowColumn.entries.forEach { column ->
                Text(
                    text = column.title,
                    modifier = Modifier.weight(column.weight),
                    style = MaterialTheme.type.footnoteEmphasized,
                    color = colors.contentVariant,
                    textAlign = column.align,
                )
            }
        }

        VDivider(modifier = Modifier.padding(horizontal = 16.dp))

        transactions.forEachIndexed { index, transaction ->
            if (index > 0) VDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = transaction.merchant.ifBlank { UnknownMerchant },
                    modifier = Modifier.weight(FlowColumn.Merchant.weight),
                    style = MaterialTheme.type.footnote,
                    color = colors.contentVariant,
                    textAlign = FlowColumn.Merchant.align,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = transaction.stampText(withDate),
                    modifier = Modifier.weight(FlowColumn.Time.weight),
                    style = MaterialTheme.type.footnote.copy(fontFeatureSettings = "tnum"),
                    color = colors.contentVariant,
                    textAlign = FlowColumn.Time.align,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = transaction.amountText,
                    modifier = Modifier.weight(FlowColumn.Amount.weight),
                    style = MaterialTheme.type.footnoteEmphasized.copy(fontFeatureSettings = "tnum"),
                    // A top-up is the one row a day's list is scanned for, so it is the one that takes
                    // the accent colour; spending is the list's ordinary case and stays in the page's own
                    // content colour.
                    color = if (transaction.isExpense) colors.content else colors.primary,
                    fontFamily = rememberCardValueFontFamily(),
                    textAlign = FlowColumn.Amount.align,
                    maxLines = 1,
                )
            }
        }

        if (incomeCents > 0) {
            VDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "充值 +${centsToYuanText(incomeCents)} 元",
                    style = MaterialTheme.type.footnoteEmphasized,
                    color = colors.primary,
                )
            }
        }

        if (note != null) {
            VDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                text = note,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.type.footnote,
                color = colors.contentVariant,
            )
        }
    }
}

/**
 * Why a list that is on screen may be out of date, or null when it is not.
 *
 * Two ways to end up with an old list: the query failed and the session answered from its cache (the
 * hint), or the refresh failed outright while earlier rows were still there. Both are worth a sentence,
 * and neither is worth hiding the rows for.
 */
private fun flowsNote(state: SchoolCardFlowsState): String? = when {
    state.cacheHint != null -> state.cacheHint.flowsMessage
    state.error != null -> "刷新失败，显示的是上次的流水"
    else -> null
}

/**
 * One column of the flows table: its heading, its share of the width and where its text sits.
 *
 * The width lives in the enum so the header and the rows cannot disagree about where a column ends — and
 * the enum is what makes that impossible to forget, since both loops read it.
 */
private enum class FlowColumn(
    val title: String,
    val weight: Float,
    val align: TextAlign,
) {
    /** The longest cell by far: a merchant's name, which the server writes out in full. */
    Merchant("商户", weight = 1.9f, align = TextAlign.Start),

    /** The clock time — and the day too, when the table covers more than one. */
    Time("时间", weight = 1.8f, align = TextAlign.End),

    /** The amount, with its sign: what the column of rows adds up to. */
    Amount("金额", weight = 1.1f, align = TextAlign.End),
}

/** One of the card's non-list states: a sentence, and a way out of it when there is one. */
@Composable
private fun FlowsMessage(
    title: String,
    detail: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.height(32.dp))
            Text(title, style = MaterialTheme.type.body)
        }
        Text(
            text = detail,
            style = MaterialTheme.type.footnote,
            color = MaterialTheme.colors.contentVariant,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(action)
            }
        }
    }
}

/** What a value the query has not answered yet is drawn as. */
private const val UnknownValue = "—"

/** A row the card system sent without a merchant's name. */
private const val UnknownMerchant = "未知商户"

/** The campus tile's own word for a cached value; the two must not drift apart. */
private const val CacheNote = "缓存"
