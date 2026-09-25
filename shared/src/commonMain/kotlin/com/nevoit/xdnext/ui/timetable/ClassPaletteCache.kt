package com.nevoit.xdnext.ui.timetable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nevoit.xdnext.core.log.appLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * The generated course colours, kept between launches: `ClassPalette.json` in the support directory.
 *
 * Sixteen seeds in two themes is thirty-two schemes, measured at about 180 ms of interpreted colour maths
 * on a device — and the result is a pure function of one input, the system accent. It changes when the
 * wallpaper changes and essentially never otherwise, which is what makes it worth keeping: the app already
 * caches the timetable, the electricity reading and the campus card this way, and the reason is the same
 * one every time — a value that is expensive to compute and cheap to store should be computed once.
 *
 * **Keyed by the seed, and checked on read.** A cached palette is only an answer for the accent it was
 * generated from, so [read] is handed the seed the platform reports *now* and returns nothing unless the
 * file agrees with it. That check is the whole safety property: the file cannot outlive the wallpaper it
 * describes, and an accent that changed since the last launch regenerates rather than repainting every
 * course in colours nobody chose.
 *
 * A read that fails is a cache **miss**, never an error — the rule every cache in this app follows. An
 * unreadable or half-written file degrades to one generation, which is recoverable; a failure that
 * propagated would take down the frame that asked.
 */
class ClassPaletteCache(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val json: Json,
) {

    /**
     * The colours cached for [seed], or null when there are none to read.
     *
     * Null means "generate them": no file, an unreadable one, one written by an older layout, or one for a
     * different accent — all four are the same answer to the caller.
     */
    fun read(seed: Color): CachedClassPalette? {
        if (!fileSystem.exists(path)) return null

        val text = runCatching { fileSystem.read(path) { readUtf8() } }
            .onFailure { appLog.w(it) { "Could not read $path" } }
            .getOrNull()
            ?: return null

        val cached = runCatching { json.decodeFromString<Wire>(text) }
            .onFailure { appLog.w(it) { "Discarding unreadable $PALETTE_FILE" } }
            .getOrNull()
            ?: return null

        if (cached.version != VERSION) {
            appLog.i { "[ClassPaletteCache] $PALETTE_FILE is from layout ${cached.version}; regenerating" }
            return null
        }
        if (cached.seed != seed.toArgb()) {
            // The wallpaper moved since this was written. Reported at debug level rather than as a
            // problem: changing one's wallpaper is a normal thing to do.
            appLog.i { "[ClassPaletteCache] $PALETTE_FILE was generated for another accent; regenerating" }
            return null
        }

        val light = cached.light.toSwatches()
        val dark = cached.dark.toSwatches()
        if (light == null || dark == null) {
            appLog.w { "[ClassPaletteCache] $PALETTE_FILE does not hold two themes' worth of colours" }
            return null
        }

        appLog.i { "[ClassPaletteCache] Reusing the colours generated for this accent" }
        return CachedClassPalette(light = light, dark = dark)
    }

    /** Writes the colours for [seed]. A failure to write is logged and dropped: the palette is in hand. */
    fun save(seed: Color, light: List<ClassSwatch>, dark: List<ClassSwatch>) {
        val encoded = runCatching {
            json.encodeToString(
                Wire(
                    version = VERSION,
                    seed = seed.toArgb(),
                    light = light.toArgbs(),
                    dark = dark.toArgbs(),
                ),
            )
        }.onFailure {
            appLog.w(it) { "Could not encode the course colours for $PALETTE_FILE" }
        }.getOrNull() ?: return

        runCatching {
            fileSystem.createDirectories(path.parent!!)
            fileSystem.write(path) { writeUtf8(encoded) }
        }.onFailure {
            appLog.w(it) { "Could not write $path" }
        }
    }

    /**
     * What a cache hit hands back: the two themes' swatches, ready to be put in a [ClassPalette] without
     * generating anything.
     */
    data class CachedClassPalette(
        val light: List<ClassSwatch>,
        val dark: List<ClassSwatch>,
    )

    /**
     * The file's shape.
     *
     * Flat ARGB lists rather than a nested object: a swatch is four colours whose *order* is the codec —
     * [toArgbs] and [toSwatches] are written next to each other, in the same order, and
     * `ClassPaletteCacheTest` round-trips them, because a list of numbers has no other way to say which
     * number is which.
     *
     * [version] exists so that a future change to the generated roles discards old files instead of
     * drawing with them. **Bump it whenever the palette's meaning changes** — a new library version that
     * generates different colours, or a different scheme variant.
     */
    @Serializable
    private data class Wire(
        val version: Int,
        val seed: Int,
        val light: List<Int>,
        val dark: List<Int>,
    )

    private companion object {
        const val PALETTE_FILE = "ClassPalette.json"

        /** The layout of [Wire]. See its documentation before changing anything it describes. */
        const val VERSION = 1

        /** The swatch order every colour list in the file is written and read in. */
        fun List<ClassSwatch>.toArgbs(): List<Int> = flatMap { swatch ->
            listOf(
                swatch.primary.toArgb(),
                swatch.primaryContainer.toArgb(),
                swatch.onPrimary.toArgb(),
                swatch.onPrimaryContainer.toArgb(),
            )
        }

        /** [toArgbs] backwards, or null when the list is not a whole number of swatches. */
        fun List<Int>.toSwatches(): List<ClassSwatch>? {
            if (size % SWATCH_COLOURS != 0) return null
            return chunked(SWATCH_COLOURS).map { (primary, container, onPrimary, onContainer) ->
                ClassSwatch(
                    primary = Color(primary),
                    primaryContainer = Color(container),
                    onPrimary = Color(onPrimary),
                    onPrimaryContainer = Color(onContainer),
                )
            }
        }

        const val SWATCH_COLOURS = 4
    }
}
