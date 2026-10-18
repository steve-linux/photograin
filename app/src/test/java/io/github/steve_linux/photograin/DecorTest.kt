// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.Frame
import io.github.steve_linux.photograin.model.FrameStyle
import io.github.steve_linux.photograin.model.PxSize
import io.github.steve_linux.photograin.model.ShapeItem
import io.github.steve_linux.photograin.model.ShapeType
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

    @Test
    fun recipeKeepsShapesAndWatermark() {
        val state = EditState(
            texts = listOf(TextItem("© Stefano", opacity = 0.35f, angle = -30f, tiled = true)),
            shapes = listOf(
                ShapeItem(ShapeType.ARROW, 0.1f, 0.2f, 0.6f, 0.7f, width = 0.5f),
                ShapeItem(ShapeType.ELLIPSE, fill = true, color = 0xFF1565C0.toInt()),
            ),
        )
        assertEquals(state, EditState.fromJson(state.toJson()))
    }

    @Test
    fun oldRecipesHaveNoShapesAndPlainText() {
        val old = EditState.fromJson("""{"version":1,"edit":{"texts":[{"text":"ciao"}]}}""")!!
        assertTrue(old.shapes.isEmpty())
        val t = old.texts.single()
        assertEquals(1f, t.opacity, 0f)
        assertEquals(0f, t.angle, 0f)
        assertTrue(!t.tiled)
    }

    @Test
    fun strokeScalesWithTheOutput() {
        val s = ShapeItem(ShapeType.LINE, width = 1f)
        assertEquals(2 * s.strokePx(1000), s.strokePx(2000), 1e-3f)
        assertTrue(ShapeItem(ShapeType.RECT).isBox)
        assertTrue(!ShapeItem(ShapeType.ARROW).isBox)
    }
}
