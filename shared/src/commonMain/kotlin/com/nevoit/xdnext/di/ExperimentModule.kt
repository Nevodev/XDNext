package com.nevoit.xdnext.di

import com.nevoit.xdnext.core.platform.supportDirectoryPath
import com.nevoit.xdnext.data.experiment.ExperimentCache
import com.nevoit.xdnext.data.experiment.ExperimentCredentials
import com.nevoit.xdnext.data.experiment.ExperimentDataSource
import com.nevoit.xdnext.data.experiment.ExperimentHttpClient
import com.nevoit.xdnext.data.experiment.ExperimentReportApi
import com.nevoit.xdnext.data.experiment.ExperimentRepository
import com.nevoit.xdnext.data.experiment.ExperimentScheduleApi
import com.nevoit.xdnext.data.experiment.ExperimentSession
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

/**
 * The physics experiment module's dependency graph.
 *
 * Kept out of [sharedModule] for the reason [timetableModule] and [energyModule] are: a feature's wiring
 * should be readable on its own, and a test should be able to substitute this feature's transport
 * without dragging in the login graph.
 *
 * Two bindings carry the decisions:
 *
 *  - the lab site is read with its **own** HTTP client ([ExperimentHttpClient]) rather than the IDS one.
 *    It is a different host with a different login, and a shared cookie jar would let either session
 *    overwrite the other's;
 *  - the session's credentials are [ExperimentCredentials], which reads the password out of the secure
 *    store and defaults the account to the one this app signed in with.
 *
 * The cache is a singleton because [ExperimentRepository] reads it in its constructor: two instances
 * would be two answers to "what were the last bookings", which is a state the page must not see two of.
 */
val experimentModule = module {

    single {
        ExperimentCache(
            fileSystem = FileSystem.SYSTEM,
            // Beside the cookie store, the energy cache's and the timetable's, under the app's own
            // support directory — and under the original's own file name.
            directory = supportDirectoryPath().toPath(),
            json = get(),
        )
    }

    single { ExperimentHttpClient() }

    single { ExperimentScheduleApi(client = get<ExperimentHttpClient>().client) }

    single { ExperimentReportApi(client = get<ExperimentHttpClient>().client, json = get()) }

    single { ExperimentCredentials(secureStore = get(), idsCredentials = get()) }

    single<ExperimentDataSource> {
        ExperimentSession(
            credentials = get(),
            schedule = get(),
            report = get(),
            cache = get(),
        )
    }

    single { ExperimentRepository(source = get()) }
}
