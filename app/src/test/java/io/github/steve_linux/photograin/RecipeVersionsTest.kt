// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.data.RecipeStore
import io.github.steve_linux.photograin.data.RecipeVersion
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.EditState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeVersionsTest {
    private val store = RecipeStore(Files.createTempDirectory("recipes").toFile())
    private val key = RecipeStore.keyOf("content://photo/1")
    private fun edit(exposure: Float) = EditState(adjustments = Adjustments(exposure = exposure))

    @Test
    fun aPhotoStartsWithOneVersion() {
        assertEquals(listOf(RecipeVersion(1)), store.versions(key))
        assertNull(store.load(key, 1))
    }

    @Test
    fun duplicatesKeepTheirOwnEdit() {
        store.save(key, edit(1f), 1)
        val second = store.create(key, edit(1f))
        store.save(key, edit(-1f), second)
        assertEquals(listOf(1, 2), store.versions(key).map { it.id })
        assertEquals(edit(1f), store.load(key, 1))
        assertEquals(edit(-1f), store.load(key, second))
    }

    @Test
    fun aBlankDuplicateStillExists() {
        val id = store.create(key, EditState())
        store.save(key, EditState(), id)
        assertEquals(listOf(1, 2), store.versions(key).map { it.id })
    }

    @Test
    fun deletingTheFirstVersionPromotesTheNext() {
        store.save(key, edit(1f), 1)
        val second = store.create(key, edit(2f))
        store.rename(key, second, "Bianco e nero")
        val third = store.create(key, edit(3f))
        val left = store.delete(key, 1)
        assertEquals(listOf(RecipeVersion(1, "Bianco e nero"), RecipeVersion(third)), left)
        assertEquals(edit(2f), store.load(key, 1))
        assertEquals(edit(3f), store.load(key, third))
    }

    @Test
    fun namesCanBeClearedAndDeletedVersionsGo() {
        val second = store.create(key, edit(2f))
        store.rename(key, second, "  ")
        assertEquals(RecipeVersion(second), store.versions(key)[1])
        store.delete(key, second)
        val third = store.create(key, edit(3f))
        assertEquals(listOf(1, third), store.versions(key).map { it.id })
    }
}
