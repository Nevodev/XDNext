package com.nevoit.xdnext.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.nevoit.material.navigation.NavigationHost
import com.nevoit.material.navigation.rememberNavigator
import com.nevoit.material.navigation.shape.clipShape
import com.nevoit.material.navigation.shape.deviceCornerShape
import com.nevoit.xdnext.ui.experiment.ExperimentScreen
import com.nevoit.xdnext.ui.experiment.rememberExperimentOverlays
import com.nevoit.xdnext.ui.home.HomeScreen
import com.nevoit.xdnext.ui.energy.EnergyScreen
import com.nevoit.xdnext.ui.energy.WaterFeeScreen
import com.nevoit.xdnext.ui.schoolcard.SchoolCardScreen
import com.nevoit.xdnext.ui.settings.AccountSettingsScreen
import com.nevoit.xdnext.ui.settings.TimetableStyleScreen
import com.nevoit.xdnext.ui.timetable.ClassChangeScreen
import com.nevoit.xdnext.ui.timetable.NotArrangedScreen
import com.nevoit.xdnext.ui.timetable.TimetableScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navigator = rememberNavigator<AppDestination>(root = AppDestination.Home)
    val navigationPageShape = deviceCornerShape()

    NavigationHost(
        modifier = modifier,
        navigator = navigator,
        transitionModifier = { page, isTransitioning ->
            page
                .graphicsLayer {
                    compositingStrategy = if (isTransitioning) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .clipShape(
                    shape = navigationPageShape,
                    enabled = isTransitioning,
                )
        }) {
        when (val destination = destination) {
            AppDestination.Home -> HomeScreen(
                onOpenTimetable = { navigate(AppDestination.Timetable) },
                onOpenEnergy = { navigate(AppDestination.Energy) },
                onOpenWaterFee = { navigate(AppDestination.WaterFee) },
                onOpenSchoolCard = { navigate(AppDestination.SchoolCard) },
                onOpenAccountSettings = { navigate(AppDestination.AccountSettings) },
                onOpenTimetableStyle = { navigate(AppDestination.TimetableStyle) },
                // 实验信息 is a tile on the campus page, which is where the original opened it from
                // too: the lab's bookings belong with the rest of the campus services, and the
                // timetable only *draws* them.
                onOpenExperiments = { navigate(AppDestination.Experiments) },
            )

            AppDestination.Energy -> EnergyScreen(onBack = ::back)
            AppDestination.WaterFee -> WaterFeeScreen(onBack = ::back)
            AppDestination.SchoolCard -> SchoolCardScreen(onBack = ::back)

            AppDestination.Timetable -> TimetableScreen(
                onBack = ::back,
                onOpenNotArranged = { navigate(AppDestination.NotArrangedClasses) },
                onOpenClassChanges = { navigate(AppDestination.ClassChanges) },
                // The physics experiment bookings are drawn on this grid, which is what makes the two
                // modules one screen: the overlay answers for a week and the grid places what it hands
                // back exactly as it places a course.
                overlays = rememberExperimentOverlays(),
            )

            AppDestination.NotArrangedClasses -> NotArrangedScreen(onBack = ::back)

            AppDestination.ClassChanges -> ClassChangeScreen(onBack = ::back)

            AppDestination.Experiments -> ExperimentScreen(
                onBack = ::back,
                // The page's own remedy for a missing password is the account settings page, which is
                // where the same credentials are edited; a second dialog here would be a second way to
                // write the same two values.
                onOpenAccountSettings = { navigate(AppDestination.AccountSettings) },
            )

            AppDestination.AccountSettings -> AccountSettingsScreen(onBack = ::back)
            AppDestination.TimetableStyle -> TimetableStyleScreen(onBack = ::back)
        }
    }
}
