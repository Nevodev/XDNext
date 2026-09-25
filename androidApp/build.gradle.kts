import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

/**
 * The signing key, read the way Momento reads it.
 *
 * The four values are **not** in this repository: they live in `~/.gradle/gradle.properties` (or the
 * environment), which is where a secret belongs — a keystore path and two passwords in a checked-in
 * `gradle.properties` would be the whole key, published. `MOMENTO_SIGNING_*` rather than a name of this
 * project's own because it *is* Momento's key, the one at `D:/ASProjects/Nev Keystore/keys`: both
 * projects are installed on the same devices, and a second key would mean uninstalling one of them to
 * update the other.
 */
fun signingProperty(name: String) =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name))

val signingStoreFile = signingProperty("MOMENTO_SIGNING_STORE_FILE")
val signingStorePassword = signingProperty("MOMENTO_SIGNING_STORE_PASSWORD")
val signingKeyAlias = signingProperty("MOMENTO_SIGNING_KEY_ALIAS")
val signingKeyPassword = signingProperty("MOMENTO_SIGNING_KEY_PASSWORD")
val signingProperties = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
)
val hasSigningProperties = signingProperties.any { it.isPresent }
val isSigningConfigured = signingProperties.all { it.isPresent }

// All four or none: three of them would fail deep inside the packaging step, with the wrong message.
check(!hasSigningProperties || isSigningConfigured) {
    "XDNext signing requires all MOMENTO_SIGNING_* properties to be configured, not just some of them"
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    // Declared here as well as in :shared because :shared's Android dependencies are
    // `implementation`, not `api` — the app module starts Koin itself, so it must see it directly.
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Declared for the same reason: the Application logs the startup mark itself, so this module needs
    // `appLog`'s own type — Kermit's `Logger` — on its compile classpath.
    implementation(libs.kermit)

    // `ui-tooling` is deliberately absent: it carries `androidx.compose.material3` with it, and
    // nothing here uses Material 3. Only the `@Preview` annotation is kept, which does not.
    implementation(libs.compose.uiToolingPreview)
}

android {
    namespace = "com.nevoit.xdnext"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.nevoit.xdnext"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (isSigningConfigured) {
            create("momento") {
                storeFile = file(signingStoreFile.get())
                storePassword = signingStorePassword.get()
                keyAlias = signingKeyAlias.get()
                keyPassword = signingKeyPassword.get()
            }
        }
    }
    buildTypes {
        // Signed with the same key as release, deliberately: this build goes onto the same phones as the
        // other projects' builds, and an unsigned debug APK cannot be installed over one of them.
        debug {
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("momento")
            }
        }
        release {
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("momento")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}