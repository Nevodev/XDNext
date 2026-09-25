package com.nevoit.xdnext.di

import com.nevoit.xdnext.core.platform.supportDirectoryPath
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.schoolcard.SchoolCardApi
import com.nevoit.xdnext.data.schoolcard.SchoolCardCache
import com.nevoit.xdnext.data.schoolcard.SchoolCardDataSource
import com.nevoit.xdnext.data.schoolcard.SchoolCardRepository
import com.nevoit.xdnext.data.schoolcard.SchoolCardSession
import com.nevoit.xdnext.data.session.IdsSessionRepository
import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

/**
 * The campus card's dependency graph.
 *
 * Kept out of [sharedModule] for the reason [energyModule] is: a feature's wiring should be readable on
 * its own, and a test should be able to substitute one feature without dragging in the login graph.
 *
 * Two bindings carry the decisions:
 *
 *  - [SchoolCardApi] is built on the **IDS client**, not its own. The OAuth hop is what sets the card
 *    system's cookie, and a second client with a second cookie jar would have none of it.
 *  - the session's captcha solver is the same chained automatic-then-interactive solver the rest of the
 *    app uses, because entering the card system can raise a slider captcha of its own.
 */
val schoolCardModule = module {

    single {
        SchoolCardCache(
            fileSystem = FileSystem.SYSTEM,
            // Beside the cookie store and the energy cache, under the app's own support directory.
            directory = supportDirectoryPath().toPath(),
            json = get(),
        )
    }

    single { SchoolCardApi(client = get<HttpClient>(), json = get()) }

    single<SchoolCardDataSource> {
        SchoolCardSession(
            ids = get<IdsSessionRepository>(),
            api = get(),
            cache = get(),
            captcha = get<SliderCaptchaSolver>(),
        )
    }

    single { SchoolCardRepository(source = get()) }
}
