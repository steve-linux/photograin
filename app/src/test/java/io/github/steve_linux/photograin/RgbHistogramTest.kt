// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.render.RgbHistogram
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbHistogramTest {
    @Test
    fun readsUnsignedRgbAndExcludesPixelsHiddenByCutouts() {
        val pixels = ByteBuffer.allocateDirect(16)
        pixels.put(byteArrayOf(
            0, -1, -128, -1,
            -1, 0, -1, 0,
            0, 0, 0, 127,
            -1, -128, 0, -128,
        ))
        pixels.flip()
        val histogram = RgbHistogram.fromRgba(pixels)

        assertEquals(2, histogram.red.sum())
        assertEquals(2, histogram.green.sum())
        assertEquals(2, histogram.blue.sum())
        assertEquals(1, histogram.red[0])
        assertEquals(1, histogram.red[255])
        assertEquals(1, histogram.green[128])
        assertEquals(1, histogram.green[255])
        assertEquals(1, histogram.blue[0])
        assertEquals(1, histogram.blue[128])
        assertEquals(0, histogram.blue[255])
    }
}
