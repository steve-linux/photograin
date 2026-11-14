// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.Hsl
import io.github.steve_linux.photograin.model.HslBand
import io.github.steve_linux.photograin.model.MonochromeMix
import kotlin.math.roundToInt

@Composable
internal fun MonochromePanel(mix: MonochromeMix, onChange: (MonochromeMix) -> Unit, onCommit: () -> Unit) {
    var band by rememberSaveable { mutableStateOf(HslBand.BLUE) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().toggleable(mix.enabled, role = Role.Switch) {
                onChange(mix.copy(enabled = it))
                onCommit()
            }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.monochrome_enable), Modifier.weight(1f))
            Switch(checked = mix.enabled, onCheckedChange = null)
        }
        if (mix.enabled) {
            HueBandPicker(band, onSelect = { band = it })
            Text(
                stringResource(bandLabel(band)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            val value = mix[band]
            val number = (value * 100).roundToInt()
            ParamSlider(
                label = stringResource(R.string.monochrome_luminance),
                value = value,
                range = -1f..1f,
                display = if (number > 0) "+$number" else "$number",
                onChange = { onChange(mix.withBand(band, it)) },
                onChangeFinished = onCommit,
                onReset = { onChange(mix.withBand(band, 0f)); onCommit() },
            )
            TextButton(
                enabled = HslBand.entries.any { mix[it] != 0f },
                onClick = { onChange(mix.copy(bands = Hsl.ZERO)); onCommit() },
            ) { Text(stringResource(R.string.monochrome_reset)) }
        }
    }
}
