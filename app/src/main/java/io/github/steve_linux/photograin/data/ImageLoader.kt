// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import io.github.steve_linux.photograin.model.BaseOrientation
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** An opened photo: a screen-sized copy for the live preview plus facts about the original. */
class LoadedImage(
    val uri: Uri,
    val preview: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val base: BaseOrientation,
    val displayName: String?,
    val mimeType: String?,
)

object ImageLoader {
    /** Long edge of the preview bitmap. Large enough for tablets, small enough to stay fluid. */
    const val PREVIEW_LONG_EDGE = 2560

    fun load(context: Context, uri: Uri, maxLongEdge: Int = PREVIEW_LONG_EDGE): LoadedImage {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds decodeStream always returns null, so check the stream, not the result.
        val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IOException("Unsupported image: $uri")

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(max(w, h), maxLongEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: throw IOException("Cannot decode $uri")

        return LoadedImage(
            uri = uri,
            preview = scaleDown(decoded, maxLongEdge),
            sourceWidth = w,
            sourceHeight = h,
            base = BaseOrientation.fromExif(readOrientation(context, uri)),
            displayName = queryName(context, uri),
            mimeType = resolver.getType(uri) ?: bounds.outMimeType,
        )
    }

    /** Small decode for thumbnails in the home screen; null if the file is gone. */
    fun thumbnail(context: Context, uri: Uri, size: Int): Bitmap? = runCatching {
        val image = load(context, uri, size)
        val m = android.graphics.Matrix().apply {
            if (image.base.mirror) postScale(-1f, 1f)
            postRotate(90f * image.base.quarterTurns)
        }
        Bitmap.createBitmap(image.preview, 0, 0, image.preview.width, image.preview.height, m, true)
    }.getOrNull()

    fun readOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun sampleSizeFor(longEdge: Int, target: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val long = max(bitmap.width, bitmap.height)
        if (long <= maxLongEdge) return bitmap
        val s = maxLongEdge.toFloat() / long
        val scaled = Bitmap.createScaledBitmap(
            bitmap, max(1, (bitmap.width * s).roundToInt()), max(1, (bitmap.height * s).roundToInt()), true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
