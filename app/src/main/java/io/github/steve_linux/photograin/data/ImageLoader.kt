// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ColorProfile
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
    /** Color space of [preview] and of the regions decoded for export. */
    val profile: ColorProfile,
)

object ImageLoader {
    /** Long edge of the preview bitmap. Large enough for tablets, small enough to stay fluid. */
    const val PREVIEW_LONG_EDGE = 2560

    fun load(context: Context, uri: Uri, maxLongEdge: Int = PREVIEW_LONG_EDGE): LoadedImage {
        val resolver = context.contentResolver
        val name = queryName(context, uri)
        val mime = resolver.getType(uri)
        if (RawImage.isRaw(mime, name)) return loadRaw(context, uri, name, mime, maxLongEdge)
        if (ImageCodecs.isJxl(mime, name)) return loadJxl(context, uri, name, maxLongEdge)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds decodeStream always returns null, so check the stream, not the result.
        val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IOException("Unsupported image: $uri")
        val profile = ColorSpaces.workingSpaceFor(bounds.outColorSpace)

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(max(w, h), maxLongEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpaces.of(profile)
            })
        } ?: throw IOException("Cannot decode $uri")

        return LoadedImage(
            uri = uri,
            preview = scaleDown(decoded, maxLongEdge),
            sourceWidth = w,
            sourceHeight = h,
            base = BaseOrientation.fromExif(readOrientation(context, uri)),
            displayName = name,
            mimeType = mime ?: bounds.outMimeType,
            profile = profile,
        )
    }

    /** RAW previews skip demosaicing (half size), which is plenty for a screen and much faster. */
    private fun loadRaw(context: Context, uri: Uri, name: String?, mime: String?, maxLongEdge: Int): LoadedImage {
        val info = RawImage.probe(context, uri)
        val developed = RawImage.develop(context, uri, halfSize = max(info.width, info.height) >= maxLongEdge)
        return LoadedImage(
            uri = uri,
            preview = scaleDown(developed, maxLongEdge),
            sourceWidth = info.width,
            sourceHeight = info.height,
            base = BaseOrientation.fromExif(info.exifOrientation),
            displayName = name,
            mimeType = mime,
            profile = ColorSpaces.RAW_WORKING_SPACE,
        )
    }

    /** JPEG XL comes decoded upright, so no orientation is left to apply. */
    private fun loadJxl(context: Context, uri: Uri, name: String?, maxLongEdge: Int): LoadedImage {
        val (bitmap, profile) = ImageCodecs.decodeJxl(context, uri)
        val w = bitmap.width
        val h = bitmap.height
        return LoadedImage(
            uri = uri,
            preview = scaleDown(bitmap, maxLongEdge),
            sourceWidth = w,
            sourceHeight = h,
            base = BaseOrientation.fromExif(ExifInterface.ORIENTATION_NORMAL),
            displayName = name,
            mimeType = "image/jxl",
            profile = profile,
        )
    }

    /** Small decode for thumbnails in the home screen; null if the file is gone. */
    fun thumbnail(context: Context, uri: Uri, size: Int): Bitmap? = runCatching {
        if (RawImage.isRaw(context.contentResolver.getType(uri), queryName(context, uri))) {
            // The camera's own JPEG preview is far quicker than developing the RAW data.
            val preview = RawImage.embeddedPreview(context, uri, size)
            if (preview != null) {
                val base = BaseOrientation.fromExif(RawImage.probe(context, uri).exifOrientation)
                return@runCatching oriented(scaleDown(preview, size), base)
            }
        }
        val image = load(context, uri, size)
        oriented(image.preview, image.base)
    }.getOrNull()

    private fun oriented(bitmap: Bitmap, base: BaseOrientation): Bitmap {
        val m = android.graphics.Matrix().apply {
            if (base.mirror) postScale(-1f, 1f)
            postRotate(90f * base.quarterTurns)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** How the stored pixels must be turned to display upright, without decoding them. */
    fun baseOrientation(context: Context, uri: Uri): BaseOrientation {
        val mime = context.contentResolver.getType(uri)
        val name = queryName(context, uri)
        val tag = when {
            RawImage.isRaw(mime, name) -> RawImage.probe(context, uri).exifOrientation
            ImageCodecs.isJxl(mime, name) -> ExifInterface.ORIENTATION_NORMAL  // decoded upright
            else -> readOrientation(context, uri)
        }
        return BaseOrientation.fromExif(tag)
    }

    fun readOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    internal fun queryName(context: Context, uri: Uri): String? = runCatching {
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
