// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared preview/export grid, in stored source coordinates. Root invert/range stay in the shader. */
class CompoundRaster(private val sourceWidth: Int, private val sourceHeight: Int) {
    private val raster = BrushRaster(sourceWidth, sourceHeight)
    val width get() = raster.width
    val height get() = raster.height
    val data = ByteArray(width * height)
    private var previous: Mask? = null
    private var previousBases: List<SubjectMask?> = emptyList()

    fun sync(mask: Mask, subjects: Subjects?): Boolean {
        // These two controls are shader-only and must not cause CPU rasterization.
        val shape = mask.copy(invert = false, range = MaskRange())
        val selections = shape.selections()
        val bases = selections.map { subjects?.of(it) }
        if (shape == previous && bases == previousBases) return false
        if (shape.parts.isEmpty() && LocalAdjustment.usesRaster(shape.type)) {
            raster.sync(shape.strokes, bases[0]?.resizedTo(width, height)?.data)
            raster.data.copyInto(data)
        } else {
            val long = max(sourceWidth, sourceHeight).toFloat()
            val ax = sourceWidth / long
            val ay = sourceHeight / long
            selections.forEachIndexed { index, selection ->
                val layer = BrushRaster(sourceWidth, sourceHeight)
                val base = if (LocalAdjustment.usesRaster(selection.type)) {
                    bases[index]?.resizedTo(width, height)?.data
                } else {
                    ByteArray(width * height) { i ->
                        (selection.weight((i % width + 0.5f) / width, (i / width + 0.5f) / height, ax, ay) * 255).roundToInt().toByte()
                    }
                }
                layer.sync(selection.strokes, base)
                if (index == 0) layer.data.copyInto(data) else {
                    val operation = shape.parts[index - 1].operation
                    for (i in data.indices) {
                        val a = data[i].toInt() and 255
                        val b = layer.data[i].toInt() and 255
                        data[i] = (if (operation == MaskOperation.ADD) max(a, b) else min(a, 255 - b)).toByte()
                    }
                }
            }
        }
        previous = shape
        previousBases = bases
        return true
    }
}
