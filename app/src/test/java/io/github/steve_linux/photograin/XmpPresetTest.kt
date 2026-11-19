// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.data.XmpPreset
import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CurvePoint
import io.github.steve_linux.photograin.model.Curves
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.ToneCurve
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmpPresetTest {
    @Test
    fun importsOnlyPresentSlidersColorBandsAndCurveChannels() {
        val file = xmp(
            attributes = """edit:Exposure2012="+0.75" edit:Contrast2012="0" edit:HueAdjustmentRed="-30" """,
            contents = """
                <edit:Name><rdf:Alt><rdf:li xml:lang="fr">Rivage</rdf:li>
                    <rdf:li xml:lang="x-default">Coast</rdf:li></rdf:Alt></edit:Name>
                <edit:Texture>20</edit:Texture>
                <edit:ToneCurvePV2012Red><rdf:Seq><rdf:li>0, 0</rdf:li>
                    <rdf:li>128, 140</rdf:li><rdf:li>255, 255</rdf:li></rdf:Seq></edit:ToneCurvePV2012Red>
                <edit:Masking><rdf:Description edit:Saturation="-100"/></edit:Masking>
            """,
        )
        val preset = checkNotNull(XmpPreset.parse(file.toByteArray(), "fallback"))
        assertEquals("Coast", preset.name)
        val keptCurve = ToneCurve(listOf(CurvePoint(0f, 0.1f), CurvePoint(1f, 1f)))
        val current = Adjustments(
            exposure = 1f, contrast = 0.5f, saturation = 0.25f,
            hsl = Hsl().withHue(HslBand.BLUE, 0.6f),
            curves = Curves(blue = keptCurve),
        )
        val restored = Json.decodeFromString<Preset>(Json.encodeToString(preset))
        val red = ToneCurve(listOf(CurvePoint(0f, 0f), CurvePoint(128f / 255, 140f / 255), CurvePoint(1f, 1f)))
        assertEquals(current.copy(
            exposure = 0.75f, contrast = 0f, textureAmount = 0.2f,
            hsl = current.hsl.withHue(HslBand.RED, -0.3f), curves = current.curves.copy(red = red),
        ), restored.applyTo(current))
    }

    @Test
    fun ignoresForeignAndUnsupportedSettingsAndInvalidCurves() {
        val file = xmp(
            attributes = """foreign:Exposure2012="2" edit:Temperature="7500" edit:Vibrance="NaN" """,
            contents = """
                <edit:ToneCurvePV2012><rdf:Seq><rdf:li>0, 0</rdf:li>
                    <rdf:li>0, 100</rdf:li><rdf:li>255, 255</rdf:li></rdf:Seq></edit:ToneCurvePV2012>
            """,
        )
        assertNull(XmpPreset.parse(file.toByteArray(), "fallback"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDtdBeforeParsing() {
        val input = """<!DOCTYPE x [<!ENTITY sample "reference">]><x>&sample;</x>"""
        XmpPreset.parse(input.toByteArray(), "fallback")
    }

    private fun xmp(attributes: String, contents: String) = """
        <?xpacket begin=""?>
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description xmlns:edit="http://ns.adobe.com/camera-raw-settings/1.0/"
              xmlns:foreign="urn:other" ${attributes.trim()}>
              $contents
            </rdf:Description>
          </rdf:RDF>
        </x:xmpmeta>
        <?xpacket end="w"?>
    """.trimIndent()
}
