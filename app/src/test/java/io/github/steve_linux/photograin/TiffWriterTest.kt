// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.data.HalfFloats
import io.github.steve_linux.photograin.data.TiffWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TiffWriterTest {
    private fun sample(x: Int, y: Int, c: Int) = ((x * 997 + y * 6151 + c * 20011) % 65536)

    /** Tag -> (type, count, value bytes) of the IFD at [offset]. */
    private fun ifd(b: ByteBuffer, offset: Int): Map<Int, Triple<Int, Int, ByteArray>> {
        val n = b.getShort(offset).toInt() and 0xFFFF
        return (0 until n).associate { i ->
            val p = offset + 2 + i * 12
            val type = b.getShort(p + 2).toInt()
            val count = b.getInt(p + 4)
            val size = count * when (type) { 3 -> 2; 4 -> 4; 5 -> 8; else -> 1 }
            val at = if (size <= 4) p + 8 else b.getInt(p + 8)
            val bytes = ByteArray(size).also { arr -> for (k in arr.indices) arr[k] = b.get(at + k) }
            (b.getShort(p).toInt() and 0xFFFF) to Triple(type, count, bytes)
        }
    }

    private fun ints(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().let { IntArray(it.remaining()) { i -> it[i] } }

    @Test
    fun pixelsRoundTrip() {
        val w = 300
        val h = 500 // more than one strip
        val icc = ByteArray(301) { it.toByte() }
        val file = File.createTempFile("tiff", ".tif")
        try {
            TiffWriter.write(file, w, h, icc, null, "Photograin") { y, s ->
                for (x in 0 until w) for (c in 0 until 3) s[x * 3 + c] = sample(x, y, c).toShort()
            }
            val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x4949, b.getShort(0).toInt())
            assertEquals(42, b.getShort(2).toInt())
            val tags = ifd(b, b.getInt(4))
            assertEquals(tags.keys.sorted(), tags.keys.toList())
            assertEquals(w, ints(tags.getValue(256).third)[0])
            assertArrayEquals(icc, tags.getValue(34675).third)
            val rowsPerStrip = ints(tags.getValue(278).third)[0]
            val offsets = ints(tags.getValue(273).third)
            val counts = ints(tags.getValue(279).third)
            assertEquals((h + rowsPerStrip - 1) / rowsPerStrip, offsets.size)
            val raw = ByteArrayOutputStream()
            for (i in offsets.indices) {
                val inflater = Inflater()
                inflater.setInput(file.readBytes(), offsets[i], counts[i])
                val buf = ByteArray(65536)
                while (!inflater.finished()) raw.write(buf, 0, inflater.inflate(buf))
                inflater.end()
            }
            val px = ByteBuffer.wrap(raw.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            assertEquals(w * h * 3, px.remaining())
            for (y in 0 until h) {
                val prev = IntArray(3)
                for (x in 0 until w) for (c in 0 until 3) {
                    val v = (prev[c] + px[(y * w + x) * 3 + c]) and 0xFFFF
                    prev[c] = v
                    if (v != sample(x, y, c)) throw AssertionError("pixel $x,$y channel $c: $v")
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun exifFromBigEndianBlock() {
        // MM header, IFD0 with Make, Orientation and an EXIF pointer; the EXIF IFD holds an
        // FNumber rational, the filler light source ExifInterface adds and a pixel width, which
        // describes the old file: those two are dropped.
        val b = ByteBuffer.allocate(200).order(ByteOrder.BIG_ENDIAN)
        b.put('M'.code.toByte()).put('M'.code.toByte()).putShort(42).putInt(8)
        b.putShort(3)
        b.putShort(271).putShort(2).putInt(6).putInt(140)
        b.putShort(274).putShort(3).putInt(1).putShort(6).putShort(0)
        b.putShort(34665.toShort()).putShort(4).putInt(1).putInt(60)
        b.putInt(0)
        b.position(60)
        b.putShort(3)
        b.putShort(33437.toShort()).putShort(5).putInt(1).putInt(120)
        b.putShort(37384.toShort()).putShort(4).putInt(1).putInt(0)
        b.putShort(40962.toShort()).putShort(4).putInt(1).putInt(4000)
        b.putInt(0)
        b.position(140)
        b.put("Canon\u0000".toByteArray())
        b.position(120)
        b.putInt(28).putInt(10)

        val entries = TiffWriter.exifEntries(b.array())
        assertEquals(listOf(271, 34665), entries.map { it.tag })
        assertEquals("Canon", String(entries[0].data, 0, 5))
        val exif = entries[1].sub!!
        assertEquals(listOf(33437), exif.map { it.tag })
        assertArrayEquals(byteArrayOf(28, 0, 0, 0, 10, 0, 0, 0), exif[0].data)

        // Laid out, the pointer leads to an IFD holding the rational.
        val start = 1000L
        val out = ByteBuffer.wrap(TiffWriter.layout(entries, start)).order(ByteOrder.LITTLE_ENDIAN)
        val pointerEntry = 2 + 12
        val sub = out.getInt(pointerEntry + 8) - start.toInt()
        assertEquals(1, out.getShort(sub).toInt())
        val valueAt = out.getInt(sub + 2 + 8) - start.toInt()
        assertEquals(28, out.getInt(valueAt))
        assertEquals(10, out.getInt(valueAt + 4))
    }

    @Test
    fun halfFloats() {
        // Known encodings: 1, 0.5, -2, the largest half, the smallest subnormal.
        val known = mapOf(1f to 0x3C00, 0.5f to 0x3800, -2f to 0xC000, 65504f to 0x7BFF, 5.9604645e-8f to 0x0001, 0f to 0)
        for ((v, bits) in known) {
            assertEquals(bits.toShort(), HalfFloats.fromFloat(v))
            assertEquals(v, HalfFloats.toFloat(bits.toShort()), 0f)
        }
        // Every finite half survives the round trip, and 8-bit levels land within half a 16-bit step.
        for (bits in 0 until 0x7C00) {
            val h = bits.toShort()
            assertEquals(h, HalfFloats.fromFloat(HalfFloats.toFloat(h)))
        }
        for (level in 0..255) {
            val v = HalfFloats.toFloat(HalfFloats.fromFloat(level / 255f))
            assertEquals(level / 255f, v, 1f / 2048)
        }
        assertEquals(0x7C00.toShort(), HalfFloats.fromFloat(1e6f))
    }
}
