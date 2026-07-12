package com.meta.wearable.retail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.meta.wearable.retail.data.ThemeMode

// Industrial Foundation: Color System
data class VaultierColors(
    val primary: Color = Color(0xFF2C2A26),
    val secondary: Color = Color(0xFFF5F5F5),
    val accent: Color = Color(0xFFE47E3A),
    val surface: Color = Color.White,
    val onSurface: Color = Color.Black,
)

val LocalVaultierColors = staticCompositionLocalOf { VaultierColors() }

object VaultierTheme {
    val colors: VaultierColors
        @Composable
        @ReadOnlyComposable
        get() = LocalVaultierColors.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes
}

@Composable
fun VaultierTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colors =
        if (isDark) {
            VaultierColors(primary = Color.White, onSurface = Color.White, surface = Color.Black)
        } else {
            VaultierColors()
        }

    CompositionLocalProvider(
        LocalVaultierColors provides colors,
    ) {
        MaterialTheme(
            colorScheme =
                lightColorScheme(
                    primary = colors.primary,
                    surface = colors.surface,
                    onSurface = colors.onSurface,
                ),
            content = content,
        )
    }
}
