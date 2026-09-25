package com.nevoit.xdnext.data.energy

import com.nevoit.xdnext.core.concurrent.SingleFlight
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.data.fetch.FetchResult
import com.nevoit.xdnext.data.ids.CaptchaSolveFailedException
import com.nevoit.xdnext.data.ids.IdsAuthException
import com.nevoit.xdnext.data.ids.MissingCredentialsException
import com.nevoit.xdnext.data.ids.NoCaptchaSolver
import com.nevoit.xdnext.data.ids.PasswordWrongException
import com.nevoit.xdnext.data.ids.SliderCaptchaSolver
import com.nevoit.xdnext.data.session.IdsSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The energy reading as [EnergyRepository] sees it.
 *
 * The original's controller reached into a concrete `EnergySession`. This interface exists so the
 * controller's own logic — history trimming, the "same reading day" rule, the cache hint, the warning
 * threshold — can be tested without a campus network or a support directory, which is the same reason
 * the login slice injects its captcha solver and second-factor handler.
 */
interface EnergyDataSource {
    /** The last successful reading, or null when nothing has ever been cached. */
    fun getCache(): FetchResult<EnergyInfo>?

    /**
     * Fetches a reading, falling back to [getCache] when the network, the login or the server fails.
     * Throws only when there is no cached value to fall back to.
     */
    suspend fun getElectricityInfo(): FetchResult<EnergyInfo>

    fun getElectricityHistory(): List<ElectricityHistoryInfo>

    fun saveElectricityHistory(history: List<ElectricityHistoryInfo>)

    fun clearElectricityHistory()

    fun deleteCache()
}

/**
 * The energy management system's session: login, the five calls that make up a reading, and the cache.
 *
 * Ported from `lib/repository/ids_session/energy_session.dart`. The flow is a strict sequence, and
 * every step depends on a cookie or an identifier the previous one produced:
 *
 * 1. log in against the energy system's OAuth service through IDS, then walk the redirect chain and
 *    read the `code` the energy system appended;
 * 2. exchange that code (`OauthGetUserInfo`) — the response is empty, the *cookie* is the point;
 * 3. sign in (`H5UserIDLogIn`) as the IDS account, which yields the node that owns the meters;
 * 4. list the node's meters (`H5QueryMeterList`) and pick the electricity one;
 * 5. read that meter's last month of readings (`GetMetRead`), and the water meter's last year.
 *
 * The system is **only reachable from the campus network**, which is why a cached reading is the
 * expected outcome of a refresh away from campus rather than an exceptional one.
 *
 * @param captcha the solver used for the IDS login step; the original passed one in per call, this
 *   port injects the same chained auto/manual solver the rest of the app uses.
 * @param clock and [timeZone] are injectable so the query windows of a test are not the day it runs.
 */
class EnergySession(
    private val ids: IdsSessionRepository,
    private val api: EnergyApi,
    private val cache: EnergyCache,
    private val captcha: SliderCaptchaSolver = NoCaptchaSolver,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : EnergyDataSource {

    /**
     * Coalesces overlapping refreshes: the original wrapped this call in a `SingleFlight`, so a
     * screen that refreshes on every recomposition still produces one login and one query.
     */
    private val flight = SingleFlight<FetchResult<EnergyInfo>>()

    override fun getCache(): FetchResult<EnergyInfo>? = cache.getEnergyInfo()

    override fun getElectricityHistory(): List<ElectricityHistoryInfo> =
        cache.getElectricityHistory()

    override fun saveElectricityHistory(history: List<ElectricityHistoryInfo>) =
        cache.saveElectricityHistory(history)

    override fun clearElectricityHistory() = cache.clearElectricityHistory()

    override fun deleteCache() = cache.deleteEnergyInfo()

    override suspend fun getElectricityInfo(): FetchResult<EnergyInfo> = flight.run { fetchOnce() }

    /**
     * One refresh attempt, with the original's fallback: a failure never throws away a cached
     * reading, it annotates it with why the refresh failed.
     *
     * Cancellation is the one exception. The original had no equivalent concern, but swallowing a
     * cancelled refresh into "here is the cached value" would leave a screen showing stale data as if
     * the request had completed.
     */
    private suspend fun fetchOnce(): FetchResult<EnergyInfo> {
        val fetchTime = clock.now()
        val cached = cache.getEnergyInfo()

        return try {
            appLog.i { "[EnergySession] Fetching the electricity reading" }
            val info = requestEnergyInfo()
            cache.saveEnergyInfo(info)
            FetchResult.fresh(fetchTime = fetchTime, data = info)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[EnergySession] Could not refresh the electricity reading" }
            if (cached == null) throw error
            FetchResult.cache(
                fetchTime = cached.fetchTime,
                data = cached.data,
                hintKey = electricityCacheHintFor(error).key,
            )
        }
    }

    private suspend fun requestEnergyInfo(): EnergyInfo {
        val code = openEnergySession()
        api.oauthGetUserInfo(code)

        val account = ids.currentAccount() ?: throw MissingCredentialsException()
        val nodeId = api.h5UserIdLogin(account)

        val rows = api.queryMeterList(nodeId)
        if (rows.isEmpty()) throw EnergyProtocolException("H5QueryMeterList 没有返回任何计量表")

        // The original did not search the rows for the electricity meter: it read the *first* row's
        // medium code and assumed the other row was the other medium. Kept as-is, because a node that
        // ever returns three meters would otherwise be interpreted differently here than there.
        val electricityIndex =
            if (rows.first().mediumCode == EnergyEndpoints.ELECTRICITY_MEDIUM_CODE) 0 else 1
        val electricity = rows.getOrNull(electricityIndex)
            ?: throw EnergyProtocolException("H5QueryMeterList 只返回了 ${rows.size} 个计量表，缺少电表")
        val metId = electricity.metId
            ?: throw EnergyProtocolException("电表行缺少 MetID")
        val electricityRemain = electricity.lastNum
            ?: throw EnergyProtocolException("电表行缺少 LastNum")
        val lastReadDate = electricity.lastReadDate
            ?: throw EnergyProtocolException("电表行缺少 LastReadDate")

        // The electricity window ends at the meter's own last reading rather than at today: a meter
        // that has not been read recently would otherwise return a window with no readings in it.
        val electricityMeterList = api.getMeterRead(
            metId = metId,
            from = lastReadDate.shift(months = -1),
            to = lastReadDate,
        )

        val waterMeterList = fetchWaterMeterReadings(rows, electricityIndex)

        return EnergyInfo(
            lastReadDate = lastReadDate,
            electricityRemain = electricityRemain,
            electricityMeterList = electricityMeterList,
            waterMeterList = waterMeterList,
        )
    }

    /** Logs in, walks the redirect chain, and returns the `code` the energy system appended to it. */
    private suspend fun openEnergySession(): String {
        val target = EnergyEndpoints.LOGIN_TARGET
        val location = try {
            ids.loginForService(service = target, captcha = captcha).getOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: IdsAuthException) {
            // The IDS layer classified this itself: a wrong password, a refused captcha, an
            // unanswered second factor. Those are the errors worth naming in the UI.
            throw auth
        } catch (error: Throwable) {
            // Everything the IDS layer could not classify reached it from the network stack, so this
            // is a transport failure — which here means "probably not on the campus network".
            throw EnergyNetworkException("能源系统登录请求失败", error)
        }

        val response = try {
            ids.followRedirects(initialLocation = location, service = target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (auth: IdsAuthException) {
            throw auth
        } catch (error: Throwable) {
            throw EnergyNetworkException("能源系统登录跳转失败", error)
        }

        return response.call.request.url.parameters["code"]
            ?: throw EnergyProtocolException("能源系统登录跳转缺少 code 参数")
    }

    /**
     * The water meter, over the last year.
     *
     * A failure here is logged and swallowed — the original's own choice, and the right one: the
     * screens treat a missing water list as "no water meter", and it must not cost the user the
     * electricity reading that succeeded a moment earlier.
     */
    private suspend fun fetchWaterMeterReadings(
        rows: List<EnergyMeterRow>,
        electricityIndex: Int,
    ): List<MeterInfo>? {
        val waterIndex = if (electricityIndex == 0) 1 else 0
        val waterMetId = rows.getOrNull(waterIndex)?.metId ?: return null
        return try {
            val today = clock.now().toLocalDateTime(timeZone).date
            api.getMeterRead(
                metId = waterMetId,
                from = today.shift(years = -1),
                to = today,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            appLog.w(error) { "[EnergySession] Could not fetch the water readings; leaving them blank" }
            null
        }
    }
}

/**
 * Classifies a refresh failure into the hint the cache notice shows.
 *
 * Ported from `EnergySession._cacheHintFromError`. The original's own exception types do not all
 * exist here — its `NotSchoolNetworkException` was raised by its HTTP layer, which this port replaces
 * with Ktor — so the mapping is by what went wrong rather than by name.
 */
internal fun electricityCacheHintFor(error: Throwable): EnergyCacheHint = when (error) {
    is MissingCredentialsException -> EnergyCacheHint.ACCOUNT_MISSING
    is CaptchaSolveFailedException -> EnergyCacheHint.CAPTCHA_FAILED
    is PasswordWrongException -> EnergyCacheHint.PASSWORD_WRONG
    is EnergyProtocolException -> EnergyCacheHint.ACCOUNT_PARSE_FAILED
    is EnergyNetworkException -> EnergyCacheHint.NETWORK_FAILED
    // Ordered after the specific auth failures: all of them are IdsAuthException subtypes.
    is IdsAuthException -> EnergyCacheHint.LOGIN_FAILED
    else -> EnergyCacheHint.UNKNOWN_ERROR
}
