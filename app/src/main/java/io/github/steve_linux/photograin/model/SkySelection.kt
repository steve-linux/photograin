// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/** Postprocessing of the dedicated sky model. Probabilities must never be min/max stretched. */
object SkySelection {
    const val MODEL_SIZE = 384

    /** No-sky photos remain empty; uncertain edges have a smooth, narrower transition. */
    fun fromModel(raw: ByteArray, base: BaseOrientation, width: Int, height: Int, n: Int = MODEL_SIZE): SubjectMask {
        require(n > 0 && raw.size.toLong() == n.toLong() * n)
        require(width > 0 && height > 0)
        // Reject isolated high-confidence flecks, without requiring sky to touch an image border.
        val confident = raw.count { (it.toInt() and 255) >= 166 }
        if (confident < maxOf(1, raw.size / 500)) return SubjectMask(width, height, ByteArray(width * height))
        return SubjectMask.fromModel(raw, base, width, height, n, background = 0.35f, foreground = 0.65f)
    }
}
