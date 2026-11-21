// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Local calibrations keyed by camera, lens and reported focal length. */
internal class LensProfiles(private val context: Context) {
    private val preferences = context.getSharedPreferences("lens_profiles", Context.MODE_PRIVATE)

    fun identify(uri: Uri): String? = runCatching {
        val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
            ?: return null
        fun tag(name: String) = exif.getAttribute(name).orEmpty().trim().lowercase(Locale.ROOT)
        val model = tag(ExifInterface.TAG_MODEL)
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val lens = tag(ExifInterface.TAG_LENS_MODEL)
        if (model.isEmpty() || !focal.isFinite() || focal <= 0.0) return null
        JSONArray().put(tag(ExifInterface.TAG_MAKE)).put(model).put(lens)
            .put(String.format(Locale.ROOT, "%.2f", focal))
            .put(exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0))
            .toString()
    }.getOrNull()

    fun get(key: String): Float? =
        local(key) ?: preferences.getFloat("catalog:$key", Float.NaN).takeIf { it in -1f..1f }

    fun local(key: String): Float? =
        preferences.getFloat(key, Float.NaN).takeIf { it in -1f..1f }

    fun importCatalog(uri: Uri) {
        val data = context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(bytes.size() + count <= 1_048_576)
                bytes.write(buffer, 0, count)
            }
            JSONObject(bytes.toString(Charsets.UTF_8.name()))
        } ?: throw IOException("Cannot read lens catalog")
        require(data.getInt("version") == 1)
        val entries = data.getJSONArray("profiles")
        require(entries.length() in 1..2000)
        val profiles = mutableMapOf<String, Float>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            fun normalized(name: String) = entry.optString(name).trim().lowercase(Locale.ROOT).also {
                require(it.length <= 256)
            }
            val model = normalized("cameraModel")
            val focal = entry.getDouble("focalMm")
            val equivalent = entry.optInt("focal35Mm", 0)
            val amount = entry.getDouble("distortion")
            require(model.isNotEmpty() && focal in 0.01..10000.0 && equivalent in 0..10000 && amount in -1.0..1.0)
            val key = JSONArray().put(normalized("cameraMake")).put(model).put(normalized("lensModel"))
                .put(String.format(Locale.ROOT, "%.2f", focal)).put(equivalent).toString()
            require(profiles.put("catalog:$key", amount.toFloat()) == null)
        }
        val update = preferences.edit()
        preferences.all.keys.filter { it.startsWith("catalog:") }.forEach { update.remove(it) }
        profiles.forEach { (key, value) -> update.putFloat(key, value) }
        check(update.commit())
    }

    fun save(key: String, amount: Float) {
        preferences.edit().putFloat(key, amount).apply()
    }

    fun delete(key: String) {
        preferences.edit().remove(key).apply()
    }
}
