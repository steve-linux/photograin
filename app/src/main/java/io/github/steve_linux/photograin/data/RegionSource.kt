// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import io.github.steve_linux.photograin.model.ColorProfile
import java.io.Closeable
import java.io.IOException

/** Decodes arbitrary rectangles of the full-resolution original, as stored (no EXIF rotation). */
interface RegionSource : Closeable {
    val width: Int
    val height: Int

    /** Color space of the decoded pixels, which the edit is rendered in. */
    val profile: ColorProfile

    /** Decodes [rect] (source pixels), downsampled by the power of two [sampleSize]. */
    fun decode(rect: Rect, sampleSize: Int): Bitmap

    companion object {
        /**
         * Opens [uri] with [BitmapRegionDecoder] so huge images never need to fit in memory
         * at once, falling back to a full decode for formats it does not support.
         */
        fun open(context: Context, uri: Uri): RegionSource {
            val resolver = context.contentResolver
            if (RawImage.isRaw(resolver.getType(uri), ImageLoader.queryName(context, uri))) {
                return BitmapRegionSource(RawImage.develop(context, uri, halfSize = false), ColorSpaces.RAW_WORKING_SPACE)
            }
            if (ImageCodecs.isJxl(resolver.getType(uri), ImageLoader.queryName(context, uri))) {
                val (bitmap, profile) = ImageCodecs.decodeJxl(context, uri)
                return BitmapRegionSource(bitmap, profile)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val profile = ColorSpaces.workingSpaceFor(bounds.outColorSpace)
            val decoder = runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    if (Build.VERSION.SDK_INT >= 31) {
                        BitmapRegionDecoder.newInstance(stream)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(stream, false)
                    }
                }
            }.getOrNull()
            if (decoder != null) return DecoderRegionSource(decoder, profile)

            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPreferredColorSpace = ColorSpaces.of(profile)
                })
            } ?: throw IOException("Cannot decode $uri")
            return BitmapRegionSource(bitmap, profile)
        }
    }
}

private class DecoderRegionSource(
    private val decoder: BitmapRegionDecoder,
    override val profile: ColorProfile,
) : RegionSource {
    override val width get() = decoder.width
    override val height get() = decoder.height

    override fun decode(rect: Rect, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpaces.of(profile)
        }
        return decoder.decodeRegion(rect, options) ?: throw IOException("Region decode failed for $rect")
    }

    override fun close() = decoder.recycle()
}

private class BitmapRegionSource(
    private val bitmap: Bitmap,
    override val profile: ColorProfile,
) : RegionSource {
    override val width get() = bitmap.width
    override val height get() = bitmap.height

    override fun decode(rect: Rect, sampleSize: Int): Bitmap {
        val region = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        if (sampleSize <= 1) return region
        val w = maxOf(1, (rect.width() + sampleSize - 1) / sampleSize)
        val h = maxOf(1, (rect.height() + sampleSize - 1) / sampleSize)
        return Bitmap.createScaledBitmap(region, w, h, true).also { if (it !== region) region.recycle() }
    }

    override fun close() = bitmap.recycle()
}
