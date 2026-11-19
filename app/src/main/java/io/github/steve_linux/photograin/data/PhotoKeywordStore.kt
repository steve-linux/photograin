// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keywords belong to the original photo URI and are shared by all its edit versions. */
internal class PhotoKeywordStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("photo_keywords", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(
        prefs.all.mapNotNull { (uri, value) ->
            val words = (value as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
            if (words.isEmpty()) null else uri to words
        }.toMap(),
    )
    val state = current.asStateFlow()

    @Synchronized
    fun set(uri: Uri, words: Set<String>) {
        val key = uri.toString()
        if (current.value[key].orEmpty() == words) return
        val editor = prefs.edit()
        if (words.isEmpty()) {
            editor.remove(key).apply()
            current.value = current.value - key
        } else {
            val saved = words.toSet()
            editor.putStringSet(key, saved).apply()
            current.value = current.value + (key to saved)
        }
    }

    companion object {
        @Volatile private var instance: PhotoKeywordStore? = null

        fun get(context: Context): PhotoKeywordStore = instance ?: synchronized(this) {
            instance ?: PhotoKeywordStore(context.applicationContext).also { instance = it }
        }
    }
}
