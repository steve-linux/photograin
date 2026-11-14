// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.steve_linux.photograin.model.LensCorrection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Offline lookup against the bundled Lensfun calibration data. */
internal object LensProfiles {
    private var database: LensDatabase? = null

    fun read(context: Context, uri: Uri, raw: RawImage.Info? = null): LensCorrection? {
        val resolver = context.contentResolver
        val exif = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
        val info = raw ?: if (RawImage.isRaw(resolver.getType(uri), ImageLoader.queryName(context, uri))) {
            runCatching { RawImage.probe(context, uri) }.getOrNull()
        } else null
        fun tag(name: String, fallback: String?) =
            exif?.getAttribute(name)?.takeIf { it.isNotBlank() } ?: fallback.orEmpty()
        fun number(name: String, fallback: Double?) =
            exif?.getAttributeDouble(name, 0.0)?.takeIf { it.isFinite() && it > 0 } ?: fallback ?: 0.0
        val focal = number(ExifInterface.TAG_FOCAL_LENGTH, info?.focal)
        if (!focal.isFinite() || focal <= 0) return null
        return database(context).find(
            tag(ExifInterface.TAG_MAKE, info?.make),
            tag(ExifInterface.TAG_MODEL, info?.model),
            tag(ExifInterface.TAG_LENS_MODEL, info?.lens),
            focal,
            number(ExifInterface.TAG_F_NUMBER, info?.aperture),
            number(ExifInterface.TAG_SUBJECT_DISTANCE, null),
        )
    }

    @Synchronized
    private fun database(context: Context): LensDatabase {
        return database ?: context.assets.open("optics/lensfun.json").bufferedReader().use {
            Json.decodeFromString<LensDatabase>(it.readText())
        }.also { database = it }
    }
}

@Serializable
internal class LensDatabase(private val cameras: List<Camera>, private val lenses: List<Lens>) {
    @Serializable
    class Camera(val make: String, val model: String, val mount: String, val crop: Double)

    @Serializable
    class Lens(val names: List<String>, val mounts: List<String>, val crop: Double, val aspect: Double, val rows: List<Row>)

    @Serializable
    data class Row(val kind: String, val focal: Double, val aperture: Double, val distance: Double, val coeff: List<Double>)

    fun find(make: String, model: String, lens: String, focal: Double, aperture: Double, distance: Double): LensCorrection? {
        val maker = key(make)
        if (maker.isEmpty()) return null
        val cameraModel = cameraKey(make, model)
        val camera = cameras.filter { key(it.make) == maker && cameraKey(it.make, it.model) == cameraModel }
            .distinctBy { it.mount to it.crop }.singleOrNull() ?: return null
        val lensName = key(lens)
        val candidates = lenses.filter { camera.mount in it.mounts }
        val match = if (lensName.isNotEmpty()) {
            candidates.filter { item -> item.names.any { key(it) == lensName } }.singleOrNull()
        } else {
            // Dedicated fixed-lens mounts have one camera family and one profile.
            candidates.singleOrNull()?.takeIf { cameras.count { it.mount == camera.mount } == 1 }
        } ?: return null
        val calibrationScale = match.crop / camera.crop * hypot(match.aspect, 1.0)
        val distortion = atFocal(match.rows.filter { it.kind == "distortion" }, focal)
        val tca = atFocal(match.rows.filter { it.kind == "tca" }, focal)
        val vignette = if (aperture.isFinite() && aperture > 0) {
            val rows = match.rows.filter { it.kind == "vignetting" }
            val atEachFocal = rows.groupBy { it.focal }.values.map { group ->
                val byAperture = group.groupBy { it.aperture }.values.map { samples ->
                    // With no focus distance, choose the farthest calibration.
                    if (distance.isFinite() && distance > 0) samples.minBy { abs(1 / it.distance - 1 / distance) }
                    else samples.maxBy { it.distance }
                }.sortedBy { it.aperture }
                byAperture.first().copy(coeff = interpolate(byAperture, aperture, Row::aperture))
            }
            atFocal(atEachFocal, focal)
        } else null
        if (distortion == null && tca == null && vignette == null) return null
        return LensCorrection(
            match.names.first(), calibrationScale, match.crop / camera.crop,
            distortion ?: listOf(1.0, 0.0, 0.0, 0.0, 0.0),
            tca?.take(3) ?: listOf(1.0, 0.0, 0.0),
            tca?.drop(3) ?: listOf(1.0, 0.0, 0.0),
            vignette ?: listOf(0.0, 0.0, 0.0),
        )
    }

    private fun atFocal(rows: List<Row>, focal: Double): List<Double>? {
        if (rows.isEmpty()) return null
        // Lensfun often supplies only one focal sample, including for zooms. Keep the nearest
        // calibration outside the measured range and interpolate inside it.
        return interpolate(rows.sortedBy { it.focal }, focal, Row::focal)
    }

    private fun interpolate(rows: List<Row>, value: Double, coordinate: (Row) -> Double): List<Double> {
        val low = rows.lastOrNull { coordinate(it) <= value } ?: return rows.first().coeff
        val high = rows.firstOrNull { coordinate(it) > value } ?: return low.coeff
        val fraction = (value - coordinate(low)) / (coordinate(high) - coordinate(low))
        return low.coeff.zip(high.coeff) { a, b -> a + (b - a) * fraction }
    }

    private fun cameraKey(make: String, model: String): String =
        key(model.trim().let { if (it.startsWith(make.trim() + " ", ignoreCase = true)) it.drop(make.trim().length) else it })

    private fun key(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
