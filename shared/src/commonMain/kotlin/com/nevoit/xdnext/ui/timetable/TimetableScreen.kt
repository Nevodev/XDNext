package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.AlertDialog
import com.nevoit.material.core.component.Button
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TextButton
import com.nevoit.material.core.component.VGap
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.tokens.Springs
import com.nevoit.xdnext.data.timetable.ClassTableData
import com.nevoit.xdnext.data.timetable.TimetableCalendar
import com.nevoit.xdnext.data.timetable.TimetableGrid
import com.nevoit.xdnext.data.timetable.TimetableOverlay
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.data.timetable.TimetableState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.koin.compose.koinInject

@Composable
fun TimetableScreen(
    onBack: () -> Unit,
    onOpenNotArranged: () -> Unit,
    onOpenClassChanges: () -> Unit,
    modifier: Modifier = Modifier,
    overlays: List<TimetableOverlay> = emptyList(),
) {
    val repository: TimetableRepository = koinInject()
    val state by repository.state.collectAsState()
    val appearanceStore: TimetableAppearanceStore = koinInject()
    val appearance by appearanceStore.appearance.collectAsState()
    val scope = rememberCoroutineScope()
    val now = rememberTimetableNow()

    var showErrorSummary by remember { mutableStateOf(false) }
    var showRefreshConfirm by remember { mutableStateOf(false) }

    val data = state.data
    val termStart = state.termStartDay
    val currentWeek = termStart?.let { TimetableCalendar.currentWeek(it, now.date) } ?: -1
    val hasGrid = data != null && data.hasClasses && termStart != null

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.pageBackground),
    ) {
        val showOverview = maxHeight >= TimetableSpecs.OverviewMinHeight

        Column(modifier = Modifier.fillMaxSize()) {
            TimetableTopBar(
                onBack = onBack,
                menu = if (hasGrid) {
                    TimetableMenuActions(
                        onOpenNotArranged = onOpenNotArranged,
                        onOpenClassChanges = onOpenClassChanges,
                        onRefresh = { showRefreshConfirm = true },
                    )
                } else {
                    null
                },
                onShowErrors = if (hasSourceErrors(state)) {
                    { showErrorSummary = true }
                } else {
                    null
                }
            )
//            TimetableBanner(loading = state.loadingSources, cache = state.cacheSources)

            if (data != null && termStart != null && data.hasClasses) {
                TimetableContent(
                    data = data,
                    termStart = termStart,
                    currentWeek = currentWeek,
                    now = now,
                    appearance = appearance,
                    overlays = overlays,
                    showOverview = showOverview,
                )
            } else {
                EmptyTimetableBody(
                    notice = emptyTimetableNotice(state),
                    onRefresh = { scope.launch { repository.refresh() } },
                )
            }
        }
    }

    if (showErrorSummary) {
        AlertDialog(
            onDismissRequest = { showErrorSummary = false },
            title = { Text(TimetableStrings.ERROR_DIALOG_TITLE) },
            text = {
                Text(
                    text = loadErrorSummary(state),
                    style = MaterialTheme.type.subHeadline,
                )
            },
            confirmButton = {
                TextButton(onClick = { showErrorSummary = false }) {
                    Text(TimetableStrings.CONFIRM)
                }
            },
        )
    }

    if (showRefreshConfirm) {
        AlertDialog(
            onDismissRequest = { showRefreshConfirm = false },
            title = { Text(TimetableStrings.REFRESH_DIALOG_TITLE) },
            text = {
                Text(
                    text = TimetableStrings.REFRESH_DIALOG_CONTENT,
                    style = MaterialTheme.type.subHeadline,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRefreshConfirm = false
                        scope.launch { repository.refresh() }
                    },
                ) {
                    Text(TimetableStrings.CONFIRM)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshConfirm = false }) {
                    Text(TimetableStrings.CANCEL)
                }
            },
        )
    }
}

@Composable
private fun TimetableContent(
    data: ClassTableData,
    termStart: LocalDate,
    currentWeek: Int,
    now: LocalDateTime,
    appearance: TimetableAppearance,
    overlays: List<TimetableOverlay>,
    showOverview: Boolean,
) {
    val semesterLength = data.semesterLength.coerceAtLeast(1)
    val initialWeek = currentWeek.coerceIn(0, semesterLength - 1)

    var chosenWeek by rememberSaveable(data.semesterCode) { mutableStateOf(initialWeek) }
    val week = chosenWeek.coerceIn(0, semesterLength - 1)

    val grid = remember(data, overlays) { TimetableGrid(data, overlays) }
    // Both start where the *selection* is, which after a configuration change is the week the user had
    // chosen rather than the one the calendar is in — and for the pager that also means the first page it
    // reports is the page the strip is already showing.
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = week)
    val matrixState = rememberPagerState(initialPage = week) { semesterLength }
    val scope = rememberCoroutineScope()

    // The grid is **observed**, and nothing here ever scrolls it. That asymmetry is the fix for the
    // doubled animation: the write-back below used to land in the same `chosenWeek` that the effect after
    // it was keyed on, so the grid animated itself to a page it was already on its way to — a second
    // animation, under the finger, halfway through the swipe.
    //
    // `currentPage`, because it moves as the pages move: the strip then tracks the grid while the finger
    // is still down, which is the whole point of the strip being slid to follow the grid. Nothing else may
    // move the grid, or the loop comes back — which is why the only pager scroll left is the tap below.
    LaunchedEffect(matrixState) {
        snapshotFlow { matrixState.currentPage }.collect { page ->
            chosenWeek = page
        }
    }

    // The strip follows the selection, however the selection was made.
    LaunchedEffect(week, stripState) {
        stripState.animateScrollToItem(week)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekStrip(
            listState = stripState,
            semesterLength = semesterLength,
            chosenWeek = week,
            currentWeek = currentWeek,
            termStart = termStart,
            grid = grid,
            now = now,
            showOverview = showOverview,
            onWeekSelected = { index ->
                // A tap is the only thing that moves the grid, and it is an event rather than a state, so
                // it is launched from the tap itself: an effect keyed on the week would fire for the weeks
                // the grid reports on its own, which is the loop this replaces.
                chosenWeek = index
                scope.launch {
                    matrixState.animateScrollToPage(
                        page = index,
                        animationSpec = Springs.smooth(300),
                    )
                }
            },
        )
        VGap()
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            val isPhone = maxWidth < TimetableSpecs.PhoneWidth
            val blockUnit = (maxHeight - TimetableSpecs.MidRowHeight).coerceAtLeast(0.dp) /
                    (if (isPhone) {
                        TimetableSpecs.BlocksOnPhone.toFloat()
                    } else {
                        TimetableSpecs.BlocksOnWideScreen.toFloat()
                    })

            HorizontalPager(
                state = matrixState,
                pageSize = PageSize.Fill,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 24.dp,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { page ->
                WeekTable(
                    weekIndex = page,
                    termStart = termStart,
                    grid = grid,
                    now = now,
                    appearance = appearance,
                    blockUnit = blockUnit,
                )
            }
        }
    }
}

@Composable
private fun EmptyTimetableBody(notice: String, onRefresh: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = notice,
                style = MaterialTheme.type.body,
                color = MaterialTheme.colors.contentVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRefresh) {
                Text(TimetableStrings.MENU_REFRESH)
            }
        }
    }
}

/**
 * What the page says when it has no grid to draw.
 *
 * A request that is still running is not "this semester has no courses", and saying so is what made a
 * first sign-in look like it needed a tap on 刷新日程表: the fetch was already on its way, and the page
 * gave no sign of it — the status banner that would have said so is not drawn. The banner's own sentence
 * is reused rather than a new one invented, because it is the original's wording for exactly this state.
 *
 * A *failed* first fetch keeps the original's sentence: it has no cache hint to show and the title bar
 * carries the 加载错误 summary, which is where the reason is.
 */
internal fun emptyTimetableNotice(state: TimetableState): String =
    if (state.data == null && state.isLoading) {
        TimetableStrings.bannerLoading(TimetableStrings.SOURCE_CLASS_TABLE)
    } else {
        TimetableStrings.emptyNoCourse(state.semesterCode)
    }

/** Whether the title bar should offer the error summary. */
private fun hasSourceErrors(state: TimetableState): Boolean =
    state.errorWithoutCacheSources.isNotEmpty() || state.errorWithCacheSources.isNotEmpty()

/**
 * The error summary's body: what failed outright, then what is being served from a cache and why.
 *
 * Ported from `_showLoadErrorDialog`. The two groups are separated by a blank line, each names its
 * sources, and each source's own line is "source: reason" — where the reason is the cache hint when
 * there is one and the generic network sentence when there is not. A failure with *no* cache has no
 * hint to show, so it always reads as the generic sentence; that is the original's behaviour and it is
 * why the first group looks the way it does.
 */
internal fun loadErrorSummary(state: TimetableState): String = buildString {
    val withoutCache = state.errorWithoutCacheSources
    val withCache = state.errorWithCacheSources

    if (withoutCache.isNotEmpty()) {
        appendLine(TimetableStrings.errorSummary(withoutCache.joinToString("、") { it.label }))
        withoutCache.forEach { source ->
            appendLine("${source.label}: ${TimetableStrings.NETWORK_ERROR}")
        }
    }
    if (withoutCache.isNotEmpty() && withCache.isNotEmpty()) appendLine()
    if (withCache.isNotEmpty()) {
        appendLine(
            TimetableStrings.bannerCache(withCache.joinToString("、") { it.label }),
        )
        withCache.forEach { source ->
            appendLine(
                "${source.label}: ${state.cacheHint?.message ?: TimetableStrings.NETWORK_ERROR}",
            )
        }
    }
}
