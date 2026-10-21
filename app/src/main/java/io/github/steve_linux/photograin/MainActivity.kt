// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.data.RawImage
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.ui.batch.BatchScreen
import io.github.steve_linux.photograin.ui.batch.BatchViewModel
import io.github.steve_linux.photograin.ui.editor.EditorScreen
import io.github.steve_linux.photograin.ui.editor.EditorViewModel
import io.github.steve_linux.photograin.ui.editor.QuickAction
import io.github.steve_linux.photograin.ui.guide.GuideScreen
import io.github.steve_linux.photograin.ui.home.HomeScreen
import io.github.steve_linux.photograin.ui.settings.SettingsScreen
import io.github.steve_linux.photograin.ui.theme.PhotograinTheme

class MainActivity : ComponentActivity() {
    private val editor: EditorViewModel by viewModels()
    private val batch: BatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val prefs = Preferences(applicationContext)

        val settings = SettingsStore.get(applicationContext)

        setContent {
            val appSettings by settings.state.collectAsStateWithLifecycle()
            PhotograinTheme(appSettings.theme) {
                val state by editor.state.collectAsStateWithLifecycle()
                val batchState by batch.state.collectAsStateWithLifecycle()
                var recents by remember { mutableStateOf(prefs.recents()) }
                var showTip by remember { mutableStateOf(!prefs.presetTipDismissed) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showGuide by rememberSaveable { mutableStateOf(false) }
                var guideFocus by rememberSaveable { mutableStateOf<Int?>(null) }
                // A quick action picked on the home screen waits here for its photo.
                var pickedAction by rememberSaveable { mutableStateOf<QuickAction?>(null) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { editor.open(it, action = pickedAction) }
                    pickedAction = null
                }
                val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isNotEmpty()) batch.start(uris)
                }
                if (showGuide) {
                    GuideScreen(onClose = { showGuide = false }, focus = guideFocus)
                } else if (showSettings) {
                    SettingsScreen(
                        onClose = { showSettings = false },
                        onShowTips = {
                            prefs.presetTipDismissed = false
                            showTip = true
                        },
                    )
                } else if (batchState.items.isNotEmpty()) {
                    BatchScreen(batch, onClose = {
                        batch.clear()
                        recents = prefs.recents()
                    })
                } else if (state.image != null || state.loading) {
                    EditorScreen(
                        editor,
                        onClose = {
                            editor.close()
                            recents = prefs.recents()
                        },
                        onGuide = { topic ->
                            guideFocus = topic
                            showGuide = true
                        },
                    )
                } else {
                    HomeScreen(
                        recents = recents,
                        onOpenPicker = { picker.launch(OPEN_TYPES) },
                        onOpen = { uri, version -> editor.open(uri, version) },
                        onQuickAction = { action ->
                            pickedAction = action
                            picker.launch(OPEN_TYPES)
                        },
                        onBatch = { multiPicker.launch(OPEN_TYPES) },
                        showTip = showTip,
                        onDismissTip = {
                            prefs.presetTipDismissed = true
                            showTip = false
                        },
                        onSettings = { showSettings = true },
                        onGuide = {
                            guideFocus = null
                            showGuide = true
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens photos shared to or opened with Photograin from other apps. */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        uri?.let(editor::open)
    }
}

/** Everything the system picker should offer: all images, plus RAW and JPEG XL types it may not count as images. */
private val OPEN_TYPES = arrayOf("image/*", "image/jxl") + RawImage.MIME_TYPES
