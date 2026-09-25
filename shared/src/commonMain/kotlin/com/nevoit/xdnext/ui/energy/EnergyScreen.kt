package com.nevoit.xdnext.ui.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.nevoit.material.core.component.ButtonStyles
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TopBarHeight
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.layout.VerticalStack
import com.nevoit.material.core.layout.isScrolledPast
import com.nevoit.material.core.utility.NavigationBarSpacer
import com.nevoit.material.core.utility.StatusBarSpacer
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.tokens.Springs
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.energy.EnergyState
import com.nevoit.xdnext.data.energy.MeterInfo
import com.nevoit.xdnext.ui.home.Card
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
 * The electricity page.
 *
 * The reading itself is the first card; under it the week's usage, which is the part worth looking at
 * twice, and at the bottom the balance over time — the page's own history rather than the meter's, so
 * it is the last thing to read rather than the first.
 */
@Composable
fun EnergyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: EnergyRepository = koinInject()
    val state by repository.state.collectAsState()
    val warning by repository.lowElectricityWarning.collectAsState()
    val scope = rememberCoroutineScope()
    val today =
        remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    LaunchedEffect(repository) {
        val current = repository.state.value
        if (current.info == null && !current.isLoading) {
            repository.refreshElectricityInfo()
        }
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
                .fillMaxSize().layerBackdrop(backdrop),
        ) {
            item {
                StatusBarSpacer()
                VGap(TopBarHeight)
            }
            item {
                ElectricitySummary(
                    state = state,
                    warning = warning.isTriggeredBy(
                        state.info?.electricityRemain ?: Double.MAX_VALUE
                    ),
                    onRefresh = { scope.launch { repository.refreshElectricityInfo() } },
                )
                VGap(24.dp)
            }
            item {
                DailyUsageCard(
                    rows = state.info?.electricityMeterList.orEmpty(),
                    today = today,
                )
                VGap(24.dp)
            }
            item {
                UsageHistoryCard(state)
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
            title = "电量",
            onBack = onBack,
            trailing = {
                IconButton(
                    enabled = !state.isLoading,
                    onClick = { scope.launch { repository.refreshElectricityInfo() } },
                ) {
                    SymbolIcon(name = Symbol.Refresh, contentDescription = "刷新电量")
                }
            }
        )
    }
}

/**
 * The page's reading, as the shared [SummaryCard] with this screen's words in it.
 *
 * Only the three questions a query can be in — answered, failed, still running — are decided here;
 * how a number with a unit and a line under it looks is the card's business.
 */
@Composable
private fun ElectricitySummary(
    state: EnergyState,
    warning: Boolean,
    onRefresh: () -> Unit,
) {
    val info = state.info

    SummaryCard(
        value = when {
            info != null -> info.electricityRemainText
            state.error != null -> "查询失败"
            else -> "正在查询"
        },
        unit = "度",
        caption = when {
            info != null -> "读取日期 ${info.lastReadText}"
            state.error != null -> "请检查网络后重试"
            else -> "首次查询可能需要一点时间"
        },
        icon = Symbol.Bolt,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/**
 * The week's usage, one page per week, swiped sideways.
 *
 * The pager moves the chart *and its value axis* together, because each week is scaled to itself —
 * see [UsageWeekPage]. The week's name above the chart, and the average under that name, follow the
 * page that is on screen. A tap on the arrows does what a swipe does, through the same [PagerState],
 * so the two cannot disagree.
 */
@Composable
private fun DailyUsageCard(
    rows: List<MeterInfo>,
    today: LocalDate,
) {
    val weeks = remember(rows) { usageWeeks(rows) }

    if (weeks.isEmpty()) {
        ChartCard(title = "每日用量", subtitle = "按抄表日期合并") {
            EmptyChartText("暂无足够的日用量数据")
        }
        return
    }

    val weekCount = rememberUpdatedState(weeks.size)
    // The page count is read through a state because `rememberPagerState` keeps the lambda it was
    // given: a refresh that returns a longer window would otherwise leave the pager on the old count.
    val pagerState = rememberPagerState(initialPage = weeks.lastIndex) { weekCount.value }
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage.coerceIn(weeks.indices)
    val week = weeks[page]

    val buttonStyle = ButtonStyles.secondaryVariant().copy(containerShape = Capsule())

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                // Which week this is *is* the title: the page's own name for the week on screen, so
                // the chart needs no heading of its own.
                Text(weekTitle(week, today))
                Text(
                    text = weekSubtitle(week),
                    style = MaterialTheme.type.footnote,
                    color = MaterialTheme.colors.contentVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    modifier = Modifier.width(40.dp).height(48.dp),
                    style = buttonStyle,
                    enabled = page > 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = page - 1,
                                animationSpec = Springs.smooth(300),
                            )
                        }
                    },
                ) {
                    SymbolIcon(
                        name = Symbol.ChevronLeft, contentDescription = "上一周",
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    modifier = Modifier.width(40.dp).height(48.dp),
                    style = buttonStyle,
                    enabled = page < weeks.lastIndex,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = page + 1,
                                animationSpec = Springs.smooth(300),
                            )
                        }
                    },
                ) {
                    SymbolIcon(
                        name = Symbol.Chevron,
                        contentDescription = "下一周",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        VGap(24.dp)
        WeeklyUsageChart(pagerState = pagerState, weeks = weeks)
    }
}

/**
 * The pager, with every page carrying its own chart *and* its own axis.
 *
 * The labels move with the bars because the scale does. That is the whole reason they are inside the
 * pager and not beside it: a week whose tallest day is 2 kWh and a week whose tallest is 40 kWh
 * cannot share a scale without one of them being a row of stubs, and the numbers on the right are
 * what makes the two comparable anyway.
 */
@Composable
private fun WeeklyUsageChart(
    pagerState: PagerState,
    weeks: List<UsageWeek>,
) {
    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fill,
        beyondViewportPageCount = 1,
        contentPadding = PaddingValues(horizontal = 24.dp),
        pageSpacing = 48.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        // `getOrNull` rather than an index: a refresh can shorten the window under a pager that
        // still reports the page it was on, and that is a blank page, not a crash.
        weeks.getOrNull(page)?.let { UsageWeekPage(week = it) }
    }
}

@Composable
private fun UsageAxisLabels(axis: UsageAxis) {
    Column(
        modifier = Modifier
            .width(AxisLabelWidth)
            .height(ChartHeight + AxisLabelHeight)
            .offset(y = -(AxisLabelHeight * 0.5f)),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        // Top gridline first: the labels run down from `axis.max` to the zero line.
        for (index in axis.intervals downTo 0) {
            Box(
                modifier = Modifier.height(AxisLabelHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = axis.label(index),
                    style = MaterialTheme.type.footnote.copy(fontFeatureSettings = "tnum"),
                    fontFamily = rememberCardValueFontFamily(),
                    color = MaterialTheme.colors.contentVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageWeekPage(week: UsageWeek) {
    // The week is scaled to its own tallest day rather than to the tallest day of the whole window:
    // what a page answers is "how did this week go", and one 40 kWh day in another week would press
    // every bar here flat. The labels travel with the scale, so nothing about it is hidden.
    val axis = remember(week) { usageAxis(week.peak ?: 0.0) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
                UsageGuides(week = week, axis = axis)
                Row(modifier = Modifier.fillMaxSize()) {
                    week.days.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            UsageBar(week = week, day = day, axis = axis)
                        }
                    }
                }
            }
            VGap(4.dp)
            Row(modifier = Modifier.fillMaxWidth().height(WeekdayRowHeight)) {
                WeekdayLetters.forEach { letter ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = letter,
                            style = MaterialTheme.type.footnote,
                            color = MaterialTheme.colors.contentVariant,
                        )
                    }
                }
            }
        }
        UsageAxisLabels(axis)
    }
}

/**
 * The lines the bars are read against: the scale's gridlines, and the week's own average.
 *
 * The line at the very top is deliberately absent — the top label sits on the chart's edge, where a
 * line would only crowd it. The average is drawn dashed and in the accent that the bars above it take,
 * because it is not a rung of the scale: it is a value of this week, and it says what the brighter
 * bars have in common.
 */
@Composable
private fun UsageGuides(week: UsageWeek, axis: UsageAxis) {
    val guideColor = MaterialTheme.colors.inactiveThumb.copy(alpha = 0.2f)
    val averageColor = MaterialTheme.colors.content
    val average = week.average

    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(axis.intervals) { index ->
            val y = size.height * index / axis.intervals
            drawLine(guideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        // A week with nothing read in it has no average to draw, and one whose days corrected the
        // meter downwards has an average below the baseline: both leave the chart without the line.
        if (average != null && average > 0.0) {
            val y = size.height * (1f - axis.fraction(average))
            drawLine(
                color = averageColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
    }
}

@Composable
private fun UsageBar(week: UsageWeek, day: DayUsage?, axis: UsageAxis) {
    // A day the meter did not report, and a day it reported as nothing used, are both nothing to
    // draw. The correction days the protocol can carry are negative and do not fit a bar that grows
    // upwards from the baseline either.
    if (day == null || day.value <= 0.0) return

    val colors = MaterialTheme.colors
    val height = (ChartHeight * axis.fraction(day.value)).coerceIn(MinBarHeight, ChartHeight)
    val fill = if (week.isAboveAverage(day)) colors.tertiaryFixedDim else colors.primaryFixedDim

    // The cap is a capsule rather than a fixed corner radius: a bar is a stub on a quiet week and a
    // full column on a busy one, and a capsule reads as round at either height.
    Box(
        modifier = Modifier
            .width(BarWidth)
            .height(height)
            .background(fill, Capsule()),
    )
}

@Composable
private fun UsageHistoryCard(state: EnergyState) {
    val points = remember(state.history) {
        state.history.mapNotNull { row -> row.remain.toDoubleOrNull()?.let { row.fetchDay to it } }
    }
    Column {
        Text(
            text = "历史用量",
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.type.footnote,
            color = MaterialTheme.colors.contentVariant
        )
        VGap()
        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            if (points.isEmpty()) {
                EmptyChartText("暂无记录")
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    HistoryChart(points)
                    DateCaption(points.map { it.first })
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.type.title3)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.type.footnote,
                        color = MaterialTheme.colors.contentVariant,
                    )
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun EmptyChartText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = MaterialTheme.type.footnote,
        color = MaterialTheme.colors.contentVariant,
    )
}

@Composable
private fun HistoryChart(points: List<Pair<LocalDate, Double>>) {
    val lineColor = MaterialTheme.colors.primary
    val guideColor = MaterialTheme.colors.content.copy(alpha = 0.2f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
    ) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(guideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val min = points.minOf { it.second }
        val max = points.maxOf { it.second }
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val xStep = if (points.size == 1) 0f else size.width / (points.size - 1)
        val coordinates = points.mapIndexed { index, (_, value) ->
            Offset(xStep * index, size.height - ((value - min) / range).toFloat() * size.height)
        }
        if (coordinates.size > 1) {
            val path = Path().apply {
                moveTo(coordinates.first().x, coordinates.first().y)
                coordinates.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path,
                lineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
            )
        }
        coordinates.forEach { point ->
            drawCircle(lineColor, radius = 4.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun DateCaption(dates: List<LocalDate>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dates.take(5).map { it.toString().substringAfter('-').replace('-', '.') }.forEach {
            Text(
                it,
                style = MaterialTheme.type.footnote,
                color = MaterialTheme.colors.contentVariant
            )
        }
    }
}

// The chart's geometry, kept together so the bars, the labels and the gridlines cannot drift apart.
private val ChartHeight = 176.dp
private val BarWidth = 28.dp
private val MinBarHeight = 8.dp
private val WeekdayRowHeight = 20.dp

// Wide enough for the longest label a step can produce — `0.25`, `7.5`, `22.5` — because a step of
// 2.5 at the tenth magnitude is a real axis for a week that used well under a kWh a day, and a label
// that wraps to two lines throws the whole column off its gridlines.
private val AxisLabelWidth = 32.dp
private val AxisLabelHeight = 16.dp
