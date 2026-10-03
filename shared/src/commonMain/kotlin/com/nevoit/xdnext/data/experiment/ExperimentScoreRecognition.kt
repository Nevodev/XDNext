package com.nevoit.xdnext.data.experiment

import com.nevoit.xdnext.core.image.CaptchaImage
import com.nevoit.xdnext.core.log.appLog

/**
 * Which mark a report-system image shows.
 *
 * The lab never prints a number. It prints a *picture* of one — `9.5`, `已上传`, `未上传` — so the
 * score has to be recognised from the image, and the original's way of recognising it is a pixel hash
 * against a table generated from the pictures the lab serves. [ScoreHashes] is that table, copied from
 * the original's `lib/generated/score_hashes.g.dart`, which its own generator built by downloading the
 * marks once and hashing them.
 *
 * That makes the table a *snapshot*: a mark the lab re-renders with different pixels, or a mark added
 * after the table was generated, hashes to nothing here — which is why [ExperimentScore.found] exists
 * and why an unrecognised image is reported rather than hidden. The table is the same one the
 * original ships, so the two apps recognise exactly the same set.
 */
internal object ExperimentScoreRecognition {

    /**
     * How much of an image is hashed: a 50 × 20 pixel window from its top-left corner.
     *
     * The original's own window, and it is not a guess about the image's size — it is the part of the
     * mark that carries the glyph, with the rest of the picture (padding, a border frame) left out. An
     * image smaller than the window contributes what it has; the hash of a cropped image cannot match
     * a full one's entry anyway, so the clamp only avoids reading outside it.
     */
    private const val HASH_WIDTH = 50
    private const val HASH_HEIGHT = 20

    /** FNV-1a's 32-bit parameters: the offset basis, and the prime each byte is multiplied by. */
    private const val FNV_OFFSET_BASIS = -2128831035 // 0x811C9DC5
    private const val FNV_PRIME = 16777619 // 0x01000193

    /** The score a hashed image shows, or null when the table has no entry for it. */
    fun labelFor(hash: Int): String? =
        scoreHashes.entries.firstOrNull { it.value == hash }?.key

    /**
     * The hash of an image's top-left window.
     *
     * Two details are the original's and both matter to the result matching its table:
     *
     *  - **only fully opaque pixels are hashed.** The pictures have transparent surroundings, and
     *    including those pixels' colour would hash whatever the decoder left in them;
     *  - the channels go in as red, then green, then blue, one byte each.
     */
    fun hashOf(image: CaptchaImage): Int {
        var hash = FNV_OFFSET_BASIS
        val width = minOf(HASH_WIDTH, image.width)
        val height = minOf(HASH_HEIGHT, image.height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (image.alpha(x, y) != OPAQUE) continue
                hash = fnvStep(hash, image.red(x, y))
                hash = fnvStep(hash, image.green(x, y))
                hash = fnvStep(hash, image.blue(x, y))
            }
        }
        return hash
    }

    private fun fnvStep(hash: Int, byte: Int): Int = (hash xor byte) * FNV_PRIME

    private const val OPAQUE = 255

    /**
     * The mark for the image's hash, as the whole [ExperimentScore] the model carries.
     *
     * The score always carries the image it was taken from, found or not: the page offers to show it
     * and the user is the one who can say what it means.
     */
    fun recognise(image: CaptchaImage, url: String): ExperimentScore {
        val hash = hashOf(image)
        val label = labelFor(hash)
        if (label == null) {
            appLog.i { "[ExperimentScoreRecognition] No label for image hash $hash" }
        }
        return ExperimentScore(label = label.orEmpty(), found = label != null, rawUrl = url)
    }

    /**
     * The mark-to-hash table, generated from the lab's own pictures.
     *
     * Copied value for value from the original's `kScoreHashes`, with each entry written as the signed
     * 32-bit number its unsigned form denotes — the hex comment beside each is the value the generator
     * printed, so a future regeneration can be diffed against this without decoding anything.
     *
     * `internal` rather than private so a test can pin a couple of entries against the generator's own
     * values: a transcription slip here is a mark that silently stops being recognised.
     */
    internal val scoreHashes: Map<String, Int> = linkedMapOf(
        "0" to -763535233, // 0xD27D607F
        "10" to -1535580416, // 0xA478E700
        "4" to 314909311, // 0x12C5227F
        "5" to -877103927, // 0xCBB874C9
        "5.5" to 1879675493, // 0x70099265
        "6" to 1633340036, // 0x615ACA84
        "6.5" to 406889604, // 0x1840A484
        "7" to 923381590, // 0x3709AF56
        "7.5" to -615725352, // 0xDB4CC6D8
        "8" to 957817480, // 0x39172288
        "8.5" to -84665090, // 0xFAF41CFE
        "9" to -793138086, // 0xD0B9AC5A
        "9.5" to -107152068, // 0xF99CFD3C
        "已上传" to -217039258, // 0xF3103E66
        "未上传" to 843703430, // 0x3249E486
    )
}
