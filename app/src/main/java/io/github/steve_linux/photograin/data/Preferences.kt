// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.steve_linux.photograin.model.Adjustments
import kotlinx.serialization.json.Json

/** Recently edited photos and the copied adjustments, kept in SharedPreferences. */
class Preferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("photograin", Context.MODE_PRIVATE)

    fun recents(): List<Uri> =
        prefs.getString(KEY_RECENTS, null)?.split('\n')?.filter { it.isNotBlank() }?.map(Uri::parse).orEmpty()

    /** Moves [uri] to the top of the recents and keeps read access to it across restarts when possible. */
    fun addRecent(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val list = (listOf(uri) + recents().filter { it != uri }).take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, list.joinToString("\n")).apply()
    }

    fun removeRecent(uri: Uri) {
        prefs.edit().putString(KEY_RECENTS, recents().filter { it != uri }.joinToString("\n")).apply()
    }

    var copiedAdjustments: Adjustments?
        get() = prefs.getString(KEY_COPIED, null)?.let {
            runCatching { json.decodeFromString(Adjustments.serializer(), it) }.getOrNull()
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_COPIED) else putString(KEY_COPIED, json.encodeToString(Adjustments.serializer(), value))
            }.apply()
        }

    /** Whether the home screen tip about saving presets was closed. */
    var presetTipDismissed: Boolean
        get() = prefs.getBoolean(KEY_PRESET_TIP, false)
        set(value) = prefs.edit().putBoolean(KEY_PRESET_TIP, value).apply()

    private companion object {
        const val KEY_RECENTS = "recents"
        const val KEY_COPIED = "copied_adjustments"
        const val KEY_PRESET_TIP = "preset_tip_dismissed"
        const val MAX_RECENTS = 24
        val json = Json { ignoreUnknownKeys = true }
    }
}
