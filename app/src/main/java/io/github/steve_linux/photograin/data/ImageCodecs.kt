// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.github.steve_linux.photograin.model.ColorProfile
import java.io.IOException

/**
 * JPEG XL (libjxl) and AVIF (libavif + libaom) through the native photograin_codec library.
 * Android itself cannot write either format, nor read JPEG XL.
 */
object ImageCodecs {
    private val loaded by lazy { runCatching { System.loadLibrary("photograin_codec") }.isSuccess }

    fun isJxl(mimeType: String?, displayName: String?): Boolean =
        mimeType == "image/jxl" || displayName?.substringAfterLast('.', "")?.lowercase() == "jxl"

    /**
     * Encodes an opaque ARGB_8888 [bitmap] whose pixels are already in [profile].
     * [exif] is TIFF-form EXIF; [icc] describes [profile] where AVIF has no code for it.
     */
    fun encode(
        format: ExportFormat,
        bitmap: Bitmap,
        quality: Int,
        profile: ColorProfile,
        exif: ByteArray?,
        icc: ByteArray?,
    ): ByteArray {
        val code = when (format) {
            ExportFormat.JXL -> 0
            ExportFormat.AVIF -> 1
            else -> throw IllegalArgumentException("$format is encoded by Android")
        }
        check()
        return nativeEncode(code, bitmap, quality, profile.ordinal, exif, icc)
            ?: throw IOException("Encoding failed")
    }

    /** Decodes the whole image, upright: sRGB images in sRGB, any other space converted to Display P3. */
    fun decodeJxl(context: Context, uri: Uri): Pair<Bitmap, ColorProfile> {
        check()
        val data = read(context, uri)
        val v = nativeProbeJxl(data) ?: throw IOException("Cannot read $uri")
        val profile = ColorProfile.entries[v[2]]
        val bitmap = Bitmap.createBitmap(v[0], v[1], Bitmap.Config.ARGB_8888, true, ColorSpaces.of(profile))
        nativeDecodeJxl(data, profile.ordinal, bitmap)
        return bitmap to profile
    }

    private fun check() {
        if (!loaded) throw IOException("JPEG XL and AVIF are not available on this device")
    }

    private fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Cannot open $uri")

    @JvmStatic private external fun nativeEncode(
        format: Int, bitmap: Bitmap, quality: Int, profile: Int, exif: ByteArray?, icc: ByteArray?,
    ): ByteArray?
    @JvmStatic private external fun nativeProbeJxl(file: ByteArray): IntArray?
    @JvmStatic private external fun nativeDecodeJxl(file: ByteArray, profile: Int, bitmap: Bitmap)
}
