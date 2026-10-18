// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

/** IEEE 754 half-precision conversions for the bits of RGBA_F16 pixels and GL half-float reads. */
object HalfFloats {
    fun toFloat(bits: Short): Float {
        val h = bits.toInt() and 0xFFFF
        val sign = (h and 0x8000) shl 16
        val exp = (h ushr 10) and 0x1F
        val mant = h and 0x3FF
        val f = when (exp) {
            0 -> return (if (sign != 0) -1f else 1f) * mant * SUBNORMAL
            0x1F -> sign or 0x7F800000 or (mant shl 13)
            else -> sign or ((exp + 112) shl 23) or (mant shl 13)
        }
        return Float.fromBits(f)
    }

    /** Rounds to the nearest half; values too large become infinity. */
    fun fromFloat(v: Float): Short {
        val f = v.toRawBits()
        val sign = (f ushr 16) and 0x8000
        val exp = (f ushr 23) and 0xFF
        val mant = f and 0x7FFFFF
        if (exp == 0xFF) return (sign or 0x7C00 or (if (mant != 0) 0x200 else 0)).toShort()
        val e = exp - 112
        if (e >= 0x1F) return (sign or 0x7C00).toShort()
        if (e <= 0) {
            // Subnormal or zero: the implicit bit joins the mantissa, shifted into place.
            if (e < -10) return sign.toShort()
            val m = mant or 0x800000
            val shift = 14 - e
            val rounded = (m + (1 shl (shift - 1))) shr shift
            return (sign or rounded).toShort()
        }
        val rounded = ((e shl 10) or (mant shr 13)) + ((mant shr 12) and 1)
        return (sign or rounded).toShort()
    }

    private const val SUBNORMAL = 1f / (1 shl 24)
}
