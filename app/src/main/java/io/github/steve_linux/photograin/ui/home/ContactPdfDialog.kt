// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.ImageLoader
import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.model.CollectionPhoto
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.PhotoRating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COLUMNS = 3
private const val ROWS = 4
private const val PER_PAGE = COLUMNS * ROWS

@Composable
internal fun ContactPdfDialog(photos: List<CollectionPhoto<Uri>>, ratings: Map<Uri, PhotoRating>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edited by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(0) }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val useEdits = edited
            busy = true
            status = 0
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { writeContactPdf(context, uri, photos, ratings, useEdits) }
                    status = R.string.msg_exported
                } catch (e: CancellationException) {
                    throw e
                } catch (_: OutOfMemoryError) {
                    status = R.string.msg_out_of_memory
                } catch (_: Exception) {
                    status = R.string.msg_export_failed
                } finally {
                    busy = false
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.contact_pdf)) },
        text = {
            Column {
                Text(stringResource(R.string.contact_pdf_pages, photos.size, (photos.size + PER_PAGE - 1) / PER_PAGE))
                FilterChip(selected = edited, enabled = !busy, onClick = { edited = !edited },
                    label = { Text(stringResource(R.string.contact_pdf_edited)) })
                if (busy) LinearProgressIndicator()
                if (status != 0) Text(stringResource(status))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { destination.launch("Photograin-contact-sheet.pdf") }) {
                Text(stringResource(R.string.export_action))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private suspend fun writeContactPdf(
    context: Context,
    target: Uri,
    photos: List<CollectionPhoto<Uri>>,
    ratings: Map<Uri, PhotoRating>,
    edited: Boolean,
) {
    val recipes = RecipeStore(context)
    val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f }
    val width = 595
    val height = 842
    val margin = 26f
    val cellWidth = (width - 2 * margin) / COLUMNS
    val cellHeight = (height - 2 * margin - 18) / ROWS
    val pdf = PdfDocument()
    try {
        for ((index, group) in photos.chunked(PER_PAGE).withIndex()) {
            currentCoroutineContext().ensureActive()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
            try {
                for ((position, photo) in group.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val x = margin + position % COLUMNS * cellWidth
                    val y = margin + position / COLUMNS * cellHeight
                    val edit = if (edited) recipes.load(photo.source, photo.version) else null
                    val useVersion = edit != null && edit != EditState()
                    val bitmap = if (useVersion) EditedThumbnails.load(context, photo.source, 480, photo.version)
                        else ImageLoader.thumbnail(context, photo.source, 480)
                    if (bitmap != null) {
                        try {
                            val scale = minOf((cellWidth - 10) / bitmap.width, (cellHeight - 27) / bitmap.height)
                            val w = bitmap.width * scale
                            val h = bitmap.height * scale
                            val left = x + (cellWidth - w) / 2
                            val top = y + (cellHeight - 27 - h) / 2
                            page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), imagePaint)
                        } finally {
                            if (!useVersion) bitmap.recycle()
                        }
                    } else {
                        page.canvas.drawText(context.getString(R.string.contact_pdf_unreadable), x + 4, y + cellHeight / 2, label)
                    }
                    val name = ImageLoader.queryName(context, photo.source) ?: photo.source.lastPathSegment.orEmpty()
                    val version = if (edited && photo.version != 1) " · v${photo.version}" else ""
                    val display = name + version
                    val length = label.breakText(display, true, cellWidth - 10, null)
                    page.canvas.drawText(display.take(length), x + 4, y + cellHeight - 16, label)
                    val stars = ratings[photo.source]?.stars ?: 0
                    if (stars > 0) page.canvas.drawText(context.getString(R.string.contact_pdf_rating, stars),
                        x + 4, y + cellHeight - 5, label)
                }
                page.canvas.drawText((index + 1).toString(), width - margin, height - margin, label)
            } finally {
                pdf.finishPage(page)
            }
        }
        val output = context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot open PDF")
        output.use(pdf::writeTo)
    } finally {
        pdf.close()
    }
}
