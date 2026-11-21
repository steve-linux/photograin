// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import io.github.steve_linux.photograin.model.Preset
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/** Compatible with presets.json. Serialize read/modify/write across library instances. */
internal class PresetStore(private val file: File) {
    fun load(): List<Preset> = synchronized(lock) { runCatching { read() }.getOrDefault(emptyList()) }
    fun save(preset: Preset): Preset = synchronized(lock) {
        val all = read()
        val saved = preset.copy(id = UUID.randomUUID().toString(), name = cleanName(preset.name), builtIn = null)
        write(all + saved)
        saved
    }
    fun rename(id: String, name: String) = synchronized(lock) {
        val clean = cleanName(name)
        val all = read()
        if (all.any { it.id == id }) write(all.map { if (it.id == id) it.copy(name = clean) else it })
    }
    fun duplicate(id: String, name: String): Preset? = synchronized(lock) {
        val source = read().firstOrNull { it.id == id } ?: return@synchronized null
        save(source.copy(name = name))
    }
    fun delete(id: String) = synchronized(lock) {
        val all = read()
        if (all.any { it.id == id }) write(all.filter { it.id != id })
    }
    private fun read(): List<Preset> = if (!file.exists()) emptyList()
        else json.decodeFromString(ListSerializer(Preset.serializer()), file.readText())

    private fun write(presets: List<Preset>) {
        val tmp = File.createTempFile("presets-", ".tmp", file.parentFile)
        try {
            tmp.writeText(json.encodeToString(ListSerializer(Preset.serializer()), presets))
            if (!tmp.renameTo(file)) throw IOException("Cannot replace preset library")
        } finally { tmp.delete() }
    }
    private fun cleanName(name: String): String = name.trim().take(40).also { require(it.isNotBlank()) }
    companion object {
        private val lock = Any()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
