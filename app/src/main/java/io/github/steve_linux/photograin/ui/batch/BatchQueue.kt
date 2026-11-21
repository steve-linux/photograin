// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.content.Context
import android.util.AtomicFile
import io.github.steve_linux.photograin.data.ExportOptions
import io.github.steve_linux.photograin.data.FileNaming
import io.github.steve_linux.photograin.data.SaveLocation
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.LocalAdjustment
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ExportQueueItem(
    val uri: String,
    val edit: EditState? = null,
    val saved: Boolean = false,
    val failed: Boolean = false,
    val profiles: List<QueueOperation> = emptyList(),
)

@Serializable
internal enum class QueueOperation { PENDING, DONE, FAILED }

@Serializable
internal data class ExportQueue(
    val source: BatchSource,
    val masks: List<LocalAdjustment>,
    val profiles: List<ExportOptions>,
    val location: SaveLocation,
    val folder: String?,
    val naming: FileNaming,
    val items: List<ExportQueueItem>,
    val unfinishedTarget: String? = null,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val profileNames: List<String> = emptyList(),
)

internal class BatchQueueStore(context: Context) {
    private val directory = File(context.filesDir, "batch-queues").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[0-9a-f-]{36}")))
        return AtomicFile(File(directory, "$id.json"))
    }

    fun read(id: String): ExportQueue? = runCatching {
        json.decodeFromString<ExportQueue>(file(id).readFully().decodeToString()).takeIf { it.id == id }
    }.getOrNull()

    fun list(): List<BatchQueueSummary> = directory.listFiles().orEmpty().mapNotNull { entry ->
        val name = entry.name.removeSuffix(".bak").removeSuffix(".new")
        name.takeIf { it.endsWith(".json") }?.removeSuffix(".json")
    }.distinct().mapNotNull { id ->
        read(id)?.let { queue ->
            BatchQueueSummary(id, queue.createdAt, queue.items.size, queue.items.count { item ->
                !item.saved || item.profiles.any { it != QueueOperation.DONE }
            })
        }
    }.sortedByDescending { it.createdAt }

    fun write(queue: ExportQueue) {
        val bytes = json.encodeToString(queue).encodeToByteArray()
        val file = file(queue.id)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

data class BatchQueueSummary(val id: String, val createdAt: Long, val photos: Int, val remaining: Int)
