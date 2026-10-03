package com.nevoit.xdnext.data.experiment

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Which lab system a booking came from.
 *
 * The original's `ExperimentData` carried this because it merged two systems into one page — the
 * physics lab (`PhyEws`) and the postgraduate/other lab — and the home page drew both. Only the
 * physics one is ported, so only [Physics] is ever produced; [Others] exists because the field is part
 * of the JSON the original wrote into `PhysicsExperiment.json`, and a reader that cannot name both
 * values would call an unrecognisable cache file a corrupt one instead of a partly readable one.
 */
@Serializable
enum class ExperimentType { Physics, Others }

/**
 * One physics experiment booking, as the lab site lists it.
 *
 * Ported from `lib/model/xidian_ids/experiment.dart`, field for field, because the JSON is also the
 * on-disk cache — `PhysicsExperiment.json`, the original's own name and shape.
 *
 * Two of the original's fields are worth knowing about:
 *
 *  - **[timeRanges] is a list.** One booking is one experiment in one slot, but the page it is read
 *    from lists a *booking per slot*, and the original kept the list so that a row that ever carried
 *    two would not lose one. Every row this port reads produces exactly one range today;
 *  - **[score] is nullable and is not a number.** The lab site never reports a mark — it shows an
 *    image per experiment, and which image it is is the score. See [ExperimentScore].
 */
@Serializable
data class ExperimentEntry(
    val type: ExperimentType = ExperimentType.Physics,
    /** The experiment's name, with the `（3学时）` suffix the site puts on it removed. */
    val name: String,
    /** The lab room, as the site spells it. */
    val classroom: String,
    val timeRanges: List<ExperimentTimeRange>,
    /**
     * The teacher of this slot, read from the course plan grid — [ExperimentScheduleApi] does that hop.
     *
     * Nullable, and null means "the plan grid did not name one" rather than "unknown": the grid holds one
     * course's slots at a time, so a booking of a course it no longer lists has no teacher to find. The
     * sentence the page prints for that is a *resource*, which is why the data layer records the absence
     * and not the wording.
     */
    val teacher: String? = null,
    /** The reference material column, when the booking has one. */
    val reference: String? = null,
    val score: ExperimentScore? = null,
)

/** One sitting of an experiment: when it starts and when it ends, on the day it is booked for. */
@Serializable
data class ExperimentTimeRange(
    val start: LocalDateTime,
    val stop: LocalDateTime,
)

/**
 * What the report system showed in a booking's score cell.
 *
 * The score is an *image*, and the original recognises it by hashing the image's pixels and looking
 * the hash up in a table generated from the images the lab serves — see [ExperimentScoreRecognition].
 * So an unrecognised image is a normal outcome rather than an error: [found] is false, [label] is
 * empty, and [rawUrl] still names the picture, which is what lets the page offer to show it.
 *
 * The field is named `rawUrl` after the original's own `RecognitionResult`, so a cache file written by
 * either app can be read by eye against the other.
 */
@Serializable
data class ExperimentScore(
    val label: String,
    val found: Boolean,
    val rawUrl: String,
)

/**
 * Why a cached experiment list is being shown instead of a fresh one.
 *
 * Only the key is here: the sentence a user reads is a string resource, mapped from this value where it
 * is drawn (`ui.experiment`). The key is what travels through `FetchResult.hintKey`, and it is the
 * original's own (`experiment.physics_cache_hint_*`) — kept as *provenance* rather than as a lookup, so
 * that a captured value can still be compared with the original's settings file.
 *
 * One variant of the original is deliberately **absent**: it could report 不在校园网 because its own
 * HTTP layer raised a dedicated `NotSchoolNetworkException`, and nothing here can tell that failure
 * apart from any other transport failure. It is reported as [NETWORK_FAILED] instead, which is the
 * actionable half — the server is only reachable from the campus network, and the sentence says so.
 */
enum class ExperimentCacheHint(val key: String) {
    MISSING_PASSWORD("experiment.physics_cache_hint_missing_password"),
    LOGIN_FAILED("experiment.physics_cache_hint_login_failed"),
    NETWORK_FAILED("experiment.physics_cache_hint_network_failed"),
    UNKNOWN_ERROR("experiment.physics_cache_hint_unknown_error"),
}

/** The lab site answered in a way this port does not define. */
class ExperimentProtocolException(message: String) : Exception(message)

/**
 * The lab site could not be reached, or the exchange was cut short.
 *
 * The same split the timetable and energy modules make: transport failures become this type here so
 * nothing above has to know the HTTP engine's exception hierarchy, which differs per platform.
 */
class ExperimentNetworkException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The lab site refused the login.
 *
 * [detail] is the page's own message (`login1_Label1`), with its `<font>` and `<br>` markup turned
 * into the sentence the user should read — the original's own three `replaceAll` calls, kept because
 * that element is the only place the server explains itself.
 */
class ExperimentLoginFailedException(val detail: String?) :
    Exception(detail ?: "物理实验登录失败")

/** No experiment password is stored, so the module has nothing to log in with. */
class ExperimentNoPasswordException : Exception("未填写物理实验密码")
