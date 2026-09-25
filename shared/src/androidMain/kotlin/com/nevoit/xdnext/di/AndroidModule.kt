package com.nevoit.xdnext.di

import android.content.Context
import com.nevoit.xdnext.core.platform.PlatformContext
import com.nevoit.xdnext.core.store.AndroidSecureStore
import com.nevoit.xdnext.core.store.SecureStore
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.dsl.module

/**
 * Android bindings for the shared graph.
 *
 * `multiplatform-settings` is wired to an explicit `SharedPreferences` file rather than the
 * no-argument variant, so the file name is under this project's control. The original's preferences
 * lived in `FlutterSharedPreferences` with a `flutter.` key prefix because its home-screen widget
 * read that file directly; nothing here needs that coupling.
 */
val androidModule = module {

    single<Settings> {
        SharedPreferencesSettings(
            PlatformContext.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ),
        )
    }

    single<SecureStore> { AndroidSecureStore(PlatformContext.applicationContext) }
}

private const val PREFERENCES_NAME = "xdnext"
