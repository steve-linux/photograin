// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.steve_linux.photograin.data.ExportDestination
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.ExportWriter
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.LookLibrary
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.render.Exporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ItemStatus { PENDING, WORKING, DONE, FAILED }

data class BatchItem(val uri: Uri, val status: ItemStatus = ItemStatus.PENDING)

/** What gets applied to every photo. */
sealed interface BatchSource {
    fun applyTo(current: Adjustments): Adjustments

    /** The adjustments copied in the editor, pasted as they are. */
    data class Copied(val adjustments: Adjustments) : BatchSource {
        override fun applyTo(current: Adjustments) = adjustments
    }

    data class FromPreset(val preset: Preset) : BatchSource {
        override fun applyTo(current: Adjustments) = preset.applyTo(current)
    }
}

data class BatchUiState(
    val items: List<BatchItem> = emptyList(),
    val copied: Adjustments? = null,
    val userPresets: List<Preset> = emptyList(),
    val source: BatchSource? = null,
    val running: Boolean = false,
    /** Set once a run ends: how many photos succeeded and failed. */
    val result: Pair<Int, Int>? = null,
) {
    val processed get() = items.count { it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED }
}

/**
 * Applies one set of adjustments to many photos. Each photo keeps its own crop and its recipe is
 * saved, so it opens in the editor with the change; optionally every photo is also exported.
 * Photos are processed one at a time to keep memory bounded.
 */
class BatchViewModel(app: Application) : AndroidViewModel(app) {
    private val recipes = RecipeStore(app)
    private val prefs = Preferences(app)
    private val library = LookLibrary(app)
    private val settings = SettingsStore.get(app)
    private var job: Job? = null

    private val _state = MutableStateFlow(BatchUiState())
    val state: StateFlow<BatchUiState> = _state.asStateFlow()

    fun start(uris: List<Uri>) {
        job?.cancel()
        val copied = prefs.copiedAdjustments
        _state.value = BatchUiState(
            items = uris.distinct().map { BatchItem(it) },
            copied = copied,
            source = copied?.let { BatchSource.Copied(it) },
        )
        viewModelScope.launch {
            val presets = withContext(Dispatchers.IO) { library.userPresets() }
            _state.update { it.copy(userPresets = presets) }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = BatchUiState()
    }

    fun selectSource(source: BatchSource) = _state.update { it.copy(source = source) }

    /**
     * Saves the edit to every photo; with [options] also exports each one, into [folder]
     * (a document tree) or, without one, where the settings say.
     */
    fun run(options: ExportOptions?, folder: Uri? = null) {
        val source = _state.value.source ?: return
        if (_state.value.running) return
        _state.update { s -> s.copy(running = true, result = null, items = s.items.map { BatchItem(it.uri) }) }
        job = viewModelScope.launch(Dispatchers.Default) {
            var ok = 0
            var failed = 0
            try {
                for (item in _state.value.items) {
                    ensureActive()
                    setStatus(item.uri, ItemStatus.WORKING)
                    val success = process(item.uri, source, options, folder)
                    if (success) ok++ else failed++
                    setStatus(item.uri, if (success) ItemStatus.DONE else ItemStatus.FAILED)
                }
            } finally {
                _state.update { it.copy(running = false, result = ok to failed) }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun setStatus(uri: Uri, status: ItemStatus) = _state.update { s ->
        s.copy(items = s.items.map { if (it.uri == uri) it.copy(status = status) else it })
    }

    private fun process(uri: Uri, source: BatchSource, options: ExportOptions?, folder: Uri?): Boolean {
        return try {
            val before = recipes.load(uri) ?: EditState()
            val edit = before.copy(adjustments = source.applyTo(before.adjustments))
            recipes.save(uri, edit)
            prefs.addRecent(uri)
            if (options != null) export(uri, edit, options, folder)
            true
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory on $uri", e)
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Batch failed on $uri", e)
            false
        }
    }

    private fun export(uri: Uri, edit: EditState, options: ExportOptions, folder: Uri?) {
        val app = getApplication<Application>()
        val base = ImageLoader.baseOrientation(app, uri)
        val lut = edit.adjustments.look.lutId?.let { library.loadLut(it) }
        val bitmap = RegionSource.open(app, uri).use { Exporter.render(it, base, edit, lut, options.maxLongEdge, options.colorProfile) {} }
        try {
            val current = settings.value
            val name = ExportDestination.fileName(ImageLoader.queryName(app, uri), current.naming, options.format)
            // Taken names get " (1)" and so on from the provider.
            val target = (if (folder != null) {
                ExportDestination.createInTree(app, folder, name, options.format)
            } else {
                ExportDestination.create(app, current, name, options.format)
            }) ?: throw IOException("Cannot create $name")
            try {
                ExportWriter.write(app, bitmap, uri, target, options)
                ExportDestination.finish(app, target)
            } catch (e: Exception) {
                ExportDestination.discard(app, target)
                throw e
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "BatchViewModel"
    }
}
