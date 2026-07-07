package com.meta.wearable.retail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VaultierColors(
    val brandPrimary: Color,
    val brandSecondary: Color,
    val surfaceBase: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val error: Color
)

val LightVaultierColors = VaultierColors(
    brandPrimary = Color(0xFF2C2A26),
    brandSecondary = Color(0xFF433E38),
    surfaceBase = Color(0xFFF5F2EB),
    textPrimary = Color(0xFF2C2A26),
    textSecondary = Color(0xFF5D5A53),
    error = Color(0xFFB00020)
)

val LocalVaultierColors = staticCompositionLocalOf { LightVaultierColors }

@Composable
fun VaultierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For this migration, we'll keep it simple and use light colors
    val colors = LightVaultierColors
    
    CompositionLocalProvider(
        LocalVaultierColors provides colors
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = colors.brandPrimary,
                surface = colors.surfaceBase,
                onSurface = colors.textPrimary,
                error = colors.error
            ),
            content = content
        )
    }
}

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

    val styles: VaultierComponentStyles = VaultierComponentStyles
}
