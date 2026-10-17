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
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.ui.editor.EditorScreen
import io.github.steve_linux.photograin.ui.editor.EditorViewModel
import io.github.steve_linux.photograin.ui.home.HomeScreen
import io.github.steve_linux.photograin.ui.theme.PhotograinTheme

class MainActivity : ComponentActivity() {
    private val editor: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val prefs = Preferences(applicationContext)

        setContent {
            PhotograinTheme {
                val state by editor.state.collectAsStateWithLifecycle()
                var recents by remember { mutableStateOf(prefs.recents()) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(editor::open)
                }
                if (state.image != null || state.loading) {
                    EditorScreen(editor, onClose = {
                        editor.close()
                        recents = prefs.recents()
                    })
                } else {
                    HomeScreen(
                        recents = recents,
                        onOpenPicker = { picker.launch(arrayOf("image/*")) },
                        onOpen = editor::open,
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
