package com.nevoit.xdnext.ui.experiment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nevoit.material.core.component.AlertDialog
import com.nevoit.material.core.component.Button
import com.nevoit.material.core.component.CircularProgressIndicator
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TextButton
import com.nevoit.material.core.component.TopBarHeight
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.layout.VerticalStack
import com.nevoit.material.core.layout.isScrolledPast
import com.nevoit.material.core.utility.NavigationBarSpacer
import com.nevoit.material.core.utility.StatusBarSpacer
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.core.image.decodeImageBitmap
import com.nevoit.xdnext.data.experiment.ExperimentCacheHint
import com.nevoit.xdnext.data.experiment.ExperimentEntry
import com.nevoit.xdnext.data.experiment.ExperimentGroups
import com.nevoit.xdnext.data.experiment.ExperimentRepository
import com.nevoit.xdnext.data.experiment.ExperimentState
import com.nevoit.xdnext.data.experiment.ExperimentTimeRange
import com.nevoit.xdnext.data.experiment.experimentCacheHintFor
import com.nevoit.xdnext.data.experiment.groupExperiments
import com.nevoit.xdnext.ui.home.Card
import com.nevoit.xdnext.ui.shared.AppTopBar
import com.nevoit.xdnext.ui.shared.BlurShade
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon
import com.nevoit.xdnext.ui.timetable.TimetableStrings
import com.nevoit.xdnext.ui.timetable.rememberTimetableNow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.experiment_all_finished
import xdnext.shared.generated.resources.experiment_cache_hint_login_failed
import xdnext.shared.generated.resources.experiment_cache_hint_missing_password
import xdnext.shared.generated.resources.experiment_cache_hint_network_failed
import xdnext.shared.generated.resources.experiment_cache_hint_unknown_error
import xdnext.shared.generated.resources.experiment_cache_notice
import xdnext.shared.generated.resources.experiment_confirm
import xdnext.shared.generated.resources.experiment_finished
import xdnext.shared.generated.resources.experiment_loading
import xdnext.shared.generated.resources.experiment_no_password
import xdnext.shared.generated.resources.experiment_none_finished
import xdnext.shared.generated.resources.experiment_not_finished
import xdnext.shared.generated.resources.experiment_ongoing
import xdnext.shared.generated.resources.experiment_open_account_settings
import xdnext.shared.generated.resources.experiment_predict_score
import xdnext.shared.generated.resources.experiment_refresh
import xdnext.shared.generated.resources.experiment_score_found_hint
import xdnext.shared.generated.resources.experiment_score_hint
import xdnext.shared.generated.resources.experiment_score_info
import xdnext.shared.generated.resources.experiment_score_unknown_hint
import xdnext.shared.generated.resources.experiment_sitting
import xdnext.shared.generated.resources.experiment_tap_for_score
import xdnext.shared.generated.resources.experiment_teacher_not_provided
import xdnext.shared.generated.resources.experiment_title
import xdnext.shared.generated.resources.experiment_your_score

/**
 * 实验信息 — the physics experiment bookings the lab site has for this account.
 *
 * Ported from `experiment_window.dart` and `experiment_info_card.dart`. The page is three lists under
 * one another — 正在进行实验, 未完成实验 and 已完成实验 — and the split between them is a value
 * ([groupExperiments]) rather than a tree of `where` calls, so the two sentences the original drew for
 * an empty list, 所有实验全部完成 and 目前没有已经完成的实验, are decided here.
 *
 * The chrome is the rest of the app's: a **floating title bar over a blurred copy of the page**, which
 * only appears once the content has moved under it (`isScrolledPast`), so the page opens with its title
 * sitting on the background rather than on a bar. The content is a lazy stack with the bar's own height
 * reserved at the top and the navigation bar's at the bottom, which is what lets the first card scroll
 * all the way under the bar instead of being clipped by it.
 *
 * Every sentence on this page is a string resource — see `composeResources/values/strings.xml` — and so
 * is the *reason* a cached list is being shown: [ExperimentCacheHint] carries no text of its own, and the
 * failure the page prints when it has nothing to show is the same classified hint rather than an
 * exception's `toString()`.
 *
 * What is *not* carried over is the original page's other half: it merged the other experiment system
 * into the same three lists, and every state in it is spelled out twice for the two sources. Only the
 * physics system exists in this port.
 */
@Composable
fun ExperimentScreen(
    onBack: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: ExperimentRepository = koinInject()
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    // The same minute-aligned clock the timetable's own page reads, so "正在进行" means the same thing
    // on both screens rather than "was true when this page was composed".
    val now = rememberTimetableNow()

    // The booking whose mark's picture is being shown, if any.
    var shownScoreOf by remember { mutableStateOf<ExperimentEntry?>(null) }

    val colors = MaterialTheme.colors
    val listState = rememberLazyListState()
    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = colors.pageBackground,
            topLeft = Offset(-this.size.width, -this.size.height),
            size = Size(this.size.width * 3, this.size.height * 3),
        )
        drawContent()
    }
    val shadeVisible by listState.isScrolledPast(0.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground),
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

            when {
                state.needsPassword -> item {
                    MissingPasswordBody(onOpenAccountSettings = onOpenAccountSettings)
                }

                !state.hasData -> item {
                    EmptyExperimentBody(
                        state = state,
                        onRefresh = { scope.launch { repository.refresh() } },
                    )
                }

                else -> ExperimentList(
                    state = state,
                    groups = groupExperiments(state.entries, now),
                    onShowScore = { entry -> shownScoreOf = if (entry.score != null) entry else null },
                )
            }

            item { NavigationBarSpacer() }
        }

        BlurShade(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TopBarHeight),
            backdrop = backdrop,
            visible = shadeVisible,
        )

        AppTopBar(
            title = stringResource(Res.string.experiment_title),
            onBack = onBack,
            trailing = if (state.hasData) {
                {
                    IconButton(
                        enabled = !state.isLoading,
                        onClick = { scope.launch { repository.refresh() } },
                    ) {
                        SymbolIcon(
                            name = Symbol.Refresh,
                            contentDescription = stringResource(Res.string.experiment_refresh),
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    shownScoreOf?.let { entry ->
        ScoreDialog(
            entry = entry,
            repository = repository,
            onDismiss = { shownScoreOf = null },
        )
    }
}

/**
 * What the page says when it has no list to draw.
 *
 * A request still running is not "you have no experiments", and a failure with nothing to fall back on
 * names its reason **by class** — `experimentCacheHintFor` is the same classification the cache notice
 * uses, so the two cannot say different things about one failure, and neither shows a Java exception's
 * text to a user. The block is 240 dp tall rather than full-height: it sits in a list that has already
 * reserved the title bar's room, and a page that fills the screen with one sentence reads as a broken
 * page rather than as a page with nothing on it.
 */
@Composable
private fun EmptyExperimentBody(state: ExperimentState, onRefresh: () -> Unit) {
    val error = state.error

    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.isLoading && error == null) {
                CircularProgressIndicator()
                return@Column
            }

            Text(
                text = error?.let { stringResource(experimentCacheHintFor(it).messageRes) }
                    ?: stringResource(Res.string.experiment_loading),
                style = MaterialTheme.type.body,
                color = MaterialTheme.colors.contentVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRefresh) {
                Text(stringResource(Res.string.experiment_refresh))
            }
        }
    }
}

/**
 * The card the original drew instead of an error whenever the failure was "no password".
 *
 * It is not an error state and should not read like one: the account is fine and the page is one setting
 * away from working, so it says so and offers the way there. The remedy is on the account settings page
 * rather than in a dialog here, because that page is where the same credentials are edited for the whole
 * app.
 */
@Composable
private fun MissingPasswordBody(onOpenAccountSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymbolIcon(
                        name = Symbol.Science,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(Res.string.experiment_no_password),
                        style = MaterialTheme.type.body,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onOpenAccountSettings) {
                    Text(stringResource(Res.string.experiment_open_account_settings))
                }
            }
        }
    }
}

/** The three groups, the score hint above them and the cache notice, in the original's own order. */
private fun LazyListScope.ExperimentList(
    state: ExperimentState,
    groups: ExperimentGroups,
    onShowScore: (ExperimentEntry) -> Unit,
) {
    state.cacheHint?.takeIf { state.isFromCache }?.let { hint ->
        item {
            NoticeCard(
                text = stringResource(
                    Res.string.experiment_cache_notice,
                    stringResource(hint.messageRes),
                ),
            )
        }
    }

    item { NoticeCard(text = stringResource(Res.string.experiment_score_hint)) }

    if (groups.ongoing.isNotEmpty()) {
        item { GroupTitle(stringResource(Res.string.experiment_ongoing)) }
        items(groups.ongoing, key = { it.sectionKey("ongoing") }) { entry ->
            ExperimentCard(entry, onShowScore)
        }
    }

    item { GroupTitle(stringResource(Res.string.experiment_not_finished)) }
    if (groups.upcoming.isEmpty()) {
        item { EmptyGroupLine(stringResource(Res.string.experiment_all_finished)) }
    } else {
        items(groups.upcoming, key = { it.sectionKey("upcoming") }) { entry ->
            ExperimentCard(entry, onShowScore)
        }
    }

    item { GroupTitle(stringResource(Res.string.experiment_finished)) }
    if (groups.finished.isEmpty()) {
        item { EmptyGroupLine(stringResource(Res.string.experiment_none_finished)) }
    } else {
        items(groups.finished, key = { it.sectionKey("finished") }) { entry ->
            ExperimentCard(entry, onShowScore)
        }
    }
}

/**
 * Which sentence a cache hint is drawn with, and which one a failure with nothing to show gets.
 *
 * The mapping is here rather than on the enum because the enum is in `data`, and a data type that knows
 * the sentences a screen prints is how the original ended up with its translations spread across four
 * languages' worth of keys inside its models.
 */
private val ExperimentCacheHint.messageRes: StringResource
    get() = when (this) {
        ExperimentCacheHint.MISSING_PASSWORD -> Res.string.experiment_cache_hint_missing_password
        ExperimentCacheHint.LOGIN_FAILED -> Res.string.experiment_cache_hint_login_failed
        ExperimentCacheHint.NETWORK_FAILED -> Res.string.experiment_cache_hint_network_failed
        ExperimentCacheHint.UNKNOWN_ERROR -> Res.string.experiment_cache_hint_unknown_error
    }

/**
 * A list key for a booking in one section.
 *
 * A booking whose sittings straddle the clock is in two sections at once, with a different set of
 * sittings in each, so the key is the section *and* the sittings it is showing there — two cards for one
 * booking are two different rows, and a key that named only the booking would be a duplicate.
 */
private fun ExperimentEntry.sectionKey(section: String): String =
    "$section:${timeRanges.joinToString { it.start.toString() }}"

/** A section's heading, the original's `TimelineTitle`. */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.type.headline,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** The sentence a group with nothing in it shows, in the heading's position rather than a card's. */
@Composable
private fun EmptyGroupLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.type.subHeadline,
        color = MaterialTheme.colors.contentVariant,
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
    )
}

/** A one-sentence card, which is what the original drew both its hints and its failures in. */
@Composable
private fun NoticeCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(
            text = text,
            style = MaterialTheme.type.subHeadline,
            color = MaterialTheme.colors.contentVariant,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        )
    }
}

/**
 * One booking: its name, its mark, and the three facts the original printed under them.
 *
 * The mark is the only tappable part of the card, exactly as in the original's `ReXCardRemaining`: the
 * rest of the card has nothing to open, and a card-wide target that opens a detail sheet only when there
 * is a score would be a target that sometimes does nothing.
 */
@Composable
private fun ExperimentCard(entry: ExperimentEntry, onShowScore: (ExperimentEntry) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.type.body,
                    modifier = Modifier.weight(1f),
                )
                entry.score?.let { score ->
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = { onShowScore(entry) }) {
                        Text(
                            text = if (score.found) {
                                stringResource(Res.string.experiment_score_info, score.label)
                            } else {
                                stringResource(Res.string.experiment_tap_for_score)
                            },
                            style = MaterialTheme.type.subHeadline,
                            color = MaterialTheme.colors.primary,
                        )
                    }
                }
            }

            entry.timeRanges.forEach { range ->
                InfoLine(icon = Symbol.Calendar, text = sittingText(range))
            }
            InfoLine(
                icon = Symbol.EmptyRoom,
                text = entry.classroom.ifBlank {
                    stringResource(Res.string.experiment_teacher_not_provided)
                },
            )
            entry.reference?.let { InfoLine(icon = Symbol.Library, text = it) }
            InfoLine(
                icon = Symbol.Person,
                text = entry.teacher ?: stringResource(Res.string.experiment_teacher_not_provided),
            )
        }
    }
}

/** One line of a card's facts: the original's `InformationWithIcon`. */
@Composable
private fun InfoLine(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(
            name = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.type.subHeadline,
            color = MaterialTheme.colors.contentVariant,
        )
    }
}

/**
 * One sitting, as the card prints it: `2026/3/5 星期四 15:55~18:10`.
 *
 * The original's two `DateFormat`s (`y/M/d EEEE` and `HH:mm:ss`) in one line. The seconds are dropped —
 * both of the lab's sittings start and end on the minute, and `HH:mm:ss` printed `:00` twice on every
 * line — and the weekday is the character `ui.timetable` already names its own days with, so the two
 * pages cannot disagree about what a 星期四 is.
 */
@Composable
private fun sittingText(range: ExperimentTimeRange): String = stringResource(
    Res.string.experiment_sitting,
    dateText(range.start),
    TimetableStrings.weekday(range.start.date.dayOfWeek.isoDayNumber),
    timeText(range.start),
    timeText(range.stop),
)

/** `2026/3/5` — the original's `y/M/d`, which is also how the lab site writes a date. */
private fun dateText(value: LocalDateTime): String =
    "${value.year}/${value.month.number}/${value.day}"

private fun timeText(value: LocalDateTime): String =
    "${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"

/**
 * The mark, as the picture it was read from.
 *
 * The original showed the image straight from the report system (`Image.network`) and explained what
 * the recogniser did with it. Two things differ here, both because this app has no image-loading
 * pipeline: the bytes are fetched through the repository — the same request, made by the module rather
 * than by the widget — and the picture is drawn on a **white** backdrop. That backdrop is the original's
 * own `BoxDecoration(color: Colors.white)`, and it is load-bearing: the marks are dark glyphs on
 * transparency, and a theme that draws white text would make them invisible.
 */
@Composable
private fun ScoreDialog(
    entry: ExperimentEntry,
    repository: ExperimentRepository,
    onDismiss: () -> Unit,
) {
    val score = entry.score ?: return
    var image by remember(score.rawUrl) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(score.rawUrl) { mutableStateOf(true) }

    LaunchedEffect(score.rawUrl, repository) {
        image = repository.scoreImage(score.rawUrl)?.let { decodeImageBitmap(it) }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        if (score.found) {
                            Res.string.experiment_score_found_hint
                        } else {
                            Res.string.experiment_score_unknown_hint
                        },
                    ),
                    style = MaterialTheme.type.subHeadline,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.experiment_your_score),
                        style = MaterialTheme.type.subHeadline,
                    )
                    Spacer(Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .clip(MaterialTheme.specs.cardShape)
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            image != null -> Image(
                                bitmap = image!!,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.heightIn(max = 96.dp),
                            )

                            loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            else -> SymbolIcon(
                                name = Symbol.Error,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                if (score.found) {
                    Text(
                        text = stringResource(Res.string.experiment_predict_score, score.label),
                        style = MaterialTheme.type.subHeadline,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.experiment_confirm))
            }
        },
    )
}
