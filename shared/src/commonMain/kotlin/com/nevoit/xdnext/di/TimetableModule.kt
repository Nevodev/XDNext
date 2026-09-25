package com.nevoit.xdnext.di

import com.nevoit.xdnext.core.platform.supportDirectoryPath
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.session.IdsSessionRepository
import com.nevoit.xdnext.data.timetable.TimetableApi
import com.nevoit.xdnext.data.timetable.TimetableCache
import com.nevoit.xdnext.data.timetable.TimetableDataSource
import com.nevoit.xdnext.data.timetable.TimetableRepository
import com.nevoit.xdnext.data.timetable.TimetableSession
import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

/**
 * The timetable's dependency graph.
 *
 * Kept out of [sharedModule] for the reason [energyModule] and [schoolCardModule] are: a feature's
 * wiring should be readable on its own, and a test should be able to substitute this feature's
 * transport without dragging in the login graph.
 *
 * Two bindings carry the decisions, and both are the original's own:
 *
 *  - [TimetableApi] is built on the **IDS client**, not its own. The CAS hop is what sets eHall's
 *    session cookie, and a second client with a second cookie jar would have none of it.
 *  - the session's captcha solver is the same chained automatic-then-interactive solver the rest of
 *    the app uses, because entering the registrar can raise a slider captcha of its own.
 *
 * The cache is a singleton because [TimetableRepository] reads it in its constructor: two instances
 * would be two answers to "what was the last timetable", which is a state the page must not see two
 * of.
 */
val timetableModule = module {

    single {
        TimetableCache(
            fileSystem = FileSystem.SYSTEM,
            // Beside the cookie store, the energy cache and the campus card's, under the app's own
            // support directory — and under the original's own file name.
            directory = supportDirectoryPath().toPath(),
            json = get(),
        )
    }

    single { TimetableApi(client = get<HttpClient>(), json = get()) }

    single<TimetableDataSource> {
        TimetableSession(
            ids = get<IdsSessionRepository>(),
            api = get(),
            cache = get(),
            settings = get(),
            captcha = get<SliderCaptchaSolver>(),
        )
    }

    single { TimetableRepository(source = get()) }
}
