// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import io.github.steve_linux.photograin.model.EditState
import java.io.File
import java.security.MessageDigest

/**
 * Keeps each photo's recipe in app storage, keyed by its URI. The original file is never
 * modified, and reopening the photo restores the edit exactly as it was left.
 */
class RecipeStore(context: Context) {
    private val dir = File(context.filesDir, "recipes").apply { mkdirs() }

    fun load(uri: Uri): EditState? {
        val file = fileFor(uri)
        if (!file.exists()) return null
        return EditState.fromJson(file.readText())
    }

    fun save(uri: Uri, state: EditState) {
        val file = fileFor(uri)
        if (state == EditState()) {
            file.delete()
            return
        }
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(state.toJson())
        tmp.renameTo(file)
    }

    private fun fileFor(uri: Uri): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}
