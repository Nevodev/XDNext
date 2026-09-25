package com.nevoit.xdnext.data.ids

import com.nevoit.xdnext.core.image.CaptchaImage
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the slider-captcha matcher.
 *
 * The important one is [prefersTheRealMatchOverAFlatRegion]: the original's score was unbounded, so it
 * reliably chose featureless areas over the real match, and this test fails against that formula. It is
 * the regression test for the defect that made automatic solving appear impossible.
 */
class SliderCaptchaMathTest {

    @Test
    fun prefersTheRealMatchOverAFlatRegion() {
        // A background with two candidate regions: a flat, saturated patch and the fragment's real
        // location. The original's `sum(w*t)/sum(w*w)` scores the flat patch higher because its window
        // energy is near zero, so it always answers wrongly here.
        val width = 240
        val height = 80
        val fragmentWidth = 60
        val fragmentHeight = 60
        val truth = 120

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if (x < 70) 255 else texture(x, y)
                pixels[y * width + x] = CaptchaImage.packArgb(255, value, value, value)
            }
        }
        val puzzle = CaptchaImage(width, height, pixels)

        val piecePixels = IntArray(fragmentWidth * fragmentHeight) { index ->
            val x = index % fragmentWidth
            val y = index / fragmentWidth
            val value = texture(truth + x, y)
            CaptchaImage.packArgb(255, value, value, value)
        }
        val piece = CaptchaImage(fragmentWidth, fragmentHeight, piecePixels)

        val match = assertNotNull(SliderCaptchaMath.findMatch(puzzle, piece))

        assertTrue(
            abs(match.offsetPixels - truth) <= 1,
            "expected the real match near $truth but got ${match.offsetPixels}",
        )
        assertTrue(
            match.score > 0.9,
            "a genuine match should score above 0.9 but scored ${match.score}",
        )
    }

    @Test
    fun returnsNullWhenTheFragmentIsFullyTransparent() {
        val puzzle = solid(120, 60, 128)
        val blank = CaptchaImage(
            width = 40,
            height = 40,
            pixels = IntArray(40 * 40) { CaptchaImage.packArgb(0, 0, 0, 0) },
        )
        assertNull(SliderCaptchaMath.findMatch(puzzle, blank))
    }

    @Test
    fun returnsNullWhenTheFragmentHasNoContrast() {
        // A uniform fragment carries no information. Scoring it would divide by zero, and the original's
        // 0.000001 floor turned that into an arbitrary huge number instead.
        val puzzle = textureImage(200, 80)
        val flat = CaptchaImage(
            width = 40,
            height = 40,
            pixels = IntArray(40 * 40) { CaptchaImage.packArgb(255, 128, 128, 128) },
        )
        assertNull(SliderCaptchaMath.findMatch(puzzle, flat))
    }

    @Test
    fun scoreIsBoundedToTheCorrelationRange() {
        // The whole point of the correction: a score outside [-1, 1] means the formula is not a
        // correlation. The original produced values up to 7 on real captchas.
        val puzzle = textureImage(300, 100)
        val piece = CaptchaImage(
            width = 60,
            height = 60,
            pixels = IntArray(60 * 60) { index ->
                val x = index % 60
                val y = index / 60
                val value = texture(150 + x, y)
                CaptchaImage.packArgb(255, value, value, value)
            },
        )
        val match = assertNotNull(SliderCaptchaMath.findMatch(puzzle, piece))
        assertTrue(
            match.score <= 1.0 && match.score >= -1.0,
            "score ${match.score} is outside the correlation range",
        )
        assertTrue(match.score > 0.9, "expected a strong match, got ${match.score}")
    }

    @Test
    fun findsTheOffsetForAFragmentNearTheLeftEdge() {
        val puzzle = textureImage(200, 70)
        val truth = 5
        val piece = crop(puzzle, truth, 50, 50)
        val match = assertNotNull(SliderCaptchaMath.findMatch(puzzle, piece))
        assertTrue(
            abs(match.offsetPixels - truth) <= 1,
            "expected ~$truth but got ${match.offsetPixels}",
        )
    }

    @Test
    fun matchesAFullSizePieceCanvas() {
        // The original's range formula uses the piece image's width, so a piece as wide as the
        // background collapses the search to nothing and it silently answers zero forever.
        val puzzle = textureImage(200, 70)
        val truth = 90
        val pieceWidth = 200
        val pieceHeight = 70
        val pixels = IntArray(pieceWidth * pieceHeight) { CaptchaImage.packArgb(0, 0, 0, 0) }
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                val value = texture(truth + x, y)
                pixels[y * pieceWidth + x] = CaptchaImage.packArgb(255, value, value, value)
            }
        }
        val piece = CaptchaImage(pieceWidth, pieceHeight, pixels)

        val match = assertNotNull(SliderCaptchaMath.findMatch(puzzle, piece))
        assertTrue(
            abs(match.offsetPixels - truth) <= 1,
            "expected ~$truth but got ${match.offsetPixels}",
        )
    }

    // --- Drag generation ------------------------------------------------------------------------

    @Test
    fun dragStartsAtTheOriginAndEndsAtTheTarget() {
        val tracks = SliderCaptchaMath.generateTracks(offset = 150, random = Random(7))
        assertEquals(CaptchaTrackPoint(0, 0, 0), tracks.first())
        assertEquals(150, tracks.last().x, "the last point must reach the requested offset")
        assertTrue(tracks.size in 12..16, "expected 12..16 points but got ${tracks.size}")
    }

    @Test
    fun dragNeverMovesBackwards() {
        val tracks = SliderCaptchaMath.generateTracks(offset = 200, random = Random(11))
        for (index in 1 until tracks.size) {
            assertTrue(
                tracks[index].x >= tracks[index - 1].x,
                "point $index moved backwards: ${tracks[index - 1].x} -> ${tracks[index].x}",
            )
        }
    }

    @Test
    fun dragStaysWithinTheVerticalWanderLimit() {
        repeat(25) { seed ->
            val tracks = SliderCaptchaMath.generateTracks(offset = 180, random = Random(seed))
            for (point in tracks) {
                assertTrue(point.y in -10..10, "vertical drift ${point.y} exceeded the clamp")
            }
        }
    }

    @Test
    fun dragFollowsASlowFastSlowCurve() {
        // A linear ramp is the obvious giveaway of a bot, and the server scores the shape: a two-point
        // stub is rejected at every position. The skew-sigmoid's characteristic is that the middle of
        // the gesture covers far more ground than either end.
        val tracks = SliderCaptchaMath.generateTracks(offset = 200, random = Random(3))
        val third = tracks.size / 3
        val early = tracks[third].x
        val late = tracks[2 * third].x
        val end = tracks.last().x

        assertTrue(
            late - early > early,
            "expected the gesture to accelerate out of the start: early=$early late=$late",
        )
        assertTrue(
            late - early > end - late,
            "expected the gesture to decelerate into the target: early=$early late=$late end=$end",
        )
    }

    @Test
    fun dragIsDeterministicForAFixedRandomSeed() {
        val first = SliderCaptchaMath.generateTracks(offset = 120, random = Random(99))
        val second = SliderCaptchaMath.generateTracks(offset = 120, random = Random(99))
        assertEquals(first, second)
    }

    @Test
    fun dragOfZeroOffsetIsJustTheOrigin() {
        assertEquals(
            listOf(CaptchaTrackPoint(0, 0, 0)),
            SliderCaptchaMath.generateTracks(offset = 0, random = Random(1)),
        )
    }

    @Test
    fun candidateOffsetsIncludeTheMatchItself() {
        // The original tried only `match +/- 1..4`, never the match, even though it is a likely answer.
        assertEquals(0, SliderCaptchaMath.OFFSET_DELTAS.first())
        assertTrue(SliderCaptchaMath.OFFSET_DELTAS.toSet().size == SliderCaptchaMath.OFFSET_DELTAS.size)
    }

    @Test
    fun candidateOffsetsReachFarEnoughToHitTheAcceptedPosition() {
        // Regression test for a defect in this port rather than in the original. The server's accepted
        // position lay within four units of the match in every one of five live trials, with observed
        // shifts of -4, +4, +4, 0 and 0 — so a neighbourhood of only +/-3 misses real answers, and the
        // automatic path fails intermittently for a reason that looks like a broken matcher.
        val reach = SliderCaptchaMath.OFFSET_DELTAS.maxOf { kotlin.math.abs(it) }
        assertTrue(reach >= 4, "the neighbourhood must reach at least +/-4 but reached +/-$reach")
        for (delta in 1..reach) {
            assertTrue(
                delta in SliderCaptchaMath.OFFSET_DELTAS && -delta in SliderCaptchaMath.OFFSET_DELTAS,
                "offset $delta is reachable in only one direction",
            )
        }
    }

    @Test
    fun candidateOffsetsAreOrderedByDistance() {
        // Nearer candidates are likelier, and a failure costs a request, so the order is not cosmetic.
        val distances = SliderCaptchaMath.OFFSET_DELTAS.map { kotlin.math.abs(it) }
        assertEquals(distances.sorted(), distances)
    }

    // --- Fixtures -------------------------------------------------------------------------------

    private fun textureImage(width: Int, height: Int): CaptchaImage {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val value = texture(x, y)
            CaptchaImage.packArgb(255, value, value, value)
        }
        return CaptchaImage(width, height, pixels)
    }

    private fun crop(source: CaptchaImage, fromX: Int, width: Int, height: Int): CaptchaImage {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val value = texture(fromX + x, y)
            CaptchaImage.packArgb(255, value, value, value)
        }
        return CaptchaImage(width, height, pixels)
    }

    private fun solid(width: Int, height: Int, value: Int): CaptchaImage =
        CaptchaImage(width, height, IntArray(width * height) {
            CaptchaImage.packArgb(255, value, value, value)
        })

    /**
     * Deterministic per-pixel texture.
     *
     * Real captcha backgrounds are photographs; a smooth gradient would make many positions score
     * similarly and the tests would prove nothing. The mixing constants are the usual 32-bit hash primes.
     */
    private fun texture(x: Int, y: Int): Int {
        var h = x * 73856093 xor y * 19349663
        h = h xor (h ushr 13)
        h *= 1274126177
        h = h xor (h ushr 16)
        return 20 + (h and 0xFF) * 215 / 255
    }
}
