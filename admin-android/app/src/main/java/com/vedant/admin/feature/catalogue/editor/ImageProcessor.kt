package com.vedant.admin.feature.catalogue.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/** A compressed image ready to upload: the bytes plus their content type. */
class PickedImage(val bytes: ByteArray, val mimeType: String)

/**
 * Turns a picked/captured [Uri] into a payload the `product-images` bucket will
 * accept.
 *
 * The bucket caps objects at **5 MB** and takes `jpeg|png|webp`. Rather than
 * trust the operator's camera, the image is down-sampled while decoding (so a
 * 12 MP photo never fully materialises in memory) and then scaled so its longest
 * edge is [MAX_DIMENSION], and re-encoded as JPEG at [JPEG_QUALITY]. The result
 * is comfortably under the cap for any realistic product photo.
 *
 * Runs off the main thread — the caller dispatches it to `Dispatchers.IO`.
 */
object ImageProcessor {

    /** Longest edge of the stored image, in pixels. */
    const val MAX_DIMENSION = 1600

    /** JPEG quality for the re-encode. */
    const val JPEG_QUALITY = 85

    /** Decodes, down-samples, scales and re-encodes, or `null` if unreadable. */
    fun loadAndCompress(context: Context, uri: Uri): PickedImage? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        val scaled = scaleToMax(decoded, MAX_DIMENSION)
        if (scaled !== decoded) decoded.recycle()

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        scaled.recycle()
        return PickedImage(bytes, "image/jpeg")
    }

    /** Largest power of two that keeps both edges at or above [max]. */
    private fun sampleSize(width: Int, height: Int, max: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= max && h / 2 >= max) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMax(bitmap: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val ratio = max.toFloat() / longest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
