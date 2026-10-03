package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.image.CaptchaImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the marks' picture recognition.
 *
 * The recogniser is a hash comparison, so every bug it can have is silent: a wrong channel order or a
 * window one row too short does not fail, it returns *a* hash, and the mark simply stops being
 * recognised — which the page then reports as "the library has no picture for this", as if the library
 * were at fault.
 *
 * So the hash is checked against an implementation written here from the algorithm's own definition
 * (FNV-1a over the bytes the pixels produce, offset basis `0x811C9DC5`, prime `0x01000193`), and the two
 * properties the window has — only opaque pixels count, and nothing outside the top-left 50 × 20 is read
 * — are checked by constructing images that differ *only* where they must not matter.
 */
class ExperimentScoreRecognitionTest {

    @Test
    fun hashesTheOpaquePixelsInRedGreenBlueOrder() {
        val pixels = IntArray(WIDTH * HEIGHT) { CaptchaImage.packArgb(0, 0, 0, 0) }
        pixels[0] = CaptchaImage.packArgb(255, 0x01, 0x02, 0x03)
        pixels[1] = CaptchaImage.packArgb(255, 0x10, 0x20, 0x30)

        val image = CaptchaImage(WIDTH, HEIGHT, pixels)

        assertEquals(fnv1a32(byteArrayOf(0x01, 0x02, 0x03, 0x10, 0x20, 0x30)), ExperimentScoreRecognition.hashOf(image))
    }

    @Test
    fun ignoresTransparentPixelsEntirely() {
        // The pictures have transparent surroundings, and the hash must not depend on whatever colour the
        // decoder left in them — two images that differ only there are the same mark.
        val withJunk = IntArray(WIDTH * HEIGHT) { CaptchaImage.packArgb(0, 0xAB, 0xCD, 0xEF) }
        val empty = IntArray(WIDTH * HEIGHT) { CaptchaImage.packArgb(0, 0, 0, 0) }
        withJunk[0] = CaptchaImage.packArgb(255, 0x40, 0x50, 0x60)
        empty[0] = CaptchaImage.packArgb(255, 0x40, 0x50, 0x60)

        assertEquals(
            ExperimentScoreRecognition.hashOf(CaptchaImage(WIDTH, HEIGHT, empty)),
            ExperimentScoreRecognition.hashOf(CaptchaImage(WIDTH, HEIGHT, withJunk)),
        )
    }

    @Test
    fun readsOnlyTheTopLeftWindow() {
        // 60 × 30, so both dimensions are larger than the window: the pixel outside it is a different
        // colour in one image and absent in the other, and the two hashes must agree.
        val window = IntArray(60 * 30) { CaptchaImage.packArgb(0, 0, 0, 0) }
        window[0] = CaptchaImage.packArgb(255, 0x11, 0x22, 0x33)
        val outside = window.copyOf().also { it[25 * 60 + 55] = CaptchaImage.packArgb(255, 0xFF, 0xFF, 0xFF) }

        assertEquals(
            ExperimentScoreRecognition.hashOf(CaptchaImage(60, 30, window)),
            ExperimentScoreRecognition.hashOf(CaptchaImage(60, 30, outside)),
        )
    }

    @Test
    fun anImageSmallerThanTheWindowIsReadToItsEdge() {
        // A cropped image cannot match a full one's entry, but it must not read outside its own pixels.
        val image = CaptchaImage(4, 3, IntArray(4 * 3) { CaptchaImage.packArgb(255, 1, 2, 3) })

        assertEquals(
            ExperimentScoreRecognition.hashOf(image),
            fnv1a32(ByteArray(4 * 3 * 3) { listOf(1, 2, 3)[it % 3].toByte() }),
        )
    }

    @Test
    fun anUnknownPictureIsReportedRatherThanGuessed() {
        val image = CaptchaImage(2, 2, IntArray(4) { CaptchaImage.packArgb(255, 7, 7, 7) })

        val score = ExperimentScoreRecognition.recognise(image, "http://wlsy.xidian.edu.cn/score/1.png")

        assertFalse(score.found)
        assertEquals("", score.label)
        assertEquals(
            "http://wlsy.xidian.edu.cn/score/1.png",
            score.rawUrl,
            "The picture is named even when it was not recognised: it is what the page shows.",
        )
    }

    @Test
    fun theTableKeepsTheValuesTheGeneratorPrinted() {
        // Two entries pinned against the original's `kScoreHashes` in its *unsigned* form: the table is
        // copied by hand, and a slip of a digit is a mark that silently stops being recognised.
        assertEquals(0xF99CFD3C.toInt(), ExperimentScoreRecognition.scoreHashes.getValue("9.5"))
        assertEquals(0x3249E486.toInt(), ExperimentScoreRecognition.scoreHashes.getValue("未上传"))
        assertTrue(
            ExperimentScoreRecognition.scoreHashes.containsKey("已上传"),
            "The library's two states are what an unmarked experiment shows.",
        )
    }

    /** FNV-1a, 32-bit, written from the algorithm rather than shared with the class under test. */
    private fun fnv1a32(bytes: ByteArray): Int {
        var hash = -2128831035 // 0x811C9DC5
        bytes.forEach { byte ->
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= 16777619 // 0x01000193
        }
        return hash
    }

    private companion object {
        const val WIDTH = 50
        const val HEIGHT = 20
    }
}
