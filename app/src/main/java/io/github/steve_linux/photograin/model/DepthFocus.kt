// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlin.math.floor

/** A normalized grayscale depth map stored in the same orientation as the source pixels. */
@Serializable
data class DepthMap(val encoded: String) {
    internal val raster: SubjectMask? by lazy {
        if (encoded.length != (SIZE * SIZE + 2) / 3 * 4) return@lazy null
        val data = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
        data?.takeIf { it.size == SIZE * SIZE }?.let { SubjectMask(SIZE, SIZE, it) }
    }

    internal fun at(x: Float, y: Float): Float? {
        val values = raster?.data ?: return null
        val sx = (x * SIZE - 0.5f).coerceIn(0f, SIZE - 1f)
        val sy = (y * SIZE - 0.5f).coerceIn(0f, SIZE - 1f)
        val ix = floor(sx).toInt()
        val iy = floor(sy).toInt()
        val nx = (ix + 1).coerceAtMost(SIZE - 1)
        val ny = (iy + 1).coerceAtMost(SIZE - 1)
        fun sample(col: Int, row: Int) = (values[row * SIZE + col].toInt() and 255) / 255f
        val top = sample(ix, iy) * (1f - (sx - ix)) + sample(nx, iy) * (sx - ix)
        val bottom = sample(ix, ny) * (1f - (sx - ix)) + sample(nx, ny) * (sx - ix)
        return top * (1f - (sy - iy)) + bottom * (sy - iy)
    }

    companion object {
        internal const val SIZE = 128

        internal fun fromUpright(values: ByteArray, base: BaseOrientation): DepthMap {
            require(values.size == SIZE * SIZE)
            val stored = ByteArray(values.size)
            val indices = SubjectMask.uprightToStored(base, SIZE)
            for (i in values.indices) stored[indices[i]] = values[i]
            return DepthMap(Base64.getEncoder().encodeToString(stored))
        }
    }
}

@Serializable
data class DepthFocus(
    val map: DepthMap,
    val center: Float = 0.5f,
    val width: Float = 0.2f,
    val transition: Float = 0.25f,
)
