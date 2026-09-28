package com.nevoit.xdnext.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.nevoit.material.core.component.PageHeader
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.interaction.overscroll.rememberOwnRangeOverscrollEffect
import com.nevoit.material.theme.MaterialShapes
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.toShape
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.energy.EnergyState
import com.nevoit.xdnext.data.schoolcard.SchoolCardRepository
import com.nevoit.xdnext.data.schoolcard.SchoolCardState
import com.nevoit.xdnext.data.schoolcard.centsToYuanText
import com.nevoit.xdnext.data.timetable.TimetableCalendar
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.data.timetable.TimetableState
import com.nevoit.xdnext.ui.shared.rememberCardValueFontFamily
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.timetable.TimetableStrings
import com.nevoit.xdnext.ui.timetable.classSwatchFor
import com.nevoit.xdnext.ui.timetable.rememberTimetableNow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun FocusScreen(
    energy: EnergyRepository,
    schoolCard: SchoolCardRepository,
    timetable: TimetableRepository,
    refresher: CardRefresher,
    actions: TileActions
) {
    val energyState by energy.state.collectAsState()
    val schoolCardState by schoolCard.state.collectAsState()
    val timetableState by timetable.state.collectAsState()

    // The banner counts what is left of *today*, so it follows the clock rather than the date the page
    // was opened on: a class that ends at 10:05 stops being counted at 10:05, with nothing to tap.
    val now = rememberTimetableNow()

    LaunchedEffect(refresher) { refresher.loadOnce() }

    val order = DefaultHomeOrder

    val today =
        remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    PageColumn {
        item { PageHeader(title = formatDateTitle(today), subTitle = formatWeekday(today)) }

        item {
            HomeGrid(spans = order.map { it.span }) {
                order.forEach { id ->
                    key(id) {
                        HomeTile(
                            id = id,
                            energyState = energyState,
                            schoolCardState = schoolCardState,
                            timetableState = timetableState,
                            now = now,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTile(
    id: HomeTileId,
    energyState: EnergyState,
    schoolCardState: SchoolCardState,
    timetableState: TimetableState,
    now: LocalDateTime,
    actions: TileActions,
) {
    when (id) {
        HomeTileId.Timetable -> TimetableBanner(
            state = timetableState,
            now = now,
            onClick = { actions.openTimetable() },
        )

        HomeTileId.Energy -> EnergyTile(
            state = energyState,
            onClick = { actions.openEnergy() },
        )

        HomeTileId.Library -> WidgetTile(
            icon = Symbol.Book,
            iconContainerShape = MaterialShapes.Gem.toShape(),
            title = "图书借阅",
            value = "2 本",
            detail = "待归还 2 本",
        )

        HomeTileId.CampusPay -> SchoolCardTile(
            state = schoolCardState,
            onClick = { actions.openSchoolCard() },
        )

        HomeTileId.Grade, HomeTileId.Exam, HomeTileId.EmptyRoom, HomeTileId.Attendance,
        HomeTileId.Network, HomeTileId.Science, HomeTileId.Sport,
            -> ShortcutSpecs.getValue(id).let { StatTile(icon = it.symbol, label = it.label) }

        HomeTileId.Water -> ShortcutSpecs.getValue(id).let { StatTile(icon = it.symbol, label = it.label) }

        HomeTileId.WaterFee -> ShortcutSpecs.getValue(id).let {
            StatTile(icon = it.symbol, label = it.label, onClick = { actions.openWaterFee() })
        }
    }
}

/**
 * The timetable banner: how many classes are left today, which week the term is in, and the classes
 * themselves.
 *
 * The right half is the room [InfoCard] leaves for exactly this. It carries one **horizontal card per
 * remaining class** — name over room on the card's left, start over end on its right — each painted in
 * that course's own colour from the shared course palette, so a course that is red on the grid is red
 * here.
 *
 * The number on the left and the list on the right come from the same reading of
 * [remainingClassesToday] — once per minute change and once per timetable change — so they cannot
 * disagree about how many are left.
 */
@Composable
private fun TimetableBanner(
    state: TimetableState,
    now: LocalDateTime,
    onClick: () -> Unit,
) {
    val classes = remember(state, now) { remainingClassesToday(state, now) }
    val hasTimetable = state.hasTimetable

    InfoCard(
        icon = Symbol.Calendar,
        title = "今日剩余",
        // A timetable that is not here has no answer to give, and "0" would be a wrong one: on a first
        // launch off campus there is nothing cached and nothing to count.
        value = if (hasTimetable) classes.size.toString() else UNKNOWN_COUNT,
        unit = if (hasTimetable) "个日程" else null,
        detail = currentWeekText(state, now),
        onClick = onClick,
    ) {
        RemainingClasses(classes)
    }
}

/**
 * 第 N 周, or nothing at all while there is no term to count weeks from.
 *
 * The week is derived from the timetable's own term start day — the same field the grid counts from —
 * rather than from a second, local guess at when the term began. See `TodayHeader.formatWeekOfTerm`.
 */
private fun currentWeekText(state: TimetableState, now: LocalDateTime): String {
    val termStart = state.termStartDay ?: return ""
    // `-1` is the calendar's "the term has not started" rather than a week number.
    val week = TimetableCalendar.currentWeek(termStart, now.date).takeIf { it >= 0 } ?: return ""
    return formatWeekOfTerm(week)
}

/**
 * The remaining classes, one card each: an ordinary column while the day fits the space it is given, and
 * a lazy list once it does not.
 *
 * The choice is the point. A column has no scroll of its own, so a drag over a day that fits finds
 * nothing to scroll and stays the page's: the page moves, and when the page runs out it is the page's band
 * that draws. A day that overflows gets a list instead, and that list is a scroll of its own:
 * `rememberOwnRangeOverscrollEffect` answers the page wherever it runs out of range, so the page neither
 * follows it nor draws a band for it.
 *
 * A lazy list is safe here although the page is one too: [HomeGrid] measures the banner into a
 * tile-sized fixed box, and that same bound is what tells the two apart — the column is measured against
 * it, unbounded, and the list is composed only when the column turns out to be taller.
 */
@Composable
private fun RemainingClasses(classes: List<TodayClass>) {
    val band = rememberOwnRangeOverscrollEffect()

    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val column = subcompose(ClassesSlot.Column) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(end = 12.dp, top = 12.dp, bottom = 12.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                classes.forEach { RemainingClassCard(it) }
            }
        }.first().measure(
            Constraints(
                maxWidth = constraints.maxWidth,
                maxHeight = Constraints.Infinity,
            )
        )

        // The box is bounded here; an unbounded pass is one this is free to fill, which is the same
        // answer as fitting.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else column.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else column.height

        if (column.height <= height) {
            layout(width, height) { column.placeRelative(0, 0) }
        } else {
            val list = subcompose(ClassesSlot.List) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(band.modifier),
                    overscrollEffect = band,
                    contentPadding = PaddingValues(end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(classes) { entry ->
                        RemainingClassCard(entry)
                    }
                }
            }.first().measure(constraints)
            layout(width, height) { list.placeRelative(0, 0) }
        }
    }
}

/** The two shapes [RemainingClasses] chooses between, as subcomposition slots of their own. */
private enum class ClassesSlot { Column, List }

/**
 * One class: its name over its room, its start over its end.
 *
 * The colour is the course's own, taken from the same palette the grid draws from and by the same index,
 * and the text is that colour's container pair — which is what makes it readable in both themes.
 */
@Composable
private fun RemainingClassCard(entry: TodayClass) {
    val swatch = classSwatchFor(entry.paletteIndex)
    val contentColor = swatch.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(12.dp))
            .background(swatch.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = contentColor,
                style = MaterialTheme.type.footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.place ?: TimetableStrings.UNKNOWN_CLASSROOM,
                color = contentColor,
                style = MaterialTheme.type.footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = rememberCardValueFontFamily(),
            )
        }
        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = entry.startText,
                color = contentColor,
                style = MaterialTheme.type.footnote,
                fontFamily = rememberCardValueFontFamily(),
            )
            Text(
                text = entry.stopText,
                color = contentColor,
                style = MaterialTheme.type.footnote,
                fontFamily = rememberCardValueFontFamily(),
            )
        }
    }
}

/** Drawn in place of the count while there is no timetable to count. */
private const val UNKNOWN_COUNT = "—"

private fun freshnessNote(isFromCache: Boolean, hasValue: Boolean): String? =
    if (isFromCache && hasValue) "· 缓存" else null

@Composable
private fun SchoolCardTile(
    state: SchoolCardState,
    onClick: () -> Unit,
) {
    val snapshot = state.snapshot
    val expense = snapshot?.todayExpenseCents
    val balance = snapshot?.balance

    WidgetTile(
        icon = Symbol.CreditCard,
        iconContainerShape = MaterialShapes.Cookie4Sided.toShape(),
        title = "今日支出",
        value = when {
            expense != null -> centsToYuanText(expense)
            balance == null && state.error != null -> "查询失败"
            else -> "—"
        },
        unit = "元",
        detail = (balance?.let { "余额 $it" } ?: when {
            state.error != null -> "点击重试"
            else -> "正在查询…"
        }).let { line ->
            listOfNotNull(
                line,
                freshnessNote(state.isFromCache, snapshot != null)
            ).joinToString(" ")
        },
        onClick = onClick,
    )
}

@Composable
private fun EnergyTile(
    state: EnergyState,
    onClick: () -> Unit,
) {
    val info = state.info
    WidgetTile(
        icon = Symbol.Bolt,
        iconContainerShape = MaterialShapes.VerySunny.toShape(),
        title = "电量",
        value = when {
            info != null -> info.electricityRemainText
            state.error != null -> "查询失败"
            else -> "-"
        },
        unit = "度",
        detail = when {
            info == null && state.error != null -> "点击重试"
            info != null -> info.lastReadText
            else -> null
        }?.let { line ->
            listOfNotNull(line, freshnessNote(state.isFromCache, info != null)).joinToString(" ")
        },
        onClick = onClick
    )
}
