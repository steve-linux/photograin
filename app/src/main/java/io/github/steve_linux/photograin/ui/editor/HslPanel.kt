// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand

/** Hue, saturation and luminance for one of eight color bands at a time. */
@Composable
fun HslPanel(hsl: Hsl, onChange: (Hsl) -> Unit, onChangeFinished: () -> Unit) {
    var band by rememberSaveable { mutableStateOf(HslBand.RED) }
    val i = band.ordinal
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (b in HslBand.entries) {
                val label = stringResource(bandLabel(b))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(bandColor(b))
                        .border(
                            width = if (b == band) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        )
                        .clickable { band = b }
                        .semantics { contentDescription = label },
                )
            }
        }
        Text(
            stringResource(bandLabel(band)),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        HslSlider(R.string.hsl_hue, hsl.hue.getOrElse(i) { 0f }, { onChange(hsl.withHue(band, it)) }, onChangeFinished) {
            onChange(hsl.withHue(band, 0f)); onChangeFinished()
        }
        HslSlider(R.string.hsl_saturation, hsl.saturation.getOrElse(i) { 0f }, { onChange(hsl.withSaturation(band, it)) }, onChangeFinished) {
            onChange(hsl.withSaturation(band, 0f)); onChangeFinished()
        }
        HslSlider(R.string.hsl_luminance, hsl.luminance.getOrElse(i) { 0f }, { onChange(hsl.withLuminance(band, it)) }, onChangeFinished) {
            onChange(hsl.withLuminance(band, 0f)); onChangeFinished()
        }
    }
}

@Composable
private fun HslSlider(label: Int, value: Float, onChange: (Float) -> Unit, onChangeFinished: () -> Unit, onReset: () -> Unit) {
    val n = Math.round(value * 100)
    ParamSlider(
        label = stringResource(label),
        help = helpFor(label),
        value = value,
        range = -1f..1f,
        display = if (n > 0) "+$n" else "$n",
        onChange = onChange,
        onChangeFinished = onChangeFinished,
        onReset = onReset,
    )
}

private fun bandColor(b: HslBand) = when (b) {
    HslBand.RED -> Color(0xFFE53935)
    HslBand.ORANGE -> Color(0xFFFB8C00)
    HslBand.YELLOW -> Color(0xFFFDD835)
    HslBand.GREEN -> Color(0xFF43A047)
    HslBand.AQUA -> Color(0xFF00ACC1)
    HslBand.BLUE -> Color(0xFF1E88E5)
    HslBand.PURPLE -> Color(0xFF8E24AA)
    HslBand.MAGENTA -> Color(0xFFD81B60)
}

private fun bandLabel(b: HslBand) = when (b) {
    HslBand.RED -> R.string.band_red
    HslBand.ORANGE -> R.string.band_orange
    HslBand.YELLOW -> R.string.band_yellow
    HslBand.GREEN -> R.string.band_green
    HslBand.AQUA -> R.string.band_aqua
    HslBand.BLUE -> R.string.band_blue
    HslBand.PURPLE -> R.string.band_purple
    HslBand.MAGENTA -> R.string.band_magenta
}
