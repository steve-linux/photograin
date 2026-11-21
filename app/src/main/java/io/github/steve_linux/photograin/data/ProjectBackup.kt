// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import io.github.steve_linux.photograin.model.EditState
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class RecipeOriginalMismatch : IOException("The recipe belongs to a different original")

internal class ProjectBackup(private val context: Context, private val library: LookLibrary) {
    private val recipes = RecipeStore(context)

    @Serializable
    internal data class Version(val name: String?, val recipe: String)

    @Serializable
    internal data class Manifest(
        val format: Int = 1,
        val versions: List<Version>,
        val originalSha256: String? = null,
    ) {
        fun validate(digest: String?) {
            if (digest == null) {
                require(format == 1 && originalSha256 == null && versions.size in 1..128)
            } else {
                require(format == 2 && versions.size == 1 && originalSha256?.matches(sha256Pattern) == true)
                if (originalSha256 != digest) throw RecipeOriginalMismatch()
            }
        }
    }

    fun export(photo: Uri, destination: Uri, includeOriginal: Boolean) {
        val versions = recipes.versions(photo).map {
            Version(it.name, (recipes.load(photo, it.id) ?: EditState()).toJson())
        }
        exportArchive(photo, destination, includeOriginal, Manifest(versions = versions), digest = null)
    }

    suspend fun exportRecipe(photo: Uri, destination: Uri, edit: EditState, name: String?) {
        val digest = photoDigest(context, photo) ?: throw IOException("Cannot read original")
        val manifest = Manifest(2, listOf(Version(name, edit.toJson())), digest)
        exportArchive(photo, destination, false, manifest, digest)
    }

    private fun exportArchive(photo: Uri, destination: Uri, includeOriginal: Boolean, manifest: Manifest, digest: String?) {
        manifest.validate(digest)
        val edits = manifest.versions.map { checkNotNull(EditState.fromJson(it.recipe)) }
        val assets = assetNames(edits)
        for (name in assets) check(File(context.filesDir, name).isFile) { "Missing project asset" }
        val originalName = if (includeOriginal) {
            val extension = ImageLoader.queryName(context, photo)?.substringAfterLast('.', "")
                ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) } ?: "img"
            "original.$extension"
        } else null
        val stream = context.contentResolver.openOutputStream(destination, "wt") ?: throw IOException("Cannot write project")
        ZipOutputStream(stream).use { zip ->
            fun add(name: String, input: InputStream) {
                input.use {
                    zip.putNextEntry(ZipEntry(name))
                    it.copyTo(zip)
                    zip.closeEntry()
                }
            }
            add("project.json", Json.encodeToString(Manifest.serializer(), manifest).byteInputStream())
            for (name in assets) add(name, File(context.filesDir, name).inputStream())
            if (originalName != null) {
                val source = context.contentResolver.openInputStream(photo) ?: throw IOException("Cannot read original")
                add(originalName, source)
            }
        }
    }

    /** Adds the imported edits as versions of the selected original, preserving existing edits. */
    fun restore(photo: Uri, archive: Uri): Int = restoreArchive(photo, archive, null)

    suspend fun restoreRecipe(photo: Uri, archive: Uri): Int {
        val digest = photoDigest(context, photo) ?: throw IOException("Cannot read original")
        return restoreArchive(photo, archive, digest)
    }

    private fun restoreArchive(photo: Uri, archive: Uri, digest: String?): Int {
        val staging = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "project").toFile()
        try {
            val source = context.contentResolver.openInputStream(archive) ?: throw IOException("Cannot read project")
            val files = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(source).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(files.size < 1024 && files.add(name))
                    require(name == "project.json" || assetPattern.matches(name) || (digest == null && originalPattern.matches(name)))
                    val file = File(staging, name)
                    if (originalPattern.matches(name)) {
                        total += consume(zip, null, 512L * 1024 * 1024 - total)
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output ->
                            val limit = 32L * 1024 * 1024
                            total += consume(zip, output, minOf(limit, 512L * 1024 * 1024 - total))
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = Json.decodeFromString(Manifest.serializer(), File(staging, "project.json").readText())
            manifest.validate(digest)
            val edits = manifest.versions.map { requireNotNull(EditState.fromJson(it.recipe)) }
            val assets = assetNames(edits)
            restoreAssets(staging, assets, edits)
            val created = mutableListOf<Int>()
            try {
                for ((index, edit) in edits.withIndex()) {
                    val id = recipes.create(photo, edit)
                    created += id
                    recipes.rename(photo, id, manifest.versions[index].name?.take(256))
                }
                return created.first()
            } catch (error: Exception) {
                for (id in created.asReversed()) recipes.delete(photo, id)
                throw error
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    internal fun restoreAssets(staging: File, assets: Set<String>, edits: List<EditState>) {
        require(assets.all(assetPattern::matches))
        for (name in assets) {
            val file = File(staging, name)
            require(file.isFile)
            if (name.endsWith(".cube")) {
                val id = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                require(name == "luts/$id.cube")
                io.github.steve_linux.photograin.model.CubeLut.parse(file.readText())
            } else {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outMimeType == "image/png" && bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600)
            }
        }
        for (name in assets) {
            if (name.endsWith(".cube")) {
                val file = File(staging, name)
                require(file.length() <= 16L * 1024 * 1024)
                val id = file.nameWithoutExtension
                val title = edits.firstOrNull { it.adjustments.look.lutId == id }?.adjustments?.look?.name
                library.importLutBytes(file.readBytes(), title)
                continue
            }
            val target = File(context.filesDir, name)
            target.parentFile?.mkdirs()
            if (target.exists()) {
                require(target.readBytes().contentEquals(File(staging, name).readBytes()))
            } else {
                val temporary = File.createTempFile("project-", ".tmp", target.parentFile)
                try {
                    File(staging, name).copyTo(temporary, overwrite = true)
                    check(temporary.renameTo(target))
                } finally {
                    temporary.delete()
                }
            }
        }
    }

    internal fun assetNames(edits: List<EditState>): Set<String> = buildSet {
        for (edit in edits) {
            require(edit.images.size <= 8)
            for (layer in edit.images) add("image-layers/${layer.id}.png")
            edit.adjustments.look.lutId?.let { add("luts/$it.cube") }
        }
        require(all(assetPattern::matches))
    }

    internal fun consume(input: InputStream, output: java.io.OutputStream?, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            require(total <= limit)
            output?.write(buffer, 0, count)
        }
    }

    internal companion object {
        private val sha256Pattern = Regex("[0-9a-f]{64}")
        val assetPattern = Regex("(image-layers/[0-9a-f-]{36}\\.png|luts/[0-9a-f]{64}\\.cube)")
        val originalPattern = Regex("original\\.[a-zA-Z0-9]{1,8}")
    }
}
