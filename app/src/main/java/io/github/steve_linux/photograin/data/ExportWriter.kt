// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.github.steve_linux.photograin.model.ColorProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.Inflater

enum class ExportFormat(val mimeType: String, val extension: String, val label: String, val lossy: Boolean) {
    JPEG("image/jpeg", "jpg", "JPEG", lossy = true),
    PNG("image/png", "png", "PNG", lossy = false),
    WEBP("image/webp", "webp", "WebP", lossy = true),
    AVIF("image/avif", "avif", "AVIF", lossy = true),
    /** At quality 100 JPEG XL is mathematically lossless. */
    JXL("image/jxl", "jxl", "JPEG XL", lossy = true),
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    /** Null exports at full resolution. */
    val maxLongEdge: Int? = null,
    /** Location is dropped by default so shared photos do not reveal where they were taken. */
    val keepLocation: Boolean = false,
    /** sRGB suits the web and most viewers; wider spaces keep more color for printing or wide-gamut screens. */
    val colorProfile: ColorProfile = ColorProfile.SRGB,
)

/** Encodes a rendered bitmap, carries over useful metadata and writes it to a user-chosen document. */
object ExportWriter {
    private val COPIED_TAGS = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
    )

    private val LOCATION_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )

    fun write(context: Context, bitmap: Bitmap, original: Uri, destination: Uri, options: ExportOptions) {
        if (options.format == ExportFormat.AVIF || options.format == ExportFormat.JXL) {
            val exif = runCatching { exifPayload(context, original, options) }.getOrNull()
            // AVIF names sRGB and Display P3 with CICP codes; Adobe RGB needs its ICC profile.
            val icc = if (options.format == ExportFormat.AVIF && options.colorProfile == ColorProfile.ADOBE_RGB) {
                iccProfile(ColorSpaces.of(options.colorProfile))
            } else {
                null
            }
            val data = ImageCodecs.encode(options.format, bitmap, options.quality, options.colorProfile, exif, icc)
            val out = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Cannot write $destination")
            out.use { it.write(data) }
            return
        }
        val tmp = File.createTempFile("export", ".${options.format.extension}", context.cacheDir)
        try {
            tmp.outputStream().use { out ->
                check(bitmap.compress(compressFormat(options.format), options.quality, out)) { "Encoding failed" }
            }
            runCatching { copyMetadata(context, original, tmp, options) }
            val out = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Cannot write $destination")
            out.use { stream -> tmp.inputStream().use { it.copyTo(stream) } }
        } finally {
            tmp.delete()
        }
    }

    private fun copyMetadata(context: Context, original: Uri, file: File, options: ExportOptions) {
        val source = context.contentResolver.openInputStream(original)?.use { ExifInterface(it) } ?: return
        val target = ExifInterface(file)
        val tags = if (options.keepLocation) COPIED_TAGS + LOCATION_TAGS else COPIED_TAGS
        for (tag in tags) source.getAttribute(tag)?.let { target.setAttribute(tag, it) }
        target.setAttribute(ExifInterface.TAG_SOFTWARE, "Photograin")
        // EXIF can only name sRGB; anything else is "uncalibrated" and described by the embedded ICC profile.
        target.setAttribute(
            ExifInterface.TAG_COLOR_SPACE,
            if (options.colorProfile == ColorProfile.SRGB) ExifInterface.COLOR_SPACE_S_RGB.toString()
            else ExifInterface.COLOR_SPACE_UNCALIBRATED.toString(),
        )
        // Pixels are already rotated, so viewers must not rotate them again.
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        target.saveAttributes()
    }

    /**
     * The EXIF block (TIFF form) the export would carry. ExifInterface cannot write AVIF or JPEG XL,
     * so the tags are written into a 1x1 JPEG and its APP1 segment is lifted out.
     */
    private fun exifPayload(context: Context, original: Uri, options: ExportOptions): ByteArray? {
        val tmp = File.createTempFile("exif", ".jpg", context.cacheDir)
        try {
            val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            tmp.outputStream().use { pixel.compress(Bitmap.CompressFormat.JPEG, 50, it) }
            pixel.recycle()
            copyMetadata(context, original, tmp, options)
            return jpegExif(tmp.readBytes())
        } finally {
            tmp.delete()
        }
    }

    /** The TIFF data of the first "Exif" APP1 segment of a JPEG file. */
    internal fun jpegExif(jpeg: ByteArray): ByteArray? {
        var i = 2
        while (i + 4 <= jpeg.size && jpeg[i] == 0xFF.toByte()) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break  // image data follows: no more metadata
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val start = i + 4
            val end = i + 2 + length
            if (end > jpeg.size) break
            if (marker == 0xE1 && end - start > 6 && String(jpeg, start, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                return jpeg.copyOfRange(start + 6, end)
            }
            i = end
        }
        return null
    }

    /** ICC profile bytes for [space], as Android embeds them: taken from a 1x1 PNG's iCCP chunk. */
    private fun iccProfile(space: ColorSpace): ByteArray? = runCatching {
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888, false, space)
        val png = ByteArrayOutputStream().also { pixel.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        pixel.recycle()
        var i = 8
        while (i + 8 <= png.size) {
            val length = ((png[i].toInt() and 0xFF) shl 24) or ((png[i + 1].toInt() and 0xFF) shl 16) or
                ((png[i + 2].toInt() and 0xFF) shl 8) or (png[i + 3].toInt() and 0xFF)
            if (String(png, i + 4, 4, Charsets.ISO_8859_1) == "iCCP") {
                // Profile name, a zero byte, the compression method (zlib), then the profile.
                var p = i + 8
                while (png[p] != 0.toByte()) p++
                val inflater = Inflater()
                inflater.setInput(png, p + 2, i + 8 + length - (p + 2))
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && inflater.needsInput()) break
                    out.write(buffer, 0, n)
                }
                inflater.end()
                return@runCatching out.toByteArray()
            }
            i += 12 + length
        }
        null
    }.getOrNull()

    private fun compressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        ExportFormat.AVIF, ExportFormat.JXL -> throw IllegalArgumentException("$format is encoded natively")
    }
}
