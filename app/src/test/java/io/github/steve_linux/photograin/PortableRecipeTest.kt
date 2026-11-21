// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.data.ProjectBackup
import io.github.steve_linux.photograin.data.RecipeOriginalMismatch
import io.github.steve_linux.photograin.model.EditState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableRecipeTest {
    private val original = "a".repeat(64)
    private val entry = ProjectBackup.Version("Version 2", EditState().toJson())

    @Test
    fun roundTripsARecipeAndChecksItsOriginal() {
        val recipe = ProjectBackup.Manifest(2, listOf(entry), original)
        val serializer = ProjectBackup.Manifest.serializer()
        val restored = Json.decodeFromString(serializer, Json.encodeToString(serializer, recipe))
        restored.validate(original)
        assertEquals(recipe, restored)
        assertThrows(RecipeOriginalMismatch::class.java) { restored.validate("b".repeat(64)) }
    }

    @Test
    fun preservesLegacyBackupsAndRejectsMultipleVersionsAsOneRecipe() {
        val legacy = ProjectBackup.Manifest(versions = listOf(entry, entry))
        legacy.validate(null)
        assertThrows(IllegalArgumentException::class.java) { legacy.validate(original) }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectBackup.Manifest(2, listOf(entry, entry), original).validate(original)
        }
    }
}
