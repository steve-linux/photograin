// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Frame
import io.github.steve_linux.photograin.model.FrameStyle
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.TextFont
import io.github.steve_linux.photograin.model.TextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorTest {
    @Test
    fun noFrameKeepsTheSize() {
        val l = Frame().layout(PxSize(4000, 3000))
        assertEquals(PxSize(4000, 3000), l.size)
        assertEquals(0, l.imageLeft)
    }

    @Test
    fun borderSurroundsThePhotoEvenly() {
        val l = Frame(FrameStyle.BORDER, width = 1f).layout(PxSize(4000, 3000))
        // 15% of the short edge on each side.
        assertEquals(450, l.imageLeft)
        assertEquals(450, l.imageTop)
        assertEquals(PxSize(4900, 3900), l.size)
    }

    @Test
    fun polaroidLeavesRoomBelow() {
        val l = Frame(FrameStyle.POLAROID, width = 0.2f).layout(PxSize(3000, 3000))
        val bottom = l.size.height - l.imageTop - 3000
        assertTrue(bottom >= 3 * l.imageLeft)
        assertEquals(l.size.width - 3000, 2 * l.imageLeft)
    }

    @Test
    fun recipeKeepsFrameAndTexts() {
        val state = EditState(
            frame = Frame(FrameStyle.POLAROID, 0.4f, 0xFF333333.toInt()),
            texts = listOf(TextItem("Roma 2026\nciao", 0.5f, 0.92f, 0.2f, font = TextFont.HANDWRITING, bold = true)),
        )
        assertEquals(state, EditState.fromJson(state.toJson()))
    }
}
