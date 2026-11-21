// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.data.PresetStore
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.Preset
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PresetStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file() = File(temp.root, "presets.json")
    private val preset = Preset("source", "Warm", Adjustments(temperature = .4f, grain = .2f))

    @Test fun renameKeepsIdValuesOrderAndPersistsAcrossInstances() {
        val store = PresetStore(file())
        val first = store.save(preset)
        val second = store.save(preset.copy(name = "Other"))
        store.rename(first.id, "  Sunset  ")
        assertEquals(listOf(first.copy(name = "Sunset"), second), PresetStore(file()).load())
    }
    @Test fun duplicateGetsNewIdentityAndCanBeRenamedDeletedIndependently() {
        val store = PresetStore(file())
        val first = store.save(preset)
        val copy = store.duplicate(first.id, "Copy")!!
        assertNotEquals(first.id, copy.id)
        assertEquals(first.adjustments, copy.adjustments)
        store.rename(copy.id, "Different")
        assertEquals(first, store.load()[0])
        store.delete(copy.id)
        assertEquals(listOf(first), store.load())
    }
    @Test fun blankNamesCannotOverwriteExistingLibrary() {
        val store = PresetStore(file())
        val first = store.save(preset)
        val original = file().readText()
        assertThrows(IllegalArgumentException::class.java) { store.rename(first.id, "  ") }
        assertThrows(IllegalArgumentException::class.java) { store.save(preset.copy(name = "")) }
        assertEquals(original, file().readText())
    }
    @Test fun malformedExistingLibraryIsNeverOverwrittenByMutation() {
        file().writeText("broken json")
        val store = PresetStore(file())
        assertTrue(store.load().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.save(preset) }
        assertEquals("broken json", file().readText())
    }
    @Test fun missingTargetsAreNoOpsAndBuiltInIsConvertedToUserPreset() {
        val store = PresetStore(file())
        val saved = store.save(preset.copy(builtIn = "warm", name = "a".repeat(80)))
        assertNull(saved.builtIn)
        assertEquals(40, saved.name.length)
        store.rename("missing", "New")
        store.delete("missing")
        assertNull(store.duplicate("missing", "Copy"))
        assertEquals(listOf(saved), store.load())
    }
    @Test fun multipleInstancesPreserveEarlierSavesAndLeaveNoTemporaryFiles() {
        val a = PresetStore(file()); val b = PresetStore(file())
        val first = a.save(preset); val second = b.save(preset.copy(name = "B"))
        assertEquals(listOf(first, second), a.load())
        assertEquals(listOf("presets.json"), temp.root.list()!!.toList())
    }
}
