package com.nevoit.xdnext.ui.timetable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the course colours' on-disk cache.
 *
 * Three rules, and each of them is a way the cache could hurt rather than help:
 *
 *  - a palette written for one accent must never be served for another, or the whole app would be painted
 *    in the colours of a wallpaper the user has changed;
 *  - a file that cannot be read is a *miss*, not a failure — the same rule the timetable, energy and campus
 *    card caches follow, and the reason a half-written file costs one generation instead of a crash;
 *  - what comes back must be exactly what went in. The colours are stored as flat lists of numbers, which
 *    carry no names, so the only thing keeping the encode and the decode in step is this round trip.
 */
class ClassPaletteCacheTest {

    private val seed = Color(0xFF1B6EF3)

    @Test
    fun hasNothingToSayBeforeAnythingIsWritten() {
        assertNull(cache().read(seed))
    }

    @Test
    fun roundTripsTheColoursExactly() {
        val cache = cache()
        val palette = ClassPalette.from(seed)
        cache.save(seed, light = palette.light, dark = palette.dark)

        val cached = assertNotNull(cache.read(seed))

        assertEquals(palette.light, cached.light)
        assertEquals(palette.dark, cached.dark)
        assertEquals(ClassPalette.Seeds.size, cached.light.size)
        assertEquals(ClassPalette.Seeds.size, cached.dark.size)
    }

    @Test
    fun refusesACacheGeneratedForAnotherAccent() {
        // The check the caller relies on: the file says which seed it belongs to, and a wallpaper change
        // makes it worthless rather than subtly wrong.
        val cache = cache()
        val palette = ClassPalette.from(seed)
        cache.save(seed, light = palette.light, dark = palette.dark)

        assertNull(cache.read(Color(0xFFD32F2F)))
        assertNotNull(cache.read(seed), "The file itself is still good for its own seed.")
    }

    @Test
    fun anUnreadableFileIsAMissRatherThanAFailure() {
        val directory = directory()
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(directory / FILE_NAME) { writeUtf8("{ this is not the palette") }

        assertNull(ClassPaletteCache(FileSystem.SYSTEM, directory / FILE_NAME, JSON).read(seed))
    }

    @Test
    fun aFileFromAnOlderLayoutIsAMiss() {
        // A future version that changes what the roles mean has to be able to discard these rather than
        // draw with them; the version field is how, and this pins that it is checked.
        val directory = directory()
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.write(directory / FILE_NAME) {
            writeUtf8("""{"version":0,"seed":${seed.toArgb()},"light":[],"dark":[]}""")
        }

        assertNull(ClassPaletteCache(FileSystem.SYSTEM, directory / FILE_NAME, JSON).read(seed))
    }

    @Test
    fun writesTheFileIntoTheDirectoryItWasGiven() {
        val directory = directory()
        val cache = ClassPaletteCache(FileSystem.SYSTEM, directory / FILE_NAME, JSON)
        val palette = ClassPalette.from(seed)

        cache.save(seed, light = palette.light, dark = palette.dark)

        assertTrue(FileSystem.SYSTEM.exists(directory / FILE_NAME))
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun directory(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "xdnext-class-palette-${instances++}"

    private fun cache(): ClassPaletteCache =
        ClassPaletteCache(FileSystem.SYSTEM, directory() / FILE_NAME, JSON)

    private companion object {
        const val FILE_NAME = "ClassPalette.json"

        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /** One file per test, so no two of them share a cache. */
        var instances = 0
    }
}
