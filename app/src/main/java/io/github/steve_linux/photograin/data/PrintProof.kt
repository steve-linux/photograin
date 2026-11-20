// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

internal object PrintProof {
    private const val MAX_PROFILE_BYTES = 16 * 1024 * 1024
    private val loaded by lazy { runCatching { System.loadLibrary("photograin_codec") }.isSuccess }

    data class Profile(val name: String, val bytes: ByteArray)

    fun read(context: Context, uri: Uri): Profile {
        val bytes = context.contentResolver.openInputStream(uri)?.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_PROFILE_BYTES) throw IOException("ICC profile exceeds limit")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("Cannot read ICC profile")
        if (bytes.size < 128 || bytes.decodeToString(36, 40) != "acsp") throw IOException("Invalid ICC profile")
        return Profile(ImageLoader.queryName(context, uri) ?: uri.lastPathSegment.orEmpty(), bytes)
    }

    fun preview(bitmap: Bitmap, profile: Profile, gamut: Boolean): Bitmap? {
        if (!loaded) return null
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = nativeProof(profile.bytes, pixels, gamut) ?: return null
        return Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
    }

    private external fun nativeProof(profile: ByteArray, pixels: IntArray, gamut: Boolean): IntArray?
}
