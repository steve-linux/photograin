// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.*
import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    @Test fun jumpsInBothDirectionsPreservingRedoOrder() {
        val h = History(0)
        (1..5).forEach(h::commit)
        assertEquals(2, h.goTo(2))
        assertEquals((0..5).toList(), h.states)
        assertEquals(2, h.position)
        assertEquals(3, h.redo())
        assertEquals(5, h.goTo(5))
        assertFalse(h.canRedo)
        assertEquals(0, h.goTo(0))
        assertFalse(h.canUndo)
        assertEquals(5, h.goTo(5))
    }
    @Test fun newEditAfterRestoreReplacesOnlyForwardBranch() {
        val h = History("initial")
        listOf("a", "b", "c").forEach(h::commit)
        h.goTo(1)
        h.commit("different")
        assertEquals(listOf("initial", "a", "different"), h.states)
        assertFalse(h.canRedo)
        assertEquals("a", h.undo())
        assertEquals("different", h.redo())
    }
    @Test fun invalidJumpsAndIdenticalCommitPreserveHistory() {
        val h = History(0)
        h.commit(1); h.commit(2); h.goTo(1)
        h.commit(1)
        assertEquals(1, h.goTo(-1)); assertEquals(1, h.goTo(3))
        assertEquals(listOf(0, 1, 2), h.states)
        assertTrue(h.canRedo)
    }
    @Test fun snapshotsDoNotChangeWhenNavigatingAndResetStartsNewSession() {
        val h = History(10)
        h.commit(20)
        val snapshot = h.states
        h.goTo(0); h.commit(30)
        assertEquals(listOf(10, 20), snapshot)
        h.reset(40)
        assertEquals(listOf(40), h.states)
        assertEquals(0, h.position)
        assertFalse(h.canUndo); assertFalse(h.canRedo)
    }
    @Test fun summaryNamesEveryChangedSliderAndMaskMetadata() {
        val before = EditState(masks = listOf(LocalAdjustment()))
        val after = before.copy(adjustments = Adjustments(exposure = 1f, temperature = .4f),
            masks = listOf(LocalAdjustment(name = "Sky", enabled = false)))
        val summary = EditChanges.between(before, after)
        assertEquals(listOf("exposure", "temperature"), summary.parameters)
        assertEquals(listOf(EditChanges.Area.MASKS), summary.areas)
        assertEquals(summary, EditChanges.between(after, before))
    }
    @Test fun summaryCoversNonSliderEditsAndUnchangedState() {
        val initial = EditState()
        val changed = initial.copy(adjustments = Adjustments(look = Look(lutId = "test")), correctLens = true)
        assertEquals(listOf(EditChanges.Area.LOOK, EditChanges.Area.OPTICS), EditChanges.between(initial, changed).areas)
        assertEquals(EditChanges.Summary(emptyList(), emptyList()), EditChanges.between(changed, changed))
    }
}
