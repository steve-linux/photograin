// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SaveLocation {
    /** Pictures/Photograin, through MediaStore: no permission needed on Android 10 and later. */
    PICTURES,

    /** A folder the user picked once, kept with a persistable permission. */
    FOLDER,

    /** The system "save as" dialog every time. */
    ASK,
}

enum class FileNaming { ORIGINAL, DATE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val saveLocation: SaveLocation = SaveLocation.PICTURES,
    val folder: Uri? = null,
    val naming: FileNaming = FileNaming.ORIGINAL,
    /** Starting values of the export dialog, including whether to keep metadata and location. */
    val export: ExportOptions = ExportOptions(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

/** App-wide settings in SharedPreferences, shared by every screen through [state]. */
class SettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val value: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            saveLocation = enumOf(prefs.getString(KEY_LOCATION, null), d.saveLocation),
            folder = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse),
            naming = enumOf(prefs.getString(KEY_NAMING, null), d.naming),
            export = ExportOptions(
                format = enumOf(prefs.getString(KEY_FORMAT, null), d.export.format),
                quality = prefs.getInt(KEY_QUALITY, d.export.quality).coerceIn(50, 100),
                maxLongEdge = prefs.getInt(KEY_MAX_EDGE, 0).takeIf { it > 0 },
                keepLocation = prefs.getBoolean(KEY_LOCATION_TAGS, d.export.keepLocation),
                colorProfile = enumOf(prefs.getString(KEY_PROFILE, null), d.export.colorProfile),
                keepMetadata = prefs.getBoolean(KEY_METADATA, d.export.keepMetadata),
            ),
            theme = enumOf(prefs.getString(KEY_THEME, null), d.theme),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString(KEY_LOCATION, s.saveLocation.name)
            .putString(KEY_FOLDER, s.folder?.toString())
            .putString(KEY_NAMING, s.naming.name)
            .putString(KEY_FORMAT, s.export.format.name)
            .putInt(KEY_QUALITY, s.export.quality)
            .putInt(KEY_MAX_EDGE, s.export.maxLongEdge ?: 0)
            .putBoolean(KEY_LOCATION_TAGS, s.export.keepLocation)
            .putString(KEY_PROFILE, s.export.colorProfile.name)
            .putBoolean(KEY_METADATA, s.export.keepMetadata)
            .putString(KEY_THEME, s.theme.name)
            .apply()
    }

    companion object {
        private const val KEY_LOCATION = "save_location"
        private const val KEY_FOLDER = "save_folder"
        private const val KEY_NAMING = "file_naming"
        private const val KEY_FORMAT = "export_format"
        private const val KEY_QUALITY = "export_quality"
        private const val KEY_MAX_EDGE = "export_max_edge"
        private const val KEY_LOCATION_TAGS = "export_keep_location"
        private const val KEY_PROFILE = "export_color_profile"
        private const val KEY_METADATA = "export_keep_metadata"
        private const val KEY_THEME = "theme"

        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }

        private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
