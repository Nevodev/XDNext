package com.nevoit.xdnext.data.experiment

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.nevoit.xdnext.core.log.appLog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * One row of the lab site's booking table, before its teacher is looked up.
 *
 * [teacher] is not here on purpose: it is not on this page. The booking table names a course and a
 * time, and the teacher of that slot lives on a *different* page that has to be posted back to once
 * per course — see [ExperimentScheduleApi.teachers].
 */
internal data class ExperimentRow(
    val subject: String,
    val timeText: String,
    val date: LocalDate,
    val classroom: String,
    val reference: String?,
)

/** One row of the course page's plan grid: a slot's time text and the teacher who runs it. */
internal data class TeacherSlot(val timeText: String, val teacher: String)

/**
 * The lab site's HTML, read with ksoup.
 *
 * Every selector here is a reading of the original's own DOM navigation
 * (`physics_experiment_session.dart`), not a measurement — there is no recorded page capture for this
 * site the way `reference/probe/energy_encoding_probe.dart` captures the energy system's wire bytes.
 * What the port changed is only the *robustness*: the original indexed into what it found
 * (`expTds[1]`, `span.first`, `DateTime(...)`) and threw on anything else, so one row the site spells
 * differently took the whole list with it. Each row is read defensively here and a row that cannot be
 * read is logged and dropped, which is the rule the timetable's parser already follows.
 */
internal object ExperimentParser {

    /** The suffix the site puts on a booking's name to say how long the sitting is. */
    private val DurationSuffixes = listOf("（3学时）", "(3学时)")

    private const val BOOKING_TABLE_ID = "Orders_ctl00"
    private const val SUBJECT_LIST_ID = "plan1_ExpeList"
    private const val PLAN_TIME_ID_PREFIX = "plan1_PlanGrid_TimeN_"
    private const val PLAN_TEACHER_ID_PREFIX = "plan1_PlanGrid_Teacher_"
    private const val LOGIN_ERROR_ID = "login1_Label1"

    /** The booking table's rows, in the order the page lists them. */
    fun rows(html: String): List<ExperimentRow> {
        val table = Ksoup.parse(html).getElementById(BOOKING_TABLE_ID)
        if (table == null) {
            appLog.w { "[ExperimentParser] No $BOOKING_TABLE_ID table in ${html.length} chars" }
            return emptyList()
        }

        return table.getElementsByTag("tr").mapNotNull { row ->
            rowOf(row.getElementsByTag("td").toList())
        }
    }

    private fun rowOf(cells: List<Element>): ExperimentRow? {
        // The columns are positional and the original's own indices: 1 the course, 3 the time, 4 the
        // day, 5 the room, 9 the reference. A row with fewer cells than that is a header, a spacer or a
        // layout this port has not seen, and it is not an error.
        if (cells.size <= 9) return null

        val subject = cells[1].getElementsByClass("linkSmallBold").firstOrNull()?.text()
            ?.let(::stripDuration)
            ?.takeIf { it.isNotBlank() }
        val timeText = spanText(cells[3])
        val dateText = spanText(cells[4])
        val classroom = spanText(cells[5])
        val date = dateText?.let(::parseSiteDate)

        if (subject == null || timeText == null || date == null) {
            appLog.w {
                "[ExperimentParser] Skipping a booking row: " +
                        "name=${subject != null}, time=$timeText, date=$dateText"
            }
            return null
        }
        return ExperimentRow(
            subject = subject,
            timeText = timeText,
            date = date,
            // The room is what the card prints, and an empty one is a booking without a room rather
            // than a row to drop.
            classroom = classroom.orEmpty(),
            reference = spanText(cells[9])?.takeIf { it.isNotBlank() },
        )
    }

    /** The text of a cell's first span, which is where every one of the five fields lives. */
    private fun spanText(cell: Element): String? =
        cell.getElementsByTag("span").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The sitting a booking's time column names, on the day the booking is for.
     *
     * The site's time column is not parsed as a time. The original decided between the lab's two
     * sessions by whether the text mentions `15` at all, and that test is kept: the two bell times are
     * the lab's own, they are the same for every booking, and the column's text is not a time this
     * port has ever seen an example of.
     */
    fun timeRangeOf(date: LocalDate, timeText: String): ExperimentTimeRange {
        val (start, stop) = if (timeText.contains(ExperimentEndpoints.AFTERNOON_MARKER)) {
            (ExperimentEndpoints.AFTERNOON_START_HOUR to ExperimentEndpoints.AFTERNOON_START_MINUTE) to
                    (ExperimentEndpoints.AFTERNOON_STOP_HOUR to ExperimentEndpoints.AFTERNOON_STOP_MINUTE)
        } else {
            (ExperimentEndpoints.EVENING_START_HOUR to ExperimentEndpoints.EVENING_START_MINUTE) to
                    (ExperimentEndpoints.EVENING_STOP_HOUR to ExperimentEndpoints.EVENING_STOP_MINUTE)
        }
        return ExperimentTimeRange(
            start = LocalDateTime(date, LocalTime(start.first, start.second)),
            stop = LocalDateTime(date, LocalTime(stop.first, stop.second)),
        )
    }

    /**
     * The courses the plan grid can be shown for, as its option list: display name to form value.
     *
     * The page renders one course at a time and switches between them by posting this value back, so
     * the map is what makes a teacher lookup possible at all. The original read the select's children
     * (`selectInfo[i.innerHtml] = i.attributes["value"]`); the option elements are selected here,
     * which is the same set without depending on the select holding nothing else.
     */
    fun subjectOptions(html: String): Map<String, String> {
        val options = Ksoup.parse(html).getElementById(SUBJECT_LIST_ID)?.getElementsByTag("option")
            ?: return emptyMap()
        return options
            .mapNotNull { option ->
                val label = option.text().trim()
                val value = option.attr("value")
                if (label.isEmpty() || value.isEmpty()) null else label to value
            }
            .toMap()
    }

    /**
     * The plan grid's slots: each row's time text, and the teacher who runs it.
     *
     * The two halves are paired **by the numeric suffix in the element's id** rather than by their
     * position in two separately selected lists. The original paired them by index, which is only
     * correct while the page emits both spans for every row — and a page that skipped a teacher span
     * would attribute every teacher after it to the wrong slot.
     */
    fun teacherSlots(html: String): List<TeacherSlot> {
        val document = Ksoup.parse(html)
        return document.select("span[id^=$PLAN_TIME_ID_PREFIX]").mapNotNull { span ->
            val suffix = span.attr("id").removePrefix(PLAN_TIME_ID_PREFIX)
            if (suffix.isEmpty() || suffix.any { !it.isDigit() }) return@mapNotNull null

            val teacher = document.getElementById(PLAN_TEACHER_ID_PREFIX + suffix)?.text()?.trim()
            val timeText = span.text().trim()
            if (teacher.isNullOrEmpty() || timeText.isEmpty()) null else TeacherSlot(timeText, teacher)
        }
    }

    /** The teacher of the slot whose time text contains [timeText], or null when no row matches. */
    fun teacherFor(html: String, timeText: String): String? {
        if (timeText.isBlank()) return null
        return teacherSlots(html).firstOrNull { it.timeText.contains(timeText) }?.teacher
    }

    /**
     * The login page's own complaint, when it refused a login.
     *
     * The element's markup is stripped the way the original stripped it — its three `replaceAll` calls
     * are the behaviour, and the `<br>` becomes the full-width full stop the sentence continues with.
     *
     * Two things about the stripping are this port's rather than the original's, both because the parse
     * is ksoup's and the original's was `package:html`'s: the tags are matched by *pattern* (a `<font>`
     * with any attributes, a `<br>` with or without its slash) instead of by one exact spelling, and the
     * whitespace the serializer puts around a tag is removed. What the server sends is a sentence, and
     * the newline (or space) its markup left behind is a rendering artefact, not content.
     */
    fun loginError(html: String): String? =
        Ksoup.parse(html)
            .getElementById(LOGIN_ERROR_ID)
            ?.html()
            ?.replace(FontTag, "")
            ?.replace("</font>", "")
            ?.replace(BreakTag, "。")
            ?.replace(Whitespace, "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private val FontTag = Regex("<font[^>]*>")
    private val BreakTag = Regex("<br\\s*/?>")
    private val Whitespace = Regex("\\s+")

    /**
     * `9/15/2026` — the site's own date format, and it is **month, day, year**.
     *
     * That order is measured rather than guessed: the site's time column also names the weekday, and
     * `9/15/2026 星期二`, `9/24/2026 星期四` and `10/14/2026 星期三` are the three a device logged — each
     * one is the weekday that reading it as month/day/year produces, and none of them is the one the
     * other order produces. The original's own expression says the same thing: it built
     * `DateTime(dateNums[2], dateNums[0], dateNums[1])`, whose first argument is the *year*.
     *
     * A page that ever spells it the other way round is recognised by the year's own length rather than
     * guessed at, because a date this module cannot read is a booking that vanishes from the grid — which
     * is exactly what happened while this function had the two orders the wrong way round.
     */
    private fun parseSiteDate(text: String): LocalDate? {
        val parts = text.trim().split('/')
        if (parts.size != 3) return null
        val numbers = parts.map { it.trim().toIntOrNull() ?: return null }

        val yearFirst = numbers[0] >= YEAR_THRESHOLD
        val year = if (yearFirst) numbers[0] else numbers[2]
        val month = if (yearFirst) numbers[1] else numbers[0]
        val day = if (yearFirst) numbers[2] else numbers[1]

        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    /** A four-digit field is the year whichever position it is in. */
    private const val YEAR_THRESHOLD = 1000

    /** `实验名称（3学时）` as `实验名称`. */
    private fun stripDuration(name: String): String =
        DurationSuffixes.fold(name.trim()) { acc, suffix -> acc.replace(suffix, "") }.trim()
}
