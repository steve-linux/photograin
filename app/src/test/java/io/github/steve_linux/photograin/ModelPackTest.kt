// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.BaseOrientation
import io.github.steve_linux.photograin.model.ModelPack
import io.github.steve_linux.photograin.model.SubjectMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.security.MessageDigest

class ModelPackTest {
    @Test
    fun packsAreKnownByTheirChecksum() {
        assertSame(ModelPack.ISNET, ModelPack.bySha256(ModelPack.ISNET.sha256.uppercase()))
        assertNull(ModelPack.bySha256(ModelPack.hex(MessageDigest.getInstance("SHA-256").digest(ByteArray(0)))))
    }

    @Test
    fun checksumsAreHex() {
        val empty = MessageDigest.getInstance("SHA-256").digest(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ModelPack.hex(empty))
        for (pack in ModelPack.all) assertEquals(64, pack.sha256.length)
    }

    @Test
    fun subjectModelsOfAnySize() {
        // A 1024 model sees the grid as it is: each pixel of an upright 8 x 8 image stays put.
        val pixels = IntArray(64) { (0xFF shl 24) or it }
        val input = SubjectMask.modelInput(pixels, 8, 8, BaseOrientation(), 8)
        for (i in 0 until 64) assertEquals(i, input[i] and 0xFFFFFF)
        val mask = SubjectMask.fromModel(ByteArray(64) { if (it % 8 < 4) -1 else 0 }, BaseOrientation(), 8, 8, 8)
        assertEquals(255, mask.data[0].toInt() and 0xFF)
        assertEquals(0, mask.data[7].toInt() and 0xFF)
    }
}
