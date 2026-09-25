import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {

    android {
        namespace = "com.nevoit.xdnext.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Coroutines / Flow
            implementation(libs.kotlinx.coroutines.core)

            // Serialization & time
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Network
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.okio)

            // HTML / charset
            implementation(libs.ksoup)
            implementation(libs.fleeksoft.charset)
            implementation(libs.fleeksoft.charset.ext)
            implementation(libs.fleeksoft.io)

            // Crypto
            implementation(libs.cryptography.core)

            // Icons: Material Symbols rendered as a font, selected by ligature name.
            implementation(libs.fonticons.core)

            // Settings
            implementation(libs.multiplatform.settings)

            // DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Logging
            implementation(libs.kermit)

            implementation(libs.shapes)
            implementation(libs.backdrop)

            // Theme: the Material You palette is generated from the system accent colour.
            // Only the colour maths is taken, not MaterialKolor's Compose wrapper — that one
            // drags the official Material 3 in, which this project does not use.
            implementation(libs.material.color.utilities)

            implementation(libs.androidx.graphics.shapes)
        }

        androidMain.dependencies {
            // The androidMain actual of PlatformBackHandler delegates to the platform's own back
            // handling, which lives in activity-compose.
            implementation(libs.androidx.activity.compose)

            // `ui-tooling` is deliberately absent: it carries `androidx.compose.material3` with it,
            // and nothing here uses Material 3. Only the `@Preview` annotation is kept, which does
            // not. Adding tooling back would re-introduce Material 3 in the APK.
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.multiplatform.settings.noarg)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
    }
}
