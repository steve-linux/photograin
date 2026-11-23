// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.EditState
import io.github.steve_linux.photograin.model.LensCorrection
import kotlin.math.roundToInt

/**
 * Optics, as in Lightroom: chromatic aberration removal, the lens profile when the photo's
 * metadata names a known lens, and manual distortion and vignetting for any photo.
 */
@Composable
internal fun OpticsPanel(
    profile: LensCorrection?,
    edit: EditState,
    onLensCorrection: (Boolean) -> Unit,
    onRemoveChromaticAberration: (Boolean) -> Unit,
    onDistortion: (Float) -> Unit,
    onVignetting: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OpticsSwitch(
            label = stringResource(R.string.optics_remove_ca),
            checked = edit.removeChromaticAberration,
            enabled = true,
            onCheckedChange = onRemoveChromaticAberration,
        )
        OpticsSwitch(
            label = stringResource(R.string.optics_correct_lens),
            checked = edit.correctLens && profile != null,
            enabled = profile != null,
            onCheckedChange = onLensCorrection,
        )
        Text(
            profile?.name ?: stringResource(R.string.optics_no_profile),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OpticsSlider(R.string.optics_distortion, R.string.help_optics_distortion, edit.lensDistortion, onDistortion, onChangeFinished)
        OpticsSlider(R.string.optics_vignetting, R.string.help_optics_vignetting, edit.lensVignetting, onVignetting, onChangeFinished)
    }
}

@Composable
private fun OpticsSwitch(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun OpticsSlider(label: Int, help: Int, value: Float, onChange: (Float) -> Unit, onChangeFinished: () -> Unit) {
    val shown = (value * 100).roundToInt()
    ParamSlider(
        label = stringResource(label),
        value = value,
        range = -1f..1f,
        display = if (shown > 0) "+$shown" else "$shown",
        onChange = onChange,
        onChangeFinished = onChangeFinished,
        onReset = { onChange(0f); onChangeFinished() },
        help = help,
    )
}
