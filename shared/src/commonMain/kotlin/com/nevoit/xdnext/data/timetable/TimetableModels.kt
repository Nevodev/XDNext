package com.nevoit.xdnext.data.timetable

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The registrar's timetable, as `data.timetable` sees it.
 *
 * Ported from `lib/model/xidian_ids/classtable.dart`. The shapes and the field names are the
 * original's, including the ones that are load-bearing rather than incidental — [TimeArrangement]'s
 * index semantics, [ClassDetail]'s equality, and the boolean week list. Each of those is called out
 * where it lives, because each is the kind of thing a tidy-up would break silently.
 */

/**
 * Where a [TimeArrangement]'s course detail lives.
 *
 * The original declared `{empty, school}` plus — per `docs/model/class_table_model.md` — a `user`
 * variant for locally added courses, whose index points into a *second* detail list. `empty` is a
 * placeholder nothing ever produces, so it is not carried over; `user` is the next variant and the
 * doc says exactly what it needs ([ClassTableData.detailFor] is the one place that must learn it).
 *
 * The enumeration is serialized by name rather than by ordinal so a reordering cannot silently
 * reinterpret an existing `ClassTable.json`.
 */
@Serializable
enum class ClassSource {
    /** A course the registrar's own timetable answered with. */
    @SerialName("school")
    School,
}

/** A course, without any time or place: the part a [TimeArrangement] points at. */
@Serializable
class ClassDetail(
    val name: String,
    val code: String? = null,
    val number: String? = null,
) {

    /**
     * Two details are the same course when their **names** match.
     *
     * This is the original's own rule, and the parser depends on it: it de-duplicates the registrar's
     * rows with `classDetail.contains(...)` and then resolves an arrangement's `index` with
     * `indexOf(...)`. A structural equality over all three fields would leave two rows for one course
     * whenever the server reports a different `code` for the same class, and both of those calls
     * would answer differently than the original did.
     */
    override fun equals(other: Any?): Boolean = other is ClassDetail && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "$name $code $number"
}

/**
 * A course that exists in the registrar's records but has no place in the weekly grid.
 *
 * Kept apart from [ClassDetail] because it carries a teacher and is never indexed by an arrangement.
 */
@Serializable
class NotArrangementClassDetail(
    val name: String,
    val code: String? = null,
    val number: String? = null,
    val teacher: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is NotArrangementClassDetail && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "$name $code $number"
}

/**
 * One occupying of the week grid: which course, which weeks, which day, which periods, where.
 *
 * Three of these five fields are the original's own conventions rather than obvious choices:
 *
 *  - **[index] is not this arrangement's index.** It points into [ClassTableData.classDetail] — or,
 *    for [ClassSource]'s future `user` variant, into the user-defined detail list.
 *  - **[weekList] is a boolean per teaching week**, `true` meaning "there is a class that week". The
 *    original chose it over a string because the flag string the server sends (`"0110"`) survives
 *    caching and re-serialization badly.
 *  - **[day] is 1..7 with Monday as 1**, matching the server and `IsoDayOfWeek`.
 *
 * [classroom] is nullable because the registrar leaves it empty for a course with no room assigned,
 * and the card says 未知教室 for that rather than drawing a blank.
 */
@Serializable
data class TimeArrangement(
    val index: Int,
    @SerialName("week_list") val weekList: List<Boolean>,
    val day: Int,
    val start: Int,
    val stop: Int,
    val source: ClassSource = ClassSource.School,
    val teacher: String? = null,
    val classroom: String? = null,
) {
    /** How many periods this arrangement covers; the card's height is proportional to it. */
    val step: Int get() = stop - start

    override fun toString(): String = "$source $index $classroom $teacher"
}

/**
 * Everything the timetable page draws from.
 *
 * `semesterLength` is the number of teaching weeks the grid offers, and it is derived from the data
 * rather than answered by the server: the registrar reports a week flag string per class and the
 * longest one wins. A timetable whose every class ends in week 16 therefore has sixteen weeks.
 *
 * `termStartDay` is a formatted day, not a date — kept as the original's string because it is what
 * `ClassTable.json` holds and what the "current week" arithmetic parses. The original asserted that a
 * non-empty [timeArrangement] implies a non-empty [termStartDay], because every week computation
 * needs it; an assertion in a debug build is not a guard for a network response, so [TimetableParser]
 * is where that invariant is actually established.
 */
@Serializable
data class ClassTableData(
    val semesterLength: Int = 1,
    val semesterCode: String = "",
    val termStartDay: String = "",
    val classDetail: List<ClassDetail> = emptyList(),
    val notArranged: List<NotArrangementClassDetail> = emptyList(),
    val timeArrangement: List<TimeArrangement> = emptyList(),
    val classChanges: List<ClassChange> = emptyList(),
) {

    /**
     * The course [arrangement] belongs to.
     *
     * A `when` with no `else`, deliberately: the next source of details — locally added courses —
     * has to be handled here rather than falling through to the registrar's list, and a `when` that
     * does not compile is a better reminder than a comment.
     */
    fun detailFor(arrangement: TimeArrangement): ClassDetail = when (arrangement.source) {
        ClassSource.School -> classDetail[arrangement.index]
    }

    /** The course the arrangement at [index] in [timeArrangement] belongs to. */
    fun detailForArrangementAt(index: Int): ClassDetail = detailFor(timeArrangement[index])

    /** Whether this semester has a timetable to draw at all. */
    val hasClasses: Boolean get() = timeArrangement.isNotEmpty() && classDetail.isNotEmpty()
}

/** What kind of adjustment a [ClassChange] describes. */
@Serializable
enum class ChangeType {
    /** 调课 — the class moved. */
    @SerialName("change")
    Change,

    /** 停课 — the class was cancelled. */
    @SerialName("stop")
    Stop,

    /** 补课 — an extra session was added. */
    @SerialName("patch")
    Patch,
}

/**
 * One registrar-issued change to the schedule: a class moved, cancelled or added.
 *
 * The page does not project these onto the grid — the parser folds them into [ClassTableData] first,
 * exactly as the original did — but it *lists* them, which is why every original field is here. The
 * week lists stay boolean per week and the derived readings below are the original's own.
 *
 * Two fields are `null` in ordinary rows rather than absent: the registrar omits `XSKZC`/`XSKJS` on a
 * 停课 entry, and the original turned each into a null.
 */
@Serializable
data class ClassChange(
    val type: ChangeType,
    /** KCH */
    val classCode: String,
    /** KXH */
    val classNumber: String,
    /** KCM */
    val className: String,
    /** SKZC, boolean per week. */
    val originalAffectedWeeks: List<Boolean>? = null,
    /** XSKZC, boolean per week. */
    val newAffectedWeeks: List<Boolean>? = null,
    /** YSKJS */
    val originalTeacherData: String? = null,
    /** XSKJS */
    val newTeacherData: String? = null,
    /** KSJC–JSJC */
    val originalClassRange: List<Int> = emptyList(),
    /** XKSJC–XJSJC */
    val newClassRange: List<Int> = emptyList(),
    /** SKXQ */
    val originalWeek: Int? = null,
    /** XSKXQ */
    val newWeek: Int? = null,
    /** JASMC */
    val originalClassroom: String? = null,
    /** XJASMC */
    val newClassroom: String? = null,
) {

    /**
     * The weeks the original arrangement covered, as indices into [originalAffectedWeeks].
     *
     * Zero-based, as in the original: its own getter returned raw indices and left the "第 N 周"
     * formatting to the page, which is where the `+ 1` lives here too.
     */
    val originalAffectedWeekIndices: List<Int>
        get() = originalAffectedWeeks.orEmpty().indices.filter { originalAffectedWeeks!![it] }

    /** The weeks the new arrangement covers, as indices. Zero-based, as above. */
    val newAffectedWeekIndices: List<Int>
        get() = newAffectedWeeks.orEmpty().indices.filter { newAffectedWeeks!![it] }

    /** The original teacher's name, with the server's numbering and separators stripped. */
    val originalTeacher: String? get() = originalTeacherData?.stripTeacherCode()

    /** The new teacher's name, likewise stripped. */
    val newTeacher: String? get() = newTeacherData?.stripTeacherCode()

    /**
     * Whether the teacher actually changed.
     *
     * Compares the *coded* teacher data, not the display names: the server writes `"1234/张三"` and a
     * change from one rendering of the same teacher to another is not a change. The original's rule is
     * kept exactly — split on `,` or `/`, keep only the parts carrying a digit, and compare the lists.
     */
    val isTeacherChanged: Boolean
        get() = originalTeacherData.teacherCodes() != newTeacherData.teacherCodes()

    /** 调课 / 补课 / 停课, the words the list page shows. */
    val changeTypeText: String
        get() = when (type) {
            ChangeType.Change -> "调课"
            ChangeType.Patch -> "补课"
            ChangeType.Stop -> "停课"
        }
}

/**
 * Why a cached timetable is being shown instead of a fresh one.
 *
 * Ported from `ClassTableSession._cacheHintFromError`, key and sentence both, for the reason
 * [com.nevoit.xdnext.data.energy.EnergyCacheHint] keeps its keys: this app has no i18n layer yet, and
 * the original's translations can be reused verbatim later if the keys survive.
 */
enum class TimetableCacheHint(val key: String, val message: String) {
    PASSWORD_WRONG(
        "classtable.cache_hint_password_wrong",
        "统一认证密码错误或已失效。",
    ),
    LOGIN_FAILED(
        "classtable.cache_hint_login_failed",
        "登录课表服务失败。",
    ),
    NETWORK_FAILED(
        "classtable.cache_hint_network_failed",
        "课表网络请求失败。",
    ),
    UNKNOWN_ERROR(
        "classtable.cache_hint_unknown_error",
        "在线获取课表失败。详细错误请查看日志。",
    ),
}

/** Removes the server's teacher numbering and separators from a teacher string. */
private fun String.stripTeacherCode(): String = replace(Regex("""(/|[0-9a-zA-z])"""), "")

/**
 * The coded teacher identifiers inside a raw teacher string.
 *
 * `"1234/张三,5678/李四"` yields `["1234", "5678"]`; a name with no code at all yields nothing, which
 * is what makes two anonymous renderings compare equal.
 */
private fun String?.teacherCodes(): List<String> =
    this?.replace(" ", "")?.split(Regex(""",|/"""))?.filter { it.any(Char::isDigit) }.orEmpty()
