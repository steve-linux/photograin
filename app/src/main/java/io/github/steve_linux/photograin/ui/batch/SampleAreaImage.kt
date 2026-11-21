// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.batch

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R

@Composable
internal fun SampleAreaImage(image: Bitmap, area: Offset, fraction: Float, photoKey: Any, onSample: (Offset) -> Unit) {
    val callback = rememberUpdatedState(onSample)
    Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height).pointerInput(photoKey) {
        detectTapGestures { point -> callback.value(Offset(
            (point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f),
        )) }
    }) {
        Image(image.asImageBitmap(), stringResource(R.string.match_target), Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds)
        Canvas(Modifier.matchParentSize()) {
            val width = size.width * fraction
            val height = size.height * fraction
            val left = (area.x * size.width - width / 2).coerceIn(0f, size.width - width)
            val top = (area.y * size.height - height / 2).coerceIn(0f, size.height - height)
            drawRect(Color.White, Offset(left, top), Size(width, height), style = Stroke(2.dp.toPx()))
        }
    }
}
