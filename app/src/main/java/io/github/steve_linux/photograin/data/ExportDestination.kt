// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where exported photos go, following [AppSettings.saveLocation]. */
object ExportDestination {
    const val FOLDER_NAME = "Photograin"
    private const val TAG = "ExportDestination"

    /** Whether [settings] name a place to save without asking the user. */
    fun isAutomatic(settings: AppSettings): Boolean = when (settings.saveLocation) {
        SaveLocation.PICTURES -> Build.VERSION.SDK_INT >= 29
        SaveLocation.FOLDER -> settings.folder != null
        SaveLocation.ASK -> false
    }

    fun fileName(original: String?, naming: FileNaming, format: ExportFormat, now: Date = Date()): String {
        val base = when (naming) {
            FileNaming.ORIGINAL ->
                (original?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "photo") + "_photograin"
            FileNaming.DATE -> "Photograin_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        }
        return "$base.${format.extension}"
    }

    /**
     * Creates the file to write into, or null when the user has to pick one: the setting is
     * [SaveLocation.ASK], the folder is gone, or the system refused the file type.
     * Call [finish] once the file is written, or [discard] if writing failed.
     */
    fun create(context: Context, settings: AppSettings, name: String, format: ExportFormat): Uri? = when (settings.saveLocation) {
        SaveLocation.PICTURES -> if (Build.VERSION.SDK_INT >= 29) inPictures(context, name, format) else null
        SaveLocation.FOLDER -> settings.folder?.let { createInTree(context, it, name, format) }
        SaveLocation.ASK -> null
    }

    /** A new document in a folder picked with ACTION_OPEN_DOCUMENT_TREE; the provider renames it if taken. */
    fun createInTree(context: Context, tree: Uri, name: String, format: ExportFormat): Uri? = runCatching {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        DocumentsContract.createDocument(context.contentResolver, dir, format.mimeType, name)
    }.onFailure { Log.w(TAG, "Cannot create $name in $tree", it) }.getOrNull()

    /** Makes a photo saved to Pictures/Photograin visible to galleries. */
    fun finish(context: Context, uri: Uri) {
        if (uri.authority != MediaStore.AUTHORITY || Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    /** Removes a file whose export failed, so no empty file is left behind. */
    fun discard(context: Context, uri: Uri) {
        runCatching {
            if (uri.authority == MediaStore.AUTHORITY) {
                context.contentResolver.delete(uri, null, null)
            } else {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        }
    }

    /**
     * Pictures/Photograin through MediaStore, which needs no permission from Android 10 on.
     * Older systems do not know every type in the images table (JPEG XL), so the generic
     * files table is tried next.
     */
    @RequiresApi(29)
    private fun inPictures(context: Context, name: String, format: ExportFormat): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$FOLDER_NAME")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val tables = listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        )
        for (table in tables) {
            runCatching { context.contentResolver.insert(table, values) }
                .onFailure { Log.w(TAG, "Cannot add $name to $table", it) }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }
}
