// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Amber = Color(0xFFE0A84F)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B00),
    secondary = Color(0xFFB9A88C),
    background = Color(0xFF121212),
    surface = Color(0xFF181818),
    surfaceContainer = Color(0xFF1E1E1E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5300),
    secondary = Color(0xFF6B5D47),
)

/** Neutral gray behind photos, so colors are judged against a non-tinted background. */
val PhotoBackgroundDark = Color(0xFF101010)
val PhotoBackgroundLight = Color(0xFFE6E6E6)

@Composable
fun PhotograinTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
