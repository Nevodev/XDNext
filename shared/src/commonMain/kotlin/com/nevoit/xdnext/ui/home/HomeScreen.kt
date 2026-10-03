package com.nevoit.xdnext.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.nevoit.material.core.component.DefaultNavigationBarHeight
import com.nevoit.material.core.component.NavigationBar
import com.nevoit.material.core.component.NavigationBarItem
import com.nevoit.material.core.component.Surface
import com.nevoit.material.core.component.Text
import com.nevoit.material.navigation.CrossfadeNavContainer
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.experiment.ExperimentRepository
import com.nevoit.xdnext.data.schoolcard.SchoolCardRepository
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.ui.settings.SettingsScreen
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon
import org.koin.compose.koinInject

enum class HomeTab(val label: String, val icon: String) {
    Focus("聚焦", Symbol.School),
    Toolbox("工具箱", Symbol.Widgets),
    Settings("设置", Symbol.Settings),
}

private val BarBottomMargin = 12.dp

internal val FloatingBarSpace = DefaultNavigationBarHeight + BarBottomMargin

@Composable
fun HomeScreen(
    onOpenTimetable: () -> Unit,
    onOpenEnergy: () -> Unit,
    onOpenWaterFee: () -> Unit,
    onOpenSchoolCard: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenTimetableStyle: () -> Unit,
    onOpenExperiments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Focus) }

    val energy: EnergyRepository = koinInject()
    val schoolCard: SchoolCardRepository = koinInject()
    val timetable: TimetableRepository = koinInject()
    val experiments: ExperimentRepository = koinInject()
    val cardRefresher = remember(energy, schoolCard, timetable, experiments) {
        CardRefresher(energy, schoolCard, timetable, experiments)
    }

    val tileActions = remember(
        onOpenTimetable,
        onOpenEnergy,
        onOpenWaterFee,
        onOpenSchoolCard,
        onOpenExperiments,
    ) {
        TileActions(
            openTimetable = onOpenTimetable,
            openEnergy = onOpenEnergy,
            openWaterFee = onOpenWaterFee,
            openSchoolCard = onOpenSchoolCard,
            openExperiments = onOpenExperiments,
        )
    }

    val colors = MaterialTheme.colors
    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = colors.pageBackground,
            topLeft = Offset(-this.size.width, -this.size.height),
            size = Size(this.size.width * 3, this.size.height * 3)
        )
        drawContent()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colors.pageBackground,
        contentColor = MaterialTheme.colors.content,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CrossfadeNavContainer(
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                destination = tab,
                destinations = HomeTab.entries,
            ) { destination ->
                when (destination) {
                    HomeTab.Focus -> FocusScreen(
                        energy = energy,
                        schoolCard = schoolCard,
                        timetable = timetable,
                        refresher = cardRefresher,
                        actions = tileActions,
                    )

                    HomeTab.Toolbox -> ToolboxScreen()
                    HomeTab.Settings -> SettingsScreen(
                        onOpenAccountSettings = onOpenAccountSettings,
                        onOpenTimetableStyle = onOpenTimetableStyle,
                    )
                }
            }

            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            padding = 32.dp.toPx()
                            blur(48.dp.toPx(), TileMode.Mirror)
                        }),
            ) {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        icon = { SymbolIcon(name = entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        }
    }
}
