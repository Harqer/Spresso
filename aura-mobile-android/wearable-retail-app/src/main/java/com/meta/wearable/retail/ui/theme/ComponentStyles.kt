package com.meta.wearable.retail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Industrial Hardening: Standardizing Style Extensions
// Replaced experimental Style API with stable Modifier extensions

@Composable
fun Modifier.userChatBubble(): Modifier = this
    .background(Color(0xFF2C2A26), RoundedCornerShape(12.dp))

@Composable
fun Modifier.assistantChatBubble(): Modifier = this
    .background(Color.White, RoundedCornerShape(12.dp))

@Composable
fun Modifier.productCard(): Modifier = this
    .background(Color.White, RoundedCornerShape(8.dp))
