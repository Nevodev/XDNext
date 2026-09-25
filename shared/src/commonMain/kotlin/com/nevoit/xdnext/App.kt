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
    val palettes: StartupPalettes = koinInject()
    val seedColor = rememberSystemSeedColor()
    val darkTheme = resolveDarkTheme(ThemeMode.SYSTEM)

    MaterialTheme(
        darkTheme = darkTheme,
        seedColor = seedColor,
        colors = remember(palettes, seedColor, darkTheme) {
            palettes.themeColors(seedColor, darkTheme)
        },
    ) {
        ProvideClassPalette(palette = remember(seedColor) { palettes.courseColors(seedColor) }) {
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
