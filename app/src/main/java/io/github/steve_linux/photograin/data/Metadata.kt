// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import io.github.steve_linux.photograin.R
import java.util.Locale
import kotlin.math.roundToInt

/** Kinds of EXIF data, each kept or dropped as a whole when exporting. */
enum class MetadataGroup(@param:StringRes val label: Int, val tags: List<String>) {
    DATE(
        R.string.metadata_date,
        listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
        ),
    ),
    CAMERA(
        R.string.metadata_camera,
        listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
        ),
    ),
    SETTINGS(
        R.string.metadata_settings,
        listOf(
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_FLASH,
        ),
    ),
    AUTHOR(R.string.metadata_author, listOf(ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT)),
    LOCATION(
        R.string.metadata_location,
        listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
        ),
    ),
}

/** One line of the photo's information: what it is and its value, ready to show. */
data class MetadataEntry(val group: MetadataGroup, @param:StringRes val label: Int, val value: String)

object Metadata {
    /** The EXIF data of the photo at [uri] worth showing, grouped as [MetadataGroup]; empty if none. */
    fun read(context: Context, uri: Uri): List<MetadataEntry> {
        val exif = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<MetadataEntry>()
        fun add(group: MetadataGroup, label: Int, value: String?) {
            if (!value.isNullOrBlank()) out += MetadataEntry(group, label, value.trim())
        }
        add(MetadataGroup.DATE, R.string.metadata_taken, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let(::formatDate))
        val camera = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
            .map(String::trim).distinct()
        // Many models already start with the maker's name.
        add(MetadataGroup.CAMERA, R.string.metadata_camera_model, if (camera.size == 2 && camera[1].startsWith(camera[0], ignoreCase = true)) camera[1] else camera.joinToString(" "))
        add(MetadataGroup.CAMERA, R.string.metadata_lens, exif.getAttribute(ExifInterface.TAG_LENS_MODEL))
        add(MetadataGroup.SETTINGS, R.string.metadata_aperture, exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { "f/" + trim(it) })
        add(MetadataGroup.SETTINGS, R.string.metadata_shutter, exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let(::formatShutter))
        add(MetadataGroup.SETTINGS, R.string.metadata_iso, exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        add(
            MetadataGroup.SETTINGS,
            R.string.metadata_focal,
            focal.takeIf { it > 0 }?.let { "${trim(it)} mm" + if (focal35 > 0) " (${focal35} mm eq.)" else "" },
        )
        add(MetadataGroup.AUTHOR, R.string.metadata_artist, exif.getAttribute(ExifInterface.TAG_ARTIST))
        add(MetadataGroup.AUTHOR, R.string.metadata_copyright, exif.getAttribute(ExifInterface.TAG_COPYRIGHT))
        exif.latLong?.let { (lat, lon) ->
            add(MetadataGroup.LOCATION, R.string.metadata_coordinates, String.format(Locale.ROOT, "%.5f, %.5f", lat, lon))
        }
        exif.getAltitude(Double.NaN).takeIf { !it.isNaN() }?.let {
            add(MetadataGroup.LOCATION, R.string.metadata_altitude, "${it.roundToInt()} m")
        }
        return out
    }

    /** "2023:06:19 19:29:16" as "2023-06-19 19:29". */
    private fun formatDate(exif: String): String =
        if (exif.length >= 16) exif.substring(0, 10).replace(':', '-') + " " + exif.substring(11, 16) else exif

    private fun formatShutter(seconds: Double): String =
        if (seconds >= 0.3) "${trim(seconds)} s" else "1/${(1 / seconds).roundToInt()} s"

    private fun trim(v: Double): String =
        if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(Locale.ROOT, "%.1f", v)
}
