// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Deflater

/**
 * Writes 16-bit RGB TIFF files: little-endian, Deflate with horizontal prediction, in strips,
 * with an optional ICC profile and the EXIF tags of a TIFF-form EXIF block.
 */
object TiffWriter {
    /** One IFD entry. [data] holds the values little-endian; [sub] is the IFD a pointer tag leads to. */
    internal class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray, val sub: List<Entry>? = null)

    private const val SHORT = 3
    private const val LONG = 4
    private const val ASCII = 2
    private const val RATIONAL = 5
    private const val UNDEFINED = 7

    private const val TAG_EXIF_IFD = 34665
    private const val TAG_GPS_IFD = 34853

    /** Tags of the main IFD taken from the EXIF block: who and what made the photo, and when. */
    private val MAIN_TAGS = setOf(271, 272, 306, 315, 33432)

    /** EXIF IFD tags that describe the old file rather than the photo: sizes, interop, maker notes. */
    private val SKIPPED_EXIF_TAGS = setOf(37500, 40962, 40963, 40965)

    private const val STRIP_BYTES = 256 * 1024

    /**
     * Writes a [width] x [height] image to [file]. [row] fills its array with one row of
     * 16-bit samples (R, G, B per pixel, 0..65535) for the given y, top to bottom.
     */
    fun write(
        file: File,
        width: Int,
        height: Int,
        icc: ByteArray?,
        exif: ByteArray?,
        software: String,
        row: (y: Int, samples: ShortArray) -> Unit,
    ) {
        require(width > 0 && height > 0)
        val rowBytes = width * 6
        val rowsPerStrip = (STRIP_BYTES / rowBytes).coerceIn(1, height)
        val strips = (height + rowsPerStrip - 1) / rowsPerStrip
        val offsets = LongArray(strips)
        val counts = LongArray(strips)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0, 0, 0, 0, 0))
            val samples = ShortArray(width * 3)
            val raw = ByteArray(rowBytes * rowsPerStrip)
            val deflater = Deflater(6)
            val buffer = ByteArray(64 * 1024)
            for (s in 0 until strips) {
                val y0 = s * rowsPerStrip
                val rows = minOf(rowsPerStrip, height - y0)
                for (r in 0 until rows) {
                    row(y0 + r, samples)
                    predict(samples, width)
                    var o = r * rowBytes
                    for (v in samples) {
                        raw[o] = v.toByte()
                        raw[o + 1] = (v.toInt() shr 8).toByte()
                        o += 2
                    }
                }
                offsets[s] = raf.filePointer
                deflater.reset()
                deflater.setInput(raw, 0, rows * rowBytes)
                deflater.finish()
                while (!deflater.finished()) {
                    val n = deflater.deflate(buffer)
                    raf.write(buffer, 0, n)
                }
                counts[s] = raf.filePointer - offsets[s]
            }
            deflater.end()
            if (raf.filePointer % 2 != 0L) raf.write(0)

            val entries = ArrayList<Entry>()
            entries += long(256, width.toLong())
            entries += long(257, height.toLong())
            entries += shorts(258, 16, 16, 16)
            entries += shorts(259, 8)
            entries += shorts(262, 2)
            entries += longs(273, offsets)
            entries += shorts(277, 3)
            entries += long(278, rowsPerStrip.toLong())
            entries += longs(279, counts)
            entries += rational(282, 72, 1)
            entries += rational(283, 72, 1)
            entries += shorts(284, 1)
            entries += shorts(296, 2)
            entries += ascii(305, software)
            entries += shorts(317, 2)
            if (icc != null) entries += Entry(34675, UNDEFINED, icc.size, icc)
            exif?.let { runCatching { exifEntries(it) }.getOrNull() }?.let { entries += it }

            val start = raf.filePointer
            raf.write(layout(entries.sortedBy { it.tag }, start))
            raf.seek(4)
            raf.write(le32(start))
        }
    }

    /** Replaces each sample by its difference from the same channel of the pixel to its left. */
    internal fun predict(samples: ShortArray, width: Int) {
        for (x in width - 1 downTo 1) for (c in 0 until 3) {
            val i = x * 3 + c
            samples[i] = (samples[i] - samples[i - 3]).toShort()
        }
    }

    /**
     * The main IFD's maker, model, date, artist and copyright plus the EXIF and GPS IFDs of a
     * TIFF-form EXIF block, values converted to little-endian.
     */
    internal fun exifEntries(block: ByteArray): List<Entry> {
        val reader = IfdReader(block)
        val out = ArrayList<Entry>()
        for (e in reader.read(reader.u32(4).toInt(), 0)) {
            when {
                e.tag in MAIN_TAGS -> out += e
                e.sub != null && (e.tag == TAG_EXIF_IFD || e.tag == TAG_GPS_IFD) -> {
                    val kept = e.sub.filter { it.tag !in SKIPPED_EXIF_TAGS && it.sub == null }
                    if (kept.isNotEmpty()) out += Entry(e.tag, LONG, 1, ByteArray(4), kept)
                }
            }
        }
        return out
    }

    private class IfdReader(val b: ByteArray) {
        val little = b.size >= 8 && b[0] == 'I'.code.toByte()

        fun u16(o: Int): Int = if (little) {
            (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        } else {
            ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
        }

        fun u32(o: Int): Long = if (little) {
            u16(o).toLong() or (u16(o + 2).toLong() shl 16)
        } else {
            (u16(o).toLong() shl 16) or u16(o + 2).toLong()
        }

        fun read(offset: Int, depth: Int): List<Entry> {
            if (offset <= 0 || offset + 2 > b.size || depth > 2) return emptyList()
            val n = u16(offset)
            val out = ArrayList<Entry>()
            for (i in 0 until n) {
                val p = offset + 2 + i * 12
                if (p + 12 > b.size) break
                val tag = u16(p)
                val type = u16(p + 2)
                val count = u32(p + 4)
                val size = typeSize(type)
                if (size == 0 || count > 1_000_000) continue
                val bytes = (count * size).toInt()
                val at = if (bytes <= 4) p + 8 else u32(p + 8).toInt()
                if (at < 0 || at + bytes > b.size) continue
                if (tag == TAG_EXIF_IFD || tag == TAG_GPS_IFD || tag == 40965) {
                    out += Entry(tag, LONG, 1, ByteArray(4), read(u32(p + 8).toInt(), depth + 1))
                    continue
                }
                out += Entry(tag, type, count.toInt(), toLittle(at, bytes, type))
            }
            return out
        }

        /** The value bytes with each element's byte order swapped to little-endian. */
        private fun toLittle(at: Int, bytes: Int, type: Int): ByteArray {
            val data = b.copyOfRange(at, at + bytes)
            if (little) return data
            // Rationals are two 32-bit numbers; doubles are one 8-byte number.
            val unit = when (type) {
                3, 8 -> 2
                4, 9, 5, 10, 11 -> 4
                12 -> 8
                else -> 1
            }
            if (unit > 1) {
                var i = 0
                while (i + unit <= data.size) {
                    data.reverse(i, i + unit)
                    i += unit
                }
            }
            return data
        }
    }

    private fun typeSize(type: Int) = when (type) {
        1, 2, 6, 7 -> 1
        3, 8 -> 2
        4, 9, 11 -> 4
        5, 10, 12 -> 8
        else -> 0
    }

    /** The bytes of an IFD placed at file offset [start], followed by its values and sub-IFDs. */
    internal fun layout(entries: List<Entry>, start: Long): ByteArray {
        val head = 2 + entries.size * 12 + 4
        val data = ByteArrayOutputStream()
        val inline = arrayOfNulls<ByteArray>(entries.size)
        for ((i, e) in entries.withIndex()) {
            if (e.sub != null) continue
            if (e.data.size <= 4) {
                inline[i] = e.data.copyOf(4)
            } else {
                inline[i] = le32(start + head + data.size())
                data.write(e.data)
                if (data.size() % 2 != 0) data.write(0)
            }
        }
        for ((i, e) in entries.withIndex()) {
            val sub = e.sub ?: continue
            val at = start + head + data.size()
            inline[i] = le32(at)
            data.write(layout(sub.sortedBy { it.tag }, at))
        }
        val out = ByteArrayOutputStream(head + data.size())
        out.write(le16(entries.size))
        for ((i, e) in entries.withIndex()) {
            out.write(le16(e.tag))
            out.write(le16(e.type))
            out.write(le32(e.count.toLong()))
            out.write(inline[i]!!)
        }
        out.write(le32(0))
        data.writeTo(out)
        return out.toByteArray()
    }

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun long(tag: Int, v: Long) = Entry(tag, LONG, 1, le32(v))

    private fun longs(tag: Int, v: LongArray) =
        Entry(tag, LONG, v.size, ByteArray(v.size * 4).also { b -> v.forEachIndexed { i, x -> le32(x).copyInto(b, i * 4) } })

    private fun shorts(tag: Int, vararg v: Int) =
        Entry(tag, SHORT, v.size, ByteArray(v.size * 2).also { b -> v.forEachIndexed { i, x -> le16(x).copyInto(b, i * 2) } })

    private fun rational(tag: Int, num: Long, den: Long) = Entry(tag, RATIONAL, 1, le32(num) + le32(den))

    private fun ascii(tag: Int, s: String): Entry {
        val b = s.toByteArray(Charsets.US_ASCII) + 0
        return Entry(tag, ASCII, b.size, b)
    }
}
