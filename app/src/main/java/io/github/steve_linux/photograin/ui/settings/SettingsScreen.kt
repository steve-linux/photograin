// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ExportDestination
import io.github.steve_linux.photograin.data.FileNaming
import io.github.steve_linux.photograin.data.SaveLocation
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.data.ThemeMode
import io.github.steve_linux.photograin.ui.editor.ExportOptionsEditor

private const val SOURCE_URL = "https://github.com/steve-linux/photograin"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Open source code bundled in the app, with its license. */
private val LIBRARIES = listOf(
    "LibRaw" to "LGPL-2.1",
    "libjxl" to "BSD-3-Clause",
    "Highway" to "Apache-2.0 / BSD-3-Clause",
    "Brotli" to "MIT",
    "skcms" to "BSD-3-Clause",
    "libavif" to "BSD-2-Clause",
    "libaom" to "BSD-2-Clause + AOM Patent License 1.0",
    "ncnn" to "BSD-3-Clause",
    "glslang" to "BSD-3-Clause / BSD-2-Clause / MIT / Apache-2.0",
    "Real-ESRGAN (realesr-general-x4v3)" to "BSD-3-Clause",
    "U²-Net (u2netp)" to "Apache-2.0",
    "MI-GAN (Picsart AI Research)" to "MIT",
    "AndroidX, Jetpack Compose, Material" to "Apache-2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache-2.0",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit, onShowTips: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    var showLibraries by rememberSaveable { mutableStateOf(false) }
    var tipsReset by rememberSaveable { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(tree, flags) }
            settings.folder?.takeIf { it != tree }?.let { old ->
                runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
            }
            store.update { it.copy(saveLocation = SaveLocation.FOLDER, folder = tree) }
        }
    }

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Section(R.string.settings_save)
            val picturesAvailable = Build.VERSION.SDK_INT >= 29
            Choice(
                title = stringResource(R.string.settings_save_pictures, ExportDestination.FOLDER_NAME),
                subtitle = stringResource(if (picturesAvailable) R.string.settings_save_pictures_jxl else R.string.settings_save_pictures_old),
                selected = settings.saveLocation == SaveLocation.PICTURES,
                enabled = picturesAvailable,
                onClick = { store.update { it.copy(saveLocation = SaveLocation.PICTURES) } },
            )
            Choice(
                title = stringResource(R.string.settings_save_folder),
                subtitle = settings.folder?.let(::folderLabel) ?: stringResource(R.string.settings_save_folder_none),
                selected = settings.saveLocation == SaveLocation.FOLDER,
                onClick = {
                    if (settings.folder == null) folderPicker.launch(null)
                    else store.update { it.copy(saveLocation = SaveLocation.FOLDER) }
                },
            )
            if (settings.saveLocation == SaveLocation.FOLDER) {
                TextButton(onClick = { folderPicker.launch(settings.folder) }, Modifier.padding(start = 56.dp)) {
                    Text(stringResource(R.string.settings_save_folder_change))
                }
            }
            Choice(
                title = stringResource(R.string.settings_save_ask),
                selected = settings.saveLocation == SaveLocation.ASK,
                onClick = { store.update { it.copy(saveLocation = SaveLocation.ASK) } },
            )

            Section(R.string.settings_naming)
            Choice(
                title = stringResource(R.string.settings_naming_original),
                subtitle = ExportDestination.fileName("IMG_1234.jpg", FileNaming.ORIGINAL, settings.export.format),
                selected = settings.naming == FileNaming.ORIGINAL,
                onClick = { store.update { it.copy(naming = FileNaming.ORIGINAL) } },
            )
            Choice(
                title = stringResource(R.string.settings_naming_date),
                subtitle = ExportDestination.fileName(null, FileNaming.DATE, settings.export.format),
                selected = settings.naming == FileNaming.DATE,
                onClick = { store.update { it.copy(naming = FileNaming.DATE) } },
            )

            Section(R.string.settings_export)
            Text(
                stringResource(R.string.settings_export_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            ExportOptionsEditor(
                settings.export,
                onChange = { options -> store.update { it.copy(export = options) } },
                Modifier.padding(horizontal = 16.dp),
                allowUpscale = false,
            )

            Section(R.string.settings_appearance)
            for ((mode, label) in listOf(
                ThemeMode.SYSTEM to R.string.settings_theme_system,
                ThemeMode.LIGHT to R.string.settings_theme_light,
                ThemeMode.DARK to R.string.settings_theme_dark,
            )) {
                Choice(
                    title = stringResource(label),
                    selected = settings.theme == mode,
                    onClick = { store.update { it.copy(theme = mode) } },
                )
            }
            if (Build.VERSION.SDK_INT >= 33) {
                InfoRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_hint),
                    onClick = {
                        open(context, Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    },
                )
            }
            InfoRow(
                title = stringResource(R.string.settings_show_tips),
                subtitle = if (tipsReset) stringResource(R.string.settings_show_tips_done) else null,
                onClick = {
                    onShowTips()
                    tipsReset = true
                },
            )

            Section(R.string.settings_about)
            InfoRow(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.settings_version, versionName(context)))
            InfoRow(
                title = stringResource(R.string.settings_license),
                subtitle = stringResource(R.string.settings_license_value),
                onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(LICENSE_URL))) },
            )
            InfoRow(
                title = stringResource(R.string.settings_source),
                subtitle = SOURCE_URL.removePrefix("https://"),
                onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) },
            )
            InfoRow(title = stringResource(R.string.settings_libraries), onClick = { showLibraries = true })
        }
    }

    if (showLibraries) {
        AlertDialog(
            onDismissRequest = { showLibraries = false },
            title = { Text(stringResource(R.string.settings_libraries)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((name, license) in LIBRARIES) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraries = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider(Modifier.padding(top = 16.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Choice(title: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 16.dp)) {
            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Pictures/Mine" from a tree such as primary:Pictures/Mine. */
private fun folderLabel(tree: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "/" } }.getOrDefault(tree.toString())

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
