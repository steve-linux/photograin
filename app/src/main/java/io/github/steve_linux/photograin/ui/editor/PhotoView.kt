// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.RectF
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.unit.Density
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
import io.github.steve_linux.photograin.model.Frame
import io.github.steve_linux.photograin.model.MaskType
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
    val retouchMode = localMode && state.retouchMode
    val maskMode = localMode && !state.retouchMode
    // An eyedropper is on: a tap picks a color instead of its usual meaning.
    val picking = state.picker != Picker.NONE && !cropMode
    val geometry = state.edit.geometry
    val displayGeometry = if (cropMode) geometry.copy(crop = CropRect.FULL) else geometry
    val mapper = viewModel.mapper(displayGeometry) ?: return
    val content = mapper.outputSize
    // The frame is left out while cropping, so the crop handles sit on the photo's edges.
    val decorFrame = if (cropMode) Frame() else state.edit.frame
    val layout = decorFrame.layout(content)
    val textMode = state.tool == ToolGroup.EFFECTS && state.effectsMode == 2

    val renderer = remember { PreviewRenderer() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // Reset zoom whenever what is displayed changes shape.
    LaunchedEffect(image, cropMode, layout.size) {
        zoom = 1f
        pan = Offset.Zero
    }

    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val padding = with(density) { (if (cropMode) 28.dp else 8.dp).toPx() }
        val total = layout.size
        val fit = min((viewW - 2 * padding) / total.width, (viewH - 2 * padding) / total.height)
        val scale = fit * zoom
        val frameLeft = (viewW - total.width * scale) / 2 + pan.x
        val frameTop = (viewH - total.height * scale) / 2 + pan.y
        val frameRect = RectF(frameLeft, frameTop, frameLeft + total.width * scale, frameTop + total.height * scale)
        val left = frameLeft + layout.imageLeft * scale
        val top = frameTop + layout.imageTop * scale
        val rect = RectF(left, top, left + content.width * scale, top + content.height * scale)

        val compare = when {
            cropMode -> -1f
            state.showOriginal -> 2f
            state.split != null -> state.split
            else -> -1f
        }
        val frame = PreviewFrame(
            // Holding the photo shows it untouched, spots included.
            bitmap = if (state.showOriginal) image.preview else state.retouchedPreview ?: image.preview,
            base = image.base,
            geometry = displayGeometry,
            adjustments = state.edit.adjustments,
            lut = state.lut,
            imageRect = rect,
            compareSplit = compare,
            background = floatArrayOf(background.red, background.green, background.blue),
            profile = image.profile,
            masks = state.edit.masks,
            showMask = if (maskMode && (state.showMask || state.maskDragging)) state.activeMask ?: -1 else -1,
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

        if (!cropMode) {
            DecorOverlay(decorFrame, frameRect, rect, total, state.edit.texts, if (textMode) state.activeText else null)
        }

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
            val space = if (localMode || picking) {
                MaskSpace(rect, mapper.normalizedOutputToSource(), image.preview.width, image.preview.height)
            } else {
                null
            }
            val currentSpace by rememberUpdatedState(space)
            val currentMasks by rememberUpdatedState(state.edit.masks)
            val currentSelected by rememberUpdatedState(state.activeMask)
            // A finger on a mask handle: holding still must not reveal the original.
            var handleHeld by remember { mutableStateOf(false) }
            val currentBrush by rememberUpdatedState(state.brush)
            var brushCursor by remember { mutableStateOf<Offset?>(null) }
            val currentTexts by rememberUpdatedState(state.edit.texts)
            val currentText by rememberUpdatedState(state.activeText)
            val currentFrameRect by rememberUpdatedState(frameRect)
            val currentTotal by rememberUpdatedState(total)
            val currentSpots by rememberUpdatedState(state.edit.retouch)
            val currentSpot by rememberUpdatedState(state.activeSpot)
            val currentSpotSize by rememberUpdatedState(state.spotSize)
            if (space != null && retouchMode) RetouchOverlay(state.edit.retouch, state.activeSpot, space)
            if (space != null && maskMode) {
                MaskOverlay(
                    state.edit.masks, state.activeMask, space, state.maskDragging,
                    brushCursor, brushRadiusPx(state.brush.size, density),
                )
            }
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
                    .pointerInput(textMode) {
                        if (!textMode) return@pointerInput
                        val reach = 12.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val texts = currentTexts
                            val index = hitText(texts, currentTotal, currentFrameRect, down.position, reach)
                                ?: return@awaitEachGesture
                            down.consume()
                            handleHeld = true
                            if (index != currentText) viewModel.selectText(index)
                            val start = texts[index]
                            val box = currentFrameRect
                            var moved = false
                            try {
                                drag(down.id) { change ->
                                    moved = true
                                    val d = change.position - down.position
                                    viewModel.setText(
                                        start.copy(
                                            x = (start.x + d.x / box.width()).coerceIn(0f, 1f),
                                            y = (start.y + d.y / box.height()).coerceIn(0f, 1f),
                                        ),
                                    )
                                    change.consume()
                                }
                            } finally {
                                handleHeld = false
                                if (moved) viewModel.commit()
                            }
                        }
                    }
                    .pointerInput(retouchMode) {
                        if (!retouchMode) return@pointerInput
                        val reach = 24.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val sp = currentSpace ?: return@awaitEachGesture
                            val spots = currentSpots
                            val hit = hitSpot(sp, spots, currentSpot, down.position, reach)
                            if (hit != null) {
                                val (index, part) = hit
                                down.consume()
                                handleHeld = true
                                if (index != currentSpot) viewModel.selectSpot(index)
                                val start = spots[index]
                                var moved = false
                                try {
                                    drag(down.id) { change ->
                                        moved = true
                                        viewModel.setSpot(dragSpot(sp, start, part, down.position, change.position))
                                        change.consume()
                                    }
                                } finally {
                                    handleHeld = false
                                    if (moved) viewModel.commit()
                                }
                                return@awaitEachGesture
                            }
                            // A tap on the photo fixes the spot under it; anything else is zoom or pan.
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                                val c = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                                if (!c.pressed) {
                                    if (c.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                                        return@awaitEachGesture
                                    }
                                    val (x, y) = sp.toSource(down.position)
                                    if (x !in 0.0..1.0 || y !in 0.0..1.0) return@awaitEachGesture
                                    val radiusPx = brushRadiusPx(currentSpotSize, this)
                                    viewModel.addSpot(x.toFloat(), y.toFloat(), sp.unitsFor(down.position, radiusPx).toFloat())
                                    return@awaitEachGesture
                                }
                            }
                        }
                    }
                    .pointerInput(maskMode, picking) {
                        if (!maskMode || picking) return@pointerInput
                        val reach = 28.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val sp = currentSpace ?: return@awaitEachGesture
                            val masks = currentMasks
                            val selected = currentSelected
                            if (selected != null && masks[selected].mask.type == MaskType.BRUSH) {
                                // One finger paints; a second one before it moves means zoom or pan.
                                handleHeld = true
                                var painting = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                                        val c = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                        if (!c.pressed) return@awaitEachGesture
                                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                            c.consume()
                                            break
                                        }
                                    }
                                    val radiusPx = brushRadiusPx(currentBrush.size, this)
                                    val (x, y) = sp.toSource(down.position)
                                    viewModel.beginStroke(x.toFloat(), y.toFloat(), sp.unitsFor(down.position, radiusPx).toFloat())
                                    painting = true
                                    brushCursor = down.position
                                    drag(down.id) { change ->
                                        val (cx, cy) = sp.toSource(change.position)
                                        viewModel.extendStroke(cx.toFloat(), cy.toFloat())
                                        brushCursor = change.position
                                        change.consume()
                                    }
                                } finally {
                                    handleHeld = false
                                    brushCursor = null
                                    if (painting) viewModel.endStroke()
                                }
                                return@awaitEachGesture
                            }
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
                    }
                    .pointerInput(picking) {
                        if (!picking) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val sp = currentSpace ?: return@awaitEachGesture
                            // A tap picks the color under it; anything else is zoom or pan.
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                                val c = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                                if (!c.pressed) {
                                    val (x, y) = sp.toSource(down.position)
                                    if (x in 0.0..1.0 && y in 0.0..1.0) viewModel.pickAt(x.toFloat(), y.toFloat())
                                    return@awaitEachGesture
                                }
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

/** Brush radius on screen for a brush [size] of 0..1. */
private fun brushRadiusPx(size: Float, density: Density): Float = with(density) { (6 + 74 * size).dp.toPx() }
