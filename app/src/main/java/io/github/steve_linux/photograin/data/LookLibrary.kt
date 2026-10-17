// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import io.github.steve_linux.photograin.model.CubeLut
import io.github.steve_linux.photograin.model.Preset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** An imported LUT: [id] is the SHA-256 of its file, so importing the same file twice is a no-op. */
@Serializable
data class LutEntry(val id: String, val name: String)

/**
 * Imported .cube LUTs and the user's presets, kept in app storage. LUTs are copied in, so they
 * keep working after the original file is moved or deleted.
 */
class LookLibrary(private val context: Context) {
    private val lutDir = File(context.filesDir, "luts").apply { mkdirs() }
    private val lutIndex = File(lutDir, "index.json")
    private val presetFile = File(context.filesDir, "presets.json")

    fun luts(): List<LutEntry> = readList(lutIndex, LutEntry.serializer())

    /** Copies and validates the .cube file at [uri]; throws [IOException] with a reason if it is not usable. */
    fun importLut(uri: Uri): LutEntry {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_LUT_BYTES) throw IOException("File too large")
            }
            out.toByteArray()
        } ?: throw IOException("Cannot open $uri")
        val lut = try {
            CubeLut.parse(bytes.decodeToString())
        } catch (e: IllegalArgumentException) {
            throw IOException(e.message, e)
        }
        val id = sha256(bytes)
        luts().firstOrNull { it.id == id }?.let { return it }
        File(lutDir, "$id.cube").writeBytes(bytes)
        val name = lut.title ?: ImageLoader.queryName(context, uri)?.substringBeforeLast('.') ?: "LUT"
        val entry = LutEntry(id, name)
        writeList(lutIndex, LutEntry.serializer(), luts() + entry)
        cache[id] = lut
        return entry
    }

    /** The parsed LUT, or null if it is missing (deleted, or a recipe from another device). */
    fun loadLut(id: String): CubeLut? = cache[id] ?: runCatching {
        CubeLut.parse(File(lutDir, "$id.cube").readText())
    }.getOrNull()?.also { cache[id] = it }

    fun deleteLut(id: String) {
        writeList(lutIndex, LutEntry.serializer(), luts().filter { it.id != id })
        File(lutDir, "$id.cube").delete()
        cache.remove(id)
    }

    fun userPresets(): List<Preset> = readList(presetFile, Preset.serializer())

    fun savePreset(preset: Preset): Preset {
        val saved = preset.copy(id = UUID.randomUUID().toString(), builtIn = null)
        writeList(presetFile, Preset.serializer(), userPresets() + saved)
        return saved
    }

    fun deletePreset(id: String) = writeList(presetFile, Preset.serializer(), userPresets().filter { it.id != id })

    private fun <T> readList(file: File, serializer: KSerializer<T>): List<T> =
        runCatching { json.decodeFromString(ListSerializer(serializer), file.readText()) }.getOrDefault(emptyList())

    private fun <T> writeList(file: File, serializer: KSerializer<T>, list: List<T>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(ListSerializer(serializer), list))
        tmp.renameTo(file)
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_LUT_BYTES = 16 * 1024 * 1024
        val json = Json { ignoreUnknownKeys = true }
        /** Parsed LUTs shared by every screen; a 33³ table is about 430 KB. */
        val cache = ConcurrentHashMap<String, CubeLut>()
    }
}
