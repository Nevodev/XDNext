package com.nevoit.xdnext.di

import com.nevoit.material.theme.systemIsDarkTheme
import com.nevoit.material.theme.systemSeedColor
import com.nevoit.xdnext.core.crypto.AesCbc
import com.nevoit.xdnext.core.platform.supportDirectoryPath
import com.nevoit.xdnext.core.startup.AppWarmUp
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.ids.IdsAuthApi
import com.nevoit.xdnext.data.ids.IdsCrypto
import com.nevoit.xdnext.data.ids.IdsReAuthHandler
import com.nevoit.xdnext.data.ids.SliderCaptchaAutoSolver
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.ids.SliderCaptchaVerifier
import com.nevoit.xdnext.data.net.FileCookieStorage
import com.nevoit.xdnext.data.net.HttpClientFactory
import com.nevoit.xdnext.data.schoolcard.SchoolCardRepository
import com.nevoit.xdnext.data.session.ChainedSliderCaptchaSolver
import com.nevoit.xdnext.data.session.CredentialStore
import com.nevoit.xdnext.data.session.IdsReAuthBroker
import com.nevoit.xdnext.data.session.IdsSessionRepository
import com.nevoit.xdnext.data.session.SliderCaptchaBroker
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.ui.StartupPalettes
import com.nevoit.xdnext.ui.login.LoginViewModel
import com.nevoit.xdnext.ui.timetable.ClassPaletteCache
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

/**
 * Shared dependency graph.
 *
 * The original had no DI container for the app at all: controllers were `static final X i` singletons
 * and sessions were constructed ad hoc, which is why nothing could be substituted in a test. Koin is
 * used here mainly so the login slice — including both interactive steps — can be exercised against
 * fakes.
 *
 * Platform-specific bindings (`Settings`, `SecureStore`) live in the platform module, so this module
 * stays free of `expect`/`actual` concerns.
 */
val sharedModule = module {

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }

    single { AesCbc() }
    single { IdsCrypto(get()) }

    // The palettes are built *in this constructor*, on whichever thread resolves it first, so that the
    // composition's resolution is a hand-off: the start-up warm-up builds it on a worker and the first
    // frame waits there for the real thing rather than building its own. See `StartupPalettes`.
    single {
        StartupPalettes(
            seed = systemSeedColor(),
            isDarkTheme = systemIsDarkTheme(),
            cache = get(),
        )
    }

    // The course colours are the expensive half of that and a pure function of the accent, so they are
    // kept between launches. Beside the cookie store and the other caches, under the app's own support
    // directory — see `ClassPaletteCache`.
    single {
        ClassPaletteCache(
            fileSystem = FileSystem.SYSTEM,
            path = supportDirectoryPath().toPath() / "ClassPalette.json",
            json = get(),
        )
    }

    single { SettingsStore(get()) }
    single { CredentialStore(get(), get(), get()) }

    // The cookie file lives beside the other support files, mirroring the original's
    // `<support directory>/cookie/general` layout.
    single {
        FileCookieStorage(
            fileSystem = FileSystem.SYSTEM,
            path = supportDirectoryPath().toPath() / "cookie" / "ids.json",
            json = get(),
        )
    }

    single { HttpClientFactory(get()) }
    single { get<HttpClientFactory>().createIdsClient() }

    single { IdsAuthApi(get(), get(), get()) }

    // --- Slider captcha -------------------------------------------------------------------------
    single { SliderCaptchaVerifier(get(), get(), get()) }
    single {
        SliderCaptchaAutoSolver(
            verifier = get(),
            // A rejected challenge is spent, so the solver needs a way to replace it — the original
            // refreshed the captcha and retried, and that is what this supplies.
            challengeProvider = { get<IdsAuthApi>().fetchSliderCaptcha() },
        )
    }
    single { SliderCaptchaBroker(get(), get()) }

    // Automatic first because it is fast and, with a correctly normalised correlation, accurate; the
    // interactive solver second because it always works. See tools/README.md for the measurements.
    single<SliderCaptchaSolver> {
        ChainedSliderCaptchaSolver(
            listOf(get<SliderCaptchaAutoSolver>(), get<SliderCaptchaBroker>()),
        )
    }

    // --- Second factor --------------------------------------------------------------------------
    single { IdsReAuthBroker() }
    single<IdsReAuthHandler> { get<IdsReAuthBroker>() }

    single { IdsSessionRepository(get(), get(), get(), get()) }

    // App-scoped on purpose: the session must outlive any single screen, so this is not tied to a
    // composition or an Android ViewModelStore.
    single { LoginViewModel(get(), get(), get(), get(), get()) }

    /**
     * What the first composition would otherwise have to build on the main thread.
     *
     * The list is here, beside the bindings it names, rather than in the Application that starts it:
     * which singletons are worth building early is a fact about this graph — the three repositories that
     * read a cache in their constructors, and the login graph that carries the HTTP client — and about
     * nothing on either platform. The thunks are lazy, so declaring them constructs none of them.
     */
    single {
        AppWarmUp(
            listOf(
                // First, because the first frame cannot be drawn without them: `MaterialTheme` paints with
                // the theme palette and the campus page draws courses in the course colours. Building them
                // here — with the platform's own seed and mode, on a worker — is what keeps that work off
                // the frame, which waits for this step instead of repeating it. About 40 ms plus 180 ms in
                // a debug build; see `StartupPalettes`.
                AppWarmUp.Step("palettes") { get<StartupPalettes>() },
                AppWarmUp.Step("timetable cache") { get<TimetableRepository>() },
                AppWarmUp.Step("energy cache") { get<EnergyRepository>() },
                AppWarmUp.Step("campus card cache") { get<SchoolCardRepository>() },
                AppWarmUp.Step("login graph") { get<LoginViewModel>() },
            ),
        )
    }
}
