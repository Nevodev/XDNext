package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.core.image.CaptchaImage
import com.nevoit.xdnext.core.image.IntBounds
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The slider-captcha matcher: locate the fragment in the background, and synthesise a plausible drag.
 *
 * Ported from `slider_captcha_client.dart`, **with the scoring function corrected**. The original
 * computed `sum(w*t) / sum(w*w)` — a regression slope, not a correlation. It is unbounded, and it grows
 * without limit wherever the window is nearly flat, so the matcher reliably preferred featureless sky
 * over the real match: measured on the live server, the original metric's "best" positions scored 1.7
 * to 7.1 on flat regions while the true position scored 0.026.
 *
 * Dividing by the template's energy as well gives a real normalised cross-correlation bounded to
 * [-1, 1]. With that one change the same images yield a sharp peak of **0.996** within one pixel of the
 * position the server accepts — the content really is there, and the original simply could not see it.
 *
 * Two further differences from the original:
 *  - the search range is bounded by the fragment's width, not the piece image's width. The original
 *    computed `background.width - pieceImage.width + windowWidth`, which collapses to nothing whenever
 *    the piece image is a full-size canvas, silently pinning the answer at offset 0 forever;
 *  - several edge trims are scored and the best peak wins, instead of the first trim that produces any
 *    window at all. Matching is pure local arithmetic, so this costs no network requests.
 */
object SliderCaptchaMath {

    /**
     * Margin trimmed from the fragment outline before matching.
     *
     * The fragment has a soft, anti-aliased edge, and matching it makes the score sensitive to those
     * pixels rather than to the shape. Measured on a real captcha pair, trimming 24 px gives a peak
     * score of 0.996 where trimming nothing gives 0.303 — so the original's constant is not arbitrary
     * and is kept first in the candidate list.
     */
    const val BORDER = 24

    /** Normalisation constant for the skew-sigmoid drag curve. */
    private val GEN_TRACKS_NORM = 1.0 / (1.0 + exp(-7.0 * (1.0 - 0.42)))

    /** Where in the background the fragment belongs, and how convincing the match was. */
    data class Match(
        /** Offset of the piece image's left edge within the background, in background pixels. */
        val offsetPixels: Int,
        /** Normalised correlation at that offset, in [-1, 1]. */
        val score: Double,
        /** Which edge trim produced it. */
        val border: Int,
    )

    /**
     * Finds the fragment in the background.
     *
     * @return the best [Match], or null when the images cannot be matched at all (no opaque outline, no
     *   legal offsets, or a fragment with no contrast).
     */
    fun findMatch(puzzle: CaptchaImage, piece: CaptchaImage): Match? {
        val bounds = piece.opaqueBounds() ?: return null

        var best: Match? = null
        for (border in BORDER_CANDIDATES) {
            val candidate = matchWithBorder(puzzle, piece, bounds, border) ?: continue
            if (best == null || candidate.score > best.score) best = candidate
        }
        return best
    }

    private fun matchWithBorder(
        puzzle: CaptchaImage,
        piece: CaptchaImage,
        bounds: IntBounds,
        border: Int,
    ): Match? {
        val left = bounds.left + border
        val top = bounds.top + border
        val right = bounds.right - border
        val bottom = bounds.bottom - border
        if (right <= left || bottom <= top) return null

        val windowWidth = right - left + 1
        val windowHeight = bottom - top + 1

        // The furthest legal offset places the fragment's right edge at the background's right edge.
        val maxOffset = puzzle.width - 1 - right
        if (maxOffset < 1) return null

        val area = windowWidth * windowHeight
        val templateMean = piece.luminanceSum(left, top, windowWidth, windowHeight) / area
        val template = DoubleArray(area)
        var templateEnergy = 0.0
        run {
            var index = 0
            for (y in top until top + windowHeight) {
                for (x in left until left + windowWidth) {
                    val centred = piece.luminance(x, y) - templateMean
                    template[index++] = centred
                    templateEnergy += centred * centred
                }
            }
        }
        // A fragment with no contrast carries no information; scoring it would divide by zero.
        if (templateEnergy <= 1e-9) return null

        // Column sums let each horizontal step update in O(windowHeight) instead of re-scanning.
        val columnSums = DoubleArray(maxOffset + windowWidth) { x ->
            val px = x + left
            if (px < puzzle.width) puzzle.luminanceSum(px, top, 1, windowHeight) else 0.0
        }

        var windowSum = 0.0
        for (x in 0 until windowWidth) windowSum += columnSums[x]

        var bestOffset = 0
        var bestScore = Double.NEGATIVE_INFINITY

        for (offset in 0..maxOffset) {
            if (offset > 0) {
                windowSum += columnSums[offset + windowWidth - 1] - columnSums[offset - 1]
            }
            val score = correlation(
                puzzle = puzzle,
                left = offset + left,
                top = top,
                width = windowWidth,
                height = windowHeight,
                template = template,
                windowMean = windowSum / area,
                templateEnergy = templateEnergy,
            )
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }

        return Match(offsetPixels = bestOffset, score = bestScore, border = border)
    }

    /**
     * Normalised cross-correlation between the mean-subtracted template and a mean-subtracted window,
     * bounded to [-1, 1].
     *
     * Both energies appear, which is what makes the score comparable across positions. The original
     * divided only by the window's energy, and that unbounded score is why flat regions won.
     */
    private fun correlation(
        puzzle: CaptchaImage,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        template: DoubleArray,
        windowMean: Double,
        templateEnergy: Double,
    ): Double {
        var cross = 0.0
        var windowEnergy = 0.0
        var index = 0
        for (y in top until top + height) {
            for (x in left until left + width) {
                val centred = puzzle.luminance(x, y) - windowMean
                cross += centred * template[index++]
                windowEnergy += centred * centred
            }
        }
        if (windowEnergy <= 1e-9) return 0.0
        return cross / sqrt(windowEnergy * templateEnergy)
    }

    /**
     * Builds a drag track that ends at [offset] pixels.
     *
     * The server validates the *shape* of the movement: a two-point stub track is rejected at every
     * position, while a 10-14 point skew-sigmoid passes. The curve is slow, then fast, then slow, with a
     * small vertical wander and 300-500 ms per step — the original's parameters, which are what the
     * server accepts.
     *
     * The first point is always `(0, 0, 0)`; drag-tracking libraries expect that and the original sent
     * it.
     */
    fun generateTracks(offset: Int, random: Random = Random.Default): List<CaptchaTrackPoint> {
        val tracks = mutableListOf(CaptchaTrackPoint(x = 0, y = 0, millis = 0))
        if (offset <= 0) return tracks

        val steps = random.nextInt(5) + 10 // 10..14
        var vertical = 0

        for (step in 0 until steps) {
            val progress =
                1.0 / (1.0 + exp(-7.0 * (step.toDouble() / steps - 0.42))) / GEN_TRACKS_NORM
            val x = min(offset - 1, max(tracks.last().x + 1, (offset * progress).roundToInt()))

            val roll = random.nextDouble()
            vertical = when {
                roll < 0.65 -> vertical - 1
                roll < 0.80 -> vertical + 1
                else -> vertical
            }
            vertical = max(-10, min(10, vertical))

            tracks.add(CaptchaTrackPoint(x = x, y = vertical, millis = random.nextInt(201) + 300))
        }

        tracks.add(CaptchaTrackPoint(x = offset, y = vertical, millis = random.nextInt(201) + 300))
        return tracks
    }

    /**
     * Canvas-unit offsets to try around the match, best candidate first.
     *
     * The server's acceptance window is narrow, so being close is not enough. Measured on the live
     * server, the position it accepts lay within **four** units of the match in every one of five trials
     * — but not always at zero: the accepted shifts were -4, +4, +4, 0, 0. The neighbourhood therefore
     * has to reach at least +/-4, and an earlier +/-3 here is exactly why automatic solving appeared to
     * be broken while the matcher was in fact right.
     *
     * Ordering is by distance from the match, because that is the order of likelihood. The cost of the
     * whole list is only paid when the attempt fails, in which case the user is asked to drag instead.
     */
    val OFFSET_DELTAS = intArrayOf(0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6)

    /** Edge trims to score, best-first on real captchas. */
    private val BORDER_CANDIDATES = intArrayOf(BORDER, 16, 8, 0)
}
