package com.nevoit.xdnext.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.nevoit.material.navigation.NavigationHost
import com.nevoit.material.navigation.rememberNavigator
import com.nevoit.material.navigation.shape.clipShape
import com.nevoit.material.navigation.shape.deviceCornerShape
import com.nevoit.xdnext.ui.home.HomeScreen
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
            )

            AppDestination.Timetable -> TimetableScreen(
                onBack = ::back,
                onOpenNotArranged = { navigate(AppDestination.NotArrangedClasses) },
                onOpenClassChanges = { navigate(AppDestination.ClassChanges) },
            )

            AppDestination.NotArrangedClasses -> NotArrangedScreen(onBack = ::back)

            AppDestination.ClassChanges -> ClassChangeScreen(onBack = ::back)
        }
    }
}
