// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.LookLibrary
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.data.SubjectSegmenter
import io.github.steve_linux.photograin.model.ColorProfile
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.render.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Small renders of recent photos with their saved recipe applied, for the Home comparison.
 * Keyed by the recipe too, so a photo edited again is rendered afresh.
 */
internal object EditedThumbnails {
    private const val TAG = "EditedThumbnails"
    private val cache = LruCache<String, Bitmap>(24)

    // One offscreen GL context at a time.
    private val gpu = Mutex()

    /** The edited photo, or null when it has no recipe or cannot be rendered. */
    suspend fun load(context: Context, uri: Uri, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        val edit = RecipeStore(context).load(uri) ?: return@withContext null
        if (edit == EditState()) return@withContext null
        val key = "$uri\n${edit.toJson().hashCode()}\n$size"
        cache.get(key)?.let { return@withContext it }
        gpu.withLock {
            try {
                val base = ImageLoader.baseOrientation(context, uri)
                val lut = edit.adjustments.look.lutId?.let { LookLibrary(context).loadLut(it) }
                RegionSource.open(context, uri).use {
                    val subject = SubjectSegmenter.forEdit(context, it, base, edit)
                    Exporter.render(it, base, edit, lut, size, ColorProfile.SRGB, subject = subject) {}
                }.also { cache.put(key, it) }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot render $uri", e)
                null
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Out of memory on $uri", e)
                null
            }
        }
    }
}
