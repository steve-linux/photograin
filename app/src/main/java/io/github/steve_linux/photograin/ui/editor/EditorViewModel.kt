// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.ExportWriter
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.LoadedImage
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.AspectRatio
import io.github.steve_linux.photograin.model.CropRect
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Geometry
import io.github.steve_linux.photograin.model.GeometryMapper
import io.github.steve_linux.photograin.model.History
import io.github.steve_linux.photograin.model.ParamSpec
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.ToolGroup
import io.github.steve_linux.photograin.model.flippedHorizontally
import io.github.steve_linux.photograin.model.flippedVertically
import io.github.steve_linux.photograin.model.rotatedClockwise
import io.github.steve_linux.photograin.render.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Message { EXPORTED, EXPORT_FAILED, LOAD_FAILED, ADJUSTMENTS_COPIED, ADJUSTMENTS_PASTED, OUT_OF_MEMORY }

data class EditorUiState(
    val image: LoadedImage? = null,
    val loading: Boolean = false,
    /** The live edit, including a slider that is still being dragged. */
    val edit: EditState = EditState(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tool: ToolGroup = ToolGroup.LIGHT,
    /** Press-and-hold on the photo shows the original. */
    val showOriginal: Boolean = false,
    /** Position of the before/after divider (0..1), or null when split view is off. */
    val split: Float? = null,
    val exportProgress: Float? = null,
    val message: Message? = null,
    val hasCopiedAdjustments: Boolean = false,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val recipes = RecipeStore(app)
    private val prefs = Preferences(app)
    private var history = History(EditState())
    private var loadJob: Job? = null

    /** Recipe writes run one at a time, in order. */
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(EditorUiState(hasCopiedAdjustments = prefs.copiedAdjustments != null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun open(uri: Uri) {
        if (_state.value.image?.uri == uri) return
        loadJob?.cancel()
        _state.update { EditorUiState(loading = true, hasCopiedAdjustments = it.hasCopiedAdjustments) }
        loadJob = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) { ImageLoader.load(getApplication(), uri) }
                val saved = withContext(Dispatchers.IO) { recipes.load(uri) } ?: EditState()
                prefs.addRecent(uri)
                history = History(saved)
                _state.update { it.copy(image = image, loading = false, edit = saved).withHistory() }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open $uri", e)
                prefs.removeRecent(uri)
                _state.update { it.copy(loading = false, message = Message.LOAD_FAILED) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(loading = false, message = Message.OUT_OF_MEMORY) }
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        _state.update { EditorUiState(hasCopiedAdjustments = it.hasCopiedAdjustments) }
    }

    fun selectTool(tool: ToolGroup) = _state.update { it.copy(tool = tool) }

    // --- Adjustments -------------------------------------------------------------------------

    /** Live update while a slider moves; [commit] records the step when the finger lifts. */
    fun setParam(spec: ParamSpec, value: Float) = updateLive { e ->
        e.copy(adjustments = spec.set(e.adjustments, value.coerceIn(spec.min, spec.max)))
    }

    fun resetParam(spec: ParamSpec) {
        setParam(spec, spec.default)
        commit()
    }

    fun commit() {
        val edit = _state.value.edit
        history.commit(edit)
        _state.update { it.withHistory() }
        persist(edit)
    }

    fun undo() = applyHistory(history.undo())

    fun redo() = applyHistory(history.redo())

    fun resetAll() {
        updateLive { EditState() }
        commit()
    }

    fun copyAdjustments() {
        prefs.copiedAdjustments = _state.value.edit.adjustments
        _state.update { it.copy(hasCopiedAdjustments = true, message = Message.ADJUSTMENTS_COPIED) }
    }

    fun pasteAdjustments() {
        val copied: Adjustments = prefs.copiedAdjustments ?: return
        updateLive { it.copy(adjustments = copied) }
        commit()
        _state.update { it.copy(message = Message.ADJUSTMENTS_PASTED) }
    }

    // --- Before / after ----------------------------------------------------------------------

    fun setShowOriginal(show: Boolean) = _state.update { it.copy(showOriginal = show) }

    fun toggleSplit() = _state.update { it.copy(split = if (it.split == null) 0.5f else null) }

    fun setSplit(x: Float) = _state.update { it.copy(split = x.coerceIn(0f, 1f)) }

    // --- Geometry ----------------------------------------------------------------------------

    /** Mapper for the preview bitmap and the given geometry; null until an image is loaded. */
    fun mapper(geometry: Geometry = _state.value.edit.geometry): GeometryMapper? {
        val image = _state.value.image ?: return null
        return GeometryMapper(image.preview.width, image.preview.height, image.base, geometry)
    }

    /** Frame size (rotated and straightened, before crop) of the preview. */
    fun frameSize(): PxSize? = mapper()?.let { PxSize(it.frameWidth.toInt(), it.frameHeight.toInt()) }

    fun rotate() = commitGeometry { it.rotatedClockwise() }

    fun flipHorizontal() = commitGeometry { it.flippedHorizontally() }

    fun flipVertical() = commitGeometry { it.flippedVertically() }

    /** Live straighten; the crop shrinks as needed so no empty corners appear. */
    fun setStraighten(degrees: Float) = updateLive { e ->
        val g = e.geometry.copy(straighten = degrees.coerceIn(-45f, 45f))
        val m = mapper(g) ?: return@updateLive e
        e.copy(geometry = g.copy(crop = m.fitCrop(e.geometry.crop)))
    }

    /** Live crop change from the overlay; rejected if it would include empty corners. */
    fun setCrop(crop: CropRect) = updateLive { e ->
        val clamped = GeometryMapper.clampCrop(crop)
        val m = mapper() ?: return@updateLive e
        if (m.isCropValid(clamped)) e.copy(geometry = e.geometry.copy(crop = clamped)) else e
    }

    fun setAspect(aspect: AspectRatio) = commitGeometry { g ->
        val m = mapper(g) ?: return@commitGeometry g
        val ratio = aspectRatioValue(aspect, g)
        val crop = if (ratio == null) g.crop else m.centeredCrop(ratio)
        g.copy(aspect = aspect, crop = crop)
    }

    /** Width / height of an aspect preset in output pixels, or null for free-form. */
    fun aspectRatioValue(aspect: AspectRatio, geometry: Geometry = _state.value.edit.geometry): Float? {
        if (aspect == AspectRatio.FREE) return null
        if (aspect == AspectRatio.ORIGINAL) {
            val m = mapper(geometry.copy(straighten = 0f)) ?: return null
            return (m.frameWidth / m.frameHeight).toFloat()
        }
        val r = aspect.ratio ?: return null
        val m = mapper(geometry) ?: return r
        // Follow the frame's orientation: a portrait frame gets 3:4 instead of 4:3.
        return if (m.frameHeight > m.frameWidth) 1f / r else r
    }

    fun resetGeometry() = commitGeometry { Geometry() }

    private fun commitGeometry(transform: (Geometry) -> Geometry) {
        updateLive { it.copy(geometry = transform(it.geometry)) }
        commit()
    }

    // --- Export ------------------------------------------------------------------------------

    fun exportSize(options: ExportOptions): PxSize? {
        val image = _state.value.image ?: return null
        return Exporter.outputSize(
            PxSize(image.sourceWidth, image.sourceHeight), image.base, _state.value.edit, options.maxLongEdge,
        )
    }

    fun export(destination: Uri, options: ExportOptions) {
        val image = _state.value.image ?: return
        val edit = _state.value.edit
        _state.update { it.copy(exportProgress = 0f) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                try {
                    val app = getApplication<Application>()
                    val bitmap = RegionSource.open(app, image.uri).use { source ->
                        Exporter.render(source, image.base, edit, options.maxLongEdge) { p ->
                            _state.update { it.copy(exportProgress = p) }
                        }
                    }
                    ExportWriter.write(app, bitmap, image.uri, destination, options)
                    bitmap.recycle()
                    Message.EXPORTED
                } catch (e: OutOfMemoryError) {
                    Message.OUT_OF_MEMORY
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    Message.EXPORT_FAILED
                }
            }
            _state.update { it.copy(exportProgress = null, message = ok) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // --- Internals ---------------------------------------------------------------------------

    private inline fun updateLive(crossinline transform: (EditState) -> EditState) =
        _state.update { it.copy(edit = transform(it.edit)) }

    private fun applyHistory(edit: EditState) {
        _state.update { it.copy(edit = edit).withHistory() }
        persist(edit)
    }

    private fun EditorUiState.withHistory() = copy(canUndo = history.canUndo, canRedo = history.canRedo)

    private fun persist(edit: EditState) {
        val uri = _state.value.image?.uri ?: return
        viewModelScope.launch(saveDispatcher) {
            runCatching { recipes.save(uri, edit) }.onFailure { Log.w(TAG, "Cannot save recipe", it) }
        }
    }

    private companion object {
        const val TAG = "EditorViewModel"
    }
}
