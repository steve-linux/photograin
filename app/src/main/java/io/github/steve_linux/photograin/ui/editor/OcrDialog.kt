// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.data.LoadedImage
import io.github.steve_linux.photograin.data.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun OcrDialog(image: LoadedImage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf("ita") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    fun run(model: Uri? = null) {
        val selected = language
        busy = true
        result = ""
        message = null
        scope.launch {
            try {
                result = withContext(Dispatchers.IO) { recognize(context, image, selected, model) }
                if (result.isBlank()) message = R.string.ocr_empty
            } catch (e: CancellationException) {
                throw e
            } catch (_: MissingOcrModel) {
                message = R.string.ocr_missing
            } catch (_: Exception) {
                message = R.string.ocr_failed
            } catch (_: LinkageError) {
                message = R.string.ocr_failed
            } catch (_: OutOfMemoryError) {
                message = R.string.msg_out_of_memory
            } finally {
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run(uri)
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ocr_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ocr_hint))
                for ((code, label) in listOf("ita" to R.string.ocr_italian, "eng" to R.string.ocr_english)) {
                    androidx.compose.material3.FilterChip(
                        selected = language == code, enabled = !busy,
                        onClick = { language = code; result = ""; message = null },
                        label = { Text(stringResource(label)) },
                    )
                }
                TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.ocr_import))
                }
                if (busy) Text(stringResource(R.string.ocr_busy))
                message?.let { Text(stringResource(it)) }
                if (result.isNotBlank()) SelectionContainer { Text(result) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (result.isBlank()) run() else {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("OCR", result))
                    onDismiss()
                }
            }) { Text(stringResource(if (result.isBlank()) R.string.ocr_read else R.string.ocr_copy)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private class MissingOcrModel : Exception()

private fun recognize(context: Context, image: LoadedImage, language: String, importUri: Uri?): String {
    val root = File(context.filesDir, "ocr")
    val data = File(root, "tessdata").apply { mkdirs() }
    val model = File(data, "$language.traineddata")
    // Validate an imported model in a separate Tesseract root before replacing the saved one.
    val staging = importUri?.let { java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "ocr").toFile() }
    try {
        if (staging != null && importUri != null) {
            val filename = "$language.traineddata"
            check(ImageLoader.queryName(context, importUri) == filename)
            val staged = File(staging, "tessdata/$filename")
            staged.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(importUri) ?: error("Model unavailable")
            input.use { source ->
                staged.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 64 * 1024 * 1024)
                        out.write(buffer, 0, count)
                    }
                }
            }
        } else if (!model.isFile) {
            throw MissingOcrModel()
        }
        val api = TessBaseAPI()
        try {
            check(api.init((staging ?: root).absolutePath, language))
            if (staging != null) {
                val temporary = File.createTempFile("model-", ".tmp", data)
                try {
                    File(staging, "tessdata/$language.traineddata").copyTo(temporary, overwrite = true)
                    check(temporary.renameTo(model))
                } finally {
                    temporary.delete()
                }
            }
            val source = image.preview
            val matrix = Matrix().apply {
                if (image.base.mirror) setScale(-1f, 1f)
                postRotate(image.base.quarterTurns * 90f)
            }
            val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            try {
                val bitmap = oriented.copy(Bitmap.Config.ARGB_8888, false) ?: error("OCR bitmap unavailable")
                try {
                    api.setImage(bitmap)
                    return api.getUTF8Text().orEmpty().trim()
                } finally {
                    bitmap.recycle()
                }
            } finally {
                if (oriented !== source) oriented.recycle()
            }
        } finally {
            api.recycle()
        }
    } finally {
        staging?.deleteRecursively()
    }
}
