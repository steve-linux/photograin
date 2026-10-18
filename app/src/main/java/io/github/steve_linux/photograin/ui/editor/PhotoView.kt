// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.RectF
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.steve_linux.photograin.model.CropRect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.steve_linux.photograin.model.ToolGroup
import io.github.steve_linux.photograin.render.PreviewFrame
import io.github.steve_linux.photograin.render.PreviewRenderer
import kotlin.math.min
import kotlin.math.roundToInt

/** The photo with live GPU rendering, zoom/pan, press-to-compare and the crop overlay. */
@Composable
fun PhotoView(
    state: EditorUiState,
    viewModel: EditorViewModel,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val image = state.image ?: return
    val cropMode = state.tool == ToolGroup.CROP
    val localMode = state.tool == ToolGroup.LOCAL
    val geometry = state.edit.geometry
    val displayGeometry = if (cropMode) geometry.copy(crop = CropRect.FULL) else geometry
    val mapper = viewModel.mapper(displayGeometry) ?: return
    val content = mapper.outputSize

    val renderer = remember { PreviewRenderer() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // Reset zoom whenever what is displayed changes shape.
    LaunchedEffect(image, cropMode, content) {
        zoom = 1f
        pan = Offset.Zero
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val padding = with(density) { (if (cropMode) 28.dp else 8.dp).toPx() }
        val fit = min((viewW - 2 * padding) / content.width, (viewH - 2 * padding) / content.height)
        val w = content.width * fit * zoom
        val h = content.height * fit * zoom
        val left = (viewW - w) / 2 + pan.x
        val top = (viewH - h) / 2 + pan.y
        val rect = RectF(left, top, left + w, top + h)

        val compare = when {
            cropMode -> -1f
            state.showOriginal -> 2f
            state.split != null -> state.split
            else -> -1f
        }
        val frame = PreviewFrame(
            bitmap = image.preview,
            base = image.base,
            geometry = displayGeometry,
            adjustments = state.edit.adjustments,
            lut = state.lut,
            imageRect = rect,
            compareSplit = compare,
            background = floatArrayOf(background.red, background.green, background.blue),
            profile = image.profile,
            masks = state.edit.masks,
            showMask = if (localMode && (state.showMask || state.maskDragging)) state.activeMask ?: -1 else -1,
        )

        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var surface by remember { mutableStateOf<GLSurfaceView?>(null) }
        DisposableEffect(lifecycle, surface) {
            val view = surface
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view?.onResume()
                    Lifecycle.Event.ON_PAUSE -> view?.onPause()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    surface = this
                }
            },
            update = { view ->
                renderer.submit(frame)
                view.requestRender()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (cropMode) {
            CropOverlay(
                crop = geometry.crop,
                imageRect = rect,
                aspect = viewModel.aspectRatioValue(geometry.aspect),
                frameWidth = mapper.frameWidth.toFloat(),
                frameHeight = mapper.frameHeight.toFloat(),
                onCropChange = viewModel::setCrop,
                onCropEnd = viewModel::commit,
            )
        } else {
            val currentRect by rememberUpdatedState(rect)
            val space = if (localMode) {
                MaskSpace(rect, mapper.normalizedOutputToSource(), image.preview.width, image.preview.height)
            } else {
                null
            }
            val currentSpace by rememberUpdatedState(space)
            val currentMasks by rememberUpdatedState(state.edit.masks)
            val currentSelected by rememberUpdatedState(state.activeMask)
            // A finger on a mask handle: holding still must not reveal the original.
            var handleHeld by remember { mutableStateOf(false) }
            if (space != null) MaskOverlay(state.edit.masks, state.activeMask, space, state.maskDragging)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(viewW, viewH) {
                        detectTransformGestures { centroid, panChange, zoomChange, _ ->
                            val newZoom = (zoom * zoomChange).coerceIn(1f, 8f)
                            val factor = newZoom / zoom
                            // Zoom around the fingers: keep the point under the centroid fixed.
                            val center = Offset(viewW / 2, viewH / 2)
                            pan = (pan - (centroid - center)) * factor + (centroid - center) + panChange
                            zoom = newZoom
                            if (zoom == 1f) pan = Offset.Zero
                        }
                    }
                    .pointerInput(viewW, viewH) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                if (zoom > 1f) {
                                    zoom = 1f; pan = Offset.Zero
                                } else {
                                    val center = Offset(viewW / 2, viewH / 2)
                                    zoom = 2.5f
                                    pan = (center - tap) * 1.5f
                                }
                            },
                            onPress = {
                                // Holding the photo shows the original; a short tap does nothing.
                                coroutineScope {
                                    val reveal = launch {
                                        delay(180)
                                        if (!handleHeld) viewModel.setShowOriginal(true)
                                    }
                                    tryAwaitRelease()
                                    reveal.cancel()
                                    viewModel.setShowOriginal(false)
                                }
                            },
                        )
                    }
                    // Last in the chain, so it sees each touch before the gestures above and can
                    // claim the ones that start on a mask handle.
                    .pointerInput(localMode) {
                        if (!localMode) return@pointerInput
                        val reach = 28.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val sp = currentSpace ?: return@awaitEachGesture
                            val masks = currentMasks
                            val (index, handle) = hitMask(sp, masks, currentSelected, down.position, reach)
                                ?: return@awaitEachGesture
                            down.consume()
                            handleHeld = true
                            if (index != currentSelected) viewModel.selectMask(index)
                            val start = masks[index].mask
                            viewModel.setMaskDragging(true)
                            try {
                                drag(down.id) { change ->
                                    viewModel.setMaskShape(sp.drag(start, handle, down.position, change.position))
                                    change.consume()
                                }
                            } finally {
                                handleHeld = false
                                viewModel.setMaskDragging(false)
                            }
                        }
                    },
            )
            state.split?.let { split ->
                SplitDivider(split, currentRect, viewModel::setSplit)
            }
        }
    }
}

@Composable
private fun SplitDivider(split: Float, rect: RectF, onSplit: (Float) -> Unit) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.primary
    val x = rect.left + split * rect.width()
    val currentRect by rememberUpdatedState(rect)
    val currentSplit by rememberUpdatedState(split)
    Canvas(Modifier.fillMaxSize()) {
        drawLine(Color.White, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = 2.dp.toPx())
        drawCircle(color, radius = 12.dp.toPx(), center = Offset(x, rect.centerY()))
    }
    val handle = 48.dp
    val handlePx = with(density) { handle.toPx() }
    Box(
        Modifier
            .offset { IntOffset((x - handlePx / 2).roundToInt(), rect.top.roundToInt()) }
            .size(handle, with(density) { rect.height().toDp() })
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onSplit(currentSplit + dragAmount / currentRect.width())
                }
            },
    )
}
