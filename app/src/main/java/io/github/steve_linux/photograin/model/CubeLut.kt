// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import kotlin.math.floor
import kotlin.math.min

/**
 * A 3D color lookup table in the Adobe/Resolve .cube format, normalized to the 0..1 domain.
 * [data] holds size³ RGB triples with red varying fastest, then green, then blue.
 */
class CubeLut(val title: String?, val size: Int, val data: FloatArray) {
    init {
        require(size in 2..MAX_SIZE) { "LUT size $size out of range" }
        require(data.size == size * size * size * 3) { "LUT has ${data.size / 3} entries, expected ${size * size * size}" }
    }

    /** Trilinear lookup of an RGB color in 0..1. */
    fun sample(r: Float, g: Float, b: Float): FloatArray {
        val n = size - 1
        val fr = r.coerceIn(0f, 1f) * n
        val fg = g.coerceIn(0f, 1f) * n
        val fb = b.coerceIn(0f, 1f) * n
        val r0 = min(floor(fr).toInt(), n - 1)
        val g0 = min(floor(fg).toInt(), n - 1)
        val b0 = min(floor(fb).toInt(), n - 1)
        val tr = fr - r0
        val tg = fg - g0
        val tb = fb - b0
        val out = FloatArray(3)
        for (c in 0..2) {
            fun at(ri: Int, gi: Int, bi: Int) = data[((bi * size + gi) * size + ri) * 3 + c]
            val c00 = at(r0, g0, b0) * (1 - tr) + at(r0 + 1, g0, b0) * tr
            val c10 = at(r0, g0 + 1, b0) * (1 - tr) + at(r0 + 1, g0 + 1, b0) * tr
            val c01 = at(r0, g0, b0 + 1) * (1 - tr) + at(r0 + 1, g0, b0 + 1) * tr
            val c11 = at(r0, g0 + 1, b0 + 1) * (1 - tr) + at(r0 + 1, g0 + 1, b0 + 1) * tr
            val c0 = c00 * (1 - tg) + c10 * tg
            val c1 = c01 * (1 - tg) + c11 * tg
            out[c] = c0 * (1 - tb) + c1 * tb
        }
        return out
    }

    companion object {
        const val MAX_SIZE = 65
        /** Size a 1D table or a non-standard domain is resampled to. */
        private const val RESAMPLED_SIZE = 33

        /** Parses .cube text; throws [IllegalArgumentException] with a reason when it is not valid. */
        fun parse(text: String): CubeLut {
            var title: String? = null
            var size3 = 0
            var size1 = 0
            val domainMin = floatArrayOf(0f, 0f, 0f)
            val domainMax = floatArrayOf(1f, 1f, 1f)
            val values = ArrayList<Float>()
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val first = line[0]
                if (first.isDigit() || first == '-' || first == '.' || first == '+') {
                    val parts = line.split(WHITESPACE)
                    require(parts.size == 3) { "Bad data line: $line" }
                    for (p in parts) values += p.toFloatOrNull() ?: throw IllegalArgumentException("Bad number: $p")
                    continue
                }
                val key = line.substringBefore(' ').uppercase()
                val rest = line.substringAfter(' ', "").trim()
                when (key) {
                    "TITLE" -> title = rest.trim('"').takeIf { it.isNotBlank() }
                    "LUT_3D_SIZE" -> size3 = rest.toIntOrNull() ?: throw IllegalArgumentException("Bad size: $rest")
                    "LUT_1D_SIZE" -> size1 = rest.toIntOrNull() ?: throw IllegalArgumentException("Bad size: $rest")
                    "DOMAIN_MIN" -> readTriple(rest, domainMin)
                    "DOMAIN_MAX" -> readTriple(rest, domainMax)
                    "LUT_1D_INPUT_RANGE", "LUT_3D_INPUT_RANGE" -> {
                        val parts = rest.split(WHITESPACE).mapNotNull { it.toFloatOrNull() }
                        require(parts.size == 2) { "Bad input range: $rest" }
                        domainMin.fill(parts[0])
                        domainMax.fill(parts[1])
                    }
                    // Other keywords carry nothing we need.
                }
            }
            require(size3 > 0 || size1 > 0) { "No LUT_3D_SIZE or LUT_1D_SIZE" }
            for (c in 0..2) require(domainMax[c] > domainMin[c]) { "Empty domain" }
            val standardDomain = domainMin.all { it == 0f } && domainMax.all { it == 1f }

            if (size3 > 0) {
                require(size3 in 2..MAX_SIZE) { "LUT_3D_SIZE $size3 is not supported (2..$MAX_SIZE)" }
                require(values.size == size3 * size3 * size3 * 3) { "Expected ${size3 * size3 * size3} entries, found ${values.size / 3}" }
                val lut = CubeLut(title, size3, values.toFloatArray())
                if (standardDomain) return lut
                // Re-index so lookups use 0..1 inputs.
                return build(title, RESAMPLED_SIZE) { r, g, b ->
                    lut.sample(norm(r, domainMin[0], domainMax[0]), norm(g, domainMin[1], domainMax[1]), norm(b, domainMin[2], domainMax[2]))
                }
            }

            require(size1 in 2..65536) { "LUT_1D_SIZE $size1 is not supported" }
            require(values.size == size1 * 3) { "Expected $size1 entries, found ${values.size / 3}" }
            val table = values.toFloatArray()
            fun curve(c: Int, v: Float): Float {
                val f = norm(v, domainMin[c], domainMax[c]) * (size1 - 1)
                val i = min(floor(f).toInt(), size1 - 2)
                val t = f - i
                return table[i * 3 + c] * (1 - t) + table[(i + 1) * 3 + c] * t
            }
            return build(title, RESAMPLED_SIZE) { r, g, b -> floatArrayOf(curve(0, r), curve(1, g), curve(2, b)) }
        }

        private val WHITESPACE = Regex("\\s+")

        private fun readTriple(text: String, into: FloatArray) {
            val parts = text.split(WHITESPACE)
            require(parts.size == 3) { "Bad domain: $text" }
            for (i in 0..2) into[i] = parts[i].toFloatOrNull() ?: throw IllegalArgumentException("Bad domain: $text")
        }

        /** Maps a 0..1 lookup input to the table's own domain position, also 0..1. */
        private fun norm(v: Float, lo: Float, hi: Float) = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)

        private fun build(title: String?, size: Int, f: (Float, Float, Float) -> FloatArray): CubeLut {
            val data = FloatArray(size * size * size * 3)
            var k = 0
            val n = (size - 1).toFloat()
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                val v = f(r / n, g / n, b / n)
                data[k++] = v[0]; data[k++] = v[1]; data[k++] = v[2]
            }
            return CubeLut(title, size, data)
        }
    }
}
