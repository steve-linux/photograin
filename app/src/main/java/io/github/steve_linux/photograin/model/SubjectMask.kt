// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the main subject of a photo is, found by the segmentation model: 0 for background to
 * 255 for subject, over the whole source as stored in the file. It has the size of a
 * [BrushRaster] for that source, so it can seed a brush mask that refines it.
 */
class SubjectMask(val width: Int, val height: Int, val data: ByteArray) {
    init {
        require(data.size == width * height) { "Expected ${width * height} values, got ${data.size}" }
    }

    @Volatile
    private var resized: SubjectMask? = null

    /** This mask at [w] x [h], resampled bilinearly; the last size asked for is kept. */
    fun resizedTo(w: Int, h: Int): SubjectMask {
        if (w == width && h == height) return this
        resized?.takeIf { it.width == w && it.height == h }?.let { return it }
        val out = ByteArray(w * h)
        val values = FloatArray(width * height) { (data[it].toInt() and 0xFF).toFloat() }
        for (y in 0 until h) for (x in 0 until w) {
            out[y * w + x] = bilinear(values, width, height, (x + 0.5f) * width / w - 0.5f, (y + 0.5f) * height / h - 0.5f)
                .roundToInt().coerceIn(0, 255).toByte()
        }
        return SubjectMask(w, h, out).also { resized = it }
    }

    companion object {
        /** The model sees the photo squeezed to this square. */
        const val MODEL_SIZE = 320

        /**
         * Model values below this count as background and above [SUBJECT] as subject: the
         * small model is often unsure on thin parts such as arms, which would otherwise come
         * out half transparent.
         */
        const val BACKGROUND = 0.1f
        const val SUBJECT = 0.5f

        /**
         * The model's input from the [w] x [h] ARGB [pixels] of a photo stored with orientation
         * [base]: squeezed to [MODEL_SIZE] squared, each value the average of the pixels it
         * covers, and turned upright, the way the model was trained to see subjects.
         */
        fun modelInput(pixels: IntArray, w: Int, h: Int, base: BaseOrientation): IntArray {
            val n = MODEL_SIZE
            val stored = IntArray(n * n)
            for (ty in 0 until n) {
                val y0 = ty * h / n
                val y1 = max(y0 + 1, (ty + 1) * h / n)
                for (tx in 0 until n) {
                    val x0 = tx * w / n
                    val x1 = max(x0 + 1, (tx + 1) * w / n)
                    var r = 0
                    var g = 0
                    var b = 0
                    for (y in y0 until y1) for (x in x0 until x1) {
                        val c = pixels[y * w + x]
                        r += c shr 16 and 0xFF
                        g += c shr 8 and 0xFF
                        b += c and 0xFF
                    }
                    val count = (y1 - y0) * (x1 - x0)
                    stored[ty * n + tx] = (0xFF shl 24) or ((r + count / 2) / count shl 16) or
                        ((g + count / 2) / count shl 8) or ((b + count / 2) / count)
                }
            }
            val upright = uprightToStored(base)
            return IntArray(n * n) { stored[upright[it]] }
        }

        /**
         * The mask for a source whose [BrushRaster] grid is [width] x [height], from the
         * model's upright output [raw] ([MODEL_SIZE] squared bytes) for orientation [base].
         */
        fun fromModel(raw: ByteArray, base: BaseOrientation, width: Int, height: Int): SubjectMask {
            val n = MODEL_SIZE
            require(raw.size == n * n) { "Expected ${n * n} model values, got ${raw.size}" }
            val upright = uprightToStored(base)
            val stored = FloatArray(n * n)
            for (i in 0 until n * n) stored[upright[i]] = (raw[i].toInt() and 0xFF) / 255f
            val out = ByteArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                val v = bilinear(stored, n, n, (x + 0.5f) * n / width - 0.5f, (y + 0.5f) * n / height - 0.5f)
                out[y * width + x] = (curve(v) * 255f).roundToInt().toByte()
            }
            return SubjectMask(width, height, out)
        }

        /** Model value 0..1 to mask strength 0..1, see [BACKGROUND] and [SUBJECT]. */
        fun curve(v: Float): Float {
            val t = ((v - BACKGROUND) / (SUBJECT - BACKGROUND)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /**
         * For each pixel of the upright model square, the index of the same pixel in the square
         * as stored. Upright is the stored image mirrored if [BaseOrientation.mirror], then
         * turned clockwise [BaseOrientation.quarterTurns] times.
         */
        fun uprightToStored(base: BaseOrientation): IntArray {
            val n = MODEL_SIZE
            val turns = base.quarterTurns.mod(4)
            return IntArray(n * n) { i ->
                var x = i % n
                var y = i / n
                // A clockwise turn moves (x, y) to (n - 1 - y, x); undo them one by one.
                repeat(turns) {
                    val px = y
                    y = n - 1 - x
                    x = px
                }
                if (base.mirror) x = n - 1 - x
                y * n + x
            }
        }

        private fun bilinear(v: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
            val x = fx.coerceIn(0f, (w - 1).toFloat())
            val y = fy.coerceIn(0f, (h - 1).toFloat())
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val x1 = min(x0 + 1, w - 1)
            val y1 = min(y0 + 1, h - 1)
            val ax = x - x0
            val ay = y - y0
            val top = v[y0 * w + x0] * (1 - ax) + v[y0 * w + x1] * ax
            val bottom = v[y1 * w + x0] * (1 - ax) + v[y1 * w + x1] * ax
            return top * (1 - ay) + bottom * ay
        }
    }
}

/**
 * What the subject masks of an edit cover before their strokes: the main subject for those
 * without [Mask.points], and the object selected by each set of points.
 */
class Subjects(val main: SubjectMask? = null, val objects: Map<List<Float>, SubjectMask> = emptyMap()) {
    /** The base of the subject mask [mask], or null until it is known. */
    fun of(mask: Mask): SubjectMask? = if (mask.points.isEmpty()) main else objects[mask.points]
}
