package com.meta.wearable.retail.ui.theme

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.background
import androidx.compose.foundation.style.shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object VaultierComponentStyles {
    val userChatBubbleStyle: Style = Style {
        background(Color(0xFF2C2A26))
        shape(vaultierShapes.medium)
    }

    val assistantChatBubbleStyle: Style = Style {
        background(Color.White)
        shape(vaultierShapes.medium)
    }

    val productCardStyle: Style = Style {
        background(Color.White)
        shape(vaultierShapes.extraSmall)
    }
}

// Extensions on StyleScope to reference theme tokens
val StyleScope.vaultierColors: VaultierColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVaultierColors.currentValue

val StyleScope.vaultierTypography: androidx.compose.material3.Typography
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography

val StyleScope.vaultierShapes: androidx.compose.material3.Shapes
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.shapes
