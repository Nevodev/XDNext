package com.nevoit.xdnext.ui.home

import com.nevoit.xdnext.ui.symbols.Symbol

/**
 * Every tile the campus page can show.
 *
 * A tile is addressed by this id, never by its position. A user-made order — or any future
 * rearrangement — is a `List<HomeTileId>`, so inserting, removing or renaming a tile cannot silently
 * move somebody else's, and an order read back from disk still means something after an upgrade.
 *
 * Adding a tile is three deliberate edits: an entry here, its [span], and a branch in `FocusScreen`'s
 * renderer. That `when` is exhaustive on purpose, so a new id fails the build instead of quietly
 * vanishing from the page.
 */
enum class HomeTileId {
    /** The timetable, as the full-width banner across the top. */
    Timetable,

    /** The electricity reading. The one tile backed by a real module today. */
    Energy,

    Library,
    CampusPay,

    Grade,
    Exam,
    EmptyRoom,
    Attendance,
    Network,
    Water,
    Science,
    Sport,
}

/** How much of the grid a tile takes. The readings are square widgets; everything else is one cell. */
val HomeTileId.span: TileSpan
    get() = when (this) {
        HomeTileId.Timetable -> TileSpan(columns = 4, rows = 2)
        HomeTileId.Energy, HomeTileId.Library, HomeTileId.CampusPay -> TileSpan(
            columns = 2,
            rows = 2
        )

        HomeTileId.Grade, HomeTileId.Exam, HomeTileId.EmptyRoom, HomeTileId.Attendance,
        HomeTileId.Network, HomeTileId.Water, HomeTileId.Science, HomeTileId.Sport -> TileSpan()
    }

/**
 * The order the page is laid out in until the user makes one of their own.
 *
 * [packTiles] hands the first free cell to each tile in turn, so this list **is** the layout: the
 * banner, then the square widgets, then the single cells that fill in beside them. A stored order will
 * replace this list at the one place it is read — see `FocusScreen`.
 */
val DefaultHomeOrder: List<HomeTileId> = listOf(
    HomeTileId.Timetable,
    HomeTileId.Energy,
    HomeTileId.Library,
    HomeTileId.CampusPay,
    HomeTileId.Grade,
    HomeTileId.Exam,
    HomeTileId.EmptyRoom,
    HomeTileId.Attendance,
    HomeTileId.Network,
    HomeTileId.Water,
    HomeTileId.Science,
    HomeTileId.Sport,
)

/** The icon and text of a single-cell shortcut. */
internal data class ShortcutSpec(val symbol: String, val label: String)

/**
 * How each shortcut draws itself.
 *
 * Only the single-cell tiles appear here; the ones with their own widget — the banner, the three
 * readings — are rendered directly. `HomeTileRegistryTest` asserts that this covers exactly the ids
 * whose [span] is one cell, so a tile cannot be left with no renderer.
 */
internal val ShortcutSpecs: Map<HomeTileId, ShortcutSpec> = mapOf(
    HomeTileId.Grade to ShortcutSpec(Symbol.Grade, "成绩查询"),
    HomeTileId.Exam to ShortcutSpec(Symbol.Exam, "考试安排"),
    HomeTileId.EmptyRoom to ShortcutSpec(Symbol.EmptyRoom, "空闲教室"),
    HomeTileId.Attendance to ShortcutSpec(Symbol.Assignment, "考勤查询"),
    HomeTileId.Network to ShortcutSpec(Symbol.Wifi, "网络查询"),
    HomeTileId.Water to ShortcutSpec(Symbol.Water, "宿舍水机"),
    HomeTileId.Science to ShortcutSpec(Symbol.Science, "实验信息"),
    HomeTileId.Sport to ShortcutSpec(Symbol.Run, "体育信息"),
)
