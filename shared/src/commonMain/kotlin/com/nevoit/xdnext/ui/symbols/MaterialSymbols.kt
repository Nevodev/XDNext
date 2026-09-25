package com.nevoit.xdnext.ui.symbols

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.startup.StartupClock
import dev.tclement.fonticons.FontIcon
import dev.tclement.fonticons.IconFont
import dev.tclement.fonticons.LocalIconFont
import dev.tclement.fonticons.LocalIconTintProvider
import dev.tclement.fonticons.rememberStaticIconFont
import org.jetbrains.compose.resources.FontResource
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.material_symbols_sharp_24_400
import xdnext.shared.generated.resources.material_symbols_sharp_24_400_filled

/**
 * Icons, drawn from **Material Symbols Sharp** as an icon font.
 *
 * The glyph is chosen by *writing the icon's name as text* — the font carries `liga` ligatures that
 * substitute the name for the glyph — which is why [SymbolIcon] takes a [String] rather than a vector.
 * Same fonts, same library and same approach as the Rephoto project in this workspace.
 *
 * Two variants ship: `…24_400` (outlined) and `…24_400_filled`. The outlined one is the default
 * throughout, and the filled one is available per icon for selected states.
 *
 * [ProvideMaterialSymbols] must wrap anything that draws an icon: the library's `LocalIconFont` has no
 * fallback and throws if it is missing, which is a deliberate design choice on its part — a missing icon
 * font should fail loudly rather than render tofu.
 */
@Composable
fun ProvideMaterialSymbols(content: @Composable () -> Unit) {
    // The library can take a Compose Resources FontResource directly, which avoids a separate
    // conversion step on every platform.
    //
    // Both fonts are ~1.2 MB and Compose Resources reads them where they are named, which is here and
    // therefore inside the first composition. That is why this is timed: it is the only startup cost
    // left on the main thread that the warm-up in `XdNextApplication` cannot reach, so it is the one
    // that shows up in a slow first frame once everything else has moved off it.
    val startedAt = StartupClock.elapsedMs()
    val outlined = rememberSymbolFont(Res.font.material_symbols_sharp_24_400)
    val filled = rememberSymbolFont(Res.font.material_symbols_sharp_24_400_filled)
    // Remembered rather than logged: `remember` computes a value and may not return `Unit`, so the
    // measurement is the value and the log line is left to an effect.
    val loadMs = remember { StartupClock.elapsedMs() - startedAt }
    LaunchedEffect(Unit) {
        appLog.i { "[Startup] icon fonts loaded in ${loadMs}ms" }
    }

    CompositionLocalProvider(
        LocalIconFont provides outlined,
        LocalMaterialSymbolsFilled provides filled,
        // Icons then follow the same colour as the text beside them, as in Rephoto.
        LocalIconTintProvider provides LocalContentColor,
        content = content,
    )
}

@Composable
private fun rememberSymbolFont(resource: FontResource): IconFont =
    rememberStaticIconFont(fontResource = resource, fontFeatureSettings = LIGATURES)

/**
 * Draws one Material Symbol.
 *
 * @param name the icon's ligature name, e.g. [Symbol.Visibility].
 * @param filled draws the filled variant when one is available.
 */
@Composable
fun SymbolIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val filledFont = LocalMaterialSymbolsFilled.current
    if (filled && filledFont != null) {
        FontIcon(
            iconName = name,
            contentDescription = contentDescription,
            modifier = modifier,
            iconFont = filledFont,
        )
    } else {
        FontIcon(
            iconName = name,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

internal val LocalMaterialSymbolsFilled = staticCompositionLocalOf<IconFont?> { null }

/**
 * Ligature names for the Material Symbols this app uses.
 *
 * Kept as constants so a typo is a compile error rather than an empty box at runtime — a misspelt name
 * renders as the literal text, which is easy to miss in review and obvious on screen.
 *
 * Every name here is present in the bundled font: the font is a subset, so names that exist in the
 * published Material Symbols set — `smartphone` among them — can still be absent from it. Checking a
 * new name against the bundled `.ttf` in `composeResources/font` is the cheapest way to avoid
 * shipping literal text.
 */
object Symbol {
    const val Visibility = "visibility"
    const val VisibilityOff = "visibility_off"
    const val Person = "person"
    const val Lock = "lock"
    const val Login = "login"
    const val Logout = "logout"
    const val Refresh = "refresh"
    const val Error = "error"
    const val Warning = "warning"
    const val Success = "check_circle"
    const val Security = "security"
    const val Puzzle = "extension"
    const val Sms = "sms"
    const val QrCode = "qr_code"
    const val Chevron = "chevron_right"
    const val ChevronLeft = "chevron_left"
    const val ArrowBack = "arrow_back"

    /** The overflow menu's three dots. */
    const val MoreVert = "more_vert"
    const val MoreHoriz = "more_horiz"

    // Home tabs.
    const val School = "school"
    const val Widgets = "widgets"
    const val Settings = "settings"

    // Campus info: the three big cards and the course table.
    const val Bolt = "bolt"
    const val Book = "book_2"
    const val Library = "library_books"
    const val CreditCard = "credit_card"
    const val Calendar = "calendar_month"

    // Campus info: the small tiles.
    const val Grade = "grade"
    const val Exam = "event_note"
    const val EmptyRoom = "meeting_room"
    const val Assignment = "assignment"
    const val Wifi = "wifi"
    const val Water = "water_drop"
    const val WaterFee = "water_damage"
    const val Science = "science"
    const val Run = "directions_run"

    // Toolbox.
    const val Payments = "payments"
    const val Repair = "build"
    const val Seat = "event_seat"

    /** A globe: `smartphone` and `phone_android` are not in the bundled font subset. */
    const val Portal = "web"
    const val Calculator = "calculate"
    const val Explore = "explore"

    // Settings.
    const val Info = "info"
    const val Palette = "palette"
    const val Notifications = "notifications"
    const val Tune = "tune"
    const val Article = "article"
    const val Restart = "restart_alt"
    const val FormatSize = "format_size"
    const val DarkMode = "dark_mode"
    const val Language = "language"
    const val Image = "image"
    const val BatteryAlert = "battery_alert"
    const val ChevronLeftIOS = "arrow_back_ios_new"
}

/** `liga` is what turns the name above into a glyph. */
private const val LIGATURES = "liga"
