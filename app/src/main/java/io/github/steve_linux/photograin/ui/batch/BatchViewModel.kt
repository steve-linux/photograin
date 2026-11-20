// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.steve_linux.photograin.data.AppSettings
import io.github.steve_linux.photograin.data.ExportDestination
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.ExportWriter
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.LookLibrary
import io.github.steve_linux.photograin.data.ObjectRemover
import io.github.steve_linux.photograin.data.Preferences
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RegionSource
import io.github.steve_linux.photograin.data.SubjectSegmenter
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
import kotlinx.serialization.Serializable

enum class ItemStatus { PENDING, WORKING, DONE, FAILED }

data class BatchItem(val uri: Uri, val status: ItemStatus = ItemStatus.PENDING, val profiles: List<Pair<String, ItemStatus>> = emptyList())

/** What gets applied to every photo. */
@Serializable
sealed interface BatchSource {
    fun applyTo(uri: String, current: Adjustments): Adjustments
    fun includes(uri: String): Boolean = true

    /** The adjustments copied in the editor, pasted as they are. */
    @Serializable
    data class Copied(val adjustments: Adjustments) : BatchSource {
        override fun applyTo(uri: String, current: Adjustments) = adjustments
    }

    @Serializable
    data class FromPreset(val preset: Preset) : BatchSource {
        override fun applyTo(uri: String, current: Adjustments) = preset.applyTo(current)
    }

    @Serializable
    data class MatchedExposure(val corrections: Map<String, Float>) : BatchSource {
        override fun includes(uri: String) = uri in corrections
        override fun applyTo(uri: String, current: Adjustments) =
            current.copy(exposure = (current.exposure + corrections.getValue(uri)).coerceIn(-4f, 4f))
    }

    @Serializable
    data class MatchedWhiteBalance(val corrections: Map<String, Pair<Float, Float>>) : BatchSource {
        override fun includes(uri: String) = uri in corrections

        override fun applyTo(uri: String, current: Adjustments): Adjustments {
            val (temperature, tint) = corrections.getValue(uri)
            return current.copy(temperature = temperature, tint = tint)
        }
    }

}

data class BatchUiState(
    val items: List<BatchItem> = emptyList(),
    val copied: Adjustments? = null,
    val userPresets: List<Preset> = emptyList(),
    val source: BatchSource? = null,
    val running: Boolean = false,
    val queues: List<BatchQueueSummary> = emptyList(),
    val activeQueue: String? = null,
    val canResume: Boolean = false,
    val queueView: Boolean = false,
    val storageFailed: Boolean = false,
    val availableMasks: Int = 0,
    val copyMasks: Boolean = false,
    /** Set once a run ends: how many photos succeeded and failed. */
    val result: Pair<Int, Int>? = null,
) {
    val queueAvailable get() = queues.isNotEmpty()
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
    private val queueStore = BatchQueueStore(app)
    private var job: Job? = null

    private val _state = MutableStateFlow(BatchUiState())
    val state: StateFlow<BatchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            refreshQueues()
        }
    }

    fun start(uris: List<Uri>) {
        job?.cancel()
        val copied = prefs.copiedAdjustments
        _state.value = BatchUiState(
            items = uris.distinct().map { BatchItem(it) },
            copied = copied,
            availableMasks = prefs.copiedAutomaticMasks.size,
            source = copied?.let { BatchSource.Copied(it) },
            queues = _state.value.queues,
        )
        viewModelScope.launch {
            val presets = withContext(Dispatchers.IO) { library.userPresets() }
            _state.update { it.copy(userPresets = presets) }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = BatchUiState(queues = _state.value.queues)
    }

    fun setCopyMasks(value: Boolean) = _state.update { it.copy(copyMasks = value) }

    fun selectSource(source: BatchSource) = _state.update { it.copy(source = source) }

    /**
     * Saves the edit to every photo; with [options] also exports each one, into [folder]
     * (a document tree) or, without one, where the settings say.
     */
    fun run(options: ExportOptions?, folder: Uri? = null) = runProfiles(listOfNotNull(options), folder)

    fun openQueue() {
        _state.value.queues.firstOrNull()?.id?.let { openQueue(it) }
    }

    fun openQueue(id: String) {
        if (_state.value.running) return
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) { queueStore.read(id) } ?: return@launch
            if (!_state.value.running) showQueue(queue)
        }
    }

    fun resumeQueue(folder: Uri? = null) {
        val id = _state.value.activeQueue ?: return
        launchQueue(persistent = true) {
            val queue = queueStore.read(id) ?: return@launchQueue null
            if (folder == null) queue else {
                val resolver = getApplication<Application>().contentResolver
                resolver.takePersistableUriPermission(folder,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                queue.copy(folder = folder.toString())
            }
        }
    }

    fun runProfiles(options: List<ExportOptions>, folder: Uri? = null, labels: List<String> = emptyList()) {
        val state = _state.value
        val source = state.source ?: return
        val current = settings.value
        val masks = if (state.copyMasks) prefs.copiedAutomaticMasks else emptyList()
        val uris = state.items.map { it.uri }
            .filter { source.includes(it.toString()) }
        if (uris.isEmpty()) return
        launchQueue(persistent = options.isNotEmpty()) {
            val app = getApplication<Application>()
            for (uri in uris) runCatching {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            folder?.let { runCatching {
                app.contentResolver.takePersistableUriPermission(it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } }
            val destination = folder ?: current.folder.takeIf { current.saveLocation == io.github.steve_linux.photograin.data.SaveLocation.FOLDER }
            ExportQueue(source, masks, options, current.saveLocation, destination?.toString(), current.naming,
                uris.map { ExportQueueItem(it.toString(), profiles = List(options.size) { QueueOperation.PENDING }) },
                profileNames = labels)
        }
    }

    private fun launchQueue(persistent: Boolean, request: () -> ExportQueue?) {
        if (_state.value.running) return
        _state.update { it.copy(running = true, result = null, storageFailed = false) }
        job = viewModelScope.launch(Dispatchers.IO) {
            var id: String? = null
            try {
                val queue = request() ?: return@launch
                id = queue.id
                processQueue(queue, persistent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not save queue", e)
                _state.update { it.copy(storageFailed = true) }
            } finally {
                if (persistent) {
                    id?.let(queueStore::read)?.let { showQueue(it) }
                    _state.update { it.copy(queues = queueStore.list()) }
                }
                _state.update { it.copy(running = false, items = it.items.map { item ->
                    if (item.status == ItemStatus.WORKING) item.copy(status = ItemStatus.PENDING) else item
                }) }
            }
        }
    }

    private suspend fun refreshQueues() {
        val queues = withContext(Dispatchers.IO) { queueStore.list() }
        _state.update { it.copy(queues = queues) }
    }

    private suspend fun processQueue(initial: ExportQueue, persistent: Boolean) {
        var queue = initial
        fun save(next: ExportQueue, working: Int? = null) {
            if (persistent) queueStore.write(next)
            queue = next
            showQueue(queue, working, persistent)
        }
        fun change(index: Int, item: ExportQueueItem, target: String? = queue.unfinishedTarget) {
            val items = queue.items.toMutableList().apply { set(index, item) }
            save(queue.copy(items = items, unfinishedTarget = target), index)
        }
        val app = getApplication<Application>()
        queue.unfinishedTarget?.let { ExportDestination.discard(app, Uri.parse(it)) }
        save(queue.copy(unfinishedTarget = null))
        val destination = AppSettings(saveLocation = queue.location, naming = queue.naming)
        for (index in queue.items.indices) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            var item = queue.items[index]
            if (item.saved && item.profiles.all { it == QueueOperation.DONE }) continue
            val uri = Uri.parse(item.uri)
            showQueue(queue, index, persistent)
            if (!item.saved) {
                val edit = item.edit ?: attempt(uri) {
                    val before = recipes.load(uri) ?: EditState()
                    val merged = (before.masks + queue.masks.filterNot { it in before.masks })
                        .take(io.github.steve_linux.photograin.model.LocalAdjustment.MAX)
                    before.copy(adjustments = queue.source.applyTo(item.uri, before.adjustments), masks = merged)
                }
                if (edit == null) {
                    change(index, item.copy(failed = true))
                    continue
                }
                item = item.copy(edit = edit)
                change(index, item)
                val saved = attempt(uri) { recipes.save(uri, edit); prefs.addRecent(uri); true } == true
                item = item.copy(saved = saved, failed = !saved)
                change(index, item)
                if (!saved) continue
            }
            for (profileIndex in queue.profiles.indices) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (item.profiles[profileIndex] == QueueOperation.DONE) continue
                val success = attempt(uri) {
                    export(uri, checkNotNull(item.edit), queue.profiles[profileIndex], queue.folder?.let(Uri::parse),
                        destination, index) { target -> change(index, item, target.toString()) }
                    true
                } == true
                val profiles = item.profiles.toMutableList().apply {
                    set(profileIndex, if (success) QueueOperation.DONE else QueueOperation.FAILED)
                }
                item = item.copy(profiles = profiles)
                change(index, item, target = null)
            }
            if (item.saved && item.profiles.all { it == QueueOperation.DONE }) change(index, item.copy(edit = null))
        }
        showQueue(queue, persistent = persistent)
    }

    private inline fun <T> attempt(uri: Uri, action: () -> T): T? = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory on $uri", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Batch failed on $uri", e)
        null
    }

    private fun showQueue(queue: ExportQueue, working: Int? = null, persistent: Boolean = true) {
        val items = queue.items.mapIndexed { index, operation ->
            val status = when {
                index == working -> ItemStatus.WORKING
                operation.failed || QueueOperation.FAILED in operation.profiles -> ItemStatus.FAILED
                operation.saved && operation.profiles.all { it == QueueOperation.DONE } -> ItemStatus.DONE
                else -> ItemStatus.PENDING
            }
            val profiles = operation.profiles.mapIndexed { i, result ->
                val label = queue.profileNames.getOrNull(i) ?: "${i + 1} · ${queue.profiles[i].format.label}"
                label to when (result) {
                    QueueOperation.DONE -> ItemStatus.DONE
                    QueueOperation.FAILED -> ItemStatus.FAILED
                    QueueOperation.PENDING -> ItemStatus.PENDING
                }
            }
            BatchItem(Uri.parse(operation.uri), status, profiles)
        }
        _state.update { it.copy(items = items, activeQueue = queue.id.takeIf { persistent }, queueView = persistent,
            canResume = persistent && items.any { item -> item.status != ItemStatus.DONE },
            result = if (working == null) items.count { item -> item.status == ItemStatus.DONE } to
                items.count { item -> item.status == ItemStatus.FAILED } else null) }
    }

    fun stop() {
        job?.cancel()
    }

    private fun export(uri: Uri, edit: EditState, options: ExportOptions, folder: Uri?,
        current: AppSettings, index: Int, onTarget: (Uri) -> Unit,
    ) {
        val app = getApplication<Application>()
        val base = ImageLoader.baseOrientation(app, uri)
        val lut = edit.adjustments.look.lutId?.let { library.loadLut(it) }
        val original = RegionSource.open(app, uri, edit.rawHighlight.libRawMode, edit.rawWhiteBalance)
        // Each photo has its own subject: a recipe with a subject mask finds it again here.
        val subjects = try {
            SubjectSegmenter.forEdit(app, original, base, edit)
        } catch (e: Throwable) {
            original.close()
            throw e
        }
        val (source, render) = Exporter.prepare(app, original, base, edit, options)
        val bitmap = source.use {
            val ids = edit.images.map { it.id }.toSet()
            val layers = io.github.steve_linux.photograin.data.ImageLayerStore(app).load(ids)
            try {
                check(layers.size == ids.size) { "Missing image layer" }
                Exporter.render(
                    it, base, edit, lut, render.maxLongEdge, render.colorProfile, render.upscale, render.format.deep,
                    subjects = subjects, opaque = !render.format.alpha, inpaint = ObjectRemover.with(app),
                    layerBitmaps = layers, outputSharpen = options.outputSharpen,
                ) {}
            } finally {
                layers.values.forEach(Bitmap::recycle)
            }
        }
        try {
            val name = if (options.batchPrefix.isBlank()) {
                ExportDestination.fileName(ImageLoader.queryName(app, uri), current.naming, options.format)
            } else {
                val prefix = options.batchPrefix.trim().replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(64)
                val number = (options.batchStart.coerceIn(1, 999999) + index).toString().padStart(3, '0')
                "$prefix-$number.${options.format.extension}"
            }
            // Taken names get " (1)" and so on from the provider.
            val target = (if (folder != null) {
                ExportDestination.createInTree(app, folder, name, options.format)
            } else {
                ExportDestination.create(app, current, name, options.format)
            }) ?: throw IOException("Cannot create $name")
            try {
                onTarget(target)
                ExportWriter.write(app, bitmap, uri, target, options.copy(gpsLatitude = edit.gpsLatitude, gpsLongitude = edit.gpsLongitude))
                ExportDestination.finish(app, target)
            } catch (e: Throwable) {
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
