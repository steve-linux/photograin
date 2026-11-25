// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One bracketed frame as stored, before the merge decodes it. */
data class HdrFrame(
    /** Size after the EXIF orientation is applied. */
    val width: Int,
    val height: Int,
    /** RAW frames are developed to half floats: 8 bytes a pixel instead of 4. */
    val raw: Boolean,
)

/** Why a set of photos cannot be merged. */
enum class HdrProblem { TOO_FEW, TOO_MANY, DIFFERENT_SHAPES }

/** Where the native merge put each frame: see hdr_merge.h. */
data class HdrAlignment(
    val reference: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val dx: List<Int>,
    val dy: List<Int>,
) {
    /** Whether any frame had to be moved: the photos were handheld. */
    val shifted: Boolean get() = dx.any { it != 0 } || dy.any { it != 0 }

    /** The largest move, in pixels of the merged frames. */
    val largestShift: Int get() = dx.indices.maxOfOrNull { max(abs(dx[it]), abs(dy[it])) } ?: 0

    companion object {
        /** Reads [reference, x, y, width, height, dx0, dy0, dx1, dy1, ...]. */
        fun fromNative(v: IntArray): HdrAlignment {
            require(v.size >= 7 && (v.size - 5) % 2 == 0) { "Malformed alignment" }
            val n = (v.size - 5) / 2
            return HdrAlignment(
                reference = v[0], x = v[1], y = v[2], width = v[3], height = v[4],
                dx = List(n) { v[5 + 2 * it] }, dy = List(n) { v[6 + 2 * it] },
            )
        }
    }
}

/** Checks and sizes an HDR merge of bracketed exposures before any pixel is decoded. */
object HdrPlan {
    const val MIN_FRAMES = 2

    /** Brackets rarely go past 9 shots, and each frame costs memory. */
    const val MAX_FRAMES = 9

    /** Native working memory per merged pixel (pyramids and weights), measured on a 12 MP merge. */
    const val WORK_BYTES_PER_PIXEL = 44L

    /** Frames shot in portrait and landscape, or cropped, cannot be stacked: within 1% is the same shape. */
    private const val SHAPE_TOLERANCE = 0.01

    fun problem(frames: List<HdrFrame>): HdrProblem? = when {
        frames.size < MIN_FRAMES -> HdrProblem.TOO_FEW
        frames.size > MAX_FRAMES -> HdrProblem.TOO_MANY
        frames.any { !sameShape(it, frames[0]) } -> HdrProblem.DIFFERENT_SHAPES
        else -> null
    }

    private fun sameShape(a: HdrFrame, b: HdrFrame): Boolean {
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        return abs(ra - rb) <= SHAPE_TOLERANCE * rb
    }

    /** Bytes the merge needs at [scale] of the first frame's size. */
    fun bytesNeeded(frames: List<HdrFrame>, scale: Float = 1f): Long {
        val first = frames[0]
        val pixels = (first.width * scale).roundToInt().toLong() * (first.height * scale).roundToInt()
        val frameBytes = frames.sumOf { if (it.raw) 8L else 4L }
        // The output is 4 bytes a pixel; one frame is briefly held twice while it is rotated upright.
        return pixels * (WORK_BYTES_PER_PIXEL + frameBytes + 4 + if (frames.any { it.raw }) 8 else 4)
    }

    /**
     * The scale (at most 1) that fits the merge in [available] bytes, so a phone with little free
     * memory still gets a merge, only smaller. Never below 1/4.
     */
    fun scaleFor(frames: List<HdrFrame>, available: Long): Float {
        val full = bytesNeeded(frames)
        if (full <= available) return 1f
        val scale = sqrt(available.toDouble() / full).toFloat()
        // Rounded down to a hundredth so the estimate stays under the budget.
        return max(0.25f, min(1f, (scale * 100).toInt() / 100f))
    }

    /** Merged size in megapixels, for the screen. */
    fun megapixels(frames: List<HdrFrame>, scale: Float): Float {
        val first = frames[0]
        return first.width * scale * first.height * scale / 1_000_000f
    }

    /**
     * The merged photo's file name: the reference frame's name with "_HDR", or with dated names
     * "Photograin_HDR_" and the time.
     */
    fun fileName(original: String?, dated: Boolean, now: Date = Date()): String {
        val base = if (dated) {
            "Photograin_HDR_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        } else {
            (original?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "photo") + "_HDR"
        }
        return "$base.jpg"
    }
}
