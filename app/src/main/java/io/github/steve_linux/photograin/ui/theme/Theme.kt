// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.steve_linux.photograin.R
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
val PhotoBackgroundDark = Color.Black
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
    background = Color.Black,
    surface = Color(0xFF202020),
    surfaceContainer = Color(0xFF252525),
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

private val WorkspaceFont = FontFamily(
    Font(R.font.source_sans_regular, FontWeight.Normal),
    Font(R.font.source_sans_semibold, FontWeight.SemiBold),
)

private val WorkspaceTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = WorkspaceFont),
        bodyMedium = bodyMedium.copy(fontFamily = WorkspaceFont),
        bodySmall = bodySmall.copy(fontFamily = WorkspaceFont),
        labelLarge = labelLarge.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(fontFamily = WorkspaceFont, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    )
}

@Composable
internal fun PhotoWorkspaceTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    MaterialTheme(
        colorScheme = if (dark) WorkspaceDarkColors else WorkspaceLightColors,
        typography = WorkspaceTypography,
        content = content,
    )
}
