package com.nevoit.xdnext.ui.energy

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.MaterialType
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.energy.EnergyState
import com.nevoit.xdnext.data.energy.MeterInfo
import com.nevoit.xdnext.ui.home.Card
import com.nevoit.xdnext.ui.home.formatDateTitle
import com.nevoit.xdnext.ui.shared.AppTopBar
import com.nevoit.xdnext.ui.shared.BlurShade
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun WaterFeeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: EnergyRepository = koinInject()
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository) {
        val current = repository.state.value
        if (current.info == null && !current.isLoading) repository.refreshElectricityInfo()
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
        ) {
            item {
                StatusBarSpacer()
                VGap(TopBarHeight)
            }
            item {
                WaterFeeRecords(
                    state = state,
                    onRefresh = { scope.launch { repository.refreshElectricityInfo() } },
                )
            }
            item {
                NavigationBarSpacer()
                VGap()
            }
        }
        BlurShade(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(TopBarHeight),
            backdrop = backdrop,
            visible = shadeVisible
        )
        AppTopBar(
            title = "水费",
            onBack = onBack,
            trailing = {
                IconButton(
                    enabled = !state.isLoading,
                    onClick = { scope.launch { repository.refreshElectricityInfo() } },
                ) {
                    SymbolIcon(name = Symbol.Refresh, contentDescription = "刷新水费")
                }
            },
        )
    }
}

@Composable
private fun WaterFeeRecords(
    state: EnergyState,
    onRefresh: () -> Unit,
) {
    val readings = state.info?.waterMeterList.orEmpty()

    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
        when {
            // Newest first, and sorted here rather than trusted from the query: the reading a user opens
            // this page for is the one at the top, whatever order the energy system sent them in.
            readings.isNotEmpty() -> WaterFeeTable(readings.sortedByDescending { it.readTime })

            state.isLoading -> Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(32.dp))
                Text("正在查询水费记录", style = MaterialTheme.type.body)
            }

            state.error != null -> Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "水费记录查询失败",
                    style = MaterialTheme.type.body,
                    color = MaterialTheme.colors.error,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("重新查询")
                }
            }

            else -> Text(
                "暂无水费记录",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.type.body,
                color = MaterialTheme.colors.contentVariant,
            )
        }
    }
}

/**
 * The readings as a table: a header, then one row per reading, newest at the top.
 *
 * The columns are declared once, in [WaterColumns], and read twice — the header and every row — which is
 * what holds them in line. The row of `SpaceBetween` texts this replaces placed each cell wherever its
 * own text happened to end, so the numbers under 用量 and under 当前 sat at a different offset on every
 * row and the header sat over none of them.
 */
@Composable
private fun WaterFeeTable(readings: List<MeterInfo>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp)
        ) {
            WaterColumns.forEach { column ->
                Text(
                    text = column.title,
                    modifier = Modifier.weight(column.weight),
                    style = MaterialTheme.type.footnoteEmphasized,
                    color = MaterialTheme.colors.contentVariant,
                    textAlign = column.align,
                )
            }
        }

        // The rule under the header is the one that says where the columns are named and where the data
        // starts; the rest only separate one reading from the next. All of them are inset to the cells'
        // own padding, so they read as part of the table rather than as a box around it.
        VDivider(modifier = Modifier.padding(horizontal = 16.dp))

        readings.forEachIndexed { index, reading ->
            if (index > 0) VDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                WaterColumns.forEach { column ->
                    Text(
                        text = column.value(reading),
                        modifier = Modifier.weight(column.weight),
                        style = column.style(MaterialTheme.type),
                        color = if (column.emphasized) {
                            MaterialTheme.colors.content
                        } else {
                            MaterialTheme.colors.contentVariant
                        },
                        textAlign = column.align,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * One column of the table: what it is called, how much of the width it takes, and what it prints.
 *
 * The width lives here rather than at the two call sites, so a header and its column cannot disagree
 * about where the column ends. [emphasized] marks the one number a row is *about* — the usage — and the
 * rest are the raw readings it was worked out from.
 */
private data class WaterColumn(
    val title: String,
    val weight: Float,
    val align: TextAlign,
    val emphasized: Boolean,
    val value: (MeterInfo) -> String,
    val style: (MaterialType) -> TextStyle,
)

private val WaterColumns = listOf(
    WaterColumn(
        title = "日期",
        // The widest column: a date with its year is the longest cell here, and the three numbers are
        // rarely more than six digits each.
        weight = 1.4f,
        align = TextAlign.Start,
        emphasized = false,
        value = { it.readDateText() },
        style = { it.footnote.copy(fontFeatureSettings = "tnum") },
    ),
    WaterColumn(
        title = "用量",
        weight = 1f,
        align = TextAlign.End,
        emphasized = true,
        value = { it.readNum.toPlain() },
        style = { it.footnoteEmphasized.copy(fontFeatureSettings = "tnum") },
    ),
    WaterColumn(
        title = "当前",
        weight = 1f,
        align = TextAlign.End,
        emphasized = false,
        value = { it.endNum.toPlain() },
        style = { it.footnote.copy(fontFeatureSettings = "tnum") },
    ),
    WaterColumn(
        title = "上次",
        weight = 1f,
        align = TextAlign.End,
        emphasized = false,
        value = { it.startNum.toPlain() },
        style = { it.footnote.copy(fontFeatureSettings = "tnum") },
    ),
)

/**
 * `2026年9月25日` — the date the rest of the app prints, with the year in front of it.
 *
 * The year is on every row rather than left to a page heading: this is what the energy system has on
 * record, it can reach back a year, and a bare `9月25日` would not say which one.
 */
private fun MeterInfo.readDateText(): String = "${readTime.year}年${formatDateTitle(readTime)}"

private fun Double.toPlain(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
