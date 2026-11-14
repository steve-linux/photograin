// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.data.LensDatabase
import io.github.steve_linux.photograin.math.Vec2
import io.github.steve_linux.photograin.model.EditState
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LensCorrectionTest {
    private val database by lazy {
        Json.decodeFromString<LensDatabase>(File("src/main/assets/optics/lensfun.json").readText())
    }

    @Test
    fun matchesRealProfilesAndRefusesAnUnidentifiedInterchangeableLens() {
        val sony = database.find("SONY", "ILCE-6000", "E PZ 16-50mm F3.5-5.6 OSS", 16.0, 3.5, 0.0)
        assertNotNull(sony)
        assertEquals(-0.10642, sony!!.distortion[2], 1e-7)
        assertEquals(0.0002723, sony.red[2], 1e-7)
        assertEquals(-0.6128, sony.vignette[0], 1e-7)
        assertNull(database.find("SONY", "ILCE-6000", "", 16.0, 3.5, 0.0))
        assertNull(database.find("SONY", "Unknown body", sony.name, 16.0, 3.5, 0.0))
        assertNotNull(database.find("SONY", "DSC-RX100", "", 10.4, 1.8, 0.0))
    }

    @Test
    fun interpolatesCalibrationAtTheShootingFocalLength() {
        val result = database.find("Sony", "ILCE-6000", "E PZ 16-50mm f/3.5-5.6 OSS", 16.5, 0.0, 0.0)!!
        assertEquals((-0.10642 - 0.1021) / 2, result.distortion[2], 1e-7)
        assertTrue(result.vignette.all { it == 0.0 })
    }

    @Test
    fun correctedCoordinatesRoundTripAndExportTilesEncloseAllChannels() {
        val profile = database.find("Sony", "ILCE-6000", "E PZ 16-50mm f/3.5-5.6 OSS", 16.0, 3.5, 10.0)!!
        for ((w, h) in listOf(6000 to 4000, 4000 to 6000)) {
            val mapping = profile.mapping(w, h)
            val bounds = mapping.sourceBounds(0.13, 0.61, 0.37, 1.0)
            for (y in 0..20) for (x in 0..20) {
                val corrected = Vec2(0.13 + 0.24 * x / 20, 0.61 + 0.39 * y / 20)
                val original = mapping.toOriginal(corrected)
                val recovered = mapping.fromOriginal(original)
                assertEquals(corrected.x, recovered.x, 1e-6)
                assertEquals(corrected.y, recovered.y, 1e-6)
                val dx = original.x - 0.5
                val dy = original.y - 0.5
                val radius = hypot(dx * mapping.axisX, dy * mapping.axisY)
                for (c in listOf(listOf(1.0, 0.0, 0.0), profile.red, profile.blue)) {
                    val gain = c[0] + c[1] * radius + c[2] * radius * radius
                    assertTrue(0.5 + dx * gain in bounds.left..bounds.right)
                    assertTrue(0.5 + dy * gain in bounds.top..bounds.bottom)
                }
            }
            for (y in 0..50) for (x in 0..50) {
                val p = mapping.toOriginal(Vec2(x / 50.0, y / 50.0))
                assertTrue(p.x in -1e-6..1.000001 && p.y in -1e-6..1.000001)
            }
        }
    }

    @Test
    fun lensToggleIsSavedAndOlderRecipesDefaultToOff() {
        val old = EditState.fromJson("""{"version":1,"edit":{}}""")
        assertFalse(old!!.correctLens)
        val active = old.copy(correctLens = true)
        assertEquals(active, EditState.fromJson(active.toJson()))
    }
}
