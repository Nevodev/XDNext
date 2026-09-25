package com.nevoit.xdnext.ui.timetable

/**
 * The timetable page's sentences.
 *
 * The original kept these in `assets/flutter_i18n/zh_CN.yaml` and reached them through
 * `FlutterI18n.translate(context, "classtable.…")`. This app has no i18n layer yet, so they live
 * here — and each one carries the key it came from, because the keys are what a translation layer
 * needs in order to reuse the original's own translations verbatim when it arrives. That is the same
 * arrangement `EnergyCacheHint` and `TimetableCacheHint` make.
 *
 * Only the strings this slice actually draws are here. The menu entries for the features that are not
 * built — 添加课程信息, 生成日历文件, 导出到系统日历, 切换课程表学期, 时间指示设置, 课表样式设置 —
 * are deliberately absent rather than present-but-inert: a menu entry that does nothing is worse than
 * no menu entry, and the same goes for the strings behind them.
 */
object TimetableStrings {

    const val PAGE_TITLE = "日程表"

    // --- The overflow menu (`classtable.popup_menu.*`) ------------------------------------------

    const val MENU_NOT_ARRANGED = "查看未安排课程信息"
    const val MENU_CLASS_CHANGED = "查看课程安排调整信息"
    const val MENU_REFRESH = "刷新日程表"

    // --- The status banner and the error dialog -------------------------------------------------

    /** `classtable.status_source.class_table`. */
    const val SOURCE_CLASS_TABLE = "课表"

    /** `classtable.status_banner.loading`, with `{sources}`. */
    fun bannerLoading(sources: String): String = "正在更新：$sources"

    /** `classtable.status_banner.cache`, with `{sources}`. */
    fun bannerCache(sources: String): String = "当前使用缓存：$sources"

    /** `classtable.status_banner.error_summary`, with `{sources}`. */
    fun errorSummary(sources: String): String = "以下信息加载失败：$sources"

    /** `load_error` — the error button's tooltip and the empty page's dialog title. */
    const val LOAD_ERROR = "加载错误"

    /** `classtable.error_dialog_title`. */
    const val ERROR_DIALOG_TITLE = "错误信息概览"

    /** `network_error`, used for a source that failed without a cache hint of its own. */
    const val NETWORK_ERROR = "网络错误，可能是没联网，可能是学校服务器出现了故障:-P"

    const val CONFIRM = "确定"
    const val CANCEL = "取消"

    /** `no_info`. */
    const val NO_INFO = "没有信息"

    /** `setting.class_refresh_title`. */
    const val REFRESH_DIALOG_TITLE = "确认对话框"

    /** `setting.class_refresh_content`. */
    const val REFRESH_DIALOG_CONTENT =
        "是否要强制刷新课表？同意后，将会从学校一站式后端重新获取课表，耗时会比较久。"

    // --- The empty page (`classtable.empty_state.*`) --------------------------------------------

    /** `classtable.empty_state.no_course`, with `{semester_code}`. */
    fun emptyNoCourse(semesterCode: String): String = "$semesterCode 学期没有课程安排。"

    // --- The week strip and the grid ------------------------------------------------------------

    /** `classtable.week_title`, with `{week}` — a **one-based** week number. */
    fun weekTitle(week: Int): String = "第${week}周"

    /** `classtable.month`, with `{month}` — two lines, the number over 月. */
    fun month(month: Int): String = "$month"

    /** `classtable.no_class`, shown in the grid of a week with nothing in it. */
    const val NO_CLASS = "本周暂无安排"

    // --- The class card (`classtable.class_card.*`) ---------------------------------------------

    /** `classtable.class_card.unknown_classroom`, drawn after the `@`. */
    const val UNKNOWN_CLASSROOM = "未知教室"

    /** `classtable.class_card.remains_hint`, with `{remain_count}`. */
    fun remainsHint(count: Int): String = "还有${count}个日程"

    // --- 未安排课程 (`classtable.not_arranged_page.*`) -------------------------------------------

    const val NOT_ARRANGED_TITLE = "没有时间安排的科目"
    const val NOT_ARRANGED_EMPTY = "目前全部课程均有时间安排"

    /** `classtable.not_arranged_page.content`, with `{classCode}`, `{classNumber}`, `{teacher}`. */
    fun notArrangedContent(code: String, number: String, teacher: String): String =
        "编号: $code | $number 班\n\n老师: $teacher"

    // --- 调课记录 (`classtable.class_change_page.*`) ---------------------------------------------

    const val CLASS_CHANGE_TITLE = "课程调整"
    const val CLASS_CHANGE_EMPTY = "目前没有调课信息"

    /** `classtable.class_change_page.teacher_change`, with `{previous_teacher}`, `{new_teacher}`. */
    fun teacherChange(previous: String, new: String): String = "教师变更：从${previous}变为$new"

    /** `classtable.class_change_page.no_teacher_change`. */
    const val NO_TEACHER_CHANGE = "教师信息没有改变"

    /**
     * `classtable.class_change_page.change_class_message`.
     *
     * The original's value is written with spaces where a Chinese sentence would not have them — it came
     * from a YAML entry folded across lines — and the page then removed every one of them
     * (`classChange.replaceAll(" ", '')`). They are kept here so the value still lines up with the
     * original's translation, and removed at the call site, which is where the original removed them.
     */
    fun changeClassMessage(
        originalWeeks: String,
        originalWeekChar: String,
        originalStart: String,
        originalEnd: String,
        newWeeks: String,
        newWeekChar: String,
        newStart: String,
        newStop: String,
        newClassroom: String,
    ): String = "调课信息，从第${originalWeeks}周 星期$originalWeekChar 的" +
            "$originalStart-$originalEnd 节调整为第${newWeeks}周星期$newWeekChar 的" +
            "$newStart-$newStop 节，$newClassroom 教室上课"

    /** `classtable.class_change_page.patch_class_message`. */
    fun patchClassMessage(
        newWeeks: String,
        newWeekChar: String,
        newStart: String,
        newStop: String,
        newClassroom: String,
    ): String = "补课信息，第${newWeeks}周 星期$newWeekChar 的$newStart-$newStop 节，" +
            "$newClassroom 补课"

    /** `classtable.class_change_page.stop_class_message`. */
    fun stopClassMessage(
        originalWeeks: String,
        originalWeekChar: String,
        originalStart: String,
        originalEnd: String,
    ): String = "停课信息，第${originalWeeks}周 星期$originalWeekChar 的" +
            "$originalStart-$originalEnd 节停课"

    /** `classtable.class_change_page.class_info`, with `{classCode}`, `{classNumber}`, … */
    fun classChangeInfo(
        classCode: String,
        classNumber: String,
        classChange: String,
        teacherChange: String,
    ): String = "编号: $classCode | $classNumber 班\n\n安排变更：$classChange$teacherChange"

    /** `classtable.class_change_page.1` … `.7` — the weekday as one character, or `""`. */
    fun weekChar(week: Int?): String = when (week) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> ""
    }

    /** `weekday.monday` … `weekday.sunday`, indexed by `IsoDayOfWeek` (1..7). */
    fun weekday(isoDayOfWeek: Int): String = when (isoDayOfWeek) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }
}
