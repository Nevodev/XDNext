package com.nevoit.xdnext.core.platform

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

object PlatformContext {
    lateinit var applicationContext: Context
        private set

    fun initialise(context: Context) {
        applicationContext = context.applicationContext
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun supportDirectoryPath(): String =
    PlatformContext.applicationContext.filesDir.absolutePath

actual fun createPlatformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) { config(this) }
