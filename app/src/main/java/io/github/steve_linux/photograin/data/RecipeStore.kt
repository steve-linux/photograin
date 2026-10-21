// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import io.github.steve_linux.photograin.model.EditState
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** One edit of a photo: [id] 1 is the photo's first edit, the others are duplicates of it. */
data class RecipeVersion(val id: Int, val name: String? = null)

/**
 * Keeps each photo's recipes in app storage, keyed by its URI. The original file is never
 * modified, and reopening the photo restores the edit exactly as it was left.
 *
 * A photo can have several versions, each its own recipe on the same original, like
 * Darktable's duplicates. Version 1 lives in the file older releases used, so their edits
 * open as version 1; the others, and the versions' names, sit in files next to it.
 */
class RecipeStore(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "recipes"))

    init {
        dir.mkdirs()
    }

    fun load(uri: Uri, version: Int = 1): EditState? = load(keyOf(uri), version)

    fun save(uri: Uri, state: EditState, version: Int = 1) = save(keyOf(uri), state, version)

    /** The photo's versions in order; always at least version 1. */
    fun versions(uri: Uri): List<RecipeVersion> = versions(keyOf(uri))

    /** Adds a version holding [state] and returns its id. */
    fun create(uri: Uri, state: EditState): Int = create(keyOf(uri), state)

    /** Removes [version]; removing version 1 moves the next one into its place. Returns the ids now in use. */
    fun delete(uri: Uri, version: Int): List<RecipeVersion> = delete(keyOf(uri), version)

    fun rename(uri: Uri, version: Int, name: String?) = rename(keyOf(uri), version, name)

    internal fun load(key: String, version: Int): EditState? {
        val file = recipeFile(key, version)
        if (!file.exists()) return null
        return EditState.fromJson(file.readText())
    }

    internal fun save(key: String, state: EditState, version: Int) {
        val file = recipeFile(key, version)
        // An untouched first version needs no file; a duplicate keeps its own so it still exists.
        if (version == 1 && state == EditState()) {
            file.delete()
            return
        }
        write(file, state.toJson())
    }

    internal fun versions(key: String): List<RecipeVersion> {
        val index = readIndex(key)
        return (listOf(1) + index.extra).map { RecipeVersion(it, index.names[it]) }
    }

    internal fun create(key: String, state: EditState): Int {
        val index = readIndex(key)
        val id = (index.extra.maxOrNull() ?: 1) + 1
        write(recipeFile(key, id), state.toJson())
        writeIndex(key, index.copy(extra = index.extra + id))
        return id
    }

    internal fun delete(key: String, version: Int): List<RecipeVersion> {
        val index = readIndex(key)
        if (version == 1) {
            val next = index.extra.firstOrNull() ?: run {
                recipeFile(key, 1).delete()
                writeIndex(key, VersionIndex())
                return versions(key)
            }
            recipeFile(key, 1).delete()
            recipeFile(key, next).renameTo(recipeFile(key, 1))
            val names = index.names - 1 - next + listOfNotNull(index.names[next]?.let { 1 to it })
            writeIndex(key, VersionIndex(index.extra - next, names))
        } else if (version in index.extra) {
            recipeFile(key, version).delete()
            writeIndex(key, VersionIndex(index.extra - version, index.names - version))
        }
        return versions(key)
    }

    internal fun rename(key: String, version: Int, name: String?) {
        val index = readIndex(key)
        val clean = name?.trim()?.takeIf { it.isNotEmpty() }
        writeIndex(key, index.copy(names = if (clean == null) index.names - version else index.names + (version to clean)))
    }

    private fun readIndex(key: String): VersionIndex {
        val file = File(dir, "$key.versions.json")
        if (!file.exists()) return VersionIndex()
        return runCatching { json.decodeFromString<VersionIndex>(file.readText()) }.getOrDefault(VersionIndex())
    }

    private fun writeIndex(key: String, index: VersionIndex) {
        val file = File(dir, "$key.versions.json")
        if (index == VersionIndex()) file.delete() else write(file, json.encodeToString(index))
    }

    private fun write(file: File, text: String) {
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    private fun recipeFile(key: String, version: Int) = File(dir, if (version == 1) "$key.json" else "$key.v$version.json")

    private fun keyOf(uri: Uri): String = keyOf(uri.toString())

    @Serializable
    private data class VersionIndex(val extra: List<Int> = emptyList(), val names: Map<Int, String> = emptyMap())

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun keyOf(uri: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
