// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import io.github.steve_linux.photograin.model.ImageLayer
import java.io.File
import java.util.UUID

internal class ImageLayerStore(private val context: Context) {
    private val directory = File(context.filesDir, "image-layers")

    fun importImage(uri: Uri): ImageLayer {
        val image = ImageLoader.load(context, uri, 1600)
        val original = image.preview
        val layer = ImageLayer(UUID.randomUUID().toString())
        val target = file(layer.id)
        try {
            val orientation = Matrix().apply {
                if (image.base.mirror) setScale(-1f, 1f)
                postRotate(image.base.quarterTurns * 90f)
            }
            val upright = Bitmap.createBitmap(original, 0, 0, original.width, original.height, orientation, true)
            try {
                directory.mkdirs()
                target.outputStream().use { check(upright.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                if (upright !== original) upright.recycle()
            }
            return layer
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            original.recycle()
        }
    }

    fun load(ids: Set<String>): Map<String, Bitmap> = buildMap {
        for (id in ids) {
            if (!id.matches(ID)) continue
            BitmapFactory.decodeFile(file(id).absolutePath)?.let { put(id, it) }
        }
    }

    fun discard(id: String) {
        if (id.matches(ID)) file(id).delete()
    }

    private fun file(id: String) = File(directory, "$id.png")

    private companion object {
        val ID = Regex("[0-9a-f-]{36}")
    }
}
