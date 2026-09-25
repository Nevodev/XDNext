package com.nevoit.xdnext.core.image

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nevoit.xdnext.core.log.appLog

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = decodeBitmap(bytes)?.asImageBitmap()

actual fun decodeCaptchaImage(bytes: ByteArray): CaptchaImage? {
    val bitmap = decodeBitmap(bytes) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    // getPixels yields non-premultiplied ARGB, matching what CaptchaImage reads.
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return CaptchaImage(width = bitmap.width, height = bitmap.height, pixels = pixels)
}

private fun decodeBitmap(bytes: ByteArray) = runCatching {
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.onFailure {
    appLog.w(it) { "Could not decode an image of ${bytes.size} bytes" }
}.getOrNull()
