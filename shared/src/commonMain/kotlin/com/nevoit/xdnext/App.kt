package com.nevoit.xdnext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nevoit.material.core.component.Surface
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.ThemeMode
import com.nevoit.material.theme.rememberSystemSeedColor
import com.nevoit.material.theme.resolveDarkTheme
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.startup.StartupClock
import com.nevoit.xdnext.ui.AppNavHost
import com.nevoit.xdnext.ui.StartupPalettes
import com.nevoit.xdnext.ui.login.LoginInteractions
import com.nevoit.xdnext.ui.login.LoginScreen
import com.nevoit.xdnext.ui.login.LoginViewModel
import com.nevoit.xdnext.ui.symbols.ProvideMaterialSymbols
import com.nevoit.xdnext.ui.timetable.ProvideClassPalette
import org.koin.compose.koinInject

@Composable
fun App() {
    val composedAt = remember { StartupClock.elapsedMs() }

    // Resolved before anything is drawn, and that is the point: the start-up warm-up is building the real
    // palettes from the platform's own seed on a worker, so if it has not finished, this waits for *that*
    // construction on the container's lock instead of building a second copy on the frame. See
    // `StartupPalettes` for what it costs and why it is worth waiting for.
    val palettes: StartupPalettes = koinInject()
    val palettesReadyAt = remember { StartupClock.elapsedMs() }
    val seedColor = rememberSystemSeedColor()
    val darkTheme = resolveDarkTheme(ThemeMode.SYSTEM)

    MaterialTheme(
        darkTheme = darkTheme,
        seedColor = seedColor,
        colors = remember(palettes, seedColor, darkTheme) {
            palettes.themeColors(seedColor, darkTheme)
        },
    ) {
        val themeReadyAt = remember { StartupClock.elapsedMs() }
        val coursePalette = remember(seedColor) { palettes.courseColors(seedColor) }
        val courseReadyAt = remember { StartupClock.elapsedMs() }
        LaunchedEffect(Unit) {
            val frameAt = StartupClock.elapsedMs()
            appLog.i {
                // Four numbers, because they mean different things and only two of them are this frame's
                // own work. `waited` is time spent on the warm-up's worker — the palettes it prepares are
                // used if they are ready by now and built here if they are not, so a worker that is still
                // busy costs nothing but also buys nothing. `theme` and `course` are what this frame
                // really spent; `first frame` is where the composition ended, which includes everything
                // else the shell does.
                "[Startup] palettes waited ${palettesReadyAt - composedAt}ms, theme " +
                        "${themeReadyAt - palettesReadyAt}ms, course ${courseReadyAt - themeReadyAt}ms, " +
                        "first frame at ${frameAt}ms (${frameAt - composedAt}ms of composition)"
            }
        }

        ProvideClassPalette(palette = coursePalette) {
            ProvideMaterialSymbols {
                val login: LoginViewModel = koinInject()
                LaunchedEffect(login) { login.start() }
                val loggedIn by login.loggedIn.collectAsState()

                if (loggedIn) {
                    AppNavHost()
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colors.pageBackground,
                        contentColor = MaterialTheme.colors.content,
                    ) {
                        LoginScreen(viewModel = login)
                    }
                }

                LoginInteractions(login)
            }
        }
    }
}
