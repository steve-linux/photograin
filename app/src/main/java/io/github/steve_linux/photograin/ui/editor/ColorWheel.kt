// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ring width as a share of the wheel's radius. */
private const val RING = 0.18f

/** Side of the bitmap the triangle is drawn into, scaled to fit the wheel. */
private const val TRIANGLE_PX = 360

/**
 * A hue ring with a saturation/value triangle inside, as in GIMP and GTK: the triangle's
 * corners are the pure hue, white and black. Hue runs counter-clockwise from red on the right.
 */
@Composable
internal fun ColorWheel(
    hue: Float,
    sat: Float,
    value: Float,
    onHue: (Float) -> Unit,
    onSatValue: (sat: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentHue by rememberUpdatedState(hue)
    val currentOnHue by rememberUpdatedState(onHue)
    val currentOnSatValue by rememberUpdatedState(onSatValue)
    val triangle = remember(hue) { triangleBitmap(hue) }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    // A touch on the ring moves the hue, anywhere inside moves the point in the triangle.
                    val onRing = (down.position - center).getDistance() > radius * (1 - RING) * 0.95f
                    fun apply(p: Offset) {
                        val d = p - center
                        if (onRing) {
                            currentOnHue(angleToHue(d))
                        } else {
                            val (s, v) = pointToSatValue(d / (radius * (1 - RING)), currentHue)
                            currentOnSatValue(s, v)
                        }
                    }
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        apply(change.position)
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val ringWidth = radius * RING
        drawCircle(
            Brush.sweepGradient(
                // Sweep goes clockwise on screen, hue counter-clockwise.
                listOf(0f, 300f, 240f, 180f, 120f, 60f, 0f).map { Color.hsv(it, 1f, 1f) },
                center,
            ),
            radius = radius - ringWidth / 2,
            style = Stroke(ringWidth),
        )
        val inner = radius - ringWidth
        val markerAt = { r: Float -> center + hueDirection(hue) * r }
        drawLine(Color.White, markerAt(inner), markerAt(radius), strokeWidth = 3.dp.toPx())

        val side = (inner * 2).toInt()
        drawImage(
            triangle,
            dstOffset = IntOffset((center.x - inner).toInt(), (center.y - inner).toInt()),
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.Medium,
        )
        val point = center + satValueToPoint(sat, value, hue) * inner
        drawCircle(Color.Black, radius = 6.dp.toPx(), center = point, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, radius = 6.dp.toPx(), center = point, style = Stroke(1.5.dp.toPx()))
    }
}

/** A labelled bar whose track shows the colors it runs through. */
@Composable
internal fun GradientBar(
    label: String,
    fraction: Float,
    colors: List<Color>,
    onChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f).height(32.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            fun apply(x: Float) = currentOnChange((x / size.width).coerceIn(0f, 1f))
                            apply(down.position.x)
                            down.consume()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                apply(change.position.x)
                                change.consume()
                            }
                        }
                    },
            ) {
                val track = 22.dp.toPx()
                val top = (size.height - track) / 2
                drawRoundRect(
                    Brush.horizontalGradient(colors),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, track),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                val x = fraction.coerceIn(0f, 1f) * size.width
                val w = 6.dp.toPx()
                val thumbTopLeft = Offset((x - w / 2).coerceIn(0f, size.width - w), 0f)
                drawRoundRect(Color.Black, thumbTopLeft, Size(w, size.height), CornerRadius(3.dp.toPx()), style = Stroke(3.dp.toPx()))
                drawRoundRect(Color.White, thumbTopLeft, Size(w, size.height), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

private fun hueDirection(hue: Float): Offset {
    val a = Math.toRadians(hue.toDouble())
    return Offset(cos(a).toFloat(), -sin(a).toFloat())
}

private fun angleToHue(d: Offset): Float {
    val deg = Math.toDegrees(atan2(-d.y.toDouble(), d.x.toDouble())).toFloat()
    return (deg + 360f) % 360f
}

/** Triangle corners on the unit circle: pure hue, white, black. */
private fun corners(hue: Float): Triple<Offset, Offset, Offset> =
    Triple(hueDirection(hue), hueDirection(hue + 240f), hueDirection(hue + 120f))

/** Point in the unit circle for a color: hue weight v·s, white v·(1−s), black 1−v. */
private fun satValueToPoint(sat: Float, value: Float, hue: Float): Offset {
    val (h, w, b) = corners(hue)
    return h * (value * sat) + w * (value * (1 - sat)) + b * (1 - value)
}

/** Nearest color in the triangle to [p], given in unit-circle coordinates. */
private fun pointToSatValue(p: Offset, hue: Float): Pair<Float, Float> {
    val (h, w, b) = corners(hue)
    var (wh, ww, wb) = barycentric(p, h, w, b)
    // Outside the triangle: drop the negative weights and share out what remains.
    wh = wh.coerceAtLeast(0f); ww = ww.coerceAtLeast(0f); wb = wb.coerceAtLeast(0f)
    val sum = wh + ww + wb
    wh /= sum; ww /= sum
    val value = (wh + ww).coerceIn(0f, 1f)
    val sat = if (value > 1e-4f) (wh / value).coerceIn(0f, 1f) else 0f
    return sat to value
}

private fun barycentric(p: Offset, a: Offset, b: Offset, c: Offset): Triple<Float, Float, Float> {
    val det = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
    val wa = ((b.y - c.y) * (p.x - c.x) + (c.x - b.x) * (p.y - c.y)) / det
    val wb = ((c.y - a.y) * (p.x - c.x) + (a.x - c.x) * (p.y - c.y)) / det
    return Triple(wa, wb, 1 - wa - wb)
}

private fun triangleBitmap(hue: Float): ImageBitmap {
    val n = TRIANGLE_PX
    val (h, w, b) = corners(hue)
    val pure = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    val pr = android.graphics.Color.red(pure) / 255f
    val pg = android.graphics.Color.green(pure) / 255f
    val pb = android.graphics.Color.blue(pure) / 255f
    val pixels = IntArray(n * n)
    // Half a pixel of slack, so the edges are not left ragged.
    val edge = -1.5f / n
    for (y in 0 until n) for (x in 0 until n) {
        val p = Offset((x + 0.5f) / n * 2 - 1, (y + 0.5f) / n * 2 - 1)
        val (wh, ww, wb) = barycentric(p, h, w, b)
        if (wh < edge || ww < edge || wb < edge) continue
        val r = (wh * pr + ww).coerceIn(0f, 1f)
        val g = (wh * pg + ww).coerceIn(0f, 1f)
        val bl = (wh * pb + ww).coerceIn(0f, 1f)
        pixels[y * n + x] = android.graphics.Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), (bl * 255).toInt())
    }
    return Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** HSV value and saturation to HSL lightness and saturation. */
internal fun hsvToHsl(sat: Float, value: Float): Pair<Float, Float> {
    val l = value * (1 - sat / 2)
    val m = min(l, 1 - l)
    return (if (m < 1e-4f) 0f else (value - l) / m) to l
}

/** HSL saturation and lightness back to HSV (saturation, value); [keepSat] when it is undefined. */
internal fun hslToHsv(sat: Float, light: Float, keepSat: Float): Pair<Float, Float> {
    val v = light + sat * min(light, 1 - light)
    val s = if (v < 1e-4f) keepSat else 2 * (1 - light / v)
    return s.coerceIn(0f, 1f) to v.coerceIn(0f, 1f)
}
