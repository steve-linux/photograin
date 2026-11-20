// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CropGuide
import kotlinx.serialization.json.Json

/** Recently edited photos and the copied adjustments, kept in SharedPreferences. */
class Preferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("photograin", Context.MODE_PRIVATE)

    fun recents(): List<Uri> =
        prefs.getString(KEY_RECENTS, null)?.split('\n')?.filter { it.isNotBlank() }?.map(Uri::parse).orEmpty()

    internal fun knownPhotos(): Set<Uri> =
        prefs.getStringSet("known_photos", emptySet()).orEmpty().map(Uri::parse).toSet() + recents()

    @Synchronized
    internal fun rememberRestoredPhotos(uris: List<Uri>) {
        val known = prefs.getStringSet("known_photos", emptySet()).orEmpty() + uris.map(Uri::toString)
        val recent = (uris + recents()).distinct().take(MAX_RECENTS)
        prefs.edit().putStringSet("known_photos", known).putString(KEY_RECENTS, recent.joinToString("\n")).apply()
    }

    /** Moves [uri] to the top of the recents and keeps read access to it across restarts when possible. */
    @Synchronized
    fun addRecent(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val list = (listOf(uri) + recents().filter { it != uri }).take(MAX_RECENTS)
        val known = prefs.getStringSet("known_photos", emptySet()).orEmpty() + list.map(Uri::toString)
        prefs.edit().putString(KEY_RECENTS, list.joinToString("\n")).putStringSet("known_photos", known).apply()
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

    var copiedAutomaticMasks: List<io.github.steve_linux.photograin.model.LocalAdjustment>
        get() = prefs.getString("copied_automatic_masks", null)?.let {
            io.github.steve_linux.photograin.model.EditState.fromJson(it)?.masks
        }.orEmpty()
        set(value) {
            prefs.edit().putString("copied_automatic_masks", io.github.steve_linux.photograin.model.EditState(masks = value).toJson()).apply()
        }

    var cropGuide: CropGuide
        get() = prefs.getString("crop_guide", null).let { saved ->
            CropGuide.entries.firstOrNull { it.name == saved } ?: CropGuide.THIRDS
        }
        set(value) = prefs.edit().putString("crop_guide", value.name).apply()

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
