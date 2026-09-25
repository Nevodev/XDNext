package com.nevoit.xdnext.di

import com.nevoit.xdnext.core.platform.supportDirectoryPath
import com.nevoit.xdnext.data.energy.EnergyApi
import com.nevoit.xdnext.data.energy.EnergyCache
import com.nevoit.xdnext.data.energy.EnergyCrypto
import com.nevoit.xdnext.data.energy.EnergyDataSource
import com.nevoit.xdnext.data.energy.EnergyRepository
import com.nevoit.xdnext.data.energy.EnergySession
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.session.IdsSessionRepository
import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

/**
 * The electricity query's dependency graph.
 *
 * Kept out of [sharedModule] so a feature's wiring can be read on its own and a test can substitute
 * one feature without dragging in the login graph.
 *
 * Two bindings carry the interesting decisions:
 *
 *  - [EnergyApi] is built on the **IDS client**, not its own: the OAuth hop that the energy login
 *    performs is what sets the cookies that authorise every call after it, and a second client with a
 *    second cookie jar would have none of them.
 *  - the session's captcha solver is the same chained automatic-then-interactive solver the login
 *    screen uses, because entering the energy system can raise a slider captcha of its own.
 */
val energyModule = module {

    single { EnergyCrypto(get()) }

    single {
        EnergyCache(
            fileSystem = FileSystem.SYSTEM,
            // Beside the cookie store, under the app's own support directory, matching the original's
            // `<support dir>/EnergyInfo.json`.
            directory = supportDirectoryPath().toPath(),
            json = get(),
        )
    }

    single { EnergyApi(client = get<HttpClient>(), crypto = get(), json = get()) }

    single<EnergyDataSource> {
        EnergySession(
            ids = get<IdsSessionRepository>(),
            api = get(),
            cache = get(),
            captcha = get<SliderCaptchaSolver>(),
        )
    }

    single { EnergyRepository(source = get(), settings = get()) }
}
