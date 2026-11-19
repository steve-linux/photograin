// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import io.github.steve_linux.photograin.model.Adjustments
import io.github.steve_linux.photograin.model.CurveChannel
import io.github.steve_linux.photograin.model.CurvePoint
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.HslControl
import io.github.steve_linux.photograin.model.Params
import io.github.steve_linux.photograin.model.Preset
import io.github.steve_linux.photograin.model.PresetFields
import io.github.steve_linux.photograin.model.ToneCurve
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/** Imports compatible global Camera Raw settings without opening external resources. */
internal object XmpPreset {
    const val MAX_BYTES = 1024 * 1024
    private const val CRS = "http://ns.adobe.com/camera-raw-settings/1.0/"
    private const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val XML = "http://www.w3.org/XML/1998/namespace"
    private const val MAX_CURVE_POINTS = 64

    private val sliders = mapOf(
        "Exposure2012" to "exposure",
        "Contrast2012" to "contrast",
        "Highlights2012" to "highlights",
        "Shadows2012" to "shadows",
        "Whites2012" to "whites",
        "Blacks2012" to "blacks",
        "Vibrance" to "vibrance",
        "Saturation" to "saturation",
        "Clarity2012" to "clarity",
        "Texture" to "textureAmount",
        "Dehaze" to "dehaze",
        "Sharpness" to "sharpness",
        "LuminanceSmoothing" to "noiseReduction",
        "ColorNoiseReduction" to "colorNoiseReduction",
        "PostCropVignetteAmount" to "vignette",
        "GrainAmount" to "grain",
        "GrainSize" to "grainSize",
        "GrainFrequency" to "grainRoughness",
    )
    private val specs = Params.all.associateBy { it.id }
    private val curves = mapOf(
        "ToneCurvePV2012" to CurveChannel.MASTER,
        "ToneCurvePV2012Red" to CurveChannel.RED,
        "ToneCurvePV2012Green" to CurveChannel.GREEN,
        "ToneCurvePV2012Blue" to CurveChannel.BLUE,
    )

    fun parse(bytes: ByteArray, fallbackName: String): Preset? {
        require(bytes.size <= MAX_BYTES)
        val xml = bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
        // The reader receives decoded text, so an encoding declaration cannot hide a DTD.
        require(!xml.contains("<!DOCTYPE", ignoreCase = true))
        val handler = Reader(fallbackName)
        val reader = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }.newSAXParser().xmlReader
        reader.entityResolver = handler
        reader.errorHandler = handler
        reader.contentHandler = handler
        reader.parse(InputSource(StringReader(xml)))
        return handler.preset
    }

    private class Reader(private val fallbackName: String) : DefaultHandler() {
        private val stack = mutableListOf<Pair<String, String>>()
        private var descriptionDepth = -1
        private val scalars = mutableMapOf<String, String>()
        private val pointLists = mutableMapOf<String, MutableList<String>>()
        private var title: String? = null
        private var text: StringBuilder? = null
        private var isDefaultTitle = false
        var preset: Preset? = null
            private set

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            throw SAXException("External entities are disabled")
        }

        override fun error(e: SAXParseException) { throw e }
        override fun fatalError(e: SAXParseException) { throw e }

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (stack.size >= 64) throw SAXException("XMP nesting limit")
            val parent = stack.lastOrNull()
            stack.add(uri to localName)
            val depth = stack.size
            if (preset != null) return
            if (localName == "Description" && uri == RDF && parent == (RDF to "RDF") && depth in 2..3) {
                descriptionDepth = depth
                for (i in 0 until attributes.length) if (attributes.getURI(i) == CRS) {
                    scalars[attributes.getLocalName(i)] = attributes.getValue(i)
                }
                return
            }
            if (descriptionDepth < 0) return

            when (depth - descriptionDepth) {
                1 -> if (uri == CRS) text = StringBuilder()
                2 -> text = null
                3 -> if (uri == RDF && localName == "li") {
                    val property = stack[descriptionDepth]
                    val isName = property == (CRS to "Name") && parent == (RDF to "Alt")
                    val isPoint = property.first == CRS && property.second in curves && parent == (RDF to "Seq")
                    if (isName || isPoint) {
                        isDefaultTitle = isName && attributes.getValue(XML, "lang") == "x-default"
                        text = StringBuilder()
                    }
                }
                else -> text = null
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val value = text ?: return
            val count = minOf(length, 1024 - value.length)
            if (count > 0) value.append(ch, start, count)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val depth = stack.size
            if (descriptionDepth >= 0 && preset == null) {
                when (depth - descriptionDepth) {
                    0 -> {
                        preset = convert(scalars, pointLists, title ?: fallbackName)
                        descriptionDepth = -1
                        scalars.clear()
                        pointLists.clear()
                        title = null
                    }
                    1 -> if (uri == CRS) {
                        text?.toString()?.let { scalars.putIfAbsent(localName, it) }
                        text = null
                    }
                    3 -> if (uri == RDF && localName == "li") {
                        val property = stack[descriptionDepth].second
                        val value = text?.toString()?.trim()
                        if (!value.isNullOrEmpty()) {
                            if (property == "Name") {
                                if (title == null || isDefaultTitle) title = value
                            } else {
                                val entries = pointLists.getOrPut(property) { mutableListOf() }
                                if (entries.size <= MAX_CURVE_POINTS) entries.add(value)
                            }
                        }
                        text = null
                    }
                }
            }
            stack.removeAt(depth - 1)
        }
    }

    private fun convert(
        scalars: Map<String, String>,
        points: Map<String, List<String>>,
        name: String,
    ): Preset? {
        fun scalar(field: String): Float? = scalars[field]?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() }
        var adjustments = Adjustments()
        val includedSliders = mutableSetOf<String>()
        for ((tag, name) in sliders) {
            val number = scalar(tag) ?: continue
            val spec = specs.getValue(name)
            val scale = if (tag == "Exposure2012") 1f else 100f
            adjustments = spec.set(adjustments, (number / scale).coerceIn(spec.min, spec.max))
            includedSliders.add(name)
        }
        var hsl = adjustments.hsl
        val includedMix = mutableSetOf<String>()
        for (control in HslControl.entries) {
            val prefix = when (control) {
                HslControl.HUE -> "HueAdjustment"
                HslControl.SATURATION -> "SaturationAdjustment"
                HslControl.LUMINANCE -> "LuminanceAdjustment"
            }
            for (band in HslBand.entries) {
                val name = band.name.lowercase().replaceFirstChar { it.uppercaseChar() }
                val number = scalar(prefix + name) ?: continue
                hsl = control.update(hsl, band, number / 100f)
                includedMix.add(PresetFields.colorMixKey(control, band))
            }
        }
        var tone = adjustments.curves
        val includedCurves = mutableSetOf<CurveChannel>()
        for ((tag, channel) in curves) {
            val entries = points[tag] ?: continue
            val curve = readCurve(entries) ?: continue
            tone = tone.with(channel, curve)
            includedCurves.add(channel)
        }
        val fields = PresetFields(includedSliders, includedMix, includedCurves)
        if (fields.isEmpty) return null
        return Preset(id = "", name = name.take(256), adjustments = adjustments.copy(hsl = hsl, curves = tone), fields = fields)
    }

    private fun readCurve(entries: List<String>): ToneCurve? {
        if (entries.size !in 2..MAX_CURVE_POINTS) return null
        val points = entries.map { entry ->
            val pair = entry.split(',', limit = 3)
            if (pair.size != 2) return null
            val x = pair[0].trim().toFloatOrNull()?.takeIf { it in 0f..255f } ?: return null
            val y = pair[1].trim().toFloatOrNull()?.takeIf { it in 0f..255f } ?: return null
            CurvePoint(x / 255f, y / 255f)
        }
        if (points.first().x != 0f || points.last().x != 1f) return null
        if (points.zipWithNext().any { (a, b) -> a.x >= b.x }) return null
        return ToneCurve(points)
    }
}
