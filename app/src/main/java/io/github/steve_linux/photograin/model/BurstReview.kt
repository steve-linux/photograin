// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

internal data class BurstFrame<T>(val photo: T, val time: LocalDateTime, val camera: String?)

internal object BurstReview {
    /** Split chronologically consecutive photos when their cameras differ or the gap exceeds two seconds. */
    fun <T> groups(frames: List<BurstFrame<T>>): List<List<BurstFrame<T>>> = buildList {
        val current = mutableListOf<BurstFrame<T>>()
        for (item in frames.distinctBy { it.photo }.sortedBy { it.time }) {
            val previous = current.lastOrNull()
            if (previous != null &&
                (item.camera != previous.camera || Duration.between(previous.time, item.time) > Duration.ofSeconds(2))
            ) {
                if (current.size >= 2) add(current.toList())
                current.clear()
            }
            current.add(item)
        }
        if (current.size >= 2) add(current.toList())
    }

    /** Higher detail first, with capture order preserved for equal scores. */
    fun <T> ranked(photos: List<T>, scores: Map<T, Float>): List<T> =
        photos.mapNotNull { photo -> scores[photo]?.takeIf(Float::isFinite)?.let { photo to it } }
            .sortedByDescending { it.second }.map { it.first }

    /** Mean squared luminance Laplacian. All samples must come from an equally sized region. */
    fun sharpness(luma: FloatArray, width: Int): Float {
        if (width < 3 || luma.size % width != 0 || luma.size / width < 3) return 0f
        var total = 0f
        var count = 0
        for (y in 1 until luma.size / width - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val response = abs(4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width])
                // Small responses ignored to reduce the influence of pixel noise.
                val detail = (response - 2f).coerceAtLeast(0f)
                total += detail * detail
                count++
            }
        }
        return total / count
    }
}
