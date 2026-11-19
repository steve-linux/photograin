// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.exifinterface.media.ExifInterface
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun locatePhoto(context: Context, photo: Uri, track: Uri, offset: String): Pair<Double, Double>? {
    val exif = context.contentResolver.openInputStream(photo)?.use { ExifInterface(it) } ?: return null
    val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
    val zone = ZoneOffset.of(offset.trim())
    val time = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss")).toInstant(zone)
    val parser = Xml.newPullParser()
    var closest: Pair<Double, Double>? = null
    var distance = 121L
    context.contentResolver.openInputStream(track)?.use { input ->
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(input, null)
        var point: Pair<Double, Double>? = null
        var depth = -1
        var events = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            require(++events <= 1_000_000)
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.name == "trkpt") {
                    val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    point = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
                    depth = parser.depth
                } else if (parser.name == "time" && point != null && parser.depth == depth + 1) {
                    val sample = runCatching { Instant.parse(parser.nextText()) }.getOrNull()
                    if (sample != null) {
                        val delta = kotlin.math.abs(sample.epochSecond - time.epochSecond)
                        if (delta < distance) {
                            distance = delta
                            closest = point
                        }
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) {
                point = null
                depth = -1
            }
            parser.next()
        }
    }
    return closest
}
