// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.steve_linux.photograin.R
import io.github.steve_linux.photograin.model.ColorGrade
import io.github.steve_linux.photograin.model.GradeTone
import io.github.steve_linux.photograin.model.TonalRange
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorGradePanel(grade: ColorGrade, onChange: (ColorGrade) -> Unit, onCommit: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(TonalRange.SHADOWS) }
    val tone = grade[range]

    fun setTone(next: GradeTone) {
        onChange(grade.withTone(range, next))
    }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TonalRange.entries.forEachIndexed { i, item ->
                val label = when (item) {
                    TonalRange.SHADOWS -> R.string.param_shadows
                    TonalRange.MIDTONES -> R.string.grade_midtones
                    TonalRange.HIGHLIGHTS -> R.string.grade_highlights
                }
                SegmentedButton(
                    selected = item == range,
                    onClick = { range = item },
                    shape = SegmentedButtonDefaults.itemShape(i, TonalRange.entries.size),
                    icon = {},
                ) { Text(stringResource(label)) }
            }
        }
        val hue = tone.hue.coerceIn(0f, 360f)
        val intensity = tone.intensity.coerceIn(0f, 1f)
        val luminance = tone.luminance.coerceIn(-1f, 1f)
        val balance = grade.balance.coerceIn(-1f, 1f)
        ParamSlider(
            stringResource(R.string.hsl_hue), hue, 0f..360f, "${hue.roundToInt()}°",
            { setTone(tone.copy(hue = it)) }, onCommit, { setTone(tone.copy(hue = 0f)); onCommit() },
        )
        ParamSlider(
            stringResource(R.string.grade_intensity), intensity, 0f..1f, percent(intensity),
            { setTone(tone.copy(intensity = it)) }, onCommit, { setTone(tone.copy(intensity = 0f)); onCommit() },
        )
        ParamSlider(
            stringResource(R.string.hsl_luminance), luminance, -1f..1f, percent(luminance),
            { setTone(tone.copy(luminance = it)) }, onCommit, { setTone(tone.copy(luminance = 0f)); onCommit() },
        )
        ParamSlider(
            stringResource(R.string.grade_balance), balance, -1f..1f, percent(balance),
            { onChange(grade.copy(balance = it)) }, onCommit, { onChange(grade.copy(balance = 0f)); onCommit() },
            help = R.string.grade_balance_help,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).background(Color.hsv(hue, intensity, 0.85f), CircleShape))
            TextButton(
                enabled = grade != ColorGrade(),
                onClick = { onChange(ColorGrade()); onCommit() },
            ) { Text(stringResource(R.string.grade_reset)) }
        }
    }
}

private fun percent(value: Float): String = (value * 100).roundToInt().let { if (it > 0) "+$it" else "$it" }
