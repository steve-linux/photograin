// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import io.github.steve_linux.photograin.model.ModelPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The model packs imported into the app's private storage (see [ModelPack]). A file is taken
 * only if its checksum is that of a known pack, so what runs is exactly what the project
 * published.
 */
object ModelPacks {
    /** Thrown when the file chosen is not one of [ModelPack.all]. */
    class UnknownPack : IOException("Not a Photograin model pack")

    private val _installed = MutableStateFlow<Set<String>?>(null)

    /** The ids of the packs installed, once [installed] has looked. */
    val state: StateFlow<Set<String>?> = _installed.asStateFlow()

    private fun root(context: Context) = File(context.applicationContext.filesDir, "models")

    /** The folder of [pack]'s files. */
    fun dir(context: Context, pack: ModelPack) = File(root(context), pack.id)

    /** The file [name] of [pack], or null if the pack is not installed. */
    fun file(context: Context, pack: ModelPack, name: String): File? =
        File(dir(context, pack), name).takeIf { isInstalled(context, pack) }

    fun isInstalled(context: Context, pack: ModelPack): Boolean =
        pack.files.all { File(dir(context, pack), it).isFile }

    /** The ids of the packs installed. */
    fun installed(context: Context): Set<String> =
        ModelPack.all.filter { isInstalled(context, it) }.map { it.id }.toSet().also { _installed.value = it }

    /**
     * Imports the file at [uri]: checks its checksum against the known packs while copying it,
     * then unpacks it. Returns the pack; throws [UnknownPack] for any other file. Blocking:
     * call from a background thread.
     */
    fun install(context: Context, uri: Uri): ModelPack {
        val root = root(context).apply { mkdirs() }
        val download = File.createTempFile("pack", ".zip", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
            DigestInputStream(input, digest).use { inp -> download.outputStream().use { inp.copyTo(it) } }
            val pack = ModelPack.bySha256(ModelPack.hex(digest.digest())) ?: throw UnknownPack()
            // Unpacked next to the pack's folder and swapped in, so a failure leaves no half pack.
            val staging = File(root, "${pack.id}.new").apply { deleteRecursively(); mkdirs() }
            ZipInputStream(download.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || entry.name !in pack.files) continue
                    File(staging, entry.name).outputStream().use { zip.copyTo(it) }
                }
            }
            if (!pack.files.all { File(staging, it).isFile }) throw IOException("The pack is incomplete")
            val dir = dir(context, pack)
            dir.deleteRecursively()
            if (!staging.renameTo(dir)) throw IOException("Cannot install the pack")
            installed(context)
            return pack
        } finally {
            download.delete()
        }
    }

    /** Deletes [pack]'s files. */
    fun remove(context: Context, pack: ModelPack) {
        dir(context, pack).deleteRecursively()
        installed(context)
    }
}
