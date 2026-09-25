package com.nevoit.xdnext.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.nevoit.material.theme.MaterialTheme

/**
 * The generated course palette.
 *
 * **Not nullable, and with no default.** Everything under [ProvideClassPalette] is composed only once the
 * palette exists, so a reader here is a reader that has one — and a default would have to be either a
 * palette generated for a seed nobody chose or the theme's own colours, both of which are ways of drawing
 * a course in a colour it does not have. A missing provider is a wiring mistake and says so.
 */
internal val LocalClassPalette = staticCompositionLocalOf<ClassPalette> {
    error("No course palette: anything that draws a course must be composed under ProvideClassPalette")
}

/**
 * Provides the sixteen course colours, which are what a course is drawn in.
 *
 * The palette arrives as an argument rather than being generated here, because generating it is expensive
 * on a device — about 60 ms of interpreted colour maths for its thirty-two schemes — and *where* that
 * happens is a startup decision rather than this component's: `StartupPalettes` builds it on a worker
 * before the first frame and hands it over. Every caller that composes anything which draws a course must
 * be under this.
 *
 * It is deliberately not nullable and has no default for the same reason [LocalClassPalette] has none: a
 * frame drawn without it would be a frame drawn in colours no course has.
 */
@Composable
fun ProvideClassPalette(
    palette: ClassPalette,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalClassPalette provides palette, content = content)
}

/**
 * The swatch a course is drawn with, at [paletteIndex] — the same colour in the grid, on the campus page
 * and anywhere else a course appears.
 *
 * One lookup rather than one per drawing site: the index is the *source's* own (`TimeArrangement.index`,
 * the course's position in the details), so two call sites that both ask the palette for it necessarily
 * agree, and a site that picked its own colour would be the thing that looked wrong.
 */
@Composable
internal fun classSwatchFor(paletteIndex: Int): ClassSwatch =
    LocalClassPalette.current.swatchFor(index = paletteIndex, isDark = MaterialTheme.darkTheme)
