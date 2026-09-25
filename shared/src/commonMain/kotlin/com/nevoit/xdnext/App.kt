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
                // Once, on the first composition: the stored account goes back into the form, and a device
                // that has logged in before restores its session behind the shell.
                LaunchedEffect(login) { login.start() }

                // Which screen this is was decided before anything was drawn. `loggedIn` is seeded in the
                // view model's constructor from a preference read, not from a request, so a warm start
                // composes the shell straight away — no frame shows the login screen and then navigates
                // off it, which is a flash, not navigation. The two sides are not pages of the navigator
                // and no transition is played between them: signing in or out swaps the composition, which
                // is also why back cannot return to a form that has been answered.
                val loggedIn by login.loggedIn.collectAsState()

                if (loggedIn) {
                    AppNavHost()
                } else {
                    // The shell paints the page background in its own `Surface`; the login screen is the
                    // whole composition when it is showing, so it has to bring its own.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colors.pageBackground,
                        contentColor = MaterialTheme.colors.content,
                    ) {
                        LoginScreen(viewModel = login)
                    }
                }

                // Above both, because a background refresh can raise a slider captcha or a second factor
                // long after the login screen is gone.
                LoginInteractions(login)
            }
        }
    }
}
