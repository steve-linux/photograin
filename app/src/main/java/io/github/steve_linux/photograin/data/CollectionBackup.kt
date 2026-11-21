// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.SmartAlbum
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class CollectionBackup(private val context: Context) {
    private val preferences = Preferences(context)
    private val recipes = RecipeStore(context)
    private val library = LookLibrary(context)
    private val project = ProjectBackup(context, library)
    private val albums = context.getSharedPreferences("photo_albums", Context.MODE_PRIVATE)
    private val profiles = context.getSharedPreferences("export_profiles", Context.MODE_PRIVATE)
    private val keywords = PhotoKeywordStore.get(context)
    private val ratings = PhotoRatingStore.get(context)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Version(val name: String?, val edit: EditState)

    @Serializable
    private data class Photo(
        val uri: String,
        val name: String?,
        val digest: String?,
        val versions: List<Version>,
        val keywords: Set<String>,
        val stars: Int,
        val flag: Int,
    )

    @Serializable
    private data class Manifest(
        val format: Int = 1,
        val photos: List<Photo>,
        val albums: Map<String, Set<String>>,
        val smartAlbums: Map<String, SmartAlbum>,
        val presets: List<Preset>,
        val exportProfiles: Map<String, ExportOptions>,
    )

    data class RestoreResult(val restored: Int, val missing: List<String>)

    suspend fun export(destination: Uri) {
        val manual = names("names").associateWith { albums.getStringSet("album:$it", emptySet()).orEmpty() }
        val known = (preferences.knownPhotos().map(Uri::toString) + manual.values.flatten() +
            keywords.state.value.keys + ratings.state.value.keys).distinct()
        require(known.size <= MAX_PHOTOS)
        val photos = known.filter { Uri.parse(it).scheme == "content" }.map { key ->
            currentCoroutineContext().ensureActive()
            val uri = Uri.parse(key)
            val versions = recipes.versions(uri).map { Version(it.name, recipes.load(uri, it.id) ?: EditState()) }
            val rating = ratings.state.value[key] ?: PhotoRating()
            Photo(key, ImageLoader.queryName(context, uri), photoDigest(context, uri), versions, keywords.state.value[key].orEmpty(),
                rating.stars, rating.flag.stored)
        }
        val smart = names("smart_names").mapNotNull { name ->
            albums.getString("smart:$name", null)?.let { raw -> name to json.decodeFromString<SmartAlbum>(raw) }
        }.toMap()
        val presetList = library.userPresets()
        val exportProfiles = profiles.all.mapNotNull { (name, value) ->
            if (value is String) runCatching { name to json.decodeFromString<ExportOptions>(value) }.getOrNull() else null
        }.toMap()
        val manifest = json.encodeToString(Manifest(photos = photos, albums = manual, smartAlbums = smart,
            presets = presetList, exportProfiles = exportProfiles)).encodeToByteArray()
        require(manifest.size <= MAX_MANIFEST)
        val assets = assets(photos, presetList)
        val files = assets.map { name -> name to File(context.filesDir, name).also { require(it.isFile) } }
        require(files.size <= MAX_ASSETS && files.all { it.second.length() <= MAX_ASSET } &&
            files.sumOf { it.second.length() } <= MAX_TOTAL)
        val out = context.contentResolver.openOutputStream(destination, "wt") ?: throw IOException("Cannot write backup")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            for ((name, source) in files) {
                currentCoroutineContext().ensureActive()
                zip.putNextEntry(ZipEntry(name))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun restore(source: Uri, originals: List<Uri>): RestoreResult {
        val staging = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "collection").toFile()
        try {
            unpack(source, staging)
            val manifest = json.decodeFromString<Manifest>(File(staging, MANIFEST).readText())
            require(manifest.format == 1 && manifest.photos.size <= MAX_PHOTOS)
            require(manifest.photos.all { it.versions.size in 1..128 && Uri.parse(it.uri).scheme == "content" })
            require(manifest.photos.map { it.uri }.toSet().size == manifest.photos.size)
            val candidates = (originals + preferences.knownPhotos()).distinct()
            val hashes = mutableMapOf<Uri, String?>()
            val availableByHash = candidates.mapNotNull { uri ->
                currentCoroutineContext().ensureActive()
                val hash = photoDigest(context, uri)
                hashes[uri] = hash
                hash?.let { it to uri }
            }.groupBy({ it.first }, { it.second })
            val explicit = originals.toSet()
            val selectedByHash = availableByHash.mapValues { (_, uris) -> uris.filter { it in explicit } }
            val targets = manifest.photos.associate { photo ->
                currentCoroutineContext().ensureActive()
                val original = Uri.parse(photo.uri)
                val available = if (hashes.containsKey(original)) hashes[original] else photoDigest(context, original)
                // A replacement is accepted only when its bytes match the backed-up source.
                photo.uri to when {
                    available != null && (photo.digest == null || available == photo.digest) -> original
                    photo.digest != null -> selectedByHash[photo.digest]?.takeIf { it.isNotEmpty() }?.singleOrNull()
                        ?: availableByHash[photo.digest]?.singleOrNull()
                    else -> null
                }
            }
            val edits = manifest.photos.flatMap { it.versions.map(Version::edit) }
            project.restoreAssets(staging, assets(manifest.photos, manifest.presets), edits)
            library.restorePresets(manifest.presets)
            val profilesEdit = profiles.edit()
            val knownProfiles = profiles.all.toMutableMap()
            for ((name, options) in manifest.exportProfiles) {
                val encoded = json.encodeToString(options)
                val target = restoreName(name, knownProfiles.keys) { knownProfiles[it] == encoded }
                knownProfiles[target] = encoded
                profilesEdit.putString(target, encoded)
            }
            profilesEdit.apply()
            for (photo in manifest.photos) {
                val uri = targets[photo.uri] ?: continue
                currentCoroutineContext().ensureActive()
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val existing = recipes.versions(uri).map { Version(it.name, recipes.load(uri, it.id) ?: EditState()) }.toMutableSet()
                for ((index, version) in photo.versions.withIndex()) {
                    if (!existing.add(version)) continue
                    val id = if (index == 0 && recipes.versions(uri).size == 1 &&
                        recipes.load(uri) == null && recipes.versions(uri).first().name == null) {
                        recipes.save(uri, version.edit)
                        1
                    } else recipes.create(uri, version.edit)
                    recipes.rename(uri, id, version.name?.take(256))
                }
                val previous = ratings.state.value[uri.toString()]
                if (previous == null) {
                    ratings.rate(uri, photo.stars)
                    ratings.flag(uri, PhotoFlag.entries.firstOrNull { it.stored == photo.flag } ?: PhotoFlag.NONE)
                }
                keywords.set(uri, keywords.state.value[uri.toString()].orEmpty() + photo.keywords)
            }
            preferences.rememberRestoredPhotos(targets.values.filterNotNull().distinct())
            val manual = names("names").toMutableSet()
            val smart = names("smart_names").toMutableSet()
            val albumEdit = albums.edit()
            for ((name, sourceMembers) in manifest.albums) {
                val target = restoreName(name, smart) { false }
                val mapped = sourceMembers.mapNotNull { targets[it]?.toString() }.toSet()
                val merged = albums.getStringSet("album:$target", emptySet()).orEmpty() + mapped
                albumEdit.putStringSet("album:$target", merged)
                manual.add(target)
            }
            for ((name, rule) in manifest.smartAlbums) {
                val encoded = json.encodeToString(rule)
                val target = restoreName(name, smart + manual) { it in smart && albums.getString("smart:$it", null) == encoded }
                albumEdit.putString("smart:$target", encoded)
                smart.add(target)
            }
            albumEdit.putStringSet("names", manual).putStringSet("smart_names", smart).apply()
            return RestoreResult(targets.values.count { it != null },
                manifest.photos.filter { targets[it.uri] == null }.map { it.name ?: Uri.parse(it.uri).lastPathSegment.orEmpty() })
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun names(key: String) = albums.getStringSet(key, emptySet()).orEmpty()

    private fun restoreName(name: String, existing: Set<String>, same: (String) -> Boolean): String {
        val clean = name.trim().take(64).ifBlank { "Photograin" }
        var candidate = clean
        var suffix = 2
        while (candidate in existing && !same(candidate)) candidate = "$clean (${suffix++})"
        return candidate
    }

    private fun assets(photos: List<Photo>, presets: List<Preset>): Set<String> =
        project.assetNames(photos.flatMap { list -> list.versions.map(Version::edit) } + presets.map { EditState(adjustments = it.adjustments) })

    private suspend fun unpack(source: Uri, staging: File) {
        val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot open backup")
        val seen = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory && (name == MANIFEST || ProjectBackup.assetPattern.matches(name)))
                require(seen.size <= MAX_ASSETS && seen.add(name))
                val target = File(staging, name)
                target.parentFile?.mkdirs()
                target.outputStream().use { output ->
                    val limit = if (name == MANIFEST) MAX_MANIFEST.toLong() else MAX_ASSET
                    total += project.consume(zip, output, minOf(limit, MAX_TOTAL - total))
                }
            }
        }
    }

    private companion object {
        const val MANIFEST = "collection.json"
        const val MAX_PHOTOS = 10000
        const val MAX_ASSETS = 4096
        const val MAX_MANIFEST = 32 * 1024 * 1024
        const val MAX_ASSET = 32L * 1024 * 1024
        const val MAX_TOTAL = 512L * 1024 * 1024
    }
}
