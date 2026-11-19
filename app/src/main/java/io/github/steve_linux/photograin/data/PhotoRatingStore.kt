// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import io.github.steve_linux.photograin.model.PhotoFlag
import io.github.steve_linux.photograin.model.PhotoRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rating and flags are shared by the gallery, editor and all versions of an original. */
internal class PhotoRatingStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("photo_ratings", Context.MODE_PRIVATE)
    private val blank = PhotoRating()
    private val ratings = MutableStateFlow(read())
    val state = ratings.asStateFlow()

    private fun read(): Map<String, PhotoRating> {
        val all = prefs.all
        return all.keys.asSequence()
            .filter { it.endsWith(":stars") || it.endsWith(":flag") }
            .map { it.substringBeforeLast(':') }.distinct()
            .map { key -> key to PhotoRating.fromStored(all["$key:stars"] as? Int, all["$key:flag"] as? Int) }
            .filter { it.second != blank }.toMap()
    }

    fun rate(uri: Uri, stars: Int) = update(uri) { it.withStars(stars) }

    fun toggleStars(uri: Uri, stars: Int) = update(uri) { it.toggleStars(stars) }

    fun flag(uri: Uri, flag: PhotoFlag) = update(uri) { it.copy(flag = flag) }

    @Synchronized
    private fun update(uri: Uri, change: (PhotoRating) -> PhotoRating) {
        val key = uri.toString()
        val old = ratings.value[key] ?: blank
        val next = change(old)
        if (old == next) return
        prefs.edit().apply {
            if (next.stars == 0) remove("$key:stars") else putInt("$key:stars", next.stars)
            if (next.flag == PhotoFlag.NONE) remove("$key:flag") else putInt("$key:flag", next.flag.stored)
        }.apply()
        ratings.value = if (next == blank) ratings.value - key else ratings.value + (key to next)
    }

    companion object {
        @Volatile private var instance: PhotoRatingStore? = null

        fun get(context: Context): PhotoRatingStore = instance ?: synchronized(this) {
            instance ?: PhotoRatingStore(context.applicationContext).also { instance = it }
        }
    }
}
