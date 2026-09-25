package com.nevoit.xdnext.core.image

/**
 * Decoded pixels with the accessors the slider-captcha matcher needs.
 *
 * The matcher is written against this type rather than against any image library so its arithmetic —
 * the part that can be wrong in an interesting way — is unit-testable with synthetic pixels. Decoding
 * lives in [decodeCaptchaImage], because that is the only platform-dependent part.
 *
 * Pixels are packed non-premultiplied ARGB, which is what both `Bitmap.getPixels` and
 * `ImageBitmap.readPixels` produce.
 */
class CaptchaImage(
    val width: Int,
    val height: Int,
    private val pixels: IntArray,
) {

    init {
        require(pixels.size >= width * height) {
            "pixel buffer holds ${pixels.size} values but $width x $height requires ${width * height}"
        }
    }

    /** 0 (transparent) to 255 (opaque). The matcher finds the piece outline from this. */
    fun alpha(x: Int, y: Int): Int = (pixels[y * width + x] ushr 24) and 0xFF

    /**
     * Perceptual luminance in 0..255.
     *
     * Uses the same Rec.601 weights as `package:image`'s `Pixel.luminance`, which the original's
     * template matching was tuned against.
     */
    fun luminance(x: Int, y: Int): Double {
        val packed = pixels[y * width + x]
        val red = (packed ushr 16) and 0xFF
        val green = (packed ushr 8) and 0xFF
        val blue = packed and 0xFF
        return 0.299 * red + 0.587 * green + 0.114 * blue
    }

    /** Sum of luminance over the rectangle. */
    internal fun luminanceSum(left: Int, top: Int, width: Int, height: Int): Double {
        var sum = 0.0
        for (y in top until top + height) {
            for (x in left until left + width) {
                sum += luminance(x, y)
            }
        }
        return sum
    }

    /** Rectangle of fully opaque pixels, or null when the image has none. */
    internal fun opaqueBounds(): IntBounds? {
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (alpha(x, y) == OPAQUE) {
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (left > right || top > bottom) null else IntBounds(left, top, right, bottom)
    }

    companion object {
        internal const val OPAQUE = 255

        /** Packs 8-bit channels into the ARGB layout this class reads. */
        fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int =
            ((alpha and 0xFF) shl 24) or
                    ((red and 0xFF) shl 16) or
                    ((green and 0xFF) shl 8) or
                    (blue and 0xFF)
    }
}

/** Inclusive pixel rectangle. */
internal data class IntBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
