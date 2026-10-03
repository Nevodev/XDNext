package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.concurrent.SingleFlight
import com.nevoit.xdnext.core.image.decodeCaptchaImage
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * The physics experiment list as [ExperimentRepository] sees it.
 *
 * A seam for the same reason `TimetableDataSource` and `EnergyDataSource` are ones: the failure policy
 * — a refresh that fails keeps the cached list rather than emptying the page — is worth testing without
 * the lab site, and the *other* experiment system (the one the original merged into the same page) will
 * be a second implementation of this interface.
 */
interface ExperimentDataSource {

    /** The last successfully fetched list, or null when nothing has ever been cached. */
    fun getCachedExperiments(): FetchResult<List<ExperimentEntry>>?

    /**
     * Fetches the list, falling back to [getCachedExperiments] when the login, the network or the lab
     * site fails. Throws only when there is no cached list to fall back to.
     */
    suspend fun getExperiments(): FetchResult<List<ExperimentEntry>>

    /** One score image's bytes, for the page that shows what a mark's picture looks like. */
    suspend fun getScoreImage(url: String): ByteArray

    /** Forgets the cached list, for a change of credentials. */
    fun invalidateCache()
}

/**
 * The lab site's session: log in, read the bookings, fill in their teachers, recognise their marks,
 * cache.
 *
 * Ported from `ExperimentSession` in `physics_experiment_session.dart`, and what it owns is the part
 * that is a *sequence* rather than a parsing rule:
 *
 * 1. the credentials — the account the user set (or the IDS one) and the experiment password. Without a
 *    password there is nothing to ask for, and the original threw the same way;
 * 2. the login, whose answer is the cookie every later page is read with;
 * 3. the booking list, plus a teacher for each booking, which is a page of its own per course;
 * 4. the marks, from the *other* system on the same host — which is allowed to fail, because a schedule
 *    without marks is worth far more than no schedule. The original wrapped the same call in a
 *    try/catch and logged it;
 * 5. the cache, which is what makes the module work off campus.
 *
 * Two of the original's behaviours are deliberately not carried over:
 *
 *  - **a booking whose teacher cannot be found is kept**, with no teacher on it — the page prints 未提供
 *    for one from a string resource, because the data layer has no business carrying a sentence.
 *    The original threw, and because the lookups ran inside one `Future.wait`, one missing name lost
 *    every booking in the list;
 *  - **a booking's teacher lookup is refused when the course is not in the plan grid's list**, rather
 *    than posting a null value back. The original asserted non-null there and crashed.
 *
 * @param clock injectable so "when was this fetched" is not the moment the test ran.
 */
internal class ExperimentSession(
    private val credentials: ExperimentCredentials,
    private val schedule: ExperimentScheduleApi,
    private val report: ExperimentReportApi,
    private val cache: ExperimentCache,
    private val clock: Clock = Clock.System,
) : ExperimentDataSource {

    /**
     * Overlapping refreshes share one request.
     *
     * The list is fetched at start-up and again by the page's own refresh, and the second of those can
     * easily arrive while the first is still logging in. Two logins for one list is two sessions on a
     * site that keeps its state per session.
     */
    private val flight = SingleFlight<FetchResult<List<ExperimentEntry>>>()

    override fun getCachedExperiments(): FetchResult<List<ExperimentEntry>>? = cache.read()

    override suspend fun getExperiments(): FetchResult<List<ExperimentEntry>> = flight.run { refresh() }

    override suspend fun getScoreImage(url: String): ByteArray = report.imageBytes(url)

    override fun invalidateCache() = cache.delete()

    /**
     * Reads the list, with the original's fallback: a failure never empties the page, it annotates the
     * cached list with why the refresh failed.
     *
     * Cancellation is the one exception, as in the timetable and energy modules: turning a cancelled
     * refresh into "here is the cached value" would show stale data as if the request had completed.
     */
    private suspend fun refresh(): FetchResult<List<ExperimentEntry>> {
        val fetchTime = clock.now()
        val cached = cache.read()
        return try {
            val entries = requestExperiments()
            cache.save(entries)
            FetchResult.fresh(fetchTime = fetchTime, data = entries)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[ExperimentSession] Could not refresh the experiments" }
            if (cached == null) throw error
            FetchResult.cache(
                fetchTime = cached.fetchTime,
                data = cached.data,
                hintKey = experimentCacheHintFor(error).key,
            )
        }
    }

    /** The whole sequence, ending with the marks folded into the bookings. */
    private suspend fun requestExperiments(): List<ExperimentEntry> {
        val password = credentials.password() ?: throw ExperimentNoPasswordException()
        val account = credentials.account() ?: throw ExperimentNoPasswordException()

        val cookie = schedule.login(account, password)
        val bookings = readBookings(cookie)
        appLog.i { "[ExperimentSession] ${bookings.size} bookings for $account" }
        if (bookings.isEmpty()) return emptyList()

        return withScores(bookings, account, password)
    }

    /**
     * The booking rows, each with the teacher of its slot.
     *
     * The plan grid is read **once per refresh** and switched from course to course, where the original
     * re-read it once per *booking* and could therefore run three lookups at a time. A refresh has one
     * session and one grid, so the lookups are sequential here: the grid's state is which course it is
     * showing, and two lookups at once would be two answers to that.
     */
    private suspend fun readBookings(cookie: String): List<ExperimentEntry> {
        val rows = ExperimentParser.rows(schedule.bookingPage(cookie))
        if (rows.isEmpty()) return emptyList()

        val lookup = TeacherLookup(schedule, cookie)
        return rows.map { row ->
            ExperimentEntry(
                name = row.subject,
                classroom = row.classroom,
                timeRanges = listOf(ExperimentParser.timeRangeOf(row.date, row.timeText)),
                teacher = lookup.teacherFor(row.subject, row.timeText),
                reference = row.reference,
            )
        }
    }

    /**
     * The bookings with each one's mark, or the bookings alone when the report system could not be
     * read.
     *
     * The failure is swallowed **by design** — the original did the same, and for the same reason: the
     * marks come from a second system with a session of its own, and losing the whole list over it would
     * be losing the things the page is mainly for.
     */
    private suspend fun withScores(
        bookings: List<ExperimentEntry>,
        account: String,
        password: String,
    ): List<ExperimentEntry> {
        val scores = try {
            readScores(account, password)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[ExperimentSession] Could not read the experiment marks" }
            return bookings
        }
        return bookings.map { booking -> booking.copy(score = scores[booking.name]) }
    }

    /**
     * Each experiment's mark, by downloading its picture and recognising it.
     *
     * The images are processed one at a time, as the original processed them: they are small, and the
     * report system has just been walked through a five-event handshake that a burst of parallel reads
     * would not improve.
     */
    private suspend fun readScores(account: String, password: String): Map<String, ExperimentScore> {
        val urls = report.scoreImageUrls(account, password)
        if (urls.isEmpty()) {
            appLog.i { "[ExperimentSession] The report system has no score images" }
            return emptyMap()
        }

        val scores = mutableMapOf<String, ExperimentScore>()
        urls.forEach { (name, url) ->
            val bytes = try {
                report.imageBytes(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                appLog.w(error) { "[ExperimentSession] Could not download a score image for $name" }
                return@forEach
            }
            val image = decodeCaptchaImage(bytes)
            if (image == null) {
                appLog.w { "[ExperimentSession] Could not decode a score image for $name" }
                return@forEach
            }
            scores[name] = ExperimentScoreRecognition.recognise(image, url)
        }
        return scores
    }
}

/**
 * Fills in one course's teacher, reading the plan grid as few times as it can.
 *
 * The grid shows **one course at a time** and is switched by posting the page back with that course's
 * form value, so the teacher of a booking is only on the page while the page is showing that booking's
 * course. This holds the page and which course it is showing; a second booking of the same course costs
 * nothing, and a booking of a new course costs one post.
 *
 * The original kept the option list in a `static` field shared by every session in the process, and
 * re-read the grid before each lookup. Keeping both per refresh is the same answers with one page read
 * instead of one per booking.
 */
private class TeacherLookup(
    private val api: ExperimentScheduleApi,
    private val cookie: String,
) {

    private var options: Map<String, String>? = null
    private var page: String? = null
    private var shown: String? = null

    suspend fun teacherFor(subject: String, timeText: String): String? {
        val subjects = options ?: load()
        val value = subjects[subject]
        if (value == null) {
            appLog.i { "[ExperimentSession] The plan grid does not list $subject" }
            return null
        }

        // The page opens showing the first course in the list, so a booking of that course needs no
        // post — which is the original's own test (`selectInfo[subject] != selectInfo.values.first`).
        if (value != shown) {
            page = api.selectSubject(cookie, page.orEmpty(), value)
            shown = value
        }
        return ExperimentParser.teacherFor(page.orEmpty(), timeText)
    }

    private suspend fun load(): Map<String, String> {
        val html = api.coursePage(cookie)
        val subjects = ExperimentParser.subjectOptions(html)
        page = html
        shown = subjects.values.firstOrNull()
        options = subjects
        appLog.i { "[ExperimentSession] The plan grid offers ${subjects.size} courses" }
        return subjects
    }
}

/**
 * Classifies a refresh failure into the hint shown beside a cached list.
 *
 * The same mapping the original made, but by what went wrong rather than by exception name: its HTTP
 * layer was replaced by Ktor here, so `DioException` no longer exists to be tested for.
 */
internal fun experimentCacheHintFor(error: Throwable): ExperimentCacheHint = when (error) {
    is ExperimentNoPasswordException -> ExperimentCacheHint.MISSING_PASSWORD
    is ExperimentLoginFailedException -> ExperimentCacheHint.LOGIN_FAILED
    is ExperimentNetworkException -> ExperimentCacheHint.NETWORK_FAILED
    else -> ExperimentCacheHint.UNKNOWN_ERROR
}
