package com.nevoit.xdnext.data.timetable

import com.nevoit.xdnext.core.concurrent.SingleFlight
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.core.store.SettingsKeys
import com.nevoit.xdnext.core.store.SettingsStore
import com.nevoit.xdnext.data.fetch.FetchResult
import com.nevoit.xdnext.data.ids.IdsAuthException
import com.nevoit.xdnext.data.ids.NoCaptchaSolver
import com.nevoit.xdnext.data.ids.PasswordWrongException
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.session.IdsSessionRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/**
 * The timetable as [TimetableRepository] sees it.
 *
 * A seam, for the same reason `EnergyDataSource` and `SchoolCardDataSource` are ones: the failure
 * policy — a refresh that fails keeps the cached timetable rather than emptying the screen — is worth
 * testing without a campus network, and the postgraduate branch of the registrar will be a second
 * implementation of this interface.
 */
interface TimetableDataSource {

    /** The last successful timetable, or null when nothing has ever been cached. */
    fun getCachedClassTable(): FetchResult<ClassTableData>?

    /**
     * Fetches the timetable, falling back to [getCachedClassTable] when the login, the network or the
     * registrar fails. Throws only when there is no cached timetable to fall back to.
     */
    suspend fun getClassTable(): FetchResult<ClassTableData>
}

/**
 * The registrar's timetable session: log in, settle the semester, read four endpoints, cache.
 *
 * Ported from `ClassTableSession`. What it owns is the part that is a *sequence* rather than a
 * parsing rule:
 *
 * 1. log in for the timetable app's CAS service and walk the redirect chain — the chain is what sets
 *    eHall's own session cookie, so the endpoints are refused without it;
 * 2. read `dqxnxq`, the semester the registrar considers current, and reconcile it with the one this
 *    app last used. The newer of the two wins, which is the original's `SemesterController` rule: the
 *    remote code is normally the newer one, and the local one wins only when the user has deliberately
 *    looked ahead at a future semester;
 * 3. read the term start day, then the timetable, the unarranged courses and the schedule adjustments;
 * 4. cache what came back.
 *
 * Two orderings are worth naming. The semester is settled **before** the timetable is asked for, which
 * the original did across two controllers (`SemesterController` fetched the remote code and the
 * timetable then read the reconciled value); doing it inside one refresh is the same answer with one
 * fewer login. And the adjustments are read **last** and their failure is tolerated: the original
 * logged that endpoint and carried on, and a timetable without its 调课 annotations is worth far more
 * than no timetable.
 *
 * The original also guarded against a refresh being overtaken by a newer one — it re-checked the
 * requested semester after the fetch and deleted its own cache if a newer request had won. Nothing
 * here can race that way yet, because the semester cannot be changed from the UI; when the semester
 * switcher arrives, that check belongs here.
 *
 * @param captcha the solver used for the IDS login step, as everywhere else in this app.
 * @param clock injectable so "when was this fetched" is not the moment the test ran.
 *
 * Internal because it takes a [TimetableApi], which speaks in wire rows. The rest of the app binds
 * [TimetableDataSource].
 */
internal class TimetableSession(
    private val ids: IdsSessionRepository,
    private val api: TimetableApi,
    private val cache: TimetableCache,
    private val settings: SettingsStore,
    private val captcha: SliderCaptchaSolver = NoCaptchaSolver,
    private val clock: Clock = Clock.System,
) : TimetableDataSource {

    /**
     * Overlapping refreshes share one request.
     *
     * The original wrapped its own fetch in a `SingleFlight` for the same reason: the page can be
     * opened while the start-up refresh is still running, and two logins for one timetable is two
     * opportunities to steal each other's single-use service ticket.
     */
    private val flight = SingleFlight<FetchResult<ClassTableData>>()

    override fun getCachedClassTable(): FetchResult<ClassTableData>? = cache.read()

    override suspend fun getClassTable(): FetchResult<ClassTableData> = flight.run { refresh() }

    /**
     * Reads the timetable, with the original's fallback: a failure never empties the screen, it
     * annotates the cached timetable with why the refresh failed.
     *
     * Cancellation is the one exception, as in the energy and campus-card modules: turning a cancelled
     * refresh into "here is the cached value" would show stale data as if the request had completed.
     */
    private suspend fun refresh(): FetchResult<ClassTableData> {
        val fetchTime = clock.now()
        val cached = cache.read()
        return try {
            val data = requestTimetable()
            cache.save(data)
            FetchResult.fresh(fetchTime = fetchTime, data = data)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[TimetableSession] Could not refresh the timetable" }
            if (cached == null) throw error
            FetchResult.cache(
                fetchTime = cached.fetchTime,
                data = cached.data,
                hintKey = cacheHintFor(error).key,
            )
        }
    }

    /** The four endpoints, in the order the registrar wants them. */
    private suspend fun requestTimetable(): ClassTableData {
        val target = TimetableEndpoints.SERVICE
        val location = enterRegistrar(target)
        try {
            ids.followRedirects(initialLocation = location, service = target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: IdsAuthException) {
            throw auth
        } catch (error: Throwable) {
            throw TimetableNetworkException("课表登录跳转失败", error)
        }

        // By this point the credentials are known to be good — the login above would have failed
        // otherwise — so an absent account is not a branch worth carrying.
        val account = ids.currentAccount().orEmpty()

        val semesterCode = reconcileSemester(api.currentSemesterCode())
        val termStartDay = api.termStartDay(semesterCode)

        val rows = when (val answer = api.classTableRows(semesterCode, account)) {
            // A semester the registrar has not opened. The term start day is still known, which is
            // what lets the empty page name the semester and the grid still know its weeks.
            ClassTableAnswer.NotPublished -> return ClassTableData(
                semesterCode = semesterCode,
                termStartDay = termStartDay,
            )

            is ClassTableAnswer.Rows -> answer.value
        }

        val notArranged = api.notArrangedRows(semesterCode, account)
        val changes = try {
            api.classChangeRows(semesterCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[TimetableSession] Could not read the schedule adjustments" }
            emptyList()
        }

        val data = TimetableParser.parseEhall(
            semesterCode = semesterCode,
            termStartDay = termStartDay,
            classTableRows = rows,
            notArrangedRows = notArranged,
            classChangeRows = changes,
        )
        appLog.i {
            "[TimetableSession] $semesterCode starts $termStartDay: " +
                    "${data.timeArrangement.size} arrangements, " +
                    "${data.classDetail.size} courses, " +
                    "${data.classChanges.size} adjustments, " +
                    "${data.semesterLength} weeks"
        }
        return data
    }

    /** Asks IDS for a ticket for the timetable app, naming the failures worth naming. */
    private suspend fun enterRegistrar(target: String): String = try {
        ids.loginForService(service = target, captcha = captcha).getOrThrow()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (auth: IdsAuthException) {
        // The IDS layer classified this itself: a wrong password, a refused captcha, an unanswered
        // second factor. Those are the errors worth naming in the UI.
        throw auth
    } catch (error: Throwable) {
        throw TimetableNetworkException("课表登录请求失败", error)
    }

    /**
     * The semester to read: the newer of the registrar's current one and the one last used here.
     *
     * The remote code normally wins, and it *should* win — the term rolls over without anything on the
     * device changing. The local one wins only when it is further ahead, which is how a user who has
     * deliberately looked at a future semester keeps looking at it. Both facts are kept, and the
     * reconciled answer is written back so the next launch starts from it.
     */
    private fun reconcileSemester(remote: String): String {
        val local = settings.getString(SettingsKeys.ACADEMIC_SEMESTER_CODE).orEmpty()
        val effective = SemesterCode.latest(remote, local)
        if (effective != local) settings.putString(SettingsKeys.ACADEMIC_SEMESTER_CODE, effective)
        if (effective != remote) {
            appLog.i {
                "[TimetableSession] Keeping the locally chosen semester $effective over $remote"
            }
        }
        return effective
    }
}

/**
 * Classifies a refresh failure into the hint shown beside a cached timetable.
 *
 * The same mapping the original made, but by what went wrong rather than by exception name: its HTTP
 * layer was replaced by Ktor here, so `DioException` no longer exists to be tested for.
 */
internal fun cacheHintFor(error: Throwable): TimetableCacheHint = when (error) {
    is PasswordWrongException -> TimetableCacheHint.PASSWORD_WRONG
    is TimetableNetworkException -> TimetableCacheHint.NETWORK_FAILED
    // Ordered after the specific auth failures: all of them are IdsAuthException subtypes.
    is IdsAuthException -> TimetableCacheHint.LOGIN_FAILED
    else -> TimetableCacheHint.UNKNOWN_ERROR
}
