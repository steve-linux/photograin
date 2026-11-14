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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import io.github.steve_linux.photograin.data.ThemeMode

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
fun PhotograinTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
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

private val WorkspaceDarkColors = darkColorScheme(
    primary = Color(0xFF8CC7FF),
    onPrimary = Color(0xFF002C4F),
    primaryContainer = Color(0xFF173D5B),
    onPrimaryContainer = Color(0xFFD0E6FF),
    secondaryContainer = Color(0xFF303A44),
    onSecondaryContainer = Color(0xFFEDF2F7),
    background = Color(0xFF101010),
    surface = Color(0xFF191919),
    surfaceContainer = Color(0xFF232323),
    surfaceVariant = Color(0xFF303030),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outlineVariant = Color(0xFF3B3B3B),
)

private val WorkspaceLightColors = lightColorScheme(
    primary = Color(0xFF005A9C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E7FF),
    onPrimaryContainer = Color(0xFF002C4F),
    secondaryContainer = Color(0xFFDCE5ED),
    onSecondaryContainer = Color(0xFF233442),
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFECECEC),
    surfaceVariant = Color(0xFFE1E1E1),
    onSurface = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF474747),
    outlineVariant = Color(0xFFD1D1D1),
)

@Composable
internal fun PhotoWorkspaceTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    MaterialTheme(colorScheme = if (dark) WorkspaceDarkColors else WorkspaceLightColors, content = content)
}
