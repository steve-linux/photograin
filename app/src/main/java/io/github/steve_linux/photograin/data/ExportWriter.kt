// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

enum class ExportFormat(val mimeType: String, val extension: String, val lossy: Boolean) {
    JPEG("image/jpeg", "jpg", lossy = true),
    PNG("image/png", "png", lossy = false),
    WEBP("image/webp", "webp", lossy = true),
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    /** Null exports at full resolution. */
    val maxLongEdge: Int? = null,
    /** Location is dropped by default so shared photos do not reveal where they were taken. */
    val keepLocation: Boolean = false,
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
        // Pixels are already rotated, so viewers must not rotate them again.
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        target.saveAttributes()
    }

    private fun compressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }
}
