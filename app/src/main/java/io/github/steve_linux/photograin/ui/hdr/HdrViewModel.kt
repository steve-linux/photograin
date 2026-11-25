// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.hdr

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.steve_linux.photograin.data.ExportDestination
import io.github.steve_linux.photograin.data.ExportFormat
import io.github.steve_linux.photograin.data.ExportWriter
import io.github.steve_linux.photograin.data.FileNaming
import io.github.steve_linux.photograin.data.HdrInput
import io.github.steve_linux.photograin.data.HdrMerger
import io.github.steve_linux.photograin.data.HdrOutput
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.data.SettingsStore
import io.github.steve_linux.photograin.model.HdrPlan
import io.github.steve_linux.photograin.model.HdrProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HdrError { UNREADABLE, OUT_OF_MEMORY, FAILED, SAVE_FAILED }

data class HdrUiState(
    val uris: List<Uri> = emptyList(),
    val probing: Boolean = false,
    val problem: HdrProblem? = null,
    /** Merged size in megapixels, and whether memory forced it below full size. */
    val megapixels: Float = 0f,
    val reduced: Boolean = false,
    val align: Boolean = true,
    val deghost: Boolean = true,
    val stage: HdrMerger.Stage? = null,
    val progress: Float = 0f,
    val error: HdrError? = null,
    /** Set when the settings say to ask where to save: the file name to suggest. */
    val askDestination: String? = null,
    /** Pixels the frames were moved by to line them up, once merged. */
    val shift: Int = 0,
    /** The saved merge, to open in the editor. */
    val saved: Uri? = null,
) {
    val running: Boolean get() = stage != null
    val canMerge: Boolean get() = uris.isNotEmpty() && !probing && problem == null && !running && askDestination == null
}

/**
 * Merges bracketed exposures into one photo, saves it as a high-quality JPEG next to the other
 * exports (with the metadata of the middle exposure) and hands it to the editor.
 */
class HdrViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore.get(app)
    private val prefs = Preferences(app)
    private var input: HdrInput? = null
    private var job: Job? = null
    /** A merge waiting for the user to pick where to save it. */
    private var pending: HdrOutput? = null

    private val _state = MutableStateFlow(HdrUiState())
    val state: StateFlow<HdrUiState> = _state.asStateFlow()

    fun start(uris: List<Uri>) {
        clear()
        val distinct = uris.distinct()
        _state.value = HdrUiState(uris = distinct, probing = true)
        job = viewModelScope.launch {
            val app = getApplication<Application>()
            val probed = withContext(Dispatchers.IO) {
                runCatching { HdrMerger.probe(app, distinct) }
                    .onFailure { Log.w(TAG, "Cannot read the brackets", it) }
                    .getOrNull()
            }
            if (probed == null) {
                _state.update { it.copy(probing = false, error = HdrError.UNREADABLE) }
                return@launch
            }
            input = probed
            val problem = HdrPlan.problem(probed.frames)
            val scale = if (problem == null) HdrMerger.scaleFor(app, probed) else 1f
            _state.update {
                it.copy(
                    probing = false,
                    problem = problem,
                    megapixels = if (problem == null) HdrPlan.megapixels(probed.frames, scale) else 0f,
                    reduced = scale < 1f,
                )
            }
        }
    }

    fun setAlign(on: Boolean) = _state.update { it.copy(align = on) }
    fun setDeghost(on: Boolean) = _state.update { it.copy(deghost = on) }

    fun merge() {
        val current = input ?: return
        if (!_state.value.canMerge) return
        val s = _state.value
        _state.update { it.copy(stage = HdrMerger.Stage.DECODE, progress = 0f, error = null) }
        job = viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val output = try {
                HdrMerger.merge(app, current, s.align, s.deghost) { stage, fraction ->
                    _state.update { it.copy(stage = stage, progress = fraction) }
                    isActive
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(stage = null) }
                throw e
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Out of memory", e)
                _state.update { it.copy(stage = null, error = HdrError.OUT_OF_MEMORY) }
                return@launch
            } catch (e: Exception) {
                Log.w(TAG, "HDR merge failed", e)
                _state.update { it.copy(stage = null, error = HdrError.FAILED) }
                return@launch
            }
            val name = HdrPlan.fileName(
                ImageLoader.queryName(app, output.reference),
                dated = settings.value.naming == FileNaming.DATE,
            )
            val target = ExportDestination.create(app, settings.value, name, ExportFormat.JPEG)
            if (target == null) {
                pending = output
                _state.update { it.copy(stage = null, askDestination = name, shift = output.alignment.largestShift) }
            } else {
                save(output, target)
            }
        }
    }

    /** The user picked where to save, or cancelled ([target] null). */
    fun saveTo(target: Uri?) {
        val output = pending ?: return
        pending = null
        _state.update { it.copy(askDestination = null) }
        if (target == null) {
            output.bitmap.recycle()
            return
        }
        viewModelScope.launch(Dispatchers.Default) { save(output, target) }
    }

    private fun save(output: HdrOutput, target: Uri) {
        val app = getApplication<Application>()
        _state.update { it.copy(stage = HdrMerger.Stage.FUSE, progress = 1f) }
        try {
            // Nearly lossless: the merge is a starting point for editing, not a final export.
            val options = settings.value.export.copy(
                format = ExportFormat.JPEG,
                quality = 97,
                maxLongEdge = null,
                colorProfile = output.profile,
            )
            ExportWriter.write(app, output.bitmap, output.reference, target, options)
            ExportDestination.finish(app, target)
            prefs.addRecent(target)
            _state.update { it.copy(stage = null, saved = target, shift = output.alignment.largestShift) }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot save the HDR merge", e)
            ExportDestination.discard(app, target)
            _state.update { it.copy(stage = null, error = HdrError.SAVE_FAILED) }
        } finally {
            output.bitmap.recycle()
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(stage = null) }
    }

    fun clear() {
        job?.cancel()
        pending?.bitmap?.recycle()
        pending = null
        input = null
        _state.value = HdrUiState()
    }

    override fun onCleared() {
        pending?.bitmap?.let(Bitmap::recycle)
    }

    private companion object {
        const val TAG = "HdrViewModel"
    }
}
