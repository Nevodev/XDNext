package com.nevoit.xdnext

import android.app.Application
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.log.installPlatformLogging
import com.nevoit.xdnext.core.platform.PlatformContext
import com.nevoit.xdnext.core.startup.AppWarmUp
import com.nevoit.xdnext.core.startup.StartupClock
import com.nevoit.xdnext.di.androidModule
import com.nevoit.xdnext.di.energyModule
import com.nevoit.xdnext.di.schoolCardModule
import com.nevoit.xdnext.di.sharedModule
import com.nevoit.xdnext.di.timetableModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Android entry point.
 *
 * Three things happen before any UI exists, and all must happen here rather than in an activity:
 *
 *  - [PlatformContext] is initialised, because the cookie store needs the support directory and the
 *    secure store needs a context, and both are constructed lazily by the dependency graph;
 *  - Koin is started, so `koinInject` works from the very first composition;
 *  - [AppWarmUp] is started, because a first composition that has to build the graph is a first
 *    composition that waits for it. What to warm up is the dependency graph's business, so the list
 *    lives with the modules rather than here.
 *
 * The original did the equivalent work by assigning mutable globals at the top of `main()`.
 */
class XdNextApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // The zero every later startup line is measured from, taken before anything else this app does.
        val startMark = StartupClock.elapsedMs()

        // Before anything can log: Kermit ships with no writers, so without this every line the app
        // reports — a stuck request, a rejected login, a dropped row — is discarded.
        installPlatformLogging()

        PlatformContext.initialise(this)

        appLog.i { "[Startup] Application.onCreate at ${startMark}ms" }

        startKoin {
            androidContext(this@XdNextApplication)
            modules(sharedModule, energyModule, schoolCardModule, timetableModule, androidModule)
        }.koin.get<AppWarmUp>().start()
    }
}
