package com.nevoit.xdnext.core.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Image decoding, the one genuinely platform-bound step in the captcha path.
 *
 * Compose Multiplatform only exposes a `ByteArray -> ImageBitmap` bridge on Skiko-backed targets; with
 * an Android-only KMP target the Compose artifacts resolve to AndroidX, where the backing type is
 * `android.graphics.Bitmap`. An `expect`/`actual` pair is therefore the honest way to express this, and
 * it is also where iOS and desktop will add their own decoders later.
 *
 * Both functions return null rather than throwing: an image that fails to decode must degrade to "ask
 * the user to drag it", not crash a login.
 */

/** Decodes encoded bytes (PNG or JPEG) for display. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** Decodes encoded bytes and reads back the pixels the matcher needs. */
expect fun decodeCaptchaImage(bytes: ByteArray): CaptchaImage?
